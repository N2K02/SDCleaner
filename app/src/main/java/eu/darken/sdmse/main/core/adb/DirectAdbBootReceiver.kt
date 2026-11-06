package eu.darken.sdmse.main.core.adb

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.common.adb.local.DirectAdb
import eu.darken.sdmse.common.adb.local.DirectAdbOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Best-effort, bounded recovery after reboot/upgrade; no always-on service.
 * Never starts on a phone that has not already authorized this app's own key.
 */
@AndroidEntryPoint
class DirectAdbBootReceiver : BroadcastReceiver() {
    @Inject lateinit var adb: DirectAdb

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext
        if (!DirectAdbOptions.get(app, DirectAdbOptions.Option.START_ON_BOOT) ||
            !DirectAdbOptions.wasAuthorized(app) ||
            DirectAdbOptions.stopRequested(app)) return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(18_000L) {
                    // A user-disabled Wireless debugging setting within this boot is never
                    // overridden. Enabling it after a new boot requires explicit opt-in
                    // and a previously granted WRITE_SECURE_SETTINGS permission.
                    if (DirectAdbOptions.get(
                            app, DirectAdbOptions.Option.RESTORE_AFTER_RESTART
                        ) && DirectAdbOptions.lastVerifiedBoot(app) >= 0 &&
                        DirectAdbOptions.lastVerifiedBoot(app) != DirectAdbOptions.currentBoot(app) &&
                        app.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
                            PackageManager.PERMISSION_GRANTED && onWifi(app)) {
                        runCatching {
                            if (Settings.Global.getInt(app.contentResolver, "adb_wifi_enabled", 0) == 0) {
                                Settings.Global.putInt(app.contentResolver, "adb_wifi_enabled", 1)
                            }
                        }
                    }
                    // No arbitrary host connection and no pairing-code persistence.
                    adb.ensureConnected()
                }
            } finally {
                // Do not leave a boot BroadcastReceiver holding the device awake.
                pending.finish()
            }
        }
    }

    private fun onWifi(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as?
            ConnectivityManager ?: return false
        val network = manager.activeNetwork ?: return false
        return manager.getNetworkCapabilities(network)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }
}
