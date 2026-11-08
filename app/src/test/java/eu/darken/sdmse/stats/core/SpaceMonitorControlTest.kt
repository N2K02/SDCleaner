package eu.darken.sdmse.stats.core

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.Operation
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import eu.darken.sdmse.common.BuildConfigWrap
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import javax.inject.Provider

class SpaceMonitorControlTest : BaseTest() {

    private val workName = "${BuildConfigWrap.APPLICATION_ID}.stats.space-monitor"

    private val workManager = mockk<WorkManager>().apply {
        every {
            enqueueUniquePeriodicWork(any<String>(), any<ExistingPeriodicWorkPolicy>(), any<PeriodicWorkRequest>())
        } returns mockk<Operation>()
    }
    private val workManagerProvider = mockk<Provider<WorkManager>>().apply {
        every { get() } returns workManager
    }
    private val uncaught = mutableListOf<Throwable>()

    // Shaped like AppCoroutineScope (SupervisorJob + dispatcher), but driven by the test scheduler.
    private fun TestScope.appScope() = CoroutineScope(
        SupervisorJob() + StandardTestDispatcher(testScheduler) + CoroutineExceptionHandler { _, e -> uncaught.add(e) },
    )

    private fun create(appScope: CoroutineScope) = SpaceMonitorControl(
        appScope = appScope,
        workManager = workManagerProvider,
    )

    private fun verifyEnqueuedOnce() {
        verify(exactly = 1) {
            workManager.enqueueUniquePeriodicWork(workName, ExistingPeriodicWorkPolicy.KEEP, any<PeriodicWorkRequest>())
        }
    }

    @Test fun `constructing does not resolve WorkManager`() = runTest2 {
        val scope = appScope()
        create(scope)

        verify(exactly = 0) { workManagerProvider.get() }

        scope.cancel()
    }

    @Test fun `start schedules inside the app scope, not on the caller`() = runTest2 {
        val scope = appScope()
        val control = create(scope)

        control.start()
        verify(exactly = 0) { workManagerProvider.get() }

        runCurrent()

        verify(exactly = 1) { workManagerProvider.get() }
        verifyEnqueuedOnce()

        scope.cancel()
    }

    @Test fun `repeated start schedules once and never reads storage immediately`() = runTest2 {
        val scope = appScope()
        val control = create(scope)

        control.start()
        control.start()
        runCurrent()

        verifyEnqueuedOnce()
        scope.cancel()
    }

    @Test fun `cancelled scope never enqueues future startup work`() = runTest2 {
        val scope = appScope()
        val control = create(scope)
        scope.cancel()

        control.start()
        runCurrent()

        verify(exactly = 0) {
            workManager.enqueueUniquePeriodicWork(any<String>(), any<ExistingPeriodicWorkPolicy>(), any<PeriodicWorkRequest>())
        }
    }
}
