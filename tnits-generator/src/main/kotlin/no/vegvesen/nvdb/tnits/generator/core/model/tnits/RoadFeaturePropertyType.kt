package no.vegvesen.nvdb.tnits.generator.core.model.tnits

/**
 * Enum representing different types of road feature properties.
 *
 * @property definition The string definition associated with the property type. See http://spec.tn-its.eu/codelists/RoadFeaturePropertyTypeCode.xml
 */
enum class RoadFeaturePropertyType(
    val definition: String,
    val codelistBaseUrl: String = "http://spec.tn-its.eu/codelists/RoadFeaturePropertyType#",
) {
    MaximumSpeedLimit("maximumSpeedLimit"),
    RoadName("officialName"),
    RoadNumber("officialNumber"),
    ConditionOfFacility(
        "ConditionOfFacilityValue",
        "http://inspire.ec.europa.eu/codelist/",
    ),
    MaximumHeight("maximumHeight"),
}
