package io.hrns_now.infra.process

import io.hrns_now.core.domain.model.ExecutionWrapper
import io.hrns_now.core.domain.model.HarnessCommand
import io.hrns_now.core.domain.model.PlanningReason
import io.hrns_now.core.domain.model.ProcessCancellationToken
import io.hrns_now.core.result.HarnessCheckSeverity
import io.hrns_now.core.result.HarnessOverallStatus
import io.hrns_now.core.result.ProcessRunResult
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 실제 `powershell.exe`로 stub `doctor.ps1`을 기동해 [PowerShellHarnessAdapter]의 JSON 계약
 * 파싱을 real fixture로 검증한다(mock JSON 문자열만 테스트하지 않는다).
 */
class PowerShellHarnessAdapterTest {

    private val adapter = PowerShellHarnessAdapter()

    private fun tempKitRoot(): Path {
        val dir = Files.createTempDirectory("hrns-ps-adapter-test")
        Files.createDirectories(dir.resolve("scripts"))
        return dir
    }

    private fun writeDoctorStub(kitRoot: Path, body: String) {
        val script = """
            param([string]${'$'}KitRoot,[string]${'$'}WorkspaceRoot,[string]${'$'}ProjectRoot,[string]${'$'}Date,[switch]${'$'}Json)
            $body
        """.trimIndent()
        Files.write(kitRoot.resolve("scripts/doctor.ps1"), script.toByteArray(StandardCharsets.UTF_8))
    }

    private fun doctorCommand(kitRoot: Path) = HarnessCommand.Doctor(
        kitRoot = kitRoot,
        workspaceRoot = null,
        projectRoot = null,
        date = LocalDate.of(2026, 7, 27),
    )

    @Test
    fun `정상 JSON 계약을 typed contract로 파싱한다`() = runBlocking {
        withTimeout(15_000) {
            val kitRoot = tempKitRoot()
            writeDoctorStub(
                kitRoot,
                """
                ${'$'}payload = [pscustomobject]@{
                    contract_version = '1.0'
                    overall = 'ok'
                    checks = @([pscustomobject]@{ id = 'check_001'; severity = 'info'; message = 'all good' })
                }
                Write-Output (${'$'}payload | ConvertTo-Json -Depth 10)
                exit 0
                """.trimIndent(),
            )

            val result = adapter.execute(doctorCommand(kitRoot), Duration.ofSeconds(10), ProcessCancellationToken())

            val completed = assertIs<ProcessRunResult.Completed>(result)
            assertEquals(0, completed.exitCode)
            val contract = completed.contract
            assertTrue(contract != null)
            assertEquals(HarnessOverallStatus.Ok, contract!!.overall)
            assertEquals("check_001", contract.checks.single().id)
            assertEquals(HarnessCheckSeverity.Info, contract.checks.single().severity)
        }
    }

    @Test
    fun `알려지지 않은 overall severity 값은 Unknown raw로 흡수한다`() = runBlocking {
        withTimeout(15_000) {
            val kitRoot = tempKitRoot()
            writeDoctorStub(
                kitRoot,
                """
                ${'$'}payload = [pscustomobject]@{
                    contract_version = '1.0'
                    overall = 'mystery'
                    checks = @([pscustomobject]@{ id = 'check_001'; severity = 'mystery-severity'; message = 'x' })
                }
                Write-Output (${'$'}payload | ConvertTo-Json -Depth 10)
                exit 0
                """.trimIndent(),
            )

            val result = adapter.execute(doctorCommand(kitRoot), Duration.ofSeconds(10), ProcessCancellationToken())

            val completed = assertIs<ProcessRunResult.Completed>(result)
            val contract = completed.contract!!
            assertEquals(HarnessOverallStatus.Unknown("mystery"), contract.overall)
            assertEquals(HarnessCheckSeverity.Unknown("mystery-severity"), contract.checks.single().severity)
        }
    }

    @Test
    fun `exit code가 non-zero이고 overall fail이면 그대로 반영한다`() = runBlocking {
        withTimeout(15_000) {
            val kitRoot = tempKitRoot()
            writeDoctorStub(
                kitRoot,
                """
                ${'$'}payload = [pscustomobject]@{
                    contract_version = '1.0'
                    overall = 'fail'
                    checks = @([pscustomobject]@{ id = 'kit_root_missing'; severity = 'error'; message = 'missing' })
                }
                Write-Output (${'$'}payload | ConvertTo-Json -Depth 10)
                exit 1
                """.trimIndent(),
            )

            val result = adapter.execute(doctorCommand(kitRoot), Duration.ofSeconds(10), ProcessCancellationToken())

            val completed = assertIs<ProcessRunResult.Completed>(result)
            assertEquals(1, completed.exitCode)
            assertEquals(HarnessOverallStatus.Fail, completed.contract!!.overall)
        }
    }

    @Test
    fun `checks가 없거나 필수 check id가 없으면 JSON contract를 fail closed로 거부한다`() = runBlocking {
        withTimeout(15_000) {
            val missingChecksRoot = tempKitRoot()
            writeDoctorStub(
                missingChecksRoot,
                "Write-Output '{\"contract_version\":\"1.0\",\"overall\":\"ok\"}'; exit 0",
            )
            val missingChecks = assertIs<ProcessRunResult.Completed>(
                adapter.execute(doctorCommand(missingChecksRoot), Duration.ofSeconds(10), ProcessCancellationToken()),
            )
            assertNull(missingChecks.contract)

            val missingIdRoot = tempKitRoot()
            writeDoctorStub(
                missingIdRoot,
                "Write-Output '{\"contract_version\":\"1.0\",\"overall\":\"ok\",\"checks\":[{\"severity\":\"info\",\"message\":\"x\"}]}' ; exit 0",
            )
            val missingId = assertIs<ProcessRunResult.Completed>(
                adapter.execute(doctorCommand(missingIdRoot), Duration.ofSeconds(10), ProcessCancellationToken()),
            )
            assertNull(missingId.contract)
        }
    }
    @Test
    fun `stdout이 JSON이 아니면 contract는 null이고 안전한 조각만 남긴다`() = runBlocking {
        withTimeout(15_000) {
            val kitRoot = tempKitRoot()
            writeDoctorStub(kitRoot, "Write-Output 'this is not json at all'\nexit 1")

            val result = adapter.execute(doctorCommand(kitRoot), Duration.ofSeconds(10), ProcessCancellationToken())

            val completed = assertIs<ProcessRunResult.Completed>(result)
            assertNull(completed.contract)
            assertTrue(completed.rawOutputSnippet?.contains("this is not json at all") == true)
        }
    }

    @Test
    fun `stdout이 비어있으면 contract는 null이고 snippet도 없다`() = runBlocking {
        withTimeout(15_000) {
            val kitRoot = tempKitRoot()
            writeDoctorStub(kitRoot, "exit 1")

            val result = adapter.execute(doctorCommand(kitRoot), Duration.ofSeconds(10), ProcessCancellationToken())

            val completed = assertIs<ProcessRunResult.Completed>(result)
            assertNull(completed.contract)
        }
    }

    // ── Phase 4: run-cycle.ps1 기반 mutating command (JSON 계약 없음) ──────────

    private fun writeRunCycleStub(kitRoot: Path, body: String) {
        val script = """
            param(
                [string]${'$'}WorkspaceRoot,[string]${'$'}ProjectRoot,[string]${'$'}KitRoot,[string]${'$'}Profile,[string]${'$'}Date,
                [switch]${'$'}SkipDoctor,[switch]${'$'}SkipOpsValidation,[switch]${'$'}ValidateForClosure,
                [switch]${'$'}RunPlanningWrapper,[switch]${'$'}RunReplanWrapper,
                [string]${'$'}PlanningReason,[string]${'$'}ReplanReason,
                [switch]${'$'}DryRunPlanningDecision,[switch]${'$'}AllowSameInputPlanningRerun,
                [string]${'$'}RunExecutionWrapper,[switch]${'$'}UsePythonSidecars,
                [switch]${'$'}UseWorkflowStatePrimary,[switch]${'$'}ForceDualFileCompatibility
            )
            $body
        """.trimIndent()
        Files.write(kitRoot.resolve("scripts/run-cycle.ps1"), script.toByteArray(StandardCharsets.UTF_8))
    }

    @Test
    fun `BootstrapDay는 run-cycle 스크립트를 실행하고 프로세스 산문 출력은 contract가 아닌 snippet에만 남는다`() = runBlocking {
        withTimeout(15_000) {
            val kitRoot = tempKitRoot()
            writeRunCycleStub(kitRoot, "Write-Output '==> bootstrap step complete'\nexit 0")
            val command = HarnessCommand.BootstrapDay(
                workspaceRoot = kitRoot.resolve("workspace"),
                projectRoot = kitRoot.resolve("repo"),
                kitRoot = kitRoot,
                profile = "corp-default",
                date = LocalDate.of(2026, 7, 27),
            )

            val result = adapter.execute(command, Duration.ofSeconds(10), ProcessCancellationToken())

            val completed = assertIs<ProcessRunResult.Completed>(result)
            assertEquals(0, completed.exitCode)
            assertNull(completed.contract)
            assertTrue(completed.rawOutputSnippet?.contains("bootstrap step complete") == true)
        }
    }

    @Test
    fun `RunPlanning은 exit code를 그대로 반영하고 완료 판단을 stdout 문구에 의존하지 않는다`() = runBlocking {
        withTimeout(15_000) {
            val kitRoot = tempKitRoot()
            writeRunCycleStub(kitRoot, "Write-Output 'planning ok looking good'\nexit 1")
            val command = HarnessCommand.RunPlanning(
                workspaceRoot = kitRoot.resolve("workspace"),
                projectRoot = kitRoot.resolve("repo"),
                kitRoot = kitRoot,
                profile = null,
                date = LocalDate.of(2026, 7, 27),
                reason = PlanningReason.InitialPlan,
            )

            val result = adapter.execute(command, Duration.ofSeconds(10), ProcessCancellationToken())

            val completed = assertIs<ProcessRunResult.Completed>(result)
            assertEquals(1, completed.exitCode)
            assertNull(completed.contract)
        }
    }

    // ── Phase 10: enter-project.ps1 기반 onboarding command (JSON 계약 없음) ──────

    private fun writeEnterProjectStub(kitRoot: Path, body: String) {
        val script = """
            param([string]${'$'}ProjectRoot,[string]${'$'}WorkspaceRoot,[string]${'$'}KitRoot,[string]${'$'}Profile,[string]${'$'}Date)
            $body
        """.trimIndent()
        Files.write(kitRoot.resolve("scripts/enter-project.ps1"), script.toByteArray(StandardCharsets.UTF_8))
    }

    @Test
    fun `OnboardProject는 enter-project 스크립트를 실행하고 working directory는 kitRoot다`() = runBlocking {
        withTimeout(15_000) {
            val kitRoot = tempKitRoot()
            writeEnterProjectStub(kitRoot, "Write-Output ('cwd=' + (Get-Location).Path)\nexit 0")
            val command = HarnessCommand.OnboardProject(
                workspaceRoot = kitRoot.resolve("workspace"),
                projectRoot = kitRoot.resolve("repo"),
                kitRoot = kitRoot,
                profile = "corp-default",
                date = LocalDate.of(2026, 7, 27),
            )

            val result = adapter.execute(command, Duration.ofSeconds(10), ProcessCancellationToken())

            val completed = assertIs<ProcessRunResult.Completed>(result)
            assertEquals(0, completed.exitCode)
            assertNull(completed.contract)
            assertTrue(completed.rawOutputSnippet?.contains(kitRoot.toRealPath().toString()) == true)
        }
    }

    @Test
    fun `OnboardProject는 exit code를 그대로 반영하고 완료 판단을 stdout 문구에 의존하지 않는다`() = runBlocking {
        withTimeout(15_000) {
            val kitRoot = tempKitRoot()
            writeEnterProjectStub(kitRoot, "Write-Output 'bridge looks fine'\nexit 1")
            val command = HarnessCommand.OnboardProject(
                workspaceRoot = kitRoot.resolve("workspace"),
                projectRoot = kitRoot.resolve("repo"),
                kitRoot = kitRoot,
                profile = null,
                date = LocalDate.of(2026, 7, 27),
            )

            val result = adapter.execute(command, Duration.ofSeconds(10), ProcessCancellationToken())

            val completed = assertIs<ProcessRunResult.Completed>(result)
            assertEquals(1, completed.exitCode)
            assertNull(completed.contract)
        }
    }

    @Test
    fun `RunExecution code doc은 각각 working directory와 exit code를 정확히 관측한다`() = runBlocking {
        withTimeout(15_000) {
            val kitRoot = tempKitRoot()
            writeRunCycleStub(kitRoot, "Write-Output ('wrapper=' + ${'$'}RunExecutionWrapper)\nexit 0")

            val codeResult = adapter.execute(
                HarnessCommand.RunExecution(
                    workspaceRoot = kitRoot.resolve("workspace"),
                    projectRoot = kitRoot.resolve("repo"),
                    kitRoot = kitRoot,
                    profile = null,
                    date = LocalDate.of(2026, 7, 27),
                    wrapper = ExecutionWrapper.Code,
                ),
                Duration.ofSeconds(10),
                ProcessCancellationToken(),
            )
            val docResult = adapter.execute(
                HarnessCommand.RunExecution(
                    workspaceRoot = kitRoot.resolve("workspace"),
                    projectRoot = kitRoot.resolve("repo"),
                    kitRoot = kitRoot,
                    profile = null,
                    date = LocalDate.of(2026, 7, 27),
                    wrapper = ExecutionWrapper.Doc,
                ),
                Duration.ofSeconds(10),
                ProcessCancellationToken(),
            )

            val codeCompleted = assertIs<ProcessRunResult.Completed>(codeResult)
            val docCompleted = assertIs<ProcessRunResult.Completed>(docResult)
            assertTrue(codeCompleted.rawOutputSnippet?.contains("wrapper=code") == true)
            assertTrue(docCompleted.rawOutputSnippet?.contains("wrapper=doc") == true)
        }
    }
}
