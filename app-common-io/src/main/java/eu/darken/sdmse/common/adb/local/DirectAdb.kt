package eu.darken.sdmse.common.adb.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-demand localhost ADB. Never treats the ordinary Android app UID as shell UID.
 * Pairing/authorization is explicit, and each actual connection is verified as uid 2000.
 * There is no boot receiver, background watchdog, or external privileged-manager dependency.
 */
@Singleton
class DirectAdb @Inject constructor(@ApplicationContext private val context: Context) {
    data class Status(val connected: Boolean = false, val message: String = "Not connected")
    /** Fatal for this cleanup run: don't reconnect and retry the same broken stream for every package. */
    class TransportException(message: String, cause: Throwable? = null) :
        java.io.IOException(message, cause)
    data class CommandResult(val exitCode: Int, val output: String) {
        val success: Boolean get() = exitCode == 0
    }

    val status = MutableStateFlow(Status())
    private val lock = Mutex()

    private fun endpointAvailable(): Boolean = try {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", 5555), 350) }
        true
    } catch (_: Exception) { false }

    private suspend fun verifyShell(port: Int): Boolean {
        val uid = AdbTransport.exec("id -u").getOrNull()?.trim()
        val okay = uid == "2000"
        if (!okay) {
            AdbTransport.disconnect()
            status.value = Status(message = "ADB connected but shell UID verification failed")
        } else {
            DirectAdbOptions.rememberAuthorized(context, port)
            status.value = Status(connected = true, message = "ADB ready (shell UID 2000)")
        }
        return okay
    }

    /** Connect only to this phone's USB-bootstrapped TCP listener. */
    suspend fun connectTcp(): Boolean = lock.withLock {
        withContext(Dispatchers.IO) {
            if (AdbTransport.isConnected && status.value.connected) return@withContext true
            if (!endpointAvailable()) {
                status.value = Status(message = "Localhost TCP/5555 unavailable. Enable Wireless debugging or USB ADB first.")
                return@withContext false
            }
            val result = AdbTransport.connect(context, AdbTransport.LOOPBACK, AdbTransport.LEGACY_TCP_PORT)
            if (result.isFailure) {
                status.value = Status(message = "ADB connection failed: ${result.exceptionOrNull()?.message}")
                return@withContext false
            }
            verifyShell(5555)
        }
    }

    /** The user enters the six-digit code and pairing port shown in Android Wireless debugging. */
    suspend fun pair(port: Int, code: String): Boolean = lock.withLock {
        if (port !in 1..65535 || !code.matches(Regex("[0-9]{6}"))) {
            status.value = Status(message = "Enter the pairing port and six-digit code")
            return@withLock false
        }
        val result = AdbTransport.pairLocal(context, port, code)
        status.value = Status(message = if (result.isSuccess)
            "Paired. Enter the Wireless debugging connection port and tap Connect."
        else "Pairing failed: ${result.exceptionOrNull()?.message}")
        result.isSuccess
    }

    /** Connect to a local TLS listener and migrate to localhost TCP/5555 for subsequent on-demand use. */
    suspend fun connectTls(port: Int): Boolean = lock.withLock {
        if (port !in 1..65535) {
            status.value = Status(message = "Enter a valid Wireless debugging connection port")
            return@withLock false
        }
        val attached = AdbTransport.connectLocalTls(context, port)
        if (attached.isFailure || !verifyShell(port)) {
            status.value = Status(message = "Wireless ADB connection or UID verification failed")
            return@withLock false
        }
        if (!DirectAdbOptions.get(context, DirectAdbOptions.Option.TCP_5555)) return@withLock true
        if (AdbTransport.switchToTcpip().isFailure) {
            status.value = Status(message = "Shell verified, but switching to TCP/5555 failed")
            return@withLock false
        }
        AdbTransport.disconnect()
        var reverified = false
        repeat(4) {
            if (!reverified) {
                kotlinx.coroutines.delay(1_100L)
                val result = AdbTransport.connect(
                    context, AdbTransport.LOOPBACK, AdbTransport.LEGACY_TCP_PORT)
                reverified = result.isSuccess && verifyShell(5555)
                if (!reverified) AdbTransport.disconnect()
            }
        }
        if (!reverified) {
            status.value = Status(message = "TCP/5555 reconnect failed after TLS setup")
            return@withLock false
        }
        true
    }

    /** No scans, discovery, or keep-alive when disconnected. */
    suspend fun ensureConnected(): Boolean {
        if (status.value.connected && AdbTransport.isConnected) return true
        if (connectTcp()) return true
        val tlsPort = DirectAdbOptions.tlsPort(context)
        return tlsPort != null && connectTls(tlsPort)
    }

    /**
     * The shell protocol does not expose exit codes; append a unique status sentinel.
     * Commands are built from validated package names and integer Android user IDs only.
     */
    suspend fun execute(command: String): CommandResult {
        if (!ensureConnected()) throw TransportException("ADB shell UID 2000 unavailable")
        val tag = "__SDC_EXIT_${UUID.randomUUID().toString().replace("-", "")}__"
        val wrapped = "( $command ); printf '\\n$tag%s\\n' \"\$?\""
        val raw = AdbTransport.exec(wrapped).getOrElse { error ->
            if (error is CancellationException) throw error
            status.value = Status(message = "ADB command stream interrupted")
            throw TransportException("ADB stream failed; stop this cleanup run", error)
        }
        currentCoroutineContext().ensureActive()
        val lines = raw.lines()
        val last = lines.lastOrNull { it.startsWith(tag) }
        val code = last?.removePrefix(tag)?.trim()?.toIntOrNull()
        if (code == null) {
            // Do not treat a missing/malformed command completion marker as
            // an ordinary package error. The shell's output boundaries are
            // untrustworthy until a new UID-2000 verified connection exists.
            status.value = Status(message = "ADB command completion invalid; session retired")
            AdbTransport.disconnect()
            throw TransportException(if (last == null)
                "ADB stream lost command exit marker" else
                "ADB stream returned malformed exit marker")
        }
        return CommandResult(code, lines.filterNot { it.startsWith(tag) }.joinToString("\n").trim())
    }

    suspend fun clearCache(packageName: String, userId: Int): CommandResult {
        require(validPackage(packageName) && userId >= 0) { "Invalid cache target" }
        return execute("pm clear --user $userId --cache-only $packageName")
    }

    suspend fun forceStop(packageName: String, userId: Int): CommandResult {
        require(validPackage(packageName) && userId >= 0) { "Invalid force-stop target" }
        require(packageName != context.packageName && packageName != "com.android.systemui" &&
            packageName != "android") { "Unsafe force-stop target" }
        return execute("am force-stop --user $userId $packageName")
    }

    suspend fun disconnect() {
        try {
            // Disconnect must still run when the background Worker has
            // already been cancelled. The only native socket cleanup is here.
            withContext(kotlinx.coroutines.NonCancellable) {
                kotlinx.coroutines.withTimeout(5_000) { AdbTransport.disconnect() }
            }
        } finally {
            status.value = Status()
        }
    }

    companion object {
        fun validPackage(value: String): Boolean =
            value.length <= 255 && value.contains('.') &&
                value.matches(Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+"))
    }
}
