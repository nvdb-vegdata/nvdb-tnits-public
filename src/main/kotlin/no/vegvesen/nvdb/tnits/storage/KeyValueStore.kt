package no.vegvesen.nvdb.tnits.storage

import kotlinx.serialization.KSerializer

interface KeyValueStore {
    fun <T : Any> get(key: String, serializer: KSerializer<T>): T?
    fun <T : Any> put(key: String, value: T, serializer: KSerializer<T>)
    fun delete(key: String)
    fun deleteKeysByPrefix(prefix: String)
    fun findKeysByPrefix(prefix: String): List<String>
    fun countKeysByPrefix(prefix: String): Int
    fun countKeysMatchingPattern(prefix: String, suffix: String): Int
    fun clear()
    fun size(): Long

}