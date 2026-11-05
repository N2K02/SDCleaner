package eu.darken.sdmse.cleaner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.layout.SdmBrandTopAppBar
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.ui.dashboard.BottomBarState
import eu.darken.sdmse.main.ui.dashboard.DashboardViewModel
import eu.darken.sdmse.main.ui.dashboard.cards.*
import java.time.Instant
import kotlinx.coroutines.delay

/** Compact Home matching the Settings mock, with the same real tool actions. */
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
        .sortedBy(::cleanerHomePriority)

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
                top = padding.calculateTopPadding() + 6.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item(key = "cleaner-hero") {
                CleanerHomeHero(bottomBarState, busy, onMainAction)
            }
            item(key = "cleaner-section") {
                Text(
                    "Cleaning",
                    modifier = Modifier.padding(start = 6.dp, top = 3.dp),
                    style = MaterialTheme.typography.titleSmall,
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

internal fun cleanerHomePriority(item: DashboardItem): Int = when (item) {
    is ToolDashboardCardItem -> when (item.toolType) {
        SDMTool.Type.SYSTEMCLEANER -> 0
        SDMTool.Type.APPCLEANER -> 1
        SDMTool.Type.CORPSEFINDER -> 2
        SDMTool.Type.DEDUPLICATOR -> 3
        else -> 50
    }
    is SchedulerDashboardCardItem -> 4
    else -> 10
}

@Composable
private fun CleanerHomeHero(
    state: BottomBarState?,
    busy: Boolean,
    onMainAction: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            Modifier.background(
                Brush.horizontalGradient(listOf(Color(0xFF32234D), Color(0xFF7245B5))),
            ).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("SDCleaner", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text(
                        if (busy) "Cleanup in progress"
                        else if (state?.isReady == true) "Ready to clean"
                        else "Preparing cleaner…",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE9D9FC),
                    )
                }
            }
            Button(
                onClick = onMainAction,
                enabled = state?.isReady == true && state.actionState != BottomBarState.Action.WORKING,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE6CEFF),
                    contentColor = Color(0xFF2B1644),
                ),
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                shape = RoundedCornerShape(22.dp),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Rounded.CleaningServices, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    when (state?.actionState) {
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
    val icon: ImageVector
    when (item.toolType) {
        SDMTool.Type.SYSTEMCLEANER -> {
            title = "System Cleaner"
            description = "Temp files, empty folders and system junk"
            icon = Icons.Rounded.CleaningServices
        }
        SDMTool.Type.APPCLEANER -> {
            title = "App Cleaner"
            description = "Free space from application caches"
            icon = Icons.Rounded.Apps
        }
        SDMTool.Type.CORPSEFINDER -> {
            title = "Obsolete files"
            description = "Leftover files from removed apps"
            icon = Icons.Rounded.FolderDelete
        }
        SDMTool.Type.DEDUPLICATOR -> {
            title = "Duplicate files"
            description = "Review and remove identical copies"
            icon = Icons.Rounded.ContentCopy
        }
        else -> return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(19.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Icon(
                        icon, null,
                        Modifier.padding(9.dp).size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(7.dp))
                when {
                    item.progress != null -> OutlinedButton(
                        onClick = item.onCancel,
                        modifier = Modifier.heightIn(min = 40.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                    ) { Text("Cancel") }
                    else -> Button(
                        onClick = item.onScan,
                        enabled = !item.isInitializing,
                        modifier = Modifier.heightIn(min = 40.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                    ) {
                        Icon(Icons.Rounded.FolderOpen, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Scan")
                    }
                }
            }
            if (item.progress != null || item.isInitializing) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else if (item.result != null) {
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Results available",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    if (item.onDelete != null) {
                        TextButton(onClick = item.onViewDetails, contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text("Details")
                        }
                        TextButton(onClick = { item.onDelete.invoke() },
                            contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text("Clean")
                        }
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
    val nextMinutes = enabled.mapNotNull {
        runCatching { it.calcExecutionEta(now, reschedule = false) }.getOrNull()
    }.minOrNull()?.seconds?.coerceAtLeast(0L)?.let { (it + 59L) / 60L }

    Card(
        onClick = item.onManageClicked,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        Column(
            Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer),
                    ),
                ).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.EventRepeat, null, Modifier.size(29.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Scheduler", style = MaterialTheme.typography.titleMedium)
                    Text("Automatic cleanup", style = MaterialTheme.typography.bodySmall)
                }
                Surface(shape = RoundedCornerShape(30.dp),
                    color = if (enabled.isNotEmpty()) Color(0xFF68DF83)
                    else MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Text(
                        if (enabled.isNotEmpty()) "Enabled" else "Paused",
                        color = if (enabled.isNotEmpty()) Color(0xFF14251A) else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    )
                }
                Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            Text(
                when {
                    item.isInitializing -> "Checking schedule…"
                    nextMinutes != null -> "Next run in $nextMinutes min"
                    else -> "No active cleanup"
                },
                style = MaterialTheme.typography.labelSmall,
            )
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
