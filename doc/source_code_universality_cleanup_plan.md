# HRNS-NOW 소스 범용화 정리 — Claude 실행 프롬프트

## 0. 역할과 최종 목표

당신은 `S:\dev\project\hrns_now`의 소스 용어 범용화 정리 담당자다.

이번 작업은 새로운 제품 기능 개발이 아니다. 현재 동작·Harness 계약·Registry 데이터·UI 정책을 그대로 보존하면서, 제품 소스에 남은 다음 개발 과정의 잔재를 기능과 책임 중심의 이름으로 정리하는 작업이다.

- `Phase 8`, `Phase 9 QA03-B`, `Patch N`처럼 구현 당시 순서를 알아야 이해할 수 있는 주석
- `Codex 보정`처럼 내부 교차검증자를 제품 소스에 기록한 문구
- `InternalDeveloperSdk`, `DeveloperSdkRuntimeResolver`처럼 개발 checkout 구현을 제품 domain 이름으로 노출하는 심볼
- `PlaceholderActionButton`처럼 이미 운영 중인 컴포넌트를 임시 구현으로 오해하게 하는 이름
- `Greeting`, `JVMPlatform`, `InfraMarker`, `1 + 2 == 3` 테스트 같은 초기 템플릿 잔재
- 특정 host의 `D:\harness-kit`과 날짜 기반 실측을 현재 계약처럼 설명하는 주석
- `Invoke-Phase6ACleanWindowsSmoke.ps1`, `phase6a-*.json`처럼 개발 단계에 묶인 도구·산출물 이름

최종 상태에서 처음 보는 개발자는 파일명, 타입, 함수, property, 주석을 현재 기능과 불변식만으로 이해할 수 있어야 한다. 단, 외부 Harness 계약과 영속 wire 값은 범용화를 이유로 바꾸지 않는다.

작업과 검증, 보고서 작성이 끝나면 멈춘다. Git commit은 Codex만 수행한다.

---

## 1. 절대 범위와 권위

작업 저장소:

```text
S:\dev\project\hrns_now
```

기본 브랜치:

```text
harness-dev
```

조사 당시 참고 HEAD:

```text
eebdcd1cbe16b8352fe4f967b195419eb4ee3bd6
```

이 SHA로 reset하지 않는다. 시작 시 live HEAD를 다시 기록한다.

수정 대상:

```text
.github/workflows/ci.yml의 주석만
composeApp/build.gradle.kts의 주석만
composeApp/src/**
core/src/**
infra/src/**
scripts/**
변경 심볼의 exact reference를 포함한 규범 문서의 최소 부분
```

문서 정리 범위:

- `doc/**`와 `README.md`의 일반적인 Phase 이력은 이번 작업 대상이 아니다.
- 다만 rename된 Kotlin 심볼·스크립트 이름을 현재 규범으로 직접 제시하는 `doc/hrns_now_design_pattern.md`, `doc/hrns_now_claude_plan.md`의 exact reference는 최소한으로 갱신한다.
- 과거 `doc/phase_reports/**`, `doc/claude_prompts/**`는 역사 기록이므로 파일명 변경·전면 rewrite·삭제하지 않는다.
- 살아 있는 소스 주석이 과거 phase prompt/report를 규범 근거로 직접 인용한다면, 그 주석은 현재 code type·Harness artifact·안정된 설계 원칙을 가리키도록 재작성한다. 과거 문서 경로를 소스 주석에 그대로 남기는 것을 참조 무결성이라고 오해하지 않는다.

외부 Harness Kit:

```text
D:\harness-kit
```

Harness Kit은 공개 entrypoint, State field, bridge artifact를 확인하기 위한 읽기 전용 자료다. 수정·복사·백업·commit하지 않는다.

사용자 소유 보존 대상:

```text
doc/QA_captures/
doc/hrns_now_packaging_plan.md
doc/user_workflow_qa_notes.md
```

이 파일과 디렉터리는 수정·삭제·stage하지 않는다. 이 실행 프롬프트 자체인 `doc/source_code_universality_cleanup_plan.md`도 수정하지 않는다.

---

## 2. 반드시 먼저 읽을 자료

다음 파일을 전체 읽은 뒤 작업한다.

```text
doc/source_code_universality_cleanup_plan.md
doc/hrns_now_design_pattern.md
doc/hrns_now_claude_plan.md
core/src/main/kotlin/io/hrns_now/core/domain/model/RuntimeSource.kt
core/src/main/kotlin/io/hrns_now/core/usecase/RegisterProjectUseCase.kt
infra/src/main/kotlin/io/hrns_now/infra/registry/ProjectRegistryDto.kt
infra/src/main/kotlin/io/hrns_now/infra/registry/JsonProjectRegistryAdapter.kt
infra/src/main/kotlin/io/hrns_now/infra/runtime/DeveloperSdkRuntimeResolver.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/App.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/ui/Components.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/ui/Screens.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/ui/Shell.kt
scripts/Invoke-Phase6ACleanWindowsSmoke.ps1
```

필요한 Harness 계약은 live `D:\harness-kit`의 `docs/PROJECT_ONBOARDING.md`, `docs/STATE_MODEL.md`, 공개 script parameter를 직접 확인한다. HRNS-NOW 문서의 과거 설명보다 현재 양쪽 live 소스를 우선한다.

설계 우선순위:

```text
현재 HRNS-NOW 소스와 테스트
→ Harness Kit 공개 계약과 WORKFLOW_STATE.json
→ hrns_now_design_pattern.md
→ hrns_now_claude_plan.md
→ 과거 phase report/prompt
```

---

## 3. Git·안전 규칙

- Claude는 `git commit`, `add`, `amend`, `rebase`, `reset`, `clean`, `stash`, `checkout`, `restore`, `push`를 수행하지 않는다.
- Git은 `status`, `diff`, `grep`, `show`, `log`, `ls-files` 같은 읽기 작업에만 사용한다.
- 기존 사용자 변경을 삭제하거나 덮어쓰지 않는다.
- 이번 작업과 무관한 UI 기능, policy, packaging 기능, CI 실패 수정은 섞지 않는다.
- 현재 CI 실패는 별도 잔여 과제다. `.github/workflows/ci.yml`은 Phase 주석만 현재 책임으로 바꾸고 action version, runner, step, permission, Gradle command는 수정하지 않는다.
- build output, MSI, logs, IDE cache, temp fixture를 commit 대상으로 만들지 않는다.
- 테스트 삭제·skip·약화, 예외 삼키기, mock fallback, contract 완화를 금지한다.
- source rename은 파일과 선언, import, call site, test를 같은 변경 단위에서 처리한다.
- 삭제 전 `git grep`로 참조를 확인하고 삭제 후 compiler와 재검색으로 0건을 입증한다.
- 모든 수정 문서·소스·스크립트·보고서는 UTF-8 without BOM을 유지한다.

HRNS-NOW는 Git 저장소이므로 별도 zip 백업을 만들지 않는다. 대신 시작 HEAD, 작업 전 status, 사용자 untracked 목록을 보고서에 기록한다.

---

## 4. 수정 전 재검증 — 완료 전 편집 금지

### 4.1 Git과 파일 기준선

```powershell
Set-Location -LiteralPath 'S:\dev\project\hrns_now'

git status --short
git branch --show-current
git rev-parse HEAD
git log -10 --oneline --decorate
git diff --stat
git ls-files --others --exclude-standard
```

다음을 확인한다.

- 현재 branch가 `harness-dev`인지
- 조사 당시 이후 소스가 바뀌었는지
- 사용자 소유 변경과 이번 작업이 겹치는지
- 두 중간 계획서 중 이 파일 하나만 남아 있는지
- `D:\harness-kit`이 HRNS-NOW 작업 트리에 복사되지 않았는지

사용자 변경과 안전하게 분리할 수 없으면 편집하지 말고 충돌 파일과 해소 조건을 보고한 뒤 멈춘다.

### 4.2 기준 테스트

수정 전에 다음을 실행한다.

```powershell
.\gradlew.bat :core:test
.\gradlew.bat :infra:test
.\gradlew.bat :composeApp:jvmTest
.\gradlew.bat check
```

기준선 실패 시 이번 정리와 무관한 기존 실패인지 분석한다. 코드 결함인지 확인하지 않은 채 수정에 착수하지 않는다. 필수 baseline이 환경 문제로 실행 불가능하면 정확한 명령·오류·해소 조건만 보고하고 멈춘다.

### 4.3 전수 재스캔

조사 당시 다음 결과가 관찰됐지만 그대로 신뢰하지 말고 live HEAD에서 다시 산출한다.

- 추적 감사 범위: 207개 파일
- 개발 이력형 Phase/Patch 및 과거 phase 문서 참조: 249줄, 102개 파일
- QA/Codex 등 추가 개발 라벨: 31줄, 15개 파일
- `InternalDeveloperSdk` 계열: 18개 파일, 99개 일치 항목
- `PlaceholderRow`/`PlaceholderActionButton`: 선언과 호출 약 35곳
- 개발 단계가 파일명에 포함된 파일: `scripts/Invoke-Phase6ACleanWindowsSmoke.ps1`

파일명과 내용 모두에서 다음을 검색한다.

```text
Phase <number> / phase<number>
Patch<number>
QA01, QA02, QA03-A 같은 QA 번호
Codex
PASS_WITH_FIXES / READY_FOR_CODEX_REVIEW
InternalDeveloperSdk
DeveloperSdkRuntimeResolver
useInternalDeveloperSdk
internal_developer_sdk
PlaceholderRow / PlaceholderActionButton
InfraMarker / Greeting / JVMPlatform / getPlatform
auziraum / test-hantu / hos0917 / C:\Users\...
S:\dev\project
D:\harness-kit
doc/claude_prompts/phase*
doc/phase_reports/phase*
날짜가 붙은 현행 계약 설명
```

`.kt`, `.ps1`, `.gradle.kts`, `.yml`, tracked resource와 test resource까지 확인한다. 검색 결과를 파일·라인·분류별로 보고서에 기록한다.

### 4.4 선언·참조 재확인

다음은 수정 전 반드시 재증명한다.

1. `Greeting`과 `JVMPlatform/getPlatform()`이 제품에서 사용되지 않는다.
2. `InfraMarker`는 `Shell.kt`의 `infra / hrns_now-infra` 표시 외 소비자가 없다.
3. `Placeholder*` call site가 `Components.kt`, `Screens.kt`에 한정되는지 확인한다.
4. `internal_developer_sdk`가 `%APPDATA%\hrns-now\projects.json` wire 값이며 기존 테스트가 이를 고정하는지 확인한다.
5. packaging smoke script의 저장소 내 실제 consumer를 확인한다.
6. `epc_legacy_ui`가 `RibbonProjectNameTest.kt`의 fixture/assertion 두 곳 외 참조되지 않는지 확인한다.

하나라도 조사 결과와 다르면 live 사실을 우선하고 보고서에 차이를 명시한다.

---

## 5. 절대 변경하지 않는 정상 용어와 계약

아래는 범용성 위반이 아니다.

- `WorkflowPhase`, `state.phase`, `phaseRaw`, `current_phase`, Harness phase raw value
- `ClosurePhaseMismatch`, `resume_from_step_id`, `ResumeStepRemaining`
- UI의 실제 진행 단계를 뜻하는 `StageRow`, `showStagesButton`, `hideStagesButton`
- `StopReason.ClaudeContextLimit`, `ClaudeCallTimeout`, `.claude/CLAUDE.md`, `claudeCommand`
- `WORKFLOW_STATE.json` 단일 진실과 unknown raw 보존/fail-closed 처리
- required daily 4-file, optional `REQUEST_STRUCTURED.md`
- legacy fallback `WORKDAY_STATE.json`, `WORK_QUEUE.json`, `ArtifactRequirement.Legacy`
- `doctor.ps1`, `validate-ops.ps1`, `enter-project.ps1`, `run-cycle.ps1` 공개 이름과 인자
- wrapper 실제 값 `none|code|doc|auto`; 존재하지 않는 validation wrapper 금지
- `MockProjectionProvider`, `MockWorkspaceConfigProvider`가 `demo` 패키지와 명시적 demo mode에만 존재하는 구조
- 테스트의 `fixture`, `sample`, 고정 날짜, 공백·한글·서로 다른 drive를 검증하기 위한 가상 path
- 제품 기능명인 `DevelopmentStrategyCard`
- schema/contract/app version

주석에서 개발 Phase를 없앤다는 이유로 runtime `phase`나 공개 schema를 rename하면 실패다.

---

## 6. 확정 작업 — 순서대로 수행

### 6.1 템플릿과 의미 없는 marker 제거

대상:

```text
composeApp/src/jvmMain/kotlin/io/hrns_now/app/Greeting.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/Platform.kt
composeApp/src/commonTest/kotlin/io/hrns_now/app/ComposeAppCommonTest.kt
infra/src/main/kotlin/io/hrns_now/infra/InfraMarker.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/ui/Shell.kt
```

작업:

1. `Greeting.kt`, `Platform.kt`를 참조 0건 확인 후 삭제한다.
2. `1 + 2 == 3`만 검증하는 `ComposeAppCommonTest.kt`를 삭제한다. 대체용 무의미 테스트를 만들지 않는다.
3. `Shell.kt`에서 `InfraMarker` import와 `ENVIRONMENT` 카드의 `infra / hrns_now-infra` 행만 제거한다.
4. `InfraMarker.kt`를 삭제한다.
5. 환경 카드의 다른 의미 있는 상태·artifact·안내는 제거하지 않는다.

`InfraMarker`를 `RuntimeInfo`로 이름만 바꾸는 것은 금지한다. 실제 typed runtime metadata 요구가 생기기 전에는 추상화를 만들지 않는다.

검증:

```powershell
.\gradlew.bat :composeApp:compileKotlinJvm :composeApp:jvmTest
```

### 6.2 운영 UI 컴포넌트의 placeholder 명칭 제거

대상:

```text
composeApp/src/jvmMain/kotlin/io/hrns_now/app/ui/Components.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/ui/Screens.kt
```

확정 rename:

```text
PlaceholderRow
→ LabelValueRow

PlaceholderActionButton(text, ...)
PlaceholderActionButton(action, ...)
→ HrnsActionButton(text, ...)
→ HrnsActionButton(action, ...)
```

`ActionButton`처럼 Material/다른 UI 타입과 충돌하기 쉬운 지나치게 일반적인 이름은 사용하지 않는다. 두 overload와 모든 call site를 같은 변경에서 바꾼다.

동작 보존:

- `enabled = false` 기본값
- primary/secondary style
- hover interaction과 pointer
- 색상·padding·font
- loading/disabled/click semantics
- modifier와 named argument

검증:

```powershell
.\gradlew.bat :composeApp:jvmTest
```

### 6.3 기본 Harness Kit 명칭으로 domain·adapter 정렬

현재 `InternalDeveloperSdk`는 제품 domain 선택, checkout-relative resolver, 사용자 문구, Registry wire 값을 한 이름에 섞고 있다. 내부 심볼과 사용자 문구는 일반화하되 외부 wire는 보존한다.

확정 rename:

| 위치 | 현재 | 변경 |
|---|---|---|
| `RuntimeSource.kt` | `RuntimeSource.InternalDeveloperSdk` | `RuntimeSource.DefaultKit` |
| `RegisterProjectUseCase.kt` | `useInternalDeveloperSdk` | `useDefaultKit` |
| infra runtime 파일·클래스 | `DeveloperSdkRuntimeResolver` | `DefaultKitRuntimeResolver` |
| resolver constructor | `internalSdkRootProvider` | `defaultKitRootProvider` |
| resolver companion | `defaultInternalSdkRoot()` | `defaultKitRoot()` |
| 한국어 UI/진단 | `개발용 내장 SDK` | `기본 Harness Kit` |
| 영어 UI/진단 | `Internal developer SDK` | `Default Harness Kit` |

영향 파일을 다시 검색하되 최소 다음을 포함한다.

```text
composeApp/src/jvmMain/kotlin/io/hrns_now/app/App.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/mapper/CockpitProjectionAssembler.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/mapper/CockpitUiStateAssembler.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/model/CockpitProjection.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/model/HrnsUiState.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/viewmodel/AppViewModel.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/viewmodel/ViewModelStrings.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/ui/Screens.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/ui/Strings.kt
core/src/main/kotlin/io/hrns_now/core/domain/model/RuntimeSource.kt
core/src/main/kotlin/io/hrns_now/core/usecase/RegisterProjectUseCase.kt
infra/src/main/kotlin/io/hrns_now/infra/registry/JsonProjectRegistryAdapter.kt
infra/src/main/kotlin/io/hrns_now/infra/registry/ProjectRegistryDto.kt
infra/src/main/kotlin/io/hrns_now/infra/runtime/DeveloperSdkRuntimeResolver.kt
관련 core/infra/composeApp 테스트
```

파일 rename은 다음과 같이 한다.

```text
infra/.../runtime/DeveloperSdkRuntimeResolver.kt
→ infra/.../runtime/DefaultKitRuntimeResolver.kt

infra/.../runtime/DeveloperSdkRuntimeResolverTest.kt
→ infra/.../runtime/DefaultKitRuntimeResolverTest.kt
```

#### Registry wire 호환성 — 가장 중요한 Gate

다음 값은 기존 사용자 Registry의 영속 계약이므로 바꾸지 않는다.

```json
runtime_source_type: internal_developer_sdk
```

상수 이름만 의미를 명확히 한다.

```kotlin
private const val RUNTIME_SOURCE_DEFAULT_KIT_WIRE_VALUE = internal_developer_sdk
```

규칙:

- Registry schema version을 올리지 않는다.
- 새 writer도 이번 작업에서는 기존 wire 값을 쓴다.
- `default_kit`을 새로 쓰지 않는다.
- 구 값 read, load→save round-trip, `kit_root == null`, unknown discriminator 격리를 테스트한다.
- `ExternalKit`의 절대 경로 저장과 boundary validation을 변경하지 않는다.
- 기본 Kit이 없다고 외부 Kit으로 자동 fallback하지 않는다.
- `.local/harness-kit`을 생성·복사·수정하거나 MSI에 포함하지 않는다.
- `DefaultKit`이라는 이름은 선택 우선순위만 의미한다. bundled/embedded/managed runtime이라고 과장하지 않는다.

resolver의 모순된 KDoc도 현재 코드에 맞게 고친다. `root 직하 파일만 검사한다`고 쓰지 말고 Kit root 아래 `scripts/doctor.ps1`, `scripts/validate-ops.ps1`, `scripts/run-cycle.ps1`, `kit-version.json` 존재를 검사한다고 설명한다.

검증:

```powershell
.\gradlew.bat :core:test :infra:test :composeApp:jvmTest
```

### 6.4 개발 이력형 주석과 KDoc을 현재 책임으로 재작성

대소문자를 무시한 조사에서 249개의 Phase/Patch/과거 phase 문서 참조가 확인됐다. 단순 regex 삭제를 금지하며 각 문장을 다음 중 하나로 처리한다.

| 현재 형태 | 처리 |
|---|---|
| `Phase N에서 추가`, `새 Phase N 보완` | 현재 책임·보장하는 불변식으로 재작성 |
| `Phase N 범위` | 실제 기능 경계로 재작성 |
| `Phase 9 QA03-A/B`, `QA01`, `QA02` | 관찰 번호 대신 검증하는 UX·policy를 설명 |
| `Codex 보정` | 검증자 이름을 삭제하고 회귀 조건만 보존 |
| `doc/claude_prompts/phase*`, `doc/phase_reports/phase*` | 현재 code type, stable design 원칙, Harness artifact/entrypoint 근거로 교체 |
| 날짜 기반 `현행 계약(YYYY-MM-DD 실측)` | 날짜를 제거하고 현재 required/optional/legacy 계약을 직접 기술 |
| 중복되거나 코드와 동일한 이력 주석 | 의미 손실이 없으면 삭제 |

자연스러운 변환 예:

```text
전: Registry의 활성 선택만 지운다(Phase 9 QA03-A).
후: Registry의 활성 선택만 지우며 등록된 project entry는 보존한다.

전: 새 Phase 8 보완: disabled action도 사유를 표시한다.
후: disabled action도 사용자가 다음 행동을 알 수 있도록 사유를 표시한다.

전: Phase 10: bridge 또는 오늘 workspace 준비가 누락되면...
후: bridge 또는 오늘 workspace 준비가 누락되면 프로젝트 준비 CTA를 노출한다.
```

반드시 확인할 고밀도 파일:

```text
composeApp/src/jvmMain/kotlin/io/hrns_now/app/App.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/DefaultProjections.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/RecoveryProjections.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/mapper/*.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/model/*.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/viewmodel/AppViewModel.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/viewmodel/HarnessRunViewState.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/viewmodel/ViewModelStrings.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/ui/Components.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/ui/Markdown.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/ui/Screens.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/ui/Shell.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/ui/Strings.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/ui/Theme.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/ui/Typography.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/ui/WindowConstraints.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/main.kt
core/src/main/kotlin/io/hrns_now/core/domain/**/*.kt
core/src/main/kotlin/io/hrns_now/core/port/*.kt
core/src/main/kotlin/io/hrns_now/core/result/*.kt
core/src/main/kotlin/io/hrns_now/core/usecase/*.kt
infra/src/main/kotlin/io/hrns_now/infra/**/*.kt
모든 대응 test source
```

특정 수정 anchor:

1. `AppViewModelTest.kt`의 `Codex 보정:` 접두사를 삭제하고 required 4-file 재검증 조건은 보존한다.
2. `RepositoryBridgeSummary.kt`의 `D:\harness-kit\docs\PROJECT_ONBOARDING.md`는 `Harness Kit docs/PROJECT_ONBOARDING.md`와 bridge 3-file 계약으로 일반화한다.
3. `WorkspaceArtifactProbe.kt`의 `현행 계약(2026-07-23 실측)`은 dayRoot required 4-file, optional logs, legacy fallback의 현재 계약으로 바꾼다.
4. `WorkspacePathProbe.kt`의 `placeholder를 제거했다`는 이력을 실제 config 값을 probe한다는 책임으로 바꾼다.
5. `DeveloperSdkRuntimeResolverTest.kt` rename 결과의 KDoc에서 실제 `D:\harness-kit`을 언급하지 않고 격리 temp fixture만 사용한다는 규칙으로 바꾼다.
6. `RibbonProjectNameTest.kt`의 `epc_legacy_ui` 두 곳은 외부 contract가 아닌 test fixture임을 재확인한 뒤 `sample-legacy-ui`로 함께 바꾼다.

주석에서 실질적 안전 이유를 제거하지 않는다. 예를 들어 “State 재조회로 성공 판정”, “Registry entry 보존”, “raw stdout 비권위”, “UI thread I/O 금지”는 현재 불변식이므로 남긴다.

### 6.5 build와 CI 주석 일반화

#### `composeApp/build.gradle.kts`

다음 구현 이력 표현을 packaging rationale로 바꾼다.

- `Phase 6A는 외부 Kit MSI MVP만...` → Windows MSI만 지원·검증하는 현재 target 설명
- phase prompt/report 경로 → `jdk.charsets`, ASCII installer metadata, WiX host encoding 제약의 직접 설명
- `새 Phase 6 UI/UX 설치 품질 요구` → 설치 폴더 선택을 유지하는 현재 UX 이유

실제 `targetFormats`, modules, version, vendor, upgrade UUID, icon, menu, console, `dirChooser` 값은 변경하지 않는다.

#### `.github/workflows/ci.yml`

`Phase 0B`, `Phase 3+`를 제거하고 다음 현재 사실만 설명한다.

- JVM unit/check는 Ubuntu runner에서 실행한다.
- Windows PowerShell/native process/MSI 검증은 별도 Windows 검증이 필요하다.

workflow action version, branches, runner, commands를 수정하지 않는다. 알려진 CI 실패 수정은 별도 과제로 남긴다.

### 6.6 Windows MSI lifecycle smoke의 기능 중심 rename

확정 rename:

```text
scripts/Invoke-Phase6ACleanWindowsSmoke.ps1
→ scripts/Invoke-WindowsMsiLifecycleSmoke.ps1
```

신규 실행 증빙 prefix:

```text
phase6a-baseline-*.json   → msi-lifecycle-baseline-*.json
phase6a-install-*.json    → msi-lifecycle-install-*.json
phase6a-snapshot-*.json   → msi-lifecycle-snapshot-*.json
phase6a-uninstall-*.json  → msi-lifecycle-uninstall-*.json
phase6a-reinstall-*.json  → msi-lifecycle-reinstall-*.json
```

보존:

- `Stage` enum과 parameter 이름
- MSI path/evidence root 처리
- admin check
- msiexec argument와 exit code
- UTF-8 no BOM evidence
- raw secret/session/log를 복사하지 않는 경계
- 기존에 생성된 evidence 파일

과거 `phase6-report.md`는 당시 파일명의 역사 기록이므로 rewrite하지 않는다. 현재 실행법을 가리키는 live reference가 발견되면 새 이름으로 갱신한다.

검증:

1. PowerShell 5.1 AST parse 오류 0
2. release MSI가 이미 있거나 새 packaging 뒤 생성된 경우 `-Stage Baseline`만 격리 evidence root에서 실행
3. Install/Uninstall/Reinstall은 이 rename 검증에서 자동 실행하지 않음
4. 생성 JSON UTF-8 no BOM과 five-stage filename mapping 확인

### 6.7 변경 심볼을 직접 인용하는 규범 문서만 최소 정합화

대상:

```text
doc/hrns_now_design_pattern.md
doc/hrns_now_claude_plan.md
```

허용되는 수정:

- `InternalDeveloperSdk` → `DefaultKit`
- `DeveloperSdkRuntimeResolver` → `DefaultKitRuntimeResolver`
- `useInternalDeveloperSdk` → `useDefaultKit`
- 현재 기본 Kit의 source-checkout 상대 해석과 Registry wire 호환성을 정확히 설명
- 현재 실행 스크립트를 직접 지칭한다면 새 MSI smoke 이름 반영

금지:

- 문서 전체의 Phase 역사 정리
- 과거 phase report/prompt rename 또는 삭제
- 완료 상태나 roadmap 재기획
- packaging/CI 잔여 과제를 완료로 변경

### 6.8 재발 방지 source scanner 추가

다음 repeatable lint script를 추가한다.

```text
scripts/Test-SourceUniversality.ps1
```

요구사항:

- PowerShell 5.1, StrictMode, UTF-8 without BOM
- 기본 repository root는 script-relative로 해석하고 optional explicit root를 지원
- `.github`, Gradle build files, `composeApp/src`, `core/src`, `infra/src`, `scripts`의 파일명과 텍스트를 검사
- `doc`, README, build, `.gradle`, IDE, generated output는 제외
- 자기 자신의 forbidden-pattern catalog는 좁은 명시적 예외로 처리
- finding은 path, line, category만 출력하고 secret/raw file content 전체를 출력하지 않음
- finding이 있으면 non-zero exit

최소 금지 범주:

```text
개발 이력형 Phase/Patch/QA 번호
Codex/PASS_WITH_FIXES/READY_FOR_CODEX_REVIEW
과거 phase prompt/report를 현재 소스가 규범으로 인용
제거된 Kotlin/Compose/marker/template old symbol
개인 project/user/home path
production comment의 D:\harness-kit 절대 경로
```

허용 목록은 path와 이유를 함께 선언한다.

- `WorkflowPhase`, `current_phase`, `state.phase`
- Claude stop reason/command/bridge artifact
- Registry ACL과 tests 안의 wire literal `internal_developer_sdk`
- legacy fallback artifact
- demo package의 Mock provider
- Windows multi-drive/공백/한글을 검증하는 test fixture

임시 fixture로 scanner가 forbidden sample을 실제로 검출하고 protected sample을 통과시키는 self-test 또는 동등한 targeted 검증을 추가한다. 현재 실패 중인 GitHub CI에는 이번 작업에서 새 step을 연결하지 않는다.

---

## 7. 개발 이력 주석 영향 manifest

아래 목록은 조사 시점의 anchor다. line number가 이동할 수 있으므로 Claude는 이 목록만 믿지 말고 다시 스캔한다. 괄호는 관찰된 일치 줄 수다.

### 7.1 composeApp 제품 소스

```text
App.kt(4)
DefaultProjections.kt(5)
NotificationCenter.kt(1)
RecoveryProjections.kt(3)
CockpitProjectionAssembler.kt(2)
CockpitUiStateAssembler.kt(2)
DomainLabels.kt(1)
ReasonKeyStrings.kt(2)
RunStatusProjectionAssembler.kt(4)
UiActionLabels.kt(1)
CockpitProjection.kt(4)
HrnsUiEvent.kt(7)
HrnsUiState.kt(4)
NotificationItem.kt(1)
ProjectionModels.kt(9)
RegistrationFeedback.kt(3)
AppViewModel.kt(19)
HarnessRunViewState.kt(2)
ViewModelStrings.kt(5)
Components.kt(3)
Markdown.kt(2)
Screens.kt(25)
Shell.kt(9)
Strings.kt(13)
Theme.kt(3)
Typography.kt(1)
WindowConstraints.kt(2)
main.kt의 QA01 주석
```

### 7.2 core 제품 소스

```text
domain/model: ActionContext, AppLocale, BlockedReasonKey, HarnessCommand,
HarnessProject, ProcessLock, RecommendedActions, RepositoryBridgeSummary,
RepositoryStatus, RuntimeSource, UiAction, WorkflowPhase, WorkflowQueue, WorkflowState

domain/policy: ActionPolicy, ClosureBlockReasonKey, ClosurePolicy,
CompatibilityPolicy, LockStalePolicy, WorkspaceDaySelectionPolicy

port: GitStatusPort, HarnessRunnerPort, KitVersionManifestPort, ProcessLockPort,
ProjectRegistryPort, RepositoryBridgeProbePort, TodayStrategyReaderPort,
UiPreferencesPort, WorkflowStatePort

result: ProcessRunResult, RegistryResult, StateReadProjectionMapper

usecase: ClearActiveProjectUseCase, ExecuteHarnessActionUseCase, LoadCockpitUseCase,
OnboardProjectUseCase, RegisterProjectUseCase, ResolveActiveProjectUseCase,
SaveRequestUseCase
```

### 7.3 infra 제품 소스

```text
bridge/RepositoryBridgeProbe.kt
kitversion/JsonKitVersionManifestAdapter.kt
lock/LocalProcessLockAdapter.kt
preferences/UiPreferencesFileAdapter.kt
process/PowerShellHarnessAdapter.kt
process/WindowsProcessTreeTerminator.kt
registry/JsonProjectRegistryAdapter.kt
registry/ProjectRegistryDto.kt
registry/RealPathGateway.kt
runtime/DeveloperSdkRuntimeResolver.kt
security/SecretMasker.kt
serialization/JsonWorkflowStateAdapter.kt
WorkspaceArtifactProbe.kt
WorkspaceDayDiscovery.kt
WorkspacePathProbe.kt
```

### 7.4 테스트

```text
composeApp: DefaultProjectionsTest, CockpitProjectionAssemblerTest,
ReasonKeyStringsTest, RunStatusProjectionAssemblerTest, NotificationCenterTest,
RecoveryProjectionsTest, AppViewModelTest, ThemeTest, WindowConstraintsTest,
RibbonProjectNameTest

core: ActionPolicyTest, ClosurePolicyTest, CompatibilityPolicyTest,
LockStalePolicyTest, LoadCockpitUseCaseTest, OnboardProjectUseCaseTest,
RegisterProjectUseCaseTest

infra: RepositoryBridgeProbeTest, UiPreferencesFileAdapterTest,
JvmProcessExecutorTest, PowerShellHarnessAdapterTest,
JsonProjectRegistryAdapterTest, DeveloperSdkRuntimeResolverTest
```

특히 `Shell.kt`, `Theme.kt`, `WindowConstraints.kt`, `main.kt`, `AppViewModelTest.kt`에는 Phase 번호 없이 `QA01/QA02`만 적힌 줄도 있으므로 별도 QA pattern 검색을 수행한다.

---

## 8. 변경 금지 동작

명칭과 주석을 정리하면서 다음 동작을 바꾸면 실패다.

- 프로젝트 등록·활성 선택·해제·삭제의 의미
- 기본 Kit와 외부 Kit 선택 우선순위
- Registry atomic write, 손상 복구, last active project 처리
- runtime source missing/invalid의 fail-closed 판정
- boundary validation과 canonical path 비교
- CompatibilityPolicy, ActionPolicy, ClosurePolicy 결정표
- `WORKFLOW_STATE.json` 읽기·재시도·stale projection
- Harness command 종류·인자·실행 후 State 재조회
- lock 획득·heartbeat·해제·cancel·timeout·secret masking
- UI의 single state flow, polling, dispatcher 경계
- 사용자 언어·theme·notification 동작
- demo mode와 production mode 경계
- MSI package metadata, UpgradeCode, app version, JRE module

컴파일이 통과한다는 이유만으로 의미가 같은 것으로 판정하지 않는다. 기존 테스트 assertion과 정책 결정표를 보존한다.

---

## 9. 테스트와 검증 순서

### 9.1 단계별 targeted

1. template/marker 제거 후:

```powershell
.\gradlew.bat :composeApp:compileKotlinJvm :composeApp:jvmTest
```

2. UI component rename 후:

```powershell
.\gradlew.bat :composeApp:jvmTest
```

3. Default Kit rename과 Registry compatibility 후:

```powershell
.\gradlew.bat :core:test :infra:test :composeApp:jvmTest
```

최소 회귀 항목:

- wire `internal_developer_sdk` read/write round-trip
- legacy entry의 ExternalKit 해석
- `kit_root` 혼합 source 격리
- unknown source 격리
- DefaultKit missing/invalid/resolved
- source checkout 이동·working directory 탐색
- 등록 후보와 UI ko/en label

4. source scanner 추가 후:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Test-SourceUniversality.ps1
```

5. MSI lifecycle script rename 후:

- PowerShell 5.1 AST parse
- freshly packaged release MSI가 준비된 뒤 Baseline stage만 실행

### 9.2 전체 Gradle 회귀

```powershell
.\gradlew.bat :core:test
.\gradlew.bat :infra:test
.\gradlew.bat :composeApp:jvmTest
.\gradlew.bat check
.\gradlew.bat :composeApp:packageReleaseMsi --rerun-tasks
```

실제 task가 다르면 `gradlew tasks`로 확인하고 보고서에 사용한 정확한 task를 적는다. 실행하지 않은 검증을 PASS라고 쓰지 않는다.

### 9.3 정적 검증

1. 모든 수정 `.ps1` PowerShell 5.1 parse 오류 0
2. 수정 text 파일 UTF-8 strict decode PASS, BOM 0
3. `git diff --check` PASS
4. old Kotlin/UI/template symbol 검색 0
5. 개발 이력형 Phase/Patch/QA/Codex 검색 0 — protected runtime 계약 제외
6. 살아 있는 소스의 phase prompt/report 규범 인용 0
7. 개인 project/user/home production 하드코딩 0
8. missing import/reference 0
9. generated file, MSI, logs, secret, raw session ID가 Git 변경에 없음
10. 사용자 소유 untracked 보존

### 9.4 의미 보존 diff review

diff를 처음부터 끝까지 다시 읽고 다음을 파일별로 확인한다.

- comment 문장이 조사·괄호·콜론 삭제 후 자연스러운가
- 이름만 바꿔야 하는 변경에 로직 diff가 섞이지 않았는가
- `DefaultKit` rename이 registry wire/schema를 바꾸지 않았는가
- `HrnsActionButton` rename이 enabled/click/loading behavior를 바꾸지 않았는가
- 삭제 파일의 책임이 다른 곳에 잘못 복제되지 않았는가
- source scanner allowlist가 너무 넓어 실제 위반을 숨기지 않는가
- packaging smoke parameter/evidence payload가 동일한가

---

## 10. 완료 조건

다음이 모두 충족돼야 완료다.

- `Greeting`, `JVMPlatform`, `getPlatform`, `InfraMarker`, 무의미한 common example test 제거
- `PlaceholderRow`, `PlaceholderActionButton` old symbol 0
- 제품 심볼과 UI 문구의 `InternalDeveloperSdk`, `DeveloperSdkRuntimeResolver`, `useInternalDeveloperSdk`, 개발용 내장 SDK 표현 0
- Registry ACL과 tests에만 호환 wire `internal_developer_sdk`가 근거와 함께 남음
- 기존 Registry load/save와 fail-closed source 처리가 통과
- source/build/CI/script/test 주석에서 개발 이력형 Phase/Patch/QA/Codex 라벨 0
- runtime `WorkflowPhase`, `current_phase`, Claude stop reason, legacy fallback, demo/mock 경계 보존
- production source의 개인 host/project path 0
- `Invoke-WindowsMsiLifecycleSmoke.ps1` rename과 모든 live reference 정합
- `Test-SourceUniversality.ps1`이 실제 위반을 검출하고 보호 계약은 허용
- targeted test, 모든 module test, `check`, release MSI packaging PASS
- PowerShell parse, BOM, diff check PASS
- Harness Kit 무변경
- 사용자 소유 untracked 무변경
- Git 작업 없음

하나라도 남으면 완료라고 선언하지 않는다. 환경 문제로 필수 검증이 불가능하면 그 항목을 PASS라고 쓰지 말고 `BLOCKED`로 보고한다.

---

## 11. 보고서

다음 경로에 UTF-8 without BOM으로 작성한다.

```text
S:\dev\project\hrns_now\doc\source_code_universality_cleanup_report.md
```

필수 형식:

```md
# HRNS-NOW 소스 범용화 정리 보고서

## 1. 기준 상태
- 저장소/브랜치/시작 HEAD
- 작업 전 Git 상태
- 사용자 소유 변경

## 2. 수정 전 재검증
- baseline test
- 전수 재스캔 수치
- 최초 계획과 live 차이

## 3. 제거 대상과 보존 대상
- 분류 근거
- Harness/runtime 계약 보호 목록

## 4. 파일·심볼 rename
- 경로
- 이전 이름
- 새 이름
- 전체 참조 이동 근거

## 5. Default Kit와 Registry wire 호환성
- domain/adapter/UI 변경
- wire 값 보존
- 기존 Registry 회귀 결과

## 6. 템플릿·placeholder·marker 제거
- 삭제 근거
- 참조 0 증거

## 7. 주석·KDoc·경로 일반화
- 파일별 기존 anchor
- 새 의미
- protected runtime phase 구분

## 8. packaging smoke와 source scanner
- rename
- parameter/payload 보존
- scanner negative/positive control

## 9. 규범 문서 exact reference 정합

## 10. 테스트 결과
| 검증 | 명령 | 결과 |
|---|---|---|

## 11. 잔여 위반 scan

## 12. Git 상태
- 변경 파일
- 사용자 파일 보존
- Git commit/push 미수행

## 13. 잔여 위험

## 14. Codex 검토 요청
```

각 수정 파일에 대해 최소 `경로`, `기존 anchor`, `새 의미`, `참조 이동`, `회귀 검증`을 적는다. “일괄 정리 완료”라고만 쓰지 않는다.

보고서 마지막에 다음 marker를 쓴다.

```text
HRNS_NOW_SOURCE_UNIVERSALITY_STATUS: READY_FOR_CODEX_REVIEW
```

필수 검증이 실패했거나 실행 불가능하면 marker 대신 다음을 쓴다.

```text
HRNS_NOW_SOURCE_UNIVERSALITY_STATUS: BLOCKED
```

---

## 12. 종료 규칙

작업, 검증, 보고서 작성 후 즉시 멈춘다.

- Git commit/add/push 금지
- 기존 커밋 변경 금지
- Harness Kit 수정 금지
- QA 기능 개선 금지
- CI 실패 수정 금지
- packaging 잔여 과제 구현 금지
- 다음 Phase 또는 새 기능 착수 금지
- Codex 확인 전 완료·배포 가능 선언 금지

최종 응답에는 다음만 간결히 요약한다.

1. 변경한 파일과 핵심 rename
2. Registry/Harness 계약 보존 근거
3. 실행한 테스트와 결과
4. 보고서 경로
5. `READY_FOR_CODEX_REVIEW` 또는 `BLOCKED`
