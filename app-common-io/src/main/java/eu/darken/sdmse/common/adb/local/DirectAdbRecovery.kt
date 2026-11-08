package eu.darken.sdmse.common.adb.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.n2k02.adb.core.AdbOption
import dev.n2k02.adb.core.AdbTransportMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

data class DirectAdbRecoveryState(
    val scanning: Boolean = false,
    val pairingPort: Int? = null,
    val connectionPort: Int? = null,
    val message: String = "Automatic recovery is idle",
)

/**
 * Host adapter for the ONE shared discovery, watchdog, TLS/TCP and recovery
 * implementation. Settings owns a page; one-shot WorkManager owns a service.
 */
@Singleton
class DirectAdbRecovery @Inject constructor(
    @ApplicationContext private val app: Context,
    private val adb: DirectAdb,
) {
    val state = MutableStateFlow(DirectAdbRecoveryState())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val core = SharedAdbHostAdapter.core(app)
    @Volatile private var visible = false
    @Volatile private var pairingInProgress = false

    init {
        scope.launch {
            core.state.collect { current ->
                state.value = DirectAdbRecoveryState(
                    scanning = current.scanning,
                    pairingPort = current.pairingPort,
                    connectionPort = current.connectionPort,
                    message = current.message,
                )
            }
        }
    }

    @Synchronized fun enterSettings() {
        if (!visible) {
            visible = true
            // Opening Settings must not clear the user's explicit Stop choice.
            core.enterPage()
        }
        if (!DirectAdbOptions.stopRequested(app)) {
            scope.launch { core.retry(auto = true) }
        }
    }

    fun onSettingsChanged() {
        scope.launch {
            for (option in AdbOption.entries) {
                core.setOption(option,
                    DirectAdbOptions.get(app, DirectAdbOptions.Option.valueOf(option.name)))
            }
            if (visible && !DirectAdbOptions.stopRequested(app)) core.retry(auto = true)
        }
    }

    /** LeaveSettings is idempotent and never cancels another service's ADB. */
    @Synchronized fun leaveSettings() {
        if (!visible) return
        visible = false
        core.leavePage()
    }

    suspend fun recover() {
        if (!DirectAdbOptions.stopRequested(app)) core.retry(auto = true)
    }

    /**
     * Boot discovery belongs to a bounded WorkManager owner, not an activity
     * or a second mDNS scanner. DirectAdbBootWorker closes only this connection.
     */
    suspend fun recoverForBackground() {
        if (DirectAdbOptions.stopRequested(app) ||
            !DirectAdbOptions.wasAuthorized(app)) return
        core.acquireService()
        try {
            core.startAfterBoot()
            withTimeoutOrNull(18_000L) {
                core.state.first { current ->
                    current.connected && (
                        !DirectAdbOptions.get(app, DirectAdbOptions.Option.TCP_5555) ||
                            current.transport == AdbTransportMode.LOCAL_TCP_5555
                    )
                }
            }
        } finally {
            core.releaseService()
        }
    }

    /** User Stop remains sticky; ordinary worker disconnect never calls stop. */
    fun stopForQuit() {
        DirectAdbOptions.markStopped(app, true)
        leaveSettings()
        scope.launch { core.stop() }
    }

    fun manualDisconnect() {
        DirectAdbOptions.markStopped(app, true)
        leaveSettings()
        scope.launch { core.stop() }
    }

    fun disableTcp() {
        scope.launch {
            core.disableTcp()
            if (visible && !DirectAdbOptions.stopRequested(app))
                core.retry(auto = true)
        }
    }
}
