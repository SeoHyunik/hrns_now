package io.hrns_now.core.port

import io.hrns_now.core.domain.model.RecoveryDiagnostics
import io.hrns_now.core.domain.model.WorkspaceDay

/** 읽기 전용 recovery 진단 요약 port다. 로그는 상태나 CTA의 근거가 아니다. */
fun interface RecoveryDiagnosticsPort {
    fun read(day: WorkspaceDay): RecoveryDiagnostics
}
