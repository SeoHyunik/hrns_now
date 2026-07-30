package io.hrns_now.core.usecase

import io.hrns_now.core.port.ProjectRegistryPort
import io.hrns_now.core.result.RegistrySaveResult

/**
 * 활성 프로젝트 선택만 해제하는 use case다(Phase 9 QA03-A). Registry에 등록된 project entry,
 * `%APPDATA%` Registry 외 사용자 데이터, workspace, repository, Harness Kit, State/daily 파일은
 * 전혀 삭제·수정하지 않는다 — [ProjectRegistryPort.clearActive]가 "마지막으로 활성화된 프로젝트"
 * 표시만 지운다.
 */
class ClearActiveProjectUseCase(
    private val registry: ProjectRegistryPort,
) {
    suspend operator fun invoke(): RegistrySaveResult = registry.clearActive()
}
