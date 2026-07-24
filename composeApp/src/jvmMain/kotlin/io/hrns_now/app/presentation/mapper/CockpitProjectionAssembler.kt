package io.hrns_now.app.presentation.mapper

import io.hrns_now.app.presentation.model.CockpitActionItem
import io.hrns_now.app.presentation.model.CockpitDiagnostics
import io.hrns_now.app.presentation.model.CockpitProjection
import io.hrns_now.app.presentation.model.StatusChipModel
import io.hrns_now.core.domain.model.ActionContext
import io.hrns_now.core.domain.model.ArtifactReadinessState
import io.hrns_now.core.domain.model.ArtifactsState
import io.hrns_now.core.domain.model.BoundaryStatus
import io.hrns_now.core.domain.model.CompatibilityStatus
import io.hrns_now.core.domain.model.ProcessRunStatus
import io.hrns_now.core.domain.model.SelectedDayKind
import io.hrns_now.core.domain.model.UiAction
import io.hrns_now.core.domain.model.WorkflowState
import io.hrns_now.core.domain.policy.ActionPolicy
import io.hrns_now.core.domain.policy.WorkspaceDaySelection
import io.hrns_now.core.result.StateReadResult

/**
 * [StateReadResult]와 policy 입력을 [CockpitProjection]으로 조립하는 presentation mapper다
 * (`doc/hrns_now_design_pattern.md` §9 `WorkflowState → CockpitProjectionAssembler →
 * CockpitProjection → Compose UI`).
 *
 * 이 클래스는 `AppViewModel`에서 호출되는 순수 조립부다 — 파일 I/O·coroutine을 직접 다루지
 * 않고, [ActionPolicy]가 이미 계산한 fail-closed 결과를 표현만 한다.
 */
class CockpitProjectionAssembler(
    private val actionPolicy: ActionPolicy = ActionPolicy(),
) {
    fun assemble(
        projectConnected: Boolean,
        profileLabel: String,
        daySelection: WorkspaceDaySelection,
        stateRead: StateReadResult,
        compatibility: CompatibilityStatus,
        boundary: BoundaryStatus,
        process: ProcessRunStatus,
        lastSuccessfulReadAtLabel: String?,
        lastAttemptAtLabel: String?,
    ): CockpitProjection {
        val (displayState, isStale) = resolveDisplayState(stateRead)
        // 최종 계획 부록 A와 live State는 queue.active를 card/slice pointer로만 보증한다.
        // Template의 미사용 필드를 실행 근거로 승격하지 않고 Phase 2 계약 전까지 fail-closed한다.
        val activeSliceKind = null
        val selectedDayKind = if (daySelection.isReadOnly) SelectedDayKind.Past else SelectedDayKind.Today

        val context = ActionContext(
            projectConnected = projectConnected,
            selectedDayKind = if (projectConnected) selectedDayKind else null,
            stateRead = stateRead,
            compatibility = compatibility,
            boundary = boundary,
            process = process,
            activeSliceKind = activeSliceKind,
        )
        val recommended = actionPolicy.recommend(context)

        return CockpitProjection(
            projectName = displayState?.projectName,
            profileLabel = profileLabel,
            dateLabel = daySelection.workspaceDay.date.toString(),
            isReadOnlyDay = daySelection.isReadOnly,
            isStale = isStale,
            phaseLabel = displayState?.phase?.displayLabel() ?: NOT_AVAILABLE,
            statusLabel = displayState?.status?.displayLabel() ?: NOT_AVAILABLE,
            queueStatusLabel = displayState?.queue?.status?.displayLabel() ?: NOT_AVAILABLE,
            activeCardId = displayState?.queue?.active?.cardId,
            activeSliceId = displayState?.queue?.active?.sliceId,
            authorizedTargetLabel = displayState?.authorizedTargetFile,
            stopReasonLabel = displayState?.stopReason?.displayLabel(),
            blockedReasonLabel = recommended.blockedReason,
            artifactItems = displayState?.let(::artifactChips) ?: emptyList(),
            opsValidationLabel = displayState?.let { if (it.opsValidation.passed) "통과" else "미통과" } ?: NOT_AVAILABLE,
            closureLabel = displayState?.let { if (it.closure.validated) "검증 완료" else "미완료" } ?: NOT_AVAILABLE,
            executionCompletedLabel = displayState?.let { if (it.executionCompleted) "완료" else "진행 중" } ?: NOT_AVAILABLE,
            lastSuccessfulReadAtLabel = lastSuccessfulReadAtLabel,
            lastAttemptAtLabel = lastAttemptAtLabel,
            primaryAction = recommended.primary?.let(::actionItem),
            allowedActions = recommended.allowed.map(::actionItem),
            diagnostics = diagnosticsFor(stateRead, recommended.blockedReason),
        )
    }

    private fun resolveDisplayState(stateRead: StateReadResult): Pair<WorkflowState?, Boolean> =
        when (stateRead) {
            is StateReadResult.Success -> stateRead.state to false
            is StateReadResult.Malformed -> stateRead.lastKnownGood to (stateRead.lastKnownGood != null)
            is StateReadResult.EncodingError -> stateRead.lastKnownGood to (stateRead.lastKnownGood != null)
            is StateReadResult.Missing,
            is StateReadResult.UnsupportedSchema,
            is StateReadResult.AccessDenied,
            -> null to false
        }

    private fun artifactChips(state: WorkflowState): List<StatusChipModel> =
        listOf(
            "요청 입력함" to state.artifacts.requestInbox,
            "오늘 할 일 파일" to state.artifacts.todayStrategy,
            "인수인계 파일" to state.artifacts.dailyHandoff,
            "작업 상태 파일" to state.artifacts.workflowState,
        ).map { (label, readiness) ->
            StatusChipModel(
                label = label,
                value = readiness.displayLabel(),
                tone = readiness.tone(),
            )
        }

    private fun ArtifactReadinessState.displayLabel(): String =
        when (this) {
            ArtifactReadinessState.Ready -> "준비됨"
            is ArtifactReadinessState.Unknown -> "확인 필요"
        }

    private fun ArtifactReadinessState.tone(): String =
        when (this) {
            ArtifactReadinessState.Ready -> "success"
            is ArtifactReadinessState.Unknown -> "warning"
        }

    private fun diagnosticsFor(stateRead: StateReadResult, ctaGuidance: String?): CockpitDiagnostics? =
        when (stateRead) {
            is StateReadResult.Success -> null

            is StateReadResult.Missing -> CockpitDiagnostics(
                whatHappened = "오늘 날짜의 상태 파일이 아직 없습니다.",
                lastKnownGoodPreserved = false,
                nextStep = ctaGuidance ?: "작업공간과 날짜를 확인한 뒤 새로고침하세요.",
            )

            is StateReadResult.Malformed -> CockpitDiagnostics(
                whatHappened = "상태 파일을 해석할 수 없습니다.",
                lastKnownGoodPreserved = stateRead.lastKnownGood != null,
                nextStep = ctaGuidance ?: "잠시 후 새로고침하세요. 반복되면 복구 센터를 확인하세요.",
            )

            is StateReadResult.EncodingError -> CockpitDiagnostics(
                whatHappened = "상태 파일의 인코딩을 해석할 수 없습니다.",
                lastKnownGoodPreserved = stateRead.lastKnownGood != null,
                nextStep = ctaGuidance ?: "잠시 후 새로고침하세요. 반복되면 복구 센터를 확인하세요.",
            )

            is StateReadResult.UnsupportedSchema -> CockpitDiagnostics(
                whatHappened = "지원하지 않는 상태 파일 버전입니다.",
                lastKnownGoodPreserved = false,
                nextStep = ctaGuidance ?: "앱을 최신 버전으로 갱신한 뒤 다시 확인하세요.",
            )

            is StateReadResult.AccessDenied -> CockpitDiagnostics(
                whatHappened = "상태 파일에 접근할 수 없습니다.",
                lastKnownGoodPreserved = false,
                nextStep = ctaGuidance ?: "파일 권한을 확인하세요.",
            )
        }

    private fun actionItem(action: UiAction): CockpitActionItem =
        CockpitActionItem(
            action = action,
            label = action.displayLabel(),
            enabled = action == UiAction.Refresh,
        )
    private companion object {
        const val NOT_AVAILABLE = "확인 불가"
    }
}
