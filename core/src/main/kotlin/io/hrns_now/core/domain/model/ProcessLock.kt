package io.hrns_now.core.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * UI 소유 lock의 payload다. Harness workspace가 아니라
 * `%LOCALAPPDATA%\hrns-now\locks\<projectId>\<yyyy-MM-dd>.lock.json`에
 * 저장된다. raw command output·secret·raw session/request ID·workspace state는 절대 담지 않는다 —
 * 이 다섯 필드만 담는다.
 */
data class LockPayload(
    val projectId: ProjectId,
    val date: LocalDate,
    val pid: Long,
    val commandKind: HarnessCommandKind,
    val acquiredAt: Instant,
    val heartbeatAt: Instant,
)

/** 획득에 성공한 lock을 가리키는 opaque handle이다. release/heartbeat 호출에 그대로 전달한다. */
data class LockHandle(
    val projectId: ProjectId,
    val date: LocalDate,
    val pid: Long,
    val acquiredAt: Instant,
)

sealed interface LockAcquireResult {
    data class Acquired(val handle: LockHandle) : LockAcquireResult

    /** 다른 소유자가 active 상태로 보유 중이다(stale이 아님). */
    data class Busy(val owner: LockPayload) : LockAcquireResult

    /** lock 파일 write 실패 등 획득 자체를 시도할 수 없었다. */
    data class Failed(val reason: String) : LockAcquireResult
}

sealed interface LockReleaseResult {
    data object Released : LockReleaseResult
    data class Failed(val reason: String) : LockReleaseResult
}

/**
 * [io.hrns_now.core.domain.policy.LockStalePolicy]의 판정 결과다. `Stale`은 PID 미존재와
 * heartbeat 만료를 **함께** 만족해야만 나온다 — 어느 하나라도 불명확하면 `Active`로 남는다
 * (fail-closed).
 */
enum class LockState {
    Active,
    Stale,
}
