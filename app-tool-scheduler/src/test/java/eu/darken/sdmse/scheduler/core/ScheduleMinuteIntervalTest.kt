package eu.darken.sdmse.scheduler.core

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class ScheduleMinuteIntervalTest : BaseTest() {
    private val anchor = Instant.parse("2026-01-01T00:00:00Z")

    @Test fun `first execution honors initial delay`() {
        val schedule = Schedule(id = "minute", scheduledAt = anchor, intervalMinutes = 60, initialDelayMinutes = 5)
        schedule.calcFirstExecutionAt(ZoneId.of("UTC")) shouldBe anchor.plusSeconds(300)
        schedule.calcExecutionEta(anchor, reschedule = false) shouldBe Duration.ofMinutes(5)
    }

    @Test fun `recurrence advances on fixed minute boundaries`() {
        val schedule = Schedule(id = "minute", scheduledAt = anchor, intervalMinutes = 30, initialDelayMinutes = 5)
        schedule.calcExecutionEta(anchor.plusSeconds(6 * 60), reschedule = false) shouldBe Duration.ofMinutes(29)
        schedule.calcExecutionEta(anchor.plusSeconds(35 * 60), reschedule = false) shouldBe Duration.ofMinutes(30)
    }

    @Test fun `invalid backup intervals clamp to fifteen minutes`() {
        val schedule = Schedule(id = "minute", scheduledAt = anchor, intervalMinutes = 1)
        schedule.calcExecutionEta(anchor.plusSeconds(60), reschedule = false) shouldBe Duration.ofMinutes(14)
    }

    @Test fun `zero-delay interval schedules execute on enable`() {
        val schedule = Schedule(id = "immediate", scheduledAt = anchor, intervalMinutes = 60)
        schedule.calcExecutionEta(anchor.plusMillis(100), reschedule = false) shouldBe Duration.ZERO
    }

    @Test fun `one-time cleanup runs after requested delay`() {
        val schedule = Schedule(id = "once", scheduledAt = anchor, intervalMinutes = 1, initialDelayMinutes = 5, runOnce = true)
        schedule.calcExecutionEta(anchor, reschedule = false) shouldBe Duration.ofMinutes(5)
        schedule.calcExecutionEta(anchor.plusSeconds(6 * 60), reschedule = false) shouldBe Duration.ZERO
    }

    @Test fun `disabled schedule does not run`() {
        val schedule = Schedule(id = "minute", intervalMinutes = 30, initialDelayMinutes = 5)
        schedule.calcExecutionEta(anchor, reschedule = false) shouldBe null
    }

    @Test fun `late finishing worker will not immediately repeat`() {
        val schedule = Schedule(id = "minute", scheduledAt = anchor, intervalMinutes = 15)
        schedule.calcExecutionEta(anchor.plusSeconds(14 * 60), reschedule = true) shouldBe Duration.ofMinutes(16)
    }
}
