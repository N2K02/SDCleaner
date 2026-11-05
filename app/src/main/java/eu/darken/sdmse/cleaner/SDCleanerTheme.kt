package eu.darken.sdmse.cleaner

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * SDCleaner's violet color language on Home, Settings, Scheduler and nested pages.
 * Keep Material typography/shapes and the user's light/dark appearance choice.
 */
@Composable
internal fun SDCleanerTheme(content: @Composable () -> Unit) {
    val base = MaterialTheme.colorScheme
    val dark = base.background.luminance() < 0.5f
    val scheme = if (dark) {
        base.copy(
            primary = Color(0xFFBF91FF),
            onPrimary = Color(0xFF29123F),
            primaryContainer = Color(0xFF443063),
            onPrimaryContainer = Color(0xFFF1DFFF),
            secondary = Color(0xFFD5B6FF),
            onSecondary = Color(0xFF2F2048),
            secondaryContainer = Color(0xFF332841),
            onSecondaryContainer = Color(0xFFF1E7FA),
            tertiary = Color(0xFFDEB8FF),
            tertiaryContainer = Color(0xFF3B2A58),
            onTertiaryContainer = Color(0xFFF0E2FF),
            background = Color(0xFF100D17),
            surface = Color(0xFF16111F),
            surfaceContainerLow = Color(0xFF20192B),
            surfaceContainer = Color(0xFF281E34),
            surfaceContainerHigh = Color(0xFF342A42),
        )
    } else {
        base.copy(
            primary = Color(0xFF7545B6),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFEADBFF),
            onPrimaryContainer = Color(0xFF2E174A),
            secondary = Color(0xFF765A9C),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFF1E6FB),
            onSecondaryContainer = Color(0xFF34234D),
            tertiary = Color(0xFF7544A9),
            tertiaryContainer = Color(0xFFEDE0FF),
            onTertiaryContainer = Color(0xFF2E174A),
            background = Color(0xFFFCF9FF),
            surface = Color(0xFFFCF9FF),
            surfaceContainerLow = Color(0xFFF5EEFC),
            surfaceContainer = Color(0xFFF1E9F8),
            surfaceContainerHigh = Color(0xFFECE2F4),
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
