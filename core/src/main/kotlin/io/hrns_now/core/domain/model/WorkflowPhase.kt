package io.hrns_now.core.domain.model

/**
 * `state.current_phase`의 typed 표현이다.
 *
 * 알려진 값은 harness-kit 스크립트에서 실측 확인한 리터럴만 포함한다
 * (`scripts/init-workspace.ps1`, `scripts/lib/code/code-state-output.ps1`,
 * `scripts/lib/doc/doc-state-output.ps1`, `scripts/invoke-append-*.ps1` 등).
 * 목록에 없는 값은 [Unknown]으로 원문을 보존하며, 실행 가능 여부 판단은
 * 이 Unknown 값에서 fail-closed로 처리해야 한다.
 */
sealed interface WorkflowPhase {
    data object RequestIntakePending : WorkflowPhase
    data object PlanningRequired : WorkflowPhase
    data object PlanningReady : WorkflowPhase
    data object PlanningBlocked : WorkflowPhase
    data object PlanningCompleted : WorkflowPhase
    data object PlanningFailed : WorkflowPhase
    data object ExecutionReady : WorkflowPhase
    data object Execution : WorkflowPhase
    data object ExecutionBlocked : WorkflowPhase
    data object ExecutionCompleted : WorkflowPhase
    data object ExecutionOrHandoffInProgress : WorkflowPhase
    data object AwaitingManual : WorkflowPhase
    data object ClosureValidated : WorkflowPhase
    data object Blocked : WorkflowPhase
    data class Unknown(val raw: String) : WorkflowPhase
}

/** 호출 전에 필드 존재(누락이 아님)를 확인했다고 가정한다 — 누락 처리는 Mapper의 몫이다. */
fun String.toWorkflowPhase(): WorkflowPhase =
    when (this) {
        "request_intake_pending" -> WorkflowPhase.RequestIntakePending
        "planning_required" -> WorkflowPhase.PlanningRequired
        "planning_ready" -> WorkflowPhase.PlanningReady
        "planning_blocked" -> WorkflowPhase.PlanningBlocked
        "planning_completed" -> WorkflowPhase.PlanningCompleted
        "planning_failed" -> WorkflowPhase.PlanningFailed
        "execution_ready" -> WorkflowPhase.ExecutionReady
        "execution" -> WorkflowPhase.Execution
        "execution_blocked" -> WorkflowPhase.ExecutionBlocked
        "execution_completed" -> WorkflowPhase.ExecutionCompleted
        "execution_or_handoff_in_progress" -> WorkflowPhase.ExecutionOrHandoffInProgress
        "awaiting_manual" -> WorkflowPhase.AwaitingManual
        "closure_validated" -> WorkflowPhase.ClosureValidated
        "blocked" -> WorkflowPhase.Blocked
        else -> WorkflowPhase.Unknown(this)
    }
