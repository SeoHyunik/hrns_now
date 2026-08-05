package io.hrns_now.app.ui

import io.hrns_now.app.presentation.model.RegistryProjectItem
import io.hrns_now.core.domain.model.ProjectId
import kotlin.test.Test
import kotlin.test.assertEquals

class RibbonProjectNameTest {
    @Test
    fun `ViewModel이 해석한 활성 프로젝트는 Registry projection이 비어도 상단 리본에 표시된다`() {
        val name = ribbonActiveProjectName(
            selectedProjectName = "sample-legacy-ui",
            registryProjects = emptyList(),
            workflowStateProjectName = null,
        )

        assertEquals("sample-legacy-ui", name)
    }

    @Test
    fun `활성 Registry 프로젝트는 State가 아직 없어도 상단 리본에 표시된다`() {
        val name = ribbonActiveProjectName(
            registryProjects = listOf(
                RegistryProjectItem(
                    id = ProjectId("active-project"),
                    label = "연결된 프로젝트",
                    isActive = true,
                ),
            ),
            workflowStateProjectName = null,
        )

        assertEquals("연결된 프로젝트", name)
    }

    @Test
    fun `활성 Registry 프로젝트가 State 이름보다 우선한다`() {
        val name = ribbonActiveProjectName(
            registryProjects = listOf(
                RegistryProjectItem(
                    id = ProjectId("active-project"),
                    label = "Registry 프로젝트",
                    isActive = true,
                ),
            ),
            workflowStateProjectName = "State 프로젝트",
        )

        assertEquals("Registry 프로젝트", name)
    }

    @Test
    fun `활성 Registry가 없으면 State 이름을 보조로 사용한다`() {
        val name = ribbonActiveProjectName(
            registryProjects = emptyList(),
            workflowStateProjectName = "State 프로젝트",
        )

        assertEquals("State 프로젝트", name)
    }
}