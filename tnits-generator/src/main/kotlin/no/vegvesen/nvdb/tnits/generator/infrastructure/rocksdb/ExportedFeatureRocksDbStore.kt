package no.vegvesen.nvdb.tnits.generator.infrastructure.rocksdb

import jakarta.inject.Singleton
import no.vegvesen.nvdb.tnits.generator.core.api.ExportedFeatureRepository
import no.vegvesen.nvdb.tnits.generator.core.extensions.fromProtobuf
import no.vegvesen.nvdb.tnits.generator.core.extensions.toByteArray
import no.vegvesen.nvdb.tnits.generator.core.extensions.toProtobuf
import no.vegvesen.nvdb.tnits.generator.core.model.tnits.TnitsFeature
import no.vegvesen.nvdb.tnits.generator.core.services.storage.BatchOperation
import no.vegvesen.nvdb.tnits.generator.core.services.storage.ColumnFamily
import no.vegvesen.nvdb.tnits.generator.core.services.storage.WriteBatchContext

@Singleton
class ExportedFeatureRocksDbStore(private val rocksDbContext: RocksDbContext) : ExportedFeatureRepository {
    private val columnFamily: ColumnFamily = ColumnFamily.EXPORTED_FEATURES

    context(context: WriteBatchContext)
    fun batchUpdate(featuresById: Map<Long, TnitsFeature>) {
        val operations = featuresById.map { (vegobjektId, feature) ->
            val key = getKey(vegobjektId)
            val value = feature.toProtobuf()
            BatchOperation.Put(key, value)
        }
        context.write(columnFamily, operations)
    }

    override fun batchUpdate(featuresById: Map<Long, TnitsFeature>) {
        rocksDbContext.writeBatch {
            batchUpdate(featuresById)
        }
    }

    private fun getKey(vegobjektId: Long): ByteArray = vegobjektId.toByteArray()

    override fun getExportedFeatures(vegobjektIds: Collection<Long>): Map<Long, TnitsFeature> {
        if (vegobjektIds.isEmpty()) {
            return emptyMap()
        }

        val keys = vegobjektIds.map { it.toByteArray() }
        val values = rocksDbContext.getBatch(columnFamily, keys)

        return values.mapNotNull { it?.fromProtobuf<TnitsFeature>() }.associateBy { it.id }
    }
}
