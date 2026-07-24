# Phase 1C 실데이터 Cockpit 검증·보정 보고서

검증일: 2026-07-24
검증자: Codex
대상 저장소: `S:\dev\project\hrns_now`
브랜치: `harness-dev`
검증 전 HEAD: `a1251b3` (`fix: Phase 1B CTA 정책 안전성 보강`)
Claude 커밋: 없음 — Claude는 working tree만 작성하고 Codex만 커밋한다.

---

## 진척도

- 대상 Phase: Phase 1C — 실데이터 Cockpit
- Verdict: PASS_WITH_FIXES
- 다음 Phase 진행 가능: 예
- NEXT_ALLOWED_PHASE: Phase 1D — 프로젝트 Registry와 날짜 탐색

## 1. 검증 대상

- Claude 산출물: `doc/phase_reports/phase1c-report.md` 최초본과 Phase 1C working tree 전체
- 기준 계획: `doc/hrns_now_claude_plan.md` Phase 1C, §3.4, 부록 A·B
- 설계 기준: `doc/hrns_now_design_pattern.md`의 Hexagonal Architecture, Use Case, MVVM/UDF, Projection, Policy/State/Result, God ViewModel 금지 규칙
- 선행 Gate: Phase 1B Codex 커밋 `a1251b3`의 PASS_WITH_FIXES 확인
- Phase 식별 방식: 사용자가 `doc/phase_reports/phase1c-report.md`를 명시했고 working tree의 변경 범위도 Phase 1C와 일치함
- live 계약 교차 확인: `D:\harness-workspaces\auziraum\2026-06-26\WORKFLOW_STATE.json`, `D:\harness-kit`의 queue projection/serialization 스크립트
- 주요 검토 파일:
  - `composeApp/.../App.kt`, `presentation/**`, `ui/{Components,Screens,Shell}.kt`
  - `core/usecase/LoadCockpitUseCase.kt`
  - `infra/{WorkspaceDayDiscovery,WorkflowStateChangeProbe,WorkspaceArtifactProbe}.kt`
  - Phase 1C 신규·회귀 테스트

## 2. 핵심 판정

Claude 초안은 `StateFlow<HrnsUiState>`, 수동 refresh/polling, 실데이터 Reader, stale/진단 projection, mock의 명시적 demo 분리, UI read model의 presentation 이동이라는 Phase 1C 기본 골격을 구현했다. 그러나 최초 상태로는 파일 탐색과 mtime 조회가 UI dispatcher에서 실행됐고, 화면의 모든 허용 CTA가 실제 callback 없이 활성화됐다. 또한 live Harness가 보장하지 않는 `queue.active.wrapper`와 `queue.active.authorized_target_file`을 DTO/domain에 추가해 실행 slice 종류를 추측했다. 이 조합은 UI 스레드 I/O 금지, live State 계약, CTA fail-closed 기준을 위반하므로 통과시킬 수 없었다.

Codex는 조회 orchestration을 `LoadCockpitUseCase`, UI 조립을 mapper, polling/lifecycle을 AndroidX `AppViewModel`로 분리했다. 모든 filesystem 협력자를 IO dispatcher로 옮겼고 typed `HrnsUiEvent → AppViewModel → StateFlow` 흐름을 연결했다. `queue.active`는 최종 계획과 live 스크립트가 보장하는 card/slice pointer만 유지하며 active slice 종류는 Phase 2 계약 전까지 `null`로 두어 실행 CTA를 잠근다. Phase 1C에서 실제 구현된 `Refresh`만 활성화하고, unknown/raw 진단 원문과 state blocked reason을 사용자 화면에 그대로 노출하지 않는다.

최종 구현은 프로젝트/Profile/날짜, phase/status/queue/pointer/authorized target, stop·정책 차단 사유, 4-file readiness, ops validation, Closure, stale와 마지막 읽기 시각을 실데이터 projection으로 표시한다. targeted, 세 모듈, 전체 `check` 검증이 통과했으므로 Phase 1D 진입을 허용한다.

## 3. 발견 사항

### Critical

- **UI thread에서 filesystem I/O 수행** — 최초 `AppViewModel.loadOnce`는 `WorkspaceDayDiscovery.discover`와 `WorkflowStateChangeProbe.lastModifiedOrNull`을 `withContext(ioDispatcher)` 밖에서 호출했다. 보고서의 “모든 I/O가 IO dispatcher 내부” 주장과 실제 코드가 달랐다. `composeApp/.../presentation/viewmodel/AppViewModel.kt`에서 날짜 탐색, mtime probe, 전체 load를 IO 경계 안으로 이동하고 thread-name 기반 회귀 테스트를 추가했다.
- **보장되지 않은 active slice 필드로 실행 종류 추측** — 최초 구현은 `QueuePointer`와 Harness DTO에 wrapper/target을 추가하고 `ActiveSliceKindMapper`로 code/doc/validation을 판정했다. 하지만 최종 계획 부록 A는 `queue.active.card_id/slice_id`만 보장하고, 기준 live fixture의 active object도 두 pointer만 가진다. `D:\harness-kit\scripts\lib\plan\planning-workqueue-model.ps1` 등 현행 serialization도 pointer만 기록한다. 해당 확장을 제거하고 `activeSliceKind=null` fail-closed 테스트를 고정했다.

### Major

- **활성화된 no-op CTA** — 최초 Cockpit은 policy allowed action을 전부 `enabled=true`인 `ActionButtonModel`로 바꾼 뒤 버튼 `onClick={}`을 사용했다. 실행되는 것처럼 보이나 아무 동작도 하지 않았다. typed action identity와 enabled 상태를 UI까지 보존하고 `Refresh`만 실제 `HrnsUiEvent`로 연결했다. Process/Registry/Recovery CTA는 해당 Phase 전까지 disabled다.
- **외부 raw 값 화면 노출** — `Unknown(raw)`, top-level `blocked_reason`, unsupported schema raw version이 label/diagnostics에 그대로 표시됐다. raw에 session ID·token·secret이 포함될 수 있으므로 domain 보존과 UI 표시를 분리하고 generic typed label 및 `ActionPolicy`의 안전 문구를 사용하도록 변경했다.
- **God ViewModel과 설계 문서 불일치** — 최초 ViewModel 생성자는 config, path/artifact probe, day discovery/policy, state port, projection builders 등 다수 책임을 직접 조율했다. `core/usecase/LoadCockpitUseCase`와 `CockpitUiStateAssembler`를 도입해 조회, 표시 조립, lifecycle을 분리하고 AndroidX `ViewModel`/`viewModelScope`를 사용하도록 정렬했다.
- **동시 refresh 순서 경쟁** — 최초 polling은 실제 재읽기 여부를 결정하기 전에 sequence를 증가시켰다. 변경 없는 poll tick도 진행 중인 manual refresh를 무효화할 수 있었다. 실제 read 직전에만 sequence를 증가시키고 늦게 끝난 이전 결과가 최신 상태를 덮지 않는 결정적 latch 테스트를 추가했다.
- **Reader와 artifact의 날짜 불일치 가능성** — 최초 artifact probe가 별도로 날짜를 다시 선택할 수 있었다. `LoadCockpitUseCase`가 선택한 동일 `WorkspaceDay`를 State reader와 artifact probe 모두에 전달하도록 고정했다.

### Minor

- 최초 Cockpit 상태 표에 plan이 요구한 Profile 표시가 빠져 있어 추가했다.
- 실제 `Thread.sleep`/실시간 delay에 의존하던 refresh race 테스트를 latch와 전용 executor 기반 결정적 테스트로 교체했다.
- projection 이동 후 남은 `core.projection` UI 모델을 제거하고 import를 presentation 계층으로 정렬했다.

## 4. SOLID·설계 패턴 평가

| 항목 | 판정 | 근거 |
|---|---|---|
| SRP | PASS | `LoadCockpitUseCase`는 read query 조율, assembler는 UI projection, ViewModel은 event/polling/lifecycle만 담당 |
| OCP | PASS | sealed `StateReadResult`/domain 값과 typed `UiAction`; unknown은 파싱을 깨지 않고 UI에서 fail-closed |
| LSP | PASS | real/fake `WorkflowStatePort`가 Missing/AccessDenied/Success 의미를 동일하게 유지하고 mock 성공으로 바꾸지 않음 |
| ISP | PASS | Reader, mtime probe, day discovery, registry/process가 섞인 거대 provider를 만들지 않음 |
| DIP | PASS | core use case는 Compose/serialization 구현을 모르고 composition root에서 infra 협력자를 주입 |
| 계층 의존 방향 | PASS | `Compose → usecase/port → infra adapter`, domain → presentation label 방향을 유지 |
| 패턴 적정성 | PASS | MVVM/UDF, Use Case, Policy, Result/Projection, Adapter를 Phase 1C 범위에서 적용 |
| 과도한 추상화 | PASS | 기존 단일 메서드 협력자는 좁은 함수 주입으로 유지하고 불필요한 factory/범용 filesystem 계층을 만들지 않음 |

## 5. 수행한 수정

- `core/usecase/LoadCockpitUseCase.kt`
  - workspace/day discovery, path/readiness/artifact/State 조회를 read-only query로 분리
  - 동일 `WorkspaceDay`를 Reader와 artifact probe에 전달
  - 미설정/invalid workspace는 Reader를 호출하지 않고 typed Missing으로 종료
- `composeApp/presentation/model/**`
  - UI read model을 `core.projection`에서 presentation으로 이동
  - `HrnsUiState`, `HrnsUiEvent`, `CockpitProjection`, typed/enabled `CockpitActionItem` 추가
- `composeApp/presentation/mapper/**`
  - State/Policy 결과를 표시 전용 projection으로 조립
  - stale와 오류 3분리, unknown/raw 비노출, Phase 1C capability 반영
  - pointer-only active queue는 실행 종류를 추측하지 않음
- `composeApp/presentation/viewmodel/AppViewModel.kt`
  - AndroidX ViewModel + viewModelScope + 단일 StateFlow
  - 수동 refresh, 3초 mtime polling, 단일 polling job, 최신 load 우선권
  - 모든 filesystem 호출을 IO dispatcher에서 수행
- `composeApp/App.kt`, `ui/**`
  - lifecycle `viewModel` composition, typed UI event callback, Loading/Ready 렌더링
  - mock은 `HRNS_DEMO_MODE`에서만 사용
  - Refresh만 enabled, 다른 아직 미구현된 CTA는 disabled
  - Profile을 포함한 Phase 1C 필수 상태 표시
- `infra/WorkspaceDayDiscovery.kt`, `WorkflowStateChangeProbe.kt`, `WorkspaceArtifactProbe.kt`
  - 날짜 디렉터리 탐색과 mtime probe를 독립 협력자로 분리
  - artifact probe의 선택 날짜 명시 overload 및 탐색 로직 중복 제거
- 테스트
  - use case 4건, Cockpit assembler 12건, ViewModel 7건 추가·보강
  - IO thread 격리, refresh/polling/lifecycle/race, raw 비노출, live pointer-only fail-closed, 동일 day 전달 검증

부작용 검토: `WORKFLOW_STATE.json` 쓰기, Registry, ProcessBuilder, PowerShell command, lock, request writer, Closure를 추가하지 않았다. `D:\harness-kit`은 읽기 검증만 했고 변경하지 않았다.

## 6. 검증 결과

| 검증 | 명령 | 결과 |
|---|---|---|
| Targeted | `.\gradlew.bat :core:test --tests "io.hrns_now.core.usecase.LoadCockpitUseCaseTest" :composeApp:jvmTest --tests "io.hrns_now.app.presentation.mapper.CockpitProjectionAssemblerTest" --tests "io.hrns_now.app.presentation.viewmodel.AppViewModelTest"` | PASS — 23 tests, 실패 0 |
| Module | `.\gradlew.bat :core:test :infra:test :composeApp:jvmTest` | PASS — core 36, infra 45, Compose 20, 총 101 tests |
| Full | `.\gradlew.bat check` | PASS |
| CI/Smoke | GitHub Actions 원격 실행 | 미실행 — push하지 않음. 로컬 전체 check 통과 |

정적 검사:

- production의 `auziraum`, `ProcessBuilder`, `--continue`, fake validation wrapper: 0건
- `queue.active.wrapper`/`active.authorized_target_file` 소비: 0건
- Cockpit의 enabled `onClick={}`와 `remember { buildProjections() }`: 0건
- 관련 없는 `doc/hrns_now_packaging_plan.md`: 미열람·미수정·미포함

## 7. Git 상태와 커밋

- 작업 전 상태: Phase 1C 소스·테스트·보고서가 수정/untracked인 Claude working tree, 별도 사용자 untracked 파일 `doc/hrns_now_packaging_plan.md`
- Claude 커밋: 없음
- Codex 보정 커밋: 본 보고서와 Phase 1D prompt를 포함한 별도 후속 커밋으로 생성
- 커밋 메시지: `fix: Phase 1C 실데이터 Cockpit 안전성 보강`
- 미커밋 잔여: 사용자 파일 `doc/hrns_now_packaging_plan.md`만 보존 예정
- push 여부: 수행하지 않음
- 최종 SHA: Codex 최종 응답에 기록

## 8. 잔여 위험

- `queue.active`의 slice 종류는 현행 보증 필드가 아니므로 Phase 2 계약 전까지 `activeSliceKind=null`이다. 따라서 execution-ready라도 실제 실행 CTA는 fail-closed된다. 이는 현재 Phase 미완료가 아니라 안전한 후속 계약 경계다.
- `CompatibilityStatus`는 Phase 2 전까지 `Unknown`, `BoundaryStatus`는 Phase 1D 전까지 `Unknown`이다. 임의로 Supported/Valid로 승격하지 않는다.
- Registry 기반 다중 프로젝트 선택과 boundary validation은 Phase 1D 범위다. 현재는 환경변수 config fallback으로 실데이터를 읽는다.
- Recovery Center와 process action은 아직 구현되지 않았으므로 Refresh 외 CTA는 disabled다.
- 원격 GitHub Actions는 push 금지 규칙에 따라 실행하지 않았다.

## 9. 다음 단계

- NEXT_ALLOWED_PHASE: Phase 1D — 프로젝트 Registry와 날짜 탐색
- Claude에게 전달할 다음 작업: `doc/claude_prompts/phase1d-project-registry.md`
- 다음 Phase 진입 전 조건: 본 Codex 커밋을 HEAD로 유지하고 Phase 1A Reader, Phase 1B exact CTA, Phase 1C IO/polling/raw 비노출 회귀를 통과할 것
- Phase 2/3/4 기능, `queue.active` 미보증 필드, process/lock/PowerShell 실행을 선구현하지 말 것
