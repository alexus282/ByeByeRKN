package com.dostup.app

import android.app.Application
import com.dostup.app.data.Store
import com.dostup.app.update.UpdateChecker
import com.dostup.app.vpn.VpnController
import com.dostup.app.vpn.VpnNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.amnezia.awg.backend.GoBackend

class DostupApp : Application() {
    lateinit var store: Store
        private set
    lateinit var vpn: VpnController
        private set
    lateinit var updates: UpdateChecker
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        store = Store(this)
        updates = UpdateChecker(store, BuildConfig.RELEASES_REPO, BuildConfig.VERSION_NAME)
        VpnNotification.createChannel(this)
        GoBackend.setForegroundNotification(VpnNotification)
        vpn = VpnController(this, store)
        // Started by the system's "Always-on VPN" setting.
        GoBackend.setAlwaysOnCallback { vpn.connect() }
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            // Only the kind of status changes the text, not every step of a search.
            combine(vpn.status, vpn.exit) { status, exit -> status::class to exit?.colo }
                .distinctUntilChanged()
                .collect { VpnNotification.update(this@DostupApp, vpn.status.value, vpn.exit.value, store.wantConnected) }
        }
    }

    companion object {
        lateinit var instance: DostupApp
            private set
    }
}
