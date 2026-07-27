package io.hrns_now.app.presentation.mapper

import io.hrns_now.core.domain.model.LockState
import io.hrns_now.core.port.LockInspection
import java.time.Duration
import java.time.Instant

/** [RunStatusProjectionAssembler]와 실행 확인 panel(오늘 할 일 화면)이 공유하는 lock 표시 문구다. */
fun lockSummaryLabel(lockInspection: LockInspection?, now: Instant): String {
    if (lockInspection == null) return "없음"
    val ageSeconds = Duration.between(lockInspection.payload.heartbeatAt, now).seconds
    val staleLabel = if (lockInspection.state == LockState.Stale) " (stale로 보임)" else ""
    return "${lockInspection.payload.commandKind.displayLabel()} " +
        "(PID ${lockInspection.payload.pid}, 마지막 heartbeat ${ageSeconds}초 전$staleLabel)"
}
