package io.hrns_now.app.presentation

import io.hrns_now.app.presentation.model.ActionButtonModel
import io.hrns_now.app.presentation.model.CockpitActionItem
import io.hrns_now.app.presentation.model.CockpitProjection
import io.hrns_now.app.presentation.model.DevelopmentStrategyCardModel
import io.hrns_now.app.presentation.model.InfoCardModel
import io.hrns_now.app.presentation.model.SetupProjection
import io.hrns_now.app.presentation.model.ShellProjection
import io.hrns_now.app.presentation.model.SourceFreshnessItem
import io.hrns_now.app.presentation.model.StatusChipModel
import io.hrns_now.app.presentation.model.TodayWorkProjection
import io.hrns_now.core.config.PathProbeState
import io.hrns_now.core.config.WorkspaceConfig
import io.hrns_now.core.config.WorkspaceProbeSummary
import io.hrns_now.core.domain.model.AppLocale
import io.hrns_now.core.domain.model.UiAction

/**
 * production 기본 경로에서 쓰는, mock이 아닌 정적/실데이터 기반 projection 조립부다.
 *
 * `MockProjectionProvider`는 명시적 demo mode 전용이므로(`doc/claude_prompts/phase1c-live-cockpit.md`),
 * production은 이 파일의 함수로 shell/setup 화면을 구성한다. 아직 실행 연결이 없는 화면
 * (작업 계획/실행 기록)은 성공한 것처럼 꾸미지 않고 "미구현" 상태를 그대로 보여준다. 새 Phase 8
 * 보완: 화면에서 선택된 [AppLocale]로 투영한다 — 기본값 Korean은 기존 호출부/테스트가 그대로
 * 컴파일되도록 남긴다.
 */
fun buildShellProjection(locale: AppLocale = AppLocale.Korean): ShellProjection =
    when (locale) {
        AppLocale.Korean -> ShellProjection(
            title = "HRNS-NOW",
            subtitle = "파일 우선 · 읽기 전용 투영 셸",
            statusChips = emptyList(),
            sourceItems = emptyList(),
            notAppOwnedMessages = listOf(
                "UI는 WORKFLOW_STATE.json을 직접 쓰지 않습니다.",
                "UI는 마감 상태를 자체 확정하지 않습니다.",
                "현재 화면은 파일 기반 읽기 투영 셸입니다.",
            ),
        )
        AppLocale.English -> ShellProjection(
            title = "HRNS-NOW",
            subtitle = "File-first · read-only projection shell",
            statusChips = emptyList(),
            sourceItems = emptyList(),
            notAppOwnedMessages = listOf(
                "The UI never writes WORKFLOW_STATE.json directly.",
                "The UI never decides closure status on its own.",
                "This screen is a file-based, read-only projection shell.",
            ),
        )
    }

fun buildSetupProjection(
    config: WorkspaceConfig,
    probeSummary: WorkspaceProbeSummary,
    diagnosticActions: List<CockpitActionItem> = emptyList(),
    locale: AppLocale = AppLocale.Korean,
): SetupProjection {
    val rows = listOf(
        probeSummary.kitRoot,
        probeSummary.workspaceRoot,
        probeSummary.projectRoot,
        probeSummary.powerShellPath,
        probeSummary.claudeCommand,
    )
    return when (locale) {
        AppLocale.Korean -> SetupProjection(
            title = "프로젝트 관리",
            subtitle = "프로젝트와 harness-kit 실행 환경을 안전하게 연결합니다.",
            cards = listOf(
                InfoCardModel("경로 상태", rows.map { it.label to it.state.summaryLabel(locale) }),
                InfoCardModel("실행 프로필", listOf("프로필" to config.profileName)),
            ),
            actions = listOf(
                diagnosticAction(
                    label = "연결 점검",
                    description = "Harness Kit과 프로젝트 연결이 실행 가능한지 확인합니다.",
                    action = UiAction.RunDoctor,
                    diagnosticActions = diagnosticActions,
                    locale = locale,
                ),
                diagnosticAction(
                    label = "작업 준비 점검",
                    description = "오늘 작업을 시작하기 위한 상태와 기준 파일을 확인합니다.",
                    action = UiAction.RunOpsValidation,
                    diagnosticActions = diagnosticActions,
                    locale = locale,
                ),
            ),
            note = "연결 점검과 작업 준비 점검은 선택한 프로젝트·날짜 및 안전 조건을 충족할 때만 실행됩니다.",
        )
        AppLocale.English -> SetupProjection(
            title = "Project setup",
            subtitle = "Safely connect a project and the harness-kit run environment.",
            cards = listOf(
                InfoCardModel("Path status", rows.map { it.label to it.state.summaryLabel(locale) }),
                InfoCardModel("Run profile", listOf("Profile" to config.profileName)),
            ),
            actions = listOf(
                diagnosticAction(
                    label = "Check connection",
                    description = "Confirm the Harness Kit and project connection can run.",
                    action = UiAction.RunDoctor,
                    diagnosticActions = diagnosticActions,
                    locale = locale,
                ),
                diagnosticAction(
                    label = "Check readiness",
                    description = "Confirm the state and required files needed to start today's work.",
                    action = UiAction.RunOpsValidation,
                    diagnosticActions = diagnosticActions,
                    locale = locale,
                ),
            ),
            note = "Check connection and check readiness only run once the selected project, date, and safety conditions are met.",
        )
    }
}

private fun diagnosticAction(
    label: String,
    description: String,
    action: UiAction,
    diagnosticActions: List<CockpitActionItem>,
    locale: AppLocale,
): ActionButtonModel {
    val actionItem = diagnosticActions.firstOrNull { it.action == action }
    return ActionButtonModel(
        label = label,
        enabled = actionItem?.enabled == true,
        helperText = if (actionItem == null) {
            when (locale) {
                AppLocale.Korean -> "현재 상태에서는 실행할 수 없습니다."
                AppLocale.English -> "Can't run in the current state."
            }
        } else {
            null
        },
        action = action,
        description = description,
    )
}

private fun PathProbeState.summaryLabel(locale: AppLocale): String =
    when (locale) {
        AppLocale.Korean -> when (this) {
            PathProbeState.NotConfigured -> "미설정"
            PathProbeState.Exists -> "확인됨"
            PathProbeState.Missing -> "없음"
            PathProbeState.NotReadable -> "읽기 불가"
            PathProbeState.WrongType -> "유형 불일치"
            PathProbeState.Unknown -> "확인 필요"
        }
        AppLocale.English -> when (this) {
            PathProbeState.NotConfigured -> "Not configured"
            PathProbeState.Exists -> "Confirmed"
            PathProbeState.Missing -> "Missing"
            PathProbeState.NotReadable -> "Not readable"
            PathProbeState.WrongType -> "Wrong type"
            PathProbeState.Unknown -> "Needs review"
        }
    }

/**
 * "작업 계획" 화면 projection이다. 사람용 `TODAY_STRATEGY.md` 원문과 기계
 * `WORKFLOW_STATE.json` queue 판단을 서로 다른 섹션으로 분리해 보여준다 — 둘이 어긋나면
 * `WORKFLOW_STATE.json`(= [cockpit])이 최종 진실이다(`doc/claude_prompts/phase4-standard-daily-flow.md` §3).
 * action 목록은 [cockpit]이 이미 계산한 allowed action 중 일일 실행 흐름에 속하는 것만 남긴다 —
 * 연결 점검/작업 준비 점검은 프로젝트 관리 화면, Closure류는 Phase 5 범위라 여기서 다루지 않는다.
 */
fun buildTodayWorkProjection(
    cockpit: CockpitProjection,
    strategyText: String?,
    requestInboxNotice: String?,
    requestSaving: Boolean,
    requestSaveSucceeded: Boolean = false,
    lockSummaryLabel: String,
    locale: AppLocale = AppLocale.Korean,
): TodayWorkProjection {
    val dailyFlowActions = setOf(
        UiAction.BootstrapDay,
        UiAction.EditRequest,
        UiAction.RunPlanning,
        UiAction.RunReplan,
        UiAction.ReviewPlan,
        UiAction.RunCodeSlice,
        UiAction.RunDocSlice,
        UiAction.RunValidationSlice,
    )
    // 새 Phase 8 보완 §2.1: 실제 BootstrapDay 실행 CTA는 화면당 한 곳(요구사항 카드)에만 둔다 —
    // 아래 일반 action 목록에서 제외하고 별도 필드로 분리해 렌더링을 강제한다.
    val bootstrapItem = cockpit.primaryAction?.takeIf { it.action == UiAction.BootstrapDay }
    val bootstrapEligible = bootstrapItem != null
    val bootstrapAction = bootstrapItem?.let { item -> ActionButtonModel(label = item.label, enabled = item.enabled, action = item.action) }

    val actions = (listOfNotNull(cockpit.primaryAction) + cockpit.allowedActions)
        .distinctBy { it.action }
        .filter { it.action in dailyFlowActions && it.action != UiAction.BootstrapDay }
        .map { item ->
            ActionButtonModel(
                label = item.label,
                enabled = item.enabled,
                helperText = if (!item.enabled) cockpit.blockedReasonLabel else null,
                action = item.action,
            )
        }

    val notAvailable = when (locale) {
        AppLocale.Korean -> "확인 필요"
        AppLocale.English -> "Needs review"
    }
    val wrapperLabel = when {
        actions.any { it.action == UiAction.RunCodeSlice } -> "code"
        actions.any { it.action == UiAction.RunDocSlice } -> "doc"
        else -> notAvailable
    }
    val compatibilityLabel = cockpit.compatibilityDiagnostics?.whatHappened ?: when (locale) {
        AppLocale.Korean -> "호환됨"
        AppLocale.English -> "Compatible"
    }

    return when (locale) {
        AppLocale.Korean -> TodayWorkProjection(
            title = "작업 계획",
            subtitle = "요구사항 작성 → 작업 준비 → 계획/재계획 → 승인된 단일 code/doc 실행",
            statusChip = StatusChipModel(
                label = cockpit.queueStatusLabel,
                value = cockpit.activeSliceId ?: "",
                tone = if (cockpit.blockedReasonLabel != null) "warning" else "neutral",
            ),
            developmentStrategy = DevelopmentStrategyCardModel(
                text = strategyText,
                dateLabel = cockpit.dateLabel,
                isReadOnlyDay = cockpit.isReadOnlyDay,
            ),
            sections = listOf(
                InfoCardModel(
                    "작업 대기열",
                    listOf(
                        "상태" to cockpit.statusLabel,
                        "queue 상태" to cockpit.queueStatusLabel,
                        "활성 card" to (cockpit.activeCardId ?: "없음"),
                        "활성 slice" to (cockpit.activeSliceId ?: "없음"),
                        "승인된 대상 파일" to (cockpit.authorizedTargetLabel ?: "없음"),
                        "중단 사유" to (cockpit.stopReasonLabel ?: "없음"),
                    ),
                ),
                InfoCardModel(
                    "실행 확인 (code/doc 실행 전 필수 확인, read-only)",
                    listOf(
                        "wrapper" to wrapperLabel,
                        "승인된 대상 파일" to (cockpit.authorizedTargetLabel ?: "없음"),
                        "허용 범위" to "승인된 대상 파일 안에서만 변경이 허용됩니다.",
                        "금지 범위" to "승인된 대상 파일 밖의 변경과 이 화면에서의 대상 경로 편집은 금지됩니다.",
                        "예상 검증" to cockpit.opsValidationLabel,
                        "compatibility" to compatibilityLabel,
                        "잠금" to lockSummaryLabel,
                    ),
                ),
            ),
            actions = actions,
            note = "실행 전 승인된 대상 파일과 wrapper를 반드시 확인하세요. 이 화면에서 대상 경로를 직접 편집할 수 없습니다.",
            requestInboxNotice = requestInboxNotice,
            requestSaving = requestSaving,
            requestEditingEnabled = !cockpit.isReadOnlyDay &&
                (cockpit.primaryAction?.action == UiAction.EditRequest ||
                    cockpit.allowedActions.any { it.action == UiAction.EditRequest }),
            requestSaveSucceeded = requestSaveSucceeded,
            bootstrapEligible = bootstrapEligible,
            bootstrapAction = bootstrapAction,
            blockedReasonLabel = cockpit.blockedReasonLabel,
        )

        AppLocale.English -> TodayWorkProjection(
            title = "Plan",
            subtitle = "Write request → readiness check → plan/replan → run a single approved code/doc slice",
            statusChip = StatusChipModel(
                label = cockpit.queueStatusLabel,
                value = cockpit.activeSliceId ?: "",
                tone = if (cockpit.blockedReasonLabel != null) "warning" else "neutral",
            ),
            developmentStrategy = DevelopmentStrategyCardModel(
                text = strategyText,
                dateLabel = cockpit.dateLabel,
                isReadOnlyDay = cockpit.isReadOnlyDay,
            ),
            sections = listOf(
                InfoCardModel(
                    "Task queue",
                    listOf(
                        "Status" to cockpit.statusLabel,
                        "Queue status" to cockpit.queueStatusLabel,
                        "Active card" to (cockpit.activeCardId ?: "None"),
                        "Active slice" to (cockpit.activeSliceId ?: "None"),
                        "Authorized target file" to (cockpit.authorizedTargetLabel ?: "None"),
                        "Stop reason" to (cockpit.stopReasonLabel ?: "None"),
                    ),
                ),
                InfoCardModel(
                    "Execution check (required before code/doc runs, read-only)",
                    listOf(
                        "wrapper" to wrapperLabel,
                        "Authorized target file" to (cockpit.authorizedTargetLabel ?: "None"),
                        "Allowed scope" to "Changes are only allowed inside the authorized target file.",
                        "Forbidden scope" to "Changes outside the authorized target file, and editing the target path from this screen, are forbidden.",
                        "Expected validation" to cockpit.opsValidationLabel,
                        "compatibility" to compatibilityLabel,
                        "Lock" to lockSummaryLabel,
                    ),
                ),
            ),
            actions = actions,
            note = "Before running, confirm the authorized target file and wrapper. You can't edit the target path directly from this screen.",
            requestInboxNotice = requestInboxNotice,
            requestSaving = requestSaving,
            requestEditingEnabled = !cockpit.isReadOnlyDay &&
                (cockpit.primaryAction?.action == UiAction.EditRequest ||
                    cockpit.allowedActions.any { it.action == UiAction.EditRequest }),
            requestSaveSucceeded = requestSaveSucceeded,
            bootstrapEligible = bootstrapEligible,
            bootstrapAction = bootstrapAction,
            blockedReasonLabel = cockpit.blockedReasonLabel,
        )
    }
}
