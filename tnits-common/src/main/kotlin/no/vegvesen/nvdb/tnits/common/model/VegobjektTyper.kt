package no.vegvesen.nvdb.tnits.common.model

object VegobjektTyper {
    const val FELTSTREKNING = 616
    const val FARTSGRENSE = 105
    const val FUNKSJONELL_VEGKLASSE = 821
    const val ADRESSE = 538
}

val mainVegobjektTyper = ExportedFeatureType.entries.map { it.typeId }

val supportingVegobjektTyper = setOf(
    VegobjektTyper.FELTSTREKNING,
    VegobjektTyper.FUNKSJONELL_VEGKLASSE,
)
