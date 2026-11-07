package eu.darken.sdmse.common.adb.local

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class SharedAdbHostAdapterContractTest {
    @Test fun `library is pinned with legacy authorization and Stop state kept`() {
        val mod = listOf(File("."), File("app-common-io")).first {
            it.resolve("src/main/java/eu/darken/sdmse/common/adb/local").isDirectory
        }
        val build = mod.resolve("build.gradle.kts").readText()
        val src = mod.resolve("src/main/java/eu/darken/sdmse/common/adb/local/SharedAdbHostAdapter.kt").readText()
        assertTrue(build.contains("dev.n2k02:adb-core:0.1.1"))
        assertTrue(src.contains("AdbIdentity.getOrCreate(ctx)"))
        assertTrue(src.contains("DirectAdbOptions.stopRequested(app)"))
        assertTrue(src.contains("DirectAdbOptions.tlsPort(app)"))
        assertTrue(src.contains("LibAdbWire"))
    }
}
