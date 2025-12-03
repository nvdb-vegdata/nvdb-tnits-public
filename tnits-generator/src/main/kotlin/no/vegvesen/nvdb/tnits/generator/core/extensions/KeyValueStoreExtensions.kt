package no.vegvesen.nvdb.tnits.generator.core.extensions

import kotlinx.serialization.builtins.serializer
import no.vegvesen.nvdb.tnits.common.model.ExportedFeatureType
import no.vegvesen.nvdb.tnits.generator.core.api.KeyValueStore
import no.vegvesen.nvdb.tnits.generator.core.api.getValue
import no.vegvesen.nvdb.tnits.generator.core.api.putValue
import kotlin.time.Instant

fun KeyValueStore.clearVeglenkesekvensSettings() {
    deleteKeysByPrefix("veglenkesekvenser_")
}

fun KeyValueStore.clearVegobjektSettings(typeId: Int) {
    deleteKeysByPrefix("vegobjekter_$typeId")
}

fun KeyValueStore.getWorkerLastIdCount(): Int = countKeysByPrefix("veglenkesekvenser_backfill_last_id_")

fun KeyValueStore.getRangeWorkerCount(): Int = countKeysMatchingPattern("veglenkesekvenser_backfill_range_", "_completed")

fun KeyValueStore.isRangeCompleted(workerIndex: Int): Boolean = getValue<Boolean>("veglenkesekvenser_backfill_range_${workerIndex}_completed") ?: false

fun KeyValueStore.markRangeCompleted(workerIndex: Int) {
    putValue("veglenkesekvenser_backfill_range_${workerIndex}_completed", true)
}

fun KeyValueStore.putLastUpdateCheck(featureType: ExportedFeatureType, timestamp: Instant) {
    this.put("vegobjekter_${featureType.typeId}_last_update_check", timestamp, Instant.serializer())
}

fun KeyValueStore.getLastUpdateCheck(featureType: ExportedFeatureType): Instant? =
    this.get("vegobjekter_${featureType.typeId}_last_update_check", Instant.serializer())
