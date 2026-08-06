package io.hrns_now.core.port

import io.hrns_now.core.domain.model.HarnessCommand
import io.hrns_now.core.domain.model.ProcessCancellationToken
import io.hrns_now.core.result.ProcessRunResult
import java.time.Duration

/**
 * Harness PowerShell command 실행 결과에 필요한 최소 contract만 노출하는 port다
 * (`doc/hrns_now_design_pattern.md` §3.2). `ProcessBuilder`, Windows API, JSON
 * 파싱 라이브러리를 참조하지 않는다 — 이 port 자체는 순수 계약이다.
 */
fun interface HarnessRunnerPort {
    suspend fun execute(
        command: HarnessCommand,
        timeout: Duration,
        cancellationToken: ProcessCancellationToken,
    ): ProcessRunResult
}
