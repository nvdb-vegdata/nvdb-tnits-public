package no.vegvesen.nvdb.tnits.services

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.flow.Flow
import no.vegvesen.nvdb.apiles.uberiket.*
import no.vegvesen.nvdb.tnits.extensions.executeAsNdjsonFlow
import no.vegvesen.nvdb.tnits.model.VeglenkeId
import kotlin.time.Instant

const val VEGLENKER_PAGE_SIZE = 1000

const val HENDELSER_PAGE_SIZE = 1000

const val VEGOBJEKTER_PAGE_SIZE = 1000

class UberiketApi(
    private val httpClient: HttpClient,
) {
    suspend fun streamVeglenker(
        start: VeglenkeId? = null,
        ider: Collection<Long>? = null,
        antall: Int = VEGLENKER_PAGE_SIZE,
    ): Flow<VeglenkeMedId> =
        httpClient
            .prepareGet("vegnett/veglenker/stream") {
                parameter("start", start?.toString())
                parameter("ider", ider?.joinToString(","))
                parameter("antall", antall)
            }.executeAsNdjsonFlow<VeglenkeMedId>()

    suspend fun streamVeglenkesekvenser(
        start: Long? = null,
        antall: Int = VEGLENKER_PAGE_SIZE,
    ): Flow<Veglenkesekvens> =
        httpClient
            .prepareGet("vegnett/veglenkesekvenser/stream") {
                parameter("start", start?.toString())
                parameter("antall", antall)
            }.executeAsNdjsonFlow<Veglenkesekvens>()

    suspend fun getLatestHendelseId(tidspunkt: Instant): Long =
        httpClient
            .get("hendelser/veglenkesekvenser/siste") {
                parameter("tidspunkt", tidspunkt.toString())
            }.body<VegnettNotifikasjon>()
            .hendelseId

    suspend fun getVeglenkesekvensHendelser(
        start: Long? = null,
        antall: Int = HENDELSER_PAGE_SIZE,
    ): VegnettHendelserSide =
        httpClient
            .get("hendelser/veglenkesekvenser") {
                parameter("start", start?.toString())
                parameter("antall", antall)
            }.body()

    suspend fun streamVegobjekter(
        typeId: Int,
        ider: Collection<Long>? = null,
        start: Long? = null,
        antall: Int = VEGOBJEKTER_PAGE_SIZE,
    ): Flow<Vegobjekt> =
        httpClient
            .prepareGet("vegobjekter/$typeId/stream") {
                parameter("start", start?.toString())
                parameter("antall", antall)
                parameter("ider", ider?.joinToString(","))
            }.executeAsNdjsonFlow<Vegobjekt>()

    suspend fun getLatestVegobjektHendelseId(
        typeId: Int,
        tidspunkt: Instant,
    ): Long =
        httpClient
            .get("hendelser/vegobjekter/$typeId/siste") {
                parameter("tidspunkt", tidspunkt.toString())
            }.body<VegobjektNotifikasjon>()
            .hendelseId

    suspend fun getVegobjektHendelser(
        typeId: Int,
        start: Long? = null,
        antall: Int = HENDELSER_PAGE_SIZE,
    ): VegobjektHendelserSide =
        httpClient
            .get("hendelser/vegobjekter/$typeId") {
                parameter("start", start?.toString())
                parameter("antall", antall)
            }.body()
}
