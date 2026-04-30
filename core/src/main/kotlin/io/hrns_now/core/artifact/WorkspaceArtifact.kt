package io.hrns_now.core.artifact

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

data class ArtifactProbeResult(
    val label: String,
    val path: String,
    val kind: ArtifactKind,
    val state: ArtifactProbeState,
    val message: String
)

data class WorkspaceArtifactSummary(
    val items: List<ArtifactProbeResult>
)
