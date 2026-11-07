package eu.darken.sdmse.appcleaner.ui.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.n2k02.adb.core.AdbConnectionSettingsPage
import dev.n2k02.adb.core.AdbHomeSetupPage
import dev.n2k02.adb.core.AdbOption
import eu.darken.sdmse.common.adb.local.DirectAdb
import eu.darken.sdmse.common.adb.local.DirectAdbOptions
import eu.darken.sdmse.common.adb.local.DirectAdbRecovery
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DirectAdbSettingsViewModel @Inject constructor(
    private val adb: DirectAdb,
    private val recovery: DirectAdbRecovery,
    @ApplicationContext private val app: Context,
) : ViewModel() {
    val sharedCore = adb.sharedCore
    val status = adb.status
    val discovery = recovery.state

    // CleanerHome is stable; it owns discovery when the setup LazyColumn item
    // is scrolled out of view. The shared Settings page owns its own lifecycle.
    fun enterSettings() = recovery.enterSettings()
    fun leaveSettings() = recovery.leaveSettings()
    fun enabled(option: DirectAdbOptions.Option) = DirectAdbOptions.get(app, option)
    fun set(option: DirectAdbOptions.Option, checked: Boolean) =
        viewModelScope.launch {
            sharedCore.setOption(AdbOption.valueOf(option.name), checked)
        }
    fun pairedPreviously() = DirectAdbOptions.wasAuthorized(app)
    fun lastVerifiedAt() = DirectAdbOptions.verifiedAt(app)
    fun previousTransport() = DirectAdbOptions.transport(app)
    fun reconnect() = viewModelScope.launch { sharedCore.retry(auto = false) }
    fun disconnect() = recovery.manualDisconnect()
    fun disableTcp() = viewModelScope.launch { sharedCore.disableTcp() }
}

/** VRec, LocalAdb, MCP Logger and SDCleaner share exactly the same ADB cards. */
@Composable
fun DirectAdbSettingsCard(vm: DirectAdbSettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeTick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val accessibilityEnabled = remember(context, resumeTick) {
        dev.n2k02.adb.core.SharedAdbPairingAccessibilityService.isEnabled(
            context, DirectAdbPairingAccessibilityService::class.java
        )
    }
    AdbConnectionSettingsPage(
        adb = vm.sharedCore,
        accessibilityEnabled = accessibilityEnabled,
        onOpenAccessibility = {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        },
    )
}

/** The stable CleanerHome root owns lifecycle, not its LazyColumn setup item. */
@Composable
fun DirectAdbHomeSetupCard(vm: DirectAdbSettingsViewModel = hiltViewModel()) {
    Column(modifier = Modifier.fillMaxWidth()) {
        AdbHomeSetupPage(vm.sharedCore)
    }
}
