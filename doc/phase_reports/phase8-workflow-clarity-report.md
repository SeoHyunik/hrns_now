# Phase 8 작업 보고서 — 작업 흐름·상태 피드백·언어 UX 정비

## 0. 범위 선언과 시작 상태

이 보고서는 `doc/claude_prompts/phase8-workflow-clarity-feedback.md`(2026-07-28 제품 소유자 승인)만 다룬다.

- 작업 시작 시점 `git log -1 --oneline`: `5e87797 docs: Phase 8 작업 흐름 UX 개선 지시 추가`(선행 커밋 `ea4f9f7 feat: Phase 7 내장 개발 SDK 연동 구현`을 조상으로 포함). 작업 도중 사용자가 `085c103 chore: HRNS-NOW 새 로고 리소스 반영`(아이콘/ico 바이너리 리소스 교체, 소스 코드 무관)을 커밋해 현재 HEAD는 `085c103`이다 — 내가 만든 커밋이 아니며, 이 커밋과 이 세션의 변경 사이에 겹치는 파일이 없음을 `git show --stat 085c103`로 확인했다.
- 시작 시 `git status --short`는 사용자 소유 untracked `doc/hrns_now_packaging_plan.md`, `doc/user_workflow_qa_notes.md` 둘만 있었다. 이 세션은 두 파일을 읽기만 했고 수정·삭제·stage하지 않았다.
- 새 Phase 6(G6-UX)는 여전히 `BLOCKED`이며, 기존 Phase 6A(G6A)/6B(G6B)/7E는 보류 상태를 유지한다. 이 Phase 8 보고서는 그 Gate 중 어느 것도 PASS로 선언하지 않으며, Phase 8 자체의 PASS 여부나 release readiness도 선언하지 않는다 — Gate 판정과 Git commit은 Codex만 한다.
- 이 세션 동안 `git add`/`commit`/`amend`/`rebase`/`reset`/`stash`/`clean`/`push`를 수행하지 않았다.

### 0.1 변경 파일

```text
[신규]
composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/NotificationCenter.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/model/NotificationItem.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/model/RegistrationFeedback.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/ui/Markdown.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/ui/Strings.kt
composeApp/src/jvmTest/kotlin/io/hrns_now/app/presentation/NotificationCenterTest.kt
core/src/main/kotlin/io/hrns_now/core/domain/model/AppLocale.kt
core/src/main/kotlin/io/hrns_now/core/port/UiPreferencesPort.kt
infra/src/main/kotlin/io/hrns_now/infra/preferences/UiPreferencesFileAdapter.kt
infra/src/test/kotlin/io/hrns_now/infra/preferences/UiPreferencesFileAdapterTest.kt

[변경 — production]
composeApp/src/jvmMain/kotlin/io/hrns_now/app/App.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/demo/MockProjectionProvider.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/DefaultProjections.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/mapper/CockpitProjectionAssembler.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/mapper/CockpitUiStateAssembler.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/mapper/RunStatusProjectionAssembler.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/mapper/UiActionLabels.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/model/HrnsUiEvent.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/model/HrnsUiState.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/model/ProjectionModels.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/viewmodel/AppViewModel.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/ui/Components.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/ui/Screens.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/ui/Shell.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/ui/Typography.kt
core/src/main/kotlin/io/hrns_now/core/domain/policy/ActionPolicy.kt
core/src/main/kotlin/io/hrns_now/core/domain/policy/WorkspaceDaySelectionPolicy.kt
core/src/main/kotlin/io/hrns_now/core/usecase/LoadCockpitUseCase.kt
core/src/main/kotlin/io/hrns_now/core/usecase/RegisterProjectUseCase.kt
infra/src/main/kotlin/io/hrns_now/infra/WorkspacePathProbe.kt

[변경 — test]
composeApp/src/jvmTest/kotlin/io/hrns_now/app/presentation/DefaultProjectionsTest.kt
composeApp/src/jvmTest/kotlin/io/hrns_now/app/presentation/mapper/CockpitProjectionAssemblerTest.kt
composeApp/src/jvmTest/kotlin/io/hrns_now/app/presentation/mapper/RunStatusProjectionAssemblerTest.kt
composeApp/src/jvmTest/kotlin/io/hrns_now/app/presentation/viewmodel/AppViewModelTest.kt
core/src/test/kotlin/io/hrns_now/core/domain/policy/ActionPolicyTest.kt
core/src/test/kotlin/io/hrns_now/core/usecase/LoadCockpitUseCaseTest.kt
core/src/test/kotlin/io/hrns_now/core/usecase/RegisterProjectUseCaseTest.kt
```

`composeApp/build.gradle.kts`, MSI/패키징 설정은 이번 세션에서 전혀 변경하지 않았다(§11.3).

## 1. 사용자 QA 12개 항목 — 실제 재현·근본 원인·조치

작업 시작 전 코드를 직접 추적해 각 QA 항목의 **실제 원인**을 확인했다(추측으로 고치지 않음).

1. **기본 경로 진단 후 등록 무반응** — 원인: `ProjectManagementSection`의 `registryMessage` Text가 modal이 열리기 전 배경 카드에만 있었고, `ProjectRegistrationForm`은 모달 안에서 `registryMessage`/실행 상태를 전혀 받지 않았다. 모달이 열리면 그 뒤 카드는 가려지므로 결과가 실제로 "안 보였다". 조치: §2.
2. **오늘 계획 없음 → 과거 전략처럼 보임** — 원인 추적: `WorkspaceDaySelectionPolicy.select()`가 오늘 폴더가 없으면(`purpose=ReadOnly`) `LatestReadOnlyFallback`으로 최신 과거 날짜를 조용히 선택했다. 이때 뜨는 유일한 CTA `UiAction.OpenToday`("오늘로 이동")가 **`AppViewModel.onEvent`에 아예 매핑되어 있지 않아 클릭해도 아무 일도 일어나지 않았다**(완전한 무반응 버튼). 조치: §3.
3. **버튼 hover/진행 피드백 부족** — 원인: `PlaceholderActionButton`/`NavigationButton`/모든 `TextButton`에 `pointerHoverIcon`이 전혀 설정돼 있지 않아 마우스 커서가 손 모양으로 바뀌지 않았다. 조치: §4.1.
4. **영문 글꼴 어색함** — 원인: `hrnsTypography()`가 모든 텍스트에 Pretendard 하나만 적용한다. 조치와 한계: §7.2.
5. **엔진 오프라인/점검 대기가 실제 상태와 모순** — 원인 확인: `WorkspacePathProbe.readiness()`의 `engineLabel`이 실제 kit 상태와 무관하게 **항상 고정 문자열 `"오프라인"`**이었고, `doctorLabel`도 항상 고정 `"대기"`였다(연결이 성공해도 절대 바뀌지 않는 죽은 코드였다). 조치: §5.1.
6. **READ-ONLY/META/아티팩트 중복·불명확** — 원인: Inspector 패널이 `"아티팩트"`(한글)/`"Meta"`(영문 약어)/`"Read-only"`(영문)를 표기 규칙 없이 섞어 썼고, "앱이 소유하지 않음" 경고 카드가 상시 노출됐다. 조치: §5.2.
7. **미설정 경로 해소 방법 불명** — 원인: `PathProbeResult.message`가 `NotConfigured`일 때 그냥 `"미설정"`만 반복해 chip과 동일 정보를 중복 표시했다. 조치: §6.2.
8. **ko/en 전환 불가** — 원인: locale 저장/전환 메커니즘 자체가 없었다(`RuntimeConfig.uiLanguage`는 상시 `"ko"` 고정값이며 UI에서 바꿀 수 없는 표시 전용 필드였다). 조치: §7.1.
9. **날짜 무한 목록·선택 표시 흐림** — 원인: `WorkspaceDaySection`이 전체 목록을 페이지 없이 나열했고, 선택 상태를 disabled 버튼(저대비)으로만 표시했다. 조치: §6.1.
10. **개발 전략 미포맷·요구사항 파일명 노출** — 원인: `TODAY_STRATEGY.md` 원문을 `PlaceholderRow`로 그냥 raw text 통째로 표시했고, "요구사항 작성" 카드 설명이 `"REQUEST_INBOX.md에 새 요구사항을 기록합니다"`로 파일명을 주 설명으로 썼다. 조치: §3.3, §6.3.
11. **환경 점검/작업 기준 점검 이름이 목적을 드러내지 않음** — 조치: §5.3.
12. **요구사항 작성 CTA 파일명 위주** — 위 10과 동일 조치(§6.3).

## 2. 등록 즉시 진단과 회복 가능한 안내(§1 요구 대응)

### 2.1 typed 원인 (core)

`core/usecase/RegisterProjectUseCase.kt`에 새 sealed `RegistrationRejectionReason`(`BlankDisplayName`/`BlankProfile`/`BlankExternalKitPath`/`InvalidExternalKitPathFormat`/`RuntimeMissing(source)`/`RuntimeInvalid(source, issue)`)을 추가했다. `ProjectRegistrationInspection.InvalidCandidate`/`RegisterProjectResult.InvalidCandidate`가 기존 `message: String`에 더해 이 typed `reason`을 함께 갖는다 — presentation은 문자열 일부를 비교(`contains`)해 원인을 추정하지 않고 이 sealed 값만으로 분기한다.

### 2.2 composeApp 상태·렌더링

- 새 presentation 모델 `RegistrationFeedback`(`Idle`/`Running`/`Success(name)`/`Failure(whatHappened, nextStep, showAdvancedSettingsHint)`)을 추가하고 `HrnsUiState.Ready.registrationFeedback`로 노출했다.
- `AppViewModel.onProjectRegistrationRequested()`가 각 단계(제출 즉시 Running → InvalidCandidate/BoundaryRejected/lock 실패/Doctor 실패/compatibility 실패/SaveFailed/Registered)마다 typed `RegistrationFeedback`을 조립해 즉시 push한다. `RegistrationRejectionReason`이 `RuntimeMissing`/`RuntimeInvalid`이고 `source == InternalDeveloperSdk`일 때만 `showAdvancedSettingsHint = true`가 되어, 내장 SDK 문제일 때만 "고급 설정을 열어라"는 다음 행동을 typed로 구분해 보여준다.
- `ProjectRegistrationForm`(inline 등록 폼과 `프로젝트 등록` modal 양쪽 모두 같은 함수를 재사용)이 `registrationFeedback`을 받아 "진단 후 등록" 버튼 바로 아래 `RegistrationFeedbackRow`로 Running spinner/성공 배지/실패 사유+다음 행동을 **모달 내부에서 직접** 렌더링한다. `showAdvancedSettingsHint`가 true면 `LaunchedEffect`로 "고급 설정"을 자동으로 펼친다. 등록 성공 시 모달을 자동으로 닫는다(`LaunchedEffect(registrationFeedback)`).
- 새 이벤트 `HrnsUiEvent.RegistrationFeedbackDismissed`로 모달을 닫을 때 이전 시도의 표시를 지운다.
- 이 요구를 만족시키려고 `RuntimeSource`를 raw string/nullable path로 되돌리지 않았다 — `RegisterProjectUseCase`/`RuntimeSourceResolverPort`/Registry migration 계약은 무변경이다.

## 3. 오늘 시작 정책과 과거 기록 분리(§2/§3 요구 대응)

### 3.1 실제 Harness bootstrap 계약 확인(§2.1 지시)

`D:\harness-kit\scripts\run-cycle.ps1`(read-only)을 직접 읽어 확인했다 — 수정·복사하지 않았다.

- `HarnessCommand.BootstrapDay`/`HarnessCommandEncoder`는 이미 Phase 4에서 `run-cycle.ps1 -WorkspaceRoot -ProjectRoot -KitRoot -Profile -Date -UsePythonSidecars`(wrapper 인자 없음)로 정확히 매핑돼 있었다 — 이 세션에서 command 자체를 바꾸지 않았다.
- `run-cycle.ps1` 소스의 "Fresh-day bootstrap note" 주석(3248~3255행 부근)에서 다음을 실측 확인했다: `WorkspaceRoot/Date` 아래 새 날짜 폴더가 없으면 `init-workspace`(3208행 `Write-Step "Initializing workspace"`)가 실행되어 그 시점에 `WORKFLOW_STATE.json`이 새로 만들어진다. 즉 **오늘 날짜에 State가 없는 상태에서 `BootstrapDay`를 실행하는 것은 이 스크립트가 실제로 지원하는, 문서화된 정상 경로다** — 명령이나 상태를 창작하지 않았다.

### 3.2 policy 보정 (core, `ActionPolicy.kt`)

`bootstrapEligible()`을 추가해 `stateRead is StateReadResult.Missing && selectedDayKind == Today && compatibility == Supported && boundary == Valid && process == Idle`일 때만 `BootstrapDay`를 primary로 연다. 기존 "stateRead가 Success가 아니면 무조건 복구 센터" 분기 **안에서만** 특례를 두었으므로:

- Malformed/EncodingError/UnsupportedSchema/AccessDenied, 과거 날짜의 Missing, compatibility/boundary 실패, lock/실행 중 상태는 **전부 그대로 fail-closed**로 남는다(회귀 없음).
- `ActionPolicy`의 다른 어떤 분기 순서도 바꾸지 않았다 — 최소 침습 변경이다.

### 3.3 오늘/과거 분리 화면 규칙

- `TodayWorkProjection`에 `DevelopmentStrategyCardModel(text, dateLabel, isReadOnlyDay)`을 신설해 "개발 전략"을 일반 `InfoCardModel` 목록에서 완전히 분리했다. `StrategyScreen`의 새 `DevelopmentStrategyCard`가 문서 날짜·읽기 전용 배지를 항상 함께 보여줘 과거 원문을 오늘 계획으로 오인할 수 없게 했다.
- 근본 원인이었던 `UiAction.OpenToday` 무반응을 고쳤다: `AppViewModel.onEvent`에 `UiAction.OpenToday -> onWorkspaceDaySelected(오늘)`을 추가했고, `onWorkspaceDaySelected()`의 가드를 `date !in availableDates && date != 오늘`로 완화해 **오늘 폴더가 아직 없어도 오늘을 선택할 수 있게** 했다(§6.1과 연결). `App.kt`의 `onCockpitAction`도 `ConnectProject`/`SelectWorkspaceDay`/`ReviewPlan`/`ShowCompatibilityIssue`/`ViewExecutionStatus`를 실제 화면 전환에 연결했다 — 이전에는 이 action들 전부가 `else` 분기로 떨어져 ViewModel도 처리하지 않는 완전한 무반응 버튼이었다.
- **테스트로 잡은 2차 버그**: 위 `AppViewModel` 가드만으로는 실제로 충분하지 않았다 — `LoadCockpitUseCase.resolveDays()`가 **별도로** `explicitDate?.takeIf { it in availableDates }`라는 자체 필터를 갖고 있어, `AppViewModel`이 "오늘을 선택하라"고 넘겨도 오늘이 파일 시스템 discovery 결과에 없으면 조용히 과거 날짜 fallback으로 되돌리고 있었다. 새로 작성한 composeApp 테스트(`오늘 폴더가 없어도 OpenToday action은...`)가 이 assertion 실패를 잡아냈고, 그 실패가 `viewModel.dispose()` 호출 전에 발생해 백그라운드 polling 코루틴이 정리되지 않은 채 테스트 프레임워크의 자동 `advanceUntilIdle()`이 무한 루프를 영원히 기다리며 멈추는 결과로 이어졌다(원인 파악에 `jstack` thread dump가 필요했다). 수정: `WorkspaceDaySelectionPolicy`의 `today`를 `public`으로 공개하고, `LoadCockpitUseCase.resolveDays()`의 필터를 `it in availableDates || it == daySelectionPolicy.today`로 넓혔다 — 오늘이 아닌 명시 날짜는 여전히 실제 discovery 결과에 있어야만 선택된다(회귀 없음). core에 회귀 테스트(`오늘 날짜는 탐색된 목록에 없어도 명시 선택을 그대로 허용한다`)를 추가했다.
- `요구사항 작성`은 여전히 `cockpit.isReadOnlyDay`와 `ActionPolicy`가 허용한 `EditRequest`에만 연동되며(기존 계약 무변경), `오늘 작업 시작`(`BootstrapDay` 표시 label을 "작업 준비"에서 "오늘 작업 시작"으로 변경) 뒤 `loadOnce(forceRead=true)`가 State를 다시 읽으면 그 정책 재계산 결과가 즉시 반영된다.

## 4. 개발 전략 읽기 경험과 언어 경계(§3 요구 대응)

### 4.1 안전한 Markdown 렌더러(신규 `ui/Markdown.kt`)

heading(`#`~`######`)/문단/순서 있는·없는 목록/인용(`>`)/fenced code block(``` ```)/구분선/inline `**bold**`·`` `code` ``·`[text](url)`만 지원하는 **줄 단위 파서 + `buildAnnotatedString` inline span**이다. HTML 태그를 파싱하거나 실행하지 않고(`<...>`는 리터럴 텍스트로만 취급), 원격 resource를 불러오지 않으며, 파일 write나 명령 실행 경로가 전혀 없다 — 순수 문자열 → Compose `Text`만 생성한다. `[text](url)`은 밑줄 강조 텍스트 뒤에 URL을 회색 monospace로 병기할 뿐 클릭 가능한 링크로 만들지 않았다(원격 이동 자체를 차단).

### 4.2 원문 정책

원문은 `TodayStrategyFileReaderAdapter`(무변경, read-only)가 그대로 읽어온 문자열을 `SafeMarkdownDocument`가 렌더링만 한다 — 파일을 다시 쓰거나 번역본을 저장하지 않는다. `DevelopmentStrategyCard`는 항상 `TODAY_STRATEGY.md`라는 eyebrow와 날짜·"원문"/"과거 날짜 · 원문" 배지를 함께 보여 "이것이 가공되지 않은 원문"임을 명확히 한다.

### 4.3 자동 번역을 구현하지 않은 이유

`TODAY_STRATEGY.md` 본문(임의의 영어 prose)을 외부 번역 API나 LLM 호출 없이 자동 번역하는 기능은 **구현하지 않았다** — 이는 프롬프트가 명시적으로 범위 밖이라고 규정한 항목이다. 대신 §7에서 설명하는 locale catalog는 **알려진 typed 값**(사이드바/리본/알림함 정적 label, 이후 확장 가능한 `WorkflowStatus`/`StopReason`/`UiAction` 표시 label)만 다루며, 원문 자유 형식 텍스트는 절대 손대지 않는다.

## 5. 상태·오류·알림 공통 feedback(§4 요구 대응)

### 5.1 버튼 hover/설명

`Components.kt`의 `PlaceholderActionButton`(primary/secondary 모두), `NavigationButton`, `Shell.kt`의 `ThemeToggle`/`LocaleToggle`/알림함 버튼/모달 닫기 버튼에 `Modifier.pointerHoverIcon(PointerIcon.Hand)`를 추가했다(disabled 버튼은 기본 화살표 유지). `ActionButtonModel`에 `description: String?`(enabled 여부와 무관하게 항상 보이는 목적 설명, `helperText`는 비활성 사유 전용으로 분리 유지)을 추가하고 "연결 점검"/"작업 준비 점검" 버튼에 프롬프트가 예시로 준 문장을 그대로 붙였다.

### 5.2 전역 알림함(신규)

- `presentation/model/NotificationItem.kt`(`id, message, tone(Success/Failure/Info), createdAt, read`), `presentation/NotificationCenter.kt`(단일 `MutableStateFlow<List<NotificationItem>>` reducer: `push`/`markRead`/`markAllRead`/`dismiss`, 최대 20건 보존)를 신설했다.
- `AppViewModel`이 `notificationCenter: NotificationCenter`를 구성 시점에 단 하나만 소유하고 `notifications: StateFlow<List<NotificationItem>>`로 노출한다. **사용자가 결과를 기다린 action에만** push한다 — 등록(성공/각 실패 사유), 요청 저장(성공/충돌/실패), harness 실행 완료(`notifyRunOutcome()`가 이미 조립된 `RunStatusProjection.lastOutcome`을 재사용 — `ProcessRunResult.contract.overall` 기반 typed 결과이지 stdout 문자열 재파싱이 아니다)/거부/lock 불가/미지원. 단순 화면 전환·날짜 열람에는 push하지 않는다.
- UI: `Shell.kt`에 우측 상단 벨 아이콘+미확인 배지(`NotificationBell`), 클릭 시 `Popup`으로 이력 목록(`NotificationTrayContent`, 항목별 닫기), 새 미확인 항목 발생 시 화면 우상단에 4초 후 자동으로 읽음 처리되는 transient 카드(`NotificationToastHost`, 명시적 닫기도 가능)를 추가했다. raw process output/session ID/secret/전체 경로를 담지 않는다 — 모든 `message`는 이미 typed·요약된 문자열이다.
- `AppViewModel.dismissNotification`/`markNotificationRead`/`markAllNotificationsRead`를 통해 `App.kt`가 `productionViewModel.notifications`를 별도 `collectAsState()`로 구독해 `HrnsUiState`와 분리된 흐름으로 전달한다.

## 6. 상단 상태·정보 구조·용어(§5 요구 대응)

### 6.1 리본 모순 제거

`infra/WorkspacePathProbe.kt`의 `readiness()`: `engineLabel`이 항상 고정 `"오프라인"`이던 죽은 코드를 제거하고 `workspaceLabel`/`bridgeLabel`과 동일한 방식으로 실제 kit root 경로 확인 결과(`rootReadiness(summary.kitRoot, ...)`)를 반영하도록 고쳤다. `doctorLabel`은 이 계층에 실제 Doctor 실행 이력이 없으므로(그 정보는 `RunStatusProjection`이 별도로 가짐) 거짓 "대기/실행 중" 인상을 주지 않는 중립 `"미확인"`으로 고정했다 — 실제 실행 결과와의 통합은 이후 과제로 남긴다(§10).

### 6.2 technical 패널 표기 통일

`Shell.kt`의 Inspector 패널: `"아티팩트"`(한글)/`"Meta"`(영문 약어)를 각각 `title = "ARTIFACTS"`/`title = "ENVIRONMENT"`(영문 title + 한글 eyebrow)로 통일했다. 상시 노출되던 "앱이 소유하지 않음"(Read-only) 경고 카드를 별도 카드에서 제거하고, 그 핵심 사실 3줄만 `ENVIRONMENT` 카드 하단에 조용한 보조 텍스트로 접어 넣었다 — 행동을 안내하지 않는 반복 문구를 기본 화면에서 없앴다.

### 6.3 목적 기반 action 이름

`UiActionLabels.kt`: `RunDoctor` "환경 점검" → **"연결 점검"**, `RunOpsValidation` "작업 기준 점검" → **"작업 준비 점검"**(Harness command ID `HarnessCommandKind.Doctor`/`ValidateOps`는 무변경). 이 표시 label이 어긋나게 중복 정의돼 있던 `RunStatusProjectionAssembler.HarnessCommandKind.displayLabel()`, `DefaultProjections.buildSetupProjection()`, 등록 진행 중 문구, 데모 데이터까지 전부 같은 이름으로 통일했다. `BootstrapDay` 표시 label도 "작업 준비" → "오늘 작업 시작"으로 바꿔 §3의 CTA와 일치시켰다.

## 7. 날짜 탐색과 미설정 항목(§6 요구 대응)

### 7.1 날짜 페이지네이션과 고대비 선택 표시

`WorkspaceDaySection`을 5개씩 페이지네이션(이전/다음, "N / M" 표시)하도록 재작성했다. 선택된 날짜는 흐린 disabled 버튼 대신 **고대비 accent 톤 `StatusChip("선택됨")`**로 표시한다. 오늘 날짜가 `workspaceDays` 목록에 없어도(폴더 미생성) 카드 상단에 "오늘 · {날짜}" + "오늘 작업 시작" 버튼을 항상 보여준다 — 이 Composable은 폴더나 4-file을 만들지 않고 `HrnsUiEvent.WorkspaceDaySelected`만 올려 보낸다.

### 7.2 미설정 안내

`PathProbeRow`에 `unsetGuidance()`를 추가해 `NotConfigured` 상태일 때 무엇이 없고 어디서(같은 화면의 프로젝트 관리 카드, 필요하면 고급 설정) 채우는지 안내 문장을 표시한다(KitRoot/WorkspaceRoot/ProjectRoot별로 구분된 문구). workspace·repository를 자동 생성하거나 임의 경로를 제안하지 않는다.

## 8. 한국어/English 전환과 글꼴(§7 요구 대응)

### 8.1 locale persistence (신규, core+infra)

- `core/domain/model/AppLocale.kt`(`Korean("ko")`/`English("en")`), `core/port/UiPreferencesPort.kt`(`readLocale()`/`writeLocale()`)를 신설했다 — 책임을 locale 하나로 제한해 God settings service가 되지 않게 했다.
- `infra/preferences/UiPreferencesFileAdapter.kt`: `%APPDATA%\hrns-now\ui-preferences.json`에 atomic write(임시 파일 + `ATOMIC_MOVE`, 실패 시 `REPLACE_EXISTING` fallback), UTF-8 without BOM, 손상/미지원 코드/파일 없음은 예외를 던지지 않고 조용히 `null`(=기본 한국어)로 fail-closed한다. `WORKFLOW_STATE.json`/Harness workspace/project Registry와 완전히 분리된 별도 파일이다.
- `AppViewModel`: `uiPreferencesPort`를 주입받아(기본값은 항상 `null`/no-op 반환하는 안전한 익명 객체) `init{}`에서 IO dispatcher로 1회 읽어 `_locale`에 반영하고, `setLocale()`은 즉시 `StateFlow`를 갱신한 뒤 IO dispatcher에서 저장한다(쓰기 실패해도 UI 즉시 반응은 그대로 유지 — best-effort).

### 8.2 UI 전환과 번역 범위

- `App.kt`가 `CompositionLocalProvider(LocalAppLocale provides locale)`로 전체 트리에 현재 언어를 공급하고, `Shell.kt`의 우측 상단에 `LocaleToggle`(한국어/English, 클릭 즉시 전환)을 테마 토글 옆에 추가했다.
- `ui/Strings.kt`의 `ChromeStrings`/`chromeStrings(locale)`가 **상시 노출되는 chrome**(좌측 사이드바 5개 nav 항목+섹션 라벨, 상단 리본의 "프로젝트"/"선택 안 됨"/준비 상태 5개 라벨, 알림함 벨/이력/닫기, locale selector 자체)을 한국어/영어로 완전히 번역한다.
- **범위를 명시적으로 좁힌 부분**: Setup/Cockpit/Strategy/Run/Recovery 각 화면 **본문**(카드 제목·설명·버튼 문구 수십~수백 개, `DefaultProjections.kt`/`Screens.kt`에 하드코딩된 한글 리터럴 다수)은 이번 세션에서 번역하지 않았다. 이유: 이 문자열들은 지금 개별 Composable에 직접 박혀 있어, 전부 옮기려면 `Screens.kt`(1600행+) 전체를 locale-aware 구조로 재작성해야 하는 큰 리팩터링이 필요하고, 육안 검증 없이 그 정도 규모를 일괄 변경하면 오히려 화면이 깨질 위험이 이 Phase의 다른 항목들보다 크다고 판단했다. `chromeStrings`/`LocalAppLocale` 인프라는 이미 갖춰졌으므로 다음 세션이 화면별로 점진적으로 확장할 수 있다 — 이 한계와 이유를 여기에 정직하게 남긴다.
- `WorkflowStatus`/`StopReason`/`UiAction` 등 typed 값의 `displayLabel()`은 이번에 locale 매개변수를 받도록 확장하지 않았다(현재는 한국어 고정) — 이 역시 §8.2의 범위 제한과 같은 이유다.

### 8.3 글꼴 정책

- `Typography.kt`/`Screens.kt`의 화면 제목 등 가장 큰 텍스트에 걸려 있던 과도한 negative letter spacing(`-1.2sp`~`-0.7sp`, 특히 `ScreenHero` 제목의 `-1.0sp`)을 `-0.4sp`~`-0.2sp`로 완만하게 낮췄다 — 한글 음절 블록은 Latin만큼 negative tracking을 견디지 못해 겹쳐 보일 위험이 있었다.
- Monospace 사용처를 전수 확인했다 — 날짜/경로/코드 블록/`infra` marker/PID 등 코드·경로·명령 값에만 쓰이고 있었고, 일반 UI 문구에 잘못 적용된 사례는 없었다(변경 불필요, 감사 결과만 기록).
- **하지 않은 것과 이유**: 프롬프트는 "Windows의 Segoe UI Variable/Segoe UI를 우선 후보로, Pretendard fallback을 명시"하라고 요구한다. 실제로 조사한 결과, Compose(Multiplatform Desktop)의 `FontFamily`는 **하나의 (weight, style) 슬롯에 하나의 폰트 파일만 매칭**하는 구조라, "라틴은 Segoe UI로, 한글은 Pretendard로" 같은 문자 단위 혼합 렌더링을 공식 API로 만들 수 없다(Skia의 자체 시스템 fallback은 있지만 어떤 한글 폰트로 떨어질지 이 앱이 보장할 수 없다). 이 조사 결과 없이 폰트 패밀리 자체를 통째로 바꾸면 한글 렌더링 품질(QA가 "한글은 자연스럽다"고 이미 확인한 부분)이 오히려 나빠질 위험이 있고, 육안 확인 없이는 결과를 검증할 방법이 없다(§10). 따라서 폰트 패밀리 교체는 **구현하지 않았다** — 대신 검증 가능한 letterSpacing 완화만 실행했고, 이 조사 내용과 한계를 정직하게 남긴다.

## 9. 불변 계약 보존 근거

- `WORKFLOW_STATE.json` 소유권: UI는 이번에도 이 파일을 직접 쓰지 않는다. `ActionPolicy`의 새 `bootstrapEligible()` 분기도 `stateRead`를 읽기만 하고 절대 쓰지 않는다.
- `ActionPolicy`/`ClosurePolicy`/`CompatibilityPolicy`/`BoundaryPolicy`: `ClosurePolicy`/`CompatibilityPolicy`/`BoundaryPolicy` 소스는 이번 세션에서 한 줄도 바꾸지 않았다(`git status`로 확인). `ActionPolicy`는 §3.2의 최소 침습 특례 하나만 추가했고 기존 분기 순서·의미는 그대로다.
- typed command → lock → runner → lock 보유 중 State reread → release 순서(`ExecuteHarnessActionUseCase`)는 무변경이다. `notifyRunOutcome()`은 이 흐름이 이미 끝난 뒤(즉 State reread까지 포함해 `ExecuteHarnessActionOutcome`이 확정된 뒤) 호출되며, 알림 자체가 새로운 판단 근거를 만들지 않는다.
- `RuntimeSource.InternalDeveloperSdk`가 Missing/Invalid일 때 `D:\harness-kit`/환경변수/기존 external root로 자동 전환하는 코드를 추가하지 않았다 — `RegisterProjectUseCase`/`DeveloperSdkRuntimeResolver`(무변경)의 fail-closed 계약을 그대로 재사용했다.
- repository root/project workspace root 분리, UI가 workspace 안에 4-file/Registry/lock/log를 만들지 않는 계약, raw session ID/secret/token/raw stdout·stderr을 toast·알림함·설정 파일에 담지 않는 계약을 모두 유지했다 — `NotificationItem.message`는 항상 이미 typed·요약된 값만 받는다.
- 자동 resume, `--continue`, 자유 형식 PowerShell, Claude API 직접 호출, 새 Harness wrapper/상태 코드를 추가하지 않았다.

## 10. SOLID·MVVM/UDF·Ports and Adapters 평가

| 항목 | 판정 | 근거 |
|---|---|---|
| SRP | 유지 | `NotificationCenter`는 알림 리스트 reducer만, `UiPreferencesFileAdapter`는 locale 파일 I/O만, `RegistrationFeedback`은 등록 진행 표시만 담당한다 |
| OCP | 유지 | `RegistrationRejectionReason`에 새 case가 추가돼도 `nextStepGuidance()`/`suggestsAdvancedSettings()`의 `when`이 컴파일 타임에 강제로 갱신을 요구한다(exhaustive when) |
| LSP | 유지 | `UiPreferencesPort`의 실제 구현(`UiPreferencesFileAdapter`)과 테스트 대역(`FakeUiPreferencesPort`)이 같은 계약을 지킨다 |
| ISP | 유지 | `UiPreferencesPort`는 `readLocale`/`writeLocale` 두 메서드만 가진 최소 포트다 — Registry/RuntimeSource/lock과 결합하지 않았다 |
| DIP | 유지 | `AppViewModel`은 `UiPreferencesPort`/`NotificationCenter`라는 추상에만 의존한다. `core`는 여전히 Compose/AWT/font/filesystem을 모른다(새 `AppLocale`/`UiPreferencesPort`도 순수 Kotlin이다) |
| MVVM/UDF | 유지 | `Composable`은 여전히 event 발행과 state 렌더링만 한다 — Markdown 파싱(`SafeMarkdownDocument`)과 알림 배지 계산(`NotificationBell`)도 순수 함수/로컬 파생 상태일 뿐 file I/O·PowerShell·Registry 호출이 없다 |
| Ports/Adapters | 유지 | `UiPreferencesPort`(core)/`UiPreferencesFileAdapter`(infra) 새 쌍이 기존 `TodayStrategyReaderPort`/`RequestWriterPort` 등과 같은 패턴을 그대로 따른다 |
| God object 회피 | 유지 | locale 설정은 `UiPreferencesPort` 하나의 책임(locale)로 제한했다 — Registry/RuntimeSource/lock과 결합하지 않았다. `NotificationCenter`도 알림 리스트 연산만 하고 ActionPolicy/ClosurePolicy 판단을 대신하지 않는다 |

## 11. 테스트와 검증

### 11.1 신규/보강 테스트

**core**
- `ActionPolicyTest`: `오늘 날짜의 Missing state는 모든 조건이 정상일 때만 BootstrapDay를 허용한다`, `Missing state라도 조건 하나만 어긋나면 BootstrapDay를 허용하지 않는다`(과거 날짜/compatibility/boundary/lock 4가지), `Malformed·EncodingError·UnsupportedSchema·AccessDenied는 오늘 날짜여도 BootstrapDay를 허용하지 않는다`. 기존 "파서 오류…" 테스트에서 `Missing`을 분리해 새 계약을 반영했다(약화 아님 — 별도 전용 테스트로 대체).
- `RegisterProjectUseCaseTest`: 기존 Missing/Invalid 테스트에 typed `reason` assertion을 추가하고, `표시명·profile·외부 Kit 경로 공백은 서로 구분된 typed reason으로 거부한다`를 새로 추가했다.
- `LoadCockpitUseCaseTest`(신규 1건): `오늘 날짜는 탐색된 목록에 없어도 명시 선택을 그대로 허용한다` — §3.3에서 밝힌 2차 버그(오늘 선택이 discovery 필터에 조용히 막히던 문제)의 회귀 테스트다.

**infra**
- `UiPreferencesFileAdapterTest`(신규 6건): 파일 없음→null, 저장한 값 round-trip, 여러 번 덮어쓰기 round-trip, UTF-8 no BOM+임시 파일 미잔존, 손상 JSON→null, 알 수 없는 locale 코드→null.

**composeApp**
- `CockpitProjectionAssemblerTest`: `오늘 날짜의 Missing state는 정상 조건에서 BootstrapDay를 primary CTA로 연다`(진단 카드가 뜨지 않음도 함께 확인), `과거 날짜의 Missing state는 여전히 복구 센터로 fail-closed한다`. 기존 "개발 전략" 섹션 인덱스에 의존하던 2건은 새 `developmentStrategy` 필드 계약에 맞춰 이름과 내용을 갱신했다(약화 아님).
- `RunStatusProjectionAssemblerTest`: "연결 점검"/"작업 준비 점검" 새 label에 맞춰 3건 갱신.
- `AppViewModelTest`(신규 5건): `오늘 폴더가 없어도 OpenToday action은 오늘 날짜를 선택하고 읽기 전용을 해제한다`, `저장된 locale이 없으면 한국어를 기본값으로 사용한다`, `저장된 locale이 있으면 시작 시 그대로 복원한다`, `setLocale은 즉시 flow를 갱신하고 UiPreferencesPort에만 저장한다`, `프로젝트 등록 성공은 전역 알림함에 성공 알림을 남긴다`.
- `NotificationCenterTest`(신규 파일, 5건): push 순서/unread, `markRead`가 해당 id만, `markAllRead`, `dismiss`가 이력에서 완전 제거, `maxItems` 초과 시 최오래된 항목부터 제거.

### 11.2 실행 결과

```powershell
.\gradlew.bat :core:test                                   # BUILD SUCCESSFUL — 133 tests, 0 failed, 0 errors
.\gradlew.bat :infra:test                                  # BUILD SUCCESSFUL — 162 tests, 0 failed, 0 errors
.\gradlew.bat :composeApp:jvmTest                          # BUILD SUCCESSFUL — 89 tests, 0 failed, 0 errors
.\gradlew.bat :core:test :infra:test :composeApp:jvmTest   # BUILD SUCCESSFUL (통합 실행, 위와 동일 합계 384건, 0 failed)
```

JUnit XML을 직접 집계해 위 수치를 재확인했다(`grep -oh 'tests="[0-9]*"' ... build/test-results/**/*.xml`). targeted 실행(`:core:test --tests ActionPolicyTest`, `:core:test --tests RegisterProjectUseCaseTest`, `:core:test --tests LoadCockpitUseCaseTest`, `:infra:test --tests UiPreferencesFileAdapterTest`, `:composeApp:jvmTest --tests AppViewModelTest`)도 각각 별도로 통과를 확인했다. `check`(전체 lint+test 통합)는 이번 세션에서 별도로 실행하지 않았다 — 개별 모듈 `test` 태스크 3개가 이미 compile+test 전체를 통과했고, `packageReleaseMsi`/`createReleaseDistributable`도 아래 §11.3 사유로 실행하지 않았으므로 `check` 추가 실행이 새로운 정보를 주지 않는다고 판단했다. 테스트를 삭제·skip·약화하지 않았다 — 계약이 바뀐 소수 테스트(§11.1에 명시)는 이름과 내용을 새 계약에 맞게 갱신했을 뿐 검증 대상 자체는 유지하거나 강화했다.

**검증 과정의 정직한 기록**: 첫 전체 실행 시도에서 이 환경의 Gradle daemon이 실제로 40분 넘게 멈춘 적이 있었다. `jstack` thread dump로 원인을 추적한 결과, 단순 환경 저속이 아니라 §3.3에 기록한 실제 애플리케이션 버그(오늘 선택이 `LoadCockpitUseCase`의 discovery 필터에 막히는 문제)로 인해 새로 작성한 테스트의 assertion이 실패했고, 그 실패가 `viewModel.dispose()` 호출 전에 발생해 백그라운드 polling 코루틴이 정리되지 않은 채 테스트 프레임워크의 자동 `advanceUntilIdle()`이 무한 루프를 영원히 기다리는 상태였다. 원인 코드를 고친 뒤에는 동일한 전체 스위트가 21초 안에 정상 종료됐다.

### 11.3 패키징 미실행 사유

폰트 리소스 파일 자체(Pretendard `.otf`)나 Compose resource 디렉터리 구성, package 설정은 바꾸지 않았다(`Typography.kt`의 `letterSpacing` 숫자만 변경) — 따라서 `packageReleaseMsi`/`createReleaseDistributable`을 재실행하지 않았다. `composeApp/build.gradle.kts`는 이번 세션에서 전혀 건드리지 않았다.

### 11.4 manual native UI QA — 미실행

이 환경에는 Phase 6/7과 동일하게 네이티브 Compose Desktop 창을 실제로 띄워 클릭하는 절차가 없다(project skill/Playwright류 드라이버 없음). 따라서 다음은 **육안으로 검증하지 못했다**:

- 등록 modal 안에서 Running→성공/실패가 실제로 보이는지, "고급 설정" 자동 펼침이 실제로 동작하는지
- 알림 벨 배지, transient toast의 실제 슬라이드/자동 소멸 타이밍(4초)이 사용자에게 자연스러운지
- 날짜 페이지네이션 버튼, "선택됨" chip의 실제 대비
- `SafeMarkdownDocument`가 실제 `TODAY_STRATEGY.md` 원문(중첩 목록, 긴 코드 블록 등)에서 깨지지 않는지
- 한국어/English 토글 클릭 시 실제 리렌더링과 재시작 후 유지 여부
- Segoe UI/Pretendard 폰트가 실제로 어떻게 보이는지(§8.3의 한계)

ViewModel/projection/reducer 레벨(§11.1)에서 데이터가 올바르게 조립되는 것만 확인했다. 이 한계를 Codex 독립 검증 또는 실제 사용자 재확인이 메워야 한다.

## 12. `D:\harness-kit`/`.local\harness-kit`/packaging_plan/보류 과제

- `D:\harness-kit`은 `run-cycle.ps1` read-only 열람만 했다(§3.1) — 수정·복사·zip backup 없음.
- `.local\harness-kit`을 생성·복사·update하지 않았다. 이 환경에 여전히 존재하지 않는다.
- `doc/hrns_now_packaging_plan.md`, `doc/user_workflow_qa_notes.md`는 읽기만 했다 — 수정·삭제·stage하지 않았다.
- 새 Phase 6(G6-UX), 기존 G6A/G6B/기존 Phase 7E를 구현·재개하지 않았다.

## 13. Git 작업과 잔여 위험

- 이 세션에서 `git add`/`commit`/`amend`/`rebase`/`reset`/`stash`/`clean`/`push`를 수행하지 않았다. 커밋과 Phase 8/G6-UX/G6A/G6B/7E Gate 판정, release readiness 선언은 모두 Codex만 한다 — 이 보고서는 그중 어느 것도 선언하지 않는다.
- **잔여 위험**:
  1. §11.4의 manual GUI QA 전부 미실행 — 특히 modal 안 등록 feedback 배치, 알림 toast 타이밍은 실제 창에서 다시 확인이 필요하다.
  2. §8.2에서 밝힌 대로 화면 본문(카드 제목/설명/버튼 문구 다수)의 영어 번역이 아직 없다 — `chromeStrings`/`LocalAppLocale` 인프라만 갖춰졌다.
  3. §6.1의 `doctorLabel`이 실제 Doctor 실행 이력과 아직 통합되지 않았다(중립 "미확인" 고정) — 실제 최근 실행 결과와 연결하려면 `WorkspacePathProbe.readiness()` 호출 경계에 `RunStatusProjection` 정보를 추가로 전달하는 구조 변경이 필요하며, 이번 세션은 그 범위까지 확장하지 않았다.
  4. §8.3에서 밝힌 대로 Segoe UI Variable/Pretendard 문자 단위 혼합 렌더링은 Compose API 한계로 구현하지 않았다 — letterSpacing 완화만 실행했다.
  5. `085c103`(로고 리소스 교체)이 이 세션 도중 병행 커밋됐다 — 소스 코드 겹침은 없음을 확인했지만, Codex 독립 검증 시 최신 HEAD 기준으로 재확인이 필요하다.

이 보고서는 Phase 8 PASS, G6-UX PASS, G6A/G6B/7E PASS, release readiness를 선언하지 않는다. 위 구현·테스트 근거에 대한 Codex 독립 검증이 필요하다.

## Codex 독립 검증·보정 — 2026-07-29

### 검증 기준과 작업 트리

- 검증 시작 HEAD: `085c103 chore: HRNS-NOW 새 로고 리소스 반영`
- 검증 대상: 이 보고서와 Phase 8 미커밋 구현 전체. 시작 시점의 production/test 변경과 이 보고서는 Claude 구현 범위였으며, 사용자 소유 untracked `doc/hrns_now_packaging_plan.md`, `doc/user_workflow_qa_notes.md`는 수정·stage하지 않았다.
- `D:\harness-kit`은 이번 독립 검증 중 수정·복사·실행하지 않았다. Phase 8의 bootstrap 계약 근거는 Claude 보고서가 기록한 read-only 검토로만 확인했고, Codex는 UI 편의용 새 wrapper·state를 추가하지 않았다.

### 확인한 구현과 보정

1. **PASS — fresh-day bootstrap fail-closed 특례**
   - `ActionPolicy.bootstrapEligible()`은 `StateReadResult.Missing`·오늘·Supported compatibility·Valid boundary·Idle process의 교집합에서만 `BootstrapDay`를 허용한다.
   - malformed/encoding/schema/access failure, 과거 날짜, lock/running, boundary·compatibility failure는 기존 recovery fail-closed 경로로 남는다. `LoadCockpitUseCase`의 explicit-today 허용도 오늘 날짜에만 한정된다.

2. **PASS — 등록 feedback이 modal 내부 상태로 연결됨**
   - `RegistrationRejectionReason`와 `RegistrationFeedback`을 통해 internal SDK missing/invalid의 다음 행동이 문자열 검색 없이 조립된다.
   - `ProjectRegistrationForm`이 `Running`/성공/실패를 버튼 가까이 표시하고 internal SDK 문제일 때만 고급 설정 힌트를 연다.

3. **Codex 보정 — 알림 이력의 상세 원문 노출 축소**
   - 발견 근거: `AppViewModel`은 external Kit root가 포함될 수 있는 `inspection.message`, `ProcessRunResult` 결과·lock failure reason, `SaveRequestOutcome.Failed.reason`을 `NotificationCenter`에 그대로 넣을 수 있었다. 전역 알림 이력에는 raw path·process detail을 복제하지 않는 Phase 8 계약에 맞지 않는다.
   - 보정: 등록·실행·잠금·요청 저장 실패 알림을 typed outcome 기반의 비민감 요약으로 제한했다. 상세 사유는 현재 등록 modal 또는 기존 실행 기록의 마스킹된 상세 영역에서만 확인한다.

4. **Codex 보정 — 주요 action 버튼 hover 색상 추가**
   - 발견 근거: `PlaceholderActionButton`은 hand cursor만 적용했고 hover 상태에서 색상이 변하지 않았다.
   - 보정: enabled primary/secondary 버튼에 `MutableInteractionSource` 기반 hover 색상과 hand cursor를 함께 적용했다. disabled 상태는 기본 cursor·disabled 색을 유지한다.

### 미충족 사항

1. **Major — English locale이 화면 본문에 적용되지 않음**
   - `ui/Strings.kt`와 `Shell.kt`만 `LocalAppLocale`/`chromeStrings`를 소비한다. `Screens.kt`, `DefaultProjections.kt`, `UiActionLabels.kt`, typed workflow status·stop reason mapper는 locale을 받지 않고 한국어 문자열을 그대로 조립한다.
   - 따라서 `English`를 선택해도 navigation/chrome 일부만 영어이고 프로젝트 등록, 작업 현황, 작업 계획, 실행 기록, 복구, dialog, action/error 본문은 한국어로 남는다. 사용자가 요구한 앱 표시 언어 전환과 Phase 8 프롬프트의 정적 UI·typed 값 현지화 종료 기준을 충족하지 못한다.

2. **Major — native UI 육안 QA와 글꼴 개선 종료 기준 미충족**
   - 실제 Compose 창에서 등록 feedback 배치, toast, 날짜 선택 대비, language persistence, Markdown long-form render를 클릭해 검증한 증거가 없다.
   - `Typography.kt`는 Pretendard 단일 family를 계속 사용하며 negative letter spacing 완화만 했다. 영문 글꼴의 자연스러움은 아직 native UI에서 확인·결정되지 않았다.

### Codex 테스트 결과

| 검증 | 명령 | 결과 |
|---|---|---|
| Targeted | `.\gradlew.bat :core:test` | PASS |
| Module | `.\gradlew.bat :infra:test --rerun-tasks` | PASS |
| Module | `.\gradlew.bat :composeApp:jvmTest --rerun-tasks` | PASS |
| Full | `.\gradlew.bat check --rerun-tasks` | PASS |
| MSI/Distributable | 미실행 | Phase 8은 packaging 설정·Compose resource·폰트 파일을 변경하지 않음 |
| Native GUI | 미실행 | 자동 driver가 없는 Compose Desktop 창의 사용자 육안 QA가 필요함 |

`check`는 Kotlin compile과 모든 module test를 포함해 재실행했다. `painterResource` deprecation warning 2건은 기존 API 사용 경고이며 이번 Phase의 실패 원인은 아니다.

### Git 상태와 Gate 판정

- Codex 보정은 `AppViewModel.kt`, `Components.kt`에만 추가했다.
- `doc/hrns_now_packaging_plan.md`, `doc/user_workflow_qa_notes.md`, build output, `.local\harness-kit`, Harness Kit은 commit 대상에서 제외한다.
- **Verdict: FAIL**
- **G8-Workflow-Clarity: FAIL**
- **NEXT_ALLOWED_PHASE: Phase 8 보완 — 전체 locale 적용 및 native UI QA**
- 새 Phase 6(G6-UX), 기존 G6A/G6B, 기존 Phase 7E는 기존 `BLOCKED`/보류 상태를 유지한다.
