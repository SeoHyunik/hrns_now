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
    val kitRoot: Path,
    val projectWorkspaceRoot: Path,
    val repositoryRoot: Path,
    val profileId: String,
    val lastSelectedDate: LocalDate?,
    val lastDiagnosticsSummary: String?,
    val lastRunAt: Instant?,
)
