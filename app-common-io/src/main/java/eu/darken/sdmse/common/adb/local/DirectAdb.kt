package eu.darken.sdmse.common.adb.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.n2k02.adb.core.AdbTransportMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SDCleaner's bounded cleanup API. Only the versioned shared core may own
 * pairing, TLS/TCP, shell streams, UID 2000 verification, and reconnection.
 */
@Singleton
class DirectAdb @Inject constructor(@ApplicationContext private val context: Context) {
    data class Status(
        val connected: Boolean = false,
        val message: String = "Not connected",
        val verifiedUid: Int? = null,
        val transport: String = "None",
    )
    class TransportException(message: String, cause: Throwable? = null) :
        java.io.IOException(message, cause)
    data class CommandResult(val exitCode: Int, val output: String) {
        val success: Boolean get() = exitCode == 0
    }

    val status = MutableStateFlow(Status())
    private val core = SharedAdbHostAdapter.core(context)
    private val observer = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        observer.launch { core.state.collect { publish() } }
    }

    private fun publish() {
        val state = core.state.value
        status.value = Status(
            connected = state.connected,
            verifiedUid = state.verifiedUid,
            message = state.message,
            transport = when (state.transport) {
                AdbTransportMode.LOCAL_TCP_5555 -> "Local TCP/5555"
                AdbTransportMode.WIRELESS_TLS -> "Wireless TLS"
                AdbTransportMode.NONE -> "None"
            },
        )
    }

    suspend fun connectTcp(): Boolean {
        if (!DirectAdbOptions.get(context, DirectAdbOptions.Option.TCP_5555)) return false
        val okay = core.retry(auto = true)
        publish()
        return okay && core.state.value.connected &&
            core.state.value.transport == AdbTransportMode.LOCAL_TCP_5555
    }

    suspend fun pair(port: Int, code: String): Boolean {
        val paired = core.pair(port, code)
        if (paired) DirectAdbOptions.rememberPaired(context)
        publish()
        return paired
    }

    suspend fun connectTls(port: Int): Boolean {
        val okay = core.connectTls(port)
        publish()
        return okay && core.state.value.connected
    }

    suspend fun ensureConnected(): Boolean {
        if (DirectAdbOptions.stopRequested(context)) return false
        if (core.state.value.connected) {
            val stillVerified = try {
                core.execVerified("id -u", 3_000).trim() == "2000"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) { false }
            if (stillVerified) return true
        }
        val okay = core.retry(auto = true)
        publish()
        return okay && core.state.value.connected
    }

    /**
     * Cleanup commands preserve exit-code semantics using a randomized marker.
     * Every command is reverified under the SAME shared shell mutex.
     */
    suspend fun execute(command: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): CommandResult {
        if (!ensureConnected()) throw TransportException("ADB shell UID 2000 unavailable")
        val tag = "__SDC_EXIT_" + UUID.randomUUID().toString().replace("-", "") + "__"
        val wrapped = "( " + command + " ); printf '\\n" + tag + "%s\\n' \"$?\""
        val raw = try {
            core.execVerified(wrapped, timeoutMs)
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { core.disconnectIdle() }
            throw cancelled
        } catch (failure: Exception) {
            withContext(NonCancellable) { core.disconnectIdle() }
            publish()
            throw TransportException("ADB stream failed; stop this cleanup run", failure)
        }
        currentCoroutineContext().ensureActive()
        val lines = raw.lines()
        val last = lines.lastOrNull { it.startsWith(tag) }
        val code = last?.removePrefix(tag)?.trim()?.toIntOrNull()
        if (code == null || code !in 0..255) {
            withContext(NonCancellable) { core.disconnectIdle() }
            publish()
            throw TransportException(if (last == null)
                "ADB stream lost command exit marker" else
                "ADB stream returned malformed exit marker")
        }
        return CommandResult(code, lines.filterNot { it.startsWith(tag) }.joinToString("\n").trim())
    }

    suspend fun trimCaches(): CommandResult =
        execute(trimCachesCommand(), timeoutMs = TRIM_TIMEOUT_MS)

    suspend fun forceStop(packageName: String, userId: Int): CommandResult {
        require(validPackage(packageName) && userId >= 0) { "Invalid force-stop target" }
        require(packageName != context.packageName && packageName != "com.android.systemui" &&
            packageName != "android") { "Unsafe force-stop target" }
        return execute("am force-stop --user " + userId + " " + packageName)
    }

    suspend fun setRestartPermission(enabled: Boolean): Boolean {
        val pkg = context.packageName.takeIf(::validPackage) ?: return false
        if (!ensureConnected()) return false
        val action = if (enabled) "grant" else "revoke"
        return try {
            execute("pm " + action + " " + pkg +
                " android.permission.WRITE_SECURE_SETTINGS").success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) { false }
    }

    /** A background cleanup worker is done, NOT a manual Quit. */
    suspend fun disconnect() {
        try {
            withContext(NonCancellable) { core.disconnectIdle() }
        } finally {
            publish()
        }
    }

    val wasAuthorized: Boolean get() = DirectAdbOptions.wasAuthorized(context)

    companion object {
        const val DEFAULT_TIMEOUT_MS = 15_000L
        const val TRIM_TIMEOUT_MS = 120_000L
        fun trimCachesCommand(): String = "pm trim-caches " + Long.MAX_VALUE
        fun validPackage(value: String): Boolean =
            value.length <= 255 && value.contains('.') &&
                value.matches(Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+"))
    }
}
