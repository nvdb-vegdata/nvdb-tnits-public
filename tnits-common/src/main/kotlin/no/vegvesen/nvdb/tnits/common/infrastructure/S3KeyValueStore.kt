package no.vegvesen.nvdb.tnits.common.infrastructure

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import no.vegvesen.nvdb.tnits.common.api.SharedKeyValueStore
import no.vegvesen.nvdb.tnits.common.extensions.WithLogger

class S3KeyValueStore(private val minio: MinioGateway) :
    SharedKeyValueStore,
    WithLogger {
    override fun <T : Any> get(key: String, serializer: KSerializer<T>): T? {
        val bytes = minio.getOrNull(getObjectKey(key)) ?: return null
        return try {
            Json.decodeFromString(serializer, bytes.decodeToString())
        } catch (e: SerializationException) {
            log.error("Failed to deserialize value for key '$key': ${e.message}", e)
            null
        } catch (e: IllegalArgumentException) {
            log.error("Invalid JSON content for key '$key': ${e.message}", e)
            null
        }
    }

    override fun <T : Any> put(key: String, value: T, serializer: KSerializer<T>) {
        val json = Json.encodeToString(serializer, value)
        minio.put(getObjectKey(key), json.toByteArray())
    }

    override fun delete(key: String) {
        minio.delete(getObjectKey(key))
    }

    override fun clear() {
        minio.clear(KEY_PREFIX)
    }

    private fun getObjectKey(key: String) = "$KEY_PREFIX$key"

    companion object {
        private const val KEY_PREFIX = "admin-flags/"
    }
}
