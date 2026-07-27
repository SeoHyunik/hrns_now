# Phase 4 독립 검증 보고서 — 표준 일일 실행 흐름

## 진척도

- 대상 Phase: Phase 4 — 표준 일일 실행 흐름
- Verdict: PASS_WITH_FIXES
- 다음 Phase 진행 가능: 예

## 1. 검증 대상

- 저장소/브랜치: `S:\dev\project\hrns_now` / `harness-dev`
- Claude 커밋: 없음 — Claude는 작업 트리를 남겼고 Git 작업을 수행하지 않았다.
- Codex 보정 커밋: `12d808d` (`fix: Phase 4 실행 유스케이스와 요청 저장 보정`)
- 검토 파일: Phase 4 변경 33개 코드·테스트 파일, `D:\harness-kit\scripts\run-cycle.ps1`, `D:\harness-kit\docs\STATE_MODEL.md`, `D:\harness-kit\python\scripts\state_surface.py`
- 기준 계획 절: `doc/hrns_now_claude_plan.md` §2.1–2.3, §4 Phase 4·5, `doc/hrns_now_design_pattern.md` §7·§8·§13·§16·§17·§19·§20·§21
- Phase 식별 방식: 사용자가 제공한 Phase 4 완료 보고서와 `doc/phase_reports/phase4-report.md`를 실제 worktree diff와 대조하여 식별.

## 2. 핵심 판정

`run-cycle.ps1`의 실제 인자 계약을 다시 확인했다. bootstrap은 `-UsePythonSidecars`, planning/replan은 각 wrapper와 실제 reason 값, execution은 `code|doc`만 사용한다. 존재하지 않는 `validation` wrapper는 추가되지 않았고, `None`/`Auto`는 실행 UI action으로 매핑하지 않아 fail-closed를 유지한다.

초기 구현은 실행 action 이벤트가 ViewModel 경계에서만 CTA를 확인한 뒤 dispatch할 수 있고, 실행·요청 저장 orchestration이 ViewModel에 남아 있었다. `doc/hrns_now_design_pattern.md` §16·§19·§20에 맞춰 `ExecuteHarnessActionUseCase`와 `SaveRequestUseCase`로 옮겼다. 실행 use case는 policy 재검증 → typed command → lock → process → **lock 보유 중 `WORKFLOW_STATE.json` 재읽기** → release 순서를 보장한다. stdout은 완료 판정의 정본이 아니다.

요청 저장은 `REQUEST_INBOX.md` 전용이며, hash/mtime 기반 optimistic concurrency, UTF-8 no BOM, temp + atomic move를 사용한다. 과거 날짜 write는 ViewModel과 화면 projection 양쪽에서 차단했고, conflict에서는 폼 초안을 유지하고 재로드·수동 병합 경로를 안내한다. `WORKFLOW_STATE.json`과 `REQUEST_STRUCTURED.md`에는 쓰지 않는다.

`D:\harness-kit`은 읽기 전용으로만 확인했으며 변경·백업·Git 초기화가 없었다.

## 3. 발견 사항

### Critical

- 없음

### Major

- 수정 완료 — `composeApp/.../AppViewModel.kt`의 이벤트 경계만으로는 stale event가 실행 정책을 우회할 여지가 있었고, 실행 orchestration이 ViewModel에 집중돼 있었다. `core/.../ExecuteHarnessActionUseCase.kt`에서 policy를 다시 평가하고 lock-held State reread를 고정했다.
- 수정 완료 — `AppViewModel`이 과거 날짜의 요청 이벤트를 막지 못했고, `Screens.kt`의 form은 저장 결과 전 초안을 지웠다. 과거 write를 차단하고 성공 신호가 있을 때만 form을 비우도록 변경했다.
- 수정 완료 — `RunStatusProjectionAssembler.kt`가 Phase 4 command를 포함한 실행 상태를 여전히 Doctor/Ops 전용으로 설명했다. 실행·진단 공통 상태로 보정했다.

### Minor

- 실제 Claude Code를 호출하는 `run-cycle.ps1` end-to-end는 작업공간을 변경하고 비용·비결정성을 유발하므로 자동 실행하지 않았다. 실제 param 계약, argument-list test, stub PowerShell process test, State/lock/use case 회귀로 대체했다.

## 4. SOLID·설계 패턴 평가

| 항목 | 판정 | 근거 |
|---|---|---|
| SRP | PASS | request 저장과 command/lock/State reread를 각각 `SaveRequestUseCase`, `ExecuteHarnessActionUseCase`, adapter로 분리했다. |
| OCP | PASS | `UiAction`→`HarnessCommandMapper`의 typed mapping으로 command 추가가 화면 문자열 분기로 번지지 않는다. |
| LSP | PASS | fake port도 `StateReadResult`, lock acquire, request conflict의 실제 의미를 보존하는 회귀 테스트를 사용한다. |
| ISP | PASS | `RequestWriterPort`, `TodayStrategyReaderPort`, `ProcessLockPort`, `HarnessRunnerPort`가 소비자별로 분리돼 있다. |
| DIP | PASS | core use case는 Compose·`ProcessBuilder`·filesystem 구현을 참조하지 않고 port에만 의존한다. |
| 계층 의존 방향 | PASS | `core ← infra ← composeApp` 유지; composition root만 concrete adapter를 조립한다. |
| 패턴 적정성 | PASS | Ports/Adapters, Command mapper, Policy, Use Case, optimistic concurrency를 Phase 4 범위에 맞게 사용했다. |
| 과도한 추상화 | PASS | 실행·요청 저장에만 필요한 두 use case와 작은 port를 추가했으며 다음 Phase Closure port는 선구현하지 않았다. |

## 5. 수행한 수정

- `core/usecase/ExecuteHarnessActionUseCase.kt`: action policy 재검증, typed mapper, lock-held State reread 및 release 순서를 단일 application use case로 이동했다.
- `core/usecase/SaveRequestUseCase.kt`: 요청 append와 `LoadedRequest` 버전 전달을 core에 한정했다.
- `AppViewModel`/`Screens.kt`/projection: 과거 요청 write 차단, success 때만 form clear, conflict 초안 보존·수동 병합 안내, main dispatcher 상태 갱신을 보정했다.
- `ExecuteHarnessActionUseCaseTest`/`SaveRequestUseCaseTest` 및 ViewModel tests: policy 우회, lock-held reread, conflict, historical write 회귀를 추가했다.

부작용 검토: Harness workspace에 UI 소유 파일을 만들지 않았고, Validation-only dispatch·Closure·MSI·실험 기능을 추가하지 않았다.

## 6. 검증 결과

| 검증 | 명령 | 결과 |
|---|---|---|
| Targeted | `./gradlew.bat --no-daemon :core:test :composeApp:jvmTest --rerun-tasks` | PASS |
| Module | `./gradlew.bat --no-daemon :infra:test --rerun-tasks` | PASS |
| Module | `./gradlew.bat --no-daemon :composeApp:jvmTest` | PASS |
| Full | `./gradlew.bat --no-daemon check` | PASS |
| Harness/CI smoke | 미실행 | Harness source를 변경하지 않았으며 실제 `run-cycle.ps1` end-to-end는 mutation/cost 위험 때문에 실행하지 않았다. |

## 7. Git 상태와 커밋

- 작업 전 상태: `fd33b4d` 뒤 Phase 4 코드·테스트와 Claude 보고서가 미커밋 상태였고, 사용자 untracked `doc/hrns_now_packaging_plan.md`가 존재했다.
- 작업 후 상태: Phase 4 코드·테스트는 Codex 커밋으로 기록했다. 사용자 packaging plan은 읽기·수정·stage하지 않았다.
- 커밋 SHA: `12d808d`
- 커밋 메시지: `fix: Phase 4 실행 유스케이스와 요청 저장 보정`
- 미커밋 잔여: 이 보고서/다음 prompt의 문서 변경과 사용자 소유 `doc/hrns_now_packaging_plan.md`
- push 여부: 수행하지 않음

## 8. 잔여 위험

- 현재 Phase 미완료: 없음.
- 후속 Phase 항목: Closure checklist/policy, Recovery Center, read-only diagnostic viewer, repository status 경고는 Phase 5 범위다.
- 실행 출력은 참고 정보이며 State가 정본이다. UI는 외부 terminal 실행을 완전 차단한다고 주장하지 않고 mtime 기반 감지만 수행한다.

## 9. 다음 단계

- NEXT_ALLOWED_PHASE: Phase 5 — 검증·Closure·복구 센터
- Claude에게 전달할 다음 작업: `doc/claude_prompts/phase5-closure-recovery.md`를 읽고 Phase 5만 구현한다.
- 다음 Phase 진입 전 조건: Phase 4 source commit `12d808d`와 이 보고서를 확인하고, `doc/hrns_now_design_pattern.md`의 ClosurePolicy/Recovery Strategy와 live Harness의 `-ValidateForClosure` 실제 계약을 먼저 대조한다.
