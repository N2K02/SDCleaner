package eu.darken.sdmse.scheduler.core

import androidx.work.WorkInfo
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SchedulerQueuePolicyTest {
    private fun info(state: WorkInfo.State): WorkInfo = mockk {
        every { this@mockk.state } returns state
    }

    @Test fun `idle or completed chains can schedule exactly one successor`() {
        assertFalse(SchedulerManager.QueuePolicy.hasSuccessor(emptyList()))
        assertFalse(SchedulerManager.QueuePolicy.hasSuccessor(
            listOf(info(WorkInfo.State.SUCCEEDED), info(WorkInfo.State.RUNNING))
        ))
    }

    @Test fun `existing enqueued or blocked successor prevents duplicate append`() {
        assertTrue(SchedulerManager.QueuePolicy.hasSuccessor(listOf(info(WorkInfo.State.ENQUEUED))))
        assertTrue(SchedulerManager.QueuePolicy.hasSuccessor(
            listOf(info(WorkInfo.State.RUNNING), info(WorkInfo.State.BLOCKED))
        ))
    }

    @Test fun `diagnostics count manual and automatic running jobs together`() {
        val status = SchedulerManager.QueuePolicy.snapshot(
            "nightly", "Nightly",
            automatic = listOf(info(WorkInfo.State.ENQUEUED)),
            manual = listOf(info(WorkInfo.State.RUNNING)),
        )
        assertEquals(1, status.running)
        assertEquals(1, status.pending)
        assertEquals("RUNNING", status.lastState)
    }

    @Test fun `scheduler status returns idle only after both work chains finish`() {
        val status = SchedulerManager.QueuePolicy.snapshot(
            "nightly", "Nightly",
            automatic = listOf(info(WorkInfo.State.SUCCEEDED)),
            manual = listOf(info(WorkInfo.State.SUCCEEDED)),
        )
        assertEquals(0, status.running)
        assertEquals(0, status.pending)
    }

    @Test fun `manual cleaning does not overlap a running automatic cleanup`() {
        assertTrue(SchedulerManager.QueuePolicy.automaticActive(listOf(info(WorkInfo.State.RUNNING))))
        assertFalse(SchedulerManager.QueuePolicy.automaticActive(listOf(info(WorkInfo.State.ENQUEUED))))
        assertFalse(SchedulerManager.QueuePolicy.automaticActive(listOf(info(WorkInfo.State.SUCCEEDED))))
    }
}
