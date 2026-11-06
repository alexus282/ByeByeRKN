package com.dostup.app

import com.dostup.app.awg.Obfuscation
import com.dostup.app.probe.EndpointScanner
import com.dostup.app.probe.HandshakeProbe
import com.dostup.app.probe.WarpEndpoints
import com.dostup.app.warp.WarpApi
import kotlinx.coroutines.runBlocking
import org.amnezia.awg.crypto.Key
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Talks to the real Cloudflare API and endpoints from this machine.
 * Runs only with DOSTUP_LIVE=1 so regular builds stay offline.
 */
class LiveWarpTest {
    @Test
    fun registerAndProbe() = runBlocking {
        assumeTrue(System.getenv("DOSTUP_LIVE") == "1")

        val account = WarpApi().register()
        println("registered: v4=${account.addressV4} peer=${account.peerPublicKey}")

        val probe = HandshakeProbe(
            Key.fromBase64(account.privateKey).bytes,
            Key.fromBase64(account.peerPublicKey).bytes,
        )
        val endpoints = WarpEndpoints.sample(24)
        val variants = Obfuscation.entries.map { it.params() }
        val hits = EndpointScanner(probe).scan(endpoints, variants)
        println("answered ${hits.size} of ${endpoints.size * variants.size} probes")
        for (variant in Obfuscation.entries) {
            val ok = hits.filter { it.params.obfuscation == variant }
            println("  ${variant.name}: ${ok.size}/${endpoints.size} ${ok.take(5).joinToString { "${it.endpoint}=${it.rttMs}ms" }}")
        }
    }
}
