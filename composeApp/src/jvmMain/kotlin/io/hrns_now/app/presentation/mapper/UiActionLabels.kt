package io.hrns_now.app.presentation.mapper

import io.hrns_now.core.domain.model.UiAction

/**
 * [UiAction]의 표시 문구다. `doc/hrns_now_design_pattern.md` §5.3 "표시 label을 action ID로
 * 사용하지 않는다"에 따라 domain sealed interface가 아니라 여기 presentation에 둔다.
 */
fun UiAction.displayLabel(): String =
    when (this) {
        UiAction.ConnectProject -> "프로젝트 연결"
        UiAction.SelectWorkspaceDay -> "날짜 선택"
        UiAction.OpenToday -> "오늘로 이동"
        UiAction.Refresh -> "새로고침"
        UiAction.EditRequest -> "요구사항 작성"
        UiAction.RunDoctor -> "연결 점검"
        UiAction.RunOpsValidation -> "작업 준비 점검"
        UiAction.BootstrapDay -> "오늘 작업 시작"
        UiAction.RunPlanning -> "계획 실행"
        UiAction.RunReplan -> "재계획 실행"
        UiAction.ReviewPlan -> "계획 확인"
        UiAction.RunCodeSlice -> "선택된 코드 작업 실행"
        UiAction.RunDocSlice -> "선택된 문서 작업 실행"
        UiAction.RunValidationSlice -> "검증 전용 작업 실행"
        UiAction.RunClosureValidation -> "마감 검증 실행"
        UiAction.ReviewClosure -> "마감 확인"
        UiAction.CloseDay -> "오늘 종료"
        UiAction.OpenRecoveryCenter -> "복구 센터 열기"
        UiAction.ShowCompatibilityIssue -> "호환성 확인"
        UiAction.ViewExecutionStatus -> "실행 기록 보기"
    }
