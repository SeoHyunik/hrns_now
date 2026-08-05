package io.hrns_now.app.presentation.mapper

import io.hrns_now.app.presentation.model.RunStatusProjection
import io.hrns_now.app.presentation.model.StatusChipModel
import io.hrns_now.app.presentation.viewmodel.HarnessRunViewState
import io.hrns_now.core.domain.model.AppLocale
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
 * Harness 실행·진단 상태와 lock 조회 결과를 [RunStatusProjection]으로 조립한다.
 * raw stdout/session/PID 원문을 그대로 노출하지 않는다 — [ProcessRunResult]는 이미
 * `SecretMaskingProcessRunner`를 거친 값이며, 여기서는 label/tone 변환만 한다.
 * 화면에서 선택된 [AppLocale]로 투영한다.
 */
class RunStatusProjectionAssembler {

    fun assemble(
        runView: HarnessRunViewState,
        lockInspection: LockInspection?,
        now: Instant,
        externalExecutionSuspected: Boolean = false,
        locale: AppLocale = AppLocale.Korean,
    ): RunStatusProjection {
        val stages = HarnessCommandKind.entries.map { kind -> stageChip(kind, runView, locale) }
        val consoleLines = consoleLines(runView, externalExecutionSuspected, locale)
        val detailRows = detailRows(runView, lockInspection, now, locale)
        val failureChips = failureChips(runView)

        return RunStatusProjection(
            title = when (locale) {
                AppLocale.Korean -> "실행 기록"
                AppLocale.English -> "Run history"
            },
            subtitle = when (locale) {
                AppLocale.Korean -> "Harness 실행·진단 상태입니다. 권장 행동은 작업 현황과 작업 계획 화면에서 시작합니다."
                AppLocale.English -> "Harness run/diagnostic status. Start recommended actions from the status and plan screens."
            },
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
                    StatusChipModel(kind.displayLabel(locale), inProgressLabel(locale), "warning")
                } else {
                    runView.lastResult?.let { result -> StatusChipModel(kind.displayLabel(locale), result.outcomeLabel(locale), result.tone()) }
                }
            },
            lastCompletedAtLabel = runView.runCompletedAt?.formatted(),
            lastSummaryLine = runView.lastResult?.summaryLine(locale),
        )
    }

    private fun inProgressLabel(locale: AppLocale): String = when (locale) {
        AppLocale.Korean -> "진행 중"
        AppLocale.English -> "In progress"
    }

    private fun waitingLabel(locale: AppLocale): String = when (locale) {
        AppLocale.Korean -> "대기"
        AppLocale.English -> "Waiting"
    }

    private fun stageChip(kind: HarnessCommandKind, runView: HarnessRunViewState, locale: AppLocale): StatusChipModel {
        val label = kind.displayLabel(locale)
        if (runView.isRunning && runView.lastCommand == kind) {
            return StatusChipModel(label, inProgressLabel(locale), "warning")
        }
        if (runView.lastCommand != kind) {
            return StatusChipModel(label, waitingLabel(locale), "neutral")
        }
        val result = runView.lastResult ?: return StatusChipModel(label, waitingLabel(locale), "neutral")
        return StatusChipModel(label, result.outcomeLabel(locale), result.tone())
    }

    private fun consoleLines(
        runView: HarnessRunViewState,
        externalExecutionSuspected: Boolean,
        locale: AppLocale,
    ): List<String> {
        val notices = buildList {
            if (externalExecutionSuspected) {
                add(
                    when (locale) {
                        AppLocale.Korean -> "[외부 실행 가능성] WORKFLOW_STATE.json의 UI 외부 변경이 감지되어 새 실행을 보류합니다. 새로고침으로 재확인하세요."
                        AppLocale.English -> "[Possible external run] A change to WORKFLOW_STATE.json outside the UI was detected — new runs are held back. Refresh to check again."
                    },
                )
            }
            runView.notice?.let {
                add(
                    when (locale) {
                        AppLocale.Korean -> "[실행 보류] $it"
                        AppLocale.English -> "[Run held back] $it"
                    },
                )
            }
        }
        if (runView.isRunning) {
            val running = when (locale) {
                AppLocale.Korean -> "[실행 중] ${runView.lastCommand?.displayLabel(locale) ?: ""} 실행 중입니다."
                AppLocale.English -> "[Running] ${runView.lastCommand?.displayLabel(locale) ?: ""} is running."
            }
            return notices + running
        }
        val result = runView.lastResult ?: return notices.ifEmpty {
            listOf(
                when (locale) {
                    AppLocale.Korean -> "[대기] 아직 실행한 진단이 없습니다."
                    AppLocale.English -> "[Waiting] No diagnostics have been run yet."
                },
            )
        }
        return notices + when (result) {
            is ProcessRunResult.Completed -> {
                val contract = result.contract
                if (contract != null) {
                    contract.checks.map { check -> "[${check.severity.displayTag()}] ${check.message}" }
                } else {
                    when (locale) {
                        AppLocale.Korean -> listOfNotNull(
                            "[해석 불가] JSON 결과를 해석하지 못했습니다 (exit=${result.exitCode}).",
                            result.rawOutputSnippet?.let { "요약: $it" },
                        )
                        AppLocale.English -> listOfNotNull(
                            "[Unparseable] Couldn't parse the JSON result (exit=${result.exitCode}).",
                            result.rawOutputSnippet?.let { "Summary: $it" },
                        )
                    }
                }
            }

            is ProcessRunResult.StartFailed -> listOf(
                when (locale) {
                    AppLocale.Korean -> "[시작 실패] ${result.reason}"
                    AppLocale.English -> "[Start failed] ${result.reason}"
                },
            )
            is ProcessRunResult.TimedOut -> when (locale) {
                AppLocale.Korean -> listOf(
                    "[시간 초과] ${result.elapsedMillis}ms 뒤 시간 초과되어 종료를 시도했습니다.",
                    if (result.residualProcessDetected) {
                        "[경고] 종료 시도 후에도 프로세스가 남아있을 수 있습니다."
                    } else {
                        "프로세스 종료를 확인했습니다."
                    },
                )
                AppLocale.English -> listOf(
                    "[Timed out] Timed out after ${result.elapsedMillis}ms and attempted to terminate.",
                    if (result.residualProcessDetected) {
                        "[Warning] The process may still remain after the termination attempt."
                    } else {
                        "Confirmed the process terminated."
                    },
                )
            }

            is ProcessRunResult.Cancelled -> when (locale) {
                AppLocale.Korean -> listOf(
                    "[취소됨] 사용자 요청으로 취소했습니다.",
                    if (result.residualProcessDetected) {
                        "[경고] 취소 시도 후에도 프로세스가 남아있을 수 있습니다."
                    } else {
                        "프로세스 종료를 확인했습니다."
                    },
                )
                AppLocale.English -> listOf(
                    "[Cancelled] Cancelled at the user's request.",
                    if (result.residualProcessDetected) {
                        "[Warning] The process may still remain after the cancellation attempt."
                    } else {
                        "Confirmed the process terminated."
                    },
                )
            }
        }
    }

    private fun detailRows(
        runView: HarnessRunViewState,
        lockInspection: LockInspection?,
        now: Instant,
        locale: AppLocale,
    ): List<Pair<String, String>> = buildList {
        val lastRunLabel = when (locale) {
            AppLocale.Korean -> "마지막 실행"
            AppLocale.English -> "Last run"
        }
        val startedAtLabel = when (locale) {
            AppLocale.Korean -> "실행 시작 시각"
            AppLocale.English -> "Started at"
        }
        val exitCodeLabel = when (locale) {
            AppLocale.Korean -> "종료 코드"
            AppLocale.English -> "Exit code"
        }
        val lockOwnerLabel = when (locale) {
            AppLocale.Korean -> "잠금 소유자"
            AppLocale.English -> "Lock owner"
        }
        runView.lastCommand?.let { add(lastRunLabel to it.displayLabel(locale)) }
        runView.runStartedAt?.let { add(startedAtLabel to it.formatted()) }
        (runView.lastResult as? ProcessRunResult.Completed)?.let { add(exitCodeLabel to it.exitCode.toString()) }
        add(lockOwnerLabel to lockSummaryLabel(lockInspection, now))
    }

    private fun failureChips(runView: HarnessRunViewState): List<StatusChipModel> {
        val contract = (runView.lastResult as? ProcessRunResult.Completed)?.contract ?: return emptyList()
        return contract.checks
            .filter { it.severity is HarnessCheckSeverity.Warn || it.severity is HarnessCheckSeverity.Error }
            .map { StatusChipModel(it.id, it.severity.displayTag(), if (it.severity is HarnessCheckSeverity.Error) "error" else "warning") }
    }

    private fun ProcessRunResult.outcomeLabel(locale: AppLocale): String =
        when (locale) {
            AppLocale.Korean -> when (this) {
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
            AppLocale.English -> when (this) {
                is ProcessRunResult.Completed -> when (contract?.overall) {
                    HarnessOverallStatus.Ok -> "OK"
                    HarnessOverallStatus.Warn -> "Warning"
                    HarnessOverallStatus.Fail -> "Failed"
                    is HarnessOverallStatus.Unknown -> "Needs review"
                    null -> "Unparseable"
                }

                is ProcessRunResult.StartFailed -> "Start failed"
                is ProcessRunResult.TimedOut -> "Timed out"
                is ProcessRunResult.Cancelled -> "Cancelled"
            }
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

    /** 인라인 실행 feedback 카드의 "짧은 결과 요약" 한 줄이다. */
    private fun ProcessRunResult.summaryLine(locale: AppLocale): String =
        when (this) {
            is ProcessRunResult.Completed -> {
                val checkContract = contract
                if (checkContract == null) {
                    when (locale) {
                        AppLocale.Korean -> "JSON 결과를 해석하지 못했습니다 (exit=$exitCode)."
                        AppLocale.English -> "Couldn't parse the JSON result (exit=$exitCode)."
                    }
                } else {
                    val failed = checkContract.checks.firstOrNull { it.severity is HarnessCheckSeverity.Error }
                    val warned = checkContract.checks.firstOrNull { it.severity is HarnessCheckSeverity.Warn }
                    when {
                        failed != null -> failed.message
                        warned != null -> warned.message
                        checkContract.overall == HarnessOverallStatus.Ok -> when (locale) {
                            AppLocale.Korean -> "모든 점검을 통과했습니다."
                            AppLocale.English -> "All checks passed."
                        }
                        else -> when (locale) {
                            AppLocale.Korean -> "결과를 확인하세요."
                            AppLocale.English -> "Check the result."
                        }
                    }
                }
            }

            is ProcessRunResult.StartFailed -> reason
            is ProcessRunResult.TimedOut -> when (locale) {
                AppLocale.Korean -> "시간 초과 후 종료를 시도했습니다."
                AppLocale.English -> "Attempted to terminate after timing out."
            }
            is ProcessRunResult.Cancelled -> when (locale) {
                AppLocale.Korean -> "사용자 요청으로 취소했습니다."
                AppLocale.English -> "Cancelled at the user's request."
            }
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

internal fun HarnessCommandKind.displayLabel(locale: AppLocale = AppLocale.Korean): String =
    when (locale) {
        AppLocale.Korean -> when (this) {
            HarnessCommandKind.Doctor -> "연결 점검"
            HarnessCommandKind.ValidateOps -> "작업 준비 점검"
            HarnessCommandKind.OnboardProject -> "프로젝트 준비"
            HarnessCommandKind.Bootstrap -> "오늘 작업 시작"
            HarnessCommandKind.Planning -> "계획 실행"
            HarnessCommandKind.Replan -> "재계획 실행"
            HarnessCommandKind.ExecutionCode -> "코드 작업 실행"
            HarnessCommandKind.ExecutionDoc -> "문서 작업 실행"
            HarnessCommandKind.ClosureValidation -> "마감 검증 실행"
        }
        AppLocale.English -> when (this) {
            HarnessCommandKind.Doctor -> "Check connection"
            HarnessCommandKind.ValidateOps -> "Check readiness"
            HarnessCommandKind.OnboardProject -> "Prepare project"
            HarnessCommandKind.Bootstrap -> "Start today's work"
            HarnessCommandKind.Planning -> "Run planning"
            HarnessCommandKind.Replan -> "Run replanning"
            HarnessCommandKind.ExecutionCode -> "Run code task"
            HarnessCommandKind.ExecutionDoc -> "Run doc task"
            HarnessCommandKind.ClosureValidation -> "Run closure validation"
        }
    }

/**
 * 인라인 실행 feedback 카드의 "다시 점검/다시 검증" 재시도 버튼이 다시 호출할 typed
 * action이다. Bootstrap/Planning류는 각자의 화면 action 그룹에서 이미 재클릭 가능하므로 별도
 * 재시도 CTA를 중복 제공하지 않는다 — 연결 점검·작업 준비 점검만 명시적 재시도 문구를 붙인다.
 */
internal fun HarnessCommandKind.toRetryAction(): UiAction? =
    when (this) {
        HarnessCommandKind.Doctor -> UiAction.RunDoctor
        HarnessCommandKind.ValidateOps -> UiAction.RunOpsValidation
        else -> null
    }

internal fun HarnessCommandKind.retryLabel(locale: AppLocale = AppLocale.Korean): String =
    when (locale) {
        AppLocale.Korean -> when (this) {
            HarnessCommandKind.Doctor -> "다시 점검"
            HarnessCommandKind.ValidateOps -> "다시 검증"
            else -> "다시 실행"
        }
        AppLocale.English -> when (this) {
            HarnessCommandKind.Doctor -> "Check again"
            HarnessCommandKind.ValidateOps -> "Validate again"
            else -> "Run again"
        }
    }
