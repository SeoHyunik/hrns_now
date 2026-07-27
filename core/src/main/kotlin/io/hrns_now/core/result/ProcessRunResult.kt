package io.hrns_now.core.result

/**
 * `doctor.ps1 -Json`/`validate-ops.ps1 -Json`의 `overall` 값이다. 알려지지 않은 값은
 * `Unknown(raw)`으로 흡수하고 실행 CTA는 fail-closed로 처리한다(OCP, `Unknown(raw)` 원칙).
 */
sealed interface HarnessOverallStatus {
    data object Ok : HarnessOverallStatus
    data object Warn : HarnessOverallStatus
    data object Fail : HarnessOverallStatus
    data class Unknown(val raw: String) : HarnessOverallStatus
}

/** `checks[].severity` 값이다. */
sealed interface HarnessCheckSeverity {
    data object Info : HarnessCheckSeverity
    data object Warn : HarnessCheckSeverity
    data object Error : HarnessCheckSeverity
    data class Unknown(val raw: String) : HarnessCheckSeverity
}

/**
 * `checks[]`의 한 항목이다. `message`는 Harness 쪽 `Protect-HarnessDiagnosticMessage`가 이미
 * masking한 값이지만, HRNS-NOW 쪽에서도 UI에 전달하기 전 [io.hrns_now.infra.security.SecretMasker]로
 * 한 번 더 방어적으로 masking한다(§ decorator).
 */
data class HarnessCheckResult(
    val id: String,
    val severity: HarnessCheckSeverity,
    val message: String,
)

/**
 * `{contract_version, overall, checks}` JSON 계약을 그대로 표현한 typed 값이다(Phase 2).
 */
data class HarnessDiagnosticContract(
    val contractVersion: String,
    val overall: HarnessOverallStatus,
    val checks: List<HarnessCheckResult>,
)

/**
 * [io.hrns_now.core.port.HarnessRunnerPort]가 반환하는 process 실행 결과다. start failure,
 * (exit code와 무관한) 정상 종료, timeout, cancel을 각각 구분한다 — 하나의 nullable/boolean으로
 * 뭉치지 않는다.
 *
 * `stdout` 성공 문구만으로 완료를 판단하지 않는다: `Completed`는 JSON 계약이 파싱됐는지
 * ([contract]가 null이 아닌지)와 exit code를 모두 담고, 둘 중 하나만으로 성공을 주장하지 않는다.
 */
sealed interface ProcessRunResult {
    /**
     * 프로세스가 exit code를 반환하며 종료했다. `contract`가 null이면 stdout이 JSON 계약으로
     * 파싱되지 않은 것이다(예: PowerShell 자체 오류로 JSON이 전혀 출력되지 않음) — 이 경우
     * [rawOutputSnippet]에 masking·truncate된 진단용 조각만 남긴다.
     */
    data class Completed(
        val exitCode: Int,
        val contract: HarnessDiagnosticContract?,
        val rawOutputSnippet: String?,
        val stdoutTruncated: Boolean,
        val stderrTruncated: Boolean,
    ) : ProcessRunResult

    /** 프로세스 자체를 시작하지 못했다(실행 파일 없음, 권한 없음 등). */
    data class StartFailed(val reason: String) : ProcessRunResult

    /**
     * timeout으로 강제 종료를 시도했다. [residualProcessDetected]가 true면 종료 시도 뒤에도
     * process/자식 process가 여전히 남아있다고 확인된 것이다 — 이 경우 UI는 취소가 완전히
     * 성공했다고 주장하지 않는다.
     */
    data class TimedOut(val elapsedMillis: Long, val residualProcessDetected: Boolean) : ProcessRunResult

    /** 사용자가 명시적으로 취소했다. */
    data class Cancelled(val residualProcessDetected: Boolean) : ProcessRunResult
}
