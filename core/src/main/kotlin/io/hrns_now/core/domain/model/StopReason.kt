package io.hrns_now.core.domain.model

/**
 * `state.stop_reason`의 typed 표현이다.
 *
 * 알려진 값은 harness-kit `-StopReason` 리터럴을 실측 grep으로 확인한 것만 포함한다.
 *
 * 주의: 계획서 부록 C는 `dispatch_metadata_conflict`를 dispatch 계열 stop reason으로
 * 예시했으나, 실측 결과 이 문자열은 planning 단계 queue projection의 `blocked_reason`/
 * `purpose_marker`로만 쓰이고 `state.stop_reason`에는 결코 대입되지 않는다
 * (`scripts/lib/plan/planning-queue-projection.ps1`). 실제 실행 단계 `stop_reason`은
 * `dispatch_contract_mismatch`이며(`scripts/invoke-append-execute-code-step.ps1`,
 * `scripts/invoke-append-execute-doc-step.ps1`), 실제 운영 workspace의 live
 * `WORKFLOW_STATE.json` 스냅샷에서도 `state.stop_reason` 값은 `dispatch_contract_mismatch`였다.
 * 따라서 이 타입은 `DispatchContractMismatch`만 알려진 값으로 취급한다.
 */
sealed interface StopReason {
    data object RequestIntakePending : StopReason
    data object NoRequest : StopReason
    data object PlanningRequired : StopReason
    data object PlanningCompleted : StopReason
    data object ReadyForExecution : StopReason
    data object ReadyForNextSlice : StopReason
    data object InProgress : StopReason
    data object Completed : StopReason
    data object ExecutionCompleted : StopReason
    data object ExecutionQueueCompleted : StopReason
    data object CleanHandoffSkip : StopReason
    data object CacheHitTaskSatisfied : StopReason
    data object SkippedAlreadyDone : StopReason
    data object UsageLimitBlocked : StopReason
    data object ClaudeContextLimit : StopReason
    data object ClaudeCallTimeout : StopReason
    data object ClaudeResponseEmpty : StopReason
    data object ClaudeResponseTooShort : StopReason
    data object BudgetMaxTurns : StopReason
    data object BudgetOrManualStop : StopReason
    data object TransientClaudeOverloaded : StopReason
    data object DispatchContractMismatch : StopReason
    data object ManualPrerequisiteRequired : StopReason
    data object RoleSlicedWrapperException : StopReason
    data class Unknown(val raw: String) : StopReason
}

/** null/빈 문자열은 "현재 정지 사유 없음"을 의미하므로 null을 반환한다. */
fun String?.toStopReason(): StopReason? =
    when {
        this.isNullOrBlank() -> null
        this == "request_intake_pending" -> StopReason.RequestIntakePending
        this == "no_request" -> StopReason.NoRequest
        this == "planning_required" -> StopReason.PlanningRequired
        this == "planning_completed" -> StopReason.PlanningCompleted
        this == "ready_for_execution" -> StopReason.ReadyForExecution
        this == "ready_for_next_slice" -> StopReason.ReadyForNextSlice
        this == "in_progress" -> StopReason.InProgress
        this == "completed" -> StopReason.Completed
        this == "execution_completed" -> StopReason.ExecutionCompleted
        this == "execution_queue_completed" -> StopReason.ExecutionQueueCompleted
        this == "clean_handoff_skip" -> StopReason.CleanHandoffSkip
        this == "cache_hit_task_satisfied" -> StopReason.CacheHitTaskSatisfied
        this == "skipped_already_done" -> StopReason.SkippedAlreadyDone
        this == "usage_limit_blocked" -> StopReason.UsageLimitBlocked
        this == "claude_context_limit" -> StopReason.ClaudeContextLimit
        this == "claude_call_timeout" -> StopReason.ClaudeCallTimeout
        this == "claude_response_empty" -> StopReason.ClaudeResponseEmpty
        this == "claude_response_too_short" -> StopReason.ClaudeResponseTooShort
        this == "budget_max_turns" -> StopReason.BudgetMaxTurns
        this == "budget_or_manual_stop" -> StopReason.BudgetOrManualStop
        this == "transient_claude_overloaded" -> StopReason.TransientClaudeOverloaded
        this == "dispatch_contract_mismatch" -> StopReason.DispatchContractMismatch
        this == "manual_prerequisite_required" -> StopReason.ManualPrerequisiteRequired
        this == "role_sliced_wrapper_exception" -> StopReason.RoleSlicedWrapperException
        else -> StopReason.Unknown(this)
    }
