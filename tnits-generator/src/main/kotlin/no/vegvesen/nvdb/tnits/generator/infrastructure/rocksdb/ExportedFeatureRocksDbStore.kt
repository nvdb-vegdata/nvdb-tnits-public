package no.vegvesen.nvdb.tnits.generator.infrastructure.rocksdb

import jakarta.inject.Singleton
import kotlinx.serialization.SerializationException
import no.vegvesen.nvdb.tnits.common.extensions.WithLogger
import no.vegvesen.nvdb.tnits.generator.core.api.ExportedFeatureRepository
import no.vegvesen.nvdb.tnits.generator.core.extensions.fromProtobuf
import no.vegvesen.nvdb.tnits.generator.core.extensions.toByteArray
import no.vegvesen.nvdb.tnits.generator.core.extensions.toProtobuf
import no.vegvesen.nvdb.tnits.generator.core.model.tnits.TnitsFeature
import no.vegvesen.nvdb.tnits.generator.core.services.storage.BatchOperation
import no.vegvesen.nvdb.tnits.generator.core.services.storage.ColumnFamily
import no.vegvesen.nvdb.tnits.generator.core.services.storage.WriteBatchContext

@Singleton
class ExportedFeatureRocksDbStore(private val rocksDbContext: RocksDbContext) : ExportedFeatureRepository, WithLogger {
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

    override fun getExportedFeatures(vegobjektIds: List<Long>): Map<Long, TnitsFeature> {
        if (vegobjektIds.isEmpty()) {
            return emptyMap()
        }

        val keys = vegobjektIds.map { it.toByteArray() }
        val values = rocksDbContext.getBatch(columnFamily, keys)

        val failedIds = mutableSetOf<Long>()

        // This is safe because getBatch always returns the same number of values as keys, in the same order.
        val features = values.mapIndexedNotNull { i, value ->
            try {
                value?.fromProtobuf<TnitsFeature>()
            } catch (serializationException: SerializationException) {
                failedIds.add(vegobjektIds[i])
                null
            }
        }

        if (failedIds.isNotEmpty()) {
            log.error("Failed to deserialize features for ${failedIds.size} vegobjekt IDs, deleting from exported cache")
            val keysToDelete = failedIds.map { getKey(it) }
            rocksDbContext.writeBatch(columnFamily, keysToDelete.map { BatchOperation.Delete(it) })
        }

        return features.associateBy { it.id }
    }
}
