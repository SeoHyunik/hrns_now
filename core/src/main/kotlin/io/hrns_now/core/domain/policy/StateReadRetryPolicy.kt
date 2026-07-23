package io.hrns_now.core.domain.policy

/** 특정 시도 횟수 이후 재시도 여부와 지연 시간을 결정한다. */
data class RetryDecision(
    val shouldRetry: Boolean,
    val delayMillis: Long,
)

/**
 * partial write(파일이 쓰이는 도중 읽음) 등으로 인한 일시적 읽기 실패에 대한 재시도 정책이다.
 *
 * 순수 정책이며 실제 대기(sleep)는 수행하지 않는다 — 호출자가 [RetryDecision.delayMillis]를
 * 어떤 방식으로 대기할지(Thread.sleep, 테스트에서는 no-op) 주입한다.
 */
class StateReadRetryPolicy(
    private val maxAttempts: Int = 3,
    private val delayMillis: Long = 50,
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be at least 1" }
        require(delayMillis >= 0) { "delayMillis must not be negative" }
    }

    /** [attempt]는 1부터 시작하는 지금까지 수행한 시도 횟수다. */
    fun decide(attempt: Int): RetryDecision =
        RetryDecision(
            shouldRetry = attempt < maxAttempts,
            delayMillis = delayMillis,
        )
}
