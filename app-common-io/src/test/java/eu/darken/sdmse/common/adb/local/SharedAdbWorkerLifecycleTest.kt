package eu.darken.sdmse.common.adb.local

import java.io.File
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SharedAdbWorkerLifecycleTest {
    private fun source(name: String): String {
        val path = "src/main/java/eu/darken/sdmse/common/adb/local/" + name
        return listOf(File(path), File("app-common-io/" + path)).first { it.isFile }.readText()
    }

    @Test fun workerCompletionKeepsAuthorizationAndRecoversLater() {
        val direct = source("DirectAdb.kt")
        val recovery = source("DirectAdbRecovery.kt")
        assertTrue(direct.contains("core.disconnectIdle()"))
        assertTrue(recovery.contains("core.acquireService()"))
        assertTrue(recovery.contains("core.releaseService()"))
        assertTrue(recovery.contains("core.startAfterBoot()"))
        assertFalse(direct.contains("DirectAdbOptions.markStopped(context, true)"))
    }

    @Test fun explicitUserStopIsStickyButWorkerDisconnectIsNot() {
        val recovery = source("DirectAdbRecovery.kt")
        assertTrue(recovery.contains("fun stopForQuit()"))
        assertTrue(recovery.contains("fun manualDisconnect()"))
        assertTrue(recovery.contains("DirectAdbOptions.markStopped(app, true)"))
        assertTrue(recovery.contains("core.stop()"))
        val direct = source("DirectAdb.kt")
        val method = direct.substringAfter("suspend fun disconnect()")
            .substringBefore("val wasAuthorized:")
        assertTrue(method.contains("core.disconnectIdle()"))
        assertFalse(method.contains("core.stop()"))
        assertFalse(method.contains("core.quit()"))
    }
}
