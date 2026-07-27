package io.hrns_now.core.usecase

import io.hrns_now.core.domain.model.ActionContext
import io.hrns_now.core.domain.model.BoundaryStatus
import io.hrns_now.core.domain.model.HarnessCommand
import io.hrns_now.core.domain.model.HarnessCommandKind
import io.hrns_now.core.domain.model.HarnessProject
import io.hrns_now.core.domain.model.CompatibilityStatus
import io.hrns_now.core.domain.model.LockAcquireResult
import io.hrns_now.core.domain.model.LockHandle
import io.hrns_now.core.domain.model.LockReleaseResult
import io.hrns_now.core.domain.model.ProcessCancellationToken
import io.hrns_now.core.domain.model.ProcessRunStatus
import io.hrns_now.core.domain.model.ProjectId
import io.hrns_now.core.domain.model.SelectedDayKind
import io.hrns_now.core.domain.model.UiAction
import io.hrns_now.core.domain.model.WorkspaceDay
import io.hrns_now.core.domain.policy.ActionPolicy
import io.hrns_now.core.port.HarnessRunnerPort
import io.hrns_now.core.port.ProcessLockPort
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ExecuteHarnessActionUseCaseTest {
    private val day = WorkspaceDay(Path.of("C:/workspace"), LocalDate.of(2026, 7, 27))
    private val project = HarnessProject(
        id = ProjectId("sample"),
        displayName = "sample",
        kitRoot = Path.of("C:/kit"),
        projectWorkspaceRoot = Path.of("C:/workspace"),
        repositoryRoot = Path.of("C:/repo"),
        profileId = "corp-default",
        lastSelectedDate = null,
        lastDiagnosticsSummary = null,
        lastRunAt = null,
    )

    @Test
    fun `실행 뒤 State는 own lock을 보유한 채 다시 읽고 release한다`() = runBlocking {
        var lockHeld = false
        var released = false
        var command: HarnessCommand? = null
        val handle = LockHandle(project.id, day.date, 1L, Instant.EPOCH)
        val lock = object : ProcessLockPort {
            override suspend fun acquire(projectId: ProjectId, date: LocalDate, commandKind: HarnessCommandKind) =
                LockAcquireResult.Acquired(handle).also { lockHeld = true }
            override suspend fun heartbeat(handle: LockHandle) = true
            override suspend fun release(handle: LockHandle): LockReleaseResult {
                lockHeld = false
                released = true
                return LockReleaseResult.Released
            }
            override suspend fun inspect(projectId: ProjectId, date: LocalDate) = null
            override suspend fun forceRelease(projectId: ProjectId, date: LocalDate) = LockReleaseResult.Released
        }
        val runner = HarnessRunnerPort { actual, _, _ ->
            command = actual
            ProcessRunResult.Completed(0, null, null, false, false)
        }
        val state = object : WorkflowStatePort {
            override fun read(day: WorkspaceDay): StateReadResult {
                assertTrue(lockHeld, "State reread must happen before lock release")
                return StateReadResult.Missing(day.dayRoot.resolve("WORKFLOW_STATE.json"))
            }
        }

        val outcome = useCase(lock, runner, state).invoke(
            context = doctorContext(),
            action = UiAction.RunDoctor,
            timeout = Duration.ofSeconds(1),
            cancellationToken = ProcessCancellationToken(),
        )

        assertIs<ExecuteHarnessActionOutcome.Completed>(outcome)
        assertIs<HarnessCommand.Doctor>(command)
        assertTrue(released)
        assertEquals(false, lockHeld)
    }

    @Test
    fun `정책이 거부한 action은 command lock runner에 도달하지 않는다`() = runBlocking {
        var lockCalls = 0
        var runnerCalls = 0
        val lock = object : ProcessLockPort {
            override suspend fun acquire(projectId: ProjectId, date: LocalDate, commandKind: HarnessCommandKind): LockAcquireResult {
                lockCalls += 1
                return LockAcquireResult.Failed("must not acquire")
            }
            override suspend fun heartbeat(handle: LockHandle) = false
            override suspend fun release(handle: LockHandle) = LockReleaseResult.Released
            override suspend fun inspect(projectId: ProjectId, date: LocalDate) = null
            override suspend fun forceRelease(projectId: ProjectId, date: LocalDate) = LockReleaseResult.Released
        }
        val runner = HarnessRunnerPort { _, _, _ ->
            runnerCalls += 1
            ProcessRunResult.StartFailed("must not run")
        }
        val state = object : WorkflowStatePort {
            override fun read(day: WorkspaceDay): StateReadResult =
                StateReadResult.Missing(day.dayRoot.resolve("WORKFLOW_STATE.json"))
        }

        val outcome = useCase(lock, runner, state).invoke(
            context = doctorContext(),
            action = UiAction.RunPlanning,
            timeout = Duration.ofSeconds(1),
            cancellationToken = ProcessCancellationToken(),
        )

        assertIs<ExecuteHarnessActionOutcome.Rejected>(outcome)
        assertEquals(0, lockCalls)
        assertEquals(0, runnerCalls)
    }

    private fun useCase(
        lock: ProcessLockPort,
        runner: HarnessRunnerPort,
        state: WorkflowStatePort,
    ) = ExecuteHarnessActionUseCase(ActionPolicy(), HarnessCommandMapper(), lock, runner, state)

    private fun doctorContext() = HarnessExecutionContext(
        project = project,
        day = day,
        actionContext = ActionContext(
            projectConnected = true,
            selectedDayKind = SelectedDayKind.Today,
            stateRead = StateReadResult.Missing(day.dayRoot.resolve("WORKFLOW_STATE.json")),
            compatibility = CompatibilityStatus.Unknown,
            boundary = BoundaryStatus.Unknown,
            process = ProcessRunStatus.Idle,
            activeSliceKind = null,
        ),
    )
}