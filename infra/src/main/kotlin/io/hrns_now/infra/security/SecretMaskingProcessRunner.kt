package io.hrns_now.infra.security

import io.hrns_now.core.domain.model.HarnessCommand
import io.hrns_now.core.domain.model.ProcessCancellationToken
import io.hrns_now.core.port.HarnessRunnerPort
import io.hrns_now.core.result.HarnessDiagnosticContract
import io.hrns_now.core.result.ProcessRunResult
import java.time.Duration

/**
 * `Raw Process → Output Encoding → Secret Masking → Metrics → UI Delivery` 순서의 decorator다
 * (`doc/hrns_now_design_pattern.md` §12). 원본 [delegate] 구현을 바꾸지 않고 UI로 전달되기
 * 전 마지막 방어선으로 masking을 적용한다.
 */
class SecretMaskingProcessRunner(
    private val delegate: HarnessRunnerPort,
    private val masker: SecretMasker = SecretMasker(),
) : HarnessRunnerPort {

    override suspend fun execute(
        command: HarnessCommand,
        timeout: Duration,
        cancellationToken: ProcessCancellationToken,
    ): ProcessRunResult = maskResult(delegate.execute(command, timeout, cancellationToken))

    private fun maskResult(result: ProcessRunResult): ProcessRunResult =
        when (result) {
            is ProcessRunResult.Completed -> result.copy(
                contract = result.contract?.let(::maskContract),
                rawOutputSnippet = result.rawOutputSnippet?.let(masker::mask),
            )

            is ProcessRunResult.StartFailed -> result.copy(reason = masker.mask(result.reason))
            is ProcessRunResult.TimedOut -> result
            is ProcessRunResult.Cancelled -> result
        }

    private fun maskContract(contract: HarnessDiagnosticContract): HarnessDiagnosticContract =
        contract.copy(checks = contract.checks.map { it.copy(message = masker.mask(it.message)) })
}
