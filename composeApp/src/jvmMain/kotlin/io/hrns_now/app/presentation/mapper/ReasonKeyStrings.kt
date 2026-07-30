package io.hrns_now.app.presentation.mapper

import io.hrns_now.core.domain.model.AppLocale
import io.hrns_now.core.domain.model.BlockedReasonKey
import io.hrns_now.core.domain.model.ProcessRunStatus
import io.hrns_now.core.domain.model.StateInvalidKind
import io.hrns_now.core.domain.model.UnknownDomainKind
import io.hrns_now.core.domain.policy.ClosureBlockReasonKey

/**
 * [BlockedReasonKey]/[ClosureBlockReasonKey]는 `core`가 낸 typed 판정이다 — 문구는 여기
 * presentation에서만 만든다(Phase 8 보완, `doc/claude_prompts/
 * phase8-completion-localization-native-qa.md` §1). 기본값 Korean은 기존 호출부/테스트가
 * 그대로 컴파일되도록 남긴다.
 */
fun BlockedReasonKey.toDisplayText(locale: AppLocale = AppLocale.Korean): String =
    when (locale) {
        AppLocale.Korean -> when (this) {
            BlockedReasonKey.ProjectNotConnected -> "프로젝트가 연결되지 않았습니다."
            BlockedReasonKey.DayNotSelected -> "날짜가 선택되지 않았습니다."
            is BlockedReasonKey.StateInvalid -> kind.displayText(locale)
            BlockedReasonKey.UnsupportedCompatibility -> "지원하지 않거나 확인되지 않은 Harness 계약입니다."
            BlockedReasonKey.BoundaryInvalid -> "작업공간·저장소·Kit 경계를 확인할 수 없습니다."
            BlockedReasonKey.PastDateReadOnly -> "과거 날짜는 읽기 전용입니다."
            is BlockedReasonKey.ProcessBusy -> when (status) {
                ProcessRunStatus.Locked -> "다른 실행이 잠금을 보유하고 있습니다."
                ProcessRunStatus.Running -> "다른 실행이 진행 중입니다."
                ProcessRunStatus.Idle -> error("unreachable — process != Idle 분기 내부")
            }
            is BlockedReasonKey.UnknownDomainValue -> kind.displayText(locale)
            BlockedReasonKey.DispatchMetadataConflict -> "계획 대상 충돌로 재계획이 필요합니다."
            is BlockedReasonKey.StopReasonBlocking -> stopReasonBlockingText(reason, locale)
            BlockedReasonKey.HumanActionRequired -> "사용자 확인이 필요한 상태입니다."
            BlockedReasonKey.ClosureValidationMismatch -> "Closure 검증 상태가 서로 일치하지 않습니다."
            BlockedReasonKey.ClosurePhaseMismatch -> "Closure 완료 표시와 workflow phase가 서로 일치하지 않습니다."
            BlockedReasonKey.ExecutionCompletionMismatch -> "실행 완료 표시와 실행 상태가 서로 일치하지 않습니다."
            BlockedReasonKey.PlanningFailed -> "계획에 실패했습니다."
            BlockedReasonKey.GenericExecutionBlocked -> "실행이 차단되었습니다."
            BlockedReasonKey.ExecutionContractUnclear -> "실행 선행조건 또는 active queue 계약을 확인할 수 없습니다."
            BlockedReasonKey.CodeSliceTargetMissing -> "code slice의 authorized target을 확인할 수 없습니다."
            BlockedReasonKey.DocSliceTargetMissing -> "doc slice의 authorized target을 확인할 수 없습니다."
            BlockedReasonKey.ExecutionReadyUnknownSlice -> "execution_ready 상태이지만 활성 slice 종류를 확인할 수 없습니다."
        }

        AppLocale.English -> when (this) {
            BlockedReasonKey.ProjectNotConnected -> "No project is connected."
            BlockedReasonKey.DayNotSelected -> "No date is selected."
            is BlockedReasonKey.StateInvalid -> kind.displayText(locale)
            BlockedReasonKey.UnsupportedCompatibility -> "The Harness contract is unsupported or unconfirmed."
            BlockedReasonKey.BoundaryInvalid -> "The workspace/repository/Kit path boundary could not be confirmed."
            BlockedReasonKey.PastDateReadOnly -> "Past dates are read-only."
            is BlockedReasonKey.ProcessBusy -> when (status) {
                ProcessRunStatus.Locked -> "Another run holds the lock."
                ProcessRunStatus.Running -> "Another run is in progress."
                ProcessRunStatus.Idle -> error("unreachable — process != Idle 분기 내부")
            }
            is BlockedReasonKey.UnknownDomainValue -> kind.displayText(locale)
            BlockedReasonKey.DispatchMetadataConflict -> "A plan target conflict requires replanning."
            is BlockedReasonKey.StopReasonBlocking -> stopReasonBlockingText(reason, locale)
            BlockedReasonKey.HumanActionRequired -> "This state requires human confirmation."
            BlockedReasonKey.ClosureValidationMismatch -> "Closure validation state is inconsistent."
            BlockedReasonKey.ClosurePhaseMismatch -> "Closure completion flags and workflow phase are inconsistent."
            BlockedReasonKey.ExecutionCompletionMismatch -> "Execution completion flags and status are inconsistent."
            BlockedReasonKey.PlanningFailed -> "Planning failed."
            BlockedReasonKey.GenericExecutionBlocked -> "Execution is blocked."
            BlockedReasonKey.ExecutionContractUnclear -> "The execution prerequisites or active queue contract could not be confirmed."
            BlockedReasonKey.CodeSliceTargetMissing -> "The authorized target for the code slice could not be confirmed."
            BlockedReasonKey.DocSliceTargetMissing -> "The authorized target for the doc slice could not be confirmed."
            BlockedReasonKey.ExecutionReadyUnknownSlice -> "The state is execution_ready but the active slice kind could not be confirmed."
        }
    }

private fun stopReasonBlockingText(reason: io.hrns_now.core.domain.model.StopReason, locale: AppLocale): String =
    when (locale) {
        AppLocale.Korean -> when (reason) {
            io.hrns_now.core.domain.model.StopReason.UsageLimitBlocked -> "Claude 사용량 제한으로 중단되었습니다."
            io.hrns_now.core.domain.model.StopReason.ClaudeContextLimit -> "세션 문맥 한계로 fresh 실행이 필요합니다."
            io.hrns_now.core.domain.model.StopReason.ClaudeCallTimeout -> "Claude 호출 timeout으로 중단되었습니다."
            io.hrns_now.core.domain.model.StopReason.ClaudeResponseEmpty,
            io.hrns_now.core.domain.model.StopReason.ClaudeResponseTooShort,
            -> "Claude 응답을 신뢰할 수 없어 중단되었습니다."
            io.hrns_now.core.domain.model.StopReason.BudgetMaxTurns,
            io.hrns_now.core.domain.model.StopReason.BudgetOrManualStop,
            -> "실행 budget 또는 수동 중단 조건에 도달했습니다."
            io.hrns_now.core.domain.model.StopReason.TransientClaudeOverloaded -> "Claude 일시 과부하로 중단되었습니다."
            io.hrns_now.core.domain.model.StopReason.DispatchContractMismatch -> "Dispatch 계약 불일치로 중단되었습니다."
            io.hrns_now.core.domain.model.StopReason.ManualPrerequisiteRequired -> "수동 선행조건 확인이 필요합니다."
            io.hrns_now.core.domain.model.StopReason.RoleSlicedWrapperException -> "역할별 실행 래퍼 예외로 중단되었습니다."
            else -> error("unreachable — blockingStopReason이 걸러진 reason만 전달한다")
        }

        AppLocale.English -> when (reason) {
            io.hrns_now.core.domain.model.StopReason.UsageLimitBlocked -> "Stopped due to Claude usage limits."
            io.hrns_now.core.domain.model.StopReason.ClaudeContextLimit -> "Session context limit reached — a fresh run is required."
            io.hrns_now.core.domain.model.StopReason.ClaudeCallTimeout -> "Stopped due to a Claude call timeout."
            io.hrns_now.core.domain.model.StopReason.ClaudeResponseEmpty,
            io.hrns_now.core.domain.model.StopReason.ClaudeResponseTooShort,
            -> "Stopped because the Claude response could not be trusted."
            io.hrns_now.core.domain.model.StopReason.BudgetMaxTurns,
            io.hrns_now.core.domain.model.StopReason.BudgetOrManualStop,
            -> "Reached the execution budget or a manual stop condition."
            io.hrns_now.core.domain.model.StopReason.TransientClaudeOverloaded -> "Stopped due to a transient Claude overload."
            io.hrns_now.core.domain.model.StopReason.DispatchContractMismatch -> "Stopped due to a dispatch contract mismatch."
            io.hrns_now.core.domain.model.StopReason.ManualPrerequisiteRequired -> "A manual prerequisite check is required."
            io.hrns_now.core.domain.model.StopReason.RoleSlicedWrapperException -> "Stopped due to a role-sliced wrapper exception."
            else -> error("unreachable — blockingStopReason이 걸러진 reason만 전달한다")
        }
    }

private fun StateInvalidKind.displayText(locale: AppLocale): String =
    when (locale) {
        AppLocale.Korean -> when (this) {
            StateInvalidKind.NotYetRead -> "상태를 아직 읽지 않았습니다."
            StateInvalidKind.Missing -> "상태 파일이 없습니다."
            StateInvalidKind.Malformed -> "상태 파일을 해석할 수 없습니다."
            StateInvalidKind.EncodingError -> "상태 파일 인코딩을 해석할 수 없습니다."
            StateInvalidKind.UnsupportedSchema -> "지원하지 않는 schema 버전입니다."
            StateInvalidKind.AccessDenied -> "상태 파일에 접근할 수 없습니다."
        }

        AppLocale.English -> when (this) {
            StateInvalidKind.NotYetRead -> "The state hasn't been read yet."
            StateInvalidKind.Missing -> "The state file doesn't exist."
            StateInvalidKind.Malformed -> "The state file couldn't be parsed."
            StateInvalidKind.EncodingError -> "The state file's encoding couldn't be parsed."
            StateInvalidKind.UnsupportedSchema -> "Unsupported schema version."
            StateInvalidKind.AccessDenied -> "The state file couldn't be accessed."
        }
    }

private fun UnknownDomainKind.displayText(locale: AppLocale): String =
    when (locale) {
        AppLocale.Korean -> when (this) {
            UnknownDomainKind.Phase -> "알 수 없는 workflow phase입니다."
            UnknownDomainKind.Status -> "알 수 없는 workflow status입니다."
            UnknownDomainKind.QueueStatus -> "알 수 없는 queue status입니다."
            UnknownDomainKind.QueueBlockedReason -> "알 수 없는 queue 차단 사유가 있습니다."
            UnknownDomainKind.ExecutionWrapper -> "알 수 없는 execution wrapper입니다."
            UnknownDomainKind.StopReason -> "알 수 없는 stop reason입니다."
            UnknownDomainKind.ArtifactReadiness -> "알 수 없는 artifact readiness 상태입니다."
            UnknownDomainKind.ActiveSliceKind -> "알 수 없는 활성 slice 종류입니다."
        }

        AppLocale.English -> when (this) {
            UnknownDomainKind.Phase -> "Unknown workflow phase."
            UnknownDomainKind.Status -> "Unknown workflow status."
            UnknownDomainKind.QueueStatus -> "Unknown queue status."
            UnknownDomainKind.QueueBlockedReason -> "Unknown queue blocked reason."
            UnknownDomainKind.ExecutionWrapper -> "Unknown execution wrapper."
            UnknownDomainKind.StopReason -> "Unknown stop reason."
            UnknownDomainKind.ArtifactReadiness -> "Unknown artifact readiness state."
            UnknownDomainKind.ActiveSliceKind -> "Unknown active slice kind."
        }
    }

/**
 * [ClosureBlockReasonKey] → 화면 문구다. `RecoveryScreen`의 마감 체크리스트 한 행씩을 만든다.
 */
fun ClosureBlockReasonKey.toDisplayText(locale: AppLocale = AppLocale.Korean): String =
    when (locale) {
        AppLocale.Korean -> when (this) {
            is ClosureBlockReasonKey.StateInvalid -> kind.displayText(locale)
            is ClosureBlockReasonKey.UnknownDomainValue -> kind.displayText(locale)
            ClosureBlockReasonKey.RequiredArtifactsNotReady ->
                "필수 daily 파일(REQUEST_INBOX/TODAY_STRATEGY/DAILY_HANDOFF/WORKFLOW_STATE)이 모두 준비되지 않았습니다."
            ClosureBlockReasonKey.OpsValidationFailed -> "운영 검증(ops validation)을 통과하지 못했습니다."
            ClosureBlockReasonKey.ActiveSliceRemaining -> "아직 활성 slice가 남아 있습니다."
            ClosureBlockReasonKey.ResumeStepRemaining -> "재개해야 할 step이 남아 있습니다."
            ClosureBlockReasonKey.ClosureValidationFlagMismatch -> "closure 검증 상태와 상위 완료 flag가 서로 일치하지 않습니다."
            ClosureBlockReasonKey.ClosureFieldConsistencyMismatch -> "closure 필드(validated/is_clean_handoff) 간 정합성이 맞지 않습니다."
            ClosureBlockReasonKey.CleanHandoffConsistencyMismatch -> "clean_handoff와 closure.is_clean_handoff 간 정합성이 맞지 않습니다."
            ClosureBlockReasonKey.LockHeldByOtherRun -> "다른 실행이 이 프로젝트·날짜의 잠금을 보유하고 있습니다."
        }

        AppLocale.English -> when (this) {
            is ClosureBlockReasonKey.StateInvalid -> kind.displayText(locale)
            is ClosureBlockReasonKey.UnknownDomainValue -> kind.displayText(locale)
            ClosureBlockReasonKey.RequiredArtifactsNotReady ->
                "Required daily files (REQUEST_INBOX/TODAY_STRATEGY/DAILY_HANDOFF/WORKFLOW_STATE) aren't all ready."
            ClosureBlockReasonKey.OpsValidationFailed -> "Ops validation didn't pass."
            ClosureBlockReasonKey.ActiveSliceRemaining -> "An active slice is still pending."
            ClosureBlockReasonKey.ResumeStepRemaining -> "A step still needs to be resumed."
            ClosureBlockReasonKey.ClosureValidationFlagMismatch -> "Closure validation state and the top-level completion flag are inconsistent."
            ClosureBlockReasonKey.ClosureFieldConsistencyMismatch -> "Closure fields (validated/is_clean_handoff) are inconsistent."
            ClosureBlockReasonKey.CleanHandoffConsistencyMismatch -> "clean_handoff and closure.is_clean_handoff are inconsistent."
            ClosureBlockReasonKey.LockHeldByOtherRun -> "Another run holds the lock for this project and date."
        }
    }
