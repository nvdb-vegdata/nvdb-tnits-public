package no.vegvesen.nvdb.tnits.storage

import kotlinx.serialization.protobuf.ProtoBuf
import no.vegvesen.nvdb.tnits.model.ChangeType
import no.vegvesen.nvdb.tnits.storage.VegobjekterRocksDbStore.Companion.getStedfestingPrefix
import no.vegvesen.nvdb.tnits.storage.VegobjekterRocksDbStore.Companion.getStedfestingVegobjektId
import no.vegvesen.nvdb.tnits.storage.VegobjekterRocksDbStore.Companion.getVegobjektKey
import no.vegvesen.nvdb.tnits.storage.VegobjekterRocksDbStore.Companion.getVegobjektTypePrefix

class DirtyCheckingRocksDbStore(private val rocksDbContext: RocksDbContext) : DirtyCheckingRepository {

    override fun getDirtyVegobjektChanges(vegobjektType: Int): Set<VegobjektChange> {
        val directChanges = rocksDbContext.streamValuesByPrefix(ColumnFamily.DIRTY_VEGOBJEKTER, getVegobjektTypePrefix(vegobjektType))
            .map { value ->
                ProtoBuf.decodeFromByteArray(VegobjektChange.serializer(), value)
            }
            .toSet()
        val indirectChanges = rocksDbContext.streamAllKeys(ColumnFamily.DIRTY_VEGLENKESEKVENSER).map { it.toLong() }.toList().let { veglenkesekvensIds ->
            findStedfestingVegobjektIds(veglenkesekvensIds.toSet(), vegobjektType)
                .map { vegobjektId -> VegobjektChange(vegobjektId, ChangeType.MODIFIED) }
                .toSet()
        }
        return directChanges + indirectChanges
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
