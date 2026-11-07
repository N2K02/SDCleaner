package eu.darken.sdmse.common.adb.local

import java.io.File
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Never resurrect a second libadb manager alongside the shared core. */
class AdbStreamRecoveryContractTest {
    private fun source(name: String): String {
        val path = "src/main/java/eu/darken/sdmse/common/adb/local/" + name
        return listOf(File(path), File("app-common-io/" + path)).first { it.isFile }.readText()
    }

    @Test fun transportHasNoPrivateAdbWire() {
        val s = source("AdbTransport.kt")
        assertTrue(s.contains("SharedAdbHostAdapter.current()"))
        assertTrue(s.contains("core().execVerified(command, timeoutMs)"))
        for (forbidden in listOf("AbsAdbConnectionManager", "AdbStream",
            "private var manager", "private var shell", "detached.async"))
            assertFalse(forbidden, s.contains(forbidden))
    }

    @Test fun cancelledCleanupRetiresStreamWithoutStickyStop() {
        val s = source("DirectAdb.kt")
        assertTrue(s.contains("catch (cancelled: CancellationException)"))
        assertTrue(s.contains("withContext(NonCancellable) { core.disconnectIdle() }"))
        assertTrue(s.contains("throw cancelled"))
        assertTrue(s.contains("core.execVerified(wrapped, timeoutMs)"))
        assertTrue(s.contains("suspend fun disconnect()"))
        assertFalse(s.contains("core.stop()"))
    }

    @Test fun invalidCommandCompletionIsFatal() {
        val s = source("DirectAdb.kt")
        assertTrue(s.contains("if (code == null || code !in 0..255)"))
        assertTrue(s.contains("throw TransportException(if (last == null)"))
        assertTrue(s.contains("lines.filterNot { it.startsWith(tag) }"))
        assertTrue(s.contains("CommandResult(confirmedCode,"))
    }
}
