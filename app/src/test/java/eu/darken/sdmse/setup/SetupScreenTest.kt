package eu.darken.sdmse.setup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.permissions.Permission
import eu.darken.sdmse.common.files.saf.SAFPath
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.setup.inventory.InventorySetupCardItem
import eu.darken.sdmse.setup.inventory.InventorySetupModule
import eu.darken.sdmse.setup.notification.NotificationSetupCardItem
import eu.darken.sdmse.setup.notification.NotificationSetupModule
import eu.darken.sdmse.setup.saf.SAFSetupCardItem
import eu.darken.sdmse.setup.saf.SAFSetupModule
import eu.darken.sdmse.setup.storage.StorageSetupCardItem
import eu.darken.sdmse.setup.storage.StorageSetupModule
import io.mockk.mockk
import java.io.File
import java.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest
import eu.darken.sdmse.common.R as CommonR

class SetupScreenTest : BaseComposeRobolectricTest() {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `loading state shows progress indicator and no cards`() {
        composeRule.setSetupContent {
            SetupScreen(uiState = SetupUiState.Loading)
        }

        composeRule.onAllNodesWithText(context.getString(R.string.setup_notification_title)).assertCountEquals(0)
        composeRule.onAllNodesWithText(context.getString(CommonR.string.general_continue)).assertCountEquals(0)
    }

    @Test
    fun `complete state renders no cards and no continue button`() {
        // Complete triggers auto-nav in the Host; the Screen just shows a loading indicator.
        composeRule.setSetupContent {
            SetupScreen(uiState = SetupUiState.Complete)
        }

        composeRule.onAllNodesWithText(context.getString(CommonR.string.general_continue)).assertCountEquals(0)
        composeRule.onAllNodesWithText(context.getString(R.string.setup_notification_title)).assertCountEquals(0)
        composeRule.onAllNodesWithText(context.getString(R.string.setup_saf_card_title)).assertCountEquals(0)
    }

    @Test
    fun `onboarding mode hides data areas menu entry`() {
        composeRule.setSetupContent {
            SetupScreen(
                uiState = SetupUiState.Cards(items = emptyList()),
                isOnboarding = true,
            )
        }

        composeRule.onAllNodesWithText(context.getString(R.string.data_areas_label)).assertCountEquals(0)
    }

    @Test
    fun `loading card shows per-type title and loading hint`() {
        composeRule.setSetupContent {
            SetupScreen(
                uiState = SetupUiState.Cards(
                    items = listOf(SetupLoadingCardItem(state = LoadingState(SetupModule.Type.SAF))),
                ),
            )
        }
        composeRule.onAllNodesWithText(context.getString(R.string.setup_saf_card_title)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(CommonR.string.general_progress_loading)).assertCountEquals(1)
    }

    @Test
    fun `notification card shows body and grant button when incomplete`() {
        composeRule.setSetupContent {
            SetupScreen(
                uiState = SetupUiState.Cards(
                    items = listOf(
                        NotificationSetupCardItem(
                            state = NotificationSetupModule.Result(
                                missingPermission = setOf(Permission.POST_NOTIFICATIONS),
                            ),
                            onGrantAction = {},
                            onHelp = {},
                        ),
                    ),
                ),
            )
        }
        composeRule.onAllNodesWithText(context.getString(R.string.setup_notification_title)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.setup_notification_body)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(CommonR.string.general_grant_access_action)).assertCountEquals(1)
    }

    @Test
    fun `inventory card with an incomplete list shows limitation box and open settings button`() {
        composeRule.setSetupContent {
            SetupScreen(
                uiState = SetupUiState.Cards(
                    items = listOf(
                        InventorySetupCardItem(
                            state = InventorySetupModule.Result(
                                missingPermission = emptySet(),
                                access = InventorySetupModule.InventoryAccess.Incomplete,
                                settingsIntent = Intent(),
                            ),
                            onGrantAction = {},
                            onHelp = {},
                            onRetry = {},
                        ),
                    ),
                ),
            )
        }
        composeRule.onAllNodesWithText(context.getString(R.string.setup_permission_granted_label)).assertCountEquals(0)
        composeRule.onAllNodesWithText(context.getString(R.string.setup_inventory_limitation_title)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.setup_inventory_limitation_body)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.setup_inventory_limitation_body2)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(CommonR.string.general_help_action)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(CommonR.string.general_open_system_settings_action)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(CommonR.string.general_grant_access_action)).assertCountEquals(0)
    }

    @Test
    fun `inventory card limitation box buttons invoke settings and help actions`() {
        var settingsClicks = 0
        var helpClicks = 0
        composeRule.setSetupContent {
            SetupScreen(
                uiState = SetupUiState.Cards(
                    items = listOf(
                        InventorySetupCardItem(
                            state = InventorySetupModule.Result(
                                missingPermission = emptySet(),
                                access = InventorySetupModule.InventoryAccess.Incomplete,
                                settingsIntent = Intent(),
                            ),
                            onGrantAction = { settingsClicks++ },
                            onHelp = { helpClicks++ },
                            onRetry = {},
                        ),
                    ),
                ),
            )
        }
        composeRule.onNodeWithText(context.getString(CommonR.string.general_open_system_settings_action)).performClick()
        composeRule.onNodeWithText(context.getString(CommonR.string.general_help_action)).performClick()
        composeRule.runOnIdle {
            assertTrue(settingsClicks == 1)
            // The container help icon has no text label, so the click unambiguously hit the box button.
            assertTrue(helpClicks == 1)
        }
    }

    @Test
    fun `inventory card with missing permission shows grant button and no limitation box`() {
        composeRule.setSetupContent {
            SetupScreen(
                uiState = SetupUiState.Cards(
                    items = listOf(
                        InventorySetupCardItem(
                            state = InventorySetupModule.Result(
                                missingPermission = setOf(Permission.GET_INSTALLED_APPS),
                                access = InventorySetupModule.InventoryAccess.NotChecked,
                                settingsIntent = Intent(),
                            ),
                            onGrantAction = {},
                            onHelp = {},
                            onRetry = {},
                        ),
                    ),
                ),
            )
        }
        composeRule.onAllNodesWithText(context.getString(CommonR.string.general_grant_access_action)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.setup_inventory_limitation_title)).assertCountEquals(0)
        composeRule.onAllNodesWithText(context.getString(R.string.setup_permission_granted_label)).assertCountEquals(0)
    }

    @Test
    fun `inventory card with a failed probe shows the retry box and no settings button`() {
        composeRule.setSetupContent {
            SetupScreen(
                uiState = SetupUiState.Cards(
                    items = listOf(
                        InventorySetupCardItem(
                            state = InventorySetupModule.Result(
                                missingPermission = emptySet(),
                                access = InventorySetupModule.InventoryAccess.ProbeFailed,
                                settingsIntent = Intent(),
                            ),
                            onGrantAction = {},
                            onHelp = {},
                            onRetry = {},
                        ),
                    ),
                ),
            )
        }
        composeRule.onAllNodesWithText(context.getString(R.string.setup_inventory_probe_failed_title))
            .assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.setup_inventory_probe_failed_body))
            .assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(CommonR.string.general_retry_action)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(CommonR.string.general_help_action)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(CommonR.string.general_open_system_settings_action))
            .assertCountEquals(0)
        composeRule.onAllNodesWithText(context.getString(R.string.setup_permission_granted_label)).assertCountEquals(0)
        composeRule.onAllNodesWithText(context.getString(R.string.setup_inventory_limitation_title)).assertCountEquals(0)
    }

    @Test
    fun `inventory card retry button invokes onRetry`() {
        var retryClicks = 0
        composeRule.setSetupContent {
            SetupScreen(
                uiState = SetupUiState.Cards(
                    items = listOf(
                        InventorySetupCardItem(
                            state = InventorySetupModule.Result(
                                missingPermission = emptySet(),
                                access = InventorySetupModule.InventoryAccess.ProbeFailed,
                                settingsIntent = Intent(),
                            ),
                            onGrantAction = {},
                            onHelp = {},
                            onRetry = { retryClicks++ },
                        ),
                    ),
                ),
            )
        }
        composeRule.onNodeWithText(context.getString(CommonR.string.general_retry_action)).performClick()
        composeRule.runOnIdle {
            assertTrue(retryClicks == 1)
        }
    }

    @Test
    fun `inventory card probe-failed help button invokes onHelp`() {
        var helpClicks = 0
        composeRule.setSetupContent {
            SetupScreen(
                uiState = SetupUiState.Cards(
                    items = listOf(
                        InventorySetupCardItem(
                            state = InventorySetupModule.Result(
                                missingPermission = emptySet(),
                                access = InventorySetupModule.InventoryAccess.ProbeFailed,
                                settingsIntent = Intent(),
                            ),
                            onGrantAction = {},
                            onHelp = { helpClicks++ },
                            onRetry = {},
                        ),
                    ),
                ),
            )
        }
        composeRule.onNodeWithText(context.getString(CommonR.string.general_help_action)).performClick()
        composeRule.runOnIdle {
            // The container help icon has no text label, so the click unambiguously hit the box button.
            assertTrue(helpClicks == 1)
        }
    }

    @Test
    fun `storage card renders path labels`() {
        composeRule.setSetupContent {
            SetupScreen(
                uiState = SetupUiState.Cards(
                    items = listOf(
                        StorageSetupCardItem(
                            state = StorageSetupModule.Result(
                                paths = listOf(
                                    StorageSetupModule.Result.PathAccess(
                                        label = "Public storage".toCaString(),
                                        localPath = LocalPath.build(File("/storage/emulated/0")),
                                        hasAccess = false,
                                    ),
                                ),
                                missingPermission = setOf(Permission.READ_EXTERNAL_STORAGE),
                            ),
                            onPathClicked = {},
                            onHelp = {},
                        ),
                    ),
                ),
            )
        }
        composeRule.onAllNodesWithText("Public storage").assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(CommonR.string.general_grant_access_action)).assertCountEquals(1)
    }

    @Test
    fun `storage card shows granted label per granted path and hides grant button when complete`() {
        composeRule.setSetupContent {
            SetupScreen(
                uiState = SetupUiState.Cards(
                    items = listOf(
                        StorageSetupCardItem(
                            state = StorageSetupModule.Result(
                                paths = listOf(
                                    StorageSetupModule.Result.PathAccess(
                                        label = "Public storage".toCaString(),
                                        localPath = LocalPath.build(File("/storage/emulated/0")),
                                        hasAccess = true,
                                    ),
                                    StorageSetupModule.Result.PathAccess(
                                        label = "SD card".toCaString(),
                                        localPath = LocalPath.build(File("/storage/ABCD-EF12")),
                                        hasAccess = true,
                                    ),
                                ),
                                missingPermission = emptySet(),
                            ),
                            onPathClicked = {},
                            onHelp = {},
                        ),
                    ),
                ),
            )
        }

        composeRule.onAllNodesWithText(context.getString(R.string.setup_permission_granted_label)).assertCountEquals(2)
        composeRule.onAllNodesWithText(context.getString(CommonR.string.general_grant_access_action)).assertCountEquals(0)
    }

    @Test
    fun `saf card shows mixed access state and keeps grant action while incomplete`() {
        composeRule.setSetupContent {
            SetupScreen(
                uiState = SetupUiState.Cards(
                    items = listOf(
                        SAFSetupCardItem(
                            state = SAFSetupModule.Result(
                                paths = listOf(
                                    SAFSetupModule.Result.PathAccess(
                                        label = "Public storage".toCaString(),
                                        safPath = SAFPath.build(
                                            Uri.parse("content://com.android.externalstorage.documents/tree/primary%3A"),
                                        ),
                                        localPath = LocalPath.build(File("/storage/emulated/0")),
                                        uriPermission = null,
                                        grantIntent = Intent(),
                                    ),
                                    SAFSetupModule.Result.PathAccess(
                                        label = "Public app data".toCaString(),
                                        safPath = SAFPath.build(
                                            Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AAndroid%2Fdata"),
                                        ),
                                        localPath = LocalPath.build(File("/storage/emulated/0/Android/data")),
                                        uriPermission = fakeUriPermission(),
                                        grantIntent = Intent(),
                                    ),
                                ),
                            ),
                            onPathClicked = {},
                            onHelp = {},
                        ),
                    ),
                ),
            )
        }

        composeRule.onAllNodesWithText(context.getString(R.string.setup_permission_granted_label)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(CommonR.string.general_grant_access_action)).assertCountEquals(1)
    }

    @Test
    fun `saf card hides grant action once all paths are granted`() {
        composeRule.setSetupContent {
            SetupScreen(
                uiState = SetupUiState.Cards(
                    items = listOf(
                        SAFSetupCardItem(
                            state = SAFSetupModule.Result(
                                paths = listOf(
                                    SAFSetupModule.Result.PathAccess(
                                        label = "Public storage".toCaString(),
                                        safPath = SAFPath.build(
                                            Uri.parse("content://com.android.externalstorage.documents/tree/primary%3A"),
                                        ),
                                        localPath = LocalPath.build(File("/storage/emulated/0")),
                                        uriPermission = fakeUriPermission(),
                                        grantIntent = Intent(),
                                    ),
                                ),
                            ),
                            onPathClicked = {},
                            onHelp = {},
                        ),
                    ),
                ),
            )
        }

        composeRule.onAllNodesWithText(context.getString(R.string.setup_permission_granted_label)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(CommonR.string.general_grant_access_action)).assertCountEquals(0)
    }

    private data class LoadingState(
        override val type: SetupModule.Type,
        override val startAt: Instant = Instant.now(),
    ) : SetupModule.State.Loading
}

private fun ComposeContentTestRule.setSetupContent(
    content: @Composable () -> Unit,
) {
    setContent {
        PreviewWrapper {
            content()
        }
    }
}

private fun fakeUriPermission(): android.content.UriPermission =
    mockk(relaxed = true)
