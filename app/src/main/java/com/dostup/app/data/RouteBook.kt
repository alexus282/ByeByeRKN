package com.dostup.app.data

import com.dostup.app.probe.WarpEndpoints
import org.json.JSONArray
import org.json.JSONObject

/**
 * What the app has learned about routes on one network (a Wi-Fi or a mobile operator), so it
 * starts from what worked here instead of from what worked somewhere else.
 *
 * The exit data centre depends on the address family (162.159.192.x may leave via Helsinki while
 * 8.x.x.x leaves via Moscow), so bad results are remembered per family and expire: routing changes.
 */
class RouteBook(
    private val routes: MutableList<Route> = mutableListOf(),
    private val badFamilies: MutableMap<String, Long> = hashMapOf(),
) {
    class Route(val candidate: Candidate, val good: Boolean, val okAt: Long, val failures: Int)

    /** Remembered routes worth trying, the most trustworthy first. */
    fun known(now: Long): List<Candidate> = routes
        .filter { now - it.okAt < ROUTE_TTL_MS }
        .sortedWith(compareByDescending<Route> { it.good }.thenBy { it.failures }.thenByDescending { it.okAt })
        .map { it.candidate }

    /** Routes that were fully good here: the ones worth reconnecting to. */
    fun knownGood(now: Long): List<Candidate> {
        val good = routes.filter { it.good }.map { it.candidate }
        return known(now).filter { candidate -> good.any { it === candidate } }
    }

    /** The best route that worked with caveats, kept as a fallback rather than tried first. */
    fun knownFallback(now: Long): Candidate? {
        val degraded = routes.filter { !it.good }.map { it.candidate }
        return known(now).firstOrNull { candidate -> degraded.any { it === candidate } }
    }

    fun recordSuccess(candidate: Candidate, good: Boolean, now: Long) {
        routes.removeAll { it.candidate.sameRoute(candidate) }
        routes.add(Route(candidate, good, now, 0))
        if (good) badFamilies.remove(WarpEndpoints.familyOf(candidate.endpoint))
        else badFamilies[WarpEndpoints.familyOf(candidate.endpoint)] = now
        trim(now)
    }

    /** A route that stopped working loses trust and is forgotten after a few failures. */
    fun recordFailure(candidate: Candidate) {
        val index = routes.indexOfFirst { it.candidate.sameRoute(candidate) }
        if (index < 0) return
        val route = routes[index]
        if (route.failures + 1 >= MAX_FAILURES) {
            routes.removeAt(index)
        } else {
            routes[index] = Route(route.candidate, route.good, route.okAt, route.failures + 1)
        }
    }

    fun isBadFamily(endpoint: String, now: Long): Boolean =
        badFamilies[WarpEndpoints.familyOf(endpoint)]?.let { now - it < BAD_FAMILY_TTL_MS } ?: false

    /**
     * Which of the endpoints that answered a probe to try, in order: one per address family,
     * since the exit depends on the family, and families already known to be bad go last.
     */
    fun attemptOrder(hits: List<Candidate>, now: Long): List<Candidate> {
        val perFamily = hits.distinctBy { WarpEndpoints.familyOf(it.endpoint) }
        val (bad, fresh) = perFamily.partition { isBadFamily(it.endpoint, now) }
        return fresh + bad
    }

    private fun trim(now: Long) {
        routes.removeAll { now - it.okAt >= ROUTE_TTL_MS }
        known(now).drop(MAX_ROUTES).forEach { dropped -> routes.removeAll { it.candidate === dropped } }
    }

    fun toJson(): String = JSONObject()
        .put("routes", JSONArray().apply {
            routes.forEach {
                put(JSONObject().put("c", it.candidate.toJson()).put("good", it.good).put("okAt", it.okAt).put("failures", it.failures))
            }
        })
        .put("bad", JSONObject().apply { badFamilies.forEach { (family, at) -> put(family, at) } })
        .toString()

    companion object {
        const val MAX_ROUTES = 6
        const val MAX_FAILURES = 3
        const val ROUTE_TTL_MS = 7L * 24 * 3600 * 1000
        const val BAD_FAMILY_TTL_MS = 24L * 3600 * 1000

        fun fromJson(json: String): RouteBook = runCatching {
            val root = JSONObject(json)
            val routes = mutableListOf<Route>()
            val array = root.getJSONArray("routes")
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                routes += Route(
                    Candidate.fromJson(item.getString("c")),
                    item.getBoolean("good"),
                    item.getLong("okAt"),
                    item.getInt("failures"),
                )
            }
            val bad = hashMapOf<String, Long>()
            val badJson = root.getJSONObject("bad")
            badJson.keys().forEach { bad[it] = badJson.getLong(it) }
            RouteBook(routes, bad)
        }.getOrDefault(RouteBook())
    }
}
