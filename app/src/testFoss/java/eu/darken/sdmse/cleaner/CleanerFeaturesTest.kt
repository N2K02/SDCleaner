package eu.darken.sdmse.cleaner

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CleanerFeaturesTest {
    @Test fun `fresh install and refresh include all cleaning features without a purchase`() = runTest {
        val features = CleanerFeatures()
        assertTrue(features.upgradeInfo.first().isPro)
        features.refresh()
        val info = features.upgradeInfo.first()
        assertTrue(info.isPro)
        assertTrue(info.isSettled)
        assertNull(info.error)
        assertNull(info.upgradedAt)
    }
}
