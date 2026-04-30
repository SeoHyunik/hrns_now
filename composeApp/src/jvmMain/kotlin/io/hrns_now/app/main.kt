package io.hrns_now.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "HRNS-NOW",
    ) {
        App()
    }
}
