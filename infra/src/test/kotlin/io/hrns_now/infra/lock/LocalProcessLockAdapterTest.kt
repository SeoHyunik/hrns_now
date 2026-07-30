package io.hrns_now.infra.lock

import io.hrns_now.core.domain.model.HarnessCommandKind
import io.hrns_now.core.domain.model.LockAcquireResult
import io.hrns_now.core.domain.model.LockReleaseResult
import io.hrns_now.core.domain.model.ProjectId
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalProcessLockAdapterTest {

    private fun tempLocksRoot(): Path = Files.createTempDirectory("hrns-lock-test")

    private val projectId = ProjectId("sample")
    private val date: LocalDate = LocalDate.of(2026, 7, 27)

    @Test
    fun `동시에 여러 acquire를 경쟁시키면 정확히 하나만 성공한다`(): Unit = runBlocking {
        repeat(10) {
            val locksRoot = tempLocksRoot()
            val adapter = LocalProcessLockAdapter(locksRoot = locksRoot, pidAlive = { true })

            val results = (1..20).map {
                async(Dispatchers.Default) { adapter.acquire(projectId, date, HarnessCommandKind.Doctor) }
            }.awaitAll()

            assertEquals(1, results.count { it is LockAcquireResult.Acquired })
            assertEquals(19, results.count { it is LockAcquireResult.Busy })
            assertEquals(0, results.count { it is LockAcquireResult.Failed })
        }
    }

    @Test
    fun `active PID를 가진 lock은 Busy를 반환하고 소유자 정보를 그대로 담는다`(): Unit = runBlocking {
        val locksRoot = tempLocksRoot()
        val currentPidHolder = AtomicLong(1000L)
        val first = LocalProcessLockAdapter(locksRoot = locksRoot, currentPid = { currentPidHolder.get() }, pidAlive = { true })
        val acquired = assertIs<LockAcquireResult.Acquired>(first.acquire(projectId, date, HarnessCommandKind.Doctor))
        assertEquals(1000L, acquired.handle.pid)

        currentPidHolder.set(2000L)
        val second = LocalProcessLockAdapter(locksRoot = locksRoot, currentPid = { currentPidHolder.get() }, pidAlive = { true })
        val result = assertIs<LockAcquireResult.Busy>(second.acquire(projectId, date, HarnessCommandKind.ValidateOps))
        assertEquals(1000L, result.owner.pid)
        assertEquals(HarnessCommandKind.Doctor, result.owner.commandKind)
    }

    @Test
    fun `PID 미존재와 heartbeat 만료가 함께면 인수해서 Acquired가 된다`(): Unit = runBlocking {
        val locksRoot = tempLocksRoot()
        var now = Instant.parse("2026-07-27T10:00:00Z")
        val first = LocalProcessLockAdapter(locksRoot = locksRoot, clock = { now }, currentPid = { 1000L }, pidAlive = { false })
        assertIs<LockAcquireResult.Acquired>(first.acquire(projectId, date, HarnessCommandKind.Doctor))

        now = now.plusSeconds(60)
        val second = LocalProcessLockAdapter(locksRoot = locksRoot, clock = { now }, currentPid = { 2000L }, pidAlive = { false })
        val result = assertIs<LockAcquireResult.Acquired>(second.acquire(projectId, date, HarnessCommandKind.ValidateOps))
        assertEquals(2000L, result.handle.pid)
    }

    @Test
    fun `heartbeat만 만료돼도 PID가 살아있으면 Active로 Busy를 유지한다`(): Unit = runBlocking {
        val locksRoot = tempLocksRoot()
        var now = Instant.parse("2026-07-27T10:00:00Z")
        val first = LocalProcessLockAdapter(locksRoot = locksRoot, clock = { now }, currentPid = { 1000L }, pidAlive = { true })
        first.acquire(projectId, date, HarnessCommandKind.Doctor)

        now = now.plusSeconds(600)
        val second = LocalProcessLockAdapter(locksRoot = locksRoot, clock = { now }, currentPid = { 2000L }, pidAlive = { true })
        assertIs<LockAcquireResult.Busy>(second.acquire(projectId, date, HarnessCommandKind.ValidateOps))
    }

    @Test
    fun `PID만 없어져도 heartbeat이 신선하면 Active로 Busy를 유지한다`(): Unit = runBlocking {
        val locksRoot = tempLocksRoot()
        val now = Instant.parse("2026-07-27T10:00:00Z")
        val first = LocalProcessLockAdapter(locksRoot = locksRoot, clock = { now }, currentPid = { 1000L }, pidAlive = { false })
        first.acquire(projectId, date, HarnessCommandKind.Doctor)

        val second = LocalProcessLockAdapter(locksRoot = locksRoot, clock = { now }, currentPid = { 2000L }, pidAlive = { false })
        assertIs<LockAcquireResult.Busy>(second.acquire(projectId, date, HarnessCommandKind.ValidateOps))
    }

    @Test
    fun `PID 생존 여부가 불명(null)이면 fail-closed로 Busy를 유지한다`(): Unit = runBlocking {
        val locksRoot = tempLocksRoot()
        var now = Instant.parse("2026-07-27T10:00:00Z")
        val first = LocalProcessLockAdapter(locksRoot = locksRoot, clock = { now }, currentPid = { 1000L }, pidAlive = { null })
        first.acquire(projectId, date, HarnessCommandKind.Doctor)

        now = now.plusSeconds(600)
        val second = LocalProcessLockAdapter(locksRoot = locksRoot, clock = { now }, currentPid = { 2000L }, pidAlive = { null })
        assertIs<LockAcquireResult.Busy>(second.acquire(projectId, date, HarnessCommandKind.ValidateOps))
    }

    @Test
    fun `heartbeat는 소유자 identity가 일치할 때만 갱신에 성공한다`(): Unit = runBlocking {
        val locksRoot = tempLocksRoot()
        val adapter = LocalProcessLockAdapter(locksRoot = locksRoot, currentPid = { 1000L }, pidAlive = { true })
        val acquired = assertIs<LockAcquireResult.Acquired>(adapter.acquire(projectId, date, HarnessCommandKind.Doctor))

        assertTrue(adapter.heartbeat(acquired.handle))

        val staleHandle = acquired.handle.copy(pid = 9999L)
        assertFalse(adapter.heartbeat(staleHandle))
    }

    @Test
    fun `release는 자신의 handle만 지우고 소유자가 다르면 아무 것도 지우지 않는다`(): Unit = runBlocking {
        val locksRoot = tempLocksRoot()
        val adapter = LocalProcessLockAdapter(locksRoot = locksRoot, currentPid = { 1000L }, pidAlive = { true })
        val acquired = assertIs<LockAcquireResult.Acquired>(adapter.acquire(projectId, date, HarnessCommandKind.Doctor))

        val foreignHandle = acquired.handle.copy(pid = 424242L)
        assertEquals(LockReleaseResult.Released, adapter.release(foreignHandle))
        assertTrue(adapter.inspect(projectId, date) != null, "다른 handle의 release는 실제 lock을 지우면 안 된다")

        assertEquals(LockReleaseResult.Released, adapter.release(acquired.handle))
        assertNull(adapter.inspect(projectId, date))
    }

    @Test
    fun `forceRelease는 소유자와 무관하게 항상 lock 파일을 제거한다`(): Unit = runBlocking {
        val locksRoot = tempLocksRoot()
        val adapter = LocalProcessLockAdapter(locksRoot = locksRoot, currentPid = { 1000L }, pidAlive = { true })
        adapter.acquire(projectId, date, HarnessCommandKind.Doctor)

        assertEquals(LockReleaseResult.Released, adapter.forceRelease(projectId, date))
        assertNull(adapter.inspect(projectId, date))

        val reacquired = adapter.acquire(projectId, date, HarnessCommandKind.ValidateOps)
        assertIs<LockAcquireResult.Acquired>(reacquired)
    }

    @Test
    fun `lock 파일 JSON은 계획된 소유자 필드만 담고 secret 유사 값이 없다`(): Unit = runBlocking {
        val locksRoot = tempLocksRoot()
        val adapter = LocalProcessLockAdapter(locksRoot = locksRoot, currentPid = { 1000L }, pidAlive = { true })
        adapter.acquire(projectId, date, HarnessCommandKind.Doctor)

        val lockFile = locksRoot.resolve(projectId.value).resolve("$date.lock.json")
        assertTrue(Files.exists(lockFile))
        val text = Files.readString(lockFile, StandardCharsets.UTF_8)

        for (forbidden in listOf("token", "secret", "password", "session", "output", "workflow_state", "raw")) {
            assertFalse(text.lowercase().contains(forbidden), "lock 파일에 '$forbidden'이 있으면 안 된다: $text")
        }
        for (allowed in listOf("project_id", "date", "owner_pid", "owner_kind", "started_at", "heartbeat_at", "command")) {
            assertTrue(text.contains(allowed), "lock 파일에 '$allowed'가 있어야 한다: $text")
        }
    }
}
