package io.hrns_now.app.presentation.model

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
    val helperText: String? = null
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
    val note: String
)

data class RunStatusProjection(
    val title: String,
    val subtitle: String,
    val stages: List<StatusChipModel>,
    val consoleLines: List<String>,
    val stageDetailRows: List<Pair<String, String>>,
    val failureChips: List<StatusChipModel>,
    val actions: List<ActionButtonModel>
)
