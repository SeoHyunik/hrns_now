package io.hrns_now.core.port

import io.hrns_now.core.domain.model.RepositoryBridgeSummary
import java.nio.file.Path

/**
 * repository bridge 3종(`.claude/settings.local.json`, `.claude/CLAUDE.md`, `tools/run-cycle.ps1`)의
 * 존재 여부만 읽는 read-only port다(Phase 10). 이 port는 파일을 만들거나 고치지 않는다 —
 * bridge 생성은 오직 `scripts/enter-project.ps1`(typed `HarnessCommand.OnboardProject`)만
 * 수행한다. Composable/ViewModel이 `Files.exists`와 경로 조합을 직접 하지 않도록 외부
 * repository filesystem 경계를 여기 하나로 분리한다.
 */
fun interface RepositoryBridgeProbePort {
    fun probe(repositoryRoot: Path): RepositoryBridgeSummary
}
