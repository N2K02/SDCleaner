package eu.darken.sdmse.main.core.adb

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

/** Detect drift between SDCleaner and the canonical ADB core. */
class DirectAdbParityContractTest {
    private fun source(path: String): String = listOf(File(path), File("../" + path))
        .first { it.isFile }.readText()

    @Test fun settingsAndHomeUseSameCanonicalSharedUi() {
        val ui = source("app-tool-appcleaner/src/main/java/eu/darken/sdmse/appcleaner/ui/settings/DirectAdbSettingsCard.kt")
        assertTrue(ui.contains("AdbConnectionSettingsPage("))
        assertTrue(ui.contains("AdbHomeSetupPage(vm.sharedCore)"))
        assertTrue(ui.contains("DirectAdbPairingAccessibilityService"))
        assertFalse(ui.contains("DirectAdbSettingSection("))
        assertFalse(ui.contains("Pairing & recovery"))
        assertFalse(ui.contains("Connection & security"))
        val settings = source("app/src/main/java/eu/darken/sdmse/main/ui/settings/SettingsScreen.kt")
        assertTrue(settings.contains("CleanerSettingsRoute(\"adb\")"))
        assertTrue(settings.contains("\"adb\" -> DirectAdbSettingsCard()"))
        val home = source("app/src/main/java/eu/darken/sdmse/cleaner/CleanerHome.kt")
        assertTrue(home.contains("DirectAdbHomeSetupCard(vm = adbVm)"))
        assertTrue(home.contains("adbVm.enterSettings()"))
        assertTrue(home.contains("adbVm.leaveSettings()"))
    }

    @Test fun localOnlyDiscoveryIsProvidedByTheCoreRatherThanDuplicated() {
        val recovery = source("app-common-io/src/main/java/eu/darken/sdmse/common/adb/local/DirectAdbRecovery.kt")
        val adapter = source("app-common-io/src/main/java/eu/darken/sdmse/common/adb/local/SharedAdbHostAdapter.kt")
        assertTrue(adapter.contains("LocalAdbDiscovery(app)"))
        assertTrue(recovery.contains("core.enterPage()"))
        assertTrue(recovery.contains("core.leavePage()"))
        assertTrue(recovery.contains("core.acquireService()"))
        assertTrue(recovery.contains("core.releaseService()"))
        assertTrue(recovery.contains("DirectAdbOptions.stopRequested(app)"))
        assertFalse(recovery.contains("NetworkInterface.getNetworkInterfaces()"))
        assertFalse(recovery.contains("NsdManager"))
        assertFalse(recovery.contains("WifiManager"))
    }

    @Test fun pairingAccessibilityStillReadsOnlySystemSettings() {
        val reader = source("app-tool-appcleaner/src/main/java/eu/darken/sdmse/appcleaner/ui/settings/DirectAdbPairingAccessibilityService.kt")
        assertTrue(reader.contains("SharedAdbPairingAccessibilityService()"))
        assertTrue(reader.contains("SharedAdbHostAdapter.core(applicationContext)"))
        assertFalse(reader.contains("performAction("))
        assertFalse(reader.contains("Settings.Secure.putString"))
        val opts = source("app-common-io/src/main/java/eu/darken/sdmse/common/adb/local/DirectAdbOptions.kt")
        for (name in listOf("AUTO_PAIR", "WATCHDOG", "START_ON_BOOT",
            "RESTORE_AFTER_RESTART", "TCP_5555"))
            assertTrue(opts.contains(name))
        assertTrue(opts.contains("option != Option.RESTORE_AFTER_RESTART"))
    }

    @Test fun bootWorkerCannotDisconnectForegroundAdbSession() {
        val worker = source("app/src/main/java/eu/darken/sdmse/main/core/adb/DirectAdbBootWorker.kt")
        val recovery = source("app-common-io/src/main/java/eu/darken/sdmse/common/adb/local/DirectAdbRecovery.kt")
        assertTrue(recovery.contains("fun isSettingsVisible(): Boolean = visible"))
        assertTrue(worker.contains("if (!recovery.isSettingsVisible())"))
        assertTrue(worker.contains("withTimeoutOrNull(5_000L) { adb.disconnect() }"))
        assertTrue(worker.contains("withContext(NonCancellable)"))
    }

    @Test fun cleanupCommandsMustReverifyUidAndDisconnectTransiently() {
        val wire = source("app-common-io/src/main/java/eu/darken/sdmse/common/adb/local/AdbTransport.kt")
        val direct = source("app-common-io/src/main/java/eu/darken/sdmse/common/adb/local/DirectAdb.kt")
        assertFalse(wire.contains("AbsAdbConnectionManager"))
        assertFalse(wire.contains("private var shell"))
        assertTrue(wire.contains("core().execVerified(command, timeoutMs)"))
        assertTrue(direct.contains("core.execVerified(wrapped, timeoutMs)"))
        assertTrue(direct.contains("core.disconnectIdle()"))
        assertTrue(direct.contains("if (code == null || code !in 0..255)"))
        assertFalse(direct.contains("core.stop()"))
    }
}
