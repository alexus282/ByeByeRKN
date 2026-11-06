package com.dostup.app.crypto

import com.dostup.app.awg.hexToBytes
import com.dostup.app.awg.toHex
import org.bouncycastle.crypto.digests.Blake2sDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Random
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class CryptoTest {
    private val random = Random(42)

    @Test
    fun blake2sKnownVectors() {
        // RFC 7693 appendix B and the reference implementation's test vectors.
        assertEquals(
            "508c5e8c327c14e2e1a72ba34eeb452f37458b209ed63a294d999b4c86675982",
            Blake2s.hash("abc".toByteArray()).toHex(),
        )
        assertEquals(
            "69217a3079908094e11121d042354a7c1f55b6482ca1a51e1b250dfd1ed0eef9",
            Blake2s.hash(ByteArray(0)).toHex(),
        )
    }

    @Test
    fun blake2sMatchesBouncyCastle() {
        for (length in listOf(0, 1, 63, 64, 65, 116, 128, 129, 300)) {
            for (keyLength in listOf(0, 16, 32)) {
                for (outLength in listOf(16, 32)) {
                    val data = ByteArray(length).also(random::nextBytes)
                    val key = ByteArray(keyLength).also(random::nextBytes)
                    val digest = Blake2sDigest(if (keyLength == 0) null else key, outLength, null, null)
                    digest.update(data, 0, data.size)
                    val expected = ByteArray(outLength).also { digest.doFinal(it, 0) }
                    assertArrayEquals("len=$length key=$keyLength out=$outLength", expected, Blake2s.hash(data, outLength, key))
                }
            }
        }
    }

    @Test
    fun poly1305Rfc8439Vector() {
        val key = "85d6be7857556d337f4452fe42d506a80103808afb0db2fd4abff6af4149f51b".hexToBytes()
        val tag = ChaChaPoly.poly1305(key, "Cryptographic Forum Research Group".toByteArray())
        assertEquals("a8061dc1305136c6c22b8baf0c0127a9", tag.toHex())
    }

    @Test
    fun chaChaPolyMatchesJdk() {
        for (length in listOf(0, 1, 15, 16, 32, 33, 64, 65, 200)) {
            val key = ByteArray(32).also(random::nextBytes)
            val nonce = ByteArray(12).also(random::nextBytes)
            val aad = ByteArray(random.nextInt(40)).also(random::nextBytes)
            val plaintext = ByteArray(length).also(random::nextBytes)

            val jdk = Cipher.getInstance("ChaCha20-Poly1305")
            jdk.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "ChaCha20"), IvParameterSpec(nonce))
            jdk.updateAAD(aad)
            val expected = jdk.doFinal(plaintext)

            assertArrayEquals("len=$length", expected, ChaChaPoly.seal(key, nonce, plaintext, aad))
        }
    }
}
