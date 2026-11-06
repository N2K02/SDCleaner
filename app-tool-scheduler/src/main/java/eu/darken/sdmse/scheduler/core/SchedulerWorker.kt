package eu.darken.sdmse.scheduler.core

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import eu.darken.sdmse.common.adb.AdbManager
import eu.darken.sdmse.common.adb.canUseAdbNow
import eu.darken.sdmse.common.adb.local.DirectAdb
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.error.hasCause
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.automation.core.errors.AutomationException
import eu.darken.sdmse.automation.core.errors.AutomationSchedulerException
import eu.darken.sdmse.common.root.canUseRootNow
import eu.darken.sdmse.common.shell.ShellOps
import eu.darken.sdmse.common.shell.ipc.ShellOpsCmd
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.TaskResultWarnings
import eu.darken.sdmse.main.core.taskmanager.SchedulerTaskFactory
import eu.darken.sdmse.stats.core.ReportDetails
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import eu.darken.sdmse.setup.SetupHealerSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first

@HiltWorker
class SchedulerWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters,
    @Suppress("UNUSED_PARAMETER") dispatcherProvider: DispatcherProvider,
    private val taskSubmitter: TaskSubmitter,
    private val schedulerTaskFactory: SchedulerTaskFactory,
    private val schedulerManager: SchedulerManager,
    private val schedulerNotifications: SchedulerNotifications,
    private val setupHealer: SetupHealerSource,
    private val rootManager: RootManager,
    private val adbManager: AdbManager,
    private val shellOps: ShellOps,
    private val runHistory: SchedulerRunHistory,
) : CoroutineWorker(context, params) {

    init {
        log(TAG, VERBOSE) { "init(): workerId=$id" }
    }

    private var currentSchedule: Schedule? = null
    private var wasInterrupted = false
    private var startedAtMs: Long? = null
    private var lastOutcome = RunOutcome.WARNING
    private var lastDetails: List<String> = emptyList()
    private var lastError: String? = null

    private val scheduleId: ScheduleId
        get() = inputData.getString(INPUT_SCHEDULE_ID) as ScheduleId

    private val isManual: Boolean
        get() = inputData.getBoolean(INPUT_MANUAL, false)

    private suspend fun getSchedule(): Schedule {
        return schedulerManager.getSchedule(scheduleId) ?: throw IllegalStateException("Schedule not found $scheduleId")
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val schedule = currentSchedule
        return if (schedule != null) {
            schedulerNotifications.getForegroundInfo(schedule)
        } else {
            schedulerNotifications.getForegroundInfo(scheduleId)
        }
    }

    override suspend fun doWork(): Result = try {
        log(TAG, VERBOSE) { "Executing $inputData now (runAttemptCount=$runAttemptCount)" }

        // Use bounded ordinary WorkManager background jobs; no foreground promotion or notification.
        val schedule = getSchedule().also { currentSchedule = it }
        startedAtMs = System.currentTimeMillis()

        if (runAttemptCount > 0) {
            log(TAG, WARN) { "Repeat execution attempt ($runAttemptCount) for $schedule" }
            lastOutcome = RunOutcome.WARNING
            lastDetails = listOf("WorkManager retried this schedule; previous execution may have been interrupted")
            // The next occurrence is appended as a dependent of this still RUNNING work (see `finally`).
            // Returning failure would make WorkManager fail the appended successor too,
            // permanently severing the recurring chain until the next app start re-arms it.
            Result.success(inputData)
        } else {
            val start = System.currentTimeMillis()
            log(TAG, INFO) { "Executing schedule $schedule" }
            Bugs.leaveBreadCrumb("Executing schedule")

            startedAtMs = start
            val results = withTimeout(8 * 60 * 1000L) { doDoWork(schedule) }
            lastDetails = results.map { item ->
                val tool = item.task.type.toString()
                when {
                    item.error != null -> "$tool: failed - ${item.error.javaClass.simpleName}"
                    (item.result as? TaskResultWarnings)?.warningMessage != null ->
                        "$tool: ${(item.result as TaskResultWarnings).warningMessage}"
                    item.result is ReportDetails.AffectedSpace ->
                        "$tool: freed ${(item.result as ReportDetails.AffectedSpace).affectedSpace} bytes"
                    item.result != null -> "$tool: completed"
                    else -> "$tool: no result"
                }
            }
            val failed = results.count { it.error != null || it.result == null }
            val warned = results.any { (it.result as? TaskResultWarnings)?.warningMessage != null }
            lastOutcome = when {
                lastError != null -> RunOutcome.WARNING
                failed == results.size && results.isNotEmpty() -> RunOutcome.FAILED
                failed > 0 || warned -> RunOutcome.WARNING
                results.isEmpty() -> RunOutcome.WARNING
                else -> RunOutcome.SUCCESS
            }

            val duration = System.currentTimeMillis() - start
            log(TAG, INFO) { "Execution finished after ${duration}ms: $schedule" }

            Result.success(inputData)
        }
    } catch (e: TimeoutCancellationException) {
        // Ordinary WorkManager jobs have a limited execution window. Stop cleaning
        // before Android has to force-stop a long-running background job.
        log(TAG, WARN) { "Scheduled cleaning timed out after the 8-minute budget" }
        lastOutcome = RunOutcome.WARNING
        lastError = "Background cleanup exceeded 8-minute limit"
        Result.success(inputData)
    } catch (e: CancellationException) {
        // The OS or user has stopped this worker. Do not convert the signal to
        // Result.success(), and do not append a successor while cancelling.
        wasInterrupted = true
        if (hasApiLevel(31)) {
            @Suppress("NewApi")
            log(TAG, WARN) { "WorkManager stopped schedule id=$scheduleId, stopReason=$stopReason" }
        } else {
            log(TAG, WARN) { "WorkManager stopped schedule id=$scheduleId" }
        }
        lastOutcome = RunOutcome.WARNING
        lastError = "Cancelled by Android or user"
        throw e
    } catch (e: Throwable) {
        log(TAG, ERROR) { "Execution failed: ${e.asLog()}" }
        lastOutcome = RunOutcome.FAILED
        lastError = e.javaClass.simpleName + ": " + (e.message ?: "Unknown error")
        // Keep the recurring chain viable after an ordinary tool error.
        Result.success(inputData)
    } finally {
        // Only SHORT, bounded bookkeeping may outlive cancellation. No detached
        // tasks, polling, sockets or indefinitely waiting commits in a worker.
        withContext(NonCancellable) {
            currentSchedule?.let { schedule ->
                startedAtMs?.let { started ->
                    try {
                        withTimeout(3_000) {
                            runHistory.record(schedule, started, System.currentTimeMillis(),
                                lastOutcome, lastDetails, lastError, isManual)
                        }
                    } catch (e: Exception) {
                        log(TAG, ERROR) { "Run history cleanup failed: ${e.asLog()}" }
                    }
                }
            }

            if (!wasInterrupted && !isStopped) {
                try {
                    withTimeout(3_000) { schedulerManager.updateExecutedNow(scheduleId, manual = isManual) }
                } catch (e: Exception) {
                    log(TAG, ERROR) { "Failed to update execution time: ${e.asLog()}" }
                }
            }

            try {
                schedulerNotifications.cancel(scheduleId)
            } catch (e: Exception) {
                log(TAG, ERROR) { "Failed to clear old notification: ${e.asLog()}" }
            }

            if (!isManual && !wasInterrupted && !isStopped) {
                try {
                    withTimeout(5_000) { schedulerManager.reschedule(scheduleId) }
                } catch (e: Exception) {
                    log(TAG, ERROR) { "Failed to reschedule: ${e.asLog()}" }
                }
            }
        }
    }

    private suspend fun doDoWork(schedule: Schedule): Set<SchedulerNotifications.Results> {
        val enabledTools = buildSet {
            if (schedule.useCorpseFinder) add(SDMTool.Type.CORPSEFINDER)
            if (schedule.useSystemCleaner) add(SDMTool.Type.SYSTEMCLEANER)
            if (schedule.useAppCleaner) add(SDMTool.Type.APPCLEANER)
        }
        val tasks = schedulerTaskFactory.createTasks(schedule.id, enabledTools)

        delay(1000)

        // If the worker was launched from a cold-app-start some permission may be broken
        // Instead of driving straight into an error, let's slightly delay and see if we can fix any setup issues
        val setupReady = withTimeoutOrNull(5_000) {
            setupHealer.state.first { it.healAttemptCount > 0 && !it.isWorking }
        }
        if (setupReady == null) {
            // On cold starts SetupHealer may not emit (e.g. permissions not
            // available). Never keep WorkManager awake indefinitely for it.
            lastError = "Setup readiness check timed out (5s); cleanup attempted anyway"
            log(TAG, WARN) { lastError ?: "Setup readiness check timed out" }
        }

        val taskResults = coroutineScope {
            val taskJobs = tasks.map { task ->
                async {
                    try {
                    log(TAG) { "Launching $task" }
                    // Quiet scheduled jobs do not create completion or foreground notifications.
                    val result = taskSubmitter.submit(task,
                        notifyOnFinish = false, cancelOnCallerCancellation = true)
                    log(TAG) { "Finished $task -> $result" }
                    SchedulerNotifications.Results(task, result = result)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log(TAG, ERROR) { "Scheduler task failed ($task): ${e.asLog()}" }
                    val wrappedError = if (e.hasCause(AutomationException::class)) {
                        AutomationSchedulerException(e)
                    } else {
                        e
                    }
                    SchedulerNotifications.Results(task, error = wrappedError)
                    }
                }
            }
            log(TAG) { "Waiting for scheduled cleanup tasks: $taskJobs" }
            taskJobs.awaitAll().toSet()
        }
        log(TAG) { "All scheduled tasks have finished." }

        schedule.commandsAfterSchedule.takeIf { it.isNotEmpty() }?.let { cmds ->
            log(TAG, INFO) { "Post-schedule commands are available" }
            cmds.forEachIndexed { index, s -> log(TAG, INFO) { "Command #$index: $s" } }

            val shellOpsMode = when {
                rootManager.canUseRootNow() -> ShellOps.Mode.ROOT
                adbManager.canUseAdbNow() -> ShellOps.Mode.ADB
                else -> ShellOps.Mode.NORMAL
            }
            val result = shellOps.execute(ShellOpsCmd(cmds = cmds), shellOpsMode)
            log(TAG, INFO) { "Post-schedule ShellOps result: $result" }
            if (!result.isSuccess) {
                lastError = "Post-schedule command failed"
                lastOutcome = RunOutcome.WARNING
            }
        }
        return taskResults
    }

    companion object {
        const val INPUT_SCHEDULE_ID = "scheduler.worker.input.scheduleid"
        const val INPUT_MANUAL = "scheduler.worker.input.manual"
        val TAG = logTag("Scheduler", "Worker")
    }
}
