package eu.darken.sdmse.main.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.appcleaner.core.AppCleanerSettings
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.corpsefinder.core.CorpseFinderSettings
import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Settings quick switches mirror individual real cleaner filters, not a separate shadow preference.
 * Other filters stay untouched. The row itself navigates to the full cleaner settings.
 */
@HiltViewModel
class CleaningQuickSettingsViewModel @Inject constructor(
    private val appCleaner: AppCleanerSettings,
    private val systemCleaner: SystemCleanerSettings,
    private val corpseFinder: CorpseFinderSettings,
) : ViewModel() {

    data class State(
        val cache: Boolean = true,
        val temp: Boolean = true,
        val obsolete: Boolean = true,
        val emptyFolders: Boolean = true,
    )

    val state: StateFlow<State> = combine(
        appCleaner.filterDefaultCachesPublicEnabled.flow,
        systemCleaner.filterTempFilesEnabled.flow,
        corpseFinder.filterPublicDataEnabled.flow,
        systemCleaner.filterEmptyDirectoriesEnabled.flow,
    ) { cache, temp, obsolete, emptyFolders ->
        State(cache, temp, obsolete, emptyFolders)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    fun setCacheEnabled(enabled: Boolean) = viewModelScope.launch {
        appCleaner.filterDefaultCachesPublicEnabled.value(enabled)
    }

    fun setTempEnabled(enabled: Boolean) = viewModelScope.launch {
        systemCleaner.filterTempFilesEnabled.value(enabled)
    }

    fun setObsoleteEnabled(enabled: Boolean) = viewModelScope.launch {
        corpseFinder.filterPublicDataEnabled.value(enabled)
    }

    fun setEmptyFoldersEnabled(enabled: Boolean) = viewModelScope.launch {
        systemCleaner.filterEmptyDirectoriesEnabled.value(enabled)
    }
}
