package eu.darken.sdmse.main.core.adb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import eu.darken.sdmse.common.adb.local.DirectAdbOptions
import java.util.concurrent.TimeUnit

/** Enqueue only; no native ADB connection is held inside a boot broadcast. */
class DirectAdbBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext
        if (!DirectAdbOptions.get(app, DirectAdbOptions.Option.START_ON_BOOT) ||
            !DirectAdbOptions.wasAuthorized(app) ||
            DirectAdbOptions.stopRequested(app)) return

        WorkManager.getInstance(app).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<DirectAdbBootWorker>()
                .setInitialDelay(5, TimeUnit.SECONDS)
                .build(),
        )
    }

    companion object {
        const val WORK_NAME = "sdcleaner-adb-boot-recovery"
    }
}
