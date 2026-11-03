package eu.darken.sdmse.cleaner

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import dev.n2k02.updater.N2K02UpdateBadgedIcon

@Composable
fun CleanerNavigation(settings: Boolean, onHome: () -> Unit, onSettings: () -> Unit, onQuit: () -> Unit) {
    val status by CleanerUpdater.controller.status.collectAsState()
    NavigationBar {
        NavigationBarItem(selected = !settings, onClick = onHome,
            icon = { Icon(Icons.Rounded.Home, "Home") }, label = { Text("Home") })
        NavigationBarItem(selected = settings, onClick = onSettings,
            icon = { N2K02UpdateBadgedIcon(status) { Icon(Icons.Rounded.Settings, "Settings") } }, label = { Text("Settings") })
        NavigationBarItem(selected = false, onClick = onQuit,
            icon = { Icon(Icons.Rounded.PowerSettingsNew, "Quit") }, label = { Text("Quit") })
    }
}
