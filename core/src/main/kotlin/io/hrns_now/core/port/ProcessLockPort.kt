package io.hrns_now.core.port

import io.hrns_now.core.domain.model.HarnessCommandKind
import io.hrns_now.core.domain.model.LockAcquireResult
import io.hrns_now.core.domain.model.LockHandle
import io.hrns_now.core.domain.model.LockPayload
import io.hrns_now.core.domain.model.LockReleaseResult
import io.hrns_now.core.domain.model.LockState
import io.hrns_now.core.domain.model.ProjectId
import java.time.LocalDate

/** [ProcessLockPort.inspect]가 반환하는, PID/heartbeat 판정까지 끝난 조회 결과다. */
data class LockInspection(val payload: LockPayload, val state: LockState)

/**
 * UI 소유 lock의 port다(`doc/claude_prompts/phase3-process-adapter-lock.md` §4). 구현체는
 * `%LOCALAPPDATA%\hrns-now\locks\<projectId>\<yyyy-MM-dd>.lock.json`, PID lookup, 시계를
 * 알아도 되지만 이 interface 자체는 모른다. Lock은 decorator로 만들지 않는다 — 획득/해제는
 * 호출자(실행 조율부)가 명시적으로 관리한다(`doc/hrns_now_design_pattern.md` §12).
 */
interface ProcessLockPort {
    suspend fun acquire(projectId: ProjectId, date: LocalDate, commandKind: HarnessCommandKind): LockAcquireResult

    /** heartbeat 갱신에 성공하면 true. 이미 release/force-release된 handle이면 false. */
    suspend fun heartbeat(handle: LockHandle): Boolean

    suspend fun release(handle: LockHandle): LockReleaseResult

    /**
     * 현재 lock 소유자를 조회만 한다(획득 시도 없음) — UI에 owner/heartbeat와 stale 여부를
     * 보여주는 용도다. PID 조회는 구현체(infra)의 책임이며, 판정 자체는 내부적으로
     * [io.hrns_now.core.domain.policy.LockStalePolicy]를 사용한다.
     */
    suspend fun inspect(projectId: ProjectId, date: LocalDate): LockInspection?

    /** 사용자가 명시적으로 요청한 강제 해제다. stale 판정과 무관하게 lock 파일을 제거한다. */
    suspend fun forceRelease(projectId: ProjectId, date: LocalDate): LockReleaseResult
}
