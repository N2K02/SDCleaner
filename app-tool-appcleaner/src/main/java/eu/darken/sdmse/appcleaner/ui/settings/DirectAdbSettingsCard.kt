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
import androidx.compose.runtime.mutableIntStateOf
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
    fun disableTcp() {
        DirectAdbOptions.set(app, DirectAdbOptions.Option.TCP_5555, false)
        recovery.disableTcp()
    }
}

/**
 * Mirrors VRec Settings > ADB connection: only the five recovery/security choices.
 * First-time pairing lives on Home; connection details belong to Diagnostics.
 */
@Composable
fun DirectAdbSettingsCard(vm: DirectAdbSettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val status by vm.status.collectAsStateWithLifecycle()
    DisposableEffect(vm) {
        vm.enterSettings()
        onDispose { vm.leaveSettings() }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var resumeTick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val accessibilityEnabled = remember(resumeTick) {
        runCatching {
            Settings.Secure.getString(context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
                .contains("DirectAdbPairingAccessibilityService", ignoreCase = true)
        }.getOrDefault(false)
    }
    var autoPair by remember { mutableStateOf(vm.enabled(DirectAdbOptions.Option.AUTO_PAIR)) }
    var securityNote by remember { mutableStateOf("") }
    var tcpRefresh by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DirectAdbSettingSection("Pairing & recovery") {
            DirectAdbSettingRow(
                title = "Auto-detect pairing code",
                detail = "Read the Android pairing dialog with Accessibility.",
                option = DirectAdbOptions.Option.AUTO_PAIR, vm = vm,
                onChange = { autoPair = it },
            )
            if (autoPair && !accessibilityEnabled) {
                Text("Accessibility is needed for automatic pairing. Manual pairing is on Home.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.onFailure { securityNote = "Unable to open Accessibility settings." }
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
                detail = "Try to restore the helper after restarting and unlocking.",
                option = DirectAdbOptions.Option.START_ON_BOOT, vm = vm,
            )
        }
        DirectAdbSettingSection("Connection & security") {
            DirectAdbSettingRow(
                title = "Restore after restart",
                detail = "Restore Wireless debugging after reboot, using saved pairing. " +
                    "Requires Android permission; never overrides a manual off setting.",
                option = DirectAdbOptions.Option.RESTORE_AFTER_RESTART, vm = vm,
                onChange = { enabled ->
                    securityNote = if (enabled) "Restore requested. Android permission is applied after a verified ADB connection."
                        else "Restore after restart is off. Optional permission revocation requested."
                },
            )
            DirectAdbSettingRow(
                title = "Keep ADB without Wi-Fi (TCP/5555)",
                detail = "Keeps ADB available without Wi-Fi. TCP/5555 may be " +
                    "reachable by devices on your network.",
                option = DirectAdbOptions.Option.TCP_5555, vm = vm,
                refreshKey = tcpRefresh,
                onChange = { enabled ->
                    securityNote = if (enabled) "Automatic TCP/5555 enabled for the next verified connection."
                        else "Automatic TCP/5555 disabled; requested closure of the existing listener."
                },
            )
            if (status.connected && status.verifiedUid == 2000 &&
                status.transport == "Local TCP/5555") {
                OutlinedButton(
                    onClick = {
                        vm.disableTcp()
                        tcpRefresh++ // Explicit action also refreshes the TCP preference switch.
                        securityNote = "Requested TCP/5555 shutdown. Verify the connection status before relying on closure."
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Disable TCP/5555") }
            }
            if (securityNote.isNotBlank()) {
                Text(securityNote, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** First-time authorization and explicit manual pairing fallback live on Home, like VRec. */
@Composable
fun DirectAdbHomeSetupCard(vm: DirectAdbSettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val status by vm.status.collectAsStateWithLifecycle()
    val discovery by vm.discovery.collectAsStateWithLifecycle()
    // Lifecycle is owned by CleanerHome, not this LazyColumn item.
    var manualSetup by remember { mutableStateOf(false) }
    var pairingPort by remember { mutableStateOf("") }
    var pairingCode by remember { mutableStateOf("") }
    var connectPort by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    DirectAdbSettingSection("ADB setup") {
        val verified = status.connected && status.verifiedUid == 2000
        Text(if (verified) "Connected • shell UID 2000 verified" else
            if (vm.pairedPreviously()) "Previously authorized • reconnecting or needs verification"
            else "ADB setup required • shell UID 2000 not verified",
            style = MaterialTheme.typography.bodyLarge)
        if (verified) {
            Text("ADB is ready for authorized cache cleaning.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text("Enable Wireless debugging and USB debugging in Developer options. " +
                "Pair with this phone once; automatic recovery can reconnect later.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(status.message, style = MaterialTheme.typography.bodySmall)
            Text("Discovery: " + (if (discovery.scanning) "Scanning" else "Idle") +
                " • Pairing port: " + (discovery.pairingPort?.toString() ?: "Not detected"),
                style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.onFailure { note = "Unable to open Developer options." }
            }, modifier = Modifier.fillMaxWidth()) { Text("Open Developer options") }
            OutlinedButton(onClick = vm::reconnect, modifier = Modifier.fillMaxWidth()) {
                Text("Find / Retry")
            }
            OutlinedButton(onClick = { manualSetup = !manualSetup }, modifier = Modifier.fillMaxWidth()) {
                Text(if (manualSetup) "Hide manual pairing" else "Manual pairing (fallback)")
            }
            if (manualSetup) {
                Text("Use the pairing and connection ports shown by Android Wireless debugging.",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = pairingPort,
                    onValueChange = { pairingPort = it.filter(Char::isDigit).take(5) },
                    label = { Text("Pairing port") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = pairingCode,
                    onValueChange = { pairingCode = it.filter(Char::isDigit).take(6) },
                    label = { Text("Six-digit pairing code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = { vm.pair(pairingPort, pairingCode) },
                    enabled = pairingPort.toIntOrNull() in 1..65535 && pairingCode.length == 6,
                    modifier = Modifier.fillMaxWidth()) { Text("Pair") }
                OutlinedTextField(
                    value = connectPort,
                    onValueChange = { connectPort = it.filter(Char::isDigit).take(5) },
                    label = { Text("Wireless debugging connection port") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = { vm.connectTls(connectPort) },
                    enabled = connectPort.toIntOrNull() in 1..65535,
                    modifier = Modifier.fillMaxWidth()) { Text("Connect") }
            }
            if (note.isNotBlank()) Text(note, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error)
        }
    }
}

/** Detailed status has its own Settings > Diagnostics submenu, not duplicate recovery cards. */
@Composable
fun DirectAdbDiagnosticsCard(vm: DirectAdbSettingsViewModel = hiltViewModel()) {
    val status by vm.status.collectAsStateWithLifecycle()
    val discovery by vm.discovery.collectAsStateWithLifecycle()
    DisposableEffect(vm) {
        vm.enterSettings()
        onDispose { vm.leaveSettings() }
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DirectAdbSettingSection("ADB connection") {
            Text(when {
                status.connected && status.verifiedUid == 2000 -> "Shell UID 2000 verified • Connected"
                vm.pairedPreviously() -> "Previously authorized • Disconnected"
                else -> "ADB setup required"
            }, style = MaterialTheme.typography.bodyLarge)
            Text(status.message, style = MaterialTheme.typography.bodySmall)
            Text("Transport: " + if (status.connected) status.transport else vm.previousTransport(),
                style = MaterialTheme.typography.bodySmall)
            Text("Last verified: " + vm.lastVerifiedAt().let {
                if (it > 0L) DateFormat.getDateTimeInstance(DateFormat.SHORT,
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
                OutlinedButton(onClick = vm::reconnect, modifier = Modifier.weight(1f)) {
                    Text("Retry")
                }
                OutlinedButton(onClick = vm::disconnect, modifier = Modifier.weight(1f)) {
                    Text("Disconnect")
                }
            }
        }
    }
}

@Composable
private fun DirectAdbSettingSection(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            content()
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
    refreshKey: Int = 0,
) {
    var checked by remember(option, refreshKey) { mutableStateOf(vm.enabled(option)) }
    Row(Modifier.fillMaxWidth().clickable {
        val next = !checked
        checked = next
        vm.set(option, next)
        onChange?.invoke(next)
    },
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = {
            checked = it
            vm.set(option, it)
            onChange?.invoke(it)
        })
    }
}
