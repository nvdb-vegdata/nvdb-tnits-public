package no.vegvesen.nvdb.tnits.generator.infrastructure.ingest

import jakarta.inject.Singleton
import kotlinx.coroutines.flow.chunked
import kotlinx.coroutines.flow.toList
import no.vegvesen.nvdb.tnits.common.extensions.WithLogger
import no.vegvesen.nvdb.tnits.generator.core.api.*
import no.vegvesen.nvdb.tnits.generator.core.extensions.forEachChunked
import no.vegvesen.nvdb.tnits.generator.core.extensions.today
import no.vegvesen.nvdb.tnits.generator.core.model.Veglenke
import no.vegvesen.nvdb.tnits.generator.core.model.convertToDomainVeglenker
import no.vegvesen.nvdb.tnits.generator.infrastructure.rocksdb.RocksDbContext
import kotlin.time.Clock
import kotlin.time.Instant

@Singleton
class VegnettLoader(
    private val keyValueStore: KeyValueStore,
    private val uberiketApi: UberiketApi,
    private val veglenkerRepository: VeglenkerRepository,
    private val rocksDbContext: RocksDbContext,
    private val clock: Clock,
) : WithLogger {

    suspend fun backfillVeglenkesekvenser(): Int {
        val backfillCompleted = keyValueStore.getValue<Instant>("veglenkesekvenser_backfill_completed")

        if (backfillCompleted != null) {
            log.info("Backfill for veglenkesekvenser er allerede fullført den $backfillCompleted")
            return 0
        }

        var lastId = keyValueStore.getValue<Long>("veglenkesekvenser_backfill_last_id")

        if (lastId == null) {
            log.info("Ingen veglenkesekvenser backfill har blitt startet ennå. Starter backfill...")
            val now = clock.now()
            keyValueStore.putValue("veglenkesekvenser_backfill_started", now)
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
                log.info("Ingen veglenkesekvenser å sette inn, backfill fullført.")
                keyValueStore.putValue("veglenkesekvenser_backfill_completed", clock.now())
            } else {
                val updates = veglenkesekvenser.associate {
                    val domainVeglenker = it.convertToDomainVeglenker(today)
                    it.id to domainVeglenker
                }

                rocksDbContext.writeBatch {
                    veglenkerRepository.batchInsert(updates)
                    keyValueStore.putValue("veglenkesekvenser_backfill_last_id", lastId!!)
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
        var lastHendelseId =
            keyValueStore.getValue<Long>("veglenkesekvenser_last_hendelse_id") ?: uberiketApi.getLatestVeglenkesekvensHendelseId(
                keyValueStore.getValue<Instant>("veglenkesekvenser_backfill_completed")
                    ?: error("Veglenkesekvenser backfill er ikke ferdig"),
            )

        var hendelseCount = 0
        var batchCount = 0

        uberiketApi.streamVeglenkesekvensHendelser(lastHendelseId).chunked(100)
            .collect { hendelser ->
                lastHendelseId = hendelser.last().hendelseId

                val changedIds = hendelser.map { it.nettelementId }.toSet()
                val updates = fetchUpdates(changedIds)

                rocksDbContext.writeBatch {
                    veglenkerRepository.batchUpdate(updates)
                    keyValueStore.putValue("veglenkesekvenser_last_hendelse_id", lastHendelseId)
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
