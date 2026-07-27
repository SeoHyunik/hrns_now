package io.hrns_now.app.presentation.model

import io.hrns_now.core.domain.model.UiAction

/**
 * Compose 화면 전용 read model이다. domain이 아니므로 `core`에 두지 않는다
 * (`doc/hrns_now_design_pattern.md` §3.1/§9, `doc/hrns_now_claude_plan.md` Phase 1C).
 */
data class StatusChipModel(
    val label: String,
    val value: String,
    val tone: String = "neutral"
)

data class SourceFreshnessItem(
    val label: String,
    val fileName: String,
    val stateLabel: String
)

data class ActionButtonModel(
    val label: String,
    val enabled: Boolean = false,
    val helperText: String? = null,
    /** 표시 label과 분리된 typed 실행 식별자다. null이면 정보를 보여 주는 비실행 버튼이다. */
    val action: UiAction? = null,
)

data class InfoCardModel(
    val title: String,
    val rows: List<Pair<String, String>>
)

data class ShellProjection(
    val title: String,
    val subtitle: String,
    val statusChips: List<StatusChipModel>,
    val sourceItems: List<SourceFreshnessItem>,
    val notAppOwnedMessages: List<String>
)

data class SetupProjection(
    val title: String,
    val subtitle: String,
    val cards: List<InfoCardModel>,
    val actions: List<ActionButtonModel>,
    val note: String
)

data class TodayWorkProjection(
    val title: String,
    val subtitle: String,
    val statusChip: StatusChipModel,
    val sections: List<InfoCardModel>,
    val actions: List<ActionButtonModel>,
    val note: String,
    /** 요청 저장 결과 안내다(Phase 4) — 성공/충돌/실패 문구를 그대로 보여준다. */
    val requestInboxNotice: String? = null,
    /** 요청 저장 진행 중 여부다 — 편집기 제출 버튼의 중복 클릭 가드에 쓰인다. */
    val requestSaving: Boolean = false,
    /** 오늘 날짜이며 정책이 요청 입력을 허용할 때만 true다. */
    val requestEditingEnabled: Boolean = false,
    /** 마지막 요청 저장이 성공했는지 나타내며, 편집기는 이 값이 true일 때만 draft를 비운다. */
    val requestSaveSucceeded: Boolean = false,
)

data class RunStatusProjection(
    val title: String,
    val subtitle: String,
    val stages: List<StatusChipModel>,
    val consoleLines: List<String>,
    val stageDetailRows: List<Pair<String, String>>,
    val failureChips: List<StatusChipModel>,
    val actions: List<ActionButtonModel>,
    /** Doctor/ValidateOps 실행 취소가 가능한 상태인가(Phase 3) — 실제 클릭은 typed event로 연결한다. */
    val cancelEnabled: Boolean = false,
    /** 현재 lock을 강제 해제할 수 있는 상태인가(Phase 3). */
    val forceReleaseEnabled: Boolean = false,
)
