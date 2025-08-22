package no.vegvesen.nvdb.tnits

import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import no.vegvesen.nvdb.apiles.datakatalog.EgenskapstypeHeltallenum
import no.vegvesen.nvdb.apiles.uberiket.EnumEgenskap
import no.vegvesen.nvdb.apiles.uberiket.StedfestingLinje
import no.vegvesen.nvdb.apiles.uberiket.VeglenkeMedId
import no.vegvesen.nvdb.tnits.config.FETCH_SIZE
import no.vegvesen.nvdb.tnits.database.Stedfestinger
import no.vegvesen.nvdb.tnits.database.Veglenker
import no.vegvesen.nvdb.tnits.database.Vegobjekter
import no.vegvesen.nvdb.tnits.model.Veglenke
import no.vegvesen.nvdb.tnits.vegobjekter.VegobjektStedfesting
import no.vegvesen.nvdb.tnits.xml.xmlDocument
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.locationtech.jts.geom.Geometry
import org.openlr.locationreference.LineLocationReference
import java.nio.file.Files
import kotlin.time.Clock

val OsloZone = TimeZone.of("Europe/Oslo")

object VegobjektTyper {
    const val FARTSGRENSE = 105
}

fun today() = Clock.System.todayIn(OsloZone)

suspend fun getKmhByEgenskapVerdi(): Map<Int, Int> =
    datakatalogApi
        .getVegobjekttype(VegobjektTyper.FARTSGRENSE)
        .egenskapstyper!!
        .filterIsInstance<EgenskapstypeHeltallenum>()
        .single { it.id == FartsgrenseEgenskapTypeId }
        .tillatteVerdier
        .associate { it.id to it.verdi!! }

suspend fun generateSpeedLimitsFullSnapshot() {
    val path =
        Files.createTempFile("TNITS_SpeedLimits_${Clock.System.now().toString().replace(":", "-")}_complete", ".xml")
    println("Lagrer fullstendig fartsgrense-snapshot til ${path.toAbsolutePath()}")
    xmlDocument(
        path,
        rootQName = "tnits:FeatureCollection",
        namespaces =
            mapOf(
                "tnits" to "http://vegvesen.no/tnits",
                "gml" to "http://www.opengis.net/gml/3.2",
                "xsi" to "http://www.w3.org/2001/XMLSchema-instance",
            ),
        indent = "\t",
    ) {
    }
}

suspend fun generateSequence(): Sequence<SpeedLimit> {
    val kmhByEgenskapVerdi = getKmhByEgenskapVerdi()

    return sequence {
        // TODO: Paginate
        var paginationId = 0L

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

        val ids = vegobjekter.map { it.id }

        val stedfestingerByVegobjektId =
            Stedfestinger
                .selectAll()
                .where {
                    Stedfestinger.vegobjektId inList ids
                }.map {
                    VegobjektStedfesting(
                        vegobjektId = it[Stedfestinger.vegobjektId],
                        vegobjektType = it[Stedfestinger.vegobjektType],
                        veglenkesekvensId = it[Stedfestinger.veglenkesekvensId],
                        startposisjon = it[Stedfestinger.startposisjon].toDouble(),
                        sluttposisjon = it[Stedfestinger.sluttposisjon].toDouble(),
                        retning = it[Stedfestinger.retning],
                        sideposisjon = it[Stedfestinger.sideposisjon],
                        kjorefelt = it[Stedfestinger.kjorefelt],
                    )
                }.groupBy { it.vegobjektId }

        val veglenkesekvensIds =
            stedfestingerByVegobjektId.flatMapTo(mutableSetOf()) { stedfesting -> stedfesting.value.map { it.vegobjektId } }

        val overlappendeVeglenker =
            Veglenker
                .selectAll()
                .where {
                    Veglenker.veglenkesekvensId inList veglenkesekvensIds
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

//            var lineLocations =
//                stedfestingerByVegobjektId[vegobjekt.id]?.flatMap { stedfesting ->
//                    overlappendeVeglenker[stedfesting.veglenkesekvensId]
//                        .orEmpty()
//                        .filter {
//                            it.overlaps(stedfesting)
//                        }.map { veglenke ->
//                            val intersection = veglenke.utstrekning.intersect(stedfesting.utstrekning)!!
//                        }
//                }

//            val geometry =

//            yield(
//                SpeedLimit(
//                    id = vegobjekt.id,
//                    kmh = kmh
//                )
//            )
        }
    }
}

fun ResultRow.toVeglenke(): Veglenke =
    Veglenke(
        veglenkesekvensId = this[Veglenker.veglenkesekvensId],
        veglenkenummer = this[Veglenker.veglenkenummer],
        startposisjon = this[Veglenker.startposisjon].toDouble(),
        sluttposisjon = this[Veglenker.sluttposisjon].toDouble(),
        geometri = this[Veglenker.geometri],
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

data class Utstrekning(
    val veglenkesekvensId: Long,
    val startposisjon: Double,
    val sluttposisjon: Double,
)

fun VeglenkeMedId.overlaps(stedfesting: VegobjektStedfesting) = utstrekning.overlaps(stedfesting.utstrekning)

fun Utstrekning.intersect(other: Utstrekning): Utstrekning? =
    if (overlaps(other)) {
        Utstrekning(
            veglenkesekvensId,
            maxOf(startposisjon, other.startposisjon),
            minOf(sluttposisjon, other.sluttposisjon),
        )
    } else {
        null
    }

fun Utstrekning.overlaps(other: Utstrekning): Boolean =
    veglenkesekvensId == other.veglenkesekvensId &&
        startposisjon < other.sluttposisjon &&
        sluttposisjon > other.startposisjon

val VeglenkeMedId.utstrekning
    get(): Utstrekning {
        return Utstrekning(veglenkesekvensId, startposisjon, sluttposisjon)
    }

val VegobjektStedfesting.utstrekning
    get(): Utstrekning {
        return Utstrekning(veglenkesekvensId, startposisjon, sluttposisjon)
    }

object EgenskapsTyper {
    const val FARTSGRENSE = 2021
}

const val FartsgrenseEgenskapTypeId = 2021

const val FartsgrenseEgenskapTypeIdString = FartsgrenseEgenskapTypeId.toString()

data class SpeedLimit(
    val id: Long,
    val kmh: Int,
    val geometry: Geometry,
    val locationReferences: List<LineLocationReference>,
)
