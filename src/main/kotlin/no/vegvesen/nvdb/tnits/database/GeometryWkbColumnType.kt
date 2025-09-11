package no.vegvesen.nvdb.tnits.database

import no.vegvesen.nvdb.tnits.geometry.SRID
import no.vegvesen.nvdb.tnits.geometry.geometryFactories
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.vendors.OracleDialect
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.io.WKBReader
import org.locationtech.jts.io.WKBWriter
import java.nio.ByteBuffer
import java.sql.Blob

/**
 * Binary column storing pure WKB geometry data (no SRID).
 * Uses BYTEA for PostgreSQL/H2 and BLOB for Oracle.
 * Reads/writes JTS Geometry.
 */
class GeometryWkbColumnType : ColumnType<Geometry>() {
    // Create new instances for each operation - fully coroutine-safe
    private fun createReader() = WKBReader(geometryFactories[SRID.UTM33])

    private fun createWriter() = WKBWriter(2, false)

    override fun sqlType(): String = when (currentDialect) {
        is OracleDialect -> "BLOB"
        else -> "BYTEA" // PostgreSQL, H2, and others
    }

    override fun valueFromDB(value: Any): Geometry = when (value) {
        is Geometry -> value
        is ByteArray -> createReader().read(value)
        is ByteBuffer -> {
            val arr = ByteArray(value.remaining())
            value.get(arr)
            createReader().read(arr)
        }

        is Blob ->
            value.getBytes(1, value.length().toInt()).also { value.free() }.let {
                createReader().read(it)
            }

        else -> error("Unsupported DB value for Geometry WKB: ${value::class} ($value)")
    }

    override fun notNullValueToDB(value: Geometry): Any = writeWkb(value)

    // Prepared statements use this binding; string form not needed.
    override fun nonNullValueToString(value: Geometry): String = "?"

    private fun writeWkb(g: Geometry): ByteArray = createWriter().write(g)

    companion object {
        /** Table helper */
        fun Table.geometryWkb(name: String): Column<Geometry> = registerColumn(name, GeometryWkbColumnType())
    }
}
