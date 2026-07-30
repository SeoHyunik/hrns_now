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

    /**
     * [workspacePreparation]은 등록 자체의 성공과 분리된 결과다(Phase 9 QA03-B) — "등록은
     * 완료됨"과 "오늘 workspace 준비는 실패/차단됨"을 서로 다른 사실로 보여줄 수 있다.
     */
    data class Success(
        val projectName: String,
        val workspacePreparation: WorkspacePreparationOutcome = WorkspacePreparationOutcome.NotAttempted,
    ) : RegistrationFeedback

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

/**
 * 등록 직후 자동으로 시도한 오늘 workspace 준비(typed `BootstrapDay` 실행)의 결과다(Phase 9 QA03-B).
 * [reasonText]는 이미 typed 값(`BlockedReasonKey`/`ExecuteHarnessActionOutcome`)에서 locale별로
 * 조립된 안전한 문구이며, raw process 출력이나 경로 원문을 담지 않는다.
 */
sealed interface WorkspacePreparationOutcome {
    /** 등록만 선택했거나, 이미 오늘 workspace가 준비돼 있어 Bootstrap이 필요하지 않았다. */
    data object NotAttempted : WorkspacePreparationOutcome
    data object InProgress : WorkspacePreparationOutcome
    data object Prepared : WorkspacePreparationOutcome
    data class NotPrepared(val reasonText: String) : WorkspacePreparationOutcome
}
