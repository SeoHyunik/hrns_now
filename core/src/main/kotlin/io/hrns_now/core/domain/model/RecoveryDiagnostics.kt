package io.hrns_now.core.domain.model

/**
 * Recovery 화면에만 쓰는 비권위적 진단 요약이다. 로그의 raw session id, thread id, path,
 * payload는 이 모델로 옮기지 않는다. 실행 가능 여부는 항상 WORKFLOW_STATE.json과 정책이
 * 결정하며 이 값은 사람이 복구 맥락을 이해하는 용도다.
 */
data class RecoveryDiagnostics(
    val continuity: ContinuityDiagnosticSummary,
    val usageLedger: UsageLedgerSummary,
    val failureHistory: FailureHistorySummary,
) {
    companion object {
        val Empty = RecoveryDiagnostics(
            continuity = ContinuityDiagnosticSummary(available = false, recordCount = 0, actualResumeAppliedCount = 0, freshRequiredCount = 0, unreadableCount = 0),
            usageLedger = UsageLedgerSummary(available = false, recordCount = 0, sessionMetadataPresentCount = 0, unreadableCount = 0),
            failureHistory = FailureHistorySummary(available = false, entryCount = 0),
        )
    }
}

data class ContinuityDiagnosticSummary(
    val available: Boolean,
    val recordCount: Int,
    val actualResumeAppliedCount: Int,
    val freshRequiredCount: Int,
    val unreadableCount: Int,
)

data class UsageLedgerSummary(
    val available: Boolean,
    val recordCount: Int,
    val sessionMetadataPresentCount: Int,
    val unreadableCount: Int,
)

data class FailureHistorySummary(
    val available: Boolean,
    val entryCount: Int,
)
