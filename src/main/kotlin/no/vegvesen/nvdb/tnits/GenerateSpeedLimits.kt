package no.vegvesen.nvdb.tnits

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import no.vegvesen.nvdb.apiles.datakatalog.EgenskapstypeHeltallenum
import no.vegvesen.nvdb.tnits.extensions.get
import no.vegvesen.nvdb.tnits.extensions.put
import no.vegvesen.nvdb.tnits.extensions.toRounded
import no.vegvesen.nvdb.tnits.model.*
import no.vegvesen.nvdb.tnits.xml.XmlStreamDsl
import no.vegvesen.nvdb.tnits.xml.writeXmlDocument
import java.nio.file.Files
import kotlin.time.Instant
import kotlin.time.measureTime

val OsloZone = TimeZone.of("Europe/Oslo")

inline fun <T> measure(label: String, logStart: Boolean = false, block: () -> T): T {
    if (logStart) {
        println("Start: $label")
    }

    var result: T
    val time = measureTime { result = block() }
    println("${if (logStart) "End: " else "Timed: "}$label, time: $time")
    return result
}

suspend fun getKmhByEgenskapVerdi(): Map<Int, Int> = datakatalogApi
    .getVegobjekttype(VegobjektTyper.FARTSGRENSE)
    .egenskapstyper!!
    .filterIsInstance<EgenskapstypeHeltallenum>()
    .single { it.id == EgenskapsTyper.FARTSGRENSE }
    .tillatteVerdier
    .associate { it.id to it.verdi!! }

fun Instant.truncateToSeconds() = Instant.fromEpochSeconds(epochSeconds)

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

const val indent = "\t"

suspend fun generateSpeedLimitsDeltaUpdate(now: Instant) {
    val path =
        Files.createTempFile("TNITS_SpeedLimits_${now.truncateToSeconds().toString().replace(":", "-")}_update", ".xml")
    println("Lagrer endringsdata for fartsgrenser til ${path.toAbsolutePath()}")

    val since =
        keyValueStore.get<Instant>("last_speedlimit_snapshot")
            ?: keyValueStore.get<Instant>("last_speedlimit_update")
            ?: error("Ingen tidligere snapshot eller oppdateringstidspunkt funnet for fartsgrenser")

    val speedLimitsFlow = parallelSpeedLimitProcessor.generateSpeedLimitsUpdate(since)

    writeXmlDocument(
        path,
        rootQName = rootQName,
        namespaces = namespaces,
        indent = indent,
    ) {
        "metadata" {
            "Metadata" {
                "datasetId" { "NVDB-TNITS-SpeedLimits_$now" }
                "datasetCreationTime" { now }
            }
        }
        "type" { "Update" }
        "roadFeatures" {
            speedLimitsFlow.collect { speedLimit ->
                writeSpeedLimit(speedLimit)
            }
        }
    }
    keyValueStore.put("last_speedlimit_update", now)
}

suspend fun generateSpeedLimitsFullSnapshot(now: Instant) {
    val path =
        Files.createTempFile(
            "TNITS_SpeedLimits_${now.truncateToSeconds().toString().replace(":", "-")}_snapshot",
            ".xml",
        )
    println("Lagrer fullstendig fartsgrense-snapshot til ${path.toAbsolutePath()}")

    val speedLimitsFlow = generateSpeedLimitsSnapshot()

    measure("Generating full snapshot", logStart = true) {
        writeXmlDocument(
            path,
            rootQName = rootQName,
            namespaces = namespaces,
            indent = indent,
        ) {
            "metadata" {
                "Metadata" {
                    "datasetId" { "NVDB-TNITS-SpeedLimits_$now" }
                    "datasetCreationTime" { now }
                }
            }
            "type" { "Snapshot" }
            "roadFeatures" {
                speedLimitsFlow.collect { speedLimit ->
                    writeSpeedLimit(speedLimit)
                }
            }
        }
    }
    keyValueStore.put("last_speedlimit_snapshot", now)
}

private fun XmlStreamDsl.writeSpeedLimit(speedLimit: SpeedLimit) {
    "RoadFeature" {
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
            speedLimit.validFrom.atStartOfDayIn(OsloZone)
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

val parallelSpeedLimitProcessor =
    ParallelSpeedLimitProcessor(
        veglenkerBatchLookup = { ids ->
            ids.associateWith {
                cachedVegnett.getVeglenker(it)
            }
        },
    )

fun generateSpeedLimitsSnapshot(): Flow<SpeedLimit> = parallelSpeedLimitProcessor.generateSpeedLimitsSnapshot()

val Veglenke.utstrekning
    get(): StedfestingUtstrekning =
        StedfestingUtstrekning(
            veglenkesekvensId,
            startposisjon,
            sluttposisjon,
            null,
            feltoversikt,
        )

val VegobjektStedfesting.utstrekning
    get(): StedfestingUtstrekning =
        StedfestingUtstrekning(
            veglenkesekvensId,
            startposisjon,
            sluttposisjon,
            retning,
            kjorefelt,
        )

const val FartsgrenseEgenskapTypeIdString = EgenskapsTyper.FARTSGRENSE.toString()
