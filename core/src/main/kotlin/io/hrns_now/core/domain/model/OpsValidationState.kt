package io.hrns_now.core.domain.model

/**
 * `state.ops_validation`의 typed 표현이다.
 *
 * `validated_at`은 ISO-8601 문자열로 원문 보존한다 — Phase 1A는 시각 계산을 하지 않으므로
 * `Instant` 파싱에서 발생할 수 있는 timezone/format 예외 표면을 넓히지 않는다.
 */
data class OpsValidationState(
    val passed: Boolean,
    val validatedAt: String?,
    val notes: String?,
)
