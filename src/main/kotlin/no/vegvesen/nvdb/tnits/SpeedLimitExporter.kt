package no.vegvesen.nvdb.tnits

import io.minio.MinioClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.datetime.atStartOfDayIn
import no.vegvesen.nvdb.tnits.Services.Companion.marshaller
import no.vegvesen.nvdb.tnits.config.ExportTarget
import no.vegvesen.nvdb.tnits.config.ExporterConfig
import no.vegvesen.nvdb.tnits.extensions.OsloZone
import no.vegvesen.nvdb.tnits.extensions.toRounded
import no.vegvesen.nvdb.tnits.extensions.truncateToSeconds
import no.vegvesen.nvdb.tnits.storage.S3OutputStream
import no.vegvesen.nvdb.tnits.utilities.WithLogger
import no.vegvesen.nvdb.tnits.utilities.measure
import no.vegvesen.nvdb.tnits.xml.XmlStreamDsl
import no.vegvesen.nvdb.tnits.xml.writeXmlDocument
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPOutputStream
import kotlin.time.Instant

class SpeedLimitExporter(
    private val speedLimitGenerator: SpeedLimitGenerator,
    private val exporterConfig: ExporterConfig,
    private val minioClient: MinioClient,
) : WithLogger {

    suspend fun generateSpeedLimitsDeltaUpdate(now: Instant, since: Instant) {
        val speedLimitsFlow = speedLimitGenerator.generateSpeedLimitsUpdate(since)

        exportSpeedLimits(now, speedLimitsFlow, ExportType.Update)
    }

    private fun generateS3Key(timestamp: Instant, exportType: ExportType, vegobjekttype: Int = 105): String =
        generateS3Key(timestamp, exportType, exporterConfig.gzip, vegobjekttype)

    fun openStream(path: Path): OutputStream {
        val bufferSize = 256 * 1024
        val fileOut = BufferedOutputStream(Files.newOutputStream(path), bufferSize)
        return if (exporterConfig.gzip) {
            BufferedOutputStream(GZIPOutputStream(fileOut), bufferSize)
        } else {
            fileOut
        }
    }

    fun openS3Stream(timestamp: Instant, exportType: ExportType): OutputStream {
        val bucket = exporterConfig.bucket
        val client = minioClient

        val objectKey = generateS3Key(timestamp, exportType)
        val contentType = if (exporterConfig.gzip) "application/gzip" else "application/xml"

        val s3Stream = S3OutputStream(client, bucket, objectKey, contentType)

        return if (exporterConfig.gzip) {
            val bufferSize = 256 * 1024
            BufferedOutputStream(GZIPOutputStream(s3Stream), bufferSize)
        } else {
            s3Stream
        }
    }

    suspend fun exportSpeedLimitsFullSnapshot(timestamp: Instant) {
        val speedLimitsFlow = speedLimitGenerator.generateSpeedLimitsSnapshot()

        exportSpeedLimits(timestamp, speedLimitsFlow, ExportType.Snapshot)
    }

    private suspend fun exportSpeedLimits(timestamp: Instant, speedLimitsFlow: Flow<SpeedLimit>, type: ExportType) {
        when (exporterConfig.target) {
            ExportTarget.File -> try {
                val path =
                    Files.createTempFile(
                        "TNITS_SpeedLimits_${timestamp.truncateToSeconds().toString().replace(":", "-")}_snapshot",
                        if (exporterConfig.gzip) ".xml.gz" else ".xml",
                    )
                log.info("Lagrer fullstendig fartsgrense-snapshot til ${path.toAbsolutePath()}")

                openStream(path).use { outputStream ->
                    writeSpeedLimitsToXml(timestamp, outputStream, speedLimitsFlow, type)
                }
            } catch (e: Exception) {
                log.error("Eksport til fil feilet", e)
            }

            ExportTarget.S3 -> try {
                val objectKey = generateS3Key(timestamp, type)
                log.info("Lagrer fullstendig fartsgrense-snapshot til S3: s3://${exporterConfig.bucket}/$objectKey")

                openS3Stream(timestamp, type).use { outputStream ->
                    writeSpeedLimitsToXml(timestamp, outputStream, speedLimitsFlow, type)
                }
                return
            } catch (e: Exception) {
                log.error("Eksport til S3 feilet", e)
            }
        }
    }

    enum class ExportType {
        Snapshot,
        Update,
    }

    suspend fun writeSpeedLimitsToXml(timestamp: Instant, outputStream: OutputStream, speedLimitsFlow: Flow<SpeedLimit>, exportType: ExportType) {
        log.measure("Generating $exportType", logStart = true) {
            writeXmlDocument(
                outputStream,
                rootQName = ROOT_QNAME,
                namespaces = namespaces,
            ) {
                "metadata" {
                    "Metadata" {
                        "datasetId" { "NVDB-TNITS-SpeedLimits_$timestamp" }
                        "datasetCreationTime" { timestamp }
                    }
                }
                "type" { exportType }
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

        const val ROOT_QNAME = "RoadFeatureDataset"

        val namespaces =
            mapOf(
                "" to "http://spec.tn-its.eu/schemas/",
                "xlink" to "http://www.w3.org/1999/xlink",
                "gml" to "http://www.opengis.net/gml/3.2.1",
                "xsi" to "http://www.w3.org/2001/XMLSchema-instance",
                "xsi:schemaLocation" to
                    "http://spec.tn-its.eu/schemas/ TNITS.xsd",
            )

        fun generateS3Key(timestamp: Instant, exportType: ExportType, gzip: Boolean, vegobjekttype: Int = 105): String {
            val paddedType = vegobjekttype.toString().padStart(4, '0')
            val timestampStr = timestamp.truncateToSeconds().toString().replace(":", "-")
            val extension = if (gzip) ".xml.gz" else ".xml"
            return "$paddedType-speed-limits/$timestampStr/${exportType.name.lowercase()}$extension"
        }
    }
}
