package no.vegvesen.nvdb.tnits.storage

import no.vegvesen.nvdb.apiles.uberiket.Vegobjekt

interface VegobjekterRepository {
    context(_: WriteBatchContext)
    fun batchUpdate(updates: Map<Long, Vegobjekt?>)

    context(_: WriteBatchContext)
    fun batchInsert(vegobjekter: List<Vegobjekt>)
}
