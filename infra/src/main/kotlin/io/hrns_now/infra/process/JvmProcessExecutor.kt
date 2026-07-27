package io.hrns_now.infra.process

import io.hrns_now.core.domain.model.ProcessCancellationToken
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * `JvmProcessExecutor`가 반환하는, Harness JSON 계약을 전혀 모르는 저수준 process 결과다.
 * JSON 파싱·contract 매핑은 [io.hrns_now.infra.process.PowerShellHarnessAdapter]의 책임이다.
 */
sealed interface RawProcessOutcome {
    data class Exited(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val stdoutTruncated: Boolean,
        val stderrTruncated: Boolean,
    ) : RawProcessOutcome

    data class StartFailed(val reason: String) : RawProcessOutcome
    data class TimedOut(val elapsedMillis: Long, val residualProcessDetected: Boolean) : RawProcessOutcome
    data class Cancelled(val residualProcessDetected: Boolean) : RawProcessOutcome
}

/**
 * `ProcessBuilder`에 executable/argument **목록**을 그대로 전달하는 저수준 실행기다(shell
 * 문자열 조립 금지). stdout/stderr를 동시에 drain해 deadlock을 피하고, UTF-8로 읽어 한글
 * 출력을 보존한다. timeout/취소 시 [WindowsProcessTreeTerminator]로 tree를 종료하고 잔존
 * 여부를 결과에 남긴다.
 */
class JvmProcessExecutor(
    private val processTreeTerminator: WindowsProcessTreeTerminator = WindowsProcessTreeTerminator(),
    private val maxOutputBytes: Int = 1_000_000,
    private val pollIntervalMillis: Long = 50L,
    private val outputCharset: Charset = resolveNativeConsoleCharset(),
) {
    suspend fun run(
        invocation: EncodedProcessInvocation,
        workingDirectory: Path?,
        timeoutMillis: Long,
        cancellationToken: ProcessCancellationToken,
    ): RawProcessOutcome = withContext(Dispatchers.IO) {
        val builder = ProcessBuilder(listOf(invocation.executable) + invocation.arguments)
        workingDirectory?.let { builder.directory(it.toFile()) }
        builder.redirectErrorStream(false)

        val process = try {
            builder.start()
        } catch (e: IOException) {
            return@withContext RawProcessOutcome.StartFailed(e.message ?: "process start failed")
        } catch (e: SecurityException) {
            return@withContext RawProcessOutcome.StartFailed(e.message ?: "process start denied")
        } catch (e: UnsupportedOperationException) {
            return@withContext RawProcessOutcome.StartFailed(e.message ?: "process start unsupported")
        }

        val stdoutCapture = OutputCapture(maxOutputBytes)
        val stderrCapture = OutputCapture(maxOutputBytes)
        val stdoutJob = launch { drain(process.inputStream, stdoutCapture) }
        val stderrJob = launch { drain(process.errorStream, stderrCapture) }

        val startedAt = System.currentTimeMillis()
        var exitCode: Int? = null
        while (true) {
            if (process.waitFor(pollIntervalMillis, TimeUnit.MILLISECONDS)) {
                exitCode = process.exitValue()
                break
            }
            if (cancellationToken.isCancelled()) {
                val residual = processTreeTerminator.terminate(process)
                withTimeoutOrNull(2_000L) { stdoutJob.join() }
                withTimeoutOrNull(2_000L) { stderrJob.join() }
                return@withContext RawProcessOutcome.Cancelled(residual)
            }
            val elapsed = System.currentTimeMillis() - startedAt
            if (elapsed >= timeoutMillis) {
                val residual = processTreeTerminator.terminate(process)
                withTimeoutOrNull(2_000L) { stdoutJob.join() }
                withTimeoutOrNull(2_000L) { stderrJob.join() }
                return@withContext RawProcessOutcome.TimedOut(elapsed, residual)
            }
        }

        stdoutJob.join()
        stderrJob.join()
        RawProcessOutcome.Exited(
            exitCode = requireNotNull(exitCode),
            stdout = stdoutCapture.text(),
            stderr = stderrCapture.text(),
            stdoutTruncated = stdoutCapture.truncated,
            stderrTruncated = stderrCapture.truncated,
        )
    }

    private fun drain(stream: InputStream, capture: OutputCapture) {
        stream.bufferedReader(outputCharset).use { reader ->
            val buffer = CharArray(4096)
            while (true) {
                val read = try {
                    reader.read(buffer)
                } catch (_: IOException) {
                    -1
                }
                if (read < 0) break
                capture.append(buffer, read)
            }
        }
    }
}

private class OutputCapture(private val maxChars: Int) {
    private val builder = StringBuilder()

    @Volatile
    var truncated: Boolean = false
        private set

    @Synchronized
    fun append(buffer: CharArray, len: Int) {
        if (truncated) return
        val remaining = maxChars - builder.length
        if (remaining <= 0) {
            truncated = true
            return
        }
        val toAppend = minOf(len, remaining)
        builder.append(buffer, 0, toAppend)
        if (toAppend < len) truncated = true
    }

    @Synchronized
    fun text(): String = builder.toString()
}

/**
 * 자식 프로세스로 redirect된(콘솔 없는) stdout/stderr는 Windows에서 시스템 OEM/ANSI 코드페이지로
 * 나온다 — JVM의 `file.encoding`(항상 UTF-8)과 다를 수 있다. `doctor.ps1 -Json`/
 * `validate-ops.ps1 -Json`의 실제 JSON 결과 자체는 `ConvertTo-Json`이 비-ASCII를 `\uXXXX`로
 * 이스케이프하므로 이 코드페이지와 무관하게 안전하지만, JSON 파싱이 실패했을 때 남기는
 * raw 진단 조각([RawProcessOutcome.Exited.stdout]/`stderr`)은 실제 콘솔 코드페이지로 읽어야
 * 한글 등 비-ASCII 원본이 깨지지 않는다.
 */
private fun resolveNativeConsoleCharset(): Charset {
    val name = System.getProperty("native.encoding") ?: System.getProperty("sun.jnu.encoding")
    return try {
        if (name.isNullOrBlank()) Charset.defaultCharset() else Charset.forName(name)
    } catch (_: Exception) {
        Charset.defaultCharset()
    }
}
