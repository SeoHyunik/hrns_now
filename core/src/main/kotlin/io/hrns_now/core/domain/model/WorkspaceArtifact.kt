package io.hrns_now.core.domain.model

enum class ArtifactProbeState {
    WorkspaceNotConfigured,
    Exists,
    Missing,
    NotReadable,
    WrongType,
    Unknown
}

enum class ArtifactKind {
    File,
    Directory
}

/**
 * Required = 현행 4-file daily surface. Optional = 존재하면 유용하지만 누락이 실패가 아닌 산출물.
 * Legacy = 폐기된 fallback 계약 파일로, readiness 계산에 포함하지 않고 기본 화면에서 숨긴다.
 */
enum class ArtifactRequirement {
    Required,
    Optional,
    Legacy
}

data class ArtifactProbeResult(
    val label: String,
    val path: String,
    val kind: ArtifactKind,
    val requirement: ArtifactRequirement,
    val state: ArtifactProbeState,
    val message: String
)

data class WorkspaceArtifactSummary(
    val items: List<ArtifactProbeResult>
) {
    val requiredItems: List<ArtifactProbeResult>
        get() = items.filter { it.requirement == ArtifactRequirement.Required }

    /** Optional/Legacy 상태와 무관하게, Required 항목이 모두 Exists일 때만 true. */
    val isRequiredReady: Boolean
        get() = requiredItems.isNotEmpty() && requiredItems.all { it.state == ArtifactProbeState.Exists }
}
