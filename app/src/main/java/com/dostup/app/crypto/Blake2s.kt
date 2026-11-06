package com.dostup.app.crypto

/** BLAKE2s (RFC 7693), plus the HMAC/HKDF construction WireGuard builds on top of it. */
object Blake2s {
    private val IV = intArrayOf(
        0x6A09E667, 0xBB67AE85.toInt(), 0x3C6EF372, 0xA54FF53A.toInt(),
        0x510E527F, 0x9B05688C.toInt(), 0x1F83D9AB, 0x5BE0CD19,
    )

    private val SIGMA = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15),
        intArrayOf(14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3),
        intArrayOf(11, 8, 12, 0, 5, 2, 15, 13, 10, 14, 3, 6, 7, 1, 9, 4),
        intArrayOf(7, 9, 3, 1, 13, 12, 11, 14, 2, 6, 5, 10, 4, 0, 15, 8),
        intArrayOf(9, 0, 5, 7, 2, 4, 10, 15, 14, 1, 11, 12, 6, 8, 3, 13),
        intArrayOf(2, 12, 6, 10, 0, 11, 8, 3, 4, 13, 7, 5, 15, 14, 1, 9),
        intArrayOf(12, 5, 1, 15, 14, 13, 4, 10, 0, 7, 6, 3, 9, 2, 8, 11),
        intArrayOf(13, 11, 7, 14, 12, 1, 3, 9, 5, 0, 15, 4, 8, 6, 2, 10),
        intArrayOf(6, 15, 14, 9, 11, 3, 0, 8, 12, 2, 13, 7, 1, 4, 10, 5),
        intArrayOf(10, 2, 8, 4, 7, 6, 1, 5, 15, 11, 9, 14, 3, 12, 13, 0),
    )

    private const val BLOCK = 64

    fun hash(data: ByteArray, outLen: Int = 32, key: ByteArray = ByteArray(0)): ByteArray {
        require(outLen in 1..32) { "outLen must be 1..32" }
        require(key.size <= 32) { "key must be at most 32 bytes" }

        val h = IV.copyOf()
        h[0] = h[0] xor (0x01010000 or (key.size shl 8) or outLen)

        val input = if (key.isEmpty()) data else key.copyOf(BLOCK) + data
        val v = IntArray(16)
        val m = IntArray(16)
        var counter = 0L
        var offset = 0
        while (input.size - offset > BLOCK) {
            counter += BLOCK
            compress(h, input, offset, counter, false, v, m)
            offset += BLOCK
        }
        val last = ByteArray(BLOCK)
        val remaining = input.size - offset
        System.arraycopy(input, offset, last, 0, remaining)
        counter += remaining
        compress(h, last, 0, counter, true, v, m)

        val out = ByteArray(32)
        for (i in 0 until 8) {
            out[i * 4] = h[i].toByte()
            out[i * 4 + 1] = (h[i] ushr 8).toByte()
            out[i * 4 + 2] = (h[i] ushr 16).toByte()
            out[i * 4 + 3] = (h[i] ushr 24).toByte()
        }
        return out.copyOf(outLen)
    }

    fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val k = (if (key.size > BLOCK) hash(key) else key).copyOf(BLOCK)
        val inner = ByteArray(BLOCK) { (k[it].toInt() xor 0x36).toByte() }
        val outer = ByteArray(BLOCK) { (k[it].toInt() xor 0x5c).toByte() }
        return hash(outer + hash(inner + data))
    }

    /** WireGuard's KDF_n: HKDF over HMAC-BLAKE2s, returning [n] 32-byte outputs. */
    fun kdf(chainingKey: ByteArray, input: ByteArray, n: Int): List<ByteArray> {
        val prk = hmac(chainingKey, input)
        val outputs = ArrayList<ByteArray>(n)
        var previous = ByteArray(0)
        for (i in 1..n) {
            previous = hmac(prk, previous + byteArrayOf(i.toByte()))
            outputs += previous
        }
        return outputs
    }

    private fun compress(h: IntArray, block: ByteArray, off: Int, counter: Long, last: Boolean, v: IntArray, m: IntArray) {
        for (i in 0 until 16) {
            val p = off + i * 4
            m[i] = (block[p].toInt() and 0xff) or
                ((block[p + 1].toInt() and 0xff) shl 8) or
                ((block[p + 2].toInt() and 0xff) shl 16) or
                ((block[p + 3].toInt() and 0xff) shl 24)
        }
        for (i in 0 until 8) {
            v[i] = h[i]
            v[i + 8] = IV[i]
        }
        v[12] = v[12] xor counter.toInt()
        v[13] = v[13] xor (counter ushr 32).toInt()
        if (last) v[14] = v[14].inv()

        for (round in 0 until 10) {
            val s = SIGMA[round]
            mix(v, 0, 4, 8, 12, m[s[0]], m[s[1]])
            mix(v, 1, 5, 9, 13, m[s[2]], m[s[3]])
            mix(v, 2, 6, 10, 14, m[s[4]], m[s[5]])
            mix(v, 3, 7, 11, 15, m[s[6]], m[s[7]])
            mix(v, 0, 5, 10, 15, m[s[8]], m[s[9]])
            mix(v, 1, 6, 11, 12, m[s[10]], m[s[11]])
            mix(v, 2, 7, 8, 13, m[s[12]], m[s[13]])
            mix(v, 3, 4, 9, 14, m[s[14]], m[s[15]])
        }
        for (i in 0 until 8) h[i] = h[i] xor v[i] xor v[i + 8]
    }

    private fun mix(v: IntArray, a: Int, b: Int, c: Int, d: Int, x: Int, y: Int) {
        v[a] += v[b] + x
        v[d] = (v[d] xor v[a]).rotateRight(16)
        v[c] += v[d]
        v[b] = (v[b] xor v[c]).rotateRight(12)
        v[a] += v[b] + y
        v[d] = (v[d] xor v[a]).rotateRight(8)
        v[c] += v[d]
        v[b] = (v[b] xor v[c]).rotateRight(7)
    }
}
