package com.dostup.app.crypto

import java.math.BigInteger

/**
 * ChaCha20-Poly1305 AEAD (RFC 8439). Implemented here rather than taken from JCA because
 * Android only ships it from API 28, and the app supports API 26.
 * Only used for a handful of handshake probes, so clarity beats speed.
 */
object ChaChaPoly {
    private val P = BigInteger.ONE.shiftLeft(130).subtract(BigInteger.valueOf(5))
    private val MASK_128 = BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE)

    /** WireGuard nonce layout: 4 zero bytes followed by a little-endian 64-bit counter. */
    fun seal(key: ByteArray, counter: Long, plaintext: ByteArray, aad: ByteArray): ByteArray {
        val nonce = ByteArray(12)
        for (i in 0 until 8) nonce[4 + i] = (counter ushr (8 * i)).toByte()
        return seal(key, nonce, plaintext, aad)
    }

    fun seal(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        require(key.size == 32 && nonce.size == 12)
        val polyKey = block(key, 0, nonce).copyOf(32)
        val ciphertext = xorStream(key, 1, nonce, plaintext)
        return ciphertext + poly1305(polyKey, macInput(aad, ciphertext))
    }

    fun block(key: ByteArray, counter: Int, nonce: ByteArray): ByteArray {
        val state = IntArray(16)
        state[0] = 0x61707865
        state[1] = 0x3320646e
        state[2] = 0x79622d32
        state[3] = 0x6b206574
        for (i in 0 until 8) state[4 + i] = le32(key, i * 4)
        state[12] = counter
        for (i in 0 until 3) state[13 + i] = le32(nonce, i * 4)

        val x = state.copyOf()
        repeat(10) {
            quarter(x, 0, 4, 8, 12); quarter(x, 1, 5, 9, 13); quarter(x, 2, 6, 10, 14); quarter(x, 3, 7, 11, 15)
            quarter(x, 0, 5, 10, 15); quarter(x, 1, 6, 11, 12); quarter(x, 2, 7, 8, 13); quarter(x, 3, 4, 9, 14)
        }
        val out = ByteArray(64)
        for (i in 0 until 16) {
            val w = x[i] + state[i]
            out[i * 4] = w.toByte()
            out[i * 4 + 1] = (w ushr 8).toByte()
            out[i * 4 + 2] = (w ushr 16).toByte()
            out[i * 4 + 3] = (w ushr 24).toByte()
        }
        return out
    }

    fun poly1305(key: ByteArray, message: ByteArray): ByteArray {
        val rBytes = key.copyOfRange(0, 16)
        for (i in intArrayOf(3, 7, 11, 15)) rBytes[i] = (rBytes[i].toInt() and 0x0f).toByte()
        for (i in intArrayOf(4, 8, 12)) rBytes[i] = (rBytes[i].toInt() and 0xfc).toByte()
        val r = leNumber(rBytes, 0, 16)
        val s = leNumber(key, 16, 32)

        var acc = BigInteger.ZERO
        var i = 0
        while (i < message.size) {
            val end = minOf(i + 16, message.size)
            val n = leNumber(message, i, end).setBit(8 * (end - i))
            acc = acc.add(n).multiply(r).mod(P)
            i = end
        }
        acc = acc.add(s).and(MASK_128)

        val be = acc.toByteArray()
        val out = ByteArray(16)
        for (j in 0 until 16) {
            val idx = be.size - 1 - j
            out[j] = if (idx >= 0) be[idx] else 0
        }
        return out
    }

    private fun xorStream(key: ByteArray, initialCounter: Int, nonce: ByteArray, data: ByteArray): ByteArray {
        val out = ByteArray(data.size)
        var counter = initialCounter
        var offset = 0
        while (offset < data.size) {
            val ks = block(key, counter++, nonce)
            val n = minOf(64, data.size - offset)
            for (j in 0 until n) out[offset + j] = (data[offset + j].toInt() xor ks[j].toInt()).toByte()
            offset += n
        }
        return out
    }

    private fun macInput(aad: ByteArray, ciphertext: ByteArray): ByteArray {
        fun pad(len: Int) = ByteArray((16 - len % 16) % 16)
        fun le64(v: Long) = ByteArray(8) { (v ushr (8 * it)).toByte() }
        return aad + pad(aad.size) + ciphertext + pad(ciphertext.size) +
            le64(aad.size.toLong()) + le64(ciphertext.size.toLong())
    }

    private fun quarter(x: IntArray, a: Int, b: Int, c: Int, d: Int) {
        x[a] += x[b]; x[d] = (x[d] xor x[a]).rotateLeft(16)
        x[c] += x[d]; x[b] = (x[b] xor x[c]).rotateLeft(12)
        x[a] += x[b]; x[d] = (x[d] xor x[a]).rotateLeft(8)
        x[c] += x[d]; x[b] = (x[b] xor x[c]).rotateLeft(7)
    }

    private fun le32(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xff) or ((b[off + 1].toInt() and 0xff) shl 8) or
            ((b[off + 2].toInt() and 0xff) shl 16) or ((b[off + 3].toInt() and 0xff) shl 24)

    private fun leNumber(b: ByteArray, from: Int, to: Int): BigInteger {
        val be = ByteArray(to - from) { b[to - 1 - it] }
        return BigInteger(1, be)
    }
}
