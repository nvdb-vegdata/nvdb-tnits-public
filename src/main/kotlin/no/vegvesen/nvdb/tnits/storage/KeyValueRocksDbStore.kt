package no.vegvesen.nvdb.tnits.storage

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.serializer
import org.rocksdb.RocksDBException

@OptIn(ExperimentalSerializationApi::class)
class KeyValueRocksDbStore(
    private val rocksDbConfig: RocksDbConfiguration,
    private val columnFamily: ColumnFamily = ColumnFamily.KEY_VALUE
) : KeyValueStore {
    override fun <T : Any> get(key: String, serializer: KSerializer<T>): T? {
        val keyBytes = key.toByteArray()
        val valueBytes = rocksDbConfig.get(columnFamily, keyBytes)

        return valueBytes?.let { deserializeValue(it, serializer) }
    }

    override fun <T : Any> put(key: String, value: T, serializer: KSerializer<T>) {
        val keyBytes = key.toByteArray()
        val valueBytes = serializeValue(value, serializer)

        rocksDbConfig.put(columnFamily, keyBytes, valueBytes)
    }

    override fun delete(key: String) {
        val keyBytes = key.toByteArray()
        rocksDbConfig.delete(columnFamily, keyBytes)
    }

    override fun deleteKeysByPrefix(prefix: String) {
        val prefixBytes = prefix.toByteArray()
        val keysToDelete = rocksDbConfig.findKeysByPrefix(columnFamily, prefixBytes)

        if (keysToDelete.isNotEmpty()) {
            val deleteOperations = keysToDelete.map { key -> BatchOperation.Delete(key) }
            rocksDbConfig.batchWrite(columnFamily, deleteOperations)
        }
    }

    override fun findKeysByPrefix(prefix: String): List<String> {
        val prefixBytes = prefix.toByteArray()
        val keyBytes = rocksDbConfig.findKeysByPrefix(columnFamily, prefixBytes)

        return keyBytes.map { String(it) }
    }

    override fun countKeysByPrefix(prefix: String): Int {
        val prefixBytes = prefix.toByteArray()
        val keys = rocksDbConfig.findKeysByPrefix(columnFamily, prefixBytes)

        return keys.size
    }

    override fun countKeysMatchingPattern(prefix: String, suffix: String): Int {
        val prefixBytes = prefix.toByteArray()
        val keys = rocksDbConfig.findKeysByPrefix(columnFamily, prefixBytes)
        val suffixBytes = suffix.toByteArray()

        return keys.count { key -> key.endsWith(suffixBytes) }
    }

    override fun clear() {
        try {
            val keysToDelete = mutableListOf<ByteArray>()

            rocksDbConfig.newIterator(columnFamily).use { iterator ->
                iterator.seekToFirst()
                while (iterator.isValid) {
                    keysToDelete.add(iterator.key().clone())
                    iterator.next()
                }
            }

            if (keysToDelete.isNotEmpty()) {
                val deleteOperations = keysToDelete.map { key -> BatchOperation.Delete(key) }
                rocksDbConfig.batchWrite(columnFamily, deleteOperations)
            }
        } catch (e: RocksDBException) {
            throw RuntimeException("Failed to clear key-value data", e)
        }
    }

    override fun size(): Long = rocksDbConfig.getEstimatedKeys(columnFamily)

    // Convenience inline functions for reified types
    inline fun <reified T : Any> get(key: String): T? = get(key, serializer())
    inline fun <reified T : Any> put(key: String, value: T) = put(key, value, serializer())

    private fun <T : Any> serializeValue(value: T, serializer: KSerializer<T>): ByteArray =
        ProtoBuf.encodeToByteArray(serializer, value)

    private fun <T : Any> deserializeValue(data: ByteArray, serializer: KSerializer<T>): T =
        ProtoBuf.decodeFromByteArray(serializer, data)

    private fun ByteArray.endsWith(suffix: ByteArray): Boolean {
        if (this.size < suffix.size) return false

        val startIndex = this.size - suffix.size
        for (i in suffix.indices) {
            if (this[startIndex + i] != suffix[i]) return false
        }

        return true
    }
}
