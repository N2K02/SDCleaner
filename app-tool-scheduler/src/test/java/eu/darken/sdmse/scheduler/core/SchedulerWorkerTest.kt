package eu.darken.sdmse.scheduler.core

import android.content.Context
import androidx.work.ForegroundInfo
import androidx.work.ForegroundUpdater
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.common.util.concurrent.ListenableFuture
import eu.darken.sdmse.common.adb.AdbManager
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.shell.ShellOps
import eu.darken.sdmse.main.core.taskmanager.SchedulerTaskFactory
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import eu.darken.sdmse.setup.SetupHealerSource
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import eu.darken.sdmse.main.core.SDMTool
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

class SchedulerWorkerTest : BaseTest() {

    private val scheduleId: ScheduleId = "test-schedule"
    private val schedule = Schedule(id = scheduleId)

    private val context = mockk<Context>(relaxed = true)
    private val params = mockk<WorkerParameters>(relaxed = true)
    private val taskSubmitter = mockk<TaskSubmitter>(relaxed = true)
    private val schedulerTaskFactory = mockk<SchedulerTaskFactory>()
    private val schedulerManager = mockk<SchedulerManager>()
    private val schedulerNotifications = mockk<SchedulerNotifications>(relaxed = true)
    private val setupHealer = mockk<SetupHealerSource>()
    private val rootManager = mockk<RootManager>(relaxed = true)
    private val adbManager = mockk<AdbManager>(relaxed = true)
    private val shellOps = mockk<ShellOps>(relaxed = true)
    private val runHistory = mockk<SchedulerRunHistory>(relaxed = true)

    private fun <T> immediateFuture(value: T): ListenableFuture<T> = object : ListenableFuture<T> {
        override fun addListener(listener: Runnable, executor: Executor) = executor.execute(listener)
        override fun cancel(mayInterruptIfRunning: Boolean): Boolean = false
        override fun isCancelled(): Boolean = false
        override fun isDone(): Boolean = true
        override fun get(): T = value
        override fun get(timeout: Long, unit: TimeUnit): T = value
    }

    @BeforeEach
    fun setup() {
        every { params.id } returns UUID.randomUUID()
        every { params.inputData } returns workDataOf(SchedulerWorker.INPUT_SCHEDULE_ID to scheduleId)
        every { params.runAttemptCount } returns 0
        every { params.foregroundUpdater } returns object : ForegroundUpdater {
            override fun setForegroundAsync(
                context: Context,
                id: UUID,
                foregroundInfo: ForegroundInfo,
            ): ListenableFuture<Void?> = immediateFuture(null)
        }

        coEvery { schedulerManager.getSchedule(scheduleId) } returns schedule
        coEvery { schedulerManager.updateExecutedNow(scheduleId) } returns mockk()
        coEvery { schedulerManager.reschedule(scheduleId) } returns Unit
        coEvery { schedulerTaskFactory.createTasks(scheduleId, emptySet()) } returns emptyList()
        every { setupHealer.state } returns flowOf(SetupHealerSource.State(healAttemptCount = 1))
    }

    private fun createWorker() = SchedulerWorker(
        context = context,
        params = params,
        dispatcherProvider = TestDispatcherProvider(),
        taskSubmitter = taskSubmitter,
        schedulerTaskFactory = schedulerTaskFactory,
        schedulerManager = schedulerManager,
        schedulerNotifications = schedulerNotifications,
        setupHealer = setupHealer,
        rootManager = rootManager,
        adbManager = adbManager,
        shellOps = shellOps,
        runHistory = runHistory,
    )

    @Test
    fun `scheduler idle cycle waits for task completion and only then schedules the next run`() = runTest {
        val scheduled = Schedule(id = scheduleId, useAppCleaner = true)
        val task = mockk<SDMTool.Task> {
            every { type } returns SDMTool.Type.APPCLEANER
        }
        val outcome = mockk<SDMTool.Task.Result> {
            every { type } returns SDMTool.Type.APPCLEANER
        }
        val inFlight = CompletableDeferred<SDMTool.Task.Result>()
        coEvery { schedulerManager.getSchedule(scheduleId) } returns scheduled
        coEvery { schedulerTaskFactory.createTasks(scheduleId, setOf(SDMTool.Type.APPCLEANER)) } returns listOf(task)
        coEvery {
            taskSubmitter.submit(task, notifyOnFinish = false, cancelOnCallerCancellation = true)
        } coAnswers { inFlight.await() }

        val running = async { createWorker().doWork() }
        advanceTimeBy(1_500)
        runCurrent()
        coVerify(exactly = 0) { schedulerManager.reschedule(any()) }
        coVerify(exactly = 1) {
            taskSubmitter.submit(task, notifyOnFinish = false, cancelOnCallerCancellation = true)
        }

        inFlight.complete(outcome)
        running.await().shouldBeInstanceOf<ListenableWorker.Result.Success>()
        coVerify(exactly = 1) { schedulerManager.reschedule(scheduleId) }
        coVerify(exactly = 1) { runHistory.record(any(), any(), any(), RunOutcome.SUCCESS, any(), any(), false) }
        coVerify(exactly = 0) { schedulerNotifications.notifyError(any()) }
        coVerify(exactly = 0) { schedulerNotifications.notifyResult(any(), any()) }
    }

    @Test
    fun `successful execution returns success and re-arms the schedule`() = runTest {
        val result = createWorker().doWork()

        result.shouldBeInstanceOf<ListenableWorker.Result.Success>()
        coVerify { schedulerManager.reschedule(scheduleId) }
    }

    @Test
    fun `retry attempt skips execution but must not fail the appended successor`() = runTest {
        // A killed 3AM run comes back as a retry; returning failure would cancel the
        // next occurrence appended in the finally block and permanently stop the schedule.
        every { params.runAttemptCount } returns 1

        val result = createWorker().doWork()

        result.shouldBeInstanceOf<ListenableWorker.Result.Success>()
        coVerify(exactly = 0) { schedulerTaskFactory.createTasks(any(), any()) }
        coVerify { schedulerManager.reschedule(scheduleId) }
    }

    @Test
    fun `execution error is quiet and must not fail the appended successor`() = runTest {
        coEvery { schedulerTaskFactory.createTasks(scheduleId, emptySet()) } throws RuntimeException("factory broken")

        val result = createWorker().doWork()

        result.shouldBeInstanceOf<ListenableWorker.Result.Success>()
        coVerify(exactly = 0) { schedulerNotifications.notifyError(any()) }
        coVerify { schedulerManager.reschedule(scheduleId) }
    }

    @Test
    fun `notification channel failure must not block scheduler success`() = runTest {
        coEvery { schedulerTaskFactory.createTasks(scheduleId, emptySet()) } throws RuntimeException("factory broken")
        every { schedulerNotifications.notifyError(scheduleId) } throws RuntimeException("notifications broken")

        val result = createWorker().doWork()

        result.shouldBeInstanceOf<ListenableWorker.Result.Success>()
        coVerify { schedulerManager.reschedule(scheduleId) }
    }

    @Test
    fun `failing to update the execution timestamp does not prevent rescheduling`() = runTest {
        coEvery { schedulerManager.updateExecutedNow(scheduleId) } throws RuntimeException("storage broken")

        val result = createWorker().doWork()

        result.shouldBeInstanceOf<ListenableWorker.Result.Success>()
        coVerify { schedulerManager.reschedule(scheduleId) }
    }

    @Test
    fun `failing notification cleanup does not prevent rescheduling`() = runTest {
        every { schedulerNotifications.cancel(scheduleId) } throws RuntimeException("notifications broken")

        val result = createWorker().doWork()

        result.shouldBeInstanceOf<ListenableWorker.Result.Success>()
        coVerify { schedulerManager.reschedule(scheduleId) }
    }

    @Test
    fun `manual cleanup records execution without rescheduling`() = runTest {
        every { params.inputData } returns workDataOf(
            SchedulerWorker.INPUT_SCHEDULE_ID to scheduleId,
            SchedulerWorker.INPUT_MANUAL to true,
        )
        coEvery { schedulerManager.updateExecutedNow(scheduleId, manual = true) } returns mockk()

        val result = createWorker().doWork()

        result.shouldBeInstanceOf<ListenableWorker.Result.Success>()
        coVerify(exactly = 1) { schedulerManager.updateExecutedNow(scheduleId, manual = true) }
        coVerify(exactly = 0) { schedulerManager.reschedule(any()) }
    }

    @Test
    fun `cancellation does not re-arm the schedule`() = runTest {
        // e.g. the user disabled the schedule, which cancels the running worker
        coEvery { schedulerManager.updateExecutedNow(scheduleId) } throws CancellationException("cancelled")

        shouldThrow<CancellationException> {
            createWorker().doWork()
        }

        coVerify(exactly = 0) { schedulerManager.reschedule(any()) }
    }

    @Test
    fun `setup healer never completing does not hold background worker indefinitely`() = runTest {
        every { setupHealer.state } returns flowOf(SetupHealerSource.State(healAttemptCount = 0))
        val result = createWorker().doWork()
        result.shouldBeInstanceOf<ListenableWorker.Result.Success>()
        coVerify { schedulerManager.reschedule(scheduleId) }
        coVerify { runHistory.record(any(), any(), any(), RunOutcome.WARNING, any(), any(), any()) }
    }

}
