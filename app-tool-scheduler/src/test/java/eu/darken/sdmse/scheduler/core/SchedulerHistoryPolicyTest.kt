package eu.darken.sdmse.scheduler.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class SchedulerHistoryPolicyTest {
    private val today = LocalDate.of(2026, 11, 6)
    private fun row(day: String, id: String = "a", status: RunOutcome = RunOutcome.SUCCESS, time: Long = 100) =
        SchedulerDailyRun(id, "Daily clean", day, time, time + 10, 10,
            successes = if (status == RunOutcome.SUCCESS) 1 else 0,
            warnings = if (status == RunOutcome.WARNING) 1 else 0,
            failures = if (status == RunOutcome.FAILED) 1 else 0,
            lastOutcome = status)

    @Test fun `same day same schedule is compacted preserving status counts`() {
        val first = row("2026-11-06")
        val second = row("2026-11-06", status = RunOutcome.FAILED, time = 200)
        val all = SchedulerHistoryPolicy.merge(listOf(first), second, today)
        assertEquals(1, all.size)
        assertEquals(2, all[0].executions)
        assertEquals(1, all[0].successes)
        assertEquals(1, all[0].failures)
        assertEquals(RunOutcome.FAILED, all[0].lastOutcome)
    }

    @Test fun `different schedules have separate daily rows`() {
        val all = SchedulerHistoryPolicy.merge(listOf(row("2026-11-06")), row("2026-11-06", id="b"), today)
        assertEquals(2, all.size)
    }

    @Test fun `thirty day retention prunes older and future rows`() {
        val list = listOf(row("2026-10-07"), row("2026-10-08"), row("2026-11-07"))
        val all = SchedulerHistoryPolicy.merge(list, row("2026-11-06"), today)
        assertEquals(2, all.size)
        assertTrue(all.any { it.day == "2026-10-08" })
    }

    @Test fun `old runs cannot replace newest detail`() {
        val newer = row("2026-11-06", status=RunOutcome.WARNING, time=300)
        val older = row("2026-11-06", status=RunOutcome.SUCCESS, time=100)
        val result = SchedulerHistoryPolicy.merge(listOf(newer), older, today).single()
        assertEquals(RunOutcome.WARNING, result.lastOutcome)
        assertEquals(2, result.executions)
    }
    @Test fun `delete removes only matching daily summary`() {
        val target = row("2026-11-06")
        val otherDay = row("2026-11-05")
        val otherSchedule = row("2026-11-06", id = "b")
        assertEquals(listOf(otherDay, otherSchedule),
            SchedulerHistoryPolicy.delete(listOf(target, otherDay, otherSchedule), "a", "2026-11-06"))
    }

    @Test fun `deleting absent entry preserves history`() {
        val rows = listOf(row("2026-11-06"))
        assertEquals(rows, SchedulerHistoryPolicy.delete(rows, "missing", "2026-11-06"))
        assertTrue(SchedulerHistoryPolicy.delete(emptyList(), "a", "2026-11-06").isEmpty())
    }

}
