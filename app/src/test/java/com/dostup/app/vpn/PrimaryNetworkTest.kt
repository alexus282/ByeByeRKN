package com.dostup.app.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrimaryNetworkTest {
    @Test
    fun theFirstNetworkAtStartUpIsNotAChange() {
        val primary = PrimaryNetwork<String>()
        assertFalse(primary.moveTo("wifi"))
        assertEquals("wifi", primary.current)
    }

    @Test
    fun movingBetweenNetworksIsAChange() {
        val primary = PrimaryNetwork<String>()
        primary.moveTo("wifi")
        assertTrue(primary.moveTo("cell"))
    }

    @Test
    fun losingEveryNetworkIsNotAChangeYet() {
        val primary = PrimaryNetwork<String>()
        primary.moveTo("wifi")
        assertFalse(primary.moveTo(null))
        assertNull(primary.current)
    }

    @Test
    fun aNetworkAppearingAfterACompleteLossIsAChange() {
        val primary = PrimaryNetwork<String>()
        primary.moveTo("wifi")
        primary.moveTo(null)
        assertTrue(primary.moveTo("cell"))
    }

    @Test
    fun noNetworkAtStartUpThenTheFirstOneIsStillNotAChange() {
        val primary = PrimaryNetwork<String>()
        assertFalse(primary.moveTo(null))
        assertFalse(primary.moveTo("wifi"))
    }
}
