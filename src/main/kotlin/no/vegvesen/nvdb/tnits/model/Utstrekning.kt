package no.vegvesen.nvdb.tnits.model

data class Utstrekning(
    val veglenkesekvensId: Long,
    val startposisjon: Double,
    val sluttposisjon: Double,
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

fun Utstrekning.intersect(other: Utstrekning): Utstrekning? =
    if (overlaps(other)) {
        Utstrekning(
            veglenkesekvensId,
            maxOf(startposisjon, other.startposisjon),
            minOf(sluttposisjon, other.sluttposisjon),
        )
    } else {
        null
    }

fun Utstrekning.overlaps(other: Utstrekning): Boolean =
    veglenkesekvensId == other.veglenkesekvensId &&
        (
            startposisjon < other.sluttposisjon &&
                sluttposisjon > other.startposisjon ||
                startposisjon == other.startposisjon ||
                sluttposisjon == other.sluttposisjon
        )
