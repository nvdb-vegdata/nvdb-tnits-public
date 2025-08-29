package no.vegvesen.nvdb.tnits.storage

import no.vegvesen.nvdb.tnits.model.Veglenke

interface VeglenkerRepository {
    fun get(veglenkesekvensId: Long): List<Veglenke>?

    fun batchGet(veglenkesekvensIds: Collection<Long>): Map<Long, List<Veglenke>>

    fun getAll(): Map<Long, List<Veglenke>>

    fun upsert(
        veglenkesekvensId: Long,
        veglenker: List<Veglenke>,
    )

    fun delete(veglenkesekvensId: Long)

    fun batchUpdate(updates: Map<Long, List<Veglenke>?>) {
        for ((id, veglenker) in updates) {
            if (veglenker == null) {
                delete(id)
            } else {
                upsert(id, veglenker)
            }
        }
    }

    fun size(): Long
}
