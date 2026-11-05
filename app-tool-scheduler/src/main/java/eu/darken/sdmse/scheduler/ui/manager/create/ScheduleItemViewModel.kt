package eu.darken.sdmse.scheduler.ui.manager.create

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.scheduler.core.Schedule
import eu.darken.sdmse.scheduler.core.SchedulerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/**
 * Edit form for a single Schedule.
 *
 * Architecture: the form is an *editable draft*. We don't derive [state] from
 * [schedulerManager.state] directly — that would let an external schedule save while the user is
 * typing wipe their input. Instead we initialize [draft] once from the live schedule lookup and
 * never re-sync from upstream after that.
 *
 * `scheduleId` is bound by the host via [setScheduleId] (Picker-style entry-arg binding).
 * `SavedStateHandle.toRoute<>()` is unreliable for non-nullable serializable args under Nav3 — see
 * project memory `feedback_nav3_route_binding`.
 */
@HiltViewModel
class ScheduleItemViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val schedulerManager: SchedulerManager,
) : ViewModel4(dispatcherProvider, tag = TAG) {

    private val scheduleIdFlow = MutableStateFlow<String?>(null)
    private val draft = MutableStateFlow<FormDraft?>(null)
    private val isCreateMode = MutableStateFlow<Boolean?>(null)

    fun setScheduleId(id: String) {
        if (scheduleIdFlow.value == id) return
        scheduleIdFlow.value = id
        launch {
            // Wait for the id to land, then resolve initial form values from the live schedule.
            val resolvedId = scheduleIdFlow.filterNotNull().first()
            val initial = schedulerManager.state.first().schedules.firstOrNull { it.id == resolvedId }
            isCreateMode.value = (initial == null)
            draft.value = FormDraft(
                label = initial?.label?.takeIf { it.isNotBlank() } ?: "Scheduled cleanup",
                hour = initial?.hour ?: 22,
                minute = initial?.minute ?: 0,
                repeatInterval = initial?.repeatInterval ?: Duration.ofDays(3),
                intervalMinutes = initial?.intervalMinutes ?: if (initial == null) 60L else null,
                runOnce = initial?.runOnce ?: false,
                initialDelayMinutes = initial?.initialDelayMinutes ?: if (initial == null) 5L else 0L,
                isEnabled = initial?.isEnabled ?: false,
                initiallyEnabled = initial?.isEnabled ?: false,
                useAppCleaner = initial?.useAppCleaner ?: (initial == null),
                useSystemCleaner = initial?.useSystemCleaner ?: false,
                useCorpseFinder = initial?.useCorpseFinder ?: false,
            )
        }
    }

    val state: StateFlow<State> = draft
        .filterNotNull()
        .map { d ->
            State(
                label = d.label,
                hour = d.hour,
                minute = d.minute,
                repeatInterval = d.repeatInterval,
                intervalMinutes = d.intervalMinutes,
                runOnce = d.runOnce,
                initialDelayMinutes = d.initialDelayMinutes,
                isEnabled = d.isEnabled,
                useAppCleaner = d.useAppCleaner,
                useSystemCleaner = d.useSystemCleaner,
                useCorpseFinder = d.useCorpseFinder,
                canSave = !d.label.isNullOrBlank() &&
                    (d.intervalMinutes != null || (d.hour != null && d.minute != null)) &&
                    (!d.isEnabled || d.useAppCleaner || d.useSystemCleaner || d.useCorpseFinder),
                isReady = true,
            )
        }
        .safeStateIn(initialValue = State(), onError = { State(isReady = false) })

    fun setEnabled(enabled: Boolean) {
        draft.update { it?.copy(isEnabled = enabled) }
    }

    fun setAppCleanerEnabled(enabled: Boolean) {
        draft.update { it?.copy(useAppCleaner = enabled) }
    }

    fun setSystemCleanerEnabled(enabled: Boolean) {
        draft.update { it?.copy(useSystemCleaner = enabled) }
    }

    fun setCorpseFinderEnabled(enabled: Boolean) {
        draft.update { it?.copy(useCorpseFinder = enabled) }
    }

    fun setIntervalMode(enabled: Boolean) {
        draft.update {
            it?.copy(
                intervalMinutes = if (enabled) {
                    if (it.runOnce) 60L else (it.intervalMinutes ?: 60L).coerceAtLeast(15L)
                } else null,
                runOnce = false,
            )
        }
    }

    fun setRunOnceMode() {
        draft.update {
            it?.copy(intervalMinutes = 1L, runOnce = true, initialDelayMinutes = it.initialDelayMinutes.coerceAtLeast(1))
        }
    }

    fun setIntervalMinutes(minutes: Long) {
        draft.update { it?.copy(intervalMinutes = minutes.coerceIn(if (it.runOnce) 1L else 15L, 30L * 24L * 60L)) }
    }

    fun setInitialDelayMinutes(minutes: Long) {
        draft.update { it?.copy(initialDelayMinutes = minutes.coerceIn(if (it.runOnce) 1L else 0L, 30L * 24L * 60L)) }
    }

    fun updateLabel(label: String) {
        draft.update { current -> current?.copy(label = label.takeIf { it.isNotBlank() }) }
    }

    fun updateTime(hour: Int, minute: Int) {
        draft.update { current -> current?.copy(hour = hour, minute = minute) }
    }

    fun decreaseDays() {
        draft.update { current ->
            current?.copy(
                repeatInterval = current.repeatInterval.minusDays(1).coerceAtLeast(MIN_REPEAT),
            )
        }
    }

    fun increaseDays() {
        draft.update { current ->
            current?.copy(
                repeatInterval = current.repeatInterval.plusDays(1).coerceAtMost(MAX_REPEAT),
            )
        }
    }

    fun setRepeatDays(days: Int) {
        val clamped = days.coerceIn(MIN_REPEAT.toDays().toInt(), MAX_REPEAT.toDays().toInt())
        draft.update { current -> current?.copy(repeatInterval = Duration.ofDays(clamped.toLong())) }
    }

    fun saveSchedule() = launch {
        log(TAG) { "saveSchedule()" }
        val d = draft.value ?: return@launch
        if (d.label.isNullOrBlank() || (d.intervalMinutes == null && (d.hour == null || d.minute == null)) ||
            (d.isEnabled && !d.useAppCleaner && !d.useSystemCleaner && !d.useCorpseFinder)) {
            log(TAG, WARN) { "saveSchedule: incomplete draft, ignoring" }
            return@launch
        }
        val id = scheduleIdFlow.value ?: return@launch
        val isCreate = isCreateMode.value ?: return@launch
        val live = schedulerManager.state.first().schedules.firstOrNull { it.id == id }
        if (!isCreate && live == null) {
            // Edit was started for an existing schedule that has since been deleted. Abort silently
            // rather than resurrecting the schedule from a stale form.
            log(TAG, WARN) { "saveSchedule: schedule $id was deleted, aborting" }
            navUp()
            return@launch
        }
        if ((live != null && live.isEnabled != d.initiallyEnabled) || (isCreate && live != null)) {
            // External enable/disable or another editor created this ID: do not overwrite it.
            log(TAG, WARN) { "saveSchedule: schedule $id changed outside editor, aborting" }
            navUp()
            return@launch
        }
        val timingChanged = live == null || live.hour != d.hour || live.minute != d.minute ||
            live.intervalMinutes != d.intervalMinutes || live.runOnce != d.runOnce ||
            live.initialDelayMinutes != d.initialDelayMinutes || live.repeatInterval != d.repeatInterval
        val nextStart = when {
            !d.isEnabled -> null
            live?.isEnabled == true && !timingChanged -> live.scheduledAt
            else -> Instant.now()
        }
        val toEdit = live ?: Schedule(id = id)
        schedulerManager.saveSchedule(
            toEdit.copy(
                label = d.label,
                hour = d.hour ?: toEdit.hour,
                minute = d.minute ?: toEdit.minute,
                repeatInterval = d.repeatInterval,
                intervalMinutes = d.intervalMinutes,
                runOnce = d.runOnce,
                initialDelayMinutes = d.initialDelayMinutes,
                scheduledAt = nextStart,
                userZone = if (d.isEnabled) ZoneId.systemDefault().id else toEdit.userZone,
                useAppCleaner = d.useAppCleaner,
                useSystemCleaner = d.useSystemCleaner,
                useCorpseFinder = d.useCorpseFinder,
            ),
        )
        navUp()
    }

    private data class FormDraft(
        val label: String?,
        val hour: Int?,
        val minute: Int?,
        val repeatInterval: Duration,
        val intervalMinutes: Long?,
        val runOnce: Boolean,
        val initialDelayMinutes: Long,
        val isEnabled: Boolean,
        val initiallyEnabled: Boolean,
        val useAppCleaner: Boolean,
        val useSystemCleaner: Boolean,
        val useCorpseFinder: Boolean,
    )

    data class State(
        val label: String? = null,
        val hour: Int? = null,
        val minute: Int? = null,
        val repeatInterval: Duration = Duration.ofDays(3),
        val intervalMinutes: Long? = null,
        val runOnce: Boolean = false,
        val initialDelayMinutes: Long = 0,
        val isEnabled: Boolean = false,
        val useAppCleaner: Boolean = false,
        val useSystemCleaner: Boolean = false,
        val useCorpseFinder: Boolean = false,
        val canSave: Boolean = false,
        val isReady: Boolean = false,
    )

    companion object {
        private val TAG = logTag("Scheduler", "Schedule", "ViewModel")
        private val MIN_REPEAT: Duration = Duration.ofDays(1)
        private val MAX_REPEAT: Duration = Duration.ofDays(21)
    }
}
