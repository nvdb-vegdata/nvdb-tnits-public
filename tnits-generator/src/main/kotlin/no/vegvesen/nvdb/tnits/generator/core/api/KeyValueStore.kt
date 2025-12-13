package no.vegvesen.nvdb.tnits.generator.core.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import no.vegvesen.nvdb.tnits.generator.core.services.storage.WriteBatchContext
import kotlin.time.Instant

interface KeyValueStore {
    fun <T : Any> get(key: String, serializer: KSerializer<T>): T?

    context(_: WriteBatchContext)
    fun <T : Any> put(key: String, value: T, serializer: KSerializer<T>)
    fun <T : Any> put(key: String, value: T, serializer: KSerializer<T>)
    fun delete(key: String)
    fun deleteKeysByPrefix(prefix: String)
    fun findKeysByPrefix(prefix: String): List<String>
    fun countKeysByPrefix(prefix: String): Int
    fun countKeysMatchingPattern(prefix: String, suffix: String): Int
    fun clear()
    fun size(): Long
}

// Different name to avoid clashing with the generic get/put methods when importing
inline fun <reified T : Any> KeyValueStore.getValue(key: String): T? = get(key, serializer())

context(_: WriteBatchContext)
inline fun <reified T : Any> KeyValueStore.putValue(key: String, value: T) = put(key, value, serializer())
inline fun <reified T : Any> KeyValueStore.putValue(key: String, value: T) = put(key, value, serializer())

/**
 * Strongly typed utility wrapper for vegobjekt-type-specific key-value operations
 */
class VegobjekterKeyValues(val typeId: Int, private val store: KeyValueStore) {
    private class Keys(typeId: Int) {

        val backfillStarted = vegobjekterBackfillStarted(typeId)
        val backfillCompleted = vegobjekterBackfillCompleted(typeId)
        val backfillLastId = vegobjekterBackfillLastId(typeId)
        val lastHendelseId = vegobjekterLastHendelseId(typeId)

        companion object {

            private fun vegobjekterBackfillStarted(typeId: Int) = "vegobjekter_${typeId}_backfill_started"
            private fun vegobjekterBackfillCompleted(typeId: Int) = "vegobjekter_${typeId}_backfill_completed"
            private fun vegobjekterBackfillLastId(typeId: Int) = "vegobjekter_${typeId}_backfill_last_id"
            private fun vegobjekterLastHendelseId(typeId: Int) = "vegobjekter_${typeId}_last_hendelse_id"
        }
    }

    private val keys = Keys(typeId)

    fun putBackfillStarted(instant: Instant) = store.putValue(keys.backfillStarted, instant)
    fun getBackfillStarted() = store.getValue<Instant>(keys.backfillStarted)

    fun putBackfillCompleted(instant: Instant) = store.putValue(keys.backfillCompleted, instant)
    fun getBackfillCompleted() = store.getValue<Instant>(keys.backfillCompleted)

    context(_: WriteBatchContext)
    fun putBackfillLastId(id: Long) = store.putValue(keys.backfillLastId, id)
    fun getBackfillLastId() = store.getValue<Long>(keys.backfillLastId)

    context(_: WriteBatchContext)
    fun putLastHendelseId(id: Long) = store.putValue(keys.lastHendelseId, id)
    fun getLastHendelseId() = store.getValue<Long>(keys.lastHendelseId)

    companion object {
        fun KeyValueStore.vegobjekterKeyValues(typeId: Int) = VegobjekterKeyValues(typeId, this)
    }
}

class VegnettKeyValues(private val store: KeyValueStore) {

    companion object {
        private const val VEGNETT_BACKFILL_STARTED = "veglenkesekvenser_backfill_started"
        private const val VEGNETT_BACKFILL_COMPLETED = "veglenkesekvenser_backfill_completed"
        private const val VEGNETT_BACKFILL_LAST_ID = "veglenkesekvenser_backfill_last_id"
        private const val VEGNETT_LAST_HENDELSE_ID = "veglenkesekvenser_last_hendelse_id"
    }

    fun putBackfillStarted(instant: Instant) = store.putValue(VEGNETT_BACKFILL_STARTED, instant)
    fun getBackfillStarted() = store.getValue<Instant>(VEGNETT_BACKFILL_STARTED)

    fun putBackfillCompleted(instant: Instant) = store.putValue(VEGNETT_BACKFILL_COMPLETED, instant)
    fun getBackfillCompleted() = store.getValue<Instant>(VEGNETT_BACKFILL_COMPLETED)

    context(_: WriteBatchContext)
    fun putBackfillLastId(id: Long) = store.putValue(VEGNETT_BACKFILL_LAST_ID, id)
    fun getBackfillLastId() = store.getValue<Long>(VEGNETT_BACKFILL_LAST_ID)

    context(_: WriteBatchContext)
    fun putLastHendelseId(id: Long) = store.putValue(VEGNETT_LAST_HENDELSE_ID, id)
    fun getLastHendelseId() = store.getValue<Long>(VEGNETT_LAST_HENDELSE_ID)
}

val KeyValueStore.vegnettKeyValues get() = VegnettKeyValues(this)
