package no.vegvesen.nvdb.tnits

import kotlinx.coroutines.flow.toList
import no.vegvesen.nvdb.apiles.model.VeglenkeMedId
import no.vegvesen.nvdb.tnits.config.configureDatabase
import no.vegvesen.nvdb.tnits.config.loadConfig
import no.vegvesen.nvdb.tnits.database.KeyValue
import no.vegvesen.nvdb.tnits.database.Veglenker
import no.vegvesen.nvdb.tnits.extensions.get
import no.vegvesen.nvdb.tnits.extensions.put
import no.vegvesen.nvdb.tnits.model.VeglenkeId
import no.vegvesen.nvdb.tnits.model.veglenkeId
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.inList
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Clock
import kotlin.time.Instant

suspend fun main() {
    println("Starting NVDB TN-ITS Console Application...")
    val config = loadConfig()
    configureDatabase(config)
    println("Application initialized successfully!")

    /*
    Fetch veglenker:
    - States:
    1. No backfill
    2. Backfill in progress
    3. Backfill complete - no incremental loads
    4. Backfill complete - previous incremental load

    Mode: Backfill/Live
    Can be derived from "Backfill completed" timestamp
     */

    val veglenkerBackfillCompleted = KeyValue.get<Instant>("veglenker_backfill_completed")

    if (veglenkerBackfillCompleted == null) {
        startOrResumeBackfill()
    } else {
        // Live mode
        println("Backfill already completed at $veglenkerBackfillCompleted. Running in live mode.")

        var lastHendelseId =
            KeyValue.get<Long>("veglenker_last_hendelse_id") ?: uberiketApi.getLatestHendelseId(
                veglenkerBackfillCompleted,
            )

        do {
            val response =
                uberiketApi.getVeglenkesekvensHendelser(
                    start = lastHendelseId,
                )

            if (response.hendelser.isNotEmpty()) {
                lastHendelseId = response.hendelser.last().hendelseId
                val changedIds = response.hendelser.map { it.nettelementId }.toSet()
                val veglenker = mutableListOf<VeglenkeMedId>()
                var start: VeglenkeId? = null

                do {
                    val batch =
                        uberiketApi
                            .streamVeglenker(
                                start = start,
                                ider = changedIds,
                            ).toList()

                    if (batch.isNotEmpty()) {
                        veglenker.addAll(batch)
                        start = batch.last().veglenkeId
                    }
                } while (batch.isNotEmpty())
                transaction {
                    Veglenker.deleteWhere { Veglenker.veglenkesekvensId inList changedIds }
                    insertVeglenker(veglenker)
                    KeyValue.put("veglenker_last_hendelse_id", lastHendelseId)
                }
            }
            println("Processed ${response.hendelser.size} hendelser, last ID: $lastHendelseId")
        } while (response.hendelser.isNotEmpty())
    }
}

private suspend fun startOrResumeBackfill() {
    var lastId = KeyValue.get<VeglenkeId>("veglenker_backfill_last_id")

    if (lastId == null) {
        println("No backfill started yet. Starting backfill...")
        val now = Clock.System.now()
        KeyValue.put("veglenker_backfill_started", now)
    } else {
        println("Backfill in progress. Resuming from last ID: $lastId")
    }

    var totalCount = 0

    do {
        val veglenker =
            uberiketApi
                .streamVeglenker(
                    start = lastId,
                ).toList()
        lastId = veglenker.lastOrNull()?.veglenkeId

        transaction {
            if (veglenker.isEmpty()) {
                println("No veglenker to insert, backfill complete.")
                KeyValue.put("veglenker_backfill_completed", Clock.System.now())
            } else {
                insertVeglenker(veglenker)
                KeyValue.put<VeglenkeId>("veglenker_backfill_last_id", lastId!!)
            }

            totalCount += veglenker.size
            println("Inserted ${veglenker.size} veglenker, total count: $totalCount")
        }
    } while (veglenker.isNotEmpty())
}

private fun insertVeglenker(veglenker: List<VeglenkeMedId>) {
    Veglenker.batchInsert(veglenker) { veglenke ->
        this[Veglenker.veglenkesekvensId] = veglenke.veglenkesekvensId
        this[Veglenker.veglenkenummer] = veglenke.veglenkenummer
        this[Veglenker.data] = veglenke
        this[Veglenker.sistEndret] = Clock.System.now()
    }
}
