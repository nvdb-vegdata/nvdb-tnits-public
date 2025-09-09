package no.vegvesen.nvdb.tnits.storage

import java.nio.ByteBuffer

fun Long.toByteArray(): ByteArray = ByteBuffer.allocate(8).putLong(this).array()

fun ByteArray.toLong(): Long {
    require(size >= 8) { "ByteArray must be at least 8 bytes long" }
    return ByteBuffer.wrap(this).getLong()
}
