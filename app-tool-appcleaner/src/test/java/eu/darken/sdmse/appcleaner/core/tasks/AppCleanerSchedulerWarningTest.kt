package eu.darken.sdmse.appcleaner.core.tasks

import eu.darken.sdmse.main.core.TaskResultWarnings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AppCleanerSchedulerWarningTest {
    @Test fun `complete success has no warning`() {
        val result = AppCleanerSchedulerTask.Success(affectedSpace = 0L, affectedPaths = emptySet())
        assertNull((result as TaskResultWarnings).warningMessage)
    }

    @Test fun `stopped early result is a scheduler warning`() {
        val result = AppCleanerSchedulerTask.Success(
            affectedSpace = 50L, affectedPaths = emptySet(),
            stoppedEarly = AppCleanerTask.StopReason.ERROR
        )
        assertEquals("Stopped early: ERROR", result.warningMessage)
    }

    @Test fun `skipped results remain visible to scheduler history`() {
        val result = AppCleanerSchedulerTask.Success(
            affectedSpace = 0L, affectedPaths = emptySet(), skippedCount = 3
        )
        assertEquals("Skipped 3 item(s)", result.warningMessage)
    }
}
