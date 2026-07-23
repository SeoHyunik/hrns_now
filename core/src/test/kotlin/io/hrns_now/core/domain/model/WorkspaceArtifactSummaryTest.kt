package io.hrns_now.core.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkspaceArtifactSummaryTest {

    private fun result(
        requirement: ArtifactRequirement,
        state: ArtifactProbeState,
        label: String = "item",
    ) = ArtifactProbeResult(
        label = label,
        path = "2026-07-23/$label",
        kind = ArtifactKind.File,
        requirement = requirement,
        state = state,
        message = "",
    )

    @Test
    fun `required 항목이 모두 Exists이면 ready`() {
        val summary = WorkspaceArtifactSummary(
            items = listOf(
                result(ArtifactRequirement.Required, ArtifactProbeState.Exists, "a"),
                result(ArtifactRequirement.Required, ArtifactProbeState.Exists, "b"),
            ),
        )
        assertTrue(summary.isRequiredReady)
    }

    @Test
    fun `optional 파일 누락은 readiness에 영향을 주지 않는다`() {
        val summary = WorkspaceArtifactSummary(
            items = listOf(
                result(ArtifactRequirement.Required, ArtifactProbeState.Exists, "a"),
                result(ArtifactRequirement.Optional, ArtifactProbeState.Missing, "optional"),
            ),
        )
        assertTrue(summary.isRequiredReady)
    }

    @Test
    fun `legacy 파일 존재 여부는 readiness에 영향을 주지 않는다`() {
        val summary = WorkspaceArtifactSummary(
            items = listOf(
                result(ArtifactRequirement.Required, ArtifactProbeState.Exists, "a"),
                result(ArtifactRequirement.Legacy, ArtifactProbeState.Exists, "legacy"),
            ),
        )
        assertTrue(summary.isRequiredReady)
    }

    @Test
    fun `required 항목이 하나라도 누락이면 not ready`() {
        val summary = WorkspaceArtifactSummary(
            items = listOf(
                result(ArtifactRequirement.Required, ArtifactProbeState.Exists, "a"),
                result(ArtifactRequirement.Required, ArtifactProbeState.Missing, "b"),
            ),
        )
        assertFalse(summary.isRequiredReady)
    }

    @Test
    fun `required 항목이 없으면 not ready`() {
        val summary = WorkspaceArtifactSummary(
            items = listOf(
                result(ArtifactRequirement.Optional, ArtifactProbeState.Exists, "optional"),
            ),
        )
        assertFalse(summary.isRequiredReady)
        assertEquals(emptyList(), summary.requiredItems)
    }

    @Test
    fun `requiredItems는 Required 분류만 반환한다`() {
        val summary = WorkspaceArtifactSummary(
            items = listOf(
                result(ArtifactRequirement.Required, ArtifactProbeState.Exists, "a"),
                result(ArtifactRequirement.Optional, ArtifactProbeState.Exists, "b"),
                result(ArtifactRequirement.Legacy, ArtifactProbeState.Exists, "c"),
            ),
        )
        assertEquals(1, summary.requiredItems.size)
        assertEquals("a", summary.requiredItems.single().label)
    }
}
