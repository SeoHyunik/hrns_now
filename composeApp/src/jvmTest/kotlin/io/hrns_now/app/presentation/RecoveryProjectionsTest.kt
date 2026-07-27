package io.hrns_now.app.presentation

import io.hrns_now.app.presentation.model.CockpitActionItem
import io.hrns_now.app.presentation.model.CockpitProjection
import io.hrns_now.core.domain.model.ArtifactReadinessState
import io.hrns_now.core.domain.model.ArtifactsState
import io.hrns_now.core.domain.model.ClosureState
import io.hrns_now.core.domain.model.ExecutionWrapperState
import io.hrns_now.core.domain.model.FileVersion
import io.hrns_now.core.domain.model.OpsValidationState
import io.hrns_now.core.domain.model.QueueBlockedReason
import io.hrns_now.core.domain.model.QueuePointer
import io.hrns_now.core.domain.model.QueueStatus
import io.hrns_now.core.domain.model.SchemaVersion
import io.hrns_now.core.domain.model.StopReason
import io.hrns_now.core.domain.model.UiAction
import io.hrns_now.core.domain.model.WorkflowPhase
import io.hrns_now.core.domain.model.WorkflowQueue
import io.hrns_now.core.domain.model.WorkflowState
import io.hrns_now.core.domain.model.WorkflowStatus
import io.hrns_now.core.domain.policy.ClosureDecision
import io.hrns_now.core.result.StateReadResult
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecoveryProjectionsTest {

    private fun cockpit(allowedActions: List<CockpitActionItem> = emptyList()): CockpitProjection = CockpitProjection(
        projectName = "sample",
        profileLabel = "corp-default",
        dateLabel = "2026-07-27",
        isReadOnlyDay = false,
        isStale = false,
        phaseLabel = "실행 완료",
        statusLabel = "실행 완료",
        queueStatusLabel = "비어 있음",
        activeCardId = null,
        activeSliceId = null,
        authorizedTargetLabel = null,
        stopReasonLabel = null,
        blockedReasonLabel = null,
        artifactItems = emptyList(),
        opsValidationLabel = "통과",
        closureLabel = "미완료",
        executionCompletedLabel = "완료",
        lastSuccessfulReadAtLabel = null,
        lastAttemptAtLabel = null,
        primaryAction = null,
        allowedActions = allowedActions,
        diagnostics = null,
        compatibilityDiagnostics = null,
    )

    private fun healthyState(
        stopReason: StopReason? = null,
        queueBlockedReason: QueueBlockedReason? = null,
        opsValidationPassed: Boolean = true,
    ): WorkflowState = WorkflowState(
        schemaVersion = SchemaVersion(1, 0, "1.0"),
        date = LocalDate.of(2026, 7, 27),
        projectName = "sample-project",
        workspaceRoot = "C:\\workspace",
        repoRoot = "C:\\repo",
        profile = "corp-default",
        requiredNextAction = null,
        phase = WorkflowPhase.ExecutionCompleted,
        status = WorkflowStatus.ExecutionCompleted,
        nextAction = null,
        executionWrapper = ExecutionWrapperState.None,
        stopReason = stopReason,
        blockedReason = null,
        failedReason = null,
        humanActionRequired = false,
        executionCompleted = true,
        closureValidated = false,
        cleanHandoff = false,
        resumeFromStepId = null,
        authorizedTargetFile = null,
        artifacts = ArtifactsState(
            ArtifactReadinessState.Ready,
            ArtifactReadinessState.Ready,
            ArtifactReadinessState.Ready,
            ArtifactReadinessState.Ready,
        ),
        opsValidation = OpsValidationState(passed = opsValidationPassed, validatedAt = null, notes = null),
        closure = ClosureState(isCleanHandoff = false, validated = false, validatedAt = null, validatorNotes = null),
        currentSliceRaw = null,
        sliceQueueRaw = null,
        roleSlicedRaw = null,
        usageGuardRaw = null,
        queue = WorkflowQueue(
            status = QueueStatus.Empty,
            active = QueuePointer(cardId = null, sliceId = null),
            blockedReason = queueBlockedReason,
            lastUpdatedAt = null,
        ),
    )

    private fun success(state: WorkflowState): StateReadResult.Success =
        StateReadResult.Success(state, FileVersion(Instant.EPOCH, 10, "hash"))

    @Test
    fun `문제 없는 State는 activeCard가 없다`() {
        val projection = buildRecoveryProjection(cockpit(), success(healthyState()), ClosureDecision.Allowed, "없음")
        assertNull(projection.activeCard)
    }

    @Test
    fun `usage_limit_blocked는 3분리 카드를 만든다`() {
        val projection = buildRecoveryProjection(
            cockpit(),
            success(healthyState(stopReason = StopReason.UsageLimitBlocked)),
            ClosureDecision.Allowed,
            "없음",
        )
        val card = projection.activeCard
        requireNotNull(card)
        assertTrue(card.whatHappened.contains("사용량"))
        assertTrue(card.preservedRecord.isNotBlank())
        assertTrue(card.allowedNextAction.contains("수동"))
    }

    @Test
    fun `dispatch_metadata_conflict는 재계획만 허용한다고 안내한다`() {
        val projection = buildRecoveryProjection(
            cockpit(),
            success(healthyState(queueBlockedReason = QueueBlockedReason.DispatchMetadataConflict)),
            ClosureDecision.Allowed,
            "없음",
        )
        val card = projection.activeCard
        requireNotNull(card)
        assertTrue(card.allowedNextAction.contains("재계획"))
    }

    @Test
    fun `ops validation 실패는 검증 실패 카드를 만든다`() {
        val projection = buildRecoveryProjection(
            cockpit(),
            success(healthyState(opsValidationPassed = false)),
            ClosureDecision.Allowed,
            "없음",
        )
        val card = projection.activeCard
        requireNotNull(card)
        assertTrue(card.whatHappened.contains("운영 검증"))
    }

    @Test
    fun `state 읽기 실패 카드가 다른 문제보다 우선한다`() {
        val secret = "raw-session-id=super-secret"
        val projection = buildRecoveryProjection(
            cockpit(),
            StateReadResult.Malformed(secret, healthyState(stopReason = StopReason.UsageLimitBlocked)),
            ClosureDecision.Allowed,
            "없음",
        )
        val card = projection.activeCard
        requireNotNull(card)
        assertTrue(card.title.contains("해석 실패"))
        assertTrue(!card.whatHappened.contains(secret))
        assertTrue(!card.preservedRecord.contains(secret))
        assertTrue(!card.allowedNextAction.contains(secret))
    }

    @Test
    fun `Blocked closure decision은 이유를 모두 체크리스트에 담는다`() {
        val projection = buildRecoveryProjection(
            cockpit(),
            success(healthyState()),
            ClosureDecision.Blocked(listOf("이유 A", "이유 B")),
            "없음",
        )
        assertEquals(2, projection.closureChecklistRows.size)
        assertTrue(projection.incompleteHandoffItems.isEmpty())
    }

    @Test
    fun `RequiresExplicitIncompleteHandoff는 항목을 그대로 노출한다`() {
        val projection = buildRecoveryProjection(
            cockpit(),
            success(healthyState()),
            ClosureDecision.RequiresExplicitIncompleteHandoff(listOf("커밋되지 않은 변경: src/Foo.kt")),
            "없음",
        )
        assertEquals(listOf("커밋되지 않은 변경: src/Foo.kt"), projection.incompleteHandoffItems)
    }

    @Test
    fun `actions는 Refresh RunReplan RunClosureValidation만 cockpit에서 옮긴다`() {
        val allowed = listOf(
            CockpitActionItem(UiAction.Refresh, "새로고침", enabled = true),
            CockpitActionItem(UiAction.RunReplan, "재계획 실행", enabled = true),
            CockpitActionItem(UiAction.RunClosureValidation, "마감 검증 실행", enabled = false),
            CockpitActionItem(UiAction.EditRequest, "요청 작성", enabled = true),
        )
        val projection = buildRecoveryProjection(cockpit(allowed), success(healthyState()), ClosureDecision.Allowed, "없음")

        assertEquals(setOf(UiAction.Refresh, UiAction.RunReplan, UiAction.RunClosureValidation), projection.actions.map { it.action }.toSet())
    }
}
