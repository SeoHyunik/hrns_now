package io.hrns_now.core.result

import io.hrns_now.core.domain.model.ArtifactReadinessState
import io.hrns_now.core.domain.model.ArtifactsState
import io.hrns_now.core.domain.model.ClosureState
import io.hrns_now.core.domain.model.ExecutionWrapperState
import io.hrns_now.core.domain.model.OpsValidationState
import io.hrns_now.core.domain.model.QueuePointer
import io.hrns_now.core.domain.model.QueueStatus
import io.hrns_now.core.domain.model.SchemaVersion
import io.hrns_now.core.domain.model.WorkflowPhase
import io.hrns_now.core.domain.model.WorkflowQueue
import io.hrns_now.core.domain.model.WorkflowState
import io.hrns_now.core.domain.model.WorkflowStatus
import java.nio.file.Paths
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StateReadProjectionMapperTest {

    @Test
    fun `malformed는 마지막 정상값을 malformed stale projection으로 보존한다`() {
        val lastKnownGood = workflowState()

        val projection = StateReadResult.Malformed(
            message = "truncated JSON",
            lastKnownGood = lastKnownGood,
        ).toProjection(source = "WORKFLOW_STATE.json")

        assertSame(lastKnownGood, projection.data)
        assertTrue(projection.meta.exists)
        assertTrue(projection.meta.malformed)
        assertTrue(projection.meta.stale)
        assertEquals("truncated JSON", projection.meta.message)
    }

    @Test
    fun `missing은 데이터 없이 exists false로 투영한다`() {
        val projection = StateReadResult.Missing(
            path = Paths.get("WORKFLOW_STATE.json"),
        ).toProjection(source = "WORKFLOW_STATE.json")

        assertNull(projection.data)
        assertFalse(projection.meta.exists)
        assertFalse(projection.meta.malformed)
        assertFalse(projection.meta.stale)
    }

    @Test
    fun `unsupported schema는 실행 잠금 가능한 malformed 계열로 투영한다`() {
        val projection = StateReadResult.UnsupportedSchema("2.0")
            .toProjection(source = "WORKFLOW_STATE.json")

        assertNull(projection.data)
        assertTrue(projection.meta.exists)
        assertTrue(projection.meta.malformed)
        assertFalse(projection.meta.stale)
        assertTrue(projection.meta.message.orEmpty().contains("2.0"))
    }

    private fun workflowState(): WorkflowState =
        WorkflowState(
            schemaVersion = SchemaVersion(major = 1, minor = 0, raw = "1.0"),
            date = LocalDate.of(2026, 6, 26),
            projectName = "sample-project",
            workspaceRoot = "C:\\sample-workspace",
            repoRoot = "C:\\sample-repo",
            profile = "sample-profile",
            requiredNextAction = "recover",
            phase = WorkflowPhase.Execution,
            status = WorkflowStatus.ExecutionBlocked,
            nextAction = "recover",
            executionWrapper = ExecutionWrapperState.Code,
            stopReason = null,
            blockedReason = "blocked",
            failedReason = null,
            humanActionRequired = true,
            executionCompleted = false,
            closureValidated = false,
            cleanHandoff = false,
            resumeFromStepId = "step-1",
            authorizedTargetFile = "C:\\sample-repo\\Sample.kt",
            artifacts = ArtifactsState(
                requestInbox = ArtifactReadinessState.Ready,
                todayStrategy = ArtifactReadinessState.Ready,
                dailyHandoff = ArtifactReadinessState.Ready,
                workflowState = ArtifactReadinessState.Ready,
            ),
            opsValidation = OpsValidationState(
                passed = true,
                validatedAt = null,
                notes = null,
            ),
            closure = ClosureState(
                isCleanHandoff = false,
                validated = false,
                validatedAt = null,
                validatorNotes = null,
            ),
            currentSliceRaw = null,
            sliceQueueRaw = null,
            roleSlicedRaw = null,
            usageGuardRaw = null,
            queue = WorkflowQueue(
                status = QueueStatus.Active,
                active = QueuePointer(cardId = "card-1", sliceId = "slice-1"),
                blockedReason = null,
                lastUpdatedAt = null,
            ),
        )
}
