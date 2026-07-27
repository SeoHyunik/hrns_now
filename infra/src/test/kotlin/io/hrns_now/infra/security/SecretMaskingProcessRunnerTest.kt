package io.hrns_now.infra.security

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
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SecretMaskingProcessRunnerTest {

    private val command = HarnessCommand.Doctor(
        kitRoot = Path.of("D:/harness-kit"),
        workspaceRoot = null,
        projectRoot = null,
        date = LocalDate.of(2026, 7, 27),
    )

    @Test
    fun `Completed의 check message에 있는 secret을 masking한다`() = runTest {
        val delegate = HarnessRunnerPort { _, _, _ ->
            ProcessRunResult.Completed(
                exitCode = 0,
                contract = HarnessDiagnosticContract(
                    contractVersion = "1.0",
                    overall = HarnessOverallStatus.Fail,
                    checks = listOf(
                        HarnessCheckResult("check_001", HarnessCheckSeverity.Error, "token=abcd1234 leaked"),
                    ),
                ),
                rawOutputSnippet = null,
                stdoutTruncated = false,
                stderrTruncated = false,
            )
        }
        val masking = SecretMaskingProcessRunner(delegate)

        val result = masking.execute(command, Duration.ofSeconds(5), ProcessCancellationToken()) as ProcessRunResult.Completed

        val message = result.contract!!.checks.single().message
        assertFalse(message.contains("abcd1234"))
        assertEquals("token=[REDACTED] leaked", message)
    }

    @Test
    fun `rawOutputSnippet도 masking한다`() = runTest {
        val delegate = HarnessRunnerPort { _, _, _ ->
            ProcessRunResult.Completed(
                exitCode = 1,
                contract = null,
                rawOutputSnippet = "Bearer abcdefgh12345678 was rejected",
                stdoutTruncated = false,
                stderrTruncated = false,
            )
        }
        val masking = SecretMaskingProcessRunner(delegate)

        val result = masking.execute(command, Duration.ofSeconds(5), ProcessCancellationToken()) as ProcessRunResult.Completed

        assertFalse(result.rawOutputSnippet!!.contains("abcdefgh12345678"))
    }

    @Test
    fun `StartFailed의 reason도 masking한다`() = runTest {
        val delegate = HarnessRunnerPort { _, _, _ -> ProcessRunResult.StartFailed("token=abcd1234 invalid") }
        val masking = SecretMaskingProcessRunner(delegate)

        val result = masking.execute(command, Duration.ofSeconds(5), ProcessCancellationToken()) as ProcessRunResult.StartFailed

        assertFalse(result.reason.contains("abcd1234"))
    }

    @Test
    fun `TimedOut과 Cancelled는 원본 그대로 통과시킨다`() = runTest {
        val timedOutDelegate = HarnessRunnerPort { _, _, _ -> ProcessRunResult.TimedOut(5000L, residualProcessDetected = true) }
        val cancelledDelegate = HarnessRunnerPort { _, _, _ -> ProcessRunResult.Cancelled(residualProcessDetected = false) }

        val timedOut = SecretMaskingProcessRunner(timedOutDelegate)
            .execute(command, Duration.ofSeconds(5), ProcessCancellationToken())
        val cancelled = SecretMaskingProcessRunner(cancelledDelegate)
            .execute(command, Duration.ofSeconds(5), ProcessCancellationToken())

        assertEquals(ProcessRunResult.TimedOut(5000L, true), timedOut)
        assertEquals(ProcessRunResult.Cancelled(false), cancelled)
    }
}
