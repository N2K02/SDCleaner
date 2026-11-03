package eu.darken.sdmse.cleaner

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.app.Activity
import android.content.Context
import android.os.Build
import dev.n2k02.updater.*
import kotlinx.coroutines.flow.MutableStateFlow

/** Process-owned controller; Activity recreation must not reset a download. */
object CleanerUpdater {
    private lateinit var app: Context
    private var instance by mutableStateOf<UpdaterController?>(null)
    private var canInstall: () -> Boolean = { false }
    val experimental = MutableStateFlow(false)
    val guard = UpdateInstallGuard { canInstall() }
    val controller: UpdaterController get() = requireNotNull(instance)
    private fun prefs() = app.getSharedPreferences("sdcleaner_updates", Context.MODE_PRIVATE)

    fun init(context: Context, installAllowed: () -> Boolean) {
        canInstall = installAllowed
        if (instance != null) return
        app = context.applicationContext
        experimental.value = prefs().getBoolean("experimental", false)
        instance = create()
    }

    @Suppress("DEPRECATION")
    private fun create(): UpdaterController {
        val info = app.packageManager.getPackageInfo(app.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        return UpdaterController(
            context = app,
            config = UpdateConfig(repository = "N2K02/SDCleaner", packageName = app.packageName,
                installedVersionCode = code, experimentalEnabled = experimental.value),
            publishedSource = PublishedUpdateSource("https://n2k02.github.io/sdcleaner/updates"),
            offerPreferences = prefs(),
        )
    }

    fun setExperimental(enabled: Boolean) {
        if (enabled == experimental.value) return
        controller.cancel()
        prefs().edit().putBoolean("experimental", enabled).apply()
        experimental.value = enabled
        instance = create()
        controller.check(manual = true)
    }
    fun resume(activity: Activity) { instance?.onHostResume(activity, guard) }
    fun cancel() { instance?.cancel() }
}
