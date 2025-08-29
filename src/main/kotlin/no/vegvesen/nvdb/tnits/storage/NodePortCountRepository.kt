package no.vegvesen.nvdb.tnits.storage

interface NodePortCountRepository {
    fun get(nodeId: Long): Int?

    fun batchGet(nodeIds: Collection<Long>): Map<Long, Int>

    fun upsert(
        nodeId: Long,
        portCount: Int,
    )

    fun delete(nodeId: Long)

    fun batchUpdate(updates: Map<Long, Int?>) {
        for ((id, portCount) in updates) {
            if (portCount == null) {
                delete(id)
            } else {
                upsert(id, portCount)
            }
        }
    }

    fun size(): Long
}
