package no.vegvesen.nvdb.tnits.generator.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializer
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import no.vegvesen.nvdb.tnits.generator.core.extensions.SRID.UTM33
import no.vegvesen.nvdb.tnits.generator.core.extensions.geometryFactories
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.io.WKBReader
import org.locationtech.jts.io.WKBWriter

@Serializer(forClass = Geometry::class)
object JtsGeometrySerializer : KSerializer<Geometry> {
    fun createWriter(): WKBWriter = WKBWriter(2)

    fun createReader(): WKBReader = WKBReader(geometryFactories[UTM33])

    private val byteArraySerializer = ByteArraySerializer()

    override fun serialize(encoder: Encoder, value: Geometry) {
        check(value.srid == UTM33) {
            "Only SRID $UTM33 is supported, but got ${value.srid}"
        }
        val bytes = createWriter().write(value)
        encoder.encodeSerializableValue(byteArraySerializer, bytes)
    }

    override fun deserialize(decoder: Decoder): Geometry {
        val bytes = decoder.decodeSerializableValue(byteArraySerializer)
        return createReader().read(bytes)
    }
}
