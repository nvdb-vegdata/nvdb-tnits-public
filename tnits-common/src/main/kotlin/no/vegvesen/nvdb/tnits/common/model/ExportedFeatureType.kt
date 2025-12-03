package no.vegvesen.nvdb.tnits.common.model

/**
 * Defines the feature types that are exported to TN-ITS.
 *
 * @param typeId The NVDB feature type ID.
 * @param sourceCode The source code used in TN-ITS, see http://spec.tn-its.eu/codelists/RoadFeatureSourceCode.xml
 * @param typeCode The type code used in TN-ITS, see http://spec.tn-its.eu/codelists/RoadFeatureTypeCode.xml
 */
enum class ExportedFeatureType(val typeId: Int, val sourceCode: String, val typeCode: RoadFeatureTypeCode) {
    SpeedLimit(VegobjektTyper.FARTSGRENSE, "regulation", RoadFeatureTypeCode.speedLimit),
    RoadName(VegobjektTyper.ADRESSE, "otherRoadFeature", RoadFeatureTypeCode.roadName),
    MaximumHeight(VegobjektTyper.HOYDEBEGRENSNING, "regulation", RoadFeatureTypeCode.restrictionForVehicles),
    ;

    fun getTypePrefix(): String {
        val paddedType = typeId.toString().padStart(4, '0')
        val typePrefix = "$paddedType-$name"
        return typePrefix
    }

    companion object {
        val entriesById = entries.associateBy { it.typeId }
        fun from(typeId: Int): ExportedFeatureType = entriesById[typeId] ?: error("Unknown feature typeId: $typeId")
    }
}
