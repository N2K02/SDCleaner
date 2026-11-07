package eu.darken.sdmse.appcleaner.ui.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SettingsInputAntenna
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.adb.local.DirectAdb
import eu.darken.sdmse.common.adb.local.DirectAdbOptions
import eu.darken.sdmse.common.adb.local.DirectAdbRecovery
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class DirectAdbSettingsViewModel @Inject constructor(
    private val adb: DirectAdb,
    private val recovery: DirectAdbRecovery,
    @ApplicationContext private val app: Context,
) : ViewModel() {
    val status = adb.status
    val discovery = recovery.state

    fun enterSettings() = recovery.enterSettings()
    fun leaveSettings() = recovery.leaveSettings()
    fun enabled(option: DirectAdbOptions.Option) = DirectAdbOptions.get(app, option)
    fun set(option: DirectAdbOptions.Option, checked: Boolean) {
        DirectAdbOptions.set(app, option, checked)
        if (option == DirectAdbOptions.Option.TCP_5555 && !checked) {
            recovery.disableTcp()
        } else {
            recovery.onSettingsChanged()
        }
    }
    fun pairedPreviously() = DirectAdbOptions.wasAuthorized(app)
    fun lastVerifiedAt() = DirectAdbOptions.verifiedAt(app)
    fun previousTransport() = DirectAdbOptions.transport(app)

    fun pair(port: String, code: String) =
        viewModelScope.launch { adb.pair(port.toIntOrNull() ?: -1, code) }
    fun connectTls(port: String) =
        viewModelScope.launch { adb.connectTls(port.toIntOrNull() ?: -1) }
    fun reconnect() = recovery.enterSettings()
    fun disconnect() = recovery.manualDisconnect()
}

@Composable
fun DirectAdbSettingsCard(vm: DirectAdbSettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val status by vm.status.collectAsStateWithLifecycle()
    val discovery by vm.discovery.collectAsStateWithLifecycle()
    DisposableEffect(vm) {
        vm.enterSettings()
        onDispose { vm.leaveSettings() }
    }
    var manualSetup by remember { mutableStateOf(false) }
    var pairingPort by remember { mutableStateOf("") }
    var pairingCode by remember { mutableStateOf("") }
    var connectPort by remember { mutableStateOf("") }
    var autoPair by remember { mutableStateOf(vm.enabled(DirectAdbOptions.Option.AUTO_PAIR)) }
    val enabledServices = runCatching {
        Settings.Secure.getString(context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
    }.getOrDefault("")
    val accessibilityEnabled = enabledServices.contains(
        "DirectAdbPairingAccessibilityService", ignoreCase = true)

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    androidx.compose.material3.Icon(Icons.Rounded.SettingsInputAntenna,
                        contentDescription = null)
                    Text("Pairing & recovery", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold)
                }
                DirectAdbSettingRow(
                    title = "Auto-detect pairing code",
                    detail = "Read only Android Settings' Wireless debugging pairing dialog.",
                    option = DirectAdbOptions.Option.AUTO_PAIR, vm = vm,
                    onChange = { autoPair = it },
                )
                if (autoPair && !accessibilityEnabled) {
                    Text("Accessibility is needed only to read Android's pairing code. " +
                        "It is never used to automate cache cleaning.",
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }) { Text("Enable Accessibility") }
                }
                HorizontalDivider()
                DirectAdbSettingRow(
                    title = "Connection watchdog",
                    detail = "Check connection health and recover automatically when possible.",
                    option = DirectAdbOptions.Option.WATCHDOG, vm = vm,
                )
                DirectAdbSettingRow(
                    title = "Start on boot",
                    detail = "Attempt authorized ADB recovery after restart.",
                    option = DirectAdbOptions.Option.START_ON_BOOT, vm = vm,
                )
            }
        }
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    androidx.compose.material3.Icon(Icons.Rounded.Security, contentDescription = null)
                    Text("Connection & security", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold)
                }
                DirectAdbSettingRow(
                    title = "Restore after restart",
                    detail = "Restore Wireless debugging using saved authorization, when permitted by Android.",
                    option = DirectAdbOptions.Option.RESTORE_AFTER_RESTART, vm = vm,
                )
                DirectAdbSettingRow(
                    title = "Keep ADB without Wi-Fi (TCP/5555)",
                    detail = "TCP/5555 may be reachable from your network. Disable it when not needed.",
                    option = DirectAdbOptions.Option.TCP_5555, vm = vm,
                )
            }
        }
        var showDiagnostics by remember { mutableStateOf(false) }
        OutlinedButton(onClick = { showDiagnostics = !showDiagnostics }) {
            Text(if (showDiagnostics) "Hide ADB diagnostics" else "ADB diagnostics & connection controls")
        }
        if (showDiagnostics) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    androidx.compose.material3.Icon(Icons.Rounded.Info, contentDescription = null)
                    Text("ADB connection", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                }
                Text(
                    when {
                        status.connected && status.verifiedUid == 2000 -> "Shell UID 2000 verified • Connected"
                        vm.pairedPreviously() -> "Previously authorized • Disconnected"
                        else -> "ADB setup required"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(status.message, style = MaterialTheme.typography.bodySmall)
                Text("Transport: " + if (status.connected) status.transport else vm.previousTransport(),
                    style = MaterialTheme.typography.bodySmall)
                Text("Last verified: " + vm.lastVerifiedAt().let {
                    if (it > 0) DateFormat.getDateTimeInstance(DateFormat.SHORT,
                        DateFormat.SHORT).format(Date(it)) else "Never"
                }, style = MaterialTheme.typography.bodySmall)
                Text("Discovery: " + if (discovery.scanning) "Scanning" else "Idle",
                    style = MaterialTheme.typography.bodySmall)
                Text("Pairing port: " + (discovery.pairingPort?.toString() ?: "Not detected") +
                    " • Connection port: " + (discovery.connectionPort?.toString() ?: "Not detected"),
                    style = MaterialTheme.typography.bodySmall)
                Text(discovery.message, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = vm::reconnect) { Text("Retry") }
                    OutlinedButton(onClick = vm::disconnect) { Text("Disconnect") }
                }
            }
        }
        }
        OutlinedButton(onClick = { manualSetup = !manualSetup }) {
            Text(if (manualSetup) "Hide manual pairing" else "Manual pairing (fallback)")
        }
        if (manualSetup) {
            Card(modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Manual Wireless debugging setup",
                        style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }) { Text("Open Developer options") }
                    OutlinedTextField(pairingPort,
                        { pairingPort = it.filter(Char::isDigit).take(5) },
                        label = { Text("Pairing port") }, singleLine = true)
                    OutlinedTextField(pairingCode,
                        { pairingCode = it.filter(Char::isDigit).take(6) },
                        label = { Text("Six-digit pairing code") }, singleLine = true)
                    Button(onClick = { vm.pair(pairingPort, pairingCode) }) {
                        Text("Pair")
                    }
                    OutlinedTextField(connectPort,
                        { connectPort = it.filter(Char::isDigit).take(5) },
                        label = { Text("Wireless debugging connection port") },
                        singleLine = true)
                    Button(onClick = { vm.connectTls(connectPort) }) {
                        Text("Connect")
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectAdbSettingRow(
    title: String,
    detail: String,
    option: DirectAdbOptions.Option,
    vm: DirectAdbSettingsViewModel,
    onChange: ((Boolean) -> Unit)? = null,
) {
    var checked by remember(option) { mutableStateOf(vm.enabled(option)) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = {
            checked = it
            vm.set(option, it)
            onChange?.invoke(it)
        })
    }
}
