package com.dostup.app.probe

import com.dostup.app.awg.AwgParams
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.security.SecureRandom

/**
 * Sends exactly what AmneziaWG would send when connecting (I1, junk packets, handshake
 * initiation) and waits for the server's handshake response.
 */
class HandshakeProbe(
    private val privateKey: ByteArray,
    private val peerPublic: ByteArray,
    /** Lets probes bypass a running tunnel; a no-op when the VPN is down. */
    private val protect: (DatagramSocket) -> Unit = {},
) {
    private val publicKey = WgHandshake.dh(privateKey, null)
    private val random = SecureRandom()

    /** Round-trip time of the handshake in milliseconds, or null if nothing came back. */
    fun probe(address: InetSocketAddress, params: AwgParams, timeoutMs: Int): Long? {
        if (address.isUnresolved) return null
        DatagramSocket().use { socket ->
            protect(socket)
            socket.connect(address)
            params.i1?.let { socket.send(DatagramPacket(it, it.size)) }
            repeat(params.jc) {
                val junk = ByteArray(params.jmin + random.nextInt(params.jmax - params.jmin + 1))
                random.nextBytes(junk)
                socket.send(DatagramPacket(junk, junk.size))
            }

            val senderIndex = random.nextInt()
            val initiation = WgHandshake.initiation(privateKey, publicKey, peerPublic, senderIndex)
            val start = System.nanoTime()
            val deadline = start + timeoutMs * 1_000_000L
            socket.send(DatagramPacket(initiation, initiation.size))

            val buffer = ByteArray(512)
            while (true) {
                val remainingMs = ((deadline - System.nanoTime()) / 1_000_000L).toInt()
                if (remainingMs <= 0) return null
                socket.soTimeout = remainingMs
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    return null
                }
                if (WgHandshake.isReplyTo(buffer, packet.length, senderIndex)) {
                    return (System.nanoTime() - start) / 1_000_000L
                }
            }
        }
    }
}
