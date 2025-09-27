package no.vegvesen.nvdb.tnits.services

import no.vegvesen.nvdb.apiles.datakatalog.EgenskapstypeHeltallenum
import no.vegvesen.nvdb.tnits.gateways.DatakatalogApi
import no.vegvesen.nvdb.tnits.model.EgenskapsTyper
import no.vegvesen.nvdb.tnits.model.VegobjektTyper
import no.vegvesen.nvdb.tnits.utilities.WithLogger

class EgenskapService(private val datakatalogApi: DatakatalogApi) : WithLogger {

    suspend fun getKmhByEgenskapVerdi(): Map<Int, Int> = try {
        datakatalogApi.getVegobjekttype(VegobjektTyper.FARTSGRENSE)
            .egenskapstyper!!
            .filterIsInstance<EgenskapstypeHeltallenum>()
            .single { it.id == EgenskapsTyper.FARTSGRENSE }
            .tillatteVerdier
            .associate { it.id to it.verdi!! }
    } catch (exception: Exception) {
        log.warn("Feil ved henting av vegobjekttype ${VegobjektTyper.FARTSGRENSE} fra datakatalogen: $exception. Bruker hardkodede verdier.")
        hardcodedFartsgrenseTillatteVerdier
    }

    companion object {
        val hardcodedFartsgrenseTillatteVerdier = mapOf(
            19885 to 5,
            11576 to 20,
            2726 to 30,
            2728 to 40,
            2730 to 50,
            2732 to 60,
            2735 to 70,
            2738 to 80,
            2741 to 90,
            5087 to 100,
            9721 to 110,
            19642 to 120,
        )
    }
}
