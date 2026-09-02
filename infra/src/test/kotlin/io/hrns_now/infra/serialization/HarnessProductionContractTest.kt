package io.hrns_now.infra.serialization

import io.hrns_now.core.domain.model.WorkspaceDay
import io.hrns_now.core.result.StateReadResult
import org.junit.Assume
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Production-to-production contract test: reads real Harness onboarding output through the real,
 * unmodified [JsonWorkflowStateAdapter]/[WorkflowStateMapper] chain -- the same chain HRNS-NOW's UI
 * uses -- rather than through hand-authored mock ports. This closes the false-green blind spot named
 * in the harness-kit-live-compatibility audit: every prior test derived its WORKFLOW_STATE.json from
 * a hand-typed fixture, never from a real writer, so a real writer regression (like the
 * `required_next_action`/`queue.blocked_reason` gap the audit found) could pass 437 green tests while
 * still being broken end to end.
 *
 * Two modes:
 * - Offline deterministic fixture mode (always runs, no external process, no Harness Kit needed):
 *   fresh-onboarding / post-run-cycle-once / missing-guaranteed-field fixtures.
 * - Live local Kit verification mode (opt-in only): actually invokes a real Harness Kit's
 *   `enter-project.ps1` and `run-cycle.ps1` and reads their real output. Gated by the
 *   `HRNS_LIVE_HARNESS_KIT_ROOT` environment variable -- there is no hardcoded Kit path anywhere in
 *   this file. When the variable is unset, the live case is skipped (not failed); when set, the path
 *   must be a real Kit root or the test fails outright (a misconfigured opt-in should not silently
 *   pass). Neither `enter-project.ps1` nor a wrapper-less `run-cycle.ps1` invocation makes any Claude
 *   CLI or Ollama call.
 */
class HarnessProductionContractTest {

    private val createdDirs = mutableListOf<Path>()

    @AfterTest
    fun cleanup() {
        createdDirs.forEach { dir -> dir.toFile().deleteRecursively() }
    }

    private fun tempDir(prefix: String): Path =
        Files.createTempDirectory(prefix).also { createdDirs.add(it) }

    private fun readFixtureText(name: String): String {
        val resource = HarnessProductionContractTest::class.java.classLoader.getResource("fixtures/$name")
            ?: error("fixture not found: $name")
        return Files.readString(Paths.get(resource.toURI())).replace("\r\n", "\n")
    }

    private fun writeStateFixture(root: Path, date: LocalDate, text: String) {
        val statePath = root.resolve(date.toString()).resolve("WORKFLOW_STATE.json")
        Files.createDirectories(statePath.parent)
        Files.writeString(statePath, text, Charsets.UTF_8)
    }

    // ── Offline deterministic fixture mode ──────────────────────────────────

    @Test
    fun `fresh onboarding 산출물을 실제 어댑터로 읽으면 Success다`() {
        val root = tempDir("hrns-contract-fresh")
        val date = LocalDate.of(2026, 8, 6)
        writeStateFixture(root, date, readFixtureText("workflow-state-fresh-onboarding.json"))

        val result = JsonWorkflowStateAdapter().read(WorkspaceDay(root, date))

        val success = assertIs<StateReadResult.Success>(result)
        assertEquals("contract-sample-project", success.state.projectName)
        // WorkflowStateMapper.ifBlank { null }: a present-but-blank JSON value is valid and maps to
        // domain null, not an empty string (see WorkflowState.requiredNextAction).
        assertEquals(null, success.state.requiredNextAction)
    }

    @Test
    fun `post-run-cycle-once 산출물을 실제 어댑터로 읽으면 Success다`() {
        val root = tempDir("hrns-contract-post-run-cycle")
        val date = LocalDate.of(2026, 6, 26)
        writeStateFixture(root, date, readFixtureText("workflow-state-live-shape.json"))

        val result = JsonWorkflowStateAdapter().read(WorkspaceDay(root, date))

        assertIs<StateReadResult.Success>(result)
    }

    @Test
    fun `guaranteed field가 빠진 산출물을 실제 어댑터로 읽으면 Malformed다`() {
        val root = tempDir("hrns-contract-missing-field")
        val date = LocalDate.of(2026, 8, 6)
        val withoutRequiredNextAction = readFixtureText("workflow-state-fresh-onboarding.json")
            .replace("\"required_next_action\": \"\",\n  ", "")
        writeStateFixture(root, date, withoutRequiredNextAction)

        val result = JsonWorkflowStateAdapter().read(WorkspaceDay(root, date))

        val malformed = assertIs<StateReadResult.Malformed>(result)
        assertEquals(true, malformed.message.contains("required_next_action"))
    }

    // ── Live local Kit verification mode (opt-in) ───────────────────────────

    @Test
    fun `live Kit가 지정되면 실제 enter-project와 run-cycle 산출물을 실제 어댑터로 읽어 Success를 확인한다`() {
        val kitRootText = System.getenv(LIVE_KIT_ROOT_ENV_VAR)
        Assume.assumeTrue(
            "live Kit verification is opt-in; set $LIVE_KIT_ROOT_ENV_VAR to a real Harness Kit root to run it",
            !kitRootText.isNullOrBlank(),
        )
        val kitRoot = Paths.get(kitRootText)
        check(Files.isDirectory(kitRoot)) { "$LIVE_KIT_ROOT_ENV_VAR does not point to a directory: $kitRootText" }
        val enterProjectScript = kitRoot.resolve("scripts/enter-project.ps1")
        check(Files.isRegularFile(enterProjectScript)) { "$LIVE_KIT_ROOT_ENV_VAR is not a Harness Kit root (missing scripts/enter-project.ps1): $kitRootText" }

        val scratch = tempDir("hrns-contract-live")
        val projectRoot = scratch.resolve("project")
        val workspaceRoot = scratch.resolve("workspace")
        Files.createDirectories(projectRoot)
        val date = LocalDate.now()

        val enterExit = runPowerShell(
            kitRoot.resolve("scripts/enter-project.ps1"),
            listOf(
                "-ProjectRoot", projectRoot.toString(),
                "-WorkspaceRoot", workspaceRoot.toString(),
                "-KitRoot", kitRoot.toString(),
                "-Profile", "corp-default",
                "-Date", date.toString(),
            ),
        )
        assertEquals(0, enterExit, "enter-project.ps1 must exit 0 against a real Kit root")

        val freshResult = JsonWorkflowStateAdapter().read(WorkspaceDay(workspaceRoot, date))
        assertIs<StateReadResult.Success>(freshResult)

        // No -RunPlanningWrapper/-RunExecutionWrapper: this only exercises init-workspace/doctor/
        // ops-validation, never a Claude CLI or Ollama call.
        val runCycleExit = runPowerShell(
            kitRoot.resolve("scripts/run-cycle.ps1"),
            listOf(
                "-WorkspaceRoot", workspaceRoot.toString(),
                "-ProjectRoot", projectRoot.toString(),
                "-KitRoot", kitRoot.toString(),
                "-Profile", "corp-default",
                "-Date", date.toString(),
                "-SkipDoctor",
            ),
        )
        assertEquals(0, runCycleExit, "a wrapper-less run-cycle.ps1 pass must exit 0 against a real Kit root")

        val postRunCycleResult = JsonWorkflowStateAdapter().read(WorkspaceDay(workspaceRoot, date))
        assertIs<StateReadResult.Success>(postRunCycleResult)
    }

    @Test
    fun `live Kit가 지정되면 정상 closure 성공 경로를 실제 어댑터로 읽어 확인한다`() {
        val kitRootText = System.getenv(LIVE_KIT_ROOT_ENV_VAR)
        Assume.assumeTrue(
            "live Kit verification is opt-in; set $LIVE_KIT_ROOT_ENV_VAR to a real Harness Kit root to run it",
            !kitRootText.isNullOrBlank(),
        )
        val kitRoot = Paths.get(kitRootText)
        check(Files.isDirectory(kitRoot)) { "$LIVE_KIT_ROOT_ENV_VAR does not point to a directory: $kitRootText" }

        val scratch = tempDir("hrns-contract-live-closure")
        val projectRoot = scratch.resolve("project")
        val workspaceRoot = scratch.resolve("workspace")
        Files.createDirectories(projectRoot)
        val date = LocalDate.now()

        val enterExit = runPowerShell(
            kitRoot.resolve("scripts/enter-project.ps1"),
            listOf(
                "-ProjectRoot", projectRoot.toString(),
                "-WorkspaceRoot", workspaceRoot.toString(),
                "-KitRoot", kitRoot.toString(),
                "-Profile", "corp-default",
                "-Date", date.toString(),
            ),
        )
        assertEquals(0, enterExit, "enter-project.ps1 must exit 0 against a real Kit root")

        // Deliberately no fixture hacking: plain enter-project.ps1 output, then -ValidateForClosure
        // with no wrapper flags (no Claude CLI/Ollama call). This is the exact scenario that was
        // completely broken end to end before the harness-kit-closure-validation-remediation fix
        // (claude/hooks/pre_handoff_validate.ps1's Get-StringArray crashed under StrictMode on every
        // native run-cycle.ps1 invocation), and no prior test in this suite exercised it.
        val closureExit = runPowerShell(
            kitRoot.resolve("scripts/run-cycle.ps1"),
            listOf(
                "-WorkspaceRoot", workspaceRoot.toString(),
                "-ProjectRoot", projectRoot.toString(),
                "-KitRoot", kitRoot.toString(),
                "-Profile", "corp-default",
                "-Date", date.toString(),
                "-SkipDoctor",
                "-ValidateForClosure",
            ),
        )
        assertEquals(0, closureExit, "-ValidateForClosure must exit 0 for a valid, untouched onboarding fixture")

        val closureResult = JsonWorkflowStateAdapter().read(WorkspaceDay(workspaceRoot, date))
        val success = assertIs<StateReadResult.Success>(closureResult)
        assertEquals(true, success.state.closureValidated)
        assertEquals(true, success.state.cleanHandoff)
        assertEquals(true, success.state.closure.validated)
        assertEquals(true, success.state.closure.isCleanHandoff)
    }

    private fun runPowerShell(scriptPath: Path, args: List<String>): Int {
        val command = listOf("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", scriptPath.toString()) + args
        val process = ProcessBuilder(command)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        return process.waitFor()
    }

    private companion object {
        const val LIVE_KIT_ROOT_ENV_VAR = "HRNS_LIVE_HARNESS_KIT_ROOT"
    }
}
