package no.vegvesen.nvdb.tnits.generator.core.extensions

import kotlinx.serialization.KSerializer
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.serializer

fun <T : Any> T.toProtobuf(serializer: KSerializer<T>): ByteArray = ProtoBuf.encodeToByteArray(serializer, this)

inline fun <reified T : Any> T.toProtobuf(): ByteArray = toProtobuf(serializer<T>())

fun <T : Any> ByteArray.fromProtobuf(serializer: KSerializer<T>): T = ProtoBuf.decodeFromByteArray(serializer, this)

inline fun <reified T : Any> ByteArray.fromProtobuf(): T = fromProtobuf(serializer<T>())
