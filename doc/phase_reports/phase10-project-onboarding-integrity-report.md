# Phase 10 — 프로젝트 온보딩 무결성: Bridge · Workspace · 첫 요구사항 흐름 완료 보고

## 0. 작업 순서에 대한 투명한 기록 (Phase 9 QA gate와의 관계)

Phase 9 보고서(`doc/phase_reports/phase9-desktop-layout-and-onboarding-report.md`)에는 Codex의 독립 검증이 이미 추가되어 있다. Codex는 core/infra/composeApp 회귀 수정(§9.1)을 확인하고 코드·자동 테스트·Harness 계약 재확인은 PASS로 판정했지만, 실제 native 창에서 사용자가 "진단·등록 및 오늘 작업공간 준비"/"등록만 하기"/"프로젝트 해제" 버튼을 직접 클릭하는 모습을 본 증거가 없다는 이유로 **`PHASE_9_STATUS: BLOCKED`**, **`NEXT_ALLOWED_PHASE: Phase 9 native interaction QA gate`**로 마감했다.

이 Phase 10 작업은 사용자가 `doc/claude_prompts/phase10-project-onboarding-integrity.md`를 명시적으로 지정해 "동일한 작업 방식으로 개발을 진행"하라고 지시했기 때문에 시작했다. 즉 Phase 9의 native click QA gate가 아직 열려 있는 상태에서, 사용자가 명시적으로 새 기능 Phase로 건너뛰라고 지시한 것이다. 이는 이 프로젝트의 Phase 7 선례(제품 소유자가 열린 gate에도 불구하고 병행 작업을 명시적으로 승인한 사례)와 같은 성격의 예외이며, 다음 두 가지를 동시에 사실대로 남긴다.

- Phase 9의 native 클릭 QA는 여전히 미수행 상태다. 이 보고서가 그 공백을 메우지 않는다.
- Phase 10 구현 자체는 이 보고서에서 별도로 코드·테스트·(가능한 범위의) 실제 Harness 스크립트 실행으로 검증했다. Phase 9 gate의 미해결과 Phase 10 구현의 완성도는 별개 사실이며, 아래 각 섹션은 Phase 10만을 판단 대상으로 삼는다.

## 1. 시작 HEAD, 변경 파일, 사용자 소유 untracked 자료 보존 여부

- 시작 HEAD: `9f66f5a docs: Phase 10 프로젝트 온보딩 작업 정의` (branch `harness-dev`)
- 최근 로그(읽기 전용 확인):

```text
9f66f5a (HEAD -> harness-dev) docs: Phase 10 프로젝트 온보딩 작업 정의
7d457c3 fix: 활성 프로젝트 리본 표시 보정
b55b08a feat: Phase 9 데스크톱 온보딩과 레이아웃 개선
f6d1ced docs: Phase 9 QA 개선 작업 지시 추가
```

- 세션 동안 git 작업(commit/amend/rebase/reset/stash/clean/push)은 전혀 수행하지 않았다. `git status --short`/`git log`/`git show`만 읽기 전용으로 사용했다.
- 변경한 파일(수정):
  - `core/src/main/kotlin/io/hrns_now/core/domain/model/HarnessCommand.kt`
  - `infra/src/main/kotlin/io/hrns_now/infra/process/HarnessCommandEncoder.kt`
  - `infra/src/main/kotlin/io/hrns_now/infra/process/PowerShellHarnessAdapter.kt`
  - `infra/src/test/kotlin/io/hrns_now/infra/process/HarnessCommandEncoderTest.kt`
  - `infra/src/test/kotlin/io/hrns_now/infra/process/PowerShellHarnessAdapterTest.kt`
  - `composeApp/src/jvmMain/kotlin/io/hrns_now/app/App.kt`
  - `composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/mapper/CockpitUiStateAssembler.kt`
  - `composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/mapper/RunStatusProjectionAssembler.kt`
  - `composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/model/HrnsUiEvent.kt`
  - `composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/model/HrnsUiState.kt`
  - `composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/model/RegistrationFeedback.kt`
  - `composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/viewmodel/AppViewModel.kt`
  - `composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/viewmodel/ViewModelStrings.kt`
  - `composeApp/src/jvmMain/kotlin/io/hrns_now/app/ui/Screens.kt`
  - `composeApp/src/jvmMain/kotlin/io/hrns_now/app/ui/Shell.kt`
  - `composeApp/src/jvmMain/kotlin/io/hrns_now/app/ui/Strings.kt`
  - `composeApp/src/jvmTest/kotlin/io/hrns_now/app/presentation/viewmodel/AppViewModelTest.kt`
- 새로 추가한 파일:
  - `core/src/main/kotlin/io/hrns_now/core/domain/model/RepositoryBridgeSummary.kt`
  - `core/src/main/kotlin/io/hrns_now/core/port/RepositoryBridgeProbePort.kt`
  - `core/src/main/kotlin/io/hrns_now/core/usecase/OnboardProjectUseCase.kt`
  - `core/src/test/kotlin/io/hrns_now/core/usecase/OnboardProjectUseCaseTest.kt`
  - `infra/src/main/kotlin/io/hrns_now/infra/bridge/RepositoryBridgeProbe.kt`
  - `infra/src/test/kotlin/io/hrns_now/infra/bridge/RepositoryBridgeProbeTest.kt`
  - `doc/phase_reports/phase10-project-onboarding-integrity-report.md`(이 문서)
- 사용자 소유 untracked 자료(`doc/QA_captures/`, `doc/hrns_now_packaging_plan.md`, `doc/user_workflow_qa_notes.md`)는 `git status --short`에서 여전히 `??`(untracked)로만 표시되고, 세션 내내 읽기·stage·수정·삭제하지 않았다.
- `D:\harness-kit`은 문서·스크립트를 읽기만 했다. 어떤 쓰기 도구도 그 경로를 대상으로 호출하지 않았다. `.local\harness-kit`을 자동 생성하거나 `D:\harness-kit`을 복사하지 않았다.

## 2. live Harness 문서·source 계약과 현재 앱 흐름의 차이

Phase 9까지의 흐름은 다음과 같았다(`b55b08a` 기준):

```text
Doctor(진단) → Registry 저장 + 활성 선택 → run-cycle.ps1 Bootstrap(-UsePythonSidecars)
```

`D:\harness-kit\scripts\run-cycle.ps1`을 다시 읽어 확인한 결과, `BootstrapDay`는 missing workspace를 초기화(`init-workspace.ps1` 위임)하지만 repository 쪽에는 어떤 파일도 만들지 않는다. `D:\harness-kit\docs\PROJECT_ONBOARDING.md`와 `scripts/enter-project.ps1`을 읽어 실제 계약을 재확인했다.

- `enter-project.ps1`만 아래 3개 repository-local bridge 파일을 만들거나(기본 `-Force` 없이) 이미 있으면 보존한다.

  ```text
  .claude/settings.local.json
  .claude/CLAUDE.md
  tools/run-cycle.ps1
  ```

- 같은 스크립트가 내부적으로 `init-workspace.ps1`을 호출해 external workspace root, `memory/`, `logs/`, 오늘 day root, required daily 4-file(`REQUEST_INBOX.md`/`TODAY_STRATEGY.md`/`DAILY_HANDOFF.md`/`WORKFLOW_STATE.json`)을 준비한다.
- `doctor.ps1`은 bridge가 없으면 warning만 내고 파일을 만들지 않는다(읽기 전용, `-Json`도 side effect 없음).
- `validate-ops.ps1 -Json`은 workspace daily surface와 `WORKFLOW_STATE.json`을 검증만 하고 side effect가 없다.
- `run-cycle.ps1`은 신규 등록 직후 wrapper 없이 호출하면 `WORKFLOW_STATE.json`의 첫 상태가 `execution_completed`가 되어(3절 fixture 실행에서 실측 확인, 아래 §8) 요청 입력(`request_intake_pending`) 흐름을 건너뛰게 만든다.
- `enter-project` 직후 `init-workspace.ps1`이 새로 만든 State의 정상 시작점은 `request_intake_pending`이며, 이는 Harness가 쓴 값을 Reader가 그대로 해석해야 하는 값이지 UI가 임의로 만들 값이 아니다.

이번 Phase에서 앱 흐름을 다음으로 바꿨다(§5에서 코드 근거와 함께 상세 서술):

```text
candidate 검사(runtime 해석 + boundary) → Kit-only Doctor → compatibility 확인
→ Registry 저장 + 활성 project 선택 → 명시적 onboarding 확인
→ enter-project → validate-ops -Json → bridge probe + required 4-file probe + WORKFLOW_STATE 재조회
→ 요구사항 작성 가능 상태 표시
```

## 3. Doctor / enter-project / validate-ops / run-cycle 책임 분리

| 스크립트 | 성격 | 쓰기 여부 | HRNS-NOW 매핑 |
|---|---|---|---|
| `doctor.ps1` | 환경 점검 | 읽기 전용 | `HarnessCommand.Doctor` — Health Check, 등록 전 Kit-only 진단에도 재사용 |
| `enter-project.ps1` | 프로젝트 온보딩 | repository bridge 3종 + external workspace 초기화(쓰기, 기존 파일 보존) | 신규 `HarnessCommand.OnboardProject` |
| `validate-ops.ps1 -Json` | 검증 | 읽기 전용(JSON 계약, side effect 없음) | 기존 `HarnessCommand.ValidateOps`, onboarding 완료 판정에도 재사용 |
| `run-cycle.ps1`(Bootstrap/Planning/Replan/Execution/Closure) | 일일 workflow | workspace 쓰기(등록 직후 자동 호출 금지) | 기존 `HarnessCommand.BootstrapDay`/`RunPlanning`/... — Phase 10에서 신규 등록 직후 경로에서 완전히 제거 |

`AppViewModel.kt`의 `onProjectOnboardingRequested()`/`attemptOnboardingAfterRegistration()` 어디에도 `HarnessCommand.BootstrapDay`를 생성하지 않는다 — grep으로 재확인했다(신규 등록 경로와 "프로젝트 준비" CTA 경로 모두 `OnboardProject`→`ValidateOps` 두 command만 실행한다).

## 4. typed command·port·use case·lock lifecycle 설계 근거

- **Typed command**: `core/domain/model/HarnessCommand.kt`에 `HarnessCommandKind.OnboardProject`와 `HarnessCommand.OnboardProject(workspaceRoot, projectRoot, kitRoot, profile, date)`를 추가했다. `BootstrapDay`와 완전히 분리된 데이터 클래스이며 하나의 boolean/switch로 뭉개지 않았다.
- **Encoder**: `HarnessCommandEncoder`는 `OnboardProject`를 `scripts/enter-project.ps1`에 매핑하고 인자를 정확히 `-ProjectRoot -WorkspaceRoot -KitRoot [-Profile] -Date`만 순서대로 만든다. `-Force`/`-RunDoctor`/`-MaterializeSubagents`/`-AgentNames`는 어떤 분기에도 등장하지 않는다(`HarnessCommandEncoderTest`의 금지 switch 검증, §8).
- **Repository bridge probe port**: `core/port/RepositoryBridgeProbePort`(`fun interface`, `probe(repositoryRoot: Path): RepositoryBridgeSummary`)와 `core/domain/model/RepositoryBridgeSummary`(`BridgeFileState.Ready|Missing` 3개 필드 + `isReady`)를 새로 만들었다. 실제 구현은 `infra/bridge/RepositoryBridgeProbe`이며 `Files.isRegularFile`만으로 판정하고 어떤 파일도 쓰지 않는다. `AppViewModel`/Composable에는 `Files.exists`나 path 조합이 전혀 없다 — grep으로 재확인했다. 이 port는 "인터페이스를 근거 없이 늘리지 말라"는 설계 원칙의 예외로 명시한다: repository filesystem이라는 외부 경계를 read-only로 테스트 가능하게 분리하는 것이 목적이며, 실제로 `RepositoryBridgeProbeTest`(4건)가 이 경계만 독립적으로 검증한다.
- **Use case**: `core/usecase/OnboardProjectUseCase`를 새로 만들었다. `OnboardProjectContext(project, resolvedKitRoot, day)` → lock 획득 → `enter-project` 실행 → `validate-ops -Json` 실행 → bridge probe → required 4-file probe(`artifactProbe`) → `WORKFLOW_STATE.json` 재조회까지 **하나의 `LockHandle` 안에서** 순서대로 수행하고, `finally`에서만 lock을 해제한다. `OnboardProjectOutcome`은 `Completed(onboardResult, validateOpsResult, bridgeSummary, artifactSummary, refreshedState)` / `LockUnavailable(result)` 두 가지만 있다.
- **기존 `ExecuteHarnessActionUseCase`와의 관계**: 의도적으로 공유 추상화("HarnessCommandLifecycle")를 만들지 않았다. `ExecuteHarnessActionUseCase`는 daily `ActionPolicy` 게이팅 + 단일 command + 단일 lock을 갖고, `OnboardProjectUseCase`는 정책 게이팅이 전혀 없고 두 개의 순차 command + 두 개의 filesystem probe + 하나의 lock을 갖는다 — lifecycle이 실질적으로 다르므로 억지로 하나의 인터페이스로 묶으면 오히려 각 흐름의 불변조건(정책 유무, command 개수)이 흐려진다고 판단했다. `ExecuteHarnessActionUseCase.kt` 자체는 이번 Phase에서 한 줄도 수정하지 않았다(diff 없음, grep으로 재확인).
- **AppViewModel의 책임 경계**: `AppViewModel`은 `ProcessBuilder`/PowerShell 인자/파일 존재 확인을 직접 하지 않고, `onboardProject: OnboardProjectUseCase`와 `bridgeProbe: RepositoryBridgeProbePort`를 생성자로 주입받아 위임만 한다(`App.kt`의 composition root에서 실제 `OnboardProjectUseCase`/`RepositoryBridgeProbe` 인스턴스를 조립).

## 5. registration-only, primary registration, 기존 project repair의 상태 전이

세 가지 흐름은 `HrnsUiEvent`로 명확히 구분된다.

1. **등록만(registration-only)** — `HrnsUiEvent.ProjectRegistrationRequested(candidate, prepareWorkspace = false)`. `AppViewModel.onProjectRegistrationRequested()`는 Kit-only Doctor → compatibility → `RegisterProjectUseCase.save` → `SelectProjectUseCase`까지만 실행하고, `prepareWorkspace`가 false이므로 `attemptOnboardingAfterRegistration`을 호출하지 않는다. `registrationFeedback`은 `RegistrationFeedback.Success(name, ProjectOnboardingOutcome.NotAttempted)`가 된다. `HarnessCommand.OnboardProject`/`ValidateOps`는 전혀 실행되지 않는다(회귀 테스트로 고정, §8).
2. **신규 등록 primary flow(진단·등록 및 프로젝트 준비)** — 같은 이벤트를 `prepareWorkspace = true`로 올린다(`Screens.kt`의 `OnboardingConfirmDialog` 확인 후에만 이 값으로 전송). Registry 저장·활성 선택·context 재조회(`loadOnce(forceRead = true)`)가 끝난 뒤 `attemptOnboardingAfterRegistration` → `runOnboarding` → `OnboardProjectUseCase.invoke`가 실행된다. 결과는 `interpretOnboardingCompletion`이 5가지 근거(§6)로 판정해 `ProjectOnboardingOutcome.Ready` 또는 `Blocked(reasonText)`가 된다. 어느 경우든 Registry 등록 자체는 롤백하지 않는다 — `RegisterProjectResult.Registered`가 이미 반영된 뒤에만 onboarding을 시도하기 때문이다.
3. **기존 프로젝트 repair("프로젝트 준비" CTA)** — 이미 활성인 프로젝트인데 `AppViewModel.loadOnce()`가 매 폴링마다 계산하는 `needsProjectPreparation`(활성 프로젝트 존재 && 오늘 날짜 조회 && (bridge not ready || required 4-file not ready))이 true면 Setup 화면에 단일 "프로젝트 준비" 버튼이 뜬다. 클릭하면 `HrnsUiEvent.ProjectOnboardingRequested` → `onProjectOnboardingRequested()`가 **재등록 없이** 동일한 `OnboardProjectUseCase`를 실행한다. Health Check(Doctor) 버튼과는 별도 CTA이며 서로 대체하지 않는다.

세 흐름 모두 등록 직후 `BootstrapDay`/`run-cycle`을 호출하지 않는다 — Phase 9의 자동 Bootstrap 호출을 완전히 제거했다(`attemptWorkspacePreparationAfterRegistration` 함수 자체를 삭제하고 `attemptOnboardingAfterRegistration`으로 교체).

## 6. 성공·실패 판정의 concrete evidence와 State reread 근거

`AppViewModel.interpretOnboardingCompletion(outcome: OnboardProjectOutcome.Completed)`는 다음 5가지의 논리곱(AND)으로만 `Ready`를 반환한다.

1. `onboardResult`(= `enter-project.ps1`)가 `ProcessRunResult.Completed`이고 `exitCode == 0`
2. `validateOpsResult`(= `validate-ops.ps1 -Json`)의 `contract.overall`이 `HarnessOverallStatus.Ok`
3. `bridgeSummary.isReady`(3개 bridge 파일 모두 `Ready`)
4. `artifactSummary.isRequiredReady`(required 4-file 모두 `Exists`)
5. `refreshedState`(재조회한 `WORKFLOW_STATE.json`)가 `StateReadResult.Success`

하나라도 실패하면 `ProjectOnboardingOutcome.Blocked(onboardingIncompleteNotice(locale))`가 된다. stdout 문자열은 어디에서도 성공 판정에 쓰이지 않는다 — `ProcessRunResult.Completed.rawOutputSnippet`은 진단용 조각일 뿐 판정 입력이 아니다.

lock lifecycle: `OnboardProjectUseCase.invoke`는 `processLock.acquire` 성공 뒤 `try { enter-project 실행 → validate-ops 실행 → bridge probe → artifact probe → workflowState.read } finally { processLock.release }` 구조다. 5가지 증거 수집이 모두 **lock을 보유한 채** 끝난 뒤에만 lock을 해제한다 — `OnboardProjectUseCaseTest`의 `State 재조회는 lock을 보유한 채 일어나고 그 뒤에만 release한다` 테스트가 이를 고정한다. heartbeat/timeout/cancel/secret masking은 기존 `AppViewModel.runOnboarding()`이 `ExecuteHarnessActionUseCase`와 동일한 `startHeartbeat`/`stopHeartbeat`/`ProcessCancellationToken`/`inspectLock` 경로를 그대로 재사용하며 변경하지 않았다.

## 7. default internal SDK가 없을 때 fail-closed를 유지한 사실

`onProjectOnboardingRequested()`는 `runtimeSourceResolver.resolve(project.runtimeSource)`가 `RuntimeResolution.Resolved`가 아니면(즉 internal SDK가 없거나 invalid) `resolvedKitRoot`가 null로 남고, 이 경우 `HarnessRunViewState(lastCommand = OnboardProject, notice = harnessRunNotAllowedNotice(locale))`만 갱신하고 **`OnboardProjectUseCase`를 호출하지 않은 채 즉시 반환**한다. 신규 등록 primary flow도 동일하게, `resolvedKitRoot`가 확정된 뒤(`loadOnce(forceRead = true)` 이후)에만 `attemptOnboardingAfterRegistration`을 호출한다. 이 fail-closed 판단은 Phase 7에서 확립된 `RuntimeResolution` 계약을 그대로 재사용했고 이번 Phase에서 완화하지 않았다.

## 8. 테스트와 격리 integration 결과, 미실행 항목의 이유

### 8.1 추가한 테스트

- **core** (`OnboardProjectUseCaseTest`, 4건): enter-project→validate-ops 순서 고정, lock 미획득 시 어떤 protocol도 호출하지 않음, State 재조회가 lock 해제 전에 일어남, 두 번째 command 예외 시에도 lock이 반드시 release됨.
- **infra**:
  - `HarnessCommandEncoderTest`에 2건 추가 — `OnboardProject`가 `enter-project.ps1` 경로와 정확히 5개 인자만 만들고 `-Force`/`-RunDoctor`/`-MaterializeSubagents`/`-AgentNames`가 없음을 검증, profile 공백 시 `-Profile` 생략.
  - `PowerShellHarnessAdapterTest`에 2건 추가 — 실제 `powershell.exe`로 stub `enter-project.ps1`을 기동해 working directory가 `kitRoot`임과 exit code가 stdout 문구가 아니라 그대로 반영됨을 검증.
  - `RepositoryBridgeProbeTest`(신규 파일, 4건) — 3-file 모두 존재/일부 누락/전체 누락/디렉터리 오탐 방지를 실제 임시 디렉터리로 검증하며, 어떤 테스트도 파일을 새로 만들지 않는다는 것을 명시적으로 확인.
- **composeApp** (`AppViewModelTest`): 기존 Phase 9의 4개 등록 테스트를 Phase 10 의미로 재작성(`WorkspacePreparationOutcome`→`ProjectOnboardingOutcome`, `Bootstrap` 호출 검증→`OnboardProject`+`ValidateOps` 순서 검증)하고, "프로젝트 준비" CTA가 재등록 없이 동일 lifecycle을 실행하고 `needsProjectPreparation`을 false로 되돌리는 테스트 1건을 신규 추가했다. `newViewModel` 테스트 헬퍼와 6개의 직접 `AppViewModel(...)` 생성 지점 모두에 `onboardProject`/`bridgeProbe` fake를 배선했다(컴파일 오류로 발견, 전수 수정).

### 8.2 전체 Gradle 결과(실제 JUnit XML 집계 기준)

```powershell
.\gradlew.bat :core:test :infra:test :composeApp:jvmTest check
```

| 모듈 | tests | skipped | failures | errors |
|---|---|---|---|---|
| core | 139 | 0 | 0 | 0 |
| infra | 174 | 0 | 0 | 0 |
| composeApp | 122 | 0 | 0 | 0 |

`check`(정적 검사 포함) 전체가 성공했다.

### 8.3 격리 integration 검증(`S:\tmp`, 실제 `D:\harness-kit` 스크립트, `D:\harness-kit` 자체는 미수정)

`S:\tmp\hrns-now-phase10-fixture\{repo,workspace}`를 새로 만들어 실제 `D:\harness-kit\scripts\*.ps1`을 인코더가 만드는 것과 동일한 인자로 직접 실행했다.

1. `run-cycle.ps1 -WorkspaceRoot ... -ProjectRoot ... -KitRoot D:\harness-kit -Profile corp-default -Date 2026-07-30 -UsePythonSidecars`(Bootstrap과 동일 인자) 실행 → `workspace\2026-07-30\`에 4-file이 모두 생성됨을 확인했고, `repo\.claude\settings.local.json`/`repo\.claude\CLAUDE.md`/`repo\tools\run-cycle.ps1`는 **모두 없음**을 확인했다(`Test-Path`가 세 파일 모두 `False`). 이 결과의 `WORKFLOW_STATE.json.state.current_phase`는 `execution_completed`였다 — 프롬프트가 경고한 "신규 등록 직후 wrapper 없는 Bootstrap을 자동 호출하면 요청 입력 흐름을 막는다"는 사실을 실측으로 재확인했다.
2. 같은 fixture에 `enter-project.ps1 -ProjectRoot ... -WorkspaceRoot ... -KitRoot D:\harness-kit -Profile corp-default -Date 2026-07-30`(인코더가 만드는 것과 동일 인자, `-Force`/`-RunDoctor` 없음)을 실행 → stdout이 "Bridge settings prepared"/"Bridge run-cycle prepared"/"Project CLAUDE bridge prepared"를 보고했고, `Test-Path`로 3개 bridge 파일이 모두 `True`임을 확인했다. 이어서 `validate-ops.ps1 -WorkspaceRoot ... -KitRoot D:\harness-kit -Profile corp-default -Date 2026-07-30 -Json`을 실행해 JSON을 파싱한 결과 `overall = "ok"`, `contract_version = "1.0"`, 19개 check 중 `info`가 아닌 항목 0건이었다.
3. 검증 후 `Remove-Item -Recurse -Force S:\tmp\hrns-now-phase10-fixture`로 fixture 전체를 제거했고, 제거 후 `Test-Path`가 `False`임을 확인했다. 사용자 workspace/repository는 손대지 않았다. 유료 모델/API·Claude 호출은 전혀 하지 않았다(순수 PowerShell 스크립트 실행).

### 8.4 미실행 항목과 이유

- **실제 native GUI 클릭 QA**(등록 modal 제출·onboarding 확인 다이얼로그·"프로젝트 준비" 버튼 클릭)는 이번에도 수행하지 않았다. 이유는 §0에서 밝힌 대로 Phase 9의 native interaction QA gate가 아직 열려 있고, 이 환경은 사용자의 실제 데스크톱이라 합성 마우스/키보드 입력을 쓸 수 없기 때문이다(`project_hrns_now_gui_automation_safety` 메모리 참조). 이 공백은 자동화 테스트로 대체하지 않았고 여기 정직하게 기록한다 — PASS로 과장하지 않는다.
- `discover-project.ps1`/`promote-project-brief.ps1`은 `PROJECT_ONBOARDING.md`가 human-review 전제의 선택적 단계로 명시하고 Phase 10 프롬프트도 언급하지 않으므로 구현하지 않았다(범위 밖).

## 9. Harness 문서 drift와 HRNS-NOW 수정 범위 분리

`D:\harness-kit`을 읽기만 해서 다음 drift를 실측 재확인했다. HRNS-NOW는 이 drift를 고치기 위해 Harness source나 compatibility 정책을 변경하지 않았다.

- `scripts/SMOKE_INDEX.md`(7~9번째 줄)는 "Current smoke inventory count is 75", "automatic/offline smoke suite count is 64", "live/manual smoke count is 11"이라고 명시한다 — **현재 값**.
- `docs/ROADMAP.md`(58번째 줄)는 "the offline smoke suite (61 automatic/offline scripts of a 72-script inventory, 11 manual/live)"라고 서술한다 — `SMOKE_INDEX.md`의 현재 값(75/64/11)과 다른 **stale 서술**.
- `docs/INSTALL.md`(41번째 줄 "Do not treat legacy dual-file artifacts as default required files.", 258번째 줄 "The fallback path may require legacy artifacts to exist.")에는 과거 dual-file 전환기 서술이 현재 4-file workflow-state-primary 계약과 나란히 남아 있어 독자가 혼동할 여지가 있다. 이는 Harness 소유자가 historical section을 분리·표시해야 할 사안이며, HRNS-NOW 쪽에서 임의로 정리하거나 이 drift를 근거로 compatibility 요구사항을 낮추지 않았다.

## 10. Git 작업을 하지 않았다는 사실

이번 세션에서 `git add`/`git commit`/`git rebase`/`git reset`/`git stash`/`git clean`/`git push`/`git checkout --`을 포함한 어떤 git 변경 작업도 수행하지 않았다. 사용한 git 명령은 `git status --short`, `git branch --show-current`, `git log`, `git show`(read-only)뿐이다. 커밋은 Codex의 몫으로 남겨둔다.

## 자체 판단

**PHASE_10_STATUS: READY_FOR_CODEX_REVIEW**

Doctor/enter-project/validate-ops/run-cycle의 책임을 분리한 typed command·port·use case를 구현했고, 신규 등록 primary flow·등록만·기존 프로젝트 repair 세 흐름 모두 stdout이 아닌 5가지 concrete evidence(enter-project exit 0, validate-ops overall=ok, bridge 3종 ready, required 4-file ready, State reread Success)의 교집합으로만 완료를 판정하도록 만들었다. 등록 직후 wrapper 없는 `run-cycle`/`BootstrapDay` 자동 호출은 코드에서 완전히 제거했다(grep으로 재확인). `core`/`infra`/`composeApp` 전체 테스트(139/174/122, 실패 0)와 `check`가 통과하고, 실제 `D:\harness-kit` 스크립트를 격리 fixture에서 직접 실행해 (a) run-cycle bootstrap만으로는 bridge가 생기지 않고 (b) enter-project + validate-ops로 bridge·4-file·overall=ok가 모두 충족되는 것을 실측했다. fixture는 검증 후 제거했고 `D:\harness-kit` 자체는 어떤 방법으로도 쓰지 않았다.

다만 Phase 9에서 열린 native GUI 클릭 QA gate는 이번에도 닫지 못했다 — 이 사실을 숨기지 않는다. 사용자가 명시적으로 Phase 10을 지시했기 때문에 진행했을 뿐, Phase 9 gate 자체가 해소된 것은 아니다.

```text
PHASE_10_STATUS: READY_FOR_CODEX_REVIEW
NEXT_ALLOWED_PHASE: Codex independent verification
```
## Codex 독립 검증·보정 — 2026-07-30

### 검증 기준

- 시작 HEAD: `9f66f5a docs: Phase 10 프로젝트 온보딩 작업 정의`
- 검증 브랜치: `harness-dev`
- 설계 기준: `doc/hrns_now_claude_plan.md`, `doc/hrns_now_design_pattern.md`, Phase 10 작업 프롬프트
- Harness는 `D:\harness-kit`의 문서·스크립트를 읽기/실행으로만 확인했다. 수정·복사·백업은 하지 않았다.
- `doc/QA_captures/`, `doc/hrns_now_packaging_plan.md`, `doc/user_workflow_qa_notes.md`는 사용자 소유 untracked 자료로 보존했다.

### 독립 계약 재현

`S:\tmp\hrns-now-codex-phase10-verify-20260730`에 격리 repository/workspace 두 쌍을 만들고 실제 Harness 스크립트를 실행한 뒤 즉시 제거했다.

- `run-cycle.ps1 -UsePythonSidecars`만 실행한 쌍: required daily 4-file은 모두 생성됐지만 repository bridge 3-file은 모두 생성되지 않았다.
- `enter-project.ps1` 뒤 `validate-ops.ps1 -Json`을 실행한 쌍: bridge 3-file과 required daily 4-file이 모두 존재했고, `overall=ok`, `contract_version=1.0`, State는 `request_intake_pending`이었다.
- 두 repository 아래에는 daily 4-file이 생성되지 않았다.
- `PROJECT_ONBOARDING.md`의 project discovery는 온보딩 뒤의 선택적·사람 검토 단계이며, clean onboarding의 필수 조건(bridge, 외부 workspace, 4-file, State parse, validate-ops 통과)과 충돌하지 않음을 확인했다.
- fixture 총 97개 항목은 검증 후 제거됐고, 사용자 workspace/repository와 Harness Kit은 변경하지 않았다.

### 발견 결함과 Codex 보정

1. `core/.../OnboardProjectUseCase.kt`
   - `onBeforeLockRelease` 콜백이 예외 또는 취소되면 이어지는 `processLock.release`가 실행되지 않아 lock이 남을 수 있었다.
   - 콜백을 중첩 `try/finally`로 감싸 lock 해제를 무조건 보장했고, 콜백 예외 회귀 테스트를 추가했다.

2. `composeApp/.../AppViewModel.kt`
   - 기존 프로젝트의 `프로젝트 준비` CTA는 runtime root를 해석하기 전에 빠르게 연속 클릭하면 두 coroutine이 시작될 여지가 있었다.
   - `projectOnboardingJob` 단일 실행 가드를 추가했고, 빠른 두 클릭이 `OnboardProject → ValidateOps` 한 쌍만 실행함을 ViewModel 테스트로 고정했다.

기존 `Doctor`의 read-only 성격, `enter-project`의 정확한 typed argument 목록, 등록 직후 `BootstrapDay` 미실행, stdout 단독 성공 판정 금지, lock 보유 중 validation/probe/State 재조회 순서는 유지됐다.

### 검증 결과

| 구분 | 명령/방법 | 결과 |
|---|---|---|
| Targeted core | `:core:test --tests OnboardProjectUseCaseTest` | PASS |
| Targeted UI | `:composeApp:jvmTest --tests AppViewModelTest --fail-fast` | PASS |
| Targeted infra | `:infra:test --tests HarnessCommandEncoderTest --tests RepositoryBridgeProbeTest` | PASS |
| Module + full | `:core:test :infra:test :composeApp:jvmTest check` | PASS |
| 테스트 XML 집계 | core 140 / infra 174 / composeApp 122, failures 0 | PASS |
| Live Harness fixture | 실제 `run-cycle`, `enter-project`, `validate-ops -Json` | PASS |
| Native GUI 클릭 QA | 사용자 직접 확인 필요 | 미실행 |

### 판정

- Phase 10 구현 범위 Verdict: `PASS_WITH_FIXES`
- 남은 Gate: 새 빌드의 native 프로젝트 등록/온보딩 확인 다이얼로그와 `프로젝트 준비` CTA를 실제 클릭하는 사용자 QA
- `PHASE_10_STATUS: PASS_WITH_FIXES`
- `NEXT_ALLOWED_PHASE: Phase 10 native onboarding interaction QA gate`
