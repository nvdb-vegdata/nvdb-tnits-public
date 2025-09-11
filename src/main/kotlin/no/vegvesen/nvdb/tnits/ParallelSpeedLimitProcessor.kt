package no.vegvesen.nvdb.tnits

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.toKotlinLocalDate
import no.vegvesen.nvdb.apiles.uberiket.EnumEgenskap
import no.vegvesen.nvdb.tnits.config.FETCH_SIZE
import no.vegvesen.nvdb.tnits.database.Vegobjekter
import no.vegvesen.nvdb.tnits.extensions.toOffsetDateTime
import no.vegvesen.nvdb.tnits.geometry.*
import no.vegvesen.nvdb.tnits.model.Veglenke
import no.vegvesen.nvdb.tnits.model.VegobjektStedfesting
import no.vegvesen.nvdb.tnits.model.VegobjektTyper
import no.vegvesen.nvdb.tnits.vegobjekter.getStedfestingLinjer
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.collections.flatMap
import kotlin.collections.map
import kotlin.time.Instant
import kotlin.time.measureTime

typealias VeglenkerBatchLookup = (Collection<Long>) -> Map<Long, List<Veglenke>>

data class SpeedLimitWorkItem(
    val id: Long,
    val kmh: Int,
    val stedfestingLinjer: List<VegobjektStedfesting>,
    val validFrom: kotlinx.datetime.LocalDate,
    val validTo: kotlinx.datetime.LocalDate?,
)

data class IdRange(val startId: Long, val endId: Long)

class ParallelSpeedLimitProcessor(
    private val veglenkerBatchLookup: VeglenkerBatchLookup,
    private val workerCount: Int = Runtime.getRuntime().availableProcessors(),
) {
    private val superBatchSize = workerCount * FETCH_SIZE

    fun generateSpeedLimitsUpdate(since: Instant): Flow<SpeedLimit> = flow {
        val kmhByEgenskapVerdi = getKmhByEgenskapVerdi()

        var paginationId = 0L
        var totalCount = 0

        val sinceOffset = since.toOffsetDateTime()

        while (true) {
            val changedIds =
                transaction {
                    Vegobjekter
                        .select(Vegobjekter.vegobjektId)
                        .where {
                            (Vegobjekter.vegobjektType eq VegobjektTyper.FARTSGRENSE) and
                                (Vegobjekter.sistEndret greaterEq sinceOffset) and
                                (Vegobjekter.vegobjektId greater paginationId)
                        }.orderBy(Vegobjekter.vegobjektId)
                        .limit(superBatchSize)
                        .map { it[Vegobjekter.vegobjektId] }
                }
            if (changedIds.isEmpty()) {
                break
            }
            paginationId = changedIds.last()
        }
    }

    fun generateSpeedLimitsSnapshot(): Flow<SpeedLimit> = flow {
        val kmhByEgenskapVerdi = getKmhByEgenskapVerdi()

        var paginationId = 0L
        var totalCount = 0

        while (true) {
            // Fetch only IDs for the next batch in a single lightweight query
            val ids = fetchNextSpeedLimitIds(paginationId, superBatchSize)

            if (ids.isEmpty()) {
                break
            }

            paginationId = ids.last()

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

    private fun fetchNextSpeedLimitIds(paginationId: Long, batchSize: Int): List<Long> = transaction {
        Vegobjekter
            .select(Vegobjekter.vegobjektId)
            .where {
                (Vegobjekter.vegobjektType eq VegobjektTyper.FARTSGRENSE) and
                    (Vegobjekter.vegobjektId greater paginationId)
            }.orderBy(Vegobjekter.vegobjektId)
            .limit(batchSize)
            .map { it[Vegobjekter.vegobjektId] }
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

        workItems.mapNotNull { workItem ->
            try {
                processSpeedLimitWorkItem(workItem)
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

    private fun fetchSpeedLimitWorkItemsForRange(idRange: IdRange, kmhByEgenskapVerdi: Map<Int, Int>): List<SpeedLimitWorkItem> = transaction {
        val vegobjekter =
            Vegobjekter
                .select(Vegobjekter.data)
                .where {
                    (Vegobjekter.vegobjektType eq VegobjektTyper.FARTSGRENSE) and
                        (Vegobjekter.vegobjektId greaterEq idRange.startId) and
                        (Vegobjekter.vegobjektId lessEq idRange.endId)
                }.orderBy(Vegobjekter.vegobjektId)
                .map { it[Vegobjekter.data] }

        vegobjekter.mapNotNull { vegobjekt ->
            val kmh =
                vegobjekt.egenskaper?.get(FartsgrenseEgenskapTypeIdString)?.let { egenskap ->
                    when (egenskap) {
                        is EnumEgenskap ->
                            kmhByEgenskapVerdi[egenskap.verdi]
                                ?: error("Ukjent verdi for fartsgrense: ${egenskap.verdi}")

                        else -> error("Expected EnumEgenskap, got ${egenskap::class.simpleName}")
                    }
                }

            if (kmh != null) {
                SpeedLimitWorkItem(
                    id = vegobjekt.id,
                    kmh = kmh,
                    stedfestingLinjer = vegobjekt.getStedfestingLinjer(),
                    validFrom = vegobjekt.gyldighetsperiode!!.startdato.toKotlinLocalDate(),
                    validTo = vegobjekt.gyldighetsperiode!!.sluttdato?.toKotlinLocalDate(),
                )
            } else {
                null
            }
        }
    }

    private fun processSpeedLimitWorkItem(workItem: SpeedLimitWorkItem): SpeedLimit {
        val veglenkesekvensIds = workItem.stedfestingLinjer.map { it.veglenkesekvensId }.toSet()

        val overlappendeVeglenker = veglenkerBatchLookup(veglenkesekvensIds)

        val lineStrings =
            workItem.stedfestingLinjer.flatMap { stedfesting ->
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
                ?: error("Could not determine geometry for fartsgrense ${workItem.id}")

        val locationReferences =
            openLrService.toOpenLr(
                workItem.stedfestingLinjer.map { it.utstrekning },
            )

        return SpeedLimit(
            id = workItem.id,
            kmh = workItem.kmh,
            locationReferences = locationReferences,
            validFrom = workItem.validFrom,
            validTo = workItem.validTo,
            geometry = geometry,
            updateType = UpdateType.Add,
        )
    }
}
