package io.hrns_now.core.domain.model

/**
 * [io.hrns_now.core.domain.policy.ActionPolicy]의 순수 결과다.
 *
 * 불변식은 생성 시점에 강제한다 — 잘못된 조합은 애초에 만들 수 없다.
 * - `primary`가 있으면 반드시 `allowed`에 포함된다.
 * - `primary`는 최대 하나다(타입 자체가 단일 값이므로 자연히 성립).
 * - `allowed`는 `Set`이므로 중복이 없다.
 *
 * write/execute가 잠긴 상황에서 해당 action을 `allowed`에 넣지 않는 것은 이 타입이 아니라
 * [io.hrns_now.core.domain.policy.ActionPolicy]의 책임이다 — 이 타입은 결과를 표현만 한다.
 */
data class RecommendedActions(
    val primary: UiAction?,
    val allowed: Set<UiAction>,
    /** 문구가 아니라 typed key다 — presentation이 locale별 문구로 투영한다. */
    val reasonKey: BlockedReasonKey?,
) {
    init {
        require(primary == null || primary in allowed) {
            "primary($primary)는 allowed($allowed)에 포함되어야 한다"
        }
    }
}
