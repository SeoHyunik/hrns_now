package io.hrns_now.core.usecase

import io.hrns_now.core.domain.model.ArtifactKind
import io.hrns_now.core.domain.model.ArtifactProbeResult
import io.hrns_now.core.domain.model.ArtifactProbeState
import io.hrns_now.core.domain.model.ArtifactRequirement
import io.hrns_now.core.domain.model.BridgeFileState
import io.hrns_now.core.domain.model.HarnessCommand
import io.hrns_now.core.domain.model.HarnessCommandKind
import io.hrns_now.core.domain.model.HarnessProject
import io.hrns_now.core.domain.model.LockAcquireResult
import io.hrns_now.core.domain.model.LockHandle
import io.hrns_now.core.domain.model.LockReleaseResult
import io.hrns_now.core.domain.model.ProcessCancellationToken
import io.hrns_now.core.domain.model.ProjectId
import io.hrns_now.core.domain.model.RepositoryBridgeSummary
import io.hrns_now.core.domain.model.RuntimeSource
import io.hrns_now.core.domain.model.WorkspaceArtifactSummary
import io.hrns_now.core.domain.model.WorkspaceDay
import io.hrns_now.core.port.HarnessRunnerPort
import io.hrns_now.core.port.ProcessLockPort
import io.hrns_now.core.port.RepositoryBridgeProbePort
import io.hrns_now.core.port.WorkflowStatePort
import io.hrns_now.core.result.ProcessRunResult
import io.hrns_now.core.result.StateReadResult
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * `enter-project.ps1` → `validate-ops.ps1 -Json` → bridge probe → 4-file probe →
 * State 재조회를 **하나의 lock** 안에서 순서대로 수행하는지 고정한다(Phase 10). daily
 * `ActionPolicy`/`ExecuteHarnessActionUseCase`와 완전히 분리된 별도 lifecycle이다.
 */
class OnboardProjectUseCaseTest {
    private val day = WorkspaceDay(Path.of("C:/workspace"), LocalDate.of(2026, 7, 27))
    private val project = HarnessProject(
        id = ProjectId("sample"),
        displayName = "sample",
        runtimeSource = RuntimeSource.ExternalKit(Path.of("C:/kit")),
        projectWorkspaceRoot = Path.of("C:/workspace"),
        repositoryRoot = Path.of("C:/repo"),
        profileId = "corp-default",
        lastSelectedDate = null,
        lastDiagnosticsSummary = null,
        lastRunAt = null,
    )
    private val readyBridge = RepositoryBridgeSummary(
        settingsLocalJson = BridgeFileState.Ready,
        projectClaudeMd = BridgeFileState.Ready,
        toolsRunCycle = BridgeFileState.Ready,
    )
    private val readyArtifacts = WorkspaceArtifactSummary(
        items = listOf(
            ArtifactProbeResult(
                label = "REQUEST_INBOX.md",
                path = "C:/workspace/2026-07-27/REQUEST_INBOX.md",
                kind = ArtifactKind.File,
                requirement = ArtifactRequirement.Required,
                state = ArtifactProbeState.Exists,
                message = "ok",
            ),
        ),
    )

    private fun context() = OnboardProjectContext(
        project = project,
        resolvedKitRoot = Path.of("C:/kit"),
        day = day,
    )

    private fun fakeLock(
        onAcquire: () -> LockAcquireResult = { LockAcquireResult.Acquired(LockHandle(project.id, day.date, 1L, Instant.EPOCH)) },
        onRelease: () -> Unit = {},
    ) = object : ProcessLockPort {
        override suspend fun acquire(projectId: ProjectId, date: LocalDate, commandKind: HarnessCommandKind) = onAcquire()
        override suspend fun heartbeat(handle: LockHandle) = true
        override suspend fun release(handle: LockHandle): LockReleaseResult {
            onRelease()
            return LockReleaseResult.Released
        }
        override suspend fun inspect(projectId: ProjectId, date: LocalDate) = null
        override suspend fun forceRelease(projectId: ProjectId, date: LocalDate) = LockReleaseResult.Released
    }

    @Test
    fun `enter-project 실행 후 validate-ops를 이어서 실행하고 순서를 지킨다`() = runBlocking {
        val executedKinds = mutableListOf<HarnessCommandKind>()
        val runner = HarnessRunnerPort { command, _, _ ->
            executedKinds += command.kind
            ProcessRunResult.Completed(0, null, null, false, false)
        }
        val state = object : WorkflowStatePort {
            override fun read(day: WorkspaceDay): StateReadResult =
                StateReadResult.Missing(day.dayRoot.resolve("WORKFLOW_STATE.json"))
        }
        val bridgeProbe = RepositoryBridgeProbePort { readyBridge }

        val useCase = OnboardProjectUseCase(
            processLock = fakeLock(),
            harnessRunner = runner,
            workflowState = state,
            bridgeProbe = bridgeProbe,
            artifactProbe = { _, _ -> readyArtifacts },
        )

        val outcome = useCase.invoke(context(), Duration.ofSeconds(1), ProcessCancellationToken())

        val completed = assertIs<OnboardProjectOutcome.Completed>(outcome)
        assertEquals(listOf(HarnessCommandKind.OnboardProject, HarnessCommandKind.ValidateOps), executedKinds)
        assertIs<ProcessRunResult.Completed>(completed.onboardResult)
        assertIs<ProcessRunResult.Completed>(completed.validateOpsResult)
        assertIs<StateReadResult.Missing>(completed.refreshedState)
        assertEquals(readyBridge, completed.bridgeSummary)
        assertTrue(completed.artifactSummary.isRequiredReady)
    }

    @Test
    fun `lock을 얻지 못하면 harnessRunner bridgeProbe state 어느 것도 호출하지 않는다`() = runBlocking {
        var runnerCalls = 0
        var bridgeCalls = 0
        var artifactCalls = 0
        var stateCalls = 0
        val lock = fakeLock(onAcquire = { LockAcquireResult.Failed("must not run") })
        val runner = HarnessRunnerPort { _, _, _ ->
            runnerCalls += 1
            ProcessRunResult.StartFailed("must not run")
        }
        val state = object : WorkflowStatePort {
            override fun read(day: WorkspaceDay): StateReadResult {
                stateCalls += 1
                return StateReadResult.Missing(day.dayRoot.resolve("WORKFLOW_STATE.json"))
            }
        }
        val bridgeProbe = RepositoryBridgeProbePort {
            bridgeCalls += 1
            readyBridge
        }

        val outcome = useCaseWith(lock, runner, state, bridgeProbe) { _, _ -> artifactCalls += 1; readyArtifacts }
            .invoke(context(), Duration.ofSeconds(1), ProcessCancellationToken())

        assertIs<OnboardProjectOutcome.LockUnavailable>(outcome)
        assertEquals(0, runnerCalls)
        assertEquals(0, bridgeCalls)
        assertEquals(0, artifactCalls)
        assertEquals(0, stateCalls)
    }

    @Test
    fun `State 재조회는 lock을 보유한 채 일어나고 그 뒤에만 release한다`() = runBlocking {
        var lockHeld = false
        val handle = LockHandle(project.id, day.date, 1L, Instant.EPOCH)
        val lock = object : ProcessLockPort {
            override suspend fun acquire(projectId: ProjectId, date: LocalDate, commandKind: HarnessCommandKind): LockAcquireResult {
                lockHeld = true
                return LockAcquireResult.Acquired(handle)
            }
            override suspend fun heartbeat(handle: LockHandle) = true
            override suspend fun release(handle: LockHandle): LockReleaseResult {
                lockHeld = false
                return LockReleaseResult.Released
            }
            override suspend fun inspect(projectId: ProjectId, date: LocalDate) = null
            override suspend fun forceRelease(projectId: ProjectId, date: LocalDate) = LockReleaseResult.Released
        }
        val runner = HarnessRunnerPort { _, _, _ -> ProcessRunResult.Completed(0, null, null, false, false) }
        val state = object : WorkflowStatePort {
            override fun read(day: WorkspaceDay): StateReadResult {
                assertTrue(lockHeld, "State reread must happen before lock release")
                return StateReadResult.Missing(day.dayRoot.resolve("WORKFLOW_STATE.json"))
            }
        }
        val bridgeProbe = RepositoryBridgeProbePort {
            assertTrue(lockHeld, "Bridge probe must happen before lock release")
            readyBridge
        }

        useCaseWith(lock, runner, state, bridgeProbe) { _, _ -> readyArtifacts }
            .invoke(context(), Duration.ofSeconds(1), ProcessCancellationToken())

        assertFalse(lockHeld)
    }

    @Test
    fun `validate-ops 실행 중 예외가 나도 lock은 반드시 release한다`() = runBlocking {
        var released = false
        val lock = fakeLock(onRelease = { released = true })
        var calls = 0
        val runner = HarnessRunnerPort { command, _, _ ->
            calls += 1
            if (command.kind == HarnessCommandKind.ValidateOps) {
                error("boom")
            }
            ProcessRunResult.Completed(0, null, null, false, false)
        }
        val state = object : WorkflowStatePort {
            override fun read(day: WorkspaceDay): StateReadResult =
                StateReadResult.Missing(day.dayRoot.resolve("WORKFLOW_STATE.json"))
        }
        val bridgeProbe = RepositoryBridgeProbePort { readyBridge }

        val exception = runCatching {
            useCaseWith(lock, runner, state, bridgeProbe) { _, _ -> readyArtifacts }
                .invoke(context(), Duration.ofSeconds(1), ProcessCancellationToken())
        }.exceptionOrNull()

        assertTrue(exception != null)
        assertTrue(released, "lock must be released even when the second command throws")
        assertEquals(2, calls)
    }

    @Test
    fun `lock 해제 직전 UI callback이 예외를 내도 lock은 반드시 release된다`() = runBlocking {
        var released = false
        val lock = fakeLock(onRelease = { released = true })
        val runner = HarnessRunnerPort { _, _, _ -> ProcessRunResult.Completed(0, null, null, false, false) }
        val state = object : WorkflowStatePort {
            override fun read(day: WorkspaceDay): StateReadResult =
                StateReadResult.Missing(day.dayRoot.resolve("WORKFLOW_STATE.json"))
        }

        val exception = runCatching {
            useCaseWith(lock, runner, state, RepositoryBridgeProbePort { readyBridge }) { _, _ -> readyArtifacts }
                .invoke(
                    context(),
                    Duration.ofSeconds(1),
                    ProcessCancellationToken(),
                    onBeforeLockRelease = { error("UI cleanup failed") },
                )
        }.exceptionOrNull()

        assertTrue(exception != null)
        assertTrue(released, "lock must be released even when UI cleanup throws")
    }

    private fun useCaseWith(
        lock: ProcessLockPort,
        runner: HarnessRunnerPort,
        state: WorkflowStatePort,
        bridgeProbe: RepositoryBridgeProbePort,
        artifactProbe: (String?, LocalDate) -> WorkspaceArtifactSummary,
    ) = OnboardProjectUseCase(lock, runner, state, bridgeProbe, artifactProbe)
}
