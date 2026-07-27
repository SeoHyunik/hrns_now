package io.hrns_now.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.hrns_now.app.presentation.mapper.CockpitUiStateAssembler
import io.hrns_now.app.presentation.mapper.RunStatusProjectionAssembler
import io.hrns_now.app.presentation.model.CockpitActionItem
import io.hrns_now.app.presentation.model.HrnsUiEvent
import io.hrns_now.app.presentation.model.HrnsUiState
import io.hrns_now.core.config.WorkspaceConfig
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
import io.hrns_now.core.domain.model.RootPathCheck
import io.hrns_now.core.domain.model.UiAction
import io.hrns_now.core.domain.model.WorkspaceDay
import io.hrns_now.core.domain.policy.BoundaryPolicy
import io.hrns_now.core.domain.policy.CompatibilityPolicy
import io.hrns_now.core.domain.policy.ExternalExecutionDetectionPolicy
import io.hrns_now.core.domain.policy.WorkspaceDaySelection
import io.hrns_now.core.port.HarnessRunnerPort
import io.hrns_now.core.port.KitVersionManifestPort
import io.hrns_now.core.port.LockInspection
import io.hrns_now.core.port.ProcessLockPort
import io.hrns_now.core.result.RegistryLoadResult
import io.hrns_now.core.result.RegistrySaveResult
import io.hrns_now.core.result.ProcessRunResult
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
    private val harnessRunner: HarnessRunnerPort,
    private val processLock: ProcessLockPort,
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

    private var harnessRunView: HarnessRunViewState = HarnessRunViewState()
    private var harnessRunJob: Job? = null
    private var harnessRunCancellationToken: ProcessCancellationToken? = null
    private var currentLockHandle: LockHandle? = null
    private var lockHeartbeatJob: Job? = null
    private var lastLockInspection: LockInspection? = null
    private var externalExecutionSuspected: Boolean = false
    private var localStateRefreshPending: Boolean = false

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
                UiAction.RunDoctor -> onHarnessRunRequested(HarnessCommandKind.Doctor)
                UiAction.RunOpsValidation -> onHarnessRunRequested(HarnessCommandKind.ValidateOps)
                else -> Unit
            }
            is HrnsUiEvent.ProjectSelected -> viewModelScope.launch { onProjectSelected(event.id) }
            is HrnsUiEvent.ProjectRegistrationRequested ->
                viewModelScope.launch { onProjectRegistrationRequested(event.candidate) }
            is HrnsUiEvent.ProjectDeletionRequested -> viewModelScope.launch { onProjectDeletionRequested(event.id) }
            is HrnsUiEvent.WorkspaceDaySelected -> viewModelScope.launch { onWorkspaceDaySelected(event.date) }
            HrnsUiEvent.HarnessRunCancelRequested -> harnessRunCancellationToken?.requestCancel()
            HrnsUiEvent.LockForceReleaseRequested -> viewModelScope.launch { onLockForceReleaseRequested() }
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
        // Own Doctor/ValidateOps completion triggers exactly one State reread. Its State change is not external.
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
     * 중복 클릭을 막고(이미 실행 중이면 무시), lock을 획득한 뒤에만 [harnessRunner]를 호출한다
     * (Phase 3). 실행 종료 뒤에는 lock을 보유한 채 State를 다시 읽고, heartbeat를 멈춘 뒤
     * lock을 해제·재검사한다. stdout 성공 문구가 아니라 재읽은 State가 완료 판단의
     * 근거로 남게 하기 위함이다. project/day가 실행 도중 바뀌면
     * [loadContextGeneration] 비교로 late-write를 막는다.
     */
    private fun onHarnessRunRequested(kind: HarnessCommandKind) {
        if (harnessRunView.isRunning) return
        if (externalExecutionSuspected) {
            harnessRunView = HarnessRunViewState(
                lastCommand = kind,
                notice = "WORKFLOW_STATE.json의 외부 변경이 감지되어 새 진단 실행을 보류했습니다. 새로고침으로 다시 확인하세요.",
            )
            refreshRunProjectionOnly()
            return
        }
        val project = activeProject ?: return
        val day = selectedDay?.workspaceDay ?: return

        val capturedGeneration = loadContextGeneration
        val cancellationToken = ProcessCancellationToken()
        harnessRunCancellationToken = cancellationToken
        harnessRunView = HarnessRunViewState(lastCommand = kind, isRunning = true, runStartedAt = clock(), lastResult = null)
        refreshRunProjectionOnly()

        harnessRunJob = viewModelScope.launch {
            val command = buildHarnessCommand(kind, project, day)
            val lockResult = withContext(ioDispatcher) { processLock.acquire(project.id, day.date, kind) }
            val acquired = lockResult as? LockAcquireResult.Acquired
            if (acquired == null) {
                if (capturedGeneration == loadContextGeneration) {
                    lastLockInspection = withContext(ioDispatcher) { inspectLock(project.id, day.date) }
                    val notice = when (lockResult) {
                        is LockAcquireResult.Busy -> "다른 HRNS-NOW 실행이 이 프로젝트와 날짜의 잠금을 보유 중입니다."
                        is LockAcquireResult.Failed -> "잠금을 안전하게 획득하지 못해 실행을 시작하지 않았습니다."
                        is LockAcquireResult.Acquired -> error("acquired result was already handled")
                    }
                    harnessRunView = harnessRunView.copy(isRunning = false, notice = notice)
                    refreshRunProjectionOnly()
                }
                return@launch
            }
            currentLockHandle = acquired.handle
            startHeartbeat(acquired.handle)

            val result = try {
                val executionResult = withContext(ioDispatcher) {
                    harnessRunner.execute(command, runTimeout, cancellationToken)
                }
                if (capturedGeneration == loadContextGeneration) {
                    // State 재읽기는 own lock을 가진 상태에서 수행한다. 실행 결과는 stdout이 아니라
                    // 이 State projection을 통해 해석되며 own mtime 변화는 외부 실행으로 오인하지 않는다.
                    localStateRefreshPending = true
                    loadOnce(forceRead = true)
                }
                executionResult
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ProcessRunResult.StartFailed("진단 프로세스를 안전하게 관찰하지 못했습니다.")
            } finally {
                stopHeartbeat()
                withContext(ioDispatcher) { processLock.release(acquired.handle) }
                currentLockHandle = null
                lastLockInspection = withContext(ioDispatcher) { inspectLock(project.id, day.date) }
            }

            if (capturedGeneration != loadContextGeneration) return@launch

            harnessRunView = harnessRunView.copy(isRunning = false, lastResult = result, notice = null)
            refreshRunProjectionOnly()
        }
    }

    private suspend fun onLockForceReleaseRequested() {
        val project = activeProject ?: return
        val day = selectedDay?.workspaceDay ?: return
        withContext(ioDispatcher) { processLock.forceRelease(project.id, day.date) }
        loadOnce(forceRead = true)
    }

    private fun buildHarnessCommand(kind: HarnessCommandKind, project: HarnessProject, day: WorkspaceDay): HarnessCommand =
        when (kind) {
            HarnessCommandKind.Doctor -> HarnessCommand.Doctor(
                kitRoot = project.kitRoot,
                workspaceRoot = project.projectWorkspaceRoot,
                projectRoot = project.repositoryRoot,
                date = day.date,
            )

            HarnessCommandKind.ValidateOps -> HarnessCommand.ValidateOps(
                workspaceRoot = project.projectWorkspaceRoot,
                kitRoot = project.kitRoot,
                profile = project.profileId,
                date = day.date,
            )
        }

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
        _state.value = current.copy(
            runStatus = runStatusAssembler.assemble(
                runView = harnessRunView,
                lockInspection = lastLockInspection,
                now = clock(),
                externalExecutionSuspected = externalExecutionSuspected,
            ),
            cockpit = current.cockpit.copy(
                primaryAction = current.cockpit.primaryAction?.let { item -> item.withRunEnabled(runEnabled) },
                allowedActions = current.cockpit.allowedActions.map { item -> item.withRunEnabled(runEnabled) },
            ),
        )
    }

    private fun CockpitActionItem.withRunEnabled(runEnabled: Boolean) =
        if (action == UiAction.RunDoctor || action == UiAction.RunOpsValidation) copy(enabled = runEnabled) else this

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
}
