package no.vegvesen.nvdb.tnits.generator.core.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import no.vegvesen.nvdb.apiles.uberiket.Detaljniva
import no.vegvesen.nvdb.apiles.uberiket.Retning
import no.vegvesen.nvdb.apiles.uberiket.TypeVeg
import org.locationtech.jts.geom.Geometry
import java.util.*

@Serializable
data class Superstedfesting(val veglenksekvensId: Long, val startposisjon: Double, val sluttposisjon: Double, val kjorefelt: List<String> = emptyList())

@Serializable
data class Veglenke(
    override val veglenkesekvensId: Long,
    val veglenkenummer: Int,
    override val startposisjon: Double,
    override val sluttposisjon: Double,
    val startnode: Long,
    val sluttnode: Long,
    val startdato: LocalDate,
    val sluttdato: LocalDate? = null,
    val lengde: Double,
    val konnektering: Boolean,
    @Serializable(with = JtsGeometrySerializer::class)
    val geometri: Geometry,
    val typeVeg: TypeVeg,
    val detaljniva: Detaljniva,
    val feltoversikt: List<String> = emptyList(),
    val superstedfesting: Superstedfesting? = null,
    // added after fetching from NVDB
    @Transient
    var frc: Byte? = null,
    // bitmask for allowed directions, added after fetching from NVDB
    @Transient
    var tillattRetning: Byte? = null,
) : StedfestingUtstrekning {
    val veglenkeId: VeglenkeId
        get() = VeglenkeId(veglenkesekvensId, veglenkenummer)

    val isTopLevel: Boolean
        get() = detaljniva in setOf(Detaljniva.VEGTRASE, Detaljniva.VEGTRASE_OG_KJOREBANE)

    fun isActive(date: LocalDate): Boolean = startdato <= date && (sluttdato == null || sluttdato > date)

    @Transient
    override val retning: Retning? = null

    @Transient
    override val kjorefelt: List<String> = feltoversikt

    override fun hashCode(): Int = Objects.hash(veglenkesekvensId, veglenkenummer)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Veglenke) return false
        return veglenkesekvensId == other.veglenkesekvensId && veglenkenummer == other.veglenkenummer
    }
}
