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
import kotlinx.datetime.todayIn
import no.vegvesen.nvdb.apiles.uberiket.HeltallEgenskap
import no.vegvesen.nvdb.tnits.common.extensions.WithLogger
import no.vegvesen.nvdb.tnits.common.extensions.measure
import no.vegvesen.nvdb.tnits.common.model.ExportedFeatureType
import no.vegvesen.nvdb.tnits.generator.core.api.DatakatalogApi
import no.vegvesen.nvdb.tnits.generator.core.api.ExportedFeatureRepository
import no.vegvesen.nvdb.tnits.generator.core.api.VegobjekterRepository
import no.vegvesen.nvdb.tnits.generator.core.extensions.*
import no.vegvesen.nvdb.tnits.generator.core.model.*
import no.vegvesen.nvdb.tnits.generator.core.model.tnits.*
import no.vegvesen.nvdb.tnits.generator.core.services.vegnett.CachedVegnett
import no.vegvesen.nvdb.tnits.generator.core.services.vegnett.OpenLrService
import no.vegvesen.nvdb.tnits.generator.marshaller
import kotlin.time.Clock
import kotlin.time.Instant

@Singleton
class FeatureTransformer(
    private val cachedVegnett: CachedVegnett,
    private val datakatalogApi: DatakatalogApi,
    private val openLrService: OpenLrService,
    private val vegobjekterRepository: VegobjekterRepository,
    private val exportedFeatureStore: ExportedFeatureRepository,
    private val workerCount: Int = Runtime.getRuntime().availableProcessors(),
    private val clock: Clock,
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

            log.info(
                "Fant ${vegobjekterById.count {
                    it.value != null
                }} lagrede vegobjekter og ${previousFeatures.count()} tidligere eksporterte vegobjekter for $featureType",
            )

            vegobjekterById.forEach { (id, vegobjekt) ->
                try {
                    val changeType = changesById[id]!!
                    val previous = previousFeatures[id]

                    if (changeType == ChangeType.DELETED) {
                        if (previous != null) {
                            emit(
                                previous.copy(
                                    updateType = UpdateType.Remove,
                                    validTo = timestamp.toLocalDateTime(OsloZone).date,
                                ),
                            )
                        } else {
                            log.error(
                                "Vegobjekt med id $id og type ${featureType.typeId} finnes ikke i tidligere eksporterte data! Kan ikke lage oppdatering for $changeType.",
                            )
                        }
                    } else if (vegobjekt == null) {
                        log.error("Vegobjekt med id $id og type ${featureType.typeId} finnes ikke i databasen! Kan ikke lage oppdatering for $changeType.")
                    } else if (changeType == ChangeType.MODIFIED && vegobjekt.sluttdato != null) {
                        if (previous != null) {
                            emit(
                                previous.copy(
                                    updateType = UpdateType.Modify,
                                    validTo = vegobjekt.sluttdato,
                                ),
                            )
                        } else {
                            log.error(
                                "Vegobjekt med id $id og type ${featureType.typeId} finnes ikke i tidligere eksporterte data! Kan ikke lage oppdatering for $changeType med sluttdato ${vegobjekt.sluttdato}.",
                            )
                        }
                    } else {
                        val propertyMapper = getPropertyMapper(featureType)
                        val updateType = when (changeType) {
                            ChangeType.NEW -> UpdateType.Add
                            ChangeType.MODIFIED -> UpdateType.Modify
                            else -> error("this should not happen")
                        }
                        val newFeature = processVegobjektToFeature(vegobjekt, propertyMapper, updateType)
                        if (newFeature != null) {
                            val isIdentical = previous != null && newFeature.hash == previous.hash
                            if (!isIdentical) {
                                emit(newFeature)
                            } else {
                                log.debug("Skipping identical feature for vegobjekt $id")
                            }
                        } else if (previous != null) {
                            // Feature was valid before, but no longer. Emit as Removed.
                            emit(
                                previous.copy(
                                    updateType = UpdateType.Remove,
                                    validTo = timestamp.toLocalDateTime(OsloZone).date,
                                ),
                            )
                        }
                    }
                } catch (exception: Exception) {
                    log.error("Error processing vegobjekt with id $id: ${exception.localizedMessage}")
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
        ExportedFeatureType.RoadNumber -> VegobjektPropertyMapper(::getRoadNumberProperties)
        ExportedFeatureType.MaximumHeight -> VegobjektPropertyMapper(::getMaximumHeightProperties)
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

    private fun getRoadNumberProperties(vegobjekt: Vegobjekt): Map<RoadFeaturePropertyType, RoadFeatureProperty> {
        val roadNumberVegkatagori = vegobjekt.egenskaper[EgenskapsTyper.VEGSYSTEM_VEGKATAGORI] as? EnumVerdi ?: return emptyMap()
        val vegkategori = EgenskapsTyper.vegkategoriTillatteVerdier[roadNumberVegkatagori.verdi]
        val vegnummer = when (val prop = vegobjekt.egenskaper[EgenskapsTyper.VEGSYSTEM_VEGNUMMER]) {
            is EnumVerdi -> prop.verdi
            is HeltallVerdi -> prop.verdi
            is HeltallEgenskap -> prop.verdi
            else -> return emptyMap()
        }
        val roadNumberFase = vegobjekt.egenskaper[EgenskapsTyper.VEGSYSTEM_FASE] as? EnumVerdi ?: return emptyMap()
        val vegfaseName = EgenskapsTyper.vegfaseTillatteVerdier[roadNumberFase.verdi]

        val kommunenummer = if (vegkategori in setOf("K", "S", "P")) {
            vegobjekt.stedfestinger
                .map { it.veglenkesekvensId }
                .flatMap { cachedVegnett.getVeglenker(it) }
                .map { it.kommune }
                .firstOrNull()
                ?.toString()
                ?.padStart(4, '0')
                ?: ""
        } else {
            ""
        }
        val officialNumber = "$kommunenummer $vegkategori$vegnummer".trim()
        val conditionOfFacility = vegfaseNameToConditionOfFacility[vegfaseName].toString().trim()

        return mapOf(
            RoadFeaturePropertyType.RoadNumber to StringProperty(officialNumber),
            RoadFeaturePropertyType.ConditionOfFacility to StringProperty(conditionOfFacility),
        )
    }

    val vegfaseNameToConditionOfFacility = mapOf(
        "P" to "projected",
        "A" to "under construction",
        "V" to "functional",
        "F" to "fictitious",
    )

    private fun getMaximumHeightProperties(vegobjekt: Vegobjekt): Map<RoadFeaturePropertyType, RoadFeatureProperty> {
        val skiltaHoydeEgenskap = vegobjekt.egenskaper[EgenskapsTyper.SKILTA_HOYDE] as? FlyttallVerdi ?: return emptyMap()
        return mapOf(
            RoadFeaturePropertyType.MaximumHeight to DoubleProperty(skiltaHoydeEgenskap.verdi),
        )
    }

    fun generateSnapshot(featureType: ExportedFeatureType): Flow<TnitsFeature> = flow {
        val totalCount = vegobjekterRepository.countVegobjekter(featureType.typeId)

        var count = 0

        vegobjekterRepository.findVegobjektIds(featureType.typeId).chunked(superBatchSize).forEach { ids ->

            log.measure("Behandler superbatch med ${ids.size} $featureType, starter med id ${ids.first()}, totalt $count av $totalCount") {
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

        log.info("Ferdig med å generere $featureType. Totalt $count av $totalCount")
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
        val today = clock.todayIn(OsloZone)

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
                log.warn("Error processing $featureType ${vegobjekt.id}: ${e.localizedMessage}")
                null // Skip this item but continue processing others
            }
        }
    } catch (e: Exception) {
        log.error("Error processing ID range ${idRange.startId}-${idRange.endId}: ${e.localizedMessage}", e)
        emptyList() // Return empty list for this range but don't fail the entire process
    }

    private fun processVegobjektToFeature(vegobjekt: Vegobjekt, propertyMapper: VegobjektPropertyMapper, updateType: UpdateType): TnitsFeature? {
        // Early return for invalid vegobjekter to avoid expensive operations
        val properties = propertyMapper.getFeatureProperties(vegobjekt)
        val type = ExportedFeatureType.from(vegobjekt.type)

        val isValid = vegobjekt.stedfestinger.isNotEmpty() && propertiesValidForType(type, properties)

        if (vegobjekt.sluttdato != null && vegobjekt.sluttdato <= clock.todayIn(OsloZone)) {
            log.warn("Vegobjekt for type $type med id ${vegobjekt.id} er ikke lenger gyldig (sluttdato ${vegobjekt.sluttdato}), lukkes")
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
            log.debug("Ugyldig vegobjekt for type {} med id {}, hopper over", type, vegobjekt.id)
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

        if (lineStrings.isEmpty()) {
            log.debug("Finner ingen veglenker for {} {} med stedfesting {}", type, vegobjekt.id, vegobjekt.stedfestinger)
            return null
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

    private fun propertiesValidForType(type: ExportedFeatureType, properties: Map<RoadFeaturePropertyType, RoadFeatureProperty>): Boolean = when (type) {
        ExportedFeatureType.SpeedLimit -> properties.containsKey(RoadFeaturePropertyType.MaximumSpeedLimit)

        ExportedFeatureType.RoadName -> properties[RoadFeaturePropertyType.RoadName].let {
            it is StringProperty && it.value.isNotBlank()
        }

        ExportedFeatureType.RoadNumber -> {
            val hasValidRoadNumber = properties[RoadFeaturePropertyType.RoadNumber].let {
                it is StringProperty && it.value.isNotBlank()
            }
            val hasValidConditionofFacility = properties[RoadFeaturePropertyType.ConditionOfFacility].let {
                it is StringProperty && it.value.isNotBlank() && it.value !in setOf("fictitious", "projected")
            }
            hasValidRoadNumber && hasValidConditionofFacility
        }

        ExportedFeatureType.MaximumHeight -> properties.containsKey(RoadFeaturePropertyType.MaximumHeight)
    }
}
