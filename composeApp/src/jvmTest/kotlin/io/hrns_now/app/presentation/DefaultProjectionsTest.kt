package io.hrns_now.app.presentation

import io.hrns_now.app.presentation.model.CockpitActionItem
import io.hrns_now.core.config.PathProbeKind
import io.hrns_now.core.config.PathProbeResult
import io.hrns_now.core.config.PathProbeState
import io.hrns_now.core.config.RuntimeConfig
import io.hrns_now.core.config.WorkspaceConfig
import io.hrns_now.core.config.WorkspaceProbeSummary
import io.hrns_now.core.config.WorkspaceRoots
import io.hrns_now.core.domain.model.UiAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultProjectionsTest {

    @Test
    fun `Setup 진단 버튼은 typed action과 Cockpit 허용 상태를 사용한다`() {
        val projection = buildSetupProjection(
            config = WorkspaceConfig(
                workspaceName = null,
                profileName = "test",
                roots = WorkspaceRoots(null, null, null),
                runtime = RuntimeConfig(null, null, "ko"),
            ),
            probeSummary = probeSummary(),
            diagnosticActions = listOf(
                CockpitActionItem(UiAction.RunDoctor, "표시 문구가 아닌 ID", enabled = true),
                CockpitActionItem(UiAction.RunOpsValidation, "다른 표시 문구", enabled = false),
            ),
        )

        assertEquals(UiAction.RunDoctor, projection.actions[0].action)
        assertTrue(projection.actions[0].enabled)
        assertEquals(UiAction.RunOpsValidation, projection.actions[1].action)
        assertFalse(projection.actions[1].enabled)
    }

    private fun probeSummary(): WorkspaceProbeSummary {
        val probe = PathProbeResult("KitRoot", null, PathProbeKind.Directory, PathProbeState.NotConfigured, "미설정")
        return WorkspaceProbeSummary(probe, probe, probe, probe, probe)
    }
}