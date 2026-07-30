# Phase 9 — 데스크톱 레이아웃 안정화와 신규 프로젝트 온보딩 완료 보고

## 1. 시작 HEAD, 변경 파일, 사용자 소유 untracked 파일 보존 여부

- 작업 시작 시점 기준 HEAD: `f6d1ced docs: Phase 9 QA 개선 작업 지시 추가` (지시 문서 `phase9-desktop-layout-and-onboarding.md`가 명시한 현재 기준과 일치).
- 작업 종료 시점까지 HEAD는 그대로 `f6d1ced`다. `git add`/`commit`/`amend`/`rebase`/`reset`/`stash`/`clean`/`push` 등 어떤 git 조작도 수행하지 않았다(§11에 상세 기록).
- 변경 파일(`git status --porcelain` 그대로):

```text
[수정]
composeApp/src/jvmMain/kotlin/io/hrns_now/app/App.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/main.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/model/HrnsUiEvent.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/model/RegistrationFeedback.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/viewmodel/AppViewModel.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/viewmodel/ViewModelStrings.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/ui/Screens.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/ui/Shell.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/ui/Strings.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/ui/Theme.kt
composeApp/src/jvmTest/kotlin/io/hrns_now/app/presentation/viewmodel/AppViewModelTest.kt
core/src/main/kotlin/io/hrns_now/core/port/ProjectRegistryPort.kt
core/src/test/kotlin/io/hrns_now/core/usecase/ProjectSelectionUseCaseTest.kt
core/src/test/kotlin/io/hrns_now/core/usecase/RegisterProjectUseCaseTest.kt
core/src/test/kotlin/io/hrns_now/core/usecase/ResolveActiveProjectUseCaseTest.kt
infra/src/main/kotlin/io/hrns_now/infra/registry/JsonProjectRegistryAdapter.kt
infra/src/test/kotlin/io/hrns_now/infra/registry/JsonProjectRegistryAdapterTest.kt

[신규]
composeApp/src/jvmMain/kotlin/io/hrns_now/app/ui/WindowConstraints.kt
composeApp/src/jvmTest/kotlin/io/hrns_now/app/ui/WindowConstraintsTest.kt
composeApp/src/jvmTest/kotlin/io/hrns_now/app/ui/ThemeTest.kt
core/src/main/kotlin/io/hrns_now/core/usecase/ClearActiveProjectUseCase.kt
```

- 사용자 소유 untracked 파일 `doc/QA_captures/`, `doc/hrns_now_packaging_plan.md`, `doc/user_workflow_qa_notes.md`는 이번 세션에서 읽기만 했고(QA_captures의 세 캡처는 §2 근거로 열람) 전혀 수정·삭제·stage하지 않았다 — 작업 종료 시점에도 여전히 untracked 상태로 남아 있다(`git status --short` 확인).
- `D:\harness-kit`은 §7의 계약 재확인을 위해 `scripts/run-cycle.ps1`만 읽었다. 어떤 쓰기 도구도 그 경로에 대해 호출하지 않았다.
- Phase 6A/6B/7E packaging·Runtime 배포, MSI 설정, 기존 core/infra 계약(ActionPolicy 판단 로직, HarnessCommand 매핑, ClosurePolicy)은 이번 Phase에서 변경하지 않았다 — 모두 읽기만 했다.

## 2. QA01~03을 각 캡처와 연결한 구현 결과

### QA01 (`QA01.png`, 약 729px 폭에서 상단 리본·사이드바·본문이 겹치거나 잘림)

- 근본 원인은 native 창에 최소 크기 제약이 전혀 없어 OS가 임의로 작게 줄일 수 있었다는 점이다. `composeApp/src/jvmMain/kotlin/io/hrns_now/app/ui/WindowConstraints.kt`(신규)에 `defaultSize = 1440x900dp`, `minimumSize = 1280x800dp`를 명명된 값으로 두고, `main.kt`가 실제 AWT `Window.minimumSize`(`ComposeWindow`가 상속하는 `java.awt.Frame`/`Window`의 진짜 API)에 이를 적용한다.
- 내부 Composable에 `requiredSize`를 강제하는 방식은 쓰지 않았다 — 창 자체가 줄어들지 않게 하는 native 제약만 사용했다.
- §3에 실제 창에서 확인한 증거를 기록한다.

### QA02 (`QA02.png`, 상단 정보 인지성·색상)

1. `BrandMark`는 그대로 `icon.png`(고해상도 원본)을 쓰며, 표시 크기만 76dp → **84dp**로 키웠다(`Shell.kt`).
2. 1440x900 기준 `HRNS-NOW` 제목을 20sp → **22sp**, 활성 프로젝트 이름을 15sp → **16sp**로 키웠다. 1280dp 최소 폭에서 겹치지 않도록 브랜드 블록의 `widthIn(min = 280.dp)` → `260.dp`, 상단 리본 `Arrangement.spacedBy` 16dp → 12dp, 준비 상태 리본의 구분선 padding 10dp → 8dp로 여백을 조정했다(글자를 다시 줄이지 않았다) — §3의 1280x800 실측 스크린샷으로 겹침·클리핑 없음을 확인했다.
3. 알림/언어/테마 버튼(`NotificationBell`/`LocaleToggle`/`ThemeToggle`)은 `Modifier.heightIn(min = 44.dp)`로 최소 44dp 클릭 영역을 보장하고, label 문구를 12sp → 14sp로 키웠다. hover pointer·hover 색상은 기존 Phase 6 로직(`Components.kt`)을 그대로 유지했다.
4. 활성 프로젝트가 없을 때 `Text(text = activeProjectName ?: "NONE")`로 locale과 무관한 literal `"NONE"`을 보여준다(§4에 상세). 색상은 새로 하드코딩하지 않고 기존 `colors.chelseaBlue`(icon.png 말 실루엣과 같은 계열로 이미 정의돼 있던 토큰)를 그대로 재사용한다.
5. 상단 리본 muted 텍스트는 새 전용 토큰 `HrnsColors.ribbonMutedText`로 분리했다(§4). 기존 `Color(0xFFFFE9A6)`(과도한 채도)를 dark `0xFFD9D7C7`, light `0xFF6B5A3C`로 교체했다 — 전역 `tertiaryText`는 건드리지 않았다.
6. 준비 상태 dot·label·value 3단 표현은 기존 구조(`ReadinessRibbon`)를 그대로 유지했다 — 색상만으로 확인됨/미확인을 전달하지 않는다.

Composable에는 여전히 상태 문자열 비교(`readinessDotColor`)가 남아 있으나 이는 Phase 8 이전부터 있던 기존 구조이며, 이번 Phase가 새로 문자열 분기를 추가하지는 않았다.

### QA03 (`QA03.png`, 활성 프로젝트 관리·신규 workspace 준비, Critical)

§5·§6에 상세 기록한다. 요약:

- **A. 활성 프로젝트 UI**: 활성 프로젝트가 있으면 등록 버튼이 `새 프로젝트 등록`으로 바뀌고(비활성화·숨김 없음), `활성 프로젝트` 요약 카드에 `프로젝트 해제` 버튼을 추가했다. 해제는 Registry의 마지막 활성 선택만 지운다.
- **B. 등록+오늘 workspace 준비 단일 흐름**: 등록 폼의 기본 primary 버튼이 `진단·등록 및 오늘 작업공간 준비`로 바뀌었고, 등록만 원하면 보조 버튼 `등록만 하기`를 쓴다. 경계 검증 → Doctor → compatibility → Registry 저장/선택 → context 재조회 → (ActionPolicy가 허용할 때만) 기존 `BootstrapDay` 실행 → State 재조회까지 기존 typed 파이프라인만 재사용했다.
- **C. 초기화 전후 Setup 화면**: "유효한 yyyy-MM-dd 날짜 폴더가 없습니다"라는 문구를 "아직 오늘 작업공간을 준비하지 않았습니다. 작업 계획 화면에서 오늘 작업을 시작하면 날짜 폴더가 만들어집니다"로 바꿔, 오류가 아니라 준비 전 상태임을 설명한다. 연결 점검/작업 준비 점검 버튼은 여전히 `ActionPolicy`가 허용하지 않으면 비활성 상태를 유지한다(강제 활성화하지 않음).

## 3. 실제 Compose 최소 창 크기 구현 방식과 1280 x 800 검증 증거

구현(`main.kt`, `WindowConstraints.kt`):

```kotlin
object WindowConstraints {
    val defaultSize = DpSize(1440.dp, 900.dp)
    val minimumSize = DpSize(1280.dp, 800.dp)
    fun minimumSizePx(density: Density): IntSize = with(density) {
        IntSize(minimumSize.width.roundToPx(), minimumSize.height.roundToPx())
    }
}

fun main() = application {
    val state = rememberWindowState(size = WindowConstraints.defaultSize)
    Window(onCloseRequest = ::exitApplication, state = state, icon = painterResource("hrns-now.ico"), title = "HRNS-NOW") {
        val density = LocalDensity.current
        LaunchedEffect(density) {
            val minimumPx = WindowConstraints.minimumSizePx(density)
            window.minimumSize = Dimension(minimumPx.width, minimumPx.height)
        }
        App()
    }
}
```

`Window`의 content 람다 receiver `FrameWindowScope.window`가 실제 `ComposeWindow`(= `java.awt.Frame`의 서브클래스)이며 `minimumSize`는 그 실제 AWT API다 — Compose Desktop 1.10.3 소스(`ui-desktop-1.10.3-sources.jar`)의 `WindowScope.kt`/`Window.desktop.kt`를 직접 열어 시그니처를 확인한 뒤 구현했다. 존재하지 않는 API를 가정하지 않았다.

**단위 테스트로 검증 가능한 부분** (`WindowConstraintsTest.kt`, 4건): `defaultSize == 1440x900dp`, `minimumSize == 1280x800dp`, 밀도 1.0/1.5에서 `minimumSizePx`가 정확히 비례 변환되는지. AWT `Window.minimumSize`가 실제로 native 창에 적용되는지 자체는 mock으로 성공한 것처럼 꾸미지 않았다 — 아래 실제 native 창 증거로만 확인했다.

**실제 native 창 증거** (`.\gradlew.bat :composeApp:run`으로 실행한 실제 프로세스, PowerShell `GetWindowRect`/`SetWindowPos`로 확인, 마우스 이동 없음):

```text
기동 직후 GetWindowRect 결과            : Width=1440 Height=900  (기본 크기 정확)
SetWindowPos로 900x700 강제 축소 시도 후 : Width=1280 Height=800  (OS가 최소 크기로 clamp)
```

즉 창을 명시적으로 900x700으로 줄이라고 요청했지만 실제 결과는 1280x800으로 강제됐다 — `minimumSize`가 Windows 창 관리자 수준(`WM_GETMINMAXINFO`)에서 실제로 적용된다는 직접 증거다. 1280x800으로 clamp된 상태의 스크린샷에서 상단 리본·사이드바·본문·Inspector 모두 겹치거나 잘리지 않았다(§10에 스크린샷 근거 기록).

## 4. dark/light 색상 token 및 `NONE`의 적용 위치

- `composeApp/src/jvmMain/kotlin/io/hrns_now/app/ui/Theme.kt`: `HrnsColors`에 `ribbonMutedText: Color` 필드를 추가했다. `hrnsColors(Dark)` → `0xFFD9D7C7`(낮은 채도 warm off-white), `hrnsColors(Light)` → `0xFF6B5A3C`(대비를 유지하는 muted warm dark). 전역 `tertiaryText`와는 분리된 값이다(`ThemeTest.kt`에서 서로 다른 값임을 확인).
- `Shell.kt`의 `TopRibbon`: 기존 로컬 `if (themeMode == Dark) Color(0xFFFFE9A6) else Color(0xFF765414)` 계산을 제거하고 `colors.ribbonMutedText`를 직접 사용한다. `프로젝트` 레이블 텍스트와 준비 상태 리본의 label 텍스트(`ReadinessRibbon`)가 이 토큰을 쓴다.
- `NONE`: `TopRibbon`에서 `activeProjectName ?: "NONE"`로 literal을 직접 렌더링한다(locale 무관, `ChromeStrings.notSelected`를 쓰지 않음). 색상은 `activeProjectName == null -> colors.chelseaBlue`로, 기존에 정의돼 있던(브랜드 icon과 같은 계열) `chelseaBlue` 토큰을 그대로 재사용했다 — 새 blue를 하드코딩하지 않았다.
- 검증: `ThemeTest.kt`(신규 4건)가 dark/light `ribbonMutedText`의 정확한 hex 값, `tertiaryText`와의 분리, `chelseaBlue`의 dark/light 고정값을 확인한다. `NONE` literal 자체의 실제 렌더링은 §10의 스크린샷(다음 프로젝트 없음 상태에서 "NONE"이 파란색으로 표시됨)으로 확인했다.

## 5. active 해제의 Registry 의미와 비삭제 보장

- `core/port/ProjectRegistryPort.kt`에 `suspend fun clearActive(): RegistrySaveResult`를 추가했다 — "마지막으로 활성화된 프로젝트" 표시만 지우며 project entry 목록은 절대 건드리지 않는다는 계약을 인터페이스 문서에 명시했다.
- `infra/registry/JsonProjectRegistryAdapter.kt`의 구현은 기존 `markActive`/`save`/`delete`와 동일한 `prepareMutationSnapshot()` → `writeEnvelope(projects, null)` 경로를 재사용한다 — 손상 entry 격리, atomic write, boundary 차단(`isRegistryInsideProject`) 규칙을 그대로 물려받는다(새 코드 20줄, 새 분기 없음).
- `core/usecase/ClearActiveProjectUseCase.kt`(신규): `registry.clearActive()`만 호출하는 얇은 use case.
- `AppViewModel.onActiveProjectReleaseRequested()`: 해제 성공 시 `hasResolvedActiveProject = false`로 되돌려 다음 `loadOnce`가 `ResolveActiveProjectUseCase`(Registry → 환경변수 fallback → 미선택)를 다시 타게 하고, `selectedDay`/`availableDates`/`preferredDate`/polling mtime/`harnessRunView` 등 화면 컨텍스트를 `onProjectDeletionRequested`와 동일하게 안전 초기화한다. workspace/repository/Harness Kit/State/daily 파일에는 어떤 파일 I/O도 수행하지 않는다.
- UI: `Screens.kt`의 `ActiveProjectSummaryCard`에 `프로젝트 해제` 버튼을 추가하고 `HrnsUiEvent.ActiveProjectReleaseRequested`를 올린다. `ProjectRow`의 `선택`/`삭제` 의미는 변경하지 않았다 — 삭제를 해제의 대체 수단으로 쓰지 않는다.
- 테스트:
  - `infra`: `clearActive는 project entry를 보존하고 lastActiveProjectId만 지운다`, `clearActive는 UTF-8 no BOM atomic write를 그대로 유지한다`, `부분 손상 Registry에서 clearActive는 손상 entry를 먼저 격리한 뒤 유효 entry만으로 반영된다`, `기존 등록 project root 아래에 놓인 Registry에서는 clearActive도 차단한다`(4건).
  - `core`: `활성 해제는 port의 clearActive만 호출하고 결과를 그대로 전달한다`, `활성 해제 실패 typed 결과를 호출자에게 그대로 전달한다`(2건).
  - `composeApp`: `프로젝트 해제는 활성 선택만 지우고 등록 목록은 보존한다`(해제 전후 `workspaceConfig.roots.workspaceRoot`가 프로젝트 경로 → 환경변수 fallback 경로로 바뀌고 `registryProjects`는 그대로 1개 남아 `isActive=false`가 됨을 확인), `활성 프로젝트가 없을 때 해제 요청은 아무 일도 하지 않는다`(2건).

## 6. registration-only와 registration+bootstrap의 상태 전이

- `HrnsUiEvent.ProjectRegistrationRequested(candidate, prepareWorkspace: Boolean = true)` — `prepareWorkspace`는 core `RegisterProjectCandidate`가 아니라 이벤트에만 둬서, "등록 뒤 workspace까지 준비할지"라는 순수 UI 흐름 결정을 core DTO에 섞지 않았다.
- `onProjectRegistrationRequested`의 기존 검증 순서(경계 검사 → Doctor → compatibility → Registry 저장·활성 선택)는 전혀 바꾸지 않았다. `Registered` + `Selected` 뒤에만 다음을 추가했다:
  1. `loadOnce(forceRead = true)` — ActionPolicy가 최신 재조회 결과로 다시 계산하게 한다.
  2. `prepareWorkspace == true`면 `registrationFeedback`을 `Success(name, InProgress)`로 먼저 갱신(등록 modal이 "작업공간 준비 중"을 계속 보여주게 함)한 뒤, `attemptWorkspacePreparationAfterRegistration()`을 호출한다.
  3. 이 함수는 `_state.value.todayWork.bootstrapEligible`(= `cockpit.primaryAction.action == BootstrapDay`, 기존 Phase 8 필드 재사용)이 true일 때만 기존 `runHarnessAction(BootstrapDay, Bootstrap)`을 실제로 실행한다 — 새 command/wrapper/lock lifecycle을 만들지 않고 `ExecuteHarnessActionUseCase`/`ProcessLockPort`/`HarnessRunnerPort`를 그대로 재사용한다.
  4. 실행 결과는 typed `WorkspacePreparationOutcome`(`NotAttempted`/`InProgress`/`Prepared`/`NotPrepared(reasonText)`, `composeApp/presentation/model/RegistrationFeedback.kt` 신규)으로 등록 결과와 분리해 저장한다. `Prepared`는 stdout이 아니라 **재조회한 `lastStateRead`가 `StateReadResult.Success`인지**로만 판정한다.
  5. `prepareWorkspace == false`(보조 버튼 `등록만 하기`)면 Bootstrap을 전혀 실행하지 않고 `NotAttempted`로 남는다.
- 상태 전이 요약:

```text
등록 성공 → 선택 성공 → context 재조회
    prepareWorkspace=false → registrationFeedback = Success(name, NotAttempted)          [Bootstrap 미실행]
    prepareWorkspace=true  → registrationFeedback = Success(name, InProgress)            [modal 유지]
                              → bootstrapEligible?
                                  아니오 → NotAttempted(이미 준비됨) 또는 NotPrepared(reasonText)(차단 사유 있음)
                                  예    → runHarnessAction(BootstrapDay) 실행
                                             → Completed & 재조회 State=Success → Prepared
                                             → Completed이지만 재조회 State≠Success → NotPrepared(재확인 안내)
                                             → Rejected/Failed/LockUnavailable/UnsupportedAction → NotPrepared(typed 안전 문구)
```

- `Screens.kt`의 등록 modal은 `RegistrationFeedback.Success`이면서 `workspacePreparation == InProgress`인 동안에는 자동으로 닫히지 않고(§4의 `LaunchedEffect` 조건 수정), 결과가 확정(`Prepared`/`NotPrepared`/`NotAttempted`)되면 닫힌다 — "등록은 완료됨"과 "workspace 준비 결과"를 같은 화면에서 순서대로, 그러나 분리된 사실로 보여준다.
- Bootstrap 실행 실패·차단은 어떤 경우에도 Registry 항목을 롤백하거나 UI가 daily 파일을 보정하는 이유가 되지 않는다(§8에 근거).
- Phase 8의 "화면당 실제 Bootstrap CTA는 한 곳" 원칙은 그대로 유지된다 — 등록 흐름 중에는 `harnessRunView.isRunning=true`가 전역으로 서기 때문에(§9의 버그 수정 참고) 사용자가 동시에 작업 계획 화면의 Bootstrap 버튼을 눌러도 `onHarnessRunRequested`의 최상단 가드가 즉시 막는다.

## 7. 실제 Harness bootstrap parameter·생성 파일·State 재조회 근거

`D:\harness-kit\scripts\run-cycle.ps1`을 읽기 전용으로 재확인했다(수정 없음):

- 파라미터 계약(1~42행): `-WorkspaceRoot`/`-ProjectRoot`(필수), `-KitRoot`(기본 `D:/harness-kit`), `-Profile`, `-Date`, `-SkipDoctor`, `-SkipOpsValidation`, `-ValidateForClosure`, `-RunPlanningWrapper`, `-RunReplanWrapper`, `-PlanningReason`/`-ReplanReason`(`ValidateSet`), `-RunExecutionWrapper`(`none|doc|code|auto`만 허용, `validation` 없음), `-UsePythonSidecars`, `-UseWorkflowStatePrimary`, `-ForceDualFileCompatibility`.
- 기존 `HarnessCommand.BootstrapDay`가 매핑하는 명령은 wrapper 스위치를 전혀 켜지 않고 `-UsePythonSidecars`만 추가한 "bare" 호출이다 — 이는 실계약과 정확히 일치한다.
- 3156~3161행에서 실제로 확인: `if ($ValidateForClosure -and ($RunPlanningWrapper -or $RunReplanWrapper -or $RunExecutionWrapper -ne "none")) { throw "Wrapper gate options cannot be combined with -ValidateForClosure..." }`, `if ($RunPlanningWrapper -and $RunReplanWrapper) { throw "...cannot be combined." }` — Bootstrap(모든 wrapper 스위치 off)은 이 금지 조합에 해당하지 않으므로 항상 안전하게 단독 실행된다.
- 3248~3251행 "Fresh-day bootstrap note" 주석과 3108행 `$initWorkspaceScript`/`init-workspace.ps1`(또는 `-UsePythonSidecars`일 때 `init_workspace.py`) 호출부를 확인했다 — 새 날짜 폴더에서 `WORKFLOW_STATE.json`은 이 init-workspace 단계 **이후**에 생성되며, `REQUEST_INBOX.md`/`TODAY_STRATEGY.md`/`DAILY_HANDOFF.md`/`WORKFLOW_STATE.json`은 모두 `dayRoot`(= `<workspaceRoot>/<date>/`) 아래에 생성된다 — 기존 `WorkspaceDay.dayRoot` 계산과 일치한다.
- 이 확인은 기존 `HarnessCommand.BootstrapDay`/`ActionPolicy.bootstrapEligible()`/`ExecuteHarnessActionUseCase`의 기존 구현이 이미 정확했음을 재확인했을 뿐이며, Phase 9는 이 경로를 새로 만들지 않고 그대로 재사용했다.
- 실제 Harness 실행(유료 모델 호출)은 이번 세션에서 수행하지 않았다 — 대신 §9의 fake `HarnessRunnerPort`/`WorkflowStatePort`로 "Bootstrap 완료 후 재조회 State가 Success로 바뀌는 경우"와 "완료됐지만 재조회 State가 여전히 Missing인 경우"를 모두 결정적으로 재현해 검증했다.

## 8. 실패/차단 시 fail-closed 동작과 사용자 안내

- 등록 자체의 실패(boundary/Doctor/compatibility/save)는 기존 Phase 8 경로 그대로 typed `RegistrationFeedback.Failure`로 표시되며 이번 Phase에서 바꾸지 않았다.
- Bootstrap 자동 시도가 막히는 경우는 typed `WorkspacePreparationOutcome.NotPrepared(reasonText)`로 표시하고 등록 성공 사실(`RegistrationFeedback.Success`)은 그대로 둔다 — Registry 롤백도, UI의 임의 파일 생성/보정도 하지 않는다:
  - `bootstrapEligible == false`이지만 typed 차단 사유가 있으면(`cockpit.blockedReasonLabel`, 과거 날짜/lock/boundary/compatibility 등 기존 `ActionPolicy` fail-closed 결과) 그 문구를 그대로 보여준다.
  - `ExecuteHarnessActionOutcome.Rejected` → 기존 `BlockedReasonKey.toDisplayText(locale)` 재사용.
  - `LockUnavailable` → 기존 `lockBusyNotice`/`lockFailedNotice` 재사용.
  - `Completed`이지만 재조회 State가 `Success`가 아니면(§9 근거: stdout이 아니라 State로 판단) 새 `workspacePreparationNotConfirmedNotice`로 "재확인 필요"를 안내한다.
  - `Failed`/`UnsupportedAction` → 각각 새 `workspacePreparationFailedNotice`/기존 `unsupportedActionNotice`.
- 모든 안내 문구는 `ViewModelStrings.kt`/`ReasonKeyStrings.kt`의 이미 typed·locale화된 함수에서만 나온다 — raw process 출력·경로 원문을 등록 modal이나 전역 알림(`notificationCenter`)에 노출하지 않는다(`AppViewModelTest`의 새 테스트가 `viewModel.notifications.value`에 워크스페이스 경로 문자열이 없음을 확인).
- 오늘이 아닌 날짜, lock, boundary/compatibility/Doctor 실패, malformed/unknown State에서는 여전히 fail-closed다 — 이번 Phase는 `ActionPolicy`/`ClosurePolicy`의 판단 로직 자체를 전혀 바꾸지 않았다(코드 diff에 두 파일이 없음으로 확인 가능).

## 9. 추가·변경한 테스트와 `check` 결과

### 9.1 세션 중 발견하고 고친 회귀(투명하게 기록)

`onHarnessRunRequested`를 `runHarnessAction`으로 추출하는 리팩터링 과정에서, 기존에 **launch 이전에 동기적으로 서던 `harnessRunView = HarnessRunViewState(..., isRunning = true, ...)` 설정을 실수로 launch 이후(코루틴 안)로 옮겨버렸다.** 이 때문에 연속 클릭 중복 방지 가드(`if (harnessRunView.isRunning) return`)가 코루틴이 실제 디스패치되기 전까지 무력화돼, 기존 테스트 `Doctor 실행 중 중복 클릭은 harnessRunner를 한 번만 호출한다`를 포함한 `composeApp:jvmTest` 전체가 응답 없이 CPU를 점유하며 멈추는 문제가 발생했다.

- 원인 규명 과정: `jstack`으로 실제 스레드 덤프를 떠서 `AppViewModel.loadOnce`가 폴링 루프에서 반복 실행되고 있음을 확인 → 같은 테스트를 **git HEAD 원본 코드로 완전히 되돌려** 재현했더니 재현되지 않음(2회 연속 성공) → `AppViewModel.kt`/`AppViewModelTest.kt`만 되돌린 조합으로 재현 성공 → 정확한 diff 대조로 위 원인을 특정했다.
- 수정: `onHarnessRunRequested`에서 `latestExecutionContext` null 확인과 `harnessRunView` 동기 설정을 `runHarnessAction` launch **이전**으로 복원했다. 수정 후 동일 테스트와 전체 스위트가 즉시(수십 초 내) 정상 통과함을 확인했다.
- 이 과정에서 `git stash`/`checkout`/`reset` 등은 전혀 쓰지 않았다 — `git show HEAD:<path>`(읽기 전용)로 얻은 원본 내용을 스크래치패드에 백업해 둔 내 변경본과 수동으로 맞바꿔가며 비교했고, 검증이 끝난 뒤 내 변경본을 전부 정확히 복원했다(§11).

### 9.2 추가·변경한 테스트

- **core**(`ProjectSelectionUseCaseTest.kt`): `활성 해제는 port의 clearActive만 호출하고 결과를 그대로 전달한다`, `활성 해제 실패 typed 결과를 호출자에게 그대로 전달한다`(신규 2건). `RegisterProjectUseCaseTest.kt`/`ResolveActiveProjectUseCaseTest.kt`는 새 `clearActive` 인터페이스 메서드를 fake에 추가(회귀 없음).
- **infra**(`JsonProjectRegistryAdapterTest.kt`): `clearActive는 project entry를 보존하고 lastActiveProjectId만 지운다`, `clearActive는 UTF-8 no BOM atomic write를 그대로 유지한다`, `부분 손상 Registry에서 clearActive는 손상 entry를 먼저 격리한 뒤 유효 entry만으로 반영된다`, `기존 등록 project root 아래에 놓인 Registry에서는 clearActive도 차단한다`(신규 4건).
- **composeApp**:
  - `WindowConstraintsTest.kt`(신규 파일, 4건): 기본/최소 dp 값, 밀도별 px 변환.
  - `ThemeTest.kt`(신규 파일, 4건): dark/light `ribbonMutedText` 정확한 값, `tertiaryText`와 분리, `chelseaBlue` 고정값.
  - `AppViewModelTest.kt`(신규 5건): `등록만 하기(prepareWorkspace false)는 오늘 workspace를 준비하지 않는다`, `진단 등록 및 오늘 작업공간 준비는 Bootstrap을 자동 실행하고 State가 Success가 되면 Prepared로 표시한다`, `Bootstrap 프로세스가 완료돼도 재조회한 State가 Success가 아니면 NotPrepared이고 등록은 유지된다`, `프로젝트 해제는 활성 선택만 지우고 등록 목록은 보존한다`, `활성 프로젝트가 없을 때 해제 요청은 아무 일도 하지 않는다`. 기존 `newViewModel` 헬퍼와 `FakeProjectRegistryPort`에 `clearActive` 배선을 추가했다(기존 테스트 동작 변경 없음).

### 9.3 전체 Gradle 결과(실제 JUnit XML 집계 기준, 수정 반영 후 최종 실행)

```text
:core:test        → tests=135 failures=0 errors=0 skipped=0   (Phase 8 종료 133 → 135)
:infra:test        → tests=166 failures=0 errors=0 skipped=0   (Phase 8 종료 162 → 166)
:composeApp:jvmTest → tests=117 failures=0 errors=0 skipped=0  (Phase 8 종료 103 → 117)
:check (전체)       → BUILD SUCCESSFUL
```

기존 테스트를 삭제·완화(skip)한 사례는 없다. 패키징 태스크(`packageReleaseMsi`/`createReleaseDistributable`)는 Gradle packaging 설정이나 배포 리소스를 변경하지 않았으므로 실행하지 않았다.

## 10. 수동 GUI QA 수행/미수행 항목, 제한 사항

Phase 8에서 겪은 안전 사고(이 실행 환경이 격리된 VM이 아니라 사용자의 실제 데스크톱이며, 합성 마우스 클릭이 다른 실제 애플리케이션 창으로 새어나간 사례)를 그대로 기억하고, 이번에는 **마우스·키보드 합성 입력을 전혀 사용하지 않았다.** 대신 직접 window handle을 대상으로 한 안전한 native API 호출(마우스 이동·클릭 없음)만 사용했다.

### 수행한 것 (실제 native 창, 실제 스크린샷 근거)

1. `.\gradlew.bat :composeApp:run`으로 실제 창을 띄우고 `GetWindowRect`로 크기를 확인했다 — 기동 직후 정확히 1440x900.
2. 기본 1440x900 상태를 스크린샷으로 확인했다: 로고(84dp)·제목(22sp)·"프로젝트 NONE"(레이블은 muted warm off-white, "NONE"은 파란색)·준비 상태 리본 5항목·우측 알림/한국어/Dark 버튼이 겹침·클리핑 없이 표시됨을 확인했다.
3. `SetWindowPos`(마우스 이동 없이 특정 window handle만 대상)로 900x700 축소를 시도했고, 실제 결과가 1280x800으로 clamp됨을 `GetWindowRect`로 확인했다.
4. clamp된 1280x800 상태도 스크린샷으로 확인했다 — 상단 리본·사이드바·본문·Inspector 모두 겹치거나 잘리지 않았다.
5. 창을 다시 1440x900으로 되돌리고 프로세스를 안전하게 종료했다(`Stop-Process`, 내가 띄운 프로세스만). 종료 후 Gradle/Kotlin daemon 외 다른 프로세스가 남지 않았음을 확인했다.

### 수행하지 않은 것 (정직한 제한)

- 로케일 전환, 다크/라이트 테마 전환, 프로젝트 등록 modal 입력·제출, `프로젝트 해제` 버튼 클릭, 작업 계획 화면의 Bootstrap 단일 카드 렌더링, 날짜 pager, 알림 toast/tray — 이 모든 상호작용은 마우스 클릭이나 키보드 입력을 필요로 하므로 이번에도 시도하지 않았다. 이 로직들은 §5·§6·§9에 기록한 대로 자동화된 ViewModel/adapter 테스트로 실제 동작을 검증했지만, 사람이 보는 실제 창에서의 클릭 기반 확인은 아직 없다.
- 이는 구현에 결함이 있다는 뜻이 아니라, 이 환경에서 사람 손을 대신하는 마우스/키보드 자동화가 사용자의 다른 프로그램에 영향을 줄 수 있다는 것이 Phase 8에서 실증됐기 때문에 계속 피하는 것이다 — 거짓 PASS를 보고하지 않기 위해서다.

### 사용자가 직접 재현할 수 있는 수동 검증 절차 (미검증 항목)

```powershell
Set-Location -LiteralPath 'S:\dev\project\hrns_now'
.\gradlew.bat :composeApp:run
```

1. 창을 1280x800보다 작게 드래그해 줄여 보고 실제로 더 작아지지 않는지 확인한다.
2. 등록된 프로젝트가 없는 상태에서 "진단·등록 및 오늘 작업공간 준비"로 새 프로젝트(안전한 임시 workspace/repository/`.local\harness-kit` 또는 외부 Kit)를 등록하고, 등록 modal 안에서 "작업공간을 준비하는 중입니다…" → "작업공간 준비됨"(또는 차단/실패 사유)으로 전환되는지 확인한다.
3. 활성 프로젝트 요약 카드의 "프로젝트 해제" 클릭 후 프로젝트 관리 modal에서 해당 프로젝트가 목록에 그대로 남아 있는지(단지 "활성" 표시만 사라지는지) 확인한다.
4. 로케일/테마 전환, 날짜 pager, 알림 toast의 hover·클릭 반응을 확인한다.

## 11. Git 작업을 하지 않았다는 사실

- 이번 세션에서 `git add`, `commit`, `amend`, `rebase`, `reset`, `stash`, `clean`, `push`를 전혀 수행하지 않았다.
- §9.1의 회귀 원인 규명 과정에서 원본(HEAD) 코드와 비교하기 위해 `git show HEAD:<path>`(읽기 전용, 실제 blob 내용을 표준출력으로만 조회) 명령만 사용했다 — 이는 작업 트리나 인덱스, 브랜치 상태를 전혀 바꾸지 않는다. 비교 대상 파일을 일시적으로 HEAD 내용으로 덮어썼을 때는 그 전에 반드시 내 변경본을 별도 스크래치패드 경로에 먼저 백업했고, 검증이 끝난 즉시 백업본으로 정확히 복원했다 — 복원 후 `git diff`로 의도한 변경분만 남아 있음을 재확인했다(§9.1, 위 §1의 최종 파일 목록과 §9.3의 전체 테스트 재통과로 교차 확인됨).
- `D:\harness-kit`에 대해서는 어떤 쓰기 도구 호출도 하지 않았다 — §7의 계약 확인은 읽기 전용이었다.
- 커밋은 Codex가 수행한다.

## 자체 판단

**PHASE_9_STATUS: BLOCKED**

QA01(최소 창 크기)·QA02(상단 리본 인지성·색상 토큰)·QA03(활성 프로젝트 관리·등록+오늘 workspace 준비 단일 흐름)을 모두 구현했고, `core`/`infra`/`composeApp` 전체 테스트(135/166/117, 실패 0)와 `check`가 통과한다. 세션 중 스스로 발견한 회귀(§9.1)는 근본 원인을 규명해 수정하고 재검증했다. 수동 native GUI QA는 안전한(마우스/키보드 합성 입력 없는) 범위에서 QA01을 직접 증명했고, QA02는 스크린샷으로 시각 확인했으며, 클릭이 필요한 QA03 상호작용 확인은 자동화 테스트로 대체하고 미검증 항목으로 정직하게 남겼다.

`NEXT_ALLOWED_PHASE: Phase 9 native interaction QA gate` — Gate PASS 선언과 커밋은 Codex의 몫이다.

## Codex 독립 검증·보정 — 2026-07-30

### 검증 기준

- 검증 시작 HEAD: `f6d1ced docs: Phase 9 QA 개선 작업 지시 추가`
- 검토: `doc/hrns_now_claude_plan.md`, `doc/hrns_now_design_pattern.md`, Phase 9 지시문, live 소스·diff, `D:\harness-kit\scripts\run-cycle.ps1`의 bootstrap 계약.
- `D:\harness-kit`은 읽기만 했다. 사용자 소유 `doc/QA_captures/`, `doc/hrns_now_packaging_plan.md`, `doc/user_workflow_qa_notes.md`는 수정·stage하지 않았다.

### 독립 판정과 보정

- QA01의 최소 창 크기는 Compose 화면의 강제 크기가 아니라 `main.kt`의 실제 AWT `window.minimumSize`에 연결되어 있다. `WindowConstraints`는 composeApp UI 계층에만 있어 core 의존 방향을 침범하지 않는다.
- QA02의 `NONE`, `chelseaBlue`, 상단 리본 전용 muted token과 44dp 제어 영역은 presentation/theme 책임으로 한정되어 있다.
- QA03의 active 해제는 `ProjectRegistryPort → ClearActiveProjectUseCase → JsonProjectRegistryAdapter` 방향이며, project entry 삭제·workspace 파일 쓰기 없이 atomic Registry write만 수행한다.
- Harness `run-cycle.ps1`을 재확인했다. `BootstrapDay`의 `-UsePythonSidecars` bare 호출은 실제 parameter 계약과 맞고, init 단계는 `<workspaceRoot>\<yyyy-MM-dd>` 아래 Harness 소유 daily surface를 만든다. UI가 daily 파일이나 `WORKFLOW_STATE.json`을 직접 쓰는 경로는 추가되지 않았다.
- **Codex 보정:** 초기 구현은 Bootstrap 프로세스 종료 뒤 `WORKFLOW_STATE.json` 재조회 성공만으로 `Prepared`를 표시했다. 이는 required daily 4-file이 실제로 누락돼도 준비 완료로 오인할 수 있었다. `AppViewModel`이 마지막 `WorkspaceArtifactSummary.isRequiredReady`도 함께 확인하도록 고쳤고, State 성공·required file 누락 시 `NotPrepared`가 되는 회귀 테스트를 추가했다. stdout으로 성공을 판정하지 않는다.

### 설계 평가

| 항목 | 판정 | 근거 |
|---|---|---|
| SRP/DIP | PASS | 창 제약은 composeApp, Registry 변경은 core port/use case·infra adapter, 화면은 typed event만 전송한다. |
| OCP/LSP/ISP | PASS | `clearActive()`는 기존 Registry save 결과 계약을 재사용하며 fake/real 구현 모두 typed 결과를 유지한다. |
| Command·Policy | PASS | 새 Bootstrap 경로는 기존 `ActionPolicy` 허용 결과와 `ExecuteHarnessActionUseCase`/lock/state reread를 재사용한다. |
| Fail-closed | PASS_WITH_FIX | required surface 확인 누락을 보정했다. malformed/lock/boundary/compatibility 차단 정책은 변경하지 않았다. |
| 과도한 추상화 | PASS | UI 흐름 플래그는 UI event에만 두고 신규 runtime/service 계층을 만들지 않았다. |

### 검증 결과

| 검증 | 명령 | 결과 |
|---|---|---|
| Targeted | `.\gradlew.bat :composeApp:jvmTest --rerun-tasks` | PASS |
| Module | `.\gradlew.bat :core:test :infra:test :composeApp:jvmTest --rerun-tasks` | PASS |
| Full | `.\gradlew.bat check --rerun-tasks` | PASS |
| XML 집계 | core 135 / infra 166 / composeApp 118, failures=0 | PASS |
| Harness 계약 | `D:\harness-kit\scripts\run-cycle.ps1` read-only inspection | PASS |
| 실제 클릭 QA | 등록 modal 제출·해제·완료 피드백 | 미수행 |

### Gate 판정

- 코드·자동 테스트·Harness 계약은 통과했으며 Codex 보정은 반영됐다.
- 다만 실제 native 창에서 `진단·등록 및 오늘 작업공간 준비`, `등록만 하기`, `프로젝트 해제`를 사용자가 클릭해 보았다는 증거는 없다. 자동화 테스트가 이 공백을 숨길 수 없으므로 Phase 9 전체 Gate는 아직 `BLOCKED`다.
- 다음 허용 작업은 새 기능 Phase가 아니라 **Phase 9 native interaction QA gate**다. 사용자 클릭 QA에서 결함이 나오면 그 결함만 별도 보정하고, 이상이 없으면 그때 다음 기능 Phase를 정의한다.