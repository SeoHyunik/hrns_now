package io.hrns_now.app.presentation.viewmodel

import io.hrns_now.app.presentation.model.HrnsUiEvent
import io.hrns_now.app.presentation.model.HrnsUiState
import io.hrns_now.core.config.PathProbeKind
import io.hrns_now.core.config.PathProbeResult
import io.hrns_now.core.config.PathProbeState
import io.hrns_now.core.config.RuntimeConfig
import io.hrns_now.core.config.WorkspaceConfig
import io.hrns_now.core.config.WorkspaceProbeSummary
import io.hrns_now.core.config.WorkspaceReadiness
import io.hrns_now.core.config.WorkspaceRoots
import io.hrns_now.core.domain.model.ArtifactReadinessState
import io.hrns_now.core.domain.model.ArtifactsState
import io.hrns_now.core.domain.model.ClosureState
import io.hrns_now.core.domain.model.ContractVersion
import io.hrns_now.core.domain.model.ExecutionWrapper
import io.hrns_now.core.domain.model.ExecutionWrapperState
import io.hrns_now.core.domain.model.FileVersion
import io.hrns_now.core.domain.model.HarnessCommand
import io.hrns_now.core.domain.model.HarnessCommandKind
import io.hrns_now.core.domain.model.HarnessProject
import io.hrns_now.core.domain.model.KitVersion
import io.hrns_now.core.domain.model.KitVersionManifest
import io.hrns_now.core.domain.model.KitVersionReadResult
import io.hrns_now.core.domain.model.LockAcquireResult
import io.hrns_now.core.domain.model.LockHandle
import io.hrns_now.core.domain.model.LockPayload
import io.hrns_now.core.domain.model.LockReleaseResult
import io.hrns_now.core.domain.model.LockState
import io.hrns_now.core.domain.model.OpsValidationState
import io.hrns_now.core.domain.model.PathIssue
import io.hrns_now.core.domain.model.ProjectId
import io.hrns_now.core.domain.model.QueuePointer
import io.hrns_now.core.domain.model.QueueStatus
import io.hrns_now.core.domain.model.ReplanReason
import io.hrns_now.core.domain.model.RequestEntryDraft
import io.hrns_now.core.domain.model.RequestEntryPriority
import io.hrns_now.core.domain.model.RequestEntrySource
import io.hrns_now.core.domain.model.RequestEntryType
import io.hrns_now.core.domain.model.RootPathCheck
import io.hrns_now.core.domain.model.SchemaVersion
import io.hrns_now.core.domain.model.UiAction
import io.hrns_now.core.domain.model.WorkflowPhase
import io.hrns_now.core.domain.model.WorkflowQueue
import io.hrns_now.core.domain.model.WorkflowState
import io.hrns_now.core.domain.model.WorkflowStatus
import io.hrns_now.core.domain.model.WorkspaceArtifactSummary
import io.hrns_now.core.domain.model.WorkspaceDay
import io.hrns_now.core.domain.policy.ActionPolicy
import io.hrns_now.core.domain.policy.WorkspaceDaySelectionPolicy
import io.hrns_now.core.port.HarnessRunnerPort
import io.hrns_now.core.port.KitVersionManifestPort
import io.hrns_now.core.port.LoadedRequest
import io.hrns_now.core.port.LockInspection
import io.hrns_now.core.port.ProcessLockPort
import io.hrns_now.core.port.ProjectRegistryPort
import io.hrns_now.core.port.RequestSaveResult
import io.hrns_now.core.port.RequestWriterPort
import io.hrns_now.core.domain.model.RepositoryStatus
import io.hrns_now.core.port.GitStatusPort
import io.hrns_now.core.port.TodayStrategyReaderPort
import io.hrns_now.core.port.WorkflowStatePort
import io.hrns_now.core.result.HarnessDiagnosticContract
import io.hrns_now.core.result.HarnessOverallStatus
import io.hrns_now.core.result.ProcessRunResult
import io.hrns_now.core.result.RegistryLoadResult
import io.hrns_now.core.result.RegistrySaveResult
import io.hrns_now.core.result.StateReadResult
import io.hrns_now.core.usecase.DeleteProjectUseCase
import io.hrns_now.core.usecase.ExecuteHarnessActionUseCase
import io.hrns_now.core.usecase.HarnessCommandMapper
import io.hrns_now.core.usecase.LoadCockpitUseCase
import io.hrns_now.core.usecase.LoadProjectsUseCase
import io.hrns_now.core.usecase.RegisterProjectCandidate
import io.hrns_now.core.usecase.RegisterProjectUseCase
import io.hrns_now.core.usecase.ResolveActiveProjectUseCase
import io.hrns_now.core.usecase.SaveRequestUseCase
import io.hrns_now.core.usecase.SelectProjectUseCase
import io.hrns_now.core.usecase.SelectWorkspaceDayUseCase
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    private val fixedInstant: Instant = Instant.parse("2026-06-26T12:00:00Z")

    private fun probeSummary(): WorkspaceProbeSummary {
        val notConfigured = { label: String, kind: PathProbeKind ->
            PathProbeResult(label, null, kind, PathProbeState.NotConfigured, "미설정")
        }
        return WorkspaceProbeSummary(
            kitRoot = notConfigured("KitRoot", PathProbeKind.Directory),
            workspaceRoot = PathProbeResult(
                "WorkspaceRoot",
                "S:\\workspace",
                PathProbeKind.Directory,
                PathProbeState.Exists,
                "읽기 가능",
            ),
            projectRoot = notConfigured("ProjectRoot", PathProbeKind.Directory),
            powerShellPath = notConfigured("PowerShell", PathProbeKind.Command),
            claudeCommand = notConfigured("Claude", PathProbeKind.Command),
        )
    }

    private fun readiness(): WorkspaceReadiness =
        WorkspaceReadiness("오프라인", "확인됨", "확인 필요", "테스트", "대기")

    private fun workspaceConfig(workspaceRoot: String? = "S:\\workspace"): WorkspaceConfig = WorkspaceConfig(
        workspaceName = null,
        profileName = "테스트",
        roots = WorkspaceRoots(kitRoot = null, workspaceRoot = workspaceRoot, projectRoot = null),
        runtime = RuntimeConfig(powerShellPath = null, claudeCommand = null, uiLanguage = "ko"),
    )

    private fun harnessProject(id: String, workspaceRoot: String): HarnessProject = HarnessProject(
        id = ProjectId(id),
        displayName = "project-$id",
        kitRoot = Path.of("S:\\kit-$id"),
        projectWorkspaceRoot = Path.of(workspaceRoot),
        repositoryRoot = Path.of("S:\\repo-$id"),
        profileId = "테스트",
        lastSelectedDate = null,
        lastDiagnosticsSummary = null,
        lastRunAt = null,
    )

    private fun workflowState(projectName: String): WorkflowState = WorkflowState(
        schemaVersion = SchemaVersion(1, 0, "1.0"),
        date = LocalDate.of(2026, 6, 26),
        projectName = projectName,
        workspaceRoot = "S:\\workspace",
        repoRoot = "S:\\repo",
        profile = "테스트",
        requiredNextAction = null,
        phase = WorkflowPhase.PlanningRequired,
        status = WorkflowStatus.PlanningRequired,
        nextAction = null,
        executionWrapper = ExecutionWrapperState.None,
        stopReason = null,
        blockedReason = null,
        failedReason = null,
        humanActionRequired = false,
        executionCompleted = false,
        closureValidated = false,
        cleanHandoff = false,
        resumeFromStepId = null,
        authorizedTargetFile = null,
        artifacts = ArtifactsState(
            ArtifactReadinessState.Ready,
            ArtifactReadinessState.Ready,
            ArtifactReadinessState.Ready,
            ArtifactReadinessState.Ready,
        ),
        opsValidation = OpsValidationState(true, null, null),
        closure = ClosureState(false, false, null, null),
        currentSliceRaw = null,
        sliceQueueRaw = null,
        roleSlicedRaw = null,
        usageGuardRaw = null,
        queue = WorkflowQueue(QueueStatus.PlanningRequired, QueuePointer(null, null), null, null),
    )

    private fun supportedManifest(): KitVersionManifest = KitVersionManifest(
        kitVersion = KitVersion("2026.07.23"),
        stateSchemaVersion = ContractVersion(1, 0, "1.0"),
        uiContractVersion = ContractVersion(1, 0, "1.0"),
    )

    private fun requestIntakeState(projectName: String = "request"): WorkflowState =
        workflowState(projectName).copy(status = WorkflowStatus.RequestIntakePending)

    private fun stateForAllowedHarnessAction(action: UiAction): WorkflowState =
        when (action) {
            UiAction.BootstrapDay -> workflowState("bootstrap").copy(
                phase = WorkflowPhase.ClosureValidated,
                status = WorkflowStatus.ExecutionCompleted,
                executionCompleted = true,
                closureValidated = true,
                closure = ClosureState(isCleanHandoff = true, validated = true, validatedAt = null, validatorNotes = null),
            )
            UiAction.RunPlanning -> workflowState("planning").copy(status = WorkflowStatus.PlanningRequired)
            UiAction.RunReplan -> workflowState("replan").copy(status = WorkflowStatus.PlanningFailed)
            UiAction.RunCodeSlice -> executionReadyState(ExecutionWrapperState.Code, "code-target")
            UiAction.RunDocSlice -> executionReadyState(ExecutionWrapperState.Doc, "doc-target")
            UiAction.RunClosureValidation -> workflowState("closure-validation").copy(
                phase = WorkflowPhase.ExecutionCompleted,
                status = WorkflowStatus.ExecutionCompleted,
                executionCompleted = true,
            )
            else -> error("Phase 4/5 harness action expected")
        }

    private fun executionReadyState(wrapper: ExecutionWrapperState, target: String): WorkflowState =
        workflowState("execution").copy(
            phase = WorkflowPhase.ExecutionReady,
            status = WorkflowStatus.ExecutionReady,
            executionWrapper = wrapper,
            authorizedTargetFile = "S:\\repo\\$target.md",
            queue = WorkflowQueue(QueueStatus.Active, QueuePointer("card-1", "slice-1"), null, null),
        )

    private fun validBoundary(raw: String?): RootPathCheck {
        val path = Path.of(requireNotNull(raw))
        return RootPathCheck.Valid(path, path)
    }

    private fun successfulDoctorResult(): ProcessRunResult.Completed = ProcessRunResult.Completed(
        exitCode = 0,
        contract = HarnessDiagnosticContract("1.0", HarnessOverallStatus.Ok, emptyList()),
        rawOutputSnippet = null,
        stdoutTruncated = false,
        stderrTruncated = false,
    )
    private class FakeStatePort(
        private val result: (callIndex: Int) -> StateReadResult,
    ) : WorkflowStatePort {
        val callCount = AtomicInteger(0)
        override fun read(day: WorkspaceDay): StateReadResult = result(callCount.incrementAndGet())
    }

    /** 항상 파일이 없는 것처럼 동작하는 [RequestWriterPort] 테스트 대역이다(Phase 4). */
    private class FakeRequestWriterPort : RequestWriterPort {
        override fun load(day: WorkspaceDay): LoadedRequest? = null
        override fun save(day: WorkspaceDay, content: String, expectedVersion: FileVersion): RequestSaveResult =
            RequestSaveResult.Failed("not configured")
    }

    /** 인메모리 [ProcessLockPort] 테스트 대역이다 — 기본은 항상 즉시 획득에 성공한다. */
    private class FakeProcessLockPort(
        private val clock: () -> Instant = Instant::now,
    ) : ProcessLockPort {
        private val locks = mutableMapOf<Pair<ProjectId, LocalDate>, LockPayload>()
        val acquireCalls = AtomicInteger(0)
        val heartbeatCalls = AtomicInteger(0)

        override suspend fun acquire(
            projectId: ProjectId,
            date: LocalDate,
            commandKind: HarnessCommandKind,
        ): LockAcquireResult {
            acquireCalls.incrementAndGet()
            val key = projectId to date
            val existing = locks[key]
            if (existing != null) return LockAcquireResult.Busy(existing)
            val now = clock()
            locks[key] = LockPayload(projectId, date, 4242L, commandKind, now, now)
            return LockAcquireResult.Acquired(LockHandle(projectId, date, 4242L, now))
        }

        override suspend fun heartbeat(handle: LockHandle): Boolean {
            heartbeatCalls.incrementAndGet()
            val key = handle.projectId to handle.date
            val existing = locks[key] ?: return false
            locks[key] = existing.copy(heartbeatAt = clock())
            return true
        }

        override suspend fun release(handle: LockHandle): LockReleaseResult {
            locks.remove(handle.projectId to handle.date)
            return LockReleaseResult.Released
        }

        override suspend fun inspect(projectId: ProjectId, date: LocalDate): LockInspection? {
            val payload = locks[projectId to date] ?: return null
            return LockInspection(payload, LockState.Active)
        }

        override suspend fun forceRelease(projectId: ProjectId, date: LocalDate): LockReleaseResult {
            locks.remove(projectId to date)
            return LockReleaseResult.Released
        }
    }

    /** 인메모리 [ProjectRegistryPort] 테스트 대역이다. */
    private class FakeProjectRegistryPort(
        initialProjects: List<HarnessProject> = emptyList(),
        initialActiveId: ProjectId? = null,
        private val recordThread: (() -> Unit)? = null,
        var saveResult: RegistrySaveResult = RegistrySaveResult.Success,
        var markActiveResult: RegistrySaveResult = RegistrySaveResult.Success,
        var deleteResult: RegistrySaveResult = RegistrySaveResult.Success,
    ) : ProjectRegistryPort {
        private val projects = linkedMapOf<ProjectId, HarnessProject>().apply {
            initialProjects.forEach { put(it.id, it) }
        }
        private var activeId: ProjectId? = initialActiveId

        override suspend fun findAll(): RegistryLoadResult {
            recordThread?.invoke()
            return RegistryLoadResult.Success(projects.values.toList(), activeId)
        }

        override suspend fun findById(id: ProjectId): HarnessProject? {
            recordThread?.invoke()
            return projects[id]
        }

        override suspend fun save(project: HarnessProject): RegistrySaveResult {
            recordThread?.invoke()
            if (saveResult == RegistrySaveResult.Success) projects[project.id] = project
            return saveResult
        }

        override suspend fun delete(id: ProjectId): RegistrySaveResult {
            recordThread?.invoke()
            if (deleteResult == RegistrySaveResult.Success) {
                projects.remove(id)
                if (activeId == id) activeId = null
            }
            return deleteResult
        }

        override suspend fun markActive(id: ProjectId): RegistrySaveResult {
            recordThread?.invoke()
            if (markActiveResult is RegistrySaveResult.Failed) return markActiveResult
            if (id !in projects) return RegistrySaveResult.Failed("not found: ${id.value}")
            activeId = id
            return RegistrySaveResult.Success
        }
    }
    private fun loadUseCase(
        statePort: WorkflowStatePort,
        recordThread: (() -> Unit)? = null,
        availableDates: List<LocalDate> = emptyList(),
    ): LoadCockpitUseCase = LoadCockpitUseCase(
        pathProbe = {
            recordThread?.invoke()
            probeSummary()
        },
        readinessProvider = { _, _ ->
            recordThread?.invoke()
            readiness()
        },
        artifactProbe = { _, _ ->
            recordThread?.invoke()
            WorkspaceArtifactSummary(emptyList())
        },
        dayDiscovery = {
            recordThread?.invoke()
            availableDates
        },
        daySelectionPolicy = WorkspaceDaySelectionPolicy(LocalDate.of(2026, 6, 26)),
        statePort = statePort,
    )

    private fun newViewModel(
        statePort: WorkflowStatePort,
        dispatcher: CoroutineDispatcher,
        changeProbe: (WorkspaceDay) -> FileTime? = { null },
        pollIntervalMillis: Long = 3000L,
        registry: ProjectRegistryPort = FakeProjectRegistryPort(),
        availableDates: List<LocalDate> = emptyList(),
        boundaryResolver: (String?) -> RootPathCheck = { RootPathCheck.Invalid(PathIssue.NotProvided) },
        compatibilityPort: KitVersionManifestPort = KitVersionManifestPort { KitVersionReadResult.Missing },
        harnessRunner: HarnessRunnerPort = HarnessRunnerPort { _, _, _ -> ProcessRunResult.StartFailed("not configured") },
        processLock: ProcessLockPort = FakeProcessLockPort(clock = { fixedInstant }),
        requestWriter: RequestWriterPort = FakeRequestWriterPort(),
        todayStrategyReader: TodayStrategyReaderPort = TodayStrategyReaderPort { null },
        gitStatusPort: GitStatusPort = GitStatusPort { RepositoryStatus.Clean },
    ): AppViewModel = AppViewModel(
        loadCockpit = loadUseCase(statePort, availableDates = availableDates),
        changeProbe = changeProbe,
        resolveActiveProject = ResolveActiveProjectUseCase(registry) { workspaceConfig() },
        loadProjects = LoadProjectsUseCase(registry),
        registerProject = RegisterProjectUseCase(
            pathResolver = boundaryResolver,
            registry = registry,
        ),
        selectProject = SelectProjectUseCase(registry),
        selectWorkspaceDay = SelectWorkspaceDayUseCase(registry),
        deleteProject = DeleteProjectUseCase(registry),
        boundaryPathResolver = boundaryResolver,
        compatibilityPort = compatibilityPort,
        processLock = processLock,
        harnessRunner = harnessRunner,
        executeHarnessAction = ExecuteHarnessActionUseCase(
            actionPolicy = ActionPolicy(),
            commandMapper = HarnessCommandMapper(),
            processLock = processLock,
            harnessRunner = harnessRunner,
            workflowState = statePort,
        ),
        saveRequest = SaveRequestUseCase(requestWriter),
        todayStrategyReader = todayStrategyReader,
        gitStatusPort = gitStatusPort,
        ioDispatcher = dispatcher,
        pollIntervalMillis = pollIntervalMillis,
        clock = { fixedInstant },
        mainDispatcher = dispatcher,
    )

    @Test
    fun `초기 로딩은 IO dispatcher 경로를 거쳐 Ready 상태를 한 번 발행한다`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val statePort = FakeStatePort { StateReadResult.Missing(Path.of("WORKFLOW_STATE.json")) }
        val viewModel = newViewModel(statePort, dispatcher)

        assertEquals(HrnsUiState.Loading, viewModel.state.value)
        runCurrent()

        assertIs<HrnsUiState.Ready>(viewModel.state.value)
        assertEquals(1, statePort.callCount.get())
        viewModel.dispose()
    }

    @Test
    fun `typed Refresh만 Reader를 다시 호출한다`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val statePort = FakeStatePort { StateReadResult.Missing(Path.of("WORKFLOW_STATE.json")) }
        val viewModel = newViewModel(statePort, dispatcher)
        runCurrent()

        viewModel.onEvent(HrnsUiEvent.ActionRequested(UiAction.RunDoctor))
        runCurrent()
        assertEquals(1, statePort.callCount.get())

        viewModel.onEvent(HrnsUiEvent.ActionRequested(UiAction.Refresh))
        runCurrent()
        assertEquals(2, statePort.callCount.get())
        viewModel.dispose()
    }

    @Test
    fun `polling은 mtime이 바뀐 경우에만 다시 읽는다`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val statePort = FakeStatePort { StateReadResult.Missing(Path.of("WORKFLOW_STATE.json")) }
        var mtime = FileTime.fromMillis(0)
        val viewModel = newViewModel(statePort, dispatcher, changeProbe = { mtime }, pollIntervalMillis = 1000L)
        runCurrent()
        assertEquals(1, statePort.callCount.get())

        advanceTimeBy(1_500)
        runCurrent()
        assertEquals(1, statePort.callCount.get())

        mtime = FileTime.fromMillis(999)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(2, statePort.callCount.get())
        viewModel.dispose()
    }

    @Test
    fun `polling job은 중복 생성되지 않고 dispose에서 취소된다`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var counter = 0L
        val statePort = FakeStatePort { StateReadResult.Missing(Path.of("WORKFLOW_STATE.json")) }
        val viewModel = newViewModel(
            statePort,
            dispatcher,
            changeProbe = { FileTime.fromMillis(counter++) },
            pollIntervalMillis = 1000L,
        )
        runCurrent()

        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(6, statePort.callCount.get())

        viewModel.dispose()
        val countAtDispose = statePort.callCount.get()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(countAtDispose, statePort.callCount.get())
    }

    @Test
    fun `production 실패 시 mock으로 대체하지 않고 오류 결과를 그대로 반영한다`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val statePort = FakeStatePort { StateReadResult.AccessDenied(Path.of("WORKFLOW_STATE.json")) }
        val viewModel = newViewModel(statePort, dispatcher)
        runCurrent()

        val ready = assertIs<HrnsUiState.Ready>(viewModel.state.value)
        assertTrue(ready.cockpit.diagnostics != null)
        assertEquals("확인 불가", ready.cockpit.phaseLabel)
        viewModel.dispose()
    }

    @Test
    fun `파일 시스템 협력자는 모두 지정된 IO dispatcher에서 실행된다`() = runBlocking {
        val ioExecutor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "cockpit-io") }
        val mainExecutor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "cockpit-main") }
        val ioDispatcher = ioExecutor.asCoroutineDispatcher()
        val mainDispatcher = mainExecutor.asCoroutineDispatcher()
        val observedThreads = ConcurrentLinkedQueue<String>()
        fun recordThread() {
            observedThreads.add(Thread.currentThread().name)
        }
        val statePort = object : WorkflowStatePort {
            override fun read(day: WorkspaceDay): StateReadResult {
                recordThread()
                return StateReadResult.Missing(day.dayRoot.resolve("WORKFLOW_STATE.json"))
            }
        }
        val ioProject = harnessProject("io", "S:\\project-io")
        val registry = FakeProjectRegistryPort(
            initialProjects = listOf(ioProject),
            recordThread = ::recordThread,
        )
        val viewModel = AppViewModel(
            loadCockpit = loadUseCase(statePort, ::recordThread),
            changeProbe = {
                recordThread()
                null
            },
            resolveActiveProject = ResolveActiveProjectUseCase(registry) {
                recordThread()
                workspaceConfig()
            },
            loadProjects = LoadProjectsUseCase(registry),
            registerProject = RegisterProjectUseCase(
                pathResolver = { RootPathCheck.Invalid(PathIssue.NotProvided) },
                registry = registry,
            ),
            selectProject = SelectProjectUseCase(registry),
            selectWorkspaceDay = SelectWorkspaceDayUseCase(registry),
            deleteProject = DeleteProjectUseCase(registry),
            boundaryPathResolver = {
                recordThread()
                RootPathCheck.Invalid(PathIssue.NotProvided)
            },
            compatibilityPort = {
                recordThread()
                KitVersionReadResult.Missing
            },
            harnessRunner = HarnessRunnerPort { _, _, _ ->
                recordThread()
                ProcessRunResult.StartFailed("not configured")
            },
            processLock = object : ProcessLockPort {
                override suspend fun acquire(projectId: ProjectId, date: LocalDate, commandKind: HarnessCommandKind): LockAcquireResult {
                    recordThread()
                    return LockAcquireResult.Failed("not configured")
                }
                override suspend fun heartbeat(handle: LockHandle): Boolean {
                    recordThread()
                    return false
                }
                override suspend fun release(handle: LockHandle): LockReleaseResult {
                    recordThread()
                    return LockReleaseResult.Released
                }
                override suspend fun inspect(projectId: ProjectId, date: LocalDate): LockInspection? {
                    recordThread()
                    return null
                }
                override suspend fun forceRelease(projectId: ProjectId, date: LocalDate): LockReleaseResult {
                    recordThread()
                    return LockReleaseResult.Released
                }
            },
            executeHarnessAction = ExecuteHarnessActionUseCase(
                actionPolicy = ActionPolicy(),
                commandMapper = HarnessCommandMapper(),
                processLock = FakeProcessLockPort(clock = { fixedInstant }),
                harnessRunner = HarnessRunnerPort { _, _, _ -> ProcessRunResult.StartFailed("not configured") },
                workflowState = statePort,
            ),
            saveRequest = SaveRequestUseCase(FakeRequestWriterPort()),
            todayStrategyReader = TodayStrategyReaderPort { null },
            gitStatusPort = GitStatusPort { RepositoryStatus.Clean },
            ioDispatcher = ioDispatcher,
            pollIntervalMillis = 60_000L,
            clock = { fixedInstant },
            mainDispatcher = mainDispatcher,
        )

        try {
            withTimeout(5_000) { viewModel.state.filterIsInstance<HrnsUiState.Ready>().first() }
            viewModel.onEvent(HrnsUiEvent.ProjectSelected(ioProject.id))
            withTimeout(5_000) {
                viewModel.state.filterIsInstance<HrnsUiState.Ready>()
                    .first { ready -> ready.registryProjects.singleOrNull()?.isActive == true }
            }
            assertTrue(observedThreads.isNotEmpty())
            assertTrue(observedThreads.all { it.startsWith("cockpit-io") }, observedThreads.joinToString())
        } finally {
            viewModel.dispose()
            ioDispatcher.close()
            mainDispatcher.close()
            ioExecutor.shutdownNow()
            mainExecutor.shutdownNow()
        }
    }

    @Test
    fun `늦게 끝난 이전 refresh가 최신 결과를 덮지 않는다`() = runBlocking {
        val ioExecutor = Executors.newFixedThreadPool(2) { runnable -> Thread(runnable, "race-io") }
        val mainExecutor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "race-main") }
        val ioDispatcher = ioExecutor.asCoroutineDispatcher()
        val mainDispatcher = mainExecutor.asCoroutineDispatcher()
        val firstReadEntered = CountDownLatch(1)
        val releaseFirstRead = CountDownLatch(1)
        val firstReadCompleted = CountDownLatch(1)
        val readCount = AtomicInteger(0)
        val statePort = object : WorkflowStatePort {
            override fun read(day: WorkspaceDay): StateReadResult {
                val index = readCount.incrementAndGet()
                if (index == 1) {
                    firstReadEntered.countDown()
                    assertTrue(releaseFirstRead.await(5, TimeUnit.SECONDS))
                    firstReadCompleted.countDown()
                    return StateReadResult.Success(
                        workflowState("older-result"),
                        FileVersion(Instant.EPOCH, 1, "older"),
                    )
                }
                return StateReadResult.Success(
                    workflowState("latest-result"),
                    FileVersion(Instant.EPOCH, 2, "latest"),
                )
            }
        }
        val registry = FakeProjectRegistryPort()
        val viewModel = AppViewModel(
            loadCockpit = loadUseCase(statePort),
            changeProbe = { FileTime.fromMillis(1) },
            resolveActiveProject = ResolveActiveProjectUseCase(registry) { workspaceConfig() },
            loadProjects = LoadProjectsUseCase(registry),
            registerProject = RegisterProjectUseCase(
                pathResolver = { RootPathCheck.Invalid(PathIssue.NotProvided) },
                registry = registry,
            ),
            selectProject = SelectProjectUseCase(registry),
            selectWorkspaceDay = SelectWorkspaceDayUseCase(registry),
            deleteProject = DeleteProjectUseCase(registry),
            boundaryPathResolver = { RootPathCheck.Invalid(PathIssue.NotProvided) },
            compatibilityPort = { KitVersionReadResult.Missing },
            harnessRunner = HarnessRunnerPort { _, _, _ -> ProcessRunResult.StartFailed("not configured") },
            processLock = FakeProcessLockPort(clock = { fixedInstant }),
            executeHarnessAction = ExecuteHarnessActionUseCase(
                actionPolicy = ActionPolicy(),
                commandMapper = HarnessCommandMapper(),
                processLock = FakeProcessLockPort(clock = { fixedInstant }),
                harnessRunner = HarnessRunnerPort { _, _, _ -> ProcessRunResult.StartFailed("not configured") },
                workflowState = statePort,
            ),
            saveRequest = SaveRequestUseCase(FakeRequestWriterPort()),
            todayStrategyReader = TodayStrategyReaderPort { null },
            gitStatusPort = GitStatusPort { RepositoryStatus.Clean },
            ioDispatcher = ioDispatcher,
            pollIntervalMillis = 60_000L,
            clock = { fixedInstant },
            mainDispatcher = mainDispatcher,
        )

        try {
            assertTrue(firstReadEntered.await(5, TimeUnit.SECONDS))
            viewModel.refresh()
            val latest = withTimeout(5_000) {
                viewModel.state.filterIsInstance<HrnsUiState.Ready>().first { it.cockpit.projectName == "latest-result" }
            }
            assertEquals("latest-result", latest.cockpit.projectName)

            releaseFirstRead.countDown()
            assertTrue(firstReadCompleted.await(5, TimeUnit.SECONDS))
            mainExecutor.submit {}.get(5, TimeUnit.SECONDS)
            val stillLatest = assertIs<HrnsUiState.Ready>(viewModel.state.value)
            assertEquals("latest-result", stillLatest.cockpit.projectName)
        } finally {
            releaseFirstRead.countDown()
            viewModel.dispose()
            ioDispatcher.close()
            mainDispatcher.close()
            ioExecutor.shutdownNow()
            mainExecutor.shutdownNow()
        }
    }

    @Test
    fun `프로젝트를 전환하면 새 프로젝트의 workspaceConfig로 State와 artifact를 같은 day에 다시 읽는다`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val seenStateRoots = mutableListOf<String>()
        val statePort = object : WorkflowStatePort {
            override fun read(day: WorkspaceDay): StateReadResult {
                seenStateRoots.add(day.projectWorkspaceRoot.toString())
                return StateReadResult.Missing(day.dayRoot.resolve("WORKFLOW_STATE.json"))
            }
        }
        val projectA = harnessProject("a", "S:\\project-a")
        val projectB = harnessProject("b", "S:\\project-b")
        val registry = FakeProjectRegistryPort(initialProjects = listOf(projectA, projectB))
        val viewModel = newViewModel(statePort, dispatcher, registry = registry)
        runCurrent()

        viewModel.onEvent(HrnsUiEvent.ProjectSelected(projectA.id))
        runCurrent()
        assertIs<HrnsUiState.Ready>(viewModel.state.value)
        assertTrue(seenStateRoots.last().contains("project-a"))

        viewModel.onEvent(HrnsUiEvent.ProjectSelected(projectB.id))
        runCurrent()
        assertTrue(seenStateRoots.last().contains("project-b"))

        viewModel.dispose()
    }

    @Test
    fun `프로젝트를 전환하면 새 프로젝트의 Kit root로 compatibility manifest를 다시 읽는다`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val seenKitRoots = mutableListOf<String>()
        val statePort = FakeStatePort { StateReadResult.Missing(Path.of("WORKFLOW_STATE.json")) }
        val projectA = harnessProject("a", "S:\\project-a")
        val projectB = harnessProject("b", "S:\\project-b")
        val registry = FakeProjectRegistryPort(initialProjects = listOf(projectA, projectB))
        val compatibilityPort = KitVersionManifestPort { kitRoot ->
            seenKitRoots.add(kitRoot.toString())
            KitVersionReadResult.Missing
        }
        val viewModel = newViewModel(
            statePort,
            dispatcher,
            registry = registry,
            compatibilityPort = compatibilityPort,
        )
        runCurrent()

        viewModel.onEvent(HrnsUiEvent.ProjectSelected(projectA.id))
        runCurrent()
        assertTrue(seenKitRoots.last().contains("kit-a"))

        viewModel.onEvent(HrnsUiEvent.ProjectSelected(projectB.id))
        runCurrent()
        assertTrue(seenKitRoots.last().contains("kit-b"))

        viewModel.dispose()
    }

    @Test
    fun `프로젝트 전환 중 늦게 끝난 이전 프로젝트의 compatibility 읽기가 새 프로젝트 상태를 덮지 않는다`() = runBlocking {
        val ioExecutor = Executors.newFixedThreadPool(2) { runnable -> Thread(runnable, "compat-switch-io") }
        val mainExecutor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "compat-switch-main") }
        val ioDispatcher = ioExecutor.asCoroutineDispatcher()
        val mainDispatcher = mainExecutor.asCoroutineDispatcher()

        val projectA = harnessProject("a", "S:\\project-a")
        val projectB = harnessProject("b", "S:\\project-b")
        val registry = FakeProjectRegistryPort(initialProjects = listOf(projectA, projectB))

        val firstReadEntered = CountDownLatch(1)
        val releaseFirstRead = CountDownLatch(1)
        val statePort = FakeStatePort { StateReadResult.Missing(Path.of("WORKFLOW_STATE.json")) }
        val compatibilityPort = KitVersionManifestPort { kitRoot ->
            if (kitRoot.toString().contains("kit-a")) {
                firstReadEntered.countDown()
                assertTrue(releaseFirstRead.await(5, TimeUnit.SECONDS))
                KitVersionReadResult.Malformed("project-a-stale")
            } else {
                KitVersionReadResult.Missing
            }
        }

        val loadCockpit = LoadCockpitUseCase(
            pathProbe = { probeSummary() },
            readinessProvider = { _, _ -> readiness() },
            artifactProbe = { _, _ -> WorkspaceArtifactSummary(emptyList()) },
            dayDiscovery = { emptyList() },
            daySelectionPolicy = WorkspaceDaySelectionPolicy(LocalDate.of(2026, 6, 26)),
            statePort = statePort,
        )
        val viewModel = AppViewModel(
            loadCockpit = loadCockpit,
            changeProbe = { null },
            resolveActiveProject = ResolveActiveProjectUseCase(registry) { workspaceConfig() },
            loadProjects = LoadProjectsUseCase(registry),
            registerProject = RegisterProjectUseCase(
                pathResolver = { RootPathCheck.Invalid(PathIssue.NotProvided) },
                registry = registry,
            ),
            selectProject = SelectProjectUseCase(registry),
            selectWorkspaceDay = SelectWorkspaceDayUseCase(registry),
            deleteProject = DeleteProjectUseCase(registry),
            boundaryPathResolver = { RootPathCheck.Invalid(PathIssue.NotProvided) },
            compatibilityPort = compatibilityPort,
            harnessRunner = HarnessRunnerPort { _, _, _ -> ProcessRunResult.StartFailed("not configured") },
            processLock = FakeProcessLockPort(clock = { fixedInstant }),
            executeHarnessAction = ExecuteHarnessActionUseCase(
                actionPolicy = ActionPolicy(),
                commandMapper = HarnessCommandMapper(),
                processLock = FakeProcessLockPort(clock = { fixedInstant }),
                harnessRunner = HarnessRunnerPort { _, _, _ -> ProcessRunResult.StartFailed("not configured") },
                workflowState = statePort,
            ),
            saveRequest = SaveRequestUseCase(FakeRequestWriterPort()),
            todayStrategyReader = TodayStrategyReaderPort { null },
            gitStatusPort = GitStatusPort { RepositoryStatus.Clean },
            ioDispatcher = ioDispatcher,
            pollIntervalMillis = 60_000L,
            clock = { fixedInstant },
            mainDispatcher = mainDispatcher,
        )

        try {
            withTimeout(5_000) { viewModel.state.filterIsInstance<HrnsUiState.Ready>().first() }

            viewModel.onEvent(HrnsUiEvent.ProjectSelected(projectA.id))
            assertTrue(firstReadEntered.await(5, TimeUnit.SECONDS))

            viewModel.onEvent(HrnsUiEvent.ProjectSelected(projectB.id))
            val readyB = withTimeout(5_000) {
                viewModel.state.filterIsInstance<HrnsUiState.Ready>()
                    .first { ready -> ready.registryProjects.firstOrNull { it.isActive }?.id == projectB.id }
            }
            // project-b의 compatibility 결과(Missing)만 반영되어야 하고, 아직 도착하지 않은
            // project-a의 malformed 사유는 나타나면 안 된다.
            assertTrue(requireNotNull(readyB.cockpit.compatibilityDiagnostics).whatHappened.contains("파일이 없어"))
            assertFalse(readyB.cockpit.compatibilityDiagnostics.whatHappened.contains("project-a-stale"))

            releaseFirstRead.countDown()
            mainExecutor.submit {}.get(5, TimeUnit.SECONDS)
            val stillB = assertIs<HrnsUiState.Ready>(viewModel.state.value)
            assertTrue(requireNotNull(stillB.cockpit.compatibilityDiagnostics).whatHappened.contains("파일이 없어"))
            assertFalse(stillB.cockpit.compatibilityDiagnostics.whatHappened.contains("project-a-stale"))
            assertTrue(stillB.registryProjects.firstOrNull { it.isActive }?.id == projectB.id)
        } finally {
            releaseFirstRead.countDown()
            viewModel.dispose()
            ioDispatcher.close()
            mainDispatcher.close()
            ioExecutor.shutdownNow()
            mainExecutor.shutdownNow()
        }
    }

    @Test
    fun `프로젝트 전환 중 늦게 끝난 이전 프로젝트의 읽기가 새 프로젝트 상태를 덮지 않는다`() = runBlocking {
        val ioExecutor = Executors.newFixedThreadPool(2) { runnable -> Thread(runnable, "switch-io") }
        val mainExecutor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "switch-main") }
        val ioDispatcher = ioExecutor.asCoroutineDispatcher()
        val mainDispatcher = mainExecutor.asCoroutineDispatcher()

        val projectA = harnessProject("a", "S:\\project-a")
        val projectB = harnessProject("b", "S:\\project-b")
        val registry = FakeProjectRegistryPort(initialProjects = listOf(projectA, projectB))

        val firstReadEntered = CountDownLatch(1)
        val releaseFirstRead = CountDownLatch(1)
        val statePort = object : WorkflowStatePort {
            override fun read(day: WorkspaceDay): StateReadResult {
                val projectName = day.projectWorkspaceRoot.toString()
                if (projectName.contains("project-a")) {
                    firstReadEntered.countDown()
                    assertTrue(releaseFirstRead.await(5, TimeUnit.SECONDS))
                    return StateReadResult.Success(
                        workflowState("project-a-stale"),
                        FileVersion(Instant.EPOCH, 1, "a"),
                    )
                }
                return StateReadResult.Success(
                    workflowState("project-b-latest"),
                    FileVersion(Instant.EPOCH, 2, "b"),
                )
            }
        }

        val loadCockpit = LoadCockpitUseCase(
            pathProbe = { probeSummary() },
            readinessProvider = { _, _ -> readiness() },
            artifactProbe = { _, _ -> WorkspaceArtifactSummary(emptyList()) },
            dayDiscovery = { emptyList() },
            daySelectionPolicy = WorkspaceDaySelectionPolicy(LocalDate.of(2026, 6, 26)),
            statePort = statePort,
        )
        val viewModel = AppViewModel(
            loadCockpit = loadCockpit,
            changeProbe = { null },
            resolveActiveProject = ResolveActiveProjectUseCase(registry) { workspaceConfig() },
            loadProjects = LoadProjectsUseCase(registry),
            registerProject = RegisterProjectUseCase(
                pathResolver = { RootPathCheck.Invalid(PathIssue.NotProvided) },
                registry = registry,
            ),
            selectProject = SelectProjectUseCase(registry),
            selectWorkspaceDay = SelectWorkspaceDayUseCase(registry),
            deleteProject = DeleteProjectUseCase(registry),
            boundaryPathResolver = { RootPathCheck.Invalid(PathIssue.NotProvided) },
            compatibilityPort = { KitVersionReadResult.Missing },
            harnessRunner = HarnessRunnerPort { _, _, _ -> ProcessRunResult.StartFailed("not configured") },
            processLock = FakeProcessLockPort(clock = { fixedInstant }),
            executeHarnessAction = ExecuteHarnessActionUseCase(
                actionPolicy = ActionPolicy(),
                commandMapper = HarnessCommandMapper(),
                processLock = FakeProcessLockPort(clock = { fixedInstant }),
                harnessRunner = HarnessRunnerPort { _, _, _ -> ProcessRunResult.StartFailed("not configured") },
                workflowState = statePort,
            ),
            saveRequest = SaveRequestUseCase(FakeRequestWriterPort()),
            todayStrategyReader = TodayStrategyReaderPort { null },
            gitStatusPort = GitStatusPort { RepositoryStatus.Clean },
            ioDispatcher = ioDispatcher,
            pollIntervalMillis = 60_000L,
            clock = { fixedInstant },
            mainDispatcher = mainDispatcher,
        )

        try {
            // 최초 진입(Registry에 마지막 선택 없음)은 project-a/b 어느 쪽도 아니므로 즉시 끝난다.
            withTimeout(5_000) { viewModel.state.filterIsInstance<HrnsUiState.Ready>().first() }

            viewModel.onEvent(HrnsUiEvent.ProjectSelected(projectA.id))
            assertTrue(firstReadEntered.await(5, TimeUnit.SECONDS))

            viewModel.onEvent(HrnsUiEvent.ProjectSelected(projectB.id))
            val readyB = withTimeout(5_000) {
                viewModel.state.filterIsInstance<HrnsUiState.Ready>()
                    .first { it.cockpit.projectName == "project-b-latest" }
            }
            assertEquals("project-b-latest", readyB.cockpit.projectName)

            releaseFirstRead.countDown()
            mainExecutor.submit {}.get(5, TimeUnit.SECONDS)
            val stillB = assertIs<HrnsUiState.Ready>(viewModel.state.value)
            assertEquals("project-b-latest", stillB.cockpit.projectName)
        } finally {
            releaseFirstRead.countDown()
            viewModel.dispose()
            ioDispatcher.close()
            mainDispatcher.close()
            ioExecutor.shutdownNow()
            mainExecutor.shutdownNow()
        }
    }

    @Test
    fun `프로젝트 등록 후 목록과 활성 선택이 즉시 갱신된다`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val registry = FakeProjectRegistryPort()
        val statePort = FakeStatePort { StateReadResult.Missing(Path.of("WORKFLOW_STATE.json")) }
        val resolver: (String?) -> RootPathCheck = { raw ->
            if (raw == null) {
                RootPathCheck.Invalid(PathIssue.NotProvided)
            } else {
                val path = Path.of(raw)
                RootPathCheck.Valid(path, path)
            }
        }
        val viewModel = newViewModel(
            statePort = statePort,
            dispatcher = dispatcher,
            registry = registry,
            boundaryResolver = resolver,
            compatibilityPort = { KitVersionReadResult.Success(supportedManifest()) },
            harnessRunner = HarnessRunnerPort { _, _, _ -> successfulDoctorResult() },
        )
        runCurrent()

        viewModel.onEvent(
            HrnsUiEvent.ProjectRegistrationRequested(
                RegisterProjectCandidate(
                    displayName = "신규 프로젝트",
                    kitRootRaw = "S:\\kit-new",
                    projectWorkspaceRootRaw = "S:\\workspace-new",
                    repositoryRootRaw = "S:\\repo-new",
                    profileId = "기본",
                ),
            ),
        )
        runCurrent()

        val ready = assertIs<HrnsUiState.Ready>(viewModel.state.value)
        assertEquals(1, ready.registryProjects.size)
        assertTrue(ready.registryProjects.single().isActive)
        assertEquals("S:\\workspace-new", ready.workspaceConfig.roots.workspaceRoot)
        assertTrue(ready.registryMessage?.contains("Doctor·호환성 확인 후") == true)
        viewModel.dispose()
    }

    @Test
    fun `온보딩 Doctor 실패면 Registry에 프로젝트를 저장하지 않는다`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val registry = FakeProjectRegistryPort()
        val statePort = FakeStatePort { StateReadResult.Missing(Path.of("WORKFLOW_STATE.json")) }
        val resolver: (String?) -> RootPathCheck = { raw ->
            raw?.let(Path::of)?.let { path -> RootPathCheck.Valid(path, path) }
                ?: RootPathCheck.Invalid(PathIssue.NotProvided)
        }
        val viewModel = newViewModel(
            statePort = statePort,
            dispatcher = dispatcher,
            registry = registry,
            boundaryResolver = resolver,
            compatibilityPort = { KitVersionReadResult.Success(supportedManifest()) },
            harnessRunner = HarnessRunnerPort { _, _, _ ->
                ProcessRunResult.Completed(1, null, null, false, false)
            },
        )
        runCurrent()

        viewModel.onEvent(
            HrnsUiEvent.ProjectRegistrationRequested(
                RegisterProjectCandidate("실패 프로젝트", "S:\\kit-fail", "S:\\workspace-fail", "S:\\repo-fail", "기본"),
            ),
        )
        runCurrent()

        val ready = assertIs<HrnsUiState.Ready>(viewModel.state.value)
        assertTrue(ready.registryProjects.isEmpty())
        assertTrue(ready.registryMessage?.contains("Registry를 저장하지 않았습니다") == true)
        viewModel.dispose()
    }
    @Test
    fun `프로젝트 삭제 저장 실패를 성공으로 표시하거나 목록에서 제거하지 않는다`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val project = harnessProject("a", "S:\\project-a")
        val registry = FakeProjectRegistryPort(
            initialProjects = listOf(project),
            initialActiveId = project.id,
            deleteResult = RegistrySaveResult.Failed("write denied"),
        )
        val statePort = FakeStatePort { StateReadResult.Missing(Path.of("WORKFLOW_STATE.json")) }
        val viewModel = newViewModel(statePort, dispatcher, registry = registry)
        runCurrent()

        viewModel.onEvent(HrnsUiEvent.ProjectDeletionRequested(project.id))
        runCurrent()

        val ready = assertIs<HrnsUiState.Ready>(viewModel.state.value)
        assertEquals(1, ready.registryProjects.size)
        assertTrue(ready.registryProjects.single().isActive)
        assertTrue(ready.registryMessage?.contains("삭제하지 못했습니다") == true)
        viewModel.dispose()
    }

    @Test
    fun `유효한 과거 날짜 선택은 같은 WorkspaceDay를 읽고 Registry metadata에 저장한다`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val today = LocalDate.of(2026, 6, 26)
        val past = LocalDate.of(2026, 6, 25)
        val project = harnessProject("a", "S:\\project-a")
        val registry = FakeProjectRegistryPort(
            initialProjects = listOf(project),
            initialActiveId = project.id,
        )
        val readDates = mutableListOf<LocalDate>()
        val statePort = object : WorkflowStatePort {
            override fun read(day: WorkspaceDay): StateReadResult {
                readDates += day.date
                return StateReadResult.Missing(day.dayRoot.resolve("WORKFLOW_STATE.json"))
            }
        }
        val viewModel = newViewModel(
            statePort = statePort,
            dispatcher = dispatcher,
            registry = registry,
            availableDates = listOf(past, today),
        )
        runCurrent()

        viewModel.onEvent(HrnsUiEvent.WorkspaceDaySelected(past))
        runCurrent()

        val ready = assertIs<HrnsUiState.Ready>(viewModel.state.value)
        assertEquals(past, readDates.last())
        assertTrue(ready.cockpit.isReadOnlyDay)
        assertEquals(past, ready.workspaceDays.single { it.isSelected }.date)
        val saved = assertIs<RegistryLoadResult.Success>(registry.findAll()).projects.single()
        assertEquals(past, saved.lastSelectedDate)
        viewModel.dispose()
    }

    @Test
    fun `State mtime이 같아도 polling은 변경된 compatibility manifest를 반영한다`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val project = harnessProject("a", "S:\\project-a")
        val registry = FakeProjectRegistryPort(
            initialProjects = listOf(project),
            initialActiveId = project.id,
        )
        val statePort = FakeStatePort {
            StateReadResult.Success(
                workflowState("compatibility-poll"),
                FileVersion(Instant.EPOCH, 1, "same-state"),
            )
        }
        val supportedManifest = KitVersionManifest(
            kitVersion = KitVersion("2026.07.23"),
            stateSchemaVersion = ContractVersion(1, 0, "1.0"),
            uiContractVersion = ContractVersion(1, 0, "1.0"),
        )
        var compatibilityRead: KitVersionReadResult = KitVersionReadResult.Success(supportedManifest)
        val compatibilityReads = AtomicInteger(0)
        val viewModel = newViewModel(
            statePort = statePort,
            dispatcher = dispatcher,
            changeProbe = { FileTime.fromMillis(1) },
            pollIntervalMillis = 100L,
            registry = registry,
            compatibilityPort = {
                compatibilityReads.incrementAndGet()
                compatibilityRead
            },
        )
        runCurrent()

        val supported = assertIs<HrnsUiState.Ready>(viewModel.state.value)
        assertEquals(null, supported.cockpit.compatibilityDiagnostics)
        assertEquals(1, statePort.callCount.get())

        compatibilityRead = KitVersionReadResult.Malformed("invalid_json")
        advanceTimeBy(101L)
        runCurrent()

        val malformed = assertIs<HrnsUiState.Ready>(viewModel.state.value)
        assertTrue(requireNotNull(malformed.cockpit.compatibilityDiagnostics).whatHappened.contains("invalid_json"))
        assertEquals(2, statePort.callCount.get())
        assertTrue(compatibilityReads.get() >= 2)
        viewModel.dispose()
    }

    @Test
    fun `변경 없는 polling tick은 진행 중인 수동 refresh 결과를 무효화하지 않는다`() = runBlocking {
        val ioExecutor = Executors.newFixedThreadPool(2) { runnable -> Thread(runnable, "noop-poll-io") }
        val mainExecutor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "noop-poll-main") }
        val ioDispatcher = ioExecutor.asCoroutineDispatcher()
        val mainDispatcher = mainExecutor.asCoroutineDispatcher()
        val manualReadEntered = CountDownLatch(1)
        val releaseManualRead = CountDownLatch(1)
        val noChangePollObserved = CountDownLatch(1)
        val readCount = AtomicInteger(0)
        val statePort = object : WorkflowStatePort {
            override fun read(day: WorkspaceDay): StateReadResult {
                return when (readCount.incrementAndGet()) {
                    1 -> StateReadResult.Success(
                        workflowState("initial"),
                        FileVersion(Instant.EPOCH, 1, "initial"),
                    )
                    2 -> {
                        manualReadEntered.countDown()
                        assertTrue(releaseManualRead.await(5, TimeUnit.SECONDS))
                        StateReadResult.Success(
                            workflowState("manual-refresh"),
                            FileVersion(Instant.EPOCH, 2, "manual"),
                        )
                    }
                    else -> error("변경 없는 poll은 Reader를 다시 호출하면 안 됩니다.")
                }
            }
        }
        val registry = FakeProjectRegistryPort()
        val viewModel = AppViewModel(
            loadCockpit = loadUseCase(statePort),
            changeProbe = {
                if (manualReadEntered.count == 0L) noChangePollObserved.countDown()
                FileTime.fromMillis(1)
            },
            resolveActiveProject = ResolveActiveProjectUseCase(registry) { workspaceConfig() },
            loadProjects = LoadProjectsUseCase(registry),
            registerProject = RegisterProjectUseCase(
                pathResolver = { RootPathCheck.Invalid(PathIssue.NotProvided) },
                registry = registry,
            ),
            selectProject = SelectProjectUseCase(registry),
            selectWorkspaceDay = SelectWorkspaceDayUseCase(registry),
            deleteProject = DeleteProjectUseCase(registry),
            boundaryPathResolver = { RootPathCheck.Invalid(PathIssue.NotProvided) },
            compatibilityPort = { KitVersionReadResult.Missing },
            harnessRunner = HarnessRunnerPort { _, _, _ -> ProcessRunResult.StartFailed("not configured") },
            processLock = FakeProcessLockPort(clock = { fixedInstant }),
            executeHarnessAction = ExecuteHarnessActionUseCase(
                actionPolicy = ActionPolicy(),
                commandMapper = HarnessCommandMapper(),
                processLock = FakeProcessLockPort(clock = { fixedInstant }),
                harnessRunner = HarnessRunnerPort { _, _, _ -> ProcessRunResult.StartFailed("not configured") },
                workflowState = statePort,
            ),
            saveRequest = SaveRequestUseCase(FakeRequestWriterPort()),
            todayStrategyReader = TodayStrategyReaderPort { null },
            gitStatusPort = GitStatusPort { RepositoryStatus.Clean },
            ioDispatcher = ioDispatcher,
            pollIntervalMillis = 50L,
            clock = { fixedInstant },
            mainDispatcher = mainDispatcher,
        )

        try {
            withTimeout(5_000) {
                viewModel.state.filterIsInstance<HrnsUiState.Ready>()
                    .first { it.cockpit.projectName == "initial" }
            }
            viewModel.refresh()
            assertTrue(manualReadEntered.await(5, TimeUnit.SECONDS))
            assertTrue(noChangePollObserved.await(5, TimeUnit.SECONDS))
            releaseManualRead.countDown()

            val refreshed = withTimeout(5_000) {
                viewModel.state.filterIsInstance<HrnsUiState.Ready>()
                    .first { it.cockpit.projectName == "manual-refresh" }
            }
            assertEquals("manual-refresh", refreshed.cockpit.projectName)
            assertEquals(2, readCount.get())
        } finally {
            releaseManualRead.countDown()
            viewModel.dispose()
            ioDispatcher.close()
            mainDispatcher.close()
            ioExecutor.shutdownNow()
            mainExecutor.shutdownNow()
        }
    }

    @Test
    fun `Doctor 실행 중 중복 클릭은 harnessRunner를 한 번만 호출한다`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val project = harnessProject("a", "S:\\project-a")
        val registry = FakeProjectRegistryPort(initialProjects = listOf(project), initialActiveId = project.id)
        val statePort = FakeStatePort { StateReadResult.Missing(Path.of("WORKFLOW_STATE.json")) }
        val executeCount = AtomicInteger(0)
        val runner = HarnessRunnerPort { _, _, _ ->
            executeCount.incrementAndGet()
            ProcessRunResult.Completed(0, null, null, false, false)
        }
        val viewModel = newViewModel(statePort, dispatcher, registry = registry, harnessRunner = runner)
        runCurrent()

        viewModel.onEvent(HrnsUiEvent.ActionRequested(UiAction.RunDoctor))
        viewModel.onEvent(HrnsUiEvent.ActionRequested(UiAction.RunDoctor))
        viewModel.onEvent(HrnsUiEvent.ActionRequested(UiAction.RunDoctor))
        runCurrent()

        assertEquals(1, executeCount.get())
        viewModel.dispose()
    }

    @Test
    fun `실행이 끝나면 harnessRunner 결과와 별개로 State를 다시 읽는다`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val project = harnessProject("a", "S:\\project-a")
        val registry = FakeProjectRegistryPort(initialProjects = listOf(project), initialActiveId = project.id)
        val statePort = FakeStatePort { StateReadResult.Missing(Path.of("WORKFLOW_STATE.json")) }
        val runner = HarnessRunnerPort { _, _, _ -> ProcessRunResult.Completed(0, null, null, false, false) }
        val viewModel = newViewModel(statePort, dispatcher, registry = registry, harnessRunner = runner)
        runCurrent()
        val callsBeforeRun = statePort.callCount.get()

        viewModel.onEvent(HrnsUiEvent.ActionRequested(UiAction.RunDoctor))
        runCurrent()

        assertTrue(statePort.callCount.get() > callsBeforeRun)
        viewModel.dispose()
    }

    @Test
    fun `BootstrapDay RunPlanning RunReplan RunCodeSlice RunDocSlice는 각각 대응하는 typed command를 만든다`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val project = harnessProject("a", "S:\\project-a")
        val registry = FakeProjectRegistryPort(initialProjects = listOf(project), initialActiveId = project.id)
        val capturedCommands = mutableListOf<HarnessCommand>()
        val runner = HarnessRunnerPort { command, _, _ ->
            capturedCommands.add(command)
            ProcessRunResult.Completed(0, null, null, false, false)
        }
        val actionsInOrder = listOf(
            UiAction.BootstrapDay,
            UiAction.RunPlanning,
            UiAction.RunReplan,
            UiAction.RunCodeSlice,
            UiAction.RunDocSlice,
            UiAction.RunClosureValidation,
        )

        for (action in actionsInOrder) {
            val statePort = FakeStatePort {
                StateReadResult.Success(stateForAllowedHarnessAction(action), FileVersion(fixedInstant, 1, action.toString()))
            }
            val viewModel = newViewModel(
                statePort,
                dispatcher,
                registry = registry,
                boundaryResolver = ::validBoundary,
                compatibilityPort = { KitVersionReadResult.Success(supportedManifest()) },
                harnessRunner = runner,
            )
            runCurrent()
            viewModel.onEvent(HrnsUiEvent.ActionRequested(action))
            runCurrent()
            viewModel.dispose()
        }

        assertEquals(actionsInOrder.size, capturedCommands.size)
        assertIs<HarnessCommand.BootstrapDay>(capturedCommands[0])
        assertIs<HarnessCommand.RunPlanning>(capturedCommands[1])
        val replan = assertIs<HarnessCommand.RunReplan>(capturedCommands[2])
        assertEquals(ReplanReason.HumanRequestedReplan, replan.reason)
        val codeExecution = assertIs<HarnessCommand.RunExecution>(capturedCommands[3])
        assertEquals(ExecutionWrapper.Code, codeExecution.wrapper)
        val docExecution = assertIs<HarnessCommand.RunExecution>(capturedCommands[4])
        assertEquals(ExecutionWrapper.Doc, docExecution.wrapper)
        assertIs<HarnessCommand.ValidateClosure>(capturedCommands[5])
    }

    @Test
    fun `ActionPolicy가 RunClosureValidation을 허용해도 ClosurePolicy가 막으면 비활성화된다`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val project = harnessProject("a", "S:\\project-a")
        val registry = FakeProjectRegistryPort(initialProjects = listOf(project), initialActiveId = project.id)
        val executions = AtomicInteger(0)
        // ActionPolicy 관점에서는 RunClosureValidation이 허용되는 상태이지만, ops validation을
        // 통과하지 못해 ClosurePolicy는 Blocked를 반환해야 한다 — 실행 성공(exit 0)과 closure
        // 허용은 별개의 신호임을 확인한다.
        val state = stateForAllowedHarnessAction(UiAction.RunClosureValidation)
            .copy(opsValidation = OpsValidationState(passed = false, validatedAt = null, notes = null))
        val statePort = FakeStatePort {
            StateReadResult.Success(state, FileVersion(fixedInstant, 1, "closure-blocked"))
        }
        val viewModel = newViewModel(
            statePort,
            dispatcher,
            registry = registry,
            boundaryResolver = ::validBoundary,
            compatibilityPort = { KitVersionReadResult.Success(supportedManifest()) },
            harnessRunner = HarnessRunnerPort { _, _, _ ->
                executions.incrementAndGet()
                ProcessRunResult.Completed(0, null, null, false, false)
            },
        )
        runCurrent()

        val ready = assertIs<HrnsUiState.Ready>(viewModel.state.value)
        val closureAction = (listOfNotNull(ready.cockpit.primaryAction) + ready.cockpit.allowedActions)
            .firstOrNull { it.action == UiAction.RunClosureValidation }
        assertEquals(false, closureAction?.enabled)

        // UI button이 disabled여도 stale/direct event가 들어올 수 있으므로 ViewModel 경계에서
        // ClosurePolicy를 다시 적용해야 한다.
        viewModel.onEvent(HrnsUiEvent.ActionRequested(UiAction.RunClosureValidation))
        runCurrent()
        assertEquals(0, executions.get())
        viewModel.dispose()
    }

    @Test
    fun `dirty repository의 closure 검증은 명시적 acknowledgement 없이는 실행되지 않는다`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val project = harnessProject("a", "S:\\project-a")
        val registry = FakeProjectRegistryPort(initialProjects = listOf(project), initialActiveId = project.id)
        val executions = AtomicInteger(0)
        val statePort = FakeStatePort {
            StateReadResult.Success(
                stateForAllowedHarnessAction(UiAction.RunClosureValidation),
                FileVersion(fixedInstant, 1, "closure-dirty-repository"),
            )
        }
        val viewModel = newViewModel(
            statePort,
            dispatcher,
            registry = registry,
            boundaryResolver = ::validBoundary,
            compatibilityPort = { KitVersionReadResult.Success(supportedManifest()) },
            gitStatusPort = GitStatusPort { RepositoryStatus.Dirty(listOf("src/Foo.kt")) },
            harnessRunner = HarnessRunnerPort { _, _, _ ->
                executions.incrementAndGet()
                ProcessRunResult.Completed(0, null, null, false, false)
            },
        )
        runCurrent()

        val ready = assertIs<HrnsUiState.Ready>(viewModel.state.value)
        assertTrue(ready.recovery.incompleteHandoffItems.isNotEmpty())
        assertEquals(false, ready.cockpit.allowedActions.first { it.action == UiAction.RunClosureValidation }.enabled)

        viewModel.onEvent(HrnsUiEvent.ActionRequested(UiAction.RunClosureValidation))
        runCurrent()
        assertEquals(0, executions.get())

        viewModel.onEvent(HrnsUiEvent.ClosureValidationRequested(incompleteHandoffAcknowledged = true))
        runCurrent()
        assertEquals(1, executions.get())
        viewModel.dispose()
    }

    @Test
    fun `다른 실행이 lock을 보유 중이면 새 Planning 실행은 harnessRunner를 호출하지 않는다`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val project = harnessProject("a", "S:\\project-a")
        val registry = FakeProjectRegistryPort(initialProjects = listOf(project), initialActiveId = project.id)
        val statePort = FakeStatePort {
            StateReadResult.Success(stateForAllowedHarnessAction(UiAction.RunPlanning), FileVersion(fixedInstant, 1, "planning"))
        }
        val executeCount = AtomicInteger(0)
        val runner = HarnessRunnerPort { _, _, _ ->
            executeCount.incrementAndGet()
            ProcessRunResult.Completed(0, null, null, false, false)
        }
        val busyLock = FakeProcessLockPort(clock = { fixedInstant })
        // 이미 다른 소유자(Doctor 실행)가 이 날짜의 잠금을 보유한 상태를 만든다.
        runBlocking { busyLock.acquire(project.id, LocalDate.of(2026, 6, 26), HarnessCommandKind.Doctor) }

        val viewModel = newViewModel(
            statePort,
            dispatcher,
            registry = registry,
            boundaryResolver = ::validBoundary,
            compatibilityPort = { KitVersionReadResult.Success(supportedManifest()) },
            harnessRunner = runner,
            processLock = busyLock,
        )
        runCurrent()
        viewModel.onEvent(HrnsUiEvent.ActionRequested(UiAction.RunPlanning))
        runCurrent()

        assertEquals(0, executeCount.get())
        viewModel.dispose()
    }

    @Test
    fun `요청 저장은 REQUEST_INBOX만 쓰고 성공 notice를 보인다`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val project = harnessProject("a", "S:\\project-a")
        val registry = FakeProjectRegistryPort(initialProjects = listOf(project), initialActiveId = project.id)
        val statePort = FakeStatePort {
            StateReadResult.Success(requestIntakeState(), FileVersion(fixedInstant, 1, "request"))
        }
        val savedContent = mutableListOf<String>()
        val requestWriter = object : RequestWriterPort {
            override fun load(day: WorkspaceDay): LoadedRequest =
                LoadedRequest("# REQUEST_INBOX\n\n## Current Day Entries\n", FileVersion(fixedInstant, 10, "hash"))

            override fun save(day: WorkspaceDay, content: String, expectedVersion: FileVersion): RequestSaveResult {
                savedContent.add(content)
                return RequestSaveResult.Saved
            }
        }
        val viewModel = newViewModel(
            statePort,
            dispatcher,
            registry = registry,
            boundaryResolver = ::validBoundary,
            compatibilityPort = { KitVersionReadResult.Success(supportedManifest()) },
            requestWriter = requestWriter,
        )
        runCurrent()

        viewModel.onEvent(
            HrnsUiEvent.RequestEntrySubmitted(
                RequestEntryDraft(
                    title = "새 버그",
                    type = RequestEntryType.Bug,
                    source = RequestEntrySource.Human,
                    priority = RequestEntryPriority.High,
                    summary = "요약",
                    detail = "상세",
                    constraints = "",
                ),
            ),
        )
        runCurrent()

        assertEquals(1, savedContent.size)
        assertTrue(savedContent.single().contains("### 새 버그"))
        val ready = assertIs<HrnsUiState.Ready>(viewModel.state.value)
        assertEquals("요청을 저장했습니다.", ready.todayWork.requestInboxNotice)
        assertTrue(ready.todayWork.requestSaveSucceeded)
        viewModel.dispose()
    }

    @Test
    fun `요청 저장이 충돌하면 실패 문구 대신 충돌 안내를 보인다`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val project = harnessProject("a", "S:\\project-a")
        val registry = FakeProjectRegistryPort(initialProjects = listOf(project), initialActiveId = project.id)
        val statePort = FakeStatePort {
            StateReadResult.Success(requestIntakeState(), FileVersion(fixedInstant, 1, "request"))
        }
        val requestWriter = object : RequestWriterPort {
            override fun load(day: WorkspaceDay): LoadedRequest =
                LoadedRequest("# REQUEST_INBOX\n", FileVersion(fixedInstant, 10, "hash"))

            override fun save(day: WorkspaceDay, content: String, expectedVersion: FileVersion): RequestSaveResult =
                RequestSaveResult.Conflict(FileVersion(fixedInstant, 99, "other-hash"))
        }
        val viewModel = newViewModel(
            statePort,
            dispatcher,
            registry = registry,
            boundaryResolver = ::validBoundary,
            compatibilityPort = { KitVersionReadResult.Success(supportedManifest()) },
            requestWriter = requestWriter,
        )
        runCurrent()

        viewModel.onEvent(
            HrnsUiEvent.RequestEntrySubmitted(
                RequestEntryDraft(
                    title = "제목",
                    type = RequestEntryType.Idea,
                    source = RequestEntrySource.Human,
                    priority = RequestEntryPriority.Unknown,
                    summary = "요약",
                    detail = "",
                    constraints = "",
                ),
            ),
        )
        runCurrent()

        val ready = assertIs<HrnsUiState.Ready>(viewModel.state.value)
        assertEquals("다른 곳에서 파일이 변경되어 저장하지 못했습니다. 초안은 유지됩니다. 다시 시도하면 최신 내용을 다시 읽으며, 필요하면 REQUEST_INBOX.md를 확인해 수동 병합하세요.", ready.todayWork.requestInboxNotice)
        assertFalse(ready.todayWork.requestSaveSucceeded)
        viewModel.dispose()
    }

    @Test
    fun `실행 도중 프로젝트를 전환하면 늦게 끝난 실행 결과가 새 프로젝트 상태를 덮지 않는다`() = runBlocking {
        val ioExecutor = Executors.newFixedThreadPool(2) { runnable -> Thread(runnable, "run-switch-io") }
        val mainExecutor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "run-switch-main") }
        val ioDispatcher = ioExecutor.asCoroutineDispatcher()
        val mainDispatcher = mainExecutor.asCoroutineDispatcher()

        val projectA = harnessProject("a", "S:\\project-a")
        val projectB = harnessProject("b", "S:\\project-b")
        val registry = FakeProjectRegistryPort(initialProjects = listOf(projectA, projectB), initialActiveId = projectA.id)
        val statePort = FakeStatePort { StateReadResult.Missing(Path.of("WORKFLOW_STATE.json")) }

        val runEntered = CountDownLatch(1)
        val releaseRun = CountDownLatch(1)
        val runner = HarnessRunnerPort { _, _, _ ->
            runEntered.countDown()
            assertTrue(releaseRun.await(5, TimeUnit.SECONDS))
            ProcessRunResult.Completed(0, null, "project-a-late-result", false, false)
        }

        val loadCockpit = LoadCockpitUseCase(
            pathProbe = { probeSummary() },
            readinessProvider = { _, _ -> readiness() },
            artifactProbe = { _, _ -> WorkspaceArtifactSummary(emptyList()) },
            dayDiscovery = { emptyList() },
            daySelectionPolicy = WorkspaceDaySelectionPolicy(LocalDate.of(2026, 6, 26)),
            statePort = statePort,
        )
        val viewModel = AppViewModel(
            loadCockpit = loadCockpit,
            changeProbe = { null },
            resolveActiveProject = ResolveActiveProjectUseCase(registry) { workspaceConfig() },
            loadProjects = LoadProjectsUseCase(registry),
            registerProject = RegisterProjectUseCase(
                pathResolver = { RootPathCheck.Invalid(PathIssue.NotProvided) },
                registry = registry,
            ),
            selectProject = SelectProjectUseCase(registry),
            selectWorkspaceDay = SelectWorkspaceDayUseCase(registry),
            deleteProject = DeleteProjectUseCase(registry),
            boundaryPathResolver = { RootPathCheck.Invalid(PathIssue.NotProvided) },
            compatibilityPort = { KitVersionReadResult.Missing },
            harnessRunner = runner,
            processLock = FakeProcessLockPort(),
            executeHarnessAction = ExecuteHarnessActionUseCase(
                actionPolicy = ActionPolicy(),
                commandMapper = HarnessCommandMapper(),
                processLock = FakeProcessLockPort(),
                harnessRunner = runner,
                workflowState = statePort,
            ),
            saveRequest = SaveRequestUseCase(FakeRequestWriterPort()),
            todayStrategyReader = TodayStrategyReaderPort { null },
            gitStatusPort = GitStatusPort { RepositoryStatus.Clean },
            ioDispatcher = ioDispatcher,
            pollIntervalMillis = 60_000L,
            clock = { fixedInstant },
            mainDispatcher = mainDispatcher,
        )

        try {
            withTimeout(5_000) {
                viewModel.state.filterIsInstance<HrnsUiState.Ready>()
                    .first { ready -> ready.registryProjects.firstOrNull { it.isActive }?.id == projectA.id }
            }

            viewModel.onEvent(HrnsUiEvent.ActionRequested(UiAction.RunDoctor))
            assertTrue(runEntered.await(5, TimeUnit.SECONDS))

            viewModel.onEvent(HrnsUiEvent.ProjectSelected(projectB.id))
            val readyB = withTimeout(5_000) {
                viewModel.state.filterIsInstance<HrnsUiState.Ready>()
                    .first { ready -> ready.registryProjects.firstOrNull { it.isActive }?.id == projectB.id }
            }
            assertTrue(readyB.runStatus.consoleLines.any { it.contains("대기") })

            releaseRun.countDown()
            mainExecutor.submit {}.get(5, TimeUnit.SECONDS)
            val stillB = assertIs<HrnsUiState.Ready>(viewModel.state.value)
            assertTrue(stillB.runStatus.consoleLines.any { it.contains("대기") })
            assertFalse(stillB.runStatus.consoleLines.any { it.contains("project-a-late-result") })
            assertTrue(stillB.registryProjects.firstOrNull { it.isActive }?.id == projectB.id)
        } finally {
            releaseRun.countDown()
            viewModel.dispose()
            ioDispatcher.close()
            mainDispatcher.close()
            ioExecutor.shutdownNow()
            mainExecutor.shutdownNow()
        }
    }
    @Test
    fun `UI 밖 State 변경은 새 Doctor 실행을 보류하고 새로고침 후에만 해제한다`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val project = harnessProject("a", "S:\\project-a")
        val registry = FakeProjectRegistryPort(initialProjects = listOf(project), initialActiveId = project.id)
        val statePort = FakeStatePort { StateReadResult.Missing(Path.of("WORKFLOW_STATE.json")) }
        var mtime = FileTime.fromMillis(0)
        val executions = AtomicInteger(0)
        val viewModel = newViewModel(
            statePort = statePort,
            dispatcher = dispatcher,
            changeProbe = { mtime },
            pollIntervalMillis = 100L,
            registry = registry,
            harnessRunner = HarnessRunnerPort { _, _, _ ->
                executions.incrementAndGet()
                ProcessRunResult.Completed(0, null, null, false, false)
            },
        )
        runCurrent()

        mtime = FileTime.fromMillis(1)
        advanceTimeBy(101L)
        runCurrent()
        viewModel.onEvent(HrnsUiEvent.ActionRequested(UiAction.RunDoctor))
        runCurrent()

        val held = assertIs<HrnsUiState.Ready>(viewModel.state.value)
        assertEquals(0, executions.get())
        assertTrue(held.runStatus.consoleLines.any { it.contains("외부 실행 가능성") })

        viewModel.refresh()
        runCurrent()
        viewModel.onEvent(HrnsUiEvent.ActionRequested(UiAction.RunDoctor))
        runCurrent()

        assertEquals(1, executions.get())
        viewModel.dispose()
    }
    @Test
    fun `CTA policy rejects a harness action even when a stale event is delivered`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val project = harnessProject("a", "S:\\project-a")
        val registry = FakeProjectRegistryPort(initialProjects = listOf(project), initialActiveId = project.id)
        val executions = AtomicInteger(0)
        val viewModel = newViewModel(
            statePort = FakeStatePort { StateReadResult.Missing(Path.of("WORKFLOW_STATE.json")) },
            dispatcher = dispatcher,
            registry = registry,
            harnessRunner = HarnessRunnerPort { _, _, _ ->
                executions.incrementAndGet()
                ProcessRunResult.Completed(0, null, null, false, false)
            },
        )
        runCurrent()

        viewModel.onEvent(HrnsUiEvent.ActionRequested(UiAction.RunPlanning))
        runCurrent()

        assertEquals(0, executions.get())
        val ready = assertIs<HrnsUiState.Ready>(viewModel.state.value)
        assertTrue(ready.runStatus.consoleLines.any { it.contains("허용되지 않은 실행") })
        viewModel.dispose()
    }

    @Test
    fun `past workspace day rejects a request event before the writer is called`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val today = LocalDate.of(2026, 6, 26)
        val past = LocalDate.of(2026, 6, 25)
        val project = harnessProject("a", "S:\\project-a")
        val registry = FakeProjectRegistryPort(initialProjects = listOf(project), initialActiveId = project.id)
        val writerCalls = AtomicInteger(0)
        val writer = object : RequestWriterPort {
            override fun load(day: WorkspaceDay): LoadedRequest? {
                writerCalls.incrementAndGet()
                return null
            }

            override fun save(day: WorkspaceDay, content: String, expectedVersion: FileVersion): RequestSaveResult {
                writerCalls.incrementAndGet()
                return RequestSaveResult.Saved
            }
        }
        val viewModel = newViewModel(
            statePort = FakeStatePort {
                StateReadResult.Success(requestIntakeState(), FileVersion(fixedInstant, 1, "request"))
            },
            dispatcher = dispatcher,
            registry = registry,
            availableDates = listOf(past, today),
            boundaryResolver = ::validBoundary,
            compatibilityPort = { KitVersionReadResult.Success(supportedManifest()) },
            requestWriter = writer,
        )
        runCurrent()
        viewModel.onEvent(HrnsUiEvent.WorkspaceDaySelected(past))
        runCurrent()

        viewModel.onEvent(
            HrnsUiEvent.RequestEntrySubmitted(
                RequestEntryDraft("past", RequestEntryType.Bug, RequestEntrySource.Human, RequestEntryPriority.Low, "summary", "", ""),
            ),
        )
        runCurrent()

        assertEquals(0, writerCalls.get())
        val ready = assertIs<HrnsUiState.Ready>(viewModel.state.value)
        assertTrue(ready.cockpit.isReadOnlyDay)
        assertFalse(ready.todayWork.requestEditingEnabled)
        assertFalse(ready.todayWork.requestSaveSucceeded)
        viewModel.dispose()
    }
    @Test
    fun `past workspace day still allows the read-only Doctor diagnostic`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val today = LocalDate.of(2026, 6, 26)
        val past = LocalDate.of(2026, 6, 25)
        val project = harnessProject("a", "S:\\project-a")
        val registry = FakeProjectRegistryPort(initialProjects = listOf(project), initialActiveId = project.id)
        val executions = AtomicInteger(0)
        val viewModel = newViewModel(
            statePort = FakeStatePort {
                StateReadResult.Success(requestIntakeState(), FileVersion(fixedInstant, 1, "request"))
            },
            dispatcher = dispatcher,
            registry = registry,
            availableDates = listOf(past, today),
            boundaryResolver = ::validBoundary,
            compatibilityPort = { KitVersionReadResult.Success(supportedManifest()) },
            harnessRunner = HarnessRunnerPort { command, _, _ ->
                assertIs<HarnessCommand.Doctor>(command)
                executions.incrementAndGet()
                ProcessRunResult.Completed(0, null, null, false, false)
            },
        )
        runCurrent()
        viewModel.onEvent(HrnsUiEvent.WorkspaceDaySelected(past))
        runCurrent()

        viewModel.onEvent(HrnsUiEvent.ActionRequested(UiAction.RunDoctor))
        runCurrent()

        assertEquals(1, executions.get())
        viewModel.dispose()
    }
}
