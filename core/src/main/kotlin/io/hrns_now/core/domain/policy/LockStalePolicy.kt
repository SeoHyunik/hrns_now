package io.hrns_now.core.domain.policy

import io.hrns_now.core.domain.model.LockPayload
import io.hrns_now.core.domain.model.LockState
import java.time.Duration
import java.time.Instant

/**
 * lock stale 여부를 판정하는 순수 정책이다. PID 파일·시계·프로세스 목록을 직접 조회하지 않고,
 * 이미 조회된 값만 입력으로 받는다.
 *
 * `Stale`은 PID 미존재(`pidAlive == false`)와 heartbeat 만료를 **함께** 만족해야만 나온다.
 * `pidAlive`가 `null`(PID 생존 여부를 판정할 수 없음)이면 무조건 `Active`로 fail-closed한다.
 */
class LockStalePolicy(
    private val heartbeatTimeout: Duration = Duration.ofSeconds(30),
) {
    fun evaluate(payload: LockPayload, now: Instant, pidAlive: Boolean?): LockState {
        if (pidAlive != false) {
            // true(생존) 또는 null(불명확) 모두 fail-closed로 Active 취급한다.
            return LockState.Active
        }

        val heartbeatExpired = Duration.between(payload.heartbeatAt, now) > heartbeatTimeout
        return if (heartbeatExpired) LockState.Stale else LockState.Active
    }
}
