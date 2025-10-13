package no.vegvesen.nvdb.tnits.inspire

import io.minio.MinioClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import no.vegvesen.nvdb.tnits.config.ExportTarget
import no.vegvesen.nvdb.tnits.config.ExporterConfig
import no.vegvesen.nvdb.tnits.export.openFileStream
import no.vegvesen.nvdb.tnits.export.openS3Stream
import no.vegvesen.nvdb.tnits.extensions.toRounded
import no.vegvesen.nvdb.tnits.extensions.truncateToSeconds
import no.vegvesen.nvdb.tnits.geometry.SRID
import no.vegvesen.nvdb.tnits.geometry.projectTo
import no.vegvesen.nvdb.tnits.model.Veglenke
import no.vegvesen.nvdb.tnits.openlr.toFormOfWay
import no.vegvesen.nvdb.tnits.utilities.WithLogger
import no.vegvesen.nvdb.tnits.utilities.measure
import no.vegvesen.nvdb.tnits.vegnett.CachedVegnett
import no.vegvesen.nvdb.tnits.xml.XmlStreamDsl
import no.vegvesen.nvdb.tnits.xml.writeXmlDocument
import org.locationtech.jts.geom.LineString
import org.openlr.map.FormOfWay
import org.openlr.map.FunctionalRoadClass
import java.io.OutputStream
import java.nio.file.Files
import kotlin.time.Instant

class InspireRoadNetExporter(
    private val cachedVegnett: CachedVegnett,
    private val exporterConfig: ExporterConfig,
    private val minioClient: MinioClient,
) : WithLogger {

    suspend fun exportRoadNet(timestamp: Instant) {
        log.info("Eksporterer INSPIRE RoadLink vegnett...")

        val roadLinkFlow = generateRoadLinks()

        when (exporterConfig.target) {
            ExportTarget.File -> exportToFile(timestamp, roadLinkFlow)
            ExportTarget.S3 -> exportToS3(timestamp, roadLinkFlow)
        }
    }

    private suspend fun generateRoadLinks(): Flow<InspireRoadLink> = withContext(Dispatchers.Default) {
        val allVeglenker = cachedVegnett.getAllVeglenker()
        log.info("Genererer INSPIRE RoadLinks fra ${allVeglenker.size} veglenkesekvenser (cached)...")

        allVeglenker.values
            .asFlow()
            .map { veglenkerInSekvens ->
                veglenkerInSekvens
                    .filter { it.isTopLevel && it.isActive() }
                    .map { veglenke -> veglenke.toInspireRoadLink() }
            }
            .flatMapConcat { it.asFlow() }
    }

    private fun Veglenke.toInspireRoadLink(): InspireRoadLink {
        val roadLinkId = "$veglenkesekvensId-$veglenkenummer"
        val utm33Geometry = geometri.projectTo(SRID.UTM33) as LineString

        val frc = cachedVegnett.getFrc(this)
        val fow = typeVeg.toFormOfWay()

        return InspireRoadLink(
            id = roadLinkId,
            geometry = utm33Geometry,
            functionalRoadClass = frc,
            formOfWay = fow,
        )
    }

    private suspend fun exportToFile(timestamp: Instant, roadLinkFlow: Flow<InspireRoadLink>) {
        try {
            val path = Files.createTempFile(
                "INSPIRE_RoadNet_${timestamp.truncateToSeconds().toString().replace(":", "-")}",
                if (exporterConfig.gzip) ".xml.gz" else ".xml",
            )
            log.info("Lagrer INSPIRE RoadLink eksport til ${path.toAbsolutePath()}")

            exporterConfig.openFileStream(path).use { outputStream ->
                writeRoadLinksToXml(timestamp, outputStream, roadLinkFlow)
            }
        } catch (e: Exception) {
            log.error("Eksport til fil feilet", e)
        }
    }

    private suspend fun exportToS3(timestamp: Instant, roadLinkFlow: Flow<InspireRoadLink>) {
        try {
            val objectKey = generateS3Key(timestamp)
            log.info("Lagrer INSPIRE RoadLink eksport til S3: s3://${exporterConfig.bucket}/$objectKey")

            exporterConfig.openS3Stream(minioClient, objectKey).use { outputStream ->
                writeRoadLinksToXml(timestamp, outputStream, roadLinkFlow)
            }
        } catch (e: Exception) {
            log.error("Eksport til S3 feilet", e)
        }
    }

    private suspend fun writeRoadLinksToXml(timestamp: Instant, outputStream: OutputStream, roadLinkFlow: Flow<InspireRoadLink>) {
        log.measure("Generating INSPIRE RoadLink export", logStart = true) {
            writeXmlDocument(
                outputStream,
                rootQName = "wfs:FeatureCollection",
                namespaces = INSPIRE_NAMESPACES,
            ) {
                attribute("timeStamp", timestamp.truncateToSeconds().toString())
                attribute("numberMatched", "unknown")
                attribute("numberReturned", "0")

                var count = 0
                roadLinkFlow.collect { roadLink ->
                    writeRoadLink(roadLink)
                    count++
                    if (count % 10000 == 0) {
                        log.debug("Skrev {} RoadLinks", count)
                    }
                }
                log.info("Skrev totalt {} RoadLinks", count)
            }
        }
    }

    private fun XmlStreamDsl.writeRoadLink(roadLink: InspireRoadLink) {
        "wfs:member" {
            "tn-ro:RoadLink" {
                attribute("gml:id", "RoadLink.${roadLink.id}")

                "net:inspireId" {
                    "base:Identifier" {
                        "base:localId" { text(roadLink.id) }
                        "base:namespace" { text("http://data.geonorge.no/inspire/tn-ro") }
                    }
                }

                "net:centrelineGeometry" {
                    "gml:LineString" {
                        attribute("gml:id", "RoadLink.${roadLink.id}_NET_CENTRELINEGEOMETRY")
                        attribute("srsName", "EPSG:25833")
                        "gml:posList" {
                            val coords = roadLink.geometry.coordinates.joinToString(" ") {
                                "${it.x.toRounded(2)} ${it.y.toRounded(2)}"
                            }
                            text(coords)
                        }
                    }
                }

                roadLink.functionalRoadClass?.let { frc ->
                    "tn-ro:functionalRoadClass" {
                        text(frc.toInspireValue())
                    }
                }

                "tn-ro:formOfWay" {
                    text(roadLink.formOfWay.toInspireValue())
                }
            }
        }
    }

    private fun generateS3Key(timestamp: Instant): String {
        val timestampStr = timestamp.truncateToSeconds().toString().replace(":", "-")
        val extension = if (exporterConfig.gzip) ".xml.gz" else ".xml"
        return "inspire-roadnet/$timestampStr/roadlinks$extension"
    }

    companion object {
        val INSPIRE_NAMESPACES = mapOf(
            "wfs" to "http://www.opengis.net/wfs/2.0",
            "gml" to "http://www.opengis.net/gml/3.2",
            "tn-ro" to "http://inspire.ec.europa.eu/schemas/tn-ro/5.0",
            "net" to "http://inspire.ec.europa.eu/schemas/net/5.0",
            "base" to "http://inspire.ec.europa.eu/schemas/base/4.0",
            "xsi" to "http://www.w3.org/2001/XMLSchema-instance",
            "xsi:schemaLocation" to
                "http://www.opengis.net/wfs/2.0 http://schemas.opengis.net/wfs/2.0/wfs.xsd http://www.opengis.net/gml/3.2 http://schemas.opengis.net/gml/3.2.1/gml.xsd http://inspire.ec.europa.eu/schemas/tn-ro/5.0 https://inspire.ec.europa.eu/schemas/tn-ro/5.0/RoadTransportNetwork.xsd http://inspire.ec.europa.eu/schemas/base/4.0 https://inspire.ec.europa.eu/schemas/base/4.0/BaseTypes.xsd http://inspire.ec.europa.eu/schemas/net/5.0 https://inspire.ec.europa.eu/schemas/net/5.0/Network.xsd",
        )

        private fun FunctionalRoadClass.toInspireValue(): String = when (this) {
            FunctionalRoadClass.FRC_0 -> "mainRoad"
            FunctionalRoadClass.FRC_1 -> "firstClassRoad"
            FunctionalRoadClass.FRC_2 -> "secondClassRoad"
            FunctionalRoadClass.FRC_3 -> "thirdClassRoad"
            FunctionalRoadClass.FRC_4 -> "fourthClassRoad"
            FunctionalRoadClass.FRC_5 -> "fifthClassRoad"
            FunctionalRoadClass.FRC_6 -> "sixthClassRoad"
            FunctionalRoadClass.FRC_7 -> "seventhClassRoad"
        }

        private fun FormOfWay.toInspireValue(): String = when (this) {
            FormOfWay.MOTORWAY -> "motorway"
            FormOfWay.MULTIPLE_CARRIAGEWAY -> "dualCarriageway"
            FormOfWay.SINGLE_CARRIAGEWAY -> "singleCarriageway"
            FormOfWay.ROUNDABOUT -> "roundabout"
            FormOfWay.TRAFFIC_SQUARE -> "trafficSquare"
            FormOfWay.SLIP_ROAD -> "slipRoad"
            FormOfWay.OTHER -> "singleCarriageway"
            FormOfWay.UNDEFINED -> "singleCarriageway"
        }
    }
}

data class InspireRoadLink(
    val id: String,
    val geometry: LineString,
    val functionalRoadClass: FunctionalRoadClass?,
    val formOfWay: FormOfWay,
)
