package eu.darken.sdmse.cleaner

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Calm, neutral Material 3 surfaces like the other n2k02 apps.
 * Slate-blue is used sparingly for focus/actions; dark and light appearances are supported.
 */
@Composable
internal fun SDCleanerTheme(content: @Composable () -> Unit) {
    val base = MaterialTheme.colorScheme
    val dark = base.background.luminance() < 0.5f
    val scheme = if (dark) {
        base.copy(
            primary = Color(0xFFA8C6E8),
            onPrimary = Color(0xFF17283A),
            primaryContainer = Color(0xFF29394A),
            onPrimaryContainer = Color(0xFFE1EEFA),
            secondary = Color(0xFFB9C6D5),
            onSecondary = Color(0xFF24303D),
            secondaryContainer = Color(0xFF2B3644),
            onSecondaryContainer = Color(0xFFE1E8F0),
            tertiary = Color(0xFFB6C6D7),
            tertiaryContainer = Color(0xFF33404D),
            onTertiaryContainer = Color(0xFFE8F1F8),
            background = Color(0xFF101419),
            onBackground = Color(0xFFE7ECF2),
            surface = Color(0xFF14191F),
            onSurface = Color(0xFFE7ECF2),
            onSurfaceVariant = Color(0xFFB2BDC9),
            surfaceDim = Color(0xFF101419),
            surfaceBright = Color(0xFF323C47),
            surfaceContainerLowest = Color(0xFF101419),
            surfaceContainerLow = Color(0xFF1B222A),
            surfaceContainer = Color(0xFF222B35),
            surfaceContainerHigh = Color(0xFF2C3641),
            surfaceContainerHighest = Color(0xFF35414E),
            outline = Color(0xFF788697),
            outlineVariant = Color(0xFF485462),
            surfaceTint = Color(0xFFA8C6E8),
        )
    } else {
        base.copy(
            primary = Color(0xFF476C92),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFD9E7F5),
            onPrimaryContainer = Color(0xFF1B324B),
            secondary = Color(0xFF5E7185),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFE4EBF2),
            onSecondaryContainer = Color(0xFF26394D),
            tertiary = Color(0xFF546C86),
            tertiaryContainer = Color(0xFFDCE9F7),
            onTertiaryContainer = Color(0xFF1E354E),
            background = Color(0xFFF7F9FC),
            onBackground = Color(0xFF1B252E),
            surface = Color(0xFFF9FBFD),
            onSurface = Color(0xFF1B252E),
            onSurfaceVariant = Color(0xFF505D69),
            surfaceDim = Color(0xFFDBE1E8),
            surfaceBright = Color.White,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color(0xFFF0F3F7),
            surfaceContainer = Color(0xFFE8EDF2),
            surfaceContainerHigh = Color(0xFFDFE6ED),
            surfaceContainerHighest = Color(0xFFD5DEE7),
            outline = Color(0xFF707E8B),
            outlineVariant = Color(0xFFC3CED8),
            surfaceTint = Color(0xFF476C92),
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
