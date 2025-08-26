package no.vegvesen.nvdb.tnits.storage

import no.vegvesen.nvdb.tnits.model.Veglenke

interface VeglenkerStore : AutoCloseable {
    suspend fun get(veglenkesekvensId: Long): List<Veglenke>?

    suspend fun batchGet(veglenkesekvensIds: Collection<Long>): Map<Long, List<Veglenke>>

    suspend fun getAll(): Map<Long, List<Veglenke>>

    suspend fun upsert(
        veglenkesekvensId: Long,
        veglenker: List<Veglenke>,
    )

    suspend fun delete(veglenkesekvensId: Long)

    suspend fun batchUpdate(updates: Map<Long, List<Veglenke>?>) {
        for ((id, veglenker) in updates) {
            if (veglenker == null) {
                delete(id)
            } else {
                upsert(id, veglenker)
            }
        }
    }

    suspend fun exists(veglenkesekvensId: Long): Boolean = get(veglenkesekvensId) != null

    suspend fun size(): Long

    suspend fun clear()
}
