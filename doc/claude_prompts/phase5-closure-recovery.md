# Claude 작업 지시 — Phase 5: 검증·Closure·복구 센터

## 역할과 범위

당신은 HRNS-NOW Phase 5 구현 담당자다. 이 문서의 범위만 변경하고, 완료 증거를 `doc/phase_reports/phase5-report.md`에 **UTF-8 without BOM**으로 기록한다. Git commit, amend, rebase, push, reset, stash는 절대 수행하지 않는다. Codex만 Git branch에 커밋한다.

이번 목표는 실행 성공과 하루 종료를 엄격히 분리하고, 모든 차단 상태에서 사용자가 기록 보존 여부와 허용된 다음 행동을 이해하도록 만드는 것이다. 상태 정본은 여전히 `WORKFLOW_STATE.json`이며 UI는 이 파일을 절대 쓰지 않는다.

## 시작 기준과 선행 Gate

- repository/branch: `S:\dev\project\hrns_now`, `harness-dev`
- Phase 4 Codex 검증·보정 commit: `12d808d` — `fix: Phase 4 실행 유스케이스와 요청 저장 보정`
- Phase 4 verdict: `PASS_WITH_FIXES`; Phase 5가 다음 허용 Phase다.
- 반드시 전체를 읽을 문서:
  - `doc/hrns_now_claude_plan.md`
  - `doc/hrns_now_design_pattern.md`
  - `doc/phase_reports/phase4-report.md`
- 관련 없는 사용자 파일 `doc/hrns_now_packaging_plan.md`는 읽기·수정·삭제·stage하지 않는다.
- `D:\harness-kit`은 canonical live tree이며 이번 Phase에서 기본적으로 읽기 전용이다. `-ValidateForClosure`의 실제 `run-cycle.ps1` 계약, closure 관련 state field, doctor/validation 결과를 먼저 읽어 확인한다. 계약 변경이 정말 필요하면 수정하지 말고 중단하여 보고서에 근거를 남긴다. `git init` 금지.

## Phase 4에서 Codex가 확정한 계약 — 회귀 금지

1. `core/usecase/ExecuteHarnessActionUseCase.kt`가 ActionPolicy 재검증 → typed command → per-machine lock → runner → **lock 보유 중 State reread** → release를 책임진다. 새 실행을 ViewModel에서 직접 ProcessLockPort/HarnessRunnerPort로 조합하지 않는다.
2. `core/usecase/SaveRequestUseCase.kt`와 `RequestWriterPort`는 `REQUEST_INBOX.md`만 대상으로 한다. `REQUEST_STRUCTURED.md`·`WORKFLOW_STATE.json` 쓰기 금지, UTF-8 no BOM, atomic move, hash/mtime optimistic concurrency, conflict overwrite 금지를 유지한다.
3. 과거 날짜는 요청 write가 read-only다. request conflict에서는 초안을 유지하고 재로드·수동 병합을 안내한다.
4. mutating command는 `HarnessCommand` → `HarnessCommandEncoder`의 argument list로만 PowerShell에 전달한다. shell 문자열 조립, 임의 명령 입력, `--continue`, 자동 resume 금지.
5. execution wrapper는 `code|doc`만 UI dispatch한다. `validation` wrapper를 창작하지 않으며 `RunValidationSlice`는 현재 fail-closed로 연결하지 않는다.
6. stdout/로그는 참고 정보다. 완료/CTA/Closure 판단은 재읽은 `WORKFLOW_STATE.json`과 typed policy가 한다. raw session ID, token, secret, raw log를 저장하거나 표시하지 않는다.
7. `doc/hrns_now_design_pattern.md` §16·§17·§19·§20에 따라 core use case/port와 Compose projection의 의존 방향을 유지한다.

## Phase 5 구현 범위

### 1. Closure checklist와 순수 정책

- `core/domain/policy/ClosurePolicy`와 typed input/output을 만든다. 최소 결과는 설계 문서의 `Allowed`, `Blocked(reasons)`, `RequiresExplicitIncompleteHandoff(items)` 의미를 보존해야 한다.
- closure 조건은 다음을 **State/typed probe**로 평가한다.
  - required daily 4-file의 존재·가독성과 `artifacts_state`
  - `WORKFLOW_STATE.json` parse/compatibility 상태
  - state와 queue 존재
  - `ops_validation.passed`
  - active slice 부재 또는 `resume_from_step_id`의 명확한 정합성
  - handoff placeholder 부재
  - `closure.is_clean_handoff`·`closure.validated`·상위 완료 flag의 정합성
  - UI lock 부재
  - repository의 예상 밖 변경 여부
- 실행 process 성공과 closure 허용을 절대 같은 의미로 취급하지 않는다. 조건 미충족이면 “오늘 종료” action을 비활성화한다.
- `WORKFLOW_STATE.json`의 unknown/malformed/unsupported schema, unknown stop reason, lock active는 fail-closed다.

### 2. 실제 Harness closure 검증 command

- live `D:\harness-kit\scripts\run-cycle.ps1`을 읽어 `-ValidateForClosure`의 실제 인자·출력·exit contract를 확인한다.
- 그 계약이 충분히 확인된 경우에만 existing typed command/mapper/use case 경계를 최소 확장한다. 가짜 `validation` wrapper나 새 State code를 만들지 않는다.
- closure command도 Phase 4의 `ExecuteHarnessActionUseCase` 경계와 lock/state reread 규칙을 재사용한다. 계약이 불명확하거나 Harness 수정이 필요하면 억지 구현하지 말고 BLOCKED 근거를 남긴다.

### 3. Recovery Center와 read-only 진단

- Recovery Center는 stop reason·queue blocked marker마다 다음 세 가지를 분리해 projection한다: 발생한 일 / 보존된 기록 / 현재 허용 행동.
- 최소 대상: `usage_limit_blocked`, `claude_context_limit`, `transient_claude_overloaded`, `claude_call_timeout`, `manual_prerequisite_required`, `dispatch_contract_mismatch`, `role_sliced_wrapper_exception`, `dispatch_metadata_conflict`, invalid JSON, validation failure.
- `dispatch_metadata_conflict`는 재계획 경로로, usage limit은 수동 재시도·명시적 resume 검토로 안내한다. 자동 resume·자동 수정·자동 실행은 금지한다.
- continuity doctor, usage ledger, failure history, 마지막 정상 State, compatibility, lock은 읽기 전용으로만 보여 준다. raw session ID/token/secret/raw log는 masking/요약 이전에 화면·Registry에 넣지 않는다.

### 4. Repository 상태 점검

- closure 전 repository 상태는 read-only `git status --short`로 확인하고 경고 projection만 만든다.
- UI가 자동으로 add/commit/reset/checkout/stash/파일 수정하지 않는다. Git port/adapter가 필요하면 read-only 최소 interface로 둔다.

## 금지사항

- Phase 6 MSI, Phase 7 실험 기능, Harness script 변경 선구현
- Harness state/queue/markdown 직접 write, stdout 성공 문구만으로 closure 판정
- hidden automatic closure, automatic reset/commit, raw session/secret 표시
- 기존 Phase 0–4 테스트 삭제·skip·계약 완화, demo fallback으로 live 실패 은폐
- Git 작업 전부

## 설계·테스트 필수 조건

- core: ClosurePolicy decision table을 parameterized test로 고정한다. happy path뿐 아니라 malformed, unknown, lock, missing daily file, failed ops validation, active slice/resume mismatch, placeholder, dirty repository를 검증한다.
- infra: 실제 `-ValidateForClosure` argument contract가 확인되면 encoder/adapter test, UTF-8·한글/공백 경로, command failure/cancel/lock release를 검증한다. Git status adapter는 read-only contract test를 둔다.
- composeApp: `StateFlow<HrnsUiState>` 단일 흐름, UI thread I/O 금지, Closure와 execution 성공을 분리한 enabled/disabled CTA, stop reason별 Recovery projection을 검증한다.
- Phase 4의 action policy revalidation, lock-held State reread, request write protection, validation-only fail-closed를 회귀한다.

실제 task를 확인한 뒤 최소 다음을 실행한다.

```powershell
.\gradlew.bat :core:test
.\gradlew.bat :infra:test
.\gradlew.bat :composeApp:jvmTest
.\gradlew.bat check
```

## 완료 보고서

`doc/phase_reports/phase5-report.md`에 UTF-8 without BOM으로 다음을 남긴다.

- live Harness의 closure contract와 실제 command 근거
- closure policy decision table 및 fail-closed 근거
- execution success와 closure decision을 분리한 증거
- recovery 상태별 사용자 안내와 raw session/secret 비표시 근거
- repository status read-only 보장
- 테스트·fixture·Gradle 결과와 미실행 사유
- Harness 변경 여부와 필요 시 zip backup 경로·시각·크기
- Claude가 Git 작업을 하지 않았다는 명시

Codex가 report, diff, live Harness 계약, `doc/hrns_now_design_pattern.md`, 테스트를 독립 검증한 뒤에만 commit과 Phase 6 진입을 결정한다.
