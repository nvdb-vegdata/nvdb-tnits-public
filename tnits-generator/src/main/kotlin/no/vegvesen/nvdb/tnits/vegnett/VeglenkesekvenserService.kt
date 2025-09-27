package no.vegvesen.nvdb.tnits.vegnett

import kotlinx.coroutines.flow.chunked
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.toKotlinLocalDate
import no.vegvesen.nvdb.apiles.uberiket.Veglenkesekvens
import no.vegvesen.nvdb.tnits.extensions.forEachChunked
import no.vegvesen.nvdb.tnits.gateways.UberiketApi
import no.vegvesen.nvdb.tnits.geometry.SRID
import no.vegvesen.nvdb.tnits.geometry.parseWkt
import no.vegvesen.nvdb.tnits.geometry.projectTo
import no.vegvesen.nvdb.tnits.model.Superstedfesting
import no.vegvesen.nvdb.tnits.model.Veglenke
import no.vegvesen.nvdb.tnits.storage.KeyValueRocksDbStore
import no.vegvesen.nvdb.tnits.storage.RocksDbContext
import no.vegvesen.nvdb.tnits.storage.VeglenkerRepository
import no.vegvesen.nvdb.tnits.utilities.WithLogger
import kotlin.time.Clock
import kotlin.time.Instant

class VeglenkesekvenserService(
    private val keyValueStore: KeyValueRocksDbStore,
    private val uberiketApi: UberiketApi,
    private val veglenkerRepository: VeglenkerRepository,
    private val rocksDbContext: RocksDbContext,
) : WithLogger {

    suspend fun backfillVeglenkesekvenser() {
        val backfillCompleted = keyValueStore.get<Instant>("veglenkesekvenser_backfill_completed")

        if (backfillCompleted != null) {
            log.info("Backfill for veglenkesekvenser er allerede fullført den $backfillCompleted")
            return
        }

        var lastId = keyValueStore.get<Long>("veglenkesekvenser_backfill_last_id")

        if (lastId == null) {
            log.info("Ingen veglenkesekvenser backfill har blitt startet ennå. Starter backfill...")
            val now = Clock.System.now()
            keyValueStore.put("veglenkesekvenser_backfill_started", now)
        } else {
            log.info("Veglenkesekvenser backfill pågår. Gjenopptar fra siste ID: $lastId")
        }

        var totalCount = 0

        do {
            val veglenkesekvenser = uberiketApi.streamVeglenkesekvenser(start = lastId).toList()
            lastId = veglenkesekvenser.lastOrNull()?.id

            if (veglenkesekvenser.isEmpty()) {
                log.info("Ingen veglenkesekvenser å sette inn, backfill fullført.")
                keyValueStore.put("veglenkesekvenser_backfill_completed", Clock.System.now())
            } else {
                val updates = veglenkesekvenser.associate {
                    val domainVeglenker = it.convertToDomainVeglenker()
                    it.id to domainVeglenker
                }

                rocksDbContext.writeBatch {
                    // Batch update to RocksDB
                    veglenkerRepository.batchInsert(updates)

                    // Update progress in SQL (outside RocksDB transaction)
                    keyValueStore.put("veglenkesekvenser_backfill_last_id", lastId!!)
                }

                totalCount += veglenkesekvenser.size
                log.info("Behandlet ${veglenkesekvenser.size} veglenkesekvenser, totalt antall: $totalCount")
            }
        } while (veglenkesekvenser.isNotEmpty())
    }

    suspend fun updateVeglenkesekvenser(): Int {
        var lastHendelseId =
            keyValueStore.get<Long>("veglenkesekvenser_last_hendelse_id") ?: uberiketApi.getLatestVeglenkesekvensHendelseId(
                keyValueStore.get<Instant>("veglenkesekvenser_backfill_completed")
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
                    keyValueStore.put("veglenkesekvenser_last_hendelse_id", lastHendelseId)
                }
                log.info("Behandlet ${hendelser.size} hendelser, siste ID: $lastHendelseId")
                hendelseCount += hendelser.size
            }

        log.info("Oppdatering av veglenkesekvenser fullført. Siste hendelse-ID: $lastHendelseId")

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
         * Konverterer en [Veglenkesekvens] fra Uberiket API til en liste av domenemodellen [Veglenke].
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

                    Veglenke(
                        veglenkesekvensId = this.id,
                        veglenkenummer = veglenke.nummer,
                        startposisjon = startport.posisjon,
                        sluttposisjon = sluttport.posisjon,
                        startnode = startport.nodeId,
                        sluttnode = sluttport.nodeId,
                        startdato = veglenke.gyldighetsperiode.startdato.toKotlinLocalDate(),
                        sluttdato = veglenke.gyldighetsperiode.sluttdato?.toKotlinLocalDate(),
                        geometri =
                        parseWkt(
                            veglenke.geometri.wkt,
                            veglenke.geometri.srid.value
                                .toInt(),
                        ).projectTo(SRID.WGS84),
                        typeVeg = veglenke.typeVeg,
                        detaljniva = veglenke.detaljniva,
                        feltoversikt = veglenke.feltoversikt,
                        lengde = veglenke.geometri.lengde ?: 0.0,
                        konnektering = veglenke.konnektering,
                        superstedfesting =
                        veglenke.superstedfesting?.let { stedfesting ->
                            Superstedfesting(
                                veglenksekvensId = stedfesting.id,
                                startposisjon = stedfesting.startposisjon,
                                sluttposisjon = stedfesting.sluttposisjon,
                                kjorefelt = stedfesting.kjorefelt,
                            )
                        },
                    )
                }.filter { it.sluttdato == null }
                .sortedBy { it.startposisjon }
        }
    }
}
