package io.hrns_now.core.config

enum class PathProbeState {
    NotConfigured,
    Exists,
    Missing,
    NotReadable,
    WrongType,
    Unknown
}

enum class PathProbeKind {
    Directory,
    File,
    Command
}

data class PathProbeResult(
    val label: String,
    val rawPath: String?,
    val kind: PathProbeKind,
    val state: PathProbeState,
    val message: String
)

data class WorkspaceProbeSummary(
    val kitRoot: PathProbeResult,
    val workspaceRoot: PathProbeResult,
    val projectRoot: PathProbeResult,
    val powerShellPath: PathProbeResult,
    val claudeCommand: PathProbeResult
)
