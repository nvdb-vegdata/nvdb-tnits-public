package no.vegvesen.nvdb.tnits.generator.infrastructure.ingest

import jakarta.inject.Singleton
import kotlinx.coroutines.flow.chunked
import kotlinx.coroutines.flow.toList
import no.vegvesen.nvdb.tnits.common.extensions.WithLogger
import no.vegvesen.nvdb.tnits.generator.core.api.KeyValueStore
import no.vegvesen.nvdb.tnits.generator.core.api.UberiketApi
import no.vegvesen.nvdb.tnits.generator.core.api.VeglenkerRepository
import no.vegvesen.nvdb.tnits.generator.core.api.vegnettKeyValues
import no.vegvesen.nvdb.tnits.generator.core.extensions.forEachChunked
import no.vegvesen.nvdb.tnits.generator.core.extensions.today
import no.vegvesen.nvdb.tnits.generator.core.model.Veglenke
import no.vegvesen.nvdb.tnits.generator.core.model.convertToDomainVeglenker
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

        val today = clock.today()

        do {
            val veglenkesekvenser = uberiketApi.streamVeglenkesekvenser(start = lastId).toList()
            lastId = veglenkesekvenser.lastOrNull()?.id

            if (veglenkesekvenser.isEmpty()) {
                log.info("Ingen veglenkesekvenser å sette inn, backfill fullført. Totalt ca. ${veglenkerRepository.size()} veglenkesekvenser.")
                keyValues.putBackfillCompleted(clock.now())
            } else {
                val updates = veglenkesekvenser.associate {
                    val domainVeglenker = it.convertToDomainVeglenker(today)
                    it.id to domainVeglenker
                }

                rocksDbContext.writeBatch {
                    veglenkerRepository.batchInsert(updates)
                    keyValues.putBackfillLastId(lastId!!)
                }

                totalCount += veglenkesekvenser.size
                batchCount++
                if (batchCount % 50 == 0) {
                    log.info("Lastet $totalCount veglenkesekvenser")
                }
            }
        } while (veglenkesekvenser.isNotEmpty())

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

                val changedIds = hendelser.map { it.nettelementId }.toSet()
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

    private fun WriteBatchContext.performDirtyMarking(veglenkesekvensIds: MutableSet<Long>) {
        // If there are no EXPORTED_FEATURES yet it means that we are still in the initial backfill phase
        // and should not mark anything as dirty
        val shouldDirtyMark = rocksDbContext.hasAnyKeys(ColumnFamily.EXPORTED_FEATURES)
        if (shouldDirtyMark) {
            publishChangedVeglenkesekvenser(veglenkesekvensIds, clock.now())
        }
    }

    private suspend fun fetchUpdates(changedIds: Set<Long>): MutableMap<Long, List<Veglenke>?> {
        val updates = mutableMapOf<Long, List<Veglenke>?>()

        val today = clock.today()

        // Process changed veglenkesekvenser in chunks
        changedIds.forEachChunked(100) { chunk ->
            var start: Long? = null
            do {
                val batch =
                    uberiketApi
                        .streamVeglenkesekvenser(start = start, ider = chunk)
                        .toList()

                if (batch.isNotEmpty()) {
                    batch.forEach { veglenkesekvens ->
                        val domainVeglenker = veglenkesekvens.convertToDomainVeglenker(today)
                        updates[veglenkesekvens.id] = domainVeglenker
                    }
                    start = batch.maxOf { it.id }
                }
            } while (batch.isNotEmpty())
        }

        // Handle deleted veglenkesekvenser (those that didn't return data)
        val foundIds = updates.keys
        val deletedIds = changedIds - foundIds
        deletedIds.forEach { deletedId ->
            updates[deletedId] = null // Mark for deletion
        }
        return updates
    }
}
