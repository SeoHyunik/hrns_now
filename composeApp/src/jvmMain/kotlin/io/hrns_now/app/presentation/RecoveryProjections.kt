package io.hrns_now.app.presentation

import io.hrns_now.app.presentation.mapper.displayLabel
import io.hrns_now.app.presentation.mapper.resolveDisplayWorkflowState
import io.hrns_now.app.presentation.mapper.toDisplayText
import io.hrns_now.app.presentation.model.ActionButtonModel
import io.hrns_now.app.presentation.model.CockpitProjection
import io.hrns_now.app.presentation.model.RecoveryCardModel
import io.hrns_now.app.presentation.model.RecoveryProjection
import io.hrns_now.app.presentation.model.StatusChipModel
import io.hrns_now.core.domain.model.AppLocale
import io.hrns_now.core.domain.model.QueueBlockedReason
import io.hrns_now.core.domain.model.RecoveryDiagnostics
import io.hrns_now.core.domain.model.StopReason
import io.hrns_now.core.domain.model.UiAction
import io.hrns_now.core.domain.model.WorkflowState
import io.hrns_now.core.domain.policy.ClosureDecision
import io.hrns_now.core.result.StateReadResult

/**
 * Recovery Center/마감 확인 화면 projection이다.
 *
 * stop reason·queue blocked marker·state 읽기 실패마다 최근 작업 기록/보존된 기록/현재 허용 행동을
 * 분리해 보여준다. 어떤 값도 자동으로
 * 재시도·재실행하지 않으며, 이 함수는 표시 문구만 만든다 — CTA 활성화 여부는 여전히
 * `ActionPolicy`/`ClosurePolicy`가 계산한 [CockpitProjection.allowedActions]를 그대로 옮긴다.
 * 화면에서 선택된 [AppLocale]로 투영한다.
 */
fun buildRecoveryProjection(
    cockpit: CockpitProjection,
    stateRead: StateReadResult,
    closureDecision: ClosureDecision,
    lockSummaryLabel: String,
    closureValidationEnabledAfterAcknowledgement: Boolean = false,
    recoveryDiagnostics: RecoveryDiagnostics = RecoveryDiagnostics.Empty,
    locale: AppLocale = AppLocale.Korean,
): RecoveryProjection {
    val (state, _) = resolveDisplayWorkflowState(stateRead)
    val activeCard = stateReadFailureCard(stateRead, locale) ?: state?.let { stateProblemCard(it, locale) }

    val blockedLabel = when (locale) {
        AppLocale.Korean -> "차단"
        AppLocale.English -> "Blocked"
    }
    val closureChecklistRows = when (closureDecision) {
        ClosureDecision.Allowed -> listOf(
            when (locale) {
                AppLocale.Korean -> StatusChipModel("마감 조건", "모두 충족", "success")
                AppLocale.English -> StatusChipModel("Closure conditions", "All satisfied", "success")
            },
        )
        is ClosureDecision.Blocked ->
            closureDecision.reasons.map { reason -> StatusChipModel(blockedLabel, reason.toDisplayText(locale), "error") }
        is ClosureDecision.RequiresExplicitIncompleteHandoff -> listOf(
            when (locale) {
                AppLocale.Korean -> StatusChipModel("마감 조건", "핵심 조건 충족 · 확인 필요", "warning")
                AppLocale.English -> StatusChipModel("Closure conditions", "Core conditions met · needs acknowledgement", "warning")
            },
        )
    }
    val closureNote = when (closureDecision) {
        ClosureDecision.Allowed -> when (locale) {
            AppLocale.Korean -> "모든 마감 조건을 충족했습니다. 마감 검증을 실행할 수 있습니다."
            AppLocale.English -> "All closure conditions are satisfied. Closure validation can be run."
        }
        is ClosureDecision.Blocked -> when (locale) {
            AppLocale.Korean -> "다음 조건이 해결되지 않아 마감 검증을 실행할 수 없습니다."
            AppLocale.English -> "Closure validation can't run because the following conditions aren't resolved."
        }
        is ClosureDecision.RequiresExplicitIncompleteHandoff -> when (locale) {
            AppLocale.Korean -> "커밋되지 않은 변경이 있습니다. 이 상태로 마감하려면 아래 항목을 확인하고 명시적으로 인지해야 합니다."
            AppLocale.English -> "There are uncommitted changes. To close in this state, review the items below and acknowledge them explicitly."
        }
    }
    val uncommittedChangePrefix = when (locale) {
        AppLocale.Korean -> "커밋되지 않은 변경:"
        AppLocale.English -> "Uncommitted change:"
    }
    val incompleteHandoffItems =
        (closureDecision as? ClosureDecision.RequiresExplicitIncompleteHandoff)?.changedPaths?.map { path ->
            "$uncommittedChangePrefix $path"
        } ?: emptyList()

    val actions = listOfNotNull(
        cockpit.allowedActions.firstOrNull { it.action == UiAction.Refresh },
        cockpit.allowedActions.firstOrNull { it.action == UiAction.RunReplan },
        cockpit.allowedActions.firstOrNull { it.action == UiAction.RunClosureValidation },
    ).map { item -> ActionButtonModel(label = item.label, enabled = item.enabled, action = item.action) }

    return RecoveryProjection(
        title = when (locale) {
            AppLocale.Korean -> "복구 센터 · 마감 확인"
            AppLocale.English -> "Recovery center · Closure check"
        },
        subtitle = when (locale) {
            AppLocale.Korean -> "최근 작업 기록 / 보존된 기록 / 현재 허용된 행동을 분리해서 보여줍니다. 자동 재시도·자동 마감은 없습니다."
            AppLocale.English -> "Shows what happened / what's preserved / what's currently allowed, separately. No automatic retry or closure."
        },
        activeCard = activeCard,
        closureChecklistRows = closureChecklistRows,
        closureNote = closureNote,
        incompleteHandoffItems = incompleteHandoffItems,
        closureValidationEnabledAfterAcknowledgement = closureValidationEnabledAfterAcknowledgement,
        lastKnownGoodLabel = when (locale) {
            AppLocale.Korean -> if (state != null) "있음 (${state.date})" else "없음"
            AppLocale.English -> if (state != null) "Available (${state.date})" else "None"
        },
        continuityDiagnosticsLabel = recoveryDiagnostics.continuity.toLabel(locale),
        usageLedgerLabel = recoveryDiagnostics.usageLedger.toLabel(locale),
        failureHistoryLabel = recoveryDiagnostics.failureHistory.toLabel(locale),
        compatibilityLabel = cockpit.compatibilityDiagnostics?.whatHappened ?: when (locale) {
            AppLocale.Korean -> "호환됨"
            AppLocale.English -> "Compatible"
        },
        lockLabel = lockSummaryLabel,
        actions = actions,
    )
}

private fun io.hrns_now.core.domain.model.ContinuityDiagnosticSummary.toLabel(locale: AppLocale): String =
    when (locale) {
        AppLocale.Korean -> if (!available) {
            "없음"
        } else {
            "기록 $recordCount · 실제 resume $actualResumeAppliedCount · fresh 필요 $freshRequiredCount" +
                if (unreadableCount > 0) " · 읽기 실패 $unreadableCount" else ""
        }
        AppLocale.English -> if (!available) {
            "None"
        } else {
            "Records $recordCount · actual resume $actualResumeAppliedCount · fresh required $freshRequiredCount" +
                if (unreadableCount > 0) " · unreadable $unreadableCount" else ""
        }
    }

private fun io.hrns_now.core.domain.model.UsageLedgerSummary.toLabel(locale: AppLocale): String =
    when (locale) {
        AppLocale.Korean -> if (!available) {
            "없음"
        } else {
            "기록 $recordCount · session 메타데이터 있음 $sessionMetadataPresentCount" +
                if (unreadableCount > 0) " · 읽기 실패 $unreadableCount" else ""
        }
        AppLocale.English -> if (!available) {
            "None"
        } else {
            "Records $recordCount · session metadata present $sessionMetadataPresentCount" +
                if (unreadableCount > 0) " · unreadable $unreadableCount" else ""
        }
    }

private fun io.hrns_now.core.domain.model.FailureHistorySummary.toLabel(locale: AppLocale): String =
    when (locale) {
        AppLocale.Korean -> if (!available) "없음" else "누적 항목 $entryCount"
        AppLocale.English -> if (!available) "None" else "Accumulated entries $entryCount"
    }

private fun stateProblemCard(state: WorkflowState, locale: AppLocale): RecoveryCardModel? =
    (state.queue.blockedReason as? QueueBlockedReason.DispatchMetadataConflict)?.let {
        when (locale) {
            AppLocale.Korean -> RecoveryCardModel(
                title = "계획 대상 충돌 (dispatch_metadata_conflict)",
                whatHappened = "계획 대상의 dispatch metadata가 현재 상태와 충돌합니다.",
                preservedRecord = "Queue와 계획 기록은 그대로 보존되어 있습니다.",
                allowedNextAction = "재계획만 허용됩니다. 기존 실행 CTA는 잠깁니다.",
            )
            AppLocale.English -> RecoveryCardModel(
                title = "Plan target conflict (dispatch_metadata_conflict)",
                whatHappened = "The plan target's dispatch metadata conflicts with the current state.",
                preservedRecord = "The queue and plan records are preserved as-is.",
                allowedNextAction = "Only replanning is allowed. Existing execution CTAs are locked.",
            )
        }
    } ?: stopReasonCard(state.stopReason, locale) ?: opsValidationCard(state, locale)

private fun opsValidationCard(state: WorkflowState, locale: AppLocale): RecoveryCardModel? =
    if (!state.opsValidation.passed) {
        when (locale) {
            AppLocale.Korean -> RecoveryCardModel(
                title = "운영 검증 실패",
                whatHappened = "운영 검증(ops validation)을 통과하지 못했습니다.",
                preservedRecord = "State에 기록된 검증 결과는 보존되어 있습니다.",
                allowedNextAction = "검증 실패 원인을 해결한 뒤 운영 검증을 다시 실행하세요.",
            )
            AppLocale.English -> RecoveryCardModel(
                title = "Ops validation failed",
                whatHappened = "Ops validation didn't pass.",
                preservedRecord = "The validation result recorded in state is preserved.",
                allowedNextAction = "Resolve the cause of the failure, then run ops validation again.",
            )
        }
    } else {
        null
    }

private fun stateReadFailureCard(stateRead: StateReadResult, locale: AppLocale): RecoveryCardModel? =
    when (stateRead) {
        is StateReadResult.Malformed -> when (locale) {
            AppLocale.Korean -> RecoveryCardModel(
                title = "상태 파일 해석 실패",
                whatHappened = "WORKFLOW_STATE.json을 해석할 수 없습니다(형식 오류 또는 손상).",
                preservedRecord = if (stateRead.lastKnownGood != null) {
                    "마지막으로 성공적으로 읽은 State를 계속 표시합니다."
                } else {
                    "이전에 성공적으로 읽은 State가 없습니다."
                },
                allowedNextAction = "잠시 후 새로고침하세요. 반복되면 파일을 직접 확인하세요.",
            )
            AppLocale.English -> RecoveryCardModel(
                title = "State file parse failure",
                whatHappened = "WORKFLOW_STATE.json couldn't be parsed (malformed or corrupted).",
                preservedRecord = if (stateRead.lastKnownGood != null) {
                    "Continuing to show the last successfully read state."
                } else {
                    "There is no previously successfully read state."
                },
                allowedNextAction = "Refresh again shortly. If this keeps happening, check the file directly.",
            )
        }

        is StateReadResult.EncodingError -> when (locale) {
            AppLocale.Korean -> RecoveryCardModel(
                title = "상태 파일 인코딩 오류",
                whatHappened = "WORKFLOW_STATE.json의 인코딩을 해석할 수 없습니다.",
                preservedRecord = if (stateRead.lastKnownGood != null) {
                    "마지막으로 성공적으로 읽은 State를 계속 표시합니다."
                } else {
                    "이전에 성공적으로 읽은 State가 없습니다."
                },
                allowedNextAction = "잠시 후 새로고침하세요. 반복되면 파일을 직접 확인하세요.",
            )
            AppLocale.English -> RecoveryCardModel(
                title = "State file encoding error",
                whatHappened = "WORKFLOW_STATE.json's encoding couldn't be parsed.",
                preservedRecord = if (stateRead.lastKnownGood != null) {
                    "Continuing to show the last successfully read state."
                } else {
                    "There is no previously successfully read state."
                },
                allowedNextAction = "Refresh again shortly. If this keeps happening, check the file directly.",
            )
        }

        is StateReadResult.UnsupportedSchema -> when (locale) {
            AppLocale.Korean -> RecoveryCardModel(
                title = "지원하지 않는 schema",
                whatHappened = "지원하지 않는 WORKFLOW_STATE.json schema 버전입니다.",
                preservedRecord = "이전 데이터는 변경되지 않았습니다.",
                allowedNextAction = "앱을 최신 버전으로 갱신한 뒤 다시 확인하세요.",
            )
            AppLocale.English -> RecoveryCardModel(
                title = "Unsupported schema",
                whatHappened = "Unsupported WORKFLOW_STATE.json schema version.",
                preservedRecord = "Previous data is unchanged.",
                allowedNextAction = "Update the app to the latest version, then check again.",
            )
        }

        is StateReadResult.AccessDenied -> when (locale) {
            AppLocale.Korean -> RecoveryCardModel(
                title = "상태 파일 접근 거부",
                whatHappened = "WORKFLOW_STATE.json에 접근할 수 없습니다.",
                preservedRecord = "이전 데이터는 변경되지 않았습니다.",
                allowedNextAction = "파일 권한을 확인한 뒤 새로고침하세요.",
            )
            AppLocale.English -> RecoveryCardModel(
                title = "State file access denied",
                whatHappened = "WORKFLOW_STATE.json couldn't be accessed.",
                preservedRecord = "Previous data is unchanged.",
                allowedNextAction = "Check the file permissions, then refresh.",
            )
        }

        is StateReadResult.Missing,
        is StateReadResult.Success,
        -> null
    }

/**
 * `ActionPolicy.blockingStopReason()`이 차단으로 취급하는 stop reason 전체를 다룬다 — 그
 * 목록과 어긋나면 정책이 Recovery로 보낸 이유와 화면 카드가 서로 다른 사유를 말하게 된다.
 */
private fun stopReasonCard(stopReason: StopReason?, locale: AppLocale): RecoveryCardModel? =
    when (stopReason) {
        StopReason.UsageLimitBlocked -> when (locale) {
            AppLocale.Korean -> RecoveryCardModel(
                title = stopReason.displayLabel(locale),
                whatHappened = "Claude 사용량 제한으로 실행이 중단되었습니다.",
                preservedRecord = "지금까지 진행된 State와 Queue 기록은 보존되어 있습니다.",
                allowedNextAction = "사용량이 회복된 뒤 수동으로 재시도하세요. 자동 재시도·자동 resume은 지원하지 않습니다.",
            )
            AppLocale.English -> RecoveryCardModel(
                title = stopReason.displayLabel(locale),
                whatHappened = "Execution stopped due to Claude usage limits.",
                preservedRecord = "State and queue records made so far are preserved.",
                allowedNextAction = "Retry manually once usage recovers. Automatic retry/resume isn't supported.",
            )
        }

        StopReason.ClaudeContextLimit -> when (locale) {
            AppLocale.Korean -> RecoveryCardModel(
                title = stopReason.displayLabel(locale),
                whatHappened = "세션 문맥 한계에 도달해 실행이 중단되었습니다.",
                preservedRecord = "지금까지 완료된 작업 기록은 State에 보존되어 있습니다.",
                allowedNextAction = "새 세션으로 다시 시도하세요(fresh 실행이 필요합니다).",
            )
            AppLocale.English -> RecoveryCardModel(
                title = stopReason.displayLabel(locale),
                whatHappened = "Execution stopped after reaching the session context limit.",
                preservedRecord = "Work completed so far is preserved in state.",
                allowedNextAction = "Retry with a new session (a fresh run is required).",
            )
        }

        StopReason.ClaudeCallTimeout -> when (locale) {
            AppLocale.Korean -> RecoveryCardModel(
                title = stopReason.displayLabel(locale),
                whatHappened = "Claude 호출이 시간 내에 응답하지 않아 중단되었습니다.",
                preservedRecord = "중단 시점까지의 State는 보존되어 있습니다.",
                allowedNextAction = "다시 시도하거나 네트워크 상태를 확인하세요.",
            )
            AppLocale.English -> RecoveryCardModel(
                title = stopReason.displayLabel(locale),
                whatHappened = "Execution stopped because a Claude call didn't respond in time.",
                preservedRecord = "State up to the point of stopping is preserved.",
                allowedNextAction = "Retry, or check your network connection.",
            )
        }

        StopReason.ClaudeResponseEmpty, StopReason.ClaudeResponseTooShort -> when (locale) {
            AppLocale.Korean -> RecoveryCardModel(
                title = stopReason.displayLabel(locale),
                whatHappened = "Claude 응답을 신뢰할 수 없어 중단되었습니다.",
                preservedRecord = "중단 시점까지의 State는 보존되어 있습니다.",
                allowedNextAction = "다시 시도하세요. 반복되면 요청 내용을 검토하세요.",
            )
            AppLocale.English -> RecoveryCardModel(
                title = stopReason.displayLabel(locale),
                whatHappened = "Execution stopped because the Claude response couldn't be trusted.",
                preservedRecord = "State up to the point of stopping is preserved.",
                allowedNextAction = "Retry. If this keeps happening, review the request content.",
            )
        }

        StopReason.BudgetMaxTurns, StopReason.BudgetOrManualStop -> when (locale) {
            AppLocale.Korean -> RecoveryCardModel(
                title = stopReason.displayLabel(locale),
                whatHappened = "실행 budget 또는 수동 중단 조건에 도달했습니다.",
                preservedRecord = "중단 시점까지의 State는 보존되어 있습니다.",
                allowedNextAction = "필요하면 재계획한 뒤 다시 실행하세요.",
            )
            AppLocale.English -> RecoveryCardModel(
                title = stopReason.displayLabel(locale),
                whatHappened = "Reached the execution budget or a manual stop condition.",
                preservedRecord = "State up to the point of stopping is preserved.",
                allowedNextAction = "Replan if needed, then run again.",
            )
        }

        StopReason.TransientClaudeOverloaded -> when (locale) {
            AppLocale.Korean -> RecoveryCardModel(
                title = stopReason.displayLabel(locale),
                whatHappened = "Claude 서비스의 일시적 과부하로 중단되었습니다.",
                preservedRecord = "이전까지의 진행 상태는 보존되어 있습니다.",
                allowedNextAction = "잠시 후 다시 시도하세요.",
            )
            AppLocale.English -> RecoveryCardModel(
                title = stopReason.displayLabel(locale),
                whatHappened = "Execution stopped due to a transient Claude service overload.",
                preservedRecord = "Progress made so far is preserved.",
                allowedNextAction = "Retry again shortly.",
            )
        }

        StopReason.DispatchContractMismatch -> when (locale) {
            AppLocale.Korean -> RecoveryCardModel(
                title = stopReason.displayLabel(locale),
                whatHappened = "실행 단계의 dispatch 계약이 일치하지 않아 중단되었습니다.",
                preservedRecord = "직전까지의 State는 보존되어 있습니다.",
                allowedNextAction = "재계획을 실행해 dispatch 계약을 다시 정렬하세요.",
            )
            AppLocale.English -> RecoveryCardModel(
                title = stopReason.displayLabel(locale),
                whatHappened = "Execution stopped due to a dispatch contract mismatch.",
                preservedRecord = "State up to that point is preserved.",
                allowedNextAction = "Run replanning to realign the dispatch contract.",
            )
        }

        StopReason.ManualPrerequisiteRequired -> when (locale) {
            AppLocale.Korean -> RecoveryCardModel(
                title = stopReason.displayLabel(locale),
                whatHappened = "자동으로 진행할 수 없는 수동 선행조건이 필요합니다.",
                preservedRecord = "현재 State와 Queue는 보존되어 있습니다.",
                allowedNextAction = "안내된 수동 조건을 먼저 완료한 뒤 새로고침하세요.",
            )
            AppLocale.English -> RecoveryCardModel(
                title = stopReason.displayLabel(locale),
                whatHappened = "A manual prerequisite that can't proceed automatically is required.",
                preservedRecord = "Current state and queue are preserved.",
                allowedNextAction = "Complete the guided manual condition first, then refresh.",
            )
        }

        StopReason.RoleSlicedWrapperException -> when (locale) {
            AppLocale.Korean -> RecoveryCardModel(
                title = stopReason.displayLabel(locale),
                whatHappened = "역할별 실행 래퍼에서 예외가 발생해 중단되었습니다.",
                preservedRecord = "중단 시점까지의 State는 보존되어 있습니다.",
                allowedNextAction = "재계획 또는 수동 확인 후 새로고침하세요.",
            )
            AppLocale.English -> RecoveryCardModel(
                title = stopReason.displayLabel(locale),
                whatHappened = "Execution stopped due to an exception in the role-sliced wrapper.",
                preservedRecord = "State up to the point of stopping is preserved.",
                allowedNextAction = "Replan or check manually, then refresh.",
            )
        }

        is StopReason.Unknown -> when (locale) {
            AppLocale.Korean -> RecoveryCardModel(
                title = "알 수 없는 중단 사유",
                whatHappened = "알 수 없는 stop reason으로 중단되었습니다.",
                preservedRecord = "중단 시점까지의 State는 보존되어 있습니다.",
                allowedNextAction = "새로고침 후 권장 행동을 다시 확인하세요.",
            )
            AppLocale.English -> RecoveryCardModel(
                title = "Unknown stop reason",
                whatHappened = "Execution stopped due to an unknown stop reason.",
                preservedRecord = "State up to the point of stopping is preserved.",
                allowedNextAction = "Refresh, then check the recommended action again.",
            )
        }

        else -> null
    }
