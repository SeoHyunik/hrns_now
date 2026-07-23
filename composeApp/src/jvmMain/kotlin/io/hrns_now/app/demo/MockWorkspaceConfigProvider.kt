package io.hrns_now.app.demo

import io.hrns_now.core.config.RuntimeConfig
import io.hrns_now.core.config.WorkspaceConfig
import io.hrns_now.core.config.WorkspaceReadiness
import io.hrns_now.core.config.WorkspaceRoots

/** demo/시연 전용 mock provider. 실제 infrastructure adapter가 아니므로 infra 모듈에 두지 않는다. */
class MockWorkspaceConfigProvider {
    fun config(): WorkspaceConfig =
        WorkspaceConfig(
            workspaceName = null,
            profileName = "기본",
            roots = WorkspaceRoots(
                kitRoot = null,
                workspaceRoot = null,
                projectRoot = null,
            ),
            runtime = RuntimeConfig(
                powerShellPath = null,
                claudeCommand = null,
                uiLanguage = "ko",
            ),
        )

    fun readiness(): WorkspaceReadiness =
        WorkspaceReadiness(
            engineLabel = "오프라인",
            workspaceLabel = "미선택",
            bridgeLabel = "미확인",
            profileLabel = "기본",
            doctorLabel = "대기",
        )
}
