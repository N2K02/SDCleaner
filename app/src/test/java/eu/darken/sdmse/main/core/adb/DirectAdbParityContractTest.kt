package eu.darken.sdmse.main.core.adb

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** Detect settings/transport drift from VREC's five automatic ADB choices. */
class DirectAdbParityContractTest {
    private fun source(relative: String): String {
        return listOf(File(relative), File("../$relative"))
            .first { it.isFile }.readText()
    }

    @Test
    fun `settings has the same automatic recovery options and status as VREC`() {
        val ui = source("app-tool-appcleaner/src/main/java/eu/darken/sdmse/appcleaner/ui/settings/DirectAdbSettingsCard.kt")
        for (required in listOf(
            "ADB connection", "Pairing & recovery", "Connection & security",
            "Auto-detect pairing code", "Connection watchdog", "Start on boot",
            "Restore after restart", "Keep ADB without Wi-Fi (TCP/5555)",
            "status.connected", "status.verifiedUid", "discovery.scanning",
            "discovery.pairingPort",
        )) assertTrue(ui.contains(required), required)
        // Connection port is a diagnostic detail, not a duplicate Settings card.
        val diagnostics = source("app-common-io/src/main/java/eu/darken/sdmse/common/adb/local/DirectAdbRecovery.kt")
        assertTrue(diagnostics.contains("connectionPort"))
        assertTrue(ui.contains("Manual pairing (fallback)"))
        val settings = source("app/src/main/java/eu/darken/sdmse/main/ui/settings/SettingsScreen.kt")
        assertTrue(settings.contains("CleanerSettingsRoute(\"adb\")"))
        assertTrue(settings.contains("\"adb\" -> DirectAdbSettingsCard()"))
    }

    @Test
    fun `local-only recovery cannot scan arbitrary network hosts`() {
        val recovery = source("app-common-io/src/main/java/eu/darken/sdmse/common/adb/local/DirectAdbRecovery.kt")
        assertTrue(recovery.contains("host.isLoopbackAddress"))
        assertTrue(recovery.contains("NetworkInterface.getNetworkInterfaces()"))
        assertTrue(recovery.contains("_adb-tls-pairing._tcp."))
        assertTrue(recovery.contains("_adb-tls-connect._tcp."))
        assertTrue(recovery.contains("stopDiscovery()"))
        assertTrue(recovery.contains("wifiLock?.release()"))
        assertTrue(recovery.contains("DirectAdbOptions.stopRequested(app)"))
        assertTrue(recovery.contains("delay(120_000L)"))
    }

    @Test
    fun `pairing accessibility only reads Android settings and does not automate cleaner UI`() {
        val reader = source("app-tool-appcleaner/src/main/java/eu/darken/sdmse/appcleaner/ui/settings/DirectAdbPairingAccessibilityService.kt")
        assertTrue(reader.contains("isAndroidSettings(root.packageName"))
        assertTrue(reader.contains("DirectAdbRecovery"))
        assertFalse(reader.contains("performAction("))
        assertFalse(reader.contains("Settings.Secure.putString"))
        val options = source("app-common-io/src/main/java/eu/darken/sdmse/common/adb/local/DirectAdbOptions.kt")
        for (key in listOf("AUTO_PAIR", "WATCHDOG", "START_ON_BOOT", "RESTORE_AFTER_RESTART", "TCP_5555")) {
            assertTrue(options.contains(key), key)
        }
        assertTrue(options.contains("option != Option.RESTORE_AFTER_RESTART"))
    }

    @Test
    fun `broken streams require fresh uid verification`() {
        val transport = source("app-common-io/src/main/java/eu/darken/sdmse/common/adb/local/AdbTransport.kt")
        assertTrue(transport.contains("if (manager === mgr)"))
        assertTrue(transport.contains("mgr.disconnect()"))
        assertTrue(transport.contains("watchdog.cancel()"))
        val direct = source("app-common-io/src/main/java/eu/darken/sdmse/common/adb/local/DirectAdb.kt")
        assertTrue(direct.contains("AdbTransport.exec(\"id -u\")"))
        assertTrue(direct.contains("uid == \"2000\""))
        assertTrue(direct.contains("AdbTransport.disconnect()"))
        assertTrue(direct.contains("DirectAdbOptions.tlsPort(context)"))
    }
}
