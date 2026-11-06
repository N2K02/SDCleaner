package eu.darken.sdmse.scheduler.core

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.DynamicStateFlow
import eu.darken.sdmse.common.flow.withPrevious
import eu.darken.sdmse.common.toSystemTimezone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class SchedulerManager @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    private val settings: SchedulerSettings,
    private val storage: ScheduleStorage,
    private val workManager: WorkManager,
) {

    private val internalState = DynamicStateFlow(parentScope = appScope + dispatcherProvider.IO) {
        State(schedules = storage.load() ?: emptySet())
    }
    val state: Flow<State> = internalState.flow
    // Serializes all unique-work checks and mutations in this process; without
    // this gate, cold-start reconciliation and a finishing worker can both
    // append a successor to the same active WorkManager chain.
    private val workGate = Mutex()

    /** Read-only, event-driven WorkManager status; no polling, wake locks or background service. */
    /** The queue policy is kept pure for race/idle-cycle regression tests. */
    internal object QueuePolicy {
        fun hasSuccessor(infos: List<WorkInfo>): Boolean = infos.any {
            it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED
        }

        fun automaticActive(infos: List<WorkInfo>): Boolean = infos.any {
            it.state == WorkInfo.State.RUNNING
        }
    }

    data class WorkSnapshot(
        val scheduleId: ScheduleId,
        val label: String,
        val running: Int = 0,
        val pending: Int = 0,
        val lastState: String = "Not scheduled",
        val error: String? = null,
    )

    val runtimeWork: Flow<List<WorkSnapshot>> = state
        .map { it.schedules.map { schedule -> schedule.id to schedule.label }.sortedBy { pair -> pair.first } }
        .distinctUntilChanged()
        .flatMapLatest { schedules ->
            if (schedules.isEmpty()) flowOf(emptyList())
            else combine(schedules.map { (id, label) ->
                workManager.getWorkInfosForUniqueWorkFlow("worker-schedule-$id")
                    .map { infos ->
                        WorkSnapshot(
                            scheduleId = id,
                            label = label,
                            running = infos.count { it.state == WorkInfo.State.RUNNING },
                            pending = infos.count {
                                it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED
                            },
                            lastState = infos.lastOrNull()?.state?.name ?: "Not scheduled",
                        )
                    }
                    .catch { error ->
                        log(TAG, WARN) { "WorkManager status unavailable for $id: ${error.asLog()}" }
                        emit(WorkSnapshot(id, label, error = error.javaClass.simpleName))
                    }
            }) { statuses -> statuses.toList() }
        }

    init {
        internalState.flow
            .drop(1)
            .onEach { storage.save(it.schedules) }
            .catch { log(TAG, ERROR) { "Failed to save schedules: ${it.asLog()}" } }
            .launchIn(appScope)

        // Reconcile both enabled state AND timing changes. A changed interval must replace queued
        // WorkManager requests; checking only isScheduled() leaves the old ETA in place.
        internalState.flow
            .withPrevious()
            .onEach { (oldState, newState) ->
                val oldById = oldState?.schedules?.associateBy { it.id }.orEmpty()
                val newById = newState.schedules.associateBy { it.id }
                oldById.values.filter { it.id !in newById }.forEach { it.cancel() }
                newState.schedules.forEach { current ->
                    val previous = oldById[current.id]
                    when {
                        previous == null -> current.checkSchedulingState()
                        previous.scheduledAt != current.scheduledAt ||
                            previous.intervalMinutes != current.intervalMinutes ||
                            previous.runOnce != current.runOnce ||
                            previous.initialDelayMinutes != current.initialDelayMinutes ||
                            previous.repeatInterval != current.repeatInterval ||
                            previous.hour != current.hour ||
                            previous.minute != current.minute ||
                            previous.userZone != current.userZone -> {
                            current.cancel()
                            if (current.isEnabled) current.schedule(reschedule = false)
                        }
                        else -> current.checkSchedulingState()
                    }
                }
            }
            .launchIn(appScope)

        // Check if global options have changed that require schedule re-creation
        combine(
            settings.skipWhenNotCharging.flow.distinctUntilChanged().withPrevious(),
            settings.skipWhenPowerSaving.flow.distinctUntilChanged().withPrevious(),
        ) { (oldCharge, newCharge), (oldSaving, newSaving) ->
            if (oldCharge == null && oldSaving == null) {
                // We don't act on init/first-subscribe
                return@combine
            }
            if (oldCharge == newCharge && oldSaving == newSaving) return@combine

            log(TAG) { "Global scheduler settings changed, recreating scheduled work." }
            state.first().schedules.forEach {
                if (it.isScheduled()) {
                    it.cancel()
                    it.schedule(reschedule = false)
                }
            }
        }.launchIn(appScope)
    }

    data class State(
        val schedules: Set<Schedule>,
    )

    private val Schedule.workName: String
        get() = "worker-schedule-$id"

    private suspend fun Schedule.isScheduled(): Boolean =
        workManager.getWorkInfosForUniqueWorkFlow(workName).firstOrNull()
            ?.any { infos -> infos.state == WorkInfo.State.ENQUEUED ||
                infos.state == WorkInfo.State.RUNNING || infos.state == WorkInfo.State.BLOCKED }
            ?: false

    private suspend fun Schedule.cancel() = workGate.withLock {
        workManager.cancelUniqueWork(workName).await()
        if (isScheduled()) log(TAG, WARN) { "Failed to cancel $this" }
    }

    /**
     * A user-requested cleanup runs in its own unique work chain, without replacing the
     * next automatic occurrence. KEEP prevents duplicate taps from starting extra cleanups.
     */
    suspend fun runNow(id: ScheduleId): Unit = workGate.withLock {
        val schedule = state.first().schedules.singleOrNull { it.id == id } ?: return@withLock
        // A manual tap while automatic cleanup is already RUNNING would create
        // an additional JobScheduler job and a second long CPU wake-up for the
        // same tools. The user may retry when automatic cleanup is idle.
        val running = workManager.getWorkInfosForUniqueWorkFlow(schedule.workName).first()
        if (QueuePolicy.automaticActive(running)) {
            log(TAG, INFO) { "Skipping manual run for $id: automatic cleanup is already running" }
            return@withLock
        }
        val request = OneTimeWorkRequestBuilder<SchedulerWorker>()
            .setInputData(workDataOf(
                SchedulerWorker.INPUT_SCHEDULE_ID to schedule.id,
                SchedulerWorker.INPUT_MANUAL to true,
            ))
            .build()
        workManager.enqueueUniqueWork(
            "worker-schedule-manual-$id",
            ExistingWorkPolicy.KEEP,
            request,
        ).await()
        Unit
    }

    suspend fun reschedule(id: ScheduleId) {
        log(TAG, INFO) { "Rescheduling $id" }
        // schedule() re-reads state under workGate: a concurrently disabled or
        // removed schedule must never be resurrected by a finishing worker.
        val schedule = getSchedule(id) ?: return
        if (schedule.isEnabled && !schedule.runOnce) schedule.schedule(reschedule = true)
    }

    private suspend fun Schedule.schedule(reschedule: Boolean) = workGate.withLock {
        // Revalidate while serialized: disabling/deleting a schedule can race with
        // a worker's finally block, and concurrent append calls otherwise produce
        // multiple ENQUEUED successors instead of one.
        val latest = state.first().schedules.singleOrNull { it.id == id }
        if (latest == null || !latest.isEnabled || latest != this) {
            log(TAG, WARN) { "Skipping stale schedule($label): enabled=${latest?.isEnabled}" }
            return@withLock
        }
        val pending = QueuePolicy.hasSuccessor(
            workManager.getWorkInfosForUniqueWorkFlow(workName).first()
        )
        if (pending) {
            log(TAG, INFO) { "Skipping duplicate schedule($label): a pending successor already exists" }
            return@withLock
        }
        log(TAG, INFO) { "schedule($label): (reschedule=$reschedule) $this" }

        val now = Instant.now()

        val executionEta = calcExecutionEta(now, reschedule)
            ?: throw IllegalArgumentException("Can't schedule 'unscheduled' Schedule...")

        val executionAt = now.plus(executionEta)
        val executionAtZoned = executionAt.toSystemTimezone()
        log(TAG) { "schedule($label): Next execution in $executionEta @ $executionAt ($executionAtZoned)" }

        val workRequest = OneTimeWorkRequestBuilder<SchedulerWorker>().apply {
            addTag("Label: $label")

            // https://issuetracker.google.com/issues?q=setInitialDelay%20
            // Hmm https://issuetracker.google.com/issues/155936231
            setInitialDelay(executionEta)

            setConstraints(
                Constraints.Builder().apply {
                    val noLowBattery = settings.skipWhenPowerSaving.value()
                    setRequiresBatteryNotLow(noLowBattery)
                    val onlyWhenCharging = settings.skipWhenNotCharging.value()
                    setRequiresCharging(onlyWhenCharging)
                }.build()
            )

            setInputData(workDataOf(SchedulerWorker.INPUT_SCHEDULE_ID to id))
        }.build()

        workManager.enqueueUniqueWork(
            workName,
            if (reschedule) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE,
            workRequest
        ).await()

        if (isScheduled()) {
            log(TAG, INFO) { "schedule($label): Scheduled for $executionAt" }
        } else {
            log(TAG, WARN) { "schedule($label): Failed to schedule" }
        }
    }

    private suspend fun Schedule.checkSchedulingState() {
        val isScheduled = isScheduled()

        if (isEnabled && !isScheduled) {
            log(TAG, INFO) { "checkSchedulingState($id): Enabled, but not scheduled. Scheduling $this" }
            schedule(reschedule = false)
        } else if (!isEnabled && isScheduled) {
            log(TAG, INFO) { "checkSchedulingState($id): Disabled, but scheduled. Canceling $this" }
            cancel()
        } else {
            log(TAG) { "checkSchedulingState($id): State is correct (isScheduled=$isScheduled) for $this" }
        }
    }

    suspend fun getSchedule(scheduleId: ScheduleId): Schedule? {
        return state.first().schedules.singleOrNull { it.id == scheduleId }
    }

    suspend fun saveSchedule(schedule: Schedule) = internalState.updateBlocking {
        log(TAG) { "saveSchedule(): $schedule" }
        val newSchedules = this.schedules.filter { it.id != schedule.id }.plus(schedule).toSet()
        copy(schedules = newSchedules)
    }

    /**
     * Apply a restored set atomically. The *single* state reconciler above
     * performs all WorkManager mutations. Previously this method also did
     * explicit cancel/enqueue work, so one change raced with its own collector
     * and repeatedly cancelled the newly enqueued successor.
     */
    suspend fun setSchedules(schedules: Set<Schedule>) {
        log(TAG, INFO) { "setSchedules(): ${schedules.size} schedules" }
        internalState.updateBlocking { copy(schedules = schedules) }
    }

    suspend fun removeSchedule(scheduleId: ScheduleId) = internalState.updateBlocking {
        log(TAG) { "removeSchedule(): $scheduleId" }

        val target = this.schedules.singleOrNull { it.id == scheduleId }
        if (target == null) {
            log(TAG, ERROR) { "Can't find $scheduleId" }
            return@updateBlocking this
        }

        // The state observer cancels the removed unique work exactly once.
        copy(schedules = this.schedules.minus(target).toSet())
    }

    suspend fun updateExecutedNow(id: ScheduleId, manual: Boolean = false) = internalState.updateBlocking {
        log(TAG) { "updateExecutedNow(): $id" }
        val target = this.schedules.singleOrNull { it.id == id }
        if (target == null) {
            log(TAG, ERROR) { "Can't find $id" }
            return@updateBlocking this
        }

        val updatedTarget = target.afterExecution(at = Instant.now(), manual = manual)

        copy(schedules = this.schedules.minus(target).plus(updatedTarget).toSet())
    }

    companion object {
        internal val TAG = logTag("Scheduler", "Manager")
    }
}