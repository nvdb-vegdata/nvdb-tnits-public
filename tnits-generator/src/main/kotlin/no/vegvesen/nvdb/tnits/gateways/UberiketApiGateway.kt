package no.vegvesen.nvdb.tnits.gateways

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import no.vegvesen.nvdb.apiles.uberiket.*
import no.vegvesen.nvdb.tnits.extensions.executeAsNdjsonFlow
import no.vegvesen.nvdb.tnits.extensions.forEachChunked
import kotlin.time.Instant

const val VEGLENKER_PAGE_SIZE = 1000

const val HENDELSER_PAGE_SIZE = 1000

const val VEGOBJEKTER_PAGE_SIZE = 1000

const val FETCH_IDER_BATCH_SIZE = 100

class UberiketApiGateway(private val httpClient: HttpClient) : UberiketApi {

    override suspend fun streamVeglenkesekvenser(start: Long?, slutt: Long?, ider: Collection<Long>?, antall: Int): Flow<Veglenkesekvens> = httpClient
        .prepareGet("vegnett/veglenkesekvenser/stream") {
            parameter("start", start)
            parameter("slutt", slutt)
            parameter("ider", ider?.joinToString(","))
            parameter("antall", antall)
        }.executeAsNdjsonFlow<Veglenkesekvens>()

    override suspend fun getLatestVeglenkesekvensHendelseId(tidspunkt: Instant): Long = httpClient
        .get("hendelser/veglenkesekvenser/siste") {
            parameter("tidspunkt", tidspunkt.toString())
        }.body<VegnettNotifikasjon>()
        .hendelseId

    override suspend fun getVeglenkesekvensHendelser(start: Long?, antall: Int): VegnettHendelserSide = httpClient
        .get("hendelser/veglenkesekvenser") {
            parameter("start", start)
            parameter("antall", antall)
        }.body()

    override suspend fun streamVegobjekter(typeId: Int, ider: Collection<Long>?, start: Long?, antall: Int): Flow<Vegobjekt> = httpClient
        .prepareGet("vegobjekter/$typeId/stream") {
            parameter("start", start)
            parameter("antall", antall)
            parameter("ider", ider?.joinToString(","))
        }.executeAsNdjsonFlow<Vegobjekt>()

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

    override suspend fun getVegobjektHendelser(typeId: Int, start: Long?, antall: Int, startDato: Instant?): VegobjektHendelserSide {
        require(start == null || startDato == null) { "Kan ikke bruke både start og startDato" }
        return httpClient
            .get("hendelser/vegobjekter/$typeId") {
                parameter("start", start)
                parameter("antall", antall)
                parameter("startDato", startDato)
            }.body()
    }

    override fun getVegobjekterPaginated(typeId: Int, vegobjektIds: Set<Long>, inkluderIVegobjekt: Set<InkluderIVegobjekt>): Flow<Vegobjekt> = flow {
        vegobjektIds.forEachChunked(FETCH_IDER_BATCH_SIZE) { chunk ->
            var start: String? = null
            while (true) {
                val side = httpClient.get("vegobjekter/$typeId") {
                    parameter("ider", chunk.joinToString())
                    parameter("inkluder", inkluderIVegobjekt.joinToString { it.value })
                    parameter("start", start)
                }.body<VegobjekterSide>()

                emitAll(side.vegobjekter.asFlow())

                start = side.metadata.neste?.start

                if (side.vegobjekter.isEmpty() || start == null) {
                    break
                }
            }
        }
    }
}
