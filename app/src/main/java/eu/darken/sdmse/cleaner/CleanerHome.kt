package eu.darken.sdmse.cleaner

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.layout.SdmBrandTopAppBar
import eu.darken.sdmse.main.ui.dashboard.*
import eu.darken.sdmse.main.ui.dashboard.cards.*

/** Cleanup-only presentation; actions still use the existing engine and confirmation dialogs. */
@Composable
internal fun CleanerHome(
    listState: DashboardViewModel.ListState?,
    bottomBarState: BottomBarState?,
    snackbarHostState: SnackbarHostState,
    onMainAction: () -> Unit,
) {
    val busy = bottomBarState?.actionState in setOf(BottomBarState.Action.WORKING, BottomBarState.Action.WORKING_CANCELABLE)
    val visible = listState?.items?.filter(::isCleanerHomeItem).orEmpty()
    Scaffold(
        topBar = { SdmBrandTopAppBar(title = { Text("Home") }, navigationIcon = { Icon(Icons.Rounded.Home, null, Modifier.padding(start = 16.dp)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item {
                Card(Modifier.fillMaxWidth().padding(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("A little more breathing room", style = MaterialTheme.typography.headlineSmall)
                        Text("Review unwanted files, clean app caches, and keep a regular cleaning schedule.", style = MaterialTheme.typography.bodyMedium)
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) { Text(if (busy) "Cleaning in progress" else if (bottomBarState?.isReady == true) "Ready" else "Preparing", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium) }
                        Button(onClick = onMainAction, enabled = bottomBarState?.isReady == true && bottomBarState.actionState != BottomBarState.Action.WORKING) {
                            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(when (bottomBarState?.actionState) {
                                BottomBarState.Action.DELETE -> "Review & clean"
                                BottomBarState.Action.ONECLICK -> "Clean now"
                                BottomBarState.Action.WORKING_CANCELABLE -> "Stop cleaning"
                                BottomBarState.Action.WORKING -> "Working…"
                                else -> "Scan for unwanted files"
                            }, Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
            items(visible, key = { it.stableId }) { item -> DashboardListCard(item = item) }
        }
    }
}
internal fun isCleanerHomeItem(item: DashboardItem): Boolean = when (item) {
    is ToolDashboardCardItem -> item.toolType in setOf(
        eu.darken.sdmse.main.core.SDMTool.Type.CORPSEFINDER,
        eu.darken.sdmse.main.core.SDMTool.Type.SYSTEMCLEANER,
        eu.darken.sdmse.main.core.SDMTool.Type.APPCLEANER,
        eu.darken.sdmse.main.core.SDMTool.Type.DEDUPLICATOR,
    )
    is SchedulerDashboardCardItem, is SetupDashboardCardItem,
    is ErrorDataAreaDashboardCardItem, is DryRunWarningDashboardCardItem -> true
    else -> false
}
