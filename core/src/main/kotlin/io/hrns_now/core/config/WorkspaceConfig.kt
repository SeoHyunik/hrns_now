package io.hrns_now.core.config

data class WorkspaceRoots(
    val kitRoot: String?,
    val workspaceRoot: String?,
    val projectRoot: String?
)

data class RuntimeConfig(
    val powerShellPath: String?,
    val claudeCommand: String?,
    val uiLanguage: String = "ko"
)

data class WorkspaceConfig(
    val workspaceName: String?,
    val profileName: String,
    val roots: WorkspaceRoots,
    val runtime: RuntimeConfig
)

data class WorkspaceReadiness(
    val engineLabel: String,
    val workspaceLabel: String,
    val bridgeLabel: String,
    val profileLabel: String,
    val doctorLabel: String
)
