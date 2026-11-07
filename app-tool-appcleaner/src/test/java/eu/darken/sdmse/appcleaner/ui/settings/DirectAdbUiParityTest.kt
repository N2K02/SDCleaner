package eu.darken.sdmse.appcleaner.ui.settings

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Parity with VRec: two settings cards, first-time setup on Home, diagnostics separate. */
class DirectAdbUiParityTest {
    private fun read(): String {
        val relative = "src/main/java/eu/darken/sdmse/appcleaner/ui/settings/DirectAdbSettingsCard.kt"
        return listOf(File(relative), File("app-tool-appcleaner/$relative"),
            File("../app-tool-appcleaner/$relative"))
            .first { it.isFile }.readText()
    }

    @Test fun `settings contains exactly two VRec recovery cards and five toggles`() {
        val ui = read()
        val settings = ui.substringAfter("fun DirectAdbSettingsCard(")
            .substringBefore("fun DirectAdbHomeSetupCard(")
        assertEquals(2, Regex("DirectAdbSettingSection\\(\"").findAll(settings).count())
        for (label in listOf("Pairing & recovery", "Connection & security",
            "Auto-detect pairing code", "Connection watchdog", "Start on boot",
            "Restore after restart", "Keep ADB without Wi-Fi (TCP/5555)",
            "Disable TCP/5555", "Enable Accessibility")) {
            assertTrue(settings.contains(label), label)
        }
        assertEquals(5, "option = DirectAdbOptions.Option.".toRegex()
            .findAll(settings).count())
        assertFalse(settings.contains("OutlinedTextField"))
        assertFalse(settings.contains("Manual pairing (fallback)"))
        assertFalse(settings.contains("Last verified:"))
        assertFalse(settings.contains("Discovery:"))
        assertTrue(settings.contains("status.verifiedUid == 2000"))
        assertTrue(settings.contains("securityNote"))
        assertTrue(settings.contains("tcpRefresh++"))
        assertTrue(ui.contains("remember(option, refreshKey)"))
        assertTrue(settings.contains("Lifecycle.Event.ON_RESUME"))
    }

    @Test fun `home owns initial pairing and diagnostics own detailed status`() {
        val ui = read()
        val home = ui.substringAfter("fun DirectAdbHomeSetupCard(")
            .substringBefore("fun DirectAdbDiagnosticsCard(")
        val diagnostics = ui.substringAfter("fun DirectAdbDiagnosticsCard(")
            .substringBefore("fun DirectAdbSettingSection(")
        for (label in listOf("Manual pairing (fallback)", "Six-digit pairing code",
                "Open Developer options", "Find / Retry", "Pair", "Connect")) {
            assertTrue(home.contains(label), label)
        }
        for (label in listOf("Shell UID 2000 verified", "Last verified", "Discovery",
            "Transport", "Pairing port", "Disconnect")) {
            assertTrue(diagnostics.contains(label), label)
        }
        assertFalse(diagnostics.contains("Pairing & recovery"))
        assertFalse(diagnostics.contains("Connection & security"))
        assertFalse(home.contains("DirectAdbSettingRow("))
        assertFalse(home.contains("DisposableEffect(vm)"))
        assertTrue(ui.contains("vm.enterSettings()"))
        assertTrue(ui.contains("vm.leaveSettings()"))
    }
}
