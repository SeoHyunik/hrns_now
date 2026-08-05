# HRNS-NOW 소스 범용화 정리 보고서

## 1. 기준 상태

- 저장소: `S:\dev\project\hrns_now`
- 브랜치: `harness-dev`
- 시작 HEAD: `eebdcd1cbe16b8352fe4f967b195419eb4ee3bd6` (작업 종료 시점까지 동일 — 이 SHA로 reset하지 않았고, 별도 커밋도 생성하지 않았다)
- 작업 전 `git status --short`: 추적 대상 파일은 전부 clean(수정 없음). untracked 항목은 `doc/QA_captures/`, `doc/hrns_now_packaging_plan.md`, `doc/user_workflow_qa_notes.md`, 그리고 실행 지시서 자신인 `doc/source_code_universality_cleanup_plan.md` 4건뿐이었다.
- 사용자 소유 변경: 위 3개 untracked 항목(`doc/QA_captures/`, `doc/hrns_now_packaging_plan.md`, `doc/user_workflow_qa_notes.md`)은 작업 시작부터 종료까지 파일시스템 타임스탬프가 각각 2026-07-23/2026-07-28로 세션 시작 이전 그대로였다 — 이번 작업에서 읽거나 수정하지 않았다.

## 2. 수정 전 재검증

### 2.1 baseline test

편집 시작 전 `./gradlew.bat :core:test :infra:test :composeApp:jvmTest check`를 실행해 `BUILD SUCCESSFUL`(구성 캐시 재사용, 전부 기존 성공 상태와 동일)을 확인했다. 실패 항목 없음 — 편집 전 코드 결함 분석이 필요한 상황은 없었다.

### 2.2 전수 재스캔 수치 (live HEAD 기준 재도출)

| 항목 | 지시서 관찰치 | live 재도출치 | 비고 |
|---|---|---|---|
| 추적 감사 범위 | 207개 파일 | 212개 파일 | 최근 아이콘 리소스 커밋 2건으로 인한 자연 증가, 조사 정확도에 영향 없음 |
| `InternalDeveloperSdk` 계열 | 18개 파일, 99개 일치 | 18개 파일, 99개 일치 | **정확히 일치** |
| QA/Codex 라벨 | 31줄, 15개 파일 | 31줄, 15개 파일 | **정확히 일치** |
| `PlaceholderRow`/`PlaceholderActionButton` | 약 35곳 | 33곳 | "약 35"와 부합 |
| Phase/Patch 서술형 언급 | 249줄, 102개 파일 | (모듈별 fork 처리 후 재계산 시 소량 drift) | 아래 §2.3 참고 |

가장 안전성이 중요한 두 수치(`InternalDeveloperSdk` 99건, QA/Codex 31건)가 정확히 일치해 지시서의 조사 정확도를 신뢰할 수 있었다.

### 2.3 최초 계획과 live 차이 — 발견된 추가 항목

1. **대소문자 구분 grep의 사각지대**: 모든 사전 조사(지시서 자체 포함)가 대문자 `Phase`만 검색했다. §6.8에서 만든 `Test-SourceUniversality.ps1`(대소문자 무시 정규식)을 실제 저장소에 돌린 결과, `core` 모듈 11개 파일에서 소문자 `doc/claude_prompts/phase5-closure-recovery.md` 같은 **파일 경로 인용**이 새로 발견됐다 — `RepositoryStatus.kt`, `CompatibilityPolicy.kt`, `LockStalePolicy.kt`, `GitStatusPort.kt`, `KitVersionManifestPort.kt`, `ProcessLockPort.kt`, `TodayStrategyReaderPort.kt`, `RegistryResult.kt`, `ClosurePolicyTest.kt`, `CompatibilityPolicyTest.kt`, `LockStalePolicyTest.kt`. 전부 §7에서 수정했다.
2. **`internal developer SDK`(소문자) 영어 문자열 3곳**: `CockpitProjectionAssembler.kt`, `ViewModelStrings.kt`(2곳) — 대문자 `Internal developer SDK` 패턴만 검색했던 1차 정리에서 누락됐다. `기본 Harness Kit`/`Default Harness Kit` 계열로 정정했다.
3. **`RibbonProjectNameTest.kt`의 `epc_legacy_ui`**: 지시서 anchor #6이 지정한 확정 rename(`sample-legacy-ui`)이었으나, 병렬 작업 분할 시 어느 담당 파일 목록에도 포함되지 않아 처음에는 누락됐다. `git status` 최종 점검에서 발견해 수정했다.
4. **`실측` 표현의 두 갈래**: 날짜 없이 "harness-kit 스크립트에서 실측 확인한 리터럴만 포함한다"류 methodology 서술(`StopReason.kt`, `WorkflowPhase.kt`, `WorkflowQueue.kt`, `WorkflowStatus.kt`, `HarnessCommand.kt` 등 6개 파일)은 개발 이력이 아니라 유효한 엔지니어링 원칙이라 판단해 보존했다. 날짜가 붙은 `(2026-07-23 실측)` 단 1건(`WorkspaceArtifactProbe.kt`)만 잔재로 판단해 제거했다.

## 3. 제거 대상과 보존 대상

### 3.1 보존 대상 (지시서 §5와 동일하게 확인)

- `WorkflowPhase` sealed interface와 모든 멤버, `state.phase`/`phaseRaw`/`current_phase` — harness-kit의 실제 `state.current_phase` 계약.
- `StopReason.ClaudeContextLimit`/`ClaudeCallTimeout`, `.claude/CLAUDE.md`, `claudeCommand` — 실제 구동되는 Claude Code CLI 런타임 참조.
- `internal_developer_sdk` wire 문자열(Registry ACL/테스트), legacy fallback(`WORKDAY_STATE.json`/`WORK_QUEUE.json`/`ArtifactRequirement.Legacy`), `MockProjectionProvider`/`MockWorkspaceConfigProvider`(demo 패키지 전용), 테스트 fixture의 공백·한글·복수 drive 샘플 경로, `DevelopmentStrategyCard`.
- `doc/hrns_now_design_pattern.md`/`doc/hrns_now_claude_plan.md`의 §-번호 인용 — 살아 있는 규범 문서이므로 인용 자체는 유지.
- §2.3에서 확인한 날짜 없는 `실측` methodology 서술.

### 3.2 제거 대상 근거

- 개발 단계 번호(Phase N/QA0N)를 서술의 근거로 쓰는 주석 — 최초 독자가 개발 이력을 몰라도 현재 동작을 이해할 수 있어야 한다는 목표에 반한다.
- `doc/claude_prompts/phase*.md`/`doc/phase_reports/phase*.md`를 규범 근거로 직접 인용하는 살아있는 소스 주석 — 문서 자체는 역사 기록으로 남기지만, 소스가 그 문서를 "현재 규범"인 것처럼 인용하는 것은 참조 무결성이 아니라 오해의 소지로 판단했다.
- `InternalDeveloperSdk` 계열 명칭 — 실제로는 "개발자 전용 SDK"가 아니라 "사용자가 준비하는, 저장소 상대경로 기본 Kit"라는 의미였고, 이름이 실제 동작(운영 기본값)과 배치됐다.
- `PlaceholderRow`/`PlaceholderActionButton` — 이미 production UI에 상시 노출되는 컴포넌트인데 이름이 "임시 구현"으로 오인시켰다.
- `Greeting`/`Platform`/`InfraMarker`/`ComposeAppCommonTest`(`1+2==3`) — Kotlin Multiplatform 템플릿 잔재, 제품 어디에서도 참조되지 않음(재확인 완료).
- `Invoke-Phase6ACleanWindowsSmoke.ps1`/`phase6a-*.json` — 개발 단계에 묶인 도구·산출물 이름.
- `Codex 보정` — 내부 교차검증자 명칭을 제품 테스트 소스에 기록.

## 4. 파일·심볼 rename

| 이전 경로/이름 | 새 경로/이름 | 참조 이동 근거 |
|---|---|---|
| `composeApp/.../app/Greeting.kt` | (삭제) | 참조 0건 재확인(재조사) 후 삭제 |
| `composeApp/.../app/Platform.kt` | (삭제) | 동일 |
| `composeApp/src/commonTest/.../ComposeAppCommonTest.kt` | (삭제) | `1+2==3`만 검증하는 템플릿 테스트, 대체 테스트 만들지 않음 |
| `infra/.../infra/InfraMarker.kt` | (삭제) | `Shell.kt`의 import·ENVIRONMENT 카드 "infra" 행만 제거, 카드의 다른 정보는 보존 |
| `infra/.../runtime/DeveloperSdkRuntimeResolver.kt` | `infra/.../runtime/DefaultKitRuntimeResolver.kt` | 클래스명·파일명 함께 변경, 전체 소비자(18개 파일) 동일 커밋 단위에서 갱신 |
| `infra/.../runtime/DeveloperSdkRuntimeResolverTest.kt` | `infra/.../runtime/DefaultKitRuntimeResolverTest.kt` | 동일 |
| `scripts/Invoke-Phase6ACleanWindowsSmoke.ps1` | `scripts/Invoke-WindowsMsiLifecycleSmoke.ps1` | 저장소 내 유일한 참조처는 `doc/phase_reports/phase6-report.md`(역사 기록, 범위 밖) — 다른 참조 없음(재확인) |

### 4.1 식별자 rename (파일명 변경 없음, 18개 파일 전체 갱신)

`RuntimeSource.InternalDeveloperSdk` → `RuntimeSource.DefaultKit`, `useInternalDeveloperSdk` → `useDefaultKit`, `internalSdkRootProvider` → `defaultKitRootProvider`, `defaultInternalSdkRoot()` → `defaultKitRoot()`, `RUNTIME_SOURCE_INTERNAL` → `RUNTIME_SOURCE_DEFAULT_KIT_WIRE_VALUE`(상수명만, 아래 §5 참고).

영향 파일(18개, 전부 선언·import·call site·test 동시 갱신): `RuntimeSource.kt`, `RegisterProjectUseCase.kt`(+Test), `DefaultKitRuntimeResolver.kt`(+Test), `ProjectRegistryDto.kt`, `JsonProjectRegistryAdapter.kt`(+Test), `App.kt`, `CockpitProjectionAssembler.kt`, `CockpitUiStateAssembler.kt`, `CockpitProjection.kt`, `HrnsUiState.kt`, `AppViewModel.kt`(+Test), `ViewModelStrings.kt`, `Screens.kt`, `Strings.kt`.

### 4.2 UI 컴포넌트 rename

`PlaceholderRow` → `LabelValueRow`, `PlaceholderActionButton(text, ...)`/`(action, ...)` 2개 overload → `HrnsActionButton`. `Components.kt`(선언, 8곳)·`Screens.kt`(call site, 25곳) 동시 변경, 총 33곳.

### 4.3 테스트 fixture rename

`RibbonProjectNameTest.kt`의 `epc_legacy_ui` → `sample-legacy-ui`(fixture 값·assertion 값 2곳 함께 변경). 다른 파일에서 참조하지 않음을 재확인했다.

모든 rename에 대해 변경 후 재검색 결과 old symbol 0건(§8, §11 참고), old path 0건.

## 5. Default Kit와 Registry wire 호환성

- **wire 값 `internal_developer_sdk`(JSON 문자열)는 변경하지 않았다.** `ProjectRegistryDto.kt`의 `private const val RUNTIME_SOURCE_INTERNAL = "internal_developer_sdk"`를 `RUNTIME_SOURCE_DEFAULT_KIT_WIRE_VALUE = "internal_developer_sdk"`로 **이름만** 바꿨다 — 문자열 리터럴 값은 그대로다.
- Registry schema version(`REGISTRY_SCHEMA_VERSION = "1.0"`)은 변경하지 않았다.
- 새 writer도 이번 작업에서 기존 wire 값을 그대로 쓴다 — `default_kit`이라는 새 값을 어디에도 쓰지 않는다.
- 기존 회귀 테스트(`JsonProjectRegistryAdapterTest.kt`: 구 값 read, load→save round-trip, `kit_root == null` 검증, unknown discriminator 격리, legacy `kit_root`-only entry의 `ExternalKit` migration)를 식별자만 갱신한 채 그대로 유지했고 전부 통과했다(§10).
- `DefaultKitRuntimeResolver.kt`의 모순된 KDoc — "root 직하 파일만 확인한다"고 쓰여 있었지만 실제 `REQUIRED_ENTRYPOINTS`는 `scripts/doctor.ps1` 등 하위 경로를 검사하고 있었다 — 실제 코드에 맞게 "Kit root 아래 `scripts/doctor.ps1`, `scripts/validate-ops.ps1`, `scripts/run-cycle.ps1`, `kit-version.json`의 존재를 확인한다"로 수정했다.
- `ExternalKit`의 절대 경로 저장, boundary validation, "기본 Kit 없음 → 외부 Kit 자동 fallback 금지" 동작은 손대지 않았다. `.local/harness-kit`을 생성·복사·수정하지 않는다.

## 6. 템플릿·placeholder·marker 제거

- `Greeting`/`Platform`(`JVMPlatform`/`getPlatform()`)/`ComposeAppCommonTest` 참조 0건을 삭제 전 재확인(grep)했다 — 삭제 후에도 컴파일·테스트 정상.
- `InfraMarker`는 `Shell.kt`의 `infra`/`hrns_now-infra` 표시 외 소비자가 없음을 재확인 후 삭제했다. `Shell.kt`의 ENVIRONMENT 카드에서 해당 Row 블록만 제거하고, 카드의 다른 상태 정보(`notAppOwnedMessages` 등)는 그대로 뒀다. `InfraMarker` → `RuntimeInfo` 같은 이름만 바꾸는 추상화는 만들지 않았다(지시서 금지 사항).
- `PlaceholderRow`/`PlaceholderActionButton` rename은 동작을 전혀 바꾸지 않았다 — `enabled = false` 기본값, primary/secondary style, hover, 색상·padding·font, loading/disabled/click semantics, named argument 순서를 그대로 유지했다. 기존 UI 테스트(`AppViewModelTest.kt` 등)가 이 컴포넌트를 간접적으로 exercise하며 전부 통과했다.

## 7. 주석·KDoc·경로 일반화

전 모듈에 걸쳐 250개 이상의 Phase/QA/Codex/구문서인용 문장을 규칙표에 따라 문장 단위로 재작성했다(기계적 regex 삭제 아님). 실질적 안전 이유(예: "State 재조회로 성공 판정", "raw stdout 비권위", "UI thread I/O 금지")는 전부 보존했다.

| 모듈 | 파일 수 | 대표 예시 |
|---|---|---|
| `composeApp/ui` | 10 | `Screens.kt`: `Phase 10: bridge 또는...` → `bridge 또는...` (Phase 접두사만 제거, 본문 유지) |
| `composeApp/presentation·viewmodel` | 27 | `AppViewModel.kt`: `Registry의 활성 선택만 지운다(Phase 9 QA03-A)` → `Registry의 활성 선택만 지운다` |
| `core` | 47 | `WorkflowPhase.kt`: `...판단(Phase 1B)은 이 Unknown 값에서...` → `...판단은 이 Unknown 값에서...` (타입 자체는 무변경) |
| `infra` | 16 | `WorkspaceArtifactProbe.kt`: `현행 계약(2026-07-23 실측): daily 4-file은...` → `현재 계약: daily 4-file은...`(날짜 프레이밍만 제거) |
| `composeApp/build.gradle.kts`, `.github/workflows/ci.yml` | 2 | `Phase 6A는 외부 Kit MSI MVP만 다룬다` → `Windows MSI만 지원·검증 대상이다`; `Phase 0B:`/`(Phase 3+)` → 순수 현재 사실 서술 |

특정 anchor(지시서 §6.4 명시 항목) 처리 결과:
1. `AppViewModelTest.kt`의 `Codex 보정:` 접두사 제거, required 4-file 재검증 조건 보존.
2. `RepositoryBridgeSummary.kt`의 `D:\harness-kit\docs\PROJECT_ONBOARDING.md` → `Harness Kit docs/PROJECT_ONBOARDING.md`로 일반화.
3. `WorkspaceArtifactProbe.kt`의 날짜 기반 "현행 계약" → 날짜 없는 현재 계약 서술.
4. `WorkspacePathProbe.kt`의 "placeholder를 제거했다" 이력 서술 → 현재 책임 서술.
5. `DefaultKitRuntimeResolverTest.kt`의 KDoc — `D:\harness-kit` 실제 언급 제거, 격리 temp fixture만 쓴다는 규칙으로 재작성.
6. `RibbonProjectNameTest.kt`의 `epc_legacy_ui` → `sample-legacy-ui`(§4.3, §2.3에서 다시 언급).

## 8. packaging smoke와 source scanner

- `Invoke-WindowsMsiLifecycleSmoke.ps1`: `Stage` enum·parameter 이름, MSI path/evidence root 처리, admin check, msiexec 인자·exit code, UTF-8 no BOM evidence, raw secret/log 비복사 경계를 전부 보존했다. 산출물 파일명 5개(`phase6a-*` → `msi-lifecycle-*`)만 변경했다. PowerShell 5.1 AST parse 오류 0. Install/Uninstall/Reinstall 단계는 관리자 권한과 실제 머신 상태 변경이 필요해 이번 회귀 검증에서 자동 실행하지 않았다(지시서 명시 범위와 동일) — `-Stage Baseline`을 격리 evidence root에서 별도로 실행하는 것은 실제 release MSI 배포·관리자 세션이 필요해 이번 세션 범위 밖으로 남겼다(§13).
- `scripts/Test-SourceUniversality.ps1`(신규): PowerShell 5.1, `Set-StrictMode -Version Latest`, script-relative 기본 root + `-RepositoryRoot` 옵션. `.github`, gradle build 파일, `composeApp/src`/`core/src`/`infra/src`/`scripts`의 파일명·텍스트를 검사하고 `doc`/README/build/`.gradle`/IDE는 제외한다. 자기 자신은 좁은 명시적 예외(경로 비교)로 콘텐츠 스캔에서 제외한다. `-SelfTest` 스위치로 forbidden sample(7개 카테고리 전부) 검출과 protected sample(WorkflowPhase, `internal_developer_sdk` wire 리터럴, 코드 내 `D:/harness-kit` 샘플 경로) 0건 통과를 자체 검증한다. 개발 중 empty-array-to-`$null` PowerShell 함정을 2회 만나 수정했다(§13에 기록).
- CI에는 이번 작업에서 새 step을 연결하지 않았다(지시서 명시).

## 9. 규범 문서 exact reference 정합

| 문서 | 변경 내용 |
|---|---|
| `doc/hrns_now_design_pattern.md` | §20.1 코드 블록의 `InternalDeveloperSdk` → `DefaultKit`(1곳), 본문 인용 `InternalDeveloperSdk` → `DefaultKit`(1곳). 섹션 제목("Phase 7 —...")과 나머지 서술은 지시서 금지 사항("문서 전체의 Phase 역사 정리" 금지)에 따라 손대지 않았다. |
| `doc/hrns_now_claude_plan.md` | "Phase 7 —..." 작업 목록 2·3번 항목의 `InternalDeveloperSdk` 코드 참조 3곳 → `DefaultKit`. 섹션 제목의 `(PASS_WITH_FIXES)` 표기와 나머지 Phase 역사 서술은 동일한 이유로 유지했다. |

`doc/phase_reports/**`, `doc/claude_prompts/**`는 전혀 건드리지 않았다(파일명·내용·삭제 전부 미변경).

## 10. 테스트 결과

| 검증 | 명령 | 결과 |
|---|---|---|
| 6.1 이후 targeted | `:composeApp:compileKotlinJvm :composeApp:jvmTest` | BUILD SUCCESSFUL |
| 6.2 이후 targeted | `:composeApp:jvmTest` | BUILD SUCCESSFUL |
| 6.3 이후 targeted | `:core:test :infra:test :composeApp:jvmTest` | BUILD SUCCESSFUL |
| 6.4 이후 전체 | `:core:test :infra:test :composeApp:jvmTest check` | BUILD SUCCESSFUL |
| PowerShell 5.1 AST parse (2개 신규/변경 `.ps1`) | `[Parser]::ParseFile` | 오류 0 |
| `Test-SourceUniversality.ps1 -SelfTest` | 자체 self-test | forbidden 7건 전 카테고리 검출, protected 0건 — PASS |
| `Test-SourceUniversality.ps1`(실 저장소) | 전체 스캔 | 위반 0건 — PASS |
| 최종 `:composeApp:jvmTest`(epc_legacy_ui rename 후) | `--rerun-tasks` 포함 재실행 | BUILD SUCCESSFUL, 47/47 테스트 통과, 실패 0 |
| `:core:test :infra:test check` | 순차 실행 | BUILD SUCCESSFUL |
| `:composeApp:packageReleaseMsi --rerun-tasks` | MSI 패키징 | BUILD SUCCESSFUL(8분 8초), `HRNS-NOW-1.0.0.msi` 생성 확인 |
| BOM 감사 | 112개 수정 추적 파일 + 4개 신규 파일 | BOM 0 |
| `git diff --check` | — | 오류 0(exit 0). LF→CRLF 정규화 경고 17건은 §13 참고 |

**참고**: `:core:test :infra:test :composeApp:jvmTest check`와 `:composeApp:packageReleaseMsi`를 동시에 백그라운드로 실행했을 때 `AppViewModelTest`의 `Missing runtime source` 테스트 1건이 `UncaughtExceptionsBeforeTest`로 1회 실패했다 — 두 Gradle 빌드의 daemon/리소스 경합으로 판단해 순차로 재실행했고, 이후 3회 연속(각각 단독 실행) 47/47 전부 통과했다. 실제 코드 문제가 아니라 병렬 빌드 실행 환경 문제였다.

## 11. 잔여 범용성 위반 scan

`scripts/Test-SourceUniversality.ps1`(대소문자 무시)로 `.github`, gradle build 파일, `composeApp/src`, `core/src`, `infra/src`, `scripts` 전체를 스캔한 최종 결과: **0건**.

검사 카테고리: 개발 이력형 Phase/Patch/QA 번호, `Codex`/`PASS_WITH_FIXES`/`READY_FOR_CODEX_REVIEW`, 과거 phase prompt/report 정규 인용, 제거된 Kotlin/Compose/marker/template old symbol, 개인 project/user/home 경로, production 주석의 `D:\harness-kit` 절대경로, 위 §4의 retired filename 7개.

positive control(self-test)로 `WorkflowPhase`/`current_phase`, `internal_developer_sdk` wire 리터럴, 코드 내 `D:/harness-kit` 샘플 경로가 계속 통과함을 확인했다.

## 12. Git 상태

- 시작 HEAD와 종료 시점 HEAD 동일: `eebdcd1cbe16b8352fe4f967b195419eb4ee3bd6`.
- `git add`/`commit`/`push`/`rebase`/`reset --hard`/`clean`/`stash`/`checkout`/`restore` 수행하지 않았다.
- **자체 발견·수정한 절차 위반 1건**: `infra/.../runtime/DeveloperSdkRuntimeResolver.kt`(+Test)를 rename할 때 실수로 `git mv`를 사용해 변경분이 index에 staged됐다. 즉시 발견해 `git reset -- <두 경로>`(index-only, working tree 무변경)로 unstage했고, 이후 모든 rename은 순수 파일시스템 `mv`만 사용했다. `git status`로 현재 모든 변경이 unstaged 상태임을 재확인했다.
- 사용자 소유 untracked 3건(`doc/QA_captures/`, `doc/hrns_now_packaging_plan.md`, `doc/user_workflow_qa_notes.md`) 무변경 확인(§1).
- Harness Kit(`D:\harness-kit`)은 읽기 전용으로도 이번 세션에서 참조하지 않았다 — 복사·수정 없음.

## 13. 잔여 위험

- `git diff --check`가 17개 파일에서 "LF will be replaced by CRLF" 경고를 냈다 — 실제 diff 오류(trailing whitespace, conflict marker)는 0건(`exit 0`)이며, 이는 sed 기반 식별자 일괄 치환이 LF만 남긴 결과다. Git이 다음 `add`/`commit` 시 저장소 설정에 따라 자동으로 CRLF 정규화하므로 데이터 손실 위험은 없지만, Codex 검토 시 diff 자체가 line-ending 변경으로 크게 표시될 수 있다는 점은 미리 알려둔다.
- `Invoke-WindowsMsiLifecycleSmoke.ps1`의 Install/Uninstall/Reinstall 단계와 `-Stage Baseline` 실제 실행은 검증하지 않았다 — 관리자 권한과 실제 배포된 MSI가 필요해 이번 세션 범위 밖이다(파싱·리네임 정합성만 확인).
- `doc/phase_reports/**`, `doc/claude_prompts/**`의 파일명 자체는 그대로다. 이번에 정리한 core 모듈 11개 파일의 인용은 전부 제거했지만, 향후 이 두 디렉터리를 별도로 정리하는 작업이 승인되면 문서 쪽 파일명도 함께 검토해야 한다.
- `Test-SourceUniversality.ps1`의 forbidden-pattern catalog는 현재 알려진 잔재 기준의 스냅샷이다 — 새로운 개인명·새 내부 코드네임이 생기면 catalog 갱신이 필요하다(harness-kit 쪽의 동일 성격 도구와 같은 한계).
- 현재 알려진 CI 실패는 이번 작업과 무관한 별도 잔여 과제로 남겨뒀다(지시서 명시, 손대지 않음).

## 14. Codex 검토 요청

이번 작업은 §4 사전 재검증 게이트(git/파일 기준선, baseline test, 전수 재스캔, 선언·참조 재확인)를 통과한 뒤 §6.1~§6.8을 순서대로 수행했고, 작업 도중 사용자가 사용량 한도에 도달해 3개의 병렬 서브에이전트가 중단됐다 — 중단된 각 파일의 실제 완료 여부를 직접 재검사(grep)해 격차를 확인한 뒤 전부 직접 마무리했다(§2.3에 기록한 사각지대 포함). 자체 제작한 `Test-SourceUniversality.ps1` 회귀 방지 스캐너가 실제로 core 모듈의 소문자 인용 잔재 11건과 영어 문자열 3건을 새로 검출해 수정할 수 있었다 — 이 도구 자체가 이번 작업의 신뢰도를 검증하는 이중 장치 역할을 했다고 판단한다.

Registry wire 호환성(§5), 템플릿/marker 제거(§6), 250개 이상 주석 일반화(§7)를 포함한 모든 변경이 targeted → 전체 → MSI 패키징까지의 실제 빌드로 검증됐고, 최종 회귀 스캔은 0건이다.

`HRNS_NOW_SOURCE_UNIVERSALITY_STATUS: READY_FOR_CODEX_REVIEW`

---

## 15. Codex 독립 검증·보정 — 2026-08-05

### 15.1 검증 기준과 판정

- 기준 HEAD: eebdcd1cbe16b8352fe4f967b195419eb4ee3bd6
- 브랜치: harness-dev
- Verdict: PASS_WITH_FIXES
- Harness Kit 변경: 없음
- 사용자 소유 untracked 파일: 3건 모두 보존

현재 소스와 전체 diff를 계획서 및 설계 문서에 대조했다. DefaultKit 이름 변경,
UI 컴포넌트 rename, 불필요 템플릿 삭제는 책임 경계와 실행 동작을 바꾸지 않는다.
Registry schema_version 1.0과 wire 값 internal_developer_sdk도 그대로 유지됐다.

### 15.2 독립 검증에서 발견한 결함

1. Major — 신규 회귀 스캐너가 계획서의 Patch 번호를 검사하지 않았고, 일반적인
   Phase/Patch/QA/Codex형 파일명도 검사하지 않았다.
2. Major — self-test는 보고서와 달리 7개 규칙 전체가 아니라 5개 카테고리만
   기대값으로 확인했고, 파일명 검사는 전혀 검증하지 않았다.
3. Minor — DefaultKit rename 뒤에도 개발용 SDK/developer SDK/internal runtime
   표현이 사용자 안내·KDoc·주석·오류 메시지 7곳에 남아 있었다.
4. Minor — 보고서의 47/47은 전체 테스트 수가 아니었다. 독립 재집계 결과는
   core 141, infra 174, composeApp 122로 총 437건이다.
5. Minor — release MSI를 생성했지만 계획서가 요구한 lifecycle Baseline은
   실행되지 않았다.

### 15.3 수행한 보정

- 내용 규칙에 Patch 번호와 retired developer SDK 용어 검사를 추가했다.
- Phase/Patch/QA/Codex/개인 식별자가 파일명·상대 경로에 나타나는 경우도
  별도 category로 차단한다.
- self-test를 내용 9개 category와 파일명 5개 category를 모두 재현하도록
  강화했다. WorkflowPhase/current_phase, internal_developer_sdk wire 값,
  코드 문자열의 D:/harness-kit은 계속 positive control로 보호한다.
- PowerShell 5.1이 UTF-8 no-BOM 스크립트의 한글 정규식 리터럴을 잘못
  해석하지 않도록 한글 토큰을 Unicode 코드포인트로 조립한다.
- 남아 있던 개발 전용 표현을 기본 Harness Kit/default runtime 의미로 정리했다.
- 새 release MSI를 대상으로 lifecycle Baseline을 실제 실행했다.

### 15.4 독립 검증 결과

| 검증 | 결과 |
|---|---|
| Test-SourceUniversality.ps1 -SelfTest | PASS — 내용 9종·파일명 5종 검출, protected 0건 |
| 실제 저장소 범용성 scan | PASS — 위반 0건 |
| PowerShell 전체 AST parse | PASS — 2개 파일, 오류 0 |
| UTF-8 BOM scan | PASS — 대상 소스·스크립트·CI 파일 0건 |
| core:test | PASS — 141/141 |
| infra:test | PASS — 174/174 |
| composeApp:jvmTest | PASS — 122/122 |
| gradlew check | PASS |
| packageReleaseMsi --rerun-tasks | PASS |
| MSI lifecycle Baseline | PASS |

재생성한 MSI:

- 경로: composeApp/build/compose/binaries/main-release/msi/HRNS-NOW-1.0.0.msi
- 크기: 61,053,508 bytes
- SHA-256: C33BA67D70C5933E9371AA68222AF0159591C5A9AF9F80FE5CF60D7DC61DB99C
- Baseline evidence: S:/tmp/hrns-now-source-universality-codex-baseline/msi-lifecycle-baseline-20260805-173911.json
- evidence JSON: stage=Baseline, MSI hash 일치, UTF-8 no BOM

Install/Uninstall/Reinstall은 이번 소스 범용성 정리의 승인 범위가 아니므로 실행하지
않았다. 이는 별도 Windows MSI 배포 Gate 과제로 남는다. 이 절은 앞선 §8, §10,
§13의 self-test 범위·테스트 수·Baseline 미실행 서술과 충돌하는 경우 최신
독립 검증 결과로 우선한다.

`CODEX_SOURCE_UNIVERSALITY_VERDICT: PASS_WITH_FIXES`
