package eu.darken.sdmse.common.root.service

import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.ipc.IpcClientModule
import eu.darken.sdmse.common.root.RootUnavailableException
import eu.darken.sdmse.common.sharedresource.SharedResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.plus
import javax.inject.Inject
import javax.inject.Singleton

/** Reject even direct callers; old preferences and restored backups cannot launch a su host. */
@Singleton
class RootServiceClient @Inject constructor(
    @AppScope coroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
) : SharedResource<RootServiceClient.Connection>(
    tag = TAG,
    parentScope = coroutineScope + dispatcherProvider.IO,
    source = flow { throw RootUnavailableException("Root access is not supported by SDCleaner") },
) {
    companion object { internal const val TAG = "SDCleaner:RootDisabled" }

    data class Connection(val ipc: RootServiceConnection, val clientModules: List<IpcClientModule>)
}
