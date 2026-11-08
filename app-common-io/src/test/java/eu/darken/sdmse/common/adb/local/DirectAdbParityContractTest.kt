package eu.darken.sdmse.common.adb.local

import java.io.File
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** SDCleaner delegates ADB control to the shared, verified library. */
class DirectAdbParityContractTest {
    private fun source(name: String): String {
        val path = "src/main/java/eu/darken/sdmse/common/adb/local/" + name
        return listOf(File(path), File("app-common-io/" + path), File("../app-common-io/" + path))
            .first { it.isFile }.readText()
    }

    @Test fun fiveOptionsRetainSafeDefaults() {
        assertEquals(setOf(
            DirectAdbOptions.Option.AUTO_PAIR, DirectAdbOptions.Option.WATCHDOG,
            DirectAdbOptions.Option.START_ON_BOOT, DirectAdbOptions.Option.RESTORE_AFTER_RESTART,
            DirectAdbOptions.Option.TCP_5555
        ), DirectAdbOptions.Option.entries.toSet())
        val src = source("DirectAdbOptions.kt")
        assertTrue(src.contains("option != Option.RESTORE_AFTER_RESTART"))
        assertTrue(src.contains("getBoolean(\"manual_stop\", false)"))
        assertTrue(src.contains("getInt(\"tls_boot\", -2) == boot"))
        assertFalse(src.contains("putString(\"pairing_code\""))
    }

    @Test fun discoveryAndWatchdogAreOwnedOnlyByCore() {
        val recovery = source("DirectAdbRecovery.kt")
        for (text in listOf("SharedAdbHostAdapter.core(app)", "core.enterPage()",
            "core.leavePage()", "core.acquireService()", "core.releaseService()",
            "core.startAfterBoot()", "core.retry(auto = true)"))
            assertTrue(recovery.contains(text), text)
        for (forbidden in listOf("NsdManager", "NetworkInterface", "WifiManager",
            "startDiscovery()", "delay(120_000L)"))
            assertFalse(recovery.contains(forbidden), forbidden)
        val adapter = source("SharedAdbHostAdapter.kt")
        assertTrue(adapter.contains("LocalAdbDiscovery(app)"))
        assertTrue(adapter.contains("AdbCoreRegistry.install(it)"))
        // Pairing codes are captured only by the shared Accessibility reader.
        assertFalse(recovery.contains("fun submitPairingCode("))
    }

    @Test fun openingSettingsCannotUndoExplicitManualStop() {
        val recovery = source("DirectAdbRecovery.kt")
        val enterSettings = recovery.substringAfter("@Synchronized fun enterSettings()")
            .substringBefore("fun onSettingsChanged()")
        assertTrue(enterSettings.contains("core.enterPage()"))
        assertTrue(enterSettings.contains("if (!DirectAdbOptions.stopRequested(app))"))
        assertTrue(enterSettings.contains("core.retry(auto = true)"))
        assertFalse(enterSettings.contains("markStopped(app, false)"))
        assertFalse(enterSettings.contains("core.retry(auto = false)"))
    }

    @Test fun shellCommandsRequireFreshUidAndMalformedMarkersRetireTheSession() {
        val direct = source("DirectAdb.kt")
        assertTrue(direct.contains("core.execVerified(wrapped, timeoutMs)"))
        assertTrue(direct.contains("core.execVerified(\"id -u\", 3_000)"))
        assertTrue(direct.contains("if (!ensureConnected()) throw TransportException"))
        assertTrue(direct.contains("if (code == null || code !in 0..255)"))
        assertTrue(direct.contains("throw TransportException"))
        assertTrue(direct.contains("core.disconnectIdle()"))
        assertFalse(direct.contains("AdbTransport.exec("))
    }

    @Test fun permissionRestoreIsOptInAndUsesVerifiedShell() {
        val direct = source("DirectAdb.kt")
        val adapter = source("SharedAdbHostAdapter.kt")
        assertTrue(direct.contains("if (!ensureConnected()) return false"))
        assertTrue(direct.contains("val action = if (enabled) \"grant\" else \"revoke\""))
        assertTrue(direct.contains("android.permission.WRITE_SECURE_SETTINGS"))
        assertTrue(adapter.contains("onRestoreAfterRestartChanged(enabled: Boolean)"))
        assertTrue(adapter.contains("if (enabled == granted) return"))
        assertTrue(adapter.contains("adb.execVerified("))
    }
}
