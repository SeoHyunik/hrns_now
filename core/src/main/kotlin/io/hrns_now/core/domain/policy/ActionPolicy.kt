package io.hrns_now.core.domain.policy

import io.hrns_now.core.domain.model.ActionContext
import io.hrns_now.core.domain.model.ActiveSliceKind
import io.hrns_now.core.domain.model.ArtifactReadinessState
import io.hrns_now.core.domain.model.BlockedReasonKey
import io.hrns_now.core.domain.model.BoundaryStatus
import io.hrns_now.core.domain.model.CompatibilityStatus
import io.hrns_now.core.domain.model.ExecutionWrapperState
import io.hrns_now.core.domain.model.ProcessRunStatus
import io.hrns_now.core.domain.model.QueueBlockedReason
import io.hrns_now.core.domain.model.QueueStatus
import io.hrns_now.core.domain.model.RecommendedActions
import io.hrns_now.core.domain.model.SelectedDayKind
import io.hrns_now.core.domain.model.StateInvalidKind
import io.hrns_now.core.domain.model.StopReason
import io.hrns_now.core.domain.model.UiAction
import io.hrns_now.core.domain.model.UnknownDomainKind
import io.hrns_now.core.domain.model.WorkflowPhase
import io.hrns_now.core.domain.model.WorkflowState
import io.hrns_now.core.domain.model.WorkflowStatus
import io.hrns_now.core.result.StateReadResult

/**
 * [ActionContext]로부터 현재 허용할 단 하나의 primary action과 전체 허용 action set을
 * 결정하는 순수 정책이다(`doc/hrns_now_design_pattern.md` §7).
 *
 * 외부 문자열의 원문은 진단용 domain 값에 보존하되, 이 정책의 사용자 표시 문구에는
 * 포함하지 않는다. unknown·불일치·불완전한 실행 계약은 모두 fail-closed 처리한다. 차단
 * 사유는 문구가 아니라 typed [BlockedReasonKey]로만 낸다 — presentation이 locale별 문구로
 * 투영한다.
 */
class ActionPolicy {

    fun recommend(context: ActionContext): RecommendedActions {
        if (!context.projectConnected) {
            return RecommendedActions(
                primary = UiAction.ConnectProject,
                allowed = setOf(UiAction.ConnectProject),
                reasonKey = BlockedReasonKey.ProjectNotConnected,
            )
        }

        val selectedDayKind = context.selectedDayKind
            ?: return RecommendedActions(
                primary = UiAction.SelectWorkspaceDay,
                allowed = setOf(UiAction.SelectWorkspaceDay, UiAction.RunDoctor, UiAction.RunOpsValidation),
                reasonKey = BlockedReasonKey.DayNotSelected,
            )

        val stateRead = context.stateRead
        if (stateRead !is StateReadResult.Success) {
            if (bootstrapEligible(stateRead, context)) {
                return RecommendedActions(
                    primary = UiAction.BootstrapDay,
                    allowed = setOf(UiAction.BootstrapDay, UiAction.Refresh),
                    reasonKey = null,
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
                reasonKey = BlockedReasonKey.StateInvalid(stateInvalidKind(stateRead)),
            )
        }
        val state = stateRead.state

        if (context.compatibility != CompatibilityStatus.Supported) {
            return RecommendedActions(
                primary = UiAction.ShowCompatibilityIssue,
                allowed = setOf(UiAction.ShowCompatibilityIssue, UiAction.Refresh),
                reasonKey = BlockedReasonKey.UnsupportedCompatibility,
            )
        }

        if (context.boundary != BoundaryStatus.Valid) {
            return recovery(BlockedReasonKey.BoundaryInvalid)
        }

        if (selectedDayKind == SelectedDayKind.Past) {
            return RecommendedActions(
                primary = UiAction.OpenToday,
                allowed = setOf(UiAction.OpenToday, UiAction.Refresh, UiAction.RunDoctor, UiAction.RunOpsValidation),
                reasonKey = BlockedReasonKey.PastDateReadOnly,
            )
        }

        if (context.process != ProcessRunStatus.Idle) {
            return RecommendedActions(
                primary = UiAction.ViewExecutionStatus,
                allowed = setOf(UiAction.ViewExecutionStatus, UiAction.Refresh),
                reasonKey = BlockedReasonKey.ProcessBusy(context.process),
            )
        }

        unknownDomainKind(state, context.activeSliceKind)?.let { kind ->
            return recovery(BlockedReasonKey.UnknownDomainValue(kind))
        }

        return normalActions(state, context.activeSliceKind)
    }

    /**
     * 오늘 날짜에 State가 아직 없는 새 workspace day는 `run-cycle.ps1`이 `init-workspace`로
     * `WORKFLOW_STATE.json`을 새로 만드는 fresh-day bootstrap 경로다(live 계약 확인:
     * `scripts/run-cycle.ps1`의 "Fresh-day bootstrap note" 주석 — 새 날짜 폴더에서
     * `WORKFLOW_STATE.json`은 `init-workspace` 이후에 생성된다). Malformed/UnsupportedSchema/
     * EncodingError/AccessDenied나 과거 날짜, compatibility/boundary 미확인, 실행 중/lock 상태는
     * 모두 fail-closed로 이 경로에서 제외한다.
     */
    private fun bootstrapEligible(stateRead: StateReadResult?, context: ActionContext): Boolean =
        stateRead is StateReadResult.Missing &&
            context.selectedDayKind == SelectedDayKind.Today &&
            context.compatibility == CompatibilityStatus.Supported &&
            context.boundary == BoundaryStatus.Valid &&
            context.process == ProcessRunStatus.Idle

    private fun stateInvalidKind(stateRead: StateReadResult?): StateInvalidKind =
        when (stateRead) {
            null -> StateInvalidKind.NotYetRead
            is StateReadResult.Missing -> StateInvalidKind.Missing
            is StateReadResult.Malformed -> StateInvalidKind.Malformed
            is StateReadResult.EncodingError -> StateInvalidKind.EncodingError
            is StateReadResult.UnsupportedSchema -> StateInvalidKind.UnsupportedSchema
            is StateReadResult.AccessDenied -> StateInvalidKind.AccessDenied
            is StateReadResult.Success -> error("unreachable — Success는 호출 전에 걸러진다")
        }

    private fun unknownDomainKind(state: WorkflowState, activeSliceKind: ActiveSliceKind?): UnknownDomainKind? {
        (state.phase as? WorkflowPhase.Unknown)?.let { return UnknownDomainKind.Phase }
        (state.status as? WorkflowStatus.Unknown)?.let { return UnknownDomainKind.Status }
        (state.queue.status as? QueueStatus.Unknown)?.let { return UnknownDomainKind.QueueStatus }
        (state.queue.blockedReason as? QueueBlockedReason.Other)?.let {
            return UnknownDomainKind.QueueBlockedReason
        }
        (state.executionWrapper as? ExecutionWrapperState.Unknown)?.let {
            return UnknownDomainKind.ExecutionWrapper
        }
        (state.stopReason as? StopReason.Unknown)?.let { return UnknownDomainKind.StopReason }
        listOf(
            state.artifacts.requestInbox,
            state.artifacts.todayStrategy,
            state.artifacts.dailyHandoff,
            state.artifacts.workflowState,
        ).filterIsInstance<ArtifactReadinessState.Unknown>().firstOrNull()?.let {
            return UnknownDomainKind.ArtifactReadiness
        }
        (activeSliceKind as? ActiveSliceKind.Unknown)?.let { return UnknownDomainKind.ActiveSliceKind }
        return null
    }

    private fun normalActions(state: WorkflowState, activeSliceKind: ActiveSliceKind?): RecommendedActions {
        if (state.queue.blockedReason == QueueBlockedReason.DispatchMetadataConflict) {
            return RecommendedActions(
                primary = UiAction.RunReplan,
                allowed = setOf(UiAction.RunReplan, UiAction.Refresh),
                reasonKey = BlockedReasonKey.DispatchMetadataConflict,
            )
        }

        blockingStopReason(state.stopReason)?.let { reason ->
            return recovery(BlockedReasonKey.StopReasonBlocking(reason))
        }

        if (state.humanActionRequired) {
            return recovery(BlockedReasonKey.HumanActionRequired)
        }

        if (state.phase == WorkflowPhase.ClosureValidated) {
            if (!state.executionCompleted || !state.closureValidated || !state.closure.validated) {
                return recovery(BlockedReasonKey.ClosureValidationMismatch)
            }
            return RecommendedActions(
                primary = UiAction.BootstrapDay,
                allowed = setOf(UiAction.BootstrapDay, UiAction.Refresh),
                reasonKey = null,
            )
        }

        if (state.closureValidated || state.closure.validated) {
            return recovery(BlockedReasonKey.ClosurePhaseMismatch)
        }

        if (state.executionCompleted && state.status != WorkflowStatus.ExecutionCompleted) {
            return RecommendedActions(
                primary = UiAction.ReviewClosure,
                allowed = setOf(UiAction.ReviewClosure, UiAction.Refresh),
                reasonKey = BlockedReasonKey.ExecutionCompletionMismatch,
            )
        }

        return when (state.status) {
            WorkflowStatus.RequestIntakePending,
            WorkflowStatus.NoRequest,
            -> RecommendedActions(
                primary = UiAction.EditRequest,
                allowed = setOf(UiAction.EditRequest, UiAction.Refresh),
                reasonKey = null,
            )

            WorkflowStatus.PlanningRequired -> RecommendedActions(
                primary = UiAction.RunPlanning,
                allowed = setOf(UiAction.RunPlanning, UiAction.Refresh),
                reasonKey = null,
            )

            WorkflowStatus.PlanningCompleted -> RecommendedActions(
                primary = UiAction.ReviewPlan,
                allowed = setOf(UiAction.ReviewPlan, UiAction.RunReplan, UiAction.Refresh),
                reasonKey = null,
            )

            WorkflowStatus.PlanningFailed -> RecommendedActions(
                primary = UiAction.RunReplan,
                allowed = setOf(UiAction.RunReplan, UiAction.Refresh),
                reasonKey = BlockedReasonKey.PlanningFailed,
            )

            WorkflowStatus.ContinueExistingPlanNoPlanning -> RecommendedActions(
                primary = UiAction.ReviewPlan,
                allowed = setOf(UiAction.ReviewPlan, UiAction.Refresh),
                reasonKey = null,
            )

            WorkflowStatus.ExecutionReady -> executionReadyActions(state, activeSliceKind)

            WorkflowStatus.ExecutionBlocked,
            WorkflowStatus.ManualPrerequisiteRequired,
            WorkflowStatus.UsageLimitBlocked,
            WorkflowStatus.RoleSlicedWrapperException,
            WorkflowStatus.Blocked,
            -> recovery(BlockedReasonKey.GenericExecutionBlocked)

            WorkflowStatus.ExecutionCompleted ->
                if (state.executionCompleted && state.phase == WorkflowPhase.ExecutionCompleted) {
                    RecommendedActions(
                        primary = UiAction.ReviewClosure,
                        allowed = setOf(
                            UiAction.ReviewClosure,
                            UiAction.RunClosureValidation,
                            UiAction.Refresh,
                        ),
                        reasonKey = null,
                    )
                } else {
                    recovery(BlockedReasonKey.ExecutionCompletionMismatch)
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
            return recovery(BlockedReasonKey.ExecutionContractUnclear)
        }

        return when (activeSliceKind) {
            ActiveSliceKind.Code ->
                if (state.authorizedTargetFile.isNullOrBlank()) {
                    recovery(BlockedReasonKey.CodeSliceTargetMissing)
                } else {
                    RecommendedActions(
                        primary = UiAction.RunCodeSlice,
                        allowed = setOf(UiAction.RunCodeSlice, UiAction.Refresh),
                        reasonKey = null,
                    )
                }

            ActiveSliceKind.Doc ->
                if (state.authorizedTargetFile.isNullOrBlank()) {
                    recovery(BlockedReasonKey.DocSliceTargetMissing)
                } else {
                    RecommendedActions(
                        primary = UiAction.RunDocSlice,
                        allowed = setOf(UiAction.RunDocSlice, UiAction.Refresh),
                        reasonKey = null,
                    )
                }

            ActiveSliceKind.ValidationOnly -> RecommendedActions(
                primary = UiAction.RunValidationSlice,
                allowed = setOf(UiAction.RunValidationSlice, UiAction.Refresh),
                reasonKey = null,
            )

            null -> recovery(BlockedReasonKey.ExecutionReadyUnknownSlice)
            is ActiveSliceKind.Unknown -> error("unreachable — unknownDomainReason에서 걸러진다")
        }
    }

    private fun blockingStopReason(stopReason: StopReason?): StopReason? =
        when (stopReason) {
            StopReason.UsageLimitBlocked,
            StopReason.ClaudeContextLimit,
            StopReason.ClaudeCallTimeout,
            StopReason.ClaudeResponseEmpty,
            StopReason.ClaudeResponseTooShort,
            StopReason.BudgetMaxTurns,
            StopReason.BudgetOrManualStop,
            StopReason.TransientClaudeOverloaded,
            StopReason.DispatchContractMismatch,
            StopReason.ManualPrerequisiteRequired,
            StopReason.RoleSlicedWrapperException,
            -> stopReason

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

    private fun recovery(reasonKey: BlockedReasonKey): RecommendedActions =
        RecommendedActions(
            primary = UiAction.OpenRecoveryCenter,
            allowed = setOf(UiAction.OpenRecoveryCenter, UiAction.Refresh),
            reasonKey = reasonKey,
        )
}
