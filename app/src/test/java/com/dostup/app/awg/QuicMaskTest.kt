package com.dostup.app.awg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class QuicMaskTest {
    @Test
    fun initialKeysMatchRfc9001AppendixA() {
        val keys = QuicMask.clientInitialKeys("8394c8f03e515708".hexToBytes())
        assertEquals("1f369613dd76d5467730efcbe3b1a22d", keys.key.toHex())
        assertEquals("fa044b2f42a3fd3b46fb255c", keys.iv.toHex())
        assertEquals("9f50449e04a0e810283a1e9933adedd2", keys.hp.toHex())
    }

    /** Decrypts our packet the way a receiver (or DPI) would and checks the ClientHello. */
    @Test
    fun packetDecryptsToClientHelloWithSni() {
        val packet = QuicMask.build("vk.com")
        assertEquals(1200, packet.size)
        assertEquals(0xC0, packet[0].toInt() and 0xF0)
        assertEquals("00000001", packet.copyOfRange(1, 5).toHex())
        val dcidLength = packet[5].toInt()
        val dcid = packet.copyOfRange(6, 6 + dcidLength)
        var offset = 6 + dcidLength
        assertEquals(0, packet[offset++].toInt()) // scid length
        assertEquals(0, packet[offset++].toInt()) // token length
        val length = ((packet[offset].toInt() and 0x3f) shl 8) or (packet[offset + 1].toInt() and 0xff)
        offset += 2
        val pnOffset = offset
        assertEquals(packet.size - pnOffset, length)

        val keys = QuicMask.clientInitialKeys(dcid)
        val mask = QuicMask.headerProtectionMask(keys.hp, packet.copyOfRange(pnOffset + 4, pnOffset + 20))
        val header = packet.copyOfRange(0, pnOffset + 4)
        header[0] = (header[0].toInt() xor (mask[0].toInt() and 0x0f)).toByte()
        val pnLength = (header[0].toInt() and 0x03) + 1
        assertEquals(4, pnLength)
        for (i in 0 until pnLength) header[pnOffset + i] = (header[pnOffset + i].toInt() xor mask[1 + i].toInt()).toByte()

        val nonce = keys.iv.copyOf()
        for (i in 0 until pnLength) nonce[8 + i] = (nonce[8 + i].toInt() xor header[pnOffset + i].toInt()).toByte()
        val gcm = Cipher.getInstance("AES/GCM/NoPadding")
        gcm.init(Cipher.DECRYPT_MODE, SecretKeySpec(keys.key, "AES"), GCMParameterSpec(128, nonce))
        gcm.updateAAD(header)
        val payload = gcm.doFinal(packet.copyOfRange(pnOffset + 4, packet.size))

        assertEquals(0x06, payload[0].toInt()) // CRYPTO frame
        assertEquals(0, payload[1].toInt()) // offset 0
        assertEquals(0x40, payload[2].toInt() and 0xC0) // two-byte varint length
        assertEquals(0x01, payload[4].toInt()) // TLS ClientHello
        assertTrue(String(payload, Charsets.ISO_8859_1).contains("vk.com"))
    }

    @Test
    fun configLineIsHexTag() {
        val line = Obfuscation.QUIC.params("ya.ru").configLines().single { it.startsWith("I1") }
        assertTrue(line.matches(Regex("I1 = <b 0x[0-9a-f]{2400}>")))
    }
}
