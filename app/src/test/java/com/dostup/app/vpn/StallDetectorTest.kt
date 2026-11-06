package com.dostup.app.vpn

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StallDetectorTest {
    private val start = 1_800_000_000_000L
    private fun handshake(ageSec: Long, nowMs: Long) = nowMs / 1000 - ageSec

    @Test
    fun healthyTrafficIsQuiet() {
        val d = StallDetector()
        var rx = 0L
        var tx = 0L
        for (i in 0..12) {
            val now = start + i * 5_000L
            rx += 50_000; tx += 8_000
            assertNull(d.update(now, rx, tx, handshake(30, now)))
        }
    }

    @Test
    fun idleKeepalivesAreQuiet() {
        val d = StallDetector()
        for (i in 0..12) {
            val now = start + i * 5_000L
            assertNull(d.update(now, 1_000, 1_000 + i * 6L, handshake(60, now)))
        }
    }

    @Test
    fun sendingWithoutRepliesIsFlaggedAfterTheWindow() {
        val d = StallDetector()
        var tx = 10_000L
        var flaggedAt = -1
        for (i in 0..8) {
            val now = start + i * 5_000L
            tx += 3_000
            if (d.update(now, 500_000, tx, handshake(40, now)) != null && flaggedAt < 0) flaggedAt = i
        }
        // Needs ~15 s of history before judging: 5 s samples -> flagged at the 4th one.
        assert(flaggedAt == 3) { "flagged at sample $flaggedAt" }
    }

    @Test
    fun staleHandshakeIsFlaggedImmediately() {
        val d = StallDetector()
        assertNotNull(d.update(start, 0, 0, handshake(201, start)))
        assertNull(d.update(start, 0, 0, handshake(150, start)))
    }

    @Test
    fun counterResetAfterTunnelSwapDoesNotLookLikeAStall() {
        val d = StallDetector()
        for (i in 0..3) d.update(start + i * 5_000L, 900_000, 100_000 + i * 3_000L, handshake(10, start + i * 5_000L))
        // New tunnel: counters start from zero again.
        for (i in 4..6) {
            val now = start + i * 5_000L
            assertNull(d.update(now, (i - 3) * 2_000L, (i - 3) * 1_000L, handshake(5, now)))
        }
    }
}
