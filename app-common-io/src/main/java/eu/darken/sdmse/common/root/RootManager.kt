package eu.darken.sdmse.common.root

import eu.darken.sdmse.common.access.AccessState
import eu.darken.sdmse.common.root.service.RootServiceClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/** Compatibility boundary for shared cleaning code. SDCleaner never requests root access. */
@Singleton
class RootManager @Inject constructor(val serviceClient: RootServiceClient) {
    val useRoot: Flow<Boolean> = flowOf(false)
    val accessState: Flow<AccessState> = flowOf(AccessState.Declined)
    suspend fun isRooted(): Boolean = false
    suspend fun isInstalled(): Boolean = false
    fun refresh() = Unit
}
