package eu.darken.sdmse.cleaner

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.layout.SdmBrandTopAppBar

@Composable
fun CleanerIntro(privacy: Boolean = false, onContinue: () -> Unit) {
    Scaffold(topBar = { SdmBrandTopAppBar(title = { Text(if (privacy) "Before cleaning" else "Welcome") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(if (privacy) "Stay in control" else "Welcome to SDCleaner", style = MaterialTheme.typography.headlineMedium)
            Text(if (privacy) "Cleaning may require access to files, usage data, or accessibility. Grant only the access you need. Review findings before deletion; deleted files may not be recoverable."
                else "Free space by reviewing app caches, leftover files, unwanted system files, and duplicate files. Schedule regular cleaning from Settings.")
            if (privacy) Text("Updates are managed in Settings > About & updates. Download and installation remain your choice.")
            Spacer(Modifier.weight(1f))
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
        }
    }
}
