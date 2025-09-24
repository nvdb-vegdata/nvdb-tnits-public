package no.vegvesen.nvdb.tnits.storage

import kotlinx.serialization.KSerializer
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.serializer
import org.rocksdb.RocksDBException

class KeyValueRocksDbStore(private val rocksDbContext: RocksDbContext, private val columnFamily: ColumnFamily = ColumnFamily.KEY_VALUE) {

    fun <T : Any> get(key: String, serializer: KSerializer<T>): T? {
        val keyBytes = key.toByteArray()
        val valueBytes = rocksDbContext.get(columnFamily, keyBytes)

        return valueBytes?.let { deserializeValue(it, serializer) }
    }

    context(context: WriteBatchContext)
    fun <T : Any> put(key: String, value: T, serializer: KSerializer<T>) {
        val keyBytes = key.toByteArray()
        val valueBytes = serializeValue(value, serializer)

        context.write(columnFamily, BatchOperation.Put(keyBytes, valueBytes))
    }

    fun <T : Any> put(key: String, value: T, serializer: KSerializer<T>) {
        val keyBytes = key.toByteArray()
        val valueBytes = serializeValue(value, serializer)

        rocksDbContext.put(columnFamily, keyBytes, valueBytes)
    }

    fun delete(key: String) {
        val keyBytes = key.toByteArray()
        rocksDbContext.delete(columnFamily, keyBytes)
    }

    fun deleteKeysByPrefix(prefix: String) {
        val prefixBytes = prefix.toByteArray()
        val keysToDelete = rocksDbContext.findKeysByPrefix(columnFamily, prefixBytes)

        if (keysToDelete.isNotEmpty()) {
            val deleteOperations = keysToDelete.map { key -> BatchOperation.Delete(key) }
            rocksDbContext.writeBatch(columnFamily, deleteOperations)
        }
    }

    fun findKeysByPrefix(prefix: String): List<String> {
        val prefixBytes = prefix.toByteArray()
        val keyBytes = rocksDbContext.findKeysByPrefix(columnFamily, prefixBytes)

        return keyBytes.map { String(it) }
    }

    fun countKeysByPrefix(prefix: String): Int {
        val prefixBytes = prefix.toByteArray()
        val keys = rocksDbContext.findKeysByPrefix(columnFamily, prefixBytes)

        return keys.size
    }

    fun countKeysMatchingPattern(prefix: String, suffix: String): Int {
        val prefixBytes = prefix.toByteArray()
        val keys = rocksDbContext.findKeysByPrefix(columnFamily, prefixBytes)
        val suffixBytes = suffix.toByteArray()

        return keys.count { key -> key.endsWith(suffixBytes) }
    }

    fun clear() {
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

    fun size(): Long = rocksDbContext.getEstimatedKeys(columnFamily)

    // Convenience inline functions for reified types
    inline fun <reified T : Any> get(key: String): T? = get(key, serializer())

    inline fun <reified T : Any> put(key: String, value: T) = put(key, value, serializer())

    context(_: WriteBatchContext)
    inline fun <reified T : Any> put(key: String, value: T) = put(key, value, serializer())

    private fun <T : Any> serializeValue(value: T, serializer: KSerializer<T>): ByteArray = ProtoBuf.encodeToByteArray(serializer, value)

    private fun <T : Any> deserializeValue(data: ByteArray, serializer: KSerializer<T>): T = ProtoBuf.decodeFromByteArray(serializer, data)

    private fun ByteArray.endsWith(suffix: ByteArray): Boolean {
        if (this.size < suffix.size) return false

        val startIndex = this.size - suffix.size
        for (i in suffix.indices) {
            if (this[startIndex + i] != suffix[i]) return false
        }

        return true
    }
}
