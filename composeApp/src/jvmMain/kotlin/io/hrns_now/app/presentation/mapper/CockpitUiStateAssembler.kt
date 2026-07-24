package io.hrns_now.app.presentation.mapper

import io.hrns_now.app.presentation.buildPlaceholderRunStatusProjection
import io.hrns_now.app.presentation.buildPlaceholderTodayWorkProjection
import io.hrns_now.app.presentation.buildSetupProjection
import io.hrns_now.app.presentation.buildShellProjection
import io.hrns_now.app.presentation.model.HrnsUiState
import io.hrns_now.app.presentation.model.RegistryProjectItem
import io.hrns_now.app.presentation.model.WorkspaceDayItem
import io.hrns_now.core.domain.model.BoundaryStatus
import io.hrns_now.core.domain.model.CompatibilityStatus
import io.hrns_now.core.domain.model.HarnessProject
import io.hrns_now.core.domain.model.ProcessRunStatus
import io.hrns_now.core.domain.model.ProjectId
import io.hrns_now.core.usecase.ActiveProjectSource
import io.hrns_now.core.usecase.CockpitLoadResult
import java.time.LocalDate

/** Domain/query 결과를 Phase 1C/1D의 단일 immutable UI state로 변환한다. */
class CockpitUiStateAssembler(
    private val cockpitAssembler: CockpitProjectionAssembler = CockpitProjectionAssembler(),
) {
    fun assemble(
        loaded: CockpitLoadResult,
        lastSuccessfulReadAtLabel: String?,
        lastAttemptAtLabel: String?,
        registryProjects: List<HarnessProject>,
        availableDates: List<LocalDate>,
        activeProjectId: ProjectId?,
        activeProjectSource: ActiveProjectSource,
        registryMessage: String?,
        boundaryStatus: BoundaryStatus,
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
                boundary = boundaryStatus,
                process = ProcessRunStatus.Idle,
                lastSuccessfulReadAtLabel = lastSuccessfulReadAtLabel,
                lastAttemptAtLabel = lastAttemptAtLabel,
            ),
            todayWork = buildPlaceholderTodayWorkProjection(),
            runStatus = buildPlaceholderRunStatusProjection(),
            registryProjects = registryProjects.map { project ->
                RegistryProjectItem(
                    id = project.id,
                    label = project.displayName,
                    isActive = project.id == activeProjectId,
                )
            },
            workspaceDays = availableDates.map { date ->
                WorkspaceDayItem(
                    date = date,
                    isSelected = date == loaded.daySelection.workspaceDay.date,
                )
            },
            activeProjectSourceLabel = activeProjectSource.displayLabel(),
            registryMessage = registryMessage,
        )
}
