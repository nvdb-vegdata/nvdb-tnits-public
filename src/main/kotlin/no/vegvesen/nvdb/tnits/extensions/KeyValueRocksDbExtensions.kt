package no.vegvesen.nvdb.tnits.extensions

import no.vegvesen.nvdb.tnits.storage.KeyValueRocksDbStore

fun KeyValueRocksDbStore.clearVeglenkesekvensSettings() {
    deleteKeysByPrefix("veglenkesekvenser_")
}

fun KeyValueRocksDbStore.getWorkerLastIdCount(): Int = countKeysByPrefix("veglenkesekvenser_backfill_last_id_")

fun KeyValueRocksDbStore.getRangeWorkerCount(): Int = countKeysMatchingPattern("veglenkesekvenser_backfill_range_", "_completed")

fun KeyValueRocksDbStore.isRangeCompleted(workerIndex: Int): Boolean = get<Boolean>("veglenkesekvenser_backfill_range_${workerIndex}_completed") ?: false

fun KeyValueRocksDbStore.markRangeCompleted(workerIndex: Int) {
    put("veglenkesekvenser_backfill_range_${workerIndex}_completed", true)
}
