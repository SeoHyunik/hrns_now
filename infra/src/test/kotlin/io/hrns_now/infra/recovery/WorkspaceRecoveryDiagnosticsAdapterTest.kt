package io.hrns_now.infra.recovery

import io.hrns_now.core.domain.model.WorkspaceDay
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkspaceRecoveryDiagnosticsAdapterTest {
    private lateinit var workspaceRoot: Path
    private val date = LocalDate.of(2026, 7, 27)
    private val adapter = WorkspaceRecoveryDiagnosticsAdapter()

    @BeforeTest
    fun setUp() {
        workspaceRoot = Files.createTempDirectory("hrns-now-recovery-")
    }

    @AfterTest
    fun tearDown() {
        workspaceRoot.toFile().deleteRecursively()
    }

    private fun day() = WorkspaceDay(workspaceRoot, date)

    @Test
    fun `optional diagnostic files를 raw session id 없이 안전한 집계값으로 읽는다`() {
        val continuity = workspaceRoot.resolve("logs/claude-session-continuity/$date")
        Files.createDirectories(continuity)
        Files.writeString(
            continuity.resolve("planning.session-continuity.json"),
            """{"session_id":"secret-session-id","actual_resume_applied":true,"fresh_required":true}""",
        )
        Files.writeString(continuity.resolve("broken.session-continuity.json"), "{")
        val ledger = workspaceRoot.resolve("logs/usage-ledger")
        Files.createDirectories(ledger)
        Files.writeString(
            ledger.resolve("$date.jsonl"),
            """{"session_id_present":true,"request_thread_id":"raw-thread"}
{"session_id_present":false}
not-json
""",
        )
        Files.writeString(workspaceRoot.resolve("HARNESS_FAILURES.md"), "# failures\n### [2026-07-27] one\n### [2026-07-27] two\n")

        val diagnostics = adapter.read(day())

        assertEquals(1, diagnostics.continuity.recordCount)
        assertEquals(1, diagnostics.continuity.actualResumeAppliedCount)
        assertEquals(1, diagnostics.continuity.freshRequiredCount)
        assertEquals(1, diagnostics.continuity.unreadableCount)
        assertEquals(2, diagnostics.usageLedger.recordCount)
        assertEquals(1, diagnostics.usageLedger.sessionMetadataPresentCount)
        assertEquals(1, diagnostics.usageLedger.unreadableCount)
        assertEquals(2, diagnostics.failureHistory.entryCount)
        assertFalse(diagnostics.toString().contains("secret-session-id"))
        assertFalse(diagnostics.toString().contains("raw-thread"))
    }

    @Test
    fun `optional diagnostics가 없으면 CTA에 사용할 수 없는 빈 요약을 반환한다`() {
        val diagnostics = adapter.read(day())

        assertFalse(diagnostics.continuity.available)
        assertFalse(diagnostics.usageLedger.available)
        assertFalse(diagnostics.failureHistory.available)
        assertTrue(diagnostics.toString().isNotBlank())
    }
}
