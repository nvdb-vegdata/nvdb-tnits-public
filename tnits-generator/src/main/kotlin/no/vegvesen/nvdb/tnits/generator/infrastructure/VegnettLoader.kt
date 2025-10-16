package no.vegvesen.nvdb.tnits.generator.infrastructure

import jakarta.inject.Singleton
import kotlinx.coroutines.flow.chunked
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.toKotlinLocalDate
import no.vegvesen.nvdb.apiles.uberiket.Veglenkesekvens
import no.vegvesen.nvdb.tnits.common.extensions.WithLogger
import no.vegvesen.nvdb.tnits.generator.core.api.*
import no.vegvesen.nvdb.tnits.generator.core.extensions.SRID.EPSG5973
import no.vegvesen.nvdb.tnits.generator.core.extensions.SRID.UTM33
import no.vegvesen.nvdb.tnits.generator.core.extensions.forEachChunked
import no.vegvesen.nvdb.tnits.generator.core.extensions.parseWkt
import no.vegvesen.nvdb.tnits.generator.core.model.Superstedfesting
import no.vegvesen.nvdb.tnits.generator.core.model.Veglenke
import no.vegvesen.nvdb.tnits.generator.infrastructure.rocksdb.RocksDbContext
import kotlin.time.Clock
import kotlin.time.Instant

@Singleton
class VegnettLoader(
    private val keyValueStore: KeyValueStore,
    private val uberiketApi: UberiketApi,
    private val veglenkerRepository: VeglenkerRepository,
    private val rocksDbContext: RocksDbContext,
) : WithLogger {

    suspend fun backfillVeglenkesekvenser(): Int {
        val backfillCompleted = keyValueStore.getValue<Instant>("veglenkesekvenser_backfill_completed")

        if (backfillCompleted != null) {
            log.info("Backfill for veglenkesekvenser er allerede fullført den $backfillCompleted")
            return 0
        }

        var lastId = keyValueStore.getValue<Long>("veglenkesekvenser_backfill_last_id")

        if (lastId == null) {
            log.info("Ingen veglenkesekvenser backfill har blitt startet ennå. Starter backfill...")
            val now = Clock.System.now()
            keyValueStore.putValue("veglenkesekvenser_backfill_started", now)
        } else {
            log.info("Veglenkesekvenser backfill pågår. Gjenopptar fra siste ID: $lastId")
        }

        var totalCount = 0
        var batchCount = 0

        do {
            val veglenkesekvenser = uberiketApi.streamVeglenkesekvenser(start = lastId).toList()
            lastId = veglenkesekvenser.lastOrNull()?.id

            if (veglenkesekvenser.isEmpty()) {
                log.info("Ingen veglenkesekvenser å sette inn, backfill fullført.")
                keyValueStore.putValue("veglenkesekvenser_backfill_completed", Clock.System.now())
            } else {
                val updates = veglenkesekvenser.associate {
                    val domainVeglenker = it.convertToDomainVeglenker()
                    it.id to domainVeglenker
                }

                rocksDbContext.writeBatch {
                    veglenkerRepository.batchInsert(updates)
                    keyValueStore.putValue("veglenkesekvenser_backfill_last_id", lastId!!)
                }

                totalCount += veglenkesekvenser.size
                batchCount++
                if (batchCount % 50 == 0) {
                    log.info("Lastet $totalCount veglenkesekvenser")
                }
            }
        } while (veglenkesekvenser.isNotEmpty())

        return totalCount
    }

    suspend fun updateVeglenkesekvenser(): Int {
        var lastHendelseId =
            keyValueStore.getValue<Long>("veglenkesekvenser_last_hendelse_id") ?: uberiketApi.getLatestVeglenkesekvensHendelseId(
                keyValueStore.getValue<Instant>("veglenkesekvenser_backfill_completed")
                    ?: error("Veglenkesekvenser backfill er ikke ferdig"),
            )

        var hendelseCount = 0

        uberiketApi.streamVeglenkesekvensHendelser(lastHendelseId).chunked(100)
            .collect { hendelser ->
                lastHendelseId = hendelser.last().hendelseId

                val changedIds = hendelser.map { it.nettelementId }.toSet()
                val updates = fetchUpdates(changedIds)

                rocksDbContext.writeBatch {
                    veglenkerRepository.batchUpdate(updates)
                    keyValueStore.putValue("veglenkesekvenser_last_hendelse_id", lastHendelseId)
                }
                log.debug("Behandlet ${hendelser.size} hendelser, siste ID: $lastHendelseId")
                hendelseCount += hendelser.size
            }

        log.info("Oppdatering fra $hendelseCount hendelser for veglenkesekvenser fullført. Siste hendelse-ID: $lastHendelseId")

        return hendelseCount
    }

    private suspend fun fetchUpdates(changedIds: Set<Long>): MutableMap<Long, List<Veglenke>?> {
        val updates = mutableMapOf<Long, List<Veglenke>?>()

        // Process changed veglenkesekvenser in chunks
        changedIds.forEachChunked(100) { chunk ->
            var start: Long? = null
            do {
                val batch =
                    uberiketApi
                        .streamVeglenkesekvenser(start = start, ider = chunk)
                        .toList()

                if (batch.isNotEmpty()) {
                    batch.forEach { veglenkesekvens ->
                        val domainVeglenker = veglenkesekvens.convertToDomainVeglenker()
                        updates[veglenkesekvens.id] = domainVeglenker
                    }
                    start = batch.maxOf { it.id }
                }
            } while (batch.isNotEmpty())
        }

        // Handle deleted veglenkesekvenser (those that didn't return data)
        val foundIds = updates.keys
        val deletedIds = changedIds - foundIds
        deletedIds.forEach { deletedId ->
            updates[deletedId] = null // Mark for deletion
        }
        return updates
    }

    companion object {

        /**
         * Konverterer en [no.vegvesen.nvdb.apiles.uberiket.Veglenkesekvens] fra Uberiket API til en liste av domenemodellen [Veglenke].
         * - Filtrer til bare aktive veglenker.
         * - Mapper start- og sluttporter til posisjoner og noder.
         * - Sorterer veglenkene etter startposisjon.
         */
        fun Veglenkesekvens.convertToDomainVeglenker(): List<Veglenke> {
            val portLookup = this.porter.associateBy { it.nummer }

            return this.veglenker
                .map { veglenke ->

                    val startport =
                        portLookup[veglenke.startport]
                            ?: error("Startport ${veglenke.startport} not found in veglenkesekvens ${this.id}")
                    val sluttport =
                        portLookup[veglenke.sluttport]
                            ?: error("Sluttport ${veglenke.sluttport} not found in veglenkesekvens ${this.id}")

                    val srid = veglenke.geometri.srid.value.toInt()

                    check(srid == EPSG5973)

                    Veglenke(
                        veglenkesekvensId = this.id,
                        veglenkenummer = veglenke.nummer,
                        startposisjon = startport.posisjon,
                        sluttposisjon = sluttport.posisjon,
                        startnode = startport.nodeId,
                        sluttnode = sluttport.nodeId,
                        startdato = veglenke.gyldighetsperiode.startdato.toKotlinLocalDate(),
                        sluttdato = veglenke.gyldighetsperiode.sluttdato?.toKotlinLocalDate(),
                        // 3D -> 2D
                        geometri = parseWkt(veglenke.geometri.wkt, UTM33),
                        typeVeg = veglenke.typeVeg,
                        detaljniva = veglenke.detaljniva,
                        feltoversikt = veglenke.feltoversikt,
                        lengde = veglenke.geometri.lengde ?: 0.0,
                        konnektering = veglenke.konnektering,
                        superstedfesting = veglenke.superstedfesting?.let { stedfesting ->
                            Superstedfesting(
                                veglenksekvensId = stedfesting.id,
                                startposisjon = stedfesting.startposisjon,
                                sluttposisjon = stedfesting.sluttposisjon,
                                kjorefelt = stedfesting.kjorefelt,
                            )
                        },
                    )
                    // NOTE: We assume that no veglenker have sluttdato that are non-null but in the future
                }.filter { it.sluttdato == null }
                .sortedBy { it.startposisjon }
        }
    }
}
