package eu.darken.sdmse.scheduler.ui.manager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.automirrored.twotone.HelpOutline
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.BugReport
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.twotone.Alarm
import androidx.compose.material.icons.twotone.ChevronRight
import androidx.compose.material3.Icon
import eu.darken.sdmse.common.compose.layout.SdmScaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import eu.darken.sdmse.common.compose.layout.SdmBrandTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.layout.ScrollAwareFab
import eu.darken.sdmse.common.compose.layout.SdmListDefaults
import eu.darken.sdmse.common.compose.layout.SdmTooltipIconButton
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.progress.ProgressOverlay
import eu.darken.sdmse.common.compose.tour.LocalGuidedTourController
import eu.darken.sdmse.common.compose.tour.guidedTourTarget
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.scheduler.R
import eu.darken.sdmse.scheduler.core.Schedule
import eu.darken.sdmse.scheduler.core.ScheduleId
import eu.darken.sdmse.scheduler.core.RunOutcome
import eu.darken.sdmse.scheduler.core.SchedulerDailyRun
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import eu.darken.sdmse.scheduler.ui.manager.items.AlarmHintRow
import eu.darken.sdmse.scheduler.ui.manager.items.BatteryHintRow
import eu.darken.sdmse.scheduler.ui.manager.items.CommandsEditDialog
import eu.darken.sdmse.scheduler.ui.manager.items.ScheduleRow
import eu.darken.sdmse.scheduler.ui.manager.tour.SchedulerManagerTour
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private val TAG = logTag("Scheduler", "Manager", "Screen")

@Composable
fun SchedulerManagerScreenHost(
    vm: SchedulerManagerViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingCommandsEdit by remember {
        mutableStateOf<SchedulerManagerViewModel.Event.EditCommands?>(null)
    }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is SchedulerManagerViewModel.Event.EditCommands -> pendingCommandsEdit = event

                is SchedulerManagerViewModel.Event.ShowBatteryOptimizationSettings -> {
                    runCatching { context.startActivity(event.intent) }
                        .onFailure { error ->
                            log(TAG, WARN) { "Battery optimization intent failed: $error" }
                            vm.onBatteryIntentFailed(error)
                        }
                }
            }
        }
    }

    SchedulerManagerScreen(
        stateSource = vm.state,
        snackbarHostState = snackbarHostState,
        onNavigateUp = vm::navUp,
        onShowHelp = vm::showHelp,
        onDebugSchedule = vm::debugSchedule,
        onCreateNew = vm::createNew,
        onEditSchedule = vm::editSchedule,
        onToggleSchedule = vm::toggleSchedule,
        onRunNow = vm::runNow,
        onRemoveSchedule = vm::removeSchedule,
        onToggleCorpseFinder = vm::toggleCorpseFinder,
        onToggleSystemCleaner = vm::toggleSystemCleaner,
        onToggleAppCleaner = vm::toggleAppCleaner,
        onEditCommands = vm::requestEditCommands,
        onFixBattery = vm::fixBatteryOptimization,
        onDismissBattery = vm::dismissBatteryHint,
        onDeleteHistory = vm::deleteHistory,
        onClearHistory = vm::clearHistory,
    )

    pendingCommandsEdit?.let { ev ->
        CommandsEditDialog(
            initialText = ev.initialText,
            onConfirm = { newText ->
                vm.updateCommandsAfterSchedule(ev.scheduleId, newText)
                pendingCommandsEdit = null
            },
            onDismiss = { pendingCommandsEdit = null },
        )
    }
}

@Composable
internal fun SchedulerManagerScreen(
    stateSource: StateFlow<SchedulerManagerViewModel.State> = MutableStateFlow(SchedulerManagerViewModel.State()),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateUp: () -> Unit = {},
    onShowHelp: () -> Unit = {},
    onDebugSchedule: () -> Unit = {},
    onCreateNew: () -> Unit = {},
    onEditSchedule: (ScheduleId) -> Unit = {},
    onToggleSchedule: (ScheduleId) -> Unit = {},
    onRunNow: (ScheduleId) -> Unit = {},
    onRemoveSchedule: (ScheduleId) -> Unit = {},
    onToggleCorpseFinder: (ScheduleId) -> Unit = {},
    onToggleSystemCleaner: (ScheduleId) -> Unit = {},
    onToggleAppCleaner: (ScheduleId) -> Unit = {},
    onEditCommands: (ScheduleId) -> Unit = {},
    onFixBattery: () -> Unit = {},
    onDismissBattery: () -> Unit = {},
    onDeleteHistory: (String, String) -> Unit = { _, _ -> },
    onClearHistory: () -> Unit = {},
) {
    val state by stateSource.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    val tourController = LocalGuidedTourController.current
    val tourDef = remember { SchedulerManagerTour.definition() }
    var tourStartAttempted by remember { mutableStateOf(false) }
    // The automatic schedule exists after loading.
    val tourReady = !state.isLoading
    LaunchedEffect(tourReady) {
        if (!tourReady || tourStartAttempted) return@LaunchedEffect
        // shouldStart() is false for both "done/dismissed" and "another tour active"; mark attempted
        // only after it passes so a transient block can't permanently suppress this tour.
        if (!tourController.shouldStart(tourDef)) return@LaunchedEffect
        tourStartAttempted = true
        tourController.start(tourDef)
    }

    SdmScaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.TwoTone.Alarm, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.scheduler_label))
                    }
                },
                navigationIcon = {
                    SdmTooltipIconButton(
                        icon = Icons.AutoMirrored.TwoTone.ArrowBack,
                        label = stringResource(CommonR.string.general_navigate_up_action),
                        onClick = onNavigateUp,
                    )
                },
                actions = {
                    if (Bugs.isDebug) {
                        SdmTooltipIconButton(
                            icon = Icons.TwoTone.BugReport,
                            label = "Debug schedule",
                            onClick = onDebugSchedule,
                        )
                    }
                    SdmTooltipIconButton(
                        icon = Icons.AutoMirrored.TwoTone.HelpOutline,
                        label = stringResource(CommonR.string.general_help_action),
                        onClick = onShowHelp,
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        ProgressOverlay(
            data = if (state.isLoading) Progress.Data() else null,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            ScheduleListContent(
                state = state,
                listState = listState,
                contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp + SdmListDefaults.FabClearance),
                onEditSchedule = onEditSchedule,
                onToggleSchedule = onToggleSchedule,
                onRunNow = onRunNow,
                onRemoveSchedule = onRemoveSchedule,
                onToggleCorpseFinder = onToggleCorpseFinder,
                onToggleSystemCleaner = onToggleSystemCleaner,
                onToggleAppCleaner = onToggleAppCleaner,
                onEditCommands = onEditCommands,
                onFixBattery = onFixBattery,
                onDismissBattery = onDismissBattery,
                onDeleteHistory = onDeleteHistory,
                onClearHistory = onClearHistory,
            )
        }
    }
}

@Composable
private fun ScheduleListContent(
    state: SchedulerManagerViewModel.State,
    listState: LazyListState,
    contentPadding: PaddingValues,
    onEditSchedule: (ScheduleId) -> Unit,
    onToggleSchedule: (ScheduleId) -> Unit,
    onRunNow: (ScheduleId) -> Unit,
    onRemoveSchedule: (ScheduleId) -> Unit,
    onToggleCorpseFinder: (ScheduleId) -> Unit,
    onToggleSystemCleaner: (ScheduleId) -> Unit,
    onToggleAppCleaner: (ScheduleId) -> Unit,
    onEditCommands: (ScheduleId) -> Unit,
    onFixBattery: () -> Unit,
    onDismissBattery: () -> Unit,
    onDeleteHistory: (String, String) -> Unit,
    onClearHistory: () -> Unit,
) {
    var showHistory by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<SchedulerDailyRun?>(null) }
    var clearHistory by remember { mutableStateOf(false) }
    if (deleteTarget != null || clearHistory) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleteTarget = null; clearHistory = false },
            title = { Text(if (clearHistory) "Clear run history?" else "Delete daily summary?") },
            text = { Text("Only saved history is removed. Your schedule and future cleanups are unchanged.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    if (clearHistory) onClearHistory()
                    else deleteTarget?.let { onDeleteHistory(it.scheduleId, it.day) }
                    deleteTarget = null
                    clearHistory = false
                }) { Text("Delete") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { deleteTarget = null; clearHistory = false }) { Text("Cancel") }
            },
        )
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item("scheduler-summary") {
            val now = Instant.now()
            val active = state.schedules.count { it.isEnabled }
            val next = state.schedules.filter { it.isEnabled }.mapNotNull { schedule ->
                schedule.calcExecutionEta(now, reschedule = false)
                    ?.let { schedule.label to now.plus(it) }
            }.minByOrNull { it.second }
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("NEXT CLEANUP", style = MaterialTheme.typography.labelLarge)
                        Text(next?.second?.atZone(ZoneId.systemDefault())
                            ?.format(DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.SHORT))
                            ?: "No upcoming runs", style = MaterialTheme.typography.headlineMedium)
                        next?.let { Text(it.first.ifBlank { "Schedule" }, style = MaterialTheme.typography.bodyLarge) }
                    }
                }
                Text(if (active > 0) "Automatic cleanup · On" else "Automatic cleanup · Off", style = MaterialTheme.typography.titleMedium)
            }
        }

        if (state.showBatteryHint) {
            item("battery") {
                BatteryHintRow(
                    onFix = onFixBattery,
                    onDismiss = onDismissBattery,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        items(state.schedules, key = { it.id }) { schedule ->
            ScheduleRow(
                schedule = schedule,
                showCommands = state.showCommands,
                onEdit = { onEditSchedule(schedule.id) },
                onToggle = { onToggleSchedule(schedule.id) },
                onRunNow = { onRunNow(schedule.id) },
                onRemove = { onRemoveSchedule(schedule.id) },
                onToggleCorpseFinder = { onToggleCorpseFinder(schedule.id) },
                onToggleSystemCleaner = { onToggleSystemCleaner(schedule.id) },
                onToggleAppCleaner = { onToggleAppCleaner(schedule.id) },
                onEditCommands = { onEditCommands(schedule.id) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item("scheduler-history-toggle") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Recent runs", style = MaterialTheme.typography.titleMedium)
                androidx.compose.material3.TextButton(onClick = { showHistory = !showHistory }) {
                    Text(if (showHistory) "Show less" else "View all (${state.history.size})")
                }
            }
        }
        if (state.history.isNotEmpty()) {
            item("history-clear") {
                androidx.compose.material3.TextButton(onClick = { clearHistory = true }, modifier = Modifier.padding(horizontal = 12.dp)) {
                    Text("Clear history")
                }
            }
        }
        if (state.history.isEmpty()) {
            item("scheduler-history-empty") {
                Text("No scheduled runs recorded yet", modifier = Modifier.padding(horizontal = 20.dp))
            }
        }
        items(state.history.sortedByDescending { it.lastAt }.let { if (showHistory) it else it.take(2) }, key = { "history-${it.scheduleId}-${it.day}" }) { run ->
            SchedulerHistoryRow(run, Modifier.padding(horizontal = 16.dp), onDelete = { deleteTarget = run })
        }
        item("timing-note") {
            Text("Run times may vary to save battery.",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Preview2
@Composable
private fun SchedulerManagerScreenEmptyPreview() {
    PreviewWrapper {
        SchedulerManagerScreen(
            stateSource = MutableStateFlow(
                SchedulerManagerViewModel.State(
                    schedules = emptyList(),
                    isLoading = false,
                ),
            ),
        )
    }
}

@Preview2
@Composable
private fun SchedulerManagerScreenWithSchedulePreview() {
    PreviewWrapper {
        SchedulerManagerScreen(
            stateSource = MutableStateFlow(
                SchedulerManagerViewModel.State(
                    schedules = listOf(
                        Schedule(
                            id = "preview-1",
                            label = "Daily clean",
                            hour = 22,
                            minute = 0,
                        ),
                    ),
                    showAlarmHint = false,
                    showBatteryHint = false,
                    showCommands = false,
                    isLoading = false,
                ),
            ),
        )
    }
}

@Preview2
@Composable
private fun SchedulerManagerScreenWithAcsHintPreview() {
    PreviewWrapper {
        SchedulerManagerScreen(
            stateSource = MutableStateFlow(
                SchedulerManagerViewModel.State(
                    schedules = listOf(
                        Schedule(
                            id = "preview-1",
                            label = "Nightly clean",
                            hour = 1,
                            minute = 0,
                            useAppCleaner = true,
                        ),
                        Schedule(
                            id = "preview-2",
                            label = "Weekend clean",
                            hour = 23,
                            minute = 30,
                            useAppCleaner = true,
                        ),
                    ),
                    showAlarmHint = true,
                    showBatteryHint = false,
                    showCommands = false,
                    isLoading = false,
                ),
            ),
        )
    }
}

@Preview2
@Composable
private fun SchedulerManagerScreenWithAcsSystemAppsHintPreview() {
    PreviewWrapper {
        SchedulerManagerScreen(
            stateSource = MutableStateFlow(
                SchedulerManagerViewModel.State(
                    schedules = listOf(
                        Schedule(
                            id = "preview-1",
                            label = "Nightly clean",
                            hour = 1,
                            minute = 0,
                            useAppCleaner = true,
                        ),
                    ),
                    showAlarmHint = true,
                    showBatteryHint = false,
                    showCommands = false,
                    isLoading = false,
                ),
            ),
        )
    }
}


@Composable
private fun SchedulerHistoryRow(run: SchedulerDailyRun, modifier: Modifier = Modifier, onDelete: () -> Unit = {}) {
    var expanded by remember { mutableStateOf(false) }
    val localTime = Instant.ofEpochMilli(run.lastAt).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    val outcome = when (run.lastOutcome) {
        RunOutcome.SUCCESS -> "Completed"
        RunOutcome.WARNING -> "Warning"
        RunOutcome.FAILED -> "Failed"
    }
    Card(onClick = { expanded = !expanded }, modifier = modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Column(Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(run.day, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                androidx.compose.material3.IconButton(onClick = onDelete) {
                    Icon(Icons.TwoTone.Delete, contentDescription = "Delete daily summary")
                }
            }
            Text("$outcome · Last run $localTime · ${run.executions} run(s)",
                style = MaterialTheme.typography.bodyMedium)
            Text("${run.successes} success · ${run.warnings} warning · ${run.failures} failed",
                style = MaterialTheme.typography.bodySmall)
            if (expanded) {
                Text("Duration: ${run.lastDurationMs} ms · ${if (run.lastManual) "Manual" else "Automatic"}",
                    style = MaterialTheme.typography.bodySmall)
                run.lastDetails.forEach { line -> Text(line, style = MaterialTheme.typography.bodySmall) }
                run.lastError?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
