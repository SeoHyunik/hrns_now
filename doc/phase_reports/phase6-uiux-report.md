# Phase 6 작업 보고서 — UI/UX QA 개선 (새 Phase 6, G6-UX)

## 0. 범위 선언

이 보고서는 `doc/claude_prompts/phase6-uiux-qa-improvement.md`에 따른 **새 Phase 6(UI/UX QA 개선)** 작업만 다룬다. `doc/hrns_now_claude_plan.md` §0.5가 재정의한 대로, 기존 Phase 6A(외부 Kit MSI, G6A)·기존 Phase 6B(승인 Runtime 통합, G6B)·기존 Phase 7(실험 기능)은 이번 세션에서 구현·완료 처리·축소하지 않았다 — 셋 다 여전히 별도 사용자 승인과 Codex 독립 검증이 필요한 **보류 과제**다. G6A/G6B의 현재 상태(BLOCKED, `phase6-report.md`/`phase6b-report.md` 기준)도 바꾸지 않았다.

- `D:\harness-kit`은 이번 세션에서 전혀 참조·수정·복사하지 않았다 — 새 Phase 6은 순수하게 HRNS-NOW의 presentation 계층(화면·용어·feedback·modal) 작업이다.
- `doc/hrns_now_packaging_plan.md`는 읽지도 수정하지도 않았다. `git status`상 여전히 사용자 소유 untracked 파일이다.
- 이 세션 동안 `git add`/`commit`/`amend`/`rebase`/`reset`/`stash`/`clean`/`push`를 수행하지 않았다. 커밋은 Codex가 담당한다.

## 1. 변경 파일

```text
composeApp/build.gradle.kts
composeApp/src/jvmMain/kotlin/io/hrns_now/app/App.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/demo/MockProjectionProvider.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/DefaultProjections.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/RecoveryProjections.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/mapper/CockpitProjectionAssembler.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/mapper/RunStatusProjectionAssembler.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/mapper/UiActionLabels.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/model/CockpitProjection.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/model/ProjectionModels.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/viewmodel/AppViewModel.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/viewmodel/HarnessRunViewState.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/ui/Components.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/ui/Screens.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/ui/Shell.kt
composeApp/src/jvmTest/kotlin/io/hrns_now/app/presentation/DefaultProjectionsTest.kt
composeApp/src/jvmTest/kotlin/io/hrns_now/app/presentation/RecoveryProjectionsTest.kt
composeApp/src/jvmTest/kotlin/io/hrns_now/app/presentation/mapper/RunStatusProjectionAssemblerTest.kt
```

`core`/`infra` 모듈은 이번 phase에서 전혀 변경하지 않았다 — 화면 정보 구조·용어·feedback·modal은 presentation 계층만의 책임이라는 프롬프트 지시를 그대로 따랐다.

## 2. UI QA에서 확인된 혼란과 채택한 해결

기존 Setup 화면과 실행 화면을 실제 코드 기준으로 다시 읽으며 확인한 문제:

| 발견한 혼란 | 원인 | 채택한 개선 |
|---|---|---|
| 프로젝트가 이미 등록돼 있어도 등록 폼이 화면에 항상 노출됨 | `SetupScreen`이 `ProjectRegistrySection`을 통해 목록과 등록 폼을 같은 화면에 상시 렌더링 | 프로젝트가 1개 이상이면 폼을 숨기고 활성 프로젝트 요약 + `프로젝트 등록` CTA만 남긴다. 등록·전환·삭제는 `프로젝트 관리` modal로 이동. 프로젝트가 0개일 때만 기존처럼 폼을 그대로 노출(§3) |
| 어느 프로젝트를 보고 있는지 화면 어디에서도 상시 확인 불가 | 활성 프로젝트 이름은 Cockpit 제목에만 잠깐 붙어 있었고 다른 화면·상단에는 없었음 | 상단 리본(모든 라우트 공통)에 활성 프로젝트 이름 + stale 여부를 항상 표시(§4). Setup 화면에는 이름·프로필·Kit/Workspace/Repository root를 보여주는 `활성 프로젝트` 카드 추가 |
| `환경 점검`(구 "상태 점검 실행")을 눌러도 즉시 보이는 진행/성공/실패 표시가 없고, 결과를 보려면 별도 `실행 현황`(→ `실행 기록`) 화면으로 이동해야 했음 | 실행 상태(`RunStatusProjection`)가 오직 별도 라우트에만 노출됨 | Doctor/OpsValidation 실행 하나(harness는 한 번에 하나만 실행)에 대한 인라인 feedback 카드를 프로젝트 관리·작업 계획 화면의 실행 action 바로 아래에 추가(§5) |
| 영어/한글 용어 혼용(Doctor, Ops Validation, Strategy, Queue, DIAGNOSTICS 등)과 화면명 중복(`오늘 현황`/`실행 현황`처럼 헷갈리는 이름) | 초기 Phase에서 붙인 임시 라벨이 그대로 남음 | 프롬프트가 제시한 용어 매핑표를 전체 화면·버튼·empty/error state에 일관 반영(§6) |
| 요구사항 작성 폼이 화면 한 자리를 상시 차지하고, 저장 불가 사유·미저장 닫기 확인이 없었음 | 인라인 상시 폼 구조 | 상단 `요구사항 작성` CTA + modal editor로 전환, 저장 불가 사유 표시·미저장 변경 닫기 확인 추가(§7) |
| `역할별 진행 단계`가 실행 화면 기본 뷰에 항상 노출돼 저수준 정보가 우선순위를 가림 | 항상 펼쳐진 `SectionCard` | 기본은 접힌 상태이고 "자세히 보기"를 눌러야만 펼쳐지도록 변경(§6) |

## 3. 활성 프로젝트 흐름과 `프로젝트 관리` modal

- `composeApp/src/jvmMain/kotlin/io/hrns_now/app/ui/Screens.kt`의 `ProjectManagementSection`(구 `ProjectRegistrySection`)을 다시 작성했다.
  - 프로젝트가 1개 이상이면: 활성 프로젝트 이름 + `활성` 배지(또는 미선택 안내)와 `프로젝트 등록` primary 버튼만 보이고, 상시 등록 폼은 렌더링하지 않는다.
  - 버튼을 누르면 `ModalDialog`(신규, `Components.kt`)가 열리고 그 안에서 등록된 프로젝트 목록(`ProjectRow`: 선택/삭제)과 새 프로젝트 등록 폼(`ProjectRegistrationForm`, 기존 컴포저블 그대로 재사용)을 함께 보여준다.
  - 프로젝트가 0개면 modal을 거치지 않고 화면에 바로 등록 폼을 보인다 — "프로젝트가 전혀 없을 때만 등록 온보딩을 보인다"는 지시를 그대로 만족한다.
  - `RegisterProjectUseCase`/`SelectProjectUseCase`/`DeleteProjectUseCase`와 Registry→환경변수 fallback→사용자 선택 순서, `BoundaryPolicy`/`CompatibilityPolicy` 호출 경로는 전혀 건드리지 않았다 — `ProjectRegistrationForm`이 만드는 `HrnsUiEvent`는 이전과 동일하게 `AppViewModel.onEvent`로만 전달된다.
- `ModalDialog`(`Components.kt`)는 실제 OS 창을 새로 만들지 않고 같은 Compose 트리 안에서 scrim + 중앙 카드로 그리는 오버레이다. ESC, 바깥 클릭, 헤더의 `닫기` 버튼 모두 `onDismissRequest`로 수렴하며, 카드 내부 클릭은 전파되지 않아 실수로 닫히지 않는다. Composable은 여전히 file I/O·PowerShell 실행을 하지 않는다(§8).

## 4. 상단 리본의 활성 프로젝트 식별

- `Shell.kt`의 `TopRibbon`이 이제 `activeProjectName`/`activeProjectStale`을 받아 브랜드 영역 바로 아래에 "프로젝트 <이름>"을 상시 표시한다(미선택이면 "선택 안 됨", stale이면 경고색).
- `ReadyShell`이 `state.cockpit.projectName`/`isStale`을 그대로 전달한다 — 새 domain/core 코드 없이 이미 계산된 값을 화면에 옮기기만 했다.
- 이 표시는 5개 라우트(프로젝트 관리/작업 현황/작업 계획/실행 기록/복구 센터) 어디에서나 공통으로 보이므로, "사용자가 앱을 열었을 때 즉시 어느 프로젝트를 보고 있는지 안다"는 제품 목표 1을 화면 전환과 무관하게 만족한다.
- `SetupScreen`에는 별도로 `ActiveProjectSummaryCard`를 추가해 이름·상태 배지·Profile·Kit root·Workspace root·Repository root를 한 곳에서 보여준다.

## 5. `환경 점검` 실행 feedback

- `HarnessRunViewState`(ViewModel 내부 상태)에 `runCompletedAt: Instant?`을 추가하고, `AppViewModel`의 모든 실행 완료 지점(`onProjectRegistrationRequested`의 Doctor gate, `onHarnessRunRequested`의 Completed/Failed 분기)에서 `clock()`으로 채운다.
- `RunStatusProjection`(presentation model)에 `isRunning`, `lastCommandKind`, `lastOutcome`(`StatusChipModel`), `lastCompletedAtLabel`, `lastSummaryLine`을 추가했다. `RunStatusProjectionAssembler`가 이 값을 조립하며, 실패 시 요약은 계약의 첫 `Error` check message(없으면 첫 `Warn`)를 그대로 보여줘 "사람이 이해할 수 있는 원인"을 제공한다.
- 새 `HarnessRunFeedback` 컴포저블(`Screens.kt`, private)을 프로젝트 관리·작업 계획 화면의 `실행 작업` 섹션에 추가했다.
  - 실행 중: `InlineSpinner` + "환경 점검 진행 중입니다…" 같은 typed label 기반 문구.
  - 완료: 결과 배지(`정상`/`경고`/`실패`/`확인 필요` 등, tone 포함) + 완료 시각 + 짧은 요약 한 줄 + (Doctor/ValidateOps에 한해) `다시 점검`/`다시 검증` 버튼.
  - `cancelEnabled`일 때만 `실행 취소` 버튼을 노출한다 — "cancel을 기존 runner가 지원하는 동작에만 노출한다"는 지시를 그대로 따른다.
  - harness 실행은 한 번에 하나만 허용되므로(`MUTATING_RUN_ACTIONS` 공유 lock) feedback 카드도 하나만 있으면 충분하다 — Doctor/ValidateOps별로 상태를 중복 관리하지 않는다.
  - `HarnessCommandKind.toRetryAction()`/`retryLabel()`(신규, `RunStatusProjectionAssembler.kt`)은 Doctor→`UiAction.RunDoctor`("다시 점검"), ValidateOps→`UiAction.RunOpsValidation`("다시 검증")만 매핑하고 나머지는 `null`을 반환한다 — Bootstrap/Planning류는 각 화면의 action 버튼이 이미 재클릭 가능하므로 중복 CTA를 만들지 않는다.
- **중복 클릭 방지 버그 수정**: 기존 `refreshRunProjectionOnly()`는 `cockpit`/`todayWork`/`recovery`/`runStatus`만 다시 조립하고 `state.setup`은 갱신하지 않아, 실행 중에도 프로젝트 관리 화면의 `환경 점검`/`작업 기준 점검` 버튼이 활성 상태로 남아 있을 수 있었다(ViewModel의 `harnessRunView.isRunning` 가드가 실제 중복 실행은 막았지만, 버튼 자체는 비활성화되지 않았다). `setup`도 `updatedCockpit.allowedActions`로 다시 조립하도록 고쳐, 실행 중에는 프로젝트 관리 화면의 버튼도 즉시 비활성화된다.
- success가 버튼을 영구 비활성화하지 않는다는 요구는 기존 `ActionPolicy`/`withRunEnabled` 계약을 그대로 유지해서 만족한다 — 완료 후 `enabled`는 `!harnessRunInProgress` 기준으로 다시 열린다.

## 6. 정보 구조·한국어 용어 정리

프롬프트의 표를 화면 라벨·버튼·empty/error state·CTA 라벨 전체에 반영했다.

| 기존 | 새 표시명 | 반영 위치 |
|---|---|---|
| 작업공간 연결 | 프로젝트 관리 | 좌측 네비, Setup 화면 title, `ProjectManagementSection`/modal |
| 프로젝트 Registry | 프로젝트 관리 | `ProjectManagementSection` SectionCard title |
| 오늘 현황 | 작업 현황 | 좌측 네비, Cockpit 화면 title |
| 다음 행동 | 다음 작업 | Cockpit "다음 작업" 섹션, 진단 카드 행, 저장 실패 안내 문구 |
| DIAGNOSTICS | 상태 진단 | Cockpit/Recovery 진단 카드 eyebrow |
| 발생한 일 | 최근 작업 기록 | Cockpit 진단 카드, Recovery 카드, `RecoveryProjections`/`ProjectionModels`/`CockpitProjection` 문서 |
| 이전 정상 기록 | 마지막 정상 상태 | Cockpit 진단 카드 행 |
| 오늘 할 일 | 작업 계획 | 좌측 네비, Strategy 화면 title/subtitle, 관련 파일 라벨("작업 계획 파일") |
| Strategy | 개발 전략 | 작업 계획 화면 섹션 카드 |
| Queue | 작업 대기열 | 작업 계획 화면 섹션 카드 |
| 요청 작성 | 요구사항 작성 | 상단 CTA, modal title, `UiAction.EditRequest` 표시 라벨 |
| 실행 현황 | 실행 기록 | 좌측 네비, Run 화면 title, `RunStatusProjection.title`/subtitle |
| Doctor | 환경 점검 | `UiAction.RunDoctor`/`HarnessCommandKind.Doctor` 표시 라벨, stage chip, lock 소유자 표시 |
| Ops Validation | 작업 기준 점검 | `UiAction.RunOpsValidation`/`HarnessCommandKind.ValidateOps` 표시 라벨, stage chip |
| 오늘 준비 | 작업 준비 | `UiAction.BootstrapDay`/`HarnessCommandKind.Bootstrap` 표시 라벨, 안내 문구 |

- `작업 현황`과 `실행 기록`이 같은 이름으로 중복되지 않도록 확인했다(기존에 `실행 현황`이 `오늘 현황`과 혼동될 수 있었던 지점을 해소).
- `역할별 진행 단계`(실행 기록 화면)는 기본 접힘으로 바꿨다 — `stagesExpanded` 로컬 state로 "자세히 보기"를 눌러야만 펼쳐진다. 완전히 제거하지 않은 이유는 lock/실행 이력 디버깅에 필요한 저수준 정보라 "상세/접힘 영역으로만 제공"하라는 지시를 그대로 따른 것이다.
- raw session ID/secret/token/raw log/internal path는 이번에도 어떤 화면에도 추가하지 않았다 — 새로 노출한 값은 모두 기존에 이미 typed/label 처리된 값(`CockpitProjection`, `RunStatusProjection`, `WorkspaceConfig.roots`의 경로 문자열)뿐이다.
- `UiActionLabels.kt`/`RunStatusProjectionAssembler.kt`의 `displayLabel()`이 유일한 label 생성 지점이라는 기존 계약(§5.3 "action label은 action ID가 아니다")은 그대로 유지했다 — 새 용어도 이 함수들 안에서만 바꿨다.

## 7. `요구사항 작성` modal

- `StrategyScreen`의 상시 `RequestEntryForm`을 제거하고, 상단에 `요구사항 작성` CTA(비활성 시 사유 문구 포함)만 남긴 뒤 새 `RequestEntryModal`(`Screens.kt`, private)로 옮겼다.
- `RequestEntryModal`은 `ModalDialog` 위에서 렌더링되며 다음을 구현한다.
  - 저장 버튼 비활성 사유를 typed 조건으로 계산해 그대로 문구로 보여준다: 저장 중 / 날짜·상태상 편집 불가 / 제목·요약 누락.
  - 미저장 변경(`title`/`summary`/`detail`/`constraints` 중 하나라도 비어있지 않음) 상태에서 ESC·바깥 클릭·헤더 `닫기`로 닫으려 하면 `attemptClose()`가 가로채 "저장하지 않은 변경사항이 있습니다. 닫으시겠습니까?" 확인 화면을 modal 안에 그대로 보여준다. `계속 작성`은 확인을 취소하고, `저장하지 않고 닫기`만 실제로 닫는다.
  - 저장 성공(`projection.requestSaveSucceeded == true`)은 `LaunchedEffect`로 감지해 modal을 자동으로 닫는다 — feedback은 이미 저장 전/후 notice 문구로 alert되고, 닫힌 뒤에는 화면에 결과가 반영된 상태(요청 저장 notice)가 바로 보인다.
- `RequestWriterPort`(`RequestInboxWriterAdapter`)의 atomic write, optimistic concurrency 충돌 감지, `SaveRequestUseCase` 호출 경로는 전혀 바꾸지 않았다 — modal은 기존 `HrnsUiEvent.RequestEntrySubmitted`를 그대로 발행할 뿐이다. 충돌/실패 문구(`SaveRequestOutcome.Conflict`/`Failed`)도 기존 `AppViewModel` 로직을 그대로 modal의 `notice`로 보여준다.

## 8. presentation 책임 분리와 SOLID·MVVM/UDF 판단

| 항목 | 판정 | 근거 |
|---|---|---|
| Composable은 file I/O/PowerShell 실행을 하지 않는다 | 유지 | `ModalDialog`/`ProjectManagementSection`/`RequestEntryModal`/`HarnessRunFeedback` 모두 이미 계산된 projection(`RunStatusProjection`, `RegistryProjectItem` 등)만 읽고 typed event(`HrnsUiEvent`)만 발행한다. 새로 추가한 `remember { mutableStateOf }`는 modal open/close, 확인 화면 전환, 접힘 상태 같은 순수 UI-local state뿐이다 |
| ViewModel은 event 처리·use case 호출·`StateFlow` 조립만 한다 | 유지 | `AppViewModel`에 추가한 코드는 `runCompletedAt` 대입과 `refreshRunProjectionOnly()`의 `setup` 재조립뿐이다 — 새 도메인 판단이나 file I/O를 추가하지 않았다 |
| domain policy와 표시 문구를 섞지 않는다 | 유지 | `ActionPolicy`/`ClosurePolicy`/`CompatibilityPolicy`/`BoundaryPolicy`는 이번 phase에서 코드 한 줄도 바뀌지 않았다(§9). 새 라벨은 모두 presentation의 `displayLabel()`류 함수 안에만 있다 |
| God ViewModel/Service Locator/문자열 기반 분기 금지(§19) | 위반 없음 | 새 상태(`isRunning`/`lastCommandKind`/`lastOutcome`/`lastCompletedAtLabel`/`lastSummaryLine`)는 모두 `RunStatusProjectionAssembler`라는 기존 전용 mapper 안에서만 계산된다. `HarnessCommandKind.toRetryAction()`은 typed enum→typed `UiAction?` 매핑이며 문자열 비교가 아니다 |
| 과도한 추상화 없음 | 유지 | 재시도 대상은 실제로 재시도 CTA가 필요한 Doctor/ValidateOps 두 가지로 한정했다 — 6개 `HarnessCommandKind` 전체에 범용 retry 프레임워크를 만들지 않았다. `ModalDialog`도 새 OS window API 없이 기존 Compose tree 안에서 재사용 가능한 최소 컴포넌트로 유지했다 |

## 9. `WORKFLOW_STATE.json`/Harness 계약/기존 정책을 바꾸지 않았다는 근거

- `core`/`infra` 모듈은 `git diff --stat`에 전혀 나타나지 않는다(§1) — `ActionPolicy`, `ClosurePolicy`, `CompatibilityPolicy`, `BoundaryPolicy`, `ExecuteHarnessActionUseCase`의 재검증→typed command→lock→runner→lock 보유 중 State reread→release 순서, `RegisterProjectUseCase`/`SelectProjectUseCase`/`DeleteProjectUseCase`, `RequestWriterPort` 계약 모두 이번 세션에서 손대지 않았다.
- UI는 여전히 `WORKFLOW_STATE.json`/Harness Markdown/log를 직접 쓰지 않는다 — 새로 추가한 modal/feedback 코드 어디에도 파일 쓰기가 없다(모두 기존 use case 호출 또는 순수 UI state다).
- `stdout`/label 문자열만으로 성공을 판정하지 않는다는 계약도 유지된다 — `HarnessRunFeedback`의 outcome은 `ExecuteHarnessActionUseCase`가 이미 lock 보유 중 다시 읽은 `ProcessRunResult`/`HarnessDiagnosticContract`에서만 파생된다.

## 10. 기존 Phase 6A/6B/7 — 보류 과제 명시

- `doc/hrns_now_claude_plan.md` §0.5, "[보류 배포 과제] 기존 Phase 6"과 "[보류 제품 과제] 기존 Phase 7" 절이 이미 이 재정의를 문서화하고 있다.
- 이번 세션은 그 보류 과제의 어떤 부분도 재개하지 않았다: 외부 Kit MSI clean Windows smoke(G6A), 승인 Harness Runtime artifact 통합(G6B), 실험 기능(Phase 7) 모두 구현·검증하지 않았다.
- G6A/G6B의 현재 Gate 상태(둘 다 `phase6-report.md`/`phase6b-report.md` 기준 BLOCKED)는 이번 세션으로 전혀 바뀌지 않는다. 새 Phase 6(G6-UX)의 통과 여부는 이 보고서와 별개로 Codex가 독립 판정한다.

## 11. Windows installer 품질

- 실제 DSL 표면을 다시 확인했다(`compose-gradle-plugin-1.10.3-sources.jar`의 `PlatformSettings.kt`/`AbstractDistributions.kt`를 직접 읽음, 억측하지 않음).
  - `WindowsPlatformSettings.dirChooser: Boolean = true`가 기본값이라는 것을 확인했다 — 이미 사용자가 설치 위치를 확인·변경할 수 있는 마법사 단계가 기본 제공되고 있었다. 이를 암묵적 기본값에 맡기지 않고 `composeApp/build.gradle.kts`에 `dirChooser = true`로 명시하고 그 이유를 주석으로 남겼다(§1의 유일한 build.gradle.kts 변경).
  - `AbstractDistributions`에 `licenseFile`(EULA 페이지)와 `appResourcesRootDir`가 존재함을 확인했지만, EULA/개인정보 정책은 `doc/hrns_now_claude_plan.md`의 Post-MVP D1(서명·릴리스 운영)에 속하는 별도 제품/보안 결정이므로 이번 phase에서 도입하지 않았다.
  - WiX가 노출하는 배너/다이얼로그 비트맵(`WixUIBannerBmp`/`WixUIDialogBmp`) 재스킨이나 `resource-dir` 수준의 커스터마이징에 대응하는 DSL 속성은 이 버전에 없다 — jpackage 원시 호출이나 커스텀 WXS 없이는 접근할 수 없으며, 이는 "custom bootstrapper 금지"에 해당하는 범위라 시도하지 않았다.
- Phase 6A가 이미 실측 확정한 한계는 다시 검증하지 않고 그대로 인용한다: WiX 3.11(`candle`/`light`)이 non-ASCII 명령행 인자를 이 host의 native encoding(MS949)에서 처리하지 못해 `description`/`vendor`/`packageName`/`menuGroup`을 한국어로 바꾸면 빌드 자체가 실패한다(`phase6-report.md` §6.2). 따라서 설치 마법사 안의 문구 자체를 한국어로 바꾸는 것은 이번에도 시도하지 않았다 — 대신 이미 한국어인 HRNS-NOW 앱 UI 쪽에서 최대한 이해 가능성을 높이는 쪽으로 이번 phase 범위를 지켰다.
- 커스텀 bootstrapper, 코드 서명, 자동 업데이트, 번들 Harness Runtime staging은 이번에도 만들지 않았다.
- `:composeApp:packageReleaseMsi --rerun-tasks` 결과는 §12에 기록한다.

## 12. 실행한 테스트·패키징 명령과 결과

```powershell
.\gradlew.bat :composeApp:compileKotlinJvm         # BUILD SUCCESSFUL — 새 UI 코드 컴파일 확인
.\gradlew.bat :core:test :infra:test :composeApp:jvmTest   # BUILD SUCCESSFUL
.\gradlew.bat check                                 # BUILD SUCCESSFUL (core/infra/composeApp 전체)
.\gradlew.bat :composeApp:jvmTest                   # (신규 테스트 추가 후 재실행) BUILD SUCCESSFUL
.\gradlew.bat :composeApp:packageReleaseMsi --rerun-tasks   # BUILD SUCCESSFUL, 5m 6s
```

- JUnit XML 실측 테스트 수: `core` 122, `infra` 143, `composeApp` **72**(기존 68 + 이번에 추가한 4건), 합계 **337**. 실패·에러·스킵 0건.
- 이번에 추가한 테스트(`RunStatusProjectionAssemblerTest.kt`, 4건): 실행 중 인라인 feedback의 `isRunning`/`lastCommandKind`/`lastOutcome`(진행 중), 완료된 실행의 outcome/완료 시각/짧은 요약, 실패 check의 요약이 실패 원인 message를 그대로 보여주는지, `HarnessCommandKind.toRetryAction()`/`retryLabel()`이 Doctor/ValidateOps에만 재시도 대상을 반환하는지.
- 기존 테스트 3건은 새 라벨에 맞춰 fixture 문자열만 갱신했다(`DefaultProjectionsTest.kt`의 "상태 점검 실행"→"환경 점검", `RunStatusProjectionAssemblerTest.kt`의 "Doctor"/"Ops Validation"→"환경 점검"/"작업 기준 점검" 2건, `RecoveryProjectionsTest.kt`의 "요청 작성"→"요구사항 작성") — 검증 로직 자체는 바꾸지 않았다.
- 릴리스 MSI 산출물: `composeApp\build\compose\binaries\main-release\msi\HRNS-NOW-1.0.0.msi`, 50,555,470 bytes, 빌드 시각 2026-07-28 13:48. `packageName`/`vendor`/아이콘/UpgradeCode는 Phase 6A 값 그대로 유지했다(§11에서 변경한 것은 `dirChooser` 명시뿐).
- **미실행**: 이 MSI의 clean install/uninstall smoke는 이번 phase 범위가 아니다(그 항목은 보류된 기존 Phase 6A의 G6A 소관이며, 이번 UI/UX phase는 패키징 그 자체가 아니라 "현재 계약 안에서" 최소 품질만 다룬다). packaging task 성공 자체가 build.gradle.kts 변경이 회귀를 만들지 않았다는 증거다.

## 13. 수동 QA — 수행한 것과 수행하지 못한 것 (정직한 한계)

- **수행**: 컴파일 성공(`compileKotlinJvm`), 전체 자동화 테스트 통과(337건), release MSI 패키징 성공 확인. 이는 코드 정확성/회귀 부재를 보여주지만 실제 화면 동작(제품 목표: "즉시 식별 가능한가")을 보여주지는 않는다.
- **수행하지 못함**: 이 세션에는 이 네이티브 Windows Compose Desktop 앱을 실제로 띄워 클릭해 보고 스크린샷을 남기는 절차가 없다 — 이 저장소에는 이를 위한 project skill이 없고, 브라우저 기반(Playwright)이나 Electron 기반 드라이버는 이 native JVM/Compose 창에 적용되지 않는다. 따라서 다음은 **육안으로 검증하지 못했다**: 상단 리본의 활성 프로젝트 표시가 실제로 잘리지 않고 보이는지, 프로젝트 관리 modal의 크기·스크롤이 다양한 창 크기에서 적절한지, 요구사항 작성 modal의 ESC/포커스가 실제 키보드 입력에서 기대대로 동작하는지, 실행 feedback 카드의 spinner/배지 색상이 다크/라이트 테마 모두에서 읽기 좋은지.
- **권고**: 다음 단계로 `./gradlew.bat :composeApp:run`(또는 `runReleaseDistributable`)으로 앱을 직접 띄워, 이전 Phase들과 같은 방식으로 사용자가 직접 위 네 가지를 확인해 주기를 권한다. 이 보고서는 이 수동 확인을 "완료"로 대체하지 않는다.

## 14. 잔여 위험

- `refreshRunProjectionOnly()`가 `setup.actions`를 다시 조립하도록 고친 것은 실행 중 중복 클릭을 UI에서도 막기 위한 수정이지만, 이 정확한 타이밍(실행 중에는 비활성, 완료 후 재활성)을 검증하는 새 ViewModel 레벨 테스트는 추가하지 않았다 — 기존 "Doctor 실행 중 중복 클릭은 harnessRunner를 한 번만 호출한다" 테스트가 실행 결과(중복 실행 안 됨)는 계속 보장하지만, `state.setup.actions`의 `enabled` 값 자체를 실행 도중 시점에 assert하지는 않는다. 이런 테스트를 추가하려면 이 프로젝트의 fake `HarnessRunnerPort`를 CompletableDeferred 기반으로 바꿔 실행을 인위적으로 멈춰야 하는데, 이번 phase 범위(§13에서 이미 정직하게 기록한 시간 제약)에서는 하지 않았다.
- 설치 마법사 자체의 한국어 안내는 여전히 불가능하다(§11) — 이는 이번 phase가 만든 제약이 아니라 Phase 6A에서 이미 실측된 WiX 3.11/host encoding 한계이며, host의 시스템 코드페이지를 UTF-8로 바꾸거나(사용자 환경 변경, 앱 범위 밖) Harness/HRNS-NOW가 자체 커스텀 WXS를 도입하기 전에는 해소되지 않는다.
- 육안 GUI 확인이 없었다는 한계(§13)는 그대로 남는다.

## 15. Harness/문서/Git 관련 명시

- `D:\harness-kit`은 이번 세션 동안 참조·수정·복사·zip backup 어느 것도 하지 않았다.
- `doc/hrns_now_packaging_plan.md`는 읽지도, 수정·삭제·stage하지도 않았다 — `git status`에 여전히 사용자 소유 untracked 파일로 남아 있다.
- 이 세션에서 `git add`/`commit`/`amend`/`rebase`/`reset`/`stash`/`clean`/`push`를 수행하지 않았다. 커밋과 새 Phase 6(G6-UX) Gate 판정, 그리고 보류 과제 재개 여부는 모두 Codex만 결정한다.

## Codex 독립 검증·보정 — 2026-07-28

### 검증 기준과 범위

- 검증 시작 HEAD: `8f16b47` (`docs: 새 Phase 6 UI UX QA 개선 과제 정의`)
- 기준 문서: `doc/hrns_now_claude_plan.md` §0.5, `doc/hrns_now_design_pattern.md` §8·§9·§18~20, `doc/claude_prompts/phase6-uiux-qa-improvement.md`
- 검토 대상: Claude의 미커밋 Compose/presentation·테스트 diff와 이 보고서. `core`/`infra`, `D:\harness-kit`, 사용자 소유 untracked `doc/hrns_now_packaging_plan.md`는 수정하지 않았다.
- UTF-8 without BOM을 이 보고서와 이번 보정 파일에서 직접 확인했다.

### 발견 사항과 최소 보정

1. **요구사항 CTA 위치 보정** — 구현 당시 `요구사항 작성` 카드가 개발 전략·작업 대기열 아래에 있어, 작업 지시의 "상단 CTA"와 맞지 않았다. `StrategyScreen`에서 해당 카드를 화면 hero 바로 뒤로 옮겼다.
2. **남은 용어 혼용 보정** — `작업 대기열` 제목에 남아 있던 괄호 설명과 작업 현황의 `ops validation`/`queue`/`slice` 등 혼합 표시를 `작업 기준 점검`·`작업 대기열 상태`·`현재 작업 단위` 등으로 정리했다. 활성 프로젝트 요약의 경로 라벨도 한국어로 통일했다. 실행 기록의 저수준 영역은 기본 화면에서 `실행 상세` 접힘으로만 노출한다.
3. **실행 feedback 회귀 테스트 보강** — `AppViewModelTest`에 `CompletableDeferred` 기반 실행 중단 fixture를 추가했다. 환경 점검 실행 중 프로젝트 관리의 환경 점검/작업 기준 점검 버튼이 즉시 비활성화되고, 완료 뒤 `ActionPolicy`가 허용하는 상태로 돌아오는 것을 검증한다. 이 테스트는 §14의 미검증 위험을 해소한다.

위 보정은 presentation·ViewModel 상태 투영과 테스트에 한정한다. Composable의 파일 I/O/PowerShell 호출, 정책 변경, `WORKFLOW_STATE.json` 직접 쓰기, mock fallback, Harness 명령·lock 계약 변경은 없었다.

### SOLID·설계 판정

| 항목 | 판정 | 근거 |
|---|---|---|
| SRP / MVVM·UDF | PASS | modal·spinner·접힘은 Composable local state, 실행 상태는 `HarnessRunViewState`→`RunStatusProjection`, 실행과 정책은 기존 ViewModel/use case 경로에 남아 있다. |
| DIP·계층 의존 | PASS | `core ← infra ← composeApp` 방향을 변경하지 않았고 UI가 concrete filesystem/process adapter를 만들지 않는다. |
| OCP·typed action | PASS | 재시도는 `HarnessCommandKind`→`UiAction` typed mapping이며 label 문자열을 action 식별자로 쓰지 않는다. |
| fail-closed CTA | PASS | 실행 중에는 현재 policy가 허용한 run action만 일시 비활성화하며, 완료 뒤에는 새로 읽은 policy projection을 사용한다. |
| 과도한 추상화 | PASS | 범용 modal 하나와 기존 projection mapper 확장만 사용했고 새 service/factory 계층을 만들지 않았다. |

### 자동 검증·MSI 재패키징

| 검증 | 명령/근거 | 결과 |
|---|---|---|
| Targeted | `:composeApp:jvmTest --tests AppViewModelTest --tests DefaultProjectionsTest --tests RunStatusProjectionAssemblerTest` | PASS |
| Module/Full | `:core:test :infra:test :composeApp:jvmTest check` | PASS |
| 테스트 실측 | JUnit XML: core 122, infra 143, composeApp 74 | 총 339건, failure/error/skip 0 |
| MSI | `:composeApp:packageReleaseMsi --rerun-tasks` | PASS, 5m 6s |

- 새 release MSI: `composeApp\build\compose\binaries\main-release\msi\HRNS-NOW-1.0.0.msi`
  - 생성 시각: 2026-07-28 14:02:59
  - 크기: 50,551,375 bytes
  - SHA-256: `3B5DDC36875A38B0DA67C9DDDAE3237D8A2070CF4A27822FFD4EAAAAA7A97BED`
- jpackage 인자 파일에서 `--win-dir-chooser`, 전용 `.ico`, `HRNS-NOW` 이름, `1.0.0` 버전을 확인했다. jlink runtime image의 `jdk.charsets` 모듈도 `jimage list`로 확인했으며 Harness/daily artifact는 패키지 staging에 포함하지 않았다.

### 수동 QA와 Gate 판정

- 자동 검증과 release MSI 재패키징은 통과했지만, 새 Compose Desktop 창에서 활성 프로젝트 리본, 두 modal의 실제 크기·스크롤·ESC, spinner/색상, 다크·라이트 테마 가독성을 **육안으로 재확인하지 못했다**.
- 따라서 현재 판정은 `G6-UX: BLOCKED`다. 이는 코드·자동 테스트 실패가 아니라, 이 Phase가 요구한 수동 UI QA 증빙이 아직 없기 때문이다.
- 다음 허용 작업은 **새 Phase 6(G6-UX) 수동 QA 보완**뿐이다. 기존 보류 과제 G6A/G6B/기존 Phase 7의 상태는 계속 `BLOCKED`이며 이 결과로 변경되지 않는다.
