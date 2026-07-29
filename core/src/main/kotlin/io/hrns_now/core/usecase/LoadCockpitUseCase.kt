package io.hrns_now.core.usecase

import io.hrns_now.core.config.PathProbeState
import io.hrns_now.core.config.WorkspaceConfig
import io.hrns_now.core.config.WorkspaceProbeSummary
import io.hrns_now.core.config.WorkspaceReadiness
import io.hrns_now.core.domain.model.WorkspaceArtifactSummary
import io.hrns_now.core.domain.model.WorkspaceDay
import io.hrns_now.core.domain.policy.WorkspaceDayPurpose
import io.hrns_now.core.domain.policy.WorkspaceDaySelection
import io.hrns_now.core.domain.policy.WorkspaceDaySelectionPolicy
import io.hrns_now.core.port.WorkflowStatePort
import io.hrns_now.core.result.StateReadResult
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.time.LocalDate

/**
 * Cockpit 한 번의 조회에 필요한 domain/query 결과다. presentation label이나 Compose 타입을
 * 포함하지 않으며, ViewModel은 이 결과를 UI projection assembler에 전달만 한다.
 */
data class WorkspaceDayResolution(
    val selection: WorkspaceDaySelection,
    val availableDates: List<LocalDate>,
)

data class CockpitLoadResult(
    val workspaceConfig: WorkspaceConfig,
    val projectConnected: Boolean,
    val daySelection: WorkspaceDaySelection,
    val workspaceProbeSummary: WorkspaceProbeSummary,
    val workspaceReadiness: WorkspaceReadiness,
    val workspaceArtifactSummary: WorkspaceArtifactSummary,
    val stateRead: StateReadResult,
)

/**
 * workspace/day 탐색과 State·artifact 조회를 조율하는 read-only query use case다.
 *
 * `workspaceConfig`는 더 이상 생성 시점에 고정하지 않는다 — Phase 1D의 다중 프로젝트 전환을
 * 지원하려면 호출마다 활성 프로젝트의 config를 명시적으로 받아야 한다(`doc/claude_prompts/
 * phase1d-project-registry.md` §4). 함수형 의존성은 기존 단일 메서드 probe를 위한 좁은 port
 * 역할만 한다. 실제 filesystem 구현은 composition root에서 주입하며, 호출자는 이 use case
 * 전체를 IO dispatcher에서 실행해야 한다. Harness 파일을 쓰거나 command를 실행하지 않는다.
 */
class LoadCockpitUseCase(
    private val pathProbe: (WorkspaceConfig) -> WorkspaceProbeSummary,
    private val readinessProvider: (WorkspaceConfig, WorkspaceProbeSummary) -> WorkspaceReadiness,
    private val artifactProbe: (WorkspaceConfig, WorkspaceDay) -> WorkspaceArtifactSummary,
    private val dayDiscovery: (Path) -> List<LocalDate>,
    private val daySelectionPolicy: WorkspaceDaySelectionPolicy,
    private val statePort: WorkflowStatePort,
) {
    fun hasConfiguredWorkspace(workspaceConfig: WorkspaceConfig): Boolean =
        parseWorkspaceRoot(workspaceConfig.roots.workspaceRoot) != null

    fun resolveDays(
        workspaceConfig: WorkspaceConfig,
        explicitDate: LocalDate? = null,
    ): WorkspaceDayResolution {
        val projectWorkspaceRoot = parseWorkspaceRoot(workspaceConfig.roots.workspaceRoot)
        val root = projectWorkspaceRoot ?: Path.of(".")
        val availableDates = if (projectWorkspaceRoot == null) {
            emptyList()
        } else {
            dayDiscovery(root).distinct().sortedDescending()
        }
        // 새 Phase 8 §6: 오늘은 daily directory discovery 결과와 무관하게 명시 선택을 허용한다 —
        // 그렇지 않으면 "오늘 작업 시작"으로 오늘을 선택해도 이 필터가 조용히 과거 날짜 fallback으로
        // 되돌려 버린다. 과거 날짜는 여전히 실제로 발견된 폴더에 있을 때만 명시 선택을 허용한다.
        val validExplicitDate = explicitDate?.takeIf { it in availableDates || it == daySelectionPolicy.today }
        return WorkspaceDayResolution(
            selection = daySelectionPolicy.select(
                projectWorkspaceRoot = root,
                explicitDate = validExplicitDate,
                availableDates = availableDates,
                purpose = WorkspaceDayPurpose.ReadOnly,
            ),
            availableDates = availableDates,
        )
    }

    fun resolveDay(
        workspaceConfig: WorkspaceConfig,
        explicitDate: LocalDate? = null,
    ): WorkspaceDaySelection = resolveDays(workspaceConfig, explicitDate).selection

    operator fun invoke(workspaceConfig: WorkspaceConfig, daySelection: WorkspaceDaySelection): CockpitLoadResult {
        val projectWorkspaceRoot = parseWorkspaceRoot(workspaceConfig.roots.workspaceRoot)
        val probeSummary = pathProbe(workspaceConfig)
        val projectConnected =
            projectWorkspaceRoot != null && probeSummary.workspaceRoot.state == PathProbeState.Exists
        val stateRead = if (projectConnected) {
            statePort.read(daySelection.workspaceDay)
        } else {
            StateReadResult.Missing(daySelection.workspaceDay.dayRoot.resolve("WORKFLOW_STATE.json"))
        }
        return CockpitLoadResult(
            workspaceConfig = workspaceConfig,
            projectConnected = projectConnected,
            daySelection = daySelection,
            workspaceProbeSummary = probeSummary,
            workspaceReadiness = readinessProvider(workspaceConfig, probeSummary),
            workspaceArtifactSummary = artifactProbe(workspaceConfig, daySelection.workspaceDay),
            stateRead = stateRead,
        )
    }

    private fun parseWorkspaceRoot(raw: String?): Path? {
        val value = raw?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return try {
            Path.of(value).toAbsolutePath().normalize()
        } catch (_: InvalidPathException) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }
}
