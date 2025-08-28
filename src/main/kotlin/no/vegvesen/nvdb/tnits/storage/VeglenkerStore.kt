package no.vegvesen.nvdb.tnits.storage

import no.vegvesen.nvdb.tnits.model.Veglenke

interface VeglenkerStore : AutoCloseable {
    fun getVeglenker(veglenkesekvensId: Long): List<Veglenke>?

    fun batchGetVeglenker(veglenkesekvensIds: Collection<Long>): Map<Long, List<Veglenke>>

    fun getAllVeglenker(): Map<Long, List<Veglenke>>

    fun upsertVeglenker(
        veglenkesekvensId: Long,
        veglenker: List<Veglenke>,
    )

    fun deleteVeglenker(veglenkesekvensId: Long)

    fun batchUpdateVeglenker(updates: Map<Long, List<Veglenke>?>) {
        for ((id, veglenker) in updates) {
            if (veglenker == null) {
                deleteVeglenker(id)
            } else {
                upsertVeglenker(id, veglenker)
            }
        }
    }

    fun getNodePortCount(nodeId: Long): Int?

    fun batchGetNodePortCounts(nodeIds: Collection<Long>): Map<Long, Int>

    fun upsertNodePortCount(
        nodeId: Long,
        portCount: Int,
    )

    fun deleteNodePortCount(nodeId: Long)

    fun batchUpdateNodePortCounts(updates: Map<Long, Int?>) {
        for ((id, portCount) in updates) {
            if (portCount == null) {
                deleteNodePortCount(id)
            } else {
                upsertNodePortCount(id, portCount)
            }
        }
    }

    fun size(): Long

    fun clear()
}
