package no.vegvesen.nvdb.tnits.storage

import no.vegvesen.nvdb.tnits.storage.VegobjekterRocksDbStore.Companion.getVegobjektKey
import kotlin.time.Clock

fun WriteBatchContext.publishChangedVeglenkesekvenser(veglenkesekvensIds: Set<Long>) {
    val now = Clock.System.now()
    val dirtyVeglenkesekvenserUpserts = veglenkesekvensIds.map {
        BatchOperation.Put(it.toByteArray(), now.toEpochMilliseconds().toByteArray())
    }
    write(ColumnFamily.DIRTY_VEGLENKESEKVENSER, dirtyVeglenkesekvenserUpserts)
}

fun WriteBatchContext.publishChangedVegobjekter(vegobjektType: Int, vegobjektIds: Set<Long>) {
    val now = Clock.System.now()
    val dirtyVegobjekterUpserts = vegobjektIds.map {
        BatchOperation.Put(getVegobjektKey(vegobjektType, it), now.toEpochMilliseconds().toByteArray())
    }
    write(ColumnFamily.DIRTY_VEGOBJEKTER, dirtyVegobjekterUpserts)
}
