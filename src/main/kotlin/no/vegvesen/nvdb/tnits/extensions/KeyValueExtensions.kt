package no.vegvesen.nvdb.tnits.extensions

import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import no.vegvesen.nvdb.tnits.database.KeyValue
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.v1.jdbc.upsert

private val json = Json { ignoreUnknownKeys = true }

// Suspend versions for standalone use
suspend fun <T : Any> KeyValue.get(
    key: String,
    serializer: KSerializer<T>,
): T? =
    newSuspendedTransaction(Dispatchers.IO) {
        KeyValue
            .selectAll()
            .where { KeyValue.key eq key }
            .map { it[KeyValue.value] }
            .singleOrNull()
            ?.let { json.decodeFromString(serializer, it) }
    }

suspend fun <T : Any> KeyValue.put(
    key: String,
    value: T,
    serializer: KSerializer<T>,
) {
    newSuspendedTransaction(Dispatchers.IO) {
        putSync(key, value, serializer)
    }
}

suspend fun KeyValue.remove(key: String) {
    newSuspendedTransaction(Dispatchers.IO) {
        KeyValue.deleteWhere { KeyValue.key eq key }
    }
}

suspend fun KeyValue.deleteAll() {
    newSuspendedTransaction(Dispatchers.IO) {
        KeyValue.deleteAll()
    }
}

// Non-suspend versions for use within existing transactions
fun <T : Any> KeyValue.getSync(
    key: String,
    serializer: KSerializer<T>,
): T? =
    KeyValue
        .selectAll()
        .where { KeyValue.key eq key }
        .map { it[KeyValue.value] }
        .singleOrNull()
        ?.let { json.decodeFromString(serializer, it) }

fun <T : Any> KeyValue.putSync(
    key: String,
    value: T,
    serializer: KSerializer<T>,
) {
    val serializedValue = json.encodeToString(serializer, value)
    KeyValue.upsert {
        it[KeyValue.key] = key
        it[KeyValue.value] = serializedValue
    }
}

fun KeyValue.removeSync(key: String) {
    KeyValue.deleteWhere { KeyValue.key eq key }
}

// Convenience inline functions
suspend inline fun <reified T : Any> KeyValue.get(key: String): T? = get(key, serializer())

suspend inline fun <reified T : Any> KeyValue.put(
    key: String,
    value: T,
) = put(key, value, serializer())

inline fun <reified T : Any> KeyValue.getSync(key: String): T? = getSync(key, serializer())

inline fun <reified T : Any> KeyValue.putSync(
    key: String,
    value: T,
) = putSync(key, value, serializer())
