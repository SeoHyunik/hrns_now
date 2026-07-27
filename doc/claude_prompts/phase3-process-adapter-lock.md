# Claude 작업 지시 — Phase 3: Process Adapter + Lock

## 역할과 이번 범위

당신은 HRNS-NOW의 Phase 3 구현 담당자다. 이 문서의 범위에서만 변경하고, 구현 후 실제 파일·diff·test 결과를 `doc/phase_reports/phase3-report.md`에 UTF-8 without BOM으로 기록한다. **Git commit, amend, rebase, push, reset, stash는 절대 수행하지 않는다.** Codex만 HRNS-NOW Git branch에 커밋한다.

이번 Phase의 목적은 Harness PowerShell을 위한 안전한 typed process adapter와 UI 소유 lock 기반을 만드는 것이다. Harness state의 진실은 여전히 `WORKFLOW_STATE.json`이며, UI는 이를 절대 쓰지 않는다.

## 시작 기준과 소유권

- HRNS-NOW repository: `S:\dev\project\hrns_now`, branch `harness-dev`
- Phase 2 Codex 코드 커밋: `e8a268c` (`feat: Phase 2 Harness JSON 호환성 계약 구현 및 보정`)
- 선행 Gate: Phase 0A/0B, 1A~1D, 2는 PASS/PASS_WITH_FIXES 상태다. Phase 3만 구현한다.
- 필수 참조 문서 전체 읽기:
  - `doc/hrns_now_claude_plan.md`
  - `doc/hrns_now_design_pattern.md`
  - `doc/phase_reports/phase2-report.md`
- `doc/hrns_now_packaging_plan.md`는 관련 없는 사용자 파일이다. 읽기·수정·삭제·stage하지 않는다.
- `D:\harness-kit`은 의도적으로 Git 저장소가 아니다. canonical live tree이며 zip backup으로만 보호한다. Codex가 Phase 2 보정 전 `D:\backup\harness-kit0727-1.zip`을 만들었다. Phase 3은 Harness script 변경이 필요하지 않아야 한다. 정말 필요해지면 먼저 중단하고 Phase report에 근거를 남긴다. `git init`은 금지다.

## Phase 2에서 Codex가 검증·보정한 확정 상태

다음은 이미 존재하는 계약이며 회귀시키지 않는다.

1. Harness `doctor.ps1`과 `validate-ops.ps1`는 `-Json`에서 단일 JSON stdout, text 기본 출력과 동일한 exit semantics를 제공한다. diagnostic은 secret-shaped value를 masking한다.
2. Harness `kit-version.json`은 `kit_version=2026.07.23`, `state_schema_version=1.0`, `ui_contract_version=1.0`이다. 64개 automatic/offline smoke와 docs scan(status=ok, findings=0)이 통과했다.
3. HRNS-NOW는 `KitVersionManifestPort` → `JsonKitVersionManifestAdapter` → 순수 `CompatibilityPolicy`를 사용한다. missing/malformed/major mismatch는 fail-closed이며 higher minor는 unknown field compatible으로 분류한다.
4. `AppViewModel`은 IO dispatcher에서 manifest를 읽고, state mtime이 같아도 manifest detail이 바뀌면 polling reload한다. compatibility diagnostic은 Cockpit 화면에 실제 표시된다.
5. Harness required daily surface는 정확히 `REQUEST_INBOX.md`, `TODAY_STRATEGY.md`, `DAILY_HANDOFF.md`, `WORKFLOW_STATE.json` 4개다. `REQUEST_STRUCTURED.md`와 두 logs 위치는 optional이며 legacy `WORKDAY_STATE.json`/`WORK_QUEUE.json`은 fallback이다.
6. 다음 wrapper 계약을 바꾸거나 창작하지 않는다: `-RunExecutionWrapper` 값은 `none|code|doc|auto`, replan은 `-RunReplanWrapper`, closure는 `-ValidateForClosure`다. 특히 존재하지 않는 `validation` wrapper mode를 만들지 않는다.

## Phase 3 요구사항

### 1. Core command와 port

- core에 command별 typed model을 둔다. 이번 Phase에서 실제로 연결하는 command는 **read-only** `Doctor`와 `ValidateOps`뿐이다.
- command ID/label/PowerShell argument를 문자열 배열로 화면에서 조립하지 않는다. sealed command/domain value와 encoder를 사용한다.
- core port는 process 실행 결과에 필요한 최소 contract만 노출한다. result는 start failure, success/non-zero exit, timeout, cancelled, residual-process failure를 구별하고 stdout/stderr는 UI 표시 전에 이미 masking된 값만 보관한다.
- `core`는 `ProcessBuilder`, Compose, concrete filesystem/PowerShell API를 참조하지 않는다. command policy와 result mapping은 순수 테스트 가능해야 한다.

### 2. Infra Process Adapter

- infra adapter가 core port를 구현한다. JVM `ProcessBuilder`에는 shell string이 아니라 executable과 argument **목록**을 전달한다.
- Windows PowerShell 5.1을 실제 지원한다. Kit root/workspace root의 공백·한글·drive letter 경로를 argument 분리로 보존한다.
- stdout와 stderr를 동시에 drain하여 deadlock이 없어야 하며, UTF-8 한글 출력을 보존한다.
- timeout 및 user cancel은 Windows child process tree까지 종료한다. 종료 뒤 PID/process tree 잔존 여부를 검사해 typed result로 남긴다. 단순 `destroy()` 성공만으로 취소 성공이라고 주장하지 않는다.
- timeout/cancel/start failure/exit non-zero의 message와 captured output에도 secret masking을 **UI state 이전**에 적용한다. raw session ID, token, secret, request ID는 저장·표시하지 않는다.
- output 길이/메모리 상한을 정하고 truncation이면 typed metadata를 남긴다. 예외를 삼키거나 success로 바꾸지 않는다.
- read-only Doctor/ValidateOps는 JSON mode로 invoke하고, JSON parse/result contract를 명시한다. stdout의 성공 문구만으로 완료 판정하지 않는다.

### 3. UI와 ViewModel

- `AppViewModel`은 `StateFlow<HrnsUiState>` 단일 흐름과 existing IO dispatcher 규칙을 유지한다. ProcessBuilder·stream I/O는 ViewModel/Composable에 넣지 않는다.
- Cockpit에 Doctor/ValidateOps의 실행 중·완료·실패/timeout/cancel·redacted output summary를 presentation projection으로 표시한다. duplicate click을 막고 manual refresh/polling과 경쟁해 이전 실행 결과가 새 project/day 화면에 late-write되지 않게 generation/sequence guard를 확장한다.
- UI command 권한은 typed action/정책 결과를 따른다. read-only command라도 current project/day/boundary/compatibility가 안전하지 않으면 fail-closed로 연결하지 않는다.
- 실행 종료 뒤에는 반드시 State를 다시 읽는다. 단, Phase 3은 mutating wrapper를 연결하지 않으므로 이 재읽기는 Doctor/ValidateOps 결과와 분리해 상태 관측만 갱신한다.

### 4. UI 소유 Lock 기반

- lock file은 Harness workspace가 아닌 `%LOCALAPPDATA%\hrns-now\locks\<projectId>\<yyyy-MM-dd>.lock.json`에 둔다. 테스트를 위해 root/clock/PID lookup은 주입 가능하게 한다.
- lock payload에는 project ID, day, PID, command kind, acquired/heartbeat time만 둔다. raw command output, secret, raw session/request ID, workspace state를 저장하지 않는다.
- 원자적 create/replace, duplicate click 방지, heartbeat, release를 구현한다. stale 판정은 **PID 미존재와 heartbeat 만료를 함께** 만족해야 한다. 어느 하나가 불명확하면 fail-closed로 busy 상태를 유지한다.
- UI에는 lock owner/heartbeat와 명시적 force release 경로를 제공하되, active process를 외부 실행까지 완전히 차단한다고 주장하지 않는다. external `WORKFLOW_STATE.json` 변경은 heuristic warning으로만 표현한다.
- Phase 3에서는 mutating command를 lock 준비 전 연결하지 않는다. Planning/Replan/Execution/Closure, REQUEST write, Harness state write는 Phase 4/5 범위이므로 구현하지 않는다.

## 설계 규칙

- `doc/hrns_now_design_pattern.md`의 Hexagonal 구조를 따른다: `core` command/port/policy ← `infra` process adapter, `composeApp`은 use case/projection만 의존한다.
- Adapter/ACL은 Harness PowerShell 인자·JSON·process semantics를 격리한다. masking/encoding은 decorator로 분리할 수 있지만 lock을 억지 decorator로 만들지 않는다.
- ViewModel은 orchestration만 한다. Reader/Registry/Process/Masking/Lock 책임을 하나의 God object에 합치지 않는다.
- Composable에 I/O, JSON parsing, `ProcessBuilder`, action label 문자열 분기를 넣지 않는다.
- unknown/malformed/timeout/cancel/lock ambiguity는 fail-closed다. mock/demo를 live failure fallback으로 사용하지 않는다.

## 필수 테스트와 검증

테스트는 실제 결함을 재현하도록 추가한다.

1. core: command encoding/result/lock stale policy의 parameterized 또는 decision-table test.
2. infra: 공백·한글 path, UTF-8 Korean stdout, stdout/stderr 동시 대량 출력, non-zero, start failure, timeout, cancel, residual process detection, secret masking, output truncation test. Windows process tree test는 real child fixture로 증명하고 환경 한계가 있으면 이유를 보고한다.
3. lock: atomic acquire 경쟁, active PID, stale PID+heartbeat, heartbeat만 stale/PID만 missing, force release, secret-free serialization test.
4. compose/ViewModel: duplicate click, project/day switch late result 무시, UI thread I/O 없음, refresh/polling의 post-run State reread, redacted presentation test.
5. regression: Phase 1A~2 state reader/action policy/compatibility tests를 삭제·skip하지 않는다.

실제 Gradle task를 확인한 뒤 최소 다음을 실행한다.

```powershell
.\gradlew.bat :core:test
.\gradlew.bat :infra:test
.\gradlew.bat :composeApp:jvmTest
.\gradlew.bat check
```

## 금지사항

- `WORKFLOW_STATE.json`/Harness workspace에 UI 파일 또는 lock 파일 생성
- raw session ID, token, secret, request ID, raw log 저장·표시
- shell-quoted command 한 줄, `validation` wrapper mode, stdout 성공문구만으로 완료
- automatic resume 기본 활성화 또는 `--continue`
- Phase 4/5/6/7 선구현, 대규모 architecture rewrite, test 삭제/skip, production fixture/개인 경로 hardcode
- Git commit/amend/rebase/push/reset/stash

## 완료 보고서

`doc/phase_reports/phase3-report.md`(UTF-8 without BOM)에 다음을 반드시 남긴다.

- Phase 3 변경 파일과 Phase 2 회귀 영향
- typed command/port, process lifecycle, cancellation/process-tree 증거
- lock 위치/payload/stale/force-release semantics와 external-execution 한계
- secret masking 경계와 no raw session ID 증거
- targeted/module/full Gradle 결과 및 미실행 사유
- Harness를 수정하지 않았다는 확인(수정했다면 zip backup 경로·크기·시각과 정확한 이유)
- Claude가 commit하지 않았다는 명시

Codex가 실제 diff·test·Harness 계약을 다시 독립 검증한 뒤에만 commit과 다음 Phase 허용 여부를 결정한다.