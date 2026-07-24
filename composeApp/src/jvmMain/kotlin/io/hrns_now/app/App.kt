package io.hrns_now.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.setValue
import io.hrns_now.app.demo.MockProjectionProvider
import io.hrns_now.app.demo.MockWorkspaceConfigProvider
import io.hrns_now.app.presentation.model.HrnsUiEvent
import io.hrns_now.app.presentation.model.HrnsUiState
import io.hrns_now.app.presentation.viewmodel.AppViewModel
import io.hrns_now.app.ui.HrnsShell
import io.hrns_now.app.ui.HrnsThemeMode
import io.hrns_now.app.ui.LocalHrnsColors
import io.hrns_now.app.ui.hrnsColors
import io.hrns_now.app.ui.hrnsMaterialColorScheme
import io.hrns_now.app.ui.hrnsTypography
import io.hrns_now.core.AppRoute
import io.hrns_now.core.config.PathProbeKind
import io.hrns_now.core.config.PathProbeResult
import io.hrns_now.core.config.PathProbeState
import io.hrns_now.core.config.WorkspaceProbeSummary
import io.hrns_now.core.domain.model.ArtifactKind
import io.hrns_now.core.domain.model.ArtifactProbeResult
import io.hrns_now.core.domain.model.ArtifactProbeState
import io.hrns_now.core.domain.model.ArtifactRequirement
import io.hrns_now.core.domain.model.UiAction
import io.hrns_now.core.domain.model.WorkspaceArtifactSummary
import io.hrns_now.core.domain.policy.WorkspaceDaySelectionPolicy
import io.hrns_now.core.usecase.DeleteProjectUseCase
import io.hrns_now.core.usecase.LoadCockpitUseCase
import io.hrns_now.core.usecase.LoadProjectsUseCase
import io.hrns_now.core.usecase.RegisterProjectUseCase
import io.hrns_now.core.usecase.ResolveActiveProjectUseCase
import io.hrns_now.core.usecase.SelectProjectUseCase
import io.hrns_now.core.usecase.SelectWorkspaceDayUseCase
import io.hrns_now.infra.EnvironmentWorkspaceConfigProvider
import io.hrns_now.infra.WorkflowStateChangeProbe
import io.hrns_now.infra.WorkspaceArtifactProbe
import io.hrns_now.infra.WorkspaceDayDiscovery
import io.hrns_now.infra.WorkspacePathProbe
import io.hrns_now.infra.registry.JsonProjectRegistryAdapter
import io.hrns_now.infra.registry.RealPathGateway
import io.hrns_now.infra.serialization.JsonWorkflowStateAdapter
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Production 경로는 실제 Reader/probe 결과로 단일 UI 상태를 만든다. 명시적인
 * `HRNS_DEMO_MODE`에서만 demo projection을 사용하며, 실데이터 실패를 mock으로 대체하지 않는다.
 */
@Composable
fun App() {
    var selectedRoute by remember { mutableStateOf(AppRoute.Setup) }
    var themeMode by remember { mutableStateOf(HrnsThemeMode.Dark) }
    val typography = remember { hrnsTypography() }
    val isDemoMode = remember { isDemoModeEnabled() }
    val productionViewModel = if (isDemoMode) {
        null
    } else {
        viewModel<AppViewModel> { createProductionViewModel() }
    }

    val uiState: HrnsUiState = if (productionViewModel == null) {
        remember { demoUiState() }
    } else {
        val state by productionViewModel.state.collectAsState()
        state
    }
    val onUiEvent: (HrnsUiEvent) -> Unit = remember(productionViewModel) {
        { event -> productionViewModel?.onEvent(event) }
    }
    val onCockpitAction: (UiAction) -> Unit = remember(onUiEvent) {
        { action -> onUiEvent(HrnsUiEvent.ActionRequested(action)) }
    }

    CompositionLocalProvider(LocalHrnsColors provides hrnsColors(themeMode)) {
        MaterialTheme(
            colorScheme = hrnsMaterialColorScheme(themeMode),
            typography = typography,
        ) {
            HrnsShell(
                selectedRoute = selectedRoute,
                onRouteSelected = { selectedRoute = it },
                uiState = uiState,
                themeMode = themeMode,
                onThemeToggle = {
                    themeMode = when (themeMode) {
                        HrnsThemeMode.Dark -> HrnsThemeMode.Light
                        HrnsThemeMode.Light -> HrnsThemeMode.Dark
                    }
                },
                onCockpitAction = onCockpitAction,
                onUiEvent = onUiEvent,
            )
        }
    }
}

private fun isDemoModeEnabled(): Boolean {
    val raw = System.getenv("HRNS_DEMO_MODE")?.trim()?.lowercase()
    return raw == "1" || raw == "true"
}

/** demo mode는 파일 시스템을 조회하지 않는 명시적 시연 경로다. */
private fun demoUiState(): HrnsUiState {
    val mock = MockProjectionProvider()
    val demoConfig = MockWorkspaceConfigProvider()
    val config = demoConfig.config()
    val notConfigured = { label: String, kind: PathProbeKind ->
        PathProbeResult(label = label, rawPath = null, kind = kind, state = PathProbeState.NotConfigured, message = "미설정")
    }
    return HrnsUiState.Ready(
        shell = mock.shell(),
        setup = mock.setup(),
        workspaceConfig = config,
        workspaceProbeSummary = WorkspaceProbeSummary(
            kitRoot = notConfigured("KitRoot", PathProbeKind.Directory),
            workspaceRoot = notConfigured("WorkspaceRoot", PathProbeKind.Directory),
            projectRoot = notConfigured("ProjectRoot", PathProbeKind.Directory),
            powerShellPath = notConfigured("PowerShell 경로", PathProbeKind.Command),
            claudeCommand = notConfigured("Claude 명령", PathProbeKind.Command),
        ),
        workspaceReadiness = demoConfig.readiness(),
        workspaceArtifactSummary = WorkspaceArtifactSummary(
            items = listOf(
                "요청 입력함" to "demo/REQUEST_INBOX.md",
                "오늘 할 일 파일" to "demo/TODAY_STRATEGY.md",
                "인수인계 파일" to "demo/DAILY_HANDOFF.md",
                "작업 상태 파일" to "demo/WORKFLOW_STATE.json",
            ).map { (label, path) ->
                ArtifactProbeResult(
                    label = label,
                    path = path,
                    kind = ArtifactKind.File,
                    requirement = ArtifactRequirement.Required,
                    state = ArtifactProbeState.Exists,
                    message = "demo",
                )
            },
        ),
        cockpit = mock.cockpit(),
        todayWork = mock.todayWork(),
        runStatus = mock.runStatus(),
        registryProjects = emptyList(),
        workspaceDays = emptyList(),
        activeProjectSourceLabel = "demo",
        registryMessage = null,
    )
}

/** `%APPDATA%\hrns-now\projects.json` — Harness workspace 밖의 앱 소유 Registry 경로다. */
private fun resolveRegistryPath(): Path {
    val appData = System.getenv("APPDATA")?.trim()?.takeIf(String::isNotEmpty)
        ?: (System.getProperty("user.home") + "\\AppData\\Roaming")
    return Paths.get(appData, "hrns-now", "projects.json")
}

private fun createProductionViewModel(): AppViewModel {
    val pathProbe = WorkspacePathProbe()
    val artifactProbe = WorkspaceArtifactProbe()
    val loadCockpit = LoadCockpitUseCase(
        pathProbe = pathProbe::probe,
        readinessProvider = pathProbe::readiness,
        artifactProbe = { config, day -> artifactProbe.probe(config.roots.workspaceRoot, day.date) },
        dayDiscovery = WorkspaceDayDiscovery()::discover,
        daySelectionPolicy = WorkspaceDaySelectionPolicy(),
        statePort = JsonWorkflowStateAdapter(),
    )
    val registry = JsonProjectRegistryAdapter(registryPath = resolveRegistryPath())
    val realPathGateway = RealPathGateway()
    return AppViewModel(
        loadCockpit = loadCockpit,
        changeProbe = WorkflowStateChangeProbe()::lastModifiedOrNull,
        resolveActiveProject = ResolveActiveProjectUseCase(
            registry = registry,
            environmentConfigProvider = { EnvironmentWorkspaceConfigProvider().config() },
        ),
        loadProjects = LoadProjectsUseCase(registry),
        registerProject = RegisterProjectUseCase(
            pathResolver = realPathGateway::resolve,
            registry = registry,
        ),
        selectProject = SelectProjectUseCase(registry),
        selectWorkspaceDay = SelectWorkspaceDayUseCase(registry),
        deleteProject = DeleteProjectUseCase(registry),
        boundaryPathResolver = realPathGateway::resolve,
    )
}
