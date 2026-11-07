package eu.darken.sdmse.cleaner

import android.os.Environment
import android.os.StatFs
import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.layout.SdmBrandTopAppBar
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.ui.dashboard.BottomBarState
import eu.darken.sdmse.main.ui.dashboard.DashboardViewModel
import eu.darken.sdmse.main.ui.dashboard.cards.*
import eu.darken.sdmse.appcleaner.ui.settings.DirectAdbHomeSetupCard
import java.time.Instant
import kotlinx.coroutines.delay

/** A real storage reading; never display invented percentages or storage amounts. */
internal data class StorageUsage(
    val totalBytes: Long,
    val usedBytes: Long,
    val freeBytes: Long,
) {
    val fractionUsed: Float get() = usedBytes.toDouble().div(totalBytes).toFloat()
    val percentUsed: Int get() = (fractionUsed * 100).toInt()
}

internal fun calculateStorageUsage(totalBytes: Long, freeBytes: Long): StorageUsage? {
    if (totalBytes <= 0L) return null
    val safeFree = freeBytes.coerceIn(0L, totalBytes)
    return StorageUsage(totalBytes, totalBytes - safeFree, safeFree)
}

private fun readDeviceStorage(): StorageUsage? = runCatching {
    val stats = StatFs(Environment.getDataDirectory().absolutePath)
    calculateStorageUsage(stats.totalBytes, stats.availableBytes)
}.getOrNull()

/** Home uses live DashboardViewModel tool actions and never deletes without existing confirmation. */
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
    val tools = visible.filterIsInstance<ToolDashboardCardItem>().sortedBy(::cleanerHomePriority)
    val scheduler = visible.filterIsInstance<SchedulerDashboardCardItem>().firstOrNull()
    val otherCards = visible.filterNot {
        it is ToolDashboardCardItem || it is SchedulerDashboardCardItem
    }

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
                top = padding.calculateTopPadding() + 10.dp,
                bottom = padding.calculateBottomPadding() + 18.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "storage-overview") {
                DeviceStorageCard(bottomBarState, busy, onMainAction)
            }
            item(key = "adb-setup") { DirectAdbHomeSetupCard() }
            item(key = "cleaning-title") {
                Text(
                    "Cleaning tools",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 3.dp, top = 2.dp),
                )
            }
            items(tools.chunked(2), key = { pair -> "tools-${pair.first().stableId}" }) { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { item ->
                        CleanerToolTile(item, modifier = Modifier.weight(1f))
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            scheduler?.let { item(key = "scheduler") { CleanerHomeScheduleCard(it) } }
            items(otherCards, key = { "other-${it.stableId}" }) { item ->
                DashboardListCard(item = item)
            }
        }
    }
}

internal fun cleanerHomePriority(item: DashboardItem): Int = when (item) {
    is ToolDashboardCardItem -> when (item.toolType) {
        SDMTool.Type.SYSTEMCLEANER -> 0
        SDMTool.Type.APPCLEANER -> 1
        SDMTool.Type.DEDUPLICATOR -> 2
        SDMTool.Type.CORPSEFINDER -> 3
        else -> 50
    }
    is SchedulerDashboardCardItem -> 4
    else -> 10
}

@Composable
private fun DeviceStorageCard(
    state: BottomBarState?,
    busy: Boolean,
    onMainAction: () -> Unit,
) {
    val context = LocalContext.current
    // Re-read when the cleaning action enters or exits WORKING so freed space is reflected.
    val usage = remember(busy) { readDeviceStorage() }
    Card(
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.background(
                Brush.horizontalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceContainer,
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ),
            ).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { usage?.fractionUsed ?: 0f },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant,
                        strokeWidth = 7.dp,
                        modifier = Modifier.size(82.dp),
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            usage?.let { "${it.percentUsed}%" } ?: "—",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text("Used", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Device storage", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        usage?.let {
                            "${Formatter.formatShortFileSize(context, it.usedBytes)} of " +
                                "${Formatter.formatShortFileSize(context, it.totalBytes)} used"
                        } ?: "Storage usage unavailable",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                    if (usage != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${Formatter.formatShortFileSize(context, usage.freeBytes)} available",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Button(
                onClick = onMainAction,
                enabled = state?.isReady == true &&
                    state.actionState != BottomBarState.Action.WORKING,
                modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                shape = RoundedCornerShape(24.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.CleaningServices, null, Modifier.size(20.dp))
                }
                Spacer(Modifier.width(9.dp))
                Text(
                    when (state?.actionState) {
                        BottomBarState.Action.DELETE -> "Review & clean"
                        BottomBarState.Action.ONECLICK -> "Quick clean"
                        BottomBarState.Action.WORKING_CANCELABLE -> "Stop cleaning"
                        BottomBarState.Action.WORKING -> "Cleaning…"
                        else -> "Scan for unwanted files"
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}

/**
 * One explicit tap target per action; do not trigger cleanup by tapping a card.
 * When results exist, Review remains the primary path and Clean uses the existing
 * tool callback (including its own confirmation flow).
 */
internal enum class CleanerTileAction { INITIALIZING, CANCEL, REVIEW_AND_CLEAN, SCAN }

internal fun cleanerTileAction(
    initializing: Boolean,
    scanning: Boolean,
    canClean: Boolean,
): CleanerTileAction = when {
    scanning -> CleanerTileAction.CANCEL
    initializing -> CleanerTileAction.INITIALIZING
    canClean -> CleanerTileAction.REVIEW_AND_CLEAN
    else -> CleanerTileAction.SCAN
}

@Composable
private fun CleanerToolTile(item: ToolDashboardCardItem, modifier: Modifier = Modifier) {
    val title: String
    val description: String
    val icon: ImageVector
    when (item.toolType) {
        SDMTool.Type.SYSTEMCLEANER -> {
            title = "System Cleaner"
            description = "Temporary files and empty folders"
            icon = Icons.Rounded.CleaningServices
        }
        SDMTool.Type.APPCLEANER -> {
            title = "App Cleaner"
            description = "Free application cache and data"
            icon = Icons.Rounded.Apps
        }
        SDMTool.Type.DEDUPLICATOR -> {
            title = "Duplicate Files"
            description = "Find identical file copies"
            icon = Icons.Rounded.ContentCopy
        }
        SDMTool.Type.CORPSEFINDER -> {
            title = "Obsolete Files"
            description = "Leftovers from removed apps"
            icon = Icons.Rounded.DeleteOutline
        }
        else -> return
    }
    Card(
        modifier = modifier.height(154.dp),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(6.dp).size(20.dp),
                    )
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            when (cleanerTileAction(item.isInitializing, item.progress != null, item.onDelete != null)) {
                CleanerTileAction.INITIALIZING -> LinearProgressIndicator(Modifier.fillMaxWidth())
                CleanerTileAction.CANCEL -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    OutlinedButton(
                        onClick = item.onCancel,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        Icon(Icons.Rounded.Close, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Cancel", style = MaterialTheme.typography.labelMedium)
                    }
                }
                CleanerTileAction.REVIEW_AND_CLEAN -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Button(
                            onClick = item.onViewDetails,
                            modifier = Modifier.weight(1f).heightIn(min = 42.dp),
                            contentPadding = PaddingValues(horizontal = 3.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("Review", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = { item.onDelete?.invoke() },
                            modifier = Modifier.weight(1f).heightIn(min = 42.dp),
                            contentPadding = PaddingValues(horizontal = 3.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("Clean", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                CleanerTileAction.SCAN -> {
                    Button(
                        onClick = item.onScan,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Rounded.Search, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Scan", style = MaterialTheme.typography.labelMedium)
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
    val upcoming = enabled.mapNotNull { schedule ->
        runCatching { schedule.calcExecutionEta(now, reschedule = false) }.getOrNull()
            ?.let { schedule to it }
    }.minByOrNull { it.second }
    val nextMinutes = upcoming?.second?.seconds?.coerceAtLeast(0L)?.let { (it + 59L) / 60L }
    val selected = upcoming?.first ?: enabled.firstOrNull()
    val cadence = when {
        selected == null -> "No active schedule"
        selected.runOnce -> "One-time cleanup"
        selected.intervalMinutes != null -> "Every ${selected.intervalMinutes} minutes"
        else -> "At ${selected.hour.toString().padStart(2, '0')}:${selected.minute.toString().padStart(2, '0')}"
    }
    Card(
        onClick = item.onManageClicked,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(23.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(13.dp),
                ) {
                    Icon(Icons.Rounded.EventRepeat, null,
                        Modifier.padding(9.dp).size(26.dp),
                        tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Scheduler", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Automatic cleaning at scheduled intervals",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(30.dp),
                    color = if (enabled.isNotEmpty()) Color(0xFF8DDF9F)
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        if (enabled.isNotEmpty()) "Enabled" else "Paused",
                        color = if (enabled.isNotEmpty()) Color(0xFF173A23)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Icon(Icons.Rounded.Schedule, null, Modifier.size(18.dp))
                Text(
                    when {
                        item.isInitializing -> "Checking scheduler…"
                        nextMinutes != null -> "Next in $nextMinutes min"
                        else -> "No upcoming run"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(cadence, style = MaterialTheme.typography.labelSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Rounded.ChevronRight, null, Modifier.size(19.dp))
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
