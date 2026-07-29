package io.hrns_now.app.presentation.mapper

import io.hrns_now.app.presentation.model.CockpitActionItem
import io.hrns_now.app.presentation.model.CockpitDiagnostics
import io.hrns_now.app.presentation.model.CockpitProjection
import io.hrns_now.app.presentation.model.StatusChipModel
import io.hrns_now.core.domain.model.ActionContext
import io.hrns_now.core.domain.model.ArtifactReadinessState
import io.hrns_now.core.domain.model.ArtifactsState
import io.hrns_now.core.domain.model.BoundaryStatus
import io.hrns_now.core.domain.model.HarnessCompatibilityDetail
import io.hrns_now.core.domain.model.ProcessRunStatus
import io.hrns_now.core.domain.model.RecommendedActions
import io.hrns_now.core.domain.model.RuntimeIssue
import io.hrns_now.core.domain.model.RuntimeResolution
import io.hrns_now.core.domain.model.RuntimeSource
import io.hrns_now.core.domain.model.SelectedDayKind
import io.hrns_now.core.domain.model.UiAction
import io.hrns_now.core.domain.model.WorkflowState
import io.hrns_now.core.domain.model.toActiveSliceKind
import io.hrns_now.core.domain.model.toCompatibilityStatus
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
        compatibilityDetail: HarnessCompatibilityDetail,
        boundary: BoundaryStatus,
        process: ProcessRunStatus,
        lastSuccessfulReadAtLabel: String?,
        lastAttemptAtLabel: String?,
        harnessRunInProgress: Boolean = false,
        runtimeResolution: RuntimeResolution? = null,
    ): CockpitProjection {
        val (displayState, isStale) = resolveDisplayWorkflowState(stateRead)
        val activeSliceKind = displayState?.executionWrapper?.toActiveSliceKind()
        val selectedDayKind = if (daySelection.isReadOnly) SelectedDayKind.Past else SelectedDayKind.Today

        val context = ActionContext(
            projectConnected = projectConnected,
            selectedDayKind = if (projectConnected) selectedDayKind else null,
            stateRead = stateRead,
            compatibility = compatibilityDetail.toCompatibilityStatus(),
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
            primaryAction = recommended.primary?.let { actionItem(it, harnessRunInProgress) },
            allowedActions = recommended.allowed.map { actionItem(it, harnessRunInProgress) },
            diagnostics = diagnosticsFor(stateRead, recommended),
            compatibilityDiagnostics = if (runtimeResolution is RuntimeResolution.Missing || runtimeResolution is RuntimeResolution.Invalid) {
                null
            } else {
                diagnosticsForCompatibility(compatibilityDetail, recommended.blockedReason)
            },
            runtimeSourceLabel = runtimeSourceLabel(runtimeResolution?.source),
            runtimeSourceDiagnostics = diagnosticsForRuntime(runtimeResolution, recommended.blockedReason),
        )
    }

    /** `source == null`은 활성 프로젝트가 없어 이 resolver 자체를 거치지 않은 legacy 환경변수 경로다. */
    private fun runtimeSourceLabel(source: RuntimeSource?): String =
        when (source) {
            null -> "외부 설정(환경변수)"
            RuntimeSource.InternalDeveloperSdk -> "개발용 내장 SDK"
            is RuntimeSource.ExternalKit -> "외부 Harness Kit"
        }

    /**
     * runtime source가 [RuntimeResolution.Resolved]가 아닐 때만 채운다 — compatibility 진단과
     * 근거가 다른 별개의 fail-closed 원인이다(새 Phase 7, `doc/hrns_now_design_pattern.md` §20.1).
     */
    private fun diagnosticsForRuntime(runtimeResolution: RuntimeResolution?, ctaGuidance: String?): CockpitDiagnostics? =
        when (runtimeResolution) {
            null, is RuntimeResolution.Resolved -> null
            is RuntimeResolution.Missing -> CockpitDiagnostics(
                whatHappened = when (runtimeResolution.source) {
                    RuntimeSource.InternalDeveloperSdk -> "개발용 내장 SDK(.local\\harness-kit)를 찾을 수 없습니다."
                    is RuntimeSource.ExternalKit -> "지정한 외부 Harness Kit 경로를 찾을 수 없습니다."
                },
                lastKnownGoodPreserved = false,
                nextStep = ctaGuidance ?: "SDK를 준비하거나 고급 설정에서 외부 Harness Kit 경로를 확인하세요.",
            )
            is RuntimeResolution.Invalid -> CockpitDiagnostics(
                whatHappened = when (runtimeResolution.reason) {
                    RuntimeIssue.NotDirectory -> "runtime 경로가 디렉터리가 아닙니다."
                    RuntimeIssue.NotReadable -> "runtime 경로를 읽을 수 없습니다."
                    RuntimeIssue.MissingEntrypoint -> "필요한 Harness 파일(doctor.ps1/validate-ops.ps1/run-cycle.ps1/kit-version.json)이 없습니다."
                },
                lastKnownGoodPreserved = false,
                nextStep = ctaGuidance ?: "runtime 경로 내용을 확인하거나 고급 설정에서 외부 Harness Kit 경로를 다시 선택하세요.",
            )
        }

    private fun artifactChips(state: WorkflowState): List<StatusChipModel> =
        listOf(
            "요청 입력함" to state.artifacts.requestInbox,
            "작업 계획 파일" to state.artifacts.todayStrategy,
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

    private fun diagnosticsFor(stateRead: StateReadResult, recommended: RecommendedActions): CockpitDiagnostics? {
        val ctaGuidance = recommended.blockedReason
        return when (stateRead) {
            is StateReadResult.Success -> null

            // 오늘 날짜의 정상적인 "아직 시작 안 함" 상태다(새 Phase 8 §2/§3) — BootstrapDay가
            // 열려 있으면 오류/경고 진단 카드가 아니라 그 typed CTA 자체가 다음 행동을 알려준다.
            is StateReadResult.Missing -> if (recommended.primary == UiAction.BootstrapDay) {
                null
            } else {
                CockpitDiagnostics(
                    whatHappened = "오늘 날짜의 상태 파일이 아직 없습니다.",
                    lastKnownGoodPreserved = false,
                    nextStep = ctaGuidance ?: "작업공간과 날짜를 확인한 뒤 새로고침하세요.",
                )
            }

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
    }

    /**
     * `Supported`/`SupportedWithUnknownFields`는 실행을 막지 않으므로 `null`이다(기존
     * `diagnostics` 필드처럼 이 필드도 "문제가 있을 때만 채운다" 관례를 따른다). 버전 값은
     * 그대로 표시해도 안전하다 — raw 파일 원문이나 예외 메시지가 아니다.
     */
    private fun diagnosticsForCompatibility(
        detail: HarnessCompatibilityDetail,
        ctaGuidance: String?,
    ): CockpitDiagnostics? =
        when (detail) {
            is HarnessCompatibilityDetail.Supported,
            is HarnessCompatibilityDetail.SupportedWithUnknownFields,
            -> null

            is HarnessCompatibilityDetail.UnsupportedMajorVersion -> CockpitDiagnostics(
                whatHappened = "지원하지 않는 Harness 계약 버전입니다 " +
                    "(state_schema_version=${detail.manifest.stateSchemaVersion.raw}, " +
                    "ui_contract_version=${detail.manifest.uiContractVersion.raw}).",
                lastKnownGoodPreserved = false,
                nextStep = ctaGuidance ?: "앱을 최신 버전으로 갱신하거나 Harness Kit 버전을 확인하세요.",
            )

            HarnessCompatibilityDetail.MissingManifest -> CockpitDiagnostics(
                whatHappened = "kit-version.json 파일이 없어 레거시/알 수 없는 Harness 버전으로 처리합니다.",
                lastKnownGoodPreserved = false,
                nextStep = ctaGuidance ?: "Harness Kit 경로를 확인하세요.",
            )

            is HarnessCompatibilityDetail.MalformedManifest -> CockpitDiagnostics(
                whatHappened = "kit-version.json 형식을 해석할 수 없습니다 (${detail.reason}).",
                lastKnownGoodPreserved = false,
                nextStep = ctaGuidance ?: "Harness Kit의 kit-version.json 파일을 확인하세요.",
            )
        }

    private fun actionItem(action: UiAction, harnessRunInProgress: Boolean): CockpitActionItem =
        CockpitActionItem(
            action = action,
            label = action.displayLabel(),
            enabled = when (action) {
                UiAction.Refresh, UiAction.EditRequest -> true
                UiAction.RunDoctor,
                UiAction.RunOpsValidation,
                UiAction.BootstrapDay,
                UiAction.RunPlanning,
                UiAction.RunReplan,
                UiAction.RunCodeSlice,
                UiAction.RunDocSlice,
                UiAction.RunClosureValidation,
                -> !harnessRunInProgress
                else -> false
            },
        )
    private companion object {
        const val NOT_AVAILABLE = "확인 불가"
    }
}

/**
 * `Success`가 아닌 [StateReadResult]에서 last-known-good을 골라 표시할 [WorkflowState]와
 * stale 여부를 만든다. [CockpitProjectionAssembler]와 Recovery projection이 공유한다.
 */
internal fun resolveDisplayWorkflowState(stateRead: StateReadResult): Pair<WorkflowState?, Boolean> =
    when (stateRead) {
        is StateReadResult.Success -> stateRead.state to false
        is StateReadResult.Malformed -> stateRead.lastKnownGood to (stateRead.lastKnownGood != null)
        is StateReadResult.EncodingError -> stateRead.lastKnownGood to (stateRead.lastKnownGood != null)
        is StateReadResult.Missing,
        is StateReadResult.UnsupportedSchema,
        is StateReadResult.AccessDenied,
        -> null to false
    }
