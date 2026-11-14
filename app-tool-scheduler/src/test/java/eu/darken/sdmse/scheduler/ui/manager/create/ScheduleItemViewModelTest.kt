package eu.darken.sdmse.scheduler.ui.manager.create

import eu.darken.sdmse.common.navigation.NavEvent
import eu.darken.sdmse.scheduler.core.Schedule
import eu.darken.sdmse.scheduler.core.SchedulerManager
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import java.time.Duration

class ScheduleItemViewModelTest : BaseTest() {

    private val existingSchedule = Schedule(
        id = "schedule-1",
        label = "Daily clean",
        hour = 8,
        minute = 30,
    )

    private data class Harness(
        val vm: ScheduleItemViewModel,
        val manager: SchedulerManager,
        val stateFlow: MutableStateFlow<SchedulerManager.State>,
    )

    private fun harness(initial: Set<Schedule>): Harness {
        val stateFlow = MutableStateFlow(SchedulerManager.State(schedules = initial))
        val manager = mockk<SchedulerManager>(relaxed = true).also { mock ->
            coEvery { mock.state } returns stateFlow
        }
        val vm = ScheduleItemViewModel(
            dispatcherProvider = TestDispatcherProvider(),
            schedulerManager = manager,
        )
        return Harness(vm = vm, manager = manager, stateFlow = stateFlow)
    }

    @Test
    fun `save refuses to resurrect a deleted schedule`() = runTest2 {
        val (vm, manager, stateFlow) = harness(initial = setOf(existingSchedule))
        vm.setScheduleId(existingSchedule.id)
        advanceUntilIdle()

        // Schedule was deleted between sheet open and save.
        stateFlow.value = SchedulerManager.State(schedules = emptySet())

        vm.saveSchedule()
        advanceUntilIdle()

        coVerify(exactly = 0) { manager.saveSchedule(any()) }
        vm.navEvents.first().shouldBeInstanceOf<NavEvent.Up>()
    }

    @Test
    fun `save refuses to mutate an externally-enabled schedule`() = runTest2 {
        val (vm, manager, stateFlow) = harness(initial = setOf(existingSchedule))
        vm.setScheduleId(existingSchedule.id)
        advanceUntilIdle()

        // Schedule became enabled (scheduledAt non-null) between sheet open and save.
        stateFlow.value = SchedulerManager.State(
            schedules = setOf(
                existingSchedule.copy(scheduledAt = java.time.Instant.parse("2026-04-26T00:00:00Z")),
            ),
        )

        vm.saveSchedule()
        advanceUntilIdle()

        coVerify(exactly = 0) { manager.saveSchedule(any()) }
        vm.navEvents.first().shouldBeInstanceOf<NavEvent.Up>()
    }

    @Test
    fun `save creates a new schedule when initial id was unknown`() = runTest2 {
        val (vm, manager, _) = harness(initial = emptySet())
        vm.setScheduleId("new-schedule")
        advanceUntilIdle()

        vm.updateLabel("My new schedule")
        vm.updateTime(hour = 14, minute = 15)
        advanceUntilIdle()

        vm.saveSchedule()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            manager.saveSchedule(match {
                it.id == "new-schedule" && it.label == "My new schedule" && it.hour == 14 && it.minute == 15
            })
        }
        vm.navEvents.first().shouldBeInstanceOf<NavEvent.Up>()
    }

    @Test
    fun `canSave is false for blank labels`() = runTest2 {
        val (vm, _, _) = harness(initial = emptySet())
        vm.setScheduleId("brand-new")
        advanceUntilIdle()
        vm.updateLabel("   ")
        vm.updateTime(hour = 9, minute = 0)
        advanceUntilIdle()
        vm.state.first().canSave shouldBe false
    }

    @Test
    fun `canSave is true when label hour and minute are non-blank`() = runTest2 {
        val (vm, _, _) = harness(initial = emptySet())
        vm.setScheduleId("brand-new")
        advanceUntilIdle()
        vm.updateLabel("Tidy")
        vm.updateTime(hour = 9, minute = 0)
        advanceUntilIdle()
        vm.state.first().canSave shouldBe true
    }

    @Test
    fun `switching from run once to repeating restores a valid interval`() = runTest2 {
        val (vm, _, _) = harness(initial = emptySet())
        vm.setScheduleId("new")
        advanceUntilIdle()

        vm.setRunOnceMode()
        advanceUntilIdle()
        vm.state.first().runOnce shouldBe true
        vm.state.first().intervalMinutes shouldBe 1L

        vm.setIntervalMode(true)
        advanceUntilIdle()
        vm.state.first().runOnce shouldBe false
        vm.state.first().intervalMinutes shouldBe 60L
    }

    @Test
    fun `repeating intervals clamp below fifteen minutes`() = runTest2 {
        val (vm, _, _) = harness(initial = emptySet())
        vm.setScheduleId("new")
        advanceUntilIdle()
        vm.setIntervalMode(true)
        vm.setIntervalMinutes(1)
        advanceUntilIdle()
        vm.state.first().intervalMinutes shouldBe 15L
    }

    @Test
    fun `saving a delayed one time cleanup preserves its mode`() = runTest2 {
        val (vm, manager, _) = harness(initial = emptySet())
        vm.setScheduleId("new")
        advanceUntilIdle()
        vm.updateLabel("One-off")
        vm.setRunOnceMode()
        vm.setInitialDelayMinutes(5)
        advanceUntilIdle()

        vm.state.first().canSave shouldBe true
        vm.saveSchedule()
        advanceUntilIdle()
        coVerify(exactly = 1) {
            manager.saveSchedule(match {
                it.id == "new" && it.runOnce && it.intervalMinutes == 1L && it.initialDelayMinutes == 5L
            })
        }
    }

    @Test
    fun `new editor has useful interval defaults but stays disabled until user enables it`() = runTest2 {
        val (vm, _, _) = harness(initial = emptySet())
        vm.setScheduleId("new")
        advanceUntilIdle()

        val state = vm.state.first()
        state.label shouldBe "Scheduled cleanup"
        state.intervalMinutes shouldBe 360L
        state.initialDelayMinutes shouldBe 360L
        state.useAppCleaner shouldBe true
        state.isEnabled shouldBe false
        state.canSave shouldBe true
    }

    @Test
    fun `enabling a schedule persists a real worker anchor and selected cleaners`() = runTest2 {
        val (vm, manager, _) = harness(initial = emptySet())
        vm.setScheduleId("new")
        advanceUntilIdle()
        vm.setEnabled(true)
        advanceUntilIdle()

        vm.saveSchedule()
        advanceUntilIdle()
        coVerify(exactly = 1) {
            manager.saveSchedule(match { it.id == "new" && it.isEnabled && it.useAppCleaner &&
                it.intervalMinutes == 360L && it.initialDelayMinutes == 360L })
        }
    }

    @Test
    fun `enabled schedule without cleaning tools cannot be saved`() = runTest2 {
        val (vm, manager, _) = harness(initial = emptySet())
        vm.setScheduleId("new")
        advanceUntilIdle()
        vm.setAppCleanerEnabled(false)
        vm.setEnabled(true)
        advanceUntilIdle()

        vm.state.first().canSave shouldBe false
        vm.saveSchedule()
        advanceUntilIdle()
        coVerify(exactly = 0) { manager.saveSchedule(any()) }
    }

    @Test
    fun `editing an already enabled schedule keeps it enabled and updates its interval`() = runTest2 {
        val enabled = existingSchedule.copy(
            scheduledAt = java.time.Instant.parse("2026-01-01T00:00:00Z"),
            intervalMinutes = 30,
            useSystemCleaner = true,
        )
        val (vm, manager, _) = harness(initial = setOf(enabled))
        vm.setScheduleId(enabled.id)
        advanceUntilIdle()
        vm.setIntervalMinutes(60)
        advanceUntilIdle()
        vm.saveSchedule()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            manager.saveSchedule(match { it.id == enabled.id && it.isEnabled &&
                it.intervalMinutes == 60L && it.scheduledAt != enabled.scheduledAt && it.useSystemCleaner })
        }
    }

    @Test
    fun `invalid partial minute entry blocks saving`() {
        val repeating = ScheduleItemViewModel.State(intervalMinutes = 60, initialDelayMinutes = 0)
        areMinuteFieldsValid(repeating, intervalText = "1", delayText = "0") shouldBe false
        areMinuteFieldsValid(repeating, intervalText = "", delayText = "0") shouldBe false
        areMinuteFieldsValid(repeating, intervalText = "15", delayText = "0") shouldBe true

        val delayed = repeating.copy(initialDelayMinutes = 5)
        areMinuteFieldsValid(delayed, intervalText = "60", delayText = "") shouldBe false
        areMinuteFieldsValid(delayed, intervalText = "60", delayText = "5") shouldBe true

        val oneTime = repeating.copy(intervalMinutes = 1, runOnce = true, initialDelayMinutes = 5)
        areMinuteFieldsValid(oneTime, intervalText = "1", delayText = "") shouldBe false
        areMinuteFieldsValid(oneTime, intervalText = "1", delayText = "0") shouldBe false
        areMinuteFieldsValid(oneTime, intervalText = "1", delayText = "5") shouldBe true

        val clockTime = repeating.copy(intervalMinutes = null)
        areMinuteFieldsValid(clockTime, intervalText = "", delayText = "") shouldBe true
    }

    @Test
    fun `setRepeatDays applies value in normal range`() = runTest2 {
        val (vm, _, _) = harness(initial = emptySet())
        vm.setScheduleId("brand-new")
        advanceUntilIdle()
        vm.setRepeatDays(7)
        advanceUntilIdle()
        vm.state.first().repeatInterval shouldBe Duration.ofDays(7)
    }

    @Test
    fun `setRepeatDays clamps below 1 to 1`() = runTest2 {
        val (vm, _, _) = harness(initial = emptySet())
        vm.setScheduleId("brand-new")
        advanceUntilIdle()
        vm.setRepeatDays(0)
        advanceUntilIdle()
        vm.state.first().repeatInterval shouldBe Duration.ofDays(1)
    }

    @Test
    fun `setRepeatDays clamps above 21 to 21`() = runTest2 {
        val (vm, _, _) = harness(initial = emptySet())
        vm.setScheduleId("brand-new")
        advanceUntilIdle()
        vm.setRepeatDays(99)
        advanceUntilIdle()
        vm.state.first().repeatInterval shouldBe Duration.ofDays(21)
    }
    @Test
    fun `renaming enabled schedule preserves pending run and commands`() = runTest2 {
        val enabled = existingSchedule.copy(
            scheduledAt = java.time.Instant.parse("2026-01-01T00:00:00Z"),
            intervalMinutes = 240,
            useSystemCleaner = true,
            commandsAfterSchedule = listOf("echo cleanup"),
        )
        val (vm, manager, _) = harness(initial = setOf(enabled))
        vm.setScheduleId(enabled.id)
        advanceUntilIdle()
        vm.updateLabel("Evening cleanup")
        vm.saveSchedule()
        advanceUntilIdle()
        coVerify(exactly = 1) {
            manager.saveSchedule(match {
                it.label == "Evening cleanup" && it.isEnabled &&
                    it.scheduledAt == enabled.scheduledAt &&
                    it.commandsAfterSchedule == enabled.commandsAfterSchedule
            })
        }
    }

}
