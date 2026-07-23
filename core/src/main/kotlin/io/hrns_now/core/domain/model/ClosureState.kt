package io.hrns_now.core.domain.model

/** `state.closure`의 typed 표현이다. `validated_at`은 원문 문자열로 보존한다(OpsValidationState와 동일 정책). */
data class ClosureState(
    val isCleanHandoff: Boolean,
    val validated: Boolean,
    val validatedAt: String?,
    val validatorNotes: String?,
)
