# HRNS-NOW 발전 계획서 — 최종 통합본

- **작성일:** 2026-07-23
- **문서 지위:** 본 문서가 개발 착수 기준안이다. `hrns-now-발전계획-2026-07-23.md`(원본)과 `hrns-now-발전계획-개정본-2026-07-23.md`(Codex 교차검증판)는 이력 참고용으로 대체된다.
- **대상 저장소:** `SeoHyunik/hrns_now` — 검증 HEAD `d617d27` "feat: UI 고도화 1차"
- **대상 Harness:** `D:\harness-kit` live (smoke 72 / offline 61 / manual 11 기준)
- **종합 판정:** `FOUNDATION_REBASE_REQUIRED` — 제품 기능 준비도 약 10%, 시각적 UI 골격 약 60%
- **제품 정의:**

> **HRNS-NOW = Harness Kit Desktop Control Plane.**
> PowerShell entrypoint와 `WORKFLOW_STATE.json`을 실행 엔진·상태 진실로 유지하면서, 사용자가 터미널 명령을 암기하지 않고도 현재 허용된 단 하나의 다음 행동을 안전하게 안내받고 실행하는 Windows 우선 데스크톱 앱. 최종 산출물은 배포 가능한 MSI/exe다.

---

# 0. 3차 교차검증 기록

이 최종본은 세 번의 상호 검증을 거쳤다: A(Claude 원본 분석) ↔ B(외부 격차 보고서) ↔ C(Codex 개정본), 그리고 각 라운드마다 `D:\harness-kit`·live workspace 실측 대조.

## 0.1 개정본(C)에서 채택한 개선

| 항목 | 내용 |
|---|---|
| Phase 세분화 | Phase 0→0A/0B, Phase 1→1A~1D로 분해. 실행 추적 단위가 명확해짐 |
| **CI 조기 도입 (0B)** | 원본에 없던 GitHub Actions `./gradlew check` 기반 — 계약 수정을 회귀 테스트로 고정하는 전제 조건으로 승격 |
| **typed `UiAction`** | 표시 문구를 실행 ID로 쓰지 않는 sealed interface + `RecommendedActions(primary, allowed, blockedReason)` |
| **cross-process 실행 충돌 인지** | UI 내부 lock만으로는 터미널 실행과의 동시성을 못 막는다는 지적 — 원본의 실질 공백 (단, 0.3의 범위 보정 필요) |
| 호환성 방향 역전 | `min_ui_version`(kit이 UI를 지시) 대신 **UI가 지원 가능한 계약 버전을 판단** — kit은 `{kit_version, state_schema_version, ui_contract_version}`만 선언 |
| Windows 전용 MVP | `targetFormats(Msi)`로 축소, DMG/DEB는 PowerShell Core 호환 검증 후 별도 로드맵 |
| 요청 저장 낙관적 동시성 | 로드 시 hash/mtime 저장 → 저장 직전 재검증 → 외부 변경 시 덮어쓰기 거부 |
| 날짜 정책 정밀화 | 명시 날짜 > 오늘 > 최신(읽기 전용만). 과거 날짜를 실행 목적으로 오늘처럼 취급 금지 |
| projection 모델 재배치 | `core.projection`의 UI read model들은 domain이 아님 → Phase 1C에서 `composeApp/presentation/model`로 이동. mock provider는 `composeApp/demo`로 |
| 역할 단계 권위 규칙 | 로그 문자열 기반 단계 추론은 참고 정보, 실행 판단은 항상 `WORKFLOW_STATE.json` |
| 경계 검사 확장 | 상호 포함 6종 + junction/symlink 실경로 비교, Registry 저장 이전 수행 |
| Phase별 배포 Gate | 섹션 8에 통합 채택 |

## 0.2 개정본이 누락하여 복원한 항목

1. **harness-kit Phase 2의 운영 함정**: 문서 정합성 스캐너의 기대 수치(72/36/61/11/0)가 `scripts/lib/secondary-llm/secondary-llm-docs-calibration.ps1`에 **하드코딩**되어 있다. smoke를 추가하는 순간 이 라이브러리 + `SMOKE_INDEX.md` + 두 KIT MAP 수치를 연쇄 갱신하지 않으면 docs-scan이 error/blocker를 낸다. (2026-07-23 문서 현행화 작업에서 실증된 절차)
2. **구현 선례**: `check-secondary-llm-capability.ps1`이 이미 "stdout JSON, 파일 write 없음" 패턴 — Phase 2의 `-Json` 구현 표준으로 삼는다.
3. **harness-kit 백로그 부록** (부록 E) 및 실행 **모델 배분 전략** (섹션 5).
4. **live workspace 검증 픽스처**: auziraum/2026-06-26 실측 상태(`current_phase=execution`, `current_status=execution_blocked`)를 Phase 1 수용 테스트로 사용.

## 0.3 이번 라운드에서 바로잡은 오류 (양쪽 문서 공통 포함)

1. **[중요/정정] 로그 경로 이중 구조** — 원본(A)과 개정본(C) 모두 "기존 probe의 로그 경로 `<root>\logs\<date>` 계산이 실제 구조와 반대"라고 주장했으나, **실측 결과 이 주장이 틀렸다.** 실제 계약은 두 계층이 공존한다:
   - `<workspaceRoot>\logs\<날짜>\` = **wrapper 실행 로그** (metrics/preflight/rendered — 실측 확인). 기존 probe의 계산이 이 용도로는 **정확했다**.
   - `<dayRoot>\logs\` = day 산출물 (secondary-llm run artifacts 등 — 실측 확인).
   - 추가로 `<workspaceRoot>\logs\claude-session-continuity\<날짜>\`, `<workspaceRoot>\logs\usage-ledger\<날짜>.jsonl`.
   probe의 실제 결함은 daily 4-file 위치(루트 직하 ≠ 날짜 폴더)와 파일 목록(WORKDAY_STATE 등)이며, 로그 경로는 용도 라벨만 정확히 하면 된다.
2. **[정정] 개정본 Phase 0A가 `logs\`를 필수 항목에 포함** — harness의 required daily surface는 **4-file뿐**이다. 로그 디렉터리는 첫 실행 전 없을 수 있으므로 Optional/informational로 분류한다. 필수로 두면 신규 날짜가 항상 "미준비"로 오판된다.
3. **[정정] 개정본의 validation-only 실행 명령** — `run-cycle.ps1`의 `-RunExecutionWrapper`는 `none|code|doc|auto`만 허용한다(실측). `validation` 모드는 존재하지 않으며, validation-only slice는 실행 경로 내부의 parent deterministic closeout으로 처리되고 closure 검증은 별도 `-ValidateForClosure` 스위치다. UI의 `RunValidationSlice` 액션 매핑은 Phase 4 착수 시 이 실계약으로 확정하고, **새 모드를 창작하지 않는다**. `-RunReplanWrapper`(재계획)도 실존 명령으로 typed command에 포함한다.
4. **[범위 보정] cross-process lock의 정직한 한계** — 개정본은 "Harness-level cross-process lock"이라 표현했으나, harness-kit에는 현재 lock 계약이 없고 터미널에서 실행되는 `run-cycle.ps1`은 UI의 lock 파일을 확인하지 않는다. 따라서 lock의 실제 보장 범위는 ① HRNS-NOW 인스턴스 간 상호배제(신뢰 가능) ② 외부 실행 **감지**(휴리스틱: `WORKFLOW_STATE.json` mtime/hash가 UI가 유발하지 않은 변경을 보임)다. 진짜 harness 협조 lock은 harness 측 설계 승인이 필요한 별도 백로그다(부록 E). lock 파일 위치는 workspace 오염을 피해 `%LOCALAPPDATA%\hrns-now\locks\<projectId>\<date>.lock.json`으로 한다(포터블 SSD가 동시에 두 PC에 물릴 수 없으므로 per-machine 위치로 충분).

## 0.4 패키징 계획 편입 결정 (2026-07-27)

`doc/hrns_now_packaging_plan.md`는 사용자가 제공한 **초안**이며 Git 비추적 입력으로 보존한다. 경로 분리·보존·MSI 우선이라는 방향은 채택하되, 현행 Harness 계약과 배포 소유권을 바꾸는 제안은 조건부로만 편입한다.

| 초안 항목 | 판정 | 편입 위치와 근거 |
|---|---|---|
| MSI 우선, 번들 JRE, Program Files 읽기 전용, AppData/LocalAppData 분리, 공백·한글·드라이브 경로 검증 | 채택 | Phase 6A. UI/lock/Registry가 Harness workspace 밖에 있어야 한다는 기존 불변식과 일치한다. 기존 `%APPDATA%\hrns-now\projects.json` 경로는 호환성 때문에 임의 대소문자·위치 변경이나 자동 이관을 하지 않는다. |
| 프로젝트별 LocalAppData workspace, 이름+short UUID, 첫 실행 자동 생성·복구 | 조건부 채택 | Phase 6B. Harness bootstrap·Registry 저장·경계 검사를 하나의 실패 안전한 흐름으로 묶어야 하며, UI가 daily 4-file을 직접 만들 수 없다. 6A는 기존처럼 사용자가 검증된 workspace를 선택·등록한다. |
| Harness Runtime을 MSI에 동봉 | 조건부 채택 | Phase 6B. `D:\harness-kit` 개발 트리를 복사하거나 MSI에 넣지 않는다. Harness 저장소 소유의 재현 가능한 release artifact, immutable manifest/checksum, 공개 entrypoint, smoke, 재배포 승인과 UI 호환성 Gate가 모두 먼저 필요하다. |
| manifest/checksum, staged runtime, secret scan, Runtime smoke | 조건부 채택 | Phase 6B의 Harness+UI 공동 작업. checksum만으로 신뢰·서명을 과장하지 않으며, UI 저장소에는 private Harness 원본이나 staging 산출물을 추적하지 않는다. |
| 제거 시 사용자 데이터 보존, repair/재설치 복원 | 채택 | Phase 6A는 기본 보존과 무삭제 smoke를 검증하고, repair·이동 복구의 전체 UX는 6B에서 완료한다. |
| 코드 서명·SmartScreen | 부분 채택 | 6A는 미서명 경고와 서명 필요성을 문서화한다. 인증서·서명 키·CI provenance가 준비된 뒤의 실제 서명은 Phase 7 뒤 배포 확장으로 보류한다. |
| 자동 업데이트, side-by-side Runtime, CD Key/라이선스, portable data mode, 암호화·난독화 | 보류 | CTA/실행 권한을 바꾸거나 제품 정책 결정을 요구하므로 Phase 7과 분리한 Post-MVP 배포 확장으로만 다룬다. 현 MVP에서 라이선스 미인증으로 Harness 실행을 차단하지 않는다. |

**공통 경계:** Runtime root(설치 소유·읽기/실행), repository root(사용자/Git 소유), project workspace root(Harness 산출물)는 서로 포함되지 않아야 하며 junction/symlink를 포함해 양방향 검사한다. `%APPDATA%`에는 Registry·설정만, `%LOCALAPPDATA%`에는 lock·workspace·비민감 캐시만 둔다. raw session ID, secret, token, raw log는 어느 앱 소유 저장소에도 보관하지 않는다.

---

## 0.5 Phase 순서 재정의 — UI/UX QA (2026-07-28)

사용자 QA를 통해 현재 최우선 과제를 설치/Runtime 배포가 아니라 **사용 중인 HRNS-NOW 화면의 이해 가능성·피드백·용어·프로젝트 흐름**으로 재정의했다.

```text
완료: Phase 0A~5
현재: 새 Phase 6 — UI/UX QA 개선 (G6-UX)
보류: 기존 Phase 6A(G6A), 기존 Phase 6B(G6B), 기존 Phase 7
```

기존 Phase 6A/6B와 Phase 7은 삭제·완료 처리·축소하지 않는다. 각각 clean Windows MSI smoke, 승인 Harness Runtime artifact, 실험 기능의 미해결 보류 과제로 유지한다. 새 Phase 6의 구현·통과가 이 보류 과제의 Gate를 통과시킨다는 뜻은 아니며, 보류 과제 재개는 별도 사용자 승인과 Codex 독립 검증을 요구한다.

# 1. 현재 소스 기준 재평가 (요약)

## 1.1 유지할 자산

- `core ← infra ← composeApp` Gradle 모듈 의존 방향
- `Projection<T>` / `ProjectionMeta` (파싱 실패·stale 투영에 이미 적합한 설계)
- 4개 라우트(Setup/Cockpit/Strategy/Run), 다크/라이트 테마, 한국어 셸, 컴포넌트/타이포 체계 (~1,800줄)
- `WorkspacePathProbe` 골격, 환경변수 기반 설정(`HRNS_*` 5종 — fallback으로 유지)
- Compose Desktop/JVM 기술 선택, MSI native distribution 기반

## 1.2 결정적 계약 오류 (Phase 0A 대상)

1. `WORKFLOW_STATE.json`(현행 기계 진실)을 검사하지 않음
2. legacy fallback `WORKDAY_STATE.json`을 기준 파일로 취급
3. optional `REQUEST_STRUCTURED.md`를 필수처럼 취급
4. daily 4-file을 날짜 폴더가 아니라 workspace root 직하에서 탐색
5. Run 화면 mock의 창작 실패 용어 3종 (`packet_contract_failed`, `state_finalization_failed`, `new_target_path_failed` — harness grep 0건)

## 1.3 현재 준비도

| 영역 | 준비도 | 평가 |
|---|---:|---|
| 시각적 UI 셸 | 60% | 재사용 가능 |
| 모듈 분리 | 65% | 방향 적절 |
| 경로 설정 | 20% | 환경변수 전용, GUI/영속화 없음 |
| Workspace probe | 20% | 골격은 있으나 계약 오류 |
| Workflow State 파싱 | 0% | 미구현 (kotlinx-serialization 미도입) |
| CTA 정책 | 0% | 문자열 mock |
| PowerShell 실행 | 0% | 버튼 placeholder |
| 실행 lock | 0% | 미구현 |
| Closure/복구 | 0% | 미구현 |
| 테스트 | 5% | 템플릿 테스트 1건 |
| CI | 0% | 없음 |
| 패키징 | 15% | 기본 설정만 |

---

# 2. 제품 불변 원칙

## 2.1 상태 진실

1. 상태 진실은 `WORKFLOW_STATE.json` 하나다.
2. Markdown prose로 Planning·Execution·Closure 가능 여부를 판단하지 않는다.
3. stdout 문자열만 보고 실행 성공을 확정하지 않는다. 실행 종료 후 반드시 State를 재읽는다.
4. 알 수 없는 상태·스키마·enum은 **fail-closed** — 원문을 진단 화면에 보존하되 모든 write/execute를 잠근다.

## 2.2 파일 소유권

| 파일 | 소유자 | UI 권한 |
|---|---|---|
| `REQUEST_INBOX.md` | 사람 | 보존 규칙 하에 작성 가능 (낙관적 동시성 필수) |
| `TODAY_STRATEGY.md` | Harness | 읽기 전용 |
| `DAILY_HANDOFF.md` | Harness | 읽기 전용 + Closure 확인 |
| `WORKFLOW_STATE.json` | Harness | **절대 쓰기 금지** |
| `REQUEST_STRUCTURED.md` | Harness/호환 | optional, 사용자 입력으로 노출 금지 |
| `WORKDAY_STATE.json` / `WORK_QUEUE.json` | legacy | 기본 화면에서 숨김 |

## 2.3 실행 원칙

1. Harness 로직을 Kotlin으로 재작성하지 않는다. Claude API를 UI가 직접 호출하지 않는다.
2. 임의 PowerShell 명령 입력창을 제공하지 않는다. typed command object → 인자 목록 변환만 허용 (shell 문자열 연결 금지).
3. 자동 resume 기본 비활성화, `--continue` 금지, Secondary LLM 결과 자동 채택 금지.
4. 프로젝트·날짜당 하나의 실행만 허용 — UI 내부 lock + per-machine lock 파일 + 외부 실행 감지 (0.3-4의 범위 정의).
5. UI가 harness workspace에 자기 소유 파일을 추가하지 않는다 (lock·설정은 `%LOCALAPPDATA%`/`%APPDATA%`).

## 2.4 경계 원칙

다음 관계가 성립하면 경고가 아니라 **등록·실행을 차단**한다. Registry 저장 이전에, normalized absolute path와 (가능한 경우) `toRealPath()` 비교로 검사한다.

- Workspace가 Repository 내부 / Repository가 Workspace 내부
- Kit root가 Repository 또는 Workspace 내부
- 세 경로 중 둘 이상 동일
- junction/symlink 실경로 해석 후 경계 겹침

## 2.5 비밀정보 원칙

- secret·token·raw session ID·개인 키 저장·표시 금지
- stdout/stderr 표시 전 마스킹, 마스킹 전 원문을 HRNS-NOW가 별도 파일로 복제하지 않음
- Registry에는 경로·profile·표시명·마지막 결과 요약만 저장

---

# 3. 목표 아키텍처

## 3.1 모듈 구조 (기존 Gradle 모듈 유지)

```text
core        — domain (model + policy) + port          ← Composable/파일/프로세스 의존 없음
infra       — filesystem / serialization / process / registry / security adapter
composeApp  — presentation (model / viewmodel / screen / component) + demo (mock)
:application — 초기에는 만들지 않음. use case가 비대해질 때 분리
```

- `core.projection`의 UI read model(`StatusChipModel` 등)은 domain이 아니다 → Phase 1C에서 `composeApp/presentation/model`로 이동 (Phase 0에서 무리하게 이동하지 않음).
- `MockProjectionProvider`는 infra가 아니다 → Phase 0A에서 `composeApp/demo`로 이동.

## 3.2 핵심 도메인 모델

```kotlin
data class HarnessProject(
    val id: String, val displayName: String,
    val kitRoot: Path, val projectWorkspaceRoot: Path,
    val repositoryRoot: Path, val profileId: String,
)

data class WorkspaceDay(val projectWorkspaceRoot: Path, val date: LocalDate) {
    val dayRoot: Path get() = projectWorkspaceRoot.resolve(date.toString())
    val wrapperLogsRoot: Path get() = projectWorkspaceRoot.resolve("logs").resolve(date.toString())
}
```

추가 모델: `WorkflowState`, `WorkflowStateSummary`, `QueuePointer`, `CurrentSlice`, `ArtifactState`, `ArtifactRequirement(Required|Optional|Legacy)`, `StopReason`(sealed, `Unknown(raw)` 보존), `ClosureState`, `OpsValidationState`, `RecommendedActions`, `ProcessRun`, `ProcessRunState`, `HarnessCompatibility`, `ProjectBoundaryResult`

## 3.3 typed CTA

```kotlin
sealed interface UiAction {
    data object ConnectProject : UiAction
    data object SelectWorkspaceDay : UiAction
    data object EditRequest : UiAction
    data object RunDoctor : UiAction
    data object RunOpsValidation : UiAction
    data object BootstrapDay : UiAction
    data object RunPlanning : UiAction
    data object RunReplan : UiAction          // 실측: -RunReplanWrapper
    data object RunCodeSlice : UiAction       // 실측: -RunExecutionWrapper code
    data object RunDocSlice : UiAction        // 실측: -RunExecutionWrapper doc
    data object RunClosureValidation : UiAction // 실측: -ValidateForClosure
    data object OpenRecoveryCenter : UiAction
    data object ReviewClosure : UiAction
    data object CloseDay : UiAction
}

data class RecommendedActions(
    val primary: UiAction?, val allowed: Set<UiAction>, val blockedReason: String?,
)
```

주의: validation-only slice의 실행 액션은 별도 wrapper 모드가 아니다(0.3-3). Phase 4 착수 시 실계약으로 매핑을 확정한다.

## 3.4 상태 소유자

Composable은 파일·프로세스·Registry를 직접 다루지 않는다. `AppViewModel`이 `StateFlow<HrnsUiState>`로 selectedProject / selectedWorkspaceDay / workflowProjection / artifactProjection / processRunState / compatibilityState / recommendedActions를 소유한다. 현재의 `remember { buildProjections() }` 일회성 생성은 Phase 1C에서 교체한다.

---

# 4. 개발 Phase

## Phase 0A — Harness 계약 재정렬

**목표**: legacy 계약을 정상 계약처럼 표시하는 문제 제거. 새 기능 없음.

**작업**:

1. `WorkspaceDay` 도입 — dayRoot/wrapperLogsRoot 해석을 한 곳으로. `workspaceRoot` 명칭을 `projectWorkspaceRoot`로 점진 변경.
2. `WorkspaceArtifactProbe` 수정:
   - **Required** (dayRoot 하위): `REQUEST_INBOX.md`, `TODAY_STRATEGY.md`, `DAILY_HANDOFF.md`, `WORKFLOW_STATE.json`
   - **Optional**: `REQUEST_STRUCTURED.md`, `<dayRoot>\logs\`(day 산출물), `<workspaceRoot>\logs\<날짜>\`(wrapper 실행 로그) — 로그 디렉터리는 첫 실행 전 없을 수 있으므로 readiness 실패 사유가 아님 (0.3-1,2)
   - **Legacy** (기본 숨김): `WORKDAY_STATE.json`, `WORK_QUEUE.json`
   - 필수/optional/legacy를 같은 성공 기준으로 계산하지 않음
3. 날짜 선택 정책: 명시 날짜 > 오늘 > (읽기 전용 조회에 한해) 최신 날짜. 실행 목적일 때 과거 날짜를 오늘처럼 취급 금지.
4. mock taxonomy 정리: 창작 용어 3종 제거 → `dispatch_metadata_conflict`, `transient_claude_overloaded`, `claude_call_timeout`, `claude_context_limit`, `manual_prerequisite_required` 등 실존 용어로 교체 (부록 C).
5. `MockProjectionProvider` → `composeApp/demo` 이동.

**테스트**: 날짜 폴더 4-file 정탐지 / optional 누락≠실패 / legacy 존재가 readiness에 무영향 / root 직하 동일 파일 무시 / 잘못된 날짜 디렉터리·파일 유형 / 공백·한글 경로.

**종료 기준**: 실제 workspace 연결 시 Setup 화면이 선택 날짜 폴더의 현행 4-file을 정확히 표시한다.

## Phase 0B — 프로젝트 정비·테스트·CI 기반

**작업**:

1. `rootProject.name = "hrns-now"`, README 실제 제품 설명, commonTest를 `io.hrns_now.app` 패키지·경로로 이동
2. `run-check.out`/`run-check.err` 추적 제거 + `.gitignore`에 `run-check.*` 추가
3. `core`/`infra`에 `testImplementation(kotlin("test"))` 및 테스트 태스크 설정
4. GitHub Actions 최소 workflow: checkout → JDK 17 → Gradle cache → `./gradlew check`. 초기에는 비-Windows runner 허용(순수 core/infra 테스트는 Path 추상화 + temp dir로 이식 가능하게 작성). PowerShell 통합 테스트는 Phase 3부터 Windows runner job 추가.

**종료 기준**: `./gradlew check` 통과, push/PR CI 통과, Phase 0A 회귀 테스트 존재, 저장소에 실행 로그 잔재 없음.

## Phase 1A — `WORKFLOW_STATE.json` Reader

**작업**:

1. `kotlinx-serialization-json` 도입, `ignoreUnknownKeys = true`, explicit null과 필드 누락 구분 정책 정의
2. 최소 typed 필드 (부록 A 실측 스키마 기준):
   - top-level: `schema_version, date, project_name, workspace_root, repo_root, profile, state, queue, required_next_action`
   - state: `current_phase, current_status, next_action, execution_wrapper, stop_reason, blocked_reason, failed_reason, human_action_required, execution_completed, closure_validated, clean_handoff, resume_from_step_id, artifacts_state, ops_validation, closure, authorized_target_file, current_slice, slice_queue, role_sliced, usage_guard`
   - queue: `status, active.card_id, active.slice_id, blocked_reason, last_updated_at`
3. Unknown 처리: unknown key 무시 / unknown enum `Unknown(raw)` 보존 / unknown status에서 모든 write·execute CTA 잠금 / 원문은 진단 화면 표시
4. partial write: 첫 파싱 실패 → 짧은 지연 → mtime/size 재확인 → 최대 2~3회 재읽기 → 최종 실패 시 `ProjectionMeta.malformed=true`, 마지막 정상 projection을 stale 표시로 유지, 실행 잠금
5. encoding: UTF-8, BOM 허용, encoding 오류는 malformed와 구분 표시

**테스트**: 실측 fixture / unknown key·enum / 필수 top-level 누락 / truncated JSON / BOM / 부분 기록 후 재읽기 성공 / 반복 실패 / schema major 불일치.

**종료 기준**: live `WORKFLOW_STATE.json`에서 phase/status/queue/stop reason/artifact/validation/closure를 안정 추출.

## Phase 1B — CTA 정책

**작업**: `fun recommendActions(state, compatibility, boundary, process): RecommendedActions` 순수 함수. Composable 조건문 금지, typed `UiAction`, fail-closed, lock 감지 시 실행 금지, 과거 날짜 write/execute 금지, compatibility mismatch 시 읽기만, malformed 시 복구 센터만. **결정표(부록 B)의 모든 행을 parameterized test로 고정.**

**종료 기준**: 상태 fixture마다 정확한 primary/allowed set 반환. live auziraum fixture(`execution_blocked`)에서 "복구 센터" primary가 나오는지 확인.

## Phase 1C — 실데이터 Cockpit

**작업**:

1. `AppViewModel` 도입 (3.4) — `remember` 일회성 생성 교체, UI 스레드 파일 I/O 금지
2. 갱신: 수동 새로고침 + 2~5초 mtime 폴링 (WatchService 후순위), 파싱 오류는 Reader 재시도 정책 사용
3. Cockpit 표시: 프로젝트/Profile/날짜/phase/status/queue/active id/authorized target/stop·blocked reason/artifact readiness/ops validation/closure/마지막 읽기 시각/**권장 다음 행동 1개**
4. 오류 3분리: 발생한 일 / 기록 보존 여부 / 사용자가 할 일
5. mock은 demo mode 전용 — 실데이터 실패 시 mock fallback 금지
6. `core.projection` UI read model들을 `composeApp/presentation/model`로 이동 (3.1)

**종료 기준**: 실제 workspace 선택 시 Cockpit이 mock 없이 현재 상태와 정확한 CTA를 표시.

## Phase 1D — 프로젝트 Registry와 날짜 탐색

**작업**:

1. Registry: `%APPDATA%\hrns-now\projects.json` — project ID/표시명/kit root/workspace root/repository root/profile/마지막 선택 날짜/마지막 진단 요약/마지막 실행 시각. secret·token·session ID·응답 원문·raw 로그 저장 금지. atomic write + 손상 복구.
2. 경계 검사(2.4)를 Registry 저장 **이전**에 수행 — 위반 시 저장 차단.
3. 날짜 탐색: 유효한 `yyyy-MM-dd` 폴더만, 최신 자동 선택, 오늘이 아니면 "읽기 전용" 배지 + 실행 CTA 비활성.
4. 해석 순서: Registry → 환경변수(fallback 유지) → 사용자 선택.

**종료 기준**: 다중 프로젝트 등록·전환 가능, 잘못된 경계는 저장 단계에서 차단.

## Phase 2 — Harness Kit 기계 판독 표면 (harness 측, Phase 1과 병행 가능)

**작업**:

1. `doctor.ps1 -Json`: `{contract_version, overall(ok|warn|fail), checks:[{id, severity, message}]}` stdout JSON + exit code 계약. 기존 텍스트 출력이 기본값. **선례: `check-secondary-llm-capability.ps1`** (stdout JSON, 파일 write 없음).
2. `validate-ops.ps1 -Json`: 동일 구조.
3. `kit-version.json` (kit root): `{kit_version, state_schema_version, ui_contract_version}` — **UI가 지원 가능 계약 버전을 판단**한다(kit이 UI 버전을 지시하지 않음).
4. UI 호환성 정책: 지원 schema major → 정상 / 미지원 major → 원문 표시 + 실행 잠금 / 상위 minor → unknown field 무시 / 버전 파일 없음 → legacy·unknown으로 실행 잠금 또는 명시 진단.
5. 로그 계약 문서화 (`OPERATING_GUIDE.md`): **이중 로그 구조 명시** — `<workspaceRoot>\logs\<날짜>\`(wrapper 실행 로그: metrics/preflight/rendered), `<dayRoot>\logs\`(day 산출물), continuity/usage-ledger 경로, 로그의 권위 수준(참고 정보).
6. `STATE_MODEL.md`에 "UI 소비 보증 필드" 절 추가.
7. 신규 smoke 3종(doctor JSON, validate-ops JSON, kit-version) + **연쇄 갱신 절차 필수**: `SMOKE_INDEX.md` 수치 + `secondary-llm-docs-calibration.ps1` 하드코딩 카운트(72/36/61/11/0 → 갱신) + 두 KIT MAP 수치 문구 → docs mismatch scan `findings 0` 확인 (0.2-1).

**금지**: 4-file surface 변경, 새 daily required artifact, `run-cycle.ps1` 비호환 변경, 텍스트 기본 출력 제거.

**종료 기준**: JSON 왕복 파싱 성공, 신규 포함 전체 automatic/offline smoke PASS, docs-scan `status=ok, findings 0`.

## Phase 3 — 진단용 PowerShell 실행 어댑터

**작업**:

1. typed command: `sealed interface HarnessCommand` — `DoctorCommand`, `ValidateOpsCommand` 먼저. 변환 형태: `powershell.exe -NoProfile -ExecutionPolicy Bypass -File <script> <typed args>` (`HRNS_POWERSHELL_PATH` 존중, 기본 Windows PowerShell 5.1).
2. Process adapter (`infra.process`): `ProcessBuilder` 인자 목록, stdout/stderr 비동기 수집(드레인 보장 — 데드락 방지), exit code, 시작/종료 시각, timeout, cancel(**Windows process tree 종료**), UTF-8 출력 처리(`[Console]::OutputEncoding` 강제 포함), secret 마스킹 필터.
3. 실행 lock (0.3-4 범위):
   - UI 내부: 중복 클릭·동일 ViewModel 병렬 실행 방지
   - per-machine: `%LOCALAPPDATA%\hrns-now\locks\<projectId>\<date>.lock.json` — `{project_id, date, owner_pid, owner_kind, started_at, heartbeat_at, command}`. stale 판별(PID 생존 + heartbeat timeout), 명시적 강제 해제 UI.
   - 외부 실행 감지: UI가 유발하지 않은 `WORKFLOW_STATE.json` 변경 감지 시 "외부 실행 중 가능성" 표시 + 새 실행 보류
   - **이 lock 체계가 준비되기 전에는 mutating command를 연결하지 않는다** (doctor/validate-ops는 읽기 전용이라 예외)
4. Setup "상태 점검 실행"/"운영 검증 실행" 버튼 → `-Json` 결과 카드 렌더링 (GPU 미충족은 Secondary LLM 비활성 안내일 뿐 앱 실패 아님).
5. Onboarding 마법사: Kit root → Workspace root → Repository root → Profile → 경계 검사 → doctor → compatibility → Registry 저장.

**종료 기준**: PowerShell 창 없이 doctor·validate-ops 실행/구조화 결과 확인, cancel 후 잔존 프로세스 0.

## Phase 4 — 표준 일일 실행 흐름

**작업**:

1. **오늘 준비**: bootstrap(no-wrapper `run-cycle.ps1 -UsePythonSidecars`)을 typed command로 — mutating이므로 lock 적용. 실행 후 날짜 폴더/4-file 확인 → State 재읽기 → CTA 재계산.
2. **요청 작성**: 폼(제목/유형/출처/우선순위/요약/상세/제약) → 안전 규칙 9종: 기존 내용 보존 / 저장 전 diff / UTF-8 no BOM / temp 작성 후 atomic move / 로드 시 hash·mtime 저장 / 저장 직전 재검증 / 외부 변경 감지 시 덮어쓰기 금지 / 재로드·수동 병합 제공 / `REQUEST_STRUCTURED.md` 편집 금지.
3. **Planning**: `-RunPlanningWrapper` (재계획은 `-RunReplanWrapper`) → 실행 중 경과·로그·retry·cancel·lock 표시 → 종료 후 exit code → State·Strategy·queue 재읽기 → CTA 재계산. Strategy 화면 좌(사람용 md)/우(기계 queue), 충돌 시 "`WORKFLOW_STATE.json`이 최종 진실" 명시, `queue.blocked_reason=dispatch_metadata_conflict`면 실행 버튼 숨김+재계획만.
4. **Execution dispatch**: CTA 허용 시에만 `-RunExecutionWrapper code|doc`. validation-only slice 매핑은 실계약 확인 후 확정(0.3-3). 실행 전 확인 패널: wrapper/현재 slice/authorized target/허용·금지 범위/예상 검증/lock/compatibility. **UI에서 target 경로 변경 불가.**
5. **실시간 로그·역할 단계**: 권위 규칙 — 실행 판단=State, 프로세스 생존=adapter, 역할 단계=구조화 event 있으면 event, 로그 문자열 추론은 "참고 정보" 라벨. 로그 테일 대상: process stdout/stderr + `<workspaceRoot>\logs\<날짜>\` wrapper 로그.
6. **실행 종료 시퀀스(고정)**: 프로세스 종료 → exit code 기록 → lock 해제 확인 → State 재읽기 → stop reason 해석 → queue 갱신 → CTA 재계산. stdout 성공 문구로 완료 처리 금지.

**종료 기준**: PowerShell 직접 실행 없이 요청→Planning→단일 slice 실행→결과 확인 완결. CTA 정책 위반 실행이 UI 차원에서 불가능.

## Phase 5 — 검증·Closure·복구 센터

**작업**:

1. Closure 체크리스트: 4-file 존재·가독(`artifacts_state`) / JSON 파싱 / state·queue 존재 / `ops_validation.passed` / active slice 부재 또는 명확한 재개 지점(`resume_from_step_id` 정합성) / handoff placeholder 부재 / `closure.is_clean_handoff`·`closure.validated` / lock 없음 / 예상 밖 Repository 변경 검토. closure 검증 실행은 `-ValidateForClosure` 활용 검토.
2. Closure policy 순수 함수: `evaluateClosure(...): Allowed | Blocked(reasons) | RequiresExplicitIncompleteHandoff(items)` + 테스트. 실행 성공이어도 조건 미충족이면 "오늘 종료" 비활성.
3. 복구 센터 (stop reason·queue 차단 marker별 발생한 일/기록 보존/허용 행동 — 부록 B·C 용어 기준): `usage_limit_blocked`(수동 재시도·명시적 resume 검토), `claude_context_limit`(fresh 준비), `transient_claude_overloaded`(동일 slice 재시도), `claude_call_timeout`(State 재확인 후 재시도), `manual_prerequisite_required`(체크리스트), `dispatch_contract_mismatch` 또는 queue의 `dispatch_metadata_conflict` marker(재계획), `role_sliced_wrapper_exception`(로그·State 확인), invalid JSON(재읽기·진단), validation failure(실패 항목 이동).
4. 진단 뷰어(읽기 전용): continuity doctor 결과(raw session ID 비표시 계약 유지), usage ledger 요약, 실패 이력, 마지막 정상 State, compatibility, lock 상태.
5. Repository 오염 확인: closure 전 `git status --short` 읽기 전용 실행 — 경고만, 수정·commit·reset 자동 수행 금지.

**종료 기준**: 모든 stop 상태에서 다음 행동이 이해 가능, closure 조건 미충족 시 종료 차단.

## Phase 6 — UI/UX QA 개선 (현재)

이 Phase는 실제 사용 화면을 QA하여 확인된 혼란·불편을 해소하는 제품 개선 Phase다. Harness 계약, `WORKFLOW_STATE.json` 소유권, typed command/lock/State reread 순서, 기존 CTA 권한은 바꾸지 않는다.

**작업**:

1. 활성 프로젝트를 상단에서 명확히 식별하고, 프로젝트 등록·전환·수정은 `프로젝트 관리` modal로 분리한다. 프로젝트가 없을 때만 온보딩 등록 화면을 보인다.
2. `환경 점검`을 포함한 실행 action에 running/success/failure/cancel 또는 retry의 명확한 feedback을 제공한다. success는 결과 badge와 완료 시각을 보이되 외부 State가 달라질 수 있으므로 버튼을 영구 비활성화하지 않는다.
3. 화면 정보 구조와 한국어 용어를 정리한다: 작업 현황, 다음 작업, 상태 진단, 최근 작업 기록, 마지막 정상 상태, 작업 계획, 개발 전략, 작업 대기열, 요구사항 작성, 실행 기록, 환경 점검, 작업 기준 점검, 작업 준비.
4. `요구사항 작성`을 상단 CTA와 modal editor로 제공한다. 저장 비활성 조건·필수 입력 오류·미저장 닫기 확인·저장 완료 feedback을 명확히 하며 기존 Request optimistic concurrency 계약을 유지한다.
5. 중복되는 역할별 진행 단계 등 기술 정보는 기본 화면에서 제거하거나 상세 영역으로 접는다. action label은 여전히 action ID가 아니며 정책은 typed `UiAction`으로만 판단한다.
6. Windows installer는 현재 MSI/JPackage/WiX 계약 안에서 이름·아이콘·안내·설치 흐름의 최소 품질을 개선한다. custom bootstrapper, code signing, update, bundled Harness Runtime은 이 Phase 범위가 아니다.

**종료 기준 (G6-UX)**: 활성 프로젝트와 단일 다음 작업이 즉시 식별되고, action 실행 상태와 결과가 명확하며, 용어가 일관되고, 요구사항 modal의 저장·오류·닫기 흐름이 검증된다. UI는 State/Harness 파일을 직접 쓰지 않고, 기존 core/infra 계약 회귀 없이 `./gradlew check`와 수동 QA가 PASS한다.

## [보류 배포 과제] 기존 Phase 6 — Windows 패키징·배포 (6A/6B Gate)

### Phase 6A — 외부 Kit MSI MVP

**작업**:

1. `targetFormats(TargetFormat.Msi)`만 유지한다. 앱 표시명·package name·버전·Windows icon을 단일 version source와 실제 산출물에 맞춰 정리하고, 실행 가능한 JRE를 번들한다. 모듈 축소(jlink)는 smoke로 증명할 수 있을 때만 허용한다.
2. 설치 디렉터리는 Program Files의 읽기/실행 영역이다. 앱·JRE 외의 Registry, lock, workspace, 로그, cache를 쓰지 않으며 제거 기본값은 `%APPDATA%\hrns-now` Registry와 `%LOCALAPPDATA%\hrns-now` 사용자 데이터를 보존한다.
3. Kit 해석은 기존 `Registry → 환경변수 → 사용자 선택`과 `CompatibilityPolicy`를 유지한다. `D:\harness-kit`·개발 fixture·날짜를 하드코딩하거나 Kit을 검색·복사·내장하지 않는다. Runtime/repository/workspace 경계 검사는 기존 `BoundaryPolicy`를 우회하지 않는다.
4. 실제 MSI build와 clean Windows 설치 smoke를 수행한다: 설치 → 외부 Kit 지정 → 프로젝트 등록 → doctor → State 조회 → 표준 일일 cycle. 공백·한글·서로 다른 drive letter 경로를 포함하고, 앱/프로세스 출력이 Program Files가 아니라 선택 workspace에만 생기는지 확인한다.
5. 미서명 MSI의 SmartScreen 경고와 코드 서명이 아직 배포 Gate가 아님을 문서화한다. 인증서·private key·secret을 저장·생성·commit하지 않는다.

**6A 종료 기준 (G6A)**: MSI와 JRE가 clean Windows에서 실행되고, 외부 호환 Kit 구성으로 위 smoke와 `%APPDATA%`/`%LOCALAPPDATA%` 보존 smoke가 PASS한다. 이 Gate는 번들 Runtime 없는 설치 MVP만 승인하며 Phase 6 전체 완료나 Phase 7 진입을 뜻하지 않는다.

### Phase 6B — 승인된 Harness Runtime 릴리스 통합

**선행 소유권 Gate (Harness 측)**: Harness 저장소가 다음을 release artifact로 제공하고, UI 측과 함께 검증하기 전에는 시작하지 않는다.

1. 개발 트리와 분리된 재현 가능한 Runtime artifact와 허가된 재배포 범위
2. 공개 `run-cycle.ps1`/`doctor.ps1`/`validate-ops.ps1` entrypoint 및 필요한 script/template만을 명시한 immutable manifest
3. SHA-256 checksum, Runtime version, `ui_contract_version`, `state_schema_version`, Runtime smoke와 금지 파일/secret scan 결과
4. MSI staging으로 넣어도 Program Files를 쓰지 않고 모든 Harness 산출물을 project workspace에만 쓰는 검증

**작업**: 위 artifact만 build staging으로 소비하고 private Harness source·`.git`·workspace·로그·session ID·fixture·secret을 제외한다. Runtime source 선택은 composition root에서 주입하는 typed configuration/adapter로 제한하며, `CompatibilityPolicy`는 계속 UI가 판단한다. 프로젝트 등록의 LocalAppData 기본 workspace 자동 생성, 이름+short UUID, 부분 실패 보존·재시도, repair/재설치 복원은 Registry·BoundaryPolicy·Harness bootstrap 소유권을 지키는 범위에서 구현한다. UI는 daily 4-file을 직접 만들지 않는다.

**6B 종료 기준 (G6B)**: 승인 artifact가 없으면 packaging이 fail-closed로 중단되고, 승인 artifact로만 MSI가 재현 가능하다. clean Windows에서 Kit 경로 수동 지정 없이 project workspace 생성 → doctor → 표준 cycle이 PASS하며 제거·재설치 뒤 사용자 데이터 보존과 재연결이 검증된다.

## [보류 제품 과제] 기존 Phase 7 — 실험 기능 (opt-in, 메인 흐름과 완전 분리)

Secondary LLM capability 뷰어 / candidate·audit 뷰어(비권위 라벨 필수) / live Ollama 명시적 opt-in(capability gate 결과 표시, CPU-only는 진단 안내) / legacy compatibility 뷰 / smoke suite runner / raw State 뷰어 / 고급 로그 필터.

원칙: 자동 채택 금지, 메인 CTA에 영향 금지, 기능 실패가 기본 운영에 무영향.

## Phase 7 이후 — Post-MVP 배포 확장 (Phase 7과 분리)

1. **D1 서명·릴리스 운영**: CI provenance와 보관 정책을 갖춘 Authenticode/MSI 서명, SmartScreen 대응, EULA·개인정보·비민감 crash report 정책. 인증서와 서명 키는 저장소·로그·report에 넣지 않는다.
2. **D2 업데이트·복구**: 전체 MSI update를 우선하고, side-by-side Runtime/current pointer/rollback은 별도 atomicity·lock·복구 설계와 smoke가 승인된 뒤에만 도입한다.
3. **D3 상품화**: CD Key/라이선스 서명·entitlement 정책은 제품 결정과 보안 검토 후 별도 구현한다. Runtime 암호화·난독화와 혼동하지 않으며 기존 CTA 권한을 소급해 바꾸지 않는다.
4. **D4 Portable data mode**: 기본 AppData 분리 정책을 유지한다. 외장 저장소 data mode는 lock·장치 분리·삭제·복구 정책을 별도 Gate로 검증하기 전에는 제공하지 않는다.

---

# 5. 실행 모델 배분 (합의된 전략)

전반 실행은 Sonnet, 고위험·고공수 지점만 Fable을 사용한다.

| 대상 | 모델 | 근거 |
|---|---|---|
| **Phase 2 전체** | **Fable** | 살아있는 harness 엔진 직접 수정 + 자기검증망(smoke 72, SMOKE_INDEX 정합성, docs-scan 하드코딩 카운트 연쇄 갱신) + PS 5.1/StrictMode/UTF-8 no BOM 제약. 실수가 조용히 통과했다가 늦게 터지는 구조 |
| **Phase 3의 `infra.process` + lock 코어** | **Fable** | Windows process tree 종료, 비동기 스트림 드레인(데드락), cancel·좀비 방지, stale lock 판별, 인코딩 — happy path 테스트를 통과하는 동시성 버그의 전형 지대. Phase 4~5 전체가 딛는 기반 |
| Phase 0A/0B, 1A~1D, 3의 화면 배선, 4, 5, 6A, 7 | Sonnet | 본 문서와 부록이 실행 명세 수준으로 작성됨(계약표·결정표·종료 기준·테스트 목록). 종료 기준 검증으로 품질 고정 |
| Phase 6B의 Runtime release/staging 경계 | Harness 소유자 + Codex 교차검증 | private Runtime 재배포, manifest/checksum, secret scan, MSI staging은 UI 단독 구현으로 승인할 수 없음 |

Phase 4 착수 시 실행 종료 시퀀스와 lock 상호작용에서 문제가 재발하면 해당 부분만 Fable로 승격한다.

---

# 6. 테스트·CI 전략

- **core**: CTA 결정표 전 행, Closure policy, 경계 검사, compatibility policy, unknown fail-closed, Artifact 분류 계산
- **infra**: 날짜 탐색, JSON parsing, partial write retry, BOM/encoding, Registry atomic write·손상 복구, 인자 생성, 마스킹, lock/lease(stale·PID·heartbeat), Windows path(공백·한글)
- **composeApp**: ViewModel 상태 전이, refresh, 선택 전환, disabled CTA, 오류 projection, demo mode 분리
- **통합**: fixture workspace, doctor/validate-ops JSON, PowerShell process, cancel, timeout, lock 충돌, 요청 낙관적 동시성, State 재읽기
- **CI**: 초기 `./gradlew check`(비-Windows 가능) → Phase 3부터 Windows runner(adapter·PS fixture) → Phase 6A MSI build + 설치·실행·사용자 데이터 보존 smoke → Phase 6B는 승인 Runtime artifact의 manifest/checksum/secret-scan/Runtime smoke + MSI 재현성 검증

---

# 7. 배포 전 필수 Gate

| Gate | 조건 |
|---|---|
| G0 | legacy 계약 제거, 4-file probe 정확, `./gradlew check` + CI PASS |
| G1 | live State parsing, typed CTA, fail-closed, 실데이터 Cockpit, Registry 경계 차단 |
| G2 | doctor/validate-ops JSON, compatibility handshake, harness smoke 전체 + docs-scan PASS |
| G3 | Process adapter, cancel 무잔존, secret masking, lock 체계 |
| G4 | bootstrap, request 안전 저장, Planning, execution dispatch, 종료 시퀀스 |
| G5 | Closure policy, Recovery Center, 잘못된 완료 차단 |
| G6-UX | 활성 프로젝트·단일 다음 작업·실행 feedback·일관된 용어·요구사항 modal·기존 계약 회귀 없음 |
| 보류 G6A | 외부 Kit MSI, JRE, clean Windows install/uninstall 보존 smoke, 경로 이식성 |
| 보류 G6B | 승인된 Harness Runtime artifact의 staging·manifest/checksum·secret-scan·Runtime smoke, 번들 MSI 재현성·재설치 복원 |

---

# 8. 하지 말아야 할 개발

- 화면부터 여러 개 병렬 완성 / `WORKFLOW_STATE.json` 직접 수정 / Markdown 문구로 실행 여부 결정
- UI에서 authorized target 변경 / 임의 PowerShell 콘솔 / Claude API 직접 호출
- stdout 성공 문구만으로 완료 처리 / mock을 실데이터 실패 fallback으로 사용
- 자동 session resume 기본 활성화, `--continue` 사용
- UI 내부 lock만으로 동시 실행 방지했다고 간주 / unknown status에서 낙관적 실행
- optional artifact 누락을 전체 실패로 처리 / **로그 디렉터리를 required로 취급**
- harness에 없는 wrapper 모드·상태 코드 창작 (`-RunExecutionWrapper validation` 등)
- macOS/Linux를 Windows MVP와 동시 추진 / 테스트·CI 없이 Phase 진행
- `D:\harness-kit` 개발 트리·private Harness 원본을 복사하거나 MSI/Git에 포함 / 승인 artifact 없이 번들 Runtime을 성공 처리
- 라이선스·CD Key·자동 업데이트·portable data mode를 Phase 6A 또는 Phase 7의 CTA 흐름에 선구현
- harness smoke·docs-scan 연쇄 갱신 없이 Phase 2 완료 선언

---

# 9. 최초 수직 Slice (고정)

```text
Phase 0A 계약 수정 → fixture 테스트 → Phase 0B CI
→ WorkspaceDay 선택 → WORKFLOW_STATE.json 읽기 → typed 상태 투영
→ CTA 계산 → Cockpit 표시 → 파일 변경 후 새로고침
```

이 Slice 완료 전에는 Planning·Execution 버튼을 실제 PowerShell에 연결하지 않는다.

---

# 10. 최종 완료 정의 (MVP)

1. 프로젝트 등록 가능, 2. 경계 위반 차단, 3. 실제 State 읽기, 4. 정확한 상태·권장 행동 표시, 5. unknown 상태 실행 잠금, 6. doctor/validate-ops 앱 내 실행, 7. 요청 작성 시 외부 변경 미덮어쓰기, 8. 허가된 단일 slice만 실행, 9. 실행 후 State 재읽기, 10. UI 외 프로세스 동시 실행 감지·차단, 11. 실행 성공≠Closure 구분, 12. 주요 stop reason 전부 복구 경로 보유, 13. secret·raw session ID 비노출, 14. G6A 외부 Kit MSI와 clean Windows smoke 통과, 15. 번들 제품을 표방할 경우 G6B 승인 Runtime artifact·재현성·재설치 smoke까지 통과.

---

# 부록

## 부록 A — 실측 `WORKFLOW_STATE.json` 스키마 (UI 바인딩 키)

실측: `D:\harness-workspaces\auziraum\2026-06-26\WORKFLOW_STATE.json`, `schema_version: "1.0"`

```text
top-level : schema_version, date, project_name, workspace_root, repo_root, profile,
            default_runtime_mode, state, queue, required_next_action, ...
state     : current_phase, current_status, next_action, execution_wrapper, stop_reason,
            human_action_required, execution_completed, closure_validated, clean_handoff,
            blocked_reason, failed_reason, resume_from_step_id,
            artifacts_state {request_inbox|today_strategy|daily_handoff|workflow_state},
            ops_validation {passed, validated_at, notes},
            closure {is_clean_handoff, validated, validated_at, validator_notes},
            authorized_target_file, selected_current_work, current_slice, slice_queue,
            request_thread_id, execution_mode, role_sliced, usage_guard, ...
queue     : status, active {card_id, slice_id}, queue_log[{timestamp, event, details}],
            blocked_reason, last_updated_at
```

`queue.active`는 포인터만 갖는다. slice 상세는 state 쪽(`current_slice`/`slice_queue`/`authorized_target_file`)이며 요소 존재 시점의 실제 파일과 `STATE_MODEL.md`로 확정한다.

## 부록 B — 상태별 CTA 정책 결정표 (전 행 parameterized test 대상)

| 상태 | Primary CTA | 금지 행동 |
|---|---|---|
| 프로젝트 미연결 | 프로젝트 연결 | Planning·Execution |
| 날짜 미선택 | 날짜 선택 | Write·Execution |
| 과거 날짜 | 없음 또는 오늘 열기 | 모든 Write·Execution |
| `request_intake_pending` | 요청 작성 | Execution |
| `no_request` | 새 요청 추가 | 빈 Planning 반복 |
| `planning_required` | Planning 실행 | Code/Doc 실행 |
| `planning_completed` | 계획 검토 | Closure |
| `execution_ready` + code | Code slice 실행 | Doc 실행·Target 변경 |
| `execution_ready` + doc | Doc slice 실행 | Code 실행·Target 변경 |
| validation-only | 검증 실행 | Source edit |
| `execution_blocked` | 복구 센터 | 무조건 재실행 |
| `manual_prerequisite_required` | 선행조건 확인 | 자동 실행 |
| `usage_limit_blocked` | 복구 옵션 | 자동 무한 retry |
| `claude_context_limit` | Fresh 실행 안내 | 자동 resume |
| `queue.blocked_reason=dispatch_metadata_conflict` | 재계획 | 실행 버튼 |
| `execution_completed` | 검증·인계 | 같은 slice 중복 실행 |
| `closure_validated` | 다음 날짜 준비 | 오늘 queue 수정 |
| State invalid/미파싱 | 복구 센터 | 모든 Write·Execution |
| 호환성 불일치 | 호환성 안내 | 모든 실행 |
| 다른 프로세스 lock | 실행 현황 확인 | 새 실행 |

## 부록 C — 실패·정지 용어 실측 대조

| 분류 | 용어 (harness-kit 실존 grep 확인) |
|---|---|
| Claude call-safety | `claude_call_timeout`, `claude_response_empty`, `claude_response_too_short`, `usage_limit_blocked`, `claude_context_limit`, `budget_max_turns`, `budget_or_manual_stop` |
| dispatch/실행 stop reason | `dispatch_contract_mismatch`, `transient_claude_overloaded`, `role_sliced_wrapper_exception`, `manual_prerequisite_required` |
| planning queue 차단 marker | `dispatch_metadata_conflict` (`queue.blocked_reason`/`purpose_marker`, stop reason 아님) |
| 상태 값 | `request_intake_pending`, `no_request`, `planning_required`, `planning_completed`, `execution_ready`, `execution_blocked`(live 실측), `execution_completed`, `closure_validated` |
| 역할 단계 | navi, worker, reviewer, dockeeper (+ parent) |
| **제거할 창작 용어** | `packet_contract_failed`, `state_finalization_failed`, `new_target_path_failed` |

## 부록 D — 경로·파일 계약 (이중 로그 구조 반영)

```text
<workspaceRoot>\<yyyy-MM-dd>\REQUEST_INBOX.md       필수 · 사람 소유(보존 규칙 하 폼 저장)
<workspaceRoot>\<yyyy-MM-dd>\TODAY_STRATEGY.md       필수 · 읽기 전용
<workspaceRoot>\<yyyy-MM-dd>\DAILY_HANDOFF.md        필수 · 읽기 전용 + Closure 확인
<workspaceRoot>\<yyyy-MM-dd>\WORKFLOW_STATE.json     필수 · 유일한 기계 진실(UI 쓰기 금지)
<workspaceRoot>\<yyyy-MM-dd>\REQUEST_STRUCTURED.md   optional · 사용자 입력 노출 금지
<workspaceRoot>\<yyyy-MM-dd>\logs\                   optional · day 산출물(secondary-llm 등)
<workspaceRoot>\logs\<yyyy-MM-dd>\                   optional · wrapper 실행 로그(테일 대상)
<workspaceRoot>\logs\claude-session-continuity\<날짜>\   진단 전용(세션 id 비표시 계약)
<workspaceRoot>\logs\usage-ledger\<날짜>.jsonl        진단 전용
WORKDAY_STATE.json / WORK_QUEUE.json                 legacy fallback · 기본 화면 숨김
```

UI 소유 파일은 harness workspace 밖에 둔다: Registry `%APPDATA%\hrns-now\`, lock `%LOCALAPPDATA%\hrns-now\locks\`.

## 부록 E — harness-kit 측 백로그

| 항목 | Phase | 상태 |
|---|---|---|
| `doctor.ps1 -Json` / `validate-ops.ps1 -Json` | 2 | 미구현 (현재 Write-Host 전용) |
| `kit-version.json` | 2 | 미구현 |
| 이중 로그 구조 계약 문서화 | 2 | 부분(구조 실존, 계약 미명시) |
| 신규 smoke + `SMOKE_INDEX.md` + docs-calibration 하드코딩 카운트 + KIT MAP 수치 연쇄 갱신 | 2 | 필수 절차 |
| `STATE_MODEL.md` UI 소비 보증 필드 절 | 2 | 미구현 |
| harness 협조 cross-process lock 계약 (wrapper가 lock 확인) | 별도 승인 | 설계 필요 — 그 전까지 UI는 감지 휴리스틱만 |
| validation-only slice의 UI 실행 매핑 확정 | 4 착수 시 | `-RunExecutionWrapper`는 `none|code|doc|auto`만 실존 |
| 잔재 파일 정리 (`SMOKE_INDEX.md.rej` 등 3건) | 언제든 | 삭제 승인 대기 |

---

# 결론

세 분석의 교차검증으로 확정된 사실: 기존 UI 자산과 모듈 구조는 폐기 대상이 아니고, 가장 위험한 것은 "없는 코드"가 아니라 "폐기된 계약을 구현한 코드"이며, 이번 최종 라운드에서는 앞선 두 문서가 공유하던 로그 경로 오진과 존재하지 않는 wrapper 모드까지 실측으로 바로잡았다.

개발 순서는 다음으로 고정한다.

```text
계약 재정렬(0A) → 테스트·CI(0B) → State Reader(1A) → CTA Policy(1B)
→ Live Cockpit(1C) → Registry(1D) → [병행] Harness JSON Contract(2, Fable)
→ 진단 Process Adapter + Lock(3, 코어 Fable) → 표준 일일 실행(4)
→ Closure·Recovery(5) → UI/UX QA 개선(새 6, G6-UX)
→ [보류] 외부 Kit MSI(기존 6A) → 승인 Runtime 통합(기존 6B, 조건부)
→ [보류] 실험 기능(기존 7) → Post-MVP 배포 확장(D1~D4)
```

> **최초 제품 목표(고정): 사용자가 프로젝트를 선택하면, HRNS-NOW는 현재 Harness 상태를 정확히 읽고 지금 허용된 단 하나의 다음 행동만 안전하게 안내하고 실행한다.**
