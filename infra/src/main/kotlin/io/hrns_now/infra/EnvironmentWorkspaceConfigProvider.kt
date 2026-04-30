package io.hrns_now.infra

import io.hrns_now.core.config.RuntimeConfig
import io.hrns_now.core.config.WorkspaceConfig
import io.hrns_now.core.config.WorkspaceRoots

class EnvironmentWorkspaceConfigProvider(
    private val environment: Map<String, String> = System.getenv()
) {
    fun config(): WorkspaceConfig =
        WorkspaceConfig(
            workspaceName = null,
            profileName = "기본",
            roots = WorkspaceRoots(
                kitRoot = env("HRNS_KIT_ROOT"),
                workspaceRoot = env("HRNS_WORKSPACE_ROOT"),
                projectRoot = env("HRNS_PROJECT_ROOT"),
            ),
            runtime = RuntimeConfig(
                powerShellPath = env("HRNS_POWERSHELL_PATH"),
                claudeCommand = env("HRNS_CLAUDE_COMMAND"),
                uiLanguage = "ko",
            ),
        )

    private fun env(name: String): String? =
        environment[name]?.trim()?.takeIf { it.isNotEmpty() }
}
