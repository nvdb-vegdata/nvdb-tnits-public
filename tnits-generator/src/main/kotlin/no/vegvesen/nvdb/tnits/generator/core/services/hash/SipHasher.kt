/**
 * The MIT License (MIT)
 *
 *
 * Copyright (c) 2016 Isaac Whitfield
 *
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package no.vegvesen.nvdb.tnits.generator.core.services.hash

/**
 * Provides hashing for the SipHash cryptographic hash family.
 *
 *
 * This class offers three main utilities;
 *
 *
 * - A zero-allocation SipHash algorithm.
 * - A container implementation for single-key environments.
 * - A streaming SipHash algorithm for unknown input length.
 *
 *
 * In most cases, the zero-allocation (0A) implementation will be desired. This
 * can be called via [.hash] on the most basic level.
 *
 *
 * In the case you're using a single key (such as one seeded at application
 * startup), you can make good use of a container which will simply avoid the
 * need to recalculate the initial states on each hash call. This is an extremely
 * small optimization, but avoids all possible overhead for the best throughput.
 * Containers can be created via the [.container] method, and can
 * hash input via [SipHasherContainer.hash].
 *
 *
 * For the case where the input length is unknown, a streaming implementation is
 * available via [SipHasherStream]. This can be initialized on a per-hash
 * basis via [.init] and can be updated with bytes multiple times
 * via [SipHasherStream.update]. Once all input has been updated,
 * a final call to [SipHasherStream.digest] will return the digested data.
 */
object SipHasher {
    /**
     * Default value for the C rounds of compression.
     */
    const val DEFAULT_C: Int = 2

    /**
     * Default value for the D rounds of compression.
     */
    const val DEFAULT_D: Int = 4

    /**
     * Initial value for the v0 magic number.
     */
    const val INITIAL_V0: Long = 0x736f6d6570736575L

    /**
     * Initial value for the v1 magic number.
     */
    const val INITIAL_V1: Long = 0x646f72616e646f6dL

    /**
     * Initial value for the v2 magic number.
     */
    const val INITIAL_V2: Long = 0x6c7967656e657261L

    /**
     * Initial value for the v3 magic number.
     */
    const val INITIAL_V3: Long = 0x7465646279746573L

    /**
     * Creates a new container, seeded with the provided key.
     *
     * @param key the key bytes used to seed the container.
     * @return a [SipHasherContainer] instance after initialization.
     */
    fun container(key: ByteArray): SipHasherContainer = SipHasherContainer(key)

    /**
     * Hashes a data input for a given key, using the provided rounds
     * of compression.
     *
     * @param key  the key to seed the hash with.
     * @param data the input data to hash.
     * @param c    the number of C rounds of compression
     * @param d    the number of D rounds of compression.
     * @return a long value as the output of the hash.
     */

    /**
     * Hashes a data input for a given key.
     *
     *
     * This will used the default values for C and D rounds.
     *
     * @param key  the key to seed the hash with.
     * @param data the input data to hash.
     * @return a long value as the output of the hash.
     */
    @JvmOverloads
    fun hash(key: ByteArray, data: ByteArray, c: Int = DEFAULT_C, d: Int = DEFAULT_D): Long {
        require(key.size == 16) { "Key must be exactly 16 bytes!" }

        val k0 = bytesToLong(key, 0)
        val k1 = bytesToLong(key, 8)

        return hash(
            c,
            d,
            INITIAL_V0 xor k0,
            INITIAL_V1 xor k1,
            INITIAL_V2 xor k0,
            INITIAL_V3 xor k1,
            data,
        )
    }

    /**
     * Initializes a streaming hash, seeded with the given key and desired
     * rounds of compression.
     *
     *
     * This will used the default values for C and D rounds.
     *
     * @param key the key to seed the hash with.
     * @param c   the number of C rounds of compression
     * @param d   the number of D rounds of compression.
     * @return a [SipHasherStream] instance to update and digest.
     */

    /**
     * Initializes a streaming hash, seeded with the given key.
     *
     *
     * This will used the default values for C and D rounds.
     *
     * @param key the key to seed the hash with.
     * @return a [SipHasherStream] instance to update and digest.
     */
    @JvmOverloads
    fun init(key: ByteArray, c: Int = DEFAULT_C, d: Int = DEFAULT_D): SipHasherStream = SipHasherStream(key, c, d)

    /**
     * Converts a hash to a hexidecimal representation.
     *
     * @param hash the finalized hash value to convert to hex.
     * @return a [String] representation of the hash.
     */
    fun toHexString(hash: Long): String {
        val hex = hash.toHexString()

        if (hex.length == 16) {
            return hex
        }

        val sb = StringBuilder()
        var i = 0
        val j = 16 - hex.length
        while (i < j) {
            sb.append('0')
            i++
        }

        return sb.append(hex).toString()
    }

    /**
     * Converts a chunk of 8 bytes to a number in little endian.
     *
     *
     * Accepts an offset to determine where the chunk begins.
     *
     * @param bytes  the byte array containing our bytes to convert.
     * @param offset the index to start at when chunking bytes.
     * @return a long representation, in little endian.
     */
    fun bytesToLong(bytes: ByteArray, offset: Int): Long {
        var m: Long = 0
        for (i in 0..7) {
            m = m or (((bytes[i + offset].toLong()) and 0xffL) shl (8 * i))
        }
        return m
    }

    /**
     * Internal 0A hashing implementation.
     *
     *
     * Requires initial state being manually provided (to avoid allocation). The
     * compression rounds must also be provided, as nothing will be validated in
     * this layer (such as defaults).
     *
     * @param c    the rounds of C compression to apply.
     * @param d    the rounds of D compression to apply.
     * @param v0   the seeded initial value of v0.
     * @param v1   the seeded initial value of v1.
     * @param v2   the seeded initial value of v2.
     * @param v3   the seeded initial value of v3.
     * @param data the input data to hash using the SipHash algorithm.
     * @return a long value as the output of the hash.
     */
    @JvmStatic
    fun hash(c: Int, d: Int, v0: Long, v1: Long, v2: Long, v3: Long, data: ByteArray): Long {
        var v0 = v0
        var v1 = v1
        var v2 = v2
        var v3 = v3
        var m: Long
        val last = data.size / 8 * 8
        var i = 0
        var r: Int

        while (i < last) {
            m = data[i++].toLong() and 0xffL
            r = 1
            while (r < 8) {
                m = m or ((data[i++].toLong() and 0xffL) shl (r * 8))
                r++
            }

            v3 = v3 xor m
            r = 0
            while (r < c) {
                v0 += v1
                v2 += v3
                v1 = rotateLeft(v1, 13)
                v3 = rotateLeft(v3, 16)

                v1 = v1 xor v0
                v3 = v3 xor v2
                v0 = rotateLeft(v0, 32)

                v2 += v1
                v0 += v3
                v1 = rotateLeft(v1, 17)
                v3 = rotateLeft(v3, 21)

                v1 = v1 xor v2
                v3 = v3 xor v0
                v2 = rotateLeft(v2, 32)
                r++
            }
            v0 = v0 xor m
        }

        m = 0
        i = data.size - 1
        while (i >= last) {
            m = m shl 8
            m = m or (data[i].toLong() and 0xffL)
            --i
        }
        m = m or (data.size.toLong() shl 56)

        v3 = v3 xor m
        r = 0
        while (r < c) {
            v0 += v1
            v2 += v3
            v1 = rotateLeft(v1, 13)
            v3 = rotateLeft(v3, 16)

            v1 = v1 xor v0
            v3 = v3 xor v2
            v0 = rotateLeft(v0, 32)

            v2 += v1
            v0 += v3
            v1 = rotateLeft(v1, 17)
            v3 = rotateLeft(v3, 21)

            v1 = v1 xor v2
            v3 = v3 xor v0
            v2 = rotateLeft(v2, 32)
            r++
        }
        v0 = v0 xor m

        v2 = v2 xor 0xffL
        r = 0
        while (r < d) {
            v0 += v1
            v2 += v3
            v1 = rotateLeft(v1, 13)
            v3 = rotateLeft(v3, 16)

            v1 = v1 xor v0
            v3 = v3 xor v2
            v0 = rotateLeft(v0, 32)

            v2 += v1
            v0 += v3
            v1 = rotateLeft(v1, 17)
            v3 = rotateLeft(v3, 21)

            v1 = v1 xor v2
            v3 = v3 xor v0
            v2 = rotateLeft(v2, 32)
            r++
        }

        return v0 xor v1 xor v2 xor v3
    }

    /**
     * Rotates an input number `val` left by `shift` number of bits.
     *
     *
     * Bits which are pushed off to the left are rotated back onto the right,
     * making this a left rotation (a circular shift).
     *
     *
     * This is very close to [Long.rotateLeft] aside from
     * the use of the 64 bit masking.
     *
     * @param value the value to be shifted.
     * @param shift how far left to shift.
     * @return a long value after being shifted.
     */
    fun rotateLeft(value: Long, shift: Int): Long = (value shl shift) or (value ushr (64 - shift))
}
