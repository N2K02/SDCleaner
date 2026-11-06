package eu.darken.sdmse.scheduler.ui.manager

import android.content.Context
import eu.darken.sdmse.common.BatteryHelper
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.adb.AdbManager
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.shell.ShellOps
import eu.darken.sdmse.scheduler.core.Schedule
import eu.darken.sdmse.scheduler.core.SchedulerManager
import eu.darken.sdmse.scheduler.core.SchedulerRunHistory
import eu.darken.sdmse.scheduler.core.SchedulerSettings
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import java.time.Instant
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import testhelpers.mockDataStoreValue

class SchedulerManagerViewModelTest : BaseTest() {

    private class Harness(
        val vm: SchedulerManagerViewModel,
        val useRoot: MutableStateFlow<Boolean>,
        val useAdb: MutableStateFlow<Boolean>,
        val schedules: MutableStateFlow<SchedulerManager.State>,
        val workStatus: MutableStateFlow<List<SchedulerManager.WorkSnapshot>>,
        val taskStatus: MutableStateFlow<TaskSubmitter.State>,
    )

    private fun TestScope.harness(
        useRoot: Boolean = false,
        useAdb: Boolean = false,
    ): Harness {
        val rootFlow = MutableStateFlow(useRoot)
        val adbFlow = MutableStateFlow(useAdb)
        // A seeded schedule keeps the init block's one-shot default-entry creation from firing and
        // mutating what the test is observing.
        val schedules = MutableStateFlow(
            SchedulerManager.State(schedules = setOf(Schedule(id = "test-schedule", label = "Test"))),
        )
        val workStatus = MutableStateFlow<List<SchedulerManager.WorkSnapshot>>(emptyList())
        val taskStatus = MutableStateFlow(TaskSubmitter.State())
        val schedulerManager = mockk<SchedulerManager>(relaxed = true).apply {
            every { state } returns schedules
            every { runtimeWork } returns workStatus
        }
        val settings = mockk<SchedulerSettings>().apply {
            every { createdDefaultEntry } returns mockDataStoreValue(true)
            every { hintBatteryDismissed } returns mockDataStoreValue(false)
        }
        val vm = SchedulerManagerViewModel(
            context = mockk<Context>(relaxed = true),
            dispatcherProvider = TestDispatcherProvider(),
            schedulerManager = schedulerManager,
            settings = settings,
            rootManager = mockk<RootManager>().apply { every { this@apply.useRoot } returns rootFlow },
            adbManager = mockk<AdbManager>().apply { every { this@apply.useAdb } returns adbFlow },
            batteryHelper = mockk<BatteryHelper>().apply {
                every { isIgnoringBatteryOptimizations } returns flowOf(false)
            },
            webpageTool = mockk<WebpageTool>(relaxed = true),
            shellOps = mockk<ShellOps>(relaxed = true),
            runHistory = mockk<SchedulerRunHistory>().apply {
                every { history } returns MutableStateFlow(emptyList())
            },
            taskSubmitter = mockk<TaskSubmitter>().apply {
                every { state } returns taskStatus
            },
        )
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { vm.state.collect { } }
        return Harness(vm, rootFlow, adbFlow, schedules, workStatus, taskStatus)
    }

    @Test
    fun `scheduler diagnostics reflect running work queued successors and held resource leases`() = runTest2 {
        val h = harness()
        h.workStatus.value = listOf(
            SchedulerManager.WorkSnapshot("test-schedule", "Test", running = 1, pending = 1, lastState = "RUNNING")
        )
        val pendingTask = TaskSubmitter.ManagedTask(
            id = "scheduled-task",
            toolType = SDMTool.Type.APPCLEANER,
            task = mockk(),
            notifyOnFinish = false,
            resourceHeld = true,
        )
        h.taskStatus.value = TaskSubmitter.State(listOf(pendingTask))
        advanceUntilIdle()
        h.vm.state.first().let { state ->
            state.work.single().running shouldBe 1
            state.work.single().pending shouldBe 1
            state.cleanupActive shouldBe 1
            state.cleanupLeases shouldBe 1
        }

        h.workStatus.value = listOf(
            SchedulerManager.WorkSnapshot("test-schedule", "Test", pending = 1, lastState = "ENQUEUED")
        )
        h.taskStatus.value = TaskSubmitter.State(
            listOf(pendingTask.copy(completedAt = Instant.now(), resourceHeld = false))
        )
        advanceUntilIdle()
        h.vm.state.first().let { state ->
            state.work.single().running shouldBe 0
            state.work.single().pending shouldBe 1
            state.cleanupActive shouldBe 0
            state.cleanupLeases shouldBe 0
        }
    }

    @Test
    fun `ADB becoming available reveals the commands section without a schedule change`() = runTest2 {
        val h = harness()
        advanceUntilIdle()
        h.vm.state.first().showCommands shouldBe false

        h.useAdb.value = true
        advanceUntilIdle()

        h.vm.state.first().showCommands shouldBe true
    }

    @Test
    fun `root becoming available reveals the commands section without a schedule change`() = runTest2 {
        val h = harness()
        advanceUntilIdle()
        h.vm.state.first().showCommands shouldBe false

        h.useRoot.value = true
        advanceUntilIdle()

        h.vm.state.first().showCommands shouldBe true
    }

    @Test
    fun `revoked ADB access hides the commands section again`() = runTest2 {
        val h = harness(useAdb = true)
        advanceUntilIdle()
        h.vm.state.first().showCommands shouldBe true

        h.useAdb.value = false
        advanceUntilIdle()

        h.vm.state.first().showCommands shouldBe false
    }

    @Test
    fun `revoked root access hides the commands section again`() = runTest2 {
        val h = harness(useRoot = true)
        advanceUntilIdle()
        h.vm.state.first().showCommands shouldBe true

        h.useRoot.value = false
        advanceUntilIdle()

        h.vm.state.first().showCommands shouldBe false
    }
}
