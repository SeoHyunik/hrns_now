package io.hrns_now.core.domain.policy

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExternalExecutionDetectionPolicyTest {
    private val policy = ExternalExecutionDetectionPolicy()

    @Test
    fun `초기 관측 뒤 State가 바뀌고 자체 실행이 아니면 외부 실행 가능성이다`() {
        assertTrue(policy.isSuspected(true, false, false))
    }

    @Test
    fun `자체 실행 중 State 변경은 외부 실행으로 오인하지 않는다`() {
        assertFalse(policy.isSuspected(true, true, false))
    }

    @Test
    fun `자체 실행 종료 뒤 예정된 재읽기는 외부 실행으로 오인하지 않는다`() {
        assertFalse(policy.isSuspected(true, false, true))
    }

    @Test
    fun `State가 변하지 않으면 외부 실행 가능성이 아니다`() {
        assertFalse(policy.isSuspected(false, false, false))
    }
}