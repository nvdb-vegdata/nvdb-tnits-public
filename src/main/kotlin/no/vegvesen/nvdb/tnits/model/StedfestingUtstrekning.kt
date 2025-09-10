package no.vegvesen.nvdb.tnits.model

import no.vegvesen.nvdb.apiles.uberiket.Retning

data class StedfestingUtstrekning(
    val veglenkesekvensId: Long,
    val startposisjon: Double,
    val sluttposisjon: Double,
    val retning: Retning? = null,
    val kjorefelt: List<String> = emptyList(),
) {
    init {
        require(startposisjon in 0.0..1.0) { "'startposisjon' ($startposisjon) må være i intervallet [0.0, 1.0]" }
        require(sluttposisjon in 0.0..1.0) { "'sluttposisjon' ($sluttposisjon) må være i intervallet [0.0, 1.0]" }
        require(
            startposisjon <= sluttposisjon,
        ) { "'startposisjon' ($startposisjon) kan ikke være større enn 'sluttposisjon' ($sluttposisjon)" }
    }

    val relativeLength get() = sluttposisjon - startposisjon
}

fun StedfestingUtstrekning.intersect(other: StedfestingUtstrekning): StedfestingUtstrekning? =
    if (overlaps(other)) {
        StedfestingUtstrekning(
            veglenkesekvensId,
            maxOf(startposisjon, other.startposisjon),
            minOf(sluttposisjon, other.sluttposisjon),
        )
    } else {
        null
    }

fun StedfestingUtstrekning.overlaps(other: StedfestingUtstrekning): Boolean =
    veglenkesekvensId == other.veglenkesekvensId &&
        (
            startposisjon < other.sluttposisjon &&
                sluttposisjon > other.startposisjon ||
                startposisjon == other.startposisjon ||
                sluttposisjon == other.sluttposisjon
        )
