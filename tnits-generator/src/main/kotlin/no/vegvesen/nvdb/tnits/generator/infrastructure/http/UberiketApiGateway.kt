package no.vegvesen.nvdb.tnits.generator.infrastructure.http

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import no.vegvesen.nvdb.apiles.uberiket.*
import no.vegvesen.nvdb.tnits.generator.core.api.HENDELSER_PAGE_SIZE
import no.vegvesen.nvdb.tnits.generator.core.api.UberiketApi
import no.vegvesen.nvdb.tnits.generator.core.extensions.forEachChunked
import no.vegvesen.nvdb.tnits.generator.core.extensions.getNdjson
import no.vegvesen.nvdb.tnits.generator.core.extensions.today
import no.vegvesen.nvdb.tnits.generator.core.model.*
import no.vegvesen.nvdb.tnits.generator.core.model.Veglenkesekvens
import no.vegvesen.nvdb.tnits.generator.core.model.Vegobjekt
import no.vegvesen.nvdb.tnits.generator.core.model.VegobjektHendelse
import kotlin.time.Clock
import kotlin.time.Instant
import no.vegvesen.nvdb.apiles.uberiket.Veglenkesekvens as ApiVeglenkesekvens
import no.vegvesen.nvdb.apiles.uberiket.Vegobjekt as ApiVegobjekt

private const val FETCH_IDER_BATCH_SIZE = 100

@Singleton
class UberiketApiGateway(@Named("uberiketHttpClient") private val httpClient: HttpClient, private val clock: Clock) : UberiketApi {

    override suspend fun getVeglenkesekvenser(start: Long?, antall: Int): List<Veglenkesekvens> {
        require(antall <= 1000)
        val today = clock.today()
        return httpClient
            .getNdjson<ApiVeglenkesekvens>("vegnett/veglenkesekvenser/stream") {
                parameter("start", start)
                parameter("antall", antall)
            }
            .map { it.toDomain(today) }
    }

    override suspend fun getVeglenkesekvenserWithIds(ider: Collection<Long>): List<Veglenkesekvens> {
        require(ider.size <= 100) { "too many IDs will make the URL too long" }
        val today = clock.today()
        return httpClient
            .getNdjson<ApiVeglenkesekvens>("vegnett/veglenkesekvenser/stream") {
                parameter("ider", ider.joinToString(","))
            }
            .map { it.toDomain(today) }
    }

    override suspend fun getLatestVeglenkesekvensHendelseId(tidspunkt: Instant): Long = httpClient
        .get("hendelser/veglenkesekvenser/siste") {
            parameter("tidspunkt", tidspunkt.toString())
        }.body<VegnettNotifikasjon>()
        .hendelseId

    suspend fun getVeglenkesekvensHendelser(start: Long?, antall: Int): VegnettHendelserSide = httpClient
        .get("hendelser/veglenkesekvenser") {
            parameter("start", start)
            parameter("antall", antall)
        }.body()

    override fun streamVeglenkesekvensHendelser(start: Long?): Flow<VeglenkesekvensHendelse> = flow {
        var lastId = start
        while (true) {
            val side = getVeglenkesekvensHendelser(lastId, HENDELSER_PAGE_SIZE)

            emitAll(
                side.hendelser.map {
                    VeglenkesekvensHendelse(
                        veglenkesekvensId = it.nettelementId,
                        hendelseId = it.hendelseId,
                    )
                }.asFlow(),
            )

            lastId = side.metadata.neste?.start?.toLong()

            if (side.hendelser.isEmpty() || lastId == null) {
                break
            }
        }
    }

    override suspend fun getCurrentVegobjekter(typeId: Int, start: Long?, antall: Int): List<Vegobjekt> = httpClient
        .getNdjson<ApiVegobjekt>("vegobjekter/$typeId/stream") {
            parameter("start", start)
            parameter("antall", antall)
        }
        .map { it.toDomain() }

    override suspend fun getLatestVegobjektHendelseId(typeId: Int, tidspunkt: Instant): Long = httpClient
        .get("hendelser/vegobjekter/$typeId/siste") {
            parameter("tidspunkt", tidspunkt.toString())
        }.body<VegobjektNotifikasjon>()
        .hendelseId

    override fun getVegobjektHendelserPaginated(typeId: Int, startDato: Instant, antall: Int): Flow<VegobjektNotifikasjon> = flow {
        var start: Long? = getLatestVegobjektHendelseId(typeId, startDato)
        while (true) {
            val side = getVegobjektHendelser(typeId, start, antall)

            emitAll(side.hendelser.asFlow())

            start = side.metadata.neste?.start?.toLong()

            if (side.hendelser.isEmpty() || start == null) {
                break
            }
        }
    }

    override fun streamVegobjektHendelser(typeId: Int, start: Long?): Flow<VegobjektHendelse> = flow {
        var lastId = start
        while (true) {
            val side = getVegobjektHendelser(typeId, lastId, HENDELSER_PAGE_SIZE)

            side.hendelser.forEach {
                emit(VegobjektHendelse(hendelseId = it.hendelseId, vegobjektId = it.vegobjektId, vegobjektVersjon = it.vegobjektVersjon))
            }

            lastId = side.metadata.neste?.start?.toLong()

            if (side.hendelser.isEmpty() || lastId == null) {
                break
            }
        }
    }

    private suspend fun getVegobjektHendelser(typeId: Int, start: Long?, antall: Int): VegobjektHendelserSide = httpClient
        .get("hendelser/vegobjekter/$typeId") {
            parameter("start", start)
            parameter("antall", antall)
        }.body()

    override fun getVegobjekterPaginated(typeId: Int, vegobjektIds: Set<Long>, inkluderIVegobjekt: Set<InkluderIVegobjekt>): Flow<Vegobjekt> = flow {
        vegobjektIds.forEachChunked(FETCH_IDER_BATCH_SIZE) { chunk ->
            var start: String? = null
            while (true) {
                val side = httpClient.get("vegobjekter/$typeId") {
                    parameter("ider", chunk.joinToString())
                    parameter("inkluder", inkluderIVegobjekt.joinToString { it.value })
                    parameter("start", start)
                }.body<VegobjekterSide>()

                emitAll(side.vegobjekter.map { it.toDomain() }.asFlow())

                start = side.metadata.neste?.start

                if (side.vegobjekter.isEmpty() || start == null) {
                    break
                }
            }
        }
    }
}
