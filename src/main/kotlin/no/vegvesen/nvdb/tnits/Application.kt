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
    println("Starter NVDB TN-ITS konsollapplikasjon...")
    val config = loadConfig()
    configureDatabase(config)
    println("Applikasjon initialisert vellykket!")

    val veglenkerBackfillCompleted = KeyValue.get<Instant>("veglenker_backfill_completed")

    if (veglenkerBackfillCompleted == null) {
        backfillVeglenker()
    }

    updateVeglenker()
}

private suspend fun updateVeglenker() {
    var lastHendelseId =
        KeyValue.get<Long>("veglenker_last_hendelse_id") ?: uberiketApi.getLatestHendelseId(
            KeyValue.get<Instant>("veglenker_backfill_completed") ?: error("Backfill er ikke ferdig"),
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
                val batch = uberiketApi.streamVeglenker(start = start, ider = changedIds).toList()

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
        println("Behandlet ${response.hendelser.size} hendelser, siste ID: $lastHendelseId")
    } while (response.hendelser.isNotEmpty())
}

private suspend fun backfillVeglenker() {
    var lastId = KeyValue.get<VeglenkeId>("veglenker_backfill_last_id")

    if (lastId == null) {
        println("Ingen backfill har blitt startet ennå. Starter backfill...")
        val now = Clock.System.now()
        KeyValue.put("veglenker_backfill_started", now)
    } else {
        println("Backfill pågår. Gjenopptar fra siste ID: $lastId")
    }

    var totalCount = 0

    do {
        val veglenker = uberiketApi.streamVeglenker(start = lastId).toList()
        lastId = veglenker.lastOrNull()?.veglenkeId

        transaction {
            if (veglenker.isEmpty()) {
                println("Ingen veglenker å sette inn, backfill fullført.")
                KeyValue.put("veglenker_backfill_completed", Clock.System.now())
            } else {
                insertVeglenker(veglenker)
                KeyValue.put<VeglenkeId>("veglenker_backfill_last_id", lastId!!)
            }

            totalCount += veglenker.size
            println("Satt inn ${veglenker.size} veglenker, totalt antall: $totalCount")
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
