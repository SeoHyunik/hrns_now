package io.hrns_now.core.domain.model

/**
 * `state.execution_wrapper`(읽기 측)의 typed 표현이다.
 *
 * 이름을 outbound 명령 인코딩용 `ExecutionWrapper`(-RunExecutionWrapper 인자 생성,
 * `doc/hrns_now_design_pattern.md` §6.2)와 의도적으로 다르게 둔다 — 이 타입은
 * `WORKFLOW_STATE.json`에서 이미 실행된/선언된 wrapper 값을 읽는 domain 표현이고,
 * `ExecutionWrapper`는 앞으로 실행할 명령을 만드는 CLI 인자 인코더다. 두 개념을 하나의
 * enum으로 합치면 읽기 계약과 쓰기 계약이 뒤섞인다.
 *
 * 알려진 값은 `run-cycle.ps1`의 `-RunExecutionWrapper` 실계약(`none|code|doc|auto`)과 동일하다.
 */
sealed interface ExecutionWrapperState {
    data object None : ExecutionWrapperState
    data object Code : ExecutionWrapperState
    data object Doc : ExecutionWrapperState
    data object Auto : ExecutionWrapperState
    data class Unknown(val raw: String) : ExecutionWrapperState
}

/** null/빈 문자열은 명시적으로 [ExecutionWrapperState.None]으로 취급한다. */
fun String?.toExecutionWrapperState(): ExecutionWrapperState =
    when {
        this.isNullOrBlank() -> ExecutionWrapperState.None
        this == "none" -> ExecutionWrapperState.None
        this == "code" -> ExecutionWrapperState.Code
        this == "doc" -> ExecutionWrapperState.Doc
        this == "auto" -> ExecutionWrapperState.Auto
        else -> ExecutionWrapperState.Unknown(this)
    }
