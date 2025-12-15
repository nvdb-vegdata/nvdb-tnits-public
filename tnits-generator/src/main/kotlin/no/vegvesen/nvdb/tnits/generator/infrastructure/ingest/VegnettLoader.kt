package no.vegvesen.nvdb.tnits.generator.infrastructure.ingest

import jakarta.inject.Singleton
import kotlinx.coroutines.flow.chunked
import no.vegvesen.nvdb.tnits.common.extensions.WithLogger
import no.vegvesen.nvdb.tnits.generator.core.api.KeyValueStore
import no.vegvesen.nvdb.tnits.generator.core.api.UberiketApi
import no.vegvesen.nvdb.tnits.generator.core.api.VeglenkerRepository
import no.vegvesen.nvdb.tnits.generator.core.api.vegnettKeyValues
import no.vegvesen.nvdb.tnits.generator.core.model.Veglenke
import no.vegvesen.nvdb.tnits.generator.core.services.storage.ColumnFamily
import no.vegvesen.nvdb.tnits.generator.core.services.storage.WriteBatchContext
import no.vegvesen.nvdb.tnits.generator.infrastructure.rocksdb.RocksDbContext
import no.vegvesen.nvdb.tnits.generator.infrastructure.rocksdb.publishChangedVeglenkesekvenser
import kotlin.time.Clock

@Singleton
class VegnettLoader(
    private val keyValueStore: KeyValueStore,
    private val uberiketApi: UberiketApi,
    private val veglenkerRepository: VeglenkerRepository,
    private val rocksDbContext: RocksDbContext,
    private val clock: Clock,
) : WithLogger {

    private val keyValues = keyValueStore.vegnettKeyValues

    suspend fun backfillVeglenkesekvenser(): Int {
        val backfillCompleted = keyValues.getBackfillCompleted()

        if (backfillCompleted != null) {
            log.info("Backfill for veglenkesekvenser er allerede fullført den $backfillCompleted")
            return 0
        }

        var lastId = keyValues.getBackfillLastId()

        if (lastId == null) {
            log.info("Ingen veglenkesekvenser backfill har blitt startet ennå. Starter backfill...")
            keyValues.putBackfillStarted(clock.now())
        } else {
            log.info("Veglenkesekvenser backfill pågår. Gjenopptar fra siste ID: $lastId")
        }

        var totalCount = 0
        var batchCount = 0

        do {
            val veglenkesekvensIdAndVeglenker = uberiketApi.getVeglenkesekvenser(start = lastId).toList()
            lastId = veglenkesekvensIdAndVeglenker.lastOrNull()?.veglenkesekvensId

            if (veglenkesekvensIdAndVeglenker.isEmpty()) {
                log.info("Ingen veglenkesekvenser å sette inn, backfill fullført. Totalt ca. ${veglenkerRepository.size()} veglenkesekvenser.")
                keyValues.putBackfillCompleted(clock.now())
            } else {
                val veglenkerById = veglenkesekvensIdAndVeglenker.associate { it.veglenkesekvensId to it.veglenker }

                rocksDbContext.writeBatch {
                    veglenkerRepository.batchInsert(veglenkerById)
                    keyValues.putBackfillLastId(lastId!!)
                }

                totalCount += veglenkesekvensIdAndVeglenker.size
                batchCount++
                if (batchCount % 50 == 0) {
                    log.info("Lastet $totalCount veglenkesekvenser")
                }
            }
        } while (veglenkesekvensIdAndVeglenker.isNotEmpty())

        return totalCount
    }

    suspend fun updateVeglenkesekvenser(): Int {
        val backfillCompleted = keyValues.getBackfillCompleted() ?: error("Veglenkesekvenser backfill er ikke ferdig")
        var lastHendelseId = keyValues.getLastHendelseId() ?: uberiketApi.getLatestVeglenkesekvensHendelseId(backfillCompleted)

        var hendelseCount = 0
        var batchCount = 0

        uberiketApi.streamVeglenkesekvensHendelser(lastHendelseId).chunked(100)
            .collect { hendelser ->
                lastHendelseId = hendelser.last().hendelseId

                val changedIds = hendelser.map { it.veglenkesekvensId }.toSet()
                val updates = fetchUpdates(changedIds)

                rocksDbContext.writeBatch {
                    veglenkerRepository.batchUpdate(updates)

                    performDirtyMarking(updates.keys)

                    keyValues.putLastHendelseId(lastHendelseId)
                }
                log.debug("Behandlet ${hendelser.size} hendelser, siste ID: $lastHendelseId")
                hendelseCount += hendelser.size
                batchCount++
                if (batchCount % 50 == 0) {
                    log.info("Behandlet $hendelseCount hendelser for veglenkesekvenser, siste ID: $lastHendelseId")
                }
            }

        log.info("Oppdatering fra $hendelseCount hendelser for veglenkesekvenser fullført. Siste hendelse-ID: $lastHendelseId")

        return hendelseCount
    }

    private fun WriteBatchContext.performDirtyMarking(veglenkesekvensIds: Set<Long>) {
        // If there are no EXPORTED_FEATURES yet it means that we are still in the initial backfill phase
        // and should not mark anything as dirty
        val shouldDirtyMark = rocksDbContext.hasAnyKeys(ColumnFamily.EXPORTED_FEATURES)
        if (shouldDirtyMark) {
            publishChangedVeglenkesekvenser(veglenkesekvensIds)
        }
    }

    private suspend fun fetchUpdates(changedIds: Set<Long>): Map<Long, List<Veglenke>?> {
        val veglenkerById = changedIds.chunked(100).flatMap { chunk ->
            uberiketApi.getVeglenkesekvenserWithIds(chunk).toList()
        }.associate { it.veglenkesekvensId to it.veglenker }

        // IDs that have no result from API will be marked as null, for deletion
        return changedIds.associateWith {
            veglenkerById[it]
        }
    }
}
