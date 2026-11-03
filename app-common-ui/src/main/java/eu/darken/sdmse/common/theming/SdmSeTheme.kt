package eu.darken.sdmse.common.theming

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import eu.darken.sdmse.common.compose.FocusHighlightOverlay
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun SdmSeTheme(state: ThemeState = ThemeState(), content: @Composable () -> Unit) {
    val darkTheme = when (state.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !darkTheme
        insetsController.isAppearanceLightNavigationBars = !darkTheme
    }

    dev.n2k02.theme.N2K02Theme(darkTheme = darkTheme, dynamicColor = true) {
        // Theme-level so every screen (activities, fragment ComposeViews, dialogs) gets the
        // D-pad/keyboard focus ring without per-screen wiring. An Indication override wouldn't
        // work here: M3 components pass ripple() explicitly and ignore LocalIndication.
        FocusHighlightOverlay(content = content)
    }
}
