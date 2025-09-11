package no.vegvesen.nvdb.tnits.extensions

import kotlinx.serialization.serializer
import no.vegvesen.nvdb.tnits.storage.KeyValueStore

// Convenience inline functions for reified types
inline fun <reified T : Any> KeyValueStore.get(key: String): T? = get(key, serializer())
inline fun <reified T : Any> KeyValueStore.put(key: String, value: T) = put(key, value, serializer())
