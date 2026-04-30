package io.hrns_now.infra

import io.hrns_now.core.projection.ActionButtonModel
import io.hrns_now.core.projection.InfoCardModel
import io.hrns_now.core.projection.RunStatusProjection
import io.hrns_now.core.projection.SetupProjection
import io.hrns_now.core.projection.ShellProjection
import io.hrns_now.core.projection.SourceFreshnessItem
import io.hrns_now.core.projection.StatusChipModel
import io.hrns_now.core.projection.TodayStatusProjection
import io.hrns_now.core.projection.TodayWorkProjection

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
                SourceFreshnessItem("오늘 상태 파일", "WORKDAY_STATE.json", "없음"),
                SourceFreshnessItem("오늘 할 일 파일", "TODAY_STRATEGY.md", "없음"),
                SourceFreshnessItem("오늘 실행 로그", "logs/YYYY-MM-DD/", "없음"),
            ),
            notAppOwnedMessages = listOf(
                "UI는 WORKDAY_STATE.json을 직접 쓰지 않습니다.",
                "UI는 마감 상태를 자체 확정하지 않습니다.",
                "현재 화면은 파일 기반 읽기 투영 셸입니다.",
            ),
        )

    fun setup(): SetupProjection =
        SetupProjection(
            title = "작업공간 연결",
            subtitle = "프로젝트와 harness-kit 실행 환경을 안전하게 연결합니다.",
            cards = listOf(
                InfoCardModel("하네스 엔진", listOf("상태" to "하네스 엔진: 연결되지 않음")),
                InfoCardModel("작업공간", listOf("상태" to "작업공간: 선택되지 않음")),
                InfoCardModel("저장소 연결", listOf("상태" to "저장소 연결: 아직 검사하지 않음")),
                InfoCardModel("실행 프로필", listOf("상태" to "실행 프로필: 기본")),
            ),
            actions = listOf(
                ActionButtonModel("상태 점검 실행"),
                ActionButtonModel("프로젝트 연결"),
                ActionButtonModel("프로젝트 파악 실행"),
                ActionButtonModel("브리프 확정"),
            ),
            note = "PS1 실행 연결은 다음 단계에서 추가합니다.",
        )

    fun todayStatus(): TodayStatusProjection =
        TodayStatusProjection(
            title = "오늘 현황",
            subtitle = "오늘의 작업 상태를 기준 파일과 작업 산출물 기준으로 읽어 보여줍니다.",
            stateRows = listOf(
                "current_phase" to "알 수 없음",
                "current_status" to "not_loaded",
                "active_card" to "없음",
                "작업 완료 execution_completed" to "false",
                "마감 완료 closure_validated" to "false",
            ),
            activeCardRows = listOf(
                "현재 작업 카드" to "현재 연결된 작업 카드가 없습니다.",
            ),
            roleStages = listOf(
                StatusChipModel("경로 확인 navi", ""),
                StatusChipModel("작업 수행 worker", ""),
                StatusChipModel("검토 reviewer", ""),
                StatusChipModel("기록 정리 dockeeper", ""),
                StatusChipModel("상태 확정 parent", ""),
            ),
            actions = listOf(
                ActionButtonModel("다음 할 일 정리"),
                ActionButtonModel("선택된 코드 작업 실행"),
                ActionButtonModel("마감 검증 실행"),
            ),
        )

    fun todayWork(): TodayWorkProjection =
        TodayWorkProjection(
            title = "오늘 할 일",
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
            title = "실행 현황",
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
                StatusChipModel("패킷 형식 오류 packet_contract_failed", "", "warning"),
                StatusChipModel("상태 확정 실패 state_finalization_failed", "", "warning"),
                StatusChipModel("신규 파일 경로 처리 실패 new_target_path_failed", "", "warning"),
            ),
            actions = listOf(
                ActionButtonModel("실행 패킷 열기"),
                ActionButtonModel("응답 로그 열기"),
                ActionButtonModel("조건 확인 후 재시도"),
            ),
        )
}
