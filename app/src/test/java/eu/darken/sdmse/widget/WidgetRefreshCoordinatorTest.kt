package eu.darken.sdmse.widget

import io.mockk.coVerify
import io.mockk.every
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2

class WidgetRefreshCoordinatorTest : BaseTest() {

    private val widgetUpdater = mockk<WidgetUpdater>(relaxed = true)
    private val widgetDataProvider = mockk<WidgetDataProvider>()

    private val renderState = MutableStateFlow<WidgetRenderState>(
        WidgetRenderState.Data(storages = emptyList(), freedBytes = 0L)
    )

    init {
        coEvery { widgetUpdater.hasWidgets() } returns true
        every { widgetDataProvider.renderState } returns renderState
    }

    private fun data(freed: Long = 0L, working: Boolean = false, cancellable: Boolean = false) =
        WidgetRenderState.Data(
            storages = emptyList(),
            freedBytes = freed,
            isWorking = working,
            isCancellable = working && cancellable,
        )

    private fun coordinator(scope: CoroutineScope) =
        WidgetRefreshCoordinator(scope, widgetDataProvider, widgetUpdater)

    @Test
    fun `refreshes once at start then on each distinct render state change`() = runTest2 {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val coordinator = coordinator(scope)

        coordinator.start()
        advanceUntilIdle()
        // Initial replay = the app-start refresh that re-bakes widgets after app updates.
        coVerify(exactly = 1) { widgetUpdater.updateAll() }

        renderState.value = data(freed = 1000L)
        advanceUntilIdle()
        coVerify(exactly = 2) { widgetUpdater.updateAll() }

        renderState.value = data(freed = 1000L) // unchanged → distinctUntilChanged suppresses
        advanceUntilIdle()
        coVerify(exactly = 2) { widgetUpdater.updateAll() }

        scope.cancel()
    }

    @Test
    fun `refreshes across the working-cancel cycle`() = runTest2 {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val coordinator = coordinator(scope)

        coordinator.start()
        advanceUntilIdle()
        coVerify(exactly = 1) { widgetUpdater.updateAll() }

        renderState.value = data(working = true, cancellable = true) // Clean → Cancel
        advanceUntilIdle()
        coVerify(exactly = 2) { widgetUpdater.updateAll() }

        renderState.value = data(working = true, cancellable = false) // Cancel tapped → "Working…"
        advanceUntilIdle()
        coVerify(exactly = 3) { widgetUpdater.updateAll() }

        renderState.value = data() // done → Clean
        advanceUntilIdle()
        coVerify(exactly = 4) { widgetUpdater.updateAll() }

        scope.cancel()
    }

    @Test
    fun `publishes the generated picker preview once at start`() = runTest2 {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val coordinator = coordinator(scope)

        coordinator.start()
        coordinator.start()
        advanceUntilIdle()

        coVerify(exactly = 1) { widgetUpdater.publishPreviews() }

        scope.cancel()
    }

    @Test
    fun `no widget does not subscribe or refresh while the app is idle`() = runTest2 {
        coEvery { widgetUpdater.hasWidgets() } returns false
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val coordinator = coordinator(scope)

        coordinator.start()
        advanceUntilIdle()
        coVerify(exactly = 0) { widgetUpdater.updateAll() }

        renderState.value = data(freed = 42)
        advanceUntilIdle()
        coVerify(exactly = 0) { widgetUpdater.updateAll() }
        scope.cancel()
    }

    @Test
    fun `installing and removing a widget starts and stops refreshes`() = runTest2 {
        coEvery { widgetUpdater.hasWidgets() } returns false
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val coordinator = coordinator(scope)
        coordinator.start()
        advanceUntilIdle()

        coEvery { widgetUpdater.hasWidgets() } returns true
        coordinator.onWidgetsChanged()
        advanceUntilIdle()
        coVerify(exactly = 1) { widgetUpdater.updateAll() }

        coEvery { widgetUpdater.hasWidgets() } returns false
        coordinator.onWidgetsChanged()
        advanceUntilIdle()
        renderState.value = data(freed = 24)
        advanceUntilIdle()
        coVerify(exactly = 1) { widgetUpdater.updateAll() }

        coEvery { widgetUpdater.hasWidgets() } returns true
        coordinator.onWidgetsChanged()
        advanceUntilIdle()
        coVerify(exactly = 2) { widgetUpdater.updateAll() }
        scope.cancel()
    }

    @Test
    fun `cancelling the app scope terminates widget refreshes`() = runTest2 {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val coordinator = coordinator(scope)
        coordinator.start()
        advanceUntilIdle()
        scope.cancel()
        renderState.value = data(freed = 99)
        advanceUntilIdle()
        coVerify(exactly = 1) { widgetUpdater.updateAll() }
    }

    @Test
    fun `start is idempotent`() = runTest2 {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val coordinator = coordinator(scope)

        coordinator.start()
        coordinator.start()
        advanceUntilIdle()
        coVerify(exactly = 1) { widgetUpdater.updateAll() }

        scope.cancel()
    }
}
