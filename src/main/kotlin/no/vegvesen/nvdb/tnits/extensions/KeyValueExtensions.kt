package no.vegvesen.nvdb.tnits.extensions

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import no.vegvesen.nvdb.tnits.database.KeyValue
import org.jetbrains.exposed.v1.core.statements.UpsertSqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert

private val json = Json { ignoreUnknownKeys = true }

fun <T : Any> KeyValue.get(
    key: String,
    serializer: KSerializer<T>,
): T? =
    transaction {
        KeyValue
            .selectAll()
            .where { KeyValue.key eq key }
            .map { it[KeyValue.value] }
            .singleOrNull()
            ?.let { json.decodeFromString(serializer, it) }
    }

fun <T : Any> KeyValue.put(
    key: String,
    value: T,
    serializer: KSerializer<T>,
) {
    transaction {
        val serializedValue = json.encodeToString(serializer, value)
        KeyValue.upsert {
            it[KeyValue.key] = key
            it[KeyValue.value] = serializedValue
        }
    }
}

fun KeyValue.remove(key: String) {
    transaction {
        KeyValue.deleteWhere { KeyValue.key eq key }
    }
}

fun KeyValue.deleteAll() {
    transaction {
        KeyValue.deleteAll()
    }
}

inline fun <reified T : Any> KeyValue.get(key: String): T? = get(key, serializer())

inline fun <reified T : Any> KeyValue.put(
    key: String,
    value: T,
) = put(key, value, serializer())
