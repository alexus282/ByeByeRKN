package com.dostup.app.awg

import com.dostup.app.warp.WarpAccount
import org.amnezia.awg.config.Config
import org.amnezia.awg.crypto.KeyPair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.StringReader

class WarpConfigTest {
    private val account = WarpAccount(
        id = "id", token = "token",
        privateKey = KeyPair().privateKey.toBase64(),
        peerPublicKey = "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=",
        addressV4 = "172.16.0.2", addressV6 = "2606:4700:110:8a36:df92:102a:9602:fa18",
    )

    private fun parse(text: String) = Config.parse(BufferedReader(StringReader(text)))

    @Test
    fun renderedConfigParsesWithAwgFields() {
        val text = WarpConfig.render(
            account, "162.159.192.1:2408", Obfuscation.QUIC.params("ya.ru"),
            listOf("1.1.1.1"), SplitRule(excluded = listOf("ru.rostel", "ru.ozon.app.android")),
        )
        val config = parse(text)
        val userspace = config.toAwgUserspaceString()
        assertTrue(Regex("jc=[3-6]\n").containsMatchIn(userspace))
        assertTrue(Regex("jmax=\\d+\n").containsMatchIn(userspace))
        assertTrue(userspace.contains("i1=<b 0x"))
        assertEquals(1280, config.getInterface().mtu.get())
        assertEquals(setOf("ru.rostel", "ru.ozon.app.android"), config.getInterface().excludedApplications)
        assertEquals(1, config.peers.size)
    }

    @Test
    fun applySplitReplacesImportedAppLists() {
        val imported = WarpConfig.render(account, "1.2.3.4:500", Obfuscation.NONE.params(), listOf("1.1.1.1"), SplitRule(included = listOf("a.b")))
        val replaced = WarpConfig.applySplit(imported, SplitRule(excluded = listOf("c.d")))
        val config = parse(replaced)
        assertEquals(emptySet<String>(), config.getInterface().includedApplications)
        assertEquals(setOf("c.d"), config.getInterface().excludedApplications)
        assertEquals("1.2.3.4:500", WarpConfig.endpointOf(replaced))
    }
}
