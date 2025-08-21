package no.vegvesen.nvdb.tnits.model

import org.locationtech.jts.geom.Geometry

data class Veglenke(
    val veglenkesekvensId: Long,
    val veglenkenummer: Int,
    val startposisjon: Double,
    val sluttposisjon: Double,
    val geometri: Geometry,
) {
    val veglenkeId: VeglenkeId
        get() = VeglenkeId(veglenkesekvensId, veglenkenummer)
}
