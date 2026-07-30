# Phase 10 — 프로젝트 온보딩 무결성: Bridge · Workspace · 첫 요구사항 흐름

## 역할과 범위

당신은 `hrns_now`의 구현 담당자다. 이번 작업은 수동 QA와 Codex의 Harness 계약 재검증에서 확인된 **프로젝트 온보딩 결함**을 해결하는 Phase 10이다.

현재 구현의 문제는 단순한 문구 문제가 아니다.

```text
현재: Doctor → Registry 저장 → run-cycle Bootstrap
정상: Kit Doctor → Registry 저장/활성화 → enter-project → validate-ops → State 재조회
```

`D:\harness-kit\scripts\enter-project.ps1`만 repository bridge와 external workspace를 함께 준비한다. `doctor.ps1`은 읽기 전용이고, `run-cycle.ps1`은 workspace를 초기화할 수 있어도 repository bridge를 만들지 않는다. 따라서 **Health Check를 쓰기 동작으로 바꾸지 말고**, 명시적인 프로젝트 온보딩 동작을 추가해야 한다.

Git 작업은 절대 하지 않는다. `commit`, `amend`, `rebase`, `reset`, `stash`, `clean`, `push`를 수행하지 않는다. 구현·테스트·보고서만 작성하고 커밋은 Codex가 한다.

`D:\harness-kit`은 읽기·실행 검증 대상으로만 사용한다. 수정·복사·백업·MSI 포함을 금지한다. `.local\harness-kit`을 자동 생성하거나 `D:\harness-kit`에서 자동 복사하지 않는다. 기존 Phase 6A/6B, 배포 Runtime, MSI, Phase 7의 Runtime source 설계는 이번 범위 밖이다.

## 시작 전 필수 확인

다음 파일을 전체 읽고, 보고서·과거 프롬프트의 완료 선언보다 현재 소스와 live Harness 계약을 우선한다.

- `README.md`
- `doc/hrns_now_claude_plan.md`
- `doc/hrns_now_design_pattern.md`
- `doc/phase_reports/phase7-internal-sdk-report.md`
- `doc/phase_reports/phase9-desktop-layout-and-onboarding-report.md`
- `doc/claude_prompts/phase9-desktop-layout-and-onboarding.md`
- `core/src/main/kotlin/io/hrns_now/core/domain/model/HarnessCommand.kt`
- `core/src/main/kotlin/io/hrns_now/core/domain/policy/ActionPolicy.kt`
- `core/src/main/kotlin/io/hrns_now/core/usecase/ExecuteHarnessActionUseCase.kt`
- `infra/src/main/kotlin/io/hrns_now/infra/process/HarnessCommandEncoder.kt`
- `composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/viewmodel/AppViewModel.kt`
- `composeApp/src/jvmMain/kotlin/io/hrns_now/app/ui/Screens.kt`
- `composeApp/src/jvmMain/kotlin/io/hrns_now/app/ui/Shell.kt`

Harness 계약은 아래 문서와 스크립트를 실제로 읽어 확인한다.

- `D:\harness-kit\docs\PROJECT_ONBOARDING.md`
- `D:\harness-kit\docs\WORKSPACE_SPEC.md`
- `D:\harness-kit\docs\OPERATING_GUIDE.md`
- `D:\harness-kit\docs\STATE_MODEL.md`
- `D:\harness-kit\scripts\enter-project.ps1`
- `D:\harness-kit\scripts\init-workspace.ps1`
- `D:\harness-kit\scripts\doctor.ps1`
- `D:\harness-kit\scripts\validate-ops.ps1`
- `D:\harness-kit\scripts\run-cycle.ps1`

시작 상태를 기록한다.

```powershell
Set-Location -LiteralPath 'S:\dev\project\hrns_now'
git status --short
git branch --show-current
git log -10 --oneline --decorate
```

`doc/QA_captures/`, `doc/hrns_now_packaging_plan.md`, `doc/user_workflow_qa_notes.md`는 사용자 소유 자료일 수 있다. 읽기만 하고 수정·삭제·stage하지 않는다.

## Codex가 확보한 live Harness 근거

아래 사실을 다시 실소스로 확인하되, 다른 wrapper·상태 코드·파일을 창작하지 않는다.

1. `enter-project.ps1`은 기본 `-Force` 없이 다음 repository-local bridge만 생성하거나 기존 파일을 보존한다.

```text
.claude/settings.local.json
.claude/CLAUDE.md
tools/run-cycle.ps1
```

2. 같은 `enter-project.ps1`은 `init-workspace.ps1`을 호출해 external workspace root, `memory/`, `logs/`, 오늘 day root, 그리고 required daily 4-file을 준비한다.
3. `doctor.ps1`은 bridge가 없으면 warning을 낼 뿐 파일을 만들지 않는다. `-Json`은 stdout JSON 계약이며 side effect가 없다.
4. `validate-ops.ps1 -Json`은 workspace daily surface와 `WORKFLOW_STATE.json`을 검증하며 side effect가 없다.
5. `run-cycle.ps1`은 missing workspace를 초기화하지만 bridge를 만들지 않는다. 신규 등록 직후 wrapper 없는 Bootstrap을 호출하면 첫 State가 `execution_completed`가 되어 요청 입력 흐름을 막을 수 있으므로, 등록 직후 자동 Bootstrap으로 사용하면 안 된다.
6. `enter-project` 직후 init-workspace가 만든 State의 정상 시작점은 `request_intake_pending`이다. 이 값은 UI가 새로 쓰거나 억지로 만들 값이 아니라 Harness가 쓴 State를 Reader가 해석한 결과여야 한다.

## 확정 UX·상태 전이

### 1. 역할을 분리한다

- **환경 점검(Health Check / Doctor):** 읽기 전용이다. bridge·workspace를 생성하지 않는다.
- **프로젝트 준비:** 사용자의 명시적 쓰기 행동이다. repository bridge와 external workspace를 만들 수 있음을 사전에 알리고 `enter-project.ps1`을 실행한다.
- **오늘 작업 시작(Bootstrap/run-cycle):** 온보딩 이후의 일일 workflow 행동이다. 신규 등록 직후 자동 실행하지 않는다.

Health Check 성공 또는 warning을 bridge 생성 성공으로 표시하거나, Health Check 버튼을 조용히 write command로 바꾸는 것은 실패다.

### 2. 신규 등록 primary flow

기존의 `진단·등록 및 오늘 작업공간 준비` primary 행동은 `진단·등록 및 프로젝트 준비`처럼 실제 효과가 드러나는 이름으로 바꾼다. 등록만 원하는 경우는 보조 행동으로 유지한다.

primary flow:

```text
candidate 검사(runtime 해석 + boundary)
→ Kit-only Doctor
→ compatibility 확인
→ Registry 저장 + 활성 project 선택
→ 명시적 onboarding 확인
→ enter-project
→ validate-ops -Json
→ bridge probe + required 4-file probe + WORKFLOW_STATE 재조회
→ 요구사항 작성 가능 상태 표시
```

- 등록 전 Doctor는 Kit 자체만 점검한다. 아직 생성되지 않은 candidate workspace/repository bridge 경로를 Doctor에 넘겨 정상적인 미생성 상태를 오류처럼 만들지 않는다.
- Registry 저장 성공 뒤 onboarding이 실패해도 등록을 rollback·삭제하지 않는다. `등록됨 / 프로젝트 준비 실패`를 분리하고 재시도 CTA를 제공한다.
- `등록만`은 Registry만 저장하고 어떤 Harness mutating command도 실행하지 않는다.
- primary 행동은 repository와 workspace에 생성될 항목을 보여 주는 확인 UI를 거친다. 최소한 bridge 3종, external workspace path, “기존 bridge는 덮어쓰지 않음”을 명시한다.
- 이미 등록된 활성 프로젝트인데 bridge 또는 오늘 workspace 준비가 누락됐으면 프로젝트 관리 화면에서 단일 `프로젝트 준비` CTA를 보인다. 별도 등록을 강요하지 않는다.
- 모든 사용자 문구와 오류 안내는 한국어/영어 locale 계약을 지키며 raw PowerShell 출력·secret·session ID를 표시하지 않는다.

### 3. 성공 판단과 재조회

프로젝트 준비 성공은 stdout 성공 문구 하나로 판단하면 안 된다. 최소 다음 증거의 교집합으로만 준비됨 projection을 만든다.

1. `enter-project.ps1` 프로세스가 정상 종료함
2. `validate-ops.ps1 -Json` 결과가 `overall=ok`
3. 실제 repository bridge 3종 probe가 모두 준비됨
4. 실제 external workspace의 required 4-file probe가 모두 준비됨
5. `WORKFLOW_STATE.json` 재조회가 `StateReadResult.Success`

프로세스 실행·검증·State reread는 동일한 per-machine lock lifecycle 안에서 수행한다. lock은 State reread가 끝난 뒤에만 해제한다. timeout/cancel/heartbeat/late-write guard/secret masking은 기존 Process Adapter 계약을 유지한다.

## 구현 설계 제약

### Typed command와 use case

- `HarnessCommand`에 onboarding 전용 typed variant와 `HarnessCommandKind`를 추가한다. 이름은 live contract를 반영해 `OnboardProject` 또는 동등하게 명확한 이름을 사용한다.
- `HarnessCommandEncoder`는 `scripts/enter-project.ps1`에 아래 인자만 argument list로 전달한다.

```text
-ProjectRoot <repositoryRoot>
-WorkspaceRoot <projectWorkspaceRoot>
-KitRoot <resolvedKitRoot>
-Profile <profile>
-Date <yyyy-MM-dd>
```

- `-Force`, `-RunDoctor`, `-MaterializeSubagents`, `-AgentNames`, `--continue`, invent된 wrapper, shell 문자열 조립을 금지한다.
- `run-cycle.ps1` command와 `enter-project.ps1` command를 하나의 boolean/switch로 뭉개지 않는다.
- daily `ActionPolicy`는 `WORKFLOW_STATE.json` 기반 daily CTA 정책으로 유지한다. project onboarding은 하루 State를 임의로 전이시키는 정책 우회가 아니라, 등록/bridge/workspace 준비라는 별도 lifecycle이다.
- `AppViewModel`이 ProcessBuilder, filesystem probe, lock lifecycle, PowerShell argument를 직접 소유하면 안 된다.
- 기존 `ExecuteHarnessActionUseCase`의 policy → typed command → lock → process → lock-held State reread 흐름을 재사용한다. onboarding에 별도 중복 lifecycle이 필요하다면 command 실행 공통부만 작고 명시적인 core use case로 추출하고, daily ActionPolicy 검증과 onboarding precondition을 혼합하지 않는다.
- repository bridge filesystem 확인은 read-only port/adapter와 작은 domain result로 분리한다. Composable 또는 ViewModel에서 `Files.exists`와 path 조합을 하지 않는다. 인터페이스를 아무 근거 없이 늘리지 말되, 이 경우는 외부 repository filesystem 경계를 테스트 가능하게 분리하는 정당한 port다.

### State와 presentation

- UI는 `WORKFLOW_STATE.json` 및 daily 4-file을 직접 만들거나 수정하지 않는다.
- `WorkspacePreparationOutcome`을 새 의미와 맞지 않게 재사용하지 않는다. 필요하면 등록 결과, onboarding 결과, bridge readiness를 분리한 typed result/projection으로 정리한다.
- 성공한 `enter-project` 뒤에는 자동 `BootstrapDay`/`run-cycle`을 호출하지 않는다. State 재조회 후 ActionPolicy가 `EditRequest`를 허용하는 정상 시작 흐름을 보여야 한다.
- 기존 활성 Registry 프로젝트 이름은 State가 아직 없어도 상단 리본에 표시되어야 한다. Codex의 `7d457c3 fix: 활성 프로젝트 리본 표시 보정`을 되돌리지 않는다.
- malformed/unknown State, 미래 schema, past date, compatibility/boundary failure, lock busy, timeout/cancel, 외부 실행 감지는 fail-closed를 유지한다.
- registry는 `%APPDATA%`, lock은 `%LOCALAPPDATA%` 소유이며 repository/workspace에는 UI 소유 Registry·lock·raw log를 만들지 않는다.

## 금지 사항

- `D:\harness-kit` 수정 또는 docs drift를 HRNS-NOW 구현으로 숨기기
- UI에서 `WORKFLOW_STATE.json`/daily 파일/bridge template을 직접 쓰기
- `Doctor` 또는 `validate-ops`를 bridge generator로 취급하기
- 등록 직후 wrapper 없는 `run-cycle` 자동 실행
- stdout text만으로 onboarding 완료 처리
- 외부 Kit 경로를 production source에 하드코딩하거나 internal SDK를 자동 복사
- `REQUEST_STRUCTURED.md`, `WORK_QUEUE.json`, `WORKDAY_STATE.json`, 두 log directory를 default readiness 조건으로 승격
- 테스트 삭제·skip·약화, mock/demo fallback으로 실제 준비 성공 위장
- Phase 6B/기존 배포 Runtime/Phase 7E 구현, MSI 변경, 대규모 UI 재설계

## 필수 테스트

### Core

- onboarding command mapper가 exact typed command와 date/profile/resolved Kit root를 만든다.
- daily `ActionPolicy`와 onboarding precondition이 섞이지 않음을 검증한다.
- onboarding 성공/실패/lock unavailable/cancel·timeout 결과가 typed result로 구분된다.
- validation 또는 concrete probe가 실패하면 onboarding을 Prepared로 판정하지 않는다.

### Infra

- encoder가 공백·한글 경로를 하나의 argument로 보존한다.
- `enter-project.ps1` path 및 허용 인자만 인코딩하고 금지 switch가 없는지 검증한다.
- repository bridge probe는 3개 파일 각각의 missing/ready를 구분하며 filesystem write를 하지 않는다.
- process masking/timeout/cancel/lock 기존 회귀를 유지한다.

### Compose/ViewModel

- 등록 primary flow는 Kit-only Doctor → registry save/select → onboarding → validate-ops → probe/State reread 순서를 보장한다.
- registration-only는 onboarding·run-cycle·validate-ops를 실행하지 않는다.
- 신규 등록 primary flow에서 `BootstrapDay`/`run-cycle`을 호출하지 않는다.
- `enter-project` 성공만으로 완료 처리하지 않고 bridge·4-file·State·validate-ops 결함별로 실패/재시도 projection을 보인다.
- onboarding 후 `request_intake_pending` State가 들어오면 요구사항 작성 CTA가 활성화된다.
- 기존 등록 프로젝트에서 bridge 누락이면 `프로젝트 준비` CTA 하나가 보이며, Health Check와 혼동되지 않는다.
- 진행 중 중복 클릭, lock busy, timeout/cancel, Korean/English localized explanation, notification의 raw output 비노출을 검증한다.

### 격리 integration 검증

실제 `D:\harness-kit`은 수정하지 말고, 필요한 경우 `S:\tmp`의 새 fixture repository와 workspace에서만 수행한다. 유료 모델/API와 Claude 호출은 하지 않는다.

1. 현재 run-cycle bootstrap은 4-file을 만들지만 bridge를 만들지 않는다는 live 계약을 확인한다.
2. `enter-project`은 bridge 3종 + required 4-file을 만들고 `validate-ops -Json`이 `ok`가 되는 것을 확인한다.
3. 임시 fixture는 검증 후 정확한 경로를 확인하고 제거한다. 사용자 workspace/repository는 수정하지 않는다.

실행한다.

```powershell
.\gradlew.bat :core:test
.\gradlew.bat :infra:test
.\gradlew.bat :composeApp:jvmTest
.\gradlew.bat check
```

task가 불확실하면 먼저 `./gradlew.bat tasks`로 확인한다. 테스트 실패를 환경 문제와 코드 문제로 구분하고, 실패를 숨긴 채 보고서를 완료로 쓰지 않는다.

## Harness 문서 drift 기록

Harness는 이번 Phase에서 수정하지 않는다. 다만 보고서에 다음을 정확히 기록한다.

- `scripts/SMOKE_INDEX.md`, 두 `HARNESS_KIT_MAP` 및 실제 smoke 파일은 current `75 / 64 / 11`이다.
- `docs/ROADMAP.md`의 `72 / 61 / 11` current-looking 서술은 stale이다.
- `INSTALL.md`의 과거 dual-file transition ledger는 current 4-file workflow-state-primary 계약과 혼동될 수 있으므로, Harness 소유자에게 historical section 분리/표시를 별도 요청해야 한다.

HRNS-NOW는 이 drift를 고치기 위해 Harness source를 변경하거나 compatibility 정책을 완화하지 않는다.

## 보고서

UTF-8 without BOM으로 다음 보고서를 새로 작성한다.

```text
doc/phase_reports/phase10-project-onboarding-integrity-report.md
```

반드시 포함한다.

1. 시작 HEAD, 변경 파일, 사용자 untracked 자료 보존 여부
2. live Harness 문서·source 계약과 현재 앱 흐름의 차이
3. Doctor / enter-project / validate-ops / run-cycle 책임 분리
4. typed command·port·use case·lock lifecycle 설계 근거
5. registration-only, primary registration, 기존 project repair의 상태 전이
6. 성공·실패 판정의 concrete evidence와 State reread 근거
7. default internal SDK가 없을 때 fail-closed를 유지한 사실
8. 테스트와 격리 integration 결과, 미실행 항목의 이유
9. Harness 문서 drift와 HRNS-NOW 수정 범위 분리
10. Git 작업을 하지 않았다는 사실

마지막에 다음을 명시한다.

```text
PHASE_10_STATUS: READY_FOR_CODEX_REVIEW | BLOCKED
NEXT_ALLOWED_PHASE: Codex independent verification
```

구현·테스트·보고서까지만 수행하고 중단한다. Codex의 독립 검증·필요 시 보정·한글 커밋 전에는 다음 Phase를 시작하지 않는다.
