package io.hrns_now.app.presentation.model

import io.hrns_now.core.config.WorkspaceConfig
import io.hrns_now.core.config.WorkspaceProbeSummary
import io.hrns_now.core.config.WorkspaceReadiness
import io.hrns_now.core.domain.model.WorkspaceArtifactSummary

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
    ) : HrnsUiState
}
