package io.hrns_now.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class HrnsThemeMode {
    Dark,
    Light
}

data class HrnsColors(
    val appBackground: Color,
    val panelBackground: Color,
    val cardBackground: Color,
    val border: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val accent: Color,
    val accentSoft: Color,
    val warningBackground: Color,
    val warningBorder: Color,
)

val LocalHrnsColors = staticCompositionLocalOf { hrnsColors(HrnsThemeMode.Dark) }

fun hrnsColors(mode: HrnsThemeMode): HrnsColors =
    when (mode) {
        HrnsThemeMode.Dark -> HrnsColors(
            appBackground = Color(0xFF0B1016),
            panelBackground = Color(0xFF121923),
            cardBackground = Color(0xFF151E29),
            border = Color(0xFF2A3946),
            primaryText = Color(0xFFE6EEF6),
            secondaryText = Color(0xFFB9C5D1),
            accent = Color(0xFF8AC7FF),
            accentSoft = Color(0xFF12324A),
            warningBackground = Color(0xFF2D2114),
            warningBorder = Color(0xFF8B6530),
        )

        HrnsThemeMode.Light -> HrnsColors(
            appBackground = Color(0xFFF4F7FA),
            panelBackground = Color(0xFFFFFFFF),
            cardBackground = Color(0xFFFFFFFF),
            border = Color(0xFFD7DEE7),
            primaryText = Color(0xFF17202A),
            secondaryText = Color(0xFF5F6F82),
            accent = Color(0xFF0E6BA8),
            accentSoft = Color(0xFFE6F3FF),
            warningBackground = Color(0xFFFFF6E8),
            warningBorder = Color(0xFFE2A84B),
        )
    }

fun hrnsMaterialColorScheme(mode: HrnsThemeMode): ColorScheme {
    val colors = hrnsColors(mode)

    return when (mode) {
        HrnsThemeMode.Dark -> darkColorScheme(
            background = colors.appBackground,
            surface = colors.cardBackground,
            surfaceVariant = colors.panelBackground,
            primary = colors.accent,
            primaryContainer = colors.accentSoft,
            secondary = Color(0xFF82E6C7),
            secondaryContainer = Color(0xFF123D34),
            tertiary = Color(0xFFF5C26B),
            tertiaryContainer = colors.warningBackground,
            error = Color(0xFFFF8A8A),
            errorContainer = Color(0xFF4A1E20),
            onBackground = colors.primaryText,
            onSurface = colors.primaryText,
            onSurfaceVariant = colors.secondaryText,
            onPrimary = Color(0xFF062235),
            onPrimaryContainer = colors.primaryText,
            outline = Color(0xFF405160),
            outlineVariant = colors.border,
            scrim = Color(0xFF000000),
        )

        HrnsThemeMode.Light -> lightColorScheme(
            background = colors.appBackground,
            surface = colors.cardBackground,
            surfaceVariant = colors.panelBackground,
            primary = colors.accent,
            primaryContainer = colors.accentSoft,
            secondary = Color(0xFF0E7C66),
            secondaryContainer = Color(0xFFE3F6F0),
            tertiary = Color(0xFF9A6516),
            tertiaryContainer = colors.warningBackground,
            error = Color(0xFFBA1A1A),
            errorContainer = Color(0xFFFFDAD6),
            onBackground = colors.primaryText,
            onSurface = colors.primaryText,
            onSurfaceVariant = colors.secondaryText,
            onPrimary = Color(0xFFFFFFFF),
            onPrimaryContainer = Color(0xFF123247),
            outline = Color(0xFF7A8794),
            outlineVariant = colors.border,
            scrim = Color(0xFF000000),
        )
    }
}
