package no.vegvesen.nvdb.tnits.generator.infrastructure.rocksdb

import jakarta.inject.Singleton
import no.vegvesen.nvdb.tnits.generator.core.api.DirtyCheckingRepository
import no.vegvesen.nvdb.tnits.generator.core.api.VeglenkesekvensId
import no.vegvesen.nvdb.tnits.generator.core.api.VegobjektId
import no.vegvesen.nvdb.tnits.generator.core.extensions.toLong
import no.vegvesen.nvdb.tnits.generator.core.services.storage.BatchOperation
import no.vegvesen.nvdb.tnits.generator.core.services.storage.ColumnFamily

@Singleton
class DirtyCheckingRocksDbStore(private val rocksDbContext: RocksDbContext) : DirtyCheckingRepository {

    override fun getDirectDirtyVegobjektChanges(vegobjektType: Int): Set<VegobjektId> = rocksDbContext.streamKeysByPrefix(
        ColumnFamily.DIRTY_VEGOBJEKTER,
        VegobjekterRocksDbStore.getVegobjektTypePrefix(vegobjektType),
    )
        .map { VegobjekterRocksDbStore.getVegobjektId(it) }
        .toSet()

    override fun getIndirectDirtyVegobjektChanges(vegobjektType: Int): Set<VegobjektId> {
        // The mapping from supporting vegobjekter to dirty veglenkesekvenser is done at load time, so it is correct
        // to fetch indirectly located vegobjekter here
        val veglenkesekvensIds = rocksDbContext.streamAllKeys(ColumnFamily.DIRTY_VEGLENKESEKVENSER).map { it.toLong() }.toSet()
        return findStedfestingVegobjektIds(veglenkesekvensIds, vegobjektType)
    }

    override fun findStedfestingVegobjektIds(veglenkesekvensIds: Set<VeglenkesekvensId>, vegobjektType: Int): Set<VegobjektId> {
        val vegobjektIds = mutableSetOf<VegobjektId>()

        for (veglenkesekvensId in veglenkesekvensIds) {
            val prefix = VegobjekterRocksDbStore.getStedfestingPrefix(vegobjektType, veglenkesekvensId)
            rocksDbContext.streamKeysByPrefix(ColumnFamily.VEGOBJEKTER, prefix).forEach { key ->
                vegobjektIds.add(VegobjekterRocksDbStore.getStedfestingVegobjektId(key))
            }
        }

        return vegobjektIds
    }

    override fun clearAllDirtyVeglenkesekvenser() {
        rocksDbContext.clear(ColumnFamily.DIRTY_VEGLENKESEKVENSER)
    }

    override fun clearDirtyVegobjektIds(vegobjektType: Int, vegobjektIds: Set<Long>) {
        if (vegobjektIds.isEmpty()) return

        val deletions = vegobjektIds.map { vegobjektId ->
            BatchOperation.Delete(VegobjekterRocksDbStore.getVegobjektKey(vegobjektType, vegobjektId))
        }

        rocksDbContext.writeBatch(ColumnFamily.DIRTY_VEGOBJEKTER, deletions)
    }

    override fun clearAllDirtyVegobjektIds(vegobjektType: Int) {
        val prefix = VegobjekterRocksDbStore.getVegobjektTypePrefix(vegobjektType)
        val allDirtyKeys = rocksDbContext.findKeysByPrefix(ColumnFamily.DIRTY_VEGOBJEKTER, prefix)

        if (allDirtyKeys.isNotEmpty()) {
            val deletions = allDirtyKeys.map { BatchOperation.Delete(it) }
            rocksDbContext.writeBatch(ColumnFamily.DIRTY_VEGOBJEKTER, deletions)
        }
    }
}
