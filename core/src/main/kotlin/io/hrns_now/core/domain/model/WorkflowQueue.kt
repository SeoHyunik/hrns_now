package io.hrns_now.core.domain.model

/**
 * `queue.status`의 typed 표현이다. 알려진 값은 `scripts/lib/state-surface.ps1`,
 * `scripts/lib/plan`(planning-queue-projection 등)에서 실측 확인한 리터럴이다.
 */
sealed interface QueueStatus {
    data object Active : QueueStatus
    data object Empty : QueueStatus
    data object Blocked : QueueStatus
    data object Completed : QueueStatus
    data object PlanningRequired : QueueStatus
    data class Unknown(val raw: String) : QueueStatus
}

fun String.toQueueStatus(): QueueStatus =
    when (this) {
        "active" -> QueueStatus.Active
        "empty" -> QueueStatus.Empty
        "blocked" -> QueueStatus.Blocked
        "completed" -> QueueStatus.Completed
        "planning_required" -> QueueStatus.PlanningRequired
        else -> QueueStatus.Unknown(this)
    }

/**
 * `queue.blocked_reason`은 대부분 진단 문구지만, planning이 실행을 잠글 때 사용하는
 * `dispatch_metadata_conflict`는 Phase 1B CTA에서 재계획으로 분기해야 하는 typed marker다.
 */
sealed interface QueueBlockedReason {
    data object DispatchMetadataConflict : QueueBlockedReason
    data class Other(val raw: String) : QueueBlockedReason
}

fun String?.toQueueBlockedReason(): QueueBlockedReason? =
    when {
        this.isNullOrBlank() -> null
        this == "dispatch_metadata_conflict" -> QueueBlockedReason.DispatchMetadataConflict
        else -> QueueBlockedReason.Other(this)
    }

/** `queue.active.card_id` / `queue.active.slice_id`. 활성 slice가 없으면 두 필드 모두 null이다. */
data class QueuePointer(
    val cardId: String?,
    val sliceId: String?,
)

/** `queue` 최상위 최소 필드. */
data class WorkflowQueue(
    val status: QueueStatus,
    val active: QueuePointer,
    val blockedReason: QueueBlockedReason?,
    val lastUpdatedAt: String?,
)
