package io.hrns_now.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import io.hrns_now.app.ui.HrnsDarkColorScheme
import io.hrns_now.app.ui.HrnsShell
import io.hrns_now.core.AppRoute

@Composable
fun App() {
    var selectedRoute by remember { mutableStateOf(AppRoute.Setup) }

    MaterialTheme(colorScheme = HrnsDarkColorScheme) {
        HrnsShell(
            selectedRoute = selectedRoute,
            onRouteSelected = { selectedRoute = it },
        )
    }
}
