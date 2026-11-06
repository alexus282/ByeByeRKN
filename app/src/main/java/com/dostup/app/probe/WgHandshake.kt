package com.dostup.app.probe

import com.dostup.app.crypto.Blake2s
import com.dostup.app.crypto.ChaChaPoly
import org.amnezia.awg.crypto.Curve25519
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicLong

/**
 * Builds a WireGuard handshake initiation (Noise_IKpsk2, whitepaper section 5.4.2).
 * Sending one and getting a handshake response back proves the endpoint is reachable
 * through the local DPI without bringing the VPN up.
 */
object WgHandshake {
    const val INITIATION_SIZE = 148
    const val RESPONSE_SIZE = 92
    const val COOKIE_REPLY_SIZE = 64

    private val CONSTRUCTION = "Noise_IKpsk2_25519_ChaChaPoly_BLAKE2s".toByteArray()
    private val IDENTIFIER = "WireGuard v1 zx2c4 Jason@zx2c4.com".toByteArray()
    private val LABEL_MAC1 = "mac1----".toByteArray()
    private val random = SecureRandom()
    private val lastTimestamp = AtomicLong()

    fun initiation(
        staticPrivate: ByteArray,
        staticPublic: ByteArray,
        peerPublic: ByteArray,
        senderIndex: Int,
        ephemeralPrivate: ByteArray = newPrivateKey(),
        timestamp: ByteArray = tai64n(),
    ): ByteArray {
        var chainingKey = Blake2s.hash(CONSTRUCTION)
        var hash = Blake2s.hash(chainingKey + IDENTIFIER)
        hash = Blake2s.hash(hash + peerPublic)

        val ephemeralPublic = dh(ephemeralPrivate, null)
        chainingKey = Blake2s.kdf(chainingKey, ephemeralPublic, 1)[0]
        hash = Blake2s.hash(hash + ephemeralPublic)

        val (ck1, staticKey) = Blake2s.kdf(chainingKey, dh(ephemeralPrivate, peerPublic), 2)
        val encryptedStatic = ChaChaPoly.seal(staticKey, 0, staticPublic, hash)
        hash = Blake2s.hash(hash + encryptedStatic)

        val timestampKey = Blake2s.kdf(ck1, dh(staticPrivate, peerPublic), 2)[1]
        val encryptedTimestamp = ChaChaPoly.seal(timestampKey, 0, timestamp, hash)

        val msg = ByteBuffer.allocate(INITIATION_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        msg.putInt(1) // type 1 followed by three reserved zero bytes
        msg.putInt(senderIndex)
        msg.put(ephemeralPublic)
        msg.put(encryptedStatic)
        msg.put(encryptedTimestamp)
        val bytes = msg.array()
        val mac1Key = Blake2s.hash(LABEL_MAC1 + peerPublic)
        val mac1 = Blake2s.hash(bytes.copyOfRange(0, 116), outLen = 16, key = mac1Key)
        System.arraycopy(mac1, 0, bytes, 116, 16)
        // mac2 stays zero: it is only required once the server hands out a cookie.
        return bytes
    }

    /** True when [packet] is a handshake response or cookie reply addressed to [senderIndex]. */
    fun isReplyTo(packet: ByteArray, length: Int, senderIndex: Int): Boolean {
        val le = ByteBuffer.wrap(packet, 0, length).order(ByteOrder.LITTLE_ENDIAN)
        return when {
            length == RESPONSE_SIZE && packet[0].toInt() == 2 -> le.getInt(8) == senderIndex
            length == COOKIE_REPLY_SIZE && packet[0].toInt() == 3 -> le.getInt(4) == senderIndex
            else -> false
        }
    }

    fun newPrivateKey(): ByteArray = ByteArray(32).also { random.nextBytes(it); clamp(it) }

    fun dh(privateKey: ByteArray, publicKey: ByteArray?): ByteArray {
        val clamped = privateKey.copyOf().also(::clamp)
        return ByteArray(32).also { Curve25519.eval(it, 0, clamped, publicKey) }
    }

    /** TAI64N, kept strictly increasing because the server rejects replayed timestamps. */
    fun tai64n(): ByteArray {
        val nowNanos = System.currentTimeMillis() * 1_000_000L
        val stamp = lastTimestamp.updateAndGet { maxOf(it + 1, nowNanos) }
        return ByteBuffer.allocate(12)
            .putLong(0x400000000000000aL + stamp / 1_000_000_000L)
            .putInt((stamp % 1_000_000_000L).toInt())
            .array()
    }

    private fun clamp(key: ByteArray) {
        key[0] = (key[0].toInt() and 248).toByte()
        key[31] = ((key[31].toInt() and 127) or 64).toByte()
    }
}
