package eu.darken.sdmse.cleaner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.layout.SdmBrandTopAppBar
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.ui.dashboard.BottomBarState
import eu.darken.sdmse.main.ui.dashboard.DashboardViewModel
import eu.darken.sdmse.main.ui.dashboard.cards.*
import java.time.Instant
import kotlinx.coroutines.delay

/** Main screen mirrors the new violet Settings and Scheduler UI, without changing cleaning actions. */
@Composable
internal fun CleanerHome(
    listState: DashboardViewModel.ListState?,
    bottomBarState: BottomBarState?,
    snackbarHostState: SnackbarHostState,
    onMainAction: () -> Unit,
) {
    val busy = bottomBarState?.actionState in setOf(
        BottomBarState.Action.WORKING,
        BottomBarState.Action.WORKING_CANCELABLE,
    )
    val visible = listState?.items?.filter(::isCleanerHomeItem).orEmpty()
    Scaffold(
        topBar = {
            SdmBrandTopAppBar(
                title = { Text("Home") },
                navigationIcon = { Icon(Icons.Rounded.Home, null, Modifier.padding(start = 16.dp)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "cleaner-hero") {
                CleanerHomeHero(bottomBarState = bottomBarState, busy = busy, onMainAction = onMainAction)
            }
            item(key = "cleaner-section") {
                Text(
                    "Cleaning tools",
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            items(visible, key = { it.stableId }) { item ->
                when (item) {
                    is ToolDashboardCardItem -> CleanerHomeToolCard(item)
                    is SchedulerDashboardCardItem -> CleanerHomeScheduleCard(item)
                    else -> DashboardListCard(item = item)
                }
            }
        }
    }
}

@Composable
private fun CleanerHomeHero(
    bottomBarState: BottomBarState?,
    busy: Boolean,
    onMainAction: () -> Unit,
) {
    val purple = Color(0xFF7540BF)
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .background(Brush.linearGradient(listOf(Color(0xFF3A275A), purple)))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Your cleaner dashboard", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Text("Keep your device clutter-free", color = Color(0xFFE6D6FF), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(
                if (busy) "Cleaning in progress"
                else if (bottomBarState?.isReady == true) "Ready for your next cleanup"
                else "Preparing your cleaning tools",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFF0E4FF),
            )
            Button(
                onClick = onMainAction,
                enabled = bottomBarState?.isReady == true &&
                    bottomBarState.actionState != BottomBarState.Action.WORKING,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE2C5FF),
                    contentColor = Color(0xFF2B1644),
                ),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(26.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                } else {
                    Icon(Icons.Rounded.CleaningServices, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    when (bottomBarState?.actionState) {
                        BottomBarState.Action.DELETE -> "Review & clean"
                        BottomBarState.Action.ONECLICK -> "Clean now"
                        BottomBarState.Action.WORKING_CANCELABLE -> "Stop cleaning"
                        BottomBarState.Action.WORKING -> "Working…"
                        else -> "Scan for unwanted files"
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}

@Composable
private fun CleanerHomeToolCard(item: ToolDashboardCardItem) {
    val title: String
    val description: String
    val icon: androidx.compose.ui.graphics.vector.ImageVector
    when (item.toolType) {
        SDMTool.Type.SYSTEMCLEANER -> {
            title = "System Cleaner"
            description = "Temporary files, empty folders and system junk"
            icon = Icons.Rounded.CleaningServices
        }
        SDMTool.Type.APPCLEANER -> {
            title = "App Cleaner"
            description = "Free space from application cache"
            icon = Icons.Rounded.Apps
        }
        SDMTool.Type.CORPSEFINDER -> {
            title = "Obsolete files"
            description = "Leftover data from uninstalled apps"
            icon = Icons.Rounded.FolderDelete
        }
        SDMTool.Type.DEDUPLICATOR -> {
            title = "Duplicate files"
            description = "Find and review identical copies"
            icon = Icons.Rounded.ContentCopy
        }
        else -> return
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.padding(12.dp).size(24.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (item.isInitializing || item.progress != null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else if (item.result != null) {
                Text(
                    "Scan results available — review before deleting.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                if (item.progress != null) {
                    OutlinedButton(onClick = item.onCancel) {
                        Icon(Icons.Rounded.Stop, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Cancel")
                    }
                } else {
                    if (item.onDelete != null) {
                        TextButton(onClick = item.onViewDetails) {
                            Text("Details")
                        }
                        TextButton(onClick = { item.onDelete.invoke() }) {
                            Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Clean")
                        }
                    }
                    Button(
                        onClick = item.onScan,
                        enabled = !item.isInitializing,
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Icon(Icons.Rounded.FolderOpen, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Scan")
                    }
                }
            }
        }
    }
}

@Composable
private fun CleanerHomeScheduleCard(item: SchedulerDashboardCardItem) {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(item.schedulerState) {
        while (true) {
            delay(30_000L)
            now = Instant.now()
        }
    }
    val enabled = item.schedulerState.schedules.filter { it.isEnabled }
    val next = enabled.mapNotNull { schedule ->
        runCatching { schedule.calcExecutionEta(now, reschedule = false) }.getOrNull()
    }.minOrNull()
    val minutes = next?.let { (it.seconds.coerceAtLeast(0L) + 59L) / 60L }
    Card(
        onClick = item.onManageClicked,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Rounded.EventRepeat, null, Modifier.padding(12.dp).size(26.dp),
                        tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Cleaning Scheduler", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Automate cleaning every X minutes or at a set time",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(40.dp),
                    color = if (enabled.isNotEmpty()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(
                        if (enabled.isNotEmpty()) "Enabled" else "Paused",
                        color = if (enabled.isNotEmpty()) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Schedule, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        item.isInitializing -> "Checking schedule…"
                        minutes != null -> "Next cleanup in $minutes min"
                        enabled.isEmpty() -> "No active schedule"
                        else -> "Waiting for next cleanup"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Rounded.ChevronRight, "Manage scheduler")
            }
        }
    }
}

internal fun isCleanerHomeItem(item: DashboardItem): Boolean = when (item) {
    is ToolDashboardCardItem -> item.toolType in setOf(
        SDMTool.Type.CORPSEFINDER,
        SDMTool.Type.SYSTEMCLEANER,
        SDMTool.Type.APPCLEANER,
        SDMTool.Type.DEDUPLICATOR,
    )
    is SchedulerDashboardCardItem, is SetupDashboardCardItem,
    is ErrorDataAreaDashboardCardItem, is DryRunWarningDashboardCardItem -> true
    else -> false
}
