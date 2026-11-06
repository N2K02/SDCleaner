package eu.darken.sdmse.common.adb.local

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Regression coverage for the shared VREC/LocalAdb ADB connection contract. */
class DirectAdbParityContractTest {
    private fun source(file: String): String {
        val relative = "src/main/java/eu/darken/sdmse/common/adb/local/$file"
        return listOf(File(relative), File("app-common-io/$relative"),
            File("../app-common-io/$relative"))
            .first { it.isFile }.readText()
    }

    @Test
    fun `same five recovery preferences and safe defaults`() {
        assertEquals(setOf(
            DirectAdbOptions.Option.AUTO_PAIR,
            DirectAdbOptions.Option.WATCHDOG,
            DirectAdbOptions.Option.START_ON_BOOT,
            DirectAdbOptions.Option.RESTORE_AFTER_RESTART,
            DirectAdbOptions.Option.TCP_5555
        ), DirectAdbOptions.Option.entries.toSet())
        val options = source("DirectAdbOptions.kt")
        assertTrue(options.contains("option != Option.RESTORE_AFTER_RESTART"))
        assertTrue(options.contains("getBoolean(\"manual_stop\", false)"))
        assertTrue(options.contains("getInt(\"tls_boot\", -2) == boot"))
        assertFalse(options.contains("putString(\"pairing_code\""))
    }

    @Test
    fun `discovery is strictly device local and stops on screen exit`() {
        val recovery = source("DirectAdbRecovery.kt")
        assertTrue(recovery.contains("_adb-tls-pairing._tcp."))
        assertTrue(recovery.contains("_adb-tls-connect._tcp."))
        assertTrue(recovery.contains("if (host.isLoopbackAddress) return true"))
        assertTrue(recovery.contains("if (!interfaceInfo.isUp) continue"))
        assertTrue(recovery.contains("if (!visible || activeGeneration != generation) return"))
        assertTrue(recovery.contains("stopServiceDiscovery(listener)"))
        assertTrue(recovery.contains("wifiLock?.release()"))
        assertTrue(recovery.contains("watchdog?.cancel()"))
        assertTrue(recovery.contains("DirectAdbOptions.markStopped(app, true)"))
        assertTrue(recovery.contains("DirectAdbOptions.wasPaired(app)"))
    }

    @Test
    fun `reconnect must verify shell identity and retire a broken stream`() {
        val adb = source("DirectAdb.kt")
        assertTrue(adb.contains("AdbTransport.exec(\"id -u\")"))
        assertTrue(adb.contains("DirectAdbOptions.rememberAuthorized(context, port)"))
        assertTrue(adb.contains("AdbTransport.disconnect()"))
        assertTrue(adb.contains("if (code == null)"))
        assertTrue(adb.contains("throw TransportException"))
        assertTrue(adb.contains("if (attached.isFailure || !verifyShell(port))"))
        assertTrue(adb.contains("if (!reverified) AdbTransport.disconnect()"))
    }

    @Test
    fun `optional reboot privilege is not granted without verified connection`() {
        val adb = source("DirectAdb.kt")
        val recovery = source("DirectAdbRecovery.kt")
        assertTrue(adb.contains("if (!ensureConnected()) return false"))
        assertTrue(adb.contains("val operation = if (enabled) \"grant\" else \"revoke\""))
        assertTrue(adb.contains("android.permission.WRITE_SECURE_SETTINGS"))
        assertTrue(recovery.contains("if (!adb.status.value.connected || !AdbTransport.isConnected) return"))
        assertTrue(recovery.contains("if (desired == permission) return"))
    }
}
