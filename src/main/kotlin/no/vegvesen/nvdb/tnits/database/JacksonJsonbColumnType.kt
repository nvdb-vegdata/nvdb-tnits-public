package no.vegvesen.nvdb.tnits.database

import no.vegvesen.nvdb.tnits.objectMapper
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.core.vendors.OracleDialect
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import org.jetbrains.exposed.v1.json.JsonColumnType
import kotlin.reflect.KClass

class JacksonJsonbColumnType<T : Any>(
    clazz: KClass<T>,
) : JsonColumnType<T>({
        objectMapper.writeValueAsString(it)
    }, {
        objectMapper.readValue(it, clazz.java)
    }) {
    override val usesBinaryFormat: Boolean = true

    override fun sqlType(): String =
        when (val dialect = currentDialect) {
            is H2Dialect -> dialect.originalDataTypeProvider.jsonBType()
            is OracleDialect -> "JSON" // Oracle 21c+ native JSON support
            else -> dialect.dataTypeProvider.jsonBType()
        }

    companion object {
        inline fun <reified T : Any> Table.jacksonJsonb(name: String) = registerColumn(name, JacksonJsonbColumnType(T::class))
    }
}
