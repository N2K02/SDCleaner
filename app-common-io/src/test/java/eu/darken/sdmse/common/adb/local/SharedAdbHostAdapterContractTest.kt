package eu.darken.sdmse.common.adb.local

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

class SharedAdbHostAdapterContractTest {
    @Test fun singletonCoreRetainsOriginalIdentityAndManualStop() {
        val module = listOf(File("."), File("app-common-io")).first {
            it.resolve("src/main/java/eu/darken/sdmse/common/adb/local").isDirectory
        }
        val build = module.resolve("build.gradle.kts").readText()
        val s = module.resolve("src/main/java/eu/darken/sdmse/common/adb/local/SharedAdbHostAdapter.kt").readText()
        assertTrue(build.contains("dev.n2k02:adb-core:0.1.6"))
        for (required in listOf("AdbIdentity.getOrCreate(ctx)",
            "DirectAdbOptions.stopRequested(app)", "DirectAdbOptions.tlsPort(app)",
            "DirectAdbOptions.markAuthorized(app)", "AdbCoreRegistry.install(it)",
            "AdbCoreRegistry.uninstall(it)", "LibAdbWire"))
            assertTrue(s.contains(required), required)
    }
}
