package io.hrns_now.app.presentation.mapper

import io.hrns_now.app.presentation.model.RunStatusProjection
import io.hrns_now.app.presentation.model.StatusChipModel
import io.hrns_now.app.presentation.viewmodel.HarnessRunViewState
import io.hrns_now.core.domain.model.HarnessCommandKind
import io.hrns_now.core.domain.model.LockState
import io.hrns_now.core.domain.model.UiAction
import io.hrns_now.core.port.LockInspection
import io.hrns_now.core.result.HarnessCheckSeverity
import io.hrns_now.core.result.HarnessOverallStatus
import io.hrns_now.core.result.ProcessRunResult
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Harness 실행·진단 상태와 lock 조회 결과를 [RunStatusProjection]으로 조립한다(Phase 3/4).
 * raw stdout/session/PID 원문을 그대로 노출하지 않는다 — [ProcessRunResult]는 이미
 * `SecretMaskingProcessRunner`를 거친 값이며, 여기서는 label/tone 변환만 한다.
 */
class RunStatusProjectionAssembler {

    fun assemble(
        runView: HarnessRunViewState,
        lockInspection: LockInspection?,
        now: Instant,
        externalExecutionSuspected: Boolean = false,
    ): RunStatusProjection {
        val stages = HarnessCommandKind.entries.map { kind -> stageChip(kind, runView) }
        val consoleLines = consoleLines(runView, externalExecutionSuspected)
        val detailRows = detailRows(runView, lockInspection, now)
        val failureChips = failureChips(runView)

        return RunStatusProjection(
            title = "실행 기록",
            subtitle = "Harness 실행·진단 상태입니다. 권장 행동은 작업 현황과 작업 계획 화면에서 시작합니다.",
            stages = stages,
            consoleLines = consoleLines,
            stageDetailRows = detailRows,
            failureChips = failureChips,
            actions = emptyList(),
            cancelEnabled = runView.isRunning,
            forceReleaseEnabled = lockInspection != null,
            isRunning = runView.isRunning,
            lastCommandKind = runView.lastCommand,
            lastOutcome = runView.lastCommand?.let { kind ->
                if (runView.isRunning) {
                    StatusChipModel(kind.displayLabel(), "진행 중", "warning")
                } else {
                    runView.lastResult?.let { result -> StatusChipModel(kind.displayLabel(), result.outcomeLabel(), result.tone()) }
                }
            },
            lastCompletedAtLabel = runView.runCompletedAt?.formatted(),
            lastSummaryLine = runView.lastResult?.summaryLine(),
        )
    }

    private fun stageChip(kind: HarnessCommandKind, runView: HarnessRunViewState): StatusChipModel {
        val label = kind.displayLabel()
        if (runView.isRunning && runView.lastCommand == kind) {
            return StatusChipModel(label, "실행 중", "warning")
        }
        if (runView.lastCommand != kind) {
            return StatusChipModel(label, "대기", "neutral")
        }
        val result = runView.lastResult ?: return StatusChipModel(label, "대기", "neutral")
        return StatusChipModel(label, result.outcomeLabel(), result.tone())
    }

    private fun consoleLines(
        runView: HarnessRunViewState,
        externalExecutionSuspected: Boolean,
    ): List<String> {
        val notices = buildList {
            if (externalExecutionSuspected) {
                add("[외부 실행 가능성] WORKFLOW_STATE.json의 UI 외부 변경이 감지되어 새 실행을 보류합니다. 새로고침으로 재확인하세요.")
            }
            runView.notice?.let { add("[실행 보류] $it") }
        }
        if (runView.isRunning) {
            return notices + "[실행 중] ${runView.lastCommand?.displayLabel() ?: ""} 실행 중입니다."
        }
        val result = runView.lastResult ?: return notices.ifEmpty { listOf("[대기] 아직 실행한 진단이 없습니다.") }
        return notices + when (result) {
            is ProcessRunResult.Completed -> {
                val contract = result.contract
                if (contract != null) {
                    contract.checks.map { check -> "[${check.severity.displayTag()}] ${check.message}" }
                } else {
                    listOfNotNull(
                        "[해석 불가] JSON 결과를 해석하지 못했습니다 (exit=${result.exitCode}).",
                        result.rawOutputSnippet?.let { "요약: $it" },
                    )
                }
            }

            is ProcessRunResult.StartFailed -> listOf("[시작 실패] ${result.reason}")
            is ProcessRunResult.TimedOut -> listOf(
                "[시간 초과] ${result.elapsedMillis}ms 뒤 시간 초과되어 종료를 시도했습니다.",
                if (result.residualProcessDetected) {
                    "[경고] 종료 시도 후에도 프로세스가 남아있을 수 있습니다."
                } else {
                    "프로세스 종료를 확인했습니다."
                },
            )

            is ProcessRunResult.Cancelled -> listOf(
                "[취소됨] 사용자 요청으로 취소했습니다.",
                if (result.residualProcessDetected) {
                    "[경고] 취소 시도 후에도 프로세스가 남아있을 수 있습니다."
                } else {
                    "프로세스 종료를 확인했습니다."
                },
            )
        }
    }

    private fun detailRows(
        runView: HarnessRunViewState,
        lockInspection: LockInspection?,
        now: Instant,
    ): List<Pair<String, String>> = buildList {
        runView.lastCommand?.let { add("마지막 실행" to it.displayLabel()) }
        runView.runStartedAt?.let { add("실행 시작 시각" to it.formatted()) }
        (runView.lastResult as? ProcessRunResult.Completed)?.let { add("종료 코드" to it.exitCode.toString()) }
        add("잠금 소유자" to lockSummaryLabel(lockInspection, now))
    }

    private fun failureChips(runView: HarnessRunViewState): List<StatusChipModel> {
        val contract = (runView.lastResult as? ProcessRunResult.Completed)?.contract ?: return emptyList()
        return contract.checks
            .filter { it.severity is HarnessCheckSeverity.Warn || it.severity is HarnessCheckSeverity.Error }
            .map { StatusChipModel(it.id, it.severity.displayTag(), if (it.severity is HarnessCheckSeverity.Error) "error" else "warning") }
    }

    private fun ProcessRunResult.outcomeLabel(): String =
        when (this) {
            is ProcessRunResult.Completed -> when (val overall = contract?.overall) {
                HarnessOverallStatus.Ok -> "정상"
                HarnessOverallStatus.Warn -> "경고"
                HarnessOverallStatus.Fail -> "실패"
                is HarnessOverallStatus.Unknown -> "확인 필요"
                null -> "해석 불가"
            }

            is ProcessRunResult.StartFailed -> "시작 실패"
            is ProcessRunResult.TimedOut -> "시간 초과"
            is ProcessRunResult.Cancelled -> "취소됨"
        }

    private fun ProcessRunResult.tone(): String =
        when (this) {
            is ProcessRunResult.Completed -> when (contract?.overall) {
                HarnessOverallStatus.Ok -> "success"
                HarnessOverallStatus.Warn -> "warning"
                HarnessOverallStatus.Fail -> "error"
                is HarnessOverallStatus.Unknown, null -> "warning"
            }

            is ProcessRunResult.StartFailed, is ProcessRunResult.TimedOut, is ProcessRunResult.Cancelled -> "error"
        }

    /** 인라인 실행 feedback 카드(새 Phase 6)의 "짧은 결과 요약" 한 줄이다. */
    private fun ProcessRunResult.summaryLine(): String =
        when (this) {
            is ProcessRunResult.Completed -> {
                val checkContract = contract
                if (checkContract == null) {
                    "JSON 결과를 해석하지 못했습니다 (exit=$exitCode)."
                } else {
                    val failed = checkContract.checks.firstOrNull { it.severity is HarnessCheckSeverity.Error }
                    val warned = checkContract.checks.firstOrNull { it.severity is HarnessCheckSeverity.Warn }
                    when {
                        failed != null -> failed.message
                        warned != null -> warned.message
                        checkContract.overall == HarnessOverallStatus.Ok -> "모든 점검을 통과했습니다."
                        else -> "결과를 확인하세요."
                    }
                }
            }

            is ProcessRunResult.StartFailed -> reason
            is ProcessRunResult.TimedOut -> "시간 초과 후 종료를 시도했습니다."
            is ProcessRunResult.Cancelled -> "사용자 요청으로 취소했습니다."
        }

    private fun HarnessCheckSeverity.displayTag(): String =
        when (this) {
            HarnessCheckSeverity.Info -> "INFO"
            HarnessCheckSeverity.Warn -> "WARN"
            HarnessCheckSeverity.Error -> "FAIL"
            is HarnessCheckSeverity.Unknown -> "?"
        }

    private fun Instant.formatted(): String =
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()).format(this)
}

internal fun HarnessCommandKind.displayLabel(): String =
    when (this) {
        HarnessCommandKind.Doctor -> "연결 점검"
        HarnessCommandKind.ValidateOps -> "작업 준비 점검"
        HarnessCommandKind.Bootstrap -> "오늘 작업 시작"
        HarnessCommandKind.Planning -> "계획 실행"
        HarnessCommandKind.Replan -> "재계획 실행"
        HarnessCommandKind.ExecutionCode -> "코드 작업 실행"
        HarnessCommandKind.ExecutionDoc -> "문서 작업 실행"
        HarnessCommandKind.ClosureValidation -> "마감 검증 실행"
    }

/**
 * 인라인 실행 feedback 카드(새 Phase 6)의 "다시 점검/다시 검증" 재시도 버튼이 다시 호출할 typed
 * action이다. Bootstrap/Planning류는 각자의 화면 action 그룹에서 이미 재클릭 가능하므로 별도
 * 재시도 CTA를 중복 제공하지 않는다 — 연결 점검·작업 준비 점검만 명시적 재시도 문구를 붙인다.
 */
internal fun HarnessCommandKind.toRetryAction(): UiAction? =
    when (this) {
        HarnessCommandKind.Doctor -> UiAction.RunDoctor
        HarnessCommandKind.ValidateOps -> UiAction.RunOpsValidation
        else -> null
    }

internal fun HarnessCommandKind.retryLabel(): String =
    when (this) {
        HarnessCommandKind.Doctor -> "다시 점검"
        HarnessCommandKind.ValidateOps -> "다시 검증"
        else -> "다시 실행"
    }
