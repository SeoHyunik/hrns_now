package io.hrns_now.core.usecase

import io.hrns_now.core.domain.model.HarnessProject
import io.hrns_now.core.port.ProjectRegistryPort
import io.hrns_now.core.result.RegistrySaveResult
import java.time.LocalDate

/**
 * Registry 프로젝트의 마지막 명시 날짜만 갱신한다. 경로는 등록 시 이미 boundary 검증을
 * 통과한 동일 프로젝트 값이고, 이 use case는 Harness daily 파일을 쓰지 않는다.
 */
class SelectWorkspaceDayUseCase(
    private val registry: ProjectRegistryPort,
) {
    suspend operator fun invoke(project: HarnessProject, date: LocalDate): RegistrySaveResult =
        registry.save(project.copy(lastSelectedDate = date))
}