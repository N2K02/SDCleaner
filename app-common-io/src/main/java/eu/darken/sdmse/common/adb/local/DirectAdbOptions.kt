package eu.darken.sdmse.common.adb.local

import android.content.Context
import android.provider.Settings

/**
 * The same five ADB recovery choices as VREC, LocalAdb, MCP Android Logger
 * and SEQ. Pairing codes and private keys are never written here.
 */
object DirectAdbOptions {
    enum class Option { AUTO_PAIR, WATCHDOG, START_ON_BOOT, RESTORE_AFTER_RESTART, TCP_5555 }
    private const val NAME = "sdcleaner_direct_adb_options"
    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    fun get(context: Context, option: Option): Boolean =
        prefs(context).getBoolean(option.name.lowercase(), option != Option.RESTORE_AFTER_RESTART)
    fun set(context: Context, option: Option, enabled: Boolean) {
        prefs(context).edit().putBoolean(option.name.lowercase(), enabled).apply()
    }
    fun wasAuthorized(context: Context): Boolean =
        prefs(context).getBoolean("previously_authorized", false)
    fun wasPaired(context: Context): Boolean =
        prefs(context).getBoolean("previously_paired", false)
    fun rememberPaired(context: Context) {
        prefs(context).edit().putBoolean("previously_paired", true).apply()
    }
    fun rememberAuthorized(context: Context, port: Int) {
        if (port !in 1..65535) return
        val boot = boot(context)
        prefs(context).edit()
            .putBoolean("previously_authorized", true)
            .putLong("verified_at", System.currentTimeMillis())
            .putInt("verified_boot", boot)
            .putString("transport", if (port == 5555) "Local TCP/5555" else "Wireless TLS")
            .apply {
                if (port != 5555 && boot >= 0) {
                    putInt("tls_port", port)
                    putInt("tls_boot", boot)
                }
            }.apply()
    }
    fun verifiedAt(context: Context): Long = prefs(context).getLong("verified_at", 0L)
    fun transport(context: Context): String = prefs(context).getString("transport", "None") ?: "None"
    fun tlsPort(context: Context): Int? {
        val boot = boot(context)
        return prefs(context).getInt("tls_port", -1).takeIf {
            it in 1..65535 && boot >= 0 && prefs(context).getInt("tls_boot", -2) == boot
        }
    }
    fun stopRequested(context: Context): Boolean = prefs(context).getBoolean("manual_stop", false)
    fun markStopped(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("manual_stop", value).commit()
    }
    fun lastVerifiedBoot(context: Context): Int =
        prefs(context).getInt("verified_boot", -1)
    fun currentBoot(context: Context): Int = boot(context)
    private fun boot(context: Context) = runCatching {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    }.getOrDefault(-1)
}
