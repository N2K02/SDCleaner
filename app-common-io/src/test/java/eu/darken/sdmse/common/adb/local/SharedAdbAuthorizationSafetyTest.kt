package eu.darken.sdmse.common.adb.local

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** Never claim TCP/5555 merely because the TLS port is not yet persisted. */
class SharedAdbAuthorizationSafetyTest {
    @Test fun `core adapter records verified authorization without inventing TCP 5555`() {
        val cwd = File(System.getProperty("user.dir"))
        val root = if (cwd.resolve("src/main/java/eu/darken/sdmse/common/adb/local").isDirectory) cwd
            else cwd.resolve("app-common-io")
        val dir = root.resolve("src/main/java/eu/darken/sdmse/common/adb/local")
        val adapter = dir.resolve("SharedAdbHostAdapter.kt").readText()
        val options = dir.resolve("DirectAdbOptions.kt").readText()
        assertTrue(adapter.contains("DirectAdbOptions.markAuthorized(app)"))
        assertFalse(adapter.contains("tlsPort(app) ?: 5555"))
        assertTrue(options.contains("fun markAuthorized(context: Context)"))
        assertTrue(options.contains("putBoolean(\"previously_authorized\", true)"))
    }
}
