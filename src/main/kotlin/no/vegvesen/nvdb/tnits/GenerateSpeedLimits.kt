package no.vegvesen.nvdb.tnits

import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.todayIn
import no.vegvesen.nvdb.apiles.datakatalog.EgenskapstypeHeltallenum
import no.vegvesen.nvdb.apiles.uberiket.*
import no.vegvesen.nvdb.tnits.config.FETCH_SIZE
import no.vegvesen.nvdb.tnits.database.Veglenker
import no.vegvesen.nvdb.tnits.database.Vegobjekter
import no.vegvesen.nvdb.tnits.extensions.toRounded
import no.vegvesen.nvdb.tnits.geometry.*
import no.vegvesen.nvdb.tnits.geometry.SRID
import no.vegvesen.nvdb.tnits.model.Utstrekning
import no.vegvesen.nvdb.tnits.model.Veglenke
import no.vegvesen.nvdb.tnits.model.overlaps
import no.vegvesen.nvdb.tnits.serialization.kryo
import no.vegvesen.nvdb.tnits.vegobjekter.VegobjektStedfesting
import no.vegvesen.nvdb.tnits.vegobjekter.getStedfestingLinjer
import no.vegvesen.nvdb.tnits.xml.writeXmlDocument
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.measureTime

val OsloZone = TimeZone.of("Europe/Oslo")

object VegobjektTyper {
    const val FARTSGRENSE = 105
}

fun today() = Clock.System.todayIn(OsloZone)

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

suspend fun loadAllActiveVeglenker(): Map<Long, List<Veglenke>> =
    measure("Loading all active veglenker into memory", logStart = true) {
        newSuspendedTransaction {
            val veglenker =
                Veglenker
                    .selectAll()
                    .where { Veglenker.sluttdato greater today() }
                    .map { it.toVeglenke() }
                    .groupBy { it.veglenkesekvensId }

            println("Lastet ${veglenker.values.sumOf { it.size }} aktive veglenker for ${veglenker.size} veglenkesekvenser")
            veglenker
        }
    }

fun Instant.truncateToSeconds() = Instant.fromEpochSeconds(epochSeconds)

suspend fun generateSpeedLimitsFullSnapshot() {
    val now = Clock.System.now().truncateToSeconds()
    val path =
        Files.createTempFile("TNITS_SpeedLimits_${now.toString().replace(":", "-")}_complete", ".xml")
    println("Lagrer fullstendig fartsgrense-snapshot til ${path.toAbsolutePath()}")

    val speedLimitsFlow = generateSpeedLimits()
    measure("Generating full snapshot", logStart = true) {
        writeXmlDocument(
            path,
            rootQName = "RoadFeatureDataset",
            namespaces =
                mapOf(
                    "" to "http://spec.tn-its.eu/schemas/",
                    "xlink" to "http://www.w3.org/1999/xlink",
                    "gml" to "http://www.opengis.net/gml/3.2.1",
                    "xsi" to "http://www.w3.org/2001/XMLSchema-instance",
                    "xsi:schemaLocation" to
                        "http://spec.tn-its.eu/schemas/ TNITS.xsd",
                ),
            indent = "\t",
        ) {
            "metadata" {
                "Metadata" {
                    "datasetId" { "NVDB-TNITS-SpeedLimits_$now" }
                    "datasetCreationTime" { now }
                }
            }
            "type" { "Complete" }
            "roadFeatures" {
                speedLimitsFlow.collect { speedLimit ->
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
                                "type" { "Add" }
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
            }
        }
    }
}

suspend fun generateSpeedLimits(): Flow<SpeedLimit> {
    val veglenkerLookup = loadVeglenkerWithCache()
    return ParallelSpeedLimitProcessor(veglenkerLookup).generateSpeedLimits()
}

@Deprecated(
    "Use ParallelSpeedLimitProcessor.generateSpeedLimits() for better performance",
    ReplaceWith("ParallelSpeedLimitProcessor().generateSpeedLimits()"),
)
suspend fun generateSpeedLimitsSequential(): Sequence<SpeedLimit> {
    val kmhByEgenskapVerdi = getKmhByEgenskapVerdi()
    return newSuspendedTransaction {
        sequence {
            var paginationId = 0L
            var totalCount = 0
            while (true) {
                val vegobjekter =
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

                if (vegobjekter.isEmpty()) {
                    break
                }

                totalCount += vegobjekter.size

                paginationId = vegobjekter.last().id

                val stedfestingerByVegobjektId =
                    vegobjekter.associate {
                        it.id to it.getStedfestingLinjer()
                    }

                val veglenkesekvensIds =
                    stedfestingerByVegobjektId.flatMapTo(mutableSetOf()) {
                        it.value.map { stedfesting -> stedfesting.veglenkesekvensId }
                    }

                val overlappendeVeglenker =
                    Veglenker
                        .selectAll()
                        .where {
                            Veglenker.veglenkesekvensId inList veglenkesekvensIds and
                                (Veglenker.sluttdato greater today())
                        }.map {
                            it.toVeglenke()
                        }.groupBy { it.veglenkesekvensId }

                for (vegobjekt in vegobjekter) {
                    val kmh =
                        vegobjekt.egenskaper?.get(FartsgrenseEgenskapTypeIdString)?.let { egenskap ->
                            when (egenskap) {
                                is EnumEgenskap ->
                                    kmhByEgenskapVerdi[egenskap.verdi]
                                        ?: error("Ukjent verdi for fartsgrense: ${egenskap.verdi}")

                                else -> error("Expected HeltallEgenskap, got ${egenskap::class.simpleName}")
                            }
                        } ?: continue

                    val lineStrings =
                        stedfestingerByVegobjektId[vegobjekt.id]
                            .orEmpty()
                            .flatMap { stedfesting ->
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
                            ?: error("Kunne ikke lage geometri for fartsgrense ${vegobjekt.id}")

                    val speedLimit =
                        SpeedLimit(
                            id = vegobjekt.id,
                            kmh = kmh,
                            locationReferences = emptyList(),
                            validFrom = vegobjekt.gyldighetsperiode!!.startdato.toKotlinLocalDate(),
                            validTo = vegobjekt.gyldighetsperiode!!.sluttdato?.toKotlinLocalDate(),
                            geometry = geometry,
                        )

                    yield(speedLimit)
                }

                if (totalCount % 10000 == 0) {
                    println("Generert $totalCount fartsgrenser så langt...")
                }
            }
        }
    }
}

fun ResultRow.toVeglenke(): Veglenke =
    Veglenke(
        veglenkesekvensId = this[Veglenker.veglenkesekvensId],
        veglenkenummer = this[Veglenker.veglenkenummer],
        startposisjon = this[Veglenker.startposisjon].toDouble(),
        sluttposisjon = this[Veglenker.sluttposisjon].toDouble(),
        geometri = this[Veglenker.geometri].also { it.srid = SRID.UTM33 },
        typeVeg = this[Veglenker.typeVeg],
        detaljniva = this[Veglenker.detaljniva],
        superstedfesting =
            this[Veglenker.superstedfestingId]?.let { superstedfestingId ->
                StedfestingLinje().apply {
                    id = superstedfestingId
                    startposisjon = get(Veglenker.superstedfestingStartposisjon)?.toDouble() ?: 0.0
                    sluttposisjon = get(Veglenker.superstedfestingSluttposisjon)?.toDouble() ?: 0.0
                    kjorefelt = get(Veglenker.superstedfestingKjorefelt) ?: emptyList()
                }
            },
    )

fun Stedfesting.toStedfestingLinjer(): List<StedfestingLinje> =
    when (this) {
        is StedfestingLinjer -> linjer
        else -> TODO("Stedfesting type ${this::class.simpleName} not supported yet")
    }

fun Stedfesting.toVegobjektStedfestinger(
    vegobjektId: Long,
    vegobjektType: Int,
): List<VegobjektStedfesting> =
    when (this) {
        is StedfestingLinjer ->
            linjer.map {
                VegobjektStedfesting(
                    vegobjektId = vegobjektId,
                    vegobjektType = vegobjektType,
                    veglenkesekvensId = it.id,
                    startposisjon = it.startposisjon,
                    sluttposisjon = it.sluttposisjon,
                    retning = it.retning,
                    sideposisjon = it.sideposisjon,
                    kjorefelt = it.kjorefelt,
                )
            }

        else -> error("Forventet StedfestingLinjer, fikk ${this::class.simpleName}")
    }

fun Veglenke.overlaps(stedfesting: VegobjektStedfesting) = utstrekning.overlaps(stedfesting.utstrekning)

val VeglenkeMedId.utstrekning
    get(): Utstrekning = Utstrekning(veglenkesekvensId, startposisjon, sluttposisjon)

val Veglenke.utstrekning
    get(): Utstrekning = Utstrekning(veglenkesekvensId, startposisjon, sluttposisjon)

val VegobjektStedfesting.utstrekning
    get(): Utstrekning = Utstrekning(veglenkesekvensId, startposisjon, sluttposisjon)

const val FartsgrenseEgenskapTypeId = 2021

const val FartsgrenseEgenskapTypeIdString = FartsgrenseEgenskapTypeId.toString()

suspend fun getCacheFileTimestamp(): Long? =
    newSuspendedTransaction {
        Veglenker
            .selectAll()
            .maxByOrNull { it[Veglenker.sistEndret] }
            ?.get(Veglenker.sistEndret)
            ?.toInstant()
            ?.toEpochMilli()
    }

fun saveVeglenkerToCache(
    veglenker: Map<Long, List<Veglenke>>,
    cacheFile: File,
) {
    measure("Saving veglenker to cache") {
        cacheFile.parentFile?.mkdirs()
        FileOutputStream(cacheFile).use { fos ->
            Output(fos).use { output ->
                kryo.writeObject(output, veglenker)
            }
        }
        println("Veglenker cachet til ${cacheFile.absolutePath}")
    }
}

fun loadVeglenkerFromCache(cacheFile: File): Map<Long, List<Veglenke>>? =
    try {
        measure("Loading veglenker from cache") {
            FileInputStream(cacheFile).use { fis ->
                Input(fis).use { input ->
                    @Suppress("UNCHECKED_CAST")
                    kryo.readObject(input, HashMap::class.java) as Map<Long, List<Veglenke>>
                }
            }
        }
    } catch (e: Exception) {
        println("Kunne ikke laste veglenker fra cache: ${e.message}")
        null
    }

suspend fun loadVeglenkerWithCache(): Map<Long, List<Veglenke>> {
    val cacheFile = File("veglenker-cache.kryo")
    val cacheTimestamp = if (cacheFile.exists()) cacheFile.lastModified() else null
    val dbTimestamp = getCacheFileTimestamp()

    return if (cacheFile.exists() && cacheTimestamp != null && dbTimestamp != null && cacheTimestamp >= dbTimestamp) {
        println("Laster veglenker fra cache...")
        loadVeglenkerFromCache(cacheFile) ?: run {
            println("Cache feilet, laster fra database...")
            val veglenker = loadAllActiveVeglenker()
            saveVeglenkerToCache(veglenker, cacheFile)
            veglenker
        }
    } else {
        println("Cache er utdatert eller eksisterer ikke, laster fra database...")
        val veglenker = loadAllActiveVeglenker()
        saveVeglenkerToCache(veglenker, cacheFile)
        veglenker
    }
}
