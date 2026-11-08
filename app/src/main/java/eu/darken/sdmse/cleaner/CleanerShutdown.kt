package eu.darken.sdmse.cleaner

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** How long Quit lets cancelled tasks finish before it closes anyway. */
internal const val QUIT_DRAIN_TIMEOUT_MS = 5_000L

/**
 * Quit always completes. Tasks are cancelled and given a short window to release their
 * leases; after that the ADB host is closed regardless. Ending the process mid-clean only
 * interrupts cache deletion, which is safe, and the OS closes any remaining socket.
 *
 * @return true if all tasks became idle before the host was closed.
 */
internal suspend fun stopCleaner(
    cancelBackground: suspend () -> Unit,
    cancelTasks: () -> Unit,
    awaitIdle: suspend () -> Unit,
    stopRecording: suspend () -> Unit,
    stopHost: suspend () -> Unit,
    drainTimeoutMs: Long = QUIT_DRAIN_TIMEOUT_MS,
): Boolean = withContext(NonCancellable) {
    runCatching { withTimeoutOrNull(2_000) { cancelBackground() } }
    runCatching { cancelTasks() }
    val drained = withTimeoutOrNull(drainTimeoutMs) { awaitIdle(); true } ?: false
    runCatching { withTimeoutOrNull(2_000) { stopRecording() } }
    runCatching { withTimeoutOrNull(3_000) { stopHost() } }
    drained
}
