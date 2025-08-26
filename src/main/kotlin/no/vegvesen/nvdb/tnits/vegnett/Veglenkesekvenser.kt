package no.vegvesen.nvdb.tnits.vegnett

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import no.vegvesen.nvdb.apiles.uberiket.Veglenkesekvens
import no.vegvesen.nvdb.tnits.database.KeyValue
import no.vegvesen.nvdb.tnits.extensions.forEachChunked
import no.vegvesen.nvdb.tnits.extensions.get
import no.vegvesen.nvdb.tnits.extensions.put
import no.vegvesen.nvdb.tnits.extensions.putSync
import no.vegvesen.nvdb.tnits.geometry.SRID
import no.vegvesen.nvdb.tnits.geometry.parseWkt
import no.vegvesen.nvdb.tnits.model.Superstedfesting
import no.vegvesen.nvdb.tnits.model.Veglenke
import no.vegvesen.nvdb.tnits.uberiketApi
import no.vegvesen.nvdb.tnits.veglenkerStore
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import kotlin.time.Clock

suspend fun backfillVeglenkesekvenser() {
    var lastId = KeyValue.get<Long>("veglenkesekvenser_backfill_last_id")

    if (lastId == null) {
        println("Ingen veglenkesekvenser backfill har blitt startet ennå. Starter backfill...")
        val now = Clock.System.now()
        KeyValue.put("veglenkesekvenser_backfill_started", now)
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
            newSuspendedTransaction(Dispatchers.IO) {
                KeyValue.putSync("veglenkesekvenser_backfill_completed", Clock.System.now())
            }
        } else {
            // Process veglenkesekvenser in batches for RocksDB storage
            veglenkesekvenser.forEachChunked(100) { batch ->
                batch.forEach { veglenkesekvens ->
                    val domainVeglenker = convertToDomainVeglenker(veglenkesekvens)
                    updates[veglenkesekvens.id] = domainVeglenker
                }

                // Batch update to RocksDB
                veglenkerStore.batchUpdate(updates)
                updates.clear()

                // Update progress in SQL (outside RocksDB transaction)
                newSuspendedTransaction(Dispatchers.IO) {
                    KeyValue.putSync("veglenkesekvenser_backfill_last_id", lastId!!)
                }
            }

            totalCount += veglenkesekvenser.size
            println("Behandlet ${veglenkesekvenser.size} veglenkesekvenser, totalt antall: $totalCount")
        }
    } while (veglenkesekvenser.isNotEmpty())
}

private fun convertToDomainVeglenker(veglenkesekvens: Veglenkesekvens): List<Veglenke> =
    veglenkesekvens.veglenker.map { veglenke ->

        val startPosition = resolvePortPosition(veglenkesekvens, veglenke.startport)
        val endPosition = resolvePortPosition(veglenkesekvens, veglenke.sluttport)

        Veglenke(
            veglenkesekvensId = veglenkesekvens.id,
            veglenkenummer = veglenke.nummer,
            startposisjon = startPosition,
            sluttposisjon = endPosition,
            geometri = parseWkt(veglenke.geometri.wkt, SRID.UTM33),
            typeVeg = veglenke.typeVeg,
            detaljniva = veglenke.detaljniva,
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
    }

suspend fun updateVeglenkesekvenser() {
    var lastHendelseId =
        KeyValue.get<Long>("veglenkesekvenser_last_hendelse_id") ?: uberiketApi.getLatestHendelseId(
            KeyValue.get<kotlin.time.Instant>("veglenkesekvenser_backfill_completed") ?: error("Veglenkesekvenser backfill er ikke ferdig"),
        )

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
                            .streamVeglenkesekvenser(start = start)
                            .toList()
                            .filter { it.id in chunk }

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
            veglenkerStore.batchUpdate(updates)

            newSuspendedTransaction(Dispatchers.IO) {
                publishChangedVeglenkesekvensIds(changedIds)
                KeyValue.putSync("veglenkesekvenser_last_hendelse_id", lastHendelseId)
            }

            println("Behandlet ${response.hendelser.size} hendelser, siste ID: $lastHendelseId")
        }
    } while (response.hendelser.isNotEmpty())
    println("Oppdatering av veglenkesekvenser fullført. Siste hendelse-ID: $lastHendelseId")
}

private fun resolvePortPosition(
    veglenkesekvens: Veglenkesekvens,
    portNumber: Int,
): Double =
    veglenkesekvens.porter
        .find { it.nummer == portNumber }
        ?.posisjon
        ?: error("Port $portNumber not found in veglenkesekvens ${veglenkesekvens.id}")
