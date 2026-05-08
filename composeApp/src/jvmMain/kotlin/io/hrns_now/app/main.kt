package io.hrns_now.app

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    val state: WindowState = rememberWindowState(
        size = DpSize(1440.dp, 900.dp),
    )

    Window(
        onCloseRequest = ::exitApplication,
        state = state,
        icon = painterResource("icon.png"),
        title = "HRNS-NOW",
    ) {
        App()
    }
}
