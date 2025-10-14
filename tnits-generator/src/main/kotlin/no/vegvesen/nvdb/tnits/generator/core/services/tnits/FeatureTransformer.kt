package no.vegvesen.nvdb.tnits.generator.core.services.tnits

import jakarta.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import no.vegvesen.nvdb.tnits.common.model.ExportedFeatureType
import no.vegvesen.nvdb.tnits.generator.core.api.DatakatalogApi
import no.vegvesen.nvdb.tnits.generator.core.api.VegobjekterRepository
import no.vegvesen.nvdb.tnits.generator.core.extensions.*
import no.vegvesen.nvdb.tnits.generator.core.model.*
import no.vegvesen.nvdb.tnits.generator.core.model.tnits.*
import no.vegvesen.nvdb.tnits.generator.core.services.vegnett.CachedVegnett
import no.vegvesen.nvdb.tnits.generator.core.services.vegnett.OpenLrService
import no.vegvesen.nvdb.tnits.generator.infrastructure.rocksdb.ExportedFeatureRocksDbStore
import no.vegvesen.nvdb.tnits.generator.marshaller
import kotlin.time.Instant

@Singleton
class FeatureTransformer(
    private val cachedVegnett: CachedVegnett,
    private val datakatalogApi: DatakatalogApi,
    private val openLrService: OpenLrService,
    private val vegobjekterRepository: VegobjekterRepository,
    private val exportedFeatureStore: ExportedFeatureRocksDbStore,
    private val workerCount: Int = Runtime.getRuntime().availableProcessors(),
) : WithLogger {
    private val fetchSize = 1000
    private val superBatchSize = workerCount * fetchSize

    private val kmhByEgenskapVerdi: Map<Int, Int> by lazy {
        runBlocking {
            datakatalogApi.getKmhByEgenskapVerdi()
        }
    }

    fun generateFeaturesUpdate(featureType: ExportedFeatureType, changesById: Map<Long, ChangeType>, timestamp: Instant): Flow<TnitsFeature> = flow {
        changesById.keys.forEachChunked(fetchSize) { ids ->
            val vegobjekterById = vegobjekterRepository.findVegobjekter(featureType.typeId, ids)

            val previousFeatures = exportedFeatureStore.getExportedFeatures(ids)

            vegobjekterById.forEach { (id, vegobjekt) ->
                try {
                    val changeType = changesById[id]!!

                    if (changeType == ChangeType.DELETED) {
                        val previous = previousFeatures[id]
                        if (previous != null) {
                            emit(
                                previous.copy(
                                    updateType = UpdateType.Remove,
                                    validTo = timestamp.toLocalDateTime(OsloZone).date,
                                ),
                            )
                        } else {
                            log.error(
                                "Vegobjekt med id $id og type ${featureType.typeCode} finnes ikke i tidligere eksporterte data! Kan ikke lage oppdatering for $changeType.",
                            )
                        }
                    } else if (vegobjekt == null) {
                        log.error("Vegobjekt med id $id og type ${featureType.typeCode} finnes ikke i databasen! Kan ikke lage oppdatering for $changeType.")
                    } else if (changeType == ChangeType.MODIFIED && vegobjekt.sluttdato != null) {
                        val previous = previousFeatures[id]
                        if (previous != null) {
                            emit(
                                previous.copy(
                                    updateType = UpdateType.Modify,
                                    validTo = vegobjekt.sluttdato,
                                ),
                            )
                        } else {
                            log.error(
                                "Vegobjekt med id $id og type ${featureType.typeCode} finnes ikke i tidligere eksporterte data! Kan ikke lage oppdatering for $changeType med sluttdato ${vegobjekt.sluttdato}.",
                            )
                        }
                    } else {
                        val propertyMapper = getPropertyMapper(featureType)
                        val updateType = when (changeType) {
                            ChangeType.NEW -> UpdateType.Add
                            ChangeType.MODIFIED -> UpdateType.Modify
                            else -> error("this should not happen")
                        }
                        val feature = processVegobjektToFeature(vegobjekt, propertyMapper, updateType)
                        if (feature != null) {
                            emit(feature)
                        }
                    }
                } catch (exception: Exception) {
                    log.error("Error processing vegobjekt with id $id", exception)
                }
            }
        }
    }

    fun interface VegobjektPropertyMapper {
        fun getFeatureProperties(vegobjekt: Vegobjekt): Map<RoadFeaturePropertyType, RoadFeatureProperty>
    }

    fun getPropertyMapper(type: ExportedFeatureType): VegobjektPropertyMapper = when (type) {
        ExportedFeatureType.SpeedLimit -> VegobjektPropertyMapper(::getSpeedLimitProperties)
        ExportedFeatureType.RoadName -> VegobjektPropertyMapper(::getRoadNameProperties)
    }

    private fun getSpeedLimitProperties(vegobjekt: Vegobjekt): Map<RoadFeaturePropertyType, RoadFeatureProperty> {
        val kmhEnum = vegobjekt.egenskaper[EgenskapsTyper.FARTSGRENSE] as? EnumVerdi ?: return emptyMap()
        val kmh = kmhByEgenskapVerdi[kmhEnum.verdi] ?: error("Ukjent verdi for fartsgrense: ${kmhEnum.verdi}")
        return mapOf(
            RoadFeaturePropertyType.MaximumSpeedLimit to IntProperty(kmh),
        )
    }

    private fun getRoadNameProperties(vegobjekt: Vegobjekt): Map<RoadFeaturePropertyType, RoadFeatureProperty> {
        val nameEgenskap = vegobjekt.egenskaper[EgenskapsTyper.ADRESSENAVN] as? TekstVerdi ?: return emptyMap()
        return mapOf(
            RoadFeaturePropertyType.RoadName to StringProperty(nameEgenskap.verdi),
        )
    }

    fun generateSnapshot(featureType: ExportedFeatureType): Flow<TnitsFeature> = flow {
        val totalCount = vegobjekterRepository.countVegobjekter(featureType.typeId)

        var count = 0

        vegobjekterRepository.findVegobjektIds(featureType.typeId).chunked(superBatchSize).forEach { ids ->

            log.measure("Behandler superbatch med ${ids.size} ${featureType.typeCode}, starter med id ${ids.first()}, totalt $count av $totalCount") {
                // Create ranges of work for parallel processing
                val idRanges = createIdRanges(ids)

                // Process ranges in parallel - each worker fetches and processes its range
                val getFeatureProperties = getPropertyMapper(featureType)
                val features: List<TnitsFeature> = processIdRangesInParallel(featureType, idRanges, getFeatureProperties)

                count += features.size
                features.sortedBy { it.id }.forEach { feature ->
                    emit(feature)
                }
            }
        }

        log.info("Ferdig med å generere ${featureType.typeCode}. Totalt $count av $totalCount")
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

    private suspend fun processIdRangesInParallel(
        featureType: ExportedFeatureType,
        idRanges: List<IdRange>,
        getFeatureProperties: VegobjektPropertyMapper,
    ): List<TnitsFeature> = coroutineScope {
        idRanges
            .map { idRange ->
                async {
                    processIdRangeForSnapshot(featureType, idRange, getFeatureProperties)
                }
            }.awaitAll()
            .flatten()
    }

    private fun processIdRangeForSnapshot(
        featureType: ExportedFeatureType,
        idRange: IdRange,
        getFeatureProperties: VegobjektPropertyMapper,
    ): List<TnitsFeature> = try {
        // Each worker fetches and processes its own data range directly
        val vegobjekter = vegobjekterRepository.findVegobjekter(featureType.typeId, idRange)
            .filter { !it.fjernet && (it.sluttdato == null || it.sluttdato > today) }

        check(vegobjekter.size <= fetchSize) {
            "Fetched ${vegobjekter.size} items for range ${idRange.startId}-${idRange.endId}, which exceeds the fetch size of $fetchSize"
        }

        vegobjekter.mapNotNull { vegobjekt ->
            try {
                processVegobjektToFeature(vegobjekt, getFeatureProperties, UpdateType.Snapshot)
            } catch (e: Exception) {
                log.warn("Error processing ${featureType.typeCode} ${vegobjekt.id}", e)
                null // Skip this item but continue processing others
            }
        }
    } catch (e: Exception) {
        log.error("Error processing ID range ${idRange.startId}-${idRange.endId}", e)
        emptyList() // Return empty list for this range but don't fail the entire process
    }

    private fun processVegobjektToFeature(vegobjekt: Vegobjekt, propertyMapper: VegobjektPropertyMapper, updateType: UpdateType): TnitsFeature? {
        // Early return for invalid vegobjekter to avoid expensive operations
        val properties = propertyMapper.getFeatureProperties(vegobjekt)
        val type = ExportedFeatureType.from(vegobjekt.type)

        val isValid = when (type) {
            ExportedFeatureType.SpeedLimit -> properties.containsKey(RoadFeaturePropertyType.MaximumSpeedLimit) &&
                vegobjekt.stedfestinger.isNotEmpty()

            ExportedFeatureType.RoadName -> properties[RoadFeaturePropertyType.RoadName].let {
                it is StringProperty && it.value.isNotBlank()
            } && vegobjekt.stedfestinger.isNotEmpty()
        }

        if (vegobjekt.sluttdato != null && vegobjekt.sluttdato <= today) {
            log.warn("Vegobjekt for type ${type.typeCode} med id ${vegobjekt.id} er ikke lenger gyldig (sluttdato ${vegobjekt.sluttdato}), lukkes")
            return TnitsFeature(
                id = vegobjekt.id,
                type = type,
                geometry = null,
                properties = properties,
                openLrLocationReferences = emptyList(),
                nvdbLocationReferences = emptyList(),
                validFrom = vegobjekt.originalStartdato ?: vegobjekt.startdato,
                validTo = vegobjekt.sluttdato,
                updateType = updateType,
                beginLifespanVersion = vegobjekt.startdato.atStartOfDayIn(OsloZone),
            )
        }

        if (!isValid) {
            log.warn("Ugyldig vegobjekt for type ${type.typeCode} med id ${vegobjekt.id}, hopper over")
            return null
        }

        // Now perform expensive operations only after validation passes
        val veglenkesekvensIds = vegobjekt.stedfestinger.map { it.veglenkesekvensId }.toSet()
        val overlappendeVeglenker = veglenkesekvensIds.associateWith(cachedVegnett::getVeglenker)

        val lineStrings = vegobjekt.stedfestinger.flatMap { stedfesting ->
            overlappendeVeglenker[stedfesting.veglenkesekvensId].orEmpty().mapNotNull { veglenke ->
                calculateIntersectingGeometry(
                    veglenke.geometri,
                    veglenke,
                    stedfesting,
                )
            }
        }

        require(lineStrings.isNotEmpty()) {
            "Finner ingen veglenker for $type ${vegobjekt.id} med stedfesting ${vegobjekt.stedfestinger}"
        }

        val merged = mergeGeometries(lineStrings) ?: error("Klarte ikke slå sammen geometrier for $type ${vegobjekt.id}")

        val geometry = merged.simplify(1.0)

        val openLrLocationReferences = openLrService.toOpenLr(vegobjekt.stedfestinger)

        return TnitsFeature(
            id = vegobjekt.id,
            type = type,
            properties = properties,
            openLrLocationReferences = openLrLocationReferences.map(marshaller::marshallToBase64String),
            nvdbLocationReferences = vegobjekt.stedfestinger,
            validFrom = vegobjekt.originalStartdato ?: vegobjekt.startdato,
            validTo = vegobjekt.sluttdato,
            geometry = geometry,
            updateType = updateType,
            beginLifespanVersion = vegobjekt.startdato.atStartOfDayIn(OsloZone),
        )
    }
}
