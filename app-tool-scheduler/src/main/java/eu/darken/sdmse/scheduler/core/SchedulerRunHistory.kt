package eu.darken.sdmse.scheduler.core

import android.content.Context
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
enum class RunOutcome { SUCCESS, WARNING, FAILED }

@Serializable
data class SchedulerDailyRun(
    val scheduleId: String,
    val label: String,
    val day: String,
    val firstAt: Long,
    val lastAt: Long,
    val lastDurationMs: Long,
    val executions: Int = 1,
    val successes: Int = 0,
    val warnings: Int = 0,
    val failures: Int = 0,
    val lastOutcome: RunOutcome = RunOutcome.SUCCESS,
    val lastDetails: List<String> = emptyList(),
    val lastError: String? = null,
    val lastManual: Boolean = false,
)

/** One compact row per schedule per local calendar day, with rolling 30-day retention. */
internal object SchedulerHistoryPolicy {
    const val RETAIN_DAYS = 30
    fun merge(
        previous: List<SchedulerDailyRun>,
        newRun: SchedulerDailyRun,
        today: LocalDate,
    ): List<SchedulerDailyRun> {
        val earliest = today.minusDays((RETAIN_DAYS - 1).toLong())
        val matching = previous.firstOrNull { it.scheduleId == newRun.scheduleId && it.day == newRun.day }
        val row = if (matching == null) newRun else newRun.copy(
            firstAt = minOf(matching.firstAt, newRun.firstAt),
            lastAt = maxOf(matching.lastAt, newRun.lastAt),
            executions = matching.executions + newRun.executions,
            successes = matching.successes + newRun.successes,
            warnings = matching.warnings + newRun.warnings,
            failures = matching.failures + newRun.failures,
            lastOutcome = if (newRun.lastAt >= matching.lastAt) newRun.lastOutcome else matching.lastOutcome,
            lastDetails = if (newRun.lastAt >= matching.lastAt) newRun.lastDetails else matching.lastDetails,
            lastError = if (newRun.lastAt >= matching.lastAt) newRun.lastError else matching.lastError,
            lastDurationMs = if (newRun.lastAt >= matching.lastAt) newRun.lastDurationMs else matching.lastDurationMs,
            lastManual = if (newRun.lastAt >= matching.lastAt) newRun.lastManual else matching.lastManual,
        )
        return (previous.filterNot { it.scheduleId == newRun.scheduleId && it.day == newRun.day } + row)
            .filter { date ->
                runCatching { LocalDate.parse(date.day) }.getOrNull()?.let { !it.isBefore(earliest) && !it.isAfter(today) } == true
            }
            .sortedByDescending { it.lastAt }
    }
}

@Singleton
class SchedulerRunHistory @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json,
    @AppScope appScope: CoroutineScope,
) {
    private val file = AtomicFile(File(context.filesDir, "scheduler/daily-history-v1.json"))
    private val lock = Mutex()
    private var loaded = false
    private val internal = MutableStateFlow<List<SchedulerDailyRun>>(emptyList())
    val history: StateFlow<List<SchedulerDailyRun>> = internal

    init { appScope.launch(Dispatchers.IO) { lock.withLock { ensureLoaded() } } }

    private fun ensureLoaded() {
        if (loaded) return
        val rows = try {
            json.decodeFromString(ListSerializer(SchedulerDailyRun.serializer()),
                file.openRead().bufferedReader().use { it.readText() })
        } catch (_: Exception) { emptyList() }
        val today = LocalDate.now()
        val retained = rows.filter {
            runCatching { LocalDate.parse(it.day) }.getOrNull()
                ?.let { d -> !d.isBefore(today.minusDays(29)) && !d.isAfter(today) } == true
        }.sortedByDescending { it.lastAt }
        internal.value = retained
        if (retained.size != rows.size) {
            file.baseFile.parentFile?.mkdirs()
            val output = file.startWrite()
            try {
                output.write(json.encodeToString(ListSerializer(SchedulerDailyRun.serializer()), retained)
                    .toByteArray(Charsets.UTF_8))
                file.finishWrite(output)
            } catch (e: Exception) {
                file.failWrite(output)
                log(TAG) { "History pruning write failed: ${e.message}" }
            }
        }
        loaded = true
    }

    suspend fun record(
        schedule: Schedule,
        startedAt: Long,
        finishedAt: Long,
        outcome: RunOutcome,
        details: List<String>,
        error: String? = null,
        manual: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        lock.withLock {
            ensureLoaded()
            val day = Instant.ofEpochMilli(startedAt).atZone(ZoneId.systemDefault()).toLocalDate()
            val entry = SchedulerDailyRun(
                scheduleId = schedule.id,
                label = schedule.label.ifBlank { "Unnamed schedule" }.take(100),
                day = day.toString(),
                firstAt = startedAt,
                lastAt = finishedAt,
                lastDurationMs = (finishedAt - startedAt).coerceAtLeast(0),
                successes = if (outcome == RunOutcome.SUCCESS) 1 else 0,
                warnings = if (outcome == RunOutcome.WARNING) 1 else 0,
                failures = if (outcome == RunOutcome.FAILED) 1 else 0,
                lastOutcome = outcome,
                lastDetails = details.take(8).map { it.replace('\n', ' ').take(160) },
                lastError = error?.replace('\n', ' ')?.take(220),
                lastManual = manual,
            )
            val next = SchedulerHistoryPolicy.merge(internal.value, entry, LocalDate.now())
            file.baseFile.parentFile?.mkdirs()
            val stream = file.startWrite()
            try {
                stream.write(json.encodeToString(ListSerializer(SchedulerDailyRun.serializer()), next).toByteArray(Charsets.UTF_8))
                file.finishWrite(stream)
                internal.value = next
            } catch (e: Exception) {
                file.failWrite(stream)
                throw e
            }
        }
    }

    companion object { val TAG = logTag("Scheduler", "History") }
}
