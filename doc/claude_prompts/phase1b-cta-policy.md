# HRNS-NOW Phase 1B 구현 프롬프트 — Typed CTA Policy

## 역할

너는 `hrns_now` 프로젝트의 1차 구현 담당자다.

이번 작업은 **Phase 1B — CTA Policy** 하나만 구현한다. Phase 1C ViewModel/Cockpit, Phase 1D Registry, Phase 2 Harness 변경, PowerShell process/lock/masking과 실제 실행 연결은 구현하지 않는다.

Claude는 Git commit을 만들지 않는다. 소스·테스트·Phase 보고서를 working tree에 남기고, Codex가 독립 검증·보정 후 커밋한다.

## 저장소와 필수 문서

```text
저장소            : S:\dev\project\hrns_now
브랜치            : harness-dev
Harness Kit live  : D:\harness-kit
live fixture      : D:\harness-workspaces\auziraum\2026-06-26\WORKFLOW_STATE.json
최종 계획서       : doc/hrns_now_claude_plan.md
설계 규범         : doc/hrns_now_design_pattern.md
이전 Phase 보고서 : doc/phase_reports/phase1a-report.md
```

작업 전에 위 세 문서를 **전부** 읽고 `git status --short`, `git log --oneline -n 10`, 현재 패키지 구조를 확인한다. 설계가 충돌하면 최종 계획서의 불변 원칙과 Phase 계약이 우선한다.

## Git 운영

- Claude는 commit, amend, rebase, reset, stash, push를 하지 않는다.
- Codex Phase 1A 커밋 이후의 변경만 만든다.
- 기존 파일을 대규모 포맷하거나 관련 없는 리팩터링을 하지 않는다.
- 보고서는 `doc/phase_reports/phase1b-report.md`에 UTF-8 without BOM으로 작성한다.

## Phase 1A에서 Codex가 확정·보정한 기반

다음 사항은 이미 검증된 계약이다. 되돌리거나 우회하지 않는다.

1. `JsonWorkflowStateAdapter`는 `<dayRoot>\WORKFLOW_STATE.json`만 읽고 절대 쓰지 않는다.
2. DTO/Parser/Mapper/Sanitizer/Adapter가 `infra.serialization` ACL에 분리돼 있다.
3. `StateReadResult`는 Success/Missing/Malformed/EncodingError/UnsupportedSchema/AccessDenied를 구분한다.
4. `StateReadResult.toProjection(source)`는 malformed/encoding 실패의 last-known-good을 `malformed=true, stale=true`로 전달한다.
5. known live taxonomy 보정:

   ```text
   WorkflowStatus.RequestIntakePending
   QueueStatus.PlanningRequired
   관측된 정상 lifecycle StopReason typed 값
   ```

6. dispatch 의미는 반드시 다음처럼 구분한다.

   ```text
   state.stop_reason=dispatch_contract_mismatch
       → StopReason.DispatchContractMismatch

   queue.blocked_reason=dispatch_metadata_conflict
       → QueueBlockedReason.DispatchMetadataConflict
   ```

   `dispatch_metadata_conflict`를 `StopReason`으로 되돌리지 않는다. 정책에서 raw 문자열 비교도 하지 않는다.

7. `role_sliced` 등 raw 중첩 JSON은 `RawJsonValueSanitizer`가 session ID/token/secret을 치환한 후에만 domain으로 전달한다.
8. 필수 안전 boolean과 최소 필드 누락은 false/default로 숨기지 않고 `Malformed`가 된다.
9. normalized absolute path, metadata 재검사, 결정적 AccessDenied/metadata race 테스트가 있다.
10. 상세 계약 미확정 필드(`current_slice`, `slice_queue`, `role_sliced`, `usage_guard`)는 sanitized raw다. CTA 정책이 이 raw JSON text를 파싱하거나 문자열 검색하면 안 된다.
11. `WorkflowStatePort.read`는 현재 동기 계약이다. Phase 1B에서는 파일 I/O를 호출하지 않으므로 변경하지 않는다.

## 제품 불변 계약

- 상태 진실은 `WORKFLOW_STATE.json` 하나다.
- Markdown 문구와 stdout 성공 문구로 실행 가능 여부를 결정하지 않는다.
- unknown/malformed/stale/unsupported/access denied에서는 write/execute가 fail-closed다.
- 과거 날짜는 읽기 전용이다.
- 다른 process lock/running 상태에서는 새 실행을 허용하지 않는다.
- 표시 label을 action ID로 사용하지 않는다.
- action policy는 파일, JSON, Compose, NIO, ProcessBuilder를 참조하지 않는다.
- `validation`이라는 wrapper 모드를 만들지 않는다.
- validation-only의 실제 PowerShell mapping은 Phase 4까지 유보한다.
- 자동 resume와 `--continue`를 만들지 않는다.

## 목표

현재 연결/날짜/State/compatibility/boundary/process 조건에서:

1. primary action을 최대 하나만 추천하고,
2. 허용된 typed action set을 반환하며,
3. write/execute 잠금 사유를 보존하는

순수 CTA 정책을 만든다.

Composable과 presentation은 이 결과를 표현만 해야 하며, 이번 Phase에서는 UI에 연결하지 않는다.

## 필수 아키텍처

권장 목표 구조:

```text
core/src/main/kotlin/io/hrns_now/core/
├── domain/model/
│   ├── UiAction.kt
│   ├── RecommendedActions.kt
│   └── ActionContext.kt
└── domain/policy/
    └── ActionPolicy.kt

core/src/test/kotlin/io/hrns_now/core/
└── domain/policy/
    └── ActionPolicyTest.kt
```

파일 수와 이름은 책임을 해치지 않는 범위에서 조정할 수 있다.

### Typed `UiAction`

계획서 3.3의 action을 기준으로 sealed interface/data object를 사용한다.

최소 action:

```text
ConnectProject
SelectWorkspaceDay
EditRequest
RunDoctor
RunOpsValidation
BootstrapDay
RunPlanning
RunReplan
RunCodeSlice
RunDocSlice
RunClosureValidation
OpenRecoveryCenter
ReviewClosure
CloseDay
```

결정표 표현에 꼭 필요한 read/navigation action(예: Refresh, OpenToday, ReviewPlan, ShowCompatibilityIssue, ViewExecutionStatus)은 의미가 겹치지 않게 typed ID로 최소 추가할 수 있다. 한국어 label은 presentation 책임이며 `UiAction` identity가 아니다.

validation-only row가 typed action을 필요로 하면 별도 `UiAction`으로 표현할 수 있지만 다음을 지킨다.

- Phase 1B에는 command mapper/process 연결이 없다.
- `ExecutionWrapperState.Unknown("validation")` 또는 가짜 CLI wrapper를 만들지 않는다.
- `-RunExecutionWrapper validation` mapping을 만들지 않는다.
- 실제 실행 mapping은 Phase 4에서 live 계약으로 확정한다.

### `RecommendedActions`

```kotlin
data class RecommendedActions(
    val primary: UiAction?,
    val allowed: Set<UiAction>,
    val blockedReason: String?,
)
```

불변식:

- `primary == null || primary in allowed`
- primary는 최대 하나
- 동일 action 중복 없음(Set)
- write/execute 잠금 상황에서 해당 action이 `allowed`에 들어가면 안 됨

사용자용 최종 문구 번역은 presentation 책임이지만, 정책이 잠금 진단을 전달할 최소 reason/code를 보존할 수 있다. 문자열 label을 분기 key로 사용하지 않는다.

### `ActionContext`

정책 입력은 domain 값만 가진 immutable context로 만든다.

최소로 표현할 조건:

- 프로젝트 연결 여부
- 날짜 선택 여부
- 선택 날짜가 오늘인지/과거인지
- `StateReadResult` 또는 동등한 typed state-read 상태
- compatibility supported/unsupported/unknown
- boundary valid/invalid/unknown
- process idle/running/locked
- 필요한 경우 validation-only를 표현하는 **typed** slice kind

Phase 1D/2/3 구현체를 선행하지 않는다. 정책 테스트에 필요한 최소 domain gate 타입만 허용한다. boolean soup보다 의미 있는 enum/sealed value를 우선하되, 구현체 하나도 없는 거대한 port/interface/factory를 만들지 않는다.

## 정책 함수

다음과 동등한 순수 API를 제공한다.

```kotlin
fun recommendActions(context: ActionContext): RecommendedActions
```

또는 상태 없는 `ActionPolicy.recommend(context)` class를 사용한다.

금지:

- 파일 읽기
- 시간 직접 조회(`LocalDate.now()` 등)
- Compose 타입
- JSON field/raw text 비교
- ProcessBuilder
- singleton/service locator
- 예외를 삼켜 optimistic action을 반환

오늘 날짜/과거 여부는 호출자가 context로 제공해 테스트가 결정적이어야 한다.

## Guard 우선순위

다음 우선순위를 하나의 정책에 명시적으로 고정한다.

1. 프로젝트 미연결
2. 날짜 미선택
3. State invalid:
   - Missing
   - Malformed
   - EncodingError
   - UnsupportedSchema
   - AccessDenied
   - stale/malformed projection
4. compatibility unsupported/unknown
5. boundary invalid/unknown
6. 과거 날짜
7. 다른 process running/lock
8. unknown domain 값
9. 상태별 정상 분기

상위 guard가 걸리면 하위 상태가 optimistic action을 다시 열면 안 된다.

unknown fail-closed 대상:

- `WorkflowPhase.Unknown`
- `WorkflowStatus.Unknown`
- `QueueStatus.Unknown`
- `ExecutionWrapperState.Unknown`
- `StopReason.Unknown`
- `ArtifactReadinessState.Unknown`
- policy가 실행 판단에 사용하는 기타 unknown gate

diagnostic/read-only action은 허용할 수 있지만 write/execute는 금지한다.

## 결정표

`doc/hrns_now_claude_plan.md` 부록 B의 **모든 행**을 parameterized/data-driven test로 고정한다.

| 조건 | Primary | 필수 금지 |
|---|---|---|
| 프로젝트 미연결 | 프로젝트 연결 | Planning/Execution |
| 날짜 미선택 | 날짜 선택 | Write/Execution |
| 과거 날짜 | 없음 또는 오늘 열기 | 모든 Write/Execution |
| `request_intake_pending` | 요청 작성 | Execution |
| `no_request` | 새 요청 추가/요청 작성 | 빈 Planning 반복 |
| `planning_required` | Planning 실행 | Code/Doc |
| `planning_completed` | 계획 검토 | Closure |
| `execution_ready` + code | Code slice | Doc/target 변경 |
| `execution_ready` + doc | Doc slice | Code/target 변경 |
| validation-only | typed 검증 action | source edit |
| `execution_blocked` | 복구 센터 | 무조건 재실행 |
| `manual_prerequisite_required` | 복구/선행조건 확인 | 자동 실행 |
| `usage_limit_blocked` | 복구 옵션 | 자동 무한 retry |
| `claude_context_limit` | 복구 센터/fresh 안내 | 자동 resume |
| `queue.blockedReason=DispatchMetadataConflict` | 재계획 | execution |
| `execution_completed` | 검증·인계/Closure 검토 | 동일 slice 재실행 |
| `closure_validated` | 다음 날짜 준비 | 오늘 queue 수정 |
| State invalid | 복구 센터 | 모든 Write/Execution |
| compatibility 불일치 | 호환성 안내 | 모든 실행 |
| 다른 process lock/running | 실행 현황 확인 | 새 실행 |

추가 필수 조합 테스트:

- guard 우선순위 충돌(예: 과거 날짜 + execution_ready)
- malformed + last-known-good execution_ready
- unknown status/phase/wrapper/queue/stop reason
- unknown artifact readiness
- code/doc wrapper 상호 배타
- `dispatch_contract_mismatch` stop reason과 `DispatchMetadataConflict` queue marker 구분
- `primary in allowed` 불변식 전 fixture

### live acceptance

sanitize한 auziraum 2026-06-26 shape와 동등한 domain fixture:

```text
phase=execution
status=execution_blocked
executionWrapper=code
stopReason=DispatchContractMismatch
queue.status=active
```

결과:

```text
primary = OpenRecoveryCenter
RunCodeSlice/RunDocSlice/RunPlanning 금지
```

## SOLID·패턴 기준

- SRP: `UiAction` identity, context, policy, test fixture 책임 분리
- OCP: sealed type + 중앙 정책 분기. Composable 여러 곳의 문자열 비교 금지
- LSP: malformed/unknown fixture가 성공 fixture처럼 action을 열지 않음
- ISP: Reader/Registry/Process port를 CTA policy에 합치지 않음
- DIP: core policy는 infra/Compose/NIO/JSON에 무의존
- Policy/State Machine 패턴은 결정표를 코드화하는 데만 사용
- 단순 mapping을 위한 factory/strategy 계층 남발 금지

## 테스트·검증

실제 task를 확인하고 다음 순서로 실행한다.

```powershell
.\gradlew.bat :core:test --rerun-tasks --no-daemon
.\gradlew.bat :infra:test --rerun-tasks --no-daemon
.\gradlew.bat :composeApp:jvmTest --rerun-tasks --no-daemon
.\gradlew.bat check --no-daemon
```

정적 검사:

```text
Composable의 current_status/raw 문자열 CTA 분기 0건
policy의 JSON field/raw JSON text 비교 0건
UiAction label 기반 identity 0건
unknown에서 write/execute 허용 0건
validation wrapper mode 생성 0건
ProcessBuilder/PowerShell 연결 0건
WORKFLOW_STATE.json 쓰기 0건
fixture raw session ID/secret/token 0건
```

## 완료 보고

`doc/phase_reports/phase1b-report.md`에 다음을 포함한다.

- 목표와 실제 변경 파일
- 계획서/설계 규범 적용 근거
- ActionContext/UiAction/RecommendedActions 설계
- guard 우선순위
- 부록 B 각 행과 테스트 case의 대응표
- unknown/malformed/과거 날짜/lock fail-closed 근거
- live execution_blocked acceptance 결과
- targeted/module/full 테스트 결과
- 알려진 한계
- Phase 1C만 다음 허용 Phase임을 명시
- Git commit을 만들지 않았음을 명시

## 종료 기준

- typed `UiAction`, `RecommendedActions`, immutable ActionContext
- 순수 CTA policy
- 부록 B 전 행 data-driven test
- unknown/malformed/stale/unsupported/access denied fail-closed
- 과거 날짜와 lock/running에서 write/execute 차단
- execution_blocked live fixture에서 Recovery Center primary
- code/doc 상호 배타
- validation-only를 fake wrapper로 매핑하지 않음
- UI/Process/Registry 선행 구현 없음
- 전체 `check` PASS
- source/test/report만 working tree에 남고 commit 없음
