package no.vegvesen.nvdb.tnits.storage

import kotlin.time.Clock

fun WriteBatchContext.publishChangedVeglenkesekvenser(veglenkesekvensIds: Set<Long>) {
    val now = Clock.System.now()
    val dirtyVeglenkesekvenserUpserts = veglenkesekvensIds.map {
        BatchOperation.Put(it.toByteArray(), now.toEpochMilliseconds().toByteArray())
    }
    write(ColumnFamily.DIRTY_VEGLENKESEKVENSER, dirtyVeglenkesekvenserUpserts)
}
