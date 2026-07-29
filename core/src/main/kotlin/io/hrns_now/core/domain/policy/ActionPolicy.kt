package io.hrns_now.core.domain.policy

import io.hrns_now.core.domain.model.ActionContext
import io.hrns_now.core.domain.model.ActiveSliceKind
import io.hrns_now.core.domain.model.ArtifactReadinessState
import io.hrns_now.core.domain.model.BoundaryStatus
import io.hrns_now.core.domain.model.CompatibilityStatus
import io.hrns_now.core.domain.model.ExecutionWrapperState
import io.hrns_now.core.domain.model.ProcessRunStatus
import io.hrns_now.core.domain.model.QueueBlockedReason
import io.hrns_now.core.domain.model.QueueStatus
import io.hrns_now.core.domain.model.RecommendedActions
import io.hrns_now.core.domain.model.SelectedDayKind
import io.hrns_now.core.domain.model.StopReason
import io.hrns_now.core.domain.model.UiAction
import io.hrns_now.core.domain.model.WorkflowPhase
import io.hrns_now.core.domain.model.WorkflowState
import io.hrns_now.core.domain.model.WorkflowStatus
import io.hrns_now.core.result.StateReadResult

/**
 * [ActionContext]로부터 현재 허용할 단 하나의 primary action과 전체 허용 action set을
 * 결정하는 순수 정책이다(`doc/hrns_now_design_pattern.md` §7).
 *
 * 외부 문자열의 원문은 진단용 domain 값에 보존하되, 이 정책의 사용자 표시 문구에는
 * 포함하지 않는다. unknown·불일치·불완전한 실행 계약은 모두 fail-closed 처리한다.
 */
class ActionPolicy {

    fun recommend(context: ActionContext): RecommendedActions {
        if (!context.projectConnected) {
            return RecommendedActions(
                primary = UiAction.ConnectProject,
                allowed = setOf(UiAction.ConnectProject),
                blockedReason = "프로젝트가 연결되지 않았습니다.",
            )
        }

        val selectedDayKind = context.selectedDayKind
            ?: return RecommendedActions(
                primary = UiAction.SelectWorkspaceDay,
                allowed = setOf(UiAction.SelectWorkspaceDay, UiAction.RunDoctor, UiAction.RunOpsValidation),
                blockedReason = "날짜가 선택되지 않았습니다.",
            )

        val stateRead = context.stateRead
        if (stateRead !is StateReadResult.Success) {
            if (bootstrapEligible(stateRead, context)) {
                return RecommendedActions(
                    primary = UiAction.BootstrapDay,
                    allowed = setOf(UiAction.BootstrapDay, UiAction.Refresh),
                    blockedReason = null,
                )
            }
            return RecommendedActions(
                primary = UiAction.OpenRecoveryCenter,
                allowed = setOf(
                    UiAction.OpenRecoveryCenter,
                    UiAction.Refresh,
                    UiAction.RunDoctor,
                    UiAction.RunOpsValidation,
                ),
                blockedReason = stateInvalidReason(stateRead),
            )
        }
        val state = stateRead.state

        if (context.compatibility != CompatibilityStatus.Supported) {
            return RecommendedActions(
                primary = UiAction.ShowCompatibilityIssue,
                allowed = setOf(UiAction.ShowCompatibilityIssue, UiAction.Refresh),
                blockedReason = "지원하지 않거나 확인되지 않은 Harness 계약입니다.",
            )
        }

        if (context.boundary != BoundaryStatus.Valid) {
            return recovery("작업공간·저장소·Kit 경계를 확인할 수 없습니다.")
        }

        if (selectedDayKind == SelectedDayKind.Past) {
            return RecommendedActions(
                primary = UiAction.OpenToday,
                allowed = setOf(UiAction.OpenToday, UiAction.Refresh, UiAction.RunDoctor, UiAction.RunOpsValidation),
                blockedReason = "과거 날짜는 읽기 전용입니다.",
            )
        }

        if (context.process != ProcessRunStatus.Idle) {
            return RecommendedActions(
                primary = UiAction.ViewExecutionStatus,
                allowed = setOf(UiAction.ViewExecutionStatus, UiAction.Refresh),
                blockedReason = when (context.process) {
                    ProcessRunStatus.Locked -> "다른 실행이 잠금을 보유하고 있습니다."
                    ProcessRunStatus.Running -> "다른 실행이 진행 중입니다."
                    ProcessRunStatus.Idle -> error("unreachable — process != Idle 분기 내부")
                },
            )
        }

        unknownDomainReason(state, context.activeSliceKind)?.let { reason ->
            return recovery(reason)
        }

        return normalActions(state, context.activeSliceKind)
    }

    /**
     * 오늘 날짜에 State가 아직 없는 새 workspace day는 `run-cycle.ps1`이 `init-workspace`로
     * `WORKFLOW_STATE.json`을 새로 만드는 fresh-day bootstrap 경로다(live 계약 확인:
     * `scripts/run-cycle.ps1`의 "Fresh-day bootstrap note" 주석 — 새 날짜 폴더에서
     * `WORKFLOW_STATE.json`은 `init-workspace` 이후에 생성된다). Malformed/UnsupportedSchema/
     * EncodingError/AccessDenied나 과거 날짜, compatibility/boundary 미확인, 실행 중/lock 상태는
     * 모두 fail-closed로 이 경로에서 제외한다(새 Phase 8, `doc/claude_prompts/
     * phase8-workflow-clarity-feedback.md` §2.2).
     */
    private fun bootstrapEligible(stateRead: StateReadResult?, context: ActionContext): Boolean =
        stateRead is StateReadResult.Missing &&
            context.selectedDayKind == SelectedDayKind.Today &&
            context.compatibility == CompatibilityStatus.Supported &&
            context.boundary == BoundaryStatus.Valid &&
            context.process == ProcessRunStatus.Idle

    private fun stateInvalidReason(stateRead: StateReadResult?): String =
        when (stateRead) {
            null -> "상태를 아직 읽지 않았습니다."
            is StateReadResult.Missing -> "상태 파일이 없습니다."
            is StateReadResult.Malformed -> "상태 파일을 해석할 수 없습니다."
            is StateReadResult.EncodingError -> "상태 파일 인코딩을 해석할 수 없습니다."
            is StateReadResult.UnsupportedSchema -> "지원하지 않는 schema 버전입니다."
            is StateReadResult.AccessDenied -> "상태 파일에 접근할 수 없습니다."
            is StateReadResult.Success -> error("unreachable — Success는 호출 전에 걸러진다")
        }

    private fun unknownDomainReason(state: WorkflowState, activeSliceKind: ActiveSliceKind?): String? {
        (state.phase as? WorkflowPhase.Unknown)?.let { return "알 수 없는 workflow phase입니다." }
        (state.status as? WorkflowStatus.Unknown)?.let { return "알 수 없는 workflow status입니다." }
        (state.queue.status as? QueueStatus.Unknown)?.let { return "알 수 없는 queue status입니다." }
        (state.queue.blockedReason as? QueueBlockedReason.Other)?.let {
            return "알 수 없는 queue 차단 사유가 있습니다."
        }
        (state.executionWrapper as? ExecutionWrapperState.Unknown)?.let {
            return "알 수 없는 execution wrapper입니다."
        }
        (state.stopReason as? StopReason.Unknown)?.let { return "알 수 없는 stop reason입니다." }
        listOf(
            state.artifacts.requestInbox,
            state.artifacts.todayStrategy,
            state.artifacts.dailyHandoff,
            state.artifacts.workflowState,
        ).filterIsInstance<ArtifactReadinessState.Unknown>().firstOrNull()?.let {
            return "알 수 없는 artifact readiness 상태입니다."
        }
        (activeSliceKind as? ActiveSliceKind.Unknown)?.let { return "알 수 없는 활성 slice 종류입니다." }
        return null
    }

    private fun normalActions(state: WorkflowState, activeSliceKind: ActiveSliceKind?): RecommendedActions {
        if (state.queue.blockedReason == QueueBlockedReason.DispatchMetadataConflict) {
            return RecommendedActions(
                primary = UiAction.RunReplan,
                allowed = setOf(UiAction.RunReplan, UiAction.Refresh),
                blockedReason = "계획 대상 충돌로 재계획이 필요합니다.",
            )
        }

        blockingStopReason(state.stopReason)?.let { reason ->
            return recovery(reason)
        }

        if (state.humanActionRequired) {
            return recovery("사용자 확인이 필요한 상태입니다.")
        }

        if (state.phase == WorkflowPhase.ClosureValidated) {
            if (!state.executionCompleted || !state.closureValidated || !state.closure.validated) {
                return recovery("Closure 검증 상태가 서로 일치하지 않습니다.")
            }
            return RecommendedActions(
                primary = UiAction.BootstrapDay,
                allowed = setOf(UiAction.BootstrapDay, UiAction.Refresh),
                blockedReason = null,
            )
        }

        if (state.closureValidated || state.closure.validated) {
            return recovery("Closure 완료 표시와 workflow phase가 서로 일치하지 않습니다.")
        }

        if (state.executionCompleted && state.status != WorkflowStatus.ExecutionCompleted) {
            return RecommendedActions(
                primary = UiAction.ReviewClosure,
                allowed = setOf(UiAction.ReviewClosure, UiAction.Refresh),
                blockedReason = "실행 완료 표시와 workflow status가 서로 일치하지 않습니다.",
            )
        }

        return when (state.status) {
            WorkflowStatus.RequestIntakePending,
            WorkflowStatus.NoRequest,
            -> RecommendedActions(
                primary = UiAction.EditRequest,
                allowed = setOf(UiAction.EditRequest, UiAction.Refresh),
                blockedReason = null,
            )

            WorkflowStatus.PlanningRequired -> RecommendedActions(
                primary = UiAction.RunPlanning,
                allowed = setOf(UiAction.RunPlanning, UiAction.Refresh),
                blockedReason = null,
            )

            WorkflowStatus.PlanningCompleted -> RecommendedActions(
                primary = UiAction.ReviewPlan,
                allowed = setOf(UiAction.ReviewPlan, UiAction.RunReplan, UiAction.Refresh),
                blockedReason = null,
            )

            WorkflowStatus.PlanningFailed -> RecommendedActions(
                primary = UiAction.RunReplan,
                allowed = setOf(UiAction.RunReplan, UiAction.Refresh),
                blockedReason = "계획에 실패했습니다.",
            )

            WorkflowStatus.ContinueExistingPlanNoPlanning -> RecommendedActions(
                primary = UiAction.ReviewPlan,
                allowed = setOf(UiAction.ReviewPlan, UiAction.Refresh),
                blockedReason = null,
            )

            WorkflowStatus.ExecutionReady -> executionReadyActions(state, activeSliceKind)

            WorkflowStatus.ExecutionBlocked,
            WorkflowStatus.ManualPrerequisiteRequired,
            WorkflowStatus.UsageLimitBlocked,
            WorkflowStatus.RoleSlicedWrapperException,
            WorkflowStatus.Blocked,
            -> recovery("실행이 차단되었습니다.")

            WorkflowStatus.ExecutionCompleted ->
                if (state.executionCompleted && state.phase == WorkflowPhase.ExecutionCompleted) {
                    RecommendedActions(
                        primary = UiAction.ReviewClosure,
                        allowed = setOf(
                            UiAction.ReviewClosure,
                            UiAction.RunClosureValidation,
                            UiAction.Refresh,
                        ),
                        blockedReason = null,
                    )
                } else {
                    recovery("실행 완료 상태와 완료 표시가 서로 일치하지 않습니다.")
                }

            is WorkflowStatus.Unknown -> error("unreachable — unknownDomainReason에서 걸러진다")
        }
    }

    private fun executionReadyActions(
        state: WorkflowState,
        activeSliceKind: ActiveSliceKind?,
    ): RecommendedActions {
        val executionContractReady =
            state.phase == WorkflowPhase.ExecutionReady &&
                state.queue.status == QueueStatus.Active &&
                !state.queue.active.cardId.isNullOrBlank() &&
                !state.queue.active.sliceId.isNullOrBlank() &&
                state.opsValidation.passed &&
                !state.executionCompleted &&
                !state.closureValidated

        if (!executionContractReady) {
            return recovery("실행 선행조건 또는 active queue 계약을 확인할 수 없습니다.")
        }

        return when (activeSliceKind) {
            ActiveSliceKind.Code ->
                if (state.authorizedTargetFile.isNullOrBlank()) {
                    recovery("code slice의 authorized target을 확인할 수 없습니다.")
                } else {
                    RecommendedActions(
                        primary = UiAction.RunCodeSlice,
                        allowed = setOf(UiAction.RunCodeSlice, UiAction.Refresh),
                        blockedReason = null,
                    )
                }

            ActiveSliceKind.Doc ->
                if (state.authorizedTargetFile.isNullOrBlank()) {
                    recovery("doc slice의 authorized target을 확인할 수 없습니다.")
                } else {
                    RecommendedActions(
                        primary = UiAction.RunDocSlice,
                        allowed = setOf(UiAction.RunDocSlice, UiAction.Refresh),
                        blockedReason = null,
                    )
                }

            ActiveSliceKind.ValidationOnly -> RecommendedActions(
                primary = UiAction.RunValidationSlice,
                allowed = setOf(UiAction.RunValidationSlice, UiAction.Refresh),
                blockedReason = null,
            )

            null -> recovery("execution_ready 상태이지만 활성 slice 종류를 확인할 수 없습니다.")
            is ActiveSliceKind.Unknown -> error("unreachable — unknownDomainReason에서 걸러진다")
        }
    }

    private fun blockingStopReason(stopReason: StopReason?): String? =
        when (stopReason) {
            StopReason.UsageLimitBlocked -> "Claude 사용량 제한으로 중단되었습니다."
            StopReason.ClaudeContextLimit -> "세션 문맥 한계로 fresh 실행이 필요합니다."
            StopReason.ClaudeCallTimeout -> "Claude 호출 timeout으로 중단되었습니다."
            StopReason.ClaudeResponseEmpty,
            StopReason.ClaudeResponseTooShort,
            -> "Claude 응답을 신뢰할 수 없어 중단되었습니다."
            StopReason.BudgetMaxTurns,
            StopReason.BudgetOrManualStop,
            -> "실행 budget 또는 수동 중단 조건에 도달했습니다."
            StopReason.TransientClaudeOverloaded -> "Claude 일시 과부하로 중단되었습니다."
            StopReason.DispatchContractMismatch -> "Dispatch 계약 불일치로 중단되었습니다."
            StopReason.ManualPrerequisiteRequired -> "수동 선행조건 확인이 필요합니다."
            StopReason.RoleSlicedWrapperException -> "역할별 실행 래퍼 예외로 중단되었습니다."
            StopReason.RequestIntakePending,
            StopReason.NoRequest,
            StopReason.PlanningRequired,
            StopReason.PlanningCompleted,
            StopReason.ReadyForExecution,
            StopReason.ReadyForNextSlice,
            StopReason.InProgress,
            StopReason.Completed,
            StopReason.ExecutionCompleted,
            StopReason.ExecutionQueueCompleted,
            StopReason.CleanHandoffSkip,
            StopReason.CacheHitTaskSatisfied,
            StopReason.SkippedAlreadyDone,
            null,
            -> null
            is StopReason.Unknown -> error("unreachable — unknownDomainReason에서 걸러진다")
        }

    private fun recovery(reason: String): RecommendedActions =
        RecommendedActions(
            primary = UiAction.OpenRecoveryCenter,
            allowed = setOf(UiAction.OpenRecoveryCenter, UiAction.Refresh),
            blockedReason = reason,
        )
}
