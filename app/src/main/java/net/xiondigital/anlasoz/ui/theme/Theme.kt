package net.xiondigital.anlasoz.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val AnlaSozColorScheme = darkColorScheme(
    primary = Xion.Blue,
    onPrimary = Color.White,
    primaryContainer = Xion.BlueSoft,
    onPrimaryContainer = Xion.BlueHi,
    secondary = Xion.BlueHi,
    onSecondary = Xion.Bg,
    secondaryContainer = Xion.BlueSofter,
    onSecondaryContainer = Xion.BlueHi,
    tertiary = Xion.Ok,
    onTertiary = Xion.Bg,
    background = Xion.Bg,
    onBackground = Xion.Ink,
    surface = Xion.Panel,
    onSurface = Xion.Ink,
    surfaceVariant = Xion.PanelHi,
    onSurfaceVariant = Xion.Muted,
    surfaceContainer = Xion.Panel,
    surfaceContainerHigh = Xion.PanelHi,
    surfaceContainerHighest = Xion.PanelHi,
    surfaceContainerLow = Xion.BgRaised,
    surfaceContainerLowest = Xion.Bg,
    outline = Xion.LineStrong,
    outlineVariant = Xion.Line,
    error = Xion.Danger,
    onError = Xion.Bg,
    errorContainer = Color(0x33FF6B6B),
    onErrorContainer = Xion.Danger,
    inverseSurface = Xion.Ink,
    inverseOnSurface = Xion.Bg,
    scrim = Color.Black,
)

val AnlaSozShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(11.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun AnlaSozTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    // The brand ships one dark editorial theme; a light scheme is not part of it.
    @Suppress("UNUSED_VARIABLE")
    val systemDark = isSystemInDarkTheme()

    MaterialTheme(
        colorScheme = AnlaSozColorScheme,
        typography = AnlaSozTypography,
        shapes = AnlaSozShapes,
        content = content,
    )
}
