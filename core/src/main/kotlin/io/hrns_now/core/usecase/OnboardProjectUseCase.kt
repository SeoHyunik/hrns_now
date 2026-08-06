package io.hrns_now.core.usecase

import io.hrns_now.core.domain.model.HarnessCommand
import io.hrns_now.core.domain.model.HarnessProject
import io.hrns_now.core.domain.model.LockAcquireResult
import io.hrns_now.core.domain.model.LockHandle
import io.hrns_now.core.domain.model.ProcessCancellationToken
import io.hrns_now.core.domain.model.RepositoryBridgeSummary
import io.hrns_now.core.domain.model.WorkspaceArtifactSummary
import io.hrns_now.core.domain.model.WorkspaceDay
import io.hrns_now.core.port.HarnessRunnerPort
import io.hrns_now.core.port.ProcessLockPort
import io.hrns_now.core.port.RepositoryBridgeProbePort
import io.hrns_now.core.port.WorkflowStatePort
import io.hrns_now.core.result.ProcessRunResult
import io.hrns_now.core.result.StateReadResult
import java.time.Duration
import java.time.LocalDate

/** [OnboardProjectUseCase]가 필요로 하는 최소 입력이다 — daily [ActionPolicy]/[ActionContext]와 섞지 않는다. */
data class OnboardProjectContext(
    val project: HarnessProject,
    val resolvedKitRoot: java.nio.file.Path,
    val day: WorkspaceDay,
)

sealed interface OnboardProjectOutcome {
    /**
     * `enter-project.ps1` + `validate-ops.ps1 -Json`이 모두 실행되고, lock을 보유한 채
     * bridge probe · required 4-file probe · `WORKFLOW_STATE.json` 재조회까지 마친 결과다.
     * 이 값 자체는 "성공"을 뜻하지 않는다 — 호출자가 [io.hrns_now.core.result.HarnessOverallStatus],
     * [RepositoryBridgeSummary.isReady], [WorkspaceArtifactSummary.isRequiredReady],
     * [StateReadResult]가 모두 충족되는지 함께 판단해야 한다(stdout만으로 판단 금지).
     */
    data class Completed(
        val onboardResult: ProcessRunResult,
        val validateOpsResult: ProcessRunResult,
        val bridgeSummary: RepositoryBridgeSummary,
        val artifactSummary: WorkspaceArtifactSummary,
        val refreshedState: StateReadResult,
    ) : OnboardProjectOutcome

    data class LockUnavailable(val result: LockAcquireResult) : OnboardProjectOutcome
}

/**
 * 프로젝트 온보딩(Bridge 준비 + 오늘 workspace 초기화 + 검증 + State 재조회)을 daily
 * `ActionPolicy`와 완전히 분리된 별도 lifecycle로 실행한다. `enter-project.ps1` 실행 →
 * `validate-ops.ps1 -Json` 실행 → repository bridge probe → required 4-file probe →
 * `WORKFLOW_STATE.json` 재조회까지 **하나의 lock을 보유한 채** 수행하고, 그 뒤에만 lock을
 * 해제한다. 등록 직후 자동으로 `BootstrapDay`/`run-cycle`을 호출하지 않는다 — 이 use case는
 * `enter-project`/`validate-ops`만 안다.
 */
class OnboardProjectUseCase(
    private val processLock: ProcessLockPort,
    private val harnessRunner: HarnessRunnerPort,
    private val workflowState: WorkflowStatePort,
    private val bridgeProbe: RepositoryBridgeProbePort,
    private val artifactProbe: (workspaceRoot: String?, date: LocalDate) -> WorkspaceArtifactSummary,
) {
    suspend fun invoke(
        context: OnboardProjectContext,
        timeout: Duration,
        cancellationToken: ProcessCancellationToken,
        onLockAcquired: suspend (LockHandle) -> Unit = {},
        onBeforeLockRelease: suspend (LockHandle) -> Unit = {},
    ): OnboardProjectOutcome {
        val onboardCommand = HarnessCommand.OnboardProject(
            workspaceRoot = context.project.projectWorkspaceRoot,
            projectRoot = context.project.repositoryRoot,
            kitRoot = context.resolvedKitRoot,
            profile = context.project.profileId,
            date = context.day.date,
        )
        val lockResult = processLock.acquire(context.project.id, context.day.date, onboardCommand.kind)
        val acquired = lockResult as? LockAcquireResult.Acquired
            ?: return OnboardProjectOutcome.LockUnavailable(lockResult)

        return try {
            onLockAcquired(acquired.handle)
            val onboardResult = harnessRunner.execute(onboardCommand, timeout, cancellationToken)

            val validateOpsCommand = HarnessCommand.ValidateOps(
                workspaceRoot = context.project.projectWorkspaceRoot,
                kitRoot = context.resolvedKitRoot,
                profile = context.project.profileId,
                date = context.day.date,
            )
            val validateOpsResult = harnessRunner.execute(validateOpsCommand, timeout, cancellationToken)

            val bridgeSummary = bridgeProbe.probe(context.project.repositoryRoot)
            val artifactSummary = artifactProbe(context.project.projectWorkspaceRoot.toString(), context.day.date)
            val refreshedState = workflowState.read(context.day)

            OnboardProjectOutcome.Completed(
                onboardResult = onboardResult,
                validateOpsResult = validateOpsResult,
                bridgeSummary = bridgeSummary,
                artifactSummary = artifactSummary,
                refreshedState = refreshedState,
            )
        } finally {
            // UI lifecycle callback(heartbeat/projection cleanup)이 취소·예외를 내더라도
            // per-machine lock은 반드시 해제해야 한다. 그렇지 않으면 다음 온보딩이 영구적으로
            // Busy로 보일 수 있다.
            try {
                onBeforeLockRelease(acquired.handle)
            } finally {
                processLock.release(acquired.handle)
            }
        }
    }
}
