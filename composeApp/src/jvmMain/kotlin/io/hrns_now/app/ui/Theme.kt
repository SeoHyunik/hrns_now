package io.hrns_now.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 화면 모드.
 */
enum class HrnsThemeMode {
    Dark,
    Light,
}

/**
 * 디자인 토큰.
 *
 * 원칙
 * - 테두리(stroke) 보다 면(fill) 단계로 깊이를 만든다.
 * - 강조색은 인디고 1색만 쓰고, 의미 색은 dot 표시에만 쓴다.
 * - 텍스트는 3단계 (primary / secondary / tertiary).
 *
 * 기존 필드 이름은 호환을 위해 모두 살려둔다.
 */
data class HrnsColors(
    // 배경 / 표면
    val appBackground: Color,
    val panelBackground: Color,
    val cardBackground: Color,
    val surfaceElevated: Color,
    val surfaceMuted: Color,
    val surfaceHover: Color,

    // 보더 (거의 안 씀, hairline 용)
    val border: Color,
    val borderStrong: Color,
    val borderSubtle: Color,

    // 텍스트
    val primaryText: Color,
    val secondaryText: Color,
    val tertiaryText: Color,

    /**
     * 상단 리본 전용 muted 텍스트 토큰이다(Phase 9 QA02). 전역 [tertiaryText]와 분리해 두어,
     * 이 값을 바꿔도 다른 화면의 "덜 중요한 텍스트" 의미를 훼손하지 않는다.
     */
    val ribbonMutedText: Color,

    // 강조
    val accent: Color,
    val accentSoft: Color,
    val accentMuted: Color,
    val onAccent: Color,

    // Chelsea Blue (테두리 / 그림자 톤용)
    val chelseaBlue: Color,
    val chelseaBlueSoft: Color,
    val chelseaGlow: Color,

    // 브랜드 마크용 그라데이션 색
    val brandGradientStart: Color,
    val brandGradientMid: Color,
    val brandGradientEnd: Color,

    // 의미 토큰
    val success: Color,
    val successSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val warningBackground: Color, // alias = warningSoft (호환)
    val warningBorder: Color,
    val danger: Color,
    val dangerSoft: Color,
    val info: Color,
    val infoSoft: Color,
)

val LocalHrnsColors = staticCompositionLocalOf { hrnsColors(HrnsThemeMode.Dark) }

fun hrnsColors(mode: HrnsThemeMode): HrnsColors =
    when (mode) {
        HrnsThemeMode.Dark -> HrnsColors(
            appBackground = Color(0xFF0A0A0B),
            panelBackground = Color(0xFF0A0A0B),
            cardBackground = Color(0xFF131316),
            surfaceElevated = Color(0xFF1B1B20),
            surfaceMuted = Color(0xFF101013),
            surfaceHover = Color(0xFF1F1F25),

            border = Color(0xFF26262C),
            borderStrong = Color(0xFF3A3A42),
            borderSubtle = Color(0xFF1C1C21),

            primaryText = Color(0xFFF5F5F7),
            secondaryText = Color(0xFFA1A1AA),
            tertiaryText = Color(0xFF6B6B74),

            // Phase 9 QA02: 이전 값(0xFFFFE9A6)은 채도가 너무 높았다. 흰색에 가까운 낮은 채도의
            // warm off-white로 낮췄다.
            ribbonMutedText = Color(0xFFD9D7C7),

            accent = Color(0xFF4A7FC9),
            accentSoft = Color(0xFF0F1B2E),
            accentMuted = Color(0xFF1A2D4D),
            onAccent = Color(0xFFFFFFFF),

            chelseaBlue = Color(0xFF1E5BB8),
            chelseaBlueSoft = Color(0xFF13243F),
            chelseaGlow = Color(0x33034694),

            brandGradientStart = Color(0xFFFFA751),
            brandGradientMid = Color(0xFFE6336B),
            brandGradientEnd = Color(0xFF7A4DFF),

            success = Color(0xFF4ADE80),
            successSoft = Color(0xFF0E2818),
            warning = Color(0xFFFBBF24),
            warningSoft = Color(0xFF2A1F09),
            warningBackground = Color(0xFF2A1F09),
            warningBorder = Color(0xFF6B5418),
            danger = Color(0xFFF87171),
            dangerSoft = Color(0xFF2A1416),
            info = Color(0xFF60A5FA),
            infoSoft = Color(0xFF0E1F33),
        )

        HrnsThemeMode.Light -> HrnsColors(
            appBackground = Color(0xFFFAFAFA),
            panelBackground = Color(0xFFFAFAFA),
            cardBackground = Color(0xFFFFFFFF),
            surfaceElevated = Color(0xFFFFFFFF),
            surfaceMuted = Color(0xFFF4F4F5),
            surfaceHover = Color(0xFFEEEEF0),

            border = Color(0xFFE4E4E7),
            borderStrong = Color(0xFFD4D4D8),
            borderSubtle = Color(0xFFEEEEF0),

            primaryText = Color(0xFF09090B),
            secondaryText = Color(0xFF52525B),
            tertiaryText = Color(0xFF9CA3AF),

            // Phase 9 QA02: light 배경에서 대비를 유지하는 muted warm dark 색.
            ribbonMutedText = Color(0xFF6B5A3C),

            accent = Color(0xFF034694),
            accentSoft = Color(0xFFE6EEF8),
            accentMuted = Color(0xFFCCDBED),
            onAccent = Color(0xFFFFFFFF),

            chelseaBlue = Color(0xFF034694),
            chelseaBlueSoft = Color(0xFFE6EEF8),
            chelseaGlow = Color(0x29034694),

            brandGradientStart = Color(0xFFF59E0B),
            brandGradientMid = Color(0xFFEC4899),
            brandGradientEnd = Color(0xFF6366F1),

            success = Color(0xFF16A34A),
            successSoft = Color(0xFFDCFCE7),
            warning = Color(0xFFB45309),
            warningSoft = Color(0xFFFEF3C7),
            warningBackground = Color(0xFFFEF3C7),
            warningBorder = Color(0xFFFCD34D),
            danger = Color(0xFFDC2626),
            dangerSoft = Color(0xFFFEE2E2),
            info = Color(0xFF2563EB),
            infoSoft = Color(0xFFDBEAFE),
        )
    }

fun hrnsMaterialColorScheme(mode: HrnsThemeMode): ColorScheme {
    val c = hrnsColors(mode)

    return when (mode) {
        HrnsThemeMode.Dark -> darkColorScheme(
            background = c.appBackground,
            surface = c.cardBackground,
            surfaceVariant = c.surfaceElevated,
            primary = c.accent,
            primaryContainer = c.accentSoft,
            secondary = c.success,
            secondaryContainer = c.successSoft,
            tertiary = c.warning,
            tertiaryContainer = c.warningSoft,
            error = c.danger,
            errorContainer = c.dangerSoft,
            onBackground = c.primaryText,
            onSurface = c.primaryText,
            onSurfaceVariant = c.secondaryText,
            onPrimary = c.onAccent,
            onPrimaryContainer = c.primaryText,
            outline = c.borderStrong,
            outlineVariant = c.border,
            scrim = Color(0xFF000000),
        )

        HrnsThemeMode.Light -> lightColorScheme(
            background = c.appBackground,
            surface = c.cardBackground,
            surfaceVariant = c.surfaceMuted,
            primary = c.accent,
            primaryContainer = c.accentSoft,
            secondary = c.success,
            secondaryContainer = c.successSoft,
            tertiary = c.warning,
            tertiaryContainer = c.warningSoft,
            error = c.danger,
            errorContainer = c.dangerSoft,
            onBackground = c.primaryText,
            onSurface = c.primaryText,
            onSurfaceVariant = c.secondaryText,
            onPrimary = c.onAccent,
            onPrimaryContainer = Color(0xFF1E1B4B),
            outline = c.borderStrong,
            outlineVariant = c.border,
            scrim = Color(0xFF000000),
        )
    }
}
