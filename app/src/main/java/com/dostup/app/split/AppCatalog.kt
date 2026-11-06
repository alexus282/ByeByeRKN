package com.dostup.app.split

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

data class AppEntry(val packageName: String, val label: String)

object AppCatalog {
    /**
     * Services that refuse foreign IPs or nag about VPNs, so by default they bypass the tunnel.
     * Only installed ones are ever passed to the VPN builder.
     */
    val russianServices = setOf(
        "ru.sberbankmobile", "com.idamob.tinkoff.android", "ru.alfabank.mobile.android",
        "ru.vtb24.mobilebanking.android", "ru.raiffeisennews", "ru.psbank.mobile", "ru.mkb.mobile",
        "ru.gazprombank.android.mobilebank.app", "com.openbank", "ru.pochta.bank", "ru.sovcomcard.halva.v1",
        "ru.nspk.mirpay", "ru.nspk.sbpay",
        "ru.rostel", "ru.gosuslugi.pos", "ru.nalog.www", "ru.fns.lkfl", "ru.mos.app", "ru.mos.polls",
        "ru.ozon.app.android", "com.wildberries.ru", "com.avito.android", "ru.beru.android",
        "ru.yandex.searchplugin", "com.yandex.browser", "ru.yandex.taxi", "ru.yandex.yandexmaps",
        "ru.yandex.yandexnavi", "ru.yandex.music", "ru.yandex.mail", "ru.yandex.disk", "com.yandex.bank",
        "ru.kinopoisk", "ru.foodfox.client", "com.deliveryclub",
        "com.vkontakte.android", "ru.oneme.app", "ru.ok.android", "ru.mail.mailapp", "ru.rutube.app",
        "ru.mts.mymts", "ru.megafon.mlk", "ru.beeline.services", "ru.tele2.mytele2",
        "ru.dublgis.dgismobile", "ru.rzd.pass", "ru.aeroflot", "ru.sbermarket.android",
        "ru.lentaonline.mobile", "ru.perekrestok.app", "ru.myshop.vkusvill", "ru.magnit.express.android",
    )

    /** Services that are blocked or throttled; the default list for "only selected apps" mode. */
    val blockedServices = setOf(
        "org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram",
        "com.google.android.youtube", "com.google.android.apps.youtube.music", "app.revanced.android.youtube",
        "com.discord", "com.instagram.android", "com.whatsapp", "com.whatsapp.w4b", "com.facebook.katana",
        "com.facebook.orca", "com.twitter.android", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
        "com.viber.voip", "com.snapchat.android", "com.reddit.frontpage", "com.linkedin.android",
        "com.openai.chatgpt", "com.anthropic.claude", "com.google.android.apps.bard", "org.signal.android",
        "com.patreon.android", "tv.twitch.android.app", "com.spotify.music", "com.soundcloud.android",
    )

    fun launchable(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { AppEntry(it.packageName, pm.getApplicationLabel(it).toString()) }
            .sortedBy { it.label.lowercase() }
    }

    fun installedOnly(context: Context, packages: Collection<String>): List<String> {
        val pm = context.packageManager
        return packages.filter {
            try {
                pm.getApplicationInfo(it, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }.sorted()
    }
}
