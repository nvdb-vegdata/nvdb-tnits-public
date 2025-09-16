package no.vegvesen.nvdb.tnits

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import no.vegvesen.nvdb.apiles.datakatalog.EgenskapstypeHeltallenum
import no.vegvesen.nvdb.tnits.extensions.OsloZone
import no.vegvesen.nvdb.tnits.extensions.toOffsetDateTime
import no.vegvesen.nvdb.tnits.gateways.DatakatalogApi
import no.vegvesen.nvdb.tnits.geometry.*
import no.vegvesen.nvdb.tnits.model.*
import no.vegvesen.nvdb.tnits.openlr.OpenLrService
import no.vegvesen.nvdb.tnits.storage.VegobjekterRepository
import no.vegvesen.nvdb.tnits.utilities.WithLogger
import no.vegvesen.nvdb.tnits.utilities.measure
import kotlin.time.Instant

typealias VeglenkerBatchLookup = (Collection<Long>) -> Map<Long, List<Veglenke>>

data class SpeedLimitWorkItem(
    val id: Long,
    val kmh: Int,
    val stedfestingLinjer: List<VegobjektStedfesting>,
    val validFrom: LocalDate,
    val validTo: LocalDate?,
    val beginLifespanVersion: Instant,
)

data class IdRange(val startId: Long, val endId: Long)

class SpeedLimitGenerator(
    private val veglenkerBatchLookup: VeglenkerBatchLookup,
    private val datakatalogApi: DatakatalogApi,
    private val openLrService: OpenLrService,
    private val workerCount: Int = Runtime.getRuntime().availableProcessors(),
    private val vegobjekterRepository: VegobjekterRepository,
) : WithLogger {
    private val fetchSize = 1000
    private val superBatchSize = workerCount * fetchSize

    private suspend fun DatakatalogApi.getKmhByEgenskapVerdi(): Map<Int, Int> = try {
        getVegobjekttype(VegobjektTyper.FARTSGRENSE)
            .egenskapstyper!!
            .filterIsInstance<EgenskapstypeHeltallenum>()
            .single { it.id == EgenskapsTyper.FARTSGRENSE }
            .tillatteVerdier
            .associate { it.id to it.verdi!! }
    } catch (exception: Exception) {
        log.warn("Feil ved henting av vegobjekttype ${VegobjektTyper.FARTSGRENSE} fra datakatalogen: $exception. Bruker hardkodede verdier.")
        hardcodedFartsgrenseTillatteVerdier
    }

    fun generateSpeedLimitsUpdate(since: Instant): Flow<SpeedLimit> = flow {
        val kmhByEgenskapVerdi = datakatalogApi.getKmhByEgenskapVerdi()

        var paginationId = 0L
        var totalCount = 0

        val sinceOffset = since.toOffsetDateTime()

//        while (true) {
//            val changedIds =
//                transaction {
//                    Vegobjekter
//                        .select(Vegobjekter.vegobjektId)
//                        .where {
//                            (Vegobjekter.vegobjektType eq VegobjektTyper.FARTSGRENSE) and
//                                (Vegobjekter.sistEndret greaterEq sinceOffset) and
//                                (Vegobjekter.vegobjektId greater paginationId)
//                        }.orderBy(Vegobjekter.vegobjektId)
//                        .limit(superBatchSize)
//                        .map { it[Vegobjekter.vegobjektId] }
//                }
//            if (changedIds.isEmpty()) {
//                break
//            }
//            paginationId = changedIds.last()
//        }
    }

    fun generateSpeedLimitsSnapshot(): Flow<SpeedLimit> = flow {
        val kmhByEgenskapVerdi = datakatalogApi.getKmhByEgenskapVerdi()

        val totalCount = vegobjekterRepository.countVegobjekter(VegobjektTyper.FARTSGRENSE)

        var count = 0

        vegobjekterRepository.findVegobjektIds(VegobjektTyper.FARTSGRENSE).chunked(superBatchSize).forEach { ids ->

            log.measure("Behandler superbatch med ${ids.size} fartsgrenser, starter med id ${ids.first()}, totalt $count av $totalCount") {
                // Create ranges of work for parallel processing
                val idRanges = createIdRanges(ids)

                // Process ranges in parallel - each worker fetches and processes its range
                val speedLimits: List<SpeedLimit> = processIdRangesInParallel(idRanges, kmhByEgenskapVerdi)

                count += speedLimits.size
                speedLimits.sortedBy { it.id }.forEach { speedLimit ->
                    emit(speedLimit)
                }
            }
        }

        log.info("Ferdig med å generere fartsgrenser. Totalt $count av $totalCount")
    }

    private fun createIdRanges(ids: List<Long>): List<IdRange> {
        if (ids.isEmpty()) return emptyList()

        val itemsPerWorker = (ids.size + workerCount - 1) / workerCount

        return (0 until workerCount).mapNotNull { workerIndex ->
            val startIndex = workerIndex * itemsPerWorker
            val endIndex = minOf(startIndex + itemsPerWorker - 1, ids.size - 1)

            if (startIndex < ids.size) {
                // Each worker gets an equal slice of the actual IDs
                // Range bounds ensure each worker processes exactly itemsPerWorker IDs (or remainder for last worker)
                IdRange(
                    startId = ids[startIndex],
                    endId = ids[endIndex],
                )
            } else {
                null
            }
        }
    }

    private suspend fun processIdRangesInParallel(idRanges: List<IdRange>, kmhByEgenskapVerdi: Map<Int, Int>): List<SpeedLimit> = coroutineScope {
        idRanges
            .map { idRange ->
                async {
                    processIdRange(idRange, kmhByEgenskapVerdi)
                }
            }.awaitAll()
            .flatten()
    }

    private fun processIdRange(idRange: IdRange, kmhByEgenskapVerdi: Map<Int, Int>): List<SpeedLimit> = try {
        // Each worker fetches and processes its own data range
        val workItems = fetchSpeedLimitWorkItemsForRange(idRange, kmhByEgenskapVerdi)
        check(workItems.size <= fetchSize) {
            "Fetched ${workItems.size} items for range ${idRange.startId}-${idRange.endId}, which exceeds the fetch size of $fetchSize"
        }

        workItems.mapNotNull { workItem ->
            try {
                createSpeedLimit(workItem)
            } catch (e: CancellationException) {
                throw e // Re-throw cancellation exceptions
            } catch (e: Exception) {
                log.error("Warning: Error processing speed limit ${workItem.id}: ${e.message}", e)
                null // Skip this item but continue processing others
            }
        }
    } catch (e: CancellationException) {
        throw e // Propagate cancellation
    } catch (e: Exception) {
        log.error("Error processing ID range ${idRange.startId}-${idRange.endId}: ${e.message}", e)
        emptyList() // Return empty list for this range but don't fail the entire process
    }

    private fun fetchSpeedLimitWorkItemsForRange(idRange: IdRange, kmhByEgenskapVerdi: Map<Int, Int>): List<SpeedLimitWorkItem> {
        val vegobjekter = vegobjekterRepository.findVegobjekter(VegobjektTyper.FARTSGRENSE, idRange)

        return vegobjekter.mapNotNull { vegobjekt ->
            val kmhEnum = vegobjekt.egenskaper[EgenskapsTyper.FARTSGRENSE] as? EnumVerdi ?: return@mapNotNull null
            val kmh = kmhByEgenskapVerdi[kmhEnum.verdi] ?: error("Ukjent verdi for fartsgrense: ${kmhEnum.verdi}")
            SpeedLimitWorkItem(
                id = vegobjekt.id,
                kmh = kmh,
                stedfestingLinjer = vegobjekt.stedfestinger,
                validFrom = vegobjekt.originalStartdato ?: vegobjekt.startdato,
                validTo = vegobjekt.sluttdato,
                beginLifespanVersion = vegobjekt.startdato.atStartOfDayIn(OsloZone),
            )
        }
    }

    private fun createSpeedLimit(workItem: SpeedLimitWorkItem): SpeedLimit {
        val veglenkesekvensIds = workItem.stedfestingLinjer.map { it.veglenkesekvensId }.toSet()

        val overlappendeVeglenker = veglenkerBatchLookup(veglenkesekvensIds)

        val lineStrings =
            workItem.stedfestingLinjer.flatMap { stedfesting ->
                overlappendeVeglenker[stedfesting.veglenkesekvensId].orEmpty().mapNotNull { veglenke ->
                    calculateIntersectingGeometry(
                        veglenke.geometri,
                        veglenke,
                        stedfesting,
                    )
                }
            }

        val geometry =
            mergeGeometries(lineStrings)?.simplify(1.0)?.projectTo(SRID.WGS84)
                ?: error("Could not determine geometry for fartsgrense ${workItem.id}")

        val locationReferences =
            openLrService.toOpenLr(
                workItem.stedfestingLinjer,
            )

        return SpeedLimit(
            id = workItem.id,
            kmh = workItem.kmh,
            locationReferences = locationReferences,
            validFrom = workItem.validFrom,
            validTo = workItem.validTo,
            geometry = geometry,
            updateType = UpdateType.Add,
            beginLifespanVersion = workItem.beginLifespanVersion,
        )
    }

    companion object {
        private val hardcodedFartsgrenseTillatteVerdier = mapOf(
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
}
