package no.vegvesen.nvdb.tnits.extensions

import kotlinx.serialization.builtins.serializer
import no.vegvesen.nvdb.tnits.model.ExportedFeatureType
import no.vegvesen.nvdb.tnits.storage.KeyValueRocksDbStore
import kotlin.time.Instant

fun KeyValueRocksDbStore.clearVeglenkesekvensSettings() {
    deleteKeysByPrefix("veglenkesekvenser_")
}

fun KeyValueRocksDbStore.clearVegobjektSettings() {
    deleteKeysByPrefix("vegobjekter_")
}

fun KeyValueRocksDbStore.getWorkerLastIdCount(): Int = countKeysByPrefix("veglenkesekvenser_backfill_last_id_")

fun KeyValueRocksDbStore.getRangeWorkerCount(): Int = countKeysMatchingPattern("veglenkesekvenser_backfill_range_", "_completed")

fun KeyValueRocksDbStore.isRangeCompleted(workerIndex: Int): Boolean = get<Boolean>("veglenkesekvenser_backfill_range_${workerIndex}_completed") ?: false

fun KeyValueRocksDbStore.markRangeCompleted(workerIndex: Int) {
    put("veglenkesekvenser_backfill_range_${workerIndex}_completed", true)
}

fun KeyValueRocksDbStore.putLastUpdateCheck(featureType: ExportedFeatureType, timestamp: Instant) {
    this.put("vegobjekter_${featureType.typeCode}_last_update_check", timestamp, Instant.serializer())
}

fun KeyValueRocksDbStore.getLastUpdateCheck(featureType: ExportedFeatureType): Instant? =
    this.get("vegobjekter_${featureType.typeCode}_last_update_check", Instant.serializer())
