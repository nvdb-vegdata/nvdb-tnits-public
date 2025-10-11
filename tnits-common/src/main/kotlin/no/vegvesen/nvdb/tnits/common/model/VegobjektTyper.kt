package no.vegvesen.nvdb.tnits.common.model

object VegobjektTyper {
    const val FELTSTREKNING = 616
    const val FARTSGRENSE = 105
    const val FUNKSJONELL_VEGKLASSE = 821
}

val mainVegobjektTyper = listOf(VegobjektTyper.FARTSGRENSE)

val supportingVegobjektTyper = setOf(
    VegobjektTyper.FELTSTREKNING,
    VegobjektTyper.FUNKSJONELL_VEGKLASSE,
)
