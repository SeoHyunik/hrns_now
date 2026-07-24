# Claude 작업 프롬프트 — Phase 1C 실데이터 Cockpit

## 역할과 Git 규칙

너는 `hrns_now`의 1차 구현자다. 이번 작업은 **Phase 1C — 실데이터 Cockpit**만 구현한다.

- Claude는 `git add`, `git commit`, amend, reset, stash, rebase, push를 수행하지 않는다.
- 소스·테스트·Phase 보고서만 working tree에 남긴다.
- commit과 독립 검증·보정은 Codex만 수행한다.
- 기존 사용자/Codex 변경을 삭제하거나 되돌리지 않는다.
- Phase 1D Registry, Phase 2 Harness JSON, Phase 3 Process/Lock, 실제 Harness command 실행을 선구현하지 않는다.

작업 시작 시 반드시 전체를 읽는다.

1. `doc/hrns_now_claude_plan.md`
2. `doc/hrns_now_design_pattern.md`
3. `doc/phase_reports/phase1b-report.md`
4. 이 프롬프트

기준 branch는 `harness-dev`다. 현재 HEAD의 Phase 0·1A·1B Codex 커밋을 유지한다.

---

## 이전 Phase에서 Codex가 보정한 사항

Phase 1B는 Codex 검증에서 최초 상태로는 FAIL 수준의 실행 안전 결함이 발견됐고, 아래 보정 후 `PASS_WITH_FIXES`로 승인됐다. Phase 1C에서 이 정책을 우회하거나 완화하지 않는다.

- `ActionPolicy`는 `core`의 상태 없는 순수 Policy다. Compose, 파일 I/O, JSON DTO, process에 의존하지 않는다.
- 부록 B 20개 행은 exact `primary`/전체 `allowed` set 데이터 기반 테스트로 고정돼 있다.
- `execution_ready` 공통 gate:
  - `phase == ExecutionReady`
  - `queue.status == Active`
  - nonblank active card/slice ID
  - ops validation passed
  - execution/closure 미완료
- code/doc CTA에는 nonblank `authorizedTargetFile`이 추가로 필요하다.
- validation-only는 target이 없을 수 있으며 별도 `UiAction.RunValidationSlice`다. `-RunExecutionWrapper validation`은 존재하지 않고 Phase 4 전까지 command에 연결하지 않는다.
- 실존 blocking stop reason 11종, `QueueBlockedReason.Other`, unknown domain, human action, 완료 flag 불일치는 fail-closed한다.
- malformed/encoding/schema/unknown의 raw 원문은 domain에 보존하되 사용자 `blockedReason`에 그대로 노출하지 않는다.
- `state.execution_wrapper`는 최근 실행 시도 값이며 active slice kind와 동일시하면 안 된다.
- Phase 1B 최종 로컬 검증: targeted PASS, core 32 + infra 45 + Compose 1 = 총 78 tests PASS, 전체 `check` PASS.

Phase 1C 구현 중 정책 입력 조립이 불완전하면 optimistic value를 만들지 말고 `Unknown`/`null`로 전달해 Recovery/read-only 결과를 유지한다.

---

## Phase 1C 목표

PowerShell이나 Harness command를 실행하지 않는 **실데이터 read-only Cockpit**을 만든다.

1. `AppViewModel`과 단일 `StateFlow<HrnsUiState>` 도입
2. Composable 안의 `remember { buildProjections() }` 및 동기 파일 탐색 제거
3. 수동 새로고침과 2~5초 mtime polling
4. Phase 1A Reader + Phase 1B ActionPolicy를 application/presentation 경계에서 조립
5. 실제 workspace/day의 상태를 mock 없이 화면에 표시
6. stale/malformed/unsupported/access failure를 숨기지 않고 3분리 UX로 표시
7. 화면에서 단 하나의 primary CTA를 강조하고 allowed가 아닌 action은 노출/활성화하지 않음
8. `core.projection`의 UI read model을 `composeApp/presentation/model`로 이동

---

## 설계 경계

### 계층 방향

```text
Compose UI
  -> AppViewModel / presentation assembler
  -> core ports + ActionPolicy + domain
  <- infra adapters
```

- `core`는 Compose, lifecycle ViewModel, kotlinx serialization 구현, NIO adapter를 참조하지 않는다.
- ViewModel은 concrete Reader를 직접 생성하지 않는다. composition root에서 의존성을 한 번 조립해 주입한다.
- Composable은 domain raw 문자열을 비교하거나 phase/status/action label로 분기하지 않는다.
- ViewModel은 화면용 문자열 formatter가 아니라 refresh/polling/state reduction을 담당한다. projection 조립은 작은 presentation assembler/mapper로 분리한다.
- 새 interface는 테스트/교체 경계가 실제로 필요한 경우에만 추가한다.
- 조회와 향후 command를 한 God Service로 합치지 않는다.

### StateFlow와 coroutine

- `StateFlow<HrnsUiState>` 또는 의미가 같은 단일 불변 상태 흐름을 사용한다.
- 파일 I/O와 probe는 `Dispatchers.IO` 또는 주입된 IO dispatcher에서 수행한다.
- UI state 갱신은 순서가 뒤집히지 않게 latest refresh만 반영한다.
- polling job은 ViewModel 생명주기당 하나만 생성하고 recomposition마다 늘어나지 않는다.
- polling 간격은 2~5초이며 테스트 가능한 delay/ticker 경계를 둔다.
- manual refresh는 동일한 load use case를 사용한다.
- ViewModel 정리 시 polling job을 취소한다.
- 테스트에서 실제 수 초 sleep을 사용하지 않는다. 필요하면 coroutine test dependency를 최소 추가한다.

### 실데이터와 mock

- production 기본 경로는 `EnvironmentWorkspaceConfigProvider`, workspace/day selection policy, `WorkflowStatePort` 실구현을 사용한다.
- `MockProjectionProvider`는 명시적 demo mode에서만 접근 가능하다.
- 실데이터 읽기 실패 시 mock projection으로 fallback하지 않는다.
- 개인 경로, `auziraum`, 특정 날짜를 production에 하드코딩하지 않는다.
- Phase 1D Registry/UI 파일을 workspace에 만들지 않는다.

### active slice kind

Phase 1B의 `ActionContext.activeSliceKind`는 정책 입력이다. Phase 1C에서 이를 조립할 때:

- `RawJsonValue.toString()`, Markdown 문구, label, `next_action` 문자열 검색으로 code/doc/validation을 추론하지 않는다.
- live fixture와 Phase 1A DTO가 안정적인 wrapper 필드를 제공하는지 먼저 확인한다.
- 안정적인 실제 JSON key가 확인되면 ACL/DTO/mapper 경계에 최소 typed projection을 추가하고 code/doc/validation-only/unknown 회귀 테스트를 만든다.
- 확인할 수 없으면 `null` 또는 `Unknown(raw)`을 사용해 fail-closed한다. 화면을 보기 좋게 만들려고 `Code`를 기본값으로 넣지 않는다.
- `state.execution_wrapper`를 active slice kind로 재사용하지 않는다.
- validation-only를 fake execution wrapper로 만들지 않는다.

### compatibility·boundary·process 입력

- Phase 2의 `kit-version.json`과 JSON diagnostics가 아직 없으면 compatibility를 `Supported`로 하드코딩하지 않는다. 확인할 수 없는 계약은 `Unknown`으로 전달해 실행을 잠근다.
- Phase 1D의 완전한 `BoundaryPolicy`를 선구현하지 않는다. 기존 path probe가 증명하는 범위만 표시하고, 양방향 포함·real path를 확인하지 못하면 `BoundaryStatus.Unknown`을 사용한다.
- Phase 3 lock/process adapter를 만들지 않는다. 실제 lock을 검사했다고 가장하지 않는다.
- 아직 command 실행은 연결하지 않으므로 CTA는 안내/정책 결과다. manual refresh 같은 read-only action만 실제 동작시킬 수 있다.

---

## 화면에 필요한 상태

`HrnsUiState`와 presentation model은 최소한 다음을 표현한다.

- 초기 로딩 / 정상 / stale / error
- project name 또는 연결 상태
- profile
- 선택 날짜와 오늘/과거 read-only 여부
- phase / status
- queue status
- active card ID / active slice ID
- authorized target
- stop reason / blocked reason의 안전한 표시
- required 4-file artifact readiness
- ops validation
- closure / execution completed
- 마지막 정상 읽기 시각과 마지막 읽기 시도 시각
- `RecommendedActions.primary` 단 하나
- 전체 allowed set에서 파생한 UI 가능 여부
- 오류 3분리:
  1. 발생한 일
  2. 마지막 정상 projection 보존 여부
  3. 사용자가 할 수 있는 다음 행동

raw session ID, token, secret, 응답 원문, raw log를 표시하지 않는다. unknown 값의 진단 표시는 Phase 1B 안전 문구를 우회하지 않으며, 표시가 필요하면 masking/요약된 typed label을 사용한다.

---

## projection 이동

`core/src/main/kotlin/io/hrns_now/core/projection/ProjectionModels.kt`의 UI 전용 model을 `composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/model/`로 이동한다.

- core domain/result/port를 presentation에 끌어올 수는 있지만 presentation model을 core에 남기지 않는다.
- demo provider와 화면 import를 새 package로 갱신한다.
- StateReadResult → UI projection 변환 중 UI 전용 mapper는 presentation으로 이동한다.
- core에 남겨야 할 domain-neutral result가 있다면 이유를 보고서에 명시한다.
- 대규모 UI 재설계나 테마 교체는 하지 않는다.

---

## 테스트 필수 항목

### ViewModel/application

- 초기 load가 IO dispatcher에서 실행되고 단일 UiState를 발행
- manual refresh가 실제 Reader를 다시 호출
- polling이 mtime 변경 시 refresh
- 동일 mtime이면 불필요한 parse/state churn을 만들지 않음
- polling job이 중복 생성되지 않음
- ViewModel 종료 시 polling 취소
- 늦게 끝난 이전 refresh가 최신 결과를 덮지 않음
- production load 실패 시 demo/mock으로 fallback하지 않음

### Result/projection

- Success → phase/status/queue/active/target/artifact/ops/closure/CTA 표시
- Malformed + last-known-good → stale 표시, 실행 CTA 없음
- EncodingError/UnsupportedSchema/AccessDenied/Missing 각각의 3분리 오류
- 과거 날짜 → read-only + mutating action 없음
- unknown compatibility/boundary/active slice → fail-closed
- live `execution_blocked + dispatch_contract_mismatch` 동등 fixture → Recovery Center primary
- action label은 typed `UiAction`을 presentation에서 매핑하고 label을 action ID로 사용하지 않음
- 한글·공백 workspace 경로

### Compose

- 화면은 전달받은 `HrnsUiState`만 렌더링
- primary CTA 하나만 강조
- stale/error 상태가 mock 정상 화면으로 바뀌지 않음
- recomposition으로 polling/Reader가 추가 생성되지 않음

Phase 1B의 모든 정책 테스트와 Phase 1A Reader/Phase 0 artifact 테스트가 계속 통과해야 한다.

---

## 금지사항

- UI 또는 ViewModel의 `WORKFLOW_STATE.json` 쓰기
- Composable의 파일 I/O, JSON parsing, status 문자열 분기
- `remember { buildProjections() }`에서 infra probe 수행
- 읽기 실패 시 `MockProjectionProvider` fallback
- `ActiveSliceKind.Code`/compatibility Supported/boundary Valid의 낙관적 기본값
- production의 fixture 프로젝트·날짜·개인 경로
- `ProcessBuilder`, PowerShell command, lock, Registry 구현
- `-RunExecutionWrapper validation`, 자동 resume, `--continue`
- timeout 없는 실제 sleep 기반 테스트
- raw session ID/token/secret 표시·저장
- 테스트 삭제·skip, 계약 약화, Phase 1D 선구현
- Git commit/push

---

## 검증 순서

실제 task 존재를 확인하고 다음 순서로 실행한다.

```powershell
.\gradlew.bat :core:test --rerun-tasks --no-daemon --console=plain
.\gradlew.bat :infra:test --rerun-tasks --no-daemon --console=plain
.\gradlew.bat :composeApp:jvmTest --rerun-tasks --no-daemon --console=plain
.\gradlew.bat check --no-daemon --console=plain
```

추가 확인:

- production core에 Compose/serialization/process 의존성 없음
- Composable 안의 file/probe/Reader 호출 없음
- mock fallback 없음
- polling 생성 지점 하나
- fake validation wrapper 없음
- generated output/secret 미포함
- `git diff --check`

---

## 산출물

1. Phase 1C production 코드
2. ViewModel/presentation/Compose 회귀 테스트
3. `doc/phase_reports/phase1c-report.md`

보고서에는 다음을 기록한다.

- 변경 파일과 계층별 책임
- 실제 workspace/day 선택 및 Reader 조립 방식
- dispatcher/polling/manual refresh 구조
- stale/error 3분리 규칙
- active slice kind 조립 근거 또는 fail-closed 사유
- compatibility/boundary/process 입력의 현재 한계
- demo mode와 production mode 분리 방식
- 테스트 명령·실제 결과·test count
- 설계 문서의 SOLID/패턴 준수 근거
- 잔여 위험과 Phase 1D 경계
- Claude가 commit하지 않았음을 명시

완료 후 working tree를 그대로 두고 Codex 검증을 요청한다.
