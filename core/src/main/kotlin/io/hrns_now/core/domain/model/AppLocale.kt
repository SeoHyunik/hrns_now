package io.hrns_now.core.domain.model

/**
 * 앱 표시 언어다. `WORKFLOW_STATE.json`/Harness workspace/project Registry와는
 * 무관한 UI 소유 값이다 — 저장 위치는 [io.hrns_now.core.port.UiPreferencesPort]가 결정한다.
 */
enum class AppLocale(val code: String) {
    Korean("ko"),
    English("en"),
    ;

    companion object {
        fun fromCodeOrDefault(code: String?): AppLocale = entries.firstOrNull { it.code == code } ?: Korean
    }
}
