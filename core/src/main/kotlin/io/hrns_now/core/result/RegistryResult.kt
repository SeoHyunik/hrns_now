package io.hrns_now.core.result

import io.hrns_now.core.domain.model.HarnessProject
import io.hrns_now.core.domain.model.ProjectId
import java.nio.file.Path

/**
 * [io.hrns_now.core.port.ProjectRegistryPort.findAll] 결과다. 손상된 Registry를 조용히 빈
 * 목록으로 바꾸지 않는다 — [RecoveredFromCorruption]이 원본 백업 위치와 진단 문구를 UI까지
 * 전달한다(`doc/claude_prompts/phase1d-project-registry.md`).
 */
sealed interface RegistryLoadResult {
    data class Success(
        val projects: List<HarnessProject>,
        val lastActiveProjectId: ProjectId?,
    ) : RegistryLoadResult

    /** JSON 전체 또는 일부 project entry가 손상되어 격리(quarantine) 복사본을 만든 뒤 복구했다. */
    data class RecoveredFromCorruption(
        val projects: List<HarnessProject>,
        val lastActiveProjectId: ProjectId?,
        val quarantinePath: Path,
        val message: String,
    ) : RegistryLoadResult

    /** 파일을 읽을 수 없음(권한 등) — 손상 복구 대상조차 아니다. */
    data class Unreadable(val message: String) : RegistryLoadResult
}

sealed interface RegistrySaveResult {
    data object Success : RegistrySaveResult
    data class Failed(val message: String) : RegistrySaveResult
}
