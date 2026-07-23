package io.hrns_now.core.domain.model

/**
 * `state.artifacts_state.*` 개별 항목의 typed 표현이다.
 *
 * 실제 운영 workspace의 live `WORKFLOW_STATE.json` 스냅샷에서 관측된 값은 `ready`뿐이다.
 * 다른 값(`missing`, `stale` 등)은 존재를 확인하지 못했으므로 창작하지 않고, 관측되지 않은
 * 값은 전부 [Unknown]으로 원문을 보존한다.
 */
sealed interface ArtifactReadinessState {
    data object Ready : ArtifactReadinessState
    data class Unknown(val raw: String) : ArtifactReadinessState
}

fun String.toArtifactReadinessState(): ArtifactReadinessState =
    when (this) {
        "ready" -> ArtifactReadinessState.Ready
        else -> ArtifactReadinessState.Unknown(this)
    }

/** `state.artifacts_state`의 4-file 최소 필드. 네 항목 모두 필수다 — 하나라도 누락되면 Mapper가 실패 처리한다. */
data class ArtifactsState(
    val requestInbox: ArtifactReadinessState,
    val todayStrategy: ArtifactReadinessState,
    val dailyHandoff: ArtifactReadinessState,
    val workflowState: ArtifactReadinessState,
)
