package no.vegvesen.nvdb.tnits.storage

import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import no.vegvesen.nvdb.tnits.model.TnitsFeature

class ExportedFeatureStore(private val rocksDbContext: RocksDbContext) {
    private val columnFamily: ColumnFamily = ColumnFamily.EXPORTED_FEATURES

    context(context: WriteBatchContext)
    fun batchUpdate(featuresById: Map<Long, TnitsFeature>) {
        val operations = featuresById.map { (vegobjektId, feature) ->
            val key = vegobjektId.toByteArray()
            BatchOperation.Put(key, ProtoBuf.encodeToByteArray(feature))
        }
        context.write(columnFamily, operations)
    }

    fun batchUpdate(featuresById: Map<Long, TnitsFeature>) {
        rocksDbContext.writeBatch {
            batchUpdate(featuresById)
        }
    }

    fun get(vegobjektId: Long): TnitsFeature? {
        val key = vegobjektId.toByteArray()
        val value = rocksDbContext.get(columnFamily, key) ?: return null
        return ProtoBuf.decodeFromByteArray<TnitsFeature>(value)
    }
}
