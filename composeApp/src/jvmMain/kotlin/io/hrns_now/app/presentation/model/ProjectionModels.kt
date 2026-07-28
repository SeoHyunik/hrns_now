package io.hrns_now.app.presentation.model

import io.hrns_now.core.domain.model.HarnessCommandKind
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

/** stop reason/blocked marker 하나의 3분리 표시다: 최근 작업 기록 / 보존된 기록 / 현재 허용 행동. */
data class RecoveryCardModel(
    val title: String,
    val whatHappened: String,
    val preservedRecord: String,
    val allowedNextAction: String,
)

/**
 * Recovery Center와 마감 확인 화면 projection이다(Phase 5). `closureChecklist`는
 * [io.hrns_now.core.domain.policy.ClosureDecision]을 read-only로 표현하고, 실행 CTA
 * enable/disable은 여전히 `ActionPolicy`/`ClosurePolicy`가 결정한 값만 그대로 보여준다 —
 * 이 화면 자체가 판단하지 않는다.
 */
data class RecoveryProjection(
    val title: String,
    val subtitle: String,
    /** 현재 관측된 stop reason/queue blocked marker/state 오류가 있을 때만 채워진다. */
    val activeCard: RecoveryCardModel?,
    val closureChecklistRows: List<StatusChipModel>,
    val closureNote: String,
    /** repository에 커밋되지 않은 변경이 있어 명시적 인지가 필요할 때만 채워진다. */
    val incompleteHandoffItems: List<String>,
    /**
     * ActionPolicy가 허용한 closure action을 `RequiresExplicitIncompleteHandoff` 화면에서만
     * acknowledgement와 교집합으로 다시 활성화할 수 있는지 나타낸다.
     */
    val closureValidationEnabledAfterAcknowledgement: Boolean = false,
    val lastKnownGoodLabel: String,
    val continuityDiagnosticsLabel: String = "없음",
    val usageLedgerLabel: String = "없음",
    val failureHistoryLabel: String = "없음",
    val compatibilityLabel: String,
    val lockLabel: String,
    val actions: List<ActionButtonModel>,
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
    /**
     * 환경 점검/작업 기준 점검을 화면 어디서나(프로젝트 관리·작업 계획) 인라인으로 보여주기 위한
     * 최근 실행 요약이다(새 Phase 6 UI/UX). raw stdout/session은 담지 않고 이미 typed/label 처리된
     * 값만 담는다 — [io.hrns_now.app.presentation.mapper.RunStatusProjectionAssembler]가 조립한다.
     */
    val isRunning: Boolean = false,
    val lastCommandKind: HarnessCommandKind? = null,
    val lastOutcome: StatusChipModel? = null,
    val lastCompletedAtLabel: String? = null,
    val lastSummaryLine: String? = null,
)
