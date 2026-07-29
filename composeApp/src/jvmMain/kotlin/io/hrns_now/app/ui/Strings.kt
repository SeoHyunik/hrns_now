package io.hrns_now.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import io.hrns_now.core.domain.model.AppLocale

/** 현재 화면 언어다(새 Phase 8 §7). 기본값은 한국어이며, `App()`이 `AppViewModel.locale`로 덮어쓴다. */
val LocalAppLocale = staticCompositionLocalOf { AppLocale.Korean }

/**
 * 앱 상시 chrome(사이드바·상단 리본·알림함)의 정적 label 전용 locale catalog다(새 Phase 8 §7).
 * Setup/Cockpit/Strategy/Run/Recovery 화면 본문(각 projection이 조립하는 카드 제목·설명·버튼
 * 문구 다수)은 이 Phase에서 전부 번역하지 않았다 — 범위와 사유는 phase8 보고서에 남긴다.
 */
data class ChromeStrings(
    val navSectionWorkflow: String,
    val navSectionGuide: String,
    val navSetup: String,
    val navCockpit: String,
    val navStrategy: String,
    val navRun: String,
    val navRecovery: String,
    val readOnlyShellNote: String,
    val projectLabel: String,
    val notSelected: String,
    val readinessWorkspace: String,
    val readinessEngine: String,
    val readinessBridge: String,
    val readinessProfile: String,
    val readinessDoctor: String,
    val notificationBell: String,
    val notificationEmpty: String,
    val dismiss: String,
    val localeSelectorKorean: String,
    val localeSelectorEnglish: String,
)

fun chromeStrings(locale: AppLocale): ChromeStrings =
    when (locale) {
        AppLocale.Korean -> ChromeStrings(
            navSectionWorkflow = "작업 흐름",
            navSectionGuide = "안내",
            navSetup = "프로젝트 관리",
            navCockpit = "작업 현황",
            navStrategy = "작업 계획",
            navRun = "실행 기록",
            navRecovery = "복구 센터 · 마감 확인",
            readOnlyShellNote = "작업공간 경로와 기준 파일 존재 여부만 읽는 read-only 셸입니다.",
            projectLabel = "프로젝트",
            notSelected = "선택 안 됨",
            readinessWorkspace = "작업공간",
            readinessEngine = "엔진",
            readinessBridge = "저장소",
            readinessProfile = "프로필",
            readinessDoctor = "점검",
            notificationBell = "알림",
            notificationEmpty = "최근 알림이 없습니다.",
            dismiss = "닫기",
            localeSelectorKorean = "한국어",
            localeSelectorEnglish = "English",
        )

        AppLocale.English -> ChromeStrings(
            navSectionWorkflow = "Workflow",
            navSectionGuide = "Guide",
            navSetup = "Project Setup",
            navCockpit = "Status",
            navStrategy = "Plan",
            navRun = "Run History",
            navRecovery = "Recovery · Closure",
            readOnlyShellNote = "A read-only shell that only reads the workspace path and whether required files exist.",
            projectLabel = "Project",
            notSelected = "Not selected",
            readinessWorkspace = "Workspace",
            readinessEngine = "Engine",
            readinessBridge = "Repository",
            readinessProfile = "Profile",
            readinessDoctor = "Doctor",
            notificationBell = "Notifications",
            notificationEmpty = "No recent notifications.",
            dismiss = "Dismiss",
            localeSelectorKorean = "한국어",
            localeSelectorEnglish = "English",
        )
    }
