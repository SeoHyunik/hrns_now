package io.hrns_now.core.domain.model

/**
 * `git status --short`의 read-only typed 결과다. UI는 이 값을 만들기 위해 어떤 git 명령도
 * 쓰지 않는다(add/commit/reset/checkout/stash 전부 금지).
 */
sealed interface RepositoryStatus {
    data object Clean : RepositoryStatus
    data class Dirty(val changedPaths: List<String>) : RepositoryStatus

    /** git이 없거나, repository가 아니거나, 명령이 실패한 경우다 — 마감을 강제로 막지 않는다. */
    data object Unknown : RepositoryStatus
}
