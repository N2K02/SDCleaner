package eu.darken.sdmse.corpsefinder.ui.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.settings.FeatureGateState
import org.junit.Assert.assertEquals
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class CorpseFinderSettingsScreenTest : BaseComposeRobolectricTest() {

    private fun ComposeContentTestRule.setSettingsScreen(
        state: CorpseFinderSettingsViewModel.State,
        onFilterSdcardChanged: (Boolean) -> Unit = {},
        onIncludeRiskKeeperChanged: (Boolean) -> Unit = {},
        onHelp: () -> Unit = {},
    ) {
        setContent {
            PreviewWrapper {
                CorpseFinderSettingsScreen(
                    state = state,
                    onFilterSdcardChanged = onFilterSdcardChanged,
                    onIncludeRiskKeeperChanged = onIncludeRiskKeeperChanged,
                    onHelp = onHelp,
                )
            }
        }
    }

    // The settings screen uses a LazyColumn; rows below the viewport aren't composed until
    // scrolled into view. This helper locates the scrollable container and scrolls until the
    // node matching the given text becomes available in the semantics tree.
    private fun ComposeContentTestRule.scrollToText(text: String) {
        this.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }

    @Test
    fun `root-only filters stay absent with legacy root availability`() {
        composeRule.setSettingsScreen(CorpseFinderSettingsViewModel.State(
            rootFilterGate = FeatureGateState.AVAILABLE,
            isFilterPrivateDataAvailable = true,
        ))
        composeRule.onAllNodesWithText("Private app data").assertCountEquals(0)
        org.junit.Assert.assertThrows(AssertionError::class.java) { composeRule.scrollToText("Private app data") }
        composeRule.onAllNodesWithContentDescription("Set up").assertCountEquals(0)
    }

    @Test
    fun `top bar shows the CorpseFinder title`() {
        composeRule.setSettingsScreen(CorpseFinderSettingsViewModel.State())

        composeRule.onNodeWithText("CorpseFinder").assertExists()
    }

    @Test
    fun `watcher category header and switch row are visible at startup`() {
        composeRule.setSettingsScreen(CorpseFinderSettingsViewModel.State())

        // "Uninstall watcher" appears twice: once as the category header text and once as the
        // SettingsSwitchItem title. Both are above the fold and visible at startup.
        composeRule.onAllNodesWithText("Uninstall watcher").assertCountEquals(2)
    }

    @Test
    fun `tapping the SD card filter row toggles its callback with the inverted value`() {
        var captured: Boolean? = null
        composeRule.setSettingsScreen(
            state = CorpseFinderSettingsViewModel.State(filterSdcardEnabled = true),
            onFilterSdcardChanged = { captured = it },
        )

        composeRule.scrollToText("SD card")
        composeRule.onNodeWithText("SD card").performClick()

        // Started checked → tap inverts to `false`.
        captured shouldBe false
    }

    @Test
    fun `tapping the keeper risk row toggles its callback`() {
        var captured: Boolean? = null
        composeRule.setSettingsScreen(
            state = CorpseFinderSettingsViewModel.State(includeRiskKeeper = false),
            onIncludeRiskKeeperChanged = { captured = it },
        )

        // The keeper-risk row title resolves to "Include desirable remnants" (CorpseR string
        // `corpsefinder_settings_risk_keeper_title`). Scroll to it before tapping.
        composeRule.scrollToText("Include desirable remnants")
        composeRule.onNodeWithText("Include desirable remnants").performClick()

        captured shouldBe true
    }

    @Test
    fun `help action shows in the top bar and invokes onHelp`() {
        var help = 0
        composeRule.setSettingsScreen(CorpseFinderSettingsViewModel.State(), onHelp = { help++ })

        composeRule.onNodeWithContentDescription("Help").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, help) }
    }

    private infix fun <T> T.shouldBe(expected: T) {
        if (this != expected) throw AssertionError("Expected <$expected> but was <$this>")
    }
}
