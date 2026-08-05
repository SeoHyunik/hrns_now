package io.hrns_now.infra.security

/**
 * Harness `Protect-HarnessDiagnosticMessage`(PowerShell)와 동일한 카테고리를 HRNS-NOW 실행
 * 계층 자체가 만드는 메시지(JVM 예외 메시지, JSON 파싱 실패 시 raw stdout/stderr 조각)에
 * 방어적으로 한 번 더 적용한다. Harness 쪽 `checks[].message`는 이미 masking된 값이지만,
 * 이 클래스를 통과시켜도 실제 secret이 없으면 아무 것도 바뀌지 않는다(단순 치환이므로 결과가
 * 달라지지 않는 한 안전).
 */
class SecretMasker {

    fun mask(message: String): String {
        var safe = message
        for ((pattern, replacement) in PATTERNS) {
            safe = pattern.replace(safe, replacement)
        }
        return safe
    }

    private companion object {
        val PATTERNS: List<Pair<Regex, String>> = listOf(
            Regex("(?i)\\bsk-[a-z0-9_-]{8,}\\b") to "[REDACTED_TOKEN]",
            Regex("(?i)\\bgh[pousr]_[a-z0-9]{20,}\\b") to "[REDACTED_TOKEN]",
            Regex("(?i)\\bAKIA[A-Z0-9]{16}\\b") to "[REDACTED_TOKEN]",
            Regex("(?i)\\beyJ[a-z0-9_-]{8,}\\.[a-z0-9_-]{8,}\\.[a-z0-9_-]{8,}\\b") to "[REDACTED_TOKEN]",
            Regex(
                "(?i)(\\b(?:api[_-]?key|access[_-]?token|auth[_-]?token|token|secret|password|" +
                    "session[_-]?id|request[_-]?thread[_-]?id|task[_-]?thread[_-]?id)\\b\\s*[:=]\\s*)" +
                    "([^\\s,;}\\]]+)",
            ) to "$1[REDACTED]",
            Regex("(?i)(\\bbearer\\s+)[a-z0-9._~+/-]{8,}") to "$1[REDACTED]",
        )
    }
}
