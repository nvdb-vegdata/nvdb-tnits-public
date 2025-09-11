package no.vegvesen.nvdb.tnits.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.serialization.Serializable
import no.vegvesen.nvdb.apiles.uberiket.EnumEgenskap
import no.vegvesen.nvdb.apiles.uberiket.HeltallEgenskap
import no.vegvesen.nvdb.apiles.uberiket.Retning
import no.vegvesen.nvdb.apiles.uberiket.Sideposisjon
import no.vegvesen.nvdb.tnits.vegobjekter.getStedfestingLinjer
import kotlin.time.Instant
import kotlin.time.toKotlinInstant
import no.vegvesen.nvdb.apiles.uberiket.Vegobjekt as ApiVegobjekt

@Serializable
sealed interface EgenskapVerdi

data class Heltall(val verdi: Long) : EgenskapVerdi

fun ApiVegobjekt.toDomain(vararg relevantEgenskapIds: Int): Vegobjekt = Vegobjekt(
    id = id,
    versjon = versjon,
    type = typeId,
    startdato = gyldighetsperiode!!.startdato.toKotlinLocalDate(),
    sluttdato = gyldighetsperiode!!.sluttdato?.toKotlinLocalDate(),
    sistEndret = sistEndret.toInstant().toKotlinInstant(),
    egenskaper = relevantEgenskapIds.associateWith {
        when (val egenskap = egenskaper!![it.toString()]) {
            is HeltallEgenskap -> Heltall(egenskap.verdi)
            is EnumEgenskap -> Heltall(egenskap.verdi.toLong())
            else -> error("Ukjent egenskap-verdi for egenskap $it: $egenskap")
        }
    },
    stedfestinger = getStedfestingLinjer(),
)

@Serializable
data class Vegobjekt(
    val id: Long,
    val versjon: Int,
    val type: Int,
    val startdato: LocalDate,
    val sluttdato: LocalDate?,
    val sistEndret: Instant,
    val egenskaper: Map<Int, EgenskapVerdi>,
    val stedfestinger: List<VegobjektStedfesting>,
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
