package eu.darken.sdmse.common.adb.local

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DirectAdbInputTest {
    @Test fun `package validation accepts ordinary Android identifiers`() {
        assertTrue(DirectAdb.validPackage("com.example.app"))
        assertTrue(DirectAdb.validPackage("dev.n2k02.sdcleaner"))
    }

    @Test fun `package validation prevents command injection`() {
        assertFalse(DirectAdb.validPackage("com.example; rm -rf /"))
        assertFalse(DirectAdb.validPackage("com.example.app\nreboot"))
        assertFalse(DirectAdb.validPackage("com.example.app\\$(id)"))
        assertFalse(DirectAdb.validPackage("android"))
        assertFalse(DirectAdb.validPackage(""))
    }
}
