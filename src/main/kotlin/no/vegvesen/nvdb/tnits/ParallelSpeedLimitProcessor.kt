package no.vegvesen.nvdb.tnits

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import no.vegvesen.nvdb.tnits.extensions.toOffsetDateTime
import no.vegvesen.nvdb.tnits.geometry.*
import no.vegvesen.nvdb.tnits.model.*
import no.vegvesen.nvdb.tnits.openlr.OpenLrService
import no.vegvesen.nvdb.tnits.services.DatakatalogApi
import no.vegvesen.nvdb.tnits.storage.VegobjekterRepository
import kotlin.time.Instant
import kotlin.time.measureTime

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

class ParallelSpeedLimitProcessor(
    private val veglenkerBatchLookup: VeglenkerBatchLookup,
    private val datakatalogApi: DatakatalogApi,
    private val openLrService: OpenLrService,
    private val workerCount: Int = Runtime.getRuntime().availableProcessors(),
    private val vegobjekterRepository: VegobjekterRepository,
) {
    private val FETCH_SIZE = 1000
    private val superBatchSize = workerCount * FETCH_SIZE

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

        var totalCount = 0

        vegobjekterRepository.findVegobjektIds(VegobjektTyper.FARTSGRENSE).chunked(superBatchSize).forEach { ids ->

            println("Behandler superbatch med ${ids.size} fartsgrenser, starter med id ${ids.first()}...")

            // Create ranges of work for parallel processing
            val idRanges = createIdRanges(ids)

            // Process ranges in parallel - each worker fetches and processes its range
            val speedLimits: List<SpeedLimit>
            val processingTime =
                measureTime {
                    speedLimits = processIdRangesInParallel(idRanges, kmhByEgenskapVerdi)
                }

            totalCount += speedLimits.size
            speedLimits.sortedBy { it.id }.forEach { speedLimit ->
                emit(speedLimit)
            }

            println("Behandlet superbatch: ${speedLimits.size} fartsgrenser på $processingTime (totalt: $totalCount)")
        }
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
        check(workItems.size <= FETCH_SIZE) {
            "Fetched ${workItems.size} items for range ${idRange.startId}-${idRange.endId}, which exceeds the fetch size of $FETCH_SIZE"
        }

        workItems.mapNotNull { workItem ->
            try {
                createSpeedLimit(workItem)
            } catch (e: CancellationException) {
                throw e // Re-throw cancellation exceptions
            } catch (e: Exception) {
                println("Warning: Error processing speed limit ${workItem.id}: ${e.message}")
                null // Skip this item but continue processing others
            }
        }
    } catch (e: CancellationException) {
        throw e // Propagate cancellation
    } catch (e: Exception) {
        println("Error processing ID range ${idRange.startId}-${idRange.endId}: ${e.message}")
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
}
