package io.hrns_now.core.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HarnessProfileTest {
    @Test
    fun `기본 profile은 화면 번역어가 아닌 Harness profile ID다`() {
        assertEquals("corp-default", DEFAULT_HARNESS_PROFILE_ID)
        assertFalse(DEFAULT_HARNESS_PROFILE_ID.any { it.isWhitespace() })
    }
}