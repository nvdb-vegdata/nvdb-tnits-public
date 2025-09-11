package no.vegvesen.nvdb.tnits.storage

import kotlin.time.Clock

class DirtyVeglenkesekvenserRocksDbStore(private val rocksDbContext: RocksDbContext) : DirtyVeglenkesekvenserRepository {

    private val columnFamily = ColumnFamily.DIRTY_VEGLENKESEKVENSER

    override fun publishChangedVeglenkesekvensIds(changedIds: Collection<Long>) {
        val now = Clock.System.now()

        rocksDbContext.writeBatch(
            columnFamily,
            changedIds.map { id ->
                BatchOperation.Put(
                    key = id.toByteArray(),
                    value = now.toEpochMilliseconds().toByteArray(),
                )
            },
        )
    }
}
