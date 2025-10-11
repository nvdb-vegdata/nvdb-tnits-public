package no.vegvesen.nvdb.tnits.generator.core.model

object EgenskapsTyper {
    const val FARTSGRENSE = 2021
    const val VEGKLASSE = 9338
    const val FELTOVERSIKT_I_VEGLENKERETNING = 5528

    val hardcodedFartsgrenseTillatteVerdier = mapOf(
        19885 to 5,
        11576 to 20,
        2726 to 30,
        2728 to 40,
        2730 to 50,
        2732 to 60,
        2735 to 70,
        2738 to 80,
        2741 to 90,
        5087 to 100,
        9721 to 110,
        19642 to 120,
    )
}
