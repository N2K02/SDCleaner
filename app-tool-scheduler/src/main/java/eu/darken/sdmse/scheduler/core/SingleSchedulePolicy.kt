package eu.darken.sdmse.scheduler.core

/** One editable schedule; prefer an active entry when migrating older multi-schedule backups. */
internal object SingleSchedulePolicy {
    const val DEFAULT_INTERVAL_MINUTES = 360L
    fun defaultSchedule() = Schedule(
        id = "automatic-cleanup",
        label = "Automatic cleanup",
        intervalMinutes = DEFAULT_INTERVAL_MINUTES,
        initialDelayMinutes = DEFAULT_INTERVAL_MINUTES,
        useAppCleaner = true,
    )

    fun repeating(schedule: Schedule): Schedule {
        val interval = (schedule.intervalMinutes ?: DEFAULT_INTERVAL_MINUTES).coerceIn(15L, 43_200L)
        return schedule.copy(intervalMinutes = interval, initialDelayMinutes = interval, runOnce = false)
    }

    fun select(schedules: Set<Schedule>): Schedule = schedules
        .sortedWith(compareByDescending<Schedule> { it.isEnabled }.thenBy { it.createdAt }.thenBy { it.id })
        .firstOrNull()?.let(::repeating) ?: defaultSchedule()
}
