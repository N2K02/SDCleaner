package eu.darken.sdmse.scheduler.ui.manager.create

import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.AccessTime
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.layout.SdmScaffold
import eu.darken.sdmse.common.compose.layout.SdmBrandTopAppBar
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.scheduler.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Duration

@Composable
fun ScheduleItemSheetHost(
    scheduleId: String,
    vm: ScheduleItemViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    LaunchedEffect(scheduleId) { vm.setScheduleId(scheduleId) }

    SdmScaffold(
        topBar = {
            SdmBrandTopAppBar(
                title = { Text("Scheduler") },
                navigationIcon = {
                    IconButton(onClick = vm::navUp) {
                        Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            ScheduleItemSheet(
                stateSource = vm.state,
                onLabelChanged = vm::updateLabel,
                onTimePicked = vm::updateTime,
                onIncreaseDays = vm::increaseDays,
                onDecreaseDays = vm::decreaseDays,
                onRepeatDaysChanged = vm::setRepeatDays,
                onIntervalModeChanged = vm::setIntervalMode,
                onRunOnceModeSelected = vm::setRunOnceMode,
                onIntervalMinutesChanged = vm::setIntervalMinutes,
                onInitialDelayChanged = vm::setInitialDelayMinutes,
                onSave = vm::saveSchedule,
            )
        
        }
    }}

@Composable
internal fun ScheduleItemSheet(
    stateSource: Flow<ScheduleItemViewModel.State> = MutableStateFlow(ScheduleItemViewModel.State()),
    onLabelChanged: (String) -> Unit = {},
    onTimePicked: (Int, Int) -> Unit = { _, _ -> },
    onIncreaseDays: () -> Unit = {},
    onDecreaseDays: () -> Unit = {},
    onRepeatDaysChanged: (Int) -> Unit = {},
    onIntervalModeChanged: (Boolean) -> Unit = {},
    onRunOnceModeSelected: () -> Unit = {},
    onIntervalMinutesChanged: (Long) -> Unit = {},
    onInitialDelayChanged: (Long) -> Unit = {},
    onSave: () -> Unit = {},
) {
    val state by stateSource.collectAsStateWithLifecycle(initialValue = ScheduleItemViewModel.State())

    if (!state.isReady) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    var showTimePicker by remember { mutableStateOf(false) }

    var timeText by remember { mutableStateOf(formatTime(state.hour, state.minute)) }
    LaunchedEffect(state.hour, state.minute) {
        val parsed = parseTime(timeText)
        if (parsed?.first != state.hour || parsed?.second != state.minute) {
            timeText = formatTime(state.hour, state.minute)
        }
    }

    var daysText by remember { mutableStateOf(state.repeatInterval.toDays().toString()) }
    LaunchedEffect(state.repeatInterval) {
        val stateDays = state.repeatInterval.toDays().toInt()
        val typed = daysText.toIntOrNull()
        // Re-populate when the field is empty (typed == null) too — otherwise clearing the field and
        // then using the +/- buttons leaves the input blank while the underlying state changes.
        if (typed == null || typed != stateDays) {
            daysText = stateDays.toString()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    ),
                ).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.TwoTone.AccessTime, contentDescription = null, modifier = Modifier.size(36.dp))
                Text("Cleaning Scheduler", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Automatically clean your device on your schedule",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        OutlinedTextField(
            value = state.label.orEmpty(),
            onValueChange = onLabelChanged,
            label = { Text(stringResource(R.string.scheduler_schedule_name_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        )
        Text("Run mode", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = state.runOnce,
                    onClick = onRunOnceModeSelected,
                    label = { Text("After X min", maxLines = 1) },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = state.intervalMinutes != null && !state.runOnce,
                    onClick = { onIntervalModeChanged(true) },
                    label = { Text("Repeat", maxLines = 1) },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = state.intervalMinutes == null,
                    onClick = { onIntervalModeChanged(false) },
                    label = { Text("At a time", maxLines = 1) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        val intervalMinutes = state.intervalMinutes
        if (intervalMinutes != null) {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!state.runOnce) {
                        Text("Repeat interval", style = MaterialTheme.typography.titleMedium)
                        Text("Run cleaning repeatedly at a fixed interval.", style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onIntervalMinutesChanged(intervalMinutes - 15) }) {
                                Icon(Icons.TwoTone.Remove, contentDescription = "Decrease interval")
                            }
                            OutlinedTextField(
                                value = intervalMinutes.toString(),
                                onValueChange = { it.toLongOrNull()?.let(onIntervalMinutesChanged) },
                                label = { Text("Every (minutes)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                            )
                            IconButton(onClick = { onIntervalMinutesChanged(intervalMinutes + 15) }) {
                                Icon(Icons.TwoTone.Add, contentDescription = "Increase interval")
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            listOf(15L, 30L, 60L, 120L).forEach { minutes ->
                                FilterChip(
                                    selected = intervalMinutes == minutes,
                                    onClick = { onIntervalMinutesChanged(minutes) },
                                    label = { Text("$minutes min", maxLines = 1) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                    if (!state.runOnce) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Start after (optional)", style = MaterialTheme.typography.titleMedium)
                                Text("Wait before the first run.", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(
                                checked = state.initialDelayMinutes > 0,
                                onCheckedChange = { onInitialDelayChanged(if (it) 5L else 0L) },
                            )
                        }
                    } else {
                        Text("Run once after", style = MaterialTheme.typography.titleMedium)
                        Text("Run cleaning once and then disable this schedule.", style = MaterialTheme.typography.bodySmall)
                    }
                    if (state.runOnce || state.initialDelayMinutes > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onInitialDelayChanged(state.initialDelayMinutes - 1) }) {
                                Icon(Icons.TwoTone.Remove, contentDescription = "Decrease initial delay")
                            }
                            OutlinedTextField(
                                value = state.initialDelayMinutes.toString(),
                                onValueChange = { it.toLongOrNull()?.let(onInitialDelayChanged) },
                                label = { Text("After (minutes)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                            )
                            IconButton(onClick = { onInitialDelayChanged(state.initialDelayMinutes + 1) }) {
                                Icon(Icons.TwoTone.Add, contentDescription = "Increase initial delay")
                            }
                        }
                    }
                }
            }
        } else {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = timeText,
                onValueChange = { newText ->
                    timeText = newText
                    parseTime(newText)?.let { (h, m) -> onTimePicked(h, m) }
                },
                label = { Text(stringResource(R.string.scheduler_schedule_time_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = {
                    // Re-sync the field text from the parsed state, so a partial entry collapses to
                    // the last valid value when the user dismisses the keyboard.
                    timeText = formatTime(state.hour, state.minute)
                }),
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalButton(onClick = { showTimePicker = true }) {
                Icon(
                    imageVector = Icons.TwoTone.AccessTime,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(CommonR.string.general_edit_action))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.scheduler_schedule_repeat_label),
                    style = MaterialTheme.typography.bodyMedium,
                )
                val days = state.repeatInterval.toDays().toInt()
                Text(
                    text = pluralStringResource(R.plurals.scheduler_schedule_repeat_x_days, days, days),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            IconButton(onClick = onDecreaseDays) {
                Icon(Icons.TwoTone.Remove, contentDescription = "−1")
            }
            OutlinedTextField(
                value = daysText,
                onValueChange = { newText ->
                    if (newText.isEmpty() || newText.matches(DAYS_INPUT_REGEX)) {
                        daysText = newText
                        newText.toIntOrNull()?.let(onRepeatDaysChanged)
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = {
                    daysText = state.repeatInterval.toDays().toString()
                }),
                textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
                modifier = Modifier.width(72.dp),
            )
            IconButton(onClick = onIncreaseDays) {
                Icon(Icons.TwoTone.Add, contentDescription = "+1")
            }
        }

        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = when {
                    state.runOnce -> "Cleaning will run once after ${state.initialDelayMinutes} minutes."
                    intervalMinutes != null -> "Cleaning will start after ${state.initialDelayMinutes} minutes and repeat every $intervalMinutes minutes."
                    else -> "Cleaning will run at the selected time and repeat every ${state.repeatInterval.toDays()} days."
                } + " Android battery restrictions may delay background execution.",
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Button(
            onClick = onSave,
            enabled = state.canSave,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
        ) {
            Text(stringResource(CommonR.string.general_save_action), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    if (showTimePicker) {
        ScheduleItemTimePickerDialog(
            initialHour = state.hour ?: 22,
            initialMinute = state.minute ?: 0,
            onConfirm = { h, m ->
                onTimePicked(h, m)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }
}

private fun formatTime(hour: Int?, minute: Int?): String {
    if (hour == null || minute == null) return ""
    return "%02d:%02d".format(hour, minute)
}

private val TIME_INPUT_REGEX = Regex("^(\\d{1,2}):(\\d{2})$")
private val DAYS_INPUT_REGEX = Regex("^\\d{1,2}$")

private fun parseTime(text: String): Pair<Int, Int>? {
    val match = TIME_INPUT_REGEX.matchEntire(text.trim()) ?: return null
    val h = match.groupValues[1].toIntOrNull() ?: return null
    val m = match.groupValues[2].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h to m
}

@Preview2
@Composable
private fun ScheduleItemSheetPreview() {
    PreviewWrapper {
        Box {
            ScheduleItemSheet(
                stateSource = MutableStateFlow(
                    ScheduleItemViewModel.State(
                        label = "Daily clean",
                        hour = 22,
                        minute = 0,
                        repeatInterval = Duration.ofDays(3),
                        canSave = true,
                        isReady = true,
                    ),
                ),
            )
        }
    }
}

@Preview2
@Composable
private fun ScheduleItemSheetEmptyPreview() {
    PreviewWrapper {
        Box {
            ScheduleItemSheet(
                stateSource = MutableStateFlow(
                    ScheduleItemViewModel.State(isReady = true),
                ),
            )
        }
    }
}
