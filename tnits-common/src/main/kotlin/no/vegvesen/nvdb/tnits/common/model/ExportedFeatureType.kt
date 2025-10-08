package no.vegvesen.nvdb.tnits.common.model

enum class ExportedFeatureType(val typeId: Int, val sourceCode: String, val typeCode: String) {
    SpeedLimit(VegobjektTyper.FARTSGRENSE, "regulation", "speedLimit"),

    ;

    companion object {
        val entriesById = entries.associateBy { it.typeId }
        fun from(typeId: Int): ExportedFeatureType = entriesById[typeId] ?: error("Unknown feature typeId: $typeId")
    }
}
