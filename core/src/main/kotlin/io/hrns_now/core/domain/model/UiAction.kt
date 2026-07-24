package io.hrns_now.core.domain.model

/**
 * 사용자가 취할 수 있는 행동의 typed identity다.
 *
 * 표시 문구(한국어 label)는 presentation 책임이며 이 sealed interface의 일부가 아니다 —
 * `doc/hrns_now_design_pattern.md` §5.3 "표시 label을 action ID로 사용하지 않는다"를 따른다.
 *
 * `RunCodeSlice`/`RunDocSlice`/`RunReplan`/`RunClosureValidation`은 계획서 3.3의 명명을 그대로
 * 따르되, Phase 1B에서는 어떤 PowerShell 명령으로도 매핑하지 않는다. 실제 command 인코딩은
 * Phase 3(`HarnessCommandEncoder`)에서 이 값을 typed command로 변환할 때 이뤄진다.
 *
 * `RunValidationSlice`는 validation-only 활성 slice를 위한 typed action이다.
 * 존재하지 않는 validation wrapper를 만들지 않기 위해 code/doc 실행과 별개의 identity로 두고,
 * 실제 mapping은 Phase 4에서 live 계약이 확정된 뒤 연결한다.
 */
sealed interface UiAction {
    data object ConnectProject : UiAction
    data object SelectWorkspaceDay : UiAction
    data object OpenToday : UiAction
    data object Refresh : UiAction
    data object EditRequest : UiAction
    data object RunDoctor : UiAction
    data object RunOpsValidation : UiAction
    data object BootstrapDay : UiAction
    data object RunPlanning : UiAction
    data object RunReplan : UiAction
    data object ReviewPlan : UiAction
    data object RunCodeSlice : UiAction
    data object RunDocSlice : UiAction

    /**
     * Harness validation-only slice의 논리 action이다.
     * Phase 4 live 계약 검토 전까지 process command와 연결하지 않는다.
     */
    data object RunValidationSlice : UiAction
    data object RunClosureValidation : UiAction
    data object ReviewClosure : UiAction
    data object CloseDay : UiAction
    data object OpenRecoveryCenter : UiAction
    data object ShowCompatibilityIssue : UiAction
    data object ViewExecutionStatus : UiAction
}
