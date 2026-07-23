package io.hrns_now.core.domain.policy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StateReadRetryPolicyTest {

    @Test
    fun `maxAttempts 미만이면 재시도를 허용한다`() {
        val policy = StateReadRetryPolicy(maxAttempts = 3, delayMillis = 10)
        assertTrue(policy.decide(attempt = 1).shouldRetry)
        assertTrue(policy.decide(attempt = 2).shouldRetry)
    }

    @Test
    fun `maxAttempts에 도달하면 재시도를 중단한다`() {
        val policy = StateReadRetryPolicy(maxAttempts = 3, delayMillis = 10)
        assertFalse(policy.decide(attempt = 3).shouldRetry)
        assertFalse(policy.decide(attempt = 4).shouldRetry)
    }

    @Test
    fun `delayMillis를 그대로 반환한다`() {
        val policy = StateReadRetryPolicy(maxAttempts = 5, delayMillis = 42)
        assertEquals(42, policy.decide(attempt = 1).delayMillis)
    }

    @Test
    fun `maxAttempts가 1 미만이면 생성을 거부한다`() {
        assertFailsWith<IllegalArgumentException> { StateReadRetryPolicy(maxAttempts = 0) }
    }

    @Test
    fun `delayMillis가 음수이면 생성을 거부한다`() {
        assertFailsWith<IllegalArgumentException> { StateReadRetryPolicy(delayMillis = -1) }
    }
}
