package no.vegvesen.nvdb.tnits.storage

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import no.vegvesen.nvdb.tnits.model.ChangeType
import no.vegvesen.nvdb.tnits.storage.VegobjekterRocksDbStore.Companion.getVegobjektKey
import kotlin.time.Clock

fun WriteBatchContext.publishChangedVeglenkesekvenser(veglenkesekvensIds: Set<Long>) {
    val now = Clock.System.now()
    val operations = veglenkesekvensIds.map {
        BatchOperation.Put(it.toByteArray(), now.toEpochMilliseconds().toByteArray())
    }
    write(ColumnFamily.DIRTY_VEGLENKESEKVENSER, operations)
}

@OptIn(ExperimentalSerializationApi::class)
fun WriteBatchContext.publishChangedVegobjekter(vegobjektType: Int, changes: Collection<VegobjektChange>) {
    val operations = changes.map {
        val value = ProtoBuf.encodeToByteArray(VegobjektChange.serializer(), it)
        BatchOperation.Put(getVegobjektKey(vegobjektType, it.id), value)
    }
    write(ColumnFamily.DIRTY_VEGOBJEKTER, operations)
}

@Serializable
data class VegobjektChange(val id: Long, val changeType: ChangeType)
