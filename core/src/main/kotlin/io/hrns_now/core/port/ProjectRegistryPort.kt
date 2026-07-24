package io.hrns_now.core.port

import io.hrns_now.core.domain.model.HarnessProject
import io.hrns_now.core.domain.model.ProjectId
import io.hrns_now.core.result.RegistryLoadResult
import io.hrns_now.core.result.RegistrySaveResult

/**
 * 프로젝트 Registry의 정본 port다(`doc/hrns_now_design_pattern.md` §10). `findAll`/`save`는
 * 원래 계획서 시그니처(`List<HarnessProject>`/`Unit`)에서 typed 결과로 최소 정교화했다 —
 * repository 의미(조회 4종)는 그대로 유지하되, "손상 JSON을 빈 목록으로 숨기지 않는다"와
 * "저장 실패 시 기존 파일을 보존한다"는 계약이 타입에 드러나야 하기 때문이다(근거는
 * `doc/phase_reports/phase1d-report.md`에 기록).
 *
 * `markActive`는 "Registry의 마지막 선택"(선택 우선순위 1순위)을 저장하는 별도 동작이다 —
 * 프로젝트 필드 자체(`save`)와는 다른, Registry 전체에 대한 "마지막으로 활성화된 프로젝트"
 * 메타데이터이므로 분리했다.
 *
 * 구현체는 JSON DTO, `%APPDATA%`, `Files`를 직접 알아도 되지만 이 interface 자체는 모른다.
 */
interface ProjectRegistryPort {
    suspend fun findAll(): RegistryLoadResult
    suspend fun findById(id: ProjectId): HarnessProject?
    suspend fun save(project: HarnessProject): RegistrySaveResult
    suspend fun delete(id: ProjectId): RegistrySaveResult
    suspend fun markActive(id: ProjectId): RegistrySaveResult
}
