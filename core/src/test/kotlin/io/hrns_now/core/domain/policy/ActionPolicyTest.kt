package io.hrns_now.core.domain.policy

import io.hrns_now.core.domain.model.ActionContext
import io.hrns_now.core.domain.model.ActiveSliceKind
import io.hrns_now.core.domain.model.ArtifactReadinessState
import io.hrns_now.core.domain.model.ArtifactsState
import io.hrns_now.core.domain.model.BoundaryStatus
import io.hrns_now.core.domain.model.ClosureState
import io.hrns_now.core.domain.model.CompatibilityStatus
import io.hrns_now.core.domain.model.ExecutionWrapperState
import io.hrns_now.core.domain.model.FileVersion
import io.hrns_now.core.domain.model.OpsValidationState
import io.hrns_now.core.domain.model.ProcessRunStatus
import io.hrns_now.core.domain.model.QueueBlockedReason
import io.hrns_now.core.domain.model.QueuePointer
import io.hrns_now.core.domain.model.QueueStatus
import io.hrns_now.core.domain.model.RecommendedActions
import io.hrns_now.core.domain.model.SchemaVersion
import io.hrns_now.core.domain.model.SelectedDayKind
import io.hrns_now.core.domain.model.StopReason
import io.hrns_now.core.domain.model.UiAction
import io.hrns_now.core.domain.model.WorkflowPhase
import io.hrns_now.core.domain.model.WorkflowQueue
import io.hrns_now.core.domain.model.WorkflowState
import io.hrns_now.core.domain.model.WorkflowStatus
import io.hrns_now.core.result.StateReadResult
import java.nio.file.Paths
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private fun healthyState(
    phase: WorkflowPhase = WorkflowPhase.PlanningRequired,
    status: WorkflowStatus = WorkflowStatus.PlanningRequired,
    executionWrapper: ExecutionWrapperState = ExecutionWrapperState.None,
    stopReason: StopReason? = null,
    queueStatus: QueueStatus = QueueStatus.PlanningRequired,
    queueBlockedReason: QueueBlockedReason? = null,
    activeCardId: String? = null,
    activeSliceId: String? = null,
    authorizedTargetFile: String? = null,
    humanActionRequired: Boolean = false,
    executionCompleted: Boolean = false,
    closureValidated: Boolean = false,
    closureStateValidated: Boolean = false,
    opsValidationPassed: Boolean = true,
    artifactReadiness: ArtifactReadinessState = ArtifactReadinessState.Ready,
): WorkflowState = WorkflowState(
    schemaVersion = SchemaVersion(major = 1, minor = 0, raw = "1.0"),
    date = LocalDate.of(2026, 7, 23),
    projectName = "sample-project",
    workspaceRoot = "C:/sample-workspace",
    repoRoot = "C:/sample-repo",
    profile = "corp-default",
    requiredNextAction = "proceed",
    phase = phase,
    status = status,
    nextAction = "proceed",
    executionWrapper = executionWrapper,
    stopReason = stopReason,
    blockedReason = null,
    failedReason = null,
    humanActionRequired = humanActionRequired,
    executionCompleted = executionCompleted,
    closureValidated = closureValidated,
    cleanHandoff = false,
    resumeFromStepId = null,
    authorizedTargetFile = authorizedTargetFile,
    artifacts = ArtifactsState(artifactReadiness, artifactReadiness, artifactReadiness, artifactReadiness),
    opsValidation = OpsValidationState(
        passed = opsValidationPassed,
        validatedAt = "2026-07-23T00:00:00",
        notes = null,
    ),
    closure = ClosureState(
        isCleanHandoff = false,
        validated = closureStateValidated,
        validatedAt = null,
        validatorNotes = null,
    ),
    currentSliceRaw = null,
    sliceQueueRaw = null,
    roleSlicedRaw = null,
    usageGuardRaw = null,
    queue = WorkflowQueue(
        status = queueStatus,
        active = QueuePointer(cardId = activeCardId, sliceId = activeSliceId),
        blockedReason = queueBlockedReason,
        lastUpdatedAt = null,
    ),
)

private fun executionReadyState(
    executionWrapper: ExecutionWrapperState = ExecutionWrapperState.None,
    stopReason: StopReason? = null,
): WorkflowState = healthyState(
    phase = WorkflowPhase.ExecutionReady,
    status = WorkflowStatus.ExecutionReady,
    executionWrapper = executionWrapper,
    stopReason = stopReason,
    queueStatus = QueueStatus.Active,
    activeCardId = "CARD-001",
    activeSliceId = "SLICE-001",
    authorizedTargetFile = "src/main/kotlin/Sample.kt",
)

private val dummyFileVersion = FileVersion(Instant.EPOCH, 0L, "test-hash")

private fun successContext(
    state: WorkflowState,
    selectedDayKind: SelectedDayKind = SelectedDayKind.Today,
    compatibility: CompatibilityStatus = CompatibilityStatus.Supported,
    boundary: BoundaryStatus = BoundaryStatus.Valid,
    process: ProcessRunStatus = ProcessRunStatus.Idle,
    activeSliceKind: ActiveSliceKind? = null,
): ActionContext = ActionContext(
    projectConnected = true,
    selectedDayKind = selectedDayKind,
    stateRead = StateReadResult.Success(state, dummyFileVersion),
    compatibility = compatibility,
    boundary = boundary,
    process = process,
    activeSliceKind = activeSliceKind,
)

private fun disconnectedContext() = ActionContext(
    projectConnected = false,
    selectedDayKind = null,
    stateRead = null,
    compatibility = CompatibilityStatus.Unknown,
    boundary = BoundaryStatus.Unknown,
    process = ProcessRunStatus.Idle,
    activeSliceKind = null,
)

private fun noDayContext() = disconnectedContext().copy(projectConnected = true)

private data class DecisionCase(
    val name: String,
    val context: ActionContext,
    val expectedPrimary: UiAction,
    val expectedAllowed: Set<UiAction>,
)

class ActionPolicyTest {

    private val policy = ActionPolicy()
    private val recovery = setOf(UiAction.OpenRecoveryCenter, UiAction.Refresh)

    @Test
    fun `부록 B 결정표 전 행은 primary와 allowed가 정확히 일치한다`() {
        val cases = listOf(
            DecisionCase(
                "프로젝트 미연결",
                disconnectedContext(),
                UiAction.ConnectProject,
                setOf(UiAction.ConnectProject),
            ),
            DecisionCase(
                "날짜 미선택",
                noDayContext(),
                UiAction.SelectWorkspaceDay,
                setOf(UiAction.SelectWorkspaceDay, UiAction.RunDoctor, UiAction.RunOpsValidation),
            ),
            DecisionCase(
                "과거 날짜",
                successContext(healthyState(), selectedDayKind = SelectedDayKind.Past),
                UiAction.OpenToday,
                setOf(UiAction.OpenToday, UiAction.Refresh, UiAction.RunDoctor, UiAction.RunOpsValidation),
            ),
            DecisionCase(
                "request_intake_pending",
                successContext(healthyState(phase = WorkflowPhase.RequestIntakePending, status = WorkflowStatus.RequestIntakePending)),
                UiAction.EditRequest,
                setOf(UiAction.EditRequest, UiAction.Refresh),
            ),
            DecisionCase(
                "no_request",
                successContext(healthyState(phase = WorkflowPhase.RequestIntakePending, status = WorkflowStatus.NoRequest)),
                UiAction.EditRequest,
                setOf(UiAction.EditRequest, UiAction.Refresh),
            ),
            DecisionCase(
                "planning_required",
                successContext(healthyState()),
                UiAction.RunPlanning,
                setOf(UiAction.RunPlanning, UiAction.Refresh),
            ),
            DecisionCase(
                "planning_completed",
                successContext(healthyState(phase = WorkflowPhase.PlanningCompleted, status = WorkflowStatus.PlanningCompleted, queueStatus = QueueStatus.Completed)),
                UiAction.ReviewPlan,
                setOf(UiAction.ReviewPlan, UiAction.RunReplan, UiAction.Refresh),
            ),
            DecisionCase(
                "execution_ready code",
                successContext(executionReadyState(), activeSliceKind = ActiveSliceKind.Code),
                UiAction.RunCodeSlice,
                setOf(UiAction.RunCodeSlice, UiAction.Refresh),
            ),
            DecisionCase(
                "execution_ready doc",
                successContext(executionReadyState(), activeSliceKind = ActiveSliceKind.Doc),
                UiAction.RunDocSlice,
                setOf(UiAction.RunDocSlice, UiAction.Refresh),
            ),
            DecisionCase(
                "validation-only",
                successContext(executionReadyState().copy(authorizedTargetFile = null), activeSliceKind = ActiveSliceKind.ValidationOnly),
                UiAction.RunValidationSlice,
                setOf(UiAction.RunValidationSlice, UiAction.Refresh),
            ),
            DecisionCase(
                "execution_blocked",
                successContext(healthyState(phase = WorkflowPhase.ExecutionBlocked, status = WorkflowStatus.ExecutionBlocked, queueStatus = QueueStatus.Blocked)),
                UiAction.OpenRecoveryCenter,
                recovery,
            ),
            DecisionCase(
                "manual_prerequisite_required",
                successContext(healthyState(phase = WorkflowPhase.AwaitingManual, status = WorkflowStatus.ManualPrerequisiteRequired, queueStatus = QueueStatus.Blocked, humanActionRequired = true)),
                UiAction.OpenRecoveryCenter,
                recovery,
            ),
            DecisionCase(
                "usage_limit_blocked",
                successContext(healthyState(phase = WorkflowPhase.PlanningBlocked, status = WorkflowStatus.UsageLimitBlocked, queueStatus = QueueStatus.Blocked, stopReason = StopReason.UsageLimitBlocked, humanActionRequired = true)),
                UiAction.OpenRecoveryCenter,
                recovery,
            ),
            DecisionCase(
                "context limit",
                successContext(executionReadyState(stopReason = StopReason.ClaudeContextLimit), activeSliceKind = ActiveSliceKind.Code),
                UiAction.OpenRecoveryCenter,
                recovery,
            ),
            DecisionCase(
                "dispatch metadata conflict",
                successContext(healthyState(queueStatus = QueueStatus.Blocked, queueBlockedReason = QueueBlockedReason.DispatchMetadataConflict)),
                UiAction.RunReplan,
                setOf(UiAction.RunReplan, UiAction.Refresh),
            ),
            DecisionCase(
                "execution_completed",
                successContext(healthyState(phase = WorkflowPhase.ExecutionCompleted, status = WorkflowStatus.ExecutionCompleted, queueStatus = QueueStatus.Completed, executionCompleted = true)),
                UiAction.ReviewClosure,
                setOf(UiAction.ReviewClosure, UiAction.RunClosureValidation, UiAction.Refresh),
            ),
            DecisionCase(
                "closure_validated",
                successContext(healthyState(phase = WorkflowPhase.ClosureValidated, status = WorkflowStatus.ExecutionCompleted, queueStatus = QueueStatus.Completed, executionCompleted = true, closureValidated = true, closureStateValidated = true)),
                UiAction.BootstrapDay,
                setOf(UiAction.BootstrapDay, UiAction.Refresh),
            ),
            DecisionCase(
                "invalid state",
                successContext(healthyState()).copy(stateRead = StateReadResult.Malformed("broken", null)),
                UiAction.OpenRecoveryCenter,
                setOf(UiAction.OpenRecoveryCenter, UiAction.Refresh, UiAction.RunDoctor, UiAction.RunOpsValidation),
            ),
            DecisionCase(
                "compatibility mismatch",
                successContext(healthyState(), compatibility = CompatibilityStatus.Unsupported),
                UiAction.ShowCompatibilityIssue,
                setOf(UiAction.ShowCompatibilityIssue, UiAction.Refresh),
            ),
            DecisionCase(
                "process lock",
                successContext(healthyState(), process = ProcessRunStatus.Locked),
                UiAction.ViewExecutionStatus,
                setOf(UiAction.ViewExecutionStatus, UiAction.Refresh),
            ),
        )

        cases.forEach { case ->
            val result = policy.recommend(case.context)
            assertEquals(case.expectedPrimary, result.primary, "${case.name} primary")
            assertEquals(case.expectedAllowed, result.allowed, "${case.name} allowed")
        }
    }

    @Test
    fun `모든 알려진 blocking stop reason은 execution_ready여도 fail-closed한다`() {
        val reasons = listOf(
            StopReason.UsageLimitBlocked,
            StopReason.ClaudeContextLimit,
            StopReason.ClaudeCallTimeout,
            StopReason.ClaudeResponseEmpty,
            StopReason.ClaudeResponseTooShort,
            StopReason.BudgetMaxTurns,
            StopReason.BudgetOrManualStop,
            StopReason.TransientClaudeOverloaded,
            StopReason.DispatchContractMismatch,
            StopReason.ManualPrerequisiteRequired,
            StopReason.RoleSlicedWrapperException,
        )

        reasons.forEach { reason ->
            val result = policy.recommend(
                successContext(executionReadyState(stopReason = reason), activeSliceKind = ActiveSliceKind.Code),
            )
            assertEquals(UiAction.OpenRecoveryCenter, result.primary, reason.toString())
            assertEquals(recovery, result.allowed, reason.toString())
        }
    }

    @Test
    fun `execution_ready는 active queue pointer와 authorized target과 ops validation을 모두 요구한다`() {
        val invalidStates = listOf(
            executionReadyState().copy(queue = executionReadyState().queue.copy(status = QueueStatus.Empty)),
            executionReadyState().copy(queue = executionReadyState().queue.copy(active = QueuePointer(null, "SLICE-001"))),
            executionReadyState().copy(queue = executionReadyState().queue.copy(active = QueuePointer("CARD-001", null))),
            executionReadyState().copy(authorizedTargetFile = null),
            executionReadyState().copy(authorizedTargetFile = "   "),
            executionReadyState().copy(opsValidation = executionReadyState().opsValidation.copy(passed = false)),
            executionReadyState().copy(phase = WorkflowPhase.Execution),
        )

        invalidStates.forEachIndexed { index, state ->
            val result = policy.recommend(successContext(state, activeSliceKind = ActiveSliceKind.Code))
            assertEquals(UiAction.OpenRecoveryCenter, result.primary, "invalid execution contract #$index")
            assertEquals(recovery, result.allowed, "invalid execution contract #$index")
        }
    }

    @Test
    fun `human action과 완료 flag 불일치는 mutating action을 열지 않는다`() {
        val humanRequired = policy.recommend(
            successContext(executionReadyState().copy(humanActionRequired = true), activeSliceKind = ActiveSliceKind.Code),
        )
        assertEquals(recovery, humanRequired.allowed)

        val alreadyCompleted = policy.recommend(
            successContext(executionReadyState().copy(executionCompleted = true), activeSliceKind = ActiveSliceKind.Code),
        )
        assertEquals(UiAction.ReviewClosure, alreadyCompleted.primary)
        assertEquals(setOf(UiAction.ReviewClosure, UiAction.Refresh), alreadyCompleted.allowed)

        val inconsistentClosure = policy.recommend(
            successContext(executionReadyState().copy(closureValidated = true), activeSliceKind = ActiveSliceKind.Code),
        )
        assertEquals(recovery, inconsistentClosure.allowed)
    }

    @Test
    fun `알 수 없는 queue 차단 사유와 domain 값은 fail-closed한다`() {
        val contexts = listOf(
            successContext(healthyState(queueBlockedReason = QueueBlockedReason.Other("new-secret-reason"))),
            successContext(healthyState(phase = WorkflowPhase.Unknown("new-secret-phase"))),
            successContext(healthyState(status = WorkflowStatus.Unknown("new-secret-status"))),
            successContext(healthyState(queueStatus = QueueStatus.Unknown("new-secret-queue"))),
            successContext(healthyState(executionWrapper = ExecutionWrapperState.Unknown("new-secret-wrapper"))),
            successContext(healthyState(stopReason = StopReason.Unknown("new-secret-stop"))),
            successContext(healthyState(artifactReadiness = ArtifactReadinessState.Unknown("new-secret-artifact"))),
            successContext(healthyState(), activeSliceKind = ActiveSliceKind.Unknown("new-secret-slice")),
        )

        contexts.forEachIndexed { index, context ->
            val result = policy.recommend(context)
            assertEquals(UiAction.OpenRecoveryCenter, result.primary, "unknown #$index")
            assertEquals(recovery, result.allowed, "unknown #$index")
            assertFalse(result.blockedReason.orEmpty().contains("new-secret"), "unknown raw leaked #$index")
        }
    }

    @Test
    fun `파서 오류와 미지원 schema 원문은 사용자 blockedReason에 노출하지 않는다`() {
        val reads = listOf<StateReadResult>(
            StateReadResult.Malformed("raw-session-id=secret", null),
            StateReadResult.EncodingError("token=secret", null),
            StateReadResult.UnsupportedSchema("secret-schema-value"),
            StateReadResult.Missing(Paths.get("WORKFLOW_STATE.json")),
        )

        reads.forEach { read ->
            val result = policy.recommend(successContext(healthyState()).copy(stateRead = read))
            val reason = assertNotNull(result.blockedReason)
            assertFalse(reason.contains("secret"))
            assertEquals(UiAction.OpenRecoveryCenter, result.primary)
        }
    }

    @Test
    fun `validation-only는 fake wrapper 없이 별도 typed action만 허용한다`() {
        val state = executionReadyState(executionWrapper = ExecutionWrapperState.None).copy(authorizedTargetFile = null)
        val result = policy.recommend(successContext(state, activeSliceKind = ActiveSliceKind.ValidationOnly))

        assertEquals(UiAction.RunValidationSlice, result.primary)
        assertEquals(setOf(UiAction.RunValidationSlice, UiAction.Refresh), result.allowed)
        assertFalse(UiAction.RunCodeSlice in result.allowed)
        assertFalse(UiAction.RunDocSlice in result.allowed)
    }

    @Test
    fun `live execution_blocked dispatch mismatch fixture는 Recovery Center만 추천한다`() {
        val liveEquivalent = healthyState(
            phase = WorkflowPhase.Execution,
            status = WorkflowStatus.ExecutionBlocked,
            executionWrapper = ExecutionWrapperState.Code,
            stopReason = StopReason.DispatchContractMismatch,
            queueStatus = QueueStatus.Active,
            activeCardId = "CARD-LIVE",
            activeSliceId = "SLICE-LIVE",
            authorizedTargetFile = "src/live/Target.kt",
            humanActionRequired = true,
        )

        val result = policy.recommend(successContext(liveEquivalent, activeSliceKind = ActiveSliceKind.Code))
        assertEquals(UiAction.OpenRecoveryCenter, result.primary)
        assertEquals(recovery, result.allowed)
    }

    @Test
    fun `RecommendedActions는 primary가 allowed에 없으면 생성되지 않는다`() {
        assertFailsWith<IllegalArgumentException> {
            RecommendedActions(
                primary = UiAction.RunPlanning,
                allowed = setOf(UiAction.Refresh),
                blockedReason = null,
            )
        }
    }

    @Test
    fun `과거 날짜는 read-only action만 허용한다`() {
        val result = policy.recommend(
            successContext(
                executionReadyState(),
                selectedDayKind = SelectedDayKind.Past,
                activeSliceKind = ActiveSliceKind.Code,
            ),
        )
        assertEquals(UiAction.OpenToday, result.primary)
        assertFalse(UiAction.RunCodeSlice in result.allowed)
        assertFalse(UiAction.RunPlanning in result.allowed)
        assertTrue(UiAction.Refresh in result.allowed)
    }

    @Test
    fun `unknown compatibility boundary와 running process도 fail-closed한다`() {
        listOf(CompatibilityStatus.Unsupported, CompatibilityStatus.Unknown).forEach { compatibility ->
            val result = policy.recommend(successContext(healthyState(), compatibility = compatibility))
            assertEquals(UiAction.ShowCompatibilityIssue, result.primary)
            assertEquals(setOf(UiAction.ShowCompatibilityIssue, UiAction.Refresh), result.allowed)
        }

        listOf(BoundaryStatus.Invalid, BoundaryStatus.Unknown).forEach { boundary ->
            val result = policy.recommend(successContext(healthyState(), boundary = boundary))
            assertEquals(UiAction.OpenRecoveryCenter, result.primary)
            assertEquals(recovery, result.allowed)
        }

        listOf(ProcessRunStatus.Running, ProcessRunStatus.Locked).forEach { process ->
            val result = policy.recommend(successContext(healthyState(), process = process))
            assertEquals(UiAction.ViewExecutionStatus, result.primary)
            assertEquals(setOf(UiAction.ViewExecutionStatus, UiAction.Refresh), result.allowed)
        }
    }
}
