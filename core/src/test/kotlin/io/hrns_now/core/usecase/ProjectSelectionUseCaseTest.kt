package io.hrns_now.core.usecase

import io.hrns_now.core.domain.model.HarnessProject
import io.hrns_now.core.domain.model.ProjectId
import io.hrns_now.core.port.ProjectRegistryPort
import io.hrns_now.core.result.RegistryLoadResult
import io.hrns_now.core.result.RegistrySaveResult
import java.nio.file.Path
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ProjectSelectionUseCaseTest {

    private fun project(): HarnessProject = HarnessProject(
        id = ProjectId("project-a"),
        displayName = "프로젝트 A",
        kitRoot = Path.of("S:\\kit"),
        projectWorkspaceRoot = Path.of("S:\\workspace"),
        repositoryRoot = Path.of("S:\\repo"),
        profileId = "기본",
        lastSelectedDate = null,
        lastDiagnosticsSummary = null,
        lastRunAt = null,
    )

    private class FakeRegistry(
        val project: HarnessProject,
        var markResult: RegistrySaveResult = RegistrySaveResult.Success,
        var saveResult: RegistrySaveResult = RegistrySaveResult.Success,
        var deleteResult: RegistrySaveResult = RegistrySaveResult.Success,
    ) : ProjectRegistryPort {
        var savedProject: HarnessProject? = null

        override suspend fun findAll(): RegistryLoadResult =
            RegistryLoadResult.Success(listOf(project), project.id)

        override suspend fun findById(id: ProjectId): HarnessProject? = project.takeIf { it.id == id }

        override suspend fun save(project: HarnessProject): RegistrySaveResult {
            savedProject = project
            return saveResult
        }

        override suspend fun delete(id: ProjectId): RegistrySaveResult = deleteResult

        override suspend fun markActive(id: ProjectId): RegistrySaveResult = markResult
    }

    @Test
    fun `활성 선택 저장 실패를 선택 성공으로 바꾸지 않는다`() = runTest {
        val project = project()
        val registry = FakeRegistry(
            project = project,
            markResult = RegistrySaveResult.Failed("write denied"),
        )

        val result = SelectProjectUseCase(registry)(project.id)

        val failed = assertIs<SelectProjectResult.SaveFailed>(result)
        assertEquals("write denied", failed.message)
        assertIs<SelectProjectResult.NotFound>(SelectProjectUseCase(registry)(ProjectId("missing")))
    }

    @Test
    fun `날짜 선택은 같은 프로젝트의 마지막 선택 날짜만 갱신한다`() = runTest {
        val project = project()
        val registry = FakeRegistry(project)
        val date = LocalDate.of(2026, 6, 25)

        val result = SelectWorkspaceDayUseCase(registry)(project, date)

        assertEquals(RegistrySaveResult.Success, result)
        assertEquals(date, registry.savedProject?.lastSelectedDate)
        assertEquals(project.kitRoot, registry.savedProject?.kitRoot)
    }

    @Test
    fun `삭제 실패 typed 결과를 호출자에게 그대로 전달한다`() = runTest {
        val project = project()
        val registry = FakeRegistry(
            project = project,
            deleteResult = RegistrySaveResult.Failed("disk full"),
        )

        val result = DeleteProjectUseCase(registry)(project.id)

        assertEquals(RegistrySaveResult.Failed("disk full"), result)
        assertNull(registry.savedProject)
    }
}