package io.hrns_now.infra.process

import io.hrns_now.core.domain.model.ProcessCancellationToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 실제 `powershell.exe` 자식 프로세스를 기동해 [JvmProcessExecutor]를 검증한다(mock이 아니라
 * real child fixture, `doc/claude_prompts/phase3-process-adapter-lock.md` §2).
 */
class JvmProcessExecutorTest {

    private val executor = JvmProcessExecutor(pollIntervalMillis = 20L)

    private fun psInvocation(script: String) = EncodedProcessInvocation(
        executable = "powershell.exe",
        arguments = listOf("-NoProfile", "-Command", script),
    )

    @Test
    fun `정상 종료와 exit code를 그대로 반환한다`() = runBlocking {
        withTimeout(15_000) {
            val outcome = executor.run(
                psInvocation("exit 0"),
                workingDirectory = null,
                timeoutMillis = 10_000,
                cancellationToken = ProcessCancellationToken(),
            )
            val exited = assertIs<RawProcessOutcome.Exited>(outcome)
            assertEquals(0, exited.exitCode)
        }
    }

    @Test
    fun `non-zero exit code도 Exited로 그대로 반환한다`() = runBlocking {
        withTimeout(15_000) {
            val outcome = executor.run(
                psInvocation("exit 7"),
                workingDirectory = null,
                timeoutMillis = 10_000,
                cancellationToken = ProcessCancellationToken(),
            )
            val exited = assertIs<RawProcessOutcome.Exited>(outcome)
            assertEquals(7, exited.exitCode)
        }
    }

    @Test
    fun `UTF-8 한글 stdout을 보존한다`() = runBlocking {
        withTimeout(15_000) {
            val outcome = executor.run(
                psInvocation("Write-Output '한글 테스트 출력'"),
                workingDirectory = null,
                timeoutMillis = 10_000,
                cancellationToken = ProcessCancellationToken(),
            )
            val exited = assertIs<RawProcessOutcome.Exited>(outcome)
            assertTrue(exited.stdout.contains("한글 테스트 출력"), exited.stdout)
        }
    }

    @Test
    fun `stdout stderr 동시 대량 출력에서도 deadlock 없이 끝난다`() = runBlocking {
        withTimeout(20_000) {
            val script = "for(\$i=0; \$i -lt 4000; \$i++) { Write-Output ('out-' + \$i); [Console]::Error.WriteLine('err-' + \$i) }"
            val outcome = executor.run(
                psInvocation(script),
                workingDirectory = null,
                timeoutMillis = 15_000,
                cancellationToken = ProcessCancellationToken(),
            )
            val exited = assertIs<RawProcessOutcome.Exited>(outcome)
            assertEquals(0, exited.exitCode)
            assertTrue(exited.stdout.contains("out-3999"))
            assertTrue(exited.stderr.contains("err-3999"))
        }
    }

    @Test
    fun `존재하지 않는 실행 파일은 StartFailed다`(): Unit = runBlocking {
        withTimeout(10_000) {
            val invocation = EncodedProcessInvocation(
                executable = "definitely-not-a-real-executable-xyz.exe",
                arguments = emptyList(),
            )
            val outcome = executor.run(
                invocation,
                workingDirectory = null,
                timeoutMillis = 5_000,
                cancellationToken = ProcessCancellationToken(),
            )
            assertIs<RawProcessOutcome.StartFailed>(outcome)
        }
    }

    @Test
    fun `timeout이면 강제 종료를 시도하고 잔존 여부를 typed로 남긴다`() = runBlocking {
        withTimeout(15_000) {
            val outcome = executor.run(
                psInvocation("Start-Sleep -Seconds 30"),
                workingDirectory = null,
                timeoutMillis = 500,
                cancellationToken = ProcessCancellationToken(),
            )
            val timedOut = assertIs<RawProcessOutcome.TimedOut>(outcome)
            assertTrue(timedOut.elapsedMillis in 400..10_000)
            assertTrue(!timedOut.residualProcessDetected, "종료 시도 후에도 프로세스가 남아있으면 안 된다")
        }
    }

    @Test
    fun `취소 신호를 받으면 강제 종료를 시도하고 잔존 여부를 typed로 남긴다`() = runBlocking {
        withTimeout(15_000) {
            val token = ProcessCancellationToken()
            val cancelJob = launch {
                delay(300)
                token.requestCancel()
            }
            val outcome = executor.run(
                psInvocation("Start-Sleep -Seconds 30"),
                workingDirectory = null,
                timeoutMillis = 20_000,
                cancellationToken = token,
            )
            cancelJob.join()
            val cancelled = assertIs<RawProcessOutcome.Cancelled>(outcome)
            assertTrue(!cancelled.residualProcessDetected, "취소 시도 후에도 프로세스가 남아있으면 안 된다")
        }
    }

    @Test
    fun `stdout 상한을 넘으면 truncate 표시를 남긴다`() = runBlocking {
        withTimeout(15_000) {
            val smallCapExecutor = JvmProcessExecutor(maxOutputBytes = 100, pollIntervalMillis = 20L)
            val script = "for(\$i=0; \$i -lt 200; \$i++) { Write-Output ('x' * 20) }"
            val outcome = smallCapExecutor.run(
                psInvocation(script),
                workingDirectory = null,
                timeoutMillis = 10_000,
                cancellationToken = ProcessCancellationToken(),
            )
            val exited = assertIs<RawProcessOutcome.Exited>(outcome)
            assertTrue(exited.stdoutTruncated)
            assertTrue(exited.stdout.length <= 100)
        }
    }
}
