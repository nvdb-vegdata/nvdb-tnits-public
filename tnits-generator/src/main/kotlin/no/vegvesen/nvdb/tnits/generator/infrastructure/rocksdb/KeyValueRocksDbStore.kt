package no.vegvesen.nvdb.tnits.generator.infrastructure.rocksdb

import jakarta.inject.Singleton
import kotlinx.serialization.KSerializer
import no.vegvesen.nvdb.tnits.generator.core.api.KeyValueStore
import no.vegvesen.nvdb.tnits.generator.core.extensions.fromProtobuf
import no.vegvesen.nvdb.tnits.generator.core.extensions.toProtobuf
import no.vegvesen.nvdb.tnits.generator.core.services.storage.BatchOperation
import no.vegvesen.nvdb.tnits.generator.core.services.storage.ColumnFamily
import no.vegvesen.nvdb.tnits.generator.core.services.storage.WriteBatchContext
import org.rocksdb.RocksDBException

@Singleton
class KeyValueRocksDbStore(private val rocksDbContext: RocksDbContext) : KeyValueStore {
    private val columnFamily: ColumnFamily = ColumnFamily.KEY_VALUE

    override fun <T : Any> get(key: String, serializer: KSerializer<T>): T? {
        val keyBytes = key.toByteArray()
        val valueBytes = rocksDbContext.get(columnFamily, keyBytes)

        return valueBytes?.fromProtobuf(serializer)
    }

    context(context: WriteBatchContext)
    override fun <T : Any> put(key: String, value: T, serializer: KSerializer<T>) {
        val keyBytes = key.toByteArray()
        val valueBytes = value.toProtobuf(serializer)

        context.write(columnFamily, BatchOperation.Put(keyBytes, valueBytes))
    }

    override fun <T : Any> put(key: String, value: T, serializer: KSerializer<T>) {
        val keyBytes = key.toByteArray()
        val valueBytes = value.toProtobuf(serializer)

        rocksDbContext.put(columnFamily, keyBytes, valueBytes)
    }

    override fun delete(key: String) {
        val keyBytes = key.toByteArray()
        rocksDbContext.delete(columnFamily, keyBytes)
    }

    override fun deleteKeysByPrefix(prefix: String) {
        val prefixBytes = prefix.toByteArray()
        val keysToDelete = rocksDbContext.findKeysByPrefix(columnFamily, prefixBytes)

        if (keysToDelete.isNotEmpty()) {
            val deleteOperations = keysToDelete.map { key -> BatchOperation.Delete(key) }
            rocksDbContext.writeBatch(columnFamily, deleteOperations)
        }
    }

    override fun findKeysByPrefix(prefix: String): List<String> {
        val prefixBytes = prefix.toByteArray()
        val keyBytes = rocksDbContext.findKeysByPrefix(columnFamily, prefixBytes)

        return keyBytes.map { String(it) }
    }

    override fun countKeysByPrefix(prefix: String): Int {
        val prefixBytes = prefix.toByteArray()
        val keys = rocksDbContext.findKeysByPrefix(columnFamily, prefixBytes)

        return keys.size
    }

    override fun countKeysMatchingPattern(prefix: String, suffix: String): Int {
        val prefixBytes = prefix.toByteArray()
        val keys = rocksDbContext.findKeysByPrefix(columnFamily, prefixBytes)
        val suffixBytes = suffix.toByteArray()

        return keys.count { key -> key.endsWith(suffixBytes) }
    }

    override fun clear() {
        try {
            val keysToDelete = mutableListOf<ByteArray>()

            rocksDbContext.newIterator(columnFamily).use { iterator ->
                iterator.seekToFirst()
                while (iterator.isValid) {
                    keysToDelete.add(iterator.key().clone())
                    iterator.next()
                }
            }

            if (keysToDelete.isNotEmpty()) {
                val deleteOperations = keysToDelete.map { key -> BatchOperation.Delete(key) }
                rocksDbContext.writeBatch(columnFamily, deleteOperations)
            }
        } catch (e: RocksDBException) {
            throw RuntimeException("Failed to clear key-value data", e)
        }
    }

    override fun size(): Long = rocksDbContext.getEstimatedKeys(columnFamily)

    private fun ByteArray.endsWith(suffix: ByteArray): Boolean {
        if (this.size < suffix.size) return false

        val startIndex = this.size - suffix.size
        for (i in suffix.indices) {
            if (this[startIndex + i] != suffix[i]) return false
        }

        return true
    }
}
