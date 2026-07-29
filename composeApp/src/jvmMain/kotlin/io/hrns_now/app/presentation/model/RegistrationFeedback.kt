package io.hrns_now.app.presentation.model

/**
 * "진단 후 등록" 진행 상태다(새 Phase 8 §1). `ProjectManagementSection`/`ProjectRegistrationForm`은
 * 이 값을 modal·인라인 폼 어디서든 직접 렌더링해, 결과가 modal 뒤 부모 카드에만 남아 사용자가
 * 놓치는 일이 없게 한다. 원인은 core의 typed 값(`RegistrationRejectionReason`/`ProcessRunResult`/
 * `HarnessCompatibilityDetail`)에서 그대로 유도하며, 문자열 일부를 비교해 추정하지 않는다.
 */
sealed interface RegistrationFeedback {
    data object Idle : RegistrationFeedback
    data object Running : RegistrationFeedback
    data class Success(val projectName: String) : RegistrationFeedback

    /**
     * [showAdvancedSettingsHint]는 원인이 내장 SDK 관련(Missing/Invalid)일 때만 true다 — 이 경우
     * "고급 설정을 열어 외부 Harness Kit을 선택하라"는 다음 행동을 화면이 별도로 강조한다.
     */
    data class Failure(
        val whatHappened: String,
        val nextStep: String,
        val showAdvancedSettingsHint: Boolean = false,
    ) : RegistrationFeedback
}
