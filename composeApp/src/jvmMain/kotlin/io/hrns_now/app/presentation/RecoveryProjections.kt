package io.hrns_now.app.presentation

import io.hrns_now.app.presentation.mapper.displayLabel
import io.hrns_now.app.presentation.mapper.resolveDisplayWorkflowState
import io.hrns_now.app.presentation.model.ActionButtonModel
import io.hrns_now.app.presentation.model.CockpitProjection
import io.hrns_now.app.presentation.model.RecoveryCardModel
import io.hrns_now.app.presentation.model.RecoveryProjection
import io.hrns_now.app.presentation.model.StatusChipModel
import io.hrns_now.core.domain.model.QueueBlockedReason
import io.hrns_now.core.domain.model.RecoveryDiagnostics
import io.hrns_now.core.domain.model.StopReason
import io.hrns_now.core.domain.model.UiAction
import io.hrns_now.core.domain.model.WorkflowState
import io.hrns_now.core.domain.policy.ClosureDecision
import io.hrns_now.core.result.StateReadResult

/**
 * Recovery Center/마감 확인 화면 projection이다(Phase 5).
 *
 * stop reason·queue blocked marker·state 읽기 실패마다 최근 작업 기록/보존된 기록/현재 허용 행동을
 * 분리해 보여준다(`doc/claude_prompts/phase5-closure-recovery.md` §3). 어떤 값도 자동으로
 * 재시도·재실행하지 않으며, 이 함수는 표시 문구만 만든다 — CTA 활성화 여부는 여전히
 * `ActionPolicy`/`ClosurePolicy`가 계산한 [CockpitProjection.allowedActions]를 그대로 옮긴다.
 */
fun buildRecoveryProjection(
    cockpit: CockpitProjection,
    stateRead: StateReadResult,
    closureDecision: ClosureDecision,
    lockSummaryLabel: String,
    closureValidationEnabledAfterAcknowledgement: Boolean = false,
    recoveryDiagnostics: RecoveryDiagnostics = RecoveryDiagnostics.Empty,
): RecoveryProjection {
    val (state, _) = resolveDisplayWorkflowState(stateRead)
    val activeCard = stateReadFailureCard(stateRead) ?: state?.let(::stateProblemCard)

    val closureChecklistRows = when (closureDecision) {
        ClosureDecision.Allowed ->
            listOf(StatusChipModel("마감 조건", "모두 충족", "success"))
        is ClosureDecision.Blocked ->
            closureDecision.reasons.map { reason -> StatusChipModel("차단", reason, "error") }
        is ClosureDecision.RequiresExplicitIncompleteHandoff ->
            listOf(StatusChipModel("마감 조건", "핵심 조건 충족 · 확인 필요", "warning"))
    }
    val closureNote = when (closureDecision) {
        ClosureDecision.Allowed -> "모든 마감 조건을 충족했습니다. 마감 검증을 실행할 수 있습니다."
        is ClosureDecision.Blocked -> "다음 조건이 해결되지 않아 마감 검증을 실행할 수 없습니다."
        is ClosureDecision.RequiresExplicitIncompleteHandoff ->
            "커밋되지 않은 변경이 있습니다. 이 상태로 마감하려면 아래 항목을 확인하고 명시적으로 인지해야 합니다."
    }
    val incompleteHandoffItems = (closureDecision as? ClosureDecision.RequiresExplicitIncompleteHandoff)?.items
        ?: emptyList()

    val actions = listOfNotNull(
        cockpit.allowedActions.firstOrNull { it.action == UiAction.Refresh },
        cockpit.allowedActions.firstOrNull { it.action == UiAction.RunReplan },
        cockpit.allowedActions.firstOrNull { it.action == UiAction.RunClosureValidation },
    ).map { item -> ActionButtonModel(label = item.label, enabled = item.enabled, action = item.action) }

    return RecoveryProjection(
        title = "복구 센터 · 마감 확인",
        subtitle = "최근 작업 기록 / 보존된 기록 / 현재 허용된 행동을 분리해서 보여줍니다. 자동 재시도·자동 마감은 없습니다.",
        activeCard = activeCard,
        closureChecklistRows = closureChecklistRows,
        closureNote = closureNote,
        incompleteHandoffItems = incompleteHandoffItems,
        closureValidationEnabledAfterAcknowledgement = closureValidationEnabledAfterAcknowledgement,
        lastKnownGoodLabel = if (state != null) "있음 (${state.date})" else "없음",
        continuityDiagnosticsLabel = recoveryDiagnostics.continuity.toLabel(),
        usageLedgerLabel = recoveryDiagnostics.usageLedger.toLabel(),
        failureHistoryLabel = recoveryDiagnostics.failureHistory.toLabel(),
        compatibilityLabel = cockpit.compatibilityDiagnostics?.whatHappened ?: "호환됨",
        lockLabel = lockSummaryLabel,
        actions = actions,
    )
}

private fun io.hrns_now.core.domain.model.ContinuityDiagnosticSummary.toLabel(): String =
    if (!available) "없음"
    else "기록 $recordCount · 실제 resume $actualResumeAppliedCount · fresh 필요 $freshRequiredCount" +
        if (unreadableCount > 0) " · 읽기 실패 $unreadableCount" else ""

private fun io.hrns_now.core.domain.model.UsageLedgerSummary.toLabel(): String =
    if (!available) "없음"
    else "기록 $recordCount · session 메타데이터 있음 $sessionMetadataPresentCount" +
        if (unreadableCount > 0) " · 읽기 실패 $unreadableCount" else ""

private fun io.hrns_now.core.domain.model.FailureHistorySummary.toLabel(): String =
    if (!available) "없음" else "누적 항목 $entryCount"

private fun stateProblemCard(state: WorkflowState): RecoveryCardModel? =
    (state.queue.blockedReason as? QueueBlockedReason.DispatchMetadataConflict)?.let {
        RecoveryCardModel(
            title = "계획 대상 충돌 (dispatch_metadata_conflict)",
            whatHappened = "계획 대상의 dispatch metadata가 현재 상태와 충돌합니다.",
            preservedRecord = "Queue와 계획 기록은 그대로 보존되어 있습니다.",
            allowedNextAction = "재계획만 허용됩니다. 기존 실행 CTA는 잠깁니다.",
        )
    } ?: stopReasonCard(state.stopReason) ?: opsValidationCard(state)

private fun opsValidationCard(state: WorkflowState): RecoveryCardModel? =
    if (!state.opsValidation.passed) {
        RecoveryCardModel(
            title = "운영 검증 실패",
            whatHappened = "운영 검증(ops validation)을 통과하지 못했습니다.",
            preservedRecord = "State에 기록된 검증 결과는 보존되어 있습니다.",
            allowedNextAction = "검증 실패 원인을 해결한 뒤 운영 검증을 다시 실행하세요.",
        )
    } else {
        null
    }

private fun stateReadFailureCard(stateRead: StateReadResult): RecoveryCardModel? =
    when (stateRead) {
        is StateReadResult.Malformed -> RecoveryCardModel(
            title = "상태 파일 해석 실패",
            whatHappened = "WORKFLOW_STATE.json을 해석할 수 없습니다(형식 오류 또는 손상).",
            preservedRecord = if (stateRead.lastKnownGood != null) {
                "마지막으로 성공적으로 읽은 State를 계속 표시합니다."
            } else {
                "이전에 성공적으로 읽은 State가 없습니다."
            },
            allowedNextAction = "잠시 후 새로고침하세요. 반복되면 파일을 직접 확인하세요.",
        )

        is StateReadResult.EncodingError -> RecoveryCardModel(
            title = "상태 파일 인코딩 오류",
            whatHappened = "WORKFLOW_STATE.json의 인코딩을 해석할 수 없습니다.",
            preservedRecord = if (stateRead.lastKnownGood != null) {
                "마지막으로 성공적으로 읽은 State를 계속 표시합니다."
            } else {
                "이전에 성공적으로 읽은 State가 없습니다."
            },
            allowedNextAction = "잠시 후 새로고침하세요. 반복되면 파일을 직접 확인하세요.",
        )

        is StateReadResult.UnsupportedSchema -> RecoveryCardModel(
            title = "지원하지 않는 schema",
            whatHappened = "지원하지 않는 WORKFLOW_STATE.json schema 버전입니다.",
            preservedRecord = "이전 데이터는 변경되지 않았습니다.",
            allowedNextAction = "앱을 최신 버전으로 갱신한 뒤 다시 확인하세요.",
        )

        is StateReadResult.AccessDenied -> RecoveryCardModel(
            title = "상태 파일 접근 거부",
            whatHappened = "WORKFLOW_STATE.json에 접근할 수 없습니다.",
            preservedRecord = "이전 데이터는 변경되지 않았습니다.",
            allowedNextAction = "파일 권한을 확인한 뒤 새로고침하세요.",
        )

        is StateReadResult.Missing,
        is StateReadResult.Success,
        -> null
    }

/**
 * `ActionPolicy.blockingStopReason()`이 차단으로 취급하는 stop reason 전체를 다룬다 — 그
 * 목록과 어긋나면 정책이 Recovery로 보낸 이유와 화면 카드가 서로 다른 사유를 말하게 된다.
 */
private fun stopReasonCard(stopReason: StopReason?): RecoveryCardModel? =
    when (stopReason) {
        StopReason.UsageLimitBlocked -> RecoveryCardModel(
            title = stopReason.displayLabel(),
            whatHappened = "Claude 사용량 제한으로 실행이 중단되었습니다.",
            preservedRecord = "지금까지 진행된 State와 Queue 기록은 보존되어 있습니다.",
            allowedNextAction = "사용량이 회복된 뒤 수동으로 재시도하세요. 자동 재시도·자동 resume은 지원하지 않습니다.",
        )

        StopReason.ClaudeContextLimit -> RecoveryCardModel(
            title = stopReason.displayLabel(),
            whatHappened = "세션 문맥 한계에 도달해 실행이 중단되었습니다.",
            preservedRecord = "지금까지 완료된 작업 기록은 State에 보존되어 있습니다.",
            allowedNextAction = "새 세션으로 다시 시도하세요(fresh 실행이 필요합니다).",
        )

        StopReason.ClaudeCallTimeout -> RecoveryCardModel(
            title = stopReason.displayLabel(),
            whatHappened = "Claude 호출이 시간 내에 응답하지 않아 중단되었습니다.",
            preservedRecord = "중단 시점까지의 State는 보존되어 있습니다.",
            allowedNextAction = "다시 시도하거나 네트워크 상태를 확인하세요.",
        )

        StopReason.ClaudeResponseEmpty, StopReason.ClaudeResponseTooShort -> RecoveryCardModel(
            title = stopReason.displayLabel(),
            whatHappened = "Claude 응답을 신뢰할 수 없어 중단되었습니다.",
            preservedRecord = "중단 시점까지의 State는 보존되어 있습니다.",
            allowedNextAction = "다시 시도하세요. 반복되면 요청 내용을 검토하세요.",
        )

        StopReason.BudgetMaxTurns, StopReason.BudgetOrManualStop -> RecoveryCardModel(
            title = stopReason.displayLabel(),
            whatHappened = "실행 budget 또는 수동 중단 조건에 도달했습니다.",
            preservedRecord = "중단 시점까지의 State는 보존되어 있습니다.",
            allowedNextAction = "필요하면 재계획한 뒤 다시 실행하세요.",
        )

        StopReason.TransientClaudeOverloaded -> RecoveryCardModel(
            title = stopReason.displayLabel(),
            whatHappened = "Claude 서비스의 일시적 과부하로 중단되었습니다.",
            preservedRecord = "이전까지의 진행 상태는 보존되어 있습니다.",
            allowedNextAction = "잠시 후 다시 시도하세요.",
        )

        StopReason.DispatchContractMismatch -> RecoveryCardModel(
            title = stopReason.displayLabel(),
            whatHappened = "실행 단계의 dispatch 계약이 일치하지 않아 중단되었습니다.",
            preservedRecord = "직전까지의 State는 보존되어 있습니다.",
            allowedNextAction = "재계획을 실행해 dispatch 계약을 다시 정렬하세요.",
        )

        StopReason.ManualPrerequisiteRequired -> RecoveryCardModel(
            title = stopReason.displayLabel(),
            whatHappened = "자동으로 진행할 수 없는 수동 선행조건이 필요합니다.",
            preservedRecord = "현재 State와 Queue는 보존되어 있습니다.",
            allowedNextAction = "안내된 수동 조건을 먼저 완료한 뒤 새로고침하세요.",
        )

        StopReason.RoleSlicedWrapperException -> RecoveryCardModel(
            title = stopReason.displayLabel(),
            whatHappened = "역할별 실행 래퍼에서 예외가 발생해 중단되었습니다.",
            preservedRecord = "중단 시점까지의 State는 보존되어 있습니다.",
            allowedNextAction = "재계획 또는 수동 확인 후 새로고침하세요.",
        )

        is StopReason.Unknown -> RecoveryCardModel(
            title = "알 수 없는 중단 사유",
            whatHappened = "알 수 없는 stop reason으로 중단되었습니다.",
            preservedRecord = "중단 시점까지의 State는 보존되어 있습니다.",
            allowedNextAction = "새로고침 후 권장 행동을 다시 확인하세요.",
        )

        else -> null
    }
