package eu.darken.sdmse.main.core.adb

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import eu.darken.sdmse.common.adb.local.DirectAdb
import eu.darken.sdmse.common.adb.local.DirectAdbOptions
import eu.darken.sdmse.common.adb.local.DirectAdbRecovery
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Startup recovery runs in WorkManager, not inside the boot broadcast deadline. */
@HiltWorker
class DirectAdbBootWorker @AssistedInject constructor(
    @Assisted private val app: Context,
    @Assisted params: WorkerParameters,
    private val adb: DirectAdb,
    private val recovery: DirectAdbRecovery,
) : CoroutineWorker(app, params) {
    override suspend fun doWork(): Result {
        if (!DirectAdbOptions.get(app, DirectAdbOptions.Option.START_ON_BOOT) ||
            !DirectAdbOptions.wasAuthorized(app) ||
            DirectAdbOptions.stopRequested(app)) return Result.success()
        try {
            withTimeoutOrNull(25_000L) {
                // Only explicit opt-in and an actual privileged permission may
                // restore Wireless debugging after the device has restarted.
                if (DirectAdbOptions.get(
                        app, DirectAdbOptions.Option.RESTORE_AFTER_RESTART
                    ) && DirectAdbOptions.lastVerifiedBoot(app) >= 0 &&
                    DirectAdbOptions.lastVerifiedBoot(app) != DirectAdbOptions.currentBoot(app) &&
                    app.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") ==
                        PackageManager.PERMISSION_GRANTED && onWifi()) {
                    runCatching {
                        if (Settings.Global.getInt(app.contentResolver, "adb_wifi_enabled", 0) == 0) {
                            Settings.Global.putInt(app.contentResolver, "adb_wifi_enabled", 1)
                        }
                    }
                }
                recovery.recoverForBackground()
            }
        } finally {
            // Boot recovery is a one-shot connection test, never an idle daemon.
            withContext(NonCancellable) {
                withTimeoutOrNull(5_000L) { adb.disconnect() }
            }
        }
        return Result.success()
    }

    private fun onWifi(): Boolean {
        val connectivity = app.getSystemService(Context.CONNECTIVITY_SERVICE) as?
            ConnectivityManager ?: return false
        val network = connectivity.activeNetwork ?: return false
        return connectivity.getNetworkCapabilities(network)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }
}
