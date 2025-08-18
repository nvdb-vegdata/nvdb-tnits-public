package no.vegvesen.nvdb.tnits.vegobjekter

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import no.vegvesen.nvdb.apiles.model.Vegobjekt
import no.vegvesen.nvdb.tnits.database.KeyValue
import no.vegvesen.nvdb.tnits.database.Vegobjekter
import no.vegvesen.nvdb.tnits.extensions.get
import no.vegvesen.nvdb.tnits.extensions.put
import no.vegvesen.nvdb.tnits.extensions.putSync
import no.vegvesen.nvdb.tnits.uberiketApi
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import kotlin.time.Clock
import kotlin.time.Instant

suspend fun updateVegobjekter(typeId: Int) {
    var lastHendelseId =
        KeyValue.get<Long>("vegobjekter_${typeId}_last_hendelse_id") ?: uberiketApi.getLatestVegobjektHendelseId(
            typeId,
            KeyValue.get<Instant>("vegobjekter_${typeId}_backfill_completed")
                ?: error("Backfill for type $typeId er ikke ferdig"),
        )

    do {
        val response =
            uberiketApi.getVegobjektHendelser(
                typeId = typeId,
                start = lastHendelseId,
            )

        if (response.hendelser.isNotEmpty()) {
            lastHendelseId = response.hendelser.last().hendelseId
            val changedIds = response.hendelser.map { it.vegobjektId }.toSet()
            val vegobjekter = mutableListOf<Vegobjekt>()
            var start: Long? = null

            do {
                val batch = uberiketApi.streamVegobjekter(typeId = typeId, start = start).toList()

                if (batch.isNotEmpty()) {
                    vegobjekter.addAll(batch.filter { it.id in changedIds })
                    start = batch.last().id
                }
            } while (batch.isNotEmpty() && vegobjekter.size < changedIds.size)

            newSuspendedTransaction(Dispatchers.IO) {
                changedIds.forEach { vegobjektId ->
                    Vegobjekter.deleteWhere {
                        (Vegobjekter.vegobjektId eq vegobjektId) and (Vegobjekter.vegobjektType eq typeId)
                    }
                }
                insertVegobjekter(vegobjekter)
                // Keep progress update atomic within the same transaction
                KeyValue.putSync("vegobjekter_${typeId}_last_hendelse_id", lastHendelseId)
            }
            println("Behandlet ${response.hendelser.size} hendelser for type $typeId, siste ID: $lastHendelseId")
        }
    } while (response.hendelser.isNotEmpty())
    println("Oppdatering av vegobjekter type $typeId fullført. Siste hendelse-ID: $lastHendelseId")
}

suspend fun backfillVegobjekter(typeId: Int) {
    var lastId = KeyValue.get<Long>("vegobjekter_${typeId}_backfill_last_id")

    if (lastId == null) {
        println("Ingen backfill har blitt startet ennå for type $typeId. Starter backfill...")
        val now = Clock.System.now()
        KeyValue.put("vegobjekter_${typeId}_backfill_started", now)
    } else {
        println("Backfill pågår for type $typeId. Gjenopptar fra siste ID: $lastId")
    }

    var totalCount = 0

    do {
        val vegobjekter = uberiketApi.streamVegobjekter(typeId = typeId, start = lastId).toList()
        lastId = vegobjekter.lastOrNull()?.id

        if (vegobjekter.isEmpty()) {
            println("Ingen vegobjekter å sette inn for type $typeId, backfill fullført.")
            KeyValue.put("vegobjekter_${typeId}_backfill_completed", Clock.System.now())
        } else {
            newSuspendedTransaction(Dispatchers.IO) {
                insertVegobjekter(vegobjekter)
                // Keep progress update atomic within the same transaction
                KeyValue.putSync("vegobjekter_${typeId}_backfill_last_id", lastId!!)
            }
            totalCount += vegobjekter.size
            println("Satt inn ${vegobjekter.size} vegobjekter for type $typeId, totalt antall: $totalCount")
        }
    } while (vegobjekter.isNotEmpty())
}

private fun insertVegobjekter(vegobjekter: List<Vegobjekt>) {
    Vegobjekter.batchInsert(vegobjekter) { vegobjekt ->
        this[Vegobjekter.vegobjektId] = vegobjekt.id
        this[Vegobjekter.vegobjektVersjon] = vegobjekt.versjon
        this[Vegobjekter.vegobjektType] = vegobjekt.typeId
        this[Vegobjekter.data] = vegobjekt
        this[Vegobjekter.sistEndret] = Clock.System.now()
    }
}
