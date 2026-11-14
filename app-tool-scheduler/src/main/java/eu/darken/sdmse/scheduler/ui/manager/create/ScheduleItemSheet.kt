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

    val minutes = state.intervalMinutes ?: 360L
    var customText by remember { mutableStateOf(minutes.toString()) }
    var custom by rememberSaveable { mutableStateOf(minutes !in listOf(60L, 360L, 720L, 1440L)) }
    LaunchedEffect(minutes) { customText = minutes.toString() }
    val valid = !custom || customText.toLongOrNull()?.let { it in 15L..43_200L } == true
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(16.dp).navigationBarsPadding().imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Enable scheduler", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(checked = state.isEnabled, onCheckedChange = onEnabledChanged)
            }
        }
        Text("Clean every", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(60L, 360L, 720L, 1440L).forEach { value ->
                FilterChip(selected = !custom && minutes == value,
                    onClick = { custom = false; onIntervalMinutesChanged(value) },
                    label = { Text("${value / 60}h") }, modifier = Modifier.weight(1f))
            }
        }
        FilterChip(selected = custom, onClick = { custom = true }, label = { Text("Custom interval") })
        if (custom) {
            OutlinedTextField(value = customText, onValueChange = { text ->
                if (text.matches(Regex("^\\d{0,5}$"))) {
                    customText = text
                    text.toLongOrNull()?.takeIf { it in 15L..43_200L }?.let(onIntervalMinutesChanged)
                }
            }, label = { Text("Minutes") }, supportingText = { Text("15–43200 minutes") },
                isError = !valid, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
        Text("First cleanup after the selected interval, then repeats automatically.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("What to clean", style = MaterialTheme.typography.titleMedium)
        Card {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                listOf(
                    Triple("App cache", state.useAppCleaner, onAppCleanerChanged),
                    Triple("Temporary files", state.useSystemCleaner, onSystemCleanerChanged),
                    Triple("Leftover files", state.useCorpseFinder, onCorpseFinderChanged),
                ).forEach { (label, checked, change) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(1f))
                        Switch(checked = checked, onCheckedChange = change)
                    }
                }
            }
        }
        if (state.isEnabled && !state.useAppCleaner && !state.useSystemCleaner && !state.useCorpseFinder) {
            Text("Select at least one cleaning option.", color = MaterialTheme.colorScheme.error)
        }
        Button(onClick = onSave, enabled = state.canSave && valid,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(CommonR.string.general_save_action))
        }
        Text("Run times may vary to save battery.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
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
