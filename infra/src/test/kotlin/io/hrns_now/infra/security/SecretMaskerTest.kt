package io.hrns_now.infra.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SecretMaskerTest {

    private val masker = SecretMasker()

    @Test
    fun `sk- 형태 토큰을 masking한다`() {
        val masked = masker.mask("key is sk-abcdefgh12345678 in the log")
        assertFalse(masked.contains("sk-abcdefgh12345678"))
        assertEquals("key is [REDACTED_TOKEN] in the log", masked)
    }

    @Test
    fun `github 토큰을 masking한다`() {
        val masked = masker.mask("token=ghp_ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")
        assertFalse(masked.contains("ghp_ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"))
    }

    @Test
    fun `AWS access key를 masking한다`() {
        val masked = masker.mask("AKIAABCDEFGHIJKLMNOP leaked")
        assertFalse(masked.contains("AKIAABCDEFGHIJKLMNOP"))
        assertEquals("[REDACTED_TOKEN] leaked", masked)
    }

    @Test
    fun `JWT 형태 토큰을 masking한다`() {
        val jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dGVzdHNpZ25hdHVyZQ"
        val masked = masker.mask("auth=$jwt")
        assertFalse(masked.contains(jwt))
    }

    @Test
    fun `key value 형태의 secret token password session_id를 masking한다`() {
        assertEquals("api_key: [REDACTED]", masker.mask("api_key: abcd1234"))
        assertEquals("token=[REDACTED]", masker.mask("token=abcd1234"))
        assertEquals("secret: [REDACTED]", masker.mask("secret: my-value"))
        assertEquals("password=[REDACTED]", masker.mask("password=hunter2"))
        assertEquals("session_id: [REDACTED]", masker.mask("session_id: abc-123-def"))
        assertEquals("request_thread_id: [REDACTED]", masker.mask("request_thread_id: rq-abc123"))
        assertEquals("task_thread_id: [REDACTED]", masker.mask("task_thread_id: tk-abc123"))
    }

    @Test
    fun `bearer 토큰을 masking한다`() {
        val masked = masker.mask("Authorization: Bearer abcdefgh12345678")
        assertFalse(masked.contains("abcdefgh12345678"))
        assertEquals("Authorization: Bearer [REDACTED]", masked)
    }

    @Test
    fun `secret이 없는 일반 메시지는 그대로 보존한다`() {
        val message = "Directory exists: D:\\harness-kit\\docs"
        assertEquals(message, masker.mask(message))
    }

    @Test
    fun `한글 메시지는 그대로 보존한다`() {
        val message = "작업공간이 존재하지 않습니다: D:\\harness-workspaces\\샘플"
        assertEquals(message, masker.mask(message))
    }
}
