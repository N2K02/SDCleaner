package eu.darken.sdmse.common.adb.local

import android.content.Context
import android.content.pm.PackageManager
import dev.n2k02.adb.core.AdbCore
import dev.n2k02.adb.core.AdbCoreRegistry
import dev.n2k02.adb.core.AdbHostHooks
import dev.n2k02.adb.core.AdbIdentity as CoreIdentity
import dev.n2k02.adb.core.AdbIdentitySource
import dev.n2k02.adb.core.AdbOption
import dev.n2k02.adb.core.AdbPreferenceStore
import dev.n2k02.adb.core.LibAdbWire
import dev.n2k02.adb.core.LocalAdbDiscovery

/** Only one embedded libadb socket per process, even across concurrent workers. */
internal object SharedAdbHostAdapter {
    @Volatile private var active: AdbCore? = null

    fun core(context: Context): AdbCore =
        active ?: synchronized(this) {
            active ?: create(context.applicationContext).also {
                active = it
                AdbCoreRegistry.install(it)
            }
        }

    fun current(): AdbCore? = active

    suspend fun shutdown() {
        val prior = synchronized(this) { active.also { active = null } }
        prior?.let {
            AdbCoreRegistry.uninstall(it)
            it.quit()
        }
    }

    private fun create(context: Context): AdbCore {
        val app = context.applicationContext
        lateinit var adb: AdbCore
        val hooks = object : AdbHostHooks {
            override suspend fun onRestoreAfterRestartChanged(enabled: Boolean) {
                val permission = "android.permission.WRITE_SECURE_SETTINGS"
                val granted = app.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
                if (enabled == granted) return
                val pkg = app.packageName.takeIf {
                    DirectAdb.validPackage(it)
                } ?: return
                val operation = if (enabled) "grant" else "revoke"
                // The core schedules verification callbacks after releasing its mutex.
                runCatching { adb.execVerified("pm " + operation + " " + pkg + " " + permission, 10_000) }
            }
        }
        adb = AdbCore(
            LibAdbWire(app, AdbIdentitySource { ctx ->
                val old = AdbIdentity.getOrCreate(ctx)
                CoreIdentity(old.privateKey, old.certificate)
            }, "SDCleaner"),
            Preferences(app), LocalAdbDiscovery(app), hooks
        )
        return adb
    }

    private class Preferences(private val app: Context) : AdbPreferenceStore {
        private val times = app.getSharedPreferences("sdcleaner_direct_adb_options", Context.MODE_PRIVATE)
        override fun enabled(option: AdbOption) =
            DirectAdbOptions.get(app, DirectAdbOptions.Option.valueOf(option.name))
        override fun setEnabled(option: AdbOption, enabled: Boolean) {
            DirectAdbOptions.set(app, DirectAdbOptions.Option.valueOf(option.name), enabled)
        }
        override fun manuallyStopped() = DirectAdbOptions.stopRequested(app)
        override fun setManuallyStopped(stopped: Boolean) = DirectAdbOptions.markStopped(app, stopped)
        override fun lastTlsPort() = DirectAdbOptions.tlsPort(app)
        override fun setLastTlsPort(port: Int) { DirectAdbOptions.rememberAuthorized(app, port) }
        override fun lastVerifiedAt() = DirectAdbOptions.verifiedAt(app)
        override fun setLastVerifiedAt(timeMs: Long) {
            times.edit().putLong("verified_at", timeMs).apply()
        }
        override fun wasAuthorized() = DirectAdbOptions.wasAuthorized(app)
        override fun setAuthorized() {
            // Shell authorization does not imply that TCP/5555 is running.
            DirectAdbOptions.markAuthorized(app)
        }
    }
}
