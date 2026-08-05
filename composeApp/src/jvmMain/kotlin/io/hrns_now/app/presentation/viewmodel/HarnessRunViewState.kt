package io.hrns_now.app.presentation.viewmodel

import io.hrns_now.core.domain.model.HarnessCommandKind
import io.hrns_now.core.result.ProcessRunResult
import java.time.Instant

/**
 * [AppViewModel]이 내부적으로 들고 있는 Doctor/ValidateOps 실행 스냅샷이다. Compose는
 * 이 값을 직접 보지 않는다 — [io.hrns_now.app.presentation.mapper.RunStatusProjectionAssembler]가
 * 화면 표시용 [io.hrns_now.app.presentation.model.RunStatusProjection]으로만 변환한다.
 */
data class HarnessRunViewState(
    val lastCommand: HarnessCommandKind? = null,
    val isRunning: Boolean = false,
    val runStartedAt: Instant? = null,
    /** [lastResult]가 채워진 시각이다 — 실행 feedback의 "완료 시각" 표시에 쓰인다. */
    val runCompletedAt: Instant? = null,
    val lastResult: ProcessRunResult? = null,
    /** 실행 보류·잠금 획득 실패처럼 process 결과가 없는 상태의 안전한 사용자 안내다. */
    val notice: String? = null,
)
