package io.hrns_now.core.domain.model

/**
 * [io.hrns_now.core.domain.policy.ActionPolicy]가 판정한 차단/안내 사유의 typed key다.
 * 사용자에게 보일 문구 자체는 만들지 않는다 — presentation 계층이 locale별 문구로 투영한다
 * (Phase 8 보완, `doc/claude_prompts/phase8-completion-localization-native-qa.md` §1). `core`는
 * Compose나 `AppLocale`에 의존하지 않는다.
 */
sealed interface BlockedReasonKey {
    data object ProjectNotConnected : BlockedReasonKey
    data object DayNotSelected : BlockedReasonKey
    data class StateInvalid(val kind: StateInvalidKind) : BlockedReasonKey
    data object UnsupportedCompatibility : BlockedReasonKey
    data object BoundaryInvalid : BlockedReasonKey
    data object PastDateReadOnly : BlockedReasonKey
    data class ProcessBusy(val status: ProcessRunStatus) : BlockedReasonKey
    data class UnknownDomainValue(val kind: UnknownDomainKind) : BlockedReasonKey
    data object DispatchMetadataConflict : BlockedReasonKey
    data class StopReasonBlocking(val reason: StopReason) : BlockedReasonKey
    data object HumanActionRequired : BlockedReasonKey
    data object ClosureValidationMismatch : BlockedReasonKey
    data object ClosurePhaseMismatch : BlockedReasonKey
    data object ExecutionCompletionMismatch : BlockedReasonKey
    data object PlanningFailed : BlockedReasonKey
    data object GenericExecutionBlocked : BlockedReasonKey
    data object ExecutionContractUnclear : BlockedReasonKey
    data object CodeSliceTargetMissing : BlockedReasonKey
    data object DocSliceTargetMissing : BlockedReasonKey
    data object ExecutionReadyUnknownSlice : BlockedReasonKey
}

enum class StateInvalidKind { NotYetRead, Missing, Malformed, EncodingError, UnsupportedSchema, AccessDenied }

enum class UnknownDomainKind {
    Phase, Status, QueueStatus, QueueBlockedReason, ExecutionWrapper, StopReason, ArtifactReadiness, ActiveSliceKind
}
