package io.hrns_now.app.presentation

import io.hrns_now.app.presentation.model.ActionButtonModel
import io.hrns_now.app.presentation.model.CockpitActionItem
import io.hrns_now.app.presentation.model.InfoCardModel
import io.hrns_now.app.presentation.model.SetupProjection
import io.hrns_now.app.presentation.model.ShellProjection
import io.hrns_now.app.presentation.model.SourceFreshnessItem
import io.hrns_now.app.presentation.model.StatusChipModel
import io.hrns_now.app.presentation.model.TodayWorkProjection
import io.hrns_now.core.config.PathProbeState
import io.hrns_now.core.config.WorkspaceConfig
import io.hrns_now.core.config.WorkspaceProbeSummary
import io.hrns_now.core.domain.model.UiAction

/**
 * production 기본 경로에서 쓰는, mock이 아닌 정적/실데이터 기반 projection 조립부다.
 *
 * `MockProjectionProvider`는 명시적 demo mode 전용이므로(`doc/claude_prompts/phase1c-live-cockpit.md`),
 * production은 이 파일의 함수로 shell/setup 화면을 구성한다. 아직 실행 연결이 없는 화면
 * (오늘 할 일/실행 현황)은 성공한 것처럼 꾸미지 않고 "미구현" 상태를 그대로 보여준다.
 */
fun buildShellProjection(): ShellProjection =
    ShellProjection(
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

fun buildSetupProjection(
    config: WorkspaceConfig,
    probeSummary: WorkspaceProbeSummary,
    diagnosticActions: List<CockpitActionItem> = emptyList(),
): SetupProjection {
    val rows = listOf(
        probeSummary.kitRoot,
        probeSummary.workspaceRoot,
        probeSummary.projectRoot,
        probeSummary.powerShellPath,
        probeSummary.claudeCommand,
    )
    return SetupProjection(
        title = "작업공간 연결",
        subtitle = "프로젝트와 harness-kit 실행 환경을 안전하게 연결합니다.",
        cards = listOf(
            InfoCardModel(
                "경로 상태",
                rows.map { it.label to it.state.summaryLabel() },
            ),
            InfoCardModel("실행 프로필", listOf("프로필" to config.profileName)),
        ),
        actions = listOf(
            diagnosticAction(
                label = "상태 점검 실행",
                action = UiAction.RunDoctor,
                diagnosticActions = diagnosticActions,
            ),
            diagnosticAction(
                label = "운영 검증 실행",
                action = UiAction.RunOpsValidation,
                diagnosticActions = diagnosticActions,
            ),
        ),
        note = "상태 점검과 운영 검증은 선택한 프로젝트·날짜 및 안전 조건을 충족할 때만 실행됩니다.",
    )
}

private fun diagnosticAction(
    label: String,
    action: UiAction,
    diagnosticActions: List<CockpitActionItem>,
): ActionButtonModel {
    val actionItem = diagnosticActions.firstOrNull { it.action == action }
    return ActionButtonModel(
        label = label,
        enabled = actionItem?.enabled == true,
        helperText = if (actionItem == null) "현재 상태에서는 실행할 수 없습니다." else null,
        action = action,
    )
}
private fun PathProbeState.summaryLabel(): String =
    when (this) {
        PathProbeState.NotConfigured -> "미설정"
        PathProbeState.Exists -> "확인됨"
        PathProbeState.Missing -> "없음"
        PathProbeState.NotReadable -> "읽기 불가"
        PathProbeState.WrongType -> "유형 불일치"
        PathProbeState.Unknown -> "확인 필요"
    }

fun buildPlaceholderTodayWorkProjection(): TodayWorkProjection =
    TodayWorkProjection(
        title = "오늘 할 일",
        subtitle = "이 화면은 Phase 4(표준 일일 실행 흐름)에서 실데이터로 연결됩니다.",
        statusChip = StatusChipModel("아직 연결되지 않음", "", "muted"),
        sections = listOf(
            InfoCardModel("안내", listOf("상태" to "이 화면은 아직 준비 중입니다.")),
        ),
        actions = emptyList(),
        note = "수동 실행은 이후 PS1 façade에 연결됩니다.",
    )
