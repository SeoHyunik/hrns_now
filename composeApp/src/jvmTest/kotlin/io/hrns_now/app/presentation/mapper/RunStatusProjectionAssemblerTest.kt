package io.hrns_now.app.presentation.mapper

import io.hrns_now.app.presentation.viewmodel.HarnessRunViewState
import io.hrns_now.core.domain.model.HarnessCommandKind
import io.hrns_now.core.domain.model.LockPayload
import io.hrns_now.core.domain.model.LockState
import io.hrns_now.core.domain.model.ProjectId
import io.hrns_now.core.port.LockInspection
import io.hrns_now.core.result.HarnessCheckResult
import io.hrns_now.core.result.HarnessCheckSeverity
import io.hrns_now.core.result.HarnessDiagnosticContract
import io.hrns_now.core.result.HarnessOverallStatus
import io.hrns_now.core.result.ProcessRunResult
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RunStatusProjectionAssemblerTest {

    private val assembler = RunStatusProjectionAssembler()
    private val now = Instant.parse("2026-07-27T10:00:00Z")

    @Test
    fun `아직 실행한 적 없으면 대기 메시지를 보여준다`() {
        val projection = assembler.assemble(HarnessRunViewState(), lockInspection = null, now = now)

        assertTrue(projection.consoleLines.single().contains("대기"))
        assertFalse(projection.cancelEnabled)
        assertFalse(projection.forceReleaseEnabled)
    }

    @Test
    fun `Completed 결과의 check message를 severity tag와 함께 그대로 보여준다`() {
        val contract = HarnessDiagnosticContract(
            contractVersion = "1.0",
            overall = HarnessOverallStatus.Warn,
            checks = listOf(
                HarnessCheckResult("check_001", HarnessCheckSeverity.Warn, "template placeholder remains"),
            ),
        )
        val runView = HarnessRunViewState(
            lastCommand = HarnessCommandKind.ValidateOps,
            isRunning = false,
            runStartedAt = now,
            lastResult = ProcessRunResult.Completed(0, contract, null, false, false),
        )

        val projection = assembler.assemble(runView, lockInspection = null, now = now)

        assertTrue(projection.consoleLines.single().contains("template placeholder remains"))
        assertTrue(projection.consoleLines.single().startsWith("[WARN]"))
        assertEquals(1, projection.failureChips.size)
    }

    @Test
    fun `실행 중에는 취소가 가능하고 완료 상태 메시지 대신 진행 중 문구를 보여준다`() {
        val runView = HarnessRunViewState(
            lastCommand = HarnessCommandKind.Doctor,
            isRunning = true,
            runStartedAt = now,
            lastResult = null,
        )

        val projection = assembler.assemble(runView, lockInspection = null, now = now)

        assertTrue(projection.cancelEnabled)
        assertTrue(projection.consoleLines.single().contains("실행 중"))
    }

    @Test
    fun `TimedOut Cancelled는 raw 프로세스 정보 없이 typed 문구만 보여준다`() {
        val timedOut = HarnessRunViewState(
            lastCommand = HarnessCommandKind.Doctor,
            lastResult = ProcessRunResult.TimedOut(5000L, residualProcessDetected = true),
        )
        val cancelled = HarnessRunViewState(
            lastCommand = HarnessCommandKind.Doctor,
            lastResult = ProcessRunResult.Cancelled(residualProcessDetected = false),
        )

        val timedOutProjection = assembler.assemble(timedOut, lockInspection = null, now = now)
        val cancelledProjection = assembler.assemble(cancelled, lockInspection = null, now = now)

        assertTrue(timedOutProjection.consoleLines.any { it.contains("시간 초과") })
        assertTrue(timedOutProjection.consoleLines.any { it.contains("경고") })
        assertTrue(cancelledProjection.consoleLines.any { it.contains("취소됨") })
    }

    @Test
    fun `lock 조회 결과는 PID command heartbeat 나이만 보여주고 강제 해제를 활성화한다`() {
        val payload = LockPayload(
            projectId = ProjectId("p1"),
            date = LocalDate.of(2026, 7, 27),
            pid = 4242L,
            commandKind = HarnessCommandKind.Doctor,
            acquiredAt = now.minusSeconds(120),
            heartbeatAt = now.minusSeconds(5),
        )
        val inspection = LockInspection(payload, LockState.Active)

        val projection = assembler.assemble(HarnessRunViewState(), inspection, now)

        assertTrue(projection.forceReleaseEnabled)
        val ownerRow = projection.stageDetailRows.single { it.first == "잠금 소유자" }
        assertTrue(ownerRow.second.contains("4242"))
        assertTrue(ownerRow.second.contains("Doctor"))
        assertFalse(ownerRow.second.contains("stale"), "Active 상태에서는 stale 표시가 없어야 한다")
    }

    @Test
    fun `stale로 판정된 lock은 표시에 stale 힌트를 남긴다`() {
        val payload = LockPayload(
            projectId = ProjectId("p1"),
            date = LocalDate.of(2026, 7, 27),
            pid = 4242L,
            commandKind = HarnessCommandKind.Doctor,
            acquiredAt = now.minusSeconds(600),
            heartbeatAt = now.minusSeconds(600),
        )
        val inspection = LockInspection(payload, LockState.Stale)

        val projection = assembler.assemble(HarnessRunViewState(), inspection, now)

        val ownerRow = projection.stageDetailRows.single { it.first == "잠금 소유자" }
        assertTrue(ownerRow.second.contains("stale"))
    }

    @Test
    fun `stage chip은 Doctor Ops Validation 두 항목을 항상 보여준다`() {
        val projection = assembler.assemble(HarnessRunViewState(), null, now)

        assertTrue(projection.stages.any { it.label == "Doctor" })
        assertTrue(projection.stages.any { it.label == "Ops Validation" })
    }
    @Test
    fun `외부 실행 가능성은 새 실행 보류 안내로 먼저 표시한다`() {
        val projection = assembler.assemble(
            runView = HarnessRunViewState(),
            lockInspection = null,
            now = now,
            externalExecutionSuspected = true,
        )

        assertTrue(projection.consoleLines.first().contains("외부 실행 가능성"))
        assertTrue(projection.consoleLines.first().contains("새 실행을 보류"))
    }
}
