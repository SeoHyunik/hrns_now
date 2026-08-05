package io.hrns_now.app.presentation.mapper

import io.hrns_now.app.presentation.model.CockpitActionItem
import io.hrns_now.app.presentation.model.CockpitDiagnostics
import io.hrns_now.app.presentation.model.CockpitProjection
import io.hrns_now.app.presentation.model.StatusChipModel
import io.hrns_now.core.domain.model.ActionContext
import io.hrns_now.core.domain.model.AppLocale
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
        locale: AppLocale = AppLocale.Korean,
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
        val blockedReasonText = recommended.reasonKey?.toDisplayText(locale)

        return CockpitProjection(
            projectName = displayState?.projectName,
            profileLabel = profileLabel,
            dateLabel = daySelection.workspaceDay.date.toString(),
            isReadOnlyDay = daySelection.isReadOnly,
            isStale = isStale,
            phaseLabel = displayState?.phase?.displayLabel(locale) ?: notAvailable(locale),
            statusLabel = displayState?.status?.displayLabel(locale) ?: notAvailable(locale),
            queueStatusLabel = displayState?.queue?.status?.displayLabel(locale) ?: notAvailable(locale),
            activeCardId = displayState?.queue?.active?.cardId,
            activeSliceId = displayState?.queue?.active?.sliceId,
            authorizedTargetLabel = displayState?.authorizedTargetFile,
            stopReasonLabel = displayState?.stopReason?.displayLabel(locale),
            blockedReasonLabel = blockedReasonText,
            artifactItems = displayState?.let { artifactChips(it, locale) } ?: emptyList(),
            opsValidationLabel = displayState?.let { passOrFailLabel(it.opsValidation.passed, locale) } ?: notAvailable(locale),
            closureLabel = displayState?.let { closureLabel(it.closure.validated, locale) } ?: notAvailable(locale),
            executionCompletedLabel = displayState?.let { executionCompletedLabel(it.executionCompleted, locale) } ?: notAvailable(locale),
            lastSuccessfulReadAtLabel = lastSuccessfulReadAtLabel,
            lastAttemptAtLabel = lastAttemptAtLabel,
            primaryAction = recommended.primary?.let { actionItem(it, harnessRunInProgress, locale) },
            allowedActions = recommended.allowed.map { actionItem(it, harnessRunInProgress, locale) },
            diagnostics = diagnosticsFor(stateRead, recommended, locale),
            compatibilityDiagnostics = if (runtimeResolution is RuntimeResolution.Missing || runtimeResolution is RuntimeResolution.Invalid) {
                null
            } else {
                diagnosticsForCompatibility(compatibilityDetail, blockedReasonText, locale)
            },
            runtimeSourceLabel = runtimeSourceLabel(runtimeResolution?.source, locale),
            runtimeSourceDiagnostics = diagnosticsForRuntime(runtimeResolution, blockedReasonText, locale),
        )
    }

    /** `source == null`은 활성 프로젝트가 없어 이 resolver 자체를 거치지 않은 legacy 환경변수 경로다. */
    private fun runtimeSourceLabel(source: RuntimeSource?, locale: AppLocale): String =
        when (locale) {
            AppLocale.Korean -> when (source) {
                null -> "외부 설정(환경변수)"
                RuntimeSource.DefaultKit -> "기본 Harness Kit"
                is RuntimeSource.ExternalKit -> "외부 Harness Kit"
            }
            AppLocale.English -> when (source) {
                null -> "External config (environment variable)"
                RuntimeSource.DefaultKit -> "Default Harness Kit"
                is RuntimeSource.ExternalKit -> "External Harness Kit"
            }
        }

    /**
     * runtime source가 [RuntimeResolution.Resolved]가 아닐 때만 채운다 — compatibility 진단과
     * 근거가 다른 별개의 fail-closed 원인이다(`doc/hrns_now_design_pattern.md` §20.1).
     */
    private fun diagnosticsForRuntime(
        runtimeResolution: RuntimeResolution?,
        ctaGuidance: String?,
        locale: AppLocale,
    ): CockpitDiagnostics? =
        when (runtimeResolution) {
            null, is RuntimeResolution.Resolved -> null
            is RuntimeResolution.Missing -> CockpitDiagnostics(
                whatHappened = when (locale) {
                    AppLocale.Korean -> when (runtimeResolution.source) {
                        RuntimeSource.DefaultKit -> "기본 Harness Kit(.local\\harness-kit)을 찾을 수 없습니다."
                        is RuntimeSource.ExternalKit -> "지정한 외부 Harness Kit 경로를 찾을 수 없습니다."
                    }
                    AppLocale.English -> when (runtimeResolution.source) {
                        RuntimeSource.DefaultKit -> "The default Harness Kit (.local\\harness-kit) couldn't be found."
                        is RuntimeSource.ExternalKit -> "The specified external Harness Kit path couldn't be found."
                    }
                },
                lastKnownGoodPreserved = false,
                nextStep = ctaGuidance ?: when (locale) {
                    AppLocale.Korean -> "SDK를 준비하거나 고급 설정에서 외부 Harness Kit 경로를 확인하세요."
                    AppLocale.English -> "Prepare the SDK, or check the external Harness Kit path in advanced settings."
                },
            )
            is RuntimeResolution.Invalid -> CockpitDiagnostics(
                whatHappened = when (locale) {
                    AppLocale.Korean -> when (runtimeResolution.reason) {
                        RuntimeIssue.NotDirectory -> "runtime 경로가 디렉터리가 아닙니다."
                        RuntimeIssue.NotReadable -> "runtime 경로를 읽을 수 없습니다."
                        RuntimeIssue.MissingEntrypoint -> "필요한 Harness 파일(doctor.ps1/validate-ops.ps1/run-cycle.ps1/kit-version.json)이 없습니다."
                    }
                    AppLocale.English -> when (runtimeResolution.reason) {
                        RuntimeIssue.NotDirectory -> "The runtime path isn't a directory."
                        RuntimeIssue.NotReadable -> "The runtime path couldn't be read."
                        RuntimeIssue.MissingEntrypoint -> "Required Harness files (doctor.ps1/validate-ops.ps1/run-cycle.ps1/kit-version.json) are missing."
                    }
                },
                lastKnownGoodPreserved = false,
                nextStep = ctaGuidance ?: when (locale) {
                    AppLocale.Korean -> "runtime 경로 내용을 확인하거나 고급 설정에서 외부 Harness Kit 경로를 다시 선택하세요."
                    AppLocale.English -> "Check the runtime path contents, or re-select the external Harness Kit path in advanced settings."
                },
            )
        }

    private fun artifactChips(state: WorkflowState, locale: AppLocale): List<StatusChipModel> {
        val labels = when (locale) {
            AppLocale.Korean -> listOf("요청 입력함", "작업 계획 파일", "인수인계 파일", "작업 상태 파일")
            AppLocale.English -> listOf("Request inbox", "Plan file", "Handoff file", "State file")
        }
        val readinessValues = listOf(
            state.artifacts.requestInbox,
            state.artifacts.todayStrategy,
            state.artifacts.dailyHandoff,
            state.artifacts.workflowState,
        )
        return labels.zip(readinessValues).map { (label, readiness) ->
            StatusChipModel(
                label = label,
                value = readiness.displayLabel(locale),
                tone = readiness.tone(),
            )
        }
    }

    private fun ArtifactReadinessState.displayLabel(locale: AppLocale): String =
        when (locale) {
            AppLocale.Korean -> when (this) {
                ArtifactReadinessState.Ready -> "준비됨"
                is ArtifactReadinessState.Unknown -> "확인 필요"
            }
            AppLocale.English -> when (this) {
                ArtifactReadinessState.Ready -> "Ready"
                is ArtifactReadinessState.Unknown -> "Needs review"
            }
        }

    private fun ArtifactReadinessState.tone(): String =
        when (this) {
            ArtifactReadinessState.Ready -> "success"
            is ArtifactReadinessState.Unknown -> "warning"
        }

    private fun diagnosticsFor(stateRead: StateReadResult, recommended: RecommendedActions, locale: AppLocale): CockpitDiagnostics? {
        val ctaGuidance = recommended.reasonKey?.toDisplayText(locale)
        return when (stateRead) {
            is StateReadResult.Success -> null

            // 오늘 날짜의 정상적인 "아직 시작 안 함" 상태다 — BootstrapDay가
            // 열려 있으면 오류/경고 진단 카드가 아니라 그 typed CTA 자체가 다음 행동을 알려준다.
            is StateReadResult.Missing -> if (recommended.primary == UiAction.BootstrapDay) {
                null
            } else {
                CockpitDiagnostics(
                    whatHappened = when (locale) {
                        AppLocale.Korean -> "오늘 날짜의 상태 파일이 아직 없습니다."
                        AppLocale.English -> "Today's state file doesn't exist yet."
                    },
                    lastKnownGoodPreserved = false,
                    nextStep = ctaGuidance ?: when (locale) {
                        AppLocale.Korean -> "작업공간과 날짜를 확인한 뒤 새로고침하세요."
                        AppLocale.English -> "Check the workspace and date, then refresh."
                    },
                )
            }

            is StateReadResult.Malformed -> CockpitDiagnostics(
                whatHappened = when (locale) {
                    AppLocale.Korean -> "상태 파일을 해석할 수 없습니다."
                    AppLocale.English -> "The state file couldn't be parsed."
                },
                lastKnownGoodPreserved = stateRead.lastKnownGood != null,
                nextStep = ctaGuidance ?: retryOrCheckRecoveryText(locale),
            )

            is StateReadResult.EncodingError -> CockpitDiagnostics(
                whatHappened = when (locale) {
                    AppLocale.Korean -> "상태 파일의 인코딩을 해석할 수 없습니다."
                    AppLocale.English -> "The state file's encoding couldn't be parsed."
                },
                lastKnownGoodPreserved = stateRead.lastKnownGood != null,
                nextStep = ctaGuidance ?: retryOrCheckRecoveryText(locale),
            )

            is StateReadResult.UnsupportedSchema -> CockpitDiagnostics(
                whatHappened = when (locale) {
                    AppLocale.Korean -> "지원하지 않는 상태 파일 버전입니다."
                    AppLocale.English -> "Unsupported state file version."
                },
                lastKnownGoodPreserved = false,
                nextStep = ctaGuidance ?: when (locale) {
                    AppLocale.Korean -> "앱을 최신 버전으로 갱신한 뒤 다시 확인하세요."
                    AppLocale.English -> "Update the app to the latest version, then check again."
                },
            )

            is StateReadResult.AccessDenied -> CockpitDiagnostics(
                whatHappened = when (locale) {
                    AppLocale.Korean -> "상태 파일에 접근할 수 없습니다."
                    AppLocale.English -> "The state file couldn't be accessed."
                },
                lastKnownGoodPreserved = false,
                nextStep = ctaGuidance ?: when (locale) {
                    AppLocale.Korean -> "파일 권한을 확인하세요."
                    AppLocale.English -> "Check the file permissions."
                },
            )
        }
    }

    private fun retryOrCheckRecoveryText(locale: AppLocale): String = when (locale) {
        AppLocale.Korean -> "잠시 후 새로고침하세요. 반복되면 복구 센터를 확인하세요."
        AppLocale.English -> "Refresh again shortly. If this keeps happening, check the recovery center."
    }

    /**
     * `Supported`/`SupportedWithUnknownFields`는 실행을 막지 않으므로 `null`이다(기존
     * `diagnostics` 필드처럼 이 필드도 "문제가 있을 때만 채운다" 관례를 따른다). 버전 값은
     * 그대로 표시해도 안전하다 — raw 파일 원문이나 예외 메시지가 아니다.
     */
    private fun diagnosticsForCompatibility(
        detail: HarnessCompatibilityDetail,
        ctaGuidance: String?,
        locale: AppLocale,
    ): CockpitDiagnostics? =
        when (detail) {
            is HarnessCompatibilityDetail.Supported,
            is HarnessCompatibilityDetail.SupportedWithUnknownFields,
            -> null

            is HarnessCompatibilityDetail.UnsupportedMajorVersion -> CockpitDiagnostics(
                whatHappened = when (locale) {
                    AppLocale.Korean -> "지원하지 않는 Harness 계약 버전입니다 " +
                        "(state_schema_version=${detail.manifest.stateSchemaVersion.raw}, " +
                        "ui_contract_version=${detail.manifest.uiContractVersion.raw})."
                    AppLocale.English -> "Unsupported Harness contract version " +
                        "(state_schema_version=${detail.manifest.stateSchemaVersion.raw}, " +
                        "ui_contract_version=${detail.manifest.uiContractVersion.raw})."
                },
                lastKnownGoodPreserved = false,
                nextStep = ctaGuidance ?: when (locale) {
                    AppLocale.Korean -> "앱을 최신 버전으로 갱신하거나 Harness Kit 버전을 확인하세요."
                    AppLocale.English -> "Update the app to the latest version, or check the Harness Kit version."
                },
            )

            HarnessCompatibilityDetail.MissingManifest -> CockpitDiagnostics(
                whatHappened = when (locale) {
                    AppLocale.Korean -> "kit-version.json 파일이 없어 레거시/알 수 없는 Harness 버전으로 처리합니다."
                    AppLocale.English -> "kit-version.json is missing, so this is treated as a legacy/unknown Harness version."
                },
                lastKnownGoodPreserved = false,
                nextStep = ctaGuidance ?: when (locale) {
                    AppLocale.Korean -> "Harness Kit 경로를 확인하세요."
                    AppLocale.English -> "Check the Harness Kit path."
                },
            )

            is HarnessCompatibilityDetail.MalformedManifest -> CockpitDiagnostics(
                whatHappened = when (locale) {
                    AppLocale.Korean -> "kit-version.json 형식을 해석할 수 없습니다 (${detail.reason})."
                    AppLocale.English -> "kit-version.json format couldn't be parsed (${detail.reason})."
                },
                lastKnownGoodPreserved = false,
                nextStep = ctaGuidance ?: when (locale) {
                    AppLocale.Korean -> "Harness Kit의 kit-version.json 파일을 확인하세요."
                    AppLocale.English -> "Check the Harness Kit's kit-version.json file."
                },
            )
        }

    private fun actionItem(action: UiAction, harnessRunInProgress: Boolean, locale: AppLocale): CockpitActionItem =
        CockpitActionItem(
            action = action,
            label = action.displayLabel(locale),
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

    private fun passOrFailLabel(passed: Boolean, locale: AppLocale): String = when (locale) {
        AppLocale.Korean -> if (passed) "통과" else "미통과"
        AppLocale.English -> if (passed) "Passed" else "Not passed"
    }

    private fun closureLabel(validated: Boolean, locale: AppLocale): String = when (locale) {
        AppLocale.Korean -> if (validated) "검증 완료" else "미완료"
        AppLocale.English -> if (validated) "Validated" else "Not complete"
    }

    private fun executionCompletedLabel(completed: Boolean, locale: AppLocale): String = when (locale) {
        AppLocale.Korean -> if (completed) "완료" else "진행 중"
        AppLocale.English -> if (completed) "Completed" else "In progress"
    }

    private fun notAvailable(locale: AppLocale): String = when (locale) {
        AppLocale.Korean -> "확인 불가"
        AppLocale.English -> "Unavailable"
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
