package io.hrns_now.app.presentation.mapper

import io.hrns_now.core.domain.model.ArtifactReadinessState
import io.hrns_now.core.domain.model.ArtifactsState
import io.hrns_now.core.domain.model.BoundaryStatus
import io.hrns_now.core.domain.model.ClosureState
import io.hrns_now.core.domain.model.CompatibilityStatus
import io.hrns_now.core.domain.model.ExecutionWrapperState
import io.hrns_now.core.domain.model.FileVersion
import io.hrns_now.core.domain.model.OpsValidationState
import io.hrns_now.core.domain.model.ProcessRunStatus
import io.hrns_now.core.domain.model.QueuePointer
import io.hrns_now.core.domain.model.QueueStatus
import io.hrns_now.core.domain.model.SchemaVersion
import io.hrns_now.core.domain.model.StopReason
import io.hrns_now.core.domain.model.UiAction
import io.hrns_now.core.domain.model.WorkflowPhase
import io.hrns_now.core.domain.model.WorkflowQueue
import io.hrns_now.core.domain.model.WorkflowState
import io.hrns_now.core.domain.model.WorkflowStatus
import io.hrns_now.core.domain.model.WorkspaceDay
import io.hrns_now.core.domain.policy.WorkspaceDaySelection
import io.hrns_now.core.domain.policy.WorkspaceDaySelectionSource
import io.hrns_now.core.result.StateReadResult
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CockpitProjectionAssemblerTest {

    private val assembler = CockpitProjectionAssembler()

    private fun healthyExecutionReadyState(
        authorizedTargetFile: String? = "S:\\workspace\\2026-06-26\\TARGET.md",
        stopReason: StopReason? = null,
        status: WorkflowStatus = WorkflowStatus.ExecutionReady,
        phase: WorkflowPhase = WorkflowPhase.ExecutionReady,
        blockedReason: String? = null,
    ): WorkflowState = WorkflowState(
        schemaVersion = SchemaVersion(1, 0, "1.0"),
        date = LocalDate.of(2026, 6, 26),
        projectName = "sample-project",
        workspaceRoot = "C:\\sample-workspace",
        repoRoot = "C:\\sample-repo",
        profile = "corp-default",
        requiredNextAction = null,
        phase = phase,
        status = status,
        nextAction = null,
        executionWrapper = ExecutionWrapperState.Code,
        stopReason = stopReason,
        blockedReason = blockedReason,
        failedReason = null,
        humanActionRequired = false,
        executionCompleted = false,
        closureValidated = false,
        cleanHandoff = false,
        resumeFromStepId = null,
        authorizedTargetFile = authorizedTargetFile,
        artifacts = ArtifactsState(
            requestInbox = ArtifactReadinessState.Ready,
            todayStrategy = ArtifactReadinessState.Ready,
            dailyHandoff = ArtifactReadinessState.Ready,
            workflowState = ArtifactReadinessState.Ready,
        ),
        opsValidation = OpsValidationState(passed = true, validatedAt = null, notes = null),
        closure = ClosureState(isCleanHandoff = false, validated = false, validatedAt = null, validatorNotes = null),
        currentSliceRaw = null,
        sliceQueueRaw = null,
        roleSlicedRaw = null,
        usageGuardRaw = null,
        queue = WorkflowQueue(
            status = QueueStatus.Active,
            active = QueuePointer(cardId = "card-0000001", sliceId = "slice-0000001"),
            blockedReason = null,
            lastUpdatedAt = null,
        ),
    )

    private fun todaySelection(root: Path = Path.of(".", "sample-workspace")): WorkspaceDaySelection =
        WorkspaceDaySelection(
            workspaceDay = WorkspaceDay(projectWorkspaceRoot = root, date = LocalDate.of(2026, 6, 26)),
            source = WorkspaceDaySelectionSource.Today,
            isReadOnly = false,
        )

    private fun pastSelection(root: Path = Path.of(".", "sample-workspace")): WorkspaceDaySelection =
        WorkspaceDaySelection(
            workspaceDay = WorkspaceDay(projectWorkspaceRoot = root, date = LocalDate.of(2026, 6, 20)),
            source = WorkspaceDaySelectionSource.LatestReadOnlyFallback,
            isReadOnly = true,
        )

    private fun assemble(
        stateRead: StateReadResult,
        daySelection: WorkspaceDaySelection = todaySelection(),
        compatibility: CompatibilityStatus = CompatibilityStatus.Supported,
        boundary: BoundaryStatus = BoundaryStatus.Valid,
        projectConnected: Boolean = true,
    ) = assembler.assemble(
        projectConnected = projectConnected,
        profileLabel = "corp-default",
        daySelection = daySelection,
        stateRead = stateRead,
        compatibility = compatibility,
        boundary = boundary,
        process = ProcessRunStatus.Idle,
        lastSuccessfulReadAtLabel = "12:00:00",
        lastAttemptAtLabel = "12:00:00",
    )

    private fun success(state: WorkflowState = healthyExecutionReadyState()): StateReadResult.Success =
        StateReadResult.Success(state, FileVersion(Instant.EPOCH, 10, "hash"))

    @Test
    fun `Success는 live pointer와 상태를 표시하되 active 종류 미보증으로 실행 CTA를 잠근다`() {
        val projection = assemble(success())

        assertFalse(projection.isStale)
        assertNull(projection.diagnostics)
        assertEquals("sample-project", projection.projectName)
        assertEquals("corp-default", projection.profileLabel)
        assertEquals("2026-06-26", projection.dateLabel)
        assertEquals("card-0000001", projection.activeCardId)
        assertEquals("slice-0000001", projection.activeSliceId)
        assertEquals("S:\\workspace\\2026-06-26\\TARGET.md", projection.authorizedTargetLabel)
        assertEquals("통과", projection.opsValidationLabel)
        assertEquals("미완료", projection.closureLabel)
        assertEquals(4, projection.artifactItems.size)
        assertEquals(UiAction.OpenRecoveryCenter, projection.primaryAction?.action)
        assertFalse(projection.allowedActions.any { it.action == UiAction.RunCodeSlice })
    }

    @Test
    fun `정책 CTA 중 Phase 1C에서 구현된 Refresh만 활성화한다`() {
        val projection = assemble(success())

        assertEquals(UiAction.OpenRecoveryCenter, projection.primaryAction?.action)
        assertFalse(requireNotNull(projection.primaryAction).enabled)
        assertTrue(requireNotNull(projection.allowedActions.single { it.action == UiAction.Refresh }).enabled)
        assertTrue(projection.allowedActions.filterNot { it.action == UiAction.Refresh }.none { it.enabled })
    }

    @Test
    fun `Malformed와 last-known-good이 있으면 stale로 표시하고 실행 CTA는 없다`() {
        val projection = assemble(StateReadResult.Malformed("parse error", healthyExecutionReadyState()))

        assertTrue(projection.isStale)
        assertTrue(requireNotNull(projection.diagnostics).lastKnownGoodPreserved)
        assertEquals("sample-project", projection.projectName)
        assertEquals(UiAction.OpenRecoveryCenter, projection.primaryAction?.action)
        assertFalse(projection.allowedActions.any { it.action == UiAction.RunCodeSlice })
    }

    @Test
    fun `비정상 읽기 결과는 각각 안전한 3분리 진단을 만든다`() {
        val secret = "raw-session-id=super-secret"
        val projections = listOf(
            assemble(StateReadResult.Missing(Path.of("WORKFLOW_STATE.json"))),
            assemble(StateReadResult.EncodingError(secret, null)),
            assemble(StateReadResult.UnsupportedSchema(secret)),
            assemble(StateReadResult.AccessDenied(Path.of("WORKFLOW_STATE.json"))),
        )

        projections.forEach { projection ->
            assertFalse(requireNotNull(projection.diagnostics).lastKnownGoodPreserved)
            assertEquals(UiAction.OpenRecoveryCenter, projection.primaryAction?.action)
            val visible = listOf(
                projection.diagnostics.whatHappened,
                projection.diagnostics.nextStep,
                projection.phaseLabel,
            ).joinToString(" ")
            assertFalse(visible.contains(secret))
        }
        assertEquals("확인 불가", projections.first().phaseLabel)
    }

    @Test
    fun `unknown domain raw 값과 blocked reason 원문을 UI에 노출하지 않는다`() {
        val secret = "raw-session-id=super-secret-token"
        val unknownState = healthyExecutionReadyState().copy(
            phase = WorkflowPhase.Unknown(secret),
            status = WorkflowStatus.Unknown(secret),
            stopReason = StopReason.Unknown(secret),
            blockedReason = secret,
            queue = healthyExecutionReadyState().queue.copy(status = QueueStatus.Unknown(secret)),
        )
        val projection = assemble(success(unknownState))
        val visible = listOfNotNull(
            projection.phaseLabel,
            projection.statusLabel,
            projection.queueStatusLabel,
            projection.stopReasonLabel,
            projection.blockedReasonLabel,
        ).joinToString(" ")

        assertFalse(visible.contains(secret))
        assertEquals("알 수 없음", projection.phaseLabel)
        assertEquals("알 수 없음", projection.statusLabel)
        assertEquals("알 수 없음", projection.queueStatusLabel)
        assertEquals("알 수 없음", projection.stopReasonLabel)
    }

    @Test
    fun `state blocked reason은 정책의 안전 문구로 대체한다`() {
        val secret = "token=must-not-render"
        val state = healthyExecutionReadyState(
            phase = WorkflowPhase.ExecutionBlocked,
            status = WorkflowStatus.ExecutionBlocked,
            blockedReason = secret,
        )
        val projection = assemble(success(state))

        assertEquals("실행이 차단되었습니다.", projection.blockedReasonLabel)
        assertFalse(requireNotNull(projection.blockedReasonLabel).contains(secret))
    }

    @Test
    fun `과거 날짜는 읽기 전용이며 실행 CTA를 허용하지 않는다`() {
        val projection = assemble(success(), daySelection = pastSelection())

        assertTrue(projection.isReadOnlyDay)
        assertEquals(UiAction.OpenToday, projection.primaryAction?.action)
        assertFalse(projection.allowedActions.any { it.action == UiAction.RunCodeSlice })
    }

    @Test
    fun `compatibility Unknown은 호환성 확인을 요구하고 boundary Unknown은 복구로 잠근다`() {
        val compatibilityUnknown = assemble(success(), compatibility = CompatibilityStatus.Unknown)
        val boundaryUnknown = assemble(success(), boundary = BoundaryStatus.Unknown)

        assertEquals(UiAction.ShowCompatibilityIssue, compatibilityUnknown.primaryAction?.action)
        assertEquals(UiAction.OpenRecoveryCenter, boundaryUnknown.primaryAction?.action)
    }

    @Test
    fun `pointer만 있는 live execution_ready는 활성 slice 종류를 추측하지 않는다`() {
        val projection = assemble(success())

        assertEquals(UiAction.OpenRecoveryCenter, projection.primaryAction?.action)
        assertEquals("execution_ready 상태이지만 활성 slice 종류를 확인할 수 없습니다.", projection.blockedReasonLabel)
    }

    @Test
    fun `live execution_blocked dispatch_contract_mismatch 동등 fixture는 복구 센터가 primary다`() {
        val state = healthyExecutionReadyState(
            phase = WorkflowPhase.ExecutionBlocked,
            status = WorkflowStatus.ExecutionBlocked,
            stopReason = StopReason.DispatchContractMismatch,
        )
        val projection = assemble(success(state))

        assertEquals(UiAction.OpenRecoveryCenter, projection.primaryAction?.action)
        assertEquals("Dispatch 계약 불일치", projection.stopReasonLabel)
    }

    @Test
    fun `한글과 공백이 섞인 워크스페이스 경로도 그대로 처리한다`() {
        val root = Path.of(".", "샘플 작업공간 폴더")
        val projection = assemble(success(), daySelection = todaySelection(root))

        assertEquals("2026-06-26", projection.dateLabel)
        assertEquals("sample-project", projection.projectName)
    }

    @Test
    fun `프로젝트가 연결되지 않으면 ConnectProject가 primary이고 실행되지 않는다`() {
        val projection = assemble(success(), projectConnected = false)

        assertEquals(UiAction.ConnectProject, projection.primaryAction?.action)
        assertFalse(requireNotNull(projection.primaryAction).enabled)
    }
}
