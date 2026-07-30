package io.hrns_now.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.hrns_now.app.presentation.buildRecoveryProjection
import io.hrns_now.app.presentation.buildSetupProjection
import io.hrns_now.app.presentation.buildTodayWorkProjection
import io.hrns_now.app.presentation.mapper.CockpitUiStateAssembler
import io.hrns_now.app.presentation.mapper.RunStatusProjectionAssembler
import io.hrns_now.app.presentation.mapper.displayLabel
import io.hrns_now.app.presentation.mapper.lockSummaryLabel
import io.hrns_now.app.presentation.mapper.toDisplayText
import io.hrns_now.app.presentation.model.CockpitActionItem
import io.hrns_now.app.presentation.model.HrnsUiEvent
import io.hrns_now.app.presentation.model.HrnsUiState
import io.hrns_now.app.presentation.model.NotificationItem
import io.hrns_now.app.presentation.model.NotificationTone
import io.hrns_now.app.presentation.model.RegistrationFeedback
import io.hrns_now.app.presentation.model.WorkspacePreparationOutcome
import io.hrns_now.app.presentation.NotificationCenter
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
import io.hrns_now.core.domain.model.RepositoryStatus
import io.hrns_now.core.domain.model.RecoveryDiagnostics
import io.hrns_now.core.domain.model.RootPathCheck
import io.hrns_now.core.domain.model.AppLocale
import io.hrns_now.core.domain.model.RuntimeResolution
import io.hrns_now.core.domain.model.RuntimeSource
import io.hrns_now.core.domain.model.StateInvalidKind
import io.hrns_now.core.domain.model.UiAction
import io.hrns_now.core.domain.model.WorkspaceDay
import io.hrns_now.core.domain.model.SelectedDayKind
import io.hrns_now.core.domain.model.toActiveSliceKind
import io.hrns_now.core.domain.model.toCompatibilityStatus
import io.hrns_now.core.domain.policy.BoundaryPolicy
import io.hrns_now.core.domain.policy.ClosureBlockReasonKey
import io.hrns_now.core.domain.policy.ClosureContext
import io.hrns_now.core.domain.policy.ClosureDecision
import io.hrns_now.core.domain.policy.ClosurePolicy
import io.hrns_now.core.domain.policy.CompatibilityPolicy
import io.hrns_now.core.domain.policy.ExternalExecutionDetectionPolicy
import io.hrns_now.core.domain.policy.WorkspaceDaySelection
import io.hrns_now.core.port.GitStatusPort
import io.hrns_now.core.port.RecoveryDiagnosticsPort
import io.hrns_now.core.port.HarnessRunnerPort
import io.hrns_now.core.port.KitVersionManifestPort
import io.hrns_now.core.port.LockInspection
import io.hrns_now.core.port.ProcessLockPort
import io.hrns_now.core.port.RuntimeSourceResolverPort
import io.hrns_now.core.port.TodayStrategyReaderPort
import io.hrns_now.core.port.UiPreferencesPort
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
import io.hrns_now.core.usecase.ClearActiveProjectUseCase
import io.hrns_now.core.usecase.DeleteProjectUseCase
import io.hrns_now.core.usecase.LoadCockpitUseCase
import io.hrns_now.core.usecase.LoadProjectsUseCase
import io.hrns_now.core.usecase.RegisterProjectCandidate
import io.hrns_now.core.usecase.ProjectRegistrationInspection
import io.hrns_now.core.usecase.RegisterProjectResult
import io.hrns_now.core.usecase.RegisterProjectUseCase
import io.hrns_now.core.usecase.RegistrationRejectionReason
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
    private val clearActiveProject: ClearActiveProjectUseCase,
    private val boundaryPathResolver: (String?) -> RootPathCheck,
    private val compatibilityPort: KitVersionManifestPort,
    private val runtimeSourceResolver: RuntimeSourceResolverPort,
    private val processLock: ProcessLockPort,
    /** 프로젝트 등록 전 Doctor gate는 아직 selected ActionContext가 없으므로 별도 진단 경로로 남긴다. */
    private val harnessRunner: HarnessRunnerPort,
    private val executeHarnessAction: ExecuteHarnessActionUseCase,
    private val saveRequest: SaveRequestUseCase,
    private val todayStrategyReader: TodayStrategyReaderPort,
    private val gitStatusPort: GitStatusPort,
    private val uiPreferencesPort: UiPreferencesPort = object : UiPreferencesPort {
        override fun readLocale(): AppLocale? = null
        override fun writeLocale(locale: AppLocale) = Unit
    },
    private val recoveryDiagnosticsPort: RecoveryDiagnosticsPort = RecoveryDiagnosticsPort { RecoveryDiagnostics.Empty },
    private val boundaryPolicy: BoundaryPolicy = BoundaryPolicy(),
    private val compatibilityPolicy: CompatibilityPolicy = CompatibilityPolicy(),
    private val closurePolicy: ClosurePolicy = ClosurePolicy(),
    private val externalExecutionDetectionPolicy: ExternalExecutionDetectionPolicy = ExternalExecutionDetectionPolicy(),
    private val uiStateAssembler: CockpitUiStateAssembler = CockpitUiStateAssembler(),
    private val runStatusAssembler: RunStatusProjectionAssembler = RunStatusProjectionAssembler(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val pollIntervalMillis: Long = 3000L,
    private val runTimeout: Duration = Duration.ofSeconds(90),
    private val lockHeartbeatIntervalMillis: Long = 10_000L,
    private val clock: () -> Instant = Instant::now,
    private val notificationCenter: NotificationCenter = NotificationCenter(clock = clock),
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel(CoroutineScope(SupervisorJob() + mainDispatcher)) {

    private val _state = MutableStateFlow<HrnsUiState>(HrnsUiState.Loading)
    val state: StateFlow<HrnsUiState> = _state.asStateFlow()

    /** 전역 알림함이다(새 Phase 8 §4.2) — `HrnsUiState`와 별도 StateFlow로 두어 Shell이 독립 구독한다. */
    val notifications: StateFlow<List<NotificationItem>> = notificationCenter.items

    fun dismissNotification(id: String) = notificationCenter.dismiss(id)

    fun markNotificationRead(id: String) = notificationCenter.markRead(id)

    fun markAllNotificationsRead() = notificationCenter.markAllRead()

    /**
     * 화면 표시 언어다(새 Phase 8 §7) — `WORKFLOW_STATE.json`/Harness workspace/project Registry와
     * 무관한 UI 소유 값이며 [uiPreferencesPort]에만 저장한다.
     */
    private val _locale = MutableStateFlow(AppLocale.Korean)
    val locale: StateFlow<AppLocale> = _locale.asStateFlow()

    /** 현재 선택된 화면 언어다 — registryMessage/알림/실행 notice 조립에 쓴다(Phase 8 보완 §1). */
    private val currentLocale: AppLocale
        get() = _locale.value

    fun setLocale(locale: AppLocale) {
        _locale.value = locale
        viewModelScope.launch { withContext(ioDispatcher) { uiPreferencesPort.writeLocale(locale) } }
    }

    private var hasResolvedActiveProject = false
    private var currentWorkspaceConfig: WorkspaceConfig? = null
    private var activeProject: HarnessProject? = null
    private var activeSource: ActiveProjectSource = ActiveProjectSource.NoneSelected
    private var registryProjects: List<HarnessProject> = emptyList()
    private var registryMessage: String? = null
    private var registrationFeedback: RegistrationFeedback = RegistrationFeedback.Idle

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
    private var closureDecision: ClosureDecision =
        ClosureDecision.Blocked(listOf(ClosureBlockReasonKey.StateInvalid(StateInvalidKind.NotYetRead)))
    private var lastStateRead: StateReadResult? = null
    /**
     * 마지막 재조회에서 실제 filesystem probe가 확인한 daily required 4-file 상태다. Bootstrap
     * 완료 표시는 WORKFLOW_STATE.json 파싱 성공만이 아니라 이 값도 함께 충족해야 한다.
     */
    private var lastRequiredArtifactsReady: Boolean = false
    private var recoveryDiagnostics: RecoveryDiagnostics = RecoveryDiagnostics.Empty

    init {
        viewModelScope.launch {
            _locale.value = withContext(ioDispatcher) { uiPreferencesPort.readLocale() } ?: AppLocale.Korean
        }
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
                UiAction.RunClosureValidation ->
                    onClosureValidationRequested(incompleteHandoffAcknowledged = false)
                // 과거 날짜 fallback 화면에서 "오늘로 이동"은 순수 navigation이 아니다 — 실제로 오늘
                // 날짜를 선택해야 한다(새 Phase 8 §2.3). 오늘 폴더가 아직 없어도 항상 허용한다.
                UiAction.OpenToday -> viewModelScope.launch { onWorkspaceDaySelected(todayLocalDate()) }
                else -> Unit
            }
            is HrnsUiEvent.ClosureValidationRequested ->
                onClosureValidationRequested(event.incompleteHandoffAcknowledged)
            is HrnsUiEvent.ProjectSelected -> viewModelScope.launch { onProjectSelected(event.id) }
            is HrnsUiEvent.ProjectRegistrationRequested ->
                viewModelScope.launch { onProjectRegistrationRequested(event.candidate, event.prepareWorkspace) }
            HrnsUiEvent.RegistrationFeedbackDismissed -> pushRegistrationFeedback(RegistrationFeedback.Idle)
            is HrnsUiEvent.ProjectDeletionRequested -> viewModelScope.launch { onProjectDeletionRequested(event.id) }
            HrnsUiEvent.ActiveProjectReleaseRequested -> viewModelScope.launch { onActiveProjectReleaseRequested() }
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
                refreshRegistryProjects(projectSelectedMessage(result.project.displayName, currentLocale))
            }
            SelectProjectResult.NotFound ->
                refreshRegistryProjects(projectNotFoundMessage(currentLocale))
            is SelectProjectResult.SaveFailed ->
                refreshRegistryProjects(projectSelectSaveFailedMessage(result.message, currentLocale))
        }
        loadOnce(forceRead = true)
    }

    /**
     * 신규 프로젝트는 Registry에 쓰기 전에 경계 검사 → Doctor → compatibility 순서로 검증한다.
     * Doctor warn(GPU capability 등)은 결과 카드로 보존하되, fail/계약 미파싱/호환성 불일치는
     * fail-closed로 Registry 저장을 막는다.
     */
    private suspend fun onProjectRegistrationRequested(
        candidate: RegisterProjectCandidate,
        prepareWorkspace: Boolean,
    ) {
        pushRegistrationFeedback(RegistrationFeedback.Running)
        val inspection = withContext(ioDispatcher) { registerProject.inspect(candidate) }
        val prepared = when (inspection) {
            is ProjectRegistrationInspection.Ready -> inspection
            is ProjectRegistrationInspection.InvalidCandidate -> {
                registryMessage = registrationInvalidCandidateRegistryMessage(inspection.reason, currentLocale)
                val feedback = RegistrationFeedback.Failure(
                    whatHappened = registrationWhatHappenedText(inspection.reason, currentLocale),
                    nextStep = registrationNextStepGuidance(inspection.reason, currentLocale),
                    showAdvancedSettingsHint = inspection.reason.suggestsAdvancedSettings(),
                )
                registrationFeedback = feedback
                notifyRegistrationFailure()
                loadOnce(forceRead = true)
                return
            }
            is ProjectRegistrationInspection.BoundaryRejected -> {
                val violationCount = inspection.boundary.violations.size
                registryMessage = registrationBoundaryRejectedRegistryMessage(violationCount, currentLocale)
                val feedback = RegistrationFeedback.Failure(
                    whatHappened = registrationBoundaryRejectedWhatHappened(violationCount, currentLocale),
                    nextStep = registrationBoundaryRejectedNextStep(currentLocale),
                )
                registrationFeedback = feedback
                notifyRegistrationFailure()
                loadOnce(forceRead = true)
                return
            }
        }
        val project = prepared.project
        val resolvedKitRoot = prepared.resolvedKitRoot
        val date = todayLocalDate()
        val command = HarnessCommand.Doctor(
            kitRoot = resolvedKitRoot,
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
                notice = onboardingLockFailedNotice(currentLocale),
            )
            registrationFeedback = RegistrationFeedback.Failure(
                whatHappened = onboardingLockFailedWhatHappened(currentLocale),
                nextStep = onboardingLockFailedNextStep(currentLocale),
            )
            refreshRunProjectionOnly()
            pushRegistrationFeedback(registrationFeedback)
            notifyRegistrationFailure()
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
            ProcessRunResult.StartFailed(onboardingProcessObserveFailed(currentLocale))
        } finally {
            stopHeartbeat()
            withContext(ioDispatcher) { processLock.release(acquired.handle) }
            currentLockHandle = null
            lastLockInspection = null
        }
        harnessRunView = harnessRunView.copy(isRunning = false, runCompletedAt = clock(), lastResult = result, notice = null)
        refreshRunProjectionOnly()

        val compatibility = withContext(ioDispatcher) {
            compatibilityPolicy.evaluate(compatibilityPort.readManifest(resolvedKitRoot))
        }
        if (!doctorAllowsRegistration(result)) {
            registryMessage = doctorFailedRegistryMessage(currentLocale)
            val feedback = RegistrationFeedback.Failure(
                whatHappened = doctorFailedWhatHappened(currentLocale),
                nextStep = doctorFailedNextStep(currentLocale),
            )
            registrationFeedback = feedback
            notifyRegistrationFailure()
            loadOnce(forceRead = true)
            return
        }
        if (!compatibilityAllowsRegistration(compatibility)) {
            registryMessage = compatibilityFailedRegistryMessage(currentLocale)
            val feedback = RegistrationFeedback.Failure(
                whatHappened = compatibilityFailedWhatHappened(currentLocale),
                nextStep = compatibilityFailedNextStep(currentLocale),
            )
            registrationFeedback = feedback
            notifyRegistrationFailure()
            loadOnce(forceRead = true)
            return
        }

        when (val result = withContext(ioDispatcher) { registerProject.save(prepared) }) {
            is RegisterProjectResult.Registered -> {
                notificationCenter.push(projectRegisteredNotification(result.project.displayName, currentLocale), NotificationTone.Success)
                when (val selected = withContext(ioDispatcher) { selectProject(result.project.id) }) {
                    is SelectProjectResult.Selected -> {
                        applyActiveProject(selected.project)
                        refreshRegistryProjects(projectRegisteredRefreshMessage(selected.project.displayName, currentLocale))
                        // §B: 등록·선택·context 재조회까지 끝난 뒤에만 오늘 workspace 준비 여부를
                        // 판단한다 — ActionPolicy가 최신 재조회 결과로 다시 계산한 뒤여야 한다.
                        loadOnce(forceRead = true)
                        val preparation = if (prepareWorkspace) {
                            pushRegistrationFeedback(
                                RegistrationFeedback.Success(result.project.displayName, WorkspacePreparationOutcome.InProgress),
                            )
                            attemptWorkspacePreparationAfterRegistration()
                        } else {
                            WorkspacePreparationOutcome.NotAttempted
                        }
                        registrationFeedback = RegistrationFeedback.Success(result.project.displayName, preparation)
                        pushRegistrationFeedback(registrationFeedback)
                        return
                    }
                    SelectProjectResult.NotFound -> {
                        registrationFeedback = RegistrationFeedback.Success(result.project.displayName)
                        refreshRegistryProjects(projectRegisteredButNotFoundMessage(currentLocale))
                    }
                    is SelectProjectResult.SaveFailed -> {
                        registrationFeedback = RegistrationFeedback.Success(result.project.displayName)
                        refreshRegistryProjects(projectRegisteredButSelectSaveFailedMessage(selected.message, currentLocale))
                    }
                }
            }
            is RegisterProjectResult.SaveFailed -> {
                registryMessage = registrySaveFailedRegistryMessage(result.message, currentLocale)
                val feedback = RegistrationFeedback.Failure(
                    whatHappened = registrySaveFailedWhatHappened(result.message, currentLocale),
                    nextStep = registrySaveFailedNextStep(currentLocale),
                )
                registrationFeedback = feedback
                notifyRegistrationFailure()
            }
            is RegisterProjectResult.InvalidCandidate,
            is RegisterProjectResult.BoundaryRejected,
            -> error("검증된 후보를 저장할 때는 발생할 수 없는 결과입니다.")
        }
        loadOnce(forceRead = true)
    }

    private fun RegistrationRejectionReason.suggestsAdvancedSettings(): Boolean =
        when (this) {
            is RegistrationRejectionReason.RuntimeMissing -> source == RuntimeSource.InternalDeveloperSdk
            is RegistrationRejectionReason.RuntimeInvalid -> source == RuntimeSource.InternalDeveloperSdk
            RegistrationRejectionReason.BlankDisplayName,
            RegistrationRejectionReason.BlankProfile,
            RegistrationRejectionReason.BlankExternalKitPath,
            RegistrationRejectionReason.InvalidExternalKitPathFormat,
            -> false
        }

    private fun pushRegistrationFeedback(feedback: RegistrationFeedback) {
        registrationFeedback = feedback
        val current = _state.value as? HrnsUiState.Ready ?: return
        _state.value = current.copy(registrationFeedback = feedback)
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
                refreshRegistryProjects(projectDeletedMessage(currentLocale))
            }
            is RegistrySaveResult.Failed ->
                refreshRegistryProjects(projectDeleteFailedMessage(deleted.message, currentLocale))
        }
        loadOnce(forceRead = true)
    }

    private suspend fun onWorkspaceDaySelected(date: LocalDate) {
        // 오늘은 아직 daily directory가 없어도 항상 선택할 수 있다 — UI가 폴더/4-file을 만들지는
        // 않지만, 오늘 시작 흐름은 discovery 목록 존재 여부에 의존하면 안 된다(새 Phase 8 §6).
        if (date !in availableDates && date != todayLocalDate()) {
            registryMessage = dayFolderNotFoundMessage(currentLocale)
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
                    refreshRegistryProjects(daySelectedMessage(date, currentLocale))
                }
                is RegistrySaveResult.Failed ->
                    registryMessage = daySelectedButSaveFailedMessage(saved.message, currentLocale)
            }
        } else {
            registryMessage = daySelectedMessage(date, currentLocale)
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
        val rawWorkspaceConfig = requireNotNull(currentWorkspaceConfig)

        // 활성 프로젝트가 있을 때만 typed runtime source를 해석한다(새 Phase 7). 미선택/환경변수
        // fallback 경로(`activeProject == null`)는 이 resolver를 아예 거치지 않고 기존 legacy
        // `HRNS_KIT_ROOT` 동작을 그대로 유지한다.
        val runtimeResolution = activeProject?.let { project ->
            withContext(ioDispatcher) { runtimeSourceResolver.resolve(project.runtimeSource) }
        }
        val resolvedKitRoot = (runtimeResolution as? RuntimeResolution.Resolved)?.root
        val workspaceConfig = if (runtimeResolution != null) {
            rawWorkspaceConfig.copy(roots = rawWorkspaceConfig.roots.copy(kitRoot = resolvedKitRoot?.toString()))
        } else {
            rawWorkspaceConfig
        }

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
        val compatibilityDetail = withContext(ioDispatcher) { evaluateCompatibility(workspaceConfig, runtimeResolution) }
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
        val repositoryStatus = withContext(ioDispatcher) {
            activeProject?.repositoryRoot?.let(gitStatusPort::read) ?: RepositoryStatus.Unknown
        }
        val loadedRecoveryDiagnostics = withContext(ioDispatcher) {
            recoveryDiagnosticsPort.read(daySelection.workspaceDay)
        }
        if (sequence != loadSequence || contextGeneration != loadContextGeneration) return
        lastLockInspection = lockInspection
        lastStateRead = loaded.stateRead
        lastRequiredArtifactsReady = loaded.workspaceArtifactSummary.isRequiredReady
        recoveryDiagnostics = loadedRecoveryDiagnostics
        closureDecision = closurePolicy.evaluate(
            ClosureContext(
                stateRead = loaded.stateRead,
                lockHeld = lockInspection != null,
                repositoryStatus = repositoryStatus,
            ),
        )

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

        // resolvedKitRoot가 없으면(runtime source Missing/Invalid) 실행 context 자체를 만들지
        // 않는다 — command mapper는 resolved root 없이는 절대 호출되지 않는다(§20.1). 이 상태의
        // fail-closed 이유는 `compatibilityDetail`(MissingManifest로 강제)과 새 Phase 7
        // `runtimeSourceDiagnostics`가 화면에 표시한다.
        latestExecutionContext = activeProject
            ?.takeIf { loaded.projectConnected && resolvedKitRoot != null }
            ?.let { project ->
                val activeSliceKind = (loaded.stateRead as? StateReadResult.Success)
                    ?.state
                    ?.executionWrapper
                    ?.toActiveSliceKind()
                HarnessExecutionContext(
                    project = project,
                    resolvedKitRoot = requireNotNull(resolvedKitRoot),
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
                locale = currentLocale,
            ),
            harnessRunInProgress = harnessRunView.isRunning,
            strategyText = strategyText,
            requestInboxNotice = requestInboxNotice,
            requestSaving = requestSaving,
            requestSaveSucceeded = requestSaveSucceeded,
            lockSummaryLabel = lockSummaryLabel(lockInspection, now),
            closureDecision = closureDecision,
            recoveryDiagnostics = recoveryDiagnostics,
            runtimeResolution = runtimeResolution,
            today = todayLocalDate(),
            registrationFeedback = registrationFeedback,
            locale = currentLocale,
        )
    }

    private fun todayLocalDate(): LocalDate = clock().atZone(ZoneId.systemDefault()).toLocalDate()

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
            is RegistryLoadResult.RecoveredFromCorruption -> registryRecoveredFromCorruptionMessage(message, currentLocale)
            is RegistryLoadResult.Unreadable -> registryUnreadableMessage(message, currentLocale)
        }

    private fun evaluateBoundary(workspaceConfig: WorkspaceConfig): BoundaryStatus {
        val kit = boundaryPathResolver(workspaceConfig.roots.kitRoot)
        val workspace = boundaryPathResolver(workspaceConfig.roots.workspaceRoot)
        val repository = boundaryPathResolver(workspaceConfig.roots.projectRoot)
        return boundaryPolicy.evaluate(kit = kit, workspace = workspace, repository = repository).status
    }

    /**
     * 활성 프로젝트가 있으면(새 Phase 7) `runtimeResolution`이 이미 `RuntimeSourceResolverPort`로
     * 판정한 root만 쓴다 — `Missing`/`Invalid`는 compatibility를 아예 시도하지 않고
     * `MissingManifest`로 대체한다(별개의 fail-closed 원인, `runtimeSourceDiagnostics`가 화면에서
     * 실제 이유를 보여준다). 활성 프로젝트가 없으면(환경변수 fallback/미선택) 기존처럼
     * `workspaceConfig.roots.kitRoot` 원문을 그대로 읽는다 — `HRNS_KIT_ROOT` 등 legacy 경로는
     * 이 새 resolver로 바뀌지 않는다. `evaluateBoundary`와 마찬가지로 이 함수 전체가
     * [ioDispatcher] 안에서 호출된다.
     */
    private fun evaluateCompatibility(
        workspaceConfig: WorkspaceConfig,
        runtimeResolution: RuntimeResolution?,
    ): HarnessCompatibilityDetail {
        if (runtimeResolution == null) {
            val kitRoot = parseKitRoot(workspaceConfig.roots.kitRoot)
                ?: return compatibilityPolicy.evaluate(KitVersionReadResult.Missing)
            return compatibilityPolicy.evaluate(compatibilityPort.readManifest(kitRoot))
        }
        return when (runtimeResolution) {
            is RuntimeResolution.Resolved -> compatibilityPolicy.evaluate(compatibilityPort.readManifest(runtimeResolution.root))
            else -> HarnessCompatibilityDetail.MissingManifest
        }
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
    private fun onClosureValidationRequested(incompleteHandoffAcknowledged: Boolean) {
        when (closureDecision) {
            ClosureDecision.Allowed ->
                onHarnessRunRequested(UiAction.RunClosureValidation, HarnessCommandKind.ClosureValidation)

            is ClosureDecision.RequiresExplicitIncompleteHandoff -> {
                if (incompleteHandoffAcknowledged) {
                    onHarnessRunRequested(
                        UiAction.RunClosureValidation,
                        HarnessCommandKind.ClosureValidation,
                        closureAcknowledged = true,
                    )
                } else {
                    showClosureValidationBlockedNotice()
                }
            }

            is ClosureDecision.Blocked -> showClosureValidationBlockedNotice()
        }
    }

    private fun showClosureValidationBlockedNotice() {
        harnessRunView = HarnessRunViewState(
            lastCommand = HarnessCommandKind.ClosureValidation,
            notice = closureValidationBlockedNotice(currentLocale),
        )
        refreshRunProjectionOnly()
    }

    private fun onHarnessRunRequested(
        action: UiAction,
        kind: HarnessCommandKind,
        closureAcknowledged: Boolean = false,
    ) {
        if (harnessRunView.isRunning) return
        val allowedAfterExplicitAcknowledgement =
            action == UiAction.RunClosureValidation && closureAcknowledged
        if (!isHarnessRunAllowed(action) && !allowedAfterExplicitAcknowledgement) {
            harnessRunView = HarnessRunViewState(
                lastCommand = kind,
                notice = harnessRunNotAllowedNotice(currentLocale),
            )
            refreshRunProjectionOnly()
            return
        }
        if (externalExecutionSuspected) {
            harnessRunView = HarnessRunViewState(
                lastCommand = kind,
                notice = externalExecutionSuspectedNotice(currentLocale),
            )
            refreshRunProjectionOnly()
            return
        }
        // isRunning을 launch 이전에 동기적으로 세워야 중복 클릭 가드(맨 위 harnessRunView.isRunning
        // 확인)가 코루틴이 실제로 디스패치되기 전의 연속 클릭도 정확히 차단한다.
        if (latestExecutionContext == null) return
        harnessRunView = HarnessRunViewState(lastCommand = kind, isRunning = true, runStartedAt = clock(), lastResult = null)
        refreshRunProjectionOnly()
        harnessRunJob = viewModelScope.launch { runHarnessAction(action, kind) }
    }

    /**
     * 실제 Harness 명령 실행 → lock-held State reread → 화면/알림 갱신까지 수행하고 typed
     * outcome을 돌려준다. 사용자가 직접 요청한 실행은 [onHarnessRunRequested]가 pre-check 뒤
     * launch해서 호출하고, 등록 직후 자동 Bootstrap 시도([attemptWorkspacePreparationAfterRegistration])는
     * 이미 suspend 문맥이므로 이 함수를 직접 await해 결과를 등록 feedback에 반영한다 — 등록
     * 화면용 별도 lock/process lifecycle을 복제하지 않는다(Phase 9 QA03-B).
     */
    private suspend fun runHarnessAction(action: UiAction, kind: HarnessCommandKind): ExecuteHarnessActionOutcome {
        val context = latestExecutionContext
        if (context == null) {
            val outcome = ExecuteHarnessActionOutcome.UnsupportedAction(action)
            harnessRunView = HarnessRunViewState(lastCommand = kind, notice = unsupportedActionNotice(currentLocale))
            refreshRunProjectionOnly()
            return outcome
        }
        val capturedGeneration = loadContextGeneration
        val cancellationToken = ProcessCancellationToken()
        harnessRunCancellationToken = cancellationToken
        harnessRunView = HarnessRunViewState(lastCommand = kind, isRunning = true, runStartedAt = clock(), lastResult = null)
        refreshRunProjectionOnly()

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
                ProcessRunResult.StartFailed(harnessRunObserveFailedNotice(currentLocale)),
            )
        } finally {
            stopHeartbeat()
            currentLockHandle = null
            lastLockInspection = withContext(ioDispatcher) { inspectLock(context.project.id, context.day.date) }
        }

        if (capturedGeneration != loadContextGeneration) return outcome

        when (outcome) {
            is ExecuteHarnessActionOutcome.Completed -> {
                // Use case는 own lock 보유 중 State를 이미 다시 읽었다. 아래 full reload는 strategy/artifact/UI
                // projection을 갱신하기 위한 후속 표시 작업이며, own mtime 변화는 외부 실행으로 오인하지 않는다.
                localStateRefreshPending = true
                loadOnce(forceRead = true)
                harnessRunView = harnessRunView.copy(
                    isRunning = false,
                    runCompletedAt = clock(),
                    lastResult = outcome.processResult,
                    notice = null,
                )
            }

            is ExecuteHarnessActionOutcome.Rejected -> {
                val notice = outcome.reasonKey?.toDisplayText(currentLocale) ?: rejectedFallbackNotice(currentLocale)
                harnessRunView = HarnessRunViewState(lastCommand = kind, notice = notice)
            }

            is ExecuteHarnessActionOutcome.Failed -> {
                harnessRunView = HarnessRunViewState(
                    lastCommand = kind,
                    runCompletedAt = clock(),
                    lastResult = outcome.processResult,
                )
            }

            is ExecuteHarnessActionOutcome.LockUnavailable -> {
                val lockResult = outcome.result
                val notice = when (lockResult) {
                    is LockAcquireResult.Busy -> lockBusyNotice(currentLocale)
                    is LockAcquireResult.Failed -> lockFailedNotice(lockResult.reason, currentLocale)
                    is LockAcquireResult.Acquired -> error("획득한 잠금은 LockUnavailable이 될 수 없습니다.")
                }
                harnessRunView = HarnessRunViewState(lastCommand = kind, notice = notice)
            }

            is ExecuteHarnessActionOutcome.UnsupportedAction -> {
                harnessRunView = HarnessRunViewState(
                    lastCommand = kind,
                    notice = unsupportedActionNotice(currentLocale),
                )
            }
        }
        refreshRunProjectionOnly()
        notifyRunOutcome(action, outcome)
        return outcome
    }

    /**
     * 등록+선택+context 재조회가 끝난 뒤, ActionPolicy가 실제로 `BootstrapDay`를 primary로 허용할
     * 때만 [runHarnessAction]을 그대로 재사용해 오늘 workspace를 준비한다(Phase 9 QA03-B §B).
     * 등록 자체의 성공 여부와 분리된 결과만 돌려주며, 이 결과가 Registry를 되돌리는 이유가 되지
     * 않는다. stdout 성공 문구가 아니라 재조회한 State로 준비 성공을 판단한다.
     */
    private suspend fun attemptWorkspacePreparationAfterRegistration(): WorkspacePreparationOutcome {
        val ready = _state.value as? HrnsUiState.Ready
        val eligible = ready?.todayWork?.bootstrapEligible == true
        if (!eligible) {
            return ready?.cockpit?.blockedReasonLabel
                ?.let { WorkspacePreparationOutcome.NotPrepared(it) }
                ?: WorkspacePreparationOutcome.NotAttempted
        }
        return when (val outcome = runHarnessAction(UiAction.BootstrapDay, HarnessCommandKind.Bootstrap)) {
            is ExecuteHarnessActionOutcome.Completed ->
                if (lastStateRead is StateReadResult.Success && lastRequiredArtifactsReady) {
                    WorkspacePreparationOutcome.Prepared
                } else {
                    WorkspacePreparationOutcome.NotPrepared(workspacePreparationNotConfirmedNotice(currentLocale))
                }
            is ExecuteHarnessActionOutcome.Rejected ->
                WorkspacePreparationOutcome.NotPrepared(
                    outcome.reasonKey?.toDisplayText(currentLocale) ?: rejectedFallbackNotice(currentLocale),
                )
            is ExecuteHarnessActionOutcome.Failed ->
                WorkspacePreparationOutcome.NotPrepared(workspacePreparationFailedNotice(currentLocale))
            is ExecuteHarnessActionOutcome.LockUnavailable -> {
                val lockResult = outcome.result
                val notice = when (lockResult) {
                    is LockAcquireResult.Busy -> lockBusyNotice(currentLocale)
                    is LockAcquireResult.Failed -> lockFailedNotice(lockResult.reason, currentLocale)
                    is LockAcquireResult.Acquired -> error("획득한 잠금은 LockUnavailable이 될 수 없습니다.")
                }
                WorkspacePreparationOutcome.NotPrepared(notice)
            }
            is ExecuteHarnessActionOutcome.UnsupportedAction ->
                WorkspacePreparationOutcome.NotPrepared(unsupportedActionNotice(currentLocale))
        }
    }

    /**
     * Registry의 활성 선택만 지운다(Phase 9 QA03-A) — 등록된 project entry, workspace, repository,
     * Harness Kit, State/daily 파일은 전혀 건드리지 않는다. 해제 직후 selected day/polling
     * context/active project projection을 [onProjectDeletionRequested]와 동일하게 안전 초기화하고
     * 재조회한다.
     */
    private suspend fun onActiveProjectReleaseRequested() {
        val project = activeProject ?: return
        when (val result = withContext(ioDispatcher) { clearActiveProject() }) {
            RegistrySaveResult.Success -> {
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
                refreshRegistryProjects(activeProjectReleasedMessage(project.displayName, currentLocale))
            }
            is RegistrySaveResult.Failed ->
                refreshRegistryProjects(activeProjectReleaseFailedMessage(result.message, currentLocale))
        }
        loadOnce(forceRead = true)
    }

    /**
     * 실행 결과를 전역 알림함에 남긴다(새 Phase 8 §4.2) — 등록/저장과 마찬가지로 사용자가
     * 결과를 기다린 action에만 쓴다. `Completed`/`Failed`는 이미 조립된 `RunStatusProjection.
     * lastOutcome`(= State reread가 아니라 `ProcessRunResult.contract.overall` 기반 typed 결과,
     * 기존 실행 기록 카드와 같은 신호)만 재사용하고, stdout 원문을 다시 파싱하지 않는다.
     * `Rejected`/`LockUnavailable`/`UnsupportedAction`은 이미 typed·안전한 `notice` 문구를 쓴다.
     */
    private fun notifyRunOutcome(action: UiAction, outcome: ExecuteHarnessActionOutcome) {
        val label = action.displayLabel(currentLocale)
        when (outcome) {
            is ExecuteHarnessActionOutcome.Completed -> {
                val outcomeChip = (_state.value as? HrnsUiState.Ready)?.runStatus?.lastOutcome
                val tone = when (outcomeChip?.tone) {
                    "success" -> NotificationTone.Success
                    "warning" -> NotificationTone.Info
                    else -> NotificationTone.Failure
                }
                notificationCenter.push(runNotificationMessage(label, tone), tone)
            }
            is ExecuteHarnessActionOutcome.Rejected ->
                notificationCenter.push(runRejectedNotification(label, currentLocale), NotificationTone.Failure)
            is ExecuteHarnessActionOutcome.Failed ->
                notificationCenter.push(runFailedNotification(label, currentLocale), NotificationTone.Failure)
            is ExecuteHarnessActionOutcome.LockUnavailable ->
                notificationCenter.push(runLockUnavailableNotification(label, currentLocale), NotificationTone.Failure)
            is ExecuteHarnessActionOutcome.UnsupportedAction ->
                notificationCenter.push(runUnsupportedNotification(label, currentLocale), NotificationTone.Failure)
        }
    }

    /** 알림함에는 원문 process 결과·경로·세션을 남기지 않고 typed outcome의 안전한 요약만 저장한다. */
    private fun runNotificationMessage(label: String, tone: NotificationTone): String =
        when (tone) {
            NotificationTone.Success -> runCompletedNotification(label, currentLocale)
            NotificationTone.Info -> runNeedsReviewNotification(label, currentLocale)
            NotificationTone.Failure -> runNotCompletedNotification(label, currentLocale)
        }

    /** 등록 modal의 상세 원인은 현재 입력 문맥에서만 보이고, 알림 이력에는 복제하지 않는다. */
    private fun notifyRegistrationFailure() {
        notificationCenter.push(registrationFailureNotification(currentLocale), NotificationTone.Failure)
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
            requestInboxNotice = pastDayReadOnlyNotice(currentLocale)
            refreshTodayWorkProjectionOnly()
            return
        }
        if (!isRequestEditingAllowed()) {
            requestSaveSucceeded = false
            requestInboxNotice = requestEditingNotAllowedNotice(currentLocale)
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
            SaveRequestOutcome.Saved -> requestSavedNotice(currentLocale)
            SaveRequestOutcome.InboxUnavailable -> requestInboxUnavailableNotice(currentLocale)
            SaveRequestOutcome.Conflict -> requestConflictNotice(currentLocale)
            is SaveRequestOutcome.Failed -> requestFailedNotice(outcome.reason, currentLocale)
        }
        notificationCenter.push(
            if (requestSaveSucceeded) requestSavedNotification(currentLocale) else requestNotSavedNotification(currentLocale),
            if (requestSaveSucceeded) NotificationTone.Success else NotificationTone.Failure,
        )
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
        val newLockSummaryLabel = lockSummaryLabel(lastLockInspection, now)
        _state.value = current.copy(
            runStatus = runStatusAssembler.assemble(
                runView = harnessRunView,
                lockInspection = lastLockInspection,
                now = now,
                externalExecutionSuspected = externalExecutionSuspected,
                locale = currentLocale,
            ),
            cockpit = updatedCockpit,
            // 실행 중에도 "프로젝트 관리" 화면의 연결 점검/작업 준비 점검 버튼이 즉시 비활성화되도록
            // setup도 다시 조립한다 — 그렇지 않으면 중복 클릭 방지가 다음 전체 loadOnce까지 지연된다.
            setup = buildSetupProjection(
                config = current.workspaceConfig,
                probeSummary = current.workspaceProbeSummary,
                diagnosticActions = updatedCockpit.allowedActions,
                locale = currentLocale,
            ),
            todayWork = buildTodayWorkProjection(
                updatedCockpit,
                strategyText,
                requestInboxNotice,
                requestSaving,
                requestSaveSucceeded,
                newLockSummaryLabel,
                locale = currentLocale,
            ),
            recovery = lastStateRead?.let { stateRead ->
                buildRecoveryProjection(
                    updatedCockpit,
                    stateRead,
                    closureDecision,
                    newLockSummaryLabel,
                    closureValidationEnabledAfterAcknowledgement =
                        current.recovery.closureValidationEnabledAfterAcknowledgement,
                    recoveryDiagnostics = recoveryDiagnostics,
                    locale = currentLocale,
                )
            } ?: current.recovery,
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
                locale = currentLocale,
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
            UiAction.RunClosureValidation,
        )
    }
}
