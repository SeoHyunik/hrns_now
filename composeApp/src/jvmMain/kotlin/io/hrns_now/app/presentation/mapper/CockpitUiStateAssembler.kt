package io.hrns_now.app.presentation.mapper

import io.hrns_now.app.presentation.buildPlaceholderRunStatusProjection
import io.hrns_now.app.presentation.buildPlaceholderTodayWorkProjection
import io.hrns_now.app.presentation.buildSetupProjection
import io.hrns_now.app.presentation.buildShellProjection
import io.hrns_now.app.presentation.model.HrnsUiState
import io.hrns_now.core.domain.model.BoundaryStatus
import io.hrns_now.core.domain.model.CompatibilityStatus
import io.hrns_now.core.domain.model.ProcessRunStatus
import io.hrns_now.core.usecase.CockpitLoadResult

/** Domain/query 결과를 Phase 1C의 단일 immutable UI state로 변환한다. */
class CockpitUiStateAssembler(
    private val cockpitAssembler: CockpitProjectionAssembler = CockpitProjectionAssembler(),
) {
    fun assemble(
        loaded: CockpitLoadResult,
        lastSuccessfulReadAtLabel: String?,
        lastAttemptAtLabel: String?,
    ): HrnsUiState.Ready =
        HrnsUiState.Ready(
            shell = buildShellProjection(),
            setup = buildSetupProjection(loaded.workspaceConfig, loaded.workspaceProbeSummary),
            workspaceConfig = loaded.workspaceConfig,
            workspaceProbeSummary = loaded.workspaceProbeSummary,
            workspaceReadiness = loaded.workspaceReadiness,
            workspaceArtifactSummary = loaded.workspaceArtifactSummary,
            cockpit = cockpitAssembler.assemble(
                projectConnected = loaded.projectConnected,
                profileLabel = loaded.workspaceConfig.profileName,
                daySelection = loaded.daySelection,
                stateRead = loaded.stateRead,
                compatibility = CompatibilityStatus.Unknown,
                boundary = BoundaryStatus.Unknown,
                process = ProcessRunStatus.Idle,
                lastSuccessfulReadAtLabel = lastSuccessfulReadAtLabel,
                lastAttemptAtLabel = lastAttemptAtLabel,
            ),
            todayWork = buildPlaceholderTodayWorkProjection(),
            runStatus = buildPlaceholderRunStatusProjection(),
        )
}
