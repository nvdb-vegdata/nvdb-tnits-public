package no.vegvesen.nvdb.tnits.extensions

import no.vegvesen.nvdb.tnits.storage.KeyValueStore

fun KeyValueStore.clearVeglenkesekvensSettings() {
    deleteKeysByPrefix("veglenkesekvenser_")
}

fun KeyValueStore.getWorkerLastIdCount(): Int = countKeysByPrefix("veglenkesekvenser_backfill_last_id_")

fun KeyValueStore.getRangeWorkerCount(): Int = countKeysMatchingPattern("veglenkesekvenser_backfill_range_", "_completed")

fun KeyValueStore.isRangeCompleted(workerIndex: Int): Boolean = get<Boolean>("veglenkesekvenser_backfill_range_${workerIndex}_completed") ?: false

fun KeyValueStore.markRangeCompleted(workerIndex: Int) {
    put("veglenkesekvenser_backfill_range_${workerIndex}_completed", true)
}
