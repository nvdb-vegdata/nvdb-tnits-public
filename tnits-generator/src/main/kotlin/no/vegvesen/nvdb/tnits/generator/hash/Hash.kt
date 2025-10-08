package no.vegvesen.nvdb.tnits.generator.hash

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

private val seedCache = ConcurrentHashMap(mapOf(0 to "0000000000000000".toByteArray()))

private fun getKey(seed: Int): ByteArray = seedCache.computeIfAbsent(seed) { abs(it).toString().padStart(16, '0').toByteArray() }

fun String.getHash(): Long = SipHasher.hash(getKey(0), this.toByteArray())

fun ByteArray.getHash(seed: Int? = null): Long = SipHasher.hash(getKey(seed ?: 0), this)
