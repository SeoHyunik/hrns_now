package io.hrns_now.infra.process

import io.hrns_now.core.domain.model.ExecutionWrapper
import io.hrns_now.core.domain.model.HarnessCommand
import io.hrns_now.core.domain.model.PlanningReason
import io.hrns_now.core.domain.model.ReplanReason
import java.nio.file.Path
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HarnessCommandEncoderTest {

    private val encoder = HarnessCommandEncoder()

    @Test
    fun `Doctor는 KitRoot ProjectRoot WorkspaceRoot Date Json을 목록 인자로 만든다`() {
        val command = HarnessCommand.Doctor(
            kitRoot = Path.of("D:/harness-kit"),
            workspaceRoot = Path.of("D:/harness-workspaces/sample"),
            projectRoot = Path.of("S:/repo/sample"),
            date = LocalDate.of(2026, 7, 27),
        )

        val invocation = encoder.encode(command)

        assertEquals("powershell.exe", invocation.executable)
        assertEquals(
            listOf(
                "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
                Path.of("D:/harness-kit/scripts/doctor.ps1").toString(),
                "-KitRoot", "D:\\harness-kit",
                "-WorkspaceRoot", "D:\\harness-workspaces\\sample",
                "-ProjectRoot", "S:\\repo\\sample",
                "-Date", "2026-07-27",
                "-Json",
            ),
            invocation.arguments,
        )
    }

    @Test
    fun `Doctor는 workspaceRoot projectRoot가 없으면 해당 인자를 생략한다`() {
        val command = HarnessCommand.Doctor(
            kitRoot = Path.of("D:/harness-kit"),
            workspaceRoot = null,
            projectRoot = null,
            date = LocalDate.of(2026, 7, 27),
        )

        val invocation = encoder.encode(command)

        assertTrue("-WorkspaceRoot" !in invocation.arguments)
        assertTrue("-ProjectRoot" !in invocation.arguments)
        assertTrue("-Json" in invocation.arguments)
    }

    @Test
    fun `ValidateOps는 WorkspaceRoot KitRoot Profile Date Json을 목록 인자로 만든다`() {
        val command = HarnessCommand.ValidateOps(
            workspaceRoot = Path.of("D:/harness-workspaces/sample"),
            kitRoot = Path.of("D:/harness-kit"),
            profile = "corp-default",
            date = LocalDate.of(2026, 7, 27),
        )

        val invocation = encoder.encode(command)

        assertEquals(
            listOf(
                "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
                Path.of("D:/harness-kit/scripts/validate-ops.ps1").toString(),
                "-WorkspaceRoot", "D:\\harness-workspaces\\sample",
                "-KitRoot", "D:\\harness-kit",
                "-Profile", "corp-default",
                "-Date", "2026-07-27",
                "-Json",
            ),
            invocation.arguments,
        )
    }

    @Test
    fun `ValidateOps는 profile이 공백이면 Profile 인자를 생략한다`() {
        val command = HarnessCommand.ValidateOps(
            workspaceRoot = Path.of("D:/harness-workspaces/sample"),
            kitRoot = Path.of("D:/harness-kit"),
            profile = "   ",
            date = LocalDate.of(2026, 7, 27),
        )

        val invocation = encoder.encode(command)

        assertTrue("-Profile" !in invocation.arguments)
    }

    @Test
    fun `명시된 PowerShell 경로를 powershell exe 기본값보다 우선한다`() {
        val command = HarnessCommand.Doctor(
            kitRoot = Path.of("D:/harness-kit"),
            workspaceRoot = null,
            projectRoot = null,
            date = LocalDate.of(2026, 7, 27),
        )

        val invocation = HarnessCommandEncoder("D:/Portable PowerShell/powershell.exe").encode(command)

        assertEquals("D:/Portable PowerShell/powershell.exe", invocation.executable)
    }
    @Test
    fun `공백과 한글이 섞인 경로도 하나의 argument로 보존한다`() {
        val command = HarnessCommand.Doctor(
            kitRoot = Path.of("D:/harness-kit"),
            workspaceRoot = Path.of("D:/작업 공간 폴더/샘플 1"),
            projectRoot = null,
            date = LocalDate.of(2026, 7, 27),
        )

        val invocation = encoder.encode(command)

        assertEquals("D:\\작업 공간 폴더\\샘플 1", invocation.arguments[invocation.arguments.indexOf("-WorkspaceRoot") + 1])
    }

    @Test
    fun `OnboardProject는 enter-project 스크립트에 ProjectRoot WorkspaceRoot KitRoot Profile Date만 인자로 만든다`() {
        val command = HarnessCommand.OnboardProject(
            workspaceRoot = Path.of("D:/harness-workspaces/sample"),
            projectRoot = Path.of("S:/repo/sample"),
            kitRoot = Path.of("D:/harness-kit"),
            profile = "corp-default",
            date = LocalDate.of(2026, 7, 27),
        )

        val invocation = encoder.encode(command)

        assertEquals(Path.of("D:/harness-kit/scripts/enter-project.ps1").toString(), invocation.arguments[4])
        assertEquals(
            listOf(
                "-ProjectRoot", "S:\\repo\\sample",
                "-WorkspaceRoot", "D:\\harness-workspaces\\sample",
                "-KitRoot", "D:\\harness-kit",
                "-Profile", "corp-default",
                "-Date", "2026-07-27",
            ),
            invocation.arguments.drop(5),
        )
        listOf("-Force", "-RunDoctor", "-MaterializeSubagents", "-AgentNames").forEach { forbidden ->
            assertTrue(forbidden !in invocation.arguments, "금지된 스위치 $forbidden 가 포함되면 안 된다")
        }
    }

    @Test
    fun `OnboardProject는 profile이 공백이면 Profile 인자를 생략한다`() {
        val command = HarnessCommand.OnboardProject(
            workspaceRoot = Path.of("D:/harness-workspaces/sample"),
            projectRoot = Path.of("S:/repo/sample"),
            kitRoot = Path.of("D:/harness-kit"),
            profile = "   ",
            date = LocalDate.of(2026, 7, 27),
        )

        val invocation = encoder.encode(command)

        assertTrue("-Profile" !in invocation.arguments)
    }

    @Test
    fun `BootstrapDay는 run-cycle 스크립트에 UsePythonSidecars만 붙이고 wrapper 인자를 만들지 않는다`() {
        val command = HarnessCommand.BootstrapDay(
            workspaceRoot = Path.of("D:/harness-workspaces/sample"),
            projectRoot = Path.of("S:/repo/sample"),
            kitRoot = Path.of("D:/harness-kit"),
            profile = "corp-default",
            date = LocalDate.of(2026, 7, 27),
        )

        val invocation = encoder.encode(command)

        assertEquals(Path.of("D:/harness-kit/scripts/run-cycle.ps1").toString(), invocation.arguments[4])
        assertEquals(
            listOf(
                "-WorkspaceRoot", "D:\\harness-workspaces\\sample",
                "-ProjectRoot", "S:\\repo\\sample",
                "-KitRoot", "D:\\harness-kit",
                "-Profile", "corp-default",
                "-Date", "2026-07-27",
                "-UsePythonSidecars",
            ),
            invocation.arguments.drop(5),
        )
        assertTrue("-RunPlanningWrapper" !in invocation.arguments)
        assertTrue("-RunReplanWrapper" !in invocation.arguments)
        assertTrue("-RunExecutionWrapper" !in invocation.arguments)
    }

    @Test
    fun `RunPlanning은 RunPlanningWrapper와 PlanningReason을 붙인다`() {
        val command = HarnessCommand.RunPlanning(
            workspaceRoot = Path.of("D:/harness-workspaces/sample"),
            projectRoot = Path.of("S:/repo/sample"),
            kitRoot = Path.of("D:/harness-kit"),
            profile = "corp-default",
            date = LocalDate.of(2026, 7, 27),
            reason = PlanningReason.InitialPlan,
        )

        val invocation = encoder.encode(command)

        assertEquals(
            listOf("-RunPlanningWrapper", "-PlanningReason", "initial-plan"),
            invocation.arguments.takeLast(3),
        )
    }

    @Test
    fun `RunReplan은 RunReplanWrapper와 ReplanReason을 붙이고 빈 reason을 만들지 않는다`() {
        val command = HarnessCommand.RunReplan(
            workspaceRoot = Path.of("D:/harness-workspaces/sample"),
            projectRoot = Path.of("S:/repo/sample"),
            kitRoot = Path.of("D:/harness-kit"),
            profile = "corp-default",
            date = LocalDate.of(2026, 7, 27),
            reason = ReplanReason.HumanRequestedReplan,
        )

        val invocation = encoder.encode(command)

        assertEquals(
            listOf("-RunReplanWrapper", "-ReplanReason", "human-requested-replan"),
            invocation.arguments.takeLast(3),
        )
    }

    @Test
    fun `RunExecution code는 RunExecutionWrapper code를 붙인다`() {
        val command = HarnessCommand.RunExecution(
            workspaceRoot = Path.of("D:/harness-workspaces/sample"),
            projectRoot = Path.of("S:/repo/sample"),
            kitRoot = Path.of("D:/harness-kit"),
            profile = "corp-default",
            date = LocalDate.of(2026, 7, 27),
            wrapper = ExecutionWrapper.Code,
        )

        val invocation = encoder.encode(command)

        assertEquals(listOf("-RunExecutionWrapper", "code"), invocation.arguments.takeLast(2))
    }

    @Test
    fun `RunExecution doc은 RunExecutionWrapper doc을 붙인다`() {
        val command = HarnessCommand.RunExecution(
            workspaceRoot = Path.of("D:/harness-workspaces/sample"),
            projectRoot = Path.of("S:/repo/sample"),
            kitRoot = Path.of("D:/harness-kit"),
            profile = "corp-default",
            date = LocalDate.of(2026, 7, 27),
            wrapper = ExecutionWrapper.Doc,
        )

        val invocation = encoder.encode(command)

        assertEquals(listOf("-RunExecutionWrapper", "doc"), invocation.arguments.takeLast(2))
    }

    @Test
    fun `run-cycle 계열 command도 공백과 한글 경로를 하나의 argument로 보존한다`() {
        val command = HarnessCommand.RunPlanning(
            workspaceRoot = Path.of("D:/작업 공간 폴더/샘플 1"),
            projectRoot = Path.of("S:/repo/sample"),
            kitRoot = Path.of("D:/harness-kit"),
            profile = null,
            date = LocalDate.of(2026, 7, 27),
        )

        val invocation = encoder.encode(command)

        assertEquals("D:\\작업 공간 폴더\\샘플 1", invocation.arguments[invocation.arguments.indexOf("-WorkspaceRoot") + 1])
        assertTrue("-Profile" !in invocation.arguments)
    }

    @Test
    fun `ValidateClosure는 run-cycle 스크립트에 ValidateForClosure만 붙이고 wrapper 인자를 만들지 않는다`() {
        val command = HarnessCommand.ValidateClosure(
            workspaceRoot = Path.of("D:/harness-workspaces/sample"),
            projectRoot = Path.of("S:/repo/sample"),
            kitRoot = Path.of("D:/harness-kit"),
            profile = "corp-default",
            date = LocalDate.of(2026, 7, 27),
        )

        val invocation = encoder.encode(command)

        assertEquals(Path.of("D:/harness-kit/scripts/run-cycle.ps1").toString(), invocation.arguments[4])
        assertEquals(
            listOf(
                "-WorkspaceRoot", "D:\\harness-workspaces\\sample",
                "-ProjectRoot", "S:\\repo\\sample",
                "-KitRoot", "D:\\harness-kit",
                "-Profile", "corp-default",
                "-Date", "2026-07-27",
                "-ValidateForClosure",
            ),
            invocation.arguments.drop(5),
        )
        assertTrue("-RunPlanningWrapper" !in invocation.arguments)
        assertTrue("-RunReplanWrapper" !in invocation.arguments)
        assertTrue("-RunExecutionWrapper" !in invocation.arguments)
        assertTrue("-UsePythonSidecars" !in invocation.arguments)
    }
}
