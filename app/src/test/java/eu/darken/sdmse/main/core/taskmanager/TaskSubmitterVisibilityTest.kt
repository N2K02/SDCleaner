package eu.darken.sdmse.main.core.taskmanager

import eu.darken.sdmse.main.core.SDMTool
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TaskSubmitterVisibilityTest {
    private fun managed(notify: Boolean, complete: Boolean = false) =
        TaskSubmitter.ManagedTask(
            id = if (notify) "manual" else "scheduler",
            toolType = SDMTool.Type.APPCLEANER,
            task = mockk(),
            notifyOnFinish = notify,
            completedAt = if (complete) java.time.Instant.now() else null,
        )

    @Test fun `scheduler-only work never requests foreground progress notification`() {
        assertFalse(TaskSubmitter.State(listOf(managed(false))).hasNotifiableActiveWork)
    }

    @Test fun `manual work still gets foreground monitoring even during scheduler run`() {
        assertTrue(TaskSubmitter.State(listOf(managed(false), managed(true))).hasNotifiableActiveWork)
    }

    @Test fun `resource lease diagnostics identify leaks even after task completion`() {
        val leaked = managed(false, complete = true).copy(resourceHeld = true)
        assertTrue(leaked.isComplete)
        assertTrue(leaked.resourceHeld)
        assertFalse(TaskSubmitter.State(listOf(leaked)).hasNotifiableActiveWork)
    }

    @Test fun `completed tasks do not keep a foreground notification alive`() {
        assertFalse(TaskSubmitter.State(listOf(managed(true, complete = true))).hasNotifiableActiveWork)
    }
}
