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
import io.hrns_now.core.domain.model.ExecutionWrapperState
import io.hrns_now.core.domain.model.FileVersion
import io.hrns_now.core.domain.model.OpsValidationState
import io.hrns_now.core.domain.model.QueuePointer
import io.hrns_now.core.domain.model.QueueStatus
import io.hrns_now.core.domain.model.SchemaVersion
import io.hrns_now.core.domain.model.UiAction
import io.hrns_now.core.domain.model.WorkflowPhase
import io.hrns_now.core.domain.model.WorkflowQueue
import io.hrns_now.core.domain.model.WorkflowState
import io.hrns_now.core.domain.model.WorkflowStatus
import io.hrns_now.core.domain.model.WorkspaceArtifactSummary
import io.hrns_now.core.domain.model.WorkspaceDay
import io.hrns_now.core.domain.policy.WorkspaceDaySelectionPolicy
import io.hrns_now.core.port.WorkflowStatePort
import io.hrns_now.core.result.StateReadResult
import io.hrns_now.core.usecase.LoadCockpitUseCase
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

    private class FakeStatePort(
        private val result: (callIndex: Int) -> StateReadResult,
    ) : WorkflowStatePort {
        val callCount = AtomicInteger(0)
        override fun read(day: WorkspaceDay): StateReadResult = result(callCount.incrementAndGet())
    }

    private fun loadUseCase(
        statePort: WorkflowStatePort,
        recordThread: (() -> Unit)? = null,
    ): LoadCockpitUseCase = LoadCockpitUseCase(
        workspaceConfig = workspaceConfig(),
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
            emptyList()
        },
        daySelectionPolicy = WorkspaceDaySelectionPolicy(LocalDate.of(2026, 6, 26)),
        statePort = statePort,
    )

    private fun newViewModel(
        statePort: WorkflowStatePort,
        dispatcher: CoroutineDispatcher,
        changeProbe: (WorkspaceDay) -> FileTime? = { null },
        pollIntervalMillis: Long = 3000L,
    ): AppViewModel = AppViewModel(
        loadCockpit = loadUseCase(statePort),
        changeProbe = changeProbe,
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
        val viewModel = AppViewModel(
            loadCockpit = loadUseCase(statePort, ::recordThread),
            changeProbe = {
                recordThread()
                null
            },
            ioDispatcher = ioDispatcher,
            pollIntervalMillis = 60_000L,
            clock = { fixedInstant },
            mainDispatcher = mainDispatcher,
        )

        try {
            withTimeout(5_000) { viewModel.state.filterIsInstance<HrnsUiState.Ready>().first() }
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
        val viewModel = AppViewModel(
            loadCockpit = loadUseCase(statePort),
            changeProbe = { FileTime.fromMillis(1) },
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
}
