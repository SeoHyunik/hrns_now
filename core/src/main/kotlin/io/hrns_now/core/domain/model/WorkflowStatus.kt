package io.hrns_now.core.domain.model

/**
 * `state.current_status`의 typed 표현이다.
 *
 * 알려진 값은 harness-kit 스크립트에서 실측 확인한 리터럴만 포함한다.
 * `current_phase`와 값 공간이 일부 겹치지만(`execution_blocked`, `blocked` 등)
 * 서로 다른 차원의 상태이므로 별도 sealed type으로 분리한다.
 * 목록에 없는 값은 [Unknown]으로 원문을 보존한다.
 */
sealed interface WorkflowStatus {
    data object RequestIntakePending : WorkflowStatus
    data object NoRequest : WorkflowStatus
    data object PlanningRequired : WorkflowStatus
    data object PlanningCompleted : WorkflowStatus
    data object PlanningFailed : WorkflowStatus
    data object ExecutionReady : WorkflowStatus
    data object ExecutionBlocked : WorkflowStatus
    data object ExecutionCompleted : WorkflowStatus
    data object ManualPrerequisiteRequired : WorkflowStatus
    data object UsageLimitBlocked : WorkflowStatus
    data object RoleSlicedWrapperException : WorkflowStatus
    data object ContinueExistingPlanNoPlanning : WorkflowStatus
    data object Blocked : WorkflowStatus
    data class Unknown(val raw: String) : WorkflowStatus
}

/** 호출 전에 필드 존재(누락이 아님)를 확인했다고 가정한다 — 누락 처리는 Mapper의 몫이다. */
fun String.toWorkflowStatus(): WorkflowStatus =
    when (this) {
        "request_intake_pending" -> WorkflowStatus.RequestIntakePending
        "no_request" -> WorkflowStatus.NoRequest
        "planning_required" -> WorkflowStatus.PlanningRequired
        "planning_completed" -> WorkflowStatus.PlanningCompleted
        "planning_failed" -> WorkflowStatus.PlanningFailed
        "execution_ready" -> WorkflowStatus.ExecutionReady
        "execution_blocked" -> WorkflowStatus.ExecutionBlocked
        "execution_completed" -> WorkflowStatus.ExecutionCompleted
        "manual_prerequisite_required" -> WorkflowStatus.ManualPrerequisiteRequired
        "usage_limit_blocked" -> WorkflowStatus.UsageLimitBlocked
        "role_sliced_wrapper_exception" -> WorkflowStatus.RoleSlicedWrapperException
        "continue_existing_plan_no_planning" -> WorkflowStatus.ContinueExistingPlanNoPlanning
        "blocked" -> WorkflowStatus.Blocked
        else -> WorkflowStatus.Unknown(this)
    }
