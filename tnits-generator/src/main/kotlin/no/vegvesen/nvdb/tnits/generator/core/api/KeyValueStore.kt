package no.vegvesen.nvdb.tnits.generator.core.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import no.vegvesen.nvdb.tnits.generator.core.services.storage.WriteBatchContext

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
