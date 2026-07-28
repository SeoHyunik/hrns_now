package io.hrns_now.app.demo

import io.hrns_now.app.presentation.model.ActionButtonModel
import io.hrns_now.app.presentation.model.CockpitActionItem
import io.hrns_now.app.presentation.model.CockpitProjection
import io.hrns_now.app.presentation.model.InfoCardModel
import io.hrns_now.app.presentation.model.RecoveryProjection
import io.hrns_now.app.presentation.model.RunStatusProjection
import io.hrns_now.app.presentation.model.SetupProjection
import io.hrns_now.app.presentation.model.ShellProjection
import io.hrns_now.app.presentation.model.SourceFreshnessItem
import io.hrns_now.app.presentation.model.StatusChipModel
import io.hrns_now.app.presentation.model.TodayWorkProjection
import io.hrns_now.core.domain.model.UiAction

/**
 * demo/시연 전용 mock 데이터 provider. 실제 infrastructure adapter가 아니므로 infra 모듈에 두지 않는다.
 * 실사용 모드에서 실데이터 읽기가 실패했을 때 이 provider로 fallback하지 않는다.
 */
class MockProjectionProvider {
    fun shell(): ShellProjection =
        ShellProjection(
            title = "HRNS-NOW · v0",
            subtitle = "파일 우선 · 읽기 전용 투영 셸",
            statusChips = listOf(
                StatusChipModel("작업공간", "미선택"),
                StatusChipModel("하네스 엔진", "오프라인"),
                StatusChipModel("저장소 연결", "미확인"),
                StatusChipModel("실행 프로필", "기본"),
                StatusChipModel("상태 점검", "대기"),
                StatusChipModel("실행 중", "없음"),
            ),
            sourceItems = listOf(
                SourceFreshnessItem("오늘 상태 파일", "WORKFLOW_STATE.json", "없음"),
                SourceFreshnessItem("작업 계획 파일", "TODAY_STRATEGY.md", "없음"),
                SourceFreshnessItem("날짜 산출물 로그", "YYYY-MM-DD/logs/", "없음"),
                SourceFreshnessItem("래퍼 실행 로그", "logs/YYYY-MM-DD/", "없음"),
            ),
            notAppOwnedMessages = listOf(
                "UI는 WORKFLOW_STATE.json을 직접 쓰지 않습니다.",
                "UI는 마감 상태를 자체 확정하지 않습니다.",
                "현재 화면은 파일 기반 읽기 투영 셸입니다.",
            ),
        )

    fun setup(): SetupProjection =
        SetupProjection(
            title = "프로젝트 관리",
            subtitle = "프로젝트와 harness-kit 실행 환경을 안전하게 연결합니다.",
            cards = listOf(
                InfoCardModel("하네스 엔진", listOf("상태" to "하네스 엔진: 연결되지 않음")),
                InfoCardModel("작업공간", listOf("상태" to "작업공간: 선택되지 않음")),
                InfoCardModel("저장소 연결", listOf("상태" to "저장소 연결: 아직 검사하지 않음")),
                InfoCardModel("실행 프로필", listOf("상태" to "실행 프로필: 기본")),
            ),
            actions = listOf(
                ActionButtonModel("환경 점검"),
                ActionButtonModel("프로젝트 연결"),
                ActionButtonModel("프로젝트 파악 실행"),
                ActionButtonModel("브리프 확정"),
            ),
            note = "PS1 실행 연결은 다음 단계에서 추가합니다.",
        )

    /** demo mode 전용 Cockpit 샘플이다 — 실제 [io.hrns_now.core.domain.policy.ActionPolicy] 결과가 아니라 시연용 고정 값이다. */
    fun cockpit(): CockpitProjection =
        CockpitProjection(
            projectName = "demo-project",
            profileLabel = "기본",
            dateLabel = "YYYY-MM-DD",
            isReadOnlyDay = false,
            isStale = false,
            phaseLabel = "실행 중",
            statusLabel = "실행 준비 완료",
            queueStatusLabel = "활성",
            activeCardId = "card-demo0000001",
            activeSliceId = "slice-demo0000001",
            authorizedTargetLabel = "demo/DEMO_TARGET.md",
            stopReasonLabel = null,
            blockedReasonLabel = null,
            artifactItems = listOf(
                StatusChipModel("요청 입력함", "준비됨", "success"),
                StatusChipModel("작업 계획 파일", "준비됨", "success"),
                StatusChipModel("인수인계 파일", "준비됨", "success"),
                StatusChipModel("작업 상태 파일", "준비됨", "success"),
            ),
            opsValidationLabel = "통과",
            closureLabel = "미완료",
            executionCompletedLabel = "진행 중",
            lastSuccessfulReadAtLabel = "12:00:00",
            lastAttemptAtLabel = "12:00:00",
            primaryAction = CockpitActionItem(UiAction.RunDocSlice, "선택된 문서 작업 실행 (demo)", enabled = false),
            allowedActions = listOf(CockpitActionItem(UiAction.RunDocSlice, "선택된 문서 작업 실행 (demo)", enabled = false)),
            diagnostics = null,
            compatibilityDiagnostics = null,
        )

    fun todayWork(): TodayWorkProjection =
        TodayWorkProjection(
            title = "작업 계획",
            subtitle = "선택된 단일 작업과 수동 실행 조건을 명확히 보여줍니다.",
            statusChip = StatusChipModel("코드 실행 수동 대기 code_execution_held", "", "warning"),
            sections = listOf(
                InfoCardModel("현재 선택된 작업", listOf("작업" to "선택되지 않음")),
                InfoCardModel("실행 방식 Execution wrapper", listOf("형태" to "Execution wrapper: 미정")),
                InfoCardModel("허용된 대상 파일 Authorized target file", listOf("대상" to "Authorized target file: 없음")),
                InfoCardModel("멈춘 이유 Stop reason", listOf("이유" to "Stop reason: 없음")),
                InfoCardModel("이번 작업 범위", listOf("범위" to "현재 작업 범위가 아직 정리되지 않았습니다.")),
                InfoCardModel("금지 범위", listOf("범위" to "기준 파일 외 수정은 아직 허용되지 않습니다.")),
                InfoCardModel("확인 기준", listOf("기준" to "수동 실행 연결 전")),
            ),
            actions = listOf(
                ActionButtonModel("선택된 코드 작업 실행"),
                ActionButtonModel("할 일 다시 정리"),
            ),
            note = "수동 실행은 이후 PS1 façade에 연결됩니다.",
        )

    fun runStatus(): RunStatusProjection =
        RunStatusProjection(
            title = "실행 기록",
            subtitle = "활성 wrapper 실행과 역할별 실행 패킷 상태를 확인합니다.",
            stages = listOf(
                StatusChipModel("경로 확인 navi", ""),
                StatusChipModel("작업 수행 worker", ""),
                StatusChipModel("검토 reviewer", ""),
                StatusChipModel("기록 정리 dockeeper", ""),
                StatusChipModel("상태 확정 parent", ""),
            ),
            consoleLines = listOf("[idle] 활성 wrapper 실행이 없습니다."),
            stageDetailRows = listOf(
                "상태" to "역할별 실행 패킷과 응답 로그는 다음 단계에서 연결됩니다.",
            ),
            failureChips = listOf(
                StatusChipModel("Claude 사용량 대기 usage_limit_blocked", "", "warning"),
                StatusChipModel("턴 예산 초과 budget_max_turns", "", "warning"),
                StatusChipModel("래퍼 오류 wrapper_exception", "", "warning"),
                StatusChipModel("세션 문맥 한계 claude_context_limit", "", "warning"),
                StatusChipModel("계획 대상 충돌 dispatch_metadata_conflict", "", "warning"),
                StatusChipModel("수동 선행조건 필요 manual_prerequisite_required", "", "warning"),
            ),
            actions = listOf(
                ActionButtonModel("실행 패킷 열기"),
                ActionButtonModel("응답 로그 열기"),
                ActionButtonModel("조건 확인 후 재시도"),
            ),
        )

    fun recovery(): RecoveryProjection =
        RecoveryProjection(
            title = "복구 센터 · 마감 확인",
            subtitle = "최근 작업 기록 / 보존된 기록 / 현재 허용된 행동을 분리해서 보여줍니다.",
            activeCard = null,
            closureChecklistRows = listOf(StatusChipModel("마감 조건", "demo", "neutral")),
            closureNote = "demo mode에서는 실제 마감 조건을 평가하지 않습니다.",
            incompleteHandoffItems = emptyList(),
            lastKnownGoodLabel = "demo",
            compatibilityLabel = "호환됨",
            lockLabel = "없음",
            actions = emptyList(),
        )
}
