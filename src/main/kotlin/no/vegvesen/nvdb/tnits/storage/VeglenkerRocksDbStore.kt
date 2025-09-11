package no.vegvesen.nvdb.tnits.storage

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.protobuf.ProtoBuf
import no.vegvesen.nvdb.tnits.model.Veglenke
import org.rocksdb.RocksDBException

@OptIn(ExperimentalSerializationApi::class)
class VeglenkerRocksDbStore(private val rocksDbConfig: RocksDbConfiguration, private val columnFamily: ColumnFamily = ColumnFamily.VEGLENKER) :
    VeglenkerRepository {
    override fun get(veglenkesekvensId: Long): List<Veglenke>? {
        val key = veglenkesekvensId.toByteArray()
        val value = rocksDbConfig.get(columnFamily, key)

        return value?.let { deserializeVeglenker(it) }
    }

    override fun batchGet(veglenkesekvensIds: Collection<Long>): Map<Long, List<Veglenke>> {
        if (veglenkesekvensIds.isEmpty()) {
            return emptyMap()
        }

        val keys = veglenkesekvensIds.map { it.toByteArray() }
        val values = rocksDbConfig.getBatch(columnFamily, keys)

        val result = mutableMapOf<Long, List<Veglenke>>()
        veglenkesekvensIds.zip(values).forEach { (id, value) ->
            if (value != null) {
                result[id] = deserializeVeglenker(value)
            }
        }

        return result
    }

    override fun getAll(): Map<Long, List<Veglenke>> {
        val result = mutableMapOf<Long, List<Veglenke>>()

        rocksDbConfig.newIterator(columnFamily).use { iterator ->
            iterator.seekToFirst()
            while (iterator.isValid) {
                val key =
                    ByteArray(8).let {
                        iterator.key().copyInto(it)
                        it.toLong()
                    }
                val veglenker = deserializeVeglenker(iterator.value())
                result[key] = veglenker
                iterator.next()
            }
        }

        return result
    }

    override fun upsert(veglenkesekvensId: Long, veglenker: List<Veglenke>) {
        val key = veglenkesekvensId.toByteArray()
        val value = serializeVeglenker(veglenker)

        rocksDbConfig.put(columnFamily, key, value)
    }

    override fun delete(veglenkesekvensId: Long) {
        val key = veglenkesekvensId.toByteArray()
        rocksDbConfig.delete(columnFamily, key)
    }

    override fun batchUpdate(updates: Map<Long, List<Veglenke>?>) {
        val operations =
            updates.map { (id, veglenker) ->
                val key = id.toByteArray()
                if (veglenker == null) {
                    BatchOperation.Delete(key)
                } else {
                    val value = serializeVeglenker(veglenker)
                    BatchOperation.Put(key, value)
                }
            }

        rocksDbConfig.writeBatch(columnFamily, operations)
    }

    fun clear() {
        try {
            rocksDbConfig.deleteRange(columnFamily, ByteArray(0), ByteArray(8) { 0xFF.toByte() })
        } catch (e: RocksDBException) {
            throw RuntimeException("Failed to clear veglenker data", e)
        }
    }

    override fun size(): Long = rocksDbConfig.getEstimatedKeys(columnFamily)

    private fun serializeVeglenker(veglenker: List<Veglenke>): ByteArray = ProtoBuf.encodeToByteArray(ListSerializer(Veglenke.serializer()), veglenker)

    private fun deserializeVeglenker(data: ByteArray): List<Veglenke> = ProtoBuf.decodeFromByteArray(ListSerializer(Veglenke.serializer()), data)
}
