package no.vegvesen.nvdb.tnits.storage

import org.rocksdb.ColumnFamilyHandle
import org.rocksdb.RocksDB
import org.rocksdb.WriteBatch
import org.rocksdb.WriteOptions
import java.nio.ByteBuffer

class NodePortCountRocksDbStore(
    private val db: RocksDB,
    private val columnFamily: ColumnFamilyHandle,
) : NodePortCountRepository {
    override fun get(nodeId: Long): Int? {
        val key = nodeId.toByteArray()
        val value = db.get(columnFamily, key)

        return value?.let { it.toInt() }
    }

    override fun batchGet(nodeIds: Collection<Long>): Map<Long, Int> {
        if (nodeIds.isEmpty()) {
            return emptyMap()
        }

        val columnFamilyHandleList = nodeIds.map { columnFamily }
        val keys = nodeIds.map { it.toByteArray() }
        val values = db.multiGetAsList(columnFamilyHandleList, keys)

        val result = mutableMapOf<Long, Int>()
        nodeIds.zip(values).forEach { (id, value) ->
            if (value != null) {
                result[id] = value.toInt()
            }
        }

        return result
    }

    override fun upsert(
        nodeId: Long,
        portCount: Int,
    ) {
        val key = nodeId.toByteArray()
        val value = portCount.toByteArray()

        db.put(columnFamily, key, value)
    }

    override fun delete(nodeId: Long) {
        val key = nodeId.toByteArray()
        db.delete(columnFamily, key)
    }

    override fun batchUpdate(updates: Map<Long, Int?>) {
        val writeBatch = WriteBatch()

        try {
            for ((id, portCount) in updates) {
                val key = id.toByteArray()
                if (portCount == null) {
                    writeBatch.delete(columnFamily, key)
                } else {
                    val value = portCount.toByteArray()
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

    override fun size(): Long = db.getProperty(columnFamily, "rocksdb.estimate-num-keys")?.toLongOrNull() ?: 0L

    private fun Long.toByteArray(): ByteArray = ByteBuffer.allocate(8).putLong(this).array()

    private fun Int.toByteArray(): ByteArray = ByteBuffer.allocate(4).putInt(this).array()

    private fun ByteArray.toInt(): Int {
        require(size >= 4) { "ByteArray must be at least 4 bytes long" }
        return ByteBuffer.wrap(this).getInt()
    }
}
