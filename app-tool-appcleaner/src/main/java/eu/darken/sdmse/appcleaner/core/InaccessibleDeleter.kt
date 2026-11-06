package eu.darken.sdmse.appcleaner.core

import eu.darken.sdmse.appcleaner.core.scanner.InaccessibleCacheProvider
import eu.darken.sdmse.common.adb.local.DirectAdb
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.throttleLatest
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.increaseProgress
import eu.darken.sdmse.common.progress.updateProgressCount
import eu.darken.sdmse.common.progress.updateProgressPrimary
import eu.darken.sdmse.common.progress.updateProgressSecondary
import eu.darken.sdmse.common.user.UserManager2
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Inaccessible (private) cache deletion through the locally authorized uid-2000 ADB shell.
 * No settings navigation, Accessibility, overlays, Shizuku or root.
 *
 * The shell UID can only free private caches device-wide (`pm trim-caches`, CLEAR_APP_CACHE).
 * Per-package `pm clear --cache-only` needs INTERNAL_DELETE_CACHE_FILES, which the shell lacks;
 * Android silently drops that request and `pm` never returns. So:
 *  - whole-tool cleans (manual, one-click, scheduled) run ONE trim, then verify per app;
 *  - targeted cleans of selected apps cannot remove private cache and say so clearly.
 */
class InaccessibleDeleter @Inject constructor(
    private val userManager: UserManager2,
    private val directAdb: DirectAdb,
    private val inaccessibleCacheProvider: InaccessibleCacheProvider,
    @Suppress("unused") private val settings: AppCleanerSettings,
) : Progress.Host, Progress.Client {
    private val progressPub = MutableStateFlow<Progress.Data?>(
        Progress.Data(primary = eu.darken.sdmse.common.R.string.general_progress_preparing.toCaString())
    )
    override val progress: Flow<Progress.Data?> = progressPub.throttleLatest(250)
    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun deleteInaccessible(
        snapshot: AppCleaner.Data,
        targetPkgs: Collection<InstallId>?,
        useAutomation: Boolean,
        isBackground: Boolean,
        onPartialResult: (InaccDelResult) -> Unit = {},
    ): InaccDelResult {
        val currentUser = userManager.currentUser().handle
        val requested = targetPkgs?.toSet()
        val isAllApps = requested == null
        val targets = snapshot.junks
            .filter { it.inaccessibleCache != null && it.identifier.userHandle == currentUser }
            .filter { requested == null || it.identifier in requested }
            .sortedByDescending { it.inaccessibleCache!!.totalSize }

        if (targets.isEmpty()) return InaccDelResult()

        if (!isAllApps) {
            // Never trim every app's cache because the user selected a few apps.
            return InaccDelResult(
                failed = targets.associate { it.identifier to TargetedClearUnsupportedException(it.identifier) }
            )
        }

        // Exclusion-limited junks are only in the snapshot because the device-wide trim reaches
        // them anyway. They may be credited with a success but are never reported as failures.
        val limitedIds = targets.filter { it.isExclusionLimited }.map { it.identifier }.toSet()
        val succeeded = mutableSetOf<InstallId>()
        val failed = mutableMapOf<InstallId, Exception>()
        val freedBytes = mutableMapOf<InstallId, Long>()

        fun snapshotResult(): InaccDelResult = InaccDelResult(
            succesful = succeeded.toSet(),
            failed = failed.filterKeys { it !in succeeded && it !in limitedIds },
            freedBytes = freedBytes.toMap(),
        )

        fun failAll(cause: (InstallId) -> Exception) = targets.forEach { failed[it.identifier] = cause(it.identifier) }

        updateProgressPrimary(eu.darken.sdmse.appcleaner.R.string.appcleaner_progress_shizuku_deleting_caches)
        updateProgressSecondary(CaString.EMPTY)
        updateProgressCount(Progress.Count.Indeterminate())

        try {
            if (!directAdb.ensureConnected()) {
                failAll { IllegalStateException("ADB shell UID 2000 unavailable") }
                return snapshotResult()
            }

            val trim = try {
                directAdb.trimCaches()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                log(TAG, WARN) { "ADB trim-caches failed: ${e.asLog()}" }
                failAll { id -> IOException("ADB cache trim failed for $id", e) }
                return snapshotResult()
            }
            if (!trim.success) {
                log(TAG, WARN) { "ADB trim-caches rejected (exit=${trim.exitCode}): ${trim.output.take(240)}" }
                failAll { id -> IllegalStateException("ADB cache trim rejected for $id: ${trim.output.take(240)}") }
                return snapshotResult()
            }
            log(TAG, INFO) { "ADB trim-caches issued; verifying ${targets.size} apps" }

            // StorageStatsManager lags behind the actual deletion; sample in rounds so no target
            // starves another, under one wall-clock budget.
            updateProgressCount(Progress.Count.Percent(targets.size))
            val pending = targets.toMutableList()
            val observed = mutableMapOf<InstallId, Long>()
            delay(TRIM_SETTLE_DELAY_MS)
            withTimeoutOrNull(TRIM_VERIFY_TIMEOUT_MS) {
                while (pending.isNotEmpty()) {
                    currentCoroutineContext().ensureActive()
                    val settled = pending.filter { junk ->
                        val before = junk.inaccessibleCache!!.totalSize
                        val after = inaccessibleCacheProvider.determineCache(junk.pkg)?.totalSize
                            ?: return@filter false
                        observed[junk.identifier] = after
                        after == 0L || after < before
                    }
                    settled.forEach { increaseProgress() }
                    pending.removeAll(settled)
                    if (pending.isEmpty()) break
                    delay(TRIM_POLL_MS)
                }
            }

            targets.forEach { junk ->
                val id = junk.identifier
                val before = junk.inaccessibleCache!!.totalSize
                val after = observed[id]
                when {
                    after != null && (after == 0L || after < before) -> {
                        succeeded.add(id)
                        freedBytes[id] = (before - after).coerceAtLeast(0)
                    }
                    after == null -> failed[id] = IllegalStateException("Could not measure cache after ADB trim for $id")
                    else -> failed[id] = IllegalStateException(
                        "Cache unchanged after ADB trim for $id (app may be in use)"
                    )
                }
            }
            log(TAG, INFO) { "ADB trim verified ${succeeded.size}/${targets.size} apps" }
        } catch (e: CancellationException) {
            onPartialResult(snapshotResult())
            throw e
        } finally {
            // A suspended worker may already be cancelled here. Close the ADB
            // socket under a short independent deadline rather than allowing
            // cancellation to skip cleanup or a wedged socket to block forever.
            withContext(NonCancellable) {
                try {
                    val closed = withTimeoutOrNull(4_000) {
                        directAdb.disconnect()
                        true
                    } ?: false
                    if (!closed) log(TAG, WARN) { "Timed out disconnecting the ADB cleanup session" }
                } catch (e: Exception) {
                    log(TAG, WARN) { "Failed to close ADB cleanup session: ${e.asLog()}" }
                }
            }
        }
        return snapshotResult()
    }

    class TargetedClearUnsupportedException(id: InstallId) : UnsupportedOperationException(
        "Android lets ADB clear private app caches only device-wide. " +
            "Run a full App Cleaner clean to remove the private cache of $id."
    )

    data class InaccDelResult(
        val succesful: Set<InstallId> = emptySet(),
        val failed: Map<InstallId, Exception> = emptyMap(),
        val freedBytes: Map<InstallId, Long> = emptyMap(),
        /** Retained only for backward-compatible task result bookkeeping. No ACS stage exists. */
        val skippedNoConsent: Set<InstallId> = emptySet(),
    )

    private companion object {
        val TAG = logTag("AppCleaner", "DirectAdb")
        const val TRIM_SETTLE_DELAY_MS = 1_500L
        const val TRIM_POLL_MS = 500L
        const val TRIM_VERIFY_TIMEOUT_MS = 12_000L
    }
}
