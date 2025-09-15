package no.vegvesen.nvdb.tnits.model

import kotlinx.datetime.LocalDate
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
)

/**
 * Converts an API vegobjekt to a domain vegobjekt, extracting only relevant properties
 * for the specific vegobjekt type.
 */
fun ApiVegobjekt.toDomainVegobjekt(overrideValidFrom: LocalDate? = null): Vegobjekt {
    val relevanteEgenskaper = relevanteEgenskaperPerType[typeId]
        ?: error("Ukjent vegobjekttype: $typeId")

    return toDomain(*relevanteEgenskaper.toIntArray(), originalStartdato = overrideValidFrom)
}

/**
 * Converts a collection of API vegobjekter to domain vegobjekter with optional valid-from overrides.
 */
fun List<ApiVegobjekt>.toDomainVegobjekter(validFromById: Map<Long, LocalDate> = emptyMap()): List<Vegobjekt> = map { apiVegobjekt ->
    apiVegobjekt.toDomainVegobjekt(validFromById[apiVegobjekt.id])
}

/**
 * Converts a map of API vegobjekt updates to domain vegobjekt updates.
 */
fun Map<Long, ApiVegobjekt?>.toDomainVegobjektUpdates(validFromById: Map<Long, LocalDate> = emptyMap()): Map<Long, Vegobjekt?> =
    mapValues { (id, apiVegobjekt) ->
        apiVegobjekt?.toDomainVegobjekt(validFromById[id])
    }
