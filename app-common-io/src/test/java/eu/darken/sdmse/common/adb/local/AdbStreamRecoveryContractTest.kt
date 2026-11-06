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
        val retire = src.substringAfter("fun retire() {").substringBefore("val watchdog")
        assertTrue(retire.contains("session?.close()"))
        assertTrue(retire.contains("synchronized(this@AdbTransport)"))
        assertTrue(retire.contains("if (manager === mgr)"))
        assertTrue(retire.contains("manager = null"))
        assertTrue(retire.contains("lastKnownConnected = false"))
        assertTrue(retire.contains("mgr.disconnect()"))
        assertTrue(src.contains("private const val SHELL_SERVICE = \"exec:sh\""))
        assertTrue(src.contains("mutex.withLock"))
    }

    @Test fun `caller cancellation closes the stream instead of waiting for blocking read`() {
        val src = source("AdbTransport.kt")
        val body = src.substringAfter("val work = detached.async").substringBefore("/** Owns blocking ADB I/O")
        assertTrue(body.contains("work.await()"))
        val cancel = body.substringAfter("catch (e: CancellationException)").substringBefore("catch (t: Throwable)")
        assertTrue(cancel.contains("retire()"))
        assertTrue(cancel.contains("throw e"))
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
