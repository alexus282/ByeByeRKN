package com.dostup.app.warp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import javax.net.ssl.SSLContext

class SplitHelloTest {
    private val host = "api.cloudflareclient.com"

    /** A real ClientHello with SNI, produced by the JDK's TLS stack. */
    private fun clientHello(): ByteArray {
        val engine = SSLContext.getDefault().createSSLEngine(host, 443).apply { useClientMode = true }
        val out = ByteBuffer.allocate(engine.session.packetBufferSize)
        engine.beginHandshake()
        engine.wrap(ByteBuffer.allocate(0), out)
        out.flip()
        return ByteArray(out.remaining()).also { out.get(it) }
    }

    @Test
    fun cutsInsideServerNameAndKeepsPayload() {
        val hello = clientHello()
        val (first, second) = SplitHelloSocketFactory.splitClientHello(hello)!!

        for (record in listOf(first, second)) {
            assertEquals(0x16, record[0].toInt())
            assertEquals(record.size - 5, ((record[3].toInt() and 0xff) shl 8) or (record[4].toInt() and 0xff))
        }
        val text1 = String(first, Charsets.ISO_8859_1)
        val text2 = String(second, Charsets.ISO_8859_1)
        assertFalse(text1.contains(host))
        assertFalse(text2.contains(host))
        assertTrue(text1.endsWith("ap"))
        assertTrue(text2.startsWith("i.cloudflareclient.com", 5))

        assertArrayEquals(hello.copyOfRange(5, hello.size), first.copyOfRange(5, first.size) + second.copyOfRange(5, second.size))
    }

    @Test
    fun ignoresNonTlsData() {
        assertNull(SplitHelloSocketFactory.splitClientHello("GET / HTTP/1.1\r\n\r\n".toByteArray()))
        assertNull(SplitHelloSocketFactory.splitClientHello(clientHello().copyOf(40)))
    }
}
