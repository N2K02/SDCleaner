package eu.darken.sdmse.scheduler.ui.manager.create

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.saveable.rememberSaveable
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
                onEnabledChanged = vm::setEnabled,
                onAppCleanerChanged = vm::setAppCleanerEnabled,
                onSystemCleanerChanged = vm::setSystemCleanerEnabled,
                onCorpseFinderChanged = vm::setCorpseFinderEnabled,
                onSave = vm::saveSchedule,
            )
        }
    }
}

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
    onEnabledChanged: (Boolean) -> Unit = {},
    onAppCleanerChanged: (Boolean) -> Unit = {},
    onSystemCleanerChanged: (Boolean) -> Unit = {},
    onCorpseFinderChanged: (Boolean) -> Unit = {},
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
    var showCleaningOptions by rememberSaveable { mutableStateOf(false) }

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
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Spacer(Modifier.height(2.dp))
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.background(
                    Brush.horizontalGradient(listOf(Color(0xFF30224B), Color(0xFF7542BB))),
                ).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(Icons.Rounded.EventRepeat, contentDescription = null, modifier = Modifier.size(29.dp), tint = Color.White)
                Text("Cleaning Scheduler", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(
                    "Automatically clean your device on your schedule",
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFFEBDFFF),
                )
            }
        }
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.TwoTone.AccessTime, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text("Enable scheduler", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Switch(checked = state.isEnabled, onCheckedChange = onEnabledChanged)
            }
        }
        Text("Run mode", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SchedulerModeButton(
                    selected = state.runOnce,
                    label = "After X minutes",
                    icon = Icons.Rounded.Timer,
                    modifier = Modifier.weight(1f),
                    onClick = onRunOnceModeSelected,
                )
                SchedulerModeButton(
                    selected = state.intervalMinutes != null && !state.runOnce,
                    label = "Repeat every X minutes",
                    icon = Icons.Rounded.AllInclusive,
                    modifier = Modifier.weight(1f),
                    onClick = { onIntervalModeChanged(true) },
                )
                SchedulerModeButton(
                    selected = state.intervalMinutes == null,
                    label = "At specific time",
                    icon = Icons.Rounded.Event,
                    modifier = Modifier.weight(1f),
                    onClick = { onIntervalModeChanged(false) },
                )
            }
        }
        val intervalMinutes = state.intervalMinutes
        // Keep partially entered numbers in the text field; avoid clamping the first digit.
        var intervalText by remember { mutableStateOf(intervalMinutes?.toString().orEmpty()) }
        LaunchedEffect(intervalMinutes) {
            intervalText = intervalMinutes?.toString().orEmpty()
        }
        var delayText by remember { mutableStateOf(state.initialDelayMinutes.toString()) }
        LaunchedEffect(state.initialDelayMinutes) {
            delayText = state.initialDelayMinutes.toString()
        }
        if (intervalMinutes != null && !state.runOnce) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!state.runOnce) {
                        Text("Repeat interval", style = MaterialTheme.typography.titleSmall)
                        Text("Run cleaning repeatedly at a fixed interval.", style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Every", style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.width(6.dp))
                            IconButton(onClick = { onIntervalMinutesChanged(intervalMinutes - 15) }) {
                                Icon(Icons.TwoTone.Remove, contentDescription = "Decrease interval")
                            }
                            CompactMinuteField(
                                value = intervalText,
                                onValueChange = { input ->
                                    if (input.matches(MINUTE_INPUT_REGEX)) {
                                        intervalText = input
                                        input.toLongOrNull()?.takeIf { it in 15L..43_200L }
                                            ?.let(onIntervalMinutesChanged)
                                    }
                                },
                                onDone = { intervalText = intervalMinutes.toString() },
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { onIntervalMinutesChanged(intervalMinutes + 15) }) {
                                Icon(Icons.TwoTone.Add, contentDescription = "Increase interval")
                            }
                            Text("min", style = MaterialTheme.typography.labelSmall)
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
                    }
                }
            }
        }
        if (intervalMinutes != null) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!state.runOnce) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Start after (initial delay)", style = MaterialTheme.typography.titleSmall)
                                Text("Wait before the first run.", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(
                                checked = state.initialDelayMinutes > 0,
                                onCheckedChange = { onInitialDelayChanged(if (it) 5L else 0L) },
                            )
                        }
                    } else {
                        Text("Run once after", style = MaterialTheme.typography.titleSmall)
                        Text("Run cleaning once and then disable this schedule.", style = MaterialTheme.typography.bodySmall)
                    }
                    if (state.runOnce || state.initialDelayMinutes > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("After", style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.width(6.dp))
                            IconButton(onClick = { onInitialDelayChanged(state.initialDelayMinutes - 1) }) {
                                Icon(Icons.TwoTone.Remove, contentDescription = "Decrease initial delay")
                            }
                            CompactMinuteField(
                                value = delayText,
                                onValueChange = { input ->
                                    if (input.matches(MINUTE_INPUT_REGEX)) {
                                        delayText = input
                                        val minimum = if (state.runOnce) 1L else 0L
                                        input.toLongOrNull()?.takeIf { it in minimum..43_200L }
                                            ?.let(onInitialDelayChanged)
                                    }
                                },
                                onDone = { delayText = state.initialDelayMinutes.toString() },
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { onInitialDelayChanged(state.initialDelayMinutes + 1) }) {
                                Icon(Icons.TwoTone.Add, contentDescription = "Increase initial delay")
                            }
                            Text("min", style = MaterialTheme.typography.labelSmall)
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
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Info, null, modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(9.dp))
                Text(
                    text = when {
                        state.runOnce -> "Cleaning runs once after ${state.initialDelayMinutes} min."
                        intervalMinutes != null && state.initialDelayMinutes > 0 ->
                            "Starts in ${state.initialDelayMinutes} min, then repeats every $intervalMinutes min."
                        intervalMinutes != null -> "Repeats every $intervalMinutes min."
                        else -> "Runs at ${formatTime(state.hour, state.minute)} every ${state.repeatInterval.toDays()} days."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Button(
            onClick = onSave,
            enabled = state.canSave && areMinuteFieldsValid(state, intervalText, delayText) &&
                (state.intervalMinutes != null || parseTime(timeText)?.let {
                    it.first == state.hour && it.second == state.minute
                } == true),
            modifier = Modifier.fillMaxWidth().height(52.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFFC89FFF), Color(0xFF965BF0)))),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color(0xFF2E174A),
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Icon(Icons.Rounded.Check, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(CommonR.string.general_save_action), style = MaterialTheme.typography.titleSmall)
        }
        TextButton(
            onClick = { showCleaningOptions = !showCleaningOptions },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(if (showCleaningOptions) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Cleaning options")
        }
        if (showCleaningOptions ||
            (state.isEnabled && !state.useAppCleaner && !state.useSystemCleaner && !state.useCorpseFinder)) {
            OutlinedTextField(
                value = state.label.orEmpty(),
                onValueChange = onLabelChanged,
                label = { Text(stringResource(R.string.scheduler_schedule_name_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            )
            Text("What to clean", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    listOf(
                        Triple("App cache", state.useAppCleaner, onAppCleanerChanged),
                        Triple("Temporary & system files", state.useSystemCleaner, onSystemCleanerChanged),
                        Triple("Obsolete files", state.useCorpseFinder, onCorpseFinderChanged),
                    ).forEach { (title, checked, onChange) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = checked, onCheckedChange = onChange)
                        }
                    }
                }
            }
            if (state.isEnabled && !state.useAppCleaner && !state.useSystemCleaner && !state.useCorpseFinder) {
                Text(
                    "Select at least one cleaning tool before enabling the scheduler.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
    
        }
        Text("Android background restrictions can affect exact run times.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp))
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

@Composable
private fun SchedulerModeButton(
    selected: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(13.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 3.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, null, modifier = Modifier.size(20.dp),
                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(3.dp))
            Text(label, maxLines = 2, textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun CompactMinuteField(
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Invalid or partially typed minute values must not silently save the previous valid value. */
internal fun areMinuteFieldsValid(
    state: ScheduleItemViewModel.State,
    intervalText: String,
    delayText: String,
): Boolean = when {
    state.intervalMinutes == null -> true
    state.runOnce -> delayText.toLongOrNull()?.let { it in 1L..43_200L } == true
    else -> {
        val intervalValid = intervalText.toLongOrNull()?.let { it in 15L..43_200L } == true
        val delayValid = state.initialDelayMinutes == 0L ||
            delayText.toLongOrNull()?.let { it in 1L..43_200L } == true
        intervalValid && delayValid
    }
}

private fun formatTime(hour: Int?, minute: Int?): String {
    if (hour == null || minute == null) return ""
    return "%02d:%02d".format(hour, minute)
}

private val TIME_INPUT_REGEX = Regex("^(\\d{1,2}):(\\d{2})$")
private val DAYS_INPUT_REGEX = Regex("^\\d{1,2}$")
private val MINUTE_INPUT_REGEX = Regex("^\\d{0,5}$")

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
