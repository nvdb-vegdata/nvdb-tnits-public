package no.vegvesen.nvdb.tnits.model

import kotlinx.serialization.Serializable
import no.vegvesen.nvdb.apiles.uberiket.Detaljniva
import no.vegvesen.nvdb.apiles.uberiket.TypeVeg
import org.locationtech.jts.geom.Geometry

@Serializable
data class Superstedfesting(
    val veglenksekvensId: Long,
    val startposisjon: Double,
    val sluttposisjon: Double,
    val kjorefelt: List<String> = emptyList(),
)

@Serializable
data class Veglenke(
    val veglenkesekvensId: Long,
    val veglenkenummer: Int,
    val startposisjon: Double,
    val sluttposisjon: Double,
    @Serializable(with = JtsGeometrySerializer::class)
    val geometri: Geometry,
    val typeVeg: TypeVeg,
    val detaljniva: Detaljniva,
    val superstedfesting: Superstedfesting? = null,
) {
    val veglenkeId: VeglenkeId
        get() = VeglenkeId(veglenkesekvensId, veglenkenummer)
}
