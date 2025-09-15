package no.vegvesen.nvdb.tnits

import kotlinx.coroutines.flow.collectIndexed
import kotlinx.datetime.atStartOfDayIn
import no.vegvesen.nvdb.apiles.datakatalog.EgenskapstypeHeltallenum
import no.vegvesen.nvdb.tnits.Services.Companion.marshaller
import no.vegvesen.nvdb.tnits.extensions.OsloZone
import no.vegvesen.nvdb.tnits.extensions.toRounded
import no.vegvesen.nvdb.tnits.extensions.truncateToSeconds
import no.vegvesen.nvdb.tnits.model.EgenskapsTyper
import no.vegvesen.nvdb.tnits.model.VegobjektTyper
import no.vegvesen.nvdb.tnits.services.DatakatalogApi
import no.vegvesen.nvdb.tnits.utilities.measure
import no.vegvesen.nvdb.tnits.xml.XmlStreamDsl
import no.vegvesen.nvdb.tnits.xml.writeXmlDocument
import java.io.BufferedOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Instant

suspend fun DatakatalogApi.getKmhByEgenskapVerdi(): Map<Int, Int> = try {
    getVegobjekttype(VegobjektTyper.FARTSGRENSE)
        .egenskapstyper!!
        .filterIsInstance<EgenskapstypeHeltallenum>()
        .single { it.id == EgenskapsTyper.FARTSGRENSE }
        .tillatteVerdier
        .associate { it.id to it.verdi!! }
} catch (exception: Exception) {
    log.warn("Feil ved henting av vegobjekttype ${VegobjektTyper.FARTSGRENSE} fra datakatalogen: $exception. Bruker hardkodede verdier.")
    mapOf(
        19885 to 5,
        11576 to 20,
        2726 to 30,
        2728 to 40,
        2730 to 50,
        2732 to 60,
        2735 to 70,
        2738 to 80,
        2741 to 90,
        5087 to 100,
        9721 to 110,
        19642 to 120,
    )
}

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

suspend fun ParallelSpeedLimitProcessor.generateSpeedLimitsDeltaUpdate(now: Instant, since: Instant) {
    val path =
        Files.createTempFile("TNITS_SpeedLimits_${now.truncateToSeconds().toString().replace(":", "-")}_update", ".xml")
    log.info("Lagrer endringsdata for fartsgrenser til ${path.toAbsolutePath()}")

    val speedLimitsFlow = generateSpeedLimitsUpdate(since)

    writeXmlDocument(
        path,
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

suspend fun ParallelSpeedLimitProcessor.generateSpeedLimitsFullSnapshot(now: Instant) {
    val path =
        Files.createTempFile(
            "TNITS_SpeedLimits_${now.truncateToSeconds().toString().replace(":", "-")}_snapshot",
            ".xml",
        )
    log.info("Lagrer fullstendig fartsgrense-snapshot til ${path.toAbsolutePath()}")

    generateSpeedLimitsFullSnapshot(now, path)
}

suspend fun ParallelSpeedLimitProcessor.generateSpeedLimitsFullSnapshot(now: Instant, path: Path) {
    val speedLimitsFlow = generateSpeedLimitsSnapshot()

    BufferedOutputStream(Files.newOutputStream(path)).use { outputStream ->
        log.measure("Generating full snapshot", logStart = true) {
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
                "type" { "Snapshot" }
                "roadFeatures" {
                    speedLimitsFlow.collectIndexed { i, speedLimit ->
                        writeSpeedLimit(speedLimit, i)
                    }
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
