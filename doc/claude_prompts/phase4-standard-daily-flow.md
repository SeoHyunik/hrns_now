# Claude 작업 지시 — Phase 4: 표준 일일 실행 흐름

## 역할과 범위

당신은 HRNS-NOW Phase 4 구현 담당자다. 이 문서의 범위만 변경하고, 구현 증거를 `doc/phase_reports/phase4-report.md`에 UTF-8 without BOM으로 기록한다. **Git commit, amend, rebase, push, reset, stash는 절대 수행하지 않는다.** Codex만 Git branch에 커밋한다.

이번 목표는 PowerShell 직접 실행 없이 안전한 요청 작성 → bootstrap → Planning/Replan → 허용된 단일 code/doc slice dispatch → 결과 확인을 제공하는 것이다. Harness 실행 엔진과 유일한 상태 진실은 여전히 PowerShell entrypoint와 `WORKFLOW_STATE.json`이다.

## 시작 기준

- repository/branch: `S:\dev\project\hrns_now`, `harness-dev`
- Phase 3 Codex 검증/보정 커밋:
  - `4925406` — process·lock 계약 보정
  - `88cb022` — onboarding의 검증 후 Registry 저장
- 선행 Gate G0–G3은 PASS/PASS_WITH_FIXES 상태다. Phase 4만 구현한다.
- 반드시 전체를 읽을 문서:
  - `doc/hrns_now_claude_plan.md`
  - `doc/hrns_now_design_pattern.md`
  - `doc/phase_reports/phase3-report.md`
- `doc/hrns_now_packaging_plan.md`는 관련 없는 사용자 파일이다. 읽기·수정·삭제·stage하지 않는다.
- `D:\harness-kit`은 canonical live tree이며 Git 저장소가 아니다. 이번 Phase는 Harness script 변경 없이 기존 계약을 소비해야 한다. 수정이 정말 필요하면 중단하고 Phase report에 근거를 남긴다. `git init` 금지.

## Phase 3 확정 계약 — 절대 회귀 금지

1. Command는 `HarnessCommand` sealed type과 `HarnessCommandEncoder` argument list를 통해서만 PowerShell로 간다. shell 문자열 조립 금지.
2. `HRNS_POWERSHELL_PATH`를 존중한다. `ProcessBuilder`, concurrent stdout/stderr drain, timeout/cancel Windows process-tree 종료, residual check, bounded output, secret masking을 우회하지 않는다.
3. 모든 실행은 typed result이며 stdout 성공 문구만으로 완료 처리하지 않는다. 실행 뒤 State를 다시 읽는다.
4. lock은 `%LOCALAPPDATA%\hrns-now\locks\<projectId>\<date>.lock.json`이며 payload는 `project_id,date,owner_pid,owner_kind,started_at,heartbeat_at,command`다. Harness workspace에 lock/state/UI 파일을 만들지 않는다.
5. UI 외 State change heuristic은 경고와 새 실행 보류만 제공한다. 외부 terminal 실행을 완전히 차단하거나 kill한다고 주장하지 않는다. explicit refresh만 보류 해제다.
6. onboarding은 `경계 검사 → Doctor → compatibility → Registry 저장` 순서다. 이를 Registry 선저장으로 되돌리지 않는다.
7. `doc/hrns_now_design_pattern.md` §3–4/§6/§8–12/§16/§19를 따른다. 특히 lock은 decorator가 아니라 execution coordinator에서 명시적으로 관리하고, Composable/ViewModel에 ProcessBuilder·JSON parsing·file write를 넣지 않는다.

## 구현 범위

### 1. Typed mutating command와 실행 orchestration

- Phase 3의 read-only Doctor/ValidateOps를 유지하면서 다음 실계약만 typed command/mapper로 확장한다.
  - bootstrap: no-wrapper `run-cycle.ps1 -UsePythonSidecars`
  - planning: `-RunPlanningWrapper`
  - replan: `-RunReplanWrapper`
  - execution: `-RunExecutionWrapper code|doc`만
- `validation`이라는 가짜 execution wrapper mode를 만들지 않는다. validation-only slice의 매핑은 Harness 실계약을 재확인해 안전하게 결정할 수 있을 때만 추가하며, 불명확하면 fail-closed로 숨긴다.
- mutating command는 policy가 허용한 typed `UiAction`에서만 mapper가 만든다. UI에서 script path, wrapper value, authorized target path, raw argument를 변경할 수 없어야 한다.
- Phase 4 final plan의 고정 종료 순서(프로세스 종료 → exit code 기록 → lock 해제 확인 → State 재읽기 → stop reason/queue/CTA 재계산)를 mutating command에 명시적으로 구현하고 test로 고정한다. Phase 3 diagnostic의 관측 경로를 임의로 약화하지 않는다.

### 2. 오늘 준비와 요청 작성

- bootstrap은 lock을 획득한 뒤 실행하고, 정상/비정상 종료 모두에 대해 lock 해제와 State 재읽기 규칙을 지킨다. 날짜 폴더의 required 4-file(`REQUEST_INBOX.md`, `TODAY_STRATEGY.md`, `DAILY_HANDOFF.md`, `WORKFLOW_STATE.json`)을 확인한다. optional `REQUEST_STRUCTURED.md`/logs 누락은 실패가 아니다.
- 요청 editor는 제목/유형/출처/우선순위/요약/상세/제약을 입력하되 `REQUEST_INBOX.md`만 대상으로 한다. `REQUEST_STRUCTURED.md`를 편집하거나 생성하지 않는다.
- 요청 저장은 dedicated port/adapter와 typed result로 구현한다: load 시 hash+mtime capture, save 직전 재검증, conflict 시 overwrite 금지, reload/manual merge 안내, UTF-8 no BOM temp write + atomic move, 저장 전 diff/기존 내용 보존. `WORKFLOW_STATE.json`은 절대 쓰지 않는다.

### 3. Planning/Replan과 Strategy projection

- Planning은 `-RunPlanningWrapper`, 재계획은 `-RunReplanWrapper`만 호출한다. 완료 판단은 exit code와 재읽은 State/Strategy/queue를 함께 사용한다.
- Strategy 화면은 사람용 `TODAY_STRATEGY.md`와 기계 queue projection을 분리한다. 충돌 시 `WORKFLOW_STATE.json`이 최종 진실임을 명시한다.
- `queue.blocked_reason=dispatch_metadata_conflict`이면 dispatch CTA를 숨기고 replan만 허용한다. 로그 문자열을 상태 진실로 사용하지 않는다.

### 4. Execution dispatch와 실행 표시

- `ActionPolicy`가 code/doc slice를 허용할 때만 dispatch한다. 실행 전 확인 panel에 wrapper, current slice, authorized target, 허용/금지 범위, 예상 검증, compatibility, lock을 read-only로 표시한다.
- UI에서 authorized target 경로를 편집하거나 다른 target을 지정할 수 없다.
- process stdout/stderr와 `<workspaceRoot>\logs\<yyyy-MM-dd>\` wrapper log는 참고 정보로만 보여 준다. 실행 판단은 State, 프로세스 생존은 process adapter, 역할 단계는 구조화 event가 있을 때만 권위가 있다.
- duplicate click, cancel, project/day switch late write, external State-change hold, secret masking, post-run State reread를 Phase 3 수준 이상으로 유지한다.

## 금지사항

- Phase 5 Closure/Recovery, Phase 6 MSI, Phase 7 experiment 선구현
- Harness state 직접 write, Markdown prose로 action 허용 판단, stdout 성공 문구만으로 완료, raw session ID/token/secret/raw log 저장·표시
- `--continue` 또는 자동 resume, 임의 PowerShell 콘솔/명령 입력창
- Registry/lock을 Harness workspace에 생성, test 삭제/skip, mock을 live failure fallback으로 사용
- `git commit`, `amend`, `rebase`, `push`, `reset`, `stash`

## 설계와 테스트 필수 조건

- core: command mapper와 execution/request/queue policy를 pure typed result로. unknown/malformed/lock/external change는 fail-closed.
- infra: argument-list contract, request atomic writer/conflict, UTF-8 no BOM, Korean/space/drive-letter path, process/lock integration test.
- composeApp: single `StateFlow<HrnsUiState>`, UI thread I/O 금지, typed event만, action label을 ID로 쓰지 않음.
- 통합: fixture workspace와 real PowerShell fixture로 bootstrap/planning/replan/code/doc mapping, cancel, lock release, post-run State reread, CTA violation rejection을 검증한다.
- 기존 Phase 0–3 tests는 삭제·skip하지 않는다.

실제 task를 확인한 뒤 최소 다음을 실행한다.

```powershell
.\gradlew.bat :core:test
.\gradlew.bat :infra:test
.\gradlew.bat :composeApp:jvmTest
.\gradlew.bat check
```

## 완료 보고서

`doc/phase_reports/phase4-report.md`에 다음을 UTF-8 without BOM으로 남긴다.

- 실제 변경 파일과 Phase 3 계약 보존 근거
- typed command ↔ Harness argument 계약 및 validation-only 처리 근거
- REQUEST optimistic concurrency/atomic write/conflict 증거
- bootstrap/planning/replan/code/doc policy/lock/state reread 종료 순서 증거
- 테스트/fixture/Gradle 결과와 미실행 사유
- Harness 변경 여부와 필요 시 zip backup 경로·시각·크기
- Claude가 Git 작업을 하지 않았다는 명시

Codex가 실제 diff·test·Harness 계약과 `doc/hrns_now_design_pattern.md`를 독립 검증한 뒤에만 commit과 Phase 5 진입을 결정한다.