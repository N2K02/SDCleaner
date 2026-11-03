package eu.darken.sdmse.cleaner

import kotlinx.coroutines.withTimeout

/** Never finish the Activity or stop accessibility while a cleaning task is still draining. */
internal suspend fun stopCleaner(
    cancelBackground: suspend () -> Unit,
    cancelTasks: () -> Unit,
    awaitIdle: suspend () -> Unit,
    stopRecording: suspend () -> Unit,
    stopHost: () -> Unit,
) {
    cancelBackground()
    cancelTasks()
    withTimeout(15_000) { awaitIdle() }
    stopRecording()
    stopHost()
}
