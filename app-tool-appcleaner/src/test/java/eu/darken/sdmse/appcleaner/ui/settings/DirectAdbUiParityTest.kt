package eu.darken.sdmse.appcleaner.ui.settings

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The ADB settings page matches the VREC five-toggle and status-card layout. */
class DirectAdbUiParityTest {
    private fun read(): String {
        val relative = "src/main/java/eu/darken/sdmse/appcleaner/ui/settings/DirectAdbSettingsCard.kt"
        return listOf(File(relative), File("app-tool-appcleaner/$relative"),
            File("../app-tool-appcleaner/$relative"))
            .first { it.isFile }.readText()
    }

    @Test fun `status and two settings cards match ADB app layout`() {
        val ui = read()
        for (label in listOf("ADB connection", "Pairing & recovery", "Connection & security",
            "Shell UID 2000 verified", "Last verified", "Discovery", "Transport")) {
            assertTrue(label, ui.contains(label))
        }
    }

    @Test fun `five shared toggles are available without duplicated controls`() {
        val ui = read()
        for (label in listOf("Auto-detect pairing code", "Connection watchdog",
            "Start on boot", "Restore after restart", "Keep ADB without Wi-Fi (TCP/5555)")) {
            assertTrue(label, ui.contains(label))
        }
        assertEquals(5, "option = DirectAdbOptions.Option.".toRegex()
            .findAll(ui).count())
        assertTrue(ui.contains("Manual pairing (fallback)"))
        assertTrue(ui.contains("DisposableEffect(vm)"))
        assertTrue(ui.contains("vm.enterSettings()"))
        assertTrue(ui.contains("vm.leaveSettings()"))
    }
}
