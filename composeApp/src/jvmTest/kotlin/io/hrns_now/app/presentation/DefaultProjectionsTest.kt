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
import io.hrns_now.core.domain.model.AppLocale
import io.hrns_now.app.ui.appStrings
import io.hrns_now.core.domain.model.UiAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultProjectionsTest {
    @Test
    fun `diagnostics eyebrows follow the selected locale`() {
        val korean = appStrings(AppLocale.Korean)
        val english = appStrings(AppLocale.English)

        assertEquals("상태 진단", korean.cockpit.diagnosticsEyebrow)
        assertEquals("Diagnostics", english.cockpit.diagnosticsEyebrow)
        assertEquals("상태 진단", korean.recovery.diagnosticsEyebrow)
        assertEquals("Diagnostics", english.recovery.diagnosticsEyebrow)
    }

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

    /** Phase 8 보완 §1: locale이 Shell chrome을 넘어 화면 본문에도 실제로 적용되는지 확인한다. */
    @Test
    fun `English locale은 작업 계획 제목과 대기열 섹션 제목을 영어로 투영한다`() {
        val cockpit = cockpitProjection(primaryAction = null, allowedActions = emptyList())

        val projection = buildTodayWorkProjection(
            cockpit,
            strategyText = null,
            requestInboxNotice = null,
            requestSaving = false,
            lockSummaryLabel = "None",
            locale = AppLocale.English,
        )

        assertEquals("Plan", projection.title)
        assertEquals("Task queue", projection.sections[0].title)
    }

    @Test
    fun `English locale의 Setup projection도 영어 제목을 낸다`() {
        val projection = buildSetupProjection(
            config = WorkspaceConfig(
                workspaceName = null,
                profileName = "test",
                roots = WorkspaceRoots(null, null, null),
                runtime = RuntimeConfig(null, null, "en"),
            ),
            probeSummary = probeSummary(),
            locale = AppLocale.English,
        )

        assertEquals("Project setup", projection.title)
    }

    /**
     * 새 Phase 8 보완 §2.1: BootstrapDay가 primary일 때만 [io.hrns_now.app.presentation.model.
     * TodayWorkProjection.bootstrapEligible]/[bootstrapAction]이 채워지고, 일반 `actions` 목록에는
     * BootstrapDay가 중복 포함되지 않는다 — 화면당 실제 실행 CTA는 한 곳(요구사항 카드)뿐이어야 한다.
     */
    @Test
    fun `BootstrapDay가 primary면 bootstrapAction만 채워지고 actions에는 중복되지 않는다`() {
        val cockpit = cockpitProjection(
            primaryAction = CockpitActionItem(UiAction.BootstrapDay, "오늘 작업 시작", enabled = true),
            allowedActions = listOf(
                CockpitActionItem(UiAction.BootstrapDay, "오늘 작업 시작", enabled = true),
                CockpitActionItem(UiAction.Refresh, "새로고침", enabled = true),
            ),
        )

        val projection = buildTodayWorkProjection(cockpit, strategyText = null, requestInboxNotice = null, requestSaving = false, lockSummaryLabel = "없음")

        assertTrue(projection.bootstrapEligible)
        val bootstrapAction = assertNotNull(projection.bootstrapAction)
        assertEquals(UiAction.BootstrapDay, bootstrapAction.action)
        assertTrue(bootstrapAction.enabled)
        assertFalse(projection.actions.any { it.action == UiAction.BootstrapDay })
    }

    @Test
    fun `BootstrapDay가 아니면 bootstrapEligible은 false이고 bootstrapAction은 null이다`() {
        val cockpit = cockpitProjection(
            primaryAction = CockpitActionItem(UiAction.EditRequest, "요구사항 작성", enabled = true),
            allowedActions = listOf(CockpitActionItem(UiAction.EditRequest, "요구사항 작성", enabled = true)),
        )

        val projection = buildTodayWorkProjection(cockpit, strategyText = null, requestInboxNotice = null, requestSaving = false, lockSummaryLabel = "없음")

        assertFalse(projection.bootstrapEligible)
        assertNull(projection.bootstrapAction)
    }

    @Test
    fun `blockedReasonLabel은 cockpit의 typed 차단 사유 문구를 그대로 옮긴다`() {
        val cockpit = cockpitProjection(primaryAction = null, allowedActions = emptyList()).copy(
            blockedReasonLabel = "과거 날짜는 읽기 전용입니다.",
        )

        val projection = buildTodayWorkProjection(cockpit, strategyText = null, requestInboxNotice = null, requestSaving = false, lockSummaryLabel = "없음")

        assertEquals("과거 날짜는 읽기 전용입니다.", projection.blockedReasonLabel)
    }
}
