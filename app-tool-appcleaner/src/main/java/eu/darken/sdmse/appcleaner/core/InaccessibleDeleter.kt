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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Inaccessible-cache deletion runs only through an independently authorized, local uid-2000
 * ADB shell. No settings navigation, Accessibility service, overlays, Shizuku, or root fallback.
 */
class InaccessibleDeleter @Inject constructor(
    private val userManager: UserManager2,
    private val directAdb: DirectAdb,
    private val inaccessibleCacheProvider: InaccessibleCacheProvider,
    private val settings: AppCleanerSettings,
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
        val targets = snapshot.junks
            .filter { it.inaccessibleCache != null && it.identifier.userHandle == currentUser }
            .filter { requested == null || it.identifier in requested }
            // Whole-tool cleans must respect exclusions. Explicitly selected apps are different.
            .filter { requested != null || !it.isExclusionLimited }
            .sortedByDescending { it.inaccessibleCache!!.totalSize }

        if (targets.isEmpty()) return InaccDelResult()

        updateProgressPrimary(eu.darken.sdmse.common.R.string.general_progress_preparing)
        updateProgressSecondary(CaString.EMPTY)
        updateProgressCount(Progress.Count.Percent(targets.size))
        val succeeded = mutableSetOf<InstallId>()
        val failed = mutableMapOf<InstallId, Exception>()
        val freedBytes = mutableMapOf<InstallId, Long>()

        fun snapshotResult(): InaccDelResult = InaccDelResult(
            succesful = succeeded.toSet(),
            failed = failed.filterKeys { it !in succeeded },
            freedBytes = freedBytes.toMap(),
        )

        try {
            if (!directAdb.ensureConnected()) {
                targets.forEach { failed[it.identifier] = IllegalStateException("ADB shell UID 2000 unavailable") }
                return snapshotResult()
            }

            val shouldForceStop = settings.forceStopBeforeClearing.value()
            for (junk in targets) {
                currentCoroutineContext().ensureActive()
                val id = junk.identifier
                val packageName = id.pkgId.name
                val userId = id.userHandle.handleId
                try {
                    val before = inaccessibleCacheProvider.determineCache(junk.pkg)?.totalSize
                        ?: junk.inaccessibleCache!!.totalSize
                    if (before == 0L) {
                        succeeded.add(id)
                        freedBytes[id] = 0
                        continue
                    }

                    if (shouldForceStop) {
                        // Force stop is optional and best-effort. Never stop our own process/system UI.
                        try {
                            val result = directAdb.forceStop(packageName, userId)
                            if (!result.success) log(TAG, WARN) { "Force-stop declined for $id" }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            log(TAG, WARN) { "Force-stop skipped for $id: ${e.asLog()}" }
                        }
                    }

                    // Critically, --cache-only must never be replaced by the data-erasing pm clear.
                    val issued = directAdb.clearCache(packageName, userId)
                    if (!issued.success || issued.output.contains("Failure", ignoreCase = true) ||
                        issued.output.contains("Error", ignoreCase = true)
                    ) {
                        failed[id] = IllegalStateException("ADB cache-only clear rejected for $id: ${issued.output.take(240)}")
                        continue
                    }

                    var after: Long? = null
                    withTimeoutOrNull(2500) {
                        while (after == null || after!! >= before) {
                            currentCoroutineContext().ensureActive()
                            after = inaccessibleCacheProvider.determineCache(junk.pkg)?.totalSize
                            if (after != null && after!! < before) break
                            delay(250)
                        }
                    }
                    if (after != null && after!! < before) {
                        succeeded.add(id)
                        freedBytes[id] = (before - after!!).coerceAtLeast(0)
                        log(TAG, INFO) { "Verified uid-2000 cache-only clear for $id" }
                    } else {
                        failed[id] = IllegalStateException("Cache-only command returned without verified decrease for $id")
                    }
                } catch (e: CancellationException) {
                    onPartialResult(snapshotResult())
                    throw e
                } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    failed[id] = e
                    log(TAG, WARN) { "ADB cache clear failed for $id: ${e.asLog()}" }
                } finally {
                    increaseProgress()
                }
            }
        } catch (e: CancellationException) {
            onPartialResult(snapshotResult())
            throw e
        } finally {
            // No idle listener, shell, daemon, or polling task after a cleaning run.
            directAdb.disconnect()
        }
        return snapshotResult()
    }

    data class InaccDelResult(
        val succesful: Set<InstallId> = emptySet(),
        val failed: Map<InstallId, Exception> = emptyMap(),
        val freedBytes: Map<InstallId, Long> = emptyMap(),
        /** Retained only for backward-compatible task result bookkeeping. No ACS stage exists. */
        val skippedNoConsent: Set<InstallId> = emptySet(),
    )

    private companion object {
        val TAG = logTag("AppCleaner", "DirectAdb")
    }
}
