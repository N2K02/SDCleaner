package eu.darken.sdmse.appcleaner.ui.settings

import java.io.File
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** One versioned ADB Settings/Home UI, not copied cards per app. */
class DirectAdbUiParityTest {
    private fun source(): String {
        val path = "src/main/java/eu/darken/sdmse/appcleaner/ui/settings/DirectAdbSettingsCard.kt"
        return listOf(File(path), File("app-tool-appcleaner/" + path),
            File("../app-tool-appcleaner/" + path)).first { it.isFile }.readText()
    }

    @Test fun settingsUsesCanonicalTwoCardPageAndPreservesAccessibility() {
        val all = source()
        val settings = all.substringAfter("fun DirectAdbSettingsCard(")
            .substringBefore("fun DirectAdbHomeSetupCard(")
        assertTrue(settings.contains("AdbConnectionSettingsPage("))
        assertTrue(settings.contains("adb = vm.sharedCore"))
        assertTrue(settings.contains("DirectAdbPairingAccessibilityService"))
        assertTrue(settings.contains("Settings.ACTION_ACCESSIBILITY_SETTINGS"))
        assertTrue(settings.contains("Lifecycle.Event.ON_RESUME"))
        assertFalse(settings.contains("DirectAdbSettingSection("))
        assertFalse(settings.contains("Pairing & recovery"))
        assertFalse(settings.contains("Connection & security"))
        assertFalse(settings.contains("OutlinedTextField"))
        assertFalse(settings.contains("AdbPageLifecycle("))
    }

    @Test fun homeUsesSharedSetupWithoutLazyCardLifecycleOrDuplicateSettings() {
        val ui = source()
        val home = ui.substringAfter("fun DirectAdbHomeSetupCard(")
        assertTrue(home.contains("AdbHomeSetupPage(vm.sharedCore)"))
        assertFalse(home.contains("AdbPageLifecycle("))
        assertFalse(home.contains("DirectAdbSettingSection("))
        assertFalse(ui.contains("fun DirectAdbDiagnosticsCard("))
        assertTrue(ui.contains("fun enterSettings() = recovery.enterSettings()"))
        assertTrue(ui.contains("fun leaveSettings() = recovery.leaveSettings()"))
        assertFalse(ui.contains("fun DirectAdbSettingRow("))
    }

    @Test fun viewModelUsesSameCoreForUiWorkerAndManualRecovery() {
        val ui = source()
        assertTrue(ui.contains("val sharedCore = adb.sharedCore"))
        assertTrue(ui.contains("sharedCore.setOption(AdbOption.valueOf(option.name), checked)"))
        assertTrue(ui.contains("sharedCore.retry(auto = false)"))
        assertTrue(ui.contains("sharedCore.disableTcp()"))
    }
}
