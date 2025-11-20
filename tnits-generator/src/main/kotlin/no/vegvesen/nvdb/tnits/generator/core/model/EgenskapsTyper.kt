package no.vegvesen.nvdb.tnits.generator.core.model

object EgenskapsTyper {
    const val FARTSGRENSE = 2021
    const val VEGKLASSE = 9338
    const val FELTOVERSIKT_I_VEGLENKERETNING = 5528

    const val ADRESSENAVN = 4589
    const val VEGSYSTEM_VEGKATAGORI = 11276
    const val VEGSYSTEM_VEGNUMMER = 11277
    const val VEGSYSTEM_FASE = 11278

    const val SKILTA_HOYDE = 5277

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

    val hardcodedVegkategoriTillatteVerdier = mapOf(
        19024 to "E",
        19025 to "R",
        19026 to "F",
        19027 to "K",
        19028 to "P",
        19029 to "S",
    )

    val hardcodedFaseTillatteVerdier = mapOf(
        19030 to "P",
        19031 to "A",
        19032 to "V",
        19090 to "F",
    )
}
