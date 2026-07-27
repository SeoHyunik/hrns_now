package io.hrns_now.app.presentation.mapper

import io.hrns_now.app.presentation.buildRecoveryProjection
import io.hrns_now.app.presentation.buildSetupProjection
import io.hrns_now.app.presentation.buildShellProjection
import io.hrns_now.app.presentation.buildTodayWorkProjection
import io.hrns_now.app.presentation.model.CockpitActionItem
import io.hrns_now.app.presentation.model.CockpitProjection
import io.hrns_now.app.presentation.model.HrnsUiState
import io.hrns_now.app.presentation.model.RegistryProjectItem
import io.hrns_now.app.presentation.model.RunStatusProjection
import io.hrns_now.app.presentation.model.WorkspaceDayItem
import io.hrns_now.core.domain.model.BoundaryStatus
import io.hrns_now.core.domain.model.HarnessCompatibilityDetail
import io.hrns_now.core.domain.model.HarnessProject
import io.hrns_now.core.domain.model.ProcessRunStatus
import io.hrns_now.core.domain.model.ProjectId
import io.hrns_now.core.domain.model.RecoveryDiagnostics
import io.hrns_now.core.domain.model.UiAction
import io.hrns_now.core.domain.policy.ClosureDecision
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
        compatibilityDetail: HarnessCompatibilityDetail,
        processRunStatus: ProcessRunStatus,
        runStatus: RunStatusProjection,
        harnessRunInProgress: Boolean,
        strategyText: String?,
        requestInboxNotice: String?,
        requestSaving: Boolean,
        requestSaveSucceeded: Boolean,
        lockSummaryLabel: String,
        closureDecision: ClosureDecision,
        recoveryDiagnostics: RecoveryDiagnostics,
    ): HrnsUiState.Ready {
        val actionPolicyCockpit = cockpitAssembler.assemble(
            projectConnected = loaded.projectConnected,
            profileLabel = loaded.workspaceConfig.profileName,
            daySelection = loaded.daySelection,
            stateRead = loaded.stateRead,
            compatibilityDetail = compatibilityDetail,
            boundary = boundaryStatus,
            process = processRunStatus,
            lastSuccessfulReadAtLabel = lastSuccessfulReadAtLabel,
            lastAttemptAtLabel = lastAttemptAtLabel,
            harnessRunInProgress = harnessRunInProgress,
        )
        val closureValidationEnabledByActionPolicy = actionPolicyCockpit.allowedActions
            .firstOrNull { it.action == UiAction.RunClosureValidation }
            ?.enabled == true
        val cockpit = actionPolicyCockpit.gatedByClosureDecision(closureDecision)
        return HrnsUiState.Ready(
            shell = buildShellProjection(),
            setup = buildSetupProjection(
                config = loaded.workspaceConfig,
                probeSummary = loaded.workspaceProbeSummary,
                diagnosticActions = cockpit.allowedActions,
            ),
            workspaceConfig = loaded.workspaceConfig,
            workspaceProbeSummary = loaded.workspaceProbeSummary,
            workspaceReadiness = loaded.workspaceReadiness,
            workspaceArtifactSummary = loaded.workspaceArtifactSummary,
            cockpit = cockpit,
            todayWork = buildTodayWorkProjection(
                cockpit,
                strategyText,
                requestInboxNotice,
                requestSaving,
                requestSaveSucceeded,
                lockSummaryLabel,
            ),
            runStatus = runStatus,
            recovery = buildRecoveryProjection(
                cockpit,
                loaded.stateRead,
                closureDecision,
                lockSummaryLabel,
                closureValidationEnabledAfterAcknowledgement = closureValidationEnabledByActionPolicy,
                recoveryDiagnostics = recoveryDiagnostics,
            ),
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
}

/**
 * 실행 process 성공(=`ActionPolicy`가 이미 허용한 CTA)과 하루 마감 허용을 분리한다
 * (`doc/claude_prompts/phase5-closure-recovery.md` §1). `ClosureDecision.Blocked`일 때만
 * "마감 검증 실행"을 추가로 비활성화한다 — `RequiresExplicitIncompleteHandoff`는 화면에서
 * 명시적 인지를 요구할 뿐 버튼 자체를 잠그지 않는다.
 */
private fun CockpitProjection.gatedByClosureDecision(closureDecision: ClosureDecision): CockpitProjection {
    if (closureDecision is ClosureDecision.Allowed) return this
    fun CockpitActionItem.gate() = if (action == UiAction.RunClosureValidation) copy(enabled = false) else this
    return copy(
        primaryAction = primaryAction?.gate(),
        allowedActions = allowedActions.map { it.gate() },
    )
}
