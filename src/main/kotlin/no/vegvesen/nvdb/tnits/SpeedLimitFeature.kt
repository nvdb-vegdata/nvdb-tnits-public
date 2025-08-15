package no.vegvesen.nvdb.tnits

import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory

data class SpeedLimitFeature(
    val docLocalId: String, // gml:id for this document
    val stableId: String, // NVDB object id
    val operation: String, // add | modify | remove
    val kmh: Int,
    val openLR: OpenLR,
    val geometry: List<Segment>,
    val linearHref: String? = null,
)

fun validateAgainstTnitsXsd(xml: String) {
    val sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
    val schema = sf.newSchema(java.io.File("schemas/tnits/tnits.xsd")) // your local mirror
    val v = schema.newValidator()
    v.validate(StreamSource(xml.byteInputStream()))
}
