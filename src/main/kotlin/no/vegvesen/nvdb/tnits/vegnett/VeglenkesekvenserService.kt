package no.vegvesen.nvdb.tnits.vegnett

import kotlinx.coroutines.flow.toList
import kotlinx.datetime.toKotlinLocalDate
import no.vegvesen.nvdb.apiles.uberiket.Veglenkesekvens
import no.vegvesen.nvdb.tnits.extensions.forEachChunked
import no.vegvesen.nvdb.tnits.geometry.SRID
import no.vegvesen.nvdb.tnits.geometry.parseWkt
import no.vegvesen.nvdb.tnits.geometry.projectTo
import no.vegvesen.nvdb.tnits.measure
import no.vegvesen.nvdb.tnits.model.Superstedfesting
import no.vegvesen.nvdb.tnits.model.Veglenke
import no.vegvesen.nvdb.tnits.services.UberiketApi
import no.vegvesen.nvdb.tnits.storage.DirtyVeglenkesekvenserRepository
import no.vegvesen.nvdb.tnits.storage.KeyValueRocksDbStore
import no.vegvesen.nvdb.tnits.storage.VeglenkerRepository
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Clock
import kotlin.time.Instant

class VeglenkesekvenserService(
    private val keyValueStore: KeyValueRocksDbStore,
    private val uberiketApi: UberiketApi,
    private val veglenkerRepository: VeglenkerRepository,
    private val dirtyVeglenkesekvenserRepository: DirtyVeglenkesekvenserRepository,
) {

    suspend fun backfillVeglenkesekvenser() {
        val backfillCompleted = keyValueStore.get<Instant>("veglenkesekvenser_backfill_completed")

        if (backfillCompleted != null) {
            println("Backfill for veglenkesekvenser er allerede fullført den $backfillCompleted")
            return
        }

        var lastId = keyValueStore.get<Long>("veglenkesekvenser_backfill_last_id")

        if (lastId == null) {
            println("Ingen veglenkesekvenser backfill har blitt startet ennå. Starter backfill...")
            val now = Clock.System.now()
            keyValueStore.put("veglenkesekvenser_backfill_started", now)
        } else {
            println("Veglenkesekvenser backfill pågår. Gjenopptar fra siste ID: $lastId")
        }

        var totalCount = 0
        val updates = mutableMapOf<Long, List<Veglenke>>()

        do {
            val veglenkesekvenser = uberiketApi.streamVeglenkesekvenser(start = lastId).toList()
            lastId = veglenkesekvenser.lastOrNull()?.id

            if (veglenkesekvenser.isEmpty()) {
                println("Ingen veglenkesekvenser å sette inn, backfill fullført.")
                keyValueStore.put("veglenkesekvenser_backfill_completed", Clock.System.now())
            } else {
                measure("Behandler ${veglenkesekvenser.size} veglenkesekvenser") {
                    // Process veglenkesekvenser in batches for RocksDB storage
                    veglenkesekvenser.forEach { veglenkesekvens ->
                        val domainVeglenker = convertToDomainVeglenker(veglenkesekvens)
                        updates[veglenkesekvens.id] = domainVeglenker
                    }

                    // Batch update to RocksDB
                    veglenkerRepository.batchUpdate(updates)
                    updates.clear()

                    // Update progress in SQL (outside RocksDB transaction)
                    keyValueStore.put("veglenkesekvenser_backfill_last_id", lastId!!)
                }

                totalCount += veglenkesekvenser.size
                println("Behandlet ${veglenkesekvenser.size} veglenkesekvenser, totalt antall: $totalCount")
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

        do {
            val response =
                uberiketApi.getVeglenkesekvensHendelser(
                    start = lastHendelseId,
                )

            if (response.hendelser.isNotEmpty()) {
                lastHendelseId = response.hendelser.last().hendelseId
                val changedIds = response.hendelser.map { it.nettelementId }.toSet()
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
                                val domainVeglenker = convertToDomainVeglenker(veglenkesekvens)
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

                // Apply all updates to RocksDB and mark dirty records in SQL
                veglenkerRepository.batchUpdate(updates)

                transaction {
                    dirtyVeglenkesekvenserRepository.publishChangedVeglenkesekvensIds(changedIds)
                    keyValueStore.put("veglenkesekvenser_last_hendelse_id", lastHendelseId)
                }

                println("Behandlet ${response.hendelser.size} hendelser, siste ID: $lastHendelseId")
                hendelseCount += response.hendelser.size
            }
        } while (response.hendelser.isNotEmpty())
        println("Oppdatering av veglenkesekvenser fullført. Siste hendelse-ID: $lastHendelseId")

        return hendelseCount
    }

    companion object {

        /**
         * Konverterer en [Veglenkesekvens] fra Uberiket API til en liste av domenemodellen [Veglenke].
         * - Filtrer til bare aktive veglenker.
         * - Mapper start- og sluttporter til posisjoner og noder.
         * - Sorterer veglenkene etter startposisjon.
         */
        fun convertToDomainVeglenker(veglenkesekvens: Veglenkesekvens): List<Veglenke> {
            val portLookup = veglenkesekvens.porter.associateBy { it.nummer }

            return veglenkesekvens.veglenker
                .map { veglenke ->

                    val startport =
                        portLookup[veglenke.startport]
                            ?: error("Startport ${veglenke.startport} not found in veglenkesekvens ${veglenkesekvens.id}")
                    val sluttport =
                        portLookup[veglenke.sluttport]
                            ?: error("Sluttport ${veglenke.sluttport} not found in veglenkesekvens ${veglenkesekvens.id}")

                    Veglenke(
                        veglenkesekvensId = veglenkesekvens.id,
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
