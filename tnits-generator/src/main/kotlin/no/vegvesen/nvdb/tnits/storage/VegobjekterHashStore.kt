package no.vegvesen.nvdb.tnits.storage

import java.nio.ByteBuffer

class VegobjekterHashStore(private val rocksDbContext: RocksDbContext) {
    private val columnFamily: ColumnFamily = ColumnFamily.VEGOBJEKTER_HASH

    context(context: WriteBatchContext)
    fun batchUpdate(vegobjektType: Int, hashesById: Map<Long, Long>) {
        val operations = getOperations(hashesById, vegobjektType)
        context.write(columnFamily, operations)
    }

    fun batchUpdate(vegobjektType: Int, hashesById: Map<Long, Long>) {
        val operations = getOperations(hashesById, vegobjektType)
        rocksDbContext.writeBatch(columnFamily, operations)
    }

    private fun getOperations(hashesById: Map<Long, Long>, vegobjektType: Int): List<BatchOperation.Put> = hashesById.map { (vegobjektId, hash) ->
        val key = getVegobjektHashKey(vegobjektType, vegobjektId)
        BatchOperation.Put(key, hash.toByteArray())
    }

    fun batchGet(vegobjektType: Int, vegobjektIds: List<Long>): Map<Long, Long?> {
        val keys = vegobjektIds.map { getVegobjektHashKey(vegobjektType, it) }
        return rocksDbContext.getBatch(columnFamily, keys).map { it?.toLong() }.mapIndexed { i, hash ->
            vegobjektIds[i] to hash
        }.toMap()
    }

    fun get(vegobjektType: Int, vegobjektId: Long): Long? {
        val key = getVegobjektHashKey(vegobjektType, vegobjektId)
        return rocksDbContext.get(columnFamily, key)?.toLong()
    }

    fun put(vegobjektType: Int, vegobjektId: Long, hash: Long) {
        val key = getVegobjektHashKey(vegobjektType, vegobjektId)
        rocksDbContext.put(columnFamily, key, hash.toByteArray())
    }

    fun delete(vegobjektType: Int, vegobjektId: Long) {
        val key = getVegobjektHashKey(vegobjektType, vegobjektId)
        rocksDbContext.delete(columnFamily, key)
    }

    companion object {
        /**
         * Key structure: [4 bytes vegobjektType][8 bytes vegobjektId] = 12 bytes
         */
        private fun getVegobjektHashKey(vegobjektType: Int, vegobjektId: Long): ByteArray =
            ByteBuffer.allocate(12).putInt(vegobjektType).putLong(vegobjektId).array()
    }
}
