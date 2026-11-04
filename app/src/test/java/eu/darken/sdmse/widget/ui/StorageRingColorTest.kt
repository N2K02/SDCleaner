package eu.darken.sdmse.widget.ui

import eu.darken.sdmse.common.ui.R as CommonUiR
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

/**
 * Checks the widget's color-resource selection without a Robolectric Android runtime.
 *
 * Resource resolution is still handled by Context.getColor() on the device; this unit test
 * verifies the SDK and low-space priorities without a network-dependent framework download.
 */
class StorageRingColorTest {
    @Test
    fun `normal storage below API 31 selects the app primary`() {
        storageArcColorResource(isLow = false, sdkInt = 30) shouldBe
            CommonUiR.color.md_theme_primary
    }

    @Test
    fun `normal storage on API 31 selects the Material You accent`() {
        storageArcColorResource(isLow = false, sdkInt = 31) shouldBe
            android.R.color.system_accent1_500
    }

    @Test
    fun `normal storage after API 31 uses the Material You accent`() {
        storageArcColorResource(isLow = false, sdkInt = 35) shouldBe
            android.R.color.system_accent1_500
    }

    @Test
    fun `low storage below API 31 selects amber`() {
        storageArcColorResource(isLow = true, sdkInt = 30) shouldBe
            CommonUiR.color.md_theme_storageLow
    }

    @Test
    fun `low storage overrides Material You colors on newer Android`() {
        storageArcColorResource(isLow = true, sdkInt = 33) shouldBe
            CommonUiR.color.md_theme_storageLow
        storageArcColorResource(isLow = true, sdkInt = 33) shouldNotBe
            android.R.color.system_accent1_500
    }
}
