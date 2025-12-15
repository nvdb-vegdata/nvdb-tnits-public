package no.vegvesen.nvdb.tnits.generator.core.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.toKotlinLocalDate
import no.vegvesen.nvdb.apiles.uberiket.*
import no.vegvesen.nvdb.tnits.common.extensions.WithLogger
import no.vegvesen.nvdb.tnits.common.model.VegobjektTyper
import no.vegvesen.nvdb.tnits.generator.core.extensions.associateNotNullValues
import kotlin.time.toKotlinInstant
import no.vegvesen.nvdb.apiles.uberiket.Vegobjekt as ApiVegobjekt

/**
 * Mapping utilities for converting API models to domain models for vegobjekter.
 */

/**
 * Mapping configuration for each vegobjekt type, defining which properties are relevant.
 */
private val relevanteEgenskaperPerType: Map<Int, Set<Int>> = mapOf(
    VegobjektTyper.FARTSGRENSE to setOf(EgenskapsTyper.FARTSGRENSE),
    VegobjektTyper.FUNKSJONELL_VEGKLASSE to setOf(EgenskapsTyper.VEGKLASSE),
    VegobjektTyper.FELTSTREKNING to setOf(EgenskapsTyper.FELTOVERSIKT_I_VEGLENKERETNING),
    VegobjektTyper.ADRESSE to setOf(EgenskapsTyper.ADRESSENAVN),
    VegobjektTyper.VEGSYSTEM to setOf(
        EgenskapsTyper.VEGSYSTEM_VEGKATAGORI,
        EgenskapsTyper.VEGSYSTEM_VEGNUMMER,
        EgenskapsTyper.VEGSYSTEM_FASE,
    ),
    VegobjektTyper.HOYDEBEGRENSNING to setOf(EgenskapsTyper.SKILTA_HOYDE),
)

/**
 * Converts an API vegobjekt to a domain vegobjekt, extracting only relevant properties
 * for the specific vegobjekt type.
 */
fun ApiVegobjekt.toDomain(overrideValidFrom: LocalDate? = null): Vegobjekt {
    val relevanteEgenskaper = relevanteEgenskaperPerType[typeId]
        ?: error("Ukjent vegobjekttype: $typeId")

    return Vegobjekt(
        id = id,
        type = typeId,
        startdato = gyldighetsperiode!!.startdato.toKotlinLocalDate(),
        sluttdato = gyldighetsperiode!!.sluttdato?.toKotlinLocalDate(),
        sistEndret = sistEndret.toInstant().toKotlinInstant(),
        egenskaper = relevanteEgenskaper.associateNotNullValues {
            it to when (val egenskap = egenskaper!![it.toString()]) {
                is FlyttallEgenskap -> FlyttallVerdi(egenskap.verdi)
                is EnumEgenskap -> EnumVerdi(egenskap.verdi)
                is TekstEgenskap -> TekstVerdi(egenskap.verdi)
                is HeltallEgenskap -> HeltallVerdi(egenskap.verdi)
                null -> null
                else -> error("Ugyldig egenskap-verdi for egenskap $it: $egenskap")
            }
        },
        stedfestinger = getStedfestingLinjer(),
        originalStartdato = overrideValidFrom,
        versjon = versjon,
    )
}

val logger = object : WithLogger {}

/**
 * Converts a collection of API vegobjekter to domain vegobjekter with optional valid-from overrides.
 */
fun List<ApiVegobjekt>.toDomainVegobjekter(validFromById: Map<Long, LocalDate> = emptyMap()): List<Vegobjekt> = mapNotNull { apiVegobjekt ->
    try {
        apiVegobjekt.toDomain(validFromById[apiVegobjekt.id])
    } catch (e: Throwable) {
        logger.log.warn("Feil ved mapping av vegobjekt med type ${apiVegobjekt.typeId} og id ${apiVegobjekt.id}: ${e.message}", e)
        null
    }
}

fun ApiVegobjekt.getStedfestingLinjer(): List<VegobjektStedfesting> = when (val stedfesting = this.stedfesting) {
    is StedfestingLinjer ->
        stedfesting.linjer.map {
            VegobjektStedfesting(
                veglenkesekvensId = it.id,
                startposisjon = it.startposisjon,
                sluttposisjon = it.sluttposisjon,
                retning = it.retning,
                sideposisjon = it.sideposisjon,
                kjorefelt = it.kjorefelt,
            )
        }

    else -> emptyList()
}
