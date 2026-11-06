package com.dostup.app.probe

import com.dostup.app.awg.AwgParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

object WarpEndpoints {
    const val HOST = "engage.cloudflareclient.com"

    val ports = listOf(
        500, 854, 859, 864, 878, 880, 890, 891, 894, 903, 908, 928, 934, 939, 942, 943, 945, 946,
        955, 968, 987, 988, 1002, 1010, 1014, 1018, 1070, 1074, 1180, 1387, 1701, 1843, 2371, 2408,
        2506, 3138, 3476, 3581, 3854, 4177, 4198, 4233, 4500, 5279, 5956, 7103, 7152, 7156, 7281,
        7559, 8319, 8742, 8854, 8886,
    )

    val prefixes = listOf(
        "162.159.192.", "162.159.193.", "162.159.195.",
        "188.114.96.", "188.114.97.", "188.114.98.", "188.114.99.",
        "8.6.112.", "8.34.70.", "8.34.146.", "8.35.211.", "8.39.125.", "8.39.204.", "8.39.214.", "8.47.69.",
    )

    /**
     * IPv6 endpoints embed the IPv4 one in their last 32 bits: engage.cloudflareclient.com is
     * 2606:4700:d0::a29f:c001, i.e. 162.159.192.1. IPv6 is often filtered less than IPv4.
     */
    val v6Prefixes = listOf("2606:4700:d0::a29f:c0", "2606:4700:d1::a29f:c0", "2606:4700:d0::a29f:c3", "2606:4700:d1::a29f:c3")

    private val wellKnown = listOf(
        "162.159.192.1:2408", "162.159.195.1:500", "188.114.97.1:4500", "162.159.193.10:1701", "$HOST:2408",
        "[2606:4700:d0::a29f:c001]:2408",
    )

    /**
     * Covers every address family, because the data centre a route exits from depends on the
     * family. About one in five is IPv6, free to probe: unreachable ones fail instantly.
     */
    fun sample(count: Int, random: Random = Random.Default): List<String> {
        val result = LinkedHashSet(wellKnown)
        val families = prefixes.shuffled(random)
        var next = 0
        while (result.size < count) {
            val port = ports.random(random)
            result += if (random.nextInt(5) == 0) {
                "[${v6Prefixes.random(random)}%02x]:$port".format(random.nextInt(1, 11))
            } else {
                "${families[next++ % families.size]}${random.nextInt(1, 11)}:$port"
            }
        }
        return result.toList()
    }

    /** Routes in one family tend to share an exit, so results are judged per family. */
    fun familyOf(endpoint: String): String = when {
        endpoint.startsWith("[") -> "v6"
        endpoint.startsWith(HOST) -> "162.159.192." // what the hostname resolves to
        else -> endpoint.substringBeforeLast(':').substringBeforeLast('.') + "."
    }

    fun resolve(endpoint: String): InetSocketAddress {
        val separator = endpoint.lastIndexOf(':')
        require(separator > 0) { "endpoint must be host:port" }
        val host = endpoint.substring(0, separator).removePrefix("[").removeSuffix("]")
        val port = endpoint.substring(separator + 1).toInt()
        return try {
            InetSocketAddress(InetAddress.getByName(host), port)
        } catch (_: Exception) {
            InetSocketAddress.createUnresolved(host, port)
        }
    }
}

class EndpointScanner(private val probe: HandshakeProbe) {
    class Hit(val endpoint: String, val params: AwgParams, val rttMs: Long)

    /** Probes every endpoint with every obfuscation variant and returns those that answered, fastest first. */
    suspend fun scan(
        endpoints: List<String>,
        variants: List<AwgParams>,
        onProgress: (done: Int, total: Int, found: Int) -> Unit = { _, _, _ -> },
    ): List<Hit> = scanRoutes(endpoints.flatMap { endpoint -> variants.map { endpoint to it } }, onProgress)

    /** Probes specific endpoint and obfuscation pairs, such as routes remembered for this network. */
    suspend fun scanRoutes(
        routes: List<Pair<String, AwgParams>>,
        onProgress: (done: Int, total: Int, found: Int) -> Unit = { _, _, _ -> },
    ): List<Hit> {
        val hits = Collections.synchronizedList(mutableListOf<Hit>())
        val done = AtomicInteger()
        coroutineScope {
            val semaphore = Semaphore(PARALLELISM)
            for ((endpoint, params) in routes) {
                launch(Dispatchers.IO) {
                    semaphore.withPermit {
                        val rtt = runCatching { probe.probe(WarpEndpoints.resolve(endpoint), params, TIMEOUT_MS) }.getOrNull()
                        if (rtt != null) hits += Hit(endpoint, params, rtt)
                        onProgress(done.incrementAndGet(), routes.size, hits.size)
                    }
                }
            }
        }
        return synchronized(hits) { hits.sortedBy { it.rttMs } }
    }

    private companion object {
        const val PARALLELISM = 24
        const val TIMEOUT_MS = 1500
    }
}
