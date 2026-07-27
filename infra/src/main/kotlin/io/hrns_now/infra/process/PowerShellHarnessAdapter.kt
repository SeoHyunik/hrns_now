package io.hrns_now.infra.process

import io.hrns_now.core.domain.model.HarnessCommand
import io.hrns_now.core.domain.model.ProcessCancellationToken
import io.hrns_now.core.port.HarnessRunnerPort
import io.hrns_now.core.result.HarnessCheckResult
import io.hrns_now.core.result.HarnessCheckSeverity
import io.hrns_now.core.result.HarnessDiagnosticContract
import io.hrns_now.core.result.HarnessOverallStatus
import io.hrns_now.core.result.ProcessRunResult
import java.nio.file.Path
import java.time.Duration
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** `doctor.ps1 -Json`/`validate-ops.ps1 -Json` stdout의 외부 JSON 모양이다(ACL). */
@Serializable
internal data class HarnessDiagnosticContractDto(
    @SerialName("contract_version") val contractVersion: String? = null,
    val overall: String? = null,
    val checks: List<HarnessCheckDto>? = null,
)

@Serializable
internal data class HarnessCheckDto(
    val id: String? = null,
    val severity: String? = null,
    val message: String? = null,
)

internal fun HarnessDiagnosticContractDto.toDomain(): HarnessDiagnosticContract? {
    val version = contractVersion?.takeIf(String::isNotBlank) ?: return null
    val overallRaw = overall?.takeIf(String::isNotBlank) ?: return null
    val overallStatus = when (overallRaw.lowercase()) {
        "ok" -> HarnessOverallStatus.Ok
        "warn" -> HarnessOverallStatus.Warn
        "fail" -> HarnessOverallStatus.Fail
        else -> HarnessOverallStatus.Unknown(overallRaw)
    }
    val checkResults = checks?.map { it.toDomain() ?: return null } ?: return null
    return HarnessDiagnosticContract(contractVersion = version, overall = overallStatus, checks = checkResults)
}

internal fun HarnessCheckDto.toDomain(): HarnessCheckResult? {
    val checkId = id?.takeIf(String::isNotBlank) ?: return null
    val severityRaw = severity?.takeIf(String::isNotBlank)
    val sev = when (severityRaw?.lowercase()) {
        "info" -> HarnessCheckSeverity.Info
        "warn" -> HarnessCheckSeverity.Warn
        "error" -> HarnessCheckSeverity.Error
        null -> HarnessCheckSeverity.Unknown("")
        else -> HarnessCheckSeverity.Unknown(severityRaw)
    }
    return HarnessCheckResult(id = checkId, severity = sev, message = message ?: "")
}

/**
 * [HarnessRunnerPort]의 실제 구현이다 — [HarnessCommandEncoder]로 인자를 만들고
 * [JvmProcessExecutor]로 실행한 뒤, stdout을 Phase 2 JSON 계약으로 파싱해 typed
 * [ProcessRunResult]로 매핑한다. stdout의 성공 문구만으로 완료를 판단하지 않는다 — JSON이
 * 파싱되지 않으면 `contract`를 `null`로 남기고 masking된 조각만 [ProcessRunResult.Completed.rawOutputSnippet]에
 * 담는다.
 */
class PowerShellHarnessAdapter(
    private val encoder: HarnessCommandEncoder = HarnessCommandEncoder(),
    private val executor: JvmProcessExecutor = JvmProcessExecutor(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : HarnessRunnerPort {

    override suspend fun execute(
        command: HarnessCommand,
        timeout: Duration,
        cancellationToken: ProcessCancellationToken,
    ): ProcessRunResult {
        val invocation = encoder.encode(command)
        val workingDirectory = workingDirectoryFor(command)
        return when (
            val outcome = executor.run(
                invocation = invocation,
                workingDirectory = workingDirectory,
                timeoutMillis = timeout.toMillis(),
                cancellationToken = cancellationToken,
            )
        ) {
            is RawProcessOutcome.Exited -> mapExited(outcome)
            is RawProcessOutcome.StartFailed -> ProcessRunResult.StartFailed(outcome.reason)
            is RawProcessOutcome.TimedOut ->
                ProcessRunResult.TimedOut(outcome.elapsedMillis, outcome.residualProcessDetected)
            is RawProcessOutcome.Cancelled -> ProcessRunResult.Cancelled(outcome.residualProcessDetected)
        }
    }

    private fun workingDirectoryFor(command: HarnessCommand): Path =
        when (command) {
            is HarnessCommand.Doctor -> command.kitRoot
            is HarnessCommand.ValidateOps -> command.kitRoot
        }

    private fun mapExited(outcome: RawProcessOutcome.Exited): ProcessRunResult.Completed {
        val contract = parseContract(outcome.stdout)
        val rawSnippet = if (contract == null) buildSnippet(outcome.stdout, outcome.stderr) else null
        return ProcessRunResult.Completed(
            exitCode = outcome.exitCode,
            contract = contract,
            rawOutputSnippet = rawSnippet,
            stdoutTruncated = outcome.stdoutTruncated,
            stderrTruncated = outcome.stderrTruncated,
        )
    }

    private fun parseContract(stdout: String): HarnessDiagnosticContract? {
        val trimmed = stdout.trim()
        if (trimmed.isEmpty()) return null
        return try {
            json.decodeFromString(HarnessDiagnosticContractDto.serializer(), trimmed).toDomain()
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun buildSnippet(stdout: String, stderr: String): String? {
        val combined = listOf(stdout, stderr).filter(String::isNotBlank).joinToString("\n---\n")
        if (combined.isBlank()) return null
        return combined.take(2_000)
    }
}
