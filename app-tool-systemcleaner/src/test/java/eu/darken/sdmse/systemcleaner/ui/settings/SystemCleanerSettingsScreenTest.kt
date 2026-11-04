package eu.darken.sdmse.systemcleaner.ui.settings

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

class SystemCleanerSettingsScreenTest : BaseComposeRobolectricTest() {

    private fun ComposeContentTestRule.setSettingsScreen(
        state: SystemCleanerSettingsViewModel.State,
        onScreenshotsAgeSaved: (java.time.Duration) -> Unit = {},
        onRootFilterBadgeClick: () -> Unit = {},
        onHelp: () -> Unit = {},
    ) {
        setContent {
            PreviewWrapper {
                SystemCleanerSettingsScreen(
                    state = state,
                    onScreenshotsAgeSaved = onScreenshotsAgeSaved,
                    onRootFilterBadgeClick = onRootFilterBadgeClick,
                    onHelp = onHelp,
                )
            }
        }
    }

    // The settings screen uses a LazyColumn; rows below the viewport aren't composed until
    // scrolled into view.
    private fun ComposeContentTestRule.scrollToText(text: String) {
        this.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }

    @Test
    fun `root-only filter controls stay absent with legacy availability`() {
        composeRule.setSettingsScreen(SystemCleanerSettingsViewModel.State(
            areSystemFilterAvailable = true,
            systemFilterGate = FeatureGateState.AVAILABLE,
        ))
        composeRule.onAllNodesWithContentDescription("Set up").assertCountEquals(0)
    }

    @Test
    fun `top bar shows the SystemCleaner title`() {
        composeRule.setSettingsScreen(SystemCleanerSettingsViewModel.State())

        composeRule.onNodeWithText("SystemCleaner").assertExists()
    }

    @Test
    fun `superfluous APKs sub-row hidden when filter is off`() {
        composeRule.setSettingsScreen(
            SystemCleanerSettingsViewModel.State(filterSuperfluosApks = false),
        )

        // The "Include same version" row is conditional on filterSuperfluosApks. When the
        // parent toggle is off, this row should not be rendered at all.
        composeRule.onAllNodesWithText("Include same version").assertCountEquals(0)
    }

    @Test
    fun `superfluous APKs sub-row visible when filter is on`() {
        composeRule.setSettingsScreen(
            SystemCleanerSettingsViewModel.State(filterSuperfluosApks = true),
        )

        composeRule.scrollToText("Include same version")
        composeRule.onNodeWithText("Include same version").assertExists()
    }

    @Test
    fun `screenshots age row hidden when screenshots filter is off`() {
        composeRule.setSettingsScreen(
            SystemCleanerSettingsViewModel.State(filterScreenshots = false),
        )

        composeRule.onAllNodesWithText("Screenshot Age").assertCountEquals(0)
    }

    @Test
    fun `screenshots age row visible when screenshots filter is on`() {
        composeRule.setSettingsScreen(
            SystemCleanerSettingsViewModel.State(filterScreenshots = true),
        )

        composeRule.scrollToText("Screenshot Age")
        composeRule.onNodeWithText("Screenshot Age").assertExists()
    }

    @Test
    fun `help action shows in the top bar and invokes onHelp`() {
        var help = 0
        composeRule.setSettingsScreen(SystemCleanerSettingsViewModel.State(), onHelp = { help++ })

        composeRule.onNodeWithContentDescription("Help").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, help) }
    }
}
