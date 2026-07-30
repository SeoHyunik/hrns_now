package io.hrns_now.app.presentation.model

import io.hrns_now.core.config.WorkspaceConfig
import io.hrns_now.core.config.WorkspaceProbeSummary
import io.hrns_now.core.config.WorkspaceReadiness
import io.hrns_now.core.domain.model.ProjectId
import io.hrns_now.core.domain.model.WorkspaceArtifactSummary
import java.time.LocalDate

/** Setup 화면의 Registry 목록 한 행이다. `label`은 표시 전용이며 [id]가 유일한 식별자다. */
data class RegistryProjectItem(
    val id: ProjectId,
    val label: String,
    val isActive: Boolean,
    /** "개발용 내장 SDK" 또는 "외부 Harness Kit" — 기존 external project도 명확히 구분한다(새 Phase 7). */
    val runtimeSourceLabel: String = "외부 Harness Kit",
)

/** Workspace 탐색에서 확인된 유효한 yyyy-MM-dd 디렉터리 한 행이다. */
data class WorkspaceDayItem(
    val date: LocalDate,
    val isSelected: Boolean,
)

/**
 * [io.hrns_now.app.presentation.viewmodel.AppViewModel]이 소유하는 단일 불변 UI 상태다
 * (`doc/hrns_now_design_pattern.md` §8.3). Compose는 이 값을 그리기만 한다.
 *
 * 최초 진입 시 [Loading]이며, 첫 실데이터 조립이 끝나면 [Ready]로 전이한 뒤에는 새로고침/폴링
 * 때마다 [Ready]를 갱신만 한다 — stale/오류는 [Ready.cockpit]의 진단 정보로 표현하고, 이
 * 최상위 상태를 다시 [Loading]으로 되돌리지 않는다.
 */
sealed interface HrnsUiState {
    data object Loading : HrnsUiState

    data class Ready(
        val shell: ShellProjection,
        val setup: SetupProjection,
        val workspaceConfig: WorkspaceConfig,
        val workspaceProbeSummary: WorkspaceProbeSummary,
        val workspaceReadiness: WorkspaceReadiness,
        val workspaceArtifactSummary: WorkspaceArtifactSummary,
        val cockpit: CockpitProjection,
        val todayWork: TodayWorkProjection,
        val runStatus: RunStatusProjection,
        val recovery: RecoveryProjection,
        val registryProjects: List<RegistryProjectItem>,
        val workspaceDays: List<WorkspaceDayItem>,
        /** 오늘 날짜다(새 Phase 8 §6) — 아직 daily directory가 없어도 오늘 시작을 안내하는 데 쓴다. */
        val todayDate: LocalDate,
        val activeProjectSourceLabel: String,
        val registryMessage: String?,
        /** "진단 후 등록"의 진행/결과다(새 Phase 8 §1) — modal 안에서 직접 렌더링한다. */
        val registrationFeedback: RegistrationFeedback = RegistrationFeedback.Idle,
        /**
         * 활성 프로젝트가 있지만 repository bridge 또는 오늘 external workspace 준비가 누락됐을
         * 때만 true다(Phase 10). 이 값이 true인 동안만 Setup 화면이 Health Check와 구분되는
         * 단일 "프로젝트 준비" CTA를 보여준다.
         */
        val needsProjectPreparation: Boolean = false,
    ) : HrnsUiState
}
