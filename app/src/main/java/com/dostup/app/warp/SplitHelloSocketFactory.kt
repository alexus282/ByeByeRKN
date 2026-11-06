package com.dostup.app.warp

import android.util.Log
import java.io.FilterOutputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.SocketFactory

/**
 * Plain TCP sockets whose first write, if it is a TLS ClientHello, is cut into two TLS records
 * sent as separate TCP segments, with the cut inside the server name. DPI that reads the SNI
 * from the first segment or record sees only a fragment; the server reassembles it normally.
 * Same idea as ByeDPI's `--tlsrec` + `--split`.
 */
class SplitHelloSocketFactory : SocketFactory() {
    override fun createSocket(): Socket = SplitHelloSocket()

    override fun createSocket(host: String, port: Int): Socket =
        SplitHelloSocket().apply { connect(InetSocketAddress(host, port)) }

    override fun createSocket(host: String, port: Int, localHost: InetAddress?, localPort: Int): Socket =
        SplitHelloSocket().apply {
            bind(InetSocketAddress(localHost, localPort))
            connect(InetSocketAddress(host, port))
        }

    override fun createSocket(host: InetAddress, port: Int): Socket =
        SplitHelloSocket().apply { connect(InetSocketAddress(host, port)) }

    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
        SplitHelloSocket().apply {
            bind(InetSocketAddress(localAddress, localPort))
            connect(InetSocketAddress(address, port))
        }

    companion object {
        /**
         * Returns the two TLS records to send instead of [data]'s first record, or null when
         * [data] does not start with a complete TLS handshake record.
         */
        fun splitClientHello(data: ByteArray): Pair<ByteArray, ByteArray>? {
            if (data.size < 6 || data[0].toInt() != 0x16 || data[1].toInt() != 0x03) return null
            val recordLength = ((data[3].toInt() and 0xff) shl 8) or (data[4].toInt() and 0xff)
            if (data.size < 5 + recordLength || recordLength < 2) return null
            val payload = data.copyOfRange(5, 5 + recordLength)
            val rest = data.copyOfRange(5 + recordLength, data.size)
            val cut = (sniOffset(payload)?.plus(2) ?: (recordLength / 2)).coerceIn(1, recordLength - 1)

            fun record(body: ByteArray) =
                byteArrayOf(0x16, data[1], data[2], (body.size shr 8).toByte(), body.size.toByte()) + body
            return record(payload.copyOfRange(0, cut)) to (record(payload.copyOfRange(cut, payload.size)) + rest)
        }

        /** Offset of the host name inside a ClientHello handshake message, if it has one. */
        private fun sniOffset(hello: ByteArray): Int? {
            fun u8(i: Int) = hello[i].toInt() and 0xff
            fun u16(i: Int) = (u8(i) shl 8) or u8(i + 1)
            return try {
                if (u8(0) != 0x01) return null
                var p = 4 + 2 + 32 // handshake header, legacy_version, random
                p += 1 + u8(p) // session id
                p += 2 + u16(p) // cipher suites
                p += 1 + u8(p) // compression methods
                val end = p + 2 + u16(p)
                p += 2
                while (p + 4 <= end) {
                    val type = u16(p)
                    val length = u16(p + 2)
                    // server_name: list length (2), name type (1), name length (2), name
                    if (type == 0x0000 && length >= 5) return p + 4 + 5
                    p += 4 + length
                }
                null
            } catch (_: IndexOutOfBoundsException) {
                null
            }
        }
    }
}

private class SplitHelloSocket : Socket() {
    private var stream: OutputStream? = null

    override fun getOutputStream(): OutputStream =
        stream ?: SplitHelloStream(super.getOutputStream(), this).also { stream = it }
}

private class SplitHelloStream(out: OutputStream, private val socket: Socket) : FilterOutputStream(out) {
    private var first = true

    override fun write(b: ByteArray, off: Int, len: Int) {
        if (!first) {
            out.write(b, off, len)
            return
        }
        first = false
        val split = SplitHelloSocketFactory.splitClientHello(b.copyOfRange(off, off + len))
        if (split == null) {
            out.write(b, off, len)
            return
        }
        Log.d("Dostup/Split", "ClientHello split into ${split.first.size} + ${split.second.size} bytes")
        socket.tcpNoDelay = true
        out.write(split.first)
        out.flush()
        Thread.sleep(20) // keep the two records in separate TCP segments
        out.write(split.second)
        out.flush()
    }
}
