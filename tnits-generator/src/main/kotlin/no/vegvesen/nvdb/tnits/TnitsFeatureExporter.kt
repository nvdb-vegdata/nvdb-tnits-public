package no.vegvesen.nvdb.tnits

import io.minio.MinioClient
import kotlinx.coroutines.flow.*
import kotlinx.datetime.atStartOfDayIn
import no.vegvesen.nvdb.apiles.uberiket.Retning
import no.vegvesen.nvdb.tnits.config.ExportTarget
import no.vegvesen.nvdb.tnits.config.ExporterConfig
import no.vegvesen.nvdb.tnits.extensions.OsloZone
import no.vegvesen.nvdb.tnits.extensions.toRounded
import no.vegvesen.nvdb.tnits.extensions.truncateToSeconds
import no.vegvesen.nvdb.tnits.model.*
import no.vegvesen.nvdb.tnits.storage.RocksDbContext
import no.vegvesen.nvdb.tnits.storage.S3OutputStream
import no.vegvesen.nvdb.tnits.storage.VegobjektChange
import no.vegvesen.nvdb.tnits.storage.VegobjekterHashStore
import no.vegvesen.nvdb.tnits.utilities.WithLogger
import no.vegvesen.nvdb.tnits.utilities.measure
import no.vegvesen.nvdb.tnits.xml.XmlStreamDsl
import no.vegvesen.nvdb.tnits.xml.writeXmlDocument
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.MultiLineString
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPOutputStream
import kotlin.time.Instant

class TnitsFeatureExporter(
    private val tnitsFeatureGenerator: TnitsFeatureGenerator,
    private val exporterConfig: ExporterConfig,
    private val minioClient: MinioClient,
    private val hashStore: VegobjekterHashStore,
    private val rocksDbContext: RocksDbContext,
) : WithLogger {

    suspend fun exportUpdate(timestamp: Instant, featureType: ExportedFeatureType, changes: Collection<VegobjektChange>) {
        val changesById = changes.associate { it.id to it.changeType }
        val featureFlow = tnitsFeatureGenerator.generateFeaturesUpdate(featureType, changesById)
            .chunked(1000).map { features ->

                val hashes = hashStore.batchGet(featureType.typeId, features.map { it.id })

                features.filter { hashes[it.id] != it.hash }
            }
            .flatMapConcat { it.asFlow() }

        exportFeatures(timestamp, featureType, featureFlow, ExportType.Update)

        rocksDbContext.writeBatch {
            featureFlow.chunked(1000).collect { featuresToUpdateHash ->
                val hashesById = featuresToUpdateHash.associate { it.id to it.hash }
                hashStore.batchUpdate(featureType.typeId, hashesById)
            }
        }
    }

    fun generateS3Key(timestamp: Instant, exportType: ExportType, featureType: ExportedFeatureType): String =
        generateS3Key(timestamp, exportType, exporterConfig.gzip, featureType)

    fun openStream(path: Path): OutputStream {
        val bufferSize = 256 * 1024
        val fileOut = BufferedOutputStream(Files.newOutputStream(path), bufferSize)
        return if (exporterConfig.gzip) {
            BufferedOutputStream(GZIPOutputStream(fileOut), bufferSize)
        } else {
            fileOut
        }
    }

    fun openS3Stream(objectKey: String): OutputStream {
        val bucket = exporterConfig.bucket
        val client = minioClient

        val contentType = if (exporterConfig.gzip) "application/gzip" else "application/xml"

        val s3Stream = S3OutputStream(client, bucket, objectKey, contentType)

        return if (exporterConfig.gzip) {
            val bufferSize = 256 * 1024
            BufferedOutputStream(GZIPOutputStream(s3Stream), bufferSize)
        } else {
            s3Stream
        }
    }

    suspend fun exportSnapshot(timestamp: Instant, featureType: ExportedFeatureType) {
        log.info("Eksporterer fullt snapshot av ${featureType.typeCode}...")
        val speedLimitsFlow = tnitsFeatureGenerator.generateSnapshot(featureType)

        exportFeatures(timestamp, featureType, speedLimitsFlow, ExportType.Snapshot)
    }

    private suspend fun exportFeatures(timestamp: Instant, featureType: ExportedFeatureType, featureFlow: Flow<TnitsFeature>, exportType: ExportType) {
        when (exporterConfig.target) {
            ExportTarget.File -> try {
                val path =
                    Files.createTempFile(
                        "TNITS_${featureType.name}_${timestamp.truncateToSeconds().toString().replace(":", "-")}_snapshot",
                        if (exporterConfig.gzip) ".xml.gz" else ".xml",
                    )
                log.info("Lagrer $exportType eksport av $featureType til ${path.toAbsolutePath()}")

                openStream(path).use { outputStream ->
                    writeFeaturesToXml(timestamp, outputStream, featureType, featureFlow, exportType)
                }
            } catch (e: Exception) {
                log.error("Eksport til fil feilet", e)
            }

            ExportTarget.S3 -> try {
                val objectKey = generateS3Key(timestamp, exportType, featureType)
                log.info("Lagrer $exportType eksport av $featureType til S3: s3://${exporterConfig.bucket}/$objectKey")

                openS3Stream(objectKey).use { outputStream ->
                    writeFeaturesToXml(timestamp, outputStream, featureType, featureFlow, exportType)
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

    suspend fun writeFeaturesToXml(
        timestamp: Instant,
        outputStream: OutputStream,
        featureType: ExportedFeatureType,
        featureFlow: Flow<TnitsFeature>,
        exportType: ExportType,
    ) {
        log.measure("Generating $exportType", logStart = true) {
            writeXmlDocument(
                outputStream,
                rootQName = ROOT_QNAME,
                namespaces = namespaces,
            ) {
                "metadata" {
                    "Metadata" {
                        "datasetId" { "NVDB-TNITS-${featureType}_${exportType}_${timestamp.truncateToSeconds()}" }
                        "datasetCreationTime" { timestamp }
                    }
                }
                "type" { exportType }
                featureFlow.collectIndexed { i, feature ->
                    writeFeature(feature, i)
                }
            }
        }
    }

    private fun XmlStreamDsl.writeFeatureProperty(property: RoadFeatureProperty) {
        when (property) {
            is IntProperty -> text(property.value.toString())
        }
    }

    private fun XmlStreamDsl.writeFeature(feature: TnitsFeature, index: Int) {
        "roadFeatures" {
            "RoadFeature" {
                if (feature is TnitsFeatureUpsert) {
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
                                            attribute("srsName", "EPSG::4326")
                                            "gml:posList" {
                                                lineString.coordinates.joinToString(" ") { "${it.y.toRounded(5)} ${it.x.toRounded(5)}" }
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
        return "$startposisjon-$sluttposisjon@$veglenkesekvensId:$retning:$sideposisjon:$kjorefelt"
    }

    companion object {

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

        fun generateS3Key(timestamp: Instant, exportType: ExportType, gzip: Boolean, featureType: ExportedFeatureType): String {
            val typePrefix = getTypePrefix(featureType)
            val timestampStr = timestamp.truncateToSeconds().toString().replace(":", "-")
            val extension = if (gzip) ".xml.gz" else ".xml"
            return "$typePrefix/$timestampStr/${exportType.name.lowercase()}$extension"
        }

        fun getTypePrefix(featureType: ExportedFeatureType): String {
            val paddedType = featureType.typeId.toString().padStart(4, '0')
            val typePrefix = "$paddedType-${featureType.typeCode}"
            return typePrefix
        }
    }
}
