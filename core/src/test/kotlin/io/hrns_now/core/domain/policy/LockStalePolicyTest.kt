package io.hrns_now.core.domain.policy

import io.hrns_now.core.domain.model.HarnessCommandKind
import io.hrns_now.core.domain.model.LockPayload
import io.hrns_now.core.domain.model.LockState
import io.hrns_now.core.domain.model.ProjectId
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `doc/claude_prompts/phase3-process-adapter-lock.md` §4의 stale 결정표를 표 기반으로
 * 검증한다. `Stale`은 PID 미존재와 heartbeat 만료를 **함께** 만족해야만 나온다.
 */
class LockStalePolicyTest {

    private val policy = LockStalePolicy(heartbeatTimeout = Duration.ofSeconds(30))
    private val acquiredAt = Instant.parse("2026-07-27T10:00:00Z")

    private fun payload(heartbeatAt: Instant) = LockPayload(
        projectId = ProjectId("p1"),
        date = java.time.LocalDate.of(2026, 7, 27),
        pid = 1234L,
        commandKind = HarnessCommandKind.Doctor,
        acquiredAt = acquiredAt,
        heartbeatAt = heartbeatAt,
    )

    @Test
    fun `PID 생존 + heartbeat 신선은 Active다`() {
        val now = acquiredAt.plusSeconds(5)
        val result = policy.evaluate(payload(acquiredAt), now, pidAlive = true)
        assertEquals(LockState.Active, result)
    }

    @Test
    fun `PID 생존이면 heartbeat이 만료돼도 Active다`() {
        val now = acquiredAt.plusSeconds(120)
        val result = policy.evaluate(payload(acquiredAt), now, pidAlive = true)
        assertEquals(LockState.Active, result)
    }

    @Test
    fun `PID 미존재 + heartbeat 아직 신선은 Active다`() {
        val now = acquiredAt.plusSeconds(10)
        val result = policy.evaluate(payload(acquiredAt), now, pidAlive = false)
        assertEquals(LockState.Active, result)
    }

    @Test
    fun `PID 미존재 + heartbeat 만료는 Stale이다`() {
        val now = acquiredAt.plusSeconds(31)
        val result = policy.evaluate(payload(acquiredAt), now, pidAlive = false)
        assertEquals(LockState.Stale, result)
    }

    @Test
    fun `PID 생존 여부 불명(null)이면 heartbeat 만료 여부와 무관하게 fail-closed로 Active다`() {
        val now = acquiredAt.plusSeconds(120)
        val result = policy.evaluate(payload(acquiredAt), now, pidAlive = null)
        assertEquals(LockState.Active, result)
    }

    @Test
    fun `heartbeat 만료 경계값은 Stale이 아니다`() {
        val now = acquiredAt.plusSeconds(30)
        val result = policy.evaluate(payload(acquiredAt), now, pidAlive = false)
        assertEquals(LockState.Active, result)
    }
}
