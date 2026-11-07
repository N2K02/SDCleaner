package eu.darken.sdmse.cleaner

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CleanerShutdownTest {
    @Test fun `quit lets cancelled tasks drain before closing recording and host`() = runTest {
        val drained = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        var result: Boolean? = null
        val job = launch {
            result = stopCleaner({ events += "background" }, { events += "cancel" }, { drained.await() },
                { events += "recording" }, { events += "host" })
        }
        runCurrent()
        assertEquals(listOf("background", "cancel"), events)
        assertFalse(job.isCompleted)
        drained.complete(Unit)
        job.join()
        assertEquals(listOf("background", "cancel", "recording", "host"), events)
        assertEquals(true, result)
    }

    @Test fun `stalled cleanup never blocks quit - host is closed after the drain window`() = runTest {
        var closed = false
        var recorded = false
        var result: Boolean? = null
        val job = launch {
            result = stopCleaner({}, {}, { CompletableDeferred<Unit>().await() },
                { recorded = true }, { closed = true })
        }
        advanceTimeBy(QUIT_DRAIN_TIMEOUT_MS - 1)
        runCurrent()
        assertFalse(closed)
        advanceTimeBy(2)
        job.join()
        assertTrue(closed)
        assertTrue(recorded)
        assertEquals(false, result)
    }

    @Test fun `failing cancel steps still close the host`() = runTest {
        var closed = false
        stopCleaner({ error("wm") }, { error("tm") }, {}, { error("rec") }, { closed = true })
        assertTrue(closed)
    }

    @Test fun `hung host close cannot block quit forever`() = runTest {
        val result = stopCleaner({}, {}, {}, {}, { CompletableDeferred<Unit>().await() })
        assertTrue(result)
    }
}
