package io.hrns_now.infra.git

import io.hrns_now.core.domain.model.RepositoryStatus
import io.hrns_now.core.port.GitStatusPort
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * `git status --short`만 실행하는 read-only [GitStatusPort] 구현이다. git이 없거나, 대상이
 * repository가 아니거나, 명령이 실패/시간초과하면 [RepositoryStatus.Unknown]으로 fail-soft한다 —
 * git을 쓰지 않는 프로젝트도 있을 수 있으므로 이 경우를 마감 차단 사유로 취급하지 않는다.
 *
 * `repositoryRoot`에 `.git`이 없으면 git을 아예 실행하지 않는다 — git은 기본적으로 상위
 * 디렉터리를 탐색하므로, 그렇지 않으면 `repositoryRoot`가 (홈 디렉터리 등) 더 큰 ambient
 * repository 안에 우연히 위치할 때 관련 없는 조상 repository의 상태를 보고할 수 있다.
 */
class CommandLineGitStatusAdapter(
    private val gitExecutable: String = "git",
    private val timeoutSeconds: Long = 10,
) : GitStatusPort {

    override fun read(repositoryRoot: Path): RepositoryStatus {
        // A linked worktree has a `.git` file rather than a directory. Either form
        // establishes that this exact root belongs to a repository; without it git
        // would walk into an unrelated ambient parent repository.
        if (!Files.exists(repositoryRoot.resolve(".git"))) {
            return RepositoryStatus.Unknown
        }
        val process = try {
            ProcessBuilder(gitExecutable, "status", "--short")
                .directory(repositoryRoot.toFile())
                .start()
        } catch (_: IOException) {
            return RepositoryStatus.Unknown
        } catch (_: SecurityException) {
            return RepositoryStatus.Unknown
        }
        val streamExecutor = Executors.newFixedThreadPool(2)
        return try {
            val stdout = streamExecutor.submit(Callable {
                process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            })
            val stderr = streamExecutor.submit(Callable {
                process.errorStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            })
            val exited = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!exited) {
                process.destroyForcibly()
                process.waitFor(1, TimeUnit.SECONDS)
                return RepositoryStatus.Unknown
            }
            val stdoutText = stdout.get()
            stderr.get()
            if (process.exitValue() != 0) {
                return RepositoryStatus.Unknown
            }
            val changedPaths = stdoutText.lineSequence()
                .filter { it.isNotBlank() }
                .map { line -> line.drop(minOf(3, line.length)).trim() }
                .filter { it.isNotBlank() }
                .toList()
            if (changedPaths.isEmpty()) RepositoryStatus.Clean else RepositoryStatus.Dirty(changedPaths)
        } catch (_: IOException) {
            process.destroyForcibly()
            RepositoryStatus.Unknown
        } catch (_: InterruptedException) {
            process.destroyForcibly()
            Thread.currentThread().interrupt()
            RepositoryStatus.Unknown
        } catch (_: ExecutionException) {
            process.destroyForcibly()
            RepositoryStatus.Unknown
        } finally {
            streamExecutor.shutdownNow()
        }
    }
}
