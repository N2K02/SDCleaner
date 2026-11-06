package eu.darken.sdmse.cleaner

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.TimeoutCancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CleanerShutdownTest {
    @Test fun `quit waits for task cleanup before closing recording and host`() = runTest {
        val drained = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val job = launch {
            stopCleaner({ events += "background" }, { events += "cancel" }, { drained.await() },
                { events += "recording" }, { events += "host" })
        }
        runCurrent()
        assertEquals(listOf("background", "cancel"), events)
        assertFalse(job.isCompleted)
        drained.complete(Unit)
        job.join()
        assertEquals(listOf("background", "cancel", "recording", "host"), events)
    }

    @Test fun `stalled cleanup stops recording but retains active cleaner host`() = runTest {
        var closed = false
        var recorded = false
        val error = runCatching {
            stopCleaner({}, {}, { CompletableDeferred<Unit>().await() }, { recorded = true }, { closed = true })
        }.exceptionOrNull()
        assertTrue(error is TimeoutCancellationException)
        assertFalse(closed)
        assertTrue(recorded)
    }
}
