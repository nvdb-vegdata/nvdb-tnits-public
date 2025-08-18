package no.vegvesen.nvdb.tnits.vegnett

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import no.vegvesen.nvdb.apiles.model.VeglenkeMedId
import no.vegvesen.nvdb.tnits.database.KeyValue
import no.vegvesen.nvdb.tnits.database.Veglenker
import no.vegvesen.nvdb.tnits.extensions.get
import no.vegvesen.nvdb.tnits.extensions.put
import no.vegvesen.nvdb.tnits.extensions.putSync
import no.vegvesen.nvdb.tnits.model.VeglenkeId
import no.vegvesen.nvdb.tnits.model.veglenkeId
import no.vegvesen.nvdb.tnits.uberiketApi
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.inList
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import kotlin.time.Clock
import kotlin.time.Instant

suspend fun updateVeglenker() {
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

            newSuspendedTransaction(Dispatchers.IO) {
                Veglenker.deleteWhere { Veglenker.veglenkesekvensId inList changedIds }
                insertVeglenker(veglenker)
                // Keep progress update atomic within the same transaction
                KeyValue.putSync("veglenker_last_hendelse_id", lastHendelseId)
            }
            println("Behandlet ${response.hendelser.size} hendelser, siste ID: $lastHendelseId")
        }
    } while (response.hendelser.isNotEmpty())
    println("Oppdatering av veglenker fullført. Siste hendelse-ID: $lastHendelseId")
}

suspend fun backfillVeglenker() {
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

        if (veglenker.isEmpty()) {
            println("Ingen veglenker å sette inn, backfill fullført.")
            KeyValue.put("veglenker_backfill_completed", Clock.System.now())
        } else {
            newSuspendedTransaction(Dispatchers.IO) {
                insertVeglenker(veglenker)
                // Keep progress update atomic within the same transaction
                KeyValue.putSync("veglenker_backfill_last_id", lastId!!)
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
