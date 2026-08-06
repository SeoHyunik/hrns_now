package io.hrns_now.infra.bridge

import io.hrns_now.core.domain.model.BridgeFileState
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `enter-project.ps1`이 만드는 repository bridge 3-file을 read-only로만 탐지한다.
 * 이 probe는 파일을 쓰거나 지우지 않는다 — 오직 존재 여부만 관측한다.
 */
class RepositoryBridgeProbeTest {

    private lateinit var repositoryRoot: Path
    private val probe = RepositoryBridgeProbe()

    @BeforeTest
    fun setUp() {
        repositoryRoot = Files.createTempDirectory("hrns-now-bridge-probe-")
    }

    @AfterTest
    fun tearDown() {
        repositoryRoot.toFile().deleteRecursively()
    }

    private fun writeBridgeFile(relativePath: String) {
        val target = repositoryRoot.resolve(relativePath)
        Files.createDirectories(target.parent)
        Files.writeString(target, "stub")
    }

    @Test
    fun `bridge 3-file이 모두 있으면 isReady는 true다`() {
        writeBridgeFile(".claude/settings.local.json")
        writeBridgeFile(".claude/CLAUDE.md")
        writeBridgeFile("tools/run-cycle.ps1")

        val summary = probe.probe(repositoryRoot)

        assertTrue(summary.isReady)
        assertEquals(BridgeFileState.Ready, summary.settingsLocalJson)
        assertEquals(BridgeFileState.Ready, summary.projectClaudeMd)
        assertEquals(BridgeFileState.Ready, summary.toolsRunCycle)
    }

    @Test
    fun `bridge 파일이 하나라도 없으면 isReady는 false다`() {
        writeBridgeFile(".claude/settings.local.json")
        writeBridgeFile(".claude/CLAUDE.md")
        // tools/run-cycle.ps1 은 일부러 만들지 않는다.

        val summary = probe.probe(repositoryRoot)

        assertFalse(summary.isReady)
        assertEquals(BridgeFileState.Ready, summary.settingsLocalJson)
        assertEquals(BridgeFileState.Ready, summary.projectClaudeMd)
        assertEquals(BridgeFileState.Missing, summary.toolsRunCycle)
    }

    @Test
    fun `bridge 파일이 전혀 없어도 새로 만들지 않는다`() {
        val summary = probe.probe(repositoryRoot)

        assertFalse(summary.isReady)
        assertEquals(BridgeFileState.Missing, summary.settingsLocalJson)
        assertEquals(BridgeFileState.Missing, summary.projectClaudeMd)
        assertEquals(BridgeFileState.Missing, summary.toolsRunCycle)
        assertFalse(Files.exists(repositoryRoot.resolve(".claude/settings.local.json")))
        assertFalse(Files.exists(repositoryRoot.resolve(".claude/CLAUDE.md")))
        assertFalse(Files.exists(repositoryRoot.resolve("tools/run-cycle.ps1")))
    }

    @Test
    fun `디렉터리가 파일명과 같아도 Ready로 오탐하지 않는다`() {
        Files.createDirectories(repositoryRoot.resolve(".claude/settings.local.json"))

        val summary = probe.probe(repositoryRoot)

        assertEquals(BridgeFileState.Missing, summary.settingsLocalJson)
    }
}
