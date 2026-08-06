package io.hrns_now.core.domain.policy

import io.hrns_now.core.domain.model.ArtifactReadinessState
import io.hrns_now.core.domain.model.ExecutionWrapperState
import io.hrns_now.core.domain.model.QueueBlockedReason
import io.hrns_now.core.domain.model.QueueStatus
import io.hrns_now.core.domain.model.RepositoryStatus
import io.hrns_now.core.domain.model.StateInvalidKind
import io.hrns_now.core.domain.model.StopReason
import io.hrns_now.core.domain.model.UnknownDomainKind
import io.hrns_now.core.domain.model.WorkflowPhase
import io.hrns_now.core.domain.model.WorkflowState
import io.hrns_now.core.domain.model.WorkflowStatus
import io.hrns_now.core.result.StateReadResult

/**
 * 하루 마감 checklist의 typed 결과다(`doc/hrns_now_design_pattern.md` §11).
 *
 * `Blocked`는 하나라도 있으면 "마감 검증 실행"을 비활성화해야 하는 필수 선행조건 위반이다.
 * `RequiresExplicitIncompleteHandoff`는 필수 조건은 모두 만족하지만 repository에 아직
 * 커밋되지 않은 변경이 있어 사용자가 "이 상태로 마감함"을 명시적으로 인지해야 하는 경우다 —
 * 자동으로 막거나 자동으로 진행하지 않는다. 두 결과 모두 문구가 아니라 typed key/raw 변경 경로만
 * 낸다 — presentation이 locale별 문구로 투영한다.
 */
sealed interface ClosureDecision {
    data object Allowed : ClosureDecision
    data class Blocked(val reasons: List<ClosureBlockReasonKey>) : ClosureDecision
    /** [changedPaths]는 repository의 원문 상대 경로다 — 표시 문구 접두어는 presentation이 붙인다. */
    data class RequiresExplicitIncompleteHandoff(val changedPaths: List<String>) : ClosureDecision
}

data class ClosureContext(
    val stateRead: StateReadResult,
    val lockHeld: Boolean,
    val repositoryStatus: RepositoryStatus,
)

/**
 * 실행 process 성공과 하루 마감 허용을 분리하는 순수 checklist 정책이다.
 *
 * `state.closure.validated`/`is_clean_handoff`는 harness의 결정적 `pre_handoff_validate.py`/`.ps1`가
 * 이미 계산한 결과이므로 이 정책이 다시 계산하지 않는다 — 이미 그 값이 true인지가 아니라
 * top-level 완료 flag와 **정합성이 맞는지**만 검사한다(하나만 true인 상태는 오염된 State다).
 * "마감 검증 실행"을 처음 누르는 정상 경로는 둘 다 false로 시작하므로 이 조건에 걸리지 않는다.
 * lock/repository는 harness가 전혀 모르는 UI 쪽 조건이라 이 정책이 직접 평가한다.
 */
class ClosurePolicy {
    fun evaluate(context: ClosureContext): ClosureDecision {
        val state = (context.stateRead as? StateReadResult.Success)?.state
            ?: return ClosureDecision.Blocked(listOf(ClosureBlockReasonKey.StateInvalid(stateInvalidKind(context.stateRead))))

        val reasons = buildList {
            addAll(unknownDomainReasons(state))

            if (requiredArtifacts(state).any { it !is ArtifactReadinessState.Ready }) {
                add(ClosureBlockReasonKey.RequiredArtifactsNotReady)
            }
            if (!state.opsValidation.passed) {
                add(ClosureBlockReasonKey.OpsValidationFailed)
            }
            if (!state.queue.active.cardId.isNullOrBlank() || !state.queue.active.sliceId.isNullOrBlank()) {
                add(ClosureBlockReasonKey.ActiveSliceRemaining)
            }
            if (!state.resumeFromStepId.isNullOrBlank()) {
                add(ClosureBlockReasonKey.ResumeStepRemaining)
            }
            if (state.closure.validated != state.closureValidated) {
                add(ClosureBlockReasonKey.ClosureValidationFlagMismatch)
            }
            if (state.closure.validated != state.closure.isCleanHandoff) {
                add(ClosureBlockReasonKey.ClosureFieldConsistencyMismatch)
            }
            if (state.cleanHandoff != state.closure.isCleanHandoff) {
                add(ClosureBlockReasonKey.CleanHandoffConsistencyMismatch)
            }
            if (context.lockHeld) {
                add(ClosureBlockReasonKey.LockHeldByOtherRun)
            }
        }
        if (reasons.isNotEmpty()) {
            return ClosureDecision.Blocked(reasons)
        }

        val repositoryDirty = context.repositoryStatus as? RepositoryStatus.Dirty
        return if (repositoryDirty != null && repositoryDirty.changedPaths.isNotEmpty()) {
            ClosureDecision.RequiresExplicitIncompleteHandoff(repositoryDirty.changedPaths)
        } else {
            ClosureDecision.Allowed
        }
    }

    private fun requiredArtifacts(state: WorkflowState) = listOf(
        state.artifacts.requestInbox,
        state.artifacts.todayStrategy,
        state.artifacts.dailyHandoff,
        state.artifacts.workflowState,
    )

    private fun stateInvalidKind(stateRead: StateReadResult): StateInvalidKind =
        when (stateRead) {
            is StateReadResult.Missing -> StateInvalidKind.Missing
            is StateReadResult.Malformed -> StateInvalidKind.Malformed
            is StateReadResult.EncodingError -> StateInvalidKind.EncodingError
            is StateReadResult.UnsupportedSchema -> StateInvalidKind.UnsupportedSchema
            is StateReadResult.AccessDenied -> StateInvalidKind.AccessDenied
            is StateReadResult.Success -> error("unreachable — Success는 호출 전에 걸러진다")
        }

    private fun unknownDomainReasons(state: WorkflowState): List<ClosureBlockReasonKey> = buildList {
        (state.phase as? WorkflowPhase.Unknown)?.let { add(ClosureBlockReasonKey.UnknownDomainValue(UnknownDomainKind.Phase)) }
        (state.status as? WorkflowStatus.Unknown)?.let { add(ClosureBlockReasonKey.UnknownDomainValue(UnknownDomainKind.Status)) }
        (state.queue.status as? QueueStatus.Unknown)?.let { add(ClosureBlockReasonKey.UnknownDomainValue(UnknownDomainKind.QueueStatus)) }
        (state.queue.blockedReason as? QueueBlockedReason.Other)?.let {
            add(ClosureBlockReasonKey.UnknownDomainValue(UnknownDomainKind.QueueBlockedReason))
        }
        (state.stopReason as? StopReason.Unknown)?.let { add(ClosureBlockReasonKey.UnknownDomainValue(UnknownDomainKind.StopReason)) }
        (state.executionWrapper as? ExecutionWrapperState.Unknown)?.let {
            add(ClosureBlockReasonKey.UnknownDomainValue(UnknownDomainKind.ExecutionWrapper))
        }
    }
}
