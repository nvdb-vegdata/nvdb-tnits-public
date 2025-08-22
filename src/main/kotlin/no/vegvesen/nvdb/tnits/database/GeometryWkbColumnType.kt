package no.vegvesen.nvdb.tnits.database

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Table
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.io.WKBReader
import org.locationtech.jts.io.WKBWriter
import java.nio.ByteBuffer
import java.sql.Blob

/**
 * BYTEA column storing pure WKB (no SRID).
 * Reads/writes JTS Geometry.
 */
class GeometryWkbColumnType : ColumnType<Geometry>() {
    private val reader = WKBReader()

    private val writer = WKBWriter(3, false)

    override fun sqlType(): String = "BYTEA"

    override fun valueFromDB(value: Any): Geometry =
        when (value) {
            is Geometry -> value
            is ByteArray -> reader.read(value)
            is ByteBuffer -> {
                val arr = ByteArray(value.remaining())
                value.get(arr)
                reader.read(arr)
            }

            is Blob -> value.getBytes(1, value.length().toInt()).also { value.free() }.let(reader::read)
            else -> error("Unsupported DB value for Geometry WKB: ${value::class} ($value)")
        }

    override fun notNullValueToDB(value: Geometry): Any = writeWkb(value)

    // Prepared statements use this binding; string form not needed.
    override fun nonNullValueToString(value: Geometry): String = "?"

    private fun writeWkb(g: Geometry): ByteArray = writer.write(g)

    companion object {
        /** Table helper */
        fun Table.geometryWkb(name: String): Column<Geometry> = registerColumn(name, GeometryWkbColumnType())
    }
}
