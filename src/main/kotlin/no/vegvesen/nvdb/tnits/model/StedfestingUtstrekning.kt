package no.vegvesen.nvdb.tnits.model

import no.vegvesen.nvdb.apiles.uberiket.Retning

interface StedfestingUtstrekning {
    val veglenkesekvensId: Long
    val startposisjon: Double
    val sluttposisjon: Double
    val retning: Retning?
    val kjorefelt: List<String>

    val relativeLength get() = sluttposisjon - startposisjon
}

data class EnkelUtstrekning(
    override val veglenkesekvensId: Long,
    override val startposisjon: Double,
    override val sluttposisjon: Double,
    override val retning: Retning? = null,
    override val kjorefelt: List<String> = emptyList(),
) : StedfestingUtstrekning

fun StedfestingUtstrekning.intersect(other: StedfestingUtstrekning): StedfestingUtstrekning? = if (overlaps(other)) {
    EnkelUtstrekning(
        veglenkesekvensId,
        maxOf(startposisjon, other.startposisjon),
        minOf(sluttposisjon, other.sluttposisjon),
    )
} else {
    null
}

fun StedfestingUtstrekning.overlaps(other: StedfestingUtstrekning): Boolean = veglenkesekvensId == other.veglenkesekvensId &&
    (
        startposisjon < other.sluttposisjon &&
            sluttposisjon > other.startposisjon ||
            startposisjon == other.startposisjon ||
            sluttposisjon == other.sluttposisjon
        )
