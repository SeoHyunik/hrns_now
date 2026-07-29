package io.hrns_now.app.presentation

import io.hrns_now.app.presentation.model.CockpitActionItem
import io.hrns_now.app.presentation.model.CockpitProjection
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

    private fun cockpitProjection(
        primaryAction: CockpitActionItem?,
        allowedActions: List<CockpitActionItem>,
    ): CockpitProjection = CockpitProjection(
        projectName = "sample",
        profileLabel = "corp-default",
        dateLabel = "2026-07-27",
        isReadOnlyDay = false,
        isStale = false,
        phaseLabel = "실행 준비",
        statusLabel = "실행 준비",
        queueStatusLabel = "active",
        activeCardId = "card-1",
        activeSliceId = "slice-1",
        authorizedTargetLabel = "S:\\repo\\TARGET.md",
        stopReasonLabel = null,
        blockedReasonLabel = null,
        artifactItems = emptyList(),
        opsValidationLabel = "통과",
        closureLabel = "미완료",
        executionCompletedLabel = "진행 중",
        lastSuccessfulReadAtLabel = null,
        lastAttemptAtLabel = null,
        primaryAction = primaryAction,
        allowedActions = allowedActions,
        diagnostics = null,
        compatibilityDiagnostics = null,
    )

    @Test
    fun `오늘 할 일 action 목록은 Doctor OpsValidation을 제외하고 일일 흐름 action만 남긴다`() {
        val cockpit = cockpitProjection(
            primaryAction = CockpitActionItem(UiAction.RunCodeSlice, "선택된 코드 작업 실행", enabled = true),
            allowedActions = listOf(
                CockpitActionItem(UiAction.RunCodeSlice, "선택된 코드 작업 실행", enabled = true),
                CockpitActionItem(UiAction.Refresh, "새로고침", enabled = true),
                CockpitActionItem(UiAction.RunDoctor, "환경 점검", enabled = false),
            ),
        )

        val projection = buildTodayWorkProjection(cockpit, strategyText = null, requestInboxNotice = null, requestSaving = false, lockSummaryLabel = "없음")

        assertEquals(listOf(UiAction.RunCodeSlice), projection.actions.map { it.action })
    }

    @Test
    fun `실행 확인 섹션은 wrapper 승인된 대상 파일 예상 검증 잠금을 read-only로 보여준다`() {
        val cockpit = cockpitProjection(
            primaryAction = CockpitActionItem(UiAction.RunDocSlice, "선택된 문서 작업 실행", enabled = true),
            allowedActions = listOf(CockpitActionItem(UiAction.RunDocSlice, "선택된 문서 작업 실행", enabled = true)),
        )

        val projection = buildTodayWorkProjection(
            cockpit,
            strategyText = "Execution wrapper: doc",
            requestInboxNotice = null,
            requestSaving = false,
            lockSummaryLabel = "Ops Validation (PID 4242, 마지막 heartbeat 3초 전)",
        )

        val confirmation = projection.sections.last()
        assertEquals("wrapper" to "doc", confirmation.rows[0])
        assertEquals("승인된 대상 파일" to "S:\\repo\\TARGET.md", confirmation.rows[1])
        assertEquals("예상 검증" to "통과", confirmation.rows.first { it.first == "예상 검증" })
        assertEquals("잠금" to "Ops Validation (PID 4242, 마지막 heartbeat 3초 전)", confirmation.rows.last())
    }

    /**
     * 새 Phase 8 §3: "개발 전략"은 더 이상 일반 `InfoCardModel` 목록에 섞이지 않고, 원문을 그대로
     * 담는 별도 [io.hrns_now.app.presentation.model.DevelopmentStrategyCardModel]로 분리된다 —
     * "없으면 안내 문구"는 이제 mapper가 아니라 Compose 카드가 렌더링 시점에 보여준다.
     */
    @Test
    fun `개발 전략 카드는 원문 텍스트와 날짜·읽기 전용 여부를 그대로 투영한다`() {
        val cockpit = cockpitProjection(primaryAction = null, allowedActions = emptyList())

        val projection = buildTodayWorkProjection(cockpit, strategyText = null, requestInboxNotice = null, requestSaving = false, lockSummaryLabel = "없음")

        assertEquals(null, projection.developmentStrategy.text)
        assertEquals(cockpit.dateLabel, projection.developmentStrategy.dateLabel)
        assertEquals(cockpit.isReadOnlyDay, projection.developmentStrategy.isReadOnlyDay)
    }

    @Test
    fun `작업 계획의 대기열 제목은 설명 괄호 없이 한국어 표시명만 사용한다`() {
        val cockpit = cockpitProjection(primaryAction = null, allowedActions = emptyList())

        val projection = buildTodayWorkProjection(cockpit, strategyText = "전략", requestInboxNotice = null, requestSaving = false, lockSummaryLabel = "없음")

        assertEquals("전략", projection.developmentStrategy.text)
        assertEquals("작업 대기열", projection.sections[0].title)
    }

    @Test
    fun `요청 저장 notice와 saving 상태를 그대로 투영한다`() {
        val cockpit = cockpitProjection(primaryAction = null, allowedActions = emptyList())

        val projection = buildTodayWorkProjection(
            cockpit,
            strategyText = null,
            requestInboxNotice = "요청을 저장했습니다.",
            requestSaving = true,
            lockSummaryLabel = "없음",
        )

        assertEquals("요청을 저장했습니다.", projection.requestInboxNotice)
        assertTrue(projection.requestSaving)
    }
}
