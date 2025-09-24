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
package no.vegvesen.nvdb.tnits.hash

import no.vegvesen.nvdb.tnits.hash.SipHasher.INITIAL_V0
import no.vegvesen.nvdb.tnits.hash.SipHasher.INITIAL_V1
import no.vegvesen.nvdb.tnits.hash.SipHasher.INITIAL_V2
import no.vegvesen.nvdb.tnits.hash.SipHasher.INITIAL_V3
import no.vegvesen.nvdb.tnits.hash.SipHasher.bytesToLong
import no.vegvesen.nvdb.tnits.hash.SipHasher.rotateLeft

/**
 * Streaming implementation of the SipHash algorithm.
 *
 *
 * This implementation is slower than the 0A implementation, but allows for
 * unknown input this.lengths to enable hashing as more data is received. Chunks
 * are processed in 8-byte blocks in the SipHash algorithms, so if you're
 * using huge input, you should expect slowness.
 *
 *
 * Although this implementation requires an initial allocation, there are
 * no further allocations - so memory should prove similar to the non-streaming
 * implementation.
 */
class SipHasherStream internal constructor(key: ByteArray, c: Int, d: Int) {
    /**
     * The specified rounds of C compression.
     */
    private val c: Int

    /**
     * The specified rounds of D compression.
     */
    private val d: Int

    /**
     * Counter to keep track of the input
     */
    private var len: Byte

    /**
     * Index to keep track of chunk positioning.
     */
    private var m_idx: Int

    /**
     * The current value for the m number.
     */
    private var m: Long

    /**
     * The current value for the this.v0 number.
     */
    private var v0: Long

    /**
     * The current value for the this.v1 number.
     */
    private var v1: Long

    /**
     * The current value for the this.v2 number.
     */
    private var v2: Long

    /**
     * The current value for the this.v3 number.
     */
    private var v3: Long

    /**
     * Initializes a streaming digest using a key and compression rounds.
     *
     * @param key the key to use to seed this hash container.
     * @param c   the desired rounds of C compression.
     * @param d   the desired rounds of D compression.
     */
    init {
        require(key.size == 16) { "Key must be exactly 16 bytes!" }

        val k0: Long = bytesToLong(key, 0)
        val k1: Long = bytesToLong(key, 8)

        this.v0 = INITIAL_V0 xor k0
        this.v1 = INITIAL_V1 xor k1
        this.v2 = INITIAL_V2 xor k0
        this.v3 = INITIAL_V3 xor k1

        this.c = c
        this.d = d

        this.m = 0
        this.len = 0
        this.m_idx = 0
    }

    /**
     * Updates the hash with a single byte.
     *
     *
     * This will only modify the internal `m` value, nothing will be modified
     * in the actual `v*` states until an 8-byte block has been provided.
     *
     * @param b the byte being added to the digest.
     * @return the same [SipHasherStream] for chaining.
     */
    fun update(b: Byte): SipHasherStream {
        this.len++
        this.m = this.m or ((b.toLong() and 0xffL) shl (this.m_idx++ * 8))
        if (this.m_idx < 8) {
            return this
        }
        this.v3 = this.v3 xor this.m
        for (i in 0..<this.c) {
            round()
        }
        this.v0 = this.v0 xor this.m
        this.m_idx = 0
        this.m = 0
        return this
    }

    /**
     * Updates the hash with an array of bytes.
     *
     * @param bytes the bytes being added to the digest.
     * @return the same [SipHasherStream] for chaining.
     */
    fun update(bytes: ByteArray): SipHasherStream {
        for (b in bytes) {
            update(b)
        }
        return this
    }

    /**
     * Finalizes the digest and returns the hash.
     *
     *
     * This works by padding to the next 8-byte block, before applying
     * the compression rounds once more - but this time using D rounds
     * of compression rather than C.
     *
     * @return the final result of the hash as a long.
     */
    fun digest(): Long {
        val msgLenMod256 = this.len

        while (this.m_idx < 7) {
            update(0.toByte())
        }
        update(msgLenMod256)

        this.v2 = this.v2 xor 0xffL
        for (i in 0..<this.d) {
            round()
        }

        return this.v0 xor this.v1 xor this.v2 xor this.v3
    }

    /**
     * SipRound implementation for internal use.
     */
    private fun round() {
        this.v0 += this.v1
        this.v2 += this.v3
        this.v1 = rotateLeft(this.v1, 13)
        this.v3 = rotateLeft(this.v3, 16)

        this.v1 = this.v1 xor this.v0
        this.v3 = this.v3 xor this.v2
        this.v0 = rotateLeft(this.v0, 32)

        this.v2 += this.v1
        this.v0 += this.v3
        this.v1 = rotateLeft(this.v1, 17)
        this.v3 = rotateLeft(this.v3, 21)

        this.v1 = this.v1 xor this.v2
        this.v3 = this.v3 xor this.v0
        this.v2 = rotateLeft(this.v2, 32)
    }
}
