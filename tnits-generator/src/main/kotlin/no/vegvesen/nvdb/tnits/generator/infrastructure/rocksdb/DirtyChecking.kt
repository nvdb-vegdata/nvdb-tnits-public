package no.vegvesen.nvdb.tnits.generator.infrastructure.rocksdb

import kotlinx.serialization.protobuf.ProtoBuf
import no.vegvesen.nvdb.tnits.generator.core.extensions.toByteArray
import no.vegvesen.nvdb.tnits.generator.core.services.storage.BatchOperation
import no.vegvesen.nvdb.tnits.generator.core.services.storage.ColumnFamily
import no.vegvesen.nvdb.tnits.generator.core.services.storage.VegobjektChange
import no.vegvesen.nvdb.tnits.generator.core.services.storage.WriteBatchContext
import no.vegvesen.nvdb.tnits.generator.infrastructure.rocksdb.VegobjekterRocksDbStore.Companion.getVegobjektKey
import kotlin.time.Instant

fun WriteBatchContext.publishChangedVeglenkesekvenser(veglenkesekvensIds: Set<Long>, now: Instant) {
    val operations = veglenkesekvensIds.map {
        BatchOperation.Put(it.toByteArray(), now.toEpochMilliseconds().toByteArray())
    }
    write(ColumnFamily.DIRTY_VEGLENKESEKVENSER, operations)
}

fun WriteBatchContext.publishChangedVegobjekter(vegobjektType: Int, changes: Collection<VegobjektChange>) {
    val operations = changes.map {
        val value = ProtoBuf.encodeToByteArray(VegobjektChange.serializer(), it)
        BatchOperation.Put(getVegobjektKey(vegobjektType, it.id), value)
    }
    write(ColumnFamily.DIRTY_VEGOBJEKTER, operations)
}
