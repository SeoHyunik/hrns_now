# Phase 0 Report — Harness 계약 재정렬 + 프로젝트 정비/CI 기반

- **작성일:** 2026-07-23
- **범위:** Phase 0A (Harness 계약 재정렬) + Phase 0B (프로젝트 정비·테스트·CI 기반)
- **기준 문서:** `doc/hrns_now_claude_plan.md`
- **작업 브랜치:** `harness-dev`
- **커밋 상태:** Codex 독립 검증·보정 후 Phase 0 커밋에 포함

---

## 1. 목표

- 기존 코드가 harness-kit의 폐기된(legacy) 계약을 정상 계약처럼 표시하던 문제를 제거한다. 새 기능은 추가하지 않는다.
- 이후 모든 Phase가 회귀 테스트로 검증되도록 최소 테스트/CI 기반을 구축한다.

---

## 2. Phase 0A — Harness 계약 재정렬

### 2.1 결정적 계약 오류 (수정 전)

| # | 문제 |
|---|---|
| 1 | 기계 상태 진실인 `WORKFLOW_STATE.json`을 점검 목록에서 검사하지 않음 |
| 2 | legacy fallback `WORKDAY_STATE.json`을 기준 파일로 취급 |
| 3 | optional 산출물 `REQUEST_STRUCTURED.md`를 필수처럼 취급 |
| 4 | daily 4-file을 날짜 폴더가 아니라 workspace root 직하에서 탐색 |
| 5 | Run 화면 mock에 harness-kit에 존재하지 않는 창작 실패 용어 3종 포함 |

### 2.2 변경 내용

**신규 도메인 모델**

- `core/src/main/kotlin/io/hrns_now/core/domain/model/WorkspaceDay.kt` 추가
  - `projectWorkspaceRoot`와 `date`를 받아 `dayRoot`, `dayLogsRoot`, `wrapperLogsRoot`를 명확히 분리한다.
  - 실측 결과 원래 코드의 로그 경로 계산(`<workspaceRoot>\logs\<날짜>\`)은 wrapper 실행 로그 위치로는 정확했으므로 그대로 보존하고 이름만 명확히 했다.
- `core/src/main/kotlin/io/hrns_now/core/domain/policy/WorkspaceDaySelectionPolicy.kt` 추가: 명시 날짜 > 오늘 > 읽기 전용 최신 날짜 정책과 실행 목적의 과거 날짜 fallback 금지를 고정한다.

**Artifact 분류 체계**

- `core/src/main/kotlin/io/hrns_now/core/domain/model/WorkspaceArtifact.kt`
  - `ArtifactRequirement(Required|Optional|Legacy)` enum 추가
  - `ArtifactProbeResult`에 `requirement` 필드 추가
  - `WorkspaceArtifactSummary`에 `requiredItems`(Required 항목만 필터), `isRequiredReady`(Required 항목이 모두 `Exists`일 때만 true) 계산 프로퍼티 추가 — optional/legacy 상태가 readiness 판정에 영향을 주지 않도록 분리

**`WorkspaceArtifactProbe` 재작성**

- `infra/src/main/kotlin/io/hrns_now/infra/WorkspaceArtifactProbe.kt`
  - **Required** (`dayRoot` 하위): `REQUEST_INBOX.md`, `TODAY_STRATEGY.md`, `DAILY_HANDOFF.md`, `WORKFLOW_STATE.json`
  - **Optional**: `REQUEST_STRUCTURED.md`, `<dayRoot>/logs/`, `<workspaceRoot>/logs/<날짜>/`
  - **Legacy**: `WORKDAY_STATE.json`, `WORK_QUEUE.json` (`dayRoot` 하위)
  - `probe(workspaceRoot)`와 `probe(workspaceRoot, date)`를 분리해 기본 읽기 전용 날짜 선택과 명시 날짜 조회를 지원

**UI 반영**

- `composeApp/.../ui/Shell.kt`: `InspectorPanel`의 아티팩트 목록에서 `ArtifactRequirement.Legacy` 항목을 필터링해 기본 화면에서 숨김 (계약 2.2 "legacy: 기본 화면에서 숨김" 반영)
- Run 화면 mock(`failureChips`)의 창작 용어 교체:
  - `packet_contract_failed` → `claude_context_limit`
  - `state_finalization_failed` → `dispatch_metadata_conflict`
  - `new_target_path_failed` → `manual_prerequisite_required`
  - (참고: 이 세 용어는 harness-kit 스크립트 전수 grep으로 실존이 확인된 taxonomy다.)
- `MockProjectionProvider.kt`, `MockWorkspaceConfigProvider.kt`를 `infra`에서 `composeApp/src/jvmMain/kotlin/io/hrns_now/app/demo/`로 이동 (mock은 infrastructure adapter가 아니라는 원칙 반영). `App.kt` import 갱신.
- mock 데이터 내 `WORKDAY_STATE.json` 참조(오늘 상태 파일 라벨 등)도 현행 `WORKFLOW_STATE.json`으로 정정.

---

## 3. Phase 0B — 프로젝트 정비·테스트·CI 기반

- `settings.gradle.kts`: `rootProject.name` `"MyApplication"` → `"hrns-now"`
- `README.md`: Compose 템플릿 기본 문구를 실제 제품 설명(HRNS-NOW = Harness Kit Desktop Control Plane, 모듈 구조, 빌드 방법, 작업 규칙)으로 교체
- `composeApp/src/commonTest/kotlin/org/example/project/ComposeAppCommonTest.kt` → `composeApp/src/commonTest/kotlin/io/hrns_now/app/ComposeAppCommonTest.kt`로 이동 (패키지 선언 `io.hrns_now.app`과 실제 디렉터리 불일치 수정)
- `run-check.out`, `run-check.err` 추적 해제 및 삭제, `.gitignore`에 `run-check.*` 패턴 추가
- `core/build.gradle.kts`, `infra/build.gradle.kts`: `testImplementation(libs.kotlin.test)`, `testImplementation(libs.kotlin.testJunit)` 추가 + `tasks.test { useJUnit() }` 설정
- `.github/workflows/ci.yml` 신규 추가: `checkout` → JDK 17(Temurin) → Gradle Setup Action → `./gradlew check --no-daemon`. 초기에는 `ubuntu-latest`(비-Windows) 러너 허용 — core/infra 테스트가 순수 JVM이라 이식 가능함을 확인함. PowerShell 통합 테스트가 추가되는 Phase 3부터 Windows runner job을 별도로 추가할 예정.

---

## 4. 파일 변경 요약

```text
 .gitignore                                                         |   5 +
 README.md                                                          |  71 +++++++----
 composeApp/.../org/example/project/ComposeAppCommonTest.kt (삭제)   |  12 --
 composeApp/.../io/hrns_now/app/App.kt                              |   2 +-
 composeApp/.../io/hrns_now/app/ui/Shell.kt                         |   7 +-
 core/build.gradle.kts                                              |   9 ++
 core/.../core/domain/model/WorkspaceArtifact.kt                   |  20 ++-
 infra/build.gradle.kts                                             |   6 +
 infra/.../infra/MockProjectionProvider.kt (삭제)                   | 135 ---
 infra/.../infra/MockWorkspaceConfigProvider.kt (삭제)               |  33 --
 infra/.../infra/WorkspaceArtifactProbe.kt                          |  96 ++++++++++---
 settings.gradle.kts                                                |   2 +-
 12 files changed, 170 insertions(+), 228 deletions(-)

신규 파일/디렉터리:
 .github/workflows/ci.yml
 composeApp/src/commonTest/kotlin/io/hrns_now/app/ComposeAppCommonTest.kt
 composeApp/src/jvmMain/kotlin/io/hrns_now/app/demo/MockProjectionProvider.kt
 composeApp/src/jvmMain/kotlin/io/hrns_now/app/demo/MockWorkspaceConfigProvider.kt
 core/src/main/kotlin/io/hrns_now/core/domain/model/WorkspaceDay.kt
 core/src/main/kotlin/io/hrns_now/core/domain/policy/WorkspaceDaySelectionPolicy.kt
 core/src/test/kotlin/io/hrns_now/core/domain/model/WorkspaceArtifactSummaryTest.kt
 core/src/test/kotlin/io/hrns_now/core/domain/policy/WorkspaceDaySelectionPolicyTest.kt
 infra/src/test/kotlin/io/hrns_now/infra/WorkspaceArtifactProbeTest.kt
```

---

## 5. 테스트

### 5.1 `core` — `WorkspaceArtifactSummaryTest` (6건, 전부 PASS)

- required 항목이 모두 Exists이면 ready
- optional 파일 누락은 readiness에 영향 없음
- legacy 파일 존재 여부는 readiness에 영향 없음
- required 항목이 하나라도 누락이면 not ready
- required 항목이 없으면 not ready
- requiredItems는 Required 분류만 반환

### 5.2 `core` — `WorkspaceDaySelectionPolicyTest` (5건, 전부 PASS)

- 명시 날짜 우선
- 오늘 날짜 우선
- 읽기 전용 최신 날짜 fallback
- 실행 목적의 과거 날짜 fallback 금지
- daily와 두 로그 root 분리

### 5.3 `infra` — `WorkspaceArtifactProbeTest` (11건, 전부 PASS)

- 날짜 폴더의 4-file을 정확히 탐지
- optional 파일 누락은 readiness 실패가 아님
- day 산출물 로그와 wrapper 실행 로그를 서로 다른 경로로 탐지
- 잘못된 날짜명과 날짜 형태 파일을 무시하고 최신 유효 날짜 선택
- legacy 파일 존재/부재 모두 readiness에 영향 없음 (2건)
- workspace root 직하에 동일 파일이 있어도 날짜 폴더 파일만 사용 (오탐 방지)
- 날짜 폴더 자리에 디렉터리가 아닌 파일이 있으면 → 안전하게 Missing 처리(최초 시도한 WrongType 기대는 실제 동작과 달라 테스트를 수정함, 6.1 참조)
- 기준 파일 자리에 디렉터리가 있으면 WrongType 처리
- 공백·한글이 포함된 workspace 경로 정상 처리
- workspace 경로가 비어있으면 전 항목 WorkspaceNotConfigured

### 5.4 전체 빌드

```text
./gradlew check
BUILD SUCCESSFUL
```

`core`, `infra`, `composeApp`(jvmTest 포함 compile) 전부 통과. 기존에 있던 `painterResource` deprecated 경고 2건은 이번 변경과 무관한 기존 이슈.

### 5.4 실제 harness workspace 대조 검증

프로젝트 고유 경로가 repo에 남지 않도록, `D:\harness-workspaces\auziraum\2026-06-26` 대상 1회성 스크래치 테스트를 작성해 실행 후 즉시 삭제했다.

결과:

```text
Required | 요청 입력함        | 2026-06-26/REQUEST_INBOX.md    -> Exists
Required | 오늘 할 일 파일     | 2026-06-26/TODAY_STRATEGY.md   -> Exists
Required | 인수인계 파일       | 2026-06-26/DAILY_HANDOFF.md    -> Exists
Required | 작업 상태 파일      | 2026-06-26/WORKFLOW_STATE.json -> Exists
Optional | 정리된 요청 파일    | 2026-06-26/REQUEST_STRUCTURED.md -> Exists
Optional | 날짜 산출물 로그    | 2026-06-26/logs/               -> Exists
Optional | 래퍼 실행 로그      | logs/2026-06-26/               -> Exists
Legacy   | 레거시 오늘 상태 파일 | 2026-06-26/WORKDAY_STATE.json  -> Missing
Legacy   | 레거시 작업 큐 파일  | 2026-06-26/WORK_QUEUE.json     -> Missing
isRequiredReady = true
```

실제 harness-kit 기본 lane(legacy 파일 미생성) 상태와 정확히 일치함을 확인했다.

---

## 6. 이번 Phase에서 바로잡은 사항

### 6.1 테스트 기대값 정정 (구현이 아니라 테스트가 틀렸던 사례)

"날짜 폴더 자리에 디렉터리가 아닌 파일이 있는 경우"에 대해 최초 테스트는 각 필수 파일이 `WrongType`으로 보고될 것이라 기대했으나, 실제로는 `Missing`으로 보고됨을 확인했다. `dayRoot` 자체가 파일이면 그 하위 경로는 파일시스템상 존재할 수 없으므로 `Files.exists()`가 false를 반환하는 것이 정상 동작이다. 두 경우 모두 `isRequiredReady=false`로 fail-closed하게 처리되므로 안전성에는 문제가 없다. 테스트 기대값만 실제 동작에 맞게 수정했다.

---

## 7. 알려진 한계

- Compose Desktop 네이티브 창을 이 작업 환경에서 직접 기동해 육안으로 확인하지는 못했다. 로직 검증(단위/통합 테스트 + 실 workspace 데이터 대조)으로 정확성을 확보했으나, Setup 화면 렌더링의 시각적 확인은 별도 요청 시 진행 필요.
- `MockWorkspaceConfigProvider`는 현재 어디에서도 참조되지 않는 죽은 코드로 확인되었으나(계획 문서에 명시적 언급은 없음), `MockProjectionProvider`와 동일한 "mock은 infra가 아니다" 원칙을 적용해 함께 이동했다.

---

## 8. 다음 단계

Phase 1A — `WORKFLOW_STATE.json` Reader (kotlinx-serialization 도입, unknown-key/enum 안전 처리, partial write 재시도 정책, 실측 스키마 기반 typed 모델).

---

## 9. Codex 최종 검증·보정

- Phase 0 범위는 Phase 0A와 Phase 0B 전체로 검증했다.
- `doc/hrns_now_design_pattern.md`를 규범으로 적용해 Phase 0에서 수정된 domain 모델과 정책을 목표 패키지로 이동했다.
- required 4-file, optional 두 로그 구조, legacy readiness 제외, root 직하 파일 무시, 공백·한글 경로를 회귀 테스트로 고정했다.
- 날짜 선택 정책은 명시 날짜 > 오늘 > 읽기 전용 최신 날짜이며 실행 목적에서는 과거 날짜로 자동 fallback하지 않는다.
- `run-check.*` 추적 제거·ignore, 프로젝트명, README, commonTest 경로, core/infra 테스트 의존성, JDK 17·Gradle cache 기반 CI와 `gradlew` 실행 비트(`100755`)를 확인했다.
- GitHub Actions는 공식 현재 major인 `checkout@v6`, `setup-java@v5`, `setup-gradle@v6`로 정렬했다.
- targeted, core/infra module, 전체 `check`를 모두 통과한 뒤 Codex가 Phase 0 커밋을 생성한다.
- Claude의 다음 작업은 `doc/claude_prompts/phase1a-workflow-state-reader.md`를 따른다.
