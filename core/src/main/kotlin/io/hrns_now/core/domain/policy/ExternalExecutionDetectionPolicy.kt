package io.hrns_now.core.domain.policy

/**
 * UI가 시작하지 않은 WORKFLOW_STATE 변경을 실행 중 가능성으로만 분류하는 순수 휴리스틱이다.
 * 파일을 직접 읽거나 외부 실행을 완전히 차단한다고 주장하지 않는다.
 */
class ExternalExecutionDetectionPolicy {
    fun isSuspected(
        stateChangedAfterInitialObservation: Boolean,
        localRunInProgress: Boolean,
        localStateRefreshPending: Boolean,
    ): Boolean = stateChangedAfterInitialObservation && !localRunInProgress && !localStateRefreshPending
}