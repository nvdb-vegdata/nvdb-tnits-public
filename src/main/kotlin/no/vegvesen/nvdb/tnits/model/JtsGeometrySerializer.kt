package no.vegvesen.nvdb.tnits.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializer
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.io.WKBReader
import org.locationtech.jts.io.WKBWriter

@Serializer(forClass = Geometry::class)
@OptIn(ExperimentalSerializationApi::class)
object JtsGeometrySerializer : KSerializer<Geometry> {
    fun createWriter(): WKBWriter = WKBWriter(2, true)

    fun createReader(): WKBReader = WKBReader()

    private val byteArraySerializer = ByteArraySerializer()

    override fun serialize(
        encoder: Encoder,
        value: Geometry,
    ) {
        val bytes = createWriter().write(value)
        encoder.encodeSerializableValue(byteArraySerializer, bytes)
    }

    override fun deserialize(decoder: Decoder): Geometry {
        val bytes = decoder.decodeSerializableValue(byteArraySerializer)
        return createReader().read(bytes)
    }
}
