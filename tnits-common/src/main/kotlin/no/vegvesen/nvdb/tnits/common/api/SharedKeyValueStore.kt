package no.vegvesen.nvdb.tnits.common.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

interface SharedKeyValueStore {
    fun <T : Any> get(key: String, serializer: KSerializer<T>): T?
    fun <T : Any> put(key: String, value: T, serializer: KSerializer<T>)
    fun delete(key: String)
    fun clear()
}

inline fun <reified T : Any> SharedKeyValueStore.getValue(key: String): T? = get(key, serializer())

inline fun <reified T : Any> SharedKeyValueStore.putValue(key: String, value: T) = put(key, value, serializer())

object AdminFlags {
    const val RESET_DB = "ResetDb"
    const val RESET_ROADNET = "ResetRoadnet"
    const val RESET_FEATURE_TYPES = "ResetFeatureTypes"
}
