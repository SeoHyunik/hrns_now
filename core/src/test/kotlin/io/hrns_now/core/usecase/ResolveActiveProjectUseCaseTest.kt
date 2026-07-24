package io.hrns_now.core.usecase

import io.hrns_now.core.config.RuntimeConfig
import io.hrns_now.core.config.WorkspaceConfig
import io.hrns_now.core.config.WorkspaceRoots
import io.hrns_now.core.domain.model.HarnessProject
import io.hrns_now.core.domain.model.ProjectId
import io.hrns_now.core.port.ProjectRegistryPort
import io.hrns_now.core.result.RegistryLoadResult
import io.hrns_now.core.result.RegistrySaveResult
import java.nio.file.Path
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ResolveActiveProjectUseCaseTest {

    private fun project(id: String, displayName: String = "project-$id"): HarnessProject = HarnessProject(
        id = ProjectId(id),
        displayName = displayName,
        kitRoot = Path.of("S:\\kit-$id"),
        projectWorkspaceRoot = Path.of("S:\\workspace-$id"),
        repositoryRoot = Path.of("S:\\repo-$id"),
        profileId = "기본",
        lastSelectedDate = null,
        lastDiagnosticsSummary = null,
        lastRunAt = null,
    )

    private fun envConfig(workspaceRoot: String? = null): WorkspaceConfig = WorkspaceConfig(
        workspaceName = null,
        profileName = "env",
        roots = WorkspaceRoots(kitRoot = null, workspaceRoot = workspaceRoot, projectRoot = null),
        runtime = RuntimeConfig(powerShellPath = null, claudeCommand = null),
    )

    private class FixedRegistryPort(private val result: RegistryLoadResult) : ProjectRegistryPort {
        override suspend fun findAll(): RegistryLoadResult = result
        override suspend fun findById(id: ProjectId): HarnessProject? = null
        override suspend fun save(project: HarnessProject): RegistrySaveResult = RegistrySaveResult.Success
        override suspend fun delete(id: ProjectId): RegistrySaveResult = RegistrySaveResult.Success
        override suspend fun markActive(id: ProjectId): RegistrySaveResult = RegistrySaveResult.Success
    }

    @Test
    fun `Registry의 마지막 선택 프로젝트가 있으면 최우선으로 사용한다`() = runTest {
        val projectA = project("a")
        val projectB = project("b")
        val registry = FixedRegistryPort(
            RegistryLoadResult.Success(projects = listOf(projectA, projectB), lastActiveProjectId = projectB.id),
        )
        val useCase = ResolveActiveProjectUseCase(registry) { envConfig("S:\\env-workspace") }

        val resolution = useCase()

        assertIs<ActiveProjectSource.Registry>(resolution.source)
        assertEquals(projectB, resolution.project)
        assertEquals("S:\\workspace-b", resolution.workspaceConfig.roots.workspaceRoot)
    }

    @Test
    fun `Registry가 비어 있으면 환경변수 fallback을 사용한다`() = runTest {
        val registry = FixedRegistryPort(RegistryLoadResult.Success(projects = emptyList(), lastActiveProjectId = null))
        val useCase = ResolveActiveProjectUseCase(registry) { envConfig("S:\\env-workspace") }

        val resolution = useCase()

        assertIs<ActiveProjectSource.EnvironmentFallback>(resolution.source)
        assertEquals(null, resolution.project)
        assertEquals("S:\\env-workspace", resolution.workspaceConfig.roots.workspaceRoot)
    }

    @Test
    fun `Registry도 환경변수도 없으면 사용자 선택이 필요하다`() = runTest {
        val registry = FixedRegistryPort(RegistryLoadResult.Success(projects = emptyList(), lastActiveProjectId = null))
        val useCase = ResolveActiveProjectUseCase(registry) { envConfig(null) }

        val resolution = useCase()

        assertIs<ActiveProjectSource.NoneSelected>(resolution.source)
        assertNull(resolution.project)
    }

    @Test
    fun `Registry가 손상되어도 복구된 프로젝트 목록으로 마지막 선택을 시도한다`() = runTest {
        val projectA = project("a")
        val registry = FixedRegistryPort(
            RegistryLoadResult.RecoveredFromCorruption(
                projects = listOf(projectA),
                lastActiveProjectId = projectA.id,
                quarantinePath = Path.of("S:\\appdata\\projects.json.corrupt-1"),
                message = "1개 project entry가 손상되어 제외했습니다.",
            ),
        )
        val useCase = ResolveActiveProjectUseCase(registry) { envConfig(null) }

        val resolution = useCase()

        assertIs<ActiveProjectSource.Registry>(resolution.source)
        assertEquals(projectA, resolution.project)
    }

    @Test
    fun `Registry를 읽을 수 없으면 환경변수 fallback으로 mock 성공처럼 위장하지 않는다`() = runTest {
        val registry = FixedRegistryPort(RegistryLoadResult.Unreadable("permission denied"))
        val useCase = ResolveActiveProjectUseCase(registry) { envConfig("S:\\env-workspace") }

        val resolution = useCase()

        assertIs<ActiveProjectSource.EnvironmentFallback>(resolution.source)
        assertEquals(emptyList(), resolution.registryProjects)
    }

    @Test
    fun `마지막 선택 id가 목록에 없으면 환경변수로 fallback한다`() = runTest {
        val registry = FixedRegistryPort(
            RegistryLoadResult.Success(projects = listOf(project("a")), lastActiveProjectId = ProjectId("dangling-id")),
        )
        val useCase = ResolveActiveProjectUseCase(registry) { envConfig("S:\\env-workspace") }

        val resolution = useCase()

        assertIs<ActiveProjectSource.EnvironmentFallback>(resolution.source)
    }
}
