package no.vegvesen.nvdb.tnits.generator.core.services.storage

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import no.vegvesen.nvdb.tnits.generator.core.extensions.toByteArray
import no.vegvesen.nvdb.tnits.generator.core.model.ChangeType
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

@Serializable
data class VegobjektChange(val id: Long, val changeType: ChangeType)
