package io.hrns_now.app.ui

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * 데스크톱 창의 플랫폼 크기 제약이다(Phase 9 QA01,
 * `doc/claude_prompts/phase9-desktop-layout-and-onboarding.md`). 기본 창은 [defaultSize]를
 * 유지하고, 사용자가 그보다 작게 줄이려 해도 실제 OS 창 자체가 [minimumSize] 밑으로 줄어들지
 * 않도록 `main.kt`가 실제 AWT `Window.minimumSize`에 [minimumSizePx]를 적용한다. `core`는 이
 * 값을 알지 못한다 — 창 크기 제약은 desktop UI 계층의 책임이다.
 */
object WindowConstraints {
    val defaultSize = DpSize(1440.dp, 900.dp)
    val minimumSize = DpSize(1280.dp, 800.dp)

    /** [minimumSize]를 현재 화면 [density] 기준 실제 픽셀로 변환한다 — AWT `Dimension`은 dp를 모른다. */
    fun minimumSizePx(density: Density): IntSize = with(density) {
        IntSize(minimumSize.width.roundToPx(), minimumSize.height.roundToPx())
    }
}
