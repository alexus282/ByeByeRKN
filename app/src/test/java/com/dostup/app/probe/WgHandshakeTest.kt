package com.dostup.app.probe

import org.bouncycastle.crypto.digests.Blake2sDigest
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.math.ec.rfc7748.X25519
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Plays the responder side of the WireGuard handshake with independent primitives
 * (BouncyCastle BLAKE2s/X25519, JDK ChaCha20-Poly1305) and checks it can open our initiation.
 */
class WgHandshakeTest {
    private val rng = SecureRandom()

    private fun blake(data: ByteArray, out: Int = 32, key: ByteArray? = null): ByteArray {
        val d = Blake2sDigest(key, out, null, null)
        d.update(data, 0, data.size)
        return ByteArray(out).also { d.doFinal(it, 0) }
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = HMac(Blake2sDigest(256))
        mac.init(KeyParameter(key))
        mac.update(data, 0, data.size)
        return ByteArray(32).also { mac.doFinal(it, 0) }
    }

    private fun kdf2(ck: ByteArray, input: ByteArray): Pair<ByteArray, ByteArray> {
        val prk = hmac(ck, input)
        val t1 = hmac(prk, byteArrayOf(1))
        val t2 = hmac(prk, t1 + byteArrayOf(2))
        return t1 to t2
    }

    private fun open(key: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray {
        val c = Cipher.getInstance("ChaCha20-Poly1305")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "ChaCha20"), IvParameterSpec(ByteArray(12)))
        c.updateAAD(aad)
        return c.doFinal(ciphertext)
    }

    private fun x25519(priv: ByteArray, pub: ByteArray) = ByteArray(32).also { X25519.scalarMult(priv, 0, pub, 0, it, 0) }

    @Test
    fun responderCanOpenInitiation() {
        val serverPriv = ByteArray(32).also { X25519.generatePrivateKey(rng, it) }
        val serverPub = ByteArray(32).also { X25519.generatePublicKey(serverPriv, 0, it, 0) }
        val clientPriv = WgHandshake.newPrivateKey()
        val clientPub = WgHandshake.dh(clientPriv, null)
        assertArrayEquals(ByteArray(32).also { X25519.generatePublicKey(clientPriv, 0, it, 0) }, clientPub)

        val timestamp = WgHandshake.tai64n()
        val msg = WgHandshake.initiation(clientPriv, clientPub, serverPub, senderIndex = 0x01020304, timestamp = timestamp)
        assertEquals(WgHandshake.INITIATION_SIZE, msg.size)
        assertEquals(1, msg[0].toInt())
        assertEquals(0x01020304, ByteBuffer.wrap(msg, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int)

        val ephemeral = msg.copyOfRange(8, 40)
        val encStatic = msg.copyOfRange(40, 88)
        val encTimestamp = msg.copyOfRange(88, 116)

        var ck = blake("Noise_IKpsk2_25519_ChaChaPoly_BLAKE2s".toByteArray())
        var h = blake(ck + "WireGuard v1 zx2c4 Jason@zx2c4.com".toByteArray())
        h = blake(h + serverPub)
        ck = hmac(hmac(ck, ephemeral), byteArrayOf(1))
        h = blake(h + ephemeral)
        val (ck1, k1) = kdf2(ck, x25519(serverPriv, ephemeral))
        assertArrayEquals(clientPub, open(k1, encStatic, h))
        h = blake(h + encStatic)
        val (_, k2) = kdf2(ck1, x25519(serverPriv, clientPub))
        assertArrayEquals(timestamp, open(k2, encTimestamp, h))

        val mac1 = blake(msg.copyOfRange(0, 116), 16, blake("mac1----".toByteArray() + serverPub))
        assertArrayEquals(mac1, msg.copyOfRange(116, 132))
        assertArrayEquals(ByteArray(16), msg.copyOfRange(132, 148))
    }

    @Test
    fun timestampsStrictlyIncrease() {
        var previous = WgHandshake.tai64n()
        repeat(1000) {
            val next = WgHandshake.tai64n()
            assertTrue(java.util.Arrays.compareUnsigned(next, previous) > 0)
            previous = next
        }
    }
}
