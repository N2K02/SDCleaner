package eu.darken.sdmse.common.adb.local

import java.io.File
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Guard the fixed libadb callback and prevent modules from selecting the old jar. */
class AdbDeviceNameRegressionTest {
    private fun root() =
        generateSequence(File(System.getProperty("user.dir")).canonicalFile) { it.parentFile }
            .first { it.resolve("app-common-io/src/main/java/eu/darken/sdmse/common/adb/local/SharedAdbHostAdapter.kt").isFile }

    @Test fun bothAppAndSharedIoPinFixedAdbCoreVersion() {
        val app = root().resolve("app/build.gradle.kts").readText()
        val io = root().resolve("app-common-io/build.gradle.kts").readText()
        assertTrue(app.contains("dev.n2k02:adb-core:0.1.7"))
        assertTrue(io.contains("dev.n2k02:adb-core:0.1.7"))
        assertFalse(app.contains("dev.n2k02:adb-core:0.1.4"))
        assertFalse(io.contains("dev.n2k02:adb-core:0.1.4"))
        assertTrue(Class.forName("dev.n2k02.adb.core.LibAdbWire").name.endsWith("LibAdbWire"))
    }

    @Test fun applicationKeepsSharedTransportWithNonRecursiveIdentity() {
        val adapter = root().resolve("app-common-io/src/main/java/eu/darken/sdmse/common/adb/local/SharedAdbHostAdapter.kt").readText()
        assertTrue(adapter.contains("LibAdbWire"))
        assertFalse(adapter.contains("override fun getDeviceName()"))
        assertFalse(adapter.contains("AbsAdbConnectionManager()"))
    }
}
