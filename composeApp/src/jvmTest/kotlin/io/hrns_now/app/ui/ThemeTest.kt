package io.hrns_now.app.ui

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * 상단 리본 전용 muted 텍스트 토큰이 실제로 dark/light 각각 낮은 채도의
 * 값으로 정의돼 있는지, 그리고 이 토큰이 전역 `tertiaryText`와 분리된 별도 값인지 확인한다.
 * `Shell.kt`가 이 토큰과 `chelseaBlue`를 실제로 소비하는지는 native GUI 수동 QA로 확인한다 —
 * 이 프로젝트는 Compose UI 렌더 트리를 단위 테스트하는 하네스를 쓰지 않는다.
 */
class ThemeTest {
    @Test
    fun `dark ribbonMutedText는 낮은 채도의 warm off-white다`() {
        val colors = hrnsColors(HrnsThemeMode.Dark)
        assertEquals(Color(0xFFD9D7C7), colors.ribbonMutedText)
    }

    @Test
    fun `light ribbonMutedText는 대비를 유지하는 muted warm dark다`() {
        val colors = hrnsColors(HrnsThemeMode.Light)
        assertEquals(Color(0xFF6B5A3C), colors.ribbonMutedText)
    }

    @Test
    fun `ribbonMutedText는 전역 tertiaryText와 분리된 별도 토큰이다`() {
        val dark = hrnsColors(HrnsThemeMode.Dark)
        val light = hrnsColors(HrnsThemeMode.Light)
        assertNotEquals(dark.tertiaryText, dark.ribbonMutedText)
        assertNotEquals(light.tertiaryText, light.ribbonMutedText)
    }

    @Test
    fun `chelseaBlue 토큰은 dark light 각각 하나의 값으로 고정돼 있다`() {
        val dark = hrnsColors(HrnsThemeMode.Dark)
        val light = hrnsColors(HrnsThemeMode.Light)
        assertEquals(Color(0xFF1E5BB8), dark.chelseaBlue)
        assertEquals(Color(0xFF034694), light.chelseaBlue)
    }
}
