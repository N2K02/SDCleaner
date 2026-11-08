package eu.darken.sdmse.appcleaner.ui.settings

import dev.n2k02.adb.core.AdbCore
import dev.n2k02.adb.core.SharedAdbPairingAccessibilityService
import eu.darken.sdmse.common.adb.local.SharedAdbHostAdapter

/** Manifest adapter only. The shared library owns all Settings code reading,
 * local discovery, pairing, retries, temporary leases and cancellation. */
class DirectAdbPairingAccessibilityService : SharedAdbPairingAccessibilityService() {
    override fun acquireCore(): AdbCore =
        SharedAdbHostAdapter.core(applicationContext)
}
