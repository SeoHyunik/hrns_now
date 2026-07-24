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
        statePort: WorkflowStatePort,
        dayDiscovery: (Path) -> List<LocalDate> = { emptyList() },
        workspaceProbeState: PathProbeState = PathProbeState.Exists,
        artifactProbe: (WorkspaceConfig, WorkspaceDay) -> WorkspaceArtifactSummary = { _, _ ->
            WorkspaceArtifactSummary(emptyList())
        },
    ): LoadCockpitUseCase = LoadCockpitUseCase(
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
        val config = config(workspaceRoot = "S:\\workspace with 한글")
        val useCase = useCase(
            statePort = statePort,
            dayDiscovery = { listOf(LocalDate.of(2026, 6, 20), latest) },
            artifactProbe = { _, day ->
                artifactDay = day
                WorkspaceArtifactSummary(emptyList())
            },
        )

        val selection = useCase.resolveDay(config)
        val result = useCase(config, selection)

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
        val config = config(workspaceRoot = "  ")
        val useCase = useCase(
            statePort = statePort,
            dayDiscovery = {
                discoveryCalled = true
                emptyList()
            },
        )

        val result = useCase(config, useCase.resolveDay(config))

        assertFalse(useCase.hasConfiguredWorkspace(config))
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
        val config = config(workspaceRoot = "S:\\missing-workspace")
        val useCase = useCase(statePort = statePort, workspaceProbeState = PathProbeState.Missing)

        val result = useCase(config, useCase.resolveDay(config))

        assertTrue(useCase.hasConfiguredWorkspace(config))
        assertFalse(result.projectConnected)
        assertFalse(readerCalled)
        assertIs<StateReadResult.Missing>(result.stateRead)
    }

    @Test
    fun `잘못된 workspace 경로도 예외 대신 연결되지 않은 상태로 처리한다`() {
        val statePort = object : WorkflowStatePort {
            override fun read(day: WorkspaceDay): StateReadResult = error("must not be called")
        }
        val config = config(workspaceRoot = "bad\u0000path")
        val useCase = useCase(statePort)

        assertFalse(useCase.hasConfiguredWorkspace(config))
        assertIs<StateReadResult.Missing>(useCase(config, useCase.resolveDay(config)).stateRead)
    }

    @Test
    fun `다른 프로젝트로 전환하면 같은 호출에서 새 workspaceConfig의 day를 사용한다`() {
        val configA = config(workspaceRoot = "S:\\project-a")
        val configB = config(workspaceRoot = "S:\\project-b")
        val seenRoots = mutableListOf<String?>()
        val statePort = object : WorkflowStatePort {
            override fun read(day: WorkspaceDay): StateReadResult =
                StateReadResult.Missing(day.dayRoot.resolve("WORKFLOW_STATE.json"))
        }
        val useCase = useCase(
            statePort = statePort,
            artifactProbe = { config, _ ->
                seenRoots.add(config.roots.workspaceRoot)
                WorkspaceArtifactSummary(emptyList())
            },
        )

        val selectionA = useCase.resolveDay(configA)
        useCase(configA, selectionA)
        val selectionB = useCase.resolveDay(configB)
        useCase(configB, selectionB)

        assertEquals(listOf<String?>("S:\\project-a", "S:\\project-b"), seenRoots)
        assertEquals(selectionA.workspaceDay.projectWorkspaceRoot, Path.of("S:\\project-a").toAbsolutePath().normalize())
        assertEquals(selectionB.workspaceDay.projectWorkspaceRoot, Path.of("S:\\project-b").toAbsolutePath().normalize())
    }

    @Test
    fun `명시 날짜는 탐색된 유효 폴더일 때만 선택하고 목록은 중복 없이 최신순이다`() {
        val validPast = LocalDate.of(2026, 6, 24)
        val validLatest = LocalDate.of(2026, 6, 25)
        val missing = LocalDate.of(2026, 6, 23)
        val statePort = object : WorkflowStatePort {
            override fun read(day: WorkspaceDay): StateReadResult =
                StateReadResult.Missing(day.dayRoot.resolve("WORKFLOW_STATE.json"))
        }
        val useCase = useCase(
            statePort = statePort,
            dayDiscovery = { listOf(validPast, validLatest, validPast) },
        )
        val config = config("S:\\workspace")

        val explicit = useCase.resolveDays(config, validPast)
        val invalidExplicit = useCase.resolveDays(config, missing)

        assertEquals(listOf(validLatest, validPast), explicit.availableDates)
        assertEquals(validPast, explicit.selection.workspaceDay.date)
        assertEquals(validLatest, invalidExplicit.selection.workspaceDay.date)
        assertTrue(invalidExplicit.selection.isReadOnly)
    }
}
