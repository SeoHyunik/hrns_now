package io.hrns_now.infra.process

import io.hrns_now.core.domain.model.HarnessCommand
import java.nio.file.Path
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** [HarnessCommandEncoder.encode]가 만드는, `ProcessBuilder`에 그대로 전달할 실행 형태다. */
data class EncodedProcessInvocation(
    val executable: String,
    val arguments: List<String>,
)

/**
 * `HarnessCommand`를 PowerShell 인자 **목록**으로 변환하는 순수 encoder다
 * (`doc/hrns_now_design_pattern.md` §6). shell 문자열을 조립하지 않는다 — 공백/한글/drive-letter
 * 경로가 각각 독립된 argument로 남아 `ProcessBuilder`가 별도 quoting 없이 그대로 전달할 수 있다.
 *
 * 기동 형태는 `powershell.exe -NoProfile -ExecutionPolicy Bypass -File <script> <args...>`로
 * 고정한다. read-only Doctor/ValidateOps는 항상 `-Json`을 덧붙인다.
 */
class HarnessCommandEncoder(
    private val powerShellPath: String? = System.getenv("HRNS_POWERSHELL_PATH"),
) {

    fun encode(command: HarnessCommand): EncodedProcessInvocation =
        EncodedProcessInvocation(
            executable = powerShellPath?.trim()?.takeIf(String::isNotEmpty) ?: "powershell.exe",
            arguments = listOf("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", scriptPath(command)) +
                scriptArguments(command),
        )

    private fun scriptPath(command: HarnessCommand): String =
        when (command) {
            is HarnessCommand.Doctor -> command.kitRoot.resolve("scripts/doctor.ps1").toString()
            is HarnessCommand.ValidateOps -> command.kitRoot.resolve("scripts/validate-ops.ps1").toString()
            is HarnessCommand.BootstrapDay,
            is HarnessCommand.RunPlanning,
            is HarnessCommand.RunReplan,
            is HarnessCommand.RunExecution,
            -> command.kitRoot().resolve("scripts/run-cycle.ps1").toString()
        }

    private fun scriptArguments(command: HarnessCommand): List<String> =
        when (command) {
            is HarnessCommand.Doctor -> buildList {
                add("-KitRoot")
                add(command.kitRoot.toString())
                command.workspaceRoot?.let {
                    add("-WorkspaceRoot")
                    add(it.toString())
                }
                command.projectRoot?.let {
                    add("-ProjectRoot")
                    add(it.toString())
                }
                add("-Date")
                add(command.date.format(DateTimeFormatter.ISO_LOCAL_DATE))
                add("-Json")
            }

            is HarnessCommand.ValidateOps -> buildList {
                add("-WorkspaceRoot")
                add(command.workspaceRoot.toString())
                add("-KitRoot")
                add(command.kitRoot.toString())
                command.profile?.takeIf(String::isNotBlank)?.let {
                    add("-Profile")
                    add(it)
                }
                add("-Date")
                add(command.date.format(DateTimeFormatter.ISO_LOCAL_DATE))
                add("-Json")
            }

            is HarnessCommand.BootstrapDay -> runCycleBaseArguments(
                command.workspaceRoot,
                command.projectRoot,
                command.kitRoot,
                command.profile,
                command.date,
            ) + listOf("-UsePythonSidecars")

            is HarnessCommand.RunPlanning -> runCycleBaseArguments(
                command.workspaceRoot,
                command.projectRoot,
                command.kitRoot,
                command.profile,
                command.date,
            ) + listOf("-RunPlanningWrapper", "-PlanningReason", command.reason.cliValue)

            is HarnessCommand.RunReplan -> runCycleBaseArguments(
                command.workspaceRoot,
                command.projectRoot,
                command.kitRoot,
                command.profile,
                command.date,
            ) + listOf("-RunReplanWrapper", "-ReplanReason", command.reason.cliValue)

            is HarnessCommand.RunExecution -> runCycleBaseArguments(
                command.workspaceRoot,
                command.projectRoot,
                command.kitRoot,
                command.profile,
                command.date,
            ) + listOf("-RunExecutionWrapper", command.wrapper.cliValue)
        }

    /** `run-cycle.ps1`의 실계약 필수/기본 인자(`-WorkspaceRoot`/`-ProjectRoot`/`-KitRoot`/`-Profile`/`-Date`)다. */
    private fun runCycleBaseArguments(
        workspaceRoot: Path,
        projectRoot: Path,
        kitRoot: Path,
        profile: String?,
        date: LocalDate,
    ): List<String> = buildList {
        add("-WorkspaceRoot")
        add(workspaceRoot.toString())
        add("-ProjectRoot")
        add(projectRoot.toString())
        add("-KitRoot")
        add(kitRoot.toString())
        profile?.takeIf(String::isNotBlank)?.let {
            add("-Profile")
            add(it)
        }
        add("-Date")
        add(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
    }

    private fun HarnessCommand.kitRoot(): Path =
        when (this) {
            is HarnessCommand.Doctor -> kitRoot
            is HarnessCommand.ValidateOps -> kitRoot
            is HarnessCommand.BootstrapDay -> kitRoot
            is HarnessCommand.RunPlanning -> kitRoot
            is HarnessCommand.RunReplan -> kitRoot
            is HarnessCommand.RunExecution -> kitRoot
        }
}
