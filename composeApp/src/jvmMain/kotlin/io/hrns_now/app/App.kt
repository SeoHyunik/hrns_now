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
import io.hrns_now.app.presentation.model.NotificationItem
import io.hrns_now.app.presentation.viewmodel.AppViewModel
import io.hrns_now.app.ui.HrnsShell
import io.hrns_now.app.ui.HrnsThemeMode
import io.hrns_now.app.ui.LocalAppLocale
import io.hrns_now.app.ui.LocalHrnsColors
import io.hrns_now.app.ui.hrnsColors
import io.hrns_now.app.ui.hrnsMaterialColorScheme
import io.hrns_now.app.ui.hrnsTypography
import io.hrns_now.core.AppRoute
import io.hrns_now.core.domain.model.AppLocale
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
import io.hrns_now.core.domain.policy.ActionPolicy
import io.hrns_now.core.domain.policy.WorkspaceDaySelectionPolicy
import io.hrns_now.core.usecase.ClearActiveProjectUseCase
import io.hrns_now.core.usecase.DeleteProjectUseCase
import io.hrns_now.core.usecase.ExecuteHarnessActionUseCase
import io.hrns_now.core.usecase.HarnessCommandMapper
import io.hrns_now.core.usecase.LoadCockpitUseCase
import io.hrns_now.core.usecase.LoadProjectsUseCase
import io.hrns_now.core.usecase.OnboardProjectUseCase
import io.hrns_now.core.usecase.RegisterProjectUseCase
import io.hrns_now.core.usecase.ResolveActiveProjectUseCase
import io.hrns_now.core.usecase.SaveRequestUseCase
import io.hrns_now.core.usecase.SelectProjectUseCase
import io.hrns_now.core.usecase.SelectWorkspaceDayUseCase
import io.hrns_now.infra.EnvironmentWorkspaceConfigProvider
import io.hrns_now.infra.WorkflowStateChangeProbe
import io.hrns_now.infra.WorkspaceArtifactProbe
import io.hrns_now.infra.WorkspaceDayDiscovery
import io.hrns_now.infra.WorkspacePathProbe
import io.hrns_now.infra.bridge.RepositoryBridgeProbe
import io.hrns_now.infra.kitversion.JsonKitVersionManifestAdapter
import io.hrns_now.infra.lock.LocalProcessLockAdapter
import io.hrns_now.infra.process.PowerShellHarnessAdapter
import io.hrns_now.infra.registry.JsonProjectRegistryAdapter
import io.hrns_now.infra.registry.RealPathGateway
import io.hrns_now.infra.runtime.DeveloperSdkRuntimeResolver
import io.hrns_now.infra.git.CommandLineGitStatusAdapter
import io.hrns_now.infra.preferences.UiPreferencesFileAdapter
import io.hrns_now.infra.recovery.WorkspaceRecoveryDiagnosticsAdapter
import io.hrns_now.infra.request.RequestInboxWriterAdapter
import io.hrns_now.infra.request.TodayStrategyFileReaderAdapter
import io.hrns_now.infra.security.SecretMaskingProcessRunner
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
    val notifications: List<NotificationItem> = if (productionViewModel == null) {
        emptyList()
    } else {
        val items by productionViewModel.notifications.collectAsState()
        items
    }
    val locale: AppLocale = if (productionViewModel == null) {
        AppLocale.Korean
    } else {
        val current by productionViewModel.locale.collectAsState()
        current
    }
    val onUiEvent: (HrnsUiEvent) -> Unit = remember(productionViewModel) {
        { event -> productionViewModel?.onEvent(event) }
    }
    val onCockpitAction: (UiAction) -> Unit = remember(onUiEvent) {
        { action ->
            when (action) {
                // 순수 navigation이다 — Harness/State에 영향을 주지 않으므로 ViewModel 이벤트로 보내지 않는다.
                // (새 Phase 8 §2.3/§5: 이전에는 이 분기 밖 action들이 else로 떨어져 ViewModel도
                // 처리하지 않는 완전한 무반응 버튼이었다.)
                UiAction.OpenRecoveryCenter, UiAction.ReviewClosure -> selectedRoute = AppRoute.Recovery
                UiAction.ConnectProject, UiAction.SelectWorkspaceDay -> selectedRoute = AppRoute.Setup
                UiAction.ReviewPlan -> selectedRoute = AppRoute.Strategy
                UiAction.ShowCompatibilityIssue -> selectedRoute = AppRoute.Cockpit
                UiAction.ViewExecutionStatus -> selectedRoute = AppRoute.Run
                // OpenToday는 실제 날짜 선택(IO)이 필요해 순수 navigation이 아니다 — ViewModel이 처리한다.
                else -> onUiEvent(HrnsUiEvent.ActionRequested(action))
            }
        }
    }

    CompositionLocalProvider(
        LocalHrnsColors provides hrnsColors(themeMode),
        LocalAppLocale provides locale,
    ) {
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
                notifications = notifications,
                onDismissNotification = { id -> productionViewModel?.dismissNotification(id) },
                onMarkNotificationRead = { id -> productionViewModel?.markNotificationRead(id) },
                onMarkAllNotificationsRead = { productionViewModel?.markAllNotificationsRead() },
                locale = locale,
                onLocaleChange = { newLocale -> productionViewModel?.setLocale(newLocale) },
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
                "작업 계획 파일" to "demo/TODAY_STRATEGY.md",
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
        recovery = mock.recovery(),
        registryProjects = emptyList(),
        workspaceDays = emptyList(),
        todayDate = java.time.LocalDate.now(),
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

/** `%APPDATA%\hrns-now\ui-preferences.json` — locale처럼 UI 소유 설정 전용 경로다(새 Phase 8 §7). */
private fun resolveUiPreferencesPath(): Path {
    val appData = System.getenv("APPDATA")?.trim()?.takeIf(String::isNotEmpty)
        ?: (System.getProperty("user.home") + "\\AppData\\Roaming")
    return Paths.get(appData, "hrns-now", "ui-preferences.json")
}

/** `%LOCALAPPDATA%\hrns-now\locks\` — Harness workspace 밖의 앱 소유 lock 디렉터리다(Phase 3). */
private fun resolveLocksRoot(): Path {
    val localAppData = System.getenv("LOCALAPPDATA")?.trim()?.takeIf(String::isNotEmpty)
        ?: (System.getProperty("user.home") + "\\AppData\\Local")
    return Paths.get(localAppData, "hrns-now", "locks")
}

private fun createProductionViewModel(): AppViewModel {
    val pathProbe = WorkspacePathProbe()
    val artifactProbe = WorkspaceArtifactProbe()
    val workflowState = JsonWorkflowStateAdapter()
    val processLock = LocalProcessLockAdapter(locksRoot = resolveLocksRoot())
    val harnessRunner = SecretMaskingProcessRunner(PowerShellHarnessAdapter())
    val requestWriter = RequestInboxWriterAdapter()
    val loadCockpit = LoadCockpitUseCase(
        pathProbe = pathProbe::probe,
        readinessProvider = pathProbe::readiness,
        artifactProbe = { config, day -> artifactProbe.probe(config.roots.workspaceRoot, day.date) },
        dayDiscovery = WorkspaceDayDiscovery()::discover,
        daySelectionPolicy = WorkspaceDaySelectionPolicy(),
        statePort = workflowState,
    )
    val registry = JsonProjectRegistryAdapter(registryPath = resolveRegistryPath())
    val realPathGateway = RealPathGateway()
    val bridgeProbe = RepositoryBridgeProbe()
    // 새 Phase 7: HRNS-NOW source checkout 상대 `.local/harness-kit` 개발용 SDK와 명시적 외부
    // Kit을 모두 이 하나의 resolver로 해석한다 — internal/external 분기는 여기 한 곳에만 있다
    // (`doc/hrns_now_design_pattern.md` §20.1).
    val runtimeSourceResolver = DeveloperSdkRuntimeResolver()
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
            runtimeSourceResolver = runtimeSourceResolver::resolve,
            registry = registry,
        ),
        selectProject = SelectProjectUseCase(registry),
        selectWorkspaceDay = SelectWorkspaceDayUseCase(registry),
        deleteProject = DeleteProjectUseCase(registry),
        clearActiveProject = ClearActiveProjectUseCase(registry),
        boundaryPathResolver = realPathGateway::resolve,
        compatibilityPort = JsonKitVersionManifestAdapter(),
        runtimeSourceResolver = runtimeSourceResolver,
        processLock = processLock,
        harnessRunner = harnessRunner,
        executeHarnessAction = ExecuteHarnessActionUseCase(
            actionPolicy = ActionPolicy(),
            commandMapper = HarnessCommandMapper(),
            processLock = processLock,
            harnessRunner = harnessRunner,
            workflowState = workflowState,
        ),
        onboardProject = OnboardProjectUseCase(
            processLock = processLock,
            harnessRunner = harnessRunner,
            workflowState = workflowState,
            bridgeProbe = bridgeProbe,
            artifactProbe = artifactProbe::probe,
        ),
        bridgeProbe = bridgeProbe,
        saveRequest = SaveRequestUseCase(requestWriter),
        todayStrategyReader = TodayStrategyFileReaderAdapter(),
        gitStatusPort = CommandLineGitStatusAdapter(),
        uiPreferencesPort = UiPreferencesFileAdapter(resolveUiPreferencesPath()),
        recoveryDiagnosticsPort = WorkspaceRecoveryDiagnosticsAdapter(),
    )
}
