package io.hrns_now.core.usecase

import io.hrns_now.core.domain.model.ExecutionWrapper
import io.hrns_now.core.domain.model.HarnessCommand
import io.hrns_now.core.domain.model.HarnessProject
import io.hrns_now.core.domain.model.PlanningReason
import io.hrns_now.core.domain.model.ProjectId
import io.hrns_now.core.domain.model.ReplanReason
import io.hrns_now.core.domain.model.RuntimeSource
import io.hrns_now.core.domain.model.UiAction
import io.hrns_now.core.domain.model.WorkspaceDay
import java.nio.file.Path
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** typed [UiAction] ↔ `run-cycle.ps1`/`doctor.ps1`/`validate-ops.ps1` command 매핑을 고정한다. */
class HarnessCommandMapperTest {
    private val mapper = HarnessCommandMapper()
    private val day = WorkspaceDay(Path.of("C:/workspace"), LocalDate.of(2026, 7, 27))
    private val resolvedKitRoot = Path.of("C:/kit")
    private val project = HarnessProject(
        id = ProjectId("sample"),
        displayName = "sample",
        runtimeSource = RuntimeSource.ExternalKit(resolvedKitRoot),
        projectWorkspaceRoot = Path.of("C:/workspace"),
        repositoryRoot = Path.of("C:/repo"),
        profileId = "corp-default",
        lastSelectedDate = null,
        lastDiagnosticsSummary = null,
        lastRunAt = null,
    )

    @Test
    fun `RunClosureValidation은 ValidateClosure command를 만든다`() {
        val command = mapper.map(UiAction.RunClosureValidation, project, resolvedKitRoot, day)

        val closure = assertIs<HarnessCommand.ValidateClosure>(command)
        assertEquals(project.projectWorkspaceRoot, closure.workspaceRoot)
        assertEquals(project.repositoryRoot, closure.projectRoot)
        assertEquals(resolvedKitRoot, closure.kitRoot)
        assertEquals(day.date, closure.date)
    }

    @Test
    fun `RunReplan은 항상 HumanRequestedReplan 사유를 쓴다`() {
        val command = assertIs<HarnessCommand.RunReplan>(mapper.map(UiAction.RunReplan, project, resolvedKitRoot, day))
        assertEquals(ReplanReason.HumanRequestedReplan, command.reason)
    }

    @Test
    fun `RunPlanning은 InitialPlan 사유를 쓴다`() {
        val command = assertIs<HarnessCommand.RunPlanning>(mapper.map(UiAction.RunPlanning, project, resolvedKitRoot, day))
        assertEquals(PlanningReason.InitialPlan, command.reason)
    }

    @Test
    fun `RunCodeSlice RunDocSlice는 각각 code doc wrapper를 만든다`() {
        val code = assertIs<HarnessCommand.RunExecution>(mapper.map(UiAction.RunCodeSlice, project, resolvedKitRoot, day))
        val doc = assertIs<HarnessCommand.RunExecution>(mapper.map(UiAction.RunDocSlice, project, resolvedKitRoot, day))
        assertEquals(ExecutionWrapper.Code, code.wrapper)
        assertEquals(ExecutionWrapper.Doc, doc.wrapper)
    }

    @Test
    fun `지원하지 않는 action은 null을 반환한다`() {
        assertNull(mapper.map(UiAction.ReviewClosure, project, resolvedKitRoot, day))
        assertNull(mapper.map(UiAction.OpenRecoveryCenter, project, resolvedKitRoot, day))
        assertNull(mapper.map(UiAction.EditRequest, project, resolvedKitRoot, day))
    }

    @Test
    fun `command의 kitRoot는 project runtimeSource가 아니라 전달된 resolvedKitRoot를 그대로 쓴다`() {
        val differentRoot = Path.of("D:/other-resolved-kit")

        val command = assertIs<HarnessCommand.Doctor>(mapper.map(UiAction.RunDoctor, project, differentRoot, day))

        assertEquals(differentRoot, command.kitRoot)
    }
}
