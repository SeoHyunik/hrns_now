package io.hrns_now.infra.git

import io.hrns_now.core.domain.model.RepositoryStatus
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 실제 `git` 프로세스로 [CommandLineGitStatusAdapter]가 read-only임을 검증한다 — 이 테스트
 * 파일 자체의 fixture 준비(`git init`/파일 생성)만 저장소 상태를 바꾸고, adapter 호출은
 * 어떤 git 쓰기 명령도 실행하지 않는다.
 */
class CommandLineGitStatusAdapterTest {

    private lateinit var tempRoot: Path
    private val adapter = CommandLineGitStatusAdapter()

    @BeforeTest
    fun setUp() {
        tempRoot = Files.createTempDirectory("hrns-now-git-status-")
    }

    @AfterTest
    fun tearDown() {
        tempRoot.toFile().deleteRecursively()
    }

    private fun runGit(vararg args: String) = runGitAt(tempRoot, *args)

    private fun runGitAt(directory: Path, vararg args: String) {
        val process = ProcessBuilder(listOf("git") + args)
            .directory(directory.toFile())
            .start()
        process.waitFor()
    }

    @Test
    fun `git repository가 아니면 Unknown이다`() {
        assertEquals(RepositoryStatus.Unknown, adapter.read(tempRoot))
    }

    @Test
    fun `상위 ambient repository를 탐색하지 않고 지정한 root가 repository인지 확인한다`() {
        runGit("init")
        Files.writeString(tempRoot.resolve("parent-change.txt"), "dirty")
        val nonRepositoryChild = Files.createDirectory(tempRoot.resolve("not-a-repository"))

        assertEquals(RepositoryStatus.Unknown, adapter.read(nonRepositoryChild))
    }

    @Test
    fun `초기화만 된 repository는 Clean이다`() {
        runGit("init")

        assertEquals(RepositoryStatus.Clean, adapter.read(tempRoot))
    }

    @Test
    fun `추적되지 않은 파일이 있으면 Dirty와 경로를 반환한다`() {
        runGit("init")
        Files.writeString(tempRoot.resolve("untracked.txt"), "hello")

        val status = assertIs<RepositoryStatus.Dirty>(adapter.read(tempRoot))
        assertTrue(status.changedPaths.any { it.contains("untracked.txt") })
    }

    @Test
    fun `존재하지 않는 git 실행 파일은 Unknown으로 fail-soft한다`() {
        val brokenAdapter = CommandLineGitStatusAdapter(gitExecutable = "definitely-not-a-real-git-binary-xyz")

        assertEquals(RepositoryStatus.Unknown, brokenAdapter.read(tempRoot))
    }
}
