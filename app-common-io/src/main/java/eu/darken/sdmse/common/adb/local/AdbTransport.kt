package eu.darken.sdmse.common.adb.local

import android.content.Context
import dev.n2k02.adb.core.AdbOption
import dev.n2k02.adb.core.AdbTransportMode
import kotlinx.coroutines.CancellationException

/**
 * Compatibility facade for old call sites. There is NO second libadb manager,
 * native shell stream, discovery engine, or independent watchdog in this app.
 */
object AdbTransport {
    const val LOOPBACK = "127.0.0.1"
    const val LEGACY_TCP_PORT = 5555

    val isConnected: Boolean
        get() = SharedAdbHostAdapter.current()?.state?.value?.connected == true
    val lastKnownConnected: Boolean get() = isConnected

    private fun core() = checkNotNull(SharedAdbHostAdapter.current()) {
        "Shared ADB core is not installed; no legacy transport fallback"
    }

    suspend fun exec(command: String, timeoutMs: Long = 15_000): Result<String> = try {
        Result.success(core().execVerified(command, timeoutMs))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
    }

    suspend fun pairLocal(context: Context, port: Int, code: String): Result<Unit> = try {
        check(core().pair(port, code)) { "ADB pairing failed" }
        Result.success(Unit)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
    }

    suspend fun connectLocalTls(context: Context, port: Int): Result<Unit> = try {
        check(core().connectTls(port)) { "TLS connection failed" }
        Result.success(Unit)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
    }

    suspend fun connect(context: Context, host: String, port: Int): Result<Unit> = try {
        require(host == LOOPBACK && port == LEGACY_TCP_PORT)
        check(core().retry(auto = true) &&
            core().state.value.transport == AdbTransportMode.LOCAL_TCP_5555) {
            "TCP/5555 has not been verified as shell UID 2000"
        }
        Result.success(Unit)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
    }

    suspend fun switchToTcpip(port: Int = LEGACY_TCP_PORT): Result<Unit> = try {
        require(port == LEGACY_TCP_PORT)
        core().setOption(AdbOption.TCP_5555, true)
        check(core().retry() &&
            core().state.value.transport == AdbTransportMode.LOCAL_TCP_5555)
        Result.success(Unit)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
    }

    suspend fun switchToUsb(): Result<Unit> = try {
        check(core().disableTcp()) { "TCP/5555 shutdown failed" }
        Result.success(Unit)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
    }

    suspend fun disconnect() {
        SharedAdbHostAdapter.current()?.disconnectIdle()
    }
    suspend fun close() = disconnect()
}
