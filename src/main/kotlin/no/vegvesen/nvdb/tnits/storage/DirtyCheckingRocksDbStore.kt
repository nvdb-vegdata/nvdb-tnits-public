package no.vegvesen.nvdb.tnits.storage

import no.vegvesen.nvdb.tnits.storage.VegobjekterRocksDbStore.Companion.getStedfestingPrefix
import no.vegvesen.nvdb.tnits.storage.VegobjekterRocksDbStore.Companion.getStedfestingVegobjektId
import no.vegvesen.nvdb.tnits.storage.VegobjekterRocksDbStore.Companion.getVegobjektId
import no.vegvesen.nvdb.tnits.storage.VegobjekterRocksDbStore.Companion.getVegobjektKey
import no.vegvesen.nvdb.tnits.storage.VegobjekterRocksDbStore.Companion.getVegobjektTypePrefix

class DirtyCheckingRocksDbStore(private val rocksDbContext: RocksDbContext) : DirtyCheckingRepository {

    override fun getDirtyVegobjektIds(vegobjektType: Int): Set<Long> {
        val prefix = getVegobjektTypePrefix(vegobjektType)
        return rocksDbContext.streamKeysByPrefix(ColumnFamily.DIRTY_VEGOBJEKTER, prefix)
            .map { getVegobjektId(it) }
            .toSet()
    }

    override fun findStedfestingVegobjektIds(veglenkesekvensIds: Set<Long>, vegobjektType: Int): Set<Long> {
        val vegobjektIds = mutableSetOf<Long>()

        for (veglenkesekvensId in veglenkesekvensIds) {
            val prefix = getStedfestingPrefix(vegobjektType, veglenkesekvensId)
            rocksDbContext.streamKeysByPrefix(ColumnFamily.VEGOBJEKTER, prefix).forEach { key ->
                vegobjektIds.add(getStedfestingVegobjektId(key))
            }
        }

        return vegobjektIds
    }

    override fun clearDirtyVegobjektIds(vegobjektType: Int, vegobjektIds: Set<Long>) {
        if (vegobjektIds.isEmpty()) return

        val deletions = vegobjektIds.map { vegobjektId ->
            BatchOperation.Delete(getVegobjektKey(vegobjektType, vegobjektId))
        }

        rocksDbContext.writeBatch(ColumnFamily.DIRTY_VEGOBJEKTER, deletions)
    }

    override fun clearAllDirtyVegobjektIds(vegobjektType: Int) {
        val prefix = getVegobjektTypePrefix(vegobjektType)
        val allDirtyKeys = rocksDbContext.findKeysByPrefix(ColumnFamily.DIRTY_VEGOBJEKTER, prefix)

        if (allDirtyKeys.isNotEmpty()) {
            val deletions = allDirtyKeys.map { BatchOperation.Delete(it) }
            rocksDbContext.writeBatch(ColumnFamily.DIRTY_VEGOBJEKTER, deletions)
        }
    }
}
