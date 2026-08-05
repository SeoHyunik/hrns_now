package io.hrns_now.app

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.hrns_now.app.ui.WindowConstraints
import java.awt.Dimension

fun main() = application {
    val state = rememberWindowState(size = WindowConstraints.defaultSize)

    Window(
        onCloseRequest = ::exitApplication,
        state = state,
        icon = painterResource("hrns-now.ico"),
        title = "HRNS-NOW",
    ) {
        // 사용자가 창을 1280 x 800dp보다 작게 줄이려 해도 실제 OS 창 자체가 줄어들지 않게
        // AWT `Window.minimumSize`를 강제한다. `requiredSize`로 내부 Composable만 강제하는 방식은
        // 쓰지 않는다 — 그 방식은 native 창 크기 자체를 제약하지 못해 증상을 가릴 뿐이다.
        val density = LocalDensity.current
        LaunchedEffect(density) {
            val minimumPx = WindowConstraints.minimumSizePx(density)
            window.minimumSize = Dimension(minimumPx.width, minimumPx.height)
        }
        App()
    }
}
