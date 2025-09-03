package no.vegvesen.nvdb.tnits

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toKotlinLocalDate
import no.vegvesen.nvdb.apiles.datakatalog.EgenskapstypeHeltallenum
import no.vegvesen.nvdb.apiles.uberiket.EnumEgenskap
import no.vegvesen.nvdb.tnits.config.FETCH_SIZE
import no.vegvesen.nvdb.tnits.database.KeyValue
import no.vegvesen.nvdb.tnits.database.Vegobjekter
import no.vegvesen.nvdb.tnits.extensions.get
import no.vegvesen.nvdb.tnits.extensions.put
import no.vegvesen.nvdb.tnits.extensions.toRounded
import no.vegvesen.nvdb.tnits.geometry.*
import no.vegvesen.nvdb.tnits.model.StedfestingUtstrekning
import no.vegvesen.nvdb.tnits.model.Veglenke
import no.vegvesen.nvdb.tnits.vegobjekter.VegobjektStedfesting
import no.vegvesen.nvdb.tnits.vegobjekter.getStedfestingLinjer
import no.vegvesen.nvdb.tnits.xml.XmlStreamDsl
import no.vegvesen.nvdb.tnits.xml.writeXmlDocument
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import java.nio.file.Files
import kotlin.time.Instant
import kotlin.time.measureTime

val OsloZone = TimeZone.of("Europe/Oslo")

object VegobjektTyper {
    const val FARTSGRENSE = 105
    const val FUNKSJONELL_VEGKLASSE = 821
}

inline fun <T> measure(
    label: String,
    logStart: Boolean = false,
    block: () -> T,
): T {
    if (logStart) {
        println("Start: $label")
    }

    var result: T
    val time = measureTime { result = block() }
    println("${if (logStart) "End: " else "Timed: "}$label, time: $time")
    return result
}

suspend fun getKmhByEgenskapVerdi(): Map<Int, Int> =
    datakatalogApi
        .getVegobjekttype(VegobjektTyper.FARTSGRENSE)
        .egenskapstyper!!
        .filterIsInstance<EgenskapstypeHeltallenum>()
        .single { it.id == FartsgrenseEgenskapTypeId }
        .tillatteVerdier
        .associate { it.id to it.verdi!! }

fun Instant.truncateToSeconds() = Instant.fromEpochSeconds(epochSeconds)

val rootQName = "RoadFeatureDataset"

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
        KeyValue.get<Instant>("last_speedlimit_snapshot")
            ?: KeyValue.get<Instant>("last_speedlimit_update")
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
    KeyValue.put("last_speedlimit_update", now)
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
    KeyValue.put("last_speedlimit_snapshot", now)
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

@Deprecated(
    "Use ParallelSpeedLimitProcessor.generateSpeedLimits() for better performance",
    ReplaceWith("ParallelSpeedLimitProcessor().generateSpeedLimits()"),
)
fun generateSpeedLimitsSequential(): Flow<SpeedLimit> =
    flow {
        val kmhByEgenskapVerdi = getKmhByEgenskapVerdi()

        var paginationId = 0L
        var totalCount = 0
        while (true) {
            val vegobjekter =
                newSuspendedTransaction {
                    Vegobjekter
                        .select(Vegobjekter.data)
                        .where {
                            (Vegobjekter.vegobjektType eq VegobjektTyper.FARTSGRENSE) and
                                (Vegobjekter.vegobjektId greater paginationId)
                        }.orderBy(Vegobjekter.vegobjektId)
                        .limit(FETCH_SIZE)
                        .map {
                            it[Vegobjekter.data]
                        }
                }

            if (vegobjekter.isEmpty()) {
                break
            }

            totalCount += vegobjekter.size
            paginationId = vegobjekter.last().id

            val stedfestingerByVegobjektId =
                vegobjekter.associate {
                    it.id to it.getStedfestingLinjer()
                }

            for (vegobjekt in vegobjekter) {
                val kmh =
                    vegobjekt.egenskaper?.get(FartsgrenseEgenskapTypeIdString)?.let { egenskap ->
                        when (egenskap) {
                            is EnumEgenskap ->
                                kmhByEgenskapVerdi[egenskap.verdi]
                                    ?: error("Ukjent verdi for fartsgrense: ${egenskap.verdi}")

                            else -> error("Expected EnumEgenskap, got ${egenskap::class.simpleName}")
                        }
                    } ?: continue

                val stedfestingLinjer = stedfestingerByVegobjektId[vegobjekt.id].orEmpty()
                val veglenkesekvensIds = stedfestingLinjer.map { it.veglenkesekvensId }.toSet()

                // Batch fetch veglenker from RocksDB for better performance
                val overlappendeVeglenker = veglenkerRepository.batchGet(veglenkesekvensIds)

                val lineStrings =
                    stedfestingLinjer.flatMap { stedfesting ->
                        overlappendeVeglenker[stedfesting.veglenkesekvensId].orEmpty().mapNotNull { veglenke ->
                            calculateIntersectingGeometry(
                                veglenke.geometri,
                                veglenke.utstrekning,
                                stedfesting.utstrekning,
                            )
                        }
                    }

                val geometry =
                    mergeGeometries(lineStrings)?.simplify(1.0)?.projectTo(SRID.WGS84)
                        ?: continue // Skip if we can't create geometry

                val speedLimit =
                    SpeedLimit(
                        id = vegobjekt.id,
                        kmh = kmh,
                        locationReferences = emptyList(),
                        validFrom = vegobjekt.gyldighetsperiode!!.startdato.toKotlinLocalDate(),
                        validTo = vegobjekt.gyldighetsperiode!!.sluttdato?.toKotlinLocalDate(),
                        geometry = geometry,
                        updateType = UpdateType.Add,
                    )

                emit(speedLimit)
            }

            if (totalCount % 10000 == 0) {
                println("Generert $totalCount fartsgrenser så langt...")
            }
        }
    }

val Veglenke.utstrekning
    get(): StedfestingUtstrekning = StedfestingUtstrekning(veglenkesekvensId, startposisjon, sluttposisjon)

val VegobjektStedfesting.utstrekning
    get(): StedfestingUtstrekning = StedfestingUtstrekning(veglenkesekvensId, startposisjon, sluttposisjon)

const val FartsgrenseEgenskapTypeId = 2021

const val FartsgrenseEgenskapTypeIdString = FartsgrenseEgenskapTypeId.toString()
