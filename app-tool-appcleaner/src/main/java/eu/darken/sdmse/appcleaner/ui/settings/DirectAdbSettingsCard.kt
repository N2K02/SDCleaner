package eu.darken.sdmse.appcleaner.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.adb.local.DirectAdb
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DirectAdbSettingsViewModel @Inject constructor(private val adb: DirectAdb) : ViewModel() {
    val status = adb.status
    fun pair(port: String, code: String) = viewModelScope.launch { adb.pair(port.toIntOrNull() ?: -1, code) }
    fun connectTls(port: String) = viewModelScope.launch { adb.connectTls(port.toIntOrNull() ?: -1) }
    fun connectTcp() = viewModelScope.launch { adb.connectTcp() }
    fun disconnect() = viewModelScope.launch { adb.disconnect() }
}

@Composable
fun DirectAdbSettingsCard(vm: DirectAdbSettingsViewModel = hiltViewModel()) {
    val status by vm.status.collectAsStateWithLifecycle()
    var pairingPort by remember { mutableStateOf("") }
    var pairingCode by remember { mutableStateOf("") }
    var connectPort by remember { mutableStateOf("") }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Direct ADB — no Shizuku or Accessibility")
            Text(status.message)
            Text("Enable Wireless debugging in Android Developer options. Pair this app once; then connect to the phone's Wireless debugging port. TCP/5555 may be reachable on the local network while enabled.")
            OutlinedTextField(
                value = pairingPort,
                onValueChange = { pairingPort = it.filter(Char::isDigit).take(5) },
                label = { Text("Pairing port") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = pairingCode,
                onValueChange = { pairingCode = it.filter(Char::isDigit).take(6) },
                label = { Text("Six-digit pairing code") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Button(onClick = { vm.pair(pairingPort, pairingCode) }) { Text("Pair") }
            OutlinedTextField(
                value = connectPort,
                onValueChange = { connectPort = it.filter(Char::isDigit).take(5) },
                label = { Text("Wireless ADB connection port") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.connectTls(connectPort) }) { Text("Connect") }
                OutlinedButton(onClick = vm::connectTcp) { Text("Reconnect") }
                OutlinedButton(onClick = vm::disconnect) { Text("Disconnect") }
            }
        }
    }
}
