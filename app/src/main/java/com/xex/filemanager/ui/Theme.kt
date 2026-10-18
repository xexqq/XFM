package com.xex.filemanager.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.xex.filemanager.data.AppSettings
import com.xex.filemanager.data.Palettes

/** Дополнительные цвета интерфейса (панель, разделители, плитки). */
data class XfmExtra(
    val toolbar: Color,
    val onToolbar: Color,
    val statusBar: Color,
    val separator: Color,
    val pathBg: Color,
    val pathBorder: Color,
    val pathText: Color,
    val pathTextSel: Color,
    val buttonText: Color,
    val section: Color,
    val tileBg: Color,
    val tileBorder: Color,
    val secondary: Color
)

private fun lightExtra(accent: Color) = XfmExtra(
    toolbar = Color(0xFF212121),
    onToolbar = Color.White,
    statusBar = Color.Black,
    separator = Color(0xFFD9D9D9),
    pathBg = Color.White,
    pathBorder = Color(0xFFCCCCCC),
    pathText = Color(0xFF777777),
    pathTextSel = Color(0xFF202020),
    buttonText = Color(0xFF444444),
    section = accent,
    tileBg = Color.White,
    tileBorder = Color(0xFFD2D2D2),
    secondary = Color(0x8A000000)
)

private fun darkExtra(accent: Color, amoled: Boolean) = XfmExtra(
    toolbar = if (amoled) Color(0xFF0A0A0A) else Color(0xFF212121),
    onToolbar = Color.White,
    statusBar = Color.Black,
    separator = Color(0xFF424242),
    pathBg = if (amoled) Color(0xFF050505) else Color(0xFF262626),
    pathBorder = Color(0xFF454545),
    pathText = Color(0xFFAAAAAA),
    pathTextSel = Color(0xFFEEEEEE),
    buttonText = Color(0xFFDDDDDD),
    section = accent,
    tileBg = if (amoled) Color(0xFF121212) else Color(0xFF3A3A3A),
    tileBorder = Color(0xFF555555),
    secondary = Color(0xB3FFFFFF)
)

val LocalXfm = staticCompositionLocalOf { lightExtra(Color(0xFF008578)) }

private fun onColor(c: Color): Color = if (c.luminance() > 0.5f) Color(0xFF101010) else Color.White

private fun buildDark(seed: Color, amoled: Boolean): ColorScheme {
    val primary = lerp(seed, Color.White, 0.18f)
    val container = lerp(seed, Color.Black, 0.55f)
    val bg = if (amoled) Color.Black else Color(0xFF2D2D2D)
    val surface = if (amoled) Color.Black else Color(0xFF212121)
    return darkColorScheme(
        primary = primary,
        onPrimary = onColor(primary),
        primaryContainer = container,
        onPrimaryContainer = lerp(seed, Color.White, 0.85f),
        secondary = lerp(seed, Color(0xFF9E9E9E), 0.55f),
        onSecondary = Color(0xFF101010),
        secondaryContainer = lerp(if (amoled) Color(0xFF1A1A1A) else Color(0xFF3A3A3A), seed, 0.22f),
        onSecondaryContainer = lerp(seed, Color.White, 0.88f),
        tertiary = lerp(seed, Color(0xFF9E9E9E), 0.4f),
        onTertiary = Color(0xFF101010),
        background = bg,
        onBackground = Color(0xFFEDEDED),
        surface = surface,
        onSurface = Color(0xFFEDEDED),
        surfaceVariant = if (amoled) Color(0xFF181818) else Color(0xFF3A3A3A),
        onSurfaceVariant = Color(0xFFB8B8B8),
        surfaceTint = surface,
        outline = Color(0xFF7A7A7A),
        outlineVariant = Color(0xFF454545),
        surfaceContainerLowest = if (amoled) Color.Black else Color(0xFF1A1A1A),
        surfaceContainerLow = if (amoled) Color(0xFF0A0A0A) else Color(0xFF262626),
        surfaceContainer = if (amoled) Color(0xFF121212) else Color(0xFF303030),
        surfaceContainerHigh = if (amoled) Color(0xFF1A1A1A) else Color(0xFF383838),
        surfaceContainerHighest = if (amoled) Color(0xFF222222) else Color(0xFF404040)
    )
}

private fun buildLight(seed: Color): ColorScheme {
    val primary = lerp(seed, Color.Black, 0.05f)
    return lightColorScheme(
        primary = primary,
        onPrimary = Color.White,
        primaryContainer = lerp(seed, Color.White, 0.8f),
        onPrimaryContainer = lerp(seed, Color.Black, 0.7f),
        secondary = lerp(seed, Color(0xFF616161), 0.6f),
        onSecondary = Color.White,
        secondaryContainer = lerp(seed, Color.White, 0.88f),
        onSecondaryContainer = lerp(seed, Color.Black, 0.7f),
        tertiary = lerp(seed, Color(0xFF616161), 0.5f),
        onTertiary = Color.White,
        background = Color(0xFFFAFAFA),
        onBackground = Color(0xFF000000),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF000000),
        surfaceVariant = Color(0xFFE8E8E8),
        onSurfaceVariant = Color(0xFF737373),
        surfaceTint = Color(0xFFFFFFFF),
        outline = Color(0xFF8A8F93),
        outlineVariant = Color(0xFFD9D9D9),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF7F7F7),
        surfaceContainer = Color(0xFFF2F2F2),
        surfaceContainerHigh = Color(0xFFECECEC),
        surfaceContainerHighest = Color(0xFFE6E6E6)
    )
}

@Composable
fun XfmTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.darkMode) {
        1 -> false
        2 -> true
        else -> systemDark
    }
    val ctx = LocalContext.current
    val palette = Palettes.firstOrNull { it.id == settings.themeId } ?: Palettes[0]
    val scheme = if (settings.dynamic && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else if (dark) {
        buildDark(palette.seed, settings.amoled)
    } else {
        buildLight(palette.seed)
    }
    val extra = if (dark) darkExtra(scheme.primary, settings.amoled) else lightExtra(scheme.primary)
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            val c = WindowCompat.getInsetsController(window, view)
            c.isAppearanceLightStatusBars = false
            c.isAppearanceLightNavigationBars = !dark
        }
    }
    CompositionLocalProvider(LocalXfm provides extra) {
        MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
    }
}
