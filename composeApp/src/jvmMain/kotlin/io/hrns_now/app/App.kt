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
import io.hrns_now.core.artifact.WorkspaceArtifactSummary
import io.hrns_now.core.config.WorkspaceConfig
import io.hrns_now.core.config.WorkspaceProbeSummary
import io.hrns_now.core.config.WorkspaceReadiness
import io.hrns_now.core.projection.RunStatusProjection
import io.hrns_now.core.projection.SetupProjection
import io.hrns_now.core.projection.ShellProjection
import io.hrns_now.core.projection.TodayStatusProjection
import io.hrns_now.core.projection.TodayWorkProjection
import io.hrns_now.infra.EnvironmentWorkspaceConfigProvider
import io.hrns_now.infra.MockProjectionProvider
import io.hrns_now.infra.WorkspaceArtifactProbe
import io.hrns_now.infra.WorkspacePathProbe

@Composable
fun App() {
    var selectedRoute by remember { mutableStateOf(AppRoute.Setup) }
    var themeMode by remember { mutableStateOf(HrnsThemeMode.Dark) }
    val projections = remember {
        val projectionProvider = MockProjectionProvider()
        val workspaceProvider = EnvironmentWorkspaceConfigProvider()
        val workspaceConfig = workspaceProvider.config()
        val pathProbe = WorkspacePathProbe()
        val probeSummary = pathProbe.probe(workspaceConfig)
        val artifactSummary = WorkspaceArtifactProbe().probe(workspaceConfig)
        AppProjections(
            shell = projectionProvider.shell(),
            setup = projectionProvider.setup(),
            todayStatus = projectionProvider.todayStatus(),
            todayWork = projectionProvider.todayWork(),
            runStatus = projectionProvider.runStatus(),
            workspaceConfig = workspaceConfig,
            workspaceProbeSummary = probeSummary,
            workspaceArtifactSummary = artifactSummary,
            workspaceReadiness = pathProbe.readiness(workspaceConfig, probeSummary),
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
    val workspaceConfig: WorkspaceConfig,
    val workspaceProbeSummary: WorkspaceProbeSummary,
    val workspaceArtifactSummary: WorkspaceArtifactSummary,
    val workspaceReadiness: WorkspaceReadiness,
)
