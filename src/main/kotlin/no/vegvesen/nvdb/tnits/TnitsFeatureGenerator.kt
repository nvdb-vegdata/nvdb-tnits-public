package no.vegvesen.nvdb.tnits

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import no.vegvesen.nvdb.apiles.datakatalog.EgenskapstypeHeltallenum
import no.vegvesen.nvdb.tnits.extensions.OsloZone
import no.vegvesen.nvdb.tnits.extensions.forEachChunked
import no.vegvesen.nvdb.tnits.gateways.DatakatalogApi
import no.vegvesen.nvdb.tnits.geometry.*
import no.vegvesen.nvdb.tnits.model.*
import no.vegvesen.nvdb.tnits.openlr.OpenLrService
import no.vegvesen.nvdb.tnits.storage.VegobjekterRepository
import no.vegvesen.nvdb.tnits.utilities.WithLogger
import no.vegvesen.nvdb.tnits.utilities.measure
import kotlin.time.Instant

typealias VeglenkerBatchLookup = (Collection<Long>) -> Map<Long, List<Veglenke>>

data class TnitsFeatureUpsertWorkItem(
    val id: Long,
    val type: ExportedFeatureType,
    val properties: Map<RoadFeaturePropertyType, RoadFeatureProperty>,
    val stedfestingLinjer: List<VegobjektStedfesting>,
    val validFrom: LocalDate,
    val validTo: LocalDate?,
    val beginLifespanVersion: Instant,
    val updateType: UpdateType,
) {
    fun isValid(): Boolean = when (type) {
        ExportedFeatureType.SpeedLimit -> properties.containsKey(RoadFeaturePropertyType.MaximumSpeedLimit) &&
            stedfestingLinjer.isNotEmpty()
    }
}

data class IdRange(val startId: Long, val endId: Long)

class TnitsFeatureGenerator(
    private val veglenkerBatchLookup: VeglenkerBatchLookup,
    private val datakatalogApi: DatakatalogApi,
    private val openLrService: OpenLrService,
    private val workerCount: Int = Runtime.getRuntime().availableProcessors(),
    private val vegobjekterRepository: VegobjekterRepository,
) : WithLogger {
    private val fetchSize = 1000
    private val superBatchSize = workerCount * fetchSize

    private val kmhByEgenskapVerdi: Map<Int, Int> by lazy {
        runBlocking {
            datakatalogApi.getKmhByEgenskapVerdi()
        }
    }

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

    fun generateFeaturesUpdate(featureType: ExportedFeatureType, changesById: Map<Long, ChangeType>): Flow<TnitsFeatureUpsert> = flow {
        changesById.keys.forEachChunked(fetchSize) { ids ->
            val vegobjekterById = vegobjekterRepository.findVegobjekter(featureType.typeId, ids)

            TnitsFeatureUpsert(
                id = TODO(),
                type = TODO(),
                geometry = TODO(),
                properties = TODO(),
                openLrLocationReferences = TODO(),
                nvdbLocationReferences = TODO(),
                validFrom = TODO(),
                validTo = TODO(),
                updateType = TODO(),
                beginLifespanVersion = TODO(),
            )
        }
    }

    fun interface VegobjektPropertyMapper {
        fun getFeatureProperties(vegobjekt: Vegobjekt): Map<RoadFeaturePropertyType, RoadFeatureProperty>
    }

    fun getPropertyMapper(type: ExportedFeatureType): VegobjektPropertyMapper = when (type) {
        ExportedFeatureType.SpeedLimit -> VegobjektPropertyMapper(::getSpeedLimitProperties)
    }

    private fun getSpeedLimitProperties(vegobjekt: Vegobjekt): Map<RoadFeaturePropertyType, RoadFeatureProperty> {
        val kmhEnum = vegobjekt.egenskaper[EgenskapsTyper.FARTSGRENSE] as? EnumVerdi ?: return emptyMap()
        val kmh = kmhByEgenskapVerdi[kmhEnum.verdi] ?: error("Ukjent verdi for fartsgrense: ${kmhEnum.verdi}")
        return mapOf(
            RoadFeaturePropertyType.MaximumSpeedLimit to IntProperty(kmh),
        )
    }

    fun generateSnapshot(featureType: ExportedFeatureType): Flow<TnitsFeatureUpsert> = flow {
        val totalCount = vegobjekterRepository.countVegobjekter(featureType.typeId)

        var count = 0

        vegobjekterRepository.findVegobjektIds(featureType.typeId).chunked(superBatchSize).forEach { ids ->

            log.measure("Behandler superbatch med ${ids.size} ${featureType.typeCode}, starter med id ${ids.first()}, totalt $count av $totalCount") {
                // Create ranges of work for parallel processing
                val idRanges = createIdRanges(ids)

                // Process ranges in parallel - each worker fetches and processes its range
                val getFeatureProperties = getPropertyMapper(featureType)
                val features: List<TnitsFeatureUpsert> = processIdRangesInParallel(idRanges, getFeatureProperties)

                count += features.size
                features.sortedBy { it.id }.forEach { feature ->
                    emit(feature)
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

    private suspend fun processIdRangesInParallel(idRanges: List<IdRange>, getFeatureProperties: VegobjektPropertyMapper): List<TnitsFeatureUpsert> =
        coroutineScope {
            idRanges
                .map { idRange ->
                    async {
                        processIdRange(idRange, getFeatureProperties)
                    }
                }.awaitAll()
                .flatten()
        }

    private fun processIdRange(idRange: IdRange, getFeatureProperties: VegobjektPropertyMapper): List<TnitsFeatureUpsert> = try {
        // Each worker fetches and processes its own data range
        val workItems = fetchSnapshotWorkItemsForRange(idRange, getFeatureProperties)
        check(workItems.size <= fetchSize) {
            "Fetched ${workItems.size} items for range ${idRange.startId}-${idRange.endId}, which exceeds the fetch size of $fetchSize"
        }

        workItems.mapNotNull { workItem ->
            try {
                createFeature(workItem)
            } catch (e: Exception) {
                log.error("Warning: Error processing speed limit ${workItem.id}", e)
                null // Skip this item but continue processing others
            }
        }
    } catch (e: Exception) {
        log.error("Error processing ID range ${idRange.startId}-${idRange.endId}", e)
        emptyList() // Return empty list for this range but don't fail the entire process
    }

    private fun fetchSnapshotWorkItemsForRange(idRange: IdRange, propertyMapper: VegobjektPropertyMapper): List<TnitsFeatureUpsertWorkItem> {
        val vegobjekter = vegobjekterRepository.findVegobjekter(VegobjektTyper.FARTSGRENSE, idRange)

        return vegobjekter.mapNotNull { vegobjekt ->
            TnitsFeatureUpsertWorkItem(
                id = vegobjekt.id,
                type = ExportedFeatureType.from(vegobjekt.type),
                properties = propertyMapper.getFeatureProperties(vegobjekt),
                stedfestingLinjer = vegobjekt.stedfestinger,
                validFrom = vegobjekt.originalStartdato ?: vegobjekt.startdato,
                validTo = vegobjekt.sluttdato,
                beginLifespanVersion = vegobjekt.startdato.atStartOfDayIn(OsloZone),
                updateType = UpdateType.Snapshot,
            ).takeIf { it.isValid() }
        }
    }

    private fun createFeature(workItem: TnitsFeatureUpsertWorkItem): TnitsFeatureUpsert {
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

        require(lineStrings.isNotEmpty()) {
            "Finner ingen veglenker for ${workItem.type} ${workItem.id} med stedfesting ${workItem.stedfestingLinjer}"
        }

        val geometry =
            mergeGeometries(lineStrings)?.simplify(1.0)?.projectTo(SRID.WGS84)
                ?: error("Klarte ikke lage geometri for ${workItem.type} ${workItem.id}")

        val openLrLocationReferences =
            openLrService.toOpenLr(
                workItem.stedfestingLinjer,
            )

        return TnitsFeatureUpsert(
            id = workItem.id,
            type = workItem.type,
            properties = workItem.properties,
            openLrLocationReferences = openLrLocationReferences,
            nvdbLocationReferences = workItem.stedfestingLinjer,
            validFrom = workItem.validFrom,
            validTo = workItem.validTo,
            geometry = geometry,
            updateType = workItem.updateType,
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
