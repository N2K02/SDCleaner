package eu.darken.sdmse.common.adb.local

import android.content.Context
import dev.n2k02.adb.core.AdbCore
import dev.n2k02.adb.core.AdbIdentity as CoreIdentity
import dev.n2k02.adb.core.AdbIdentitySource
import dev.n2k02.adb.core.AdbOption
import dev.n2k02.adb.core.AdbPreferenceStore
import dev.n2k02.adb.core.LibAdbWire
import dev.n2k02.adb.core.LocalAdbDiscovery

/** Adapter preserving SDCleaner's existing ADB identity and five settings.
 * Run only after the DirectAdb legacy transport has been safely released. */
internal object SharedAdbHostAdapter {
    fun create(context: Context): AdbCore {
        val app = context.applicationContext
        return AdbCore(
            LibAdbWire(app, AdbIdentitySource { ctx ->
                val old = AdbIdentity.getOrCreate(ctx)
                CoreIdentity(old.privateKey, old.certificate)
            }, "SDCleaner"),
            Preferences(app), LocalAdbDiscovery(app)
        )
    }
    private class Preferences(private val app: Context) : AdbPreferenceStore {
        private val times = app.getSharedPreferences("sdcleaner_direct_adb_options", Context.MODE_PRIVATE)
        override fun enabled(option: AdbOption) =
            DirectAdbOptions.get(app, DirectAdbOptions.Option.valueOf(option.name))
        override fun setEnabled(option: AdbOption, enabled: Boolean) =
            DirectAdbOptions.set(app, DirectAdbOptions.Option.valueOf(option.name), enabled)
        override fun manuallyStopped() = DirectAdbOptions.stopRequested(app)
        override fun setManuallyStopped(stopped: Boolean) = DirectAdbOptions.markStopped(app, stopped)
        override fun lastTlsPort() = DirectAdbOptions.tlsPort(app)
        override fun setLastTlsPort(port: Int) = DirectAdbOptions.rememberAuthorized(app, port)
        override fun lastVerifiedAt() = DirectAdbOptions.verifiedAt(app)
        override fun setLastVerifiedAt(timeMs: Long) {
            times.edit().putLong("verified_at", timeMs).apply()
        }
        override fun wasAuthorized() = DirectAdbOptions.wasAuthorized(app)
        override fun setAuthorized() {
            DirectAdbOptions.rememberAuthorized(app, DirectAdbOptions.tlsPort(app) ?: 5555)
        }
    }
}
