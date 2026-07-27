package io.hrns_now.core.domain.policy

import io.hrns_now.core.domain.model.ArtifactReadinessState
import io.hrns_now.core.domain.model.ExecutionWrapperState
import io.hrns_now.core.domain.model.QueueBlockedReason
import io.hrns_now.core.domain.model.QueueStatus
import io.hrns_now.core.domain.model.RepositoryStatus
import io.hrns_now.core.domain.model.StopReason
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
 * 자동으로 막거나 자동으로 진행하지 않는다.
 */
sealed interface ClosureDecision {
    data object Allowed : ClosureDecision
    data class Blocked(val reasons: List<String>) : ClosureDecision
    data class RequiresExplicitIncompleteHandoff(val items: List<String>) : ClosureDecision
}

data class ClosureContext(
    val stateRead: StateReadResult,
    val lockHeld: Boolean,
    val repositoryStatus: RepositoryStatus,
)

/**
 * 실행 process 성공과 하루 마감 허용을 분리하는 순수 checklist 정책이다
 * (`doc/claude_prompts/phase5-closure-recovery.md` §1).
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
            ?: return ClosureDecision.Blocked(listOf(stateInvalidReason(context.stateRead)))

        val reasons = buildList {
            addAll(unknownDomainReasons(state))

            if (requiredArtifacts(state).any { it !is ArtifactReadinessState.Ready }) {
                add("필수 daily 파일(REQUEST_INBOX/TODAY_STRATEGY/DAILY_HANDOFF/WORKFLOW_STATE)이 모두 준비되지 않았습니다.")
            }
            if (!state.opsValidation.passed) {
                add("운영 검증(ops validation)을 통과하지 못했습니다.")
            }
            if (!state.queue.active.cardId.isNullOrBlank() || !state.queue.active.sliceId.isNullOrBlank()) {
                add("아직 활성 slice가 남아 있습니다.")
            }
            if (!state.resumeFromStepId.isNullOrBlank()) {
                add("재개해야 할 step이 남아 있습니다.")
            }
            if (state.closure.validated != state.closureValidated) {
                add("closure 검증 상태와 상위 완료 flag가 서로 일치하지 않습니다.")
            }
            if (state.closure.validated != state.closure.isCleanHandoff) {
                add("closure 필드(validated/is_clean_handoff) 간 정합성이 맞지 않습니다.")
            }
            if (state.cleanHandoff != state.closure.isCleanHandoff) {
                add("clean_handoff와 closure.is_clean_handoff 간 정합성이 맞지 않습니다.")
            }
            if (context.lockHeld) {
                add("다른 실행이 이 프로젝트·날짜의 잠금을 보유하고 있습니다.")
            }
        }
        if (reasons.isNotEmpty()) {
            return ClosureDecision.Blocked(reasons)
        }

        val repositoryDirty = context.repositoryStatus as? RepositoryStatus.Dirty
        return if (repositoryDirty != null && repositoryDirty.changedPaths.isNotEmpty()) {
            ClosureDecision.RequiresExplicitIncompleteHandoff(
                repositoryDirty.changedPaths.map { "커밋되지 않은 변경: $it" },
            )
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

    private fun stateInvalidReason(stateRead: StateReadResult): String =
        when (stateRead) {
            is StateReadResult.Missing -> "상태 파일이 없습니다."
            is StateReadResult.Malformed -> "상태 파일을 해석할 수 없습니다."
            is StateReadResult.EncodingError -> "상태 파일 인코딩을 해석할 수 없습니다."
            is StateReadResult.UnsupportedSchema -> "지원하지 않는 schema 버전입니다."
            is StateReadResult.AccessDenied -> "상태 파일에 접근할 수 없습니다."
            is StateReadResult.Success -> error("unreachable — Success는 호출 전에 걸러진다")
        }

    private fun unknownDomainReasons(state: WorkflowState): List<String> = buildList {
        (state.phase as? WorkflowPhase.Unknown)?.let { add("알 수 없는 workflow phase입니다.") }
        (state.status as? WorkflowStatus.Unknown)?.let { add("알 수 없는 workflow status입니다.") }
        (state.queue.status as? QueueStatus.Unknown)?.let { add("알 수 없는 queue status입니다.") }
        (state.queue.blockedReason as? QueueBlockedReason.Other)?.let { add("알 수 없는 queue 차단 사유가 있습니다.") }
        (state.stopReason as? StopReason.Unknown)?.let { add("알 수 없는 stop reason입니다.") }
        (state.executionWrapper as? ExecutionWrapperState.Unknown)?.let {
            add("알 수 없는 execution wrapper입니다.")
        }
    }
}
