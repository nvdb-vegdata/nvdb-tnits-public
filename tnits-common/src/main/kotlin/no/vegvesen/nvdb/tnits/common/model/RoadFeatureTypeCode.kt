package no.vegvesen.nvdb.tnits.common.model

/**
 * Type codes used in TN-ITS, see http://spec.tn-its.eu/codelists/RoadFeatureTypeCode.xml
 */
@Suppress("EnumEntryName")
enum class RoadFeatureTypeCode {
    speedLimit,
    roadName,
    restrictionForVehicles,
}
