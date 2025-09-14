package no.vegvesen.nvdb.tnits.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import no.vegvesen.nvdb.apiles.uberiket.EnumEgenskap
import no.vegvesen.nvdb.apiles.uberiket.Retning
import no.vegvesen.nvdb.apiles.uberiket.Sideposisjon
import no.vegvesen.nvdb.apiles.uberiket.TekstEgenskap
import no.vegvesen.nvdb.tnits.vegobjekter.getStedfestingLinjer
import kotlin.time.Instant
import kotlin.time.toKotlinInstant
import no.vegvesen.nvdb.apiles.uberiket.Vegobjekt as ApiVegobjekt

@Serializable
sealed interface EgenskapVerdi

@Serializable
@SerialName("EnumVerdi")
data class EnumVerdi(val verdi: Int) : EgenskapVerdi

@Serializable
@SerialName("TekstVerdi")
data class TekstVerdi(val verdi: String) : EgenskapVerdi

fun ApiVegobjekt.toDomain(vararg relevantEgenskapIds: Int, originalStartdato: LocalDate?): Vegobjekt = Vegobjekt(
    id = id,
    type = typeId,
    startdato = gyldighetsperiode!!.startdato.toKotlinLocalDate(),
    sluttdato = gyldighetsperiode!!.sluttdato?.toKotlinLocalDate(),
    sistEndret = sistEndret.toInstant().toKotlinInstant(),
    egenskaper = relevantEgenskapIds.associateWith {
        when (val egenskap = egenskaper!![it.toString()]) {
            is EnumEgenskap -> EnumVerdi(egenskap.verdi)
            is TekstEgenskap -> TekstVerdi(egenskap.verdi)
            else -> error("Ugyldig egenskap-verdi for egenskap $it: $egenskap")
        }
    },
    stedfestinger = getStedfestingLinjer(),
    originalStartdato = originalStartdato,
)

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
)

@Serializable
data class VegobjektStedfesting(
    val veglenkesekvensId: Long,
    val startposisjon: Double,
    val sluttposisjon: Double,
    val retning: Retning? = null,
    val sideposisjon: Sideposisjon? = null,
    val kjorefelt: List<String> = emptyList(),
)
