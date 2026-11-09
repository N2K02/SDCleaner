package eu.darken.sdmse.scheduler.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import javax.inject.Inject


@AndroidEntryPoint
class SchedulerRestoreReceiver : BroadcastReceiver() {

    @Inject @AppScope lateinit var appScope: CoroutineScope
    @Inject lateinit var schedulerManager: SchedulerManager

    override fun onReceive(context: Context, intent: Intent) {
        log(TAG) { "onReceive($context,$intent)" }
        if (!ALLOWED_INTENTS.contains(intent.action)) {
            log(TAG, ERROR) { "Unknown intent: $intent" }
            return
        }

        log(TAG, INFO) { "Rechecking scheduler states (intent.data=${intent.data})" }

        val asyncPi = goAsync()

        Bugs.leaveBreadCrumb("Scheduler restore")

        appScope.launch {
            try {
                schedulerManager.reconcileSchedules()
                log(TAG) { "Finished scheduler checks" }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, ERROR) { "Scheduler restoration failed: $e" }
            } finally {
                // Always release the broadcast, even if the app scope is cancelled.
                asyncPi.finish()
            }
        }
    }

    companion object {
        private val ALLOWED_INTENTS = setOf(
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_FULLY_REMOVED,
            Intent.ACTION_BOOT_COMPLETED
        )
        internal val TAG = logTag("Scheduler", "Receiver", "Restore")
    }
}
