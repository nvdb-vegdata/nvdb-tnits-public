package no.vegvesen.nvdb.tnits.generator.infrastructure.ingest

import jakarta.inject.Singleton
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.LocalDate
import no.vegvesen.nvdb.apiles.uberiket.InkluderIVegobjekt
import no.vegvesen.nvdb.tnits.common.extensions.WithLogger
import no.vegvesen.nvdb.tnits.common.model.mainVegobjektTyper
import no.vegvesen.nvdb.tnits.common.model.supportingVegobjektTyper
import no.vegvesen.nvdb.tnits.generator.core.api.*
import no.vegvesen.nvdb.tnits.generator.core.api.VegobjekterKeyValues.Companion.vegobjekterKeyValues
import no.vegvesen.nvdb.tnits.generator.core.model.Vegobjekt
import no.vegvesen.nvdb.tnits.generator.core.services.storage.ColumnFamily
import no.vegvesen.nvdb.tnits.generator.core.services.storage.WriteBatchContext
import no.vegvesen.nvdb.tnits.generator.infrastructure.rocksdb.RocksDbContext
import no.vegvesen.nvdb.tnits.generator.infrastructure.rocksdb.publishChangedVeglenkesekvenser
import no.vegvesen.nvdb.tnits.generator.infrastructure.rocksdb.publishChangedVegobjekter
import kotlin.time.Clock

@Singleton
class VegobjektLoader(
    private val keyValueStore: KeyValueStore,
    private val uberiketApi: UberiketApi,
    private val vegobjekterRepository: VegobjekterRepository,
    private val rocksDbContext: RocksDbContext,
    private val clock: Clock,
) : WithLogger {

    suspend fun backfillVegobjekter(typeId: Int, fetchOriginalStartDate: Boolean): Int {
        val keyValues = keyValueStore.vegobjekterKeyValues(typeId)

        val backfillCompleted = keyValues.getBackfillCompleted()

        if (backfillCompleted != null) {
            log.info("Backfill for type $typeId er allerede fullført med tidspunkt $backfillCompleted")
            return 0
        }

        var lastId = keyValues.getBackfillLastId()

        if (lastId == null) {
            log.info("Ingen backfill har blitt startet ennå for type $typeId. Starter backfill...")
            keyValues.putBackfillStarted(clock.now())
        } else {
            log.info("Backfill pågår for type $typeId. Gjenopptar fra siste ID: $lastId")
        }

        var totalCount = 0
        var batchCount = 0

        do {
            val vegobjekter = uberiketApi.getCurrentVegobjekter(typeId = typeId, start = lastId).toList()
            lastId = vegobjekter.lastOrNull()?.id

            if (vegobjekter.isEmpty()) {
                log.info("Ingen vegobjekter å sette inn for type $typeId, backfill fullført.")
                keyValues.putBackfillCompleted(clock.now())
            } else {
                val vegobjekterWithOriginalStartdato = getVegobjekterWithOriginalStartdatoIfNeeded(fetchOriginalStartDate, typeId, vegobjekter)

                rocksDbContext.writeBatch {
                    vegobjekterRepository.batchInsert(typeId, vegobjekterWithOriginalStartdato)
                    keyValues.putBackfillLastId(lastId!!)
                }
                totalCount += vegobjekter.size
                batchCount++
                if (batchCount % 50 == 0) {
                    log.info("Lastet $totalCount vegobjekter for type $typeId")
                }
            }
        } while (vegobjekter.isNotEmpty())

        return totalCount
    }

    private suspend fun getVegobjekterWithOriginalStartdatoIfNeeded(
        fetchOriginalStartDate: Boolean,
        typeId: Int,
        vegobjekter: List<Vegobjekt>,
    ): List<Vegobjekt> {
        if (!fetchOriginalStartDate) return vegobjekter
        val validFromById = getOriginalStartdatoWhereDifferent(typeId, vegobjekter)
        return vegobjekter.map {
            validFromById[it.id]?.let { dato -> it.copy(originalStartdato = dato) } ?: it
        }
    }

    private suspend fun getOriginalStartdatoWhereDifferent(typeId: Int, vegobjekter: Collection<Vegobjekt>): Map<Long, LocalDate> {
        val vegobjekterThatAreNotFirstVersion = vegobjekter.filter { it.versjon != null && it.versjon > 1 }.map { it.id }.toSet()
        val validFromById = uberiketApi.getVegobjekterPaginated(typeId, vegobjekterThatAreNotFirstVersion, setOf(InkluderIVegobjekt.GYLDIGHETSPERIODE))
            .toList()
            .groupBy { it.id }
            .mapValues { versions -> versions.value.minBy { it.versjon ?: 0 }.startdato }
        return validFromById
    }

    data class VegobjektChanges(val changedIds: Set<Long>, val lastHendelseId: Long)

    suspend fun getVegobjektChanges(typeId: Int, lastHendelseId: Long): VegobjektChanges {
        val changedIds = mutableSetOf<Long>()

        var latestHendelseId = lastHendelseId

        uberiketApi.streamVegobjektHendelser(typeId = typeId, start = latestHendelseId).collect { hendelse ->
            latestHendelseId = hendelse.hendelseId
            changedIds += hendelse.vegobjektId
        }

        return VegobjektChanges(changedIds, latestHendelseId)
    }

    suspend fun updateVegobjekter(typeId: Int, fetchOriginalStartDate: Boolean): Int {
        val keyValues = keyValueStore.vegobjekterKeyValues(typeId)

        val backfillCompleted = keyValues.getBackfillCompleted() ?: error("Backfill for type $typeId er ikke ferdig")

        val previousHendelseId = keyValues.getLastHendelseId() ?: uberiketApi.getLatestVegobjektHendelseId(typeId, backfillCompleted)

        log.info("Starter oppdatering av vegobjekter for type $typeId, siste hendelse-ID: $previousHendelseId")

        val (changedIds, latestHendelseId) = getVegobjektChanges(typeId, previousHendelseId)

        if (changedIds.isEmpty()) {
            log.info("Ingen endringer funnet for type $typeId siden hendelse-ID $previousHendelseId")
            return 0
        }

        log.info("Oppdaterer ${changedIds.size} vegobjekter for type $typeId...")

        val vegobjekterById = fetchVegobjekterByIds(typeId, changedIds, fetchOriginalStartDate)

        val updatesById = changedIds.associateWith { id -> vegobjekterById[id] }

        rocksDbContext.writeBatch {
            val dirtyVeglenkesekvenser = vegobjekterRepository.batchUpdate(typeId, updatesById)

            performDirtyMarking(typeId, changedIds, dirtyVeglenkesekvenser)

            keyValues.putLastHendelseId(latestHendelseId)
        }

        return changedIds.size
    }

    private fun WriteBatchContext.performDirtyMarking(typeId: Int, changedIds: Set<VegobjektId>, dirtyVeglenkesekvenser: DirtyVeglenkesekvenser) {
        // If there are no EXPORTED_FEATURES yet it means that we are still in the initial backfill phase
        // and should not mark anything as dirty
        // TODO: This might be slightly unaccurate the first time after adding a new exported feature type?
        val shouldDirtyMark = rocksDbContext.hasAnyKeys(ColumnFamily.EXPORTED_FEATURES)
        if (shouldDirtyMark) {
            if (typeId in mainVegobjektTyper) {
                publishChangedVegobjekter(typeId, changedIds)
            } else if (typeId in supportingVegobjektTyper) {
                publishChangedVeglenkesekvenser(dirtyVeglenkesekvenser)
            }
        }
    }

    private suspend fun fetchVegobjekterByIds(typeId: Int, ids: Set<Long>, fetchOriginalStartDate: Boolean): Map<Long, Vegobjekt> =
        uberiketApi.getVegobjekterPaginated(typeId, ids, setOf(InkluderIVegobjekt.ALLE)).toList()
            .groupBy { it.id }
            .mapNotNull { (id, versjoner) ->
                // TODO: Vi håndterer ikke vegobjekter med fremtidige versjoner
                // Det er viktig at vi ikke tar bare aktive her, siden hvis et vegobjekt er lukket ønsker vi å få med sluttdatoen.
                val latest = versjoner.maxBy { it.startdato }
                val originalStartdato = if (fetchOriginalStartDate) {
                    val first = versjoner.minBy { it.startdato }
                    if (latest != first) {
                        first.startdato
                    } else {
                        null
                    }
                } else {
                    null
                }

                id to if (originalStartdato != null) latest.copy(originalStartdato = originalStartdato) else latest
            }.toMap()
}
