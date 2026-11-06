package eu.darken.sdmse.common.adb.local

import android.content.Context
import android.content.pm.PackageManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton

data class DirectAdbRecoveryState(
    val scanning: Boolean = false,
    val pairingPort: Int? = null,
    val connectionPort: Int? = null,
    val message: String = "Automatic recovery is idle",
)

/**
 * Settings-scoped, local-device-only mDNS discovery and authorized ADB recovery.
 * No remote host scans, no stored pairing codes, and no permanent multicast lock.
 */
@Singleton
class DirectAdbRecovery @Inject constructor(
    @ApplicationContext private val app: Context,
    private val adb: DirectAdb,
) {
    val state = MutableStateFlow(DirectAdbRecoveryState())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var mgr: NsdManager? = null
    private var wifiLock: WifiManager.MulticastLock? = null
    private var pairingListener: NsdManager.DiscoveryListener? = null
    private var connectListener: NsdManager.DiscoveryListener? = null
    private var scanTimeout: Job? = null
    private var watchdog: Job? = null
    private var connectionAttempt: Job? = null
    private var generation = 0
    @Volatile private var visible = false
    @Volatile private var pairingInProgress = false

    fun enterSettings() {
        visible = true
        DirectAdbOptions.markStopped(app, false)
        connectionAttempt?.cancel()
        connectionAttempt = scope.launch { recover() }
        configureWatchdog()
    }

    fun onSettingsChanged() {
        configureWatchdog()
        if (visible && !DirectAdbOptions.stopRequested(app)) {
            connectionAttempt?.cancel()
            connectionAttempt = scope.launch { recover() }
        }
    }

    /** Disposes timers and Wi-Fi discovery when the user leaves this page. */
    fun leaveSettings() {
        visible = false
        watchdog?.cancel()
        watchdog = null
        connectionAttempt?.cancel()
        connectionAttempt = null
        stopDiscovery()
    }

    private fun configureWatchdog() {
        watchdog?.cancel()
        watchdog = null
        if (!visible || !DirectAdbOptions.get(app, DirectAdbOptions.Option.WATCHDOG)) return
        watchdog = scope.launch {
            while (visible && DirectAdbOptions.get(app, DirectAdbOptions.Option.WATCHDOG)) {
                delay(120_000L)
                if (!visible) break
                if (!adb.status.value.connected || !AdbTransport.isConnected) recover()
            }
        }
    }

    suspend fun recover() {
        if (DirectAdbOptions.stopRequested(app)) return
        if (adb.status.value.connected && AdbTransport.isConnected &&
            adb.ensureConnected()) {
            synchronizeRestartPermission()
            return
        }
        // Only a local listener is accepted; no TCP probes outside this phone.
        if (adb.connectTcp()) {
            stopDiscovery()
            synchronizeRestartPermission()
            return
        }
        val tls = DirectAdbOptions.tlsPort(app)
        if (tls != null && adb.connectTls(tls)) {
            stopDiscovery()
            synchronizeRestartPermission()
            return
        }
        if (visible) startDiscovery()
    }

    /** WRITE_SECURE_SETTINGS is never granted unless explicitly enabled. */
    private suspend fun synchronizeRestartPermission() {
        if (!adb.status.value.connected || !AdbTransport.isConnected) return
        val desired = DirectAdbOptions.get(app, DirectAdbOptions.Option.RESTORE_AFTER_RESTART)
        val permission = app.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") ==
            PackageManager.PERMISSION_GRANTED
        if (desired == permission) return
        val changed = adb.setRestartPermission(desired)
        if (!changed) {
            state.value = state.value.copy(message =
                if (desired) "Android denied optional Wireless debugging restore permission"
                else "Could not revoke optional Wireless debugging restore permission")
        }
    }

    /**
     * One-shot boot recovery: discover only the phone's own verified TLS endpoint.
     * WorkManager owns the deadline; no persistent multicast lock or watchdog.
     */
    suspend fun recoverForBackground() {
        if (DirectAdbOptions.stopRequested(app) ||
            !DirectAdbOptions.wasAuthorized(app)) return
        if (adb.ensureConnected()) {
            synchronizeRestartPermission()
            return
        }
        val openedByUi = visible
        if (openedByUi) {
            recover()
            return
        }
        visible = true
        try {
            startDiscovery()
            // TLS UID verification is not completion while TCP promotion is pending.
            withTimeoutOrNull(18_000L) {
                adb.status.first {
                    it.connected && (
                        !DirectAdbOptions.get(app, DirectAdbOptions.Option.TCP_5555) ||
                            it.transport == "Local TCP/5555"
                    )
                }
            }
            if (adb.status.value.connected) synchronizeRestartPermission()
        } finally {
            visible = false
            stopDiscovery()
        }
    }

    fun submitPairingCode(code: String) {
        val port = state.value.pairingPort ?: return
        if (!visible || pairingInProgress ||
            !DirectAdbOptions.get(app, DirectAdbOptions.Option.AUTO_PAIR) ||
            !code.matches(Regex("[0-9]{6}"))) return
        pairingInProgress = true
        scope.launch {
            try {
                state.value = state.value.copy(message = "Pairing with this phone's Android settings")
                if (adb.pair(port, code)) {
                    DirectAdbOptions.rememberPaired(app)
                    state.value = state.value.copy(message = "Paired. Waiting for verified connection")
                    state.value.connectionPort?.let {
                        if (adb.connectTls(it)) {
                            stopDiscovery()
                            synchronizeRestartPermission()
                        }
                    }
                }
            } finally {
                pairingInProgress = false
            }
        }
    }

    private fun onDevice(host: InetAddress): Boolean {
        if (host.isLoopbackAddress) return true
        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching false
            while (interfaces.hasMoreElements()) {
                val interfaceInfo = interfaces.nextElement()
                if (!interfaceInfo.isUp) continue
                val addresses = interfaceInfo.inetAddresses
                while (addresses.hasMoreElements()) {
                    if (addresses.nextElement() == host) return@runCatching true
                }
            }
            false
        }.getOrDefault(false)
    }

    private fun startDiscovery() {
        if (!visible || Build.VERSION.SDK_INT < 30) {
            state.value = state.value.copy(message = "Wireless ADB discovery requires Android 11+")
            return
        }
        if (mgr != null) return
        val nsd = app.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        mgr = nsd
        val wifi = app.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        wifiLock = runCatching {
            wifi?.createMulticastLock("sdcleaner-adb-mdns")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }.getOrNull()
        val activeGeneration = ++generation
        state.value = state.value.copy(scanning = true,
            message = "Discovering Android Wireless debugging on this device…")
        pairingListener = listen(nsd, "_adb-tls-pairing._tcp.", activeGeneration) { port ->
            state.value = state.value.copy(pairingPort = port,
                message = "Android pairing port discovered")
        }
        connectListener = listen(nsd, "_adb-tls-connect._tcp.", activeGeneration) { port ->
            state.value = state.value.copy(connectionPort = port)
            if (DirectAdbOptions.wasAuthorized(app) || DirectAdbOptions.wasPaired(app)) scope.launch {
                if (visible && activeGeneration == generation && adb.connectTls(port)) {
                    stopDiscovery()
                    synchronizeRestartPermission()
                }
            }
        }
        scanTimeout = scope.launch {
            delay(90_000L)
            if (activeGeneration == generation) stopDiscovery()
        }
    }

    private fun listen(
        nsd: NsdManager, type: String, activeGeneration: Int, portFound: (Int) -> Unit
    ): NsdManager.DiscoveryListener {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onStartDiscoveryFailed(type: String, code: Int) = Unit
            override fun onStopDiscoveryFailed(type: String, code: Int) = Unit
            override fun onServiceLost(service: NsdServiceInfo) = Unit
            override fun onServiceFound(service: NsdServiceInfo) {
                if (!visible || activeGeneration != generation) return
                @Suppress("DEPRECATION")
                nsd.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(service: NsdServiceInfo, code: Int) = Unit
                    override fun onServiceResolved(service: NsdServiceInfo) {
                        if (!visible || activeGeneration != generation) return
                        @Suppress("DEPRECATION")
                        val host = service.host
                        if (host != null && onDevice(host) && service.port in 1..65535) {
                            portFound(service.port)
                        }
                    }
                })
            }
        }
        runCatching { nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener) }
        return listener
    }

    private fun stopDiscovery() {
        ++generation
        scanTimeout?.cancel()
        scanTimeout = null
        mgr?.let { manager ->
            listOfNotNull(pairingListener, connectListener).forEach { listener ->
                runCatching { manager.stopServiceDiscovery(listener) }
            }
        }
        pairingListener = null
        connectListener = null
        mgr = null
        runCatching { wifiLock?.release() }
        wifiLock = null
        state.value = state.value.copy(scanning = false)
    }

    /** Quit cancels future recovery, but active cleaner operations drain before socket close. */
    fun stopForQuit() {
        DirectAdbOptions.markStopped(app, true)
        leaveSettings()
    }

    fun manualDisconnect() {
        DirectAdbOptions.markStopped(app, true)
        leaveSettings()
        scope.launch { adb.disconnect() }
    }

    fun disableTcp() {
        scope.launch {
            if (adb.status.value.connected && AdbTransport.isConnected) {
                AdbTransport.switchToUsb()
            }
            adb.disconnect()
            if (visible) recover()
        }
    }
}
