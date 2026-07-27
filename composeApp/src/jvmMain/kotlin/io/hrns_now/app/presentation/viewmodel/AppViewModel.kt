package io.hrns_now.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.hrns_now.app.presentation.buildTodayWorkProjection
import io.hrns_now.app.presentation.mapper.CockpitUiStateAssembler
import io.hrns_now.app.presentation.mapper.RunStatusProjectionAssembler
import io.hrns_now.app.presentation.mapper.lockSummaryLabel
import io.hrns_now.app.presentation.model.CockpitActionItem
import io.hrns_now.app.presentation.model.HrnsUiEvent
import io.hrns_now.app.presentation.model.HrnsUiState
import io.hrns_now.core.config.WorkspaceConfig
import io.hrns_now.core.domain.model.ActionContext
import io.hrns_now.core.domain.model.BoundaryStatus
import io.hrns_now.core.domain.model.HarnessCommand
import io.hrns_now.core.domain.model.HarnessCommandKind
import io.hrns_now.core.domain.model.HarnessCompatibilityDetail
import io.hrns_now.core.domain.model.HarnessProject
import io.hrns_now.core.domain.model.KitVersionReadResult
import io.hrns_now.core.domain.model.LockAcquireResult
import io.hrns_now.core.domain.model.LockHandle
import io.hrns_now.core.domain.model.LockState
import io.hrns_now.core.domain.model.ProcessCancellationToken
import io.hrns_now.core.domain.model.ProcessRunStatus
import io.hrns_now.core.domain.model.ProjectId
import io.hrns_now.core.domain.model.RequestEntryDraft
import io.hrns_now.core.domain.model.RootPathCheck
import io.hrns_now.core.domain.model.UiAction
import io.hrns_now.core.domain.model.WorkspaceDay
import io.hrns_now.core.domain.model.SelectedDayKind
import io.hrns_now.core.domain.model.toActiveSliceKind
import io.hrns_now.core.domain.model.toCompatibilityStatus
import io.hrns_now.core.domain.policy.BoundaryPolicy
import io.hrns_now.core.domain.policy.CompatibilityPolicy
import io.hrns_now.core.domain.policy.ExternalExecutionDetectionPolicy
import io.hrns_now.core.domain.policy.WorkspaceDaySelection
import io.hrns_now.core.port.HarnessRunnerPort
import io.hrns_now.core.port.KitVersionManifestPort
import io.hrns_now.core.port.LockInspection
import io.hrns_now.core.port.ProcessLockPort
import io.hrns_now.core.port.TodayStrategyReaderPort
import io.hrns_now.core.usecase.ExecuteHarnessActionOutcome
import io.hrns_now.core.usecase.ExecuteHarnessActionUseCase
import io.hrns_now.core.usecase.HarnessExecutionContext
import io.hrns_now.core.usecase.SaveRequestOutcome
import io.hrns_now.core.usecase.SaveRequestUseCase
import io.hrns_now.core.result.RegistryLoadResult
import io.hrns_now.core.result.RegistrySaveResult
import io.hrns_now.core.result.HarnessOverallStatus
import io.hrns_now.core.result.ProcessRunResult
import io.hrns_now.core.result.StateReadResult
import io.hrns_now.core.usecase.ActiveProjectSource
import io.hrns_now.core.usecase.DeleteProjectUseCase
import io.hrns_now.core.usecase.LoadCockpitUseCase
import io.hrns_now.core.usecase.LoadProjectsUseCase
import io.hrns_now.core.usecase.RegisterProjectCandidate
import io.hrns_now.core.usecase.ProjectRegistrationInspection
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
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
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
    private val processLock: ProcessLockPort,
    /** 프로젝트 등록 전 Doctor gate는 아직 selected ActionContext가 없으므로 별도 진단 경로로 남긴다. */
    private val harnessRunner: HarnessRunnerPort,
    private val executeHarnessAction: ExecuteHarnessActionUseCase,
    private val saveRequest: SaveRequestUseCase,
    private val todayStrategyReader: TodayStrategyReaderPort,
    private val boundaryPolicy: BoundaryPolicy = BoundaryPolicy(),
    private val compatibilityPolicy: CompatibilityPolicy = CompatibilityPolicy(),
    private val externalExecutionDetectionPolicy: ExternalExecutionDetectionPolicy = ExternalExecutionDetectionPolicy(),
    private val uiStateAssembler: CockpitUiStateAssembler = CockpitUiStateAssembler(),
    private val runStatusAssembler: RunStatusProjectionAssembler = RunStatusProjectionAssembler(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val pollIntervalMillis: Long = 3000L,
    private val runTimeout: Duration = Duration.ofSeconds(90),
    private val lockHeartbeatIntervalMillis: Long = 10_000L,
    private val clock: () -> Instant = Instant::now,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
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

    private var harnessRunView: HarnessRunViewState = HarnessRunViewState()
    private var harnessRunJob: Job? = null
    private var harnessRunCancellationToken: ProcessCancellationToken? = null
    private var currentLockHandle: LockHandle? = null
    private var lockHeartbeatJob: Job? = null
    private var lastLockInspection: LockInspection? = null
    private var externalExecutionSuspected: Boolean = false
    private var localStateRefreshPending: Boolean = false
    private var latestExecutionContext: HarnessExecutionContext? = null

    private var strategyText: String? = null
    private var requestInboxNotice: String? = null
    private var requestSaving: Boolean = false
    private var requestSaveSucceeded: Boolean = false

    init {
        refresh()
        startPolling()
    }

    /** 수동 새로고침이다. 날짜를 다시 선택하고 Reader를 강제로 다시 호출한다. */
    fun refresh() {
        // 외부 State 변경은 휴리스틱이므로 사용자의 명시적 재확인으로만 보류를 해제한다.
        externalExecutionSuspected = false
        viewModelScope.launch { loadOnce(forceRead = true) }
    }

    /** Phase 1C/1D에서 실제로 연결된 이벤트만 처리한다. */
    fun onEvent(event: HrnsUiEvent) {
        when (event) {
            is HrnsUiEvent.ActionRequested -> when (event.action) {
                UiAction.Refresh -> refresh()
                UiAction.RunDoctor -> onHarnessRunRequested(UiAction.RunDoctor, HarnessCommandKind.Doctor)
                UiAction.RunOpsValidation -> onHarnessRunRequested(UiAction.RunOpsValidation, HarnessCommandKind.ValidateOps)
                UiAction.BootstrapDay -> onHarnessRunRequested(UiAction.BootstrapDay, HarnessCommandKind.Bootstrap)
                UiAction.RunPlanning -> onHarnessRunRequested(UiAction.RunPlanning, HarnessCommandKind.Planning)
                UiAction.RunReplan -> onHarnessRunRequested(UiAction.RunReplan, HarnessCommandKind.Replan)
                UiAction.RunCodeSlice -> onHarnessRunRequested(UiAction.RunCodeSlice, HarnessCommandKind.ExecutionCode)
                UiAction.RunDocSlice -> onHarnessRunRequested(UiAction.RunDocSlice, HarnessCommandKind.ExecutionDoc)
                else -> Unit
            }
            is HrnsUiEvent.ProjectSelected -> viewModelScope.launch { onProjectSelected(event.id) }
            is HrnsUiEvent.ProjectRegistrationRequested ->
                viewModelScope.launch { onProjectRegistrationRequested(event.candidate) }
            is HrnsUiEvent.ProjectDeletionRequested -> viewModelScope.launch { onProjectDeletionRequested(event.id) }
            is HrnsUiEvent.WorkspaceDaySelected -> viewModelScope.launch { onWorkspaceDaySelected(event.date) }
            HrnsUiEvent.HarnessRunCancelRequested -> harnessRunCancellationToken?.requestCancel()
            HrnsUiEvent.LockForceReleaseRequested -> viewModelScope.launch { onLockForceReleaseRequested() }
            is HrnsUiEvent.RequestEntrySubmitted -> viewModelScope.launch { onRequestEntrySubmitted(event.draft) }
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

    /**
     * 신규 프로젝트는 Registry에 쓰기 전에 경계 검사 → Doctor → compatibility 순서로 검증한다.
     * Doctor warn(GPU capability 등)은 결과 카드로 보존하되, fail/계약 미파싱/호환성 불일치는
     * fail-closed로 Registry 저장을 막는다.
     */
    private suspend fun onProjectRegistrationRequested(candidate: RegisterProjectCandidate) {
        val inspection = withContext(ioDispatcher) { registerProject.inspect(candidate) }
        val prepared = when (inspection) {
            is ProjectRegistrationInspection.Ready -> inspection
            is ProjectRegistrationInspection.InvalidCandidate -> {
                registryMessage = "등록할 수 없습니다: ${inspection.message}"
                loadOnce(forceRead = true)
                return
            }
            is ProjectRegistrationInspection.BoundaryRejected -> {
                registryMessage = "등록할 수 없습니다: 경로 경계 조건을 확인하세요 (${inspection.boundary.violations.size}건 위반)."
                loadOnce(forceRead = true)
                return
            }
        }
        val project = prepared.project
        val date = clock().atZone(ZoneId.systemDefault()).toLocalDate()
        val command = HarnessCommand.Doctor(
            kitRoot = project.kitRoot,
            workspaceRoot = project.projectWorkspaceRoot,
            projectRoot = project.repositoryRoot,
            date = date,
        )
        harnessRunCancellationToken = ProcessCancellationToken()
        harnessRunView = HarnessRunViewState(
            lastCommand = HarnessCommandKind.Doctor,
            isRunning = true,
            runStartedAt = clock(),
        )
        refreshRunProjectionOnly()

        val lockResult = withContext(ioDispatcher) {
            processLock.acquire(project.id, date, HarnessCommandKind.Doctor)
        }
        val acquired = lockResult as? LockAcquireResult.Acquired
        if (acquired == null) {
            harnessRunView = harnessRunView.copy(
                isRunning = false,
                notice = "온보딩 진단 잠금을 안전하게 획득하지 못해 Registry를 저장하지 않았습니다.",
            )
            refreshRunProjectionOnly()
            return
        }

        currentLockHandle = acquired.handle
        startHeartbeat(acquired.handle)
        val result = try {
            withContext(ioDispatcher) {
                harnessRunner.execute(command, runTimeout, requireNotNull(harnessRunCancellationToken))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ProcessRunResult.StartFailed("온보딩 진단 프로세스를 안전하게 관찰하지 못했습니다.")
        } finally {
            stopHeartbeat()
            withContext(ioDispatcher) { processLock.release(acquired.handle) }
            currentLockHandle = null
            lastLockInspection = null
        }
        harnessRunView = harnessRunView.copy(isRunning = false, lastResult = result, notice = null)
        refreshRunProjectionOnly()

        val compatibility = withContext(ioDispatcher) {
            compatibilityPolicy.evaluate(compatibilityPort.readManifest(project.kitRoot))
        }
        if (!doctorAllowsRegistration(result)) {
            registryMessage = "Doctor 진단을 통과하지 못해 Registry를 저장하지 않았습니다. 실행 현황에서 결과를 확인하세요."
            loadOnce(forceRead = true)
            return
        }
        if (!compatibilityAllowsRegistration(compatibility)) {
            registryMessage = "Harness 호환성을 확인하지 못해 Registry를 저장하지 않았습니다."
            loadOnce(forceRead = true)
            return
        }

        when (val result = withContext(ioDispatcher) { registerProject.save(prepared) }) {
            is RegisterProjectResult.Registered -> {
                when (val selected = withContext(ioDispatcher) { selectProject(result.project.id) }) {
                    is SelectProjectResult.Selected -> {
                        applyActiveProject(selected.project)
                        refreshRegistryProjects("Doctor·호환성 확인 후 '${selected.project.displayName}' 프로젝트를 등록했습니다.")
                    }
                    SelectProjectResult.NotFound ->
                        refreshRegistryProjects("프로젝트는 저장됐지만 다시 찾을 수 없습니다.")
                    is SelectProjectResult.SaveFailed ->
                        refreshRegistryProjects("프로젝트는 저장됐지만 활성 선택을 기록하지 못했습니다: ${selected.message}")
                }
            }
            is RegisterProjectResult.SaveFailed -> registryMessage = "Registry 저장 실패: ${result.message}"
            is RegisterProjectResult.InvalidCandidate,
            is RegisterProjectResult.BoundaryRejected,
            -> error("검증된 후보를 저장할 때는 발생할 수 없는 결과입니다.")
        }
        loadOnce(forceRead = true)
    }

    private fun doctorAllowsRegistration(result: ProcessRunResult): Boolean =
        result is ProcessRunResult.Completed &&
            result.exitCode == 0 &&
            result.contract?.overall !is HarnessOverallStatus.Fail &&
            result.contract?.overall !is HarnessOverallStatus.Unknown

    private fun compatibilityAllowsRegistration(detail: HarnessCompatibilityDetail): Boolean =
        detail is HarnessCompatibilityDetail.Supported || detail is HarnessCompatibilityDetail.SupportedWithUnknownFields

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
                    resetHarnessRunContext()
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
        resetHarnessRunContext()
        loadContextGeneration += 1
    }

    /**
     * project/day 전환 시 화면에 남아있던 이전 컨텍스트의 실행 표시를 지운다. 진행 중이던
     * 실행 자체는 강제 종료하지 않는다 — 취소 신호만 보내고 lock 해제까지는 백그라운드에서
     * 자연스럽게 끝나게 둔다(late-write guard가 그 결과의 late-write를 막는다).
     */
    private fun resetHarnessRunContext() {
        harnessRunCancellationToken?.requestCancel()
        harnessRunView = HarnessRunViewState()
        lastLockInspection = null
        externalExecutionSuspected = false
        localStateRefreshPending = false
        latestExecutionContext = null
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
        val stateChangedAfterInitialObservation =
            lastPolledMtime != null && currentMtime != null && mtimeChanged
        if (
            externalExecutionDetectionPolicy.isSuspected(
                stateChangedAfterInitialObservation = stateChangedAfterInitialObservation,
                localRunInProgress = harnessRunView.isRunning,
                localStateRefreshPending = localStateRefreshPending,
            )
        ) {
            externalExecutionSuspected = true
        }
        // Own Harness completion triggers exactly one State reread. Its State change is not external.
        if (localStateRefreshPending && (forceRead || mtimeChanged)) {
            localStateRefreshPending = false
        }
        val compatibilityChanged = compatibilityDetail != lastCompatibilityDetail
        val shouldRead = forceRead || mtimeChanged || compatibilityChanged || _state.value == HrnsUiState.Loading
        if (!shouldRead) return

        val sequence = ++loadSequence
        val loaded = withContext(ioDispatcher) { loadCockpit(workspaceConfig, daySelection) }
        val boundaryStatus = withContext(ioDispatcher) { evaluateBoundary(workspaceConfig) }
        val lockInspection = withContext(ioDispatcher) {
            inspectLock(activeProject?.id, daySelection.workspaceDay.date)
        }
        strategyText = withContext(ioDispatcher) { todayStrategyReader.read(daySelection.workspaceDay) }
        if (sequence != loadSequence || contextGeneration != loadContextGeneration) return
        lastLockInspection = lockInspection

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

        latestExecutionContext = activeProject
            ?.takeIf { loaded.projectConnected }
            ?.let { project ->
                val activeSliceKind = (loaded.stateRead as? StateReadResult.Success)
                    ?.state
                    ?.executionWrapper
                    ?.toActiveSliceKind()
                HarnessExecutionContext(
                    project = project,
                    day = daySelection.workspaceDay,
                    actionContext = ActionContext(
                        projectConnected = loaded.projectConnected,
                        selectedDayKind = if (daySelection.isReadOnly) SelectedDayKind.Past else SelectedDayKind.Today,
                        stateRead = loaded.stateRead,
                        compatibility = compatibilityDetail.toCompatibilityStatus(),
                        boundary = boundaryStatus,
                        process = currentProcessRunStatus(lockInspection),
                        activeSliceKind = activeSliceKind,
                    ),
                )
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
            processRunStatus = currentProcessRunStatus(lockInspection),
            runStatus = runStatusAssembler.assemble(
                runView = harnessRunView,
                lockInspection = lockInspection,
                now = clock(),
                externalExecutionSuspected = externalExecutionSuspected,
            ),
            harnessRunInProgress = harnessRunView.isRunning,
            strategyText = strategyText,
            requestInboxNotice = requestInboxNotice,
            requestSaving = requestSaving,
            requestSaveSucceeded = requestSaveSucceeded,
            lockSummaryLabel = lockSummaryLabel(lockInspection, now),
        )
    }

    private fun currentProcessRunStatus(lockInspection: LockInspection?): ProcessRunStatus =
        when {
            harnessRunView.isRunning -> ProcessRunStatus.Running
            externalExecutionSuspected || lockInspection?.state == LockState.Active -> ProcessRunStatus.Locked
            else -> ProcessRunStatus.Idle
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

    /**
     * 중복 클릭을 막고(이미 실행 중이면 무시), 정책·lock을 통과한 뒤에만 [executeHarnessAction]을 호출한다
     * (Phase 3). 실행 종료 뒤에는 lock을 보유한 채 State를 다시 읽고, heartbeat를 멈춘 뒤
     * lock을 해제·재검사한다. stdout 성공 문구가 아니라 재읽은 State가 완료 판단의
     * 근거로 남게 하기 위함이다. project/day가 실행 도중 바뀌면
     * [loadContextGeneration] 비교로 late-write를 막는다.
     */
    private fun onHarnessRunRequested(action: UiAction, kind: HarnessCommandKind) {
        if (harnessRunView.isRunning) return
        if (!isHarnessRunAllowed(action)) {
            harnessRunView = HarnessRunViewState(
                lastCommand = kind,
                notice = "현재 상태에서 허용되지 않은 실행입니다. 상태를 새로고침한 뒤 권장 행동을 확인하세요.",
            )
            refreshRunProjectionOnly()
            return
        }
        if (externalExecutionSuspected) {
            harnessRunView = HarnessRunViewState(
                lastCommand = kind,
                notice = "WORKFLOW_STATE.json의 외부 변경 가능성이 감지되어 새 실행을 보류했습니다. 새로고침으로 다시 확인하세요.",
            )
            refreshRunProjectionOnly()
            return
        }
        val context = latestExecutionContext ?: return
        val capturedGeneration = loadContextGeneration
        val cancellationToken = ProcessCancellationToken()
        harnessRunCancellationToken = cancellationToken
        harnessRunView = HarnessRunViewState(lastCommand = kind, isRunning = true, runStartedAt = clock(), lastResult = null)
        refreshRunProjectionOnly()

        harnessRunJob = viewModelScope.launch {
            val outcome = try {
                withContext(ioDispatcher) {
                    executeHarnessAction.invoke(
                        context = context,
                        action = action,
                        timeout = runTimeout,
                        cancellationToken = cancellationToken,
                        onLockAcquired = { handle ->
                            withContext(mainDispatcher) {
                                currentLockHandle = handle
                                startHeartbeat(handle)
                            }
                        },
                        onBeforeLockRelease = {
                            withContext(mainDispatcher) {
                                stopHeartbeat()
                                currentLockHandle = null
                            }
                        },
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ExecuteHarnessActionOutcome.Failed(
                    ProcessRunResult.StartFailed("Harness 실행 프로세스를 안전하게 관찰하지 못했습니다."),
                )
            } finally {
                stopHeartbeat()
                currentLockHandle = null
                lastLockInspection = withContext(ioDispatcher) { inspectLock(context.project.id, context.day.date) }
            }

            if (capturedGeneration != loadContextGeneration) return@launch

            when (outcome) {
                is ExecuteHarnessActionOutcome.Completed -> {
                    // Use case는 own lock 보유 중 State를 이미 다시 읽었다. 아래 full reload는 strategy/artifact/UI
                    // projection을 갱신하기 위한 후속 표시 작업이며, own mtime 변화는 외부 실행으로 오인하지 않는다.
                    localStateRefreshPending = true
                    loadOnce(forceRead = true)
                    harnessRunView = harnessRunView.copy(
                        isRunning = false,
                        lastResult = outcome.processResult,
                        notice = null,
                    )
                }

                is ExecuteHarnessActionOutcome.Rejected -> {
                    harnessRunView = HarnessRunViewState(lastCommand = kind, notice = outcome.reason)
                }

                is ExecuteHarnessActionOutcome.Failed -> {
                    harnessRunView = HarnessRunViewState(lastCommand = kind, lastResult = outcome.processResult)
                }

                is ExecuteHarnessActionOutcome.LockUnavailable -> {
                    val lockResult = outcome.result
                    val notice = when (lockResult) {
                        is LockAcquireResult.Busy -> "다른 HRNS-NOW 실행이 이 프로젝트와 날짜의 잠금을 보유 중입니다."
                        is LockAcquireResult.Failed -> "실행 잠금을 안전하게 획득하지 못했습니다: ${lockResult.reason}"
                        is LockAcquireResult.Acquired -> error("획득한 잠금은 LockUnavailable이 될 수 없습니다.")
                    }
                    harnessRunView = HarnessRunViewState(lastCommand = kind, notice = notice)
                }

                is ExecuteHarnessActionOutcome.UnsupportedAction -> {
                    harnessRunView = HarnessRunViewState(
                        lastCommand = kind,
                        notice = "현재 Phase에서 지원하지 않는 실행 action입니다.",
                    )
                }
            }
            refreshRunProjectionOnly()
        }
    }
    private suspend fun onLockForceReleaseRequested() {
        val project = activeProject ?: return
        val day = selectedDay?.workspaceDay ?: return
        withContext(ioDispatcher) { processLock.forceRelease(project.id, day.date) }
        loadOnce(forceRead = true)
    }

    /**
     * `REQUEST_INBOX.md`에만 쓴다 — `REQUEST_STRUCTURED.md`/`WORKFLOW_STATE.json`은 절대
     * 건드리지 않는다. lock/process와 무관한 파일 쓰기이므로 harness 실행 lock을 잡지 않는다.
     */
    private suspend fun onRequestEntrySubmitted(draft: RequestEntryDraft) {
        if (requestSaving) return
        val daySelection = selectedDay ?: return
        if (daySelection.isReadOnly) {
            requestSaveSucceeded = false
            requestInboxNotice = "과거 날짜는 읽기 전용입니다. 오늘 날짜에서만 요청을 저장할 수 있습니다."
            refreshTodayWorkProjectionOnly()
            return
        }
        if (!isRequestEditingAllowed()) {
            requestSaveSucceeded = false
            requestInboxNotice = "현재 상태에서는 요청을 저장할 수 없습니다. 상태를 새로고침한 뒤 허용된 다음 행동을 확인하세요."
            refreshTodayWorkProjectionOnly()
            return
        }
        val day = daySelection.workspaceDay

        requestSaving = true
        requestSaveSucceeded = false
        requestInboxNotice = null
        refreshTodayWorkProjectionOnly()

        val outcome = withContext(ioDispatcher) { saveRequest.invoke(day, draft) }
        requestSaving = false
        requestSaveSucceeded = outcome == SaveRequestOutcome.Saved
        requestInboxNotice = when (outcome) {
            SaveRequestOutcome.Saved -> "요청을 저장했습니다."
            SaveRequestOutcome.InboxUnavailable -> "요청 입력 파일을 찾을 수 없습니다. 먼저 '오늘 준비'를 실행하세요."
            SaveRequestOutcome.Conflict -> "다른 곳에서 파일이 변경되어 저장하지 못했습니다. 초안은 유지됩니다. 다시 시도하면 최신 내용을 다시 읽으며, 필요하면 REQUEST_INBOX.md를 확인해 수동 병합하세요."
            is SaveRequestOutcome.Failed -> "요청을 저장하지 못했습니다: ${outcome.reason}"
        }
        refreshTodayWorkProjectionOnly()
    }

    /** 화면의 버튼 상태와 같은 typed CTA 정책을 저장 직전에도 다시 확인한다. */
    private fun isRequestEditingAllowed(): Boolean {
        val ready = _state.value as? HrnsUiState.Ready ?: return false
        return !ready.cockpit.isReadOnlyDay && isActionAllowed(UiAction.EditRequest)
    }

    /** typed CTA 정책상 허용되고 현재 활성화된 action만 실행/저장 경계까지 통과한다. */
    private fun isActionAllowed(action: UiAction): Boolean {
        val ready = _state.value as? HrnsUiState.Ready ?: return false
        return listOfNotNull(ready.cockpit.primaryAction)
            .plus(ready.cockpit.allowedActions)
            .any { item -> item.action == action && item.enabled }
    }

    private fun isHarnessRunAllowed(action: UiAction): Boolean = isActionAllowed(action)

    private suspend fun inspectLock(projectId: ProjectId?, date: LocalDate): LockInspection? {
        if (projectId == null) return null
        return processLock.inspect(projectId, date)
    }

    private fun startHeartbeat(handle: LockHandle) {
        lockHeartbeatJob = viewModelScope.launch {
            while (true) {
                delay(lockHeartbeatIntervalMillis)
                val updated = withContext(ioDispatcher) { processLock.heartbeat(handle) }
                if (!updated) break
            }
        }
    }

    private fun stopHeartbeat() {
        lockHeartbeatJob?.cancel()
        lockHeartbeatJob = null
    }

    /** run/lock 표시만 즉시 갱신한다 — 전체 `loadOnce`를 다시 돌지 않는다. */
    private fun refreshRunProjectionOnly() {
        val current = _state.value as? HrnsUiState.Ready ?: return
        val runEnabled = !harnessRunView.isRunning && !externalExecutionSuspected
        val updatedCockpit = current.cockpit.copy(
            primaryAction = current.cockpit.primaryAction?.let { item -> item.withRunEnabled(runEnabled) },
            allowedActions = current.cockpit.allowedActions.map { item -> item.withRunEnabled(runEnabled) },
        )
        val now = clock()
        _state.value = current.copy(
            runStatus = runStatusAssembler.assemble(
                runView = harnessRunView,
                lockInspection = lastLockInspection,
                now = now,
                externalExecutionSuspected = externalExecutionSuspected,
            ),
            cockpit = updatedCockpit,
            todayWork = buildTodayWorkProjection(
                updatedCockpit,
                strategyText,
                requestInboxNotice,
                requestSaving,
                requestSaveSucceeded,
                lockSummaryLabel(lastLockInspection, now),
            ),
        )
    }

    /** `todayWork`만 다시 조립한다 — 요청 저장 notice처럼 cockpit 재조회 없이 바뀌는 값 전용이다. */
    private fun refreshTodayWorkProjectionOnly() {
        val current = _state.value as? HrnsUiState.Ready ?: return
        _state.value = current.copy(
            todayWork = buildTodayWorkProjection(
                current.cockpit,
                strategyText,
                requestInboxNotice,
                requestSaving,
                requestSaveSucceeded,
                lockSummaryLabel(lastLockInspection, clock()),
            ),
        )
    }

    private fun CockpitActionItem.withRunEnabled(runEnabled: Boolean) =
        if (action in MUTATING_RUN_ACTIONS) copy(enabled = runEnabled) else this

    /** 테스트나 lifecycle owner가 없는 host에서 polling과 내부 scope를 명시적으로 취소한다. */
    fun dispose() {
        pollingJob?.cancel()
        pollingJob = null
        harnessRunJob?.cancel()
        harnessRunJob = null
        lockHeartbeatJob?.cancel()
        lockHeartbeatJob = null
        viewModelScope.cancel()
    }

    private fun formatInstant(instant: Instant): String =
        DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(instant)

    private companion object {
        /** lock/process 하나를 공유하는 harness 실행 action — 실행 중에는 모두 함께 비활성화한다. */
        val MUTATING_RUN_ACTIONS = setOf(
            UiAction.RunDoctor,
            UiAction.RunOpsValidation,
            UiAction.BootstrapDay,
            UiAction.RunPlanning,
            UiAction.RunReplan,
            UiAction.RunCodeSlice,
            UiAction.RunDocSlice,
        )
    }
}
