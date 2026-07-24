package io.hrns_now.core.usecase

import io.hrns_now.core.domain.model.HarnessProject
import io.hrns_now.core.domain.model.ProjectId
import io.hrns_now.core.port.ProjectRegistryPort
import io.hrns_now.core.result.RegistrySaveResult

sealed interface SelectProjectResult {
    data class Selected(val project: HarnessProject) : SelectProjectResult
    data object NotFound : SelectProjectResult
    data class SaveFailed(val message: String) : SelectProjectResult
}

/**
 * 사용자가 명시적으로 프로젝트를 전환할 때 쓰는 use case다. Registry에 "마지막으로 활성화된
 * 프로젝트"를 함께 기록해, 다음 실행에서 [ResolveActiveProjectUseCase]가 같은 프로젝트를
 * 우선 선택할 수 있게 한다.
 */
class SelectProjectUseCase(
    private val registry: ProjectRegistryPort,
) {
    suspend operator fun invoke(id: ProjectId): SelectProjectResult {
        val project = registry.findById(id) ?: return SelectProjectResult.NotFound
        return when (val saved = registry.markActive(id)) {
            RegistrySaveResult.Success -> SelectProjectResult.Selected(project)
            is RegistrySaveResult.Failed -> SelectProjectResult.SaveFailed(saved.message)
        }
    }
}
