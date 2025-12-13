package no.vegvesen.nvdb.tnits.generator.core.extensions

import kotlinx.serialization.builtins.serializer
import no.vegvesen.nvdb.tnits.common.model.ExportedFeatureType
import no.vegvesen.nvdb.tnits.generator.core.api.KeyValueStore
import kotlin.time.Instant

fun KeyValueStore.clearVeglenkesekvensSettings() {
    deleteKeysByPrefix("veglenkesekvenser_")
}

fun KeyValueStore.clearVegobjektSettings(typeId: Int) {
    deleteKeysByPrefix("vegobjekter_$typeId")
}

fun KeyValueStore.putLastUpdateCheck(featureType: ExportedFeatureType, timestamp: Instant) {
    this.put("vegobjekter_${featureType.typeId}_last_update_check", timestamp, Instant.serializer())
}

fun KeyValueStore.getLastUpdateCheck(featureType: ExportedFeatureType): Instant? =
    this.get("vegobjekter_${featureType.typeId}_last_update_check", Instant.serializer())
