package com.dostup.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseVersionsTest {
    @Test
    fun readsTheTagFromGithubsRedirect() {
        val location = "https://github.com/amnezia-vpn/amneziawg-android/releases/tag/v3.1.20260814"
        assertEquals("3.1.20260814", ReleaseVersions.fromRedirect(location))
    }

    @Test
    fun aRepositoryWithoutReleasesGivesNoVersion() {
        assertNull(ReleaseVersions.fromRedirect("https://github.com/owner/repo/releases"))
        assertNull(ReleaseVersions.fromRedirect(null))
    }

    @Test
    fun comparesNumbersNotText() {
        assertTrue(ReleaseVersions.isNewer("0.10.0", "0.9.0"))
        assertFalse(ReleaseVersions.isNewer("0.9.0", "0.10.0"))
    }

    @Test
    fun theSameVersionIsNotNewer() {
        assertFalse(ReleaseVersions.isNewer("0.1.0", "0.1.0"))
        assertFalse(ReleaseVersions.isNewer("0.1", "0.1.0"))
    }

    @Test
    fun aSuffixDoesNotBreakTheComparison() {
        assertTrue(ReleaseVersions.isNewer("0.2.0-beta", "0.1.0"))
    }

    @Test
    fun aTagWithoutNumbersIsNeverNewer() {
        assertFalse(ReleaseVersions.isNewer("nightly", "0.1.0"))
    }

    @Test
    fun aHugeDatedPartDoesNotOverflow() {
        assertTrue(ReleaseVersions.isNewer("3.1.20260814", "3.1.20260813"))
    }
}
