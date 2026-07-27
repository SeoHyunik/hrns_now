package io.hrns_now.core.domain.policy

import io.hrns_now.core.domain.model.ArtifactReadinessState
import io.hrns_now.core.domain.model.ArtifactsState
import io.hrns_now.core.domain.model.ClosureState
import io.hrns_now.core.domain.model.ExecutionWrapperState
import io.hrns_now.core.domain.model.FileVersion
import io.hrns_now.core.domain.model.OpsValidationState
import io.hrns_now.core.domain.model.QueuePointer
import io.hrns_now.core.domain.model.QueueStatus
import io.hrns_now.core.domain.model.RepositoryStatus
import io.hrns_now.core.domain.model.SchemaVersion
import io.hrns_now.core.domain.model.StopReason
import io.hrns_now.core.domain.model.WorkflowPhase
import io.hrns_now.core.domain.model.WorkflowQueue
import io.hrns_now.core.domain.model.WorkflowState
import io.hrns_now.core.domain.model.WorkflowStatus
import io.hrns_now.core.result.StateReadResult
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * `ClosurePolicy` decision table을 고정한다(`doc/claude_prompts/phase5-closure-recovery.md` §1).
 * happy path 하나와, 각 checklist 조건을 하나씩 위반하는 fixture를 검증한다.
 */
class ClosurePolicyTest {

    private val policy = ClosurePolicy()

    private fun closureEligibleState(
        opsValidationPassed: Boolean = true,
        activeCardId: String? = null,
        activeSliceId: String? = null,
        resumeFromStepId: String? = null,
        closureValidated: Boolean = true,
        topLevelClosureValidated: Boolean = true,
        closureIsCleanHandoff: Boolean = true,
        topLevelCleanHandoff: Boolean = closureIsCleanHandoff,
        phase: WorkflowPhase = WorkflowPhase.ClosureValidated,
        status: WorkflowStatus = WorkflowStatus.ExecutionCompleted,
        queueStatus: QueueStatus = QueueStatus.Empty,
        stopReason: StopReason? = null,
        executionWrapper: ExecutionWrapperState = ExecutionWrapperState.None,
        artifactsAllReady: Boolean = true,
    ): WorkflowState {
        val readiness = if (artifactsAllReady) ArtifactReadinessState.Ready else ArtifactReadinessState.Unknown("missing")
        return WorkflowState(
            schemaVersion = SchemaVersion(1, 0, "1.0"),
            date = LocalDate.of(2026, 7, 27),
            projectName = "sample-project",
            workspaceRoot = "C:\\workspace",
            repoRoot = "C:\\repo",
            profile = "corp-default",
            requiredNextAction = null,
            phase = phase,
            status = status,
            nextAction = null,
            executionWrapper = executionWrapper,
            stopReason = stopReason,
            blockedReason = null,
            failedReason = null,
            humanActionRequired = false,
            executionCompleted = true,
            closureValidated = topLevelClosureValidated,
            cleanHandoff = topLevelCleanHandoff,
            resumeFromStepId = resumeFromStepId,
            authorizedTargetFile = null,
            artifacts = ArtifactsState(readiness, readiness, readiness, readiness),
            opsValidation = OpsValidationState(passed = opsValidationPassed, validatedAt = null, notes = null),
            closure = ClosureState(
                isCleanHandoff = closureIsCleanHandoff,
                validated = closureValidated,
                validatedAt = "2026-07-27T10:00:00",
                validatorNotes = null,
            ),
            currentSliceRaw = null,
            sliceQueueRaw = null,
            roleSlicedRaw = null,
            usageGuardRaw = null,
            queue = WorkflowQueue(
                status = queueStatus,
                active = QueuePointer(cardId = activeCardId, sliceId = activeSliceId),
                blockedReason = null,
                lastUpdatedAt = null,
            ),
        )
    }

    private fun success(state: WorkflowState) =
        StateReadResult.Success(state, FileVersion(Instant.EPOCH, 10, "hash"))

    private fun context(
        state: WorkflowState,
        lockHeld: Boolean = false,
        repositoryStatus: RepositoryStatus = RepositoryStatus.Clean,
    ) = ClosureContext(success(state), lockHeld, repositoryStatus)

    @Test
    fun `모든 조건을 충족하고 repository가 clean이면 Allowed다`() {
        val decision = policy.evaluate(context(closureEligibleState()))
        assertEquals(ClosureDecision.Allowed, decision)
    }

    @Test
    fun `State 읽기 실패는 Blocked다`() {
        val decision = policy.evaluate(
            ClosureContext(
                stateRead = StateReadResult.Missing(Path.of("WORKFLOW_STATE.json")),
                lockHeld = false,
                repositoryStatus = RepositoryStatus.Clean,
            ),
        )
        assertIs<ClosureDecision.Blocked>(decision)
    }

    @Test
    fun `알 수 없는 phase status queue stopReason은 각각 Blocked 사유를 만든다`() {
        val unknownPhase = policy.evaluate(context(closureEligibleState(phase = WorkflowPhase.Unknown("x"))))
        val unknownStatus = policy.evaluate(context(closureEligibleState(status = WorkflowStatus.Unknown("x"))))
        val unknownQueue = policy.evaluate(context(closureEligibleState(queueStatus = QueueStatus.Unknown("x"))))
        val unknownStopReason = policy.evaluate(context(closureEligibleState(stopReason = StopReason.Unknown("x"))))

        listOf(unknownPhase, unknownStatus, unknownQueue, unknownStopReason).forEach { decision ->
            assertIs<ClosureDecision.Blocked>(decision)
        }
    }

    @Test
    fun `알 수 없는 execution wrapper는 마감 검증을 fail-closed 한다`() {
        val decision = policy.evaluate(
            context(closureEligibleState(executionWrapper = ExecutionWrapperState.Unknown("future-wrapper"))),
        )

        assertIs<ClosureDecision.Blocked>(decision)
    }

    @Test
    fun `필수 daily 파일이 준비되지 않으면 Blocked다`() {
        val decision = policy.evaluate(context(closureEligibleState(artifactsAllReady = false)))
        val blocked = assertIs<ClosureDecision.Blocked>(decision)
        assertTrue(blocked.reasons.any { it.contains("daily 파일") })
    }

    @Test
    fun `ops validation 미통과는 Blocked다`() {
        val decision = policy.evaluate(context(closureEligibleState(opsValidationPassed = false)))
        val blocked = assertIs<ClosureDecision.Blocked>(decision)
        assertTrue(blocked.reasons.any { it.contains("운영 검증") })
    }

    @Test
    fun `활성 slice가 남아있으면 Blocked다`() {
        val decision = policy.evaluate(
            context(closureEligibleState(activeCardId = "card-1", activeSliceId = "slice-1")),
        )
        val blocked = assertIs<ClosureDecision.Blocked>(decision)
        assertTrue(blocked.reasons.any { it.contains("활성 slice") })
    }

    @Test
    fun `resume_from_step_id가 남아있으면 Blocked다`() {
        val decision = policy.evaluate(context(closureEligibleState(resumeFromStepId = "step-3")))
        val blocked = assertIs<ClosureDecision.Blocked>(decision)
        assertTrue(blocked.reasons.any { it.contains("재개") })
    }

    @Test
    fun `closure validated와 상위 완료 flag가 어긋나면 Blocked다`() {
        val decision = policy.evaluate(
            context(closureEligibleState(closureValidated = true, topLevelClosureValidated = false)),
        )
        val blocked = assertIs<ClosureDecision.Blocked>(decision)
        assertTrue(blocked.reasons.any { it.contains("일치하지") })
    }

    @Test
    fun `closure validated와 is_clean_handoff가 어긋나면 Blocked다`() {
        val decision = policy.evaluate(
            context(closureEligibleState(closureValidated = true, closureIsCleanHandoff = false)),
        )
        val blocked = assertIs<ClosureDecision.Blocked>(decision)
        assertTrue(blocked.reasons.isNotEmpty())
    }

    @Test
    fun `top-level clean_handoff와 closure is_clean_handoff가 어긋나면 Blocked다`() {
        val decision = policy.evaluate(
            context(closureEligibleState(closureIsCleanHandoff = true, topLevelCleanHandoff = false)),
        )
        val blocked = assertIs<ClosureDecision.Blocked>(decision)
        assertTrue(blocked.reasons.any { it.contains("clean_handoff") })
    }

    @Test
    fun `처음 마감 검증을 시도하는 정상 경로 validated false 둘 다는 차단하지 않는다`() {
        val decision = policy.evaluate(
            context(
                closureEligibleState(
                    closureValidated = false,
                    topLevelClosureValidated = false,
                    closureIsCleanHandoff = false,
                ),
            ),
        )
        assertEquals(ClosureDecision.Allowed, decision)
    }

    @Test
    fun `다른 실행이 lock을 보유하면 Blocked다`() {
        val decision = policy.evaluate(context(closureEligibleState(), lockHeld = true))
        val blocked = assertIs<ClosureDecision.Blocked>(decision)
        assertTrue(blocked.reasons.any { it.contains("잠금") })
    }

    @Test
    fun `repository가 dirty면 RequiresExplicitIncompleteHandoff다`() {
        val decision = policy.evaluate(
            context(closureEligibleState(), repositoryStatus = RepositoryStatus.Dirty(listOf("src/Foo.kt"))),
        )
        val requiresAck = assertIs<ClosureDecision.RequiresExplicitIncompleteHandoff>(decision)
        assertTrue(requiresAck.items.any { it.contains("src/Foo.kt") })
    }

    @Test
    fun `repository 상태가 Unknown이면 마감을 막지 않는다`() {
        val decision = policy.evaluate(context(closureEligibleState(), repositoryStatus = RepositoryStatus.Unknown))
        assertEquals(ClosureDecision.Allowed, decision)
    }
}
