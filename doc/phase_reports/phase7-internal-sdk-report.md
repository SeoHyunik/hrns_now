# Phase 7 작업 보고서 — 내장 개발용 Harness SDK runtime source (새 Phase 7, G7-SDK)

## 0. 범위 선언과 선행 Gate 상태

이 보고서는 `doc/claude_prompts/phase7-internal-sdk-runtime.md`의 새 Phase 7만 다룬다. 작업 시작 전 `git log -1 --oneline`으로 확인한 HEAD는 `3116f37 docs: Phase 7 시작 기준 확인 절차 정리`였고, 이는 문서가 명시한 선행 커밋 `34f57dc docs: Phase 7 내장 개발 SDK 작업 정의`와 `b8ee279 fix: Phase 6 UI UX 피드백과 용어 개선`를 조상으로 포함한다.

- **새 Phase 6(G6-UX)는 여전히 BLOCKED다** — 자동 검증과 release MSI 재패키징은 통과했지만 native 창의 수동 QA 증빙이 없다는 이유가 이번 세션으로 바뀌지 않는다.
- 제품 소유자가 2026-07-28에 G6-UX를 PASS 처리하지 않은 채 이 Phase 7을 병행 승인한 sequencing exception을 그대로 인용하며, 이 보고서는 G6-UX/G6A/G6B/기존 Phase 7E 중 어느 것도 PASS라고 주장하지 않는다.
- `D:\harness-kit`, `doc/hrns_now_packaging_plan.md`, MSI/Gradle 패키징 설정, 보류 6A/6B staging은 이번에도 수정하지 않았다(§8·§9).
- 이 세션 동안 `git add`/`commit`/`amend`/`rebase`/`reset`/`stash`/`clean`/`push`를 수행하지 않았다. 커밋과 G7-SDK Gate 판정은 Codex만 한다.

## 1. 실제 로컬 SDK 존재 여부와 자동 copy 금지

- 이 작업 환경에는 `.local\harness-kit`이 **존재하지 않는다** — `ls .local` 결과 `No such file or directory`로 재확인했다. `.gitignore`에는 이미 `.local/harness-kit/` 항목이 있었다(Codex가 앞선 문서 커밋에서 추가).
- 이 세션은 `.local\harness-kit`을 생성·복사·수정하지 않았다. `D:\harness-kit`도 참조·복사·수정하지 않았다 — 새 `DeveloperSdkRuntimeResolver`(§3)는 순수 `Files.exists`/`isDirectory`/`isReadable`/`isRegularFile` 조회만 하며 어떤 파일도 쓰지 않는다.
- 실제 SDK가 없으므로 테스트는 전부 `Files.createTempDirectory()` fixture로 만든 임시 디렉터리에 필요한 4개 파일(`scripts/doctor.ps1`, `scripts/validate-ops.ps1`, `scripts/run-cycle.ps1`, `kit-version.json`)만 채워 넣어 검증했다(§6). `D:\harness-kit`을 복사해 fixture로 삼지 않았다.
- 이 환경에서 `./gradlew.bat :composeApp:run`으로 앱을 실제로 띄우면, 기본 등록 흐름은 `InternalDeveloperSdk`를 해석하려 시도하고 `.local\harness-kit`이 없으므로 `RuntimeResolution.Missing`으로 fail-closed되어 "개발용 내장 SDK(.local\harness-kit)를 찾을 수 없습니다" 진단과 함께 등록·실행이 잠길 것으로 예상한다 — 이 예상은 §6의 자동 테스트로 검증했을 뿐, 실제 GUI 실행으로 재확인하지는 않았다(§7의 정직한 한계).

## 2. 변경 파일

```text
[신규]
core/src/main/kotlin/io/hrns_now/core/domain/model/RuntimeSource.kt
core/src/main/kotlin/io/hrns_now/core/port/RuntimeSourceResolverPort.kt
infra/src/main/kotlin/io/hrns_now/infra/runtime/DeveloperSdkRuntimeResolver.kt
infra/src/test/kotlin/io/hrns_now/infra/runtime/DeveloperSdkRuntimeResolverTest.kt

[변경 — production]
core/src/main/kotlin/io/hrns_now/core/domain/model/HarnessProject.kt
core/src/main/kotlin/io/hrns_now/core/usecase/ExecuteHarnessActionUseCase.kt
core/src/main/kotlin/io/hrns_now/core/usecase/RegisterProjectUseCase.kt
core/src/main/kotlin/io/hrns_now/core/usecase/ResolveActiveProjectUseCase.kt
infra/src/main/kotlin/io/hrns_now/infra/registry/JsonProjectRegistryAdapter.kt
infra/src/main/kotlin/io/hrns_now/infra/registry/ProjectRegistryDto.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/App.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/mapper/CockpitProjectionAssembler.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/mapper/CockpitUiStateAssembler.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/model/CockpitProjection.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/model/HrnsUiState.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/viewmodel/AppViewModel.kt
composeApp/src/jvmMain/kotlin/io/hrns_now/app/ui/Screens.kt

[변경 — test]
core/src/test/kotlin/io/hrns_now/core/usecase/ExecuteHarnessActionUseCaseTest.kt
core/src/test/kotlin/io/hrns_now/core/usecase/HarnessCommandMapperTest.kt
core/src/test/kotlin/io/hrns_now/core/usecase/ProjectSelectionUseCaseTest.kt
core/src/test/kotlin/io/hrns_now/core/usecase/RegisterProjectUseCaseTest.kt
core/src/test/kotlin/io/hrns_now/core/usecase/ResolveActiveProjectUseCaseTest.kt
infra/src/test/kotlin/io/hrns_now/infra/registry/JsonProjectRegistryAdapterTest.kt
composeApp/src/jvmTest/kotlin/io/hrns_now/app/presentation/viewmodel/AppViewModelTest.kt
```

`composeApp/build.gradle.kts`, MSI/JRE/패키징 설정은 이번 세션에서 **전혀 변경하지 않았다**(§9).

## 3. Typed runtime source·resolver·Registry migration·UX 흐름

### 3.1 Typed 모델(신규)

```kotlin
sealed interface RuntimeSource {
    data object InternalDeveloperSdk : RuntimeSource
    data class ExternalKit(val root: Path) : RuntimeSource
}

enum class RuntimeIssue { NotDirectory, NotReadable, MissingEntrypoint }

sealed interface RuntimeResolution {
    val source: RuntimeSource
    data class Resolved(override val source: RuntimeSource, val root: Path) : RuntimeResolution
    data class Missing(override val source: RuntimeSource) : RuntimeResolution
    data class Invalid(override val source: RuntimeSource, val reason: RuntimeIssue) : RuntimeResolution
}
```

`doc/hrns_now_design_pattern.md` §20.1이 요구한 그대로의 이름·의미를 유지했다. `HarnessProject.kitRoot: Path`를 **제거**하고 `runtimeSource: RuntimeSource`로 바꿨다 — 실제 파일 시스템 root가 필요한 모든 지점(command 실행, boundary, compatibility)은 이제 domain 모델이 아니라 **해석된 root**만 받는다.

### 3.2 Resolver(신규 infra adapter)

`infra/runtime/DeveloperSdkRuntimeResolver.kt`가 `RuntimeSourceResolverPort`의 유일한 구현이다.

- `InternalDeveloperSdk` → `internalSdkRootProvider()`(기본값: `Path.of(System.getProperty("user.dir")).resolve(".local").resolve("harness-kit")`, `./gradlew run`/IDE 실행 시의 HRNS-NOW source checkout 기준). 패키지된 설치본은 `user.dir`이 dev checkout이 아니므로 자연스럽게 `Missing`이 되도록 **의도적으로** 설계했다 — 이는 버그가 아니라 "개발 전용 편의, 배포 Runtime 아님"이라는 §20.1 규범을 그대로 코드로 반영한 것이다.
- `ExternalKit(root)` → 주어진 경로를 그대로 검사한다. internal/external 분기는 **이 resolver 안에서만** 존재하며, 호출자(`RegisterProjectUseCase`, `AppViewModel`, `HarnessCommandMapper`)는 `RuntimeSource`의 실제 종류를 몰라도 되게 했다.
- 두 경우 모두 존재(`Files.exists`) → 디렉터리(`isDirectory`) → 읽기 가능(`isReadable`) → 공개 entrypoint 4종(`scripts/doctor.ps1`, `scripts/validate-ops.ps1`, `scripts/run-cycle.ps1`, `kit-version.json`) **존재만** 확인한다. 파일 **내용은 읽지 않는다** — `kit-version.json`의 실제 파싱/버전 판정은 여전히 기존 `KitVersionManifestPort`/`CompatibilityPolicy`가 `Resolved.root`를 받은 뒤에만 별도로 수행한다.

### 3.3 Registry migration(infra DTO)

`HarnessProjectDto`에 optional `runtime_source_type`(`"internal_developer_sdk"` | `"external_kit"`)을 추가했다. `schema_version`은 `"1.0"` 그대로 유지했다 — "JSON schema를 불필요하게 대폭 올리지 말 것" 지시를 따라 이 field 하나만 추가했다.

- `runtime_source_type`이 없고 `kit_root`만 있는 기존(legacy) entry는 **읽기 시점에** `ExternalKit(kit_root)`으로 해석한다(별도 batch migration이 아니라 backward-compatible parser). 이 project가 다음에 다시 저장되면(`toDto()`) 그때 `runtime_source_type: "external_kit"`이 명시적으로 채워지며 자연스럽게 정착된다.
- `InternalDeveloperSdk`는 **절대 경로를 저장하지 않는다** — `toDto()`는 `kit_root = null`을 쓰고 `runtime_source_type = "internal_developer_sdk"`만 남긴다. source checkout을 다른 위치로 옮겨도 Registry가 깨진 경로를 참조하지 않는다.
- `runtime_source_type`이 알려지지 않은 값이면 그 entry만 `ProjectMapResult.Failure`로 떨어뜨리고(부분 복구), 나머지 entry는 그대로 살린다 — 기존 손상 복구 계약(quarantine·atomic rewrite)을 그대로 재사용했다.
- Registry 원자성(temp+atomic move), 손상 quarantine, UTF-8 without BOM 계약은 전혀 건드리지 않았다 — `JsonProjectRegistryAdapter`의 `save`/`findAll`/`recoverCorruption`/`writeEnvelope` 로직 자체는 무변경이다. `isRegistryInsideProject`만 `project.kitRoot` 대신 `(project.runtimeSource as? RuntimeSource.ExternalKit)?.root`를 보도록 최소 수정했다 — `InternalDeveloperSdk`는 domain에 절대 경로가 없으므로 이 경계 검사에서 자연히 제외된다(§4에서 근거 설명).

### 3.4 UX 흐름(composeApp)

- `ProjectRegistrationForm`(`Screens.kt`)에서 상시 `Kit root` 입력 필드를 제거했다. 기본은 `useInternalDeveloperSdk = true`(안내 문구: "기본값은 개발용 내장 SDK(.local\harness-kit)입니다")이며, `고급 설정` 버튼을 눌러야만 `외부 Harness Kit 사용` 체크박스와 그 아래 Kit 경로 입력이 나타난다. 체크박스가 꺼져 있으면 등록 버튼의 활성 조건에 Kit 경로가 전혀 관여하지 않는다.
- `ActiveProjectSummaryCard`는 이제 "Kit 경로" 원문 대신 **Runtime source** 행("개발용 내장 SDK"/"외부 Harness Kit")과 문제가 있을 때만 나타나는 원인 문구를 보여준다.
- `프로젝트 관리` modal의 프로젝트 목록(`ProjectRow`)에 각 프로젝트의 runtime source 배지를 추가했다 — 기존 external project는 여전히 "외부 Harness Kit"으로 명확히 표시되고 경로도 그대로 보존된다(§4).
- Cockpit 화면에 `Runtime source 확인` 진단 카드(신규)를 compatibility 카드보다 **위에** 추가했다 — runtime이 Missing/Invalid일 때만 나타나고, 그 경우 compatibility 카드는 표시하지 않는다(§5의 근거 분리).
- 이 흐름 전체에서 Composable은 파일 존재 확인·경로 조합·PowerShell 실행을 하지 않는다 — `useInternalDeveloperSdk`/`kitRootRaw` 같은 순수 입력값만 typed `RegisterProjectCandidate`로 담아 `HrnsUiEvent.ProjectRegistrationRequested`로 보낼 뿐이다. 실제 해석은 `AppViewModel` → `RegisterProjectUseCase`/`RuntimeSourceResolverPort`(IO dispatcher 안)에서만 일어난다.

## 4. External project·legacy Registry 호환성과 경계

- **손실 없는 호환성**: 기존에 `kit_root`만 가진 Registry entry(예: Phase 6까지 등록된 `auziraum`/`hantu`류 실사용 데이터)는 `runtime_source_type` 없이도 여전히 `RuntimeLoadResult.Success`로 읽히고 `ExternalKit(kit_root)`으로 정확히 해석된다 — `JsonProjectRegistryAdapterTest`의 `runtime_source_type이 없는 legacy entry는 kit_root를 ExternalKit으로 읽기 시점에 해석한다` 테스트로 고정했다(§6).
- **묵시적 override 금지**: `HRNS_KIT_ROOT` 등 기존 환경변수 fallback은 `activeProject == null`(Registry에 아무 프로젝트도 선택되지 않은 경우)일 때만 여전히 동작한다 — `AppViewModel.loadOnce()`는 활성 프로젝트가 있을 때만 `RuntimeSourceResolverPort`를 거치고, 없을 때는 기존 `EnvironmentWorkspaceConfigProvider` 경로를 그대로 쓴다. 저장된 `InternalDeveloperSdk`/명시적 `ExternalKit` 선택을 환경변수가 조용히 덮어쓸 여지가 없다.
- **경계 검사 근거**: `RegisterProjectUseCase.inspect()`는 `InternalDeveloperSdk`든 `ExternalKit`이든 **먼저 resolver로 실제 root를 얻은 뒤** 그 root를 `RealPathGateway`(`pathResolver`)로 다시 통과시켜 `RootPathCheck`를 만들고, 그 결과만 `BoundaryPolicy.evaluate(kit, workspace, repository)`에 넘긴다. 즉 `.local\harness-kit`이 HRNS-NOW source 내부에 있다는 이유로 boundary 검사를 건너뛰지 않는다 — 해석된 실제 경로가 target repository/workspace와 상호 포함되면 여전히 등록이 차단된다.
- **command/compatibility는 resolved root만**: `HarnessCommandMapper.map(action, project, resolvedKitRoot, day)`는 `project.runtimeSource`를 전혀 읽지 않는다 — `HarnessCommandMapperTest`에 "command의 kitRoot는 project runtimeSource가 아니라 전달된 resolvedKitRoot를 그대로 쓴다" 회귀 테스트를 추가해 이를 고정했다. `AppViewModel.evaluateCompatibility`도 활성 프로젝트가 있을 때는 `runtimeResolution`이 `Resolved`일 때만 `KitVersionManifestPort.readManifest()`를 호출하고, `Missing`/`Invalid`면 호출 자체를 생략한다(§5).

## 5. Runtime availability와 Compatibility의 분리(설계 근거)

- `RuntimeResolution`(가용성/무결성)과 `HarnessCompatibilityDetail`(버전 호환성)은 **서로 다른 sealed 계층**으로 남겨뒀다 — 하나로 합치거나 `RuntimeResolution`이 `kit-version.json` 내용을 판정하게 만들지 않았다.
- 정책 게이트(`ActionContext.compatibility: CompatibilityStatus`)는 기존 그대로 재사용했다 — `AppViewModel.evaluateCompatibility()`가 runtime이 Missing/Invalid일 때 `HarnessCompatibilityDetail.MissingManifest` sentinel을 반환해 **같은 fail-closed 게이트**(`Unsupported`)로 실행을 잠그지만, **화면에 보여주는 진단은 분리**했다 — `CockpitProjection.runtimeSourceDiagnostics`(신규)가 채워져 있으면 `compatibilityDiagnostics`는 `null`로 억제한다(`CockpitProjectionAssembler`). 사용자는 "Harness 버전이 안 맞습니다"가 아니라 "런타임 자체를 찾지 못했습니다"라는 정확한 원인을 본다.
- `ActionPolicy`/`ClosurePolicy`/`CompatibilityPolicy`/`BoundaryPolicy`의 판정 로직 자체는 이번 세션에서 **한 줄도 바꾸지 않았다** — 새 게이트는 모두 기존 enum/타입을 그대로 재사용해 만들었다.

## 6. 테스트

### 6.1 core

- `RegisterProjectUseCaseTest`: `useInternalDeveloperSdk=true`+`Resolved` → `InternalDeveloperSdk`로 등록, `Missing`/`Invalid`(entrypoint 없음) → `InvalidCandidate`(save 미호출), 외부 Kit `Missing` → boundary 검사 전에 `InvalidCandidate`, 외부 Kit 경로를 비우면 resolver 호출 전에 거부(신규 5건 + 기존 4건 갱신).
- `HarnessCommandMapperTest`: command의 `kitRoot`가 `project.runtimeSource`가 아니라 전달된 `resolvedKitRoot` 그대로임을 고정(신규 1건).
- `ResolveActiveProjectUseCaseTest`/`ProjectSelectionUseCaseTest`/`ExecuteHarnessActionUseCaseTest`: fixture를 `runtimeSource = RuntimeSource.ExternalKit(...)`로 갱신하고 `HarnessExecutionContext.resolvedKitRoot`를 채워 기존 계약(Registry 우선순위, 날짜 선택, lock 보유 중 State reread)이 회귀 없이 그대로 통과함을 재확인.

### 6.2 infra

- `DeveloperSdkRuntimeResolverTest`(신규, 8건): 존재하지 않는 root → `Missing`, 파일인데 디렉터리로 기대 → `Invalid(NotDirectory)`, entrypoint 일부 누락 → `Invalid(MissingEntrypoint)`, 4종 모두 존재 → `Resolved`, **한글+공백 경로**(`한글 폴더 이름 테스트`) 정상 해석, `ExternalKit`이 `internalSdkRootProvider`를 절대 쓰지 않음(provider가 유효하고 external이 무효인 경우와 그 반대 모두 검증), 기본 `defaultInternalSdkRoot()`가 `user.dir` 상대 경로를 계산함. `D:\harness-kit`을 복사하지 않고 전부 `Files.createTempDirectory()` fixture만 사용했다.
- `JsonProjectRegistryAdapterTest`(신규 3건 추가): legacy `kit_root`-only entry의 `ExternalKit` migration, `InternalDeveloperSdk`가 절대 경로 없이 저장·왕복 복원됨, `runtime_source_type`이 알 수 없는 값이면 그 entry만 격리됨. 기존 한글/공백/drive-letter, atomic move, 손상 quarantine, UTF-8 no BOM, secret 미포함 테스트는 fixture를 `runtimeSource`로만 바꾸고 그대로 재사용해 회귀를 검증했다.

### 6.3 composeApp/ViewModel

- 신규 3건: `useInternalDeveloperSdk=true`+`Missing` → Registry 미저장·사유 표시, `useInternalDeveloperSdk=true`+`Resolved` → `InternalDeveloperSdk`로 등록되고 `cockpit.runtimeSourceLabel == "개발용 내장 SDK"`, 활성 프로젝트의 runtime이 Missing이면 `runtimeSourceDiagnostics`만 채워지고 `compatibilityDiagnostics`는 `null`로 남음(§5의 분리를 그대로 assert).
- 회귀: command mapper/encoder 경로, compatibility late-write guard(프로젝트 A/B 전환), Registry 선택 순서(`ActiveProjectSource`), IO dispatcher 전체 경유(신규 resolver 호출도 `recordThread()`로 포함시켜 검증 범위를 넓혔다), Phase 6 action feedback/요구사항 modal 관련 기존 테스트는 무변경으로 통과.

### 6.4 실행 결과

```powershell
.\gradlew.bat :core:compileTestKotlin :infra:compileTestKotlin :composeApp:compileTestKotlinJvm   # BUILD SUCCESSFUL
.\gradlew.bat :core:test :infra:test :composeApp:jvmTest    # BUILD SUCCESSFUL
.\gradlew.bat check                                          # BUILD SUCCESSFUL
```

JUnit XML 실측: `core` 128(+6), `infra` 154(+11), `composeApp` 77(+9). 합계 **359**, 실패·에러 0건. 테스트를 삭제·skip·약화하지 않았다 — 기존 fixture는 새 타입(`RuntimeSource`)에 맞춰 갱신했을 뿐 검증 대상 자체는 유지했다.

### 6.5 미실행 검증과 사유

- **MSI 재패키징**: `composeApp/build.gradle.kts`를 포함해 패키징 설정을 전혀 바꾸지 않았으므로(§2) `:composeApp:packageReleaseMsi`를 재실행하지 않았다. 소스 변경은 이미 `check`(compile 포함)로 회귀 없음을 확인했다.
- **실제 GUI 수동 QA**: 이 환경에는 네이티브 Compose Desktop 창을 실제로 띄워 클릭하는 절차가 없다(Phase 6 보고서에서도 동일하게 기록한 한계 — project skill 없음, Playwright/Electron류 드라이버가 이 native JVM 창에 적용되지 않음). 따라서 "고급 설정을 펼쳐야 Kit 필드가 보인다", "Runtime source 진단 카드가 compatibility 카드 위에 실제로 보인다" 같은 화면 배치는 **육안으로 검증하지 못했다** — ViewModel/projection 레벨(§6.3)에서 데이터가 올바르게 조립되는 것만 확인했다.
- **`.local\harness-kit`을 통한 실제 Harness 실행**: §1에서 밝힌 대로 이 환경에 실제 SDK가 없어 시도하지 않았다. `D:\harness-kit`을 복사해 대체 fixture로 쓰지도 않았다.

## 7. SOLID·Ports and Adapters·Resolver/Repository/Composition Root 판단

| 항목 | 판정 | 근거 |
|---|---|---|
| SRP | 유지 | `DeveloperSdkRuntimeResolver`는 root 해석만 한다 — manifest 파싱(`JsonKitVersionManifestAdapter`)이나 compatibility 판정(`CompatibilityPolicy`)을 겸하지 않는다 |
| OCP | 유지 | `RuntimeSource`가 새 종류(예: 향후 승인 artifact)를 얻어도 `HarnessCommandMapper`/`BoundaryPolicy`/compatibility 파이프라인은 코드 변경 없이 `Resolved.root`만 계속 받는다 |
| LSP | 유지 | `RuntimeSourceResolverPort`의 실제 구현과 테스트용 람다(`identityRuntimeSourceResolver` 등)가 같은 typed 계약(`Resolved`/`Missing`/`Invalid`)을 지킨다 |
| ISP | 유지 | `RuntimeSourceResolverPort`는 `resolve(source)` 단일 메서드만 가진 fun interface다 — `KitVersionManifestPort`와 같은 크기로 유지했다 |
| DIP | 유지 | `core`는 `Files`/`Path` 존재 확인(구체적 infra 동작)을 모른다 — `RuntimeSourceResolverPort`라는 추상에만 의존한다 |
| Composition Root | 유지 | `App.kt`가 `DeveloperSdkRuntimeResolver()` 인스턴스 하나만 만들어 `RegisterProjectUseCase`와 `AppViewModel` 양쪽에 주입한다 — internal/external 분기가 흩어진 곳은 resolver 구현 내부 하나뿐이다 |
| Repository 패턴 | 유지 | `ProjectRegistryPort`/`JsonProjectRegistryAdapter`의 공개 계약(`findAll`/`findById`/`save`/`delete`/`markActive`)은 무변경 — DTO 내부 필드만 늘었다 |
| God object 회피 | 유지 | Registry, BoundaryPolicy, resolver, compatibility를 하나의 서비스로 합치지 않았다 — `RegisterProjectUseCase.inspect()`가 이들을 순서대로 호출하는 조율자 역할만 한다 |

## 8. `WORKFLOW_STATE.json`/기존 계약 보존 근거

- `ActionPolicy`, `ClosurePolicy`, `CompatibilityPolicy`, `BoundaryPolicy` 소스 코드는 `git status`에 나타나지 않는다(§2) — 판정 로직 자체를 UI 편의로 완화하지 않았다.
- `ExecuteHarnessActionUseCase`의 policy 재검증 → typed command → lock → runner → lock 보유 중 State reread → release 순서는 그대로다 — 이번에 바뀐 것은 `HarnessExecutionContext`에 `resolvedKitRoot: Path` 필드가 하나 늘고 `HarnessCommandMapper.map()`이 그 값을 파라미터로 받는 것뿐이다.
- UI는 여전히 `WORKFLOW_STATE.json`/daily 4-file을 직접 만들거나 쓰지 않는다. 자동 resume/`--continue`/Claude API 직접 호출/raw session ID·secret·token·raw log 저장·표시를 추가하지 않았다.

## 9. Harness/패키징/보류 과제 관련 명시

- `D:\harness-kit`은 이번 세션 동안 참조·수정·복사·zip backup 어느 것도 하지 않았다.
- `.local\harness-kit`을 자동 생성·자동 복사·자동 update하지 않았다 — 이 디렉터리는 이 환경에 존재하지 않고, 이 사실 자체를 fail-closed 동작(Missing)의 근거로 삼았을 뿐이다.
- `doc/hrns_now_packaging_plan.md`는 읽지도, 수정·삭제·stage하지도 않았다 — `git status`에 여전히 사용자 소유 untracked 파일로 남아 있다.
- MSI/Gradle 패키징 설정, bundled JRE, Program Files data 경계는 수정하지 않았다(§2, §6.5).
- 보류 기존 Phase 6A(G6A)/Phase 6B(G6B)/Phase 7E는 이번에도 구현·재개하지 않았다. 이 Phase 7(G7-SDK)의 완료가 그 Gate들을 통과시킨다고 주장하지 않는다.
- 이 세션에서 `git add`/`commit`/`amend`/`rebase`/`reset`/`stash`/`clean`/`push`를 수행하지 않았다. G7-SDK PASS 여부, 새 Phase 6 수동 QA 완료 여부, 보류 과제 재개 여부는 모두 Codex만 판정한다.

## 10. Residual risk(정직한 잔여 위험)

- **육안 GUI 미검증**(§6.5): 고급 설정 토글, Runtime source 진단 카드 배치, 프로젝트 목록의 source 배지가 실제 창에서 기대대로 보이는지는 다음 수동 QA에서 확인이 필요하다.
- **`Missing` ↔ `Invalid` 전환 polling 감지 사각지대**: `AppViewModel.loadOnce()`의 재조회 트리거는 `compatibilityDetail` 변화(`compatibilityChanged`)에 의존하는데, runtime이 `Missing`에서 `Invalid`로(또는 그 반대로) 바뀌어도 둘 다 동일한 `HarnessCompatibilityDetail.MissingManifest` sentinel로 매핑되므로 polling만으로는 이 전환이 감지되지 않을 수 있다 — 수동 새로고침(`forceRead=true`)은 항상 최신 상태를 다시 계산하므로 사용자가 새로고침하면 해결된다. 이 사각지대를 메우는 별도 `lastRuntimeResolution` 변경 추적은 이번 범위에서 추가하지 않았다(효과 대비 구현 비용이 낮다고 판단).
- **`.local\harness-kit`이 실제로 준비된 환경에서의 end-to-end 확인 없음**: 이 세션은 fixture로만 resolver를 검증했다. 실제 개발 SDK checkout이 있는 환경에서의 등록→Doctor→State 조회 표준 흐름은 아직 실측되지 않았다.

## Codex 독립 검증·보정 — 2026-07-28

### 검증 기준과 범위

- 검증 시작 HEAD: `3116f37 docs: Phase 7 시작 기준 확인 절차 정리`
- 검증 대상: Claude의 미커밋 Phase 7 구현과 이 보고서. 사용자 소유 untracked `doc/hrns_now_packaging_plan.md`는 읽기·수정·stage하지 않았다.
- `.local\harness-kit`은 현재도 존재하지 않으며, `D:\harness-kit`을 복사·수정·참조하지 않았다. `.gitignore`의 `.local/harness-kit/` 제외 규칙도 직접 확인했다.

### 발견 사항과 보정

1. **Major — source checkout 경계가 `user.dir`에만 의존**: 최초 `DeveloperSdkRuntimeResolver.defaultInternalSdkRoot()`는 현재 working directory를 곧바로 HRNS-NOW source root로 간주했다. IDE·직접 실행·build distributable은 서로 다른 working directory를 줄 수 있어 repository-relative `.local\harness-kit` 계약을 보장하지 못했다.
   - 보정: 현재 경로와 상위 경로에서 `settings.gradle.kts`, `gradlew.bat`, `core/build.gradle.kts`, `composeApp/build.gradle.kts`를 함께 확인해 HRNS-NOW source checkout만 식별한다. 식별 실패 시 root를 추측하지 않고 `RuntimeResolution.Missing`으로 fail-closed한다.
   - 회귀 테스트: source checkout 하위 build 경로에서 `.local\harness-kit`을 계산하는 경우와, 무관한 Git/working directory를 거부하는 경우를 추가했다.

2. **Major — internal runtime Registry entry의 `kit_root`가 조용히 유실될 수 있음**: `runtime_source_type=internal_developer_sdk`인데 `kit_root`도 있는 모순 입력을 읽을 때 기존 구현은 경로를 무시하고 다음 저장에서 삭제할 수 있었다.
   - 보정: 모순 entry를 `ProjectMapResult.Failure`로 격리하고 기존 corruption quarantine·atomic rewrite 경로를 사용한다. legacy `runtime_source_type` 누락 + `kit_root` entry는 계속 `ExternalKit`으로 해석한다.
   - 회귀 테스트: valid legacy entry를 보존하면서 모순 internal entry만 격리하는 경우를 추가했다.

### 설계 판정

- `RuntimeSourceResolverPort`의 단일 책임과 core→port←infra 의존 방향은 유지된다. source root 탐색은 infra resolver에만 남고 Composable·ViewModel·command mapper에는 분기나 경로 조합을 추가하지 않았다.
- command·compatibility·boundary는 계속 `RuntimeResolution.Resolved.root`만 소비하며, `InternalDeveloperSdk`의 절대 경로는 Registry에 저장하지 않는다.
- `WORKFLOW_STATE.json` 소유권, typed command·lock·State reread 순서, `ActionPolicy`/`ClosurePolicy`/`CompatibilityPolicy`/`BoundaryPolicy`, MSI·Harness Runtime 배포 경계는 변경하지 않았다.

### Codex 검증 결과

| 구분 | 명령 | 결과 |
|---|---|---|
| Targeted | `./gradlew.bat :core:test --tests "io.hrns_now.core.usecase.RegisterProjectUseCaseTest"` | PASS |
| Targeted | `./gradlew.bat :infra:test --tests "io.hrns_now.infra.runtime.DeveloperSdkRuntimeResolverTest" --tests "io.hrns_now.infra.registry.JsonProjectRegistryAdapterTest"` | PASS |
| Module | `./gradlew.bat :core:test :infra:test :composeApp:jvmTest` | PASS |
| Full | `./gradlew.bat check` | PASS |

JUnit XML 재확인 결과는 core 128, infra 156, composeApp 77로 합계 **361**이며 failures/errors/skipped는 모두 0이다. MSI 재패키징은 Gradle 패키징 설정을 변경하지 않아 재실행하지 않았다. native Compose 창의 육안 QA와 실제 사용자가 제공한 `.local\harness-kit`을 통한 등록→Doctor→State 조회는 현재 SDK 부재로 미실행이며, 이를 PASS로 주장하지 않는다.

### Gate 판정

- **Verdict: PASS_WITH_FIXES**
- **G7-SDK: PASS_WITH_FIXES** — source-level 계약, fail-closed 동작, Registry migration, module/full test는 통과했다. 실제 SDK checkout·native UI의 수동 end-to-end 확인은 잔여 운영 QA다.
- 새 Phase 6(G6-UX), 기존 G6A, 기존 G6B, 기존 Phase 7E는 모두 기존 `BLOCKED`/보류 상태를 유지한다. 이 판정은 MSI Runtime 배포 또는 clean Windows Gate 통과를 뜻하지 않는다.
