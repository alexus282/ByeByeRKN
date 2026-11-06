package com.dostup.app.awg

import com.dostup.app.data.Candidate
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.util.zip.CRC32

class MasksTest {
    @Test
    fun stunBindingRequestIsWellFormed() {
        val packet = Masks.stunBindingRequest()
        val buf = ByteBuffer.wrap(packet)
        assertEquals(0x0001, buf.getShort(0).toInt())
        assertEquals(packet.size - 20, buf.getShort(2).toInt())
        assertEquals(0x2112A442, buf.getInt(4))

        // Walk attributes; FINGERPRINT must be last and match the CRC of everything before it.
        var offset = 20
        var lastType = -1
        while (offset < packet.size) {
            lastType = buf.getShort(offset).toInt() and 0xffff
            val length = buf.getShort(offset + 2).toInt()
            if (lastType == 0x8028) {
                val crc = CRC32().apply { update(packet, 0, offset) }.value
                assertEquals((crc xor 0x5354554EL).toInt(), buf.getInt(offset + 4))
            }
            offset += 4 + length + (4 - length % 4) % 4
        }
        assertEquals(packet.size, offset)
        assertEquals(0x8028, lastType)
    }

    @Test
    fun dnsQueryHasOneQuestionAndEdns() {
        val packet = Masks.dnsQuery("vk.com")
        val buf = ByteBuffer.wrap(packet)
        assertEquals(1, buf.getShort(4).toInt()) // QDCOUNT
        assertEquals(1, buf.getShort(10).toInt()) // ARCOUNT (OPT)
        assertArrayEquals(byteArrayOf(2, 'v'.code.toByte(), 'k'.code.toByte(), 3, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(), 0), packet.copyOfRange(12, 20))
        assertEquals(12 + 8 + 4 + 11, packet.size)
    }

    @Test
    fun sipOptionsIsCompleteRequest() {
        val text = Masks.sipOptions()
        assertTrue(text.startsWith("OPTIONS sip:"))
        assertTrue(text.contains("\r\nCSeq: "))
        assertTrue(text.endsWith("Content-Length: 0\r\n\r\n"))
    }

    @Test
    fun junkSizesStayInPresetRangesAndUnderLimit() {
        repeat(200) {
            for (preset in Obfuscation.entries) {
                val p = preset.params("ya.ru")
                assertTrue(p.jc in preset.junkCount)
                if (p.jc > 0) {
                    assertTrue(p.jmin in preset.junkMin)
                    assertTrue(p.jmax > p.jmin && p.jmax <= Obfuscation.MAX_JUNK)
                }
                assertEquals(preset.firstPacket != FirstPacket.NONE, p.i1 != null)
            }
        }
    }

    @Test
    fun candidateRoundTripsAndReadsOldFormat() {
        val original = Candidate("[2606:4700:d0::a29f:c001]:2408", Obfuscation.STUN.params(), 42, mtu = 1200)
        val restored = Candidate.fromJson(original.toJson())
        assertEquals(original.endpoint, restored.endpoint)
        assertEquals(original.params.jc, restored.params.jc)
        assertEquals(original.params.jmax, restored.params.jmax)
        assertArrayEquals(original.params.i1, restored.params.i1)
        assertEquals(1200, restored.mtu)

        val old = Candidate.fromJson("""{"endpoint":"188.114.99.1:945","obfuscation":"QUIC","i1":"c0ffee","rtt":46}""")
        assertEquals(Candidate.DEFAULT_MTU, old.mtu)
        assertArrayEquals(byteArrayOf(0xc0.toByte(), 0xff.toByte(), 0xee.toByte()), old.params.i1)
        assertTrue(old.params.jc in Obfuscation.QUIC.junkCount)
    }
}
