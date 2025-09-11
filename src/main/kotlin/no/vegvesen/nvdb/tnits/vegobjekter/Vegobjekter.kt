package no.vegvesen.nvdb.tnits.vegobjekter

import kotlinx.coroutines.flow.toList
import no.vegvesen.nvdb.apiles.uberiket.Retning
import no.vegvesen.nvdb.apiles.uberiket.Sideposisjon
import no.vegvesen.nvdb.apiles.uberiket.StedfestingLinjer
import no.vegvesen.nvdb.apiles.uberiket.Vegobjekt
import no.vegvesen.nvdb.tnits.database.KeyValue
import no.vegvesen.nvdb.tnits.database.Stedfestinger
import no.vegvesen.nvdb.tnits.database.Vegobjekter
import no.vegvesen.nvdb.tnits.extensions.forEachChunked
import no.vegvesen.nvdb.tnits.extensions.get
import no.vegvesen.nvdb.tnits.extensions.nowOffsetDateTime
import no.vegvesen.nvdb.tnits.extensions.put
import no.vegvesen.nvdb.tnits.model.VegobjektTyper
import no.vegvesen.nvdb.tnits.uberiketApi
import no.vegvesen.nvdb.tnits.vegnett.publishChangedVeglenkesekvensIds
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.inList
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Clock
import kotlin.time.Instant

private val dirtyCheckForTypes =
    setOf(
        VegobjektTyper.FUNKSJONELL_VEGKLASSE,
    )

suspend fun updateVegobjekter(typeId: Int): Int {
    var lastHendelseId =
        KeyValue.get<Long>("vegobjekter_${typeId}_last_hendelse_id") ?: uberiketApi.getLatestVegobjektHendelseId(
            typeId,
            KeyValue.get<Instant>("vegobjekter_${typeId}_backfill_completed")
                ?: error("Backfill for type $typeId er ikke ferdig"),
        )

    var hendelseCount = 0

    println("Starter oppdatering av vegobjekter for type $typeId, siste hendelse-ID: $lastHendelseId")

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

            changedIds.forEachChunked(100) { chunk ->
                var start: Long? = null
                do {
                    val batch = uberiketApi.streamVegobjekter(typeId = typeId, start = start, ider = chunk).toList()

                    if (batch.isNotEmpty()) {
                        vegobjekter.addAll(batch.filter { it.id in changedIds })
                        start = batch.last().id
                    }
                } while (batch.isNotEmpty())
            }

            transaction {
                if (typeId in dirtyCheckForTypes) {
                    val existingStedfestedeVeglenkesekvensIds =
                        Stedfestinger
                            .select(Stedfestinger.veglenkesekvensId)
                            .where { Stedfestinger.vegobjektId inList changedIds }
                            .withDistinct(true)
                            .map { it[Stedfestinger.veglenkesekvensId] }
                            .toSet()
                    val updatedStedfestedeVeglenkesekvensIds =
                        vegobjekter
                            .flatMap { it.getStedfestingLinjer() }
                            .map { it.veglenkesekvensId }
                            .toSet()
                    publishChangedVeglenkesekvensIds(existingStedfestedeVeglenkesekvensIds + updatedStedfestedeVeglenkesekvensIds)
                }
                Vegobjekter.deleteWhere {
                    Vegobjekter.vegobjektId inList changedIds
                }
                insertVegobjekter(vegobjekter)
                KeyValue.put("vegobjekter_${typeId}_last_hendelse_id", lastHendelseId)
            }
            println("Behandlet ${response.hendelser.size} hendelser for type $typeId, siste ID: $lastHendelseId")
            hendelseCount += response.hendelser.size
        }
    } while (response.hendelser.isNotEmpty())
    println("Oppdatering av vegobjekter type $typeId fullført. Siste hendelse-ID: $lastHendelseId")
    return hendelseCount
}

suspend fun backfillVegobjekter(typeId: Int) {
    val backfillCompleted = KeyValue.get<Instant>("vegobjekter_${typeId}_backfill_completed")

    if (backfillCompleted != null) {
        println("Backfill for type $typeId er allerede fullført den $backfillCompleted")
        return
    }

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
            transaction {
                insertVegobjekter(vegobjekter)
                // Keep progress update atomic within the same transaction
                KeyValue.put("vegobjekter_${typeId}_backfill_last_id", lastId!!)
            }
            totalCount += vegobjekter.size
            println("Satt inn ${vegobjekter.size} vegobjekter for type $typeId, totalt antall: $totalCount")
        }
    } while (vegobjekter.isNotEmpty())
}

private fun insertVegobjekter(vegobjekter: List<Vegobjekt>) {
    Vegobjekter.batchInsert(vegobjekter, shouldReturnGeneratedValues = false) { vegobjekt ->
        this[Vegobjekter.vegobjektId] = vegobjekt.id
        this[Vegobjekter.vegobjektType] = vegobjekt.typeId
        this[Vegobjekter.data] = vegobjekt
        this[Vegobjekter.sistEndret] = Clock.System.nowOffsetDateTime()
    }
    val stedfestinger =
        vegobjekter.flatMap { vegobjekt ->
            vegobjekt.getStedfestingLinjer()
        }
    Stedfestinger.batchInsert(stedfestinger) { stedfesting ->
        this[Stedfestinger.vegobjektId] = stedfesting.vegobjektId
        this[Stedfestinger.vegobjektType] = stedfesting.vegobjektType
        this[Stedfestinger.veglenkesekvensId] = stedfesting.veglenkesekvensId
        this[Stedfestinger.startposisjon] = stedfesting.startposisjon
        this[Stedfestinger.sluttposisjon] = stedfesting.sluttposisjon
        this[Stedfestinger.retning] = stedfesting.retning
        this[Stedfestinger.sideposisjon] = stedfesting.sideposisjon
        this[Stedfestinger.kjorefelt] = stedfesting.kjorefelt
    }
}

fun Vegobjekt.getStedfestingLinjer(): List<VegobjektStedfesting> = when (val stedfesting = this.stedfesting) {
    is StedfestingLinjer ->
        stedfesting.linjer.map {
            VegobjektStedfesting(
                veglenkesekvensId = it.id,
                startposisjon = it.startposisjon,
                sluttposisjon = it.sluttposisjon,
                retning = it.retning,
                sideposisjon = it.sideposisjon,
                kjorefelt = it.kjorefelt,
                vegobjektId = this.id,
                vegobjektType = this.typeId,
            )
        }

    else -> error("Forventet StedfestingLinjer, fikk ${stedfesting::class.simpleName}")
}

data class VegobjektStedfesting(
    val vegobjektId: Long,
    val vegobjektType: Int,
    val veglenkesekvensId: Long,
    val startposisjon: Double,
    val sluttposisjon: Double,
    val retning: Retning? = null,
    val sideposisjon: Sideposisjon? = null,
    val kjorefelt: List<String> = emptyList(),
)

interface Stedfesting
