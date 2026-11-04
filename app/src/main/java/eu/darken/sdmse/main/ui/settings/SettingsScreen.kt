package eu.darken.sdmse.main.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.n2k02.updater.*
import eu.darken.sdmse.cleaner.CleanerUpdater
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.compose.layout.SdmBrandTopAppBar
import eu.darken.sdmse.common.compose.settings.SettingsPreferenceItem
import eu.darken.sdmse.common.navigation.LocalNavigationController
import eu.darken.sdmse.main.ui.navigation.*
import eu.darken.sdmse.main.ui.settings.general.GeneralSettingsViewModel
import eu.darken.sdmse.common.theming.ThemeMode
import eu.darken.sdmse.setup.SetupRoute
import eu.darken.sdmse.setup.SetupScreenOptions
import eu.darken.sdmse.exclusion.ui.ExclusionsListRoute
import eu.darken.sdmse.scheduler.ui.SchedulerManagerRoute
import eu.darken.sdmse.scheduler.ui.manager.SchedulerManagerViewModel
import eu.darken.sdmse.scheduler.core.Schedule
import java.time.Instant
import kotlinx.coroutines.delay
import eu.darken.sdmse.corpsefinder.ui.CorpseFinderSettingsRoute
import eu.darken.sdmse.systemcleaner.ui.SystemCleanerSettingsRoute
import eu.darken.sdmse.appcleaner.ui.AppCleanerSettingsRoute
import eu.darken.sdmse.deduplicator.ui.DeduplicatorSettingsRoute
import androidx.compose.material.icons.automirrored.rounded.ArrowBack

@Composable
fun SettingsScreenHost() {
    val nav = requireNotNull(LocalNavigationController.current)
    val status by CleanerUpdater.controller.status.collectAsState()
    val schedulerVm: SchedulerManagerViewModel = hiltViewModel()
    val schedulerState by schedulerVm.state.collectAsStateWithLifecycle()
    SettingsPage("Settings", root = true) {
        Spacer(Modifier.height(12.dp))
        Text(
            "CLEANING",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 24.dp, bottom = 8.dp),
        )
        CleanerQuickAction(
            title = "Cache cleanup",
            description = "Manage app cache cleaning and cleanup methods",
            icon = Icons.Rounded.DeleteSweep,
            onClick = { nav.goTo(AppCleanerSettingsRoute) },
        )
        CleanerQuickAction(
            title = "Temporary & system files",
            description = "Review filters for unwanted files",
            icon = Icons.Rounded.CleaningServices,
            onClick = { nav.goTo(SystemCleanerSettingsRoute) },
        )
        CleanerQuickAction(
            title = "Obsolete files",
            description = "Find leftovers from uninstalled applications",
            icon = Icons.Rounded.FolderDelete,
            onClick = { nav.goTo(CorpseFinderSettingsRoute) },
        )
        CleanerQuickAction(
            title = "Duplicate files",
            description = "Find redundant copies before removing them",
            icon = Icons.Rounded.ContentCopy,
            onClick = { nav.goTo(DeduplicatorSettingsRoute) },
        )
        Spacer(Modifier.height(12.dp))
        SchedulerOverviewCard(
            schedules = schedulerState.schedules,
            onClick = { nav.goTo(SchedulerManagerRoute) },
        )
        SettingsPreferenceItem(title = "Cleaning rules", subtitle = "Configure additional cleaning tools", icon = Icons.Rounded.Tune,
            onClick = { nav.goTo(CleanerSettingsRoute("tools")) })
        SettingsPreferenceItem(title = "Excluded files & apps", subtitle = "Protect items from scheduled cleaning", icon = Icons.Rounded.FolderOff,
            onClick = { nav.goTo(ExclusionsListRoute) })
        HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 14.dp))
        Text(
            "APP",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 24.dp, bottom = 8.dp),
        )
        SettingsPreferenceItem(title = "Appearance", subtitle = "System, light, or dark theme", icon = Icons.Rounded.Palette,
            onClick = { nav.goTo(CleanerSettingsRoute("appearance")) })
        SettingsPreferenceItem(title = "Permissions & setup", subtitle = "Review access needed by cleaning tools", icon = Icons.Rounded.Security,
            onClick = { nav.goTo(SetupRoute(options = SetupScreenOptions(showCompleted = true))) })
        SettingsPreferenceItem(title = "Backup & restore", subtitle = "Save or restore your cleaning configuration", icon = Icons.Rounded.SettingsBackupRestore,
            onClick = { nav.goTo(BackupRestoreRoute) })
        HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 14.dp))
        SettingsPreferenceItem(title = "Diagnostics", subtitle = "App and device information", icon = Icons.Rounded.BugReport,
            onClick = { nav.goTo(CleanerSettingsRoute("diagnostics")) })
        ListItem(
            headlineContent = { Text("About & updates") },
            supportingContent = { Text(if (status.hasPendingUpdate()) "A new update is available" else BuildConfigWrap.VERSION_DESCRIPTION) },
            leadingContent = { Icon(Icons.Rounded.SystemUpdate, null) },
            trailingContent = { N2K02UpdateBadge(status) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp).clip(RoundedCornerShape(20.dp)).clickable { nav.goTo(CleanerSettingsRoute("updates")) },
        )
    }
}

@Composable
private fun SchedulerOverviewCard(
    schedules: List<Schedule>,
    onClick: () -> Unit,
) {
    val enabled = schedules.filter { it.isEnabled }
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(enabled.isNotEmpty()) {
        if (enabled.isNotEmpty()) {
            while (true) {
                delay(30_000)
                now = Instant.now()
            }
        }
    }
    val next = enabled.mapNotNull { schedule ->
        runCatching {
            schedule.calcExecutionEta(now, reschedule = false)
                ?.let { schedule to it }
        }.getOrNull()
    }.minByOrNull { it.second }
    val statusLabel = when {
        enabled.isNotEmpty() -> "Enabled"
        schedules.isNotEmpty() -> "Paused"
        else -> "Not configured"
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                ) {
                    Icon(
                        Icons.Rounded.EventRepeat,
                        null,
                        modifier = Modifier.padding(12.dp).size(28.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Scheduler", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Automate cleaning at a set time or repeat every X minutes",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (enabled.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (enabled.isNotEmpty()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(Icons.Rounded.Schedule, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                val text = when {
                    next != null -> {
                        val mins = (next.second.toMinutes() + if (next.second.seconds % 60L != 0L) 1L else 0L)
                        "Next run in ${mins.coerceAtLeast(0)} min"
                    }
                    enabled.isEmpty() -> "No active cleanups"
                    else -> "Waiting for next cleanup"
                }
                Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                Text("${enabled.size} active", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Rounded.ChevronRight, null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun CleanerQuickAction(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Icon(icon, null, Modifier.padding(10.dp).size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun CleanerSettingsScreenHost(section: String) {
    val nav = requireNotNull(LocalNavigationController.current)
    val title = when (section) {
        "appearance" -> "Appearance"
        "tools" -> "Cleaning tools"
        "updates" -> "About & updates"
        else -> "Diagnostics"
    }
    SettingsPage(title) {
        when (section) {
            "updates" -> {
                val experimental by CleanerUpdater.experimental.collectAsState()
                N2K02UpdateScreen(controller = CleanerUpdater.controller, installGuard = CleanerUpdater.guard,
                    experimentalEnabled = experimental, onExperimentalEnabledChange = CleanerUpdater::setExperimental,
                    modifier = Modifier.padding(16.dp))
            }
            "tools" -> {
                SettingsPreferenceItem(title = "Leftover files", subtitle = "Data left behind by uninstalled apps", icon = Icons.Rounded.FolderDelete, onClick = { nav.goTo(CorpseFinderSettingsRoute) })
                SettingsPreferenceItem(title = "System cleaner", subtitle = "Filters for unwanted files", icon = Icons.Rounded.CleaningServices, onClick = { nav.goTo(SystemCleanerSettingsRoute) })
                SettingsPreferenceItem(title = "App cleaner", subtitle = "Cache and app cleaning options", icon = Icons.Rounded.Apps, onClick = { nav.goTo(AppCleanerSettingsRoute) })
                SettingsPreferenceItem(title = "Duplicate files", subtitle = "Find redundant copies for review", icon = Icons.Rounded.ContentCopy, onClick = { nav.goTo(DeduplicatorSettingsRoute) })
            }
            "appearance" -> AppearanceSettings()
            else -> {
                ListItem(headlineContent = { Text("SDCleaner") }, supportingContent = { Text(BuildConfigWrap.VERSION_DESCRIPTION) })
                ListItem(headlineContent = { Text("Android ${android.os.Build.VERSION.RELEASE}") }, supportingContent = { Text("${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}") })
                Text("Cleaning results depend on the access granted in Permissions & setup. Review scan results before deleting files.", Modifier.padding(24.dp))
            }
        }
    }
}

@Composable
private fun AppearanceSettings(vm: GeneralSettingsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    Text("Use device colors when supported. Choose when to use the dark appearance.", Modifier.padding(24.dp))
    ThemeMode.entries.forEach { mode ->
        ListItem(headlineContent = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
            leadingContent = { RadioButton(selected = state.themeMode == mode, onClick = { vm.setThemeMode(mode) }) })
    }
}

@Composable
private fun SettingsHeader(title: String, root: Boolean = false) {
    val nav = requireNotNull(LocalNavigationController.current)
    SdmBrandTopAppBar(title = { Text(title, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }, navigationIcon = {
        if (root) Icon(Icons.Rounded.Settings, null, Modifier.padding(start = 16.dp))
        else IconButton(onClick = { nav.up() }) {
            Icon(when (title) {
                "Appearance" -> Icons.Rounded.Palette
                "Cleaning tools" -> Icons.Rounded.CleaningServices
                "About & updates" -> Icons.Rounded.SystemUpdate
                else -> Icons.Rounded.BugReport
            }, "Back")
        }
    })
}

@Composable
private fun SettingsPage(title: String, root: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(topBar = { SettingsHeader(title, root) }) { padding ->
        androidx.compose.foundation.rememberScrollState().let { scroll ->
            Column(Modifier.fillMaxSize().padding(padding).then(Modifier.verticalScroll(scroll)).padding(bottom = 24.dp), content = content)
        }
    }
}
