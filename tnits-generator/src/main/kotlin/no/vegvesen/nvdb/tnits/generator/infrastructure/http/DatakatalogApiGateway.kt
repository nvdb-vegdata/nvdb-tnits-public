package no.vegvesen.nvdb.tnits.generator.infrastructure.http

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import jakarta.inject.Named
import jakarta.inject.Singleton
import no.vegvesen.nvdb.apiles.datakatalog.EgenskapstypeHeltallenum
import no.vegvesen.nvdb.apiles.datakatalog.Vegobjekttype
import no.vegvesen.nvdb.tnits.common.extensions.WithLogger
import no.vegvesen.nvdb.tnits.common.model.VegobjektTyper
import no.vegvesen.nvdb.tnits.generator.core.api.DatakatalogApi
import no.vegvesen.nvdb.tnits.generator.core.model.EgenskapsTyper
import no.vegvesen.nvdb.tnits.generator.core.model.EgenskapsTyper.hardcodedFartsgrenseTillatteVerdier

@Singleton
class DatakatalogApiGateway(@Named("datakatalogHttpClient") private val httpClient: HttpClient) : WithLogger, DatakatalogApi {
    suspend fun getVegobjekttype(typeId: Int): Vegobjekttype = httpClient
        .get("vegobjekttyper/$typeId")
        .body()

    override suspend fun getKmhByEgenskapVerdi(): Map<Int, Int> = try {
        getVegobjekttype(VegobjektTyper.FARTSGRENSE)
            .egenskapstyper!!
            .filterIsInstance<EgenskapstypeHeltallenum>()
            .single { it.id == EgenskapsTyper.FARTSGRENSE }
            .tillatteVerdier
            .associate { it.id to it.verdi!! }
    } catch (exception: Exception) {
        log.warn("Feil ved henting av vegobjekttype ${VegobjektTyper.FARTSGRENSE} fra datakatalogen: $exception. Bruker hardkodede verdier.")
        hardcodedFartsgrenseTillatteVerdier
    }
}
