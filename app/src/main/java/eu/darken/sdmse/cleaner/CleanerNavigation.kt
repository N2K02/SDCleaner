package eu.darken.sdmse.cleaner

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.n2k02.updater.N2K02UpdateBadgedIcon

/** Neutral rounded bottom dock, shared by root Home and Settings only. */
@Composable
fun CleanerNavigation(settings: Boolean, onHome: () -> Unit, onSettings: () -> Unit, onQuit: () -> Unit) {
    val status by CleanerUpdater.controller.status.collectAsState()
    Surface(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(23.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
        ) {
            val colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            )
            NavigationBarItem(selected = !settings, onClick = onHome, colors = colors,
                icon = { Icon(Icons.Rounded.Home, "Home") }, label = { Text("Home") })
            NavigationBarItem(selected = settings, onClick = onSettings, colors = colors,
                icon = { N2K02UpdateBadgedIcon(status) { Icon(Icons.Rounded.Settings, "Settings") } },
                label = { Text("Settings") })
            NavigationBarItem(selected = false, onClick = onQuit, colors = colors,
                icon = { Icon(Icons.Rounded.PowerSettingsNew, "Quit") }, label = { Text("Quit") })
        }
    }
}
