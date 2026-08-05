package io.hrns_now.core.domain.model

import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate

/**
 * Registry에 저장된 하나의 Harness 프로젝트 등록 정보다(`doc/hrns_now_claude_plan.md` §3.2,
 * `doc/hrns_now_design_pattern.md` §10). Registry는 이 정보만 저장한다 — secret·token·
 * raw session ID·응답 원문은 이 타입에 포함하지 않는다.
 */
data class HarnessProject(
    val id: ProjectId,
    val displayName: String,
    /**
     * 이 프로젝트의 Harness runtime을 어디서 가져오는지에 대한 typed 선택이다
     * (`doc/hrns_now_design_pattern.md` §20.1). 실제 파일 시스템 root가 필요한 곳(command 실행,
     * compatibility, boundary 검사)은 이 값을 직접 쓰지 않고 [io.hrns_now.core.port.RuntimeSourceResolverPort]가
     * 만든 `RuntimeResolution.Resolved.root`만 전달받는다.
     */
    val runtimeSource: RuntimeSource,
    val projectWorkspaceRoot: Path,
    val repositoryRoot: Path,
    val profileId: String,
    val lastSelectedDate: LocalDate?,
    val lastDiagnosticsSummary: String?,
    val lastRunAt: Instant?,
)
