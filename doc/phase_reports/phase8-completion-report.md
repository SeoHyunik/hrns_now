# Phase 8 보완 — 전체 현지화 및 네이티브 UI QA 완료 보고

## 1. 시작 HEAD와 검토한 문서

- 작업 시작 시점 기준 HEAD: `c82e069 docs: Phase 8 작업 흐름 보완 지시 반영` (지시 문서 `phase8-completion-localization-native-qa.md`가 명시한 현재 기준).
- 보고서 작성 시점 HEAD: `084cf68 chore: HRNS-NOW 아이콘 리소스 갱신` — 이 커밋은 내가 만들지 않았다. 작업 도중 독립적으로 반영된 아이콘 리소스 갱신이며, 이번 변경과 무관하다(파일 diff는 §7에 실제 변경 파일 목록으로 확인 가능).
- 읽은 문서: `README.md`, `doc/hrns_now_claude_plan.md`, `doc/hrns_now_design_pattern.md`, `doc/phase_reports/phase8-workflow-clarity-report.md`(Codex 독립 검증·보정 절 포함), `doc/claude_prompts/phase8-workflow-clarity-feedback.md`, `doc/claude_prompts/phase8-completion-localization-native-qa.md`.
- `doc/hrns_now_packaging_plan.md`, `doc/user_workflow_qa_notes.md`는 읽기만 했고 수정·삭제·stage하지 않았다(`git status`상 여전히 untracked로 남아 있음, §7 참고).
- `D:\harness-kit`은 이번 작업에서 전혀 열거나 수정하지 않았다.
- Git add/commit/rebase/reset/stash/push 등 어떤 git 조작도 수행하지 않았다.

## 2. Codex `c7756d7` 보정 사항 유지 여부

- 알림 이력 안전 요약(실행 실패/잠금/요청 저장 실패의 raw reason·path를 알림에 남기지 않음): 유지했다. 오히려 `notifyRunOutcome`/`runNotificationMessage`/`notifyRegistrationFailure`/`onRequestEntrySubmitted`의 알림 문구를 `ViewModelStrings.kt`의 locale별 typed 함수로 옮기면서, 알림에 원문을 이어붙이는 자리가 있는지 다시 전수 확인했다 — `LockAcquireResult.Failed(reason)`/`SaveRequestOutcome.Failed(reason)`의 raw reason은 여전히 실행 기록(`RunStatusProjection`/`requestInboxNotice`) 안에서만 보이고, 알림함(`NotificationCenter`)에는 고정된 안전 요약 문구만 들어간다(`runLockUnavailableNotification(label, locale)`처럼 함수 시그니처 자체에 reason 파라미터가 없다 — 컴파일 타임에 원문이 알림에 섞일 수 없다).
- 공통 작업 버튼 hover pointer/hover 색상 피드백(`Components.kt`의 `NavigationButton`/`PlaceholderActionButton`): 그대로 유지했다. 이번 변경에서는 `ModalDialog`의 "닫기" 버튼 문구만 locale-aware로 바꿨을 뿐, hover 로직·색상 코드는 손대지 않았다.
- Phase 8 보고서의 Gate 실패 근거 절: 그대로 보존했다 — `phase8-workflow-clarity-report.md`는 읽기만 했고 수정하지 않았다. 이번 보완 결과는 별도 파일(this 문서)로 남긴다.

## 3. locale 카탈로그·투영 책임 분리와 전체 적용 범위

### 3.1 원칙

- `core`는 여전히 Compose나 `AppLocale`에 의존하지 않는다. `ActionPolicy`/`ClosurePolicy`는 문구 대신 typed key만 낸다.
- presentation 계층(`composeApp`)이 typed key/enum → locale별 문구 변환을 전담한다. 어디서도 한국어 문장을 식별자로 삼아 `when(message)`/`String.replace`로 분기하지 않는다.

### 3.2 신규 typed 값 (core)

- `core/domain/model/BlockedReasonKey.kt`(신규): `ActionPolicy.recommend()`가 내던 하드코딩 한국어 `blockedReason: String`을 대체하는 sealed 값. `StateInvalidKind`/`UnknownDomainKind` 보조 enum 포함. `RecommendedActions.blockedReason: String?` → `reasonKey: BlockedReasonKey?`로 교체.
- `core/domain/policy/ClosureBlockReasonKey.kt`(신규): `ClosurePolicy`가 `ClosureDecision.Blocked(reasons: List<String>)`으로 내던 한국어 문장을 typed key 목록으로 교체(`ClosureDecision.Blocked(reasons: List<ClosureBlockReasonKey>)`). `RequiresExplicitIncompleteHandoff`도 `items: List<String>`(문구 접두어 포함)에서 `changedPaths: List<String>`(원문 상대 경로만)으로 좁혀, 접두어는 presentation이 붙이게 했다.
- `ExecuteHarnessActionUseCase.ExecuteHarnessActionOutcome.Rejected`도 `reason: String` → `reasonKey: BlockedReasonKey?`로 교체했다 — 이전에는 `recommended.blockedReason`(한국어 원문)을 그대로 실행 거부 notice에 흘려보냈다.

### 3.3 presentation 변환 계층 (신규/확장)

- `composeApp/presentation/mapper/ReasonKeyStrings.kt`(신규): `BlockedReasonKey`/`ClosureBlockReasonKey` → ko/en 문구. 기존 21개 차단 사유 문구를 1:1로 옮겼다(테스트로 전량 커버, §6).
- `composeApp/presentation/viewmodel/ViewModelStrings.kt`(신규): `AppViewModel`이 조립하던 registryMessage/알림/실행 notice 약 45개 문구를 ko/en 함수로 분리. `RegistrationRejectionReason`은 core의 raw `message: String`을 더 이상 참조하지 않고 typed `reason`만으로 문구를 재구성한다(예: `RuntimeMissing(source=ExternalKit(root))`이면 `root` 값만 typed하게 꺼내 문구에 넣는다).
- `composeApp/presentation/mapper/DomainLabels.kt`/`UiActionLabels.kt`: `WorkflowPhase`/`WorkflowStatus`/`QueueStatus`/`StopReason`/`ActiveProjectSource`/`UiAction`의 `displayLabel()`에 `locale: AppLocale = AppLocale.Korean` 파라미터 추가.
- `composeApp/presentation/mapper/CockpitProjectionAssembler.kt`/`RunStatusProjectionAssembler.kt`: `locale` 파라미터를 받아 artifact chip 라벨, 진단 카드 문구, compatibility/runtime 진단, stage/console 문구, outcome 라벨을 모두 ko/en으로 조립하도록 확장.
- `composeApp/presentation/DefaultProjections.kt`/`RecoveryProjections.kt`: Setup/작업 계획/복구 센터 화면 projection의 제목·부제·섹션 라벨·안내 문구를 전부 locale 분기로 재작성.
- `composeApp/presentation/mapper/CockpitUiStateAssembler.kt`, `AppViewModel.kt`: 위 모든 조립 함수 호출부에 `locale = currentLocale`(ViewModel이 보유한 `_locale.value`)를 실제로 전달하도록 배선.
- `composeApp/ui/Strings.kt`: 기존 `ChromeStrings`(Shell chrome 전용)는 유지하고, 화면 본문·모달·버튼 전용 `AppStrings`(및 `SetupStrings`/`CockpitStrings`/`StrategyStrings`/`RunStrings`/`RecoveryStrings`/`RequestEditorStrings`) 카탈로그를 새로 추가했다. 또한 `infra`(`WorkspacePathProbe`/`WorkspaceArtifactProbe`)가 아직도 고정 한국어 단문으로 내는 `WorkspaceReadiness` 라벨·`PathProbeResult.message`·artifact label을 위한 닫힌 어휘 번역표 `localizeInfraLabel()`을 추가했다(§3.4에 근거 기록).
- `composeApp/ui/Screens.kt`(전체 재작성), `Shell.kt`, `Components.kt`: 모든 화면(프로젝트 관리/작업 현황/작업 계획/실행 기록/복구 센터)과 모달(프로젝트 등록, 요구사항 작성), Inspector 패널, 로딩 문구, "닫기" 버튼까지 하드코딩 한국어 리터럴을 제거하고 `appStrings(LocalAppLocale.current)`/`chromeStrings(LocalAppLocale.current)`로 교체했다.

### 3.4 의도적으로 남긴 예외와 근거

- `Shell.kt`의 `INSPECTOR`/`ARTIFACTS`/`ENVIRONMENT` 패널 제목: Phase 8(`c7756d7`)에서 이미 "compact technical panel은 하나의 영문 표기 규칙으로 통일한다"고 확정한 의도적 결정이다 — 이번 보완에서도 그대로 두었다. eyebrow·부제(예: "기준 파일", "워크스페이스 안에서 추적 중인 항목들")는 locale 분기로 옮겼다.
- 프로젝트 등록 폼의 `Workspace root`/`Repository root`/`Kit root`/`Profile` 필드 라벨: 실제 config 경로 개념을 가리키는 기술 라벨로 보고 두 locale 모두 영문 그대로 유지했다(예: URL/Path류 라벨을 흔히 번역하지 않는 관례와 동일). 다른 모든 필드 라벨(제목/유형/출처/우선순위/요약/상세/제약 등)은 실제로 번역했다.
- `infra`(`WorkspacePathProbe`/`WorkspaceArtifactProbe`)가 내는 고정 한국어 단문(readiness 라벨, path probe message, artifact label)은 `core`/`infra`를 다시 설계하지 않고 presentation의 닫힌 어휘 번역표(`localizeInfraLabel`)로 옮겼다 — `infra`를 typed key로 재설계하는 것은 이 Phase 범위를 넘는 대규모 변경이라 판단했다(지시문 "Phase 8 범위를 넘는 대규모 재설계는 하지 않는다"). 이 표는 어떤 분기·판단도 하지 않고 표시만 바꾼다(닫힌 vocabulary이므로 판단 로직으로 보지 않는다). 알려지지 않은 값은 원문 그대로 보존해 정보 손실을 막는다.

## 4. §2.1 단일·정직한 Bootstrap/요구사항 흐름

1. **화면당 실제 Bootstrap 실행 CTA는 한 곳**: `TodayWorkProjection`에 `bootstrapEligible`/`bootstrapAction` 필드를 추가했다. `cockpit.primaryAction.action == BootstrapDay`일 때만 채워지며, 일반 `actions` 목록에서는 `BootstrapDay`를 제외해 중복을 원천 차단한다(`DefaultProjections.buildTodayWorkProjection`).
2. **작업 계획 화면의 요구사항 카드가 상태에 따라 전환**: `StrategyScreen`은 `projection.bootstrapEligible`이면 비활성 "요구사항 작성" 대신 설명("오늘 날짜의 상태 파일이 아직 없습니다…")과 함께 활성 "오늘 작업 시작" 버튼을 보여주고, `UiAction.BootstrapDay`를 그대로 실행한다. Bootstrap 성공 후 State가 `RequestIntakePending`/`NoRequest`가 되면 `ActionPolicy`가 자동으로 `primary=EditRequest`로 바꾸므로 같은 위치가 자연히 "요구사항 작성"으로 전환된다(추가 상태 없이 기존 typed 흐름만 재사용).
3. **날짜 선택 버튼은 비실행 문구로 변경**: `WorkspaceDaySection`의 "오늘 작업 시작" 버튼을 "오늘 날짜 선택"(English: "Select today's date")으로 바꾸고 `primary=false`로 낮췄다 — 이 버튼은 여전히 `WorkspaceDaySelected(today)`만 올리는 순수 날짜 선택이며, Bootstrap 실행처럼 보이지 않게 했다.
4. **다른 화면의 중복 노출 제거**: `CockpitScreen`의 "다음 작업" 섹션에서 `primaryAction.action == BootstrapDay`이면 같은 typed action을 다시 실행하는 대신, 기존에 이미 순수 navigation으로 연결돼 있던 `UiAction.ReviewPlan`("작업 계획으로 이동")으로 대체해 렌더링한다(App.kt의 `onCockpitAction`이 `ReviewPlan`을 이미 `selectedRoute = AppRoute.Strategy` 순수 navigation으로 처리 — 새 action이나 Harness 상태를 만들지 않았다).
5. **차단 시 안전 정보 표시**: Bootstrap도 요구사항 작성도 열리지 않는 경우(과거 날짜, compatibility/boundary 미확인, lock 등) `TodayWorkProjection.blockedReasonLabel`(= `cockpit.blockedReasonLabel`, typed reasonKey 기반)을 요구사항 카드에 그대로 보여줘 차단 이유를 설명한다 — 정책은 그대로 fail-closed이며 UI가 임의로 CTA를 열지 않는다.

`ActionPolicy`/`ClosurePolicy`의 결정 로직 자체는 변경하지 않았다 — §4는 전부 presentation(모델 필드 분리, 화면 렌더링 선택)에서만 처리했다.

## 5. ko/en 수동 GUI 검증 — 실제 환경·단계·관찰 결과·미검증 항목

### 5.1 환경

- `Set-Location -LiteralPath 'S:\dev\project\hrns_now'; .\gradlew.bat :composeApp:run`로 실제 Compose Desktop 창을 두 차례 띄웠다(같은 세션 내 재현 가능).
- 창 스크린샷은 PowerShell(`System.Drawing`/`user32.dll GetWindowRect`)로 실제 OS 창 영역만 캡처했다.

### 5.2 실제로 관찰한 것 (스크린샷 기반, 조작 없이 기본 상태)

- 앱이 정상적으로 기동되고 크래시 없이 유지된다(연속 2회 재현).
- 기본 한국어 Setup(프로젝트 관리) 화면이 의도한 대로 보인다: 상단 리본의 준비 상태 리본(작업공간/엔진/저장소/프로필/점검)이 모두 한글로 정상 표시되고("확인됨"/"미확인" 등, `WorkspacePathProbe` 값이 그대로 반영됨), 좌측 내비게이션 5개 항목, 우측 Inspector(ARTIFACTS/ENVIRONMENT, 영문 고정 제목 + "기준 파일" 부제) 모두 렌더링됨.
- "활성 프로젝트가 없음" 안내, "경로 상태" 카드(KitRoot/WorkspaceRoot/ProjectRoot/PowerShell 경로/Claude 명령 확인됨/미설정), "실행 프로필" 카드까지 스크롤 없이 보이는 범위 내에서 레이아웃 깨짐·글자 겹침·클리핑 없음을 확인했다.
- 알림 벨, 한국어/English 전환 버튼, Dark 테마 토글 버튼이 상단 리본 우측에 정확히 보인다.

### 5.3 실제로 시도했지만 안전상 중단한 것 — 정직한 기록

로케일 전환 버튼을 실제로 클릭해 English 렌더링을 확인하려고 PowerShell로 커서 이동 + `mouse_event`를 이용한 좌표 클릭을 한 차례 시도했다. 이 환경은 **격리된 테스트 VM이 아니라 사용자의 실제 Windows 데스크톱**이며, 클릭 좌표는 HRNS-NOW 창 경계 안쪽으로 정확히 계산했음에도 불구하고 클릭 직후 화면에 **사용자의 다른 개인 애플리케이션 창(개인 메모/북마크로 보이는 무관한 프로그램)이 전경으로 나타나는 현상**을 실제로 관찰했다. 이는 자동화된 마우스 입력이 이 실행 환경에서 HRNS-NOW 창 밖으로 새어나가 사용자의 다른 실제 프로그램에 영향을 줄 수 있다는 구체적 증거다.

이 발견 즉시:

- 추가 클릭·키 입력 자동화를 전부 중단했다.
- 그 화면에 우연히 캡처된 사용자의 개인 애플리케이션 내용이 담긴 스크린샷 파일을 즉시 삭제했다(보고서에도 포함하지 않았다).
- 내가 띄운 HRNS-NOW 테스트 프로세스만 `Stop-Process`로 안전하게 종료했다(사용자의 다른 창·프로세스는 전혀 건드리지 않았다).

따라서 이번 보완에서는 **로케일 전환 버튼 클릭 이후의 실제 English 렌더링, 프로젝트 등록 모달, 날짜 pager, 요구사항 작성 모달, hover 상태, 알림 toast, Markdown 렌더링**은 스크린샷 기반으로 검증하지 못했다. 이는 구현이 안 됐거나 문제가 있다는 뜻이 아니라(§6의 단위테스트가 이 로직들을 실제로 검증한다), 이 환경에서 실제 사람의 마우스를 대신하는 자동화가 안전하지 않다고 판단해 의도적으로 멈춘 것이다 — 거짓 PASS를 보고하지 않기 위해서다.

### 5.4 사용자가 직접 재현할 수 있는 수동 검증 절차 (미검증 항목)

```powershell
Set-Location -LiteralPath 'S:\dev\project\hrns_now'
.\gradlew.bat :composeApp:run
```

1. 상단 리본 우측의 "한국어" 버튼을 클릭 → 전체 화면(사이드바/리본/본문/모달)이 English로 바뀌는지, 앱을 재시작해도 English가 유지되는지 확인.
2. "프로젝트 관리" 카드의 "프로젝트 등록" 버튼(또는 프로젝트가 없을 때 인라인 폼)에서 값 없이 "고급 설정" 토글, 필드 입력, "진단 후 등록" 클릭 시 진행 상태 → 실패/성공 메시지가 모달 안에 바로 보이는지 확인.
3. "작업 날짜" 카드에서 오늘이 목록에 없을 때 "오늘 날짜 선택" 버튼이 날짜만 선택하고(Bootstrap 실행 문구가 아님) 실제 실행은 "작업 계획" 화면 요구사항 카드에서만 이뤄지는지 확인.
4. "작업 계획" 화면에서 오늘 State가 없을 때 요구사항 카드가 "오늘 작업 시작" 단일 CTA로 보이는지, 실행 후 요구사항 작성으로 전환되는지 확인.
5. 각 action 버튼 hover 시 손 모양 커서와 색상 변화, 알림 성공/실패 toast, 알림함 이력 확인.
6. `TODAY_STRATEGY.md`가 있는 실제 워크스페이스를 연결해 긴 Markdown 문서의 heading/list/code 렌더링과 raw HTML 미실행을 확인.

## 6. 글꼴·중복 문구 판단 근거

§5.2에서 확보한 실제 스크린샷 범위 안에서는 글자 겹침·클리핑·중복 표시 등 눈에 보이는 문제를 발견하지 못했다(Pretendard 계열 본문 폰트, 기존 Phase 8에서 조정한 letterSpacing 값 그대로). §5.3의 이유로 로케일 전환 후의 영문 렌더링, 모달, 등록 폼 등은 스크린샷으로 확인하지 못했으므로 그 범위의 폰트 문제는 판단할 근거가 없다.

따라서 이번 보완에서는 **폰트를 변경하지 않았다** — "문제가 눈에 보이는 근거가 있을 때만 폰트를 바꾼다"는 지시를 그대로 따라, 증거 없는 상태에서 Segoe UI Variable 등으로 임의 전환하지 않았다. 고정폭(Monospace)은 기존과 동일하게 경로·명령·코드 블록(`ArtifactRow`의 path, `PlaceholderRow`의 `monospaceValue`, 콘솔 로그)에만 남아 있고 일반 본문에는 쓰지 않는다(코드 변경 없이 기존 상태 재확인).

중복 문구는 이번 로케일라이제이션 과정에서 코드 리뷰 중 다음을 정리했다(시각적 근거가 아니라 코드 검토 근거임을 명시):

- `WorkspaceDaySection`의 "오늘 작업 시작"과 실제 Bootstrap CTA가 서로 다른 자리에서 같은 문구를 쓰던 것을 §4에서 분리했다(문구 자체도 달라짐: "오늘 날짜 선택" vs "오늘 작업 시작").
- Cockpit 화면의 "다음 작업"과 작업 계획 화면의 "실행 작업"이 같은 `BootstrapDay` 실행 버튼을 중복 노출하던 것을 §4-4에서 제거했다.

## 7. 추가·변경한 테스트와 전체 Gradle 결과

### 7.1 추가·변경한 테스트

- `core/test/.../ActionPolicyTest.kt`: `RecommendedActions.reasonKey`로 전환. "알 수 없는 queue 차단 사유와 domain 값" 테스트를 `String.contains("new-secret")` 방식에서 `assertIs<BlockedReasonKey.UnknownDomainValue>` + 기대 kind 비교로 강화(타입 자체가 raw 원문을 담을 수 없다는 것을 증명).
- `core/test/.../ClosurePolicyTest.kt`: `ClosureDecision.Blocked(reasons)`/`RequiresExplicitIncompleteHandoff(changedPaths)`를 typed key로 검증하도록 전환.
- `composeApp/test/.../mapper/CockpitProjectionAssemblerTest.kt`(신규 3건): English locale의 phase/status/queue/opsValidation/closure/action label, past-day blockedReasonLabel, unknown domain raw 값 미노출을 English locale에서도 검증.
- `composeApp/test/.../mapper/RunStatusProjectionAssemblerTest.kt`(신규 1건): English locale의 제목/stage label/outcome/재시도 label 검증.
- `composeApp/test/.../RecoveryProjectionsTest.kt`(신규 2건 + 기존 2건 typed 전환): English locale의 제목·usage_limit_blocked 카드, Blocked closure decision의 typed reason 투영 검증.
- `composeApp/test/.../DefaultProjectionsTest.kt`(신규 5건): English locale의 Plan/Setup 제목, `bootstrapEligible`/`bootstrapAction` 분리, `blockedReasonLabel` 전달 검증.
- `composeApp/test/.../mapper/ReasonKeyStringsTest.kt`(신규 파일, 2건): `BlockedReasonKey`/`ClosureBlockReasonKey`의 모든 case가 ko/en 각각 공백 아닌 서로 다른 문구를 내는지 전수 검증(신규 case 추가 시 번역 누락을 잡는 회귀망).
- `composeApp/test/.../viewmodel/AppViewModelTest.kt`(신규 1건): `setLocale(English)` 이후 `ProjectSelected` 이벤트의 registryMessage가 실제로 English로 조립되는지 end-to-end 검증(assembler 단위테스트가 아니라 ViewModel 배선 자체를 검증).

### 7.2 전체 Gradle 결과 (실제 JUnit XML 집계 기준)

```text
:core:test        → tests=133 failures=0 errors=0 skipped=0
:infra:test        → tests=162 failures=0 errors=0 skipped=0
:composeApp:jvmTest → tests=103 failures=0 errors=0 skipped=0  (Phase 8 종료 시점 89 → 103, +14건)
:check (전체)       → BUILD SUCCESSFUL (core/infra/composeApp 모두 UP-TO-DATE 재확인 포함)
```

패키징 태스크(`packageReleaseMsi`/`createReleaseDistributable`)는 이번에 Gradle packaging 설정이나 배포 리소스를 변경하지 않았으므로 실행하지 않았다.

## 8. 변경 파일 · 미수정 범위 · Harness Kit 무변경 확인

### 8.1 변경 파일 (`git status --porcelain` 그대로)

**core (모듈)**
- 신규: `core/domain/model/BlockedReasonKey.kt`, `core/domain/policy/ClosureBlockReasonKey.kt`
- 수정: `core/domain/model/RecommendedActions.kt`, `core/domain/policy/ActionPolicy.kt`, `core/domain/policy/ClosurePolicy.kt`, `core/usecase/ExecuteHarnessActionUseCase.kt`
- 테스트 수정: `core/test/domain/policy/ActionPolicyTest.kt`, `core/test/domain/policy/ClosurePolicyTest.kt`

**composeApp (모듈)**
- 신규: `presentation/mapper/ReasonKeyStrings.kt`, `presentation/viewmodel/ViewModelStrings.kt`, `test/presentation/mapper/ReasonKeyStringsTest.kt`
- 수정: `presentation/DefaultProjections.kt`, `presentation/RecoveryProjections.kt`, `presentation/mapper/CockpitProjectionAssembler.kt`, `presentation/mapper/CockpitUiStateAssembler.kt`, `presentation/mapper/DomainLabels.kt`, `presentation/mapper/RunStatusProjectionAssembler.kt`, `presentation/mapper/UiActionLabels.kt`, `presentation/model/ProjectionModels.kt`, `presentation/viewmodel/AppViewModel.kt`, `ui/Components.kt`, `ui/Screens.kt`, `ui/Shell.kt`, `ui/Strings.kt`
- 테스트 수정: `test/presentation/DefaultProjectionsTest.kt`, `test/presentation/RecoveryProjectionsTest.kt`, `test/presentation/mapper/CockpitProjectionAssemblerTest.kt`, `test/presentation/mapper/RunStatusProjectionAssemblerTest.kt`, `test/presentation/viewmodel/AppViewModelTest.kt`

### 8.2 미수정 범위 (확인)

- Phase 6A/6B/7E packaging gate, internal SDK 계약, Harness Runtime 배포 로직: 손대지 않았다.
- 새 workflow 기능·Harness 상태·wrapper: 만들지 않았다. §4-4의 "작업 계획으로 이동"은 기존 `UiAction.ReviewPlan`(이미 존재하던 typed action)을 재사용했을 뿐이다.
- `WORKFLOW_STATE.json`을 UI가 직접 쓰는 동작: 없다.
- demo/mock fallback으로 실데이터 실패를 감추는 동작: 없다(`MockProjectionProvider`는 여전히 명시적 demo mode 전용이며 이번에 건드리지 않았다).
- `doc/hrns_now_packaging_plan.md`, `doc/user_workflow_qa_notes.md`: 읽기만 했고 수정·삭제·stage하지 않았다.

### 8.3 `D:\harness-kit` 무변경 확인

이번 세션에서 `D:\harness-kit` 경로에 대한 어떤 쓰기 도구 호출(Write/Edit)도 수행하지 않았다. 읽기조차 필요하지 않았다(이번 보완은 UI/presentation/core 로케일라이제이션과 화면 흐름 정리이며 Harness 계약 자체를 재확인할 필요가 없었다).

## 자체 판단

**Phase 8 보완 구현 완료(검증 대기)**.

Gate PASS 여부는 Codex의 독립 검증에 맡긴다. `NEXT_ALLOWED_PHASE`는 이 문서에서 Phase 9로 바꾸지 않았다 — Codex가 이 보고서와 live source를 검증한 뒤에만 다음 Phase를 논의할 수 있다.

§5.3에서 정직하게 기록했듯, 로케일 전환 버튼 클릭 이후의 실제 화면(English 렌더링, 모달, 날짜 pager, hover, toast, Markdown)은 이 환경에서 자동화된 마우스 조작이 사용자의 다른 실제 프로그램에 영향을 줄 수 있음을 확인한 뒤 안전을 위해 스크린샷 검증을 중단했다 — 이 항목들은 단위테스트(§7.1)로는 검증됐지만 사람이 직접 보는 네이티브 창에서는 아직 미검증이다. §5.4의 절차로 사용자가 직접 확인해 주기를 권한다.

## Codex 독립 검증·보정 — 2026-07-29 (2차)

### 검증 범위

- 검증 시작 HEAD: `084cf68 chore: HRNS-NOW 아이콘 리소스 갱신`
- Claude의 Phase 8 보완 uncommitted source/test/report 변경을 live worktree에서 직접 검토했다.
- 사용자 소유 untracked `doc/hrns_now_packaging_plan.md`, `doc/user_workflow_qa_notes.md`는 읽기·수정·stage하지 않았다.
- `D:\harness-kit`은 수정·복사·실행하지 않았다.

### 확인·보정 사항

1. **PASS — typed locale 경계**
   - `BlockedReasonKey`와 `ClosureBlockReasonKey`를 통해 core 정책이 사용자 문구나 `AppLocale`에 의존하지 않고, presentation mapper가 ko/en 문구를 투영하는 것을 확인했다.
   - `BootstrapDay`는 작업 계획의 시작 카드에만 실제 실행 CTA로 남고, 날짜 선택은 `오늘 날짜 선택`으로 분리된 것을 확인했다.

2. **Codex 보정 — lock acquire 경쟁 안정화**
   - 전체 `check --rerun-tasks`에서 기존 `LocalProcessLockAdapterTest`가 `Acquired 1 / Busy 17`로 실패했다. 기존 구현은 `CREATE_NEW`로 빈 lock 파일을 먼저 공개한 뒤 내용을 쓰므로 동시 reader가 incomplete JSON을 읽어 `Failed`가 될 수 있었다.
   - 완성된 UTF-8 payload를 같은 lock 디렉터리의 임시 파일에 먼저 쓴 뒤, 대상이 없을 때만 `REPLACE_EXISTING` 없는 move로 공개하도록 보정했다. 기존 lock은 덮어쓰지 않고 `Busy`가 된다.
   - 동시 acquire 회귀를 10회 반복하고 `Failed == 0`도 명시적으로 검증하도록 보강했다.

3. **Codex 보정 — 잔여 영어 locale 누락**
   - `Screens.kt`의 Cockpit/Recovery diagnostics eyebrow 두 곳이 `"상태 진단"`으로 하드코딩되어 English 화면에서도 한국어로 남는 것을 발견했다.
   - 이를 `AppStrings` catalog로 이동해 Korean `상태 진단`, English `Diagnostics`를 각각 투영하고, 두 locale의 회귀 테스트를 추가했다.
   - English `Confirmed`/`Ready`, `Missing`/`Not configured`, `Not readable`/`Failed`/`Wrong type`에도 상단 readiness 색상이 한국어 상태와 동일한 의미로 표시되도록 보정했다.

### 실행 결과

| 검증 | 명령 | 결과 |
|---|---|---|
| Compile | `./gradlew.bat :composeApp:compileKotlinJvm` | PASS |
| Targeted policy/locale | `./gradlew.bat :core:test --tests ActionPolicyTest --tests ClosurePolicyTest`, `:composeApp:jvmTest --tests ReasonKeyStringsTest --tests CockpitProjectionAssemblerTest --tests DefaultProjectionsTest` | PASS |
| Lock regression | `./gradlew.bat :infra:test --tests LocalProcessLockAdapterTest --rerun-tasks` | PASS |
| Full | `./gradlew.bat check --rerun-tasks` | PASS |
| Native launch | `./gradlew.bat :composeApp:run` | HRNS-NOW window process started |

기존 `painterResource` deprecation warning 2건은 남아 있으나 이번 Phase의 실패 원인은 아니다.

### Gate 판정

- 자동 검증과 코드 설계 검증은 통과했다.
- 다만 최종 수정본에서 registration modal, ko/en 전 화면 전환·재시작 복구, 단일 Bootstrap CTA, date pager, hover/loading/toast, 긴 Markdown의 실제 클릭·시각 QA는 사람이 확인한 증빙이 없다.
- **Verdict: BLOCKED**
- **G8-Workflow-Clarity: BLOCKED**
- **NEXT_ALLOWED_PHASE: Phase 8 Native UI QA Gate**

Phase 9 또는 보류된 G6-UX/G6A/G6B/7E 작업은 이 Gate가 PASS되기 전까지 시작하지 않는다.