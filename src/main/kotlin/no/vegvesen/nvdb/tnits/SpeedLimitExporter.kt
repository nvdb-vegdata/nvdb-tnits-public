package no.vegvesen.nvdb.tnits

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.datetime.atStartOfDayIn
import no.vegvesen.nvdb.tnits.Services.Companion.marshaller
import no.vegvesen.nvdb.tnits.config.AppConfig
import no.vegvesen.nvdb.tnits.extensions.OsloZone
import no.vegvesen.nvdb.tnits.extensions.toRounded
import no.vegvesen.nvdb.tnits.extensions.truncateToSeconds
import no.vegvesen.nvdb.tnits.utilities.measure
import no.vegvesen.nvdb.tnits.xml.XmlStreamDsl
import no.vegvesen.nvdb.tnits.xml.writeXmlDocument
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPOutputStream
import kotlin.time.Instant

class SpeedLimitExporter(private val speedLimitGenerator: SpeedLimitGenerator, private val appConfig: AppConfig) {

    suspend fun generateSpeedLimitsDeltaUpdate(now: Instant, since: Instant) {
        val path =
            Files.createTempFile(
                "TNITS_SpeedLimits_${now.truncateToSeconds().toString().replace(":", "-")}_update",
                if (appConfig.gzip) ".xml.gz" else ".xml",
            )

        log.info("Lagrer endringsdata for fartsgrenser til ${path.toAbsolutePath()}")

        val speedLimitsFlow = speedLimitGenerator.generateSpeedLimitsUpdate(since)

        openStream(path).use { outputStream ->

            writeXmlDocument(
                outputStream,
                rootQName = rootQName,
                namespaces = namespaces,
            ) {
                "metadata" {
                    "Metadata" {
                        "datasetId" { "NVDB-TNITS-SpeedLimits_$now" }
                        "datasetCreationTime" { now }
                    }
                }
                "type" { "Update" }
                "roadFeatures" {
                    speedLimitsFlow.collectIndexed { i, speedLimit ->
                        writeSpeedLimit(speedLimit, i)
                    }
                }
            }
        }
    }

    fun openStream(path: Path): OutputStream {
        val fileOut = BufferedOutputStream(Files.newOutputStream(path), 1024 * 1024)
        return if (appConfig.gzip) {
            BufferedOutputStream(GZIPOutputStream(fileOut), 64 * 1024)
        } else {
            fileOut
        }
    }

    suspend fun exportSpeedLimitsFullSnapshot(timestamp: Instant) {
        val path =
            Files.createTempFile(
                "TNITS_SpeedLimits_${timestamp.truncateToSeconds().toString().replace(":", "-")}_snapshot",
                if (appConfig.gzip) ".xml.gz" else ".xml",
            )
        log.info("Lagrer fullstendig fartsgrense-snapshot til ${path.toAbsolutePath()}")

        val speedLimitsFlow = speedLimitGenerator.generateSpeedLimitsSnapshot()

        openStream(path).use { outputStream ->
            exportSpeedLimitsFullSnapshot(timestamp, outputStream, speedLimitsFlow)
        }
    }

    suspend fun exportSpeedLimitsFullSnapshot(timestamp: Instant, outputStream: OutputStream, speedLimitsFlow: Flow<SpeedLimit>) {
        log.measure("Generating full snapshot", logStart = true) {
            writeXmlDocument(
                outputStream,
                rootQName = rootQName,
                namespaces = namespaces,
            ) {
                "metadata" {
                    "Metadata" {
                        "datasetId" { "NVDB-TNITS-SpeedLimits_$timestamp" }
                        "datasetCreationTime" { timestamp }
                    }
                }
                "type" { "Snapshot" }
                "roadFeatures" {
                    speedLimitsFlow.collectIndexed { i, speedLimit ->
                        writeSpeedLimit(speedLimit, i)
                    }
                }
            }
        }
    }

    private fun XmlStreamDsl.writeSpeedLimit(speedLimit: SpeedLimit, index: Int) {
        "RoadFeature" {
            attribute("gml:id", "RF-$index")
            "id" {
                "RoadFeatureId" {
                    "id" { speedLimit.id }
                    "providerId" { "nvdb.no" }
                }
            }
            "validFrom" { speedLimit.validFrom }
            speedLimit.validTo?.let {
                "validTo" { it }
            }
            "beginLifespanVersion" {
                speedLimit.beginLifespanVersion
            }
            speedLimit.validTo?.let {
                "endLifespanVersion" {
                    it.atStartOfDayIn(OsloZone)
                }
            }
            "updateInfo" {
                "UpdateInfo" {
                    "type" { speedLimit.updateType }
                }
            }
            "source" {
                attribute("xlink:href", "http://spec.tn-its.eu/codelists/RoadFeatureSourceCode#regulation")
            }
            "type" {
                attribute("xlink:href", "http://spec.tn-its.eu/codelists/RoadFeatureTypeCode#speedLimit")
            }
            "properties" {
                "GenericRoadFeatureProperty" {
                    "type" {
                        attribute(
                            "xlink:href",
                            "http://spec.tn-its.eu/codelists/RoadFeaturePropertyType#maximumSpeedLimit",
                        )
                    }
                    "value" { speedLimit.kmh }
                }
            }
            // TODO: OpenLR location reference
            "locationReference" {
                "GeometryLocationReference" {
                    "encodedGeometry" {
                        "gml:LineString" {
                            attribute("srsDimension", "2")
                            attribute("srsName", "EPSG::4326")
                            "gml:posList" {
                                val coordinates = speedLimit.geometry.coordinates
                                for (i in coordinates.indices) {
                                    +"${coordinates[i].y.toRounded(5)} ${coordinates[i].x.toRounded(5)}"
                                    if (i < coordinates.size - 1) {
                                        +" "
                                    }
                                }
                            }
                        }
                    }
                }
            }
            for (locationReference in speedLimit.locationReferences) {
                "locationReference" {
                    "OpenLRLocationReference" {
                        "binaryLocationReference" {
                            "BinaryLocationReference" {
                                "base64String" {
                                    marshaller.marshallToBase64String(locationReference)
                                }
                                "openLRBinaryVersion" {
                                    attribute("xlink:href", "http://spec.tn-its.eu/codelists/OpenLRBinaryVersionCode#v2_4")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {

        const val rootQName = "RoadFeatureDataset"

        val namespaces =
            mapOf(
                "" to "http://spec.tn-its.eu/schemas/",
                "xlink" to "http://www.w3.org/1999/xlink",
                "gml" to "http://www.opengis.net/gml/3.2.1",
                "xsi" to "http://www.w3.org/2001/XMLSchema-instance",
                "xsi:schemaLocation" to
                    "http://spec.tn-its.eu/schemas/ TNITS.xsd",
            )
    }
}
