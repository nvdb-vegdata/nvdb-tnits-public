package no.vegvesen.nvdb.tnits

import no.vegvesen.nvdb.apiles.uberiket.Vegobjekt
import no.vegvesen.nvdb.tnits.vegobjekter.VegobjektStedfesting
import no.vegvesen.nvdb.tnits.vegobjekter.getStedfestingLinjer
import org.openlr.map.Path

fun mapSingleSpeedLimit(vegobjekt: Vegobjekt) {
    val stedfestinger = vegobjekt.getStedfestingLinjer()

    val path = getPath(stedfestinger)

    val lineLocation = locationFactory.createLineLocation(path)
}

fun getPath(stedfestinger: Iterable<VegobjektStedfesting>): Path<OpenLrLine> {
    TODO()
}
