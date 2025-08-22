package no.vegvesen.nvdb.tnits.model

import no.vegvesen.nvdb.apiles.uberiket.Detaljniva
import no.vegvesen.nvdb.apiles.uberiket.StedfestingLinje
import no.vegvesen.nvdb.apiles.uberiket.TypeVeg
import org.locationtech.jts.geom.Geometry

data class Veglenke(
    val veglenkesekvensId: Long,
    val veglenkenummer: Int,
    val startposisjon: Double,
    val sluttposisjon: Double,
    val geometri: Geometry,
    val typeVeg: TypeVeg,
    val detaljniva: Detaljniva,
    val superstedfesting: StedfestingLinje? = null,
) {
    val veglenkeId: VeglenkeId
        get() = VeglenkeId(veglenkesekvensId, veglenkenummer)
}
