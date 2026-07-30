package io.hrns_now.infra.bridge

import io.hrns_now.core.domain.model.BridgeFileState
import io.hrns_now.core.domain.model.RepositoryBridgeSummary
import io.hrns_now.core.port.RepositoryBridgeProbePort
import java.nio.file.Files
import java.nio.file.Path

/**
 * `scripts/enter-project.ps1`이 만드는 repository bridge 3종을 read-only로 확인한다(Phase 10).
 * 어떤 경로도 만들거나 쓰지 않는다 — [Files.isRegularFile] 조회만 수행한다.
 */
class RepositoryBridgeProbe : RepositoryBridgeProbePort {
    override fun probe(repositoryRoot: Path): RepositoryBridgeSummary =
        RepositoryBridgeSummary(
            settingsLocalJson = repositoryRoot.resolve(".claude/settings.local.json").state(),
            projectClaudeMd = repositoryRoot.resolve(".claude/CLAUDE.md").state(),
            toolsRunCycle = repositoryRoot.resolve("tools/run-cycle.ps1").state(),
        )

    private fun Path.state(): BridgeFileState =
        if (Files.isRegularFile(this)) BridgeFileState.Ready else BridgeFileState.Missing
}
