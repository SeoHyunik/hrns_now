# HRNS-NOW Phase 1A 구현 프롬프트 — WORKFLOW_STATE.json Reader

## 역할

너는 `hrns_now` 프로젝트의 1차 구현 담당자다.

이번 작업은 **Phase 1A — `WORKFLOW_STATE.json` Reader** 하나만 구현한다. Phase 1B CTA 정책, Phase 1C ViewModel/Cockpit, Phase 1D Registry, PowerShell 실행 기능은 구현하지 않는다.

Claude는 Git commit을 만들지 않는다. 소스·테스트·Phase 보고서를 working tree에 남기고, Codex가 독립 검증·보정 후 커밋한다.

## 저장소와 기준 문서

```text
저장소             : S:\dev\project\hrns_now
브랜치             : harness-dev
Harness Kit live   : D:\harness-kit
live fixture       : D:\harness-workspaces\auziraum\2026-06-26\WORKFLOW_STATE.json
최종 계획서        : doc/hrns_now_claude_plan.md
설계 규범          : doc/hrns_now_design_pattern.md
이전 Phase 보고서  : doc/phase_reports/phase0-report.md
```

작업 전에 위 세 문서를 전부 읽고 현재 `git status`, 최근 커밋, 실제 패키지 구조를 확인한다. 설계가 충돌하면 최종 계획서의 불변 계약과 Phase 계약을 우선한다.

## Phase 0에서 Codex가 확정한 내용

다음 사항은 이미 검증·보정된 기반이므로 되돌리거나 중복 구현하지 않는다.

1. required daily surface는 다음 4-file뿐이다.

   ```text
   REQUEST_INBOX.md
   TODAY_STRATEGY.md
   DAILY_HANDOFF.md
   WORKFLOW_STATE.json
   ```

2. `REQUEST_STRUCTURED.md`, `<dayRoot>\logs\`, `<projectWorkspaceRoot>\logs\<date>\`는 Optional이다.
3. `WORKDAY_STATE.json`, `WORK_QUEUE.json`은 Legacy이며 readiness에서 제외된다.
4. `WorkspaceDay`는 다음 경로를 구분한다.

   ```text
   core/domain/model/WorkspaceDay.kt
   dayRoot
   dayLogsRoot
   wrapperLogsRoot
   ```

5. 날짜 선택 정책은 `core/domain/policy/WorkspaceDaySelectionPolicy.kt`에 있다.
   - 명시 날짜 > 오늘 > 읽기 전용 최신 날짜
   - 실행 목적에서는 과거 날짜로 자동 fallback하지 않음
   - 오늘이 아닌 날짜는 `isReadOnly=true`
6. artifact 모델은 `core/domain/model/WorkspaceArtifact.kt`에 있다.
7. mock provider는 `composeApp/.../demo`에만 존재한다.
8. 프로젝트명은 `hrns-now`, JDK는 17이며 `.\gradlew.bat check`, Linux 실행 비트가 설정된 `gradlew`, GitHub Actions CI 기반이 준비돼 있다.
9. `run-check.*`는 삭제·ignore 대상이다.
10. Phase 0에서 수정한 core 파일은 설계 규범의 목표 패키지로 이동했다. 옛 `core.artifact`, `core.workspace` 패키지를 복원하지 않는다.

## 제품 불변 계약

- 상태 진실은 `<dayRoot>\WORKFLOW_STATE.json` 하나다.
- Reader와 UI는 `WORKFLOW_STATE.json`을 절대 쓰지 않는다.
- Markdown 문구나 stdout 성공 문구로 상태를 판단하지 않는다.
- unknown key는 허용하지만 unknown enum/status 원문은 보존한다.
- unknown·malformed·미지원 schema는 fail-closed다.
- raw session ID, token, secret을 fixture·로그·화면 모델에 복제하지 않는다.
- live fixture의 개인 경로나 세션 값을 production 코드에 하드코딩하지 않는다.

## 목표

live `WORKFLOW_STATE.json`에서 Phase 1A 최소 필드를 안전하게 typed domain으로 변환하고, partial write·BOM·encoding·schema mismatch를 구분하는 Reader 계약을 만든다.

최종 결과는 다음 특성을 가져야 한다.

- Harness JSON 구조는 `infra.serialization` 내부 DTO에 격리
- domain은 kotlinx serialization annotation과 JSON 필드명을 모름
- Reader 결과는 성공·누락·malformed·encoding 오류·미지원 schema·접근 오류를 구분
- 마지막 정상값을 보존하여 malformed 재읽기 실패 시 stale projection 생성 가능
- 외부 field 추가와 unknown enum/status에 파서가 깨지지 않음

## 필수 아키텍처

`doc/hrns_now_design_pattern.md`의 Hexagonal Architecture와 Anti-Corruption Layer를 적용한다.

권장 목표 구조:

```text
core/src/main/kotlin/io/hrns_now/core/
├── domain/model/
│   ├── WorkflowState.kt
│   ├── WorkflowStatus.kt
│   ├── StopReason.kt
│   ├── WorkflowQueue.kt
│   └── WorkflowStateSummary.kt
├── domain/policy/
│   └── StateReadRetryPolicy.kt
├── port/
│   └── WorkflowStatePort.kt
└── result/
    └── StateReadResult.kt

infra/src/main/kotlin/io/hrns_now/infra/
└── serialization/
    ├── HarnessWorkflowStateDto.kt
    ├── WorkflowStateParser.kt
    ├── WorkflowStateMapper.kt
    └── JsonWorkflowStateAdapter.kt
```

파일 수는 책임에 따라 조정할 수 있지만 다음 분리는 지킨다.

- DTO: 외부 JSON 모양
- Parser: bytes/text → DTO
- Mapper: DTO → domain
- Adapter/Reader: 파일 metadata, retry, last-known-good 조정
- Policy: retry 횟수와 재시도 가능 조건
- Result: Reader 호출자가 처리할 typed 결과

신규 파일은 목표 패키지에 만든다. 기존 `Projection.kt`/`ProjectionMeta.kt`를 Phase 1A에서 수정한다면 설계 규범 §17에 따라 `core/result`로 함께 이동하고 참조를 갱신한다. 단순 패키지 정리를 위한 범위 밖 big-bang 이동은 금지한다.

## Serialization 설정

- `kotlinx-serialization-json`을 version catalog와 `infra`에 도입한다.
- Kotlin serialization plugin은 필요한 모듈에만 적용한다.
- `Json { ignoreUnknownKeys = true }`를 사용한다.
- DTO는 가능한 한 `internal`로 제한한다.
- domain 모델에 `@Serializable`, `@SerialName`을 붙이지 않는다.
- UTF-8 BOM은 허용한다.
- 잘못된 UTF-8 byte sequence는 일반 JSON malformed와 구분한다.
- explicit null과 필드 누락의 의미 정책을 코드 또는 Phase 보고서에 명시한다.

## 최소 typed 필드

계획서 부록 A와 live fixture를 직접 대조해 다음 필드를 구현한다.

### Top-level

```text
schema_version
date
project_name
workspace_root
repo_root
profile
state
queue
required_next_action
```

### state

```text
current_phase
current_status
next_action
execution_wrapper
stop_reason
blocked_reason
failed_reason
human_action_required
execution_completed
closure_validated
clean_handoff
resume_from_step_id
artifacts_state
ops_validation
closure
authorized_target_file
current_slice
slice_queue
role_sliced
usage_guard
```

### queue

```text
status
active.card_id
active.slice_id
blocked_reason
last_updated_at
```

모든 중첩 구조를 범용 `Map<String, Any?>`로 넘기지 않는다. Phase 1A에서 실제로 소비할 최소 구조는 typed model로 만들고, 아직 상세 계약이 불명확한 값은 안전한 raw wrapper 또는 명시적 nullable typed 값으로 보존한다.

## Unknown과 schema 정책

- `StopReason.Unknown(raw)`처럼 unknown 원문을 보존한다.
- `WorkflowStatus.Unknown(raw)`을 제공한다.
- execution wrapper도 새 값이 와서 파서가 깨지지 않게 한다.
- 알려진 값 매핑 문자열은 Harness live taxonomy만 사용한다.
- `packet_contract_failed`, `state_finalization_failed`, `new_target_path_failed` 같은 창작 용어를 추가하지 않는다.
- schema version을 typed 값으로 파싱한다.
- 지원 major는 `1`이다.
- major 불일치는 `UnsupportedSchema(rawVersion)`으로 반환한다.
- 상위 minor는 unknown field 무시 정책으로 읽을 수 있어야 한다.
- schema_version 누락·형식 오류를 단순 default `"1.0"`으로 숨기지 않는다.

## Reader와 partial write 정책

Reader는 다음 순서를 구현한다.

1. `WorkspaceDay.dayRoot.resolve("WORKFLOW_STATE.json")`만 읽는다.
2. 읽기 전 mtime/size를 기록한다.
3. UTF-8 decode와 JSON parse를 수행한다.
4. 읽기 후 mtime/size를 다시 확인한다.
5. parse 실패 또는 metadata 변경이면 짧은 지연 후 최대 2~3회 재읽는다.
6. 지연 함수/clock 또는 retry 결정을 테스트 가능하게 주입한다.
7. 반복 실패 시 malformed 결과와 마지막 정상 domain 값을 함께 반환한다.
8. 성공 후 해당 경로의 last-known-good를 갱신한다.
9. 모든 실패를 `null` 또는 빈 domain으로 삼키지 않는다.

예시 결과 형태:

```kotlin
sealed interface StateReadResult {
    data class Success(
        val state: WorkflowState,
        val sourceVersion: FileVersion,
    ) : StateReadResult

    data class Missing(val path: Path) : StateReadResult
    data class Malformed(
        val message: String,
        val lastKnownGood: WorkflowState?,
    ) : StateReadResult
    data class EncodingError(
        val message: String,
        val lastKnownGood: WorkflowState?,
    ) : StateReadResult
    data class UnsupportedSchema(val rawVersion: String) : StateReadResult
    data class AccessDenied(val path: Path) : StateReadResult
}
```

정확한 이름은 설계 일관성을 해치지 않는 범위에서 조정할 수 있다.

기존 `Projection<T>`/`ProjectionMeta`와 연결할 경우 다음 의미를 지킨다.

- Success → `exists=true`, `malformed=false`, `stale=false`
- Missing → `exists=false`
- Malformed/EncodingError + last-known-good → `malformed=true`, `stale=true`
- UnsupportedSchema/AccessDenied → 진단 원문 보존, 실행 잠금 가능한 projection

## 테스트

최소 다음 테스트를 추가한다.

1. sanitize한 live fixture 구조 파싱
2. unknown top-level/nested key 무시
3. unknown status 원문 보존
4. unknown stop reason 원문 보존
5. 필수 top-level 필드 누락
6. schema major 불일치
7. 상위 minor 허용
8. truncated JSON 첫 실패 후 재읽기 성공
9. 반복 malformed 후 last-known-good stale 유지
10. UTF-8 BOM 허용
11. 잘못된 UTF-8과 JSON malformed 구분
12. mtime/size 변경 감지 후 재읽기
13. 파일 누락
14. 빈 파일
15. 공백·한글 경로

fixture는 `infra/src/test/resources/fixtures/` 아래에 둔다. live fixture의 필드 구조는 사용하되 raw session ID, 개인 경로, 응답 원문, secret은 제거하거나 안전한 가상 값으로 치환한다. 테스트가 `D:\...` 절대 경로에 의존하면 안 된다.

## 금지 범위

- Phase 1B `UiAction`/CTA 결정표 구현
- Phase 1C `AppViewModel`, polling, Cockpit 배선
- Phase 1D Registry
- PowerShell `ProcessBuilder`
- lock, masking, Git 실행
- 실제 실행 버튼 연결
- `WORKFLOW_STATE.json` 쓰기
- 실데이터 실패 시 mock fallback
- `Map<String, Any?>`를 UI까지 전달
- 다음 Phase를 위한 대규모 선행 추상화

## 검증

실제 task를 확인하고 다음 순서로 실행한다.

```powershell
.\gradlew.bat :core:test
.\gradlew.bat :infra:test
.\gradlew.bat :composeApp:jvmTest
.\gradlew.bat check
```

다음 정적 검사도 수행한다.

```text
production 코드의 auziraum/D:\harness-workspaces/특정 날짜 하드코딩 0건
창작 Harness 상태 용어 0건
WORKFLOW_STATE.json 쓰기 0건
raw session ID fixture 저장 0건
generated build output Git 추적 0건
```

## 완료 보고

`doc/phase_reports/phase1a-report.md`를 UTF-8 without BOM으로 작성한다.

보고서에 포함할 내용:

- 구현 목표와 실제 변경 파일
- 계획서 Phase 1A 항목별 충족 근거
- `hrns_now_design_pattern.md` 적용 근거
- DTO/Parser/Mapper/Adapter/Policy/Result 책임 분리
- explicit null/필드 누락 정책
- retry와 last-known-good 의미
- 테스트별 결과와 전체 `check`
- 알려진 한계
- 다음 허용 Phase가 Phase 1B임을 명시
- Git commit은 만들지 않았음을 명시

## 종료 기준

- live schema 기반 phase/status/queue/stop reason/artifact/validation/closure typed 추출
- unknown 값 원문 보존
- partial write 재시도
- 마지막 정상값 stale 유지 가능
- malformed·encoding·schema mismatch 구분
- 모든 Reader 실패에서 fail-closed 가능한 typed 결과
- targeted/module/full 테스트 통과
- Phase 1B 이상 기능 미포함
- working tree에 source/test/report만 남고 commit 없음
