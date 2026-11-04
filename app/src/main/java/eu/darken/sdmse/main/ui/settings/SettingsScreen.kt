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
import eu.darken.sdmse.corpsefinder.ui.CorpseFinderSettingsRoute
import eu.darken.sdmse.systemcleaner.ui.SystemCleanerSettingsRoute
import eu.darken.sdmse.appcleaner.ui.AppCleanerSettingsRoute
import eu.darken.sdmse.deduplicator.ui.DeduplicatorSettingsRoute
import androidx.compose.material.icons.automirrored.rounded.ArrowBack

@Composable
fun SettingsScreenHost() {
    val nav = requireNotNull(LocalNavigationController.current)
    val status by CleanerUpdater.controller.status.collectAsState()
    SettingsPage("Settings", root = true) {
        SettingsPreferenceItem(title = "Appearance", subtitle = "System, light, or dark theme", icon = Icons.Rounded.Palette,
            onClick = { nav.goTo(CleanerSettingsRoute("appearance")) })
        SettingsPreferenceItem(title = "Cleaning tools", subtitle = "App caches, leftover files, system files, and duplicates", icon = Icons.Rounded.CleaningServices,
            onClick = { nav.goTo(CleanerSettingsRoute("tools")) })
        SettingsPreferenceItem(title = "Cleaning schedules", subtitle = "Choose when automatic cleaning runs", icon = Icons.Rounded.Schedule,
            onClick = { nav.goTo(SchedulerManagerRoute) })
        SettingsPreferenceItem(title = "Exclusions", subtitle = "Files and apps to keep", icon = Icons.Rounded.Shield,
            onClick = { nav.goTo(ExclusionsListRoute) })
        SettingsPreferenceItem(title = "Permissions & setup", subtitle = "Review access needed by cleaning tools", icon = Icons.Rounded.Security,
            onClick = { nav.goTo(SetupRoute(options = SetupScreenOptions(showCompleted = true))) })
        SettingsPreferenceItem(title = "Backup & restore", subtitle = "Save or restore your cleaning configuration", icon = Icons.Rounded.SettingsBackupRestore,
            onClick = { nav.goTo(BackupRestoreRoute) })
        HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
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
