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
    val rangeSize = 100_000L // Each worker processes a contiguous range of 100k IDs
    val maxEstimatedId = 2_000_000L // Conservative estimate for total veglenkesekvens IDs

    // Check if configuration has changed since last run
    val existingWorkerCount = KeyValue.getRangeWorkerCount()
    if (existingWorkerCount > 0 && existingWorkerCount != workerCount) {
        println("Worker count har endret seg fra $existingWorkerCount til $workerCount. Nullstiller backfill...")
        transaction {
            KeyValue.clearVeglenkesekvensSettings()
        }
        println("Backfill nullstilt på grunn av endret worker count.")
        KeyValue.put("veglenkesekvenser_backfill_started", Clock.System.now())
    }

    println("Starter parallel backfill med $workerCount workers og range-størrelse $rangeSize")

    coroutineScope {
        (0 until workerCount).forEach { workerIndex ->
            launch {
                runRangeBackfillWorker(workerIndex, workerCount, rangeSize, maxEstimatedId)
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

private suspend fun runRangeBackfillWorker(
    workerIndex: Int,
    workerCount: Int,
    rangeSize: Long,
    maxEstimatedId: Long,
) {
    println("Worker $workerIndex startet med range-basert backfill")
    var currentRangeIndex = workerIndex
    var totalProcessed = 0

    while (currentRangeIndex * rangeSize < maxEstimatedId) {
        val rangeStart = currentRangeIndex * rangeSize + 1
        val rangeEnd = minOf((currentRangeIndex + 1) * rangeSize, maxEstimatedId)

        // Skip if this range is already completed
        if (KeyValue.isRangeCompleted(currentRangeIndex)) {
            println("Worker $workerIndex: Range $currentRangeIndex (ID $rangeStart-$rangeEnd) allerede ferdig, hopper over")
            currentRangeIndex += workerCount
            continue
        }

        println("Worker $workerIndex: Behandler range $currentRangeIndex (ID $rangeStart-$rangeEnd)")

        try {
            val workerUpdates = mutableMapOf<Long, List<Veglenke>>()
            var batchSize = 0

            // Use the slutt parameter to get exactly the range we need
            val veglenkesekvenser =
                uberiketApi
                    .streamVeglenkesekvenser(
                        start = rangeStart,
                        slutt = rangeEnd,
                    ).toList()

            println("Worker $workerIndex: Hentet ${veglenkesekvenser.size} veglenkesekvenser fra range $currentRangeIndex")

            // Process all veglenkesekvenser in this range
            veglenkesekvenser.forEach { veglenkesekvens ->
                val domainVeglenker = convertToDomainVeglenker(veglenkesekvens)
                workerUpdates[veglenkesekvens.id] = domainVeglenker
                batchSize++

                // Flush in batches of 2000 for memory management
                if (batchSize >= 2000) {
                    veglenkerStore.batchUpdate(workerUpdates)
                    totalProcessed += batchSize
                    println("Worker $workerIndex: Behandlet $batchSize veglenkesekvenser, worker totalt: $totalProcessed")
                    workerUpdates.clear()
                    batchSize = 0
                }
            }

            // Final batch flush
            if (workerUpdates.isNotEmpty()) {
                veglenkerStore.batchUpdate(workerUpdates)
                totalProcessed += batchSize
                println("Worker $workerIndex: Behandlet siste $batchSize veglenkesekvenser, worker totalt: $totalProcessed")
            }

            // Mark this range as completed
            KeyValue.markRangeCompleted(currentRangeIndex)

            val currentTotalInDb = veglenkerStore.size()
            println("Worker $workerIndex: Fullførte range $currentRangeIndex. DB totalt: $currentTotalInDb")
        } catch (e: Exception) {
            println("Worker $workerIndex: Feil ved behandling av range $currentRangeIndex: ${e.message}")

            // For timeout/network errors, continue to next range instead of failing completely
            if (e.message?.contains("timeout", ignoreCase = true) == true ||
                e.message?.contains("connection", ignoreCase = true) == true ||
                e.javaClass.simpleName.contains("Timeout")
            ) {
                println("Worker $workerIndex: Nettverksfeil oppdaget. Hopper til neste range.")
            } else {
                println("Worker $workerIndex: Kritisk feil, avslutter worker")
                e.printStackTrace()
                throw e
            }
        }

        // Move to next range for this worker (skip workerCount ranges)
        currentRangeIndex += workerCount
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
