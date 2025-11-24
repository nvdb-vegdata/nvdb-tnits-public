package no.vegvesen.nvdb.tnits.generator.core.model.tnits

/**
 * Enum representing different types of road feature properties.
 *
 * @property definition The string definition associated with the property type. See http://spec.tn-its.eu/codelists/RoadFeaturePropertyTypeCode.xml
 */
enum class RoadFeaturePropertyType(val definition: String) {
    MaximumSpeedLimit("maximumSpeedLimit"),
    RoadName("officialName"),
    MaximumHeight("maximumHeight"),
}
