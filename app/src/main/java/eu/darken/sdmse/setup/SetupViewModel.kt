package eu.darken.sdmse.setup

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.SingleEventFlow
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import eu.darken.sdmse.common.navigation.routes.DashboardRoute
import eu.darken.sdmse.common.navigation.routes.DataAreasRoute
import eu.darken.sdmse.common.permissions.Permission
import eu.darken.sdmse.common.permissions.RuntimePermission
import eu.darken.sdmse.common.permissions.Specialpermission
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.setup.inventory.InventorySetupCardItem
import eu.darken.sdmse.setup.inventory.InventorySetupModule
import eu.darken.sdmse.setup.notification.NotificationSetupCardItem
import eu.darken.sdmse.setup.notification.NotificationSetupModule
import eu.darken.sdmse.setup.saf.SAFSetupCardItem
import eu.darken.sdmse.setup.saf.SAFSetupModule
import eu.darken.sdmse.setup.storage.StorageSetupCardItem
import eu.darken.sdmse.setup.storage.StorageSetupModule
import eu.darken.sdmse.setup.usagestats.UsageStatsSetupCardItem
import eu.darken.sdmse.setup.usagestats.UsageStatsSetupModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SetupViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    @Suppress("StaticFieldLeak") @ApplicationContext private val context: Context,
    private val setupManager: SetupManager,
    private val storageSetupModule: StorageSetupModule,
    private val safSetupModule: SAFSetupModule,
    private val webpageTool: WebpageTool,
    private val inventorySetupModule: InventorySetupModule,
) : ViewModel4(dispatcherProvider, TAG) {

    // Options are driven from the Host via setScreenOptions() (the SetupRoute entry forwards the
    // route object). Do NOT read SavedStateHandle.toRoute<>() here — it crashes under Nav3.
    private val screenOptionsFlow: MutableStateFlow<SetupScreenOptions> =
        MutableStateFlow(SetupScreenOptions())
    val screenOptions: SetupScreenOptions get() = screenOptionsFlow.value

    fun setScreenOptions(options: SetupScreenOptions) {
        log(TAG) { "setScreenOptions($options)" }
        screenOptionsFlow.value = options
    }

    init {
        log(TAG) { "Setup init: options=${screenOptionsFlow.value}" }
        setupManager.setDismissed(false)
    }

    val events = SingleEventFlow<SetupEvents>()

    private fun emitPermissionEvent(perm: Permission) {
        when (perm) {
            is Specialpermission -> events.tryEmit(
                SetupEvents.SpecialPermissionRequest(
                    item = perm,
                    intent = perm.createIntent(context, deviceDetective),
                    fallbackIntent = perm.createIntentFallback(context),
                )
            )

            is RuntimePermission -> events.tryEmit(SetupEvents.RuntimePermissionRequests(perm))
            else -> log(TAG, WARN) { "Unknown permission type for $perm, cannot launch" }
        }
    }

    private val itemsStateFlow: StateFlow<List<SetupCardItem>?> = combine(
        setupManager.state,
        screenOptionsFlow,
    ) { setupState, options ->
        val items = mutableListOf<SetupCardItem>()

        val typeFilter = options.typeFilter
        setupState.moduleStates
            .filter { typeFilter == null || typeFilter.contains(it.type) }
            .filter { it.type != SetupModule.Type.SHIZUKU && it.type != SetupModule.Type.AUTOMATION }
            .filter { it !is SetupModule.State.Current || !it.isComplete || options.showCompleted }
                .mapNotNull { state ->
                    when (state.type) {
                        SetupModule.Type.SAF -> when (state) {
                            is SetupModule.State.Current -> SAFSetupCardItem(
                                state = state as SAFSetupModule.Result,
                                onPathClicked = {
                                    if (!it.hasAccess) {
                                        events.tryEmit(SetupEvents.SafRequestAccess(it))
                                    }
                                },
                                onHelp = {
                                    webpageTool.open("https://sdmse.darken.eu/setup#storage-access-framework")
                                },
                            ).takeIf { it.state.paths.isNotEmpty() }

                            is SetupModule.State.Loading -> SetupLoadingCardItem(state)
                        }

                        SetupModule.Type.STORAGE -> when (state) {
                            is SetupModule.State.Current -> StorageSetupCardItem(
                                state = state as StorageSetupModule.Result,
                                onPathClicked = {
                                    state.missingPermission.firstOrNull()?.let { perm ->
                                        emitPermissionEvent(perm)
                                    }
                                },
                                onHelp = {
                                    webpageTool.open("https://sdmse.darken.eu/setup#manage-storage")
                                },
                            )

                            is SetupModule.State.Loading -> SetupLoadingCardItem(state)
                        }

                        SetupModule.Type.ROOT -> null // Unsupported legacy route.

                        SetupModule.Type.USAGE_STATS -> when (state) {
                            is SetupModule.State.Current -> UsageStatsSetupCardItem(
                                state = state as UsageStatsSetupModule.Result,
                                onGrantAction = {
                                    state.missingPermission.firstOrNull()?.let { perm ->
                                        emitPermissionEvent(perm)
                                    }
                                },
                                onHelp = {
                                    webpageTool.open("https://sdmse.darken.eu/setup#usage-statistics")
                                }
                            )

                            is SetupModule.State.Loading -> SetupLoadingCardItem(state)
                        }

                        SetupModule.Type.AUTOMATION -> null

                        SetupModule.Type.NOTIFICATION -> when (state) {
                            is SetupModule.State.Current -> NotificationSetupCardItem(
                                state = state as NotificationSetupModule.Result,
                                onGrantAction = {
                                    state.missingPermission.firstOrNull()?.let { perm ->
                                        emitPermissionEvent(perm)
                                    }
                                },
                                onHelp = {
                                    webpageTool.open("https://sdmse.darken.eu/setup#notifications")
                                }
                            )

                            is SetupModule.State.Loading -> SetupLoadingCardItem(state)
                        }

                        SetupModule.Type.SHIZUKU -> null

                        SetupModule.Type.INVENTORY -> when (state) {
                            is SetupModule.State.Current -> InventorySetupCardItem(
                                state = state as InventorySetupModule.Result,
                                onGrantAction = {
                                    val result = state
                                    val runtimePerm = result.missingPermission.firstOrNull { it is RuntimePermission }
                                    if (runtimePerm != null) {
                                        emitPermissionEvent(runtimePerm)
                                    } else {
                                        events.tryEmit(SetupEvents.ShowOurDetailsPage(result.settingsIntent))
                                    }
                                },
                                onHelp = {
                                    webpageTool.open(InventorySetupModule.INFO_URL)
                                },
                                onRetry = { launch { inventorySetupModule.refresh() } },
                            )

                            is SetupModule.State.Loading -> SetupLoadingCardItem(state)
                        }
                    }
                }
                // A card changing between incomplete, loading and complete must not move under the
                // user's finger. Completion is conveyed by each card's content; position is stable.
                .sortedInSetupDisplayOrder()
                .run { items.addAll(this) }

        items
    }
        .setupCommonEventHandlers(TAG) { "listItems" }
        .stateIn(vmScope, SharingStarted.Eagerly, null)

    internal val uiState: StateFlow<SetupUiState> = combine(
        itemsStateFlow,
        screenOptionsFlow,
    ) { items, options ->
            when {
                items == null -> SetupUiState.Loading
                items.isEmpty() && !options.showCompleted -> SetupUiState.Complete
                else -> SetupUiState.Cards(items)
            }
        }
        .distinctUntilChanged()
        .safeStateIn(
            initialValue = SetupUiState.Loading,
            onError = { SetupUiState.Loading },
        )

    fun onSafAccessGranted(uri: Uri?) = launch {
        log(TAG) { "onSafAccessGranted(uri=$uri)" }
        if (uri == null) return@launch
        try {
            safSetupModule.takePermission(uri)
        } catch (e: IllegalArgumentException) {
            events.tryEmit(SetupEvents.SafWrongPathError(e))
        }
    }

    fun onRuntimePermissionsGranted(permission: Permission?, granted: Boolean) = launch {
        log(TAG) { "onRuntimePermissionGranted(result=$permission,granted=$granted)" }
        if (granted) {
            val isStoragePermission = permission == Permission.READ_EXTERNAL_STORAGE ||
                permission == Permission.WRITE_EXTERNAL_STORAGE ||
                (android.os.Build.VERSION.SDK_INT >= 30 && permission == Permission.MANAGE_EXTERNAL_STORAGE)
            if (permission != null && isStoragePermission) {
                storageSetupModule.onPermissionChanged(permission, granted)
            }
            setupManager.refresh()
        } else if (permission == Permission.GET_INSTALLED_APPS) {
            // Runtime dialog may not work on some OEMs — fall back to app settings
            val settingsIntent = context.packageName.toPkgId().getSettingsIntent(context)
            events.tryEmit(SetupEvents.ShowOurDetailsPage(settingsIntent))
        }
    }

    fun onAccessibilityReturn() = launch {
        log(TAG) { "onAccessibilityReturn" }
        setupManager.refresh()
    }

    fun openSetupHelp() {
        webpageTool.open("https://sdmse.darken.eu/setup")
    }

    fun openSafMissingAppHelpUrl() {
        webpageTool.open("https://sdmse.darken.eu/setup#saf-open_document_tree-activitynotfoundexception")
    }

    fun openSafWrongPathHelpUrl() {
        webpageTool.open("https://sdmse.darken.eu/setup#storage-access-framework")
    }

    fun navToDataAreas() {
        navTo(DataAreasRoute)
    }

    fun navback() {
        if (screenOptions.isOnboarding) {
            navTo(
                DashboardRoute,
                popUpTo = DashboardRoute,
                inclusive = true,
            )
        } else {
            navUp()
        }
    }

    companion object {

        private val TAG = logTag("Setup", "ViewModel")
    }
}

private val SETUP_DISPLAY_ORDER = listOf(
    SetupModule.Type.INVENTORY,
    SetupModule.Type.NOTIFICATION,
    SetupModule.Type.STORAGE,
    SetupModule.Type.SAF,
    SetupModule.Type.SHIZUKU,
    SetupModule.Type.USAGE_STATS,
    SetupModule.Type.AUTOMATION,
)

internal fun List<SetupCardItem>.sortedInSetupDisplayOrder(): List<SetupCardItem> =
    sortedBy { item -> SETUP_DISPLAY_ORDER.indexOf(item.state.type) }
