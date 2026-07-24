package io.hrns_now.core.usecase

import io.hrns_now.core.domain.model.ProjectId
import io.hrns_now.core.port.ProjectRegistryPort
import io.hrns_now.core.result.RegistrySaveResult

/** 명시적 사용자 이벤트로만 호출되는 프로젝트 삭제 use case다. */
class DeleteProjectUseCase(
    private val registry: ProjectRegistryPort,
) {
    suspend operator fun invoke(id: ProjectId): RegistrySaveResult = registry.delete(id)
}
