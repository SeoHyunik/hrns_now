package io.hrns_now.infra.process

import io.hrns_now.core.domain.model.HarnessCommand
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
}
