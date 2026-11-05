package eu.darken.sdmse.main.ui.settings

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class InstalledAppVersionTest {
    @Test
    fun `installed stable release version shows actual package version`() {
        formatInstalledVersion("1.2", 100_081) shouldBe "Version 1.2 (100081)"
    }

    @Test
    fun `installed experimental release version is not replaced with upstream version`() {
        formatInstalledVersion("1.2-experimental.81", 100_081) shouldBe
            "Version 1.2-experimental.81 (100081)"
    }

    @Test
    fun `unknown version name remains readable`() {
        formatInstalledVersion(null, 99) shouldBe "Version Unknown (99)"
    }

    @Test
    fun `diagnostics is no longer a supported settings page`() {
        cleanerSettingsTitle("diagnostics") shouldBe null
        cleanerSettingsTitle("about") shouldBe "About"
        cleanerSettingsTitle("updates") shouldBe "Updates"
    }
}
