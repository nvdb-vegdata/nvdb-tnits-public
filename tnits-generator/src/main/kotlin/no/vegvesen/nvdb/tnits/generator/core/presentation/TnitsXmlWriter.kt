package no.vegvesen.nvdb.tnits.generator.core.presentation

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.atStartOfDayIn
import no.vegvesen.nvdb.apiles.uberiket.Retning
import no.vegvesen.nvdb.tnits.common.extensions.WithLogger
import no.vegvesen.nvdb.tnits.common.extensions.measure
import no.vegvesen.nvdb.tnits.common.model.ExportedFeatureType
import no.vegvesen.nvdb.tnits.generator.core.extensions.*
import no.vegvesen.nvdb.tnits.generator.core.extensions.SRID.WGS84
import no.vegvesen.nvdb.tnits.generator.core.model.VegobjektStedfesting
import no.vegvesen.nvdb.tnits.generator.core.model.tnits.*
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.MultiLineString
import java.io.OutputStream
import kotlin.time.Instant

object TnitsXmlWriter : WithLogger {

    suspend fun writeFeaturesToXml(
        timestamp: Instant,
        outputStream: OutputStream,
        featureType: ExportedFeatureType,
        featureFlow: Flow<TnitsFeature>,
        exportType: TnitsExportType,
        lastTimestamp: Instant?,
    ) {
        log.measure("Generating $exportType", logStart = true) {
            writeXmlDocument(
                outputStream,
                rootQName = ROOT_QNAME,
                namespaces = namespaces,
            ) {
                "metadata" {
                    "Metadata" {
                        val time = if (lastTimestamp != null) {
                            "${lastTimestamp.truncateToSeconds()}-${timestamp.truncateToSeconds()}"
                        } else {
                            "${timestamp.truncateToSeconds()}"
                        }
                        "datasetId" { "NVDB-TNITS-${featureType.typeId}-${featureType}_${exportType}_$time" }
                        "datasetCreationTime" { timestamp }
                    }
                }
                "type" { exportType }
                featureFlow.collect { feature ->
                    writeFeature(feature)
                }
            }
        }
    }

    const val ROOT_QNAME = "RoadFeatureDataset"

    val namespaces =
        mapOf(
            "xlink" to "http://www.w3.org/1999/xlink",
            "gml" to "http://www.opengis.net/gml/3.2",
            "xsi" to "http://www.w3.org/2001/XMLSchema-instance",
            "" to "http://spec.tn-its.eu/schemas/",
            "xsi:schemaLocation" to
                "http://spec.tn-its.eu/schemas/ http://spec.tn-its.eu/schemas/TNITS.xsd http://www.opengis.net/gml/3.2 http://schemas.opengis.net/gml/3.2.1/gml.xsd",
        )

    private fun VegobjektStedfesting.toExternalReference(): String {
        val retning = when (retning) {
            Retning.MED -> "MED"
            Retning.MOT -> "MOT"
            else -> "-"
        }
        val sideposisjon = when (sideposisjon) {
            null -> "-"
            else -> sideposisjon.name
        }
        val kjorefelt = kjorefelt.joinToString("#").ifEmpty { "-" }
        return "${startposisjon.toFormattedString(8)}-${sluttposisjon.toFormattedString(8)}@$veglenkesekvensId:$retning:$sideposisjon:$kjorefelt"
    }

    private fun XmlStreamDsl.writeFeatureProperty(property: RoadFeatureProperty) {
        when (property) {
            is DoubleProperty -> text(property.value.toString())
            is IntProperty -> text(property.value.toString())
            is StringProperty -> text(property.value)
        }
    }

    private fun XmlStreamDsl.writeFeature(feature: TnitsFeature) {
        "roadFeatures" {
            "RoadFeature" {
                "validFrom" { feature.validFrom }
                feature.validTo?.let {
                    "validTo" { it }
                }
                "beginLifespanVersion" {
                    feature.beginLifespanVersion
                }
                feature.validTo?.let {
                    "endLifespanVersion" {
                        it.atStartOfDayIn(OsloZone)
                    }
                }
                if (feature.updateType != UpdateType.Snapshot) {
                    "updateInfo" {
                        "UpdateInfo" {
                            "type" { feature.updateType }
                        }
                    }
                }
                "source" {
                    attribute("xlink:href", "http://spec.tn-its.eu/codelists/RoadFeatureSourceCode#${feature.type.sourceCode}")
                }
                "type" {
                    attribute("xlink:href", "http://spec.tn-its.eu/codelists/RoadFeatureTypeCode#${feature.type.typeCode}")
                }
                if (feature.properties.any()) {
                    "properties" {
                        for ((type, property) in feature.properties) {
                            "GenericRoadFeatureProperty" {
                                "type" {
                                    attribute(
                                        "xlink:href",
                                        "http://spec.tn-its.eu/codelists/RoadFeaturePropertyType#${type.definition}",
                                    )
                                }
                                "value" { writeFeatureProperty(property) }
                            }
                        }
                    }
                }
                "id" {
                    "RoadFeatureId" {
                        "providerId" { "nvdb.no" }
                        "id" { feature.id }
                    }
                }
                feature.geometry?.let { geometry ->
                    val lineStrings = when (val geometry = geometry) {
                        is LineString -> listOf(geometry)
                        is MultiLineString -> (0 until geometry.numGeometries).map { geometry.getGeometryN(it) as LineString }
                        else -> throw IllegalArgumentException("Ugyldig geometri for vegobjekt ${feature.id}: ${geometry.geometryType}")
                    }

                    for (lineString in lineStrings) {
                        "locationReference" {
                            "GeometryLocationReference" {
                                "encodedGeometry" {
                                    "gml:LineString" {
                                        attribute("srsDimension", "2")
                                        attribute("srsName", "EPSG:4326")
                                        "gml:posList" {
                                            lineString.projectTo(WGS84).coordinates.joinToString(" ") { "${it.y.toRounded(5)} ${it.x.toRounded(5)}" }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                for (locationReference in feature.openLrLocationReferences) {
                    "locationReference" {
                        "OpenLRLocationReference" {
                            "binaryLocationReference" {
                                "BinaryLocationReference" {
                                    "base64String" {
                                        locationReference
                                    }
                                    "openLRBinaryVersion" {
                                        attribute("xlink:href", "http://spec.tn-its.eu/codelists/OpenLRBinaryVersionCode#v2_4")
                                    }
                                }
                            }
                        }
                    }
                }
                for (locationReference in feature.nvdbLocationReferences) {
                    "locationReference" {
                        "LocationByExternalReference" {
                            "predefinedLocationReference" {
                                attribute("xlink:href", "nvdb.no:${locationReference.toExternalReference()}")
                            }
                        }
                    }
                }
            }
        }
    }
}
