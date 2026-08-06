package io.hrns_now.core.usecase

import io.hrns_now.core.domain.model.ActionContext
import io.hrns_now.core.domain.model.BlockedReasonKey
import io.hrns_now.core.domain.model.ExecutionWrapper
import io.hrns_now.core.domain.model.HarnessCommand
import io.hrns_now.core.domain.model.HarnessProject
import io.hrns_now.core.domain.model.LockAcquireResult
import io.hrns_now.core.domain.model.LockHandle
import io.hrns_now.core.domain.model.PlanningReason
import io.hrns_now.core.domain.model.ProcessCancellationToken
import io.hrns_now.core.domain.model.ReplanReason
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

/**
 * 실행 직전의 순수 policy 입력과 command 경계에 필요한 project/day를 묶은 값이다. [resolvedKitRoot]는
 * `project.runtimeSource`를 이미 `RuntimeResolution.Resolved`로 해석한 결과다 —
 * command mapper/encoder는 `runtimeSource` 자체나 internal/external 분기를 알지 못하고 이 값만 쓴다.
 */
data class HarnessExecutionContext(
    val project: HarnessProject,
    val resolvedKitRoot: Path,
    val day: WorkspaceDay,
    val actionContext: ActionContext,
)

/**
 * typed [UiAction]을 Harness command·lock·process·State reread 순서로 조정한다.
 *
 * State reread는 release 전에 실행해 stdout 성공 문구가 아닌 `WORKFLOW_STATE.json`이 실행 후
 * 판단의 근거가 되게 한다. heartbeat 표시는 lifecycle을 소유하는 ViewModel이 callback으로 연결한다.
 */
class ExecuteHarnessActionUseCase(
    private val actionPolicy: ActionPolicy,
    private val commandMapper: HarnessCommandMapper,
    private val processLock: ProcessLockPort,
    private val harnessRunner: HarnessRunnerPort,
    private val workflowState: WorkflowStatePort,
) {
    suspend fun invoke(
        context: HarnessExecutionContext,
        action: UiAction,
        timeout: Duration,
        cancellationToken: ProcessCancellationToken,
        onLockAcquired: suspend (LockHandle) -> Unit = {},
        onBeforeLockRelease: suspend (LockHandle) -> Unit = {},
    ): ExecuteHarnessActionOutcome {
        val recommended = actionPolicy.recommend(context.actionContext)
        if (action !in recommended.allowed) {
            return ExecuteHarnessActionOutcome.Rejected(recommended.reasonKey)
        }

        val command = commandMapper.map(action, context.project, context.resolvedKitRoot, context.day)
            ?: return ExecuteHarnessActionOutcome.UnsupportedAction(action)
        val lockResult = processLock.acquire(context.project.id, context.day.date, command.kind)
        val acquired = lockResult as? LockAcquireResult.Acquired
            ?: return ExecuteHarnessActionOutcome.LockUnavailable(lockResult)

        return try {
            onLockAcquired(acquired.handle)
            val processResult = harnessRunner.execute(command, timeout, cancellationToken)
            val refreshedState = workflowState.read(context.day)
            ExecuteHarnessActionOutcome.Completed(command, processResult, refreshedState)
        } finally {
            onBeforeLockRelease(acquired.handle)
            processLock.release(acquired.handle)
        }
    }
}

sealed interface ExecuteHarnessActionOutcome {
    data class Completed(
        val command: HarnessCommand,
        val processResult: ProcessRunResult,
        /** own lock을 보유한 채 다시 읽은 State다. */
        val refreshedState: StateReadResult,
    ) : ExecuteHarnessActionOutcome

    /** 문구가 아니라 typed key다 — presentation이 locale별 문구로 투영한다. */
    data class Rejected(val reasonKey: BlockedReasonKey?) : ExecuteHarnessActionOutcome
    data class Failed(val processResult: ProcessRunResult) : ExecuteHarnessActionOutcome
    data class LockUnavailable(val result: LockAcquireResult) : ExecuteHarnessActionOutcome
    data class UnsupportedAction(val action: UiAction) : ExecuteHarnessActionOutcome
}

/**
 * typed UI action과 harness-kit의 실제 `run-cycle.ps1` contract를 연결한다. `resolvedKitRoot`는
 * 호출자가 이미 `RuntimeSourceResolverPort`로 해석해 둔 값이다 — 이 mapper는 `project.runtimeSource`나
 * internal/external 구분을 알지 못한다(`doc/hrns_now_design_pattern.md` §20.1).
 */
class HarnessCommandMapper {
    fun map(action: UiAction, project: HarnessProject, resolvedKitRoot: Path, day: WorkspaceDay): HarnessCommand? =
        when (action) {
            UiAction.RunDoctor -> HarnessCommand.Doctor(
                kitRoot = resolvedKitRoot,
                workspaceRoot = project.projectWorkspaceRoot,
                projectRoot = project.repositoryRoot,
                date = day.date,
            )
            UiAction.RunOpsValidation -> HarnessCommand.ValidateOps(
                workspaceRoot = project.projectWorkspaceRoot,
                kitRoot = resolvedKitRoot,
                profile = project.profileId,
                date = day.date,
            )
            UiAction.BootstrapDay -> HarnessCommand.BootstrapDay(
                workspaceRoot = project.projectWorkspaceRoot,
                projectRoot = project.repositoryRoot,
                kitRoot = resolvedKitRoot,
                profile = project.profileId,
                date = day.date,
            )
            UiAction.RunPlanning -> HarnessCommand.RunPlanning(
                workspaceRoot = project.projectWorkspaceRoot,
                projectRoot = project.repositoryRoot,
                kitRoot = resolvedKitRoot,
                profile = project.profileId,
                date = day.date,
                reason = PlanningReason.InitialPlan,
            )
            UiAction.RunReplan -> HarnessCommand.RunReplan(
                workspaceRoot = project.projectWorkspaceRoot,
                projectRoot = project.repositoryRoot,
                kitRoot = resolvedKitRoot,
                profile = project.profileId,
                date = day.date,
                reason = ReplanReason.HumanRequestedReplan,
            )
            UiAction.RunCodeSlice -> HarnessCommand.RunExecution(
                workspaceRoot = project.projectWorkspaceRoot,
                projectRoot = project.repositoryRoot,
                kitRoot = resolvedKitRoot,
                profile = project.profileId,
                date = day.date,
                wrapper = ExecutionWrapper.Code,
            )
            UiAction.RunDocSlice -> HarnessCommand.RunExecution(
                workspaceRoot = project.projectWorkspaceRoot,
                projectRoot = project.repositoryRoot,
                kitRoot = resolvedKitRoot,
                profile = project.profileId,
                date = day.date,
                wrapper = ExecutionWrapper.Doc,
            )
            UiAction.RunClosureValidation -> HarnessCommand.ValidateClosure(
                workspaceRoot = project.projectWorkspaceRoot,
                projectRoot = project.repositoryRoot,
                kitRoot = resolvedKitRoot,
                profile = project.profileId,
                date = day.date,
            )
            else -> null
        }
}