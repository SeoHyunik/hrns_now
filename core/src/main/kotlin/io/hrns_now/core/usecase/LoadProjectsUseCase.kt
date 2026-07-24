package io.hrns_now.core.usecase

import io.hrns_now.core.port.ProjectRegistryPort
import io.hrns_now.core.result.RegistryLoadResult

/** Registry에 등록된 프로젝트 목록을 읽는 read-only query다. */
class LoadProjectsUseCase(
    private val registry: ProjectRegistryPort,
) {
    suspend operator fun invoke(): RegistryLoadResult = registry.findAll()
}
