package io.hrns_now.app.ui

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * AWT `Window.minimumSize` 자체는 실제 native 창 없이 단위 테스트로 검증할 수 없다 — 이 테스트는
 * 그 부분을 mock으로 성공한 것처럼 꾸미지 않고, 실제로 단위 테스트 가능한 부분(dp 상수와 dp→px
 * 변환)만 검증한다. native 창 적용 자체는 수동 GUI QA로 확인한다(Phase 9 QA01).
 */
class WindowConstraintsTest {
    @Test
    fun `기본 창 크기는 1440x900dp다`() {
        assertEquals(1440.dp, WindowConstraints.defaultSize.width)
        assertEquals(900.dp, WindowConstraints.defaultSize.height)
    }

    @Test
    fun `최소 창 크기는 1280x800dp다`() {
        assertEquals(1280.dp, WindowConstraints.minimumSize.width)
        assertEquals(800.dp, WindowConstraints.minimumSize.height)
    }

    @Test
    fun `밀도 1이면 최소 크기 px는 dp 값과 같다`() {
        val px = WindowConstraints.minimumSizePx(Density(density = 1f))
        assertEquals(1280, px.width)
        assertEquals(800, px.height)
    }

    @Test
    fun `밀도가 커지면 최소 크기 px도 비례해 커진다`() {
        val px = WindowConstraints.minimumSizePx(Density(density = 1.5f))
        assertEquals(1920, px.width)
        assertEquals(1200, px.height)
    }
}
