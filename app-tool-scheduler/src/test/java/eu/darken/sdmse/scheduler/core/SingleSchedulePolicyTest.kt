package eu.darken.sdmse.scheduler.core

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

class SingleSchedulePolicyTest {
    @Test fun `empty storage creates stable six hour schedule with delayed first run`() {
        val first = SingleSchedulePolicy.select(emptySet())
        first.id shouldBe SingleSchedulePolicy.select(emptySet()).id
        first.intervalMinutes shouldBe 360L
        first.initialDelayMinutes shouldBe 360L
        first.useAppCleaner shouldBe true
        first.isEnabled shouldBe false
    }

    @Test fun `existing customized schedule is preserved exactly`() {
        val custom = Schedule(id = "custom", intervalMinutes = 123, initialDelayMinutes = 17,
            commandsAfterSchedule = listOf("echo done"), useSystemCleaner = true)
        SingleSchedulePolicy.select(setOf(custom)) shouldBe custom.copy(initialDelayMinutes = 123)
    }

    @Test fun `migration chooses active entry independently of storage order`() {
        val paused = Schedule(id = "paused", createdAt = Instant.EPOCH)
        val active = Schedule(id = "active", scheduledAt = Instant.EPOCH)
        SingleSchedulePolicy.select(setOf(paused, active)) shouldBe SingleSchedulePolicy.repeating(active)
        SingleSchedulePolicy.select(setOf(active, paused)) shouldBe SingleSchedulePolicy.repeating(active)
    }

    @Test fun `migration uses oldest active entry with stable id tie break`() {
        val older = Schedule(id = "a", createdAt = Instant.EPOCH, scheduledAt = Instant.EPOCH)
        val newer = older.copy(id = "b", createdAt = Instant.EPOCH.plusSeconds(1))
        SingleSchedulePolicy.select(setOf(newer, older)) shouldBe SingleSchedulePolicy.repeating(older)
        SingleSchedulePolicy.select(setOf(older.copy(id = "b"), older)) shouldBe SingleSchedulePolicy.repeating(older)
    }
    @Test fun `legacy one time and clock modes become repeating without a separate delay`() {
        val once = Schedule(id = "once", runOnce = true, intervalMinutes = 1, initialDelayMinutes = 120)
        val normalized = SingleSchedulePolicy.repeating(once)
        normalized.runOnce shouldBe false
        normalized.intervalMinutes shouldBe 15L
        normalized.initialDelayMinutes shouldBe 15L
        SingleSchedulePolicy.repeating(Schedule(id = "clock")).intervalMinutes shouldBe 360L
    }

}
