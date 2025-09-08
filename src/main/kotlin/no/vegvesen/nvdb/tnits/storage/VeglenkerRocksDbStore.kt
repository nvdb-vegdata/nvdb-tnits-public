package no.vegvesen.nvdb.tnits.storage

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.protobuf.ProtoBuf
import no.vegvesen.nvdb.tnits.model.Veglenke
import org.rocksdb.ColumnFamilyHandle
import org.rocksdb.RocksDB
import org.rocksdb.RocksDBException
import org.rocksdb.WriteBatch
import org.rocksdb.WriteOptions
import java.nio.ByteBuffer

@OptIn(ExperimentalSerializationApi::class)
class VeglenkerRocksDbStore(
    private val db: RocksDB,
    private val columnFamily: ColumnFamilyHandle,
) : VeglenkerRepository {
    override fun get(veglenkesekvensId: Long): List<Veglenke>? {
        val key = veglenkesekvensId.toByteArray()
        val value = db.get(columnFamily, key)

        return value?.let { deserializeVeglenker(it) }
    }

    override fun batchGet(veglenkesekvensIds: Collection<Long>): Map<Long, List<Veglenke>> {
        if (veglenkesekvensIds.isEmpty()) {
            return emptyMap()
        }

        val columnFamilyHandleList = veglenkesekvensIds.map { columnFamily }
        val keys = veglenkesekvensIds.map { it.toByteArray() }
        val values = db.multiGetAsList(columnFamilyHandleList, keys)

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

        db.newIterator(columnFamily).use { iterator ->
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

    override fun upsert(
        veglenkesekvensId: Long,
        veglenker: List<Veglenke>,
    ) {
        val key = veglenkesekvensId.toByteArray()
        val value = serializeVeglenker(veglenker)

        db.put(columnFamily, key, value)
    }

    override fun delete(veglenkesekvensId: Long) {
        val key = veglenkesekvensId.toByteArray()
        db.delete(columnFamily, key)
    }

    override fun batchUpdate(updates: Map<Long, List<Veglenke>?>) {
        val writeBatch = WriteBatch()

        try {
            for ((id, veglenker) in updates) {
                val key = id.toByteArray()
                if (veglenker == null) {
                    writeBatch.delete(columnFamily, key)
                } else {
                    val value = serializeVeglenker(veglenker)
                    writeBatch.put(columnFamily, key, value)
                }
            }

            WriteOptions().use { writeOpts ->
                db.write(writeOpts, writeBatch)
            }
        } finally {
            writeBatch.close()
        }
    }

    fun clear() {
        try {
            db.deleteRange(columnFamily, ByteArray(0), ByteArray(8) { 0xFF.toByte() })
        } catch (e: RocksDBException) {
            throw RuntimeException("Failed to clear veglenker data", e)
        }
    }

    override fun size(): Long = db.getProperty(columnFamily, "rocksdb.estimate-num-keys")?.toLongOrNull() ?: 0L

    private fun serializeVeglenker(veglenker: List<Veglenke>): ByteArray =
        ProtoBuf.encodeToByteArray(ListSerializer(Veglenke.serializer()), veglenker)

    private fun deserializeVeglenker(data: ByteArray): List<Veglenke> =
        ProtoBuf.decodeFromByteArray(ListSerializer(Veglenke.serializer()), data)

    private fun Long.toByteArray(): ByteArray = ByteBuffer.allocate(8).putLong(this).array()

    private fun ByteArray.toLong(): Long {
        require(size >= 8) { "ByteArray must be at least 8 bytes long" }
        return ByteBuffer.wrap(this).getLong()
    }
}
