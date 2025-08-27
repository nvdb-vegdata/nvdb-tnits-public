package no.vegvesen.nvdb.tnits.vegnett

import kotlinx.coroutines.flow.toList
import kotlinx.datetime.toKotlinLocalDate
import no.vegvesen.nvdb.apiles.uberiket.VeglenkeMedId
import no.vegvesen.nvdb.tnits.database.DirtyVeglenkesekvenser
import no.vegvesen.nvdb.tnits.database.KeyValue
import no.vegvesen.nvdb.tnits.database.Veglenker
import no.vegvesen.nvdb.tnits.extensions.*
import no.vegvesen.nvdb.tnits.geometry.SRID
import no.vegvesen.nvdb.tnits.geometry.parseWkt
import no.vegvesen.nvdb.tnits.model.VeglenkeId
import no.vegvesen.nvdb.tnits.model.veglenkeId
import no.vegvesen.nvdb.tnits.uberiketApi
import no.vegvesen.nvdb.tnits.utstrekning
import no.vegvesen.nvdb.tnits.vegobjekter.MAX_DATE
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.inList
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Clock
import kotlin.time.Instant

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
            transaction {
                insertVeglenkerSql(veglenker)
                // Keep progress update atomic within the same transaction
                KeyValue.putSync("veglenker_backfill_last_id", lastId!!)
            }
            totalCount += veglenker.size
            println("Satt inn ${veglenker.size} veglenker, totalt antall: $totalCount")
        }
    } while (veglenker.isNotEmpty())
}

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

            changedIds.forEachChunked(100) { chunk ->
                var start: VeglenkeId? = null
                do {
                    val batch = uberiketApi.streamVeglenker(start = start, ider = chunk).toList()

                    if (batch.isNotEmpty()) {
                        veglenker.addAll(batch)
                        start = batch.last().veglenkeId
                    }
                } while (batch.isNotEmpty())
            }

            transaction {
                Veglenker.deleteWhere { Veglenker.veglenkesekvensId inList changedIds }
                insertVeglenkerSql(veglenker)
                publishChangedVeglenkesekvensIds(changedIds)
                KeyValue.putSync("veglenker_last_hendelse_id", lastHendelseId)
            }
            println("Behandlet ${response.hendelser.size} hendelser, siste ID: $lastHendelseId")
        }
    } while (response.hendelser.isNotEmpty())
    println("Oppdatering av veglenker fullført. Siste hendelse-ID: $lastHendelseId")
}

fun publishChangedVeglenkesekvensIds(changedIds: Collection<Long>) {
    DirtyVeglenkesekvenser.batchInsert(changedIds) {
        this[DirtyVeglenkesekvenser.veglenkesekvensId] = it
        this[DirtyVeglenkesekvenser.sistEndret] =
            Clock.System.nowOffsetDateTime()
    }
}

private fun insertVeglenkerRocksDb(veglenker: List<VeglenkeMedId>) {
}

private fun insertVeglenkerSql(veglenker: List<VeglenkeMedId>) {
    Veglenker.batchInsert(veglenker, shouldReturnGeneratedValues = false) { veglenke ->
        this[Veglenker.veglenkesekvensId] = veglenke.veglenkesekvensId
        this[Veglenker.veglenkenummer] = veglenke.veglenkenummer
        this[Veglenker.sistEndret] = Clock.System.nowOffsetDateTime()
        this[Veglenker.startdato] = veglenke.gyldighetsperiode.startdato.toKotlinLocalDate()
        this[Veglenker.sluttdato] = veglenke.gyldighetsperiode.sluttdato?.toKotlinLocalDate()
            ?: MAX_DATE
        this[Veglenker.startnode] = veglenke.startnode
        this[Veglenker.sluttnode] = veglenke.sluttnode
        this[Veglenker.startposisjon] = veglenke.utstrekning.startposisjon.toBigDecimal()
        this[Veglenker.sluttposisjon] = veglenke.utstrekning.sluttposisjon.toBigDecimal()
        this[Veglenker.geometri] = parseWkt(veglenke.geometri.wkt, SRID.UTM33)
        this[Veglenker.typeVeg] = veglenke.typeVeg
        this[Veglenker.detaljniva] = veglenke.detaljniva
        veglenke.superstedfesting?.let { stedfesting ->
            this[Veglenker.superstedfestingId] = stedfesting.id
            this[Veglenker.superstedfestingStartposisjon] =
                stedfesting.startposisjon.toBigDecimal()
            this[Veglenker.superstedfestingSluttposisjon] =
                stedfesting.sluttposisjon.toBigDecimal()
            this[Veglenker.superstedfestingKjorefelt] = stedfesting.kjorefelt.ifEmpty { null }
        }
    }
}
