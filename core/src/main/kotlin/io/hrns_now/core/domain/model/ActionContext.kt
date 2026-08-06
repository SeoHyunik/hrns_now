package io.hrns_now.core.domain.model

import io.hrns_now.core.result.StateReadResult

/** 선택된 날짜가 오늘인지 과거인지. 미래 날짜는 harness-kit workspace 폴더 개념상 존재하지 않는다. */
enum class SelectedDayKind {
    Today,
    Past,
}

/**
 * Harness JSON schema/UI 계약 호환성 판정이다. `kit-version.json` 핸드셰이크로 이 값을
 * 직접 판정하는 로직은 아직 구현되지 않았으므로, 호출자가 이 값을 직접 구성한다.
 */
enum class CompatibilityStatus {
    Supported,
    Unsupported,
    Unknown,
}

/**
 * Kit root/workspace root/repository root 경계 검사 결과다.
 * `BoundaryPolicy`(`ProjectBoundaryResult`)가 아직 이 판정을 만들지 않으므로 호출자가 구성한다.
 */
enum class BoundaryStatus {
    Valid,
    Invalid,
    Unknown,
}

/**
 * 같은 프로젝트·날짜에 대한 다른 실행의 존재 여부다. lock/프로세스 조율 계층이
 * 아직 이 값을 만들지 않으므로 호출자가 구성한다.
 */
enum class ProcessRunStatus {
    Idle,
    Running,
    Locked,
}

/**
 * 현재 활성 queue slice의 실행 종류다.
 *
 * `WorkflowState.executionWrapper`(`state.execution_wrapper`)는 "가장 최근 실행 시도에 사용된
 * CLI wrapper" 값이며, harness의 개별 queue slice가 선언하는 wrapper(`queue.cards[].slices[].wrapper`,
 * 예: `"none (verification-only)"`)와는 다른 필드다. slice 목록 전체는 typed model로
 * 만들지 않았으므로(`WorkflowQueue.active`는 pointer만 가짐), 이 타입은 호출자가 별도로 판정해
 * 주입하는 독립적인 context 차원이다. `ValidationOnly`는 검증 전용 slice를 뜻하며, 가짜
 * `ExecutionWrapperState`나 존재하지 않는 CLI wrapper 값으로 대체하지 않는다.
 */
sealed interface ActiveSliceKind {
    data object Code : ActiveSliceKind
    data object Doc : ActiveSliceKind
    data object ValidationOnly : ActiveSliceKind
    data class Unknown(val raw: String) : ActiveSliceKind
}

/**
 * `state.execution_wrapper`에서 [ActiveSliceKind]를 유도한다.
 *
 * harness-kit의 `state_surface.py`(`sync_harness_state_projection_from_queue`)가 active queue
 * slice를 가진 동안 `execution_wrapper`를 그 slice 자신의 `wrapper` 값으로 동기화하므로,
 * `queue.active`가 유효한 pointer일 때(= [io.hrns_now.core.domain.policy.ActionPolicy]의
 * `executionContractReady`) 이 필드는 실제로 활성 slice가 요구하는 wrapper를 반영한다
 * (`docs/STATE_MODEL.md` §6.3 근거).
 *
 * `None`/`Auto`는 의도적으로 `null`로 fail-closed한다: "none (verification-only)"은 사람용
 * `TODAY_STRATEGY.md` 산문 관례일 뿐 machine `wrapper` 값의 정확한 문자열 형태가 확정되지
 * 않았고, `Auto`는 CLI 계약 충실성을 위해서만 존재하며 UI 액션으로 노출하지 않는다
 * (`doc/hrns_now_design_pattern.md` §6.2). 둘 다 [ActionPolicy]에서 동일하게
 * "활성 slice 종류를 확인할 수 없습니다" 복구 경로로 이어진다.
 */
fun ExecutionWrapperState.toActiveSliceKind(): ActiveSliceKind? =
    when (this) {
        ExecutionWrapperState.Code -> ActiveSliceKind.Code
        ExecutionWrapperState.Doc -> ActiveSliceKind.Doc
        ExecutionWrapperState.None,
        ExecutionWrapperState.Auto,
        -> null
        is ExecutionWrapperState.Unknown -> ActiveSliceKind.Unknown(raw)
    }

/**
 * [io.hrns_now.core.domain.policy.ActionPolicy]의 유일한 입력이다. domain 값만 가진 불변
 * context이며, 파일 경로·JSON·Compose·프로세스 핸들을 전혀 참조하지 않는다.
 *
 * `selectedDayKind`/`stateRead`가 오늘 날짜인지, 실제로 State를 읽었는지는 호출자(ViewModel/use case)가
 * [io.hrns_now.core.domain.model.WorkspaceDay]와
 * [io.hrns_now.core.port.WorkflowStatePort]를 조합해 결정한다 — 정책 자체는 `LocalDate.now()`
 * 등 시간을 직접 조회하지 않는다.
 */
data class ActionContext(
    val projectConnected: Boolean,
    val selectedDayKind: SelectedDayKind?,
    val stateRead: StateReadResult?,
    val compatibility: CompatibilityStatus,
    val boundary: BoundaryStatus,
    val process: ProcessRunStatus,
    val activeSliceKind: ActiveSliceKind?,
)
