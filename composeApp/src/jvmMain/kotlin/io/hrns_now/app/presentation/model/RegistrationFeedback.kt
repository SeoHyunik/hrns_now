package io.hrns_now.app.presentation.model

/**
 * "진단 후 등록" 진행 상태다. `ProjectManagementSection`/`ProjectRegistrationForm`은
 * 이 값을 modal·인라인 폼 어디서든 직접 렌더링해, 결과가 modal 뒤 부모 카드에만 남아 사용자가
 * 놓치는 일이 없게 한다. 원인은 core의 typed 값(`RegistrationRejectionReason`/`ProcessRunResult`/
 * `HarnessCompatibilityDetail`)에서 그대로 유도하며, 문자열 일부를 비교해 추정하지 않는다.
 */
sealed interface RegistrationFeedback {
    data object Idle : RegistrationFeedback
    data object Running : RegistrationFeedback

    /**
     * [onboarding]은 등록 자체의 성공과 분리된 결과다 — "등록은 완료됨"과 "프로젝트
     * 준비(bridge/외부 workspace)는 실패/차단됨"을 서로 다른 사실로 보여줄 수 있다.
     */
    data class Success(
        val projectName: String,
        val onboarding: ProjectOnboardingOutcome = ProjectOnboardingOutcome.NotAttempted,
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
 * 등록 직후(또는 기존 활성 프로젝트의 "프로젝트 준비" 재시도) 시도한 온보딩(typed
 * `HarnessCommand.OnboardProject` = `enter-project.ps1` + `validate-ops.ps1 -Json` + bridge/4-file
 * probe + State 재조회)의 결과다. 이 값은 `WorkspacePreparationOutcome`(=
 * `BootstrapDay` 실행 결과)과 의미가 다르다 — 온보딩은 오늘 daily 작업을 시작하지 않는다.
 * [Blocked.reasonText]는 이미 typed 값에서 locale별로 조립된 안전한 문구이며, raw process
 * 출력이나 경로 원문·secret·session ID를 담지 않는다.
 */
sealed interface ProjectOnboardingOutcome {
    /** 등록만 선택했거나, 이미 bridge와 오늘 workspace가 준비돼 있어 onboarding이 필요하지 않았다. */
    data object NotAttempted : ProjectOnboardingOutcome
    data object InProgress : ProjectOnboardingOutcome

    /** enter-project 정상 종료 + validate-ops overall=ok + bridge 3종 ready + 4-file ready + State Success 교집합. */
    data object Ready : ProjectOnboardingOutcome
    data class Blocked(val reasonText: String) : ProjectOnboardingOutcome
}
