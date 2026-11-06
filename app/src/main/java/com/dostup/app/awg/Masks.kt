package com.dostup.app.awg

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.zip.CRC32
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * First-packet disguises for AmneziaWG's I1. Each builds a well-formed packet of a common UDP
 * protocol, so a DPI box classifying the flow by its first datagram sees something harmless.
 */
object Masks {
    private val random = SecureRandom()

    private val dnsNames = listOf(
        "yandex.ru", "vk.com", "mail.ru", "ozon.ru", "wildberries.ru", "gosuslugi.ru",
        "avito.ru", "dzen.ru", "rutube.ru", "sberbank.ru",
    )

    /** A recursive DNS query (A or HTTPS record) with an EDNS0 OPT record, like a stub resolver sends. */
    fun dnsQuery(name: String = dnsNames[random.nextInt(dnsNames.size)]): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(ByteBuffer.allocate(12)
            .putShort(random.nextInt(0x10000).toShort()) // transaction id
            .putShort(0x0120) // standard query, recursion desired, AD
            .putShort(1).putShort(0).putShort(0).putShort(1) // 1 question, 1 additional (OPT)
            .array())
        for (label in name.split('.')) {
            out.write(label.length)
            out.write(label.toByteArray())
        }
        out.write(0)
        out.write(byteArrayOf(0, if (random.nextBoolean()) 1 else 65, 0, 1)) // QTYPE A or HTTPS, QCLASS IN
        out.write(byteArrayOf(0, 0, 41, 0x04, 0xD0.toByte(), 0, 0, 0, 0, 0, 0)) // OPT, 1232-byte UDP payload
        return out.toByteArray()
    }

    /**
     * An ICE connectivity check: a STUN Binding Request with USERNAME, ICE-CONTROLLING, PRIORITY,
     * MESSAGE-INTEGRITY and FINGERPRINT, the shape WebRTC calls use (RFC 5389 / RFC 8445).
     */
    fun stunBindingRequest(): ByteArray {
        val transactionId = ByteArray(12).also(random::nextBytes)
        val attributes = ByteArrayOutputStream()
        fun attribute(type: Int, value: ByteArray) {
            attributes.write(ByteBuffer.allocate(4).putShort(type.toShort()).putShort(value.size.toShort()).array())
            attributes.write(value)
            repeat((4 - value.size % 4) % 4) { attributes.write(0) }
        }
        attribute(0x0006, "${randomToken(4)}:${randomToken(4)}".toByteArray()) // USERNAME
        attribute(0x802A, ByteArray(8).also(random::nextBytes)) // ICE-CONTROLLING
        attribute(0x0024, ByteBuffer.allocate(4).putInt(0x6E7F00FF - random.nextInt(0x100)).array()) // PRIORITY

        fun header(length: Int) = ByteBuffer.allocate(20)
            .putShort(0x0001) // Binding Request
            .putShort(length.toShort())
            .putInt(0x2112A442) // magic cookie
            .put(transactionId)
            .array()

        // MESSAGE-INTEGRITY covers the message with the length already counting itself (24 bytes).
        val beforeIntegrity = attributes.toByteArray()
        val integrity = Mac.getInstance("HmacSHA1").run {
            init(SecretKeySpec(randomToken(22).toByteArray(), "HmacSHA1"))
            doFinal(header(beforeIntegrity.size + 24) + beforeIntegrity)
        }
        attribute(0x0008, integrity)

        // FINGERPRINT is the CRC-32 of everything before it, length counting the 8-byte attribute.
        val beforeFingerprint = attributes.toByteArray()
        val crc = CRC32().apply { update(header(beforeFingerprint.size + 8) + beforeFingerprint) }.value
        attribute(0x8028, ByteBuffer.allocate(4).putInt((crc xor 0x5354554EL).toInt()).array())

        val body = attributes.toByteArray()
        return header(body.size) + body
    }

    /** A SIP OPTIONS keep-alive, as softphones send to their registrar. */
    fun sipOptions(): String {
        val user = "user${random.nextInt(9000) + 1000}"
        val host = listOf("sip.zadarma.com", "sip.mtt.ru", "sip.beeline.ru", "voip.ms")[random.nextInt(4)]
        val localIp = "192.168.${(1 + random.nextInt(254))}.${(2 + random.nextInt(252))}"
        val port = 5060
        return buildString {
            append("OPTIONS sip:$host SIP/2.0\r\n")
            append("Via: SIP/2.0/UDP $localIp:$port;rport;branch=z9hG4bK${randomToken(10)}\r\n")
            append("Max-Forwards: 70\r\n")
            append("From: <sip:$user@$host>;tag=${randomToken(8)}\r\n")
            append("To: <sip:$user@$host>\r\n")
            append("Call-ID: ${randomToken(16)}@$localIp\r\n")
            append("CSeq: ${(1 + random.nextInt(499))} OPTIONS\r\n")
            append("Contact: <sip:$user@$localIp:$port>\r\n")
            append("User-Agent: MicroSIP/3.21.4\r\n")
            append("Accept: application/sdp\r\n")
            append("Content-Length: 0\r\n\r\n")
        }
    }

    private fun randomToken(length: Int): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
        return String(CharArray(length) { alphabet[random.nextInt(alphabet.length)] })
    }
}
