# Phase 9 — 데스크톱 레이아웃 안정화와 신규 프로젝트 온보딩

## 역할과 범위

당신은 `hrns_now`의 구현 담당자다. 이번 작업은 Codex 독립 검증 뒤 확정된 사용자 QA01~QA03을 해결하는 **Phase 9**다.

이전 Phase 6B Runtime distribution과 기존 Phase 7 이후 미해결 배포 과제는 계속 보류한다. 이번 Phase는 그 Gate를 해제하거나 Harness Runtime을 번들링하는 작업이 아니다. `D:\harness-kit`은 읽기·실행 검증 대상으로만 사용하고 수정하지 않는다.

Git 작업은 하지 않는다. commit, amend, rebase, reset, stash, clean, push를 수행하지 않는다. 구현·테스트·보고서만 작성하며, commit은 Codex가 별도 수행한다.

## 작업 시작 전 반드시 확인할 자료

현재 HEAD와 아래 파일을 실제로 읽는다. 과거 보고서의 완료 선언보다 live 소스와 Harness 계약을 우선한다.

- `README.md`
- `doc/hrns_now_claude_plan.md`
- `doc/hrns_now_design_pattern.md`
- `doc/phase_reports/phase4-report.md`
- `doc/phase_reports/phase7-internal-sdk-report.md`
- `doc/phase_reports/phase8-completion-report.md`
- `doc/claude_prompts/phase8-native-ui-qa-gate.md`
- `doc/QA_captures/QA01.png`
- `doc/QA_captures/QA02.png`
- `doc/QA_captures/QA03.png`
- `composeApp/src/jvmMain/kotlin/io/hrns_now/app/main.kt`
- `composeApp/src/jvmMain/kotlin/io/hrns_now/app/ui/Shell.kt`
- `composeApp/src/jvmMain/kotlin/io/hrns_now/app/ui/Screens.kt`
- `composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/viewmodel/AppViewModel.kt`
- `core/src/main/kotlin/io/hrns_now/core/domain/policy/ActionPolicy.kt`
- `core/src/main/kotlin/io/hrns_now/core/domain/model/HarnessCommand.kt`

`doc/QA_captures/`, `doc/hrns_now_packaging_plan.md`, `doc/user_workflow_qa_notes.md`는 사용자 소유 자료일 수 있다. 읽기만 하고 수정·삭제·stage하지 않는다.

작업 전 아래를 기록한다.

```powershell
Set-Location -LiteralPath 'S:\dev\project\hrns_now'
git status --short
git branch --show-current
git log -10 --oneline --decorate
```

현재 `harness-dev` HEAD를 기준으로 하며, 특정 과거 SHA로 reset하지 않는다.

---

## 사용자 QA의 확정 해석

### QA01 — 축소 시 중첩·깨짐

`QA01.png`은 약 729px 폭에서 상단 준비 상태, 사이드바, 본문이 서로 겹치거나 잘리는 것을 보여 준다. HRNS-NOW는 Windows 데스크톱 control plane이며 모바일 앱을 이번 Phase에 새로 설계하지 않는다.

**확정 정책:** 기본 창은 `1440 x 900dp`를 유지하고, 네이티브 창 최소 크기를 **1280 x 800dp**로 둔다. 사용자가 그보다 작게 줄이려 해도 Windows 창 자체가 더 작아지지 않아야 한다.

- Compose Desktop의 실제 `Window`/AWT API를 확인해 최소 크기를 설정한다. 존재하지 않는 API를 가정하지 않는다.
- 최소 크기는 `main.kt`에 매직 넘버로 흩어 두지 말고, 플랫폼 창 제약을 표현하는 작은 명명된 값/구성으로 둔다. core는 Compose/AWT를 알면 안 된다.
- `requiredSize`로 내부 Composable만 강제해 scroll·clip으로 증상을 가리는 방식은 금지한다.
- 1280 x 800과 기본 1440 x 900에서 상단 리본, 사이드바, 준비 상태 리본, 우측 제어 버튼, 모달, 본문 카드가 겹치거나 clip되지 않아야 한다.
- 모바일 전용 navigation, 별도 phone layout, 임의 break-point 기능은 이번 범위 밖이다.

### QA02 — 상단 정보의 인지성·색상

QA02의 상단 리본은 앱을 관리하는 핵심 상태인데 로고·제품명·프로젝트명·준비 상태·우측 제어가 작고, muted 문구가 너무 눈에 띄거나 읽기 어렵다.

다음 기준으로 보정한다.

1. 앱 내부 상단 `BrandMark`는 현재 원본 고해상도 `icon.png`을 사용한다. Windows 창/작업표시줄의 `hrns-now.ico` 역할과 섞지 않는다.
2. 1440 x 900 기준으로 내부 로고는 **84dp**, `HRNS-NOW` 제목은 **22sp**, 활성 프로젝트 이름은 **16sp**를 기준으로 한다. 실제 레이아웃 측정에서 1280px 최소 폭을 침범한다면 나머지 여백·배치를 조정하되, 글자를 다시 축소해서 해결하지 않는다.
3. 알림(안내), 언어, 테마 등 우측 상단 제어는 최소 44dp의 클릭 영역과 읽기 쉬운 14sp 수준의 label을 제공한다. hover pointer·hover/pressed 상태는 Phase 6 규칙을 유지한다.
4. 활성 프로젝트가 없을 때 기존 `선택 안 됨` 대신 locale과 무관하게 literal **`NONE`**을 표시한다. 색상은 현재 `icon.png`의 말 실루엣 파랑을 기준으로 한 theme token을 사용한다. 임의의 다른 blue를 산발적으로 하드코딩하지 않는다.
5. 현재 상단 리본의 연노랑 muted text는 너무 채도가 높다. dark theme에서는 흰색에 가까운 낮은 채도의 warm off-white/yellow(예: `#D9D7C7` 계열), light theme에서는 대비를 유지하는 muted warm dark 색을 **상단 리본 전용 theme token**으로 정의한다. 전역 `tertiaryText`를 바꿔 다른 화면의 의미를 훼손하지 않는다.
6. 준비 상태의 dot, label, value는 label/value 위계를 유지한다. 색상만으로 `확인됨`/`미확인`을 전달하지 말고 기존 텍스트와 dot을 함께 유지한다.

Composable에 상태 문자열 비교나 palette 분기를 흩어 두지 않는다. theme token과 `ChromeStrings`/presentation model의 책임을 유지한다.

### QA03 — 활성 프로젝트 관리와 신규 작업공간 준비 (Critical)

현재 등록은 Doctor·boundary·compatibility를 통과하면 Registry에 저장·선택할 뿐, 신규 workspace의 오늘 날짜 산출물을 준비하지 않는다. 그 결과 필수 파일이 없고, 날짜 섹션은 유효 폴더 없음으로 보이며, ActionPolicy가 fail-closed로 진단 CTA를 막아 사용자가 다음 행동을 이해할 수 없다.

이것은 UI가 daily 파일을 직접 만들면 안 되는 Harness 계약과 충돌하는 것처럼 보이지만, 해법은 **UI 직접 파일 생성이 아니라 기존 typed `BootstrapDay`를 명시적 온보딩 흐름으로 실행**하는 것이다.

#### A. 활성 프로젝트 UI

- 활성 프로젝트가 있으면 메인 `프로젝트 등록` 버튼은 **`새 프로젝트 등록`**으로 표시한다. 비활성화로 숨기지 않는다.
- 활성 프로젝트 요약에서 `활성` 상태 옆에 **`프로젝트 해제`** 버튼을 둔다.
- 해제는 Registry의 last-active 선택만 제거한다. 등록된 project entry, `%APPDATA%` Registry 외 사용자 데이터, workspace, repository, Harness Kit, State/daily 파일을 삭제·수정하지 않는다.
- 이를 위해 필요하면 `ProjectRegistryPort`에 최소 `clearActive()` 계약과 전용 use case/event를 추가한다. registry adapter는 기존 atomic write·손상 복구·경계 검사 규칙을 그대로 지킨다.
- 해제 직후 selected day, polling context, active project projection을 안전하게 초기화하고 재조회한다. 환경변수 fallback이 있는 경우에는 그것을 source로 명확히 표시하며, registry 프로젝트가 여전히 활성이라고 거짓 표시하지 않는다.
- ProjectRow의 `선택`/`삭제` 의미와 충돌하지 않게 label, enabled 상태, confirmation 필요성을 검토한다. 삭제를 해제의 대체 수단으로 사용하지 않는다.

#### B. 등록과 오늘 작업공간 준비의 명시적 단일 흐름

신규 프로젝트의 기본 primary flow는 사용자가 효과를 알 수 있는 **`진단·등록 및 오늘 작업공간 준비`**여야 한다. 등록만 원하는 경우는 보조 행동으로 명시한다.

- primary 선택은 다음 순서만 따른다.
  1. 후보 boundary 검증
  2. 기존 등록 Doctor 실행 및 Compatibility 검증
  3. Registry 저장과 활성 project 선택
  4. 최신 execution context/state 재조회
  5. 오늘 날짜에 대해 ActionPolicy가 허용할 때 기존 typed `HarnessCommand.BootstrapDay` 실행
  6. 실행 종료 뒤 `WORKFLOW_STATE.json` 재읽기, 날짜 목록/readiness/CTA 재조립
- 신규 command, 가짜 `init`, UI의 `Files.createDirectories`, `WORKFLOW_STATE.json` 직접 write, Markdown을 근거로 한 완료 판정은 금지한다.
- Bootstrap은 기존 Process Adapter + per-machine lock + cancellation + stdout/stderr drain + 종료 후 state re-read 파이프라인을 재사용한다. 등록 화면용 별도 ProcessBuilder/lock lifecycle을 복제하지 않는다.
- stdout 성공 문구가 아니라 재조회한 State와 required daily surface로 준비 성공을 판단한다.
- 오늘이 아닌 날짜, lock, boundary/compatibility/Doctor 실패, malformed/unknown State 등 Policy가 막는 경우에는 Bootstrap을 억지로 실행하지 않는다. 등록 성공과 작업공간 준비 실패/차단을 분리해 설명하고 Registry를 rollback하지 않는다.
- Bootstrap 실행 실패도 Registry를 삭제하거나 daily 파일을 UI가 보정하는 이유가 되지 않는다. "등록은 완료됨 / 작업공간 준비는 실패 또는 차단됨"과 typed·localized next step을 표시한다.
- Phase 8의 “보이는 Bootstrap CTA는 한 번만” 원칙을 유지한다. 등록 진행 중에는 동일 Bootstrap 행동을 다른 화면에 중복 노출하지 않고, 등록 후 미완료 상태에서는 사용자가 이해할 수 있는 한 곳의 재시도/이동 경로만 제공한다.

#### C. 초기화 전후 Setup 화면

- 새 프로젝트에서 아직 daily directory가 없는 것은 오류가 아니라 `오늘 작업공간 준비 전` 상태로 설명한다. `유효한 폴더가 없음`만 단독으로 보여 사용자를 막지 않는다.
- 연결 점검·작업 준비 점검을 Policy를 무시하고 강제로 enable하지 않는다. 등록 중 수행한 Doctor 결과와 현재 허용된 단 하나의 다음 행동을 명확히 표시한다.
- Bootstrap이 State를 정상 생성한 뒤에는 오늘 날짜와 daily 4-file (`REQUEST_INBOX.md`, `TODAY_STRATEGY.md`, `DAILY_HANDOFF.md`, `WORKFLOW_STATE.json`) readiness가 실제 재조회 결과로 갱신돼야 한다.
- `REQUEST_STRUCTURED.md`, 두 log directory, legacy files는 readiness 성공 조건으로 만들지 않는다.

---

## 아키텍처·안전 제약

- `WORKFLOW_STATE.json`은 Harness의 단일 진실이다. UI는 절대 직접 쓰지 않는다.
- `core`는 Compose, AWT, filesystem, ProcessBuilder, JSON 구현에 의존하지 않는다.
- 창 최소 크기는 desktop UI/platform 계층 책임이다. Registry active selection 해제는 core port/use case → infra adapter 방향을 유지한다.
- UI event는 typed event/action을 사용한다. action label 또는 localized text를 식별자로 쓰지 않는다.
- 새 project 준비 결과는 `RegistrationFeedback`과 run/state projection의 책임을 구분한다. 하나의 거대한 ViewModel 상태 문자열이나 boolean 묶음으로 만들지 않는다.
- unknown/malformed/stale/past-day/lock/compatibility failure는 계속 fail-closed다.
- raw session ID, token, secret, raw process output을 Registry·UI·notification에 저장/표시하지 않는다.
- Registry와 lock은 AppData 소유 영역에만 둔다. Harness workspace에 UI 소유 파일을 만들지 않는다.
- `D:\harness-kit` 수정, Runtime 번들, Phase 6B/7 구현, MSI 기능 변경, 테스트 삭제/skip, 대규모 자동 포맷은 금지한다.

---

## 구현 전 Harness 계약 재확인

`D:\harness-kit`을 읽기 전용으로 검사한다.

1. `scripts/run-cycle.ps1`의 wrapper 없는 bootstrap (`-UsePythonSidecars`) 실제 parameter와 today/day root 생성 동작을 확인한다.
2. bootstrap 후 `WORKFLOW_STATE.json`과 required 4-file이 어느 root에 생성되는지 확인한다.
3. Bootstrap이 Doctor/Planning/Execution과 어떤 조합을 금지하는지 확인한다.
4. 불명확하면 wrapper/상태코드/파일을 창작하지 말고 보고서에 BLOCKED 근거를 남긴다.

가능한 경우 외부 Kit·workspace·repository가 서로 포함되지 않는 안전한 임시 fixture에서 deterministic/offline 방식으로 registration + bootstrap 결과를 검증한다. 유료 모델/API 호출은 하지 않는다.

---

## 필수 테스트

기존 테스트를 삭제·완화하지 말고 회귀를 추가한다.

### Core / Infra

- active selection 해제는 project records를 보존하고 last-active만 null로 만든다.
- clear active의 registry atomic write, UTF-8, corruption recovery, boundary protection 회귀를 검증한다.
- 해제 후 Registry → 환경변수 → 사용자 선택 순서가 그대로 유지됨을 검증한다.
- registration-only는 Bootstrap을 실행하지 않는다.
- combined registration은 Doctor/compatibility/registry save/select/context refresh가 성공한 뒤에만 Bootstrap을 요청한다.
- validation/Doctor/compatibility/boundary/lock/past-day 실패 시 Bootstrap이 실행되지 않음을 검증한다.

### Compose / ViewModel

- 1280 x 800 native minimum size configuration을 테스트 가능한 작은 값/구성으로 검증한다. 존재하지 않는 API를 mock으로 성공처럼 보이게 하지 않는다.
- active project 유무에 따른 `새 프로젝트 등록`, `프로젝트 해제`, 기존 선택·삭제 UI 상태를 검증한다.
- active release가 workspace/repository/Harness artifacts를 건드리지 않고 projection을 갱신함을 검증한다.
- combined registration 성공은 Bootstrap 후 state re-read를 수행하고, 실제 projection에서 오늘 날짜/readiness/다음 CTA가 갱신됨을 검증한다.
- Bootstrap 실패/차단은 등록 완료 사실을 보존하면서 localized next step을 보여 주고 raw process reason을 notification에 노출하지 않음을 검증한다.
- `NONE` label, horse-blue theme token, dark/light ribbon muted token을 확인하는 presentation/UI 테스트를 추가한다.

### 수동 QA

```powershell
Set-Location -LiteralPath 'S:\dev\project\hrns_now'
.\gradlew.bat :composeApp:run
```

- 1440 x 900, 1280 x 800에서 QA01의 중첩·clip이 없는지 확인한다.
- 1280 x 800보다 작게 resize하려 해도 native minimum이 지켜지는지 확인한다.
- QA02의 로고/HRNS-NOW/프로젝트명/준비 상태/우측 버튼 크기와 dark/light muted text를 확인한다.
- 활성 프로젝트에서 `새 프로젝트 등록`과 `프로젝트 해제`를 확인하고, 해제 후 registry record가 남아 있는지 확인한다.
- 안전한 새 fixture에서 `진단·등록 및 오늘 작업공간 준비` 후 Harness가 만든 오늘 day root, required 4-file, State 재조회 결과를 확인한다.

실제 클릭 검증이 불가능하면 수행하지 못한 항목을 PASS라고 쓰지 않는다.

실행 순서:

```powershell
.\gradlew.bat :core:test
.\gradlew.bat :infra:test
.\gradlew.bat :composeApp:jvmTest
.\gradlew.bat check
```

task가 없으면 먼저 `./gradlew.bat tasks`로 실제 task를 확인한다.

---

## 산출물과 보고서

보고서는 UTF-8 without BOM으로 다음 파일에 작성한다.

```text
doc/phase_reports/phase9-desktop-layout-and-onboarding-report.md
```

반드시 포함한다.

1. 시작 HEAD, 변경 파일, 사용자 소유 untracked 파일 보존 여부
2. QA01~03을 각 캡처와 연결한 구현 결과
3. 실제 Compose 최소 창 크기 구현 방식과 1280 x 800 검증 증거
4. dark/light 색상 token 및 `NONE`의 적용 위치
5. active release의 Registry 의미와 비삭제 보장
6. registration-only와 registration+bootstrap의 상태 전이
7. 실제 Harness bootstrap parameter·생성 파일·State 재조회 근거
8. 실패/차단 시 fail-closed 동작과 사용자 안내
9. 추가·변경 테스트와 `check` 결과
10. 수동 GUI QA 수행/미수행 항목, 제한 사항
11. Git 작업을 하지 않았다는 사실

보고서 끝에는 다음을 명시한다.

```text
PHASE_9_STATUS: READY_FOR_CODEX_REVIEW | BLOCKED
NEXT_ALLOWED_PHASE: Codex independent verification
```

Code 구현·테스트·보고서까지만 수행하고 중단한다. Codex 검증과 커밋 전에 다음 Phase를 시작하지 않는다.
