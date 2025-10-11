package no.vegvesen.nvdb.tnits.generator.core.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import no.vegvesen.nvdb.apiles.uberiket.Retning
import no.vegvesen.nvdb.apiles.uberiket.Sideposisjon
import kotlin.time.Instant

@Serializable
sealed interface EgenskapVerdi

@Serializable
@SerialName("EnumVerdi")
data class EnumVerdi(val verdi: Int) : EgenskapVerdi

@Serializable
@SerialName("TekstVerdi")
data class TekstVerdi(val verdi: String) : EgenskapVerdi

@Serializable
data class Vegobjekt(
    val id: Long,
    val type: Int,
    val startdato: LocalDate,
    val sluttdato: LocalDate?,
    val sistEndret: Instant,
    val egenskaper: Map<Int, EgenskapVerdi>,
    val stedfestinger: List<VegobjektStedfesting>,
    val originalStartdato: LocalDate?,
    val fjernet: Boolean = false,
)

@Serializable
data class VegobjektStedfesting(
    override val veglenkesekvensId: Long,
    override val startposisjon: Double,
    override val sluttposisjon: Double,
    override val retning: Retning? = null,
    val sideposisjon: Sideposisjon? = null,
    override val kjorefelt: List<String> = emptyList(),
) : StedfestingUtstrekning
