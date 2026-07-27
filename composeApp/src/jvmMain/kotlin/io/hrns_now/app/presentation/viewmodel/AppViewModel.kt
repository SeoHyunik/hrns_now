package io.hrns_now.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.hrns_now.app.presentation.mapper.CockpitUiStateAssembler
import io.hrns_now.app.presentation.model.HrnsUiEvent
import io.hrns_now.app.presentation.model.HrnsUiState
import io.hrns_now.core.config.WorkspaceConfig
import io.hrns_now.core.domain.model.BoundaryStatus
import io.hrns_now.core.domain.model.HarnessCompatibilityDetail
import io.hrns_now.core.domain.model.HarnessProject
import io.hrns_now.core.domain.model.KitVersionReadResult
import io.hrns_now.core.domain.model.ProjectId
import io.hrns_now.core.domain.model.RootPathCheck
import io.hrns_now.core.domain.model.UiAction
import io.hrns_now.core.domain.model.WorkspaceDay
import io.hrns_now.core.domain.policy.BoundaryPolicy
import io.hrns_now.core.domain.policy.CompatibilityPolicy
import io.hrns_now.core.domain.policy.WorkspaceDaySelection
import io.hrns_now.core.port.KitVersionManifestPort
import io.hrns_now.core.result.RegistryLoadResult
import io.hrns_now.core.result.RegistrySaveResult
import io.hrns_now.core.result.StateReadResult
import io.hrns_now.core.usecase.ActiveProjectSource
import io.hrns_now.core.usecase.DeleteProjectUseCase
import io.hrns_now.core.usecase.LoadCockpitUseCase
import io.hrns_now.core.usecase.LoadProjectsUseCase
import io.hrns_now.core.usecase.RegisterProjectCandidate
import io.hrns_now.core.usecase.RegisterProjectResult
import io.hrns_now.core.usecase.RegisterProjectUseCase
import io.hrns_now.core.usecase.ResolveActiveProjectUseCase
import io.hrns_now.core.usecase.SelectProjectResult
import io.hrns_now.core.usecase.SelectProjectUseCase
import io.hrns_now.core.usecase.SelectWorkspaceDayUseCase
import io.hrns_now.core.usecase.toWorkspaceConfig
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Phase 1C/1D의 단일 UI 상태 보유자다. 조회 orchestration은 [LoadCockpitUseCase]/
 * [ResolveActiveProjectUseCase], Registry 쓰기는 [RegisterProjectUseCase]/[SelectProjectUseCase]/
 * [DeleteProjectUseCase], 화면 조립은 [CockpitUiStateAssembler]에 위임하고 이 클래스는
 * refresh/polling/lifecycle/이벤트 분배만 관리한다. 모든 filesystem 협력자는 [ioDispatcher]
 * 안에서만 호출한다(God ViewModel 금지, `doc/hrns_now_design_pattern.md` §19.1).
 */
class AppViewModel(
    private val loadCockpit: LoadCockpitUseCase,
    private val changeProbe: (WorkspaceDay) -> FileTime?,
    private val resolveActiveProject: ResolveActiveProjectUseCase,
    private val loadProjects: LoadProjectsUseCase,
    private val registerProject: RegisterProjectUseCase,
    private val selectProject: SelectProjectUseCase,
    private val selectWorkspaceDay: SelectWorkspaceDayUseCase,
    private val deleteProject: DeleteProjectUseCase,
    private val boundaryPathResolver: (String?) -> RootPathCheck,
    private val compatibilityPort: KitVersionManifestPort,
    private val boundaryPolicy: BoundaryPolicy = BoundaryPolicy(),
    private val compatibilityPolicy: CompatibilityPolicy = CompatibilityPolicy(),
    private val uiStateAssembler: CockpitUiStateAssembler = CockpitUiStateAssembler(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val pollIntervalMillis: Long = 3000L,
    private val clock: () -> Instant = Instant::now,
    mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel(CoroutineScope(SupervisorJob() + mainDispatcher)) {

    private val _state = MutableStateFlow<HrnsUiState>(HrnsUiState.Loading)
    val state: StateFlow<HrnsUiState> = _state.asStateFlow()

    private var hasResolvedActiveProject = false
    private var currentWorkspaceConfig: WorkspaceConfig? = null
    private var activeProject: HarnessProject? = null
    private var activeSource: ActiveProjectSource = ActiveProjectSource.NoneSelected
    private var registryProjects: List<HarnessProject> = emptyList()
    private var registryMessage: String? = null

    private var selectedDay: WorkspaceDaySelection? = null
    private var availableDates: List<LocalDate> = emptyList()
    private var preferredDate: LocalDate? = null
    private var lastSuccessfulReadAt: Instant? = null
    private var lastAttemptAt: Instant? = null
    private var lastPolledMtime: FileTime? = null
    private var lastCompatibilityDetail: HarnessCompatibilityDetail? = null
    private var loadContextGeneration: Long = 0L
    private var loadSequence: Long = 0L
    private var pollingJob: Job? = null

    init {
        refresh()
        startPolling()
    }

    /** 수동 새로고침이다. 날짜를 다시 선택하고 Reader를 강제로 다시 호출한다. */
    fun refresh() {
        viewModelScope.launch { loadOnce(forceRead = true) }
    }

    /** Phase 1C/1D에서 실제로 연결된 이벤트만 처리한다. */
    fun onEvent(event: HrnsUiEvent) {
        when (event) {
            is HrnsUiEvent.ActionRequested -> if (event.action == UiAction.Refresh) refresh()
            is HrnsUiEvent.ProjectSelected -> viewModelScope.launch { onProjectSelected(event.id) }
            is HrnsUiEvent.ProjectRegistrationRequested ->
                viewModelScope.launch { onProjectRegistrationRequested(event.candidate) }
            is HrnsUiEvent.ProjectDeletionRequested -> viewModelScope.launch { onProjectDeletionRequested(event.id) }
            is HrnsUiEvent.WorkspaceDaySelected -> viewModelScope.launch { onWorkspaceDaySelected(event.date) }
        }
    }

    private suspend fun onProjectSelected(id: ProjectId) {
        when (val result = withContext(ioDispatcher) { selectProject(id) }) {
            is SelectProjectResult.Selected -> {
                applyActiveProject(result.project)
                refreshRegistryProjects("'${result.project.displayName}' 프로젝트를 선택했습니다.")
            }
            SelectProjectResult.NotFound ->
                refreshRegistryProjects("선택한 프로젝트를 찾을 수 없습니다.")
            is SelectProjectResult.SaveFailed ->
                refreshRegistryProjects("프로젝트 선택을 저장하지 못했습니다: ${result.message}")
        }
        loadOnce(forceRead = true)
    }

    private suspend fun onProjectRegistrationRequested(candidate: RegisterProjectCandidate) {
        when (val result = withContext(ioDispatcher) { registerProject(candidate) }) {
            is RegisterProjectResult.Registered -> {
                when (val selected = withContext(ioDispatcher) { selectProject(result.project.id) }) {
                    is SelectProjectResult.Selected -> {
                        applyActiveProject(selected.project)
                        refreshRegistryProjects("'${selected.project.displayName}' 프로젝트를 등록하고 선택했습니다.")
                    }
                    SelectProjectResult.NotFound ->
                        refreshRegistryProjects("프로젝트는 저장됐지만 다시 찾을 수 없습니다.")
                    is SelectProjectResult.SaveFailed ->
                        refreshRegistryProjects("프로젝트는 저장됐지만 활성 선택을 기록하지 못했습니다: ${selected.message}")
                }
            }
            is RegisterProjectResult.InvalidCandidate ->
                registryMessage = "등록할 수 없습니다: ${result.message}"
            is RegisterProjectResult.BoundaryRejected -> {
                registryMessage =
                    "등록할 수 없습니다: 경로 경계 조건을 확인하세요 (${result.boundary.violations.size}건 위반)."
            }
            is RegisterProjectResult.SaveFailed ->
                registryMessage = "등록 실패: ${result.message}"
        }
        loadOnce(forceRead = true)
    }

    private suspend fun onProjectDeletionRequested(id: ProjectId) {
        when (val deleted = withContext(ioDispatcher) { deleteProject(id) }) {
            RegistrySaveResult.Success -> {
                if (activeProject?.id == id) {
                    hasResolvedActiveProject = false
                    activeProject = null
                    currentWorkspaceConfig = null
                    selectedDay = null
                    availableDates = emptyList()
                    preferredDate = null
                    lastPolledMtime = null
                    lastCompatibilityDetail = null
                    loadContextGeneration += 1
                }
                refreshRegistryProjects("프로젝트를 삭제했습니다.")
            }
            is RegistrySaveResult.Failed ->
                refreshRegistryProjects("프로젝트를 삭제하지 못했습니다: ${deleted.message}")
        }
        loadOnce(forceRead = true)
    }

    private suspend fun onWorkspaceDaySelected(date: LocalDate) {
        if (date !in availableDates) {
            registryMessage = "선택한 날짜 폴더를 찾을 수 없습니다."
            loadOnce(forceRead = true)
            return
        }

        preferredDate = date
        selectedDay = null
        loadContextGeneration += 1
        lastPolledMtime = null
        val project = activeProject
        if (project != null) {
            when (val saved = withContext(ioDispatcher) { selectWorkspaceDay(project, date) }) {
                RegistrySaveResult.Success -> {
                    activeProject = project.copy(lastSelectedDate = date)
                    refreshRegistryProjects("${date} 날짜를 선택했습니다.")
                }
                is RegistrySaveResult.Failed ->
                    registryMessage = "날짜는 열었지만 마지막 선택을 저장하지 못했습니다: ${saved.message}"
            }
        } else {
            registryMessage = "${date} 날짜를 선택했습니다."
        }
        loadOnce(forceRead = true)
    }

    private suspend fun refreshRegistryProjects(actionMessage: String) {
        val loaded = withContext(ioDispatcher) { loadProjects() }
        registryProjects = loaded.projects()
        registryMessage = loaded.userMessage() ?: actionMessage
    }

    private fun applyActiveProject(project: HarnessProject) {
        hasResolvedActiveProject = true
        activeProject = project
        activeSource = ActiveProjectSource.Registry
        currentWorkspaceConfig = project.toWorkspaceConfig()
        selectedDay = null
        availableDates = emptyList()
        preferredDate = project.lastSelectedDate
        lastPolledMtime = null
        lastCompatibilityDetail = null
        loadContextGeneration += 1
    }

    private fun startPolling() {
        if (pollingJob != null) return
        pollingJob = viewModelScope.launch {
            while (true) {
                delay(pollIntervalMillis)
                loadOnce(forceRead = false)
            }
        }
    }

    /**
     * polling은 선택된 State 파일의 mtime이 달라진 경우에만 전체 query를 다시 실행한다.
     * 활성 프로젝트가 아직 결정되지 않았으면(최초 진입) [ResolveActiveProjectUseCase]로
     * Registry의 마지막 선택 → 환경변수 fallback → 미선택 순으로 결정한다. 날짜 탐색, mtime
     * 조회, boundary 판정도 모두 IO dispatcher에서 수행한다.
     */
    private suspend fun loadOnce(forceRead: Boolean) {
        var contextGeneration = loadContextGeneration

        if (!hasResolvedActiveProject) {
            val resolution = withContext(ioDispatcher) { resolveActiveProject() }
            if (contextGeneration != loadContextGeneration) return
            hasResolvedActiveProject = true
            activeProject = resolution.project
            activeSource = resolution.source
            registryProjects = resolution.registryProjects
            currentWorkspaceConfig = resolution.workspaceConfig
            preferredDate = resolution.project?.lastSelectedDate
            registryMessage = resolution.registryLoadResult.userMessage() ?: registryMessage
            loadContextGeneration += 1
            contextGeneration = loadContextGeneration
        }
        val workspaceConfig = requireNotNull(currentWorkspaceConfig)

        val dayResolution = if (forceRead || selectedDay == null) {
            withContext(ioDispatcher) { loadCockpit.resolveDays(workspaceConfig, preferredDate) }
        } else {
            io.hrns_now.core.usecase.WorkspaceDayResolution(
                selection = requireNotNull(selectedDay),
                availableDates = availableDates,
            )
        }
        val daySelection = dayResolution.selection

        val currentMtime = if (loadCockpit.hasConfiguredWorkspace(workspaceConfig)) {
            withContext(ioDispatcher) { changeProbe(daySelection.workspaceDay) }
        } else {
            null
        }
        val compatibilityDetail = withContext(ioDispatcher) { evaluateCompatibility(workspaceConfig) }
        val mtimeChanged = currentMtime != lastPolledMtime
        val compatibilityChanged = compatibilityDetail != lastCompatibilityDetail
        val shouldRead = forceRead || mtimeChanged || compatibilityChanged || _state.value == HrnsUiState.Loading
        if (!shouldRead) return

        val sequence = ++loadSequence
        val loaded = withContext(ioDispatcher) { loadCockpit(workspaceConfig, daySelection) }
        val boundaryStatus = withContext(ioDispatcher) { evaluateBoundary(workspaceConfig) }
        if (sequence != loadSequence || contextGeneration != loadContextGeneration) return

        selectedDay = daySelection
        availableDates = dayResolution.availableDates
        if (preferredDate != null && preferredDate !in availableDates) {
            preferredDate = null
        }
        lastPolledMtime = currentMtime
        lastCompatibilityDetail = compatibilityDetail
        val now = clock()
        lastAttemptAt = now
        if (loaded.stateRead is StateReadResult.Success) {
            lastSuccessfulReadAt = now
        }

        _state.value = uiStateAssembler.assemble(
            loaded = loaded,
            lastSuccessfulReadAtLabel = lastSuccessfulReadAt?.let(::formatInstant),
            lastAttemptAtLabel = lastAttemptAt?.let(::formatInstant),
            registryProjects = registryProjects,
            availableDates = availableDates,
            activeProjectId = activeProject?.id,
            activeProjectSource = activeSource,
            registryMessage = registryMessage,
            boundaryStatus = boundaryStatus,
            compatibilityDetail = compatibilityDetail,
        )
    }

    private fun RegistryLoadResult.projects(): List<HarnessProject> =
        when (this) {
            is RegistryLoadResult.Success -> projects
            is RegistryLoadResult.RecoveredFromCorruption -> projects
            is RegistryLoadResult.Unreadable -> emptyList()
        }

    private fun RegistryLoadResult.userMessage(): String? =
        when (this) {
            is RegistryLoadResult.Success -> null
            is RegistryLoadResult.RecoveredFromCorruption -> "Registry 손상을 복구했습니다. $message"
            is RegistryLoadResult.Unreadable -> "Registry를 읽을 수 없습니다: $message"
        }

    private fun evaluateBoundary(workspaceConfig: WorkspaceConfig): BoundaryStatus {
        val kit = boundaryPathResolver(workspaceConfig.roots.kitRoot)
        val workspace = boundaryPathResolver(workspaceConfig.roots.workspaceRoot)
        val repository = boundaryPathResolver(workspaceConfig.roots.projectRoot)
        return boundaryPolicy.evaluate(kit = kit, workspace = workspace, repository = repository).status
    }

    /**
     * 선택된 프로젝트의 Kit root에서 `kit-version.json`을 읽고 판정한다(Phase 2). Kit root가
     * 아직 설정되지 않았거나 경로를 해석할 수 없으면 manifest가 없는 것과 동일하게 처리한다 —
     * `evaluateBoundary`와 마찬가지로 이 함수 전체가 [ioDispatcher] 안에서 호출된다.
     */
    private fun evaluateCompatibility(workspaceConfig: WorkspaceConfig): HarnessCompatibilityDetail {
        val kitRoot = parseKitRoot(workspaceConfig.roots.kitRoot)
            ?: return compatibilityPolicy.evaluate(KitVersionReadResult.Missing)
        return compatibilityPolicy.evaluate(compatibilityPort.readManifest(kitRoot))
    }

    private fun parseKitRoot(raw: String?): Path? {
        val value = raw?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return try {
            Path.of(value).toAbsolutePath().normalize()
        } catch (_: InvalidPathException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    /** 테스트나 lifecycle owner가 없는 host에서 polling과 내부 scope를 명시적으로 취소한다. */
    fun dispose() {
        pollingJob?.cancel()
        pollingJob = null
        viewModelScope.cancel()
    }

    private fun formatInstant(instant: Instant): String =
        DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(instant)
}
