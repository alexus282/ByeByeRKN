package com.dostup.app.probe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TelegramProbeTest {
    @Test
    fun reqPqPacketFollowsAbridgedFraming() {
        val packet = TelegramProbe.reqPqPacket(1_800_000_000_123L)
        assertEquals(0xEF, packet[0].toInt() and 0xFF)
        assertEquals(10, packet[1].toInt()) // 40 bytes = 10 words
        assertEquals(42, packet.size)

        val body = ByteBuffer.wrap(packet, 2, 40).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0L, body.getLong())
        val messageId = body.getLong()
        assertEquals(0L, messageId and 3)
        assertEquals(1_800_000_000L, messageId ushr 32)
        assertEquals(20, body.getInt())
        assertEquals(0xBE7E8EF1.toInt(), body.getInt())
    }

    @Test
    fun recognisesResPqOnly() {
        fun payload(constructor: Int) =
            ByteBuffer.allocate(84).order(ByteOrder.LITTLE_ENDIAN).putLong(0).putLong(1).putInt(64).putInt(constructor).array()
        assertTrue(TelegramProbe.isResPq(payload(0x05162463)))
        assertFalse(TelegramProbe.isResPq(payload(0x12345678)))
        assertFalse(TelegramProbe.isResPq(ByteArray(4))) // transport error code
    }

    /** Runs only with DOSTUP_LIVE=1: whether it passes depends on this machine's network. */
    @Test
    fun liveDataCentresAnswer() {
        assumeTrue(System.getenv("DOSTUP_LIVE") == "1")
        for (dc in TelegramProbe.dcs) {
            val result = TelegramProbe.check(dc.v4)
            println("DC${dc.id} ${dc.v4}: connect=${result.connectMs} reply=${result.replyMs} error=${result.error}")
        }
    }
}
