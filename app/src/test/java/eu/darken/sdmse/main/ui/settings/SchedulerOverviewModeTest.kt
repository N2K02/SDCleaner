package eu.darken.sdmse.main.ui.settings

import eu.darken.sdmse.scheduler.core.Schedule
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SchedulerOverviewModeTest {

    @Test
    fun `empty scheduler has descriptive inactive summary`() {
        scheduleOverviewMode(null) shouldBe "No active schedule"
    }

    @Test
    fun `repeating schedule shows exact minute interval`() {
        scheduleOverviewMode(Schedule(id = "interval", intervalMinutes = 60L)) shouldBe "Repeat every 60 min"
        scheduleOverviewMode(Schedule(id = "short", intervalMinutes = 15L)) shouldBe "Repeat every 15 min"
    }

    @Test
    fun `one-time schedule never mislabels as repeat`() {
        scheduleOverviewMode(Schedule(id = "once", intervalMinutes = 1L, runOnce = true, initialDelayMinutes = 5L)) shouldBe "Once after 5 min"
    }

    @Test
    fun `clock schedule displays padded local clock time`() {
        scheduleOverviewMode(Schedule(id = "clock", hour = 9, minute = 3)) shouldBe "At 09:03"
    }
}
