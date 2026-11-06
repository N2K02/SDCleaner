package eu.darken.sdmse.common.adb.local

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** A dropped shell must never be reused after native libadb says Stream closed. */
class AdbStreamRecoveryContractTest {
    private fun source(path: String): String {
        val candidates = listOf(
            File("src/main/java/eu/darken/sdmse/common/adb/local/$path"),
            File("app-common-io/src/main/java/eu/darken/sdmse/common/adb/local/$path")
        )
        return candidates.first { it.isFile }.readText()
    }

    @Test fun `closed stream retires entire connection and clears identity flag`() {
        val src = source("AdbTransport.kt")
        val failed = src.substringAfter("// A desynchronised session stays desynchronised")
            .substringBefore("throw t")
        assertTrue(failed.contains("session?.close()"))
        assertTrue(failed.contains("synchronized(this@AdbTransport)"))
        assertTrue(failed.contains("if (manager === mgr)"))
        assertTrue(failed.contains("manager = null"))
        assertTrue(failed.contains("lastKnownConnected = false"))
        assertTrue(failed.contains("mgr.disconnect()"))
        assertTrue(src.contains("private const val SHELL_SERVICE = \"exec:sh\""))
        assertTrue(src.contains("mutex.withLock"))
    }

    @Test fun `invalid completion marker is a transport failure not an app failure`() {
        val src = source("DirectAdb.kt")
        assertTrue(src.contains("if (code == null)"))
        assertTrue(src.contains("AdbTransport.disconnect()"))
        assertTrue(src.contains("throw TransportException(if (last == null)"))
        assertTrue(src.contains("if (error is CancellationException) throw error"))
        assertTrue(src.contains("status.value = Status(message = \"ADB command stream interrupted\")"))
    }
}
