package no.vegvesen.nvdb.tnits.storage

import no.vegvesen.nvdb.tnits.model.Veglenke

interface VeglenkerRepository {
    fun get(veglenkesekvensId: Long): List<Veglenke>?

    fun batchGet(veglenkesekvensIds: Collection<Long>): Map<Long, List<Veglenke>>

    fun getAll(): Map<Long, List<Veglenke>>

    fun upsert(veglenkesekvensId: Long, veglenker: List<Veglenke>)

    fun delete(veglenkesekvensId: Long)

    context(context: WriteBatchContext)
    fun batchUpdate(updates: Map<Long, List<Veglenke>?>)

    context(context: WriteBatchContext)
    fun batchInsert(veglenkerById: Map<Long, List<Veglenke>>)

    fun size(): Long
}
