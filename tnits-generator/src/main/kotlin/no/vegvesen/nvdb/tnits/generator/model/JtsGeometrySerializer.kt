package no.vegvesen.nvdb.tnits.generator.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializer
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import no.vegvesen.nvdb.tnits.generator.geometry.SRID
import no.vegvesen.nvdb.tnits.generator.geometry.geometryFactories
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.io.WKBReader
import org.locationtech.jts.io.WKBWriter

@Serializer(forClass = Geometry::class)
object JtsGeometrySerializer : KSerializer<Geometry> {
    fun createWriter(): WKBWriter = WKBWriter(2)

    fun createReader(): WKBReader = WKBReader(geometryFactories[SRID.WGS84])

    private val byteArraySerializer = ByteArraySerializer()

    override fun serialize(encoder: Encoder, value: Geometry) {
        check(value.srid == SRID.WGS84) {
            "Only SRID ${SRID.WGS84} is supported, but got ${value.srid}"
        }
        val bytes = createWriter().write(value)
        encoder.encodeSerializableValue(byteArraySerializer, bytes)
    }

    override fun deserialize(decoder: Decoder): Geometry {
        val bytes = decoder.decodeSerializableValue(byteArraySerializer)
        return createReader().read(bytes)
    }
}
