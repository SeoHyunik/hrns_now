package io.hrns_now.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.hrns_now.app.ui.HrnsThemeMode
import io.hrns_now.app.ui.HrnsShell
import io.hrns_now.app.ui.LocalHrnsColors
import io.hrns_now.app.ui.hrnsColors
import io.hrns_now.app.ui.hrnsMaterialColorScheme
import io.hrns_now.core.AppRoute
import io.hrns_now.core.projection.RunStatusProjection
import io.hrns_now.core.projection.SetupProjection
import io.hrns_now.core.projection.ShellProjection
import io.hrns_now.core.projection.TodayStatusProjection
import io.hrns_now.core.projection.TodayWorkProjection
import io.hrns_now.infra.MockProjectionProvider

@Composable
fun App() {
    var selectedRoute by remember { mutableStateOf(AppRoute.Setup) }
    var themeMode by remember { mutableStateOf(HrnsThemeMode.Dark) }
    val projections = remember {
        val provider = MockProjectionProvider()
        AppProjections(
            shell = provider.shell(),
            setup = provider.setup(),
            todayStatus = provider.todayStatus(),
            todayWork = provider.todayWork(),
            runStatus = provider.runStatus(),
        )
    }

    CompositionLocalProvider(LocalHrnsColors provides hrnsColors(themeMode)) {
        MaterialTheme(colorScheme = hrnsMaterialColorScheme(themeMode)) {
            HrnsShell(
                selectedRoute = selectedRoute,
                onRouteSelected = { selectedRoute = it },
                projections = projections,
                themeMode = themeMode,
                onThemeToggle = {
                    themeMode = when (themeMode) {
                        HrnsThemeMode.Dark -> HrnsThemeMode.Light
                        HrnsThemeMode.Light -> HrnsThemeMode.Dark
                    }
                },
            )
        }
    }
}

data class AppProjections(
    val shell: ShellProjection,
    val setup: SetupProjection,
    val todayStatus: TodayStatusProjection,
    val todayWork: TodayWorkProjection,
    val runStatus: RunStatusProjection,
)
