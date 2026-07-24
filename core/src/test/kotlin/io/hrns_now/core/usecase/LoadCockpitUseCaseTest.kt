package io.hrns_now.core.usecase

import io.hrns_now.core.config.PathProbeKind
import io.hrns_now.core.config.PathProbeResult
import io.hrns_now.core.config.PathProbeState
import io.hrns_now.core.config.RuntimeConfig
import io.hrns_now.core.config.WorkspaceConfig
import io.hrns_now.core.config.WorkspaceProbeSummary
import io.hrns_now.core.config.WorkspaceReadiness
import io.hrns_now.core.config.WorkspaceRoots
import io.hrns_now.core.domain.model.WorkspaceArtifactSummary
import io.hrns_now.core.domain.model.WorkspaceDay
import io.hrns_now.core.domain.policy.WorkspaceDaySelectionPolicy
import io.hrns_now.core.port.WorkflowStatePort
import io.hrns_now.core.result.StateReadResult
import java.nio.file.Path
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LoadCockpitUseCaseTest {

    private val today = LocalDate.of(2026, 6, 26)

    private fun config(workspaceRoot: String?): WorkspaceConfig = WorkspaceConfig(
        workspaceName = "test",
        profileName = "test",
        roots = WorkspaceRoots(null, workspaceRoot, null),
        runtime = RuntimeConfig(null, null),
    )

    private fun probes(workspaceState: PathProbeState = PathProbeState.Exists): WorkspaceProbeSummary {
        val result = { label: String, kind: PathProbeKind ->
            PathProbeResult(label, null, kind, PathProbeState.NotConfigured, "test")
        }
        return WorkspaceProbeSummary(
            result("kit", PathProbeKind.Directory),
            PathProbeResult("workspace", "S:\\workspace", PathProbeKind.Directory, workspaceState, "test"),
            result("project", PathProbeKind.Directory),
            result("powershell", PathProbeKind.Command),
            result("claude", PathProbeKind.Command),
        )
    }

    private fun useCase(
        workspaceRoot: String?,
        statePort: WorkflowStatePort,
        dayDiscovery: (Path) -> List<LocalDate> = { emptyList() },
        workspaceProbeState: PathProbeState = PathProbeState.Exists,
        artifactProbe: (WorkspaceConfig, WorkspaceDay) -> WorkspaceArtifactSummary = { _, _ ->
            WorkspaceArtifactSummary(emptyList())
        },
    ): LoadCockpitUseCase = LoadCockpitUseCase(
        workspaceConfig = config(workspaceRoot),
        pathProbe = { probes(workspaceProbeState) },
        readinessProvider = { _, _ -> WorkspaceReadiness("e", "w", "b", "p", "d") },
        artifactProbe = artifactProbe,
        dayDiscovery = dayDiscovery,
        daySelectionPolicy = WorkspaceDaySelectionPolicy(today),
        statePort = statePort,
    )

    @Test
    fun `선택된 day를 State reader와 artifact probe에 동일하게 전달한다`() {
        val latest = LocalDate.of(2026, 6, 25)
        var stateDay: WorkspaceDay? = null
        var artifactDay: WorkspaceDay? = null
        val statePort = object : WorkflowStatePort {
            override fun read(day: WorkspaceDay): StateReadResult {
                stateDay = day
                return StateReadResult.Missing(day.dayRoot.resolve("WORKFLOW_STATE.json"))
            }
        }
        val useCase = useCase(
            workspaceRoot = "S:\\workspace with 한글",
            statePort = statePort,
            dayDiscovery = { listOf(LocalDate.of(2026, 6, 20), latest) },
            artifactProbe = { _, day ->
                artifactDay = day
                WorkspaceArtifactSummary(emptyList())
            },
        )

        val selection = useCase.resolveDay()
        val result = useCase(selection)

        assertEquals(latest, selection.workspaceDay.date)
        assertTrue(selection.isReadOnly)
        assertEquals(selection.workspaceDay, stateDay)
        assertEquals(selection.workspaceDay, artifactDay)
        assertEquals(selection, result.daySelection)
    }

    @Test
    fun `workspace 미설정은 Reader와 날짜 탐색을 호출하지 않고 Missing으로 닫는다`() {
        var readerCalled = false
        var discoveryCalled = false
        val statePort = object : WorkflowStatePort {
            override fun read(day: WorkspaceDay): StateReadResult {
                readerCalled = true
                return StateReadResult.Missing(day.dayRoot.resolve("WORKFLOW_STATE.json"))
            }
        }
        val useCase = useCase(
            workspaceRoot = "  ",
            statePort = statePort,
            dayDiscovery = {
                discoveryCalled = true
                emptyList()
            },
        )

        val result = useCase(useCase.resolveDay())

        assertFalse(useCase.hasConfiguredWorkspace)
        assertFalse(readerCalled)
        assertFalse(discoveryCalled)
        assertIs<StateReadResult.Missing>(result.stateRead)
    }

    @Test
    fun `설정된 workspace가 실제 디렉터리가 아니면 연결로 간주하지 않고 Reader를 잠근다`() {
        var readerCalled = false
        val statePort = object : WorkflowStatePort {
            override fun read(day: WorkspaceDay): StateReadResult {
                readerCalled = true
                return StateReadResult.Missing(day.dayRoot.resolve("WORKFLOW_STATE.json"))
            }
        }
        val useCase = useCase(
            workspaceRoot = "S:\\missing-workspace",
            statePort = statePort,
            workspaceProbeState = PathProbeState.Missing,
        )

        val result = useCase(useCase.resolveDay())

        assertTrue(useCase.hasConfiguredWorkspace)
        assertFalse(result.projectConnected)
        assertFalse(readerCalled)
        assertIs<StateReadResult.Missing>(result.stateRead)
    }
    @Test
    fun `잘못된 workspace 경로도 예외 대신 연결되지 않은 상태로 처리한다`() {
        val statePort = object : WorkflowStatePort {
            override fun read(day: WorkspaceDay): StateReadResult = error("must not be called")
        }
        val useCase = useCase("bad\u0000path", statePort)

        assertFalse(useCase.hasConfiguredWorkspace)
        assertIs<StateReadResult.Missing>(useCase(useCase.resolveDay()).stateRead)
    }
}
