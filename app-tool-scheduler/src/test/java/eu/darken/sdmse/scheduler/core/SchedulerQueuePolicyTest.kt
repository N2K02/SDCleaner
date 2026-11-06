package eu.darken.sdmse.scheduler.core

import androidx.work.WorkInfo
import io.mockk.every
import io.mockk.mockk
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

    @Test fun `manual cleaning does not overlap a running automatic cleanup`() {
        assertTrue(SchedulerManager.QueuePolicy.automaticActive(listOf(info(WorkInfo.State.RUNNING))))
        assertFalse(SchedulerManager.QueuePolicy.automaticActive(listOf(info(WorkInfo.State.ENQUEUED))))
        assertFalse(SchedulerManager.QueuePolicy.automaticActive(listOf(info(WorkInfo.State.SUCCEEDED))))
    }
}
