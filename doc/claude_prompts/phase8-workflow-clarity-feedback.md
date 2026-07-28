# Claude 작업 지시 — Phase 8: 작업 흐름·상태 피드백·언어 UX 정비

## 역할·현재 Phase·Git

당신은 HRNS-NOW의 **Phase 8 구현 담당자**다. 이 Phase는 실제 사용자 2차 QA에서 확인된 등록 실패 안내, 오늘 시작 흐름, 상태 피드백, 정보 구조, 언어·글꼴 문제를 하나의 사용자 여정으로 보정한다.

목표는 화면을 장식하는 것이 아니라 사용자가 다음 순서를 혼동 없이 수행하게 하는 것이다.

```text
프로젝트 연결
→ 현재 연결 상태 이해
→ 오늘 작업 시작
→ 요구사항 작성
→ 작업 계획 확인
→ 허용된 단일 작업 실행
→ 결과 확인·복구·마감
```

저장소는 `S:\dev\project\hrns_now`, branch는 `harness-dev`다. 모든 Git commit은 Codex만 수행한다. `git add`, `commit`, `amend`, `rebase`, `reset`, `stash`, `clean`, `push`를 수행하지 않는다.

완료 보고서는 `doc/phase_reports/phase8-workflow-clarity-report.md`에 **UTF-8 without BOM**으로 작성한다. 이 프롬프트와 사용자의 QA 기록은 구현 기준이며, Claude 완료 선언은 Codex 독립 검증을 대체하지 않는다.

작업 시작 전 다음을 기록한다.

```powershell
Set-Location -LiteralPath 'S:\dev\project\hrns_now'
git status --short
git branch --show-current
git log -1 --oneline
```

기준 HEAD는 Codex가 Phase 7을 독립 검증·보정해 커밋한 다음 커밋을 포함해야 한다.

```text
ea4f9f7 feat: Phase 7 내장 개발 SDK 연동 구현
```

현재 예상되는 사용자 소유 untracked 파일은 다음 둘이다. 읽을 수는 있으나 수정·삭제·stage하지 않는다.

```text
doc/hrns_now_packaging_plan.md
doc/user_workflow_qa_notes.md
```

새 Phase 6(G6-UX)는 native UI 수동 QA 증빙이 남아 `BLOCKED`이며, 기존 Phase 6A(G6A), 기존 Phase 6B(G6B), 기존 Phase 7E는 보류 상태다. 제품 소유자는 2026-07-28에 실제 UI QA 결과를 반영할 이 Phase 8을 진행하도록 승인했다. 이 Phase가 위 보류 Gate를 PASS로 바꾸거나 Harness Runtime 배포를 허용하는 것은 아니다.

## 반드시 먼저 읽을 문서

- `README.md`
- `doc/hrns_now_claude_plan.md` — 특히 §0.4, §0.5, Phase 7·보류 6A/6B/7E의 경계
- `doc/hrns_now_design_pattern.md` — 특히 MVVM/UDF, presentation 책임, typed action/policy, RuntimeSource/Resolver, SOLID 원칙
- `doc/user_workflow_qa_notes.md` — 이번 Phase의 원본 사용자 QA 기록
- `doc/phase_reports/phase6-uiux-report.md`
- `doc/phase_reports/phase7-internal-sdk-report.md` — Codex 독립 검증·보정 절을 포함해 읽는다
- `doc/claude_prompts/phase6-uiux-qa-improvement.md`
- `doc/claude_prompts/phase7-internal-sdk-runtime.md`
- 이 문서

`D:\harness-kit`은 read-only 참고 대상이다. 오늘 시작(`BootstrapDay`)의 실제 Harness 호출 계약을 확인해야 할 때만 live script·문서를 읽고, 수정·복사·junction/symlink 생성·zip backup·Git 작업을 하지 않는다. 실제 Harness state·wrapper·명령을 창작하지 않는다.

## 사용자 QA의 확정 요구

다음은 취향 메모가 아니라 구현·검증해야 할 문제다. 다만 UI 편의를 위해 Harness 안전 계약을 완화해서는 안 된다.

1. 기본 개발용 내장 SDK 선택에서 `진단 후 등록`을 눌러도 아무 피드백이 없어 원인을 알 수 없다. 고급 설정의 명시적 외부 Kit 연결은 동작한다.
2. 오늘 계획이 없으면 이전 날짜의 개발 전략 원문이 오늘 계획처럼 보이지 않아야 한다. 오늘 작업을 시작하는 명확한 CTA가 필요하다.
3. 모든 주요 버튼은 hover·disabled·running·success·failure를 일관되게 표현하고, 작업 결과는 즉시 알아볼 수 있어야 한다.
4. 영어·한국어가 섞인 화면의 글꼴과 중복 설명을 정리해야 한다.
5. 상단 상태 리본의 `엔진 오프라인`, `프로필 기본`, `점검 대기` 같은 상태가 실제 연결 상태와 모순되거나 색으로 오해를 만들면 안 된다.
6. `READ-ONLY 앱이 소유하지 않음`, `META`, 의미가 불분명한 환경 패널, `아티팩트`/`ARTIFACTS` 중복을 정리해야 한다.
7. 프로젝트 연결의 미설정 경로가 무엇이며 사용자가 어떻게 해소하는지 화면에서 알 수 있어야 한다.
8. 앱 표시 언어를 `한국어`와 `English` 사이에서 바꿀 수 있어야 한다.
9. 작업 날짜는 5개씩 명확하게 탐색하고 현재 선택을 쉽게 알아볼 수 있어야 한다.
10. 개발 전략 Markdown은 읽기 좋은 문서로 보이고, 정적 UI·알려진 상태/제목은 한국어로 이해할 수 있어야 한다.
11. `환경 점검`, `작업 기준 점검`처럼 목적이 불분명한 행동과 영문 오류를 사람이 이해할 수 있는 말로 바꿔야 한다.
12. 요구사항 작성 CTA는 충분히 눈에 띄고, `REQUEST_INBOX.md`라는 파일명보다 무엇을 입력해야 하는지를 안내해야 한다.

## 불변 계약과 금지 범위

다음은 어떤 UX 개선보다 우선한다.

- 상태 진실은 `WORKFLOW_STATE.json` 하나이며 UI는 이를 절대 쓰지 않는다.
- Markdown·화면 문구·stdout 성공 문구로 planning/execution/closure 가능 여부를 결정하지 않는다.
- 실행 뒤에는 lock 보유 중 State를 다시 읽고, 그 뒤 lock을 해제하는 기존 Phase 3~4 흐름을 유지한다.
- `ActionPolicy`, `ClosurePolicy`, `CompatibilityPolicy`, `BoundaryPolicy`의 fail-closed 의미를 화면 편의로 약화하지 않는다.
- `RuntimeSource.InternalDeveloperSdk`가 missing/invalid일 때 `D:\harness-kit`, 환경변수, 기존 external root로 자동 전환하지 않는다. 외부 Kit은 사용자가 고급 설정에서 명시적으로 선택한 경우만 사용한다.
- repository root와 project workspace root는 외부 경로로 계속 분리하며, UI가 workspace·daily 4-file·Registry·lock·log를 Harness workspace 안에 만들지 않는다.
- raw session ID, secret, token, raw stdout/stderr, raw log를 toast·알림함·설정 파일에 저장하거나 표시하지 않는다.
- 자동 resume, `--continue`, 자유 형식 PowerShell, Claude API 직접 호출, 새로운 Harness wrapper/상태 코드를 추가하지 않는다.
- `D:\harness-kit` 수정·복사, `.local\harness-kit` 자동 생성·복사·update, MSI Runtime staging, 보류 G6A/G6B/7E 구현을 하지 않는다.
- 테스트 삭제·skip·약화, 전면 architecture 재작성, 대규모 포맷 변경을 하지 않는다.

## 구현 범위

### 1. 프로젝트 연결의 즉시 진단과 회복 가능한 안내

`프로젝트 관리` 화면의 표준 등록 form과 `프로젝트 등록` modal에서 다음을 구현한다.

- 기본 source가 `개발용 내장 SDK`일 때 `진단 후 등록`을 누르면, 버튼 내부와 modal 내부에 **반드시** running → success/failure 상태가 나타나야 한다. 결과를 modal 뒤의 부모 카드에만 남겨 사용자가 놓치게 하면 안 된다.
- `.local\harness-kit` missing/invalid, entrypoint 누락, 경계 충돌, 호환성 실패, 미입력 필드, I/O 실패를 typed result 또는 명확한 presentation error로 구분한다. 문자열 일부를 비교해 원인을 추정하지 않는다.
- missing internal SDK일 때 다음을 분명히 보여 준다.
  - 사용자 제공 개발 SDK의 기대 위치와 현재 가용하지 않은 이유
  - 앱이 자동으로 다른 Kit을 선택하지 않았다는 사실
  - `고급 설정`을 열어 명시적 외부 Harness Kit을 선택할 수 있다는 다음 행동
- 실제 내부 SDK가 없어도 등록 실패가 “무반응”이 되어서는 안 된다. active project·Registry·기존 외부 프로젝트를 실패 때문에 지우거나 전환하지 않는다.
- workspace root, repository root, profile, runtime source마다 입력 목적·예시·미설정 사유를 가까운 위치에 제공한다. 경로 선택 UI가 있다면 선택한 문자열을 event로 넘길 뿐, Composable이 파일 검사·경계 판단·디렉터리 생성을 직접 하지 않는다.
- `RuntimeSourceResolverPort`, `RegisterProjectUseCase`, Registry migration과 existing explicit external project의 계약은 유지한다. 이 요구를 만족시키려고 `RuntimeSource`를 raw string 또는 nullable path로 되돌리지 않는다.

### 2. 오늘 시작 정책과 과거 기록의 분리

#### 2.1 먼저 실제 계약을 확인한다

`BootstrapDay`/`HarnessCommand.BootstrapDay`/command encoder와 live Harness `run-cycle.ps1`의 실제 parameter contract를 읽어 다음을 확인한다.

- 오늘 작업을 준비하는 실제 typed command와 필요한 입력
- `WORKFLOW_STATE.json`이 없는 새 오늘 날짜에서 실행할 수 있는지
- 실행 뒤 State를 어떤 방식으로 다시 읽어야 하는지
- planning·replan·execution·closure와 bootstrap의 상호 배타성

이 계약이 현재 코드의 `BootstrapDay` mapping과 다르거나, 새 오늘 날짜에 bootstrap이 허용된다는 근거가 없으면 명령·상태를 창작하지 않는다. 안전한 최소 UI 안내만 구현하고 정확한 blocker를 보고서에 남긴다.

#### 2.2 계약이 확인된 경우의 policy 보정

실제 계약이 확인된 경우에만, 다음 모든 조건에서 `오늘 작업 시작`을 typed `UiAction.BootstrapDay`로 제공한다.

- 선택한 날짜가 오늘이다.
- 활성 프로젝트, resolved runtime, boundary, compatibility가 정상이다.
- 로컬 실행 lock·실행 중 process·외부 변경 차단 상태가 없다.
- 오늘의 State read가 `Missing`인 경우다.
- malformed, unsupported schema, access denied, stale/unknown state, 과거 날짜, runtime missing/invalid, boundary/compatibility failure에서는 **허용하지 않는다**.

정책은 pure function 또는 기존 `ActionPolicy`의 typed context에만 둔다. Composable의 날짜·문구·파일 존재 조건문으로 CTA를 열지 않는다. 실행은 기존 typed command → lock → runner → lock 보유 중 State reread → CTA 재계산 흐름을 그대로 통과한다.

#### 2.3 화면 규칙

- `작업 현황`의 `다음 작업` 카드에 `오늘 작업 시작`을 primary action으로 표시한다. `작업 계획`의 하단 `실행 작업` 카드에는 같은 typed action을 보조로 표시할 수 있으나, 서로 다른 조건·서로 다른 명령이 되면 안 된다.
- 오늘 날짜에 State·계획이 없으면 `작업 계획` 화면은 빈 상태 또는 오늘 시작 안내만 표시한다. 이전 날짜 `TODAY_STRATEGY.md` 원문을 오늘 계획으로 재사용하거나 덮어씌우지 않는다.
- 과거 날짜는 read-only임을 명확히 표시하고, 필요하면 `이전 작업 요약`이라는 별도 카드에 해당 날짜를 표시한다. 현재 날짜의 CTA와 섞지 않는다.
- `요구사항 작성`은 과거 전략 파일의 존재가 아니라 선택된 오늘 날짜의 State와 ActionPolicy가 `EditRequest`를 허용할 때만 활성화한다. 새 오늘을 bootstrap한 뒤 State가 요구사항 입력을 허용하면 즉시 갱신한다.

### 3. 작업 계획의 읽기 경험과 언어 경계

- 사람이 읽는 `TODAY_STRATEGY.md`는 presentation 계층에서 안전하게 Markdown 렌더링한다. 제목, 목록, 강조, 인라인 코드, 코드 블록, 인용, 링크 등 실제 사용 범위를 확인해 읽기 좋은 제한된 renderer 또는 검증된 라이브러리를 사용한다.
- HTML/webview 실행, 원격 resource loading, Markdown의 파일 write/명령 실행은 허용하지 않는다. 원문은 read-only이며 UI가 수정·번역본 저장·정규화하지 않는다.
- 현재 날짜의 문서와 과거 날짜의 문서를 섞지 않는다. 문서 source date와 읽기 전용 여부를 카드에서 명확히 보인다.
- 한국어/영어 **정적 UI**, typed workflow status, stop reason, action label, known Harness Markdown heading은 locale catalog/mapper로 번역한다. 파일 원문을 문자열 치환으로 변경하거나 raw status string을 UI 곳곳에서 번역하지 않는다.
- 임의의 영어 prose가 들어 있는 `TODAY_STRATEGY.md` 전체를 외부 번역 API·LLM 없이 자동 번역했다고 주장하지 않는다. 그런 기능은 이 Phase 범위가 아니다. 원문은 안전하게 렌더링하고 `원문`임을 명확히 하며, 알려진 heading·typed metadata만 현지화한다. 이 한계는 보고서에 남긴다.
- `요구사항 작성` 카드에는 `REQUEST_INBOX.md` 파일명을 주 설명으로 쓰지 않는다. 예를 들어 “오늘 해결할 문제, 기대 결과, 제약 조건을 적어 주세요”처럼 입력 목적을 설명한다. 파일명은 필요할 때 보조 정보로만 둔다.

### 4. 상태·오류·알림의 공통 feedback 시스템

#### 4.1 버튼과 작업 결과

- 주요 CTA와 modal action은 hover에서 hand cursor와 일관된 색/outline 변화를 제공한다. keyboard focus, disabled contrast, tooltip/accessibility semantics를 훼손하지 않는다.
- `진단 후 등록`, 요구사항 저장, 연결 점검, 작업 준비 점검, 오늘 작업 시작, planning/replan, 실행, closure validation은 실행 중 spinner/progress와 중복 클릭 차단을 갖는다.
- process 결과와 State reread 결과를 분리한다. stdout 문구나 클릭만으로 success toast를 만들면 안 된다.
- success/failure/blocked/cancelled를 typed outcome으로 표현하고, 실패에는 이해 가능한 한국어/영어 요약과 안전한 다음 행동을 보인다. 세부 기술 정보는 민감정보 마스킹 후 사용자가 명시적으로 펼칠 때만 제공한다.

#### 4.2 전역 알림함

- 앱 우측 상단에 작은 알림 버튼·badge와 transient notification host를 둔다. 작업 결과가 발생하면 짧은 slide/fade 카드가 나타났다가 사라지고, 최근 비민감 결과는 알림함에서 다시 확인할 수 있게 한다.
- 알림 model/reducer는 presentation 계층의 단일 `StateFlow`에 둔다. `UiAction` ID와 표시 label을 분리하며, Composable마다 독립 toast state를 만들지 않는다.
- 새 알림은 자동 만료·명시적 dismiss를 지원하고, raw process output·secret·session ID·경로 전체를 저장하지 않는다. 알림은 ActionPolicy나 ClosurePolicy의 권위를 대체하지 않는다.
- 단순 화면 전환·읽기 전용 선택에 성공 toast를 남발하지 않는다. 등록/저장/점검/실행/마감 검증처럼 사용자가 결과를 기다린 action에만 사용한다.

### 5. 상단 상태·정보 구조·용어 재정비

#### 5.1 상단 활성 프로젝트 리본

- 활성 프로젝트 이름, 선택 날짜, runtime source, 연결·호환성·점검 상태를 간략히 표시하되 상태마다 실제 source를 하나로 통일한다.
- `엔진 오프라인`, `프로필 기본`, `점검 대기`의 색과 문구는 실제 typed result와 일치해야 한다. missing internal SDK를 ready(녹색)로 보이게 하거나, neutral profile 이름을 경고/성공 상태처럼 표현하지 않는다.
- `READ-ONLY 앱이 소유하지 않음`처럼 행동을 안내하지 않는 중복 문구는 기본 navigation/리본에서 제거하거나 상세 설명으로 이동한다. 과거 날짜 read-only처럼 실제 제약은 필요한 화면의 행동 가까이에서 설명한다.

#### 5.2 technical 패널

- `META`, `환경`, `아티팩트`처럼 서로 다른 언어·모호한 제목을 섞지 않는다. compact technical panel은 하나의 표기 규칙으로 통일한다. 권장 기본은 `STATUS`, `ENVIRONMENT`, `ARTIFACTS`이며, 카드 안의 설명과 사용자 행동은 선택 locale의 자연스러운 문장으로 제공한다.
- `ARTIFACTS`와 동일 의미의 한국어 제목을 한 화면에 중복 노출하지 않는다. required/optional/legacy 구분과 4-file 계약은 상세 정보에서 정확히 유지한다.
- `환경 점검`과 `작업 기준 점검`은 Harness command ID를 바꾸지 않고 목적 기반 표시명으로 교체한다.
  - `RunDoctor`: `연결 점검` — “Harness Kit과 프로젝트 연결이 실행 가능한지 확인합니다.”
  - `RunOpsValidation`: `작업 준비 점검` — “오늘 작업을 시작하기 위한 상태와 기준 파일을 확인합니다.”
- 외부 script의 자유 형식 영문 stderr는 기계 번역하지 않는다. known typed failure는 locale mapper로 번역하고, unstructured detail은 원문·마스킹·접힘 상태로 유지한다.

### 6. 날짜 탐색과 미설정 항목의 행동 가능성

- `작업 날짜` 카드에는 한 페이지에 최대 5개 날짜만 보이고, 이전/다음 탐색을 제공한다. 현재 선택 날짜는 고대비·명확한 `선택됨` 상태로 보여야 하며 단순 흐린 색에 의존하지 않는다.
- 오늘 날짜가 아직 daily directory 목록에 없다면 오늘을 선택/안내할 수 있어야 한다. UI가 날짜 폴더·daily 4-file을 직접 만들지 않는다.
- 과거 날짜 write/execute 차단은 그대로 유지한다.
- `미설정` 상태에는 단순 red label만 두지 말고, 무엇이 없고 어느 화면에서 어떤 값을 지정해야 하는지 한국어/영어 안내와 해당 modal로 이동하는 action을 제공한다. workspace·repository를 자동 생성하거나 임의 경로를 제안하지 않는다.

### 7. 한국어/English 전환과 글꼴

- 우측 상단 또는 설정에 명확한 `한국어 / English` selector를 제공한다. 기본 locale, 선택 즉시 적용, 다음 실행에도 유지되는지 정의한다.
- locale은 UI 소유 설정으로만 저장한다. `WORKFLOW_STATE.json`, Harness workspace, project Registry에 언어 설정을 섞지 않는다. persistence가 필요하면 작은 `UiPreferencesPort`와 infra adapter를 두고 `%APPDATA%\hrns-now` 아래에 atomic UTF-8 no-BOM으로 저장한다. AppViewModel/Composable이 파일을 직접 읽거나 쓰지 않는다.
- 새 설정이 Registry/Runtime source/lock과 결합된 God settings service가 되지 않게 한다. locale 저장·복원만 책임으로 제한한다.
- 일반 UI 영문은 Windows 우선의 자연스러운 sans-serif와 한글 fallback 조합으로 통일한다. 현재 Pretendard 자산·tracking·고정폭 사용을 먼저 감사한다. 필요한 경우 Windows의 `Segoe UI Variable` 또는 `Segoe UI`를 우선 후보로 두고 Pretendard fallback을 명시한다.
- 원격 폰트 download, 라이선스 불명 글꼴 추가, 일반 UI 전체의 `Monospace` 사용은 금지한다. 코드·경로·명령처럼 정렬이 필요한 값에만 고정폭 family를 사용한다.
- 화면 제목의 과도한 negative letter spacing, 같은 의미의 subtitle·badge·helper text 반복을 제거한다. 정보가 빠지는 대신 어떤 행동을 해야 하는지 불분명해지면 안 된다.

## 설계 원칙

- `core`는 Compose, AWT/font, filesystem, JSON, PowerShell에 의존하지 않는다.
- locale policy, today-start eligibility, notification model은 필요한 곳에만 작은 typed model/순수 policy/reducer를 둔다. 모든 기능에 interface/factory를 추가하지 않는다.
- Composable은 input event와 state render만 한다. file I/O, path probing, state parsing, command mapping, process 실행, atomic write, locale file persistence를 직접 하지 않는다.
- `AppViewModel`은 event → use case → `StateFlow<HrnsUiState>` 조립을 담당하며, markdown parser·font resolver·registry serializer·PowerShell 결과 해석을 God ViewModel로 흡수하지 않는다.
- `UiAction` ID, action label, notification text, localization key를 서로 분리한다.
- status/stop reason/unknown enum은 raw string 분기를 늘리지 말고 existing typed mapper와 `Unknown(raw)` fail-closed 계약을 보존한다.
- default registration의 `RuntimeResolution.Missing`을 “예상된 정상”으로 바꾸거나 demo fallback으로 등록 성공을 꾸미지 않는다.

## 테스트와 검증

테스트 삭제·skip·약화 없이 아래를 추가·보강한다.

1. **core policy**
   - 실제 Harness bootstrap contract가 확인된 경우, current-day + valid project/runtime/boundary/compatibility + State Missing + unlocked에서만 `BootstrapDay`가 허용되는 결정표 test
   - malformed/unknown/stale/access denied/unsupported schema/past date/lock/runtime missing/boundary·compatibility failure가 fail-closed인 회귀 test
   - bootstrap 뒤 State reread, typed command mapping, ActionPolicy가 Markdown 존재로 `EditRequest`를 허용하지 않는 기존 계약 회귀

2. **registration·resolver·infra**
   - internal SDK Missing/Invalid/Resolved가 modal에 전달할 구분된 진단·다음 행동으로 표현되는지
   - explicit external source와 legacy Registry migration이 보존되며 missing internal SDK로 자동 전환되지 않는지
   - locale preference persistence를 추가했다면 atomic write, UTF-8 no BOM, corruption recovery, secret·session/raw log 미포함, 한글 값 round-trip test

3. **composeApp/ViewModel/presentation**
   - registration running/success/failure와 modal 안의 오류·다음 행동
   - 오늘 계획 없음/과거 전략 있음에서 오늘 화면에 과거 strategy가 렌더되지 않는지
   - today-start CTA의 위치·enablement와 requirements enablement가 State/ActionPolicy만 따르는지
   - 날짜 5개 paging, selection visibility, past read-only
   - notification enqueue/dismiss/expiry와 action label·ID 분리, raw process detail 미포함
   - Korean/English static label·known typed error mapping·locale persistence 및 fallback
   - safe Markdown renderer의 heading/list/code/link 및 raw HTML 비실행

4. **manual native UI QA**
   - `./gradlew.bat :composeApp:run`으로 실제 창을 열어 internal SDK missing 등록, explicit external Kit 등록, language switch, 날짜 paging, today-start 안내, 요구사항 CTA, hover/running/result notification, Markdown card를 육안 확인한다.
   - 실제 Harness 실행은 확인된 typed CTA, 허용된 workspace, 무해한 fixture/승인된 흐름에서만 한다. 외부 API·유료 모델 호출이나 source 수정으로 성공을 꾸미지 않는다.

다음 순서로 실행한다.

```powershell
.\gradlew.bat :core:test
.\gradlew.bat :infra:test
.\gradlew.bat :composeApp:jvmTest
.\gradlew.bat check
```

글꼴 resource, Compose resource, package configuration을 변경했다면 다음도 실행한다.

```powershell
.\gradlew.bat :composeApp:packageReleaseMsi --rerun-tasks
.\gradlew.bat :composeApp:createReleaseDistributable --rerun-tasks
```

그렇지 않은 packaging 미실행 사유는 보고서에 정확히 적는다. MSI·build output·log·Registry·workspace·`.local\harness-kit`은 Git에 추가하지 않는다.

## 완료 보고서

`doc/phase_reports/phase8-workflow-clarity-report.md`에 다음을 반드시 기록한다.

- 시작 HEAD, 작업 트리 상태, 사용자 QA 항목과 실제 재현·원인
- 변경 파일과 등록 feedback, today-start policy, strategy date separation, notification, date pagination, locale, typography/용어의 구현 근거
- 실제 Harness bootstrap contract 확인 결과와, 확인 불가 시 구현하지 않은 범위 및 blocker
- Markdown 원문 렌더링·known heading/status 현지화와 자유 형식 prose 자동 번역을 구현하지 않은 이유
- `WORKFLOW_STATE.json` 소유권, Action/Closure/Compatibility/Boundary policy, typed command·lock·State reread, RuntimeSource external override 규칙을 보존한 근거
- SOLID, MVVM/UDF, ports/adapters, notification reducer, locale preference persistence의 책임 분리 평가
- targeted/module/full/package/manual QA 각각의 실제 명령·결과·미실행 사유
- `D:\harness-kit`, `.local\harness-kit`, `doc/hrns_now_packaging_plan.md`, 보류 G6A/G6B/7E를 수정하지 않은 사실
- Git 작업을 수행하지 않은 사실, residual risk, Codex 독립 검증 필요

완료 보고서에서 Phase 8 PASS, G6-UX PASS, G6A/G6B/7E PASS, release readiness를 선언하지 않는다. 해당 판정과 Git commit은 Codex만 수행한다.
