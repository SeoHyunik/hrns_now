# Phase 8 보완 — 전체 현지화 및 네이티브 UI QA

## 작업 성격과 Gate

이번 작업은 새 Phase가 아니라 **Phase 8 Workflow Clarity Gate의 보완 작업**이다.

현재 상태:

```text
현재 기준 HEAD: c7756d7 feat: Phase 8 작업 흐름 UX 개선
현재 Gate: G8-Workflow-Clarity FAIL
NEXT_ALLOWED_PHASE: Phase 8 보완 — 전체 locale 적용 및 native UI QA
```

따라서 Phase 9를 구현하거나 선언하지 않는다. Phase 8의 아래 결함을 해결하고 Codex 검증을 통과한 뒤에만 다음 Phase를 논의할 수 있다.

Claude는 구현과 보고서만 수행한다. **Git add, commit, amend, rebase, reset, clean, stash, push는 절대 수행하지 않는다.** Codex만 커밋한다.

## 반드시 먼저 읽을 자료

다음 문서를 전체로 읽고, 현재 소스를 우선 진실로 삼는다.

```text
README.md
doc/hrns_now_claude_plan.md
doc/hrns_now_design_pattern.md
doc/phase_reports/phase8-workflow-clarity-report.md
doc/claude_prompts/phase8-workflow-clarity-feedback.md
doc/user_workflow_qa_notes.md
```

특히 `phase8-workflow-clarity-report.md`의 **Codex 독립 검증·보정** 절을 이행 기준으로 사용한다.

다음 사용자 소유 untracked 파일은 읽기만 하며 수정·삭제·stage하지 않는다.

```text
doc/hrns_now_packaging_plan.md
doc/user_workflow_qa_notes.md
```

`D:\harness-kit`은 읽기·실행 대상일 뿐 수정하지 않는다.

## Codex가 이미 보정·커밋한 사항

`c7756d7`에는 Claude의 Phase 8 구현 및 다음 Codex 보정이 함께 들어 있다.

- 실행 실패·잠금·요청 저장 실패의 raw reason/path가 알림 이력으로 저장되지 않도록, 알림을 안전한 일반 요약문으로 보정했다. 상세 원인은 현재 모달/화면에서만 본다.
- 공통 작업 버튼에 hover pointer와 hover 색상 피드백을 보정했다.
- Phase 8 보고서에 Gate 실패 근거를 기록했다.

이 보정을 되돌리거나, notification history에 raw external path·process output·session ID·secret을 넣지 않는다.

## 이번 보완의 필수 결과

### 1. 화면 전체의 진짜 한국어/영어 전환

현재 `AppLocale`과 `ChromeStrings`는 Shell의 일부 chrome에만 적용되어 있다. 영어를 선택해도 화면 본문, 액션, 상태, 오류 메시지 상당수가 한국어로 남는다. 이것은 부분 번역이 아니라 **Gate 실패**다.

아래를 포함하여 앱이 소유한 모든 정적 UI 문구를 ko/en으로 제공한다.

- `Screens.kt`의 입력 폼, 등록 모달, 필드명, 버튼, empty/error/help 문구
- 프로젝트 관리, 작업 현황, 작업 계획, 실행 기록, 복구 센터·마감 확인 화면
- `DefaultProjections`, `UiActionLabels`, Run status/stop reason/validation/compatibility 등의 화면 투영 문구
- 날짜/읽기 전용/잠금/등록 실패/저장 불가/실행 불가 등 사용자에게 노출되는 정책 결과
- notification center와 toast의 앱 소유 문구

원칙:

- `core`는 Compose나 `AppLocale`에 의존하지 않는다. 정책은 가능한 한 typed 값·reason key를 내고, presentation 계층이 locale별 문구로 투영한다.
- 한국어 문장을 식별자로 삼아 `when (message)` 또는 `String.replace`로 번역하지 않는다.
- 기존의 `blockedReason: String`처럼 사용자 문구가 domain에 새어 있는 곳은 최소 범위에서 typed reason/reference 또는 presentation mapping으로 정리하되, Phase 8 범위를 넘는 대규모 재설계는 하지 않는다.
- Harness State의 raw unknown 값은 안전하게 원문을 보존하되, 앱이 추가한 설명은 선택 locale로 표시한다.
- 사용자 작성 `TODAY_STRATEGY.md`/`REQUEST_INBOX.md` 본문을 자동 번역하거나 외부 API/LLM에 보내지 않는다. 알려진 heading/metadata 외의 원문은 원문 그대로 둔다.
- locale 선택은 기존 `%APPDATA%\hrns-now\ui-preferences.json` 경로와 UTF-8 no-BOM 정책을 유지하고, 재시작 후에도 복구되어야 한다.

### 2. 실제 네이티브 창의 수동 UI QA

`jvmTest`만으로는 이 Gate를 통과할 수 없다. 반드시 다음으로 실제 Compose Desktop 창을 띄우고, 사람이 볼 수 있는 창에서 확인한다.

```powershell
Set-Location -LiteralPath 'S:\dev\project\hrns_now'
.\gradlew.bat :composeApp:run
```

기록할 최소 관찰 항목:

- 내장 SDK가 없는 기본 등록 시 `진단 후 등록`을 눌렀을 때 즉시 진행 상태와 이해 가능한 실패/다음 행동이 모달에 보이며, 고급 설정의 외부 Kit 선택으로 이어지는지
- 오늘 State가 없는 경우 과거 전략이 오늘 전략처럼 보이지 않고, State/날짜/lock 정책이 허용할 때만 `오늘 작업 시작`이 보이는지
- 날짜 선택이 최대 5개와 pager, 현재 선택 강조, 과거 read-only를 명확하게 보여 주는지
- 주요 action의 hover cursor/색상, disabled, loading, 성공·실패 toast와 알림함이 일관되게 보이는지
- 한국어와 영어를 각각 선택했을 때 Shell뿐 아니라 대표 화면의 본문·모달·action·상태·오류가 모두 해당 언어로 표시되고, 재시작 뒤 선택이 보존되는지
- 긴 Markdown의 heading/list/code가 읽기 좋게 표시되며 raw HTML이 실행되지 않는지

실제 Harness 명령·유료 모델/API 호출·대상 프로젝트 쓰기는 임의로 실행하지 않는다. 안전한 등록 진단, 화면 탐색, fixture/read-only 상태 확인까지만 수행한다. 수동 GUI 검증이 환경상 불가능하면 거짓 PASS를 쓰지 말고 정확한 미검증 항목과 재현 절차를 보고한다.

### 2.1 오늘 시작과 요구사항 작성의 단일·정직한 흐름

사용자 QA에서 다음 두 결함이 실제로 확인됐다.

- `WorkspaceDaySection`은 오늘 날짜가 목록에 없을 때 `WorkspaceDaySelected(today)`만 올리는 **날짜 선택** 기능인데도 버튼 문구를 `오늘 작업 시작`으로 사용한다. 동시에 Cockpit/작업 계획의 allowed action은 실제 `BootstrapDay`를 같은 문구로 노출하므로, 사용자는 동일한 명령이 여러 군데에 있다고 오해한다.
- 오늘 State가 `Missing`이면 ActionPolicy는 올바르게 `BootstrapDay`만 허용한다. 아직 `REQUEST_INBOX.md`가 없기 때문에 `EditRequest`가 허용되지 않고, 작업 계획의 `요구사항 작성`이 비활성으로만 보인다. 이는 UI가 State 없이 파일을 직접 쓰면 안 된다는 계약 자체는 맞지만, 사용자에게 시작 순서를 전혀 설명하지 못하는 결함이다.

아래 UX 계약을 구현하고, 정책을 약화하지 않는다.

1. **실제 Bootstrap 실행 CTA는 한 화면에 한 곳만** 표시한다. 기본 진입점은 `작업 계획` 화면 최상단의 시작/요구사항 카드다. 오늘 State가 없고 `BootstrapDay`가 ActionPolicy상 허용될 때 이 카드는 비활성 `요구사항 작성` 대신 설명과 함께 활성 `오늘 작업 시작`을 보여 준다.
2. `오늘 작업 시작`은 기존 typed `UiAction.BootstrapDay`만 실행하고, 실행 중 중복을 막고, 종료 뒤 State를 다시 읽는다. 성공한 State가 `RequestIntakePending` 또는 `NoRequest`일 때에만 같은 위치가 활성 `요구사항 작성`으로 전환된다.
3. `WorkspaceDaySection`의 버튼은 날짜를 선택할 뿐이므로 `오늘 날짜 선택` 또는 동등한 비실행 문구로 바꾸거나, 선택이 이미 명백하면 제거한다. **선택만으로 Bootstrap처럼 보이게 하지 않는다.**
4. 작업 현황·실행 기록 등 다른 화면은 `BootstrapDay` 실행 버튼을 중복 노출하지 않는다. 필요한 경우에는 `작업 계획으로 이동` 같은 순수 navigation만 제공하고, 실제 실행은 작업 계획의 단일 CTA에서 한다.
5. Bootstrap이 compatibility/boundary/lock/State 오류로 허용되지 않으면 어느 화면에도 실행 버튼을 억지로 열지 않는다. 작업 계획 카드에 차단 이유와 다음 안전한 행동을 읽기 쉽게 표시한다.

다음 회귀를 자동·수동으로 검증한다.

- eligible Missing/today 상태에서 실제 `BootstrapDay` CTA가 작업 계획에만 하나 존재하는지
- 날짜 선택 UI가 실행 CTA처럼 보이지 않는지
- Missing 상태에서는 요구사항 카드가 단순 disabled 상태가 아니라 시작 순서와 활성 Bootstrap CTA를 보여 주는지
- Bootstrap 뒤 State 재조회 결과가 request-intake이면 `요구사항 작성`으로 전환되는지
- past, locked, malformed, incompatible 상태에서는 Bootstrap과 요구사항 입력이 모두 fail-closed인지

### 3. 글꼴과 중복 문구의 근거 기반 정리

실제 네이티브 창에서 영문/한글 혼합 텍스트를 확인한다.

- 문제가 눈에 보이는 근거가 있을 때만 Windows 우선 `Segoe UI Variable` 또는 `Segoe UI`와 검증된 한글 fallback을 사용한다. 원격 폰트 다운로드나 라이선스 불명 폰트 추가는 금지한다.
- 경로·명령·코드 블록만 고정폭 글꼴을 사용하고 일반 본문에 `Monospace`를 쓰지 않는다.
- 같은 정보가 한 화면에 중복되는 경우 한 표현으로 통일하되, State의 기계값과 사람 설명을 혼동해 삭제하지 않는다.

## 테스트 보강

다음을 실제로 추가·보강한다.

- 한국어/영어 각각에서 대표 화면 문구, UI action label, typed action/stop/validation/error 투영을 검증하는 presentation 단위 테스트
- locale 적용이 Shell에만 머무르지 않는 회귀를 막는 테스트
- supplied external root, lock reason, request save failure reason을 주어도 notification history에 그 raw 값이 남지 않는 보안 회귀 테스트
- Markdown은 장문 구조를 안전하게 보여 주고 raw HTML을 실행하지 않는 테스트 가능한 부분의 회귀 테스트
- 기존 Bootstrap eligibility, 과거 날짜 read-only, malformed/unknown fail-closed, State 재조회 계약 회귀 테스트

테스트를 삭제·skip·약화하거나 Harness 계약을 UI 편의에 맞춰 변경하지 않는다.

변경 뒤 실제 Gradle task를 확인하고 최소 다음을 실행한다.

```powershell
.\gradlew.bat :core:test
.\gradlew.bat :infra:test
.\gradlew.bat :composeApp:jvmTest
.\gradlew.bat check
```

Gradle packaging 설정 또는 배포 리소스를 실제 변경했을 때만 아래도 수행한다.

```powershell
.\gradlew.bat :composeApp:packageReleaseMsi --rerun-tasks
.\gradlew.bat :composeApp:createReleaseDistributable --rerun-tasks
```

## 금지 범위

- Phase 6A/6B/7E packaging gate, internal SDK 계약, Harness Runtime 배포의 재구현
- 새 workflow 기능이나 Harness 상태/wrapper 창작
- `WORKFLOW_STATE.json`을 UI에서 직접 쓰는 동작
- demo/mock fallback으로 실데이터 실패를 감추는 동작
- raw path, secret, token, session ID, raw process output의 지속 저장/알림 표시
- 사용자 소유 untracked 파일 수정·삭제
- Git 작업

## 보고서

완료 후 `doc/phase_reports/phase8-completion-report.md`를 **UTF-8 without BOM**으로 작성한다. Git 작업은 하지 않는다.

보고서에 아래를 정확히 기록한다.

1. 시작 HEAD와 검토한 문서
2. Codex `c7756d7` 보정 사항을 유지했는지
3. locale 카탈로그/투영 책임 분리와 전체 적용 범위
4. ko/en 수동 GUI 검증의 실제 환경·단계·관찰 결과와 미검증 항목
5. 글꼴/중복 문구 판단 근거
6. 추가·변경한 테스트와 전체 Gradle 결과
7. 변경 파일, 미수정 범위, Harness Kit 무변경 확인
8. 자체 판단은 `Phase 8 보완 구현 완료(검증 대기)`까지만 적고 Gate PASS 선언은 Codex에게 맡긴다.

Codex가 이 보고서와 live source를 독립 검증하기 전에는 `NEXT_ALLOWED_PHASE`를 Phase 9로 변경하지 않는다.
