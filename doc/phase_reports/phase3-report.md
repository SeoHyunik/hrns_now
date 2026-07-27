# Phase 3 독립 검증 보고서 — Process Adapter + Lock

검증일: 2026-07-27
검증자·보정·커밋: Codex
대상: `S:\dev\project\hrns_now` (`harness-dev`)

## 진척도

- 대상 Phase: Phase 3 — 진단용 PowerShell 실행 어댑터 + UI 소유 lock
- Verdict: PASS_WITH_FIXES
- 다음 Phase 진행 가능: 예

## 1. 검증 대상

- 저장소/브랜치: `S:\dev\project\hrns_now`, `harness-dev`
- Claude 커밋: 없음 — Claude는 commit 권한이 없으며, Phase 3 구현은 미커밋 working tree에서 검토했다.
- Codex 보정 커밋:
  - `4925406` — `fix: Phase 3 프로세스 잠금 계약 보정`
  - `88cb022` — `fix: Phase 3 온보딩 검증 후 등록`
- 검토 파일: `core`의 Command/port/result/policy, `infra`의 process·lock·security adapter, Compose `AppViewModel`/projection/UI, 각 module regression test.
- 기준 계획 절: `doc/hrns_now_claude_plan.md` §2 불변 원칙, Phase 3, Gate G3.
- 설계 기준: `doc/hrns_now_design_pattern.md` §3–4 Hexagonal/ACL, §6 Command, §8–9 MVVM/Projection, §11 Policy, §12 Decorator, §16 실행 orchestration, §19 God ViewModel 금지, §21 테스트.
- Phase 식별 방식: 사용자가 지정한 `doc/phase_reports/phase3-report.md`와 실제 미커밋 source/diff를 대조해 확정했다.

`D:\harness-kit`은 이 Phase에서 수정하지 않았다. Harness는 의도적으로 Git 저장소가 아니며 `git init`·push를 수행하지 않았다. 관련 없는 사용자 파일 `doc/hrns_now_packaging_plan.md`도 읽기·수정·stage하지 않았다.

## 2. 핵심 판정

Doctor/ValidateOps만을 typed `HarnessCommand`로 제한하고, `ProcessBuilder`에는 executable과 argument 목록만 전달한다. Process adapter는 stdout/stderr를 동시에 drain하고 timeout·취소 시 실제 Windows process tree를 종료·잔존 확인한다. 결과는 JSON contract, exit code, timeout/cancel/start failure를 구분하며 production composition에서 secret-masking decorator를 거친다.

Codex는 실제 구현에서 누락되어 있던 `HRNS_POWERSHELL_PATH` 반영, malformed JSON fail-closed, plan payload schema, external State-change heuristic, Setup 버튼 typed 배선, State 재조회/lock 순서, pre-save onboarding 순서를 보정했다. `WORKFLOW_STATE.json`을 UI가 쓰는 경로와 mutating wrapper는 추가하지 않았다. Gate G3의 process adapter·cancel 무잔존·masking·lock 조건을 충족한다.

## 3. 발견 사항

### Critical

- 없음

### Major

- 해결됨 — `infra/process/HarnessCommandEncoder.kt`가 항상 `powershell.exe`만 실행해 `HRNS_POWERSHELL_PATH` runtime 설정을 무시했다. optional path 주입과 기본 Windows PowerShell fallback을 추가하고 path preservation test를 보강했다.
- 해결됨 — `infra/process/PowerShellHarnessAdapter.kt`가 `checks` 누락 또는 일부 malformed check를 빈/부분 성공 contract로 바꿨다. 필수 list와 각 element를 전부 검증해 실패 시 `contract=null`으로 fail-closed 처리했다.
- 해결됨 — `infra/lock/LocalProcessLockAdapter.kt` payload가 계획의 `{project_id,date,owner_pid,owner_kind,started_at,heartbeat_at,command}`와 달랐고 default field가 serialization에서 빠졌다. schema를 정렬하고 `encodeDefaults=true` 및 disk JSON regression test를 추가했다.
- 해결됨 — `WindowsProcessTreeTerminator.kt`가 parent 종료 뒤 descendants를 다시 탐색해 re-parented child를 놓칠 수 있었다. 종료 전 target snapshot을 만들고 같은 handle 목록으로 residual을 검사한다.
- 해결됨 — Phase 3 보고서와 달리 external `WORKFLOW_STATE.json` 변경 heuristic이 구현돼 있지 않았다. `ExternalExecutionDetectionPolicy`와 `AppViewModel` mtime guard를 추가해 UI 자체 refresh/run이 아닌 변경은 경고·새 Doctor/Validate 실행 보류로 처리한다. 명시 refresh만 보류를 해제한다.
- 해결됨 — Setup의 “상태 점검 실행” 버튼은 disabled placeholder였고 callback도 없었다. typed `UiAction`을 presentation model에 보존해 Setup에서 Doctor/ValidateOps action event로 연결하고, Cockpit 정책의 enablement만 사용한다.
- 해결됨 — Registry form이 저장을 먼저 하고 Doctor/compatibility를 나중에 실행했다. `RegisterProjectUseCase.inspect`로 저장 전 typed boundary 검증을 분리하고, Doctor 성공·supported compatibility 뒤에만 `save`한다. Doctor fail/미파싱/unsupported compatibility는 Registry를 쓰지 않는다.

### Minor

- 계획 문구의 PowerShell-side `[Console]::OutputEncoding` 강제는 `-File <script>` 인자 계약을 보존하기 위해 도입하지 않았다. 대신 `JvmProcessExecutor`가 host native console charset을 감지해 decode하며 실제 Korean stdout regression test가 통과한다. 이후 PowerShell 5.1/7 혼합 host를 추가 지원할 때 별도 fixture로 재검토한다.

## 4. SOLID·설계 패턴 평가

| 항목 | 판정 | 근거 |
|---|---|---|
| SRP | PASS | Command/lock/result은 core, process·masking·lock I/O는 infra, 화면 문구는 `RunStatusProjectionAssembler`, lifecycle은 ViewModel로 분리됐다. onboarding의 pre-save 검증은 `RegisterProjectUseCase.inspect`에 둬 화면이 경계를 직접 해석하지 않는다. |
| OCP | PASS | `HarnessCommand`, `ProcessRunResult`, JSON overall/severity가 sealed type과 `Unknown(raw)`을 사용해 새 값이 문자열 분기를 확산시키지 않는다. |
| LSP | PASS | fake `ProcessLockPort`/`HarnessRunnerPort`와 real adapter가 Busy/Completed/Cancelled 등 동일 typed 결과를 소비하며 regression test가 이를 사용한다. |
| ISP | PASS | runner와 lock은 각각 최소 port이며 Registry/State write API와 결합하지 않는다. |
| DIP | PASS | core는 Compose, ProcessBuilder, kotlinx JSON, filesystem을 참조하지 않는다. infra가 core port를 구현하고 Compose는 constructor injection만 사용한다. |
| 계층 의존 방향 | PASS | `core ← infra`, `core ← composeApp`을 유지한다. Composable에는 file I/O/JSON/ProcessBuilder가 없다. |
| 패턴 적정성 | PASS | Command + Adapter/ACL, Policy, Projection, masking Decorator를 적용했다. lock은 Decorator가 아니라 coordinator에서 명시적으로 acquire/release한다. |
| 과도한 추상화 | PASS | Doctor/ValidateOps와 두 작은 port만 추가했으며 Phase 4 command/Closure interface는 선구현하지 않았다. |

## 5. 수행한 수정

- `4925406`: PowerShell path 선택, JSON contract fail-closed, plan lock schema/serialization, process-tree snapshot, external-change policy/projection, Setup typed action, lock 보유 State reread와 exception-to-typed-result 처리, 회귀 test를 보강했다.
- `88cb022`: candidate inspection과 save를 분리해 onboarding을 `경계 검사 → Doctor → compatibility → Registry 저장` 순서로 만들고, 성공/실패 ViewModel test를 추가했다.
- 부작용 검토: Doctor/ValidateOps만 read-only로 유지했고 `RunPlanning`, `RunReplan`, `RunExecutionWrapper`, REQUEST write, Closure command를 추가하지 않았다. UI 소유 lock은 `%LOCALAPPDATA%` root에만 남으며 Harness workspace에는 생성하지 않는다.

## 6. 검증 결과

| 검증 | 명령 | 결과 |
|---|---|---|
| Targeted | `./gradlew.bat :core:test --tests ExternalExecutionDetectionPolicyTest` | PASS |
| Targeted | `./gradlew.bat :infra:test --tests HarnessCommandEncoderTest --tests PowerShellHarnessAdapterTest --tests WindowsProcessTreeTerminatorTest --tests LocalProcessLockAdapterTest` | PASS |
| Targeted | `./gradlew.bat :composeApp:jvmTest --tests DefaultProjectionsTest --tests RunStatusProjectionAssemblerTest --tests AppViewModelTest` | PASS |
| Module | `./gradlew.bat :core:test :infra:test :composeApp:jvmTest --rerun-tasks` | PASS — core 89, infra 115, composeApp 45; 총 249 tests |
| Full | `./gradlew.bat check` | PASS |
| Harness/CI smoke | 미실행 — Phase 3은 `D:\harness-kit` source를 변경하지 않았고, Phase 2의 64/64 automatic/offline smoke 및 docs scan PASS 계약을 소비만 한다. |

## 7. Git 상태와 커밋

- 작업 전 상태: HEAD `2439d0a` (`docs: Phase 2 검증 보고서와 Phase 3 작업 지시`), Phase 3 source/test와 Claude report는 미커밋이었다.
- 작업 후 상태: Phase 3 source/test는 `4925406`, onboarding 보정은 `88cb022`, 이 보고서와 Phase 4 지시문은 후속 docs commit에 분리한다.
- 커밋 SHA: `4925406`, `88cb022`
- 커밋 메시지: 위 참조.
- 미커밋 잔여: `doc/hrns_now_packaging_plan.md`만 사용자 untracked 파일로 유지한다.
- push 여부: 수행하지 않음.

## 8. 잔여 위험

- 현재 Phase 미완료: 없음.
- 운영 위험: lock은 HRNS-NOW 인스턴스 간 상호배제와 UI 밖 State 변경 heuristic만 제공한다. Harness terminal process 자체를 완전 차단하거나 kill한다고 주장하지 않는다.
- 후속 Phase 항목: bootstrap/request atomic write/Planning/Replan/code·doc dispatch/실시간 wrapper log/Phase 4 exit sequence는 아직 구현하지 않았다.

## 9. 다음 단계

- NEXT_ALLOWED_PHASE: Phase 4 — 표준 일일 실행 흐름
- Claude에게 전달할 다음 작업: `doc/claude_prompts/phase4-standard-daily-flow.md`만 따라 Phase 4를 구현하고 `doc/phase_reports/phase4-report.md`에 실제 diff/test 결과를 기록한다. Claude는 commit하지 않는다.
- 다음 Phase 진입 전 조건: `88cb022` 이후에서 시작하고, Phase 3 Doctor/Validate command·masking·lock/external-change guard를 회귀시키지 않으며, `doc/hrns_now_packaging_plan.md`를 수정·stage하지 않는다.