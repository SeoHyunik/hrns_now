package io.hrns_now.core.domain.model

import java.util.concurrent.atomic.AtomicBoolean

/**
 * [io.hrns_now.core.port.HarnessRunnerPort.execute] 호출 하나를 취소하기 위한 협력 신호다.
 * 코루틴 `Job` 취소 대신 명시적 flag를 쓴다 — 취소 시에도 예외를 던지지 않고 typed
 * `ProcessRunResult.Cancelled`를 정상 반환값으로 만들기 위함이다(구조적 동시성의
 * `CancellationException` 전파와 섞이지 않도록 분리).
 */
class ProcessCancellationToken {
    private val cancelled = AtomicBoolean(false)

    fun requestCancel() {
        cancelled.set(true)
    }

    fun isCancelled(): Boolean = cancelled.get()
}
