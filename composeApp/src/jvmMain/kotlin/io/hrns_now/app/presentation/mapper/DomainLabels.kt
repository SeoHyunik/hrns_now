package io.hrns_now.app.presentation.mapper

import io.hrns_now.core.domain.model.AppLocale
import io.hrns_now.core.domain.model.QueueStatus
import io.hrns_now.core.domain.model.StopReason
import io.hrns_now.core.domain.model.WorkflowPhase
import io.hrns_now.core.domain.model.WorkflowStatus
import io.hrns_now.core.usecase.ActiveProjectSource

/**
 * domain sealed 값의 화면 표시 문구다. 외부 JSON의 `Unknown(raw)` 원문은 domain에
 * 보존하되 UI에는 그대로 표시하지 않는다. 상태 필드는 신뢰 경계 밖의 입력이므로 원문에
 * session ID·token·secret이 없다고 가정할 수 없다. 새 Phase 8 보완: 화면에서 실제로 선택된
 * [AppLocale]로 투영한다 — 기본값 Korean은 기존 호출부/테스트가 그대로 컴파일되도록 남긴다.
 */
fun WorkflowPhase.displayLabel(locale: AppLocale = AppLocale.Korean): String =
    when (locale) {
        AppLocale.Korean -> when (this) {
            WorkflowPhase.RequestIntakePending -> "요청 접수 대기"
            WorkflowPhase.PlanningRequired -> "계획 필요"
            WorkflowPhase.PlanningReady -> "계획 준비 완료"
            WorkflowPhase.PlanningBlocked -> "계획 차단"
            WorkflowPhase.PlanningCompleted -> "계획 완료"
            WorkflowPhase.PlanningFailed -> "계획 실패"
            WorkflowPhase.ExecutionReady -> "실행 준비 완료"
            WorkflowPhase.Execution -> "실행 중"
            WorkflowPhase.ExecutionBlocked -> "실행 차단"
            WorkflowPhase.ExecutionCompleted -> "실행 완료"
            WorkflowPhase.ExecutionOrHandoffInProgress -> "실행/인수인계 진행 중"
            WorkflowPhase.AwaitingManual -> "수동 확인 대기"
            WorkflowPhase.ClosureValidated -> "마감 검증 완료"
            WorkflowPhase.Blocked -> "차단됨"
            is WorkflowPhase.Unknown -> "알 수 없음"
        }

        AppLocale.English -> when (this) {
            WorkflowPhase.RequestIntakePending -> "Awaiting request intake"
            WorkflowPhase.PlanningRequired -> "Planning required"
            WorkflowPhase.PlanningReady -> "Planning ready"
            WorkflowPhase.PlanningBlocked -> "Planning blocked"
            WorkflowPhase.PlanningCompleted -> "Planning completed"
            WorkflowPhase.PlanningFailed -> "Planning failed"
            WorkflowPhase.ExecutionReady -> "Execution ready"
            WorkflowPhase.Execution -> "Executing"
            WorkflowPhase.ExecutionBlocked -> "Execution blocked"
            WorkflowPhase.ExecutionCompleted -> "Execution completed"
            WorkflowPhase.ExecutionOrHandoffInProgress -> "Execution/handoff in progress"
            WorkflowPhase.AwaitingManual -> "Awaiting manual check"
            WorkflowPhase.ClosureValidated -> "Closure validated"
            WorkflowPhase.Blocked -> "Blocked"
            is WorkflowPhase.Unknown -> "Unknown"
        }
    }

fun WorkflowStatus.displayLabel(locale: AppLocale = AppLocale.Korean): String =
    when (locale) {
        AppLocale.Korean -> when (this) {
            WorkflowStatus.RequestIntakePending -> "요청 접수 대기"
            WorkflowStatus.NoRequest -> "요청 없음"
            WorkflowStatus.PlanningRequired -> "계획 필요"
            WorkflowStatus.PlanningCompleted -> "계획 완료"
            WorkflowStatus.PlanningFailed -> "계획 실패"
            WorkflowStatus.ExecutionReady -> "실행 준비 완료"
            WorkflowStatus.ExecutionBlocked -> "실행 차단"
            WorkflowStatus.ExecutionCompleted -> "실행 완료"
            WorkflowStatus.ManualPrerequisiteRequired -> "수동 선행조건 필요"
            WorkflowStatus.UsageLimitBlocked -> "사용량 제한으로 차단"
            WorkflowStatus.RoleSlicedWrapperException -> "역할별 실행 래퍼 예외"
            WorkflowStatus.ContinueExistingPlanNoPlanning -> "계획 없이 기존 계획 진행"
            WorkflowStatus.Blocked -> "차단됨"
            is WorkflowStatus.Unknown -> "알 수 없음"
        }

        AppLocale.English -> when (this) {
            WorkflowStatus.RequestIntakePending -> "Awaiting request intake"
            WorkflowStatus.NoRequest -> "No request"
            WorkflowStatus.PlanningRequired -> "Planning required"
            WorkflowStatus.PlanningCompleted -> "Planning completed"
            WorkflowStatus.PlanningFailed -> "Planning failed"
            WorkflowStatus.ExecutionReady -> "Execution ready"
            WorkflowStatus.ExecutionBlocked -> "Execution blocked"
            WorkflowStatus.ExecutionCompleted -> "Execution completed"
            WorkflowStatus.ManualPrerequisiteRequired -> "Manual prerequisite required"
            WorkflowStatus.UsageLimitBlocked -> "Blocked by usage limit"
            WorkflowStatus.RoleSlicedWrapperException -> "Role-sliced wrapper exception"
            WorkflowStatus.ContinueExistingPlanNoPlanning -> "Continuing existing plan without planning"
            WorkflowStatus.Blocked -> "Blocked"
            is WorkflowStatus.Unknown -> "Unknown"
        }
    }

fun QueueStatus.displayLabel(locale: AppLocale = AppLocale.Korean): String =
    when (locale) {
        AppLocale.Korean -> when (this) {
            QueueStatus.Active -> "활성"
            QueueStatus.Empty -> "비어 있음"
            QueueStatus.Blocked -> "차단됨"
            QueueStatus.Completed -> "완료"
            QueueStatus.PlanningRequired -> "계획 필요"
            is QueueStatus.Unknown -> "알 수 없음"
        }

        AppLocale.English -> when (this) {
            QueueStatus.Active -> "Active"
            QueueStatus.Empty -> "Empty"
            QueueStatus.Blocked -> "Blocked"
            QueueStatus.Completed -> "Completed"
            QueueStatus.PlanningRequired -> "Planning required"
            is QueueStatus.Unknown -> "Unknown"
        }
    }

fun StopReason.displayLabel(locale: AppLocale = AppLocale.Korean): String =
    when (locale) {
        AppLocale.Korean -> when (this) {
            StopReason.RequestIntakePending -> "요청 접수 대기"
            StopReason.NoRequest -> "요청 없음"
            StopReason.PlanningRequired -> "계획 필요"
            StopReason.PlanningCompleted -> "계획 완료"
            StopReason.ReadyForExecution -> "실행 대기"
            StopReason.ReadyForNextSlice -> "다음 작업 대기"
            StopReason.InProgress -> "진행 중"
            StopReason.Completed -> "완료"
            StopReason.ExecutionCompleted -> "실행 완료"
            StopReason.ExecutionQueueCompleted -> "실행 큐 완료"
            StopReason.CleanHandoffSkip -> "정상 인수인계 생략"
            StopReason.CacheHitTaskSatisfied -> "캐시 적중으로 충족됨"
            StopReason.SkippedAlreadyDone -> "이미 완료되어 건너뜀"
            StopReason.UsageLimitBlocked -> "Claude 사용량 제한"
            StopReason.ClaudeContextLimit -> "세션 문맥 한계"
            StopReason.ClaudeCallTimeout -> "Claude 호출 timeout"
            StopReason.ClaudeResponseEmpty -> "Claude 응답 없음"
            StopReason.ClaudeResponseTooShort -> "Claude 응답 너무 짧음"
            StopReason.BudgetMaxTurns -> "턴 예산 초과"
            StopReason.BudgetOrManualStop -> "실행 budget 또는 수동 중단"
            StopReason.TransientClaudeOverloaded -> "Claude 일시 과부하"
            StopReason.DispatchContractMismatch -> "Dispatch 계약 불일치"
            StopReason.ManualPrerequisiteRequired -> "수동 선행조건 필요"
            StopReason.RoleSlicedWrapperException -> "역할별 실행 래퍼 예외"
            is StopReason.Unknown -> "알 수 없음"
        }

        AppLocale.English -> when (this) {
            StopReason.RequestIntakePending -> "Awaiting request intake"
            StopReason.NoRequest -> "No request"
            StopReason.PlanningRequired -> "Planning required"
            StopReason.PlanningCompleted -> "Planning completed"
            StopReason.ReadyForExecution -> "Ready for execution"
            StopReason.ReadyForNextSlice -> "Ready for next slice"
            StopReason.InProgress -> "In progress"
            StopReason.Completed -> "Completed"
            StopReason.ExecutionCompleted -> "Execution completed"
            StopReason.ExecutionQueueCompleted -> "Execution queue completed"
            StopReason.CleanHandoffSkip -> "Clean handoff skipped"
            StopReason.CacheHitTaskSatisfied -> "Satisfied by cache hit"
            StopReason.SkippedAlreadyDone -> "Skipped, already done"
            StopReason.UsageLimitBlocked -> "Claude usage limit"
            StopReason.ClaudeContextLimit -> "Session context limit"
            StopReason.ClaudeCallTimeout -> "Claude call timeout"
            StopReason.ClaudeResponseEmpty -> "Empty Claude response"
            StopReason.ClaudeResponseTooShort -> "Claude response too short"
            StopReason.BudgetMaxTurns -> "Turn budget exceeded"
            StopReason.BudgetOrManualStop -> "Execution budget or manual stop"
            StopReason.TransientClaudeOverloaded -> "Claude temporarily overloaded"
            StopReason.DispatchContractMismatch -> "Dispatch contract mismatch"
            StopReason.ManualPrerequisiteRequired -> "Manual prerequisite required"
            StopReason.RoleSlicedWrapperException -> "Role-sliced wrapper exception"
            is StopReason.Unknown -> "Unknown"
        }
    }

fun ActiveProjectSource.displayLabel(locale: AppLocale = AppLocale.Korean): String =
    when (locale) {
        AppLocale.Korean -> when (this) {
            ActiveProjectSource.Registry -> "Registry"
            ActiveProjectSource.EnvironmentFallback -> "환경변수 fallback"
            ActiveProjectSource.NoneSelected -> "선택 필요"
        }

        AppLocale.English -> when (this) {
            ActiveProjectSource.Registry -> "Registry"
            ActiveProjectSource.EnvironmentFallback -> "Environment variable fallback"
            ActiveProjectSource.NoneSelected -> "Selection required"
        }
    }
