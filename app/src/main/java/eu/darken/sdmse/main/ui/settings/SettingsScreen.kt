package eu.darken.sdmse.main.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.platform.LocalContext
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
    val quickVm: CleaningQuickSettingsViewModel = hiltViewModel()
    val quickState by quickVm.state.collectAsStateWithLifecycle()

    SettingsPage("Settings", root = true) {
        Spacer(Modifier.height(4.dp))
        Text(
            "Cleaning",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 24.dp, bottom = 4.dp, top = 4.dp),
        )
        CleanerQuickAction("Cache cleanup", "Remove public app cache files", Icons.Rounded.DeleteSweep,
            quickState.cache, quickVm::setCacheEnabled, onClick = { nav.goTo(AppCleanerSettingsRoute) })
        CleanerQuickAction("Temp files", "Remove temporary files", Icons.Rounded.Description,
            quickState.temp, quickVm::setTempEnabled, onClick = { nav.goTo(SystemCleanerSettingsRoute) })
        CleanerQuickAction("Obsolete files", "Remove leftover public app data", Icons.Rounded.FolderDelete,
            quickState.obsolete, quickVm::setObsoleteEnabled, onClick = { nav.goTo(CorpseFinderSettingsRoute) })
        CleanerQuickAction("Empty folders", "Remove empty directories", Icons.Rounded.FolderOpen,
            quickState.emptyFolders, quickVm::setEmptyFoldersEnabled, onClick = { nav.goTo(SystemCleanerSettingsRoute) })

        Spacer(Modifier.height(6.dp))
        SchedulerOverviewCard(
            schedules = schedulerState.schedules,
            onClick = { nav.goTo(SchedulerManagerRoute) },
        )
        CompactSettingsLink("Cleaning rules", "Customize what to clean", Icons.Rounded.Tune) {
            nav.goTo(CleanerSettingsRoute("tools"))
        }
        CompactSettingsLink("Excluded folders", "Folders to keep (skip cleaning)", Icons.Rounded.FolderOff) {
            nav.goTo(ExclusionsListRoute)
        }
        HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        Text(
            "App",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 24.dp, bottom = 4.dp),
        )
        CompactSettingsLink("About", "App info and version", Icons.Rounded.Info) {
            nav.goTo(CleanerSettingsRoute("about"))
        }
        CompactSettingsLink(
            "Updates",
            if (status.hasPendingUpdate()) "A new update is available" else "Check for app updates",
            Icons.Rounded.SystemUpdate,
            trailing = { N2K02UpdateBadge(status) },
        ) { nav.goTo(CleanerSettingsRoute("updates")) }

        HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        Text(
            "Advanced",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 24.dp, bottom = 4.dp),
        )
        CompactSettingsLink("Appearance", "System, light, or dark theme", Icons.Rounded.Palette) {
            nav.goTo(CleanerSettingsRoute("appearance"))
        }
        CompactSettingsLink("Permissions & setup", "Access needed by cleaning tools", Icons.Rounded.Security) {
            nav.goTo(SetupRoute(options = SetupScreenOptions(showCompleted = true)))
        }
        CompactSettingsLink("Backup & restore", "Save or restore configuration", Icons.Rounded.SettingsBackupRestore) {
            nav.goTo(BackupRestoreRoute)
        }
    }
}

internal fun scheduleOverviewMode(schedule: Schedule?): String = when {
    schedule == null -> "No active schedule"
    schedule.runOnce -> "Once after ${schedule.initialDelayMinutes} min"
    schedule.intervalMinutes != null -> "Repeat every ${schedule.intervalMinutes} min"
    else -> "At ${schedule.hour.toString().padStart(2, '0')}:${schedule.minute.toString().padStart(2, '0')}"
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
            schedule.calcExecutionEta(now, reschedule = false)?.let { schedule to it }
        }.getOrNull()
    }.minByOrNull { it.second }
    val selected = next?.first ?: enabled.firstOrNull()
    val nextMinutes = next?.second?.seconds?.coerceAtLeast(0L)?.let { (it + 59L) / 60L }
    val statusLabel = when {
        enabled.isNotEmpty() -> "Enabled"
        schedules.isNotEmpty() -> "Paused"
        else -> "Not configured"
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    ),
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                ) {
                    Icon(
                        Icons.Rounded.EventRepeat,
                        null,
                        modifier = Modifier.padding(9.dp).size(26.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Scheduler", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Automate cleaning after a delay or repeat every X minutes",
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (enabled.isNotEmpty()) Color(0xFF68DF83)
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(
                        statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (enabled.isNotEmpty()) Color(0xFF14251A)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    )
                }
                Icon(Icons.Rounded.ChevronRight, null, modifier = Modifier.size(19.dp))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Icon(Icons.Rounded.Schedule, null, modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Text(
                    scheduleOverviewMode(selected),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier.width(1.dp).height(16.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                )
                Text(
                    when {
                        nextMinutes != null -> "Starts in ${nextMinutes} min"
                        enabled.isNotEmpty() -> "Waiting for next run"
                        else -> "Inactive"
                    },
                    maxLines = 1,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun CleanerQuickAction(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp).size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(6.dp))
            Switch(
                checked = checked,
                onCheckedChange = onToggle,
                modifier = Modifier,
            )
        }
    }
}

@Composable
private fun CompactSettingsLink(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 1.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp)
                .padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Icon(icon, null, Modifier.padding(8.dp).size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (trailing != null) trailing()
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Rounded.ChevronRight, null, modifier = Modifier.size(19.dp))
        }
    }
}

internal fun cleanerSettingsTitle(section: String): String? = when (section) {
    "appearance" -> "Appearance"
    "tools" -> "Cleaning tools"
    "updates" -> "Updates"
    "about" -> "About"
    else -> null
}

/** Display the version actually installed on the device, not upstream BuildConfigWrap metadata. */
internal fun formatInstalledVersion(versionName: String?, versionCode: Long): String {
    val name = versionName?.trim()?.takeIf { it.isNotEmpty() } ?: "Unknown"
    return "Version $name ($versionCode)"
}

@Suppress("DEPRECATION")
@Composable
private fun InstalledAppAbout() {
    val context = LocalContext.current
    val version = remember(context) {
        runCatching {
            val installed = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (android.os.Build.VERSION.SDK_INT >= 28) {
                installed.longVersionCode
            } else {
                installed.versionCode.toLong()
            }
            formatInstalledVersion(installed.versionName, code)
        }.getOrElse { "Version unavailable" }
    }
    ListItem(headlineContent = { Text("SDCleaner") }, supportingContent = { Text(version) })
    ListItem(
        headlineContent = { Text("Android ${android.os.Build.VERSION.RELEASE}") },
        supportingContent = { Text("${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}") },
    )
}

@Composable
fun CleanerSettingsScreenHost(section: String) {
    val title = cleanerSettingsTitle(section)
    // Saved back stacks from older builds may still contain "diagnostics".
    // Navigate those users to real Settings rather than recreating a removed page.
    if (title == null) {
        SettingsScreenHost()
        return
    }
    val nav = requireNotNull(LocalNavigationController.current)
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
            "about" -> InstalledAppAbout()
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
                "Updates" -> Icons.Rounded.SystemUpdate
                "About" -> Icons.Rounded.Info
                else -> Icons.Rounded.Settings
            }, "Back")
        }
    })
}

@Composable
private fun SettingsPage(title: String, root: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(topBar = { SettingsHeader(title, root) }) { padding ->
        androidx.compose.foundation.rememberScrollState().let { scroll ->
            Column(Modifier.fillMaxSize().padding(padding).then(Modifier.verticalScroll(scroll)).padding(bottom = 12.dp), content = content)
        }
    }
}
