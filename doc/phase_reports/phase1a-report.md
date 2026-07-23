# Phase 1A Report — `WORKFLOW_STATE.json` Reader

- **작성일:** 2026-07-23
- **범위:** Phase 1A — State Reader만 해당
- **기준 문서:** `doc/hrns_now_claude_plan.md`, `doc/hrns_now_design_pattern.md`
- **작업 브랜치:** `harness-dev`
- **기준 커밋:** `22bbae5` (`fix: Phase 0 하네스 계약과 CI 기반 정렬`)
- **검증 방식:** Claude 미커밋 구현을 Codex가 live 소스·Harness fixture·테스트로 독립 검증하고 Phase 1A 범위에서 보정
- **최종 판정:** `PASS_WITH_FIXES`

---

## 1. 목표와 범위

live `<dayRoot>\WORKFLOW_STATE.json`을 읽어 Phase 1A 최소 필드를 typed domain으로 변환하고 다음 실패를 구분한다.

- 파일 없음
- malformed/truncated JSON
- 잘못된 UTF-8
- 미지원 schema major
- 접근 거부
- 읽기 중 metadata 변경

Reader는 상태 파일을 쓰지 않으며, 마지막 정상값(last-known-good)을 보존해 malformed 재읽기 실패 시 stale projection을 만들 수 있어야 한다.

Phase 1B CTA, Phase 1C ViewModel/Cockpit, Phase 1D Registry, PowerShell/lock/masking/Git 실행은 구현하지 않았다.

---

## 2. 최종 구현 구조

### 2.1 Core

```text
core/domain/model/
  WorkflowState, WorkflowPhase, WorkflowStatus, StopReason
  WorkflowQueue, QueueStatus, QueueBlockedReason
  ArtifactsState, OpsValidationState, ClosureState
  ExecutionWrapperState, SchemaVersion, FileVersion, RawJsonValue

core/domain/policy/
  StateReadRetryPolicy

core/port/
  WorkflowStatePort

core/result/
  StateReadResult
  StateReadProjectionMapper
```

- domain은 `kotlinx.serialization`, JSON field annotation, 파일 읽기 구현을 모른다.
- `Unknown(raw)`은 phase/status/stop reason/wrapper/queue/artifact 값의 원문을 보존한다.
- `StateReadResult`는 Reader 실패를 sealed type으로 구분한다.
- `StateReadProjectionMapper`는 `Malformed/EncodingError + lastKnownGood`을 `malformed=true, stale=true`인 기존 `Projection` 계약으로 변환한다.

### 2.2 Infra

```text
infra/serialization/
  HarnessWorkflowStateDto
  WorkflowStateParser
  WorkflowStateMapper
  RawJsonValueSanitizer
  JsonWorkflowStateAdapter
```

- DTO: Harness 외부 JSON shape, `internal`
- Parser: decoded text → DTO, `ignoreUnknownKeys=true`
- Mapper: DTO → domain, 필수 필드 검증과 known/unknown mapping
- Sanitizer: raw 중첩 JSON의 session ID/token/secret 재귀 치환
- Adapter: UTF-8/BOM, metadata 재검사, retry, schema major, last-known-good, hash
- `WorkflowStateFileAccess`: metadata 변화와 접근 거부를 결정적으로 테스트하기 위한 infra 내부 경계

---

## 3. Codex 독립 검증에서 발견·보정한 사항

### 3.1 Critical — raw session ID 보존

live fixture의 값은 출력하지 않고 property name만 검사한 결과, 실제 `state.role_sliced.stages[]`에 `session_id`가 존재한다.

Claude 구현은 `role_sliced` 전체를 `RawJsonValue`로 직렬화해 domain에 보존했으므로 raw session ID 저장·표시 금지 불변 계약을 위반할 수 있었다.

보정:

- `RawJsonValueSanitizer`를 ACL 내부에 추가
- `session_id`, `*_session_id`, token, authorization, api key, secret, password, private key 계열 property를 중첩 object/array 전체에서 `[REDACTED]`로 치환
- 실제 값 대신 가상 session ID/token을 사용하는 회귀 테스트 추가

### 3.2 Major — known live taxonomy 누락

`D:\harness-workspaces\auziraum`의 여러 날짜 State를 값만 집계한 결과 다음 실존 값이 `Unknown`으로 잘못 떨어졌다.

- `current_status=request_intake_pending`
- `queue.status=planning_required`
- 정상 lifecycle stop reason:
  - `request_intake_pending`
  - `planning_required`
  - `planning_completed`
  - `ready_for_next_slice`
  - `execution_queue_completed`

보정:

- `WorkflowStatus.RequestIntakePending`
- `QueueStatus.PlanningRequired`
- 관측된 정상 lifecycle `StopReason` 타입과 실제 Harness 리터럴 mapping
- unknown 미래 값은 계속 `Unknown(raw)`으로 보존

### 3.3 Major — `dispatch_metadata_conflict` 필드 의미 혼동

live Harness grep 결과:

- `dispatch_contract_mismatch`: 실행 단계의 실제 `state.stop_reason`
- `dispatch_metadata_conflict`: planning queue의 `blocked_reason`/`purpose_marker`

보정:

- `StopReason.DispatchContractMismatch` 유지
- `QueueBlockedReason.DispatchMetadataConflict`를 별도 typed marker로 추가
- Phase 1B가 raw 문자열을 비교하지 않고 재계획 정책을 적용할 수 있게 함
- 계획서와 설계 규범의 잘못된 `StopReason.DispatchMetadataConflict` 예시를 실제 계약에 맞게 정정

### 3.4 Major — Reader 예외 누출과 source version 경합

초기 Adapter는 다음 문제가 있었다.

- 선행 `Files.exists()`가 접근 거부를 `Missing`으로 오판할 수 있음
- metadata 조회의 `AccessDeniedException`/`IOException`이 `StateReadResult` 밖으로 누출될 수 있음
- 파싱 후 mtime을 다시 읽어 안정적으로 검증한 snapshot과 다른 시각을 `FileVersion`에 기록할 수 있음
- metadata 변경 테스트가 실제 thread timing에 의존해 변경 감지 branch를 보장하지 못함

보정:

- 선행 `Files.exists()` 제거
- normalized absolute path를 cache/result key로 사용
- metadata 읽기 결과를 `Found/Missing/AccessDenied/RetryableFailure`로 분리
- 읽기 후 안정 snapshot의 mtime/size로 `FileVersion` 생성
- 읽은 byte 길이와 snapshot size도 비교
- internal file-access boundary로 metadata 변경과 접근 거부 테스트를 결정적으로 고정

### 3.5 Major — 필수 안전 필드 누락을 `false`로 은폐

초기 Mapper는 `human_action_required`, 완료/Closure boolean, `ops_validation.passed` 등이 누락돼도 `false`로 대체했다. 특히 `human_action_required` 누락을 false로 바꾸면 향후 CTA가 잘못 열릴 수 있다.

보정:

- Phase 1A 최소 계약의 안전 판단 필드는 누락/explicit null 모두 `Malformed`로 처리
- `required_next_action`, `next_action`, `execution_wrapper`, stop/blocked/failed reason, resume/authorized target도 필드 존재를 검증
- 값이 존재하는 빈 문자열은 계약에 따라 `null` 또는 `ExecutionWrapperState.None`으로 해석
- 상세 계약이 아직 불명확하고 과거 live state에서 실제 누락된 `current_slice`, `slice_queue`, `role_sliced`, `usage_guard`만 nullable raw 값으로 유지

### 3.6 Major — Result/Projection 연결 누락

`StateReadResult.Malformed.lastKnownGood`만 존재하고 기존 `ProjectionMeta`의 `malformed/stale` 의미로 변환하는 구현이 없었다.

보정:

- `StateReadResult.toProjection(source)` 추가
- Success/Missing/Malformed/EncodingError/UnsupportedSchema/AccessDenied 전 변환 규칙 테스트
- CTA 판단은 추가하지 않고 Result/Projection 경계만 구현해 Phase 1A 범위를 유지

---

## 4. 계획·설계 적합성

| 항목 | 판정 | 근거 |
|---|---|---|
| Hexagonal/DIP | PASS | core port/result/domain은 kotlinx serialization과 NIO 구현을 모름 |
| Anti-Corruption Layer | PASS | internal DTO → Parser → Mapper/Sanitizer → domain |
| SRP | PASS | parsing, mapping, sanitizing, retry/I/O, projection mapping 책임 분리 |
| OCP | PASS | unknown 값 원문 보존, unknown key 무시 |
| LSP | PASS | file-access fake와 NIO 구현이 동일 metadata/read 계약 사용 |
| ISP | PASS | Reader port는 읽기 한 기능만 노출 |
| Result/Projection | PASS | 정밀 Reader 결과와 UI stale/malformed meta를 명시적으로 연결 |
| 과도한 추상화 | PASS | file-access 경계는 접근 거부·metadata race의 결정적 contract test에 한정 |
| Phase 경계 | PASS | CTA/ViewModel/Registry/Process 기능 미포함 |

`WorkflowStatePort.read`는 현재 동기 port다. Phase 1C의 ViewModel/use case가 IO dispatcher에서 호출해야 하며, 실제 coroutine 호출 경계가 도입될 때 suspend 전환 여부를 결정한다. 동기 API를 UI thread에서 직접 호출하는 것은 허용되지 않는다.

---

## 5. Serialization·Reader 정책

### 5.1 JSON/encoding

- `kotlinx-serialization-json`은 infra에만 적용
- `Json { ignoreUnknownKeys = true }`
- strict UTF-8 decoder 사용
- UTF-8 BOM 제거 후 파싱
- invalid UTF-8은 `EncodingError`, JSON 문법/필수 필드 실패는 `Malformed`

### 5.2 schema

- `major.minor` typed parsing
- 지원 major `1`
- major 불일치 → `UnsupportedSchema`
- 상위 minor 허용
- 누락/형식 오류를 `"1.0"` default로 숨기지 않음

### 5.3 retry/last-known-good

재시도 대상:

1. 읽기 전후 mtime/size 불일치
2. snapshot size와 실제 byte 길이 불일치
3. 일시적 metadata/body I/O 오류
4. invalid UTF-8
5. malformed/truncated JSON
6. domain 필수 필드 누락

재시도하지 않는 확정 결과:

- Missing
- AccessDenied
- UnsupportedSchema

기본 최대 3회이며 성공 시 normalized absolute path별 last-known-good을 갱신한다.

---

## 6. 테스트

### 6.1 Phase 1A 신규 테스트

Core 10건:

- retry policy 5
- Result/Projection 변환 3
- live lifecycle stop reason/unknown 보존 2

Infra 34건:

- Parser 5
- Mapper 13
- Adapter 16

합계 44건이며 Phase 0 회귀 테스트와 함께 실행한다.

주요 회귀:

- sanitized live shape
- unknown top-level/nested key
- unknown status/stop reason raw 보존
- known live status/queue/stop reason
- queue conflict marker와 execution stop reason 분리
- 필수 top-level/안전 boolean 누락
- schema major/minor
- truncated retry 성공
- 반복 malformed + last-known-good stale
- BOM/invalid UTF-8
- 결정적 metadata 변경
- 결정적 AccessDenied
- Missing/empty
- 공백·한글 경로
- raw session ID/token redaction

### 6.2 최종 실행 명령

```powershell
.\gradlew.bat :core:test :infra:test --rerun-tasks --no-daemon
.\gradlew.bat :composeApp:jvmTest --rerun-tasks --no-daemon
.\gradlew.bat check --no-daemon
```

최종 결과는 Codex 커밋 직전 재실행 결과를 기준으로 한다.

---

## 7. 정적 안전 검사

| 검사 | 결과 |
|---|---|
| production 코드의 `auziraum`/개인 workspace/특정 live 날짜 하드코딩 | 0건 |
| 창작 Harness 용어 3종 | 0건 |
| `ProcessBuilder`, `--continue`, validation wrapper 모드 | 0건 |
| production의 `WORKFLOW_STATE.json` 쓰기 | 0건 |
| fixture의 실제 raw session ID/secret/token | 0건 |
| generated build output Git 추적 | 0건 |

---

## 8. 잔여 경계

- `current_slice`, `slice_queue`, `role_sliced`, `usage_guard`는 상세 schema가 안정되지 않아 sanitized raw 값이다. Phase 1B 정책은 raw JSON 문자열을 직접 비교하지 않는다.
- validation-only slice의 실제 실행 mapping은 계획대로 Phase 4 착수 시 확정한다. `-RunExecutionWrapper validation`은 만들지 않는다.
- Phase 1C에서 Reader를 UI thread 밖 IO dispatcher에서 호출해야 한다.
- GitHub Actions 원격 실행은 push하지 않았으므로 이번 로컬 검증에 포함되지 않는다.

---

## 9. 다음 허용 Phase

`Phase 1B — CTA Policy`만 허용한다.

다음 구현은 `doc/claude_prompts/phase1b-cta-policy.md`를 따른다. Claude는 commit하지 않고 소스·테스트·보고서만 working tree에 남긴다.

본 보고서와 Phase 1B prompt를 포함한 Codex 후속 커밋의 SHA는 최종 응답에서 보고한다.
