package no.vegvesen.nvdb.tnits.vegnett

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import no.vegvesen.nvdb.apiles.uberiket.Veglenkesekvens
import no.vegvesen.nvdb.tnits.database.KeyValue
import no.vegvesen.nvdb.tnits.extensions.*
import no.vegvesen.nvdb.tnits.geometry.SRID
import no.vegvesen.nvdb.tnits.geometry.parseWkt
import no.vegvesen.nvdb.tnits.model.Superstedfesting
import no.vegvesen.nvdb.tnits.model.Veglenke
import no.vegvesen.nvdb.tnits.uberiketApi
import no.vegvesen.nvdb.tnits.veglenkerStore
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Clock
import kotlin.time.Instant

suspend fun backfillVeglenkesekvenser() {
    val veglenkesekvenserBackfillCompleted = KeyValue.get<Instant>("veglenkesekvenser_backfill_completed")

    if (veglenkesekvenserBackfillCompleted != null) {
        println("Veglenkesekvenser backfill er allerede fullført den $veglenkesekvenserBackfillCompleted. Hopper over backfill.")
        return
    }

    val started = KeyValue.get<Instant>("veglenkesekvenser_backfill_started")

    if (started == null) {
        println("Ingen veglenkesekvenser backfill har blitt startet ennå. Starter backfill...")
        KeyValue.put("veglenkesekvenser_backfill_started", Clock.System.now())
    } else {
        println("Veglenkesekvenser backfill pågår. Gjenopptar...")
    }

    val workerCount = 4
    val partitionSize = 10_000L // Larger partitions to handle ID gaps better

    // Check if worker count has changed since last run
    val existingWorkerCount = KeyValue.getWorkerLastIdCount()
    if (existingWorkerCount > 0 && existingWorkerCount != workerCount) {
        println("Worker count har endret seg fra $existingWorkerCount til $workerCount. Nullstiller backfill...")
        transaction {
            KeyValue.clearVeglenkesekvensSettings()
        }
        println("Backfill nullstilt på grunn av endret worker count.")
        KeyValue.put("veglenkesekvenser_backfill_started", Clock.System.now())
    }

    println("Starter parallel backfill med $workerCount workers og partitionsstørrelse $partitionSize (fetcher 1000 om gangen)")

    coroutineScope {
        (0 until workerCount).forEach { workerIndex ->
            launch {
                runBackfillWorker(workerIndex, workerCount, partitionSize)
            }
        }
    }

    val finalTotalCount = veglenkerStore.size()
    println("Alle workers er ferdig. Markerer backfill som fullført...")
    transaction {
        KeyValue.put("veglenkesekvenser_backfill_completed", Clock.System.now())
    }
    println("Veglenkesekvenser backfill fullført! Total veglenkesekvenser i database: $finalTotalCount")
}

private fun flushWorkerBatch(
    workerIndex: Int,
    workerUpdates: MutableMap<Long, List<Veglenke>>,
    batchSize: Int,
    totalProcessed: Int,
    currentId: Long,
    flushReason: String,
): Int {
    if (workerUpdates.isEmpty()) return totalProcessed

    veglenkerStore.batchUpdate(workerUpdates)
    val newTotalProcessed = totalProcessed + batchSize
    val currentTotalInDb = veglenkerStore.size()
    println(
        "Worker $workerIndex: Behandlet $batchSize veglenkesekvenser ($flushReason), worker totalt: $newTotalProcessed, DB totalt: $currentTotalInDb",
    )

    // Update progress
    transaction {
        KeyValue.put("veglenkesekvenser_backfill_last_id_$workerIndex", currentId - 1)
    }

    workerUpdates.clear()
    return newTotalProcessed
}

private suspend fun runBackfillWorker(
    workerIndex: Int,
    workerCount: Int,
    partitionSize: Long,
) {
    println("Worker $workerIndex startet")
    var totalProcessed = 0
    var currentPartition = workerIndex

    var done = false

    while (!done) {
        val partitionStart = currentPartition * partitionSize + 1
        val partitionEnd = (currentPartition + 1) * partitionSize

        // Get last processed ID for this worker, defaulting to partition start
        val lastId =
            KeyValue.get<Long>("veglenkesekvenser_backfill_last_id_$workerIndex")
                ?: partitionStart

        // Skip to next partition if we've already processed this one
        if (lastId > partitionEnd) {
            currentPartition += workerCount
            continue
        }

        // Each worker needs its own update map to avoid concurrency issues
        val workerUpdates = mutableMapOf<Long, List<Veglenke>>()
        var batchSize = 0
        var currentId = maxOf(lastId, partitionStart)

        try {
            println(
                "Worker $workerIndex: Behandler partisjon $currentPartition (ID $partitionStart-$partitionEnd), starter fra ID $currentId",
            )

            do {
                // Fetch up to 1000 records at a time, but process all that fall within partition
                val veglenkesekvenser =
                    try {
                        uberiketApi.streamVeglenkesekvenser(start = currentId).toList()
                    } catch (e: Exception) {
                        println("Worker $workerIndex: API-feil ved lasting av data fra ID $currentId: ${e.message}")
                        throw e // Let HttpRequestRetry plugin and outer catch handle it
                    }

                if (veglenkesekvenser.isEmpty()) {
                    println("Worker $workerIndex: Ingen flere veglenkesekvenser i partisjon $currentPartition")
                    done = true
                    break
                }

                // Filter to only process veglenkesekvenser within current partition
                val partitionVeglenkesekvenser = veglenkesekvenser.filter { it.id <= partitionEnd }

                if (partitionVeglenkesekvenser.isEmpty()) {
                    println("Worker $workerIndex: Passerte slutten av partisjon $currentPartition")
                    // Force flush any accumulated data before breaking
                    totalProcessed =
                        flushWorkerBatch(
                            workerIndex,
                            workerUpdates,
                            batchSize,
                            totalProcessed,
                            currentId,
                            "partition-end-flush",
                        )
                    batchSize = 0
                    break
                }

                // Process the batch
                partitionVeglenkesekvenser.forEach { veglenkesekvens ->
                    val domainVeglenker = convertToDomainVeglenker(veglenkesekvens)
                    workerUpdates[veglenkesekvens.id] = domainVeglenker
                    batchSize++
                }

                // Update currentId to the last processed ID + 1
                currentId = partitionVeglenkesekvenser.maxOf { it.id } + 1

                // Flush batch when we reach 2000 records or partition end
                if (batchSize >= 2000 || currentId > partitionEnd) {
                    val flushReason = if (batchSize >= 2000) "2k-batch" else "partition-end"
                    totalProcessed =
                        flushWorkerBatch(workerIndex, workerUpdates, batchSize, totalProcessed, currentId, flushReason)
                    batchSize = 0
                }
            } while (currentId <= partitionEnd)

            // Final flush for any remaining data in this partition
            totalProcessed =
                flushWorkerBatch(
                    workerIndex,
                    workerUpdates,
                    batchSize,
                    totalProcessed,
                    currentId,
                    "final-partition-flush",
                )
            batchSize = 0
        } catch (e: Exception) {
            println("Worker $workerIndex: Feil ved behandling av partisjon $currentPartition: ${e.message}")

            // Save progress before potentially retrying
            if (workerUpdates.isNotEmpty()) {
                try {
                    totalProcessed =
                        flushWorkerBatch(workerIndex, workerUpdates, batchSize, totalProcessed, currentId, "error-save")
                    // batchSize reset not needed here since we're potentially exiting or continuing to next partition
                } catch (saveError: Exception) {
                    println("Worker $workerIndex: ADVARSEL - Kunne ikke lagre fremgang: ${saveError.message}")
                }
            }

            // For timeout/network errors, continue to next partition instead of failing completely
            if (e.message?.contains("timeout", ignoreCase = true) == true ||
                e.message?.contains("connection", ignoreCase = true) == true ||
                e.javaClass.simpleName.contains("Timeout")
            ) {
                println("Worker $workerIndex: Nettverksfeil oppdaget. Hopper til neste partisjon.")
                // Don't rethrow - continue to next partition
            } else {
                println("Worker $workerIndex: Kritisk feil, avslutter worker")
                e.printStackTrace()
                throw e
            }
        }

        // Move to next partition for this worker (skip workerCount partitions)
        currentPartition += workerCount
        val totalKeysInDb = veglenkerStore.size()
        println(
            "Worker $workerIndex: Fullførte partisjon ${currentPartition - workerCount}, hopper til partisjon $currentPartition. Total veglenkesekvenser i DB: $totalKeysInDb",
        )

        // If we've moved beyond reasonable partition range, check if there's more data
        if (currentPartition > 100000) { // Reasonable upper limit to avoid infinite loops
            try {
                val testVeglenkesekvenser =
                    uberiketApi.streamVeglenkesekvenser(start = currentPartition * partitionSize + 1).toList()
                if (testVeglenkesekvenser.isEmpty()) {
                    println("Worker $workerIndex: Ingen flere veglenkesekvenser funnet, avslutter")
                    break
                }
            } catch (e: Exception) {
                println("Worker $workerIndex: Kunne ikke teste for flere data: ${e.message}. Avslutter.")
                break
            }
        }
    }

    val finalTotalKeys = veglenkerStore.size()
    println("Worker $workerIndex ferdig. Behandlet: $totalProcessed veglenkesekvenser. Total i DB: $finalTotalKeys")
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
            KeyValue.get<Instant>("veglenkesekvenser_backfill_completed")
                ?: error("Veglenkesekvenser backfill er ikke ferdig"),
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
            veglenkerStore.batchUpdate(updates)

            transaction {
                publishChangedVeglenkesekvensIds(changedIds)
                KeyValue.put("veglenkesekvenser_last_hendelse_id", lastHendelseId)
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
