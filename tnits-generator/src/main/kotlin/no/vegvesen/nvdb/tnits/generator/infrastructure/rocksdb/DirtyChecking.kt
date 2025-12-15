package no.vegvesen.nvdb.tnits.generator.infrastructure.rocksdb

import no.vegvesen.nvdb.tnits.generator.core.api.VeglenkesekvensId
import no.vegvesen.nvdb.tnits.generator.core.api.VegobjektId
import no.vegvesen.nvdb.tnits.generator.core.extensions.toByteArray
import no.vegvesen.nvdb.tnits.generator.core.services.storage.BatchOperation
import no.vegvesen.nvdb.tnits.generator.core.services.storage.ColumnFamily
import no.vegvesen.nvdb.tnits.generator.core.services.storage.WriteBatchContext
import no.vegvesen.nvdb.tnits.generator.infrastructure.rocksdb.VegobjekterRocksDbStore.Companion.getVegobjektKey

fun WriteBatchContext.publishChangedVeglenkesekvenser(veglenkesekvensIds: Set<VeglenkesekvensId>) {
    val operations = veglenkesekvensIds.map {
        BatchOperation.Put(it.toByteArray(), ByteArray(0))
    }
    write(ColumnFamily.DIRTY_VEGLENKESEKVENSER, operations)
}

fun WriteBatchContext.publishChangedVegobjekter(vegobjektType: Int, changedIds: Set<VegobjektId>) {
    val operations = changedIds.map { id ->
        BatchOperation.Put(getVegobjektKey(vegobjektType, id), ByteArray(0))
    }
    write(ColumnFamily.DIRTY_VEGOBJEKTER, operations)
}
