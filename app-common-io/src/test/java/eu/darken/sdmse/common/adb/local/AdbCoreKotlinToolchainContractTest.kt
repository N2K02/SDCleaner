package eu.darken.sdmse.common.adb.local

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** Latest-stable Kotlin across app, buildSrc and shared ADB metadata readers. */
class AdbCoreKotlinToolchainContractTest {
    private fun repoRoot(): File {
        val cwd = File(System.getProperty("user.dir")).canonicalFile
        return generateSequence(cwd) { it.parentFile }
            .take(5)
            .first { it.resolve("buildSrc/src/main/java/Versions.kt").isFile }
    }

    @Test fun `kotlin compiler build logic Compose and KSP align with released ADB core`() {
        val root = repoRoot()
        val versions = root.resolve("buildSrc/src/main/java/Versions.kt").readText()
        val buildLogic = root.resolve("buildSrc/build.gradle.kts").readText()
        val build = root.resolve("build.gradle.kts").readText()
        val io = root.resolve("app-common-io/build.gradle.kts").readText()
        assertTrue(versions.contains("const val core = \"2.4.21\""))
        assertTrue(versions.contains("const val core = \"2.60.1\""))
        assertTrue(buildLogic.contains("kotlin-gradle-plugin:2.4.21"))
        assertTrue(build.contains("org.jetbrains.kotlin.plugin.compose\") version \"2.4.21"))
        assertTrue(build.contains("com.google.devtools.ksp\") version \"2.3.10"))
        assertTrue(io.contains("dev.n2k02:adb-core:0.1.2"))
    }
}
