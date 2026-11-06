package eu.darken.sdmse.cleaner

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/** Do not terminate the ADB host or Activity while cleaning owns any resource leases. */
internal suspend fun stopCleaner(
    cancelBackground: suspend () -> Unit,
    cancelTasks: () -> Unit,
    awaitIdle: suspend () -> Unit,
    stopRecording: suspend () -> Unit,
    stopHost: suspend () -> Unit,
) {
    try {
        cancelBackground()
        cancelTasks()
        withTimeout(15_000) { awaitIdle() }
    } finally {
        // Debug recording is independent of cleaning; always stop it even if
        // draining failed. Never tear down the shared ADB host until idle.
        withContext(NonCancellable) {
            withTimeoutOrNull(2_000) { stopRecording() }
        }
    }
    stopHost()
}

/** After the initial shutdown deadline, wait without keeping the CPU awake.
 * A finishing task must release its resource lease before the ADB host closes.
 */
internal suspend fun resumeCleanerQuitAfterTimeout(
    awaitIdle: suspend () -> Unit,
    stopHost: suspend () -> Unit,
) {
    awaitIdle()
    stopHost()
}
