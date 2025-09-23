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

import no.vegvesen.nvdb.tnits.hash.SipHasher.DEFAULT_C
import no.vegvesen.nvdb.tnits.hash.SipHasher.DEFAULT_D
import no.vegvesen.nvdb.tnits.hash.SipHasher.INITIAL_V0
import no.vegvesen.nvdb.tnits.hash.SipHasher.INITIAL_V1
import no.vegvesen.nvdb.tnits.hash.SipHasher.INITIAL_V2
import no.vegvesen.nvdb.tnits.hash.SipHasher.INITIAL_V3
import no.vegvesen.nvdb.tnits.hash.SipHasher.bytesToLong

/**
 * Small container of state to aid SipHash throughput.
 *
 *
 * This will keep a constant key and seeded v* values for use across many
 * hashes. As such, this avoids a small amount of overhead on each hash which
 * might prove useful in the case you have constant keys (hash tables, etc).
 */
class SipHasherContainer internal constructor(key: ByteArray) {
    /**
     * The seeded value for the magic v0 number.
     */
    private val v0: Long

    /**
     * The seeded value for the magic v1 number.
     */
    private val v1: Long

    /**
     * The seeded value for the magic v2 number.
     */
    private val v2: Long

    /**
     * The seeded value for the magic v3 number.
     */
    private val v3: Long

    /**
     * Initializes a container from a key seed.
     *
     * @param key the key to use to seed this hash container.
     */
    init {
        require(key.size == 16) { "Key must be exactly 16 bytes!" }

        val k0: Long = bytesToLong(key, 0)
        val k1: Long = bytesToLong(key, 8)

        this.v0 = INITIAL_V0 xor k0
        this.v1 = INITIAL_V1 xor k1
        this.v2 = INITIAL_V2 xor k0
        this.v3 = INITIAL_V3 xor k1
    }

    /**
     * Hashes input data using the preconfigured state.
     *
     * @param data the data to hash and digest.
     * @param c    the desired rounds of C compression.
     * @param d    the desired rounds of D compression.
     * @return a long value as the output of the hash.
     */

    /**
     * Hashes input data using the preconfigured state.
     *
     * @param data the data to hash and digest.
     * @return a long value as the output of the hash.
     */
    @JvmOverloads
    fun hash(data: ByteArray, c: Int = DEFAULT_C, d: Int = DEFAULT_D): Long = SipHasher.hash(
        c,
        d,
        this.v0,
        this.v1,
        this.v2,
        this.v3,
        data,
    )
}
