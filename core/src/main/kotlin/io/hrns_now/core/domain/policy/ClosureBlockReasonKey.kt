package io.hrns_now.core.domain.policy

import io.hrns_now.core.domain.model.StateInvalidKind
import io.hrns_now.core.domain.model.UnknownDomainKind

/**
 * [ClosurePolicy]가 판정한 마감 차단 사유의 typed key다. 문구는 만들지 않는다 — presentation이
 * locale별 문구로 투영한다(Phase 8 보완, `doc/claude_prompts/
 * phase8-completion-localization-native-qa.md` §1). `core`는 Compose나 `AppLocale`에 의존하지 않는다.
 */
sealed interface ClosureBlockReasonKey {
    data class StateInvalid(val kind: StateInvalidKind) : ClosureBlockReasonKey
    data class UnknownDomainValue(val kind: UnknownDomainKind) : ClosureBlockReasonKey
    data object RequiredArtifactsNotReady : ClosureBlockReasonKey
    data object OpsValidationFailed : ClosureBlockReasonKey
    data object ActiveSliceRemaining : ClosureBlockReasonKey
    data object ResumeStepRemaining : ClosureBlockReasonKey
    data object ClosureValidationFlagMismatch : ClosureBlockReasonKey
    data object ClosureFieldConsistencyMismatch : ClosureBlockReasonKey
    data object CleanHandoffConsistencyMismatch : ClosureBlockReasonKey
    data object LockHeldByOtherRun : ClosureBlockReasonKey
}
