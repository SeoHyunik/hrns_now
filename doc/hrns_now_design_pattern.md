# HRNS-NOW Kotlin 아키텍처 규범

- 기준일: 2026-08-06
- 대상: 현재 production source
- 상위 계약: [현행 계획과 외부 계약](./hrns_now_claude_plan.md)

이 문서는 현재 코드의 설계 규범이다. 과거 구현 순서와 교차검토 연혁은 제거했으며, source KDoc이 참조하는 section 번호는 유지한다.

# 1. 최종 설계 결론

HRNS-NOW는 다음 조합을 사용한다.

```text
Hexagonal Architecture
+ Anti-Corruption Layer
+ CQRS-lite
+ typed Command
+ State Machine / Policy
+ MVVM + Unidirectional Data Flow
+ Projection
+ Repository
+ Result
```

핵심 목적은 외부 Harness 계약 변화와 Windows I/O를 domain에서 격리하고, UI가 파일·문구를 추측해 실행하지 못하게 하는 것이다.

# 2. 전체 아키텍처

```text
Compose UI
  ↓ UiEvent
AppViewModel
  ↓ use case / port
core domain + policy
  ↑ port implementation
infra adapter
  ↓
Registry / filesystem / Git / PowerShell / process / live Harness Kit
```

데이터 조회 흐름:

```text
Registry/runtime resolve → compatibility/boundary → day/State/artifact probe
→ policy → presentation projection → Compose
```

명령 흐름:

```text
UiAction → ActionPolicy → typed HarnessCommand → argument encoder
→ lock → process → State reread → unlock → projection refresh
```

# 3. 최상위 패턴: Hexagonal Architecture

## 3.1 선택 이유

Harness Kit은 별도 제품이며 PowerShell, JSON, filesystem, process exit code라는 외부 계약을 가진다. domain이 이 구현에 결합하면 Kit 변경, test fixture, Windows 차이, 배포 형태가 모두 policy 코드로 새어 들어온다.

따라서 `core`는 domain model, policy, port, use case, typed result만 가진다. `infra`가 외부 계약을 구현하고 `composeApp`이 composition과 UI를 담당한다.

금지 dependency:

- `core` → Compose
- `core` → kotlinx serialization DTO
- `core` → `Files`, `ProcessBuilder`, Windows API
- domain model → `%APPDATA%` 또는 특정 drive
- UI → concrete infra adapter 직접 호출

## 3.2 Port 예시

현재 주요 port:

- `WorkflowStatePort`
- `HarnessRunnerPort`
- `RuntimeSourceResolverPort`
- `KitVersionManifestPort`
- `ProjectRegistryPort`
- `ProcessLockPort`
- `WorkspaceRecoveryDiagnosticsPort`
- `RepositoryBridgeProbePort`
- `RequestWriterPort`
- `TodayStrategyReaderPort`
- Git status와 preference port

Port는 외부 예외나 raw JSON 대신 domain input과 typed result를 노출한다.

## 3.3 Adapter 예시

- `JsonWorkflowStateAdapter`
- `JsonKitVersionManifestAdapter`
- `JsonProjectRegistryAdapter`
- `PowerShellHarnessAdapter`
- `JvmProcessExecutor`
- `DefaultKitRuntimeResolver`
- `LocalProcessLockAdapter`
- `WorkspaceArtifactProbe`
- `RepositoryBridgeProbe`
- `RequestInboxWriterAdapter`

Adapter는 DTO, filesystem, encoding, exception 분류, process stdout/stderr를 알아도 되지만 domain policy를 결정하지 않는다.

# 4. Harness와 HRNS-NOW 사이의 Anti-Corruption Layer

Harness의 JSON은 외부 wire contract다. DTO는 nullable field와 `ignoreUnknownKeys=true`로 확장을 허용하고, 별도 mapper가 requiredness와 type을 판정한다.

```text
external JSON
→ nullable DTO
→ explicit mapper validation
→ domain model / sealed result
```

규칙:

- missing, null, empty string을 동일하게 뭉개지 않는다.
- schema major는 지원 범위 밖이면 `UnsupportedSchema`다.
- higher minor와 unknown field는 알려진 major 안에서 보존·무시할 수 있다.
- malformed JSON, invalid UTF-8, access denied, missing file을 구분한다.
- unknown taxonomy 값은 raw value를 보존하는 typed `Unknown`으로 매핑한다.
- readiness 필수값을 nullable 기본값으로 은폐하지 않는다.

현재 live onboarding에서 `required_next_action` writer drift가 발견된 이유도 이 ACL이 의도대로 fail-closed했기 때문이다. 외부 writer 결함을 숨기기 위해 mapper를 느슨하게 만들지 않는다.

# 5. CQRS-lite

조회와 명령은 분리하지만 별도 bus나 framework를 도입하지 않는다.

Query:

- Registry, runtime, State, artifact, Git, lock을 읽는다.
- domain policy를 계산한다.
- immutable projection을 만든다.

Command:

- 요청 저장
- 프로젝트 등록·활성 선택
- onboarding
- Doctor/Validate-Ops
- bootstrap/planning/replan/execution/closure

## 5.3 분리해야 하는 이유

- 표시 label은 locale과 UX에 따라 바뀐다.
- action ID와 command kind는 machine contract다.
- 조회가 성공했다고 쓰기를 허용할 수 없다.
- 실행 결과는 다시 State를 조회해야 확정된다.
- UI는 command line을 조립하거나 stdout을 해석하지 않는다.

# 6. Command Pattern

`HarnessCommand`는 실행 가능한 외부 동작을 closed hierarchy로 표현한다.

```text
Doctor
ValidateOps
OnboardProject
BootstrapDay
RunPlanning
RunReplan
RunExecution
ValidateClosure
```

`HarnessCommandEncoder`는 `powershell.exe -NoProfile -ExecutionPolicy Bypass -File`과 독립 argument list를 만든다. shell 문자열 quoting에 의존하지 않는다.

## 6.1 금지 방식

- 사용자 입력으로 임의 script/argument 실행
- command line 한 문자열 조립
- label 또는 raw State string을 그대로 option으로 전달
- `--continue` 같은 존재하지 않는 우회 인자
- planning/replan/closure option을 같은 pass에 잘못 결합

## 6.2 표준 방식

외부 execution wrapper 계약은 `none|code|doc|auto`다. domain의 outbound `ExecutionWrapper`는 `Code`, `Doc`, `Auto`를 표현하지만 UI dispatch는 active slice가 명시한 code/doc만 생성한다. `none`은 실행하지 않음을 나타내는 State/CLI 기본값이지 outbound 실행 action이 아니다.

Planning reason과 Replan reason은 live `ValidateSet`과 동일한 enum을 사용한다. Replan은 빈 reason을 허용하지 않는다. Closure는 `-ValidateForClosure` 단독 pass다.

`queue.active`는 pointer이므로 wrapper와 authorized target은 State의 권위 있는 별도 field와 policy 조건에서 읽는다.

# 7. State Machine + Policy Pattern

외부 State taxonomy는 typed mapping을 통과한다. 알려진 phase/status/stop reason은 enum으로, unknown은 raw 값을 가진 typed variant로 보존한다.

`ActionPolicy`는 다음 context를 종합한다.

- runtime resolution과 manifest compatibility
- Kit/workspace/repository boundary
- 오늘/과거 날짜
- State read result
- required artifact readiness
- active pointer, wrapper, authorized target
- ops validation, closure flags
- process lock과 external modification 가능성

Policy 결과는 `Allowed` 또는 reason key를 가진 blocked decision이다. Compose 조건문이 정책을 재구현하지 않는다.

# 8. MVVM + 단방향 데이터 흐름

```text
Compose → HrnsUiEvent → AppViewModel → use case/port
→ HrnsUiState → Compose
```

Compose는 projection을 그리며 filesystem이나 PowerShell을 직접 호출하지 않는다. ViewModel은 lifecycle과 coroutine 조율을 담당하지만 domain 판정을 소유하지 않는다.

## 8.3 UI State

`HrnsUiState`는 화면에 필요한 projection과 진행 상태를 포함한다. raw domain aggregate나 raw external JSON을 그대로 노출하지 않는다.

핵심 원칙:

- immutable snapshot
- locale-dependent string은 presentation에서 생성
- active project 표시명은 Registry projection으로 안정적으로 유지
- process running/notice와 domain State를 구분
- raw secret/session/log 부재

# 9. Projection Pattern

Projection assembler는 domain result를 화면 전용 model로 바꾼다.

예:

- `CockpitProjectionAssembler`
- run/recovery/closure projection assembler
- `CockpitUiStateAssembler`
- reason key string mapper

Projection은 정형화된 title, detail, tone, action label을 만들 수 있지만 실행 허용을 새로 결정하지 않는다. runtime problem과 workflow State problem은 별개의 diagnostic surface로 유지한다.

# 10. Repository Pattern

Repository/port는 저장 위치와 serialization을 감춘다. `JsonProjectRegistryAdapter`는 UTF-8, atomic move, corruption quarantine, partial-entry recovery를 담당한다.

Registry는 다음을 보장한다.

- schema major 검증
- duplicate/invalid entry 격리
- last active ID가 실제 entry를 가리키는지 검증
- Registry path가 Kit/workspace/repository 내부가 아님
- DefaultKit에는 absolute `kit_root`를 저장하지 않음
- ExternalKit에만 normalized absolute root 저장

# 11. Strategy/Policy Pattern

주요 policy:

- `CompatibilityPolicy`
- `BoundaryPolicy`
- `ActionPolicy`
- `ClosurePolicy`
- `LockStalePolicy`
- `WorkspaceDaySelectionPolicy`
- `ExternalExecutionDetectionPolicy`

Policy는 순수 함수 또는 순수 class이며 I/O를 수행하지 않는다. Boundary는 lexical과 real path를 모두 사용하고 불명확하면 fail-closed한다. Closure는 artifact, ops, queue, active slice, lock, Git을 함께 평가한다.

# 12. Decorator Pattern

cross-cutting concern은 port 구현을 감싸는 decorator로 분리한다.

예:

- `SecretMaskingProcessRunner`가 process result의 check/message/snippet을 masking
- lock heartbeat와 UI lifecycle callback
- timeout/cancellation과 child process tree terminator

실행 순서:

```text
lock acquire → heartbeat → masked runner → State reread
→ heartbeat stop → lock release
```

exception/cancel 경로에서도 `finally`로 lock을 해제한다.

# 13. Optimistic Concurrency Pattern

`REQUEST_INBOX.md` 저장은 load 시점의 `FileVersion`과 save 직전 version을 비교한다.

```text
load content + version
→ user edit
→ compare current version
→ atomic replace 또는 Conflict
```

Conflict에서는 기존 파일과 사용자 draft를 모두 보존한다. UI가 자동 병합하거나 State/structured request를 함께 쓰지 않는다.

# 14. Result Pattern과 Projection 메타

외부 실패는 sealed result로 분류한다.

State 예:

- `Success`
- `Missing`
- `Malformed`
- `EncodingError`
- `UnsupportedSchema`
- `AccessDenied`
- stale last-known-good

Process 예:

- exited result
- start failed
- timed out
- cancelled

Adapter는 사실을 분류하고, policy는 허용 여부를 결정하며, projection은 사용자 설명을 만든다. exception text나 raw output을 domain reason으로 사용하지 않는다.

# 15. 클래스·함수·람다 기준

Class/interface:

- stateful adapter
- port와 use case
- policy가 여러 dependency나 설정을 가질 때
- lifecycle과 cancellation을 소유할 때

순수 함수:

- enum/taxonomy mapping
- DTO→domain 변환의 작은 단계
- label/formatting
- immutable projection 계산

Lambda:

- clock, path provider, move function처럼 test seam이 작고 의미가 명확할 때

복잡한 policy, filesystem adapter, process lifecycle을 lambda로 숨기지 않는다.

# 16. 실행 Use Case의 최종 형태

`ExecuteHarnessActionUseCase`는 policy가 허용한 action만 받는다. 호출자는 runtime source를 이미 resolved root로 변환한다.

```text
acquire lock
→ map action to command
→ runner.execute
→ workflowState.read while lock held
→ release lock
→ typed outcome
```

Onboarding은 daily action과 별도 use case다.

```text
enter-project
→ validate-ops JSON
→ bridge 3-file probe
→ daily 4-file probe
→ State reread
```

성공은 모든 evidence가 충족될 때만 반환한다.

# 17. 패키지 구조

```text
core/domain/model
core/domain/policy
core/port
core/result
core/usecase

infra/serialization
infra/process
infra/runtime
infra/registry
infra/lock
infra/request
infra/recovery
infra/security
infra/git

composeApp/presentation/model
composeApp/presentation/mapper
composeApp/presentation/viewmodel
composeApp/ui
```

파일 이동은 package 의미와 dependency 방향을 개선할 때만 수행한다. 이름만 바꾸는 대규모 재배치는 피한다.

# 18. SOLID

- SRP: parsing, mapping, policy, execution, projection을 분리한다.
- OCP: unknown field/taxonomy 확장을 기존 domain 안정성 안에서 수용한다.
- LSP: port 구현은 같은 typed result 의미를 지킨다.
- ISP: UI가 필요 없는 mutation/query method를 하나의 거대 service에 묶지 않는다.
- DIP: use case와 ViewModel은 port에 의존한다.

# 19. 사용하지 말아야 할 패턴

## 19.1 God ViewModel

ViewModel이 JSON parsing, path validation, command encoding, process control, Git, policy, string rendering을 직접 소유하지 않는다. 새로운 책임은 domain policy, use case, adapter, projection assembler 중 맞는 위치로 이동한다.

그 밖의 금지:

- Service Locator
- string-based state dispatch
- mutable global singleton
- 의미 없는 factory
- 범용 event bus
- UI에서 concrete adapter 생성

# 20. 구현 규범

새 기능은 먼저 외부 계약과 domain type을 정의하고, port/use case, adapter, projection, UI 순으로 연결한다. 현재 source에 없는 추상 계층을 미래 가능성만으로 추가하지 않는다.

### 20.1 Runtime source 규범

`RuntimeSource.DefaultKit`는 개발 source checkout 상대 `.local/harness-kit` 선택이다. Registry에는 선택만 저장하고 absolute path를 저장하지 않는다. source checkout 표지를 찾지 못하는 packaged app에서는 경로를 추측하지 않고 `Missing`으로 fail-closed한다.

`RuntimeSource.ExternalKit(root)`는 사용자가 명시한 absolute path다. resolver는 directory/readability와 required public surface를 확인하고 manifest compatibility를 별도로 판정한다.

두 source 모두 command mapper 이후에는 resolved Kit root 하나로 처리한다. domain action과 command는 internal/external 분기를 알지 않는다.

주의할 capability gap:

- runtime root의 공통 entrypoint가 존재해도 onboarding 전용 `enter-project.ps1` 같은 기능별 entrypoint가 없을 수 있다.
- capability별 실행 전에 해당 public script를 fail-closed 검사한다.
- DefaultKit은 bundled runtime이나 release artifact를 의미하지 않는다.
- live external Kit을 MSI에 임의 복사하지 않는다.

# 21. 테스트 설계

## 21.1 Domain test

외부 시스템 없이 policy, mapping, boundary, closure, compatibility, action decision을 검증한다.

## 21.2 Adapter contract test

encoding, JSON shape, filesystem atomicity, process stdout/stderr, timeout/cancel, Registry corruption, secret masking을 검증한다.

## 21.3 Integration test

mock fixture만 사용하지 않고 live writer가 scratch에 만든 artifact를 production adapter가 읽는 production-to-production test가 필요하다. required field를 추가하면 writer와 parser를 같은 test에서 연결한다.

## 21.4 CI 단계

```text
compile → module tests → full check → contract integration
→ package build → manual native/clean-Windows Gate
```

`UP-TO-DATE`는 강제 재실행 증거가 아니다. native UI와 clean Windows lifecycle은 자동 test로 완료 처리하지 않는다.

# 22. 최종 권장 조합

필수:

- Hexagonal Architecture
- ACL DTO/mapper
- typed command/action/result
- pure policy
- MVVM/UDF
- projection
- repository/atomic persistence
- lock/cancel/secret decorators
- production-to-production contract test

선택적:

- 기능이 실제로 요구할 때만 새로운 adapter 또는 policy 분리
- Secondary LLM은 별도 opt-in diagnostic lane

금지:

- framework나 abstraction 자체가 목표인 설계
- 외부 계약 오류를 nullable default로 은폐
- 테스트 수치만으로 호환성·배포 완료 선언

# 23. 최종 판단

현재 구조의 가장 중요한 속성은 fail-closed다. live Harness artifact가 문서와 다를 때 HRNS-NOW가 임의로 추측하지 않고 차단하는 것은 결함이 아니라 안전 경계다. 해결은 권위 있는 writer·diagnostic·smoke와 production parser를 같은 계약으로 정렬하는 방식이어야 한다.
