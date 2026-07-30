# Phase 10 Native Onboarding Interaction QA Gate

## 역할과 범위

너는 `hrns_now`의 구현 담당자다. 이번 작업은 새 기능을 구현하는 Phase 11이 아니다. 현재 Phase 10의 실제 Compose Desktop 상호작용을 사용자가 확인할 수 있게 준비하고, 사용자가 제공하는 재현 결과만 사실대로 기록하는 **native QA Gate**다.

현재 기준 커밋은 다음과 같다.

```text
1445ca4 fix: Phase 10 프로젝트 준비와 활성 표시 보정
```

이 커밋은 `d54fbef`의 온보딩 무결성 구현을 포함하며, 다음 Codex 보정을 추가한다.

- 신규 등록의 실제 Harness profile 기본값: `corp-default` (`기본`은 표시 문구이지 profile ID가 아님)
- `HrnsUiState`가 Registry에서 해석한 활성 프로젝트명을 직접 보존하여, State/Registry projection 갱신 중에도 상단 리본과 프로젝트 요약이 `NONE`으로 떨어지지 않음
- 실제 외부 프로젝트에서 `enter-project.ps1`과 `validate-ops.ps1 -Json`으로 bridge 3-file·오늘 required 4-file·`overall=ok`을 확인함

기존 온보딩 무결성 보정은 다음을 포함한다.

- `HarnessCommand.OnboardProject` → 실제 `scripts/enter-project.ps1` typed argument mapping
- `enter-project → validate-ops -Json → bridge 3-file probe → daily 4-file probe → State reread`의 단일 lock lifecycle
- 등록 직후 `run-cycle`/`BootstrapDay` 자동 실행 제거
- 기존 프로젝트의 명시적 `프로젝트 준비` CTA
- lock callback 예외에도 lock을 해제하는 Codex 보정
- 빠른 연속 `프로젝트 준비` 클릭을 한 번만 수락하는 Codex 보정

`D:\harness-kit`은 수정·복사·백업하지 않는다. `doc/QA_captures/`, `doc/hrns_now_packaging_plan.md`, `doc/user_workflow_qa_notes.md`는 사용자 소유 자료이므로 읽기만 하고 수정·삭제·stage하지 않는다. Git 명령(`add`, `commit`, `amend`, `reset`, `stash`, `rebase`, `clean`, `push`)은 수행하지 않는다.

## 먼저 읽을 자료

- `README.md`
- `doc/hrns_now_claude_plan.md`
- `doc/hrns_now_design_pattern.md`
- `doc/phase_reports/phase9-desktop-layout-and-onboarding-report.md`
- `doc/phase_reports/phase10-project-onboarding-integrity-report.md`
- `doc/claude_prompts/phase10-project-onboarding-integrity.md`
- `core/.../OnboardProjectUseCase.kt`
- `composeApp/.../AppViewModel.kt`
- `composeApp/.../Screens.kt`

시작 시 현재 branch, HEAD, `git status --short`를 읽기 전용으로 기록한다. 현재 source와 `d54fbef`가 다르면 현재 source를 우선하고 차이를 보고한다.

## Gate 목적

코드 테스트가 통과했다는 이유만으로 native UI 상호작용을 PASS 처리하지 않는다. 최신 빌드를 시작한 뒤, 아래 항목은 **사용자가 실제로 클릭하고 관찰한 결과 또는 사용자가 제공한 캡처**로만 판정한다.

사용자에게 최신 코드가 반영된 새 프로세스를 시작하도록 안내할 수 있다.

```powershell
Set-Location -LiteralPath 'S:\dev\project\hrns_now'
.\gradlew.bat :composeApp:run
```

기존 실행 중인 앱은 최신 `1445ca4` 이전 build일 수 있으므로, 사용자 확인 전에 종료·재시작 여부를 분명히 알린다. 사용자의 명시적 요청 없이 마우스/키보드 합성 입력으로 UI를 조작하지 않는다.

## Profile 실행 계약 확인

신규 등록 form의 기본 입력값은 반드시 `corp-default`여야 한다. 화면에서 보이는 한국어 설명을 profile ID로 저장하거나 `기본.yaml`을 가정하지 않는다. 사용자가 직접 다른 profile ID를 입력한 경우에는 실제 Kit의 `profiles/<id>.yaml` 존재 여부를 결과와 함께 확인하며, 임의 값으로 성공 처리하지 않는다.
## 사용자 확인 체크리스트

사용자는 외부 Harness Kit, repository, workspace를 서로 포함하지 않는 테스트 경로로 지정한다. 실제 사용자 프로젝트나 `D:\harness-kit`을 초기화·삭제하지 않는다. 공백과 한글 경로를 한 번 포함해 확인한다.

1. 상단 활성 프로젝트 리본
   - 활성 Registry 프로젝트가 있는데 State `project_name`이 비어 있어도 `NONE`이 아니라 Registry 표시명이 나타나는지 확인한다.
   - 프로젝트가 진짜 없을 때만 `NONE`이 표시되는지 확인한다.

2. 프로젝트 등록 확인 다이얼로그
   - `프로젝트 관리`의 주 등록 행동을 누르면, 실제 쓰기 전에 확인 다이얼로그가 나타나는지 확인한다.
   - 다이얼로그가 다음 3개 repository bridge와 외부 workspace 경로를 정확히 고지하는지 확인한다.

```text
.claude/settings.local.json
.claude/CLAUDE.md
tools/run-cycle.ps1
```

   - 기존 bridge는 덮어쓰지 않는다는 설명이 보이는지 확인한다.
   - 취소 시 어떤 파일도 생성되지 않는지 확인한다.

3. 등록 전용과 등록+프로젝트 준비 구분
   - `등록만`은 Doctor/Registry만 수행하고 repository bridge, workspace daily 4-file, `run-cycle`을 실행하지 않는지 확인한다.
   - 주 행동(등록+프로젝트 준비)은 Health Check와 구별되는 진행 상태를 보이고, 성공/차단 결과가 raw PowerShell output 없이 이해 가능한 한국어 또는 선택한 영어로 나타나는지 확인한다.
   - 등록 자체가 성공했지만 준비가 차단되면 Registry entry를 지우지 않고, 재시도 가능한 상태로 남는지 확인한다.

4. 실제 온보딩 산출물
   - 주 행동을 확인한 뒤 repository에는 bridge 3-file만 생기는지 확인한다.
   - `REQUEST_INBOX.md`, `TODAY_STRATEGY.md`, `DAILY_HANDOFF.md`, `WORKFLOW_STATE.json`은 외부 workspace의 오늘 날짜 폴더에만 생기는지 확인한다.
   - `WORKFLOW_STATE.json`을 UI가 직접 쓰지 않고 `enter-project.ps1` 결과로 다시 읽는지 확인한다.
   - 등록 직후 앱이 `run-cycle.ps1`/`BootstrapDay`를 자동 실행하지 않는지 확인한다.

5. 기존 활성 프로젝트 복구 CTA
   - bridge 또는 오늘 workspace가 없는 기존 활성 프로젝트에서만 `프로젝트 준비`가 보이는지 확인한다.
   - Health Check를 누른 것만으로 bridge가 생성되지 않는지 확인한다.
   - `프로젝트 준비`을 빠르게 두 번 눌러도 진행 표시가 하나만 유지되고, 두 개의 실행이 동시에 시작되지 않는지 확인한다.
   - 완료 뒤 bridge·4-file·ops validation·State 중 하나라도 확인되지 않으면 성공처럼 표시되지 않는지 확인한다.

6. 다음 행동과 안전성
   - 성공 뒤 State가 `request_intake_pending`이면 요구사항 작성 CTA가 자연스럽게 갱신되는지 확인한다.
   - malformed/unknown State, 과거 날짜, runtime/compatibility/boundary 실패에서는 실행 CTA가 fail-closed인지 확인한다.
   - raw session ID, token, secret, raw log가 화면·Registry에 보이지 않는지 확인한다.

## Claude가 수행할 일

- 사용자가 준 관찰 결과, 캡처, 재현 경로를 현재 source와 대조한다.
- 실패가 명확하면 재현 근거와 Phase 10 범위 여부를 구분해 보고한다.
- 사용자가 명시적으로 새 결함 수정을 지시하기 전에는 임의의 UI/정책/패키징 기능을 구현하지 않는다.
- `D:\harness-kit` 문서 drift나 discovery/promotion의 선택적 운영 단계를 HRNS-NOW 코드로 억지 구현하지 않는다.
- 자동화 테스트가 native 클릭 증거를 대체한다고 주장하지 않는다.

사용자가 결과를 제공한 뒤에는 UTF-8 without BOM으로 다음 보고서를 작성한다.

```text
doc/phase_reports/phase10-native-onboarding-qa-gate-report.md
```

보고서에는 테스트 build SHA, 사용자 관찰/캡처, 각 항목 PASS/FAIL/미실행, 실제 산출물 위치 경계, 발견 결함의 Phase 범위, Git을 수행하지 않았다는 사실을 포함한다.

마지막 상태는 다음 둘 중 하나만 사용한다.

```text
PHASE_10_NATIVE_QA_STATUS: READY_FOR_CODEX_REVIEW
NEXT_ALLOWED_PHASE: Codex independent verification
```

또는 사용자 native QA가 아직 제공되지 않았으면:

```text
PHASE_10_NATIVE_QA_STATUS: BLOCKED
NEXT_ALLOWED_PHASE: user native onboarding interaction QA
```

이 Gate가 Codex와 사용자 검증으로 통과하기 전에는 Phase 11 기능을 시작하지 않는다.
