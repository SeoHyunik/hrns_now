package io.hrns_now.app.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.sp

/**
 * 한글·영문 모두 자연스럽게 보이는 무료 폰트 묶음.
 *
 * - 한글: Pretendard (오픈 라이선스 / 토스·카카오 등에서 표준으로 쓰는 한글 산세리프)
 * - 영문: Pretendard 가 라틴까지 매끄럽게 커버하므로 단일 패밀리로 처리
 *
 * 폰트 파일 위치
 *   composeApp/src/jvmMain/resources/font/
 *     Pretendard-Regular.otf
 *     Pretendard-Medium.otf
 *     Pretendard-SemiBold.otf
 *     Pretendard-Bold.otf
 *
 * 다운로드: https://github.com/orioncactus/pretendard/releases (SIL OFL)
 *
 * 파일이 없거나 로드 실패 시 자동으로 시스템 기본 폰트로 폴백한다.
 */
val PretendardFamily: FontFamily = runCatching {
    FontFamily(
        Font("font/Pretendard-Regular.otf", FontWeight.Normal, FontStyle.Normal),
        Font("font/Pretendard-Medium.otf", FontWeight.Medium, FontStyle.Normal),
        Font("font/Pretendard-SemiBold.otf", FontWeight.SemiBold, FontStyle.Normal),
        Font("font/Pretendard-Bold.otf", FontWeight.Bold, FontStyle.Normal),
    )
}.getOrDefault(FontFamily.Default)

/**
 * MaterialTheme 에 주입할 Typography.
 */
fun hrnsTypography(): Typography {
    val base = TextStyle(fontFamily = PretendardFamily)
    return Typography(
        // 새 Phase 8 §7: 큰 제목의 negative letter spacing을 완만하게 낮췄다 — 한글 음절 블록은
        // Latin만큼 negative tracking을 견디지 못해 겹침처럼 보일 위험이 있었다.
        displayLarge = base.copy(fontSize = 48.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp, lineHeight = 56.sp),
        displayMedium = base.copy(fontSize = 40.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp, lineHeight = 48.sp),
        displaySmall = base.copy(fontSize = 32.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp, lineHeight = 40.sp),

        headlineLarge = base.copy(fontSize = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp, lineHeight = 38.sp),
        headlineMedium = base.copy(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp, lineHeight = 32.sp),
        headlineSmall = base.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp, lineHeight = 28.sp),

        titleLarge = base.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp, lineHeight = 26.sp),
        titleMedium = base.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp, lineHeight = 22.sp),
        titleSmall = base.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp, lineHeight = 20.sp),

        bodyLarge = base.copy(fontSize = 15.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp, lineHeight = 23.sp),
        bodyMedium = base.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp, lineHeight = 20.sp),
        bodySmall = base.copy(fontSize = 12.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.1.sp, lineHeight = 18.sp),

        labelLarge = base.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp, lineHeight = 18.sp),
        labelMedium = base.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp, lineHeight = 16.sp),
        labelSmall = base.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp, lineHeight = 14.sp),
    )
}
