# HRNS-NOW ↔ Live Harness Kit 전수 호환성 감사 보고서

## 1. 최종 Verdict와 요약

```
HRNS_HARNESS_LIVE_COMPATIBILITY_AUDIT: COMPATIBLE_WITH_NONBLOCKING_GAPS
```

이 판정은 두 차례 remediation의 누적 결과다. 1차 remediation(기준선 2026-08-06, §2-§10 원문)에서 BLOCKER 2건·MEDIUM 1건·HIGH 1건·DOC 1건을 닫았지만, 그 과정에서 `claude/hooks/pre_handoff_validate.ps1`이 `run-cycle.ps1` 경유 시에만 크래시하는 신규 HIGH 결함을 발견하고도 근본 원인을 특정하지 못한 채 별도 후속 조사로 분리하고 `COMPATIBLE`로 판정했다(구 §5 finding, 구 §8). 이번 2차 remediation(`doc/claude_prompts/harness-kit-closure-validation-remediation.md`, 이 갱신 시점 2026-08-07)은 정확히 그 후속 조사다. 네이티브 재현(`run-cycle.ps1 -ValidateForClosure`)에서 완전히 유효한 fixture로도 `"The property 'Count' cannot be found on this object"` 크래시가 100% 재발했고, closure가 사실상 항상 실패로 귀결되면서도 부모 프로세스는 `closure.validated=false`에도 불구하고 exit 0로 종료하는 별도 결함까지 발견되어, 2차 remediation 착수 시점의 유효 verdict는 최소 `INCOMPATIBLE`로 재하향했다(§6.6).

근본 원인을 이번에 확정했다. `scripts/lib/harness-context-diet-mode.ps1`이 파일 최상단(dot-source되는 스크립트 자신이 아니라 **호출자**의 스코프)에서 `Set-StrictMode -Version Latest`를 실행하고 있었다 — PowerShell의 dot-source(`. <script>`)는 대상 스크립트의 최상위 statement를 호출자 자신의 스코프에서 그대로 실행하므로, 이 lib를 dot-source하는 모든 호출자(`run-cycle.ps1` 포함, 자신은 StrictMode를 설정하지 않음)가 의도치 않게 StrictMode Latest를 상속받았다. `run-cycle.ps1`이 `& $preHandoffValidateScript`로 native 실행하는 `pre_handoff_validate.ps1` 역시 자신의 StrictMode를 설정하지 않으므로, `&`가 만든 새 스크립트 스코프가 호출자로부터 이 상속된 StrictMode를 그대로 물려받았다. `Get-StringArray`가 `@()`로 배열을 만들어 반환해도 단일 원소 배열은 `$x = Get-Foo` 단순 캡처에서 PowerShell pipeline이 무조건 scalar로 unwrap하며(StrictMode 여부와 무관), 이 scalar의 `.Count` 접근은 StrictMode 하에서만 크래시한다 — 이것이 단독 실행/최소 재현 스크립트에서는 재현되지 않고 오직 `run-cycle.ps1` 경유 시에만 재현된다는 1차 remediation의 관찰(구 §5, 구 §6.2)을 정확히 설명한다.

수정은 두 계층으로 했다. (1) 근본 원인: `harness-context-diet-mode.ps1`의 `Set-StrictMode -Version Latest`를 파일 최상단에서 `Get-HarnessContextDietMode` 함수 내부로 옮겨 dot-source 호출자 스코프 오염을 원천 제거했다(함수 자신의 동작은 격리 재현으로 불변임을 확인, §6.6). (2) 방어적 이중화: `pre_handoff_validate.ps1`의 `Get-StringArray` 호출부 2곳을 `@(Get-StringArray ...)`로 감싸 StrictMode 여부와 무관하게 호출측에서 배열임을 보장했다 — pipeline unwrap 자체는 StrictMode와 독립적이므로 근본 원인 수정만으로는 이를 대체할 수 없다. `closure.validated=false`에도 부모가 exit 0로 끝나던 결함도 함께 닫았다: closure validation 실패 시 State/run_log 영속화 이후 명시적으로 `exit 1`하도록 배선했다(이전엔 우연한 `$LASTEXITCODE` 누출에 의존). 정상 closure 성공 경로도 순수 production 산출물(fixture 조작 없음)로 PowerShell/Kotlin 양쪽에서 end-to-end 재증명했다.

사용자 요청에 따라 `doctor.ps1`의 install-completeness 검사도 함께 보강했다: 기존 검사가 `run-cycle.ps1`의 실제 필수 의존성 13개 중 7개를 확인하지 않던 gap을 발견해, 단일 정본 인벤토리(`scripts/lib/harness-runtime-dependency-inventory.ps1`)를 신설하고 `doctor.ps1`이 이를 소비하도록 배선했다(필수=error severity, optional=warn severity). 정본과 `run-cycle.ps1` 실제 의존성을 양방향 자동 대조하는 회귀 smoke도 추가해 향후 드리프트를 자동 검출한다(§6.7).

이번 2차 remediation에서 재현된 HIGH는 완전히 닫혔고 새 BLOCKER/HIGH는 없다. 현재 분류는 **BLOCKER 0 / HIGH 0 / MEDIUM 0 / UNVERIFIED 1 / DOC 0**다. `packaged-app-console-window-flash`는 1차 remediation과 동일하게 실제 packaged 재현을 시도했으나 관찰하지 못해 `UNVERIFIED`로 유지한다(§6.4) — 이 한 항목만으로 verdict를 낮추지 않는다. `Invoke-RunCycleWrapper`(native-only, python 대조군 없음) 자체의 exit code 미확인 문제는 이번에도 인벤토리만 하고 변경하지 않았다(§6.2 #5, §8) — ops-validation 실패 시 wrapper 호출 자체를 막는 상위 gate(1차 remediation에서 이미 추가)가 이 경로를 이미 보호하므로 compatibility 위반은 아니지만, 근거 부족으로 수정을 보류한 열린 위험으로 계속 공개한다. 두 항목 모두 HRNS-NOW가 Harness 산출물을 오독하게 만드는 compatibility 위반이 아니므로 `COMPATIBLE`이 아닌 `COMPATIBLE_WITH_NONBLOCKING_GAPS`로 명시해 verdict와의 관계를 숨기지 않는다.

## 2. Remediation 기준선

### 2.1 1차 remediation(원문, 기준선 2026-08-06)

- HRNS-NOW: `S:\dev\project\hrns_now`, branch `harness-dev`. remediation 시작 시점에 병행 Codex 세션이 `doc/`를 계속 재구성 중이었다(`doc/README.md`→`doc/documentation_guide.md`, `doc/phase_reports/README.md`→`doc/phase_reports/current_validation_reports.md` 등). 이 remediation은 그 변경을 건드리지 않았고 `doc/QA_captures/`, `doc/hrns_now_packaging_plan.md`, `doc/user_workflow_qa_notes.md`도 손대지 않았다.
- Live Harness Kit: `D:\harness-kit`(`.git` 없음). `kit-version.json` 불변: `kit_version=2026.07.23`.
- git 조작 없음(`git add`/`commit`/`push`/`reset`/`restore` 전부 미실행) — 모든 검증은 `S:\tmp\hrns-now-harness-remediation-20260806-132933\` 아래 격리 scratch에서 수행했고, 종료 시점에 완전히 제거했다(§10).
- `D:\harness-workspaces`(실 사용자 워크스페이스)와 `%APPDATA%\hrns-now\projects.json`은 이번 remediation에서 전혀 읽거나 쓰지 않았다.

### 2.2 2차 remediation(closure-validation, 기준선 2026-08-07)

- 착수 사유: 1차 remediation이 발견하고도 근본 원인 미확정으로 남긴 HIGH(`pre-handoff-validate-run-cycle-context-strictmode-count-crash`, 구 §5)의 후속 조사. 지시서는 `doc/claude_prompts/harness-kit-closure-validation-remediation.md`.
- 착수 시점 재현: 순수 native `run-cycle.ps1 -ValidateForClosure`가 완전히 유효한 fixture에서도 100% 크래시(`Get-StringArray` 반환값의 `.Count` 접근에서 StrictMode `PropertyNotFoundException`), 게다가 부모 프로세스가 `closure.validated=false`에도 exit 0로 종료 — 이 두 가지를 이유로 착수 시점 유효 verdict는 최소 `INCOMPATIBLE`로 재하향했다(코드 수정 착수 전).
- HRNS-NOW: `S:\dev\project\hrns_now`, git 조작 없음(`git add`/`commit`/`push`/`reset`/`restore`/`checkout`/`stash`/`clean` 전부 미실행) — 이 2차 remediation 종료 시점에도 working tree는 편집된 production/test/doc 파일만 반영하고 별도 커밋을 만들지 않았다(§10).
- Live Harness Kit: `D:\harness-kit`(`.git` 없음), `kit_version=2026.07.23` 불변.
- 모든 scratch fixture는 `S:\tmp\hrns-now-closure-remediation-20260807-093930\` 아래에서만 생성했고, 종료 시점에 그 정확한 경로만 검증 후 제거했다(§10).
- `D:\harness-workspaces`, `%APPDATA%\hrns-now\projects.json`, live Claude/Ollama/외부 LLM 호출은 이번에도 전혀 사용하지 않았다.

## 3. 변경 파일 목록

### 3.1 Harness Kit (`D:\harness-kit`) — 1차 remediation

| 파일 | 변경 내용 |
|---|---|
| `scripts/init-workspace.ps1` | `Ensure-WorkflowStateBaseline`에 top-level `required_next_action` 추가, `Ensure-WorkQueueBaseline`에 `queue.blocked_reason` 추가(기존 불완전 State에 idempotent backfill) |
| `templates/workspace/WORKFLOW_STATE.json.tpl` | `required_next_action`(top-level), `queue.blocked_reason` 정적 필드 추가 |
| `scripts/lib/state-surface.ps1` | `New-HarnessWorkflowStateModel`에 `required_next_action` 추가; 신규 `Test-HarnessStateSurfacePropertyPresent`(key 존재 여부, blank와 구분); 신규 `Get-HarnessWorkflowStateGuaranteedEnvelopeFindings`(top-level/state/queue guaranteed envelope 단일 정본 validator); `Get-HarnessWorkflowStateCompatibilityFindings`가 위 신규 함수를 호출하도록 배선 |
| `scripts/validate-ops.ps1` | guaranteed envelope 체크를 error severity + `$hasFailure=$true`로 반영; native `& $validateOpsScript` 호출에 `-KitRoot`/`-Profile` 추가 + `Assert-RunCycleNativeChildSucceeded` 동등 exit code 체크(하단 run-cycle.ps1 helper 참고); `state.closure`/`state.PSObject.Properties['closure']` StrictMode 크래시 2건 수정(guaranteed-envelope 체크가 malformed state를 감지한 뒤 이 unguarded access가 raw crash로 뒤덮는 문제) |
| `docs/STATE_MODEL.md` | §6.3을 "항상 존재(guaranteed)" 필드와 "적용 시에만 존재(optional)" 필드 2계층으로 재구성 |
| `scripts/run-cycle.ps1` | 신규 공용 helper `Assert-RunCycleNativeChildSucceeded`; Doctor/Validate-Ops/pre-handoff closure validator native branch에 배선(Validate-Ops/closure validator는 `-KitRoot`/`-Profile` 누락도 함께 수정); ops-validation 실패 시 `RunExecution(code\|doc\|auto)` wrapper 호출과 lineage snapshot 기록을 차단하는 gate 추가(reload 이후 재적용 로직 포함 — 최초 구현이 reload에 의해 로그가 유실되는 버그를 자체 발견·수정) |
| `scripts/SMOKE_INDEX.md` | 신규 smoke 5개 등록, 카운트 87→92(전체)/76→81(offline) 갱신, §6.3 bug-regression coverage 추가 |
| `scripts/smoke/smoke-workflow-state-fresh-ui-envelope.ps1` | 신규(36 cases) |
| `scripts/smoke/smoke-validate-ops-guaranteed-envelope.ps1` | 신규(32 cases, table-driven) |
| `scripts/smoke/smoke-run-cycle-native-child-exit-propagation.ps1` | 신규(6 cases) |
| `scripts/smoke/smoke-run-cycle-execution-fail-closed-sentinel.ps1` | 신규(10 cases) — execution_wrapper/queue mismatch 회귀 시나리오 포함 |
| `scripts/smoke/smoke-run-cycle-closure-failure-propagation.ps1` | 신규(9 cases) |
| `scripts/smoke/smoke-plan-lineage-ledger.ps1` | fixture에 guaranteed envelope 필드 추가; `-SkipOpsValidation`을 조건부 파라미터화(execution-lineage 케이스는 실제 ops-validation 통과가 필요해짐) |
| `scripts/smoke/smoke-planning-once-guard.ps1` | fixture에 guaranteed envelope 필드 추가 |
| `scripts/smoke/smoke-planning-replan-lane.ps1` | fixture에 guaranteed envelope 필드 추가 |
| `scripts/smoke/smoke-deferred-materialization-lifecycle.ps1` | fixture에 guaranteed envelope 필드 추가 |
| `scripts/smoke/smoke-secondary-llm-docs-scan.ps1` | `$SmokeTotal` 기본값 87→92 |
| `scripts/lib/secondary-llm/secondary-llm-docs-calibration.ps1` | `Get-SecondaryLlmDocsMismatchExpectedSmokeInventory`의 하드코딩된 기준값 87/76→92/81 |
| `docs/OPERATING_GUIDE.md` | §2.1/§2.2/§9.1 예시에서 `-UsePythonSidecars`를 필수처럼 보이던 것을 optional parity 플래그로 명확화(§1의 기존 철학 문구와 정렬) |
| `docs/HARNESS_KIT_MAP.en.md`, `docs/HARNESS_KIT_MAP.ko.md` | smoke 카운트 87/76→92/81, Validate-Ops 19→20 checks 갱신 |
| `docs/ROADMAP.md` | smoke 카운트 참조 1건 갱신(§58) — §236의 과거형 "Completed and verified" 회고 문구는 historical로 남김 |

위 fixture-필드 보강 4개 smoke와 두 문서 카운트 정합 파일들은 remediation의 직접 대상이 아니었지만, P0-1/P0-2 수정이 정당하게 더 엄격해진 결과 fail하게 된 **기존** smoke/문서였다 — 전체 offline suite를 실제로 끝까지 실행해서만 발견했다(§4).

### 3.1b Harness Kit (`D:\harness-kit`) — 2차 remediation(closure-validation)

| 파일 | 변경 내용 |
|---|---|
| `scripts/lib/harness-context-diet-mode.ps1` | 근본 원인 수정 — `Set-StrictMode -Version Latest`를 파일 최상단(dot-source 호출자 스코프 오염원)에서 `Get-HarnessContextDietMode` 함수 내부로 이동. dot-source 스코프 누출 메커니즘을 설명하는 헤더 주석 추가 |
| `claude/hooks/pre_handoff_validate.ps1` | 방어적 이중화 — `Get-StringArray` 호출부 2곳(`$loadedDailyRequiredArtifacts`/`$loadedPersistentRequiredArtifacts`)을 `@(Get-StringArray ...)`로 감싸 pipeline 단일원소 unwrap에 대해 StrictMode 무관하게 항상 배열을 보장. `Get-StringArray` 함수 본문 자체는 변경 없음(전체 호출부 2곳 확인) |
| `scripts/run-cycle.ps1` | closure validation 실패 시 부모가 exit 0로 끝나던 결함 수정 — `$runCycleClosureValidationFailed` 플래그를 closure-outcome-check 실패 분기에서 설정하고, State/run_log 영속화 이후 스크립트 최종 단계에서 `exit 1` |
| `scripts/lib/harness-runtime-dependency-inventory.ps1` | 신규 — `run-cycle.ps1`의 필수/optional 런타임 의존성 12+2개를 단일 정본으로 노출(`Get-HarnessRunCycleRequiredScripts`/`Get-HarnessRunCycleOptionalScripts`) |
| `scripts/doctor.ps1` | install-completeness 검사를 위 정본으로 확장 — 필수 12개는 error severity, optional 2개는 warn severity. 체크 수 167→193 |
| `scripts/smoke/smoke-run-cycle-strictmode-scalar-array-contract.ps1` | 신규(12 cases) — `Get-StringArray` 0/1/다중 원소 계약, dot-source 스코프 누출 회귀(격리 nested process), `Set-StrictMode`가 `Get-HarnessContextDietMode` 내부에만 있음을 AST로 검증 |
| `scripts/smoke/smoke-run-cycle-closure-success-contract.ps1` | 신규(13 cases) — 순수 production 산출물 positive closure 성공 경로(콘솔 메시지/exit 0/State 필드/run_log) |
| `scripts/smoke/smoke-run-cycle-closure-failure-propagation.ps1` | 강화 — `closure_failure_parent_exit_code_is_nonzero` 단언 추가(9→10 cases) |
| `scripts/smoke/smoke-run-cycle-required-dependency-inventory.ps1` | 신규(27 cases) — 정본 인벤토리와 `run-cycle.ps1` 실제 의존성 참조를 양방향 자동 대조 + doctor.ps1이 정본을 실제로 소비하는지 확인 |
| `scripts/SMOKE_INDEX.md` | 신규 smoke 4개(3 신규 + 1 강화) 반영, 카운트 92→95(전체)/81→84(offline) 갱신, §6.3 bug-regression coverage 3건 추가 |
| `scripts/smoke/smoke-secondary-llm-docs-scan.ps1` | `$SmokeTotal` 기본값 92→95 |
| `scripts/lib/secondary-llm/secondary-llm-docs-calibration.ps1` | `Get-SecondaryLlmDocsMismatchExpectedSmokeInventory` 기준값 92/81→95/84 |
| `docs/HARNESS_KIT_MAP.en.md`, `docs/HARNESS_KIT_MAP.ko.md`, `docs/ROADMAP.md` | smoke 카운트 95/84 갱신 + 발견된 기존 stale 수치("Doctor 167 checks", ROADMAP.md의 "Validate-Ops 19 checks") 실측값(Doctor 193, Validate-Ops 20)으로 정정 |

### 3.2 HRNS-NOW (`S:\dev\project\hrns_now`) — 1차 remediation

| 파일 | 변경 내용 |
|---|---|
| `infra/src/main/kotlin/io/hrns_now/infra/runtime/DefaultKitRuntimeResolver.kt` | `REQUIRED_ENTRYPOINTS`에 `scripts/enter-project.ps1` 추가 + `init-workspace.ps1`을 의도적으로 제외한 근거를 KDoc에 명시 |
| `infra/src/test/kotlin/io/hrns_now/infra/runtime/DefaultKitRuntimeResolverTest.kt` | fixture helper에 `enter-project.ps1` 추가 + 단독 부재 시 `MissingEntrypoint`를 확인하는 신규 테스트 |
| `composeApp/.../viewmodel/ViewModelStrings.kt` | `RuntimeIssue.MissingEntrypoint` 메시지(KO+EN)에 `enter-project.ps1` 추가 |
| `composeApp/.../mapper/CockpitProjectionAssembler.kt` | 위와 동일 메시지 갱신(KO+EN) |
| `core/.../usecase/RegisterProjectUseCase.kt` | 위와 동일 메시지 갱신(KO) |
| `infra/src/test/kotlin/io/hrns_now/infra/serialization/HarnessProductionContractTest.kt` | 신규 — production-to-production contract test(offline fixture 3건 + live-Kit opt-in 1건) |
| `infra/src/test/resources/fixtures/workflow-state-fresh-onboarding.json` | 신규 — 수정 후 template 실제 형태를 반영한 fixture |

### 3.2b HRNS-NOW (`S:\dev\project\hrns_now`) — 2차 remediation(closure-validation)

| 파일 | 변경 내용 |
|---|---|
| `infra/src/test/kotlin/io/hrns_now/infra/serialization/HarnessProductionContractTest.kt` | 5번째 테스트 추가(`live Kit가 지정되면 정상 closure 성공 경로를 실제 어댑터로 읽어 확인한다`) — 기존 4번째 테스트와 동일한 opt-in `HRNS_LIVE_HARNESS_KIT_ROOT` 게이팅. 실제 `enter-project.ps1` + `run-cycle.ps1 -SkipDoctor -ValidateForClosure`를 실행하고 실제 `JsonWorkflowStateAdapter`로 `closureValidated`/`cleanHandoff`/`closure.validated`/`closure.isCleanHandoff` 4개 필드가 모두 `true`임을 확인 |

## 4. 실행한 명령과 결과

### 4.1 1차 remediation

| 명령 | 결과 |
|---|---|
| Harness targeted: `smoke-clean-root-onboarding.ps1` | 35/35 PASS |
| Harness targeted: `smoke-doctor-json-contract.ps1` | 13/13 PASS |
| Harness targeted: `smoke-validate-ops-json-contract.ps1` | 15/15 PASS(수정 전 5/15 FAIL — 자체 fixture가 guaranteed envelope 누락, 수정 후 재검증 PASS) |
| 신규 smoke 5개 개별 실행 | `smoke-workflow-state-fresh-ui-envelope.ps1` 36/36, `smoke-validate-ops-guaranteed-envelope.ps1` 32/32, `smoke-run-cycle-native-child-exit-propagation.ps1` 6/6, `smoke-run-cycle-execution-fail-closed-sentinel.ps1` 10/10, `smoke-run-cycle-closure-failure-propagation.ps1` 9/9 — 전부 PASS |
| `SMOKE_INDEX.md` §6.4 공식 execution script(live 파일에서 직접 추출·실행, 하드코딩 없음) | **81/81 PASS**, live Claude/Ollama 호출 0 |
| HRNS-NOW `gradlew.bat check --rerun-tasks --no-daemon` | BUILD SUCCESSFUL. XML 재합산: **442 tests, 0 failures, 0 errors, 1 skipped**(신규 live-mode 테스트가 opt-in env var 미설정으로 정상 skip) |
| production-to-production: `enter-project.ps1`(격리 scratch, `D:\harness-kit` 대상) | exit 0, bridge 3/3, daily 4/4 |
| 위 산출물을 실제 `JsonWorkflowStateAdapter.read()`(자체 JVM 검증기, 실제 컴파일된 core/infra 클래스)로 확인 | **`Success`**(`requiredNextAction=null` — blank 필드의 정상 매핑 확인) |
| 위 fixture `doctor.ps1 -Json` / `validate-ops.ps1 -Json` | `overall=ok`(167 checks) / `overall=ok`(20 checks) |
| `required_next_action` 제거 fixture | `validate-ops.ps1 -Json` exit 1 + `overall=fail`; JVM 검증기 `Malformed("required_next_action is missing")` |
| ops-mismatch fixture(`TODAY_STRATEGY.md`에 BOM 주입 → 실제 ops-validation 실패) + `run-cycle.ps1 -RunExecutionWrapper doc` | `run_log`에 `execution_lineage_snapshot_recorded` 없음(sentinel 미생성), `run_cycle_execution_blocked_by_ops_validation` 있음 |
| closure-failure fixture(동일 BOM 주입) + `run-cycle.ps1 -ValidateForClosure` | `closure.validated=false`, `closure.is_clean_handoff=false`, `run_log`에 `pre_handoff_validated`/`run_cycle_closure_validated` 없음, `run_cycle_returned_open_after_failed_closure_validation` 있음 |
| 한글+공백 경로(`repo 테스트`/`workspace 테스트`) `enter-project.ps1` + JVM 검증기 | exit 0, `Success` |
| console-window-flash 재현 시도 | §6.4 참고 — 관찰 안 됨, `UNVERIFIED` 유지 |

### 4.2 2차 remediation(closure-validation)

| 명령 | 결과 |
|---|---|
| Targeted: `smoke-run-cycle-strictmode-scalar-array-contract.ps1` | 12/12 PASS |
| Targeted: `smoke-run-cycle-closure-success-contract.ps1` | 13/13 PASS |
| Targeted: `smoke-run-cycle-closure-failure-propagation.ps1`(강화판) | 10/10 PASS |
| Targeted: `smoke-run-cycle-native-child-exit-propagation.ps1` | 6/6 PASS |
| Targeted: `smoke-workflow-state-fresh-ui-envelope.ps1` | 36/36 PASS |
| Targeted: `smoke-validate-ops-guaranteed-envelope.ps1` | 32/32 PASS |
| Targeted: `smoke-run-cycle-required-dependency-inventory.ps1`(신규) | 27/27 PASS |
| `SMOKE_INDEX.md` §3.2 공식 automatic/offline execution array(live 파일에서 정규식으로 직접 추출·실행, 하드코딩 없음) | **84/84 PASS**, live Claude/Ollama 호출 0 |
| HRNS-NOW `gradlew.bat check --rerun-tasks --no-daemon` | BUILD SUCCESSFUL(4m 24s). XML 재합산(UTF-8 명시 재확인): **443 tests, 0 failures, 0 errors, 2 skipped**(신규 5번째 테스트 포함 opt-in live 테스트 2건이 env var 미설정으로 정상 skip) |
| 정상 native closure: 순수 `enter-project.ps1` fixture + `run-cycle.ps1 -SkipDoctor -ValidateForClosure` | exit 0, `closure.validated=true`/`closure.is_clean_handoff=true`/top-level `closureValidated=true`/`cleanHandoff=true`, `run_log`에 `pre_handoff_validated`/`run_cycle_closure_validated` 존재, 실패 이벤트 부재(13개 조건 전부 충족) |
| 위 정상 산출물을 `HarnessProductionContractTest`의 신규 5번째 테스트(실제 `JsonWorkflowStateAdapter`, opt-in live)로 확인 | BUILD SUCCESSFUL, 5 tests / 0 failures / 0 errors / 0 skipped(env var 설정 시) |
| 실패 native closure: `TODAY_STRATEGY.md`를 완전히 빈 파일로 만든 fixture(init-workspace의 auto-heal을 우회하기 위해 삭제가 아닌 empty-file 사용) + `run-cycle.ps1 -ValidateForClosure` | **부모 exit 1**(P0-3 신규 단언), `closure.validated=false`, `run_log`에 `run_cycle_returned_open_after_failed_closure_validation` 존재 |
| Doctor 필수 의존성 파일 1개(`scripts/lib/code/code-workqueue.ps1`) 임시 제거 fixture | `doctor.ps1 -Json` → `overall=fail`, exit 1, error severity 체크 발생(복원 후 재확인 `overall=ok`, 193 checks) |
| StrictMode dot-source 누출 회귀: `harness-context-diet-mode.ps1` 수정을 되돌린 상태로 `smoke-run-cycle-strictmode-scalar-array-contract.ps1` 재실행 | 실제로 FAIL(스코프 누출 검증 케이스에서 실패) — 수정 복원 후 재실행 12/12 PASS로 복귀 확인 |
| HRNS `git status --short` / `git diff --check` | 편집된 production/test/doc 파일만 존재(신규 커밋 없음, 이번 remediation 시작 전 이미 존재하던 병행 Codex 변경도 그대로), `git diff --check` exit 0(LF/CRLF 경고만, 오류 없음) |
| `D:\harness-kit\scratch`/`logs` 잔여물 확인 | `scratch`에 이번 remediation 이전(2026-06-12/2026-07-13) 생성된 무관 디렉터리 2개만 존재, 신규 잔여 없음; `logs`에 2026-08-07 이후 생성 파일 없음 |
| `D:\harness-workspaces`, `%APPDATA%\hrns-now\projects.json` 무변경 확인 | 전자는 이번 remediation 세션 시작 이후 수정된 파일 없음, 후자 mtime은 이번 remediation 시작 이전(2026-07-30) 그대로 |

민감 정보: raw session ID/token/secret/전체 로그는 포함하지 않았다. scratch 경로 문자열은 검증 재현성을 위해 그대로 남겼다(비밀 정보 아님, 로컬 임시 경로).

## 5. Findings 최종 상태

```
ID: workflow-state-ui-envelope-writer-drift
Severity: BLOCKER → FIXED
Fix: templates/workspace/WORKFLOW_STATE.json.tpl, scripts/init-workspace.ps1(Ensure-WorkflowStateBaseline/Ensure-WorkQueueBaseline), scripts/lib/state-surface.ps1(New-HarnessWorkflowStateModel)에 required_next_action/queue.blocked_reason을 blank-string 기본값으로 일관 추가. WorkflowStateMapper.kt의 requiredNextAction.ifBlank{null} 처리와 next_action/execution_wrapper/authorized_target_file 등 형제 필드의 기존 관례를 근거로 "blank가 유효한 steady-state 값"이라는 계약을 확정했다(legacy cutover.required_operator_action 문구를 그대로 복사하지 않음).
Dynamic evidence(before/after): 수정 전 fresh enter-project.ps1 직후 JVM 검증기 → Malformed("required_next_action is missing"). 수정 후 동일 절차 → Success(requiredNextAction=null). 기존 불완전 State(필드 수동 제거)에 enter-project.ps1 -Force 재실행 → 필드 backfill 확인.
Regression test: smoke-workflow-state-fresh-ui-envelope.ps1(36 cases, fresh envelope + idempotent backfill + Get-HarnessWorkflowStateGuaranteedEnvelopeFindings 교차검증), HarnessProductionContractTest.kt(offline fixture 3건 + live-Kit opt-in).

ID: run-cycle-native-child-exitcode-and-fail-open-control-flow
Severity: BLOCKER → FIXED
Fix: 신규 Assert-RunCycleNativeChildSucceeded 공용 helper를 Doctor/Validate-Ops/pre-handoff closure validator의 native branch(각각 & $doctorScript / & $validateOpsScript / & $preHandoffValidateScript 호출 직후)에 배선해 $LASTEXITCODE를 Python sidecar branch와 대칭으로 확인·throw한다. Validate-Ops와 closure validator 호출에는 누락돼 있던 -KitRoot/-Profile도 추가했다(validate-ops.ps1의 프로필-종속 아티팩트 목록이 실제 프로필을 반영하도록). Doctor/closure validator는 기존 catch/downstream 로직이 이미 fail-closed였으므로 exit code 감지만으로 충분했다. Ops-validation은 추가로 별도 gate가 필요했다 — ops_validation.passed=false일 때 RunExecutionWrapper(doc/code) 실행 직전에 dispatch를 막고 lineage snapshot 기록도 건너뛰게 했다. 최초 구현에서 이 gate의 상태 mutation이 "Reloading runtime state after wrapper gate" 단계의 무조건 재로드에 의해 유실되는 버그를 스스로 발견해 로그 재적용 로직으로 수정했다(execution_completed/stop_reason은 별도의 기존 queue-projection sync 메커니즘이 정본이므로 건드리지 않기로 결정). Planning/Replan은 명시적으로 이 gate에서 제외했다 — daily 산출물을 만들어 ops-validation을 회복시키는 유일한 경로이므로, 막으면 영구 교착이 발생한다.
Dynamic evidence(before/after): Doctor — BOM 주입 bridge 파일로 native doctor.ps1 exit 1 유도 → run-cycle이 ops-validation 이전에 정지, "doctor.ps1 failed with exit code 1" 확인(수정 전에는 무시하고 계속 진행). Ops — BOM 주입 TODAY_STRATEGY.md로 native validate-ops.ps1 exit 1 유도 → ops_validation.passed=false + doc wrapper 미호출 + execution_lineage_snapshot_recorded 부재(수정 전에는 ops_validation.passed=true로 왜곡되고 wrapper가 그대로 호출됨). Closure — 동일 BOM 주입 → closure.validated=false/is_clean_handoff=false + false-success 로그 이벤트 전무.
Regression test: smoke-run-cycle-native-child-exit-propagation.ps1(6), smoke-run-cycle-execution-fail-closed-sentinel.ps1(10, execution_wrapper/queue mismatch 시나리오 포함), smoke-run-cycle-closure-failure-propagation.ps1(9).

ID: execution-wrapper-queue-mismatch-regression-scenario
Severity: COVERED_BY_BLOCKER → FIXED(위 BLOCKER의 acceptance scenario로 해결)
Regression test: smoke-run-cycle-execution-fail-closed-sentinel.ps1이 이 시나리오를 명시적으로 요구된 회귀 fixture로 사용한다(별도 smoke를 만들지 않음).

ID: runtime-resolver-missing-enter-project-check
Severity: MEDIUM → FIXED
Fix: DefaultKitRuntimeResolver.REQUIRED_ENTRYPOINTS에 scripts/enter-project.ps1 추가. scripts/init-workspace.ps1은 의도적으로 제외했다 — HRNS-NOW가 직접 호출하는 진입점이 아니라 enter-project.ps1/run-cycle.ps1이 내부 위임하는 스크립트이고, Kit의 install-completeness 전수 검사(60여 파일)는 이미 doctor.ps1 자신의 책임이기 때문이다(resolver는 doctor.ps1을 대신하는 전수 검사기가 아니라 "이 root가 Kit처럼 보이는가"만 확인하는 얕은 sniff test로 유지).
Dynamic evidence: DefaultKitRuntimeResolverTest.kt 신규 케이스로 enter-project.ps1만 제거한 fixture가 Invalid(MissingEntrypoint)를 반환함을 확인. gradlew compileKotlin/test 통과.
Regression test: DefaultKitRuntimeResolverTest.kt 신규 케이스 1건.

ID: validate-ops-does-not-check-ui-consumption-envelope
Severity: HIGH → FIXED
Fix: Get-HarnessWorkflowStateGuaranteedEnvelopeFindings(단일 정본)를 신설해 Get-HarnessWorkflowStateCompatibilityFindings에 배선하고, validate-ops.ps1이 그 결과를 error severity + $hasFailure=$true로 반영하도록 수정(최초 구현에서 $hasFailure 대입 누락을 스스로 발견해 수정).
Dynamic evidence(before/after): required_next_action 제거 fixture — 수정 전 overall=ok(19/19 info, 관련 언급 0). 수정 후 overall=fail, exit 1, "workflow_state_guaranteed_envelope_missing" error check 발생. 이 wiring 과정에서 validate-ops.ps1 자체의 미가드 $state.closure 접근 2건(StrictMode 크래시 유발)도 함께 발견해 수정했다 — 그렇지 않으면 guaranteed-envelope 체크가 malformed state를 정상적으로 감지한 직후 이 unguarded access가 원시 예외로 그 결과를 덮어써 machine-readable 계약 자체가 깨졌다.
Regression test: smoke-validate-ops-guaranteed-envelope.ps1(32, table-driven — top-level/state.*/queue.* 각 guaranteed 필드를 개별 제거).

ID: packaged-app-console-window-flash
Severity: UNVERIFIED → UNVERIFIED(재현 시도, 관찰 안 됨)
Reproduction attempt: JvmProcessExecutor.kt의 정확한 spawn 패턴(plain ProcessBuilder, CREATE_NO_WINDOW 없음)을 그대로 모사하는 windowless 부모(javaw.exe로 구동)에서 powershell.exe를 실제로 기동하고, (1) Get-Process MainWindowHandle 폴링과 (2) Win32 EnumWindows+IsWindowVisible P/Invoke로 spawn된 powershell.exe와 그 conhost.exe 호스트가 소유한 모든 top-level window를 6초간 30ms 간격으로 관찰했다. 두 방법 모두 가시(visible) window를 전혀 발견하지 못했다.
Classification rationale: 이 프록시는 실제 packaged native composeApp 배포판이 아니며, 완전한 GUI 재현(실제 버튼 클릭 등 사용자 입력 시뮬레이션)은 이 환경의 GUI 자동화 안전 제약 밖이므로 시도하지 않았다. 따라서 FIXED로 승격하지 않고, 근거 없이 방치하지도 않는다 — 시도했고 관찰되지 않았다는 사실을 그대로 기록한다. 이 항목 하나만으로 전체 verdict를 낮추지 않는다.
Correction gate: 향후 실제 packaged/native 앱에서 재현되면 그때 수정한다.

ID: run-cycle-usepythonsidecars-omitted-on-wrapper-commands
Severity: DOC → RESOLVED(optional로 확정)
Decision rationale: HarnessCommandEncoder.kt를 재확인한 결과 HRNS-NOW 자신의 프로덕션 encoder가 -UsePythonSidecars를 BootstrapDay 한 곳에서만 보내고 RunPlanning/RunReplan/RunExecution/ValidateClosure 4개 명령에서는 보내지 않는다 — 즉 실제 프로덕션 사용의 대다수가 이미 native branch를 거친다(이번 P0-2 수정이 실제로 보호하는 경로임을 재확인). docs/OPERATING_GUIDE.md §1이 이미 "PS1 entrypoints are the operator-facing surface; Python is sidecar/parity support, not the mainline replacement"라고 명시하고 있었으나 §2.1/§2.2/§9.1의 예시 코드가 이 철학과 모순됐다.
Fix: OPERATING_GUIDE.md 예시를 native 기본 + optional parity 플래그로 재정렬. HRNS encoder는 변경하지 않음(변경 필요성의 증거 없음).

ID: pre-handoff-validate-run-cycle-context-strictmode-count-crash [2차 remediation(closure-validation)에서 FIXED]
Severity: HIGH → FIXED
Surface: G(closure)
HRNS-NOW expectation: N/A — closure 실패가 명확한 사유로 fail-closed 되면 충분(이 축은 이미 만족되어 있었음).
Harness actual behavior(수정 전): claude/hooks/pre_handoff_validate.ps1이 scripts/run-cycle.ps1을 통해 호출될 때만(독립 실행 또는 최소 wrapper 재현 시에는 재현되지 않음) pre_handoff_validate.ps1:686의 $loadedDailyRequiredArtifacts.Count 접근이 StrictMode PropertyNotFoundException으로 크래시했다. 완전히 유효하고 손상되지 않은 fixture에서도 100% 재현되어, -ValidateForClosure를 사용하는 모든 run-cycle.ps1 호출이 사실상 항상 "실패"로 귀결됐다. 이와 함께 부모 run-cycle.ps1 프로세스가 이 실패에도 불구하고 exit 0로 종료하는 별도 결함도 이번에 발견했다(§6.6, §5 아래 항목 없음 — closure 실패 exit-code 결함은 별도 finding으로 분리하지 않고 이 항목과 §6.6에서 함께 다룬다).
Root cause(확정): scripts/lib/harness-context-diet-mode.ps1이 파일 최상단에서 Set-StrictMode -Version Latest를 실행했다. PowerShell dot-source(". <script>")는 대상의 최상위 statement를 호출자 자신의 스코프에서 실행하므로, 이 lib를 dot-source하는 모든 호출자(run-cycle.ps1 포함, 자신은 StrictMode 미설정)가 StrictMode Latest를 의도치 않게 상속받았다. run-cycle.ps1이 "& $preHandoffValidateScript"로 실행하는 pre_handoff_validate.ps1도 자신의 StrictMode를 설정하지 않으므로, "&"가 만든 새 스크립트 스코프가 호출자로부터 상속된 StrictMode를 그대로 물려받았다. Get-StringArray가 @()로 배열을 반환해도 단일 원소 배열은 "$x = Get-Foo" 캡처에서 pipeline이 무조건 scalar로 unwrap하며(StrictMode 무관), 이 scalar의 .Count 접근이 StrictMode 하에서만 크래시한다 — 이것이 독립 실행/최소 wrapper에서는 재현 안 되고 run-cycle.ps1 경유 시에만 재현되는 정확한 이유다. 격리 3-파일 repro(lib.ps1/child.ps1/parent_with_leak.ps1/parent_without_leak.ps1)로 이 메커니즘을 직접 재현·검증했다(§6.6).
Fix: (1) 근본 원인 — harness-context-diet-mode.ps1의 Set-StrictMode -Version Latest를 파일 최상단에서 Get-HarnessContextDietMode 함수 내부로 이동. (2) 방어적 이중화 — pre_handoff_validate.ps1의 Get-StringArray 호출부 2곳을 @(Get-StringArray ...)로 감싸 StrictMode 무관하게 배열을 보장. (3) run-cycle.ps1에 closure validation 실패 시 State/run_log 영속화 이후 명시적 exit 1을 배선(이전엔 우연한 $LASTEXITCODE 누출에 의존).
Dynamic evidence(before/after): 수정 전 순수 fixture + run-cycle.ps1 -ValidateForClosure → "The property 'Count' cannot be found on this object" 크래시 100% 재현, 부모 exit 0. 수정 후 동일 fixture → exit 0, closure.validated=true/is_clean_handoff=true, run_log에 pre_handoff_validated/run_cycle_closure_validated 존재(§4.2). harness-context-diet-mode.ps1 수정만 되돌린 상태로 회귀 smoke 재실행 시 실제로 FAIL함을 확인 후 복원(§4.2).
Regression test: smoke-run-cycle-strictmode-scalar-array-contract.ps1(12, 0/1/다중 배열 계약 + dot-source 스코프 누출 격리 재현 + Set-StrictMode가 Get-HarnessContextDietMode 내부에만 있음을 AST로 검증), smoke-run-cycle-closure-success-contract.ps1(13, 신규 positive closure 경로), smoke-run-cycle-closure-failure-propagation.ps1(10, 부모 nonzero exit 단언 추가), HarnessProductionContractTest.kt 5번째 테스트(live-Kit opt-in, 정상 closure를 실제 JVM adapter로 확인).

ID: doctor-install-completeness-missing-run-cycle-dependencies
Severity: MEDIUM → FIXED
Surface: 없음(HRNS-NOW compatibility 축과 무관 — Harness Kit 자체 품질/사용자 요청 사항)
Harness actual behavior(수정 전): doctor.ps1의 install-completeness 검사가 run-cycle.ps1이 실제로 의존하는 필수 스크립트/lib 13개 중 7개를 전혀 확인하지 않았다 — 그 7개가 조용히 삭제/손상돼도 doctor.ps1은 overall=ok를 보고할 수 있었다.
Fix: 단일 정본 인벤토리 scripts/lib/harness-runtime-dependency-inventory.ps1을 신설해 Get-HarnessRunCycleRequiredScripts(12개, error severity)/Get-HarnessRunCycleOptionalScripts(2개, Test-Path로 optional임이 확인된 harness-domain.ps1/build-context-diet-packet.ps1, warn severity)를 노출하고 doctor.ps1이 이를 소비하도록 배선했다. run-cycle.ps1의 실제 의존성 참조와 이 정본을 양방향 자동 대조하는 회귀 smoke를 추가해, 향후 한쪽만 갱신되는 드리프트를 사람이 기억하지 않아도 자동 검출하게 했다.
Dynamic evidence(before/after): doctor.ps1 체크 수 167→193(+26 = 필수 12×최대 2 sub-check + optional 2×1). 필수 파일 1개(code-workqueue.ps1) 임시 제거 → overall=fail, exit 1, error severity 체크 발생(복원 후 overall=ok 재확인, §4.2).
Regression test: smoke-run-cycle-required-dependency-inventory.ps1(27, 양방향 lockstep — 정본에 선언된 항목이 실제 run-cycle.ps1에서 참조되는지, run-cycle.ps1의 실제 의존성이 정본에 모두 선언돼 있는지 양쪽 모두 확인 + doctor.ps1이 정본 함수를 올바른 severity로 실제 소비하는지 확인).
```

## 6. 상세 근거

### 6.1 `required_next_action` 계약 결정 근거

`WorkflowStateMapper.kt`가 top-level `required_next_action`을 무조건 요구하되(`?: return MapResult.Failure(...)`) 도메인 매핑 시 `.ifBlank { null }` 처리를 하는 점, 그리고 `state.next_action`/`state.execution_wrapper`/`state.authorized_target_file` 등 형제 필드가 모두 fresh state에서 빈 문자열을 기본값으로 쓰는 기존 관례를 근거로, "키는 항상 존재해야 하지만 값은 blank가 유효한 steady-state"라는 계약을 확정했다. `cutover.required_operator_action`의 legacy 문구를 그대로 복사하는 지름길은 사용하지 않았다.

### 6.2 `run-cycle.ps1` native child 호출 전수 인벤토리

| # | 자식 | branch | 실패 신호 | 수정 전 caller 확인 | 수정 후 |
|---|---|---|---|---|---|
| 1 | `load_profile.py`/`load-profile.ps1` | python/native | exit code(python만) | python: 확인+throw. native: **exit을 아예 쓰지 않음**(Stop EAP 기반 자연 예외 전파 — 원래부터 정상) | 변경 없음(오탐 — 최초 시도했으나 StrictMode 크래시로 되돌림, §6.5) |
| 2 | `init_workspace.py`/`init-workspace.ps1` | python/native | exit code(python만) | python: 확인+throw. native: exit 미사용, 자연 예외 전파로 원래부터 정상 | 변경 없음(위와 동일 사유) |
| 3 | `doctor.py`/`doctor.ps1` | python/native | exit code(둘 다 exit 사용) | python: 확인+throw. native: **미확인** | native에 `Assert-RunCycleNativeChildSucceeded` 배선 |
| 4 | `validate_ops.py`/`validate-ops.ps1` | python/native | exit code(둘 다) | python: 확인+throw. native: **미확인**, `-KitRoot`/`-Profile`도 누락 | native에 helper 배선 + 누락 파라미터 추가 |
| 5 | `invoke-document-execution.ps1`/`invoke-code-execution.ps1`(`Invoke-RunCycleWrapper` 경유) | native only | 불명(자체 exit code 체크 없음) | 미확인 | **변경 안 함** — python-branch 대조 증거가 없고(원래 native-only), 실행 자체를 막는 상위 ops-validation gate로 이번 remediation의 요구사항은 충족됨. 잔여 관찰사항으로만 기록 |
| 6 | `pre_handoff_validate.py`/`pre_handoff_validate.ps1` | python/native | exit code(둘 다) | python: 확인+throw. native: **미확인** | native에 helper 배선 + 누락 파라미터 추가; **2차 remediation에서 native branch 자체가 StrictMode 상속 크래시로 사실상 항상 실패하던 별도 결함을 근본 원인까지 수정(§6.6)** |

`Assert-RunCycleNativeChildSucceeded`는 `& $child ...` 직후 `$LASTEXITCODE`를 확인하는 공용 helper다. #1/#2는 애초에 `exit`를 쓰지 않고 `$ErrorActionPreference = "Stop"` 기반 자연 종료 예외에 의존하므로(제대로 `&`를 통해 전파됨) 수정이 불필요했다 — 최초에 동일한 패턴이라고 오판해 수정했다가, 실제 실행 중 `$LASTEXITCODE cannot be retrieved because it has not been set`(StrictMode) 크래시로 이 오판을 스스로 발견해 되돌렸다(§6.5). #5(`Invoke-RunCycleWrapper`)는 2차 remediation에서도 변경하지 않았다 — 근거 부족으로 보류가 계속 유효하다(§8).

### 6.3 `& $child` exit 전파 동작 격리 재현

`javaw.exe` 상당의 windowless 상위 없이도, PowerShell 자체에서: `child.ps1`이 `exit 1`로 끝나고 `parent.ps1`이 `& .\child.ps1`로 호출하면 `$LASTEXITCODE`는 1로 설정되지만 `parent.ps1`은 **예외 없이 계속 실행**되고 자신은 exit 0로 끝난다 — 이것이 두 BLOCKER의 근본 메커니즘이다. `exit N`은 catchable PowerShell 예외를 던지지 않는다.

### 6.4 console-window-flash 재현 시도 상세

`JvmProcessExecutor.kt:50`의 정확한 패턴(`ProcessBuilder(listOf(invocation.executable) + invocation.arguments)`, `CREATE_NO_WINDOW` 없음)을 모사하는 최소 Java 프로그램을 `javaw.exe`(windowless)로 구동해 `powershell.exe -Command "Start-Sleep -Seconds 4"`를 실제로 spawn했다. `Get-Process -Name powershell,conhost`의 `MainWindowHandle` 폴링은 항상 0이었다(단, 이 지표는 modern Windows에서 실제 콘솔 창이 별도 `conhost.exe`에 위임되는 경우 신뢰할 수 없음을 확인). 이어서 `EnumWindows`+`GetWindowThreadProcessId`+`IsWindowVisible`로 spawn된 두 프로세스(`powershell.exe`, `conhost.exe`) 소유의 모든 top-level window를 6초간 30ms 간격 전수 열거했으며, 가시 window를 하나도 발견하지 못했다. `conhost.exe` 프로세스 자체는 실제로 생성되는 것을 확인했다(콘솔 서브시스템 리소스 자체는 할당됨).

### 6.5 remediation 도중 발견·수정한 자체 오류

- `load-profile.ps1`/`init-workspace.ps1` native branch에 `$LASTEXITCODE` 체크를 잘못 추가했다가, 이 두 스크립트가 애초에 `exit`를 쓰지 않는다는 사실(StrictMode 크래시로 발견)을 확인하고 즉시 되돌렸다 — §6.2 #1/#2.
- ops-validation gate의 최초 구현이 "Reloading runtime state after wrapper gate" 단계의 무조건 상태 재로드에 의해 자신이 기록한 로그를 유실하는 것을 신규 smoke 작성 중 발견해, 로그 재적용을 reload 이후로 옮겨 수정했다 — §5 두 번째 finding.
- `validate-ops.ps1`에 guaranteed-envelope 체크를 배선하며 `$hasFailure = $true` 대입을 처음에 누락했다가 스스로 재확인해 수정했다.
- `validate-ops.ps1`의 기존(remediation 대상이 아니던) `$state.closure`/`$null -ne $state.closure` 접근 2건이 StrictMode 하에서 `state.closure` 키가 완전히 부재할 때 원시 크래시를 낸다는 것을 신규 smoke의 table-driven 케이스로 발견해 `.PSObject.Properties['closure']` 존재 확인으로 수정했다.
- 전체 offline smoke suite를 실제로 끝까지 실행해서만 발견된, remediation 대상이 아니었던 기존 smoke 8개의 파손(§3.1 하단 각주)을 모두 수정했다 — 코드 리뷰만으로는 발견하지 못했을 규모다.

**2차 remediation(closure-validation)에서 발견·수정한 자체 오류**:

- 새 lockstep smoke(`smoke-run-cycle-required-dependency-inventory.ps1`)의 `Compare-Object`/`Where-Object` 결과에 대한 `.Count` 접근이 결과가 0건일 때 `$null`을 반환받아(빈 배열이 아니라) StrictMode 크래시로 이어지는 것을 2곳에서 발견해 `@(...)` 래핑으로 수정했다.
- 같은 smoke의 "정본에 선언된 항목이 실제로 참조되는지" 검사가 `harness-context-diet-mode.ps1`을 `'scripts/lib/harness-context-diet-mode.ps1'`(전체 경로) 문자열로만 찾아 false positive로 실패했다 — `run-cycle.ps1`이 실제로는 자신의 `$PSScriptRoot`(=`scripts/`) 기준 상대 경로 `'lib/harness-context-diet-mode.ps1'`을 쓴다는 것을 발견해, "scripts/" 접두사를 제거한 형태도 인정하도록 검사를 수정했다.
- 수동 grep 기반으로 처음 작성한 의존성 인벤토리가 `scripts/report/build-context-diet-packet.ps1`을 누락했다 — 직접 만든 자동 lockstep smoke의 Direction-1 검사가 이 누락을 스스로 잡아냈고, 실제로 `Test-Path`로 optional 처리됨을 확인해 `Get-HarnessRunCycleOptionalScripts`에 추가했다.
- `docs/ROADMAP.md`에 1차 remediation에서 갱신을 놓친 stale한 "Validate-Ops (19 checks)" 문구가 남아있던 것을 smoke 카운트 문서 갱신 작업 중 발견해, 실측 재검증(20 checks) 후 3개 문서(HARNESS_KIT_MAP.en/ko.md, ROADMAP.md) 모두 일관되게 정정했다.
- Gradle test XML을 PowerShell `Get-Content -Raw`로 읽어 재합산하는 최초 시도가 기본 codepage 디코딩으로 한글 테스트명을 깨뜨려 XML 파싱 자체가 실패하는 것을 발견해, `[System.IO.File]::ReadAllText(..., [System.Text.Encoding]::UTF8)`로 명시적 UTF-8 읽기로 수정했다.

### 6.6 StrictMode dot-source 스코프 누출 격리 재현(closure-validation 근본 원인)

최소 4-파일 repro로 메커니즘을 직접 검증했다: `lib.ps1`(파일 최상단에 `Set-StrictMode -Version Latest`, 단일 원소 배열을 반환하는 함수 포함), `child.ps1`(자신은 StrictMode 미설정, 그 함수를 호출해 `.Count`에 접근), `parent_with_leak.ps1`(`. .\lib.ps1`로 dot-source 후 `& .\child.ps1` 호출), `parent_without_leak.ps1`(`lib.ps1`을 dot-source하지 않고 `& .\child.ps1`만 호출). `parent_with_leak.ps1` 경유 시에만 `child.ps1`이 크래시했고, `parent_without_leak.ps1` 경유 시에는 동일한 `child.ps1`이 정상 종료했다 — dot-source가 호출자 스코프에 `Set-StrictMode`를 주입하고, 그 스코프에서 `&`로 실행된 자식이 이를 상속한다는 메커니즘을 격리 환경에서 재확인했다. `harness-context-diet-mode.ps1`을 함수-스코프 수정 전/후로 교체해 동일 repro를 재실행했을 때도 수정 전에는 크래시, 수정 후에는 정상 종료로 일치했다.

`Get-StringArray`의 pipeline unwrap 자체는 별도로 AST 추출 후 독립 평가해 확인했다: 함수 본문을 `[System.Management.Automation.Language.Parser]::ParseInput`으로 파싱해 함수 정의만 떼어내 격리 실행 컨텍스트에 로드하고, 0/1/다중 원소 입력 각각에 대해 `$x = Get-StringArray -Value ...` 단순 캡처의 결과 타입을 확인했다 — 1개 원소일 때만 스칼라로 unwrap되며, 이는 StrictMode on/off 여부와 무관하게 항상 발생하고, `.Count` 접근이 크래시하는지 여부만 StrictMode에 좌우된다는 것을 직접 관찰했다.

### 6.7 Doctor.ps1 install-completeness 확장 근거

사용자 지시("단일 runtime dependency inventory를 정본으로 재사용할 수 있는지 우선 검토")에 따라, 새 목록을 doctor.ps1에 하드코딩하는 대신 재사용 가능한 단일 정본을 먼저 검토했다. `run-cycle.ps1`을 전수 조사한 결과 실제 필수 의존성은 12개(`init-workspace.ps1`, `load-profile.ps1`, `doctor.ps1`, `validate-ops.ps1`, `invoke-planning-cycle.ps1`, `invoke-document-execution.ps1`, `invoke-code-execution.ps1`, `pre_handoff_validate.ps1`, `harness-context-diet-mode.ps1`, `state-surface.ps1`, `wrapper-json-state.ps1`, `code-workqueue.ps1`)이고, optional 2개(`harness-domain.ps1`, `build-context-diet-packet.ps1` — 둘 다 `run-cycle.ps1`에서 `Test-Path`로 감싸져 부재 시 graceful fallback이 있음을 소스에서 직접 확인)였다. 기존 `doctor.ps1`의 `$requiredKitFiles` 루프는 이 중 5개만 우연히 겹쳐 확인하고 있었다.

이 인벤토리를 `scripts/lib/harness-runtime-dependency-inventory.ps1`이라는 별도 lib로 분리해 `Get-HarnessRunCycleRequiredScripts`/`Get-HarnessRunCycleOptionalScripts` 두 함수로 노출했다 — `doctor.ps1`과 향후 다른 소비자(예: 별도 setup 검증 스크립트)가 동일 정본을 공유할 수 있게 하기 위함이며, `run-cycle.ps1` 자신은 이 lib를 소비하지 않는다(자기 자신의 의존성을 선언하는 코드가 자기 자신의 실행에 필요하지 않도록 순환을 피함). `doctor.ps1`은 필수 항목을 error severity(+`$hasFailure=$true`)로, optional 항목을 warn severity로 반영한다. 정본과 `run-cycle.ps1`의 실제 소스 간 드리프트를 사람이 기억에 의존해 유지하지 않도록, `smoke-run-cycle-required-dependency-inventory.ps1`이 정규식/AST로 `run-cycle.ps1`의 실제 참조를 추출해 정본과 양방향 대조한다(정본에 있는데 실제로 참조 안 되는 항목, 실제로 참조되는데 정본에 없는 항목 둘 다 검출) — 이 smoke를 작성하는 과정에서 실제로 누락된 의존성 1개(`build-context-diet-packet.ps1`)를 발견해 추가했다(§6.5).

## 7. 완료 조건 대조

### 7.1 1차 remediation

- [x] Harness targeted smoke(`smoke-clean-root-onboarding.ps1`/`smoke-doctor-json-contract.ps1`/`smoke-validate-ops-json-contract.ps1`) 통과.
- [x] 신규 smoke 5개 개별 통과(93 cases 전체).
- [x] 공식 offline suite(`SMOKE_INDEX.md` §6.4 스크립트 그대로 추출·실행) 81/81 통과, live Claude/Ollama 호출 0.
- [x] HRNS `gradlew check --rerun-tasks --no-daemon` 통과(442 tests, 0 failures, 0 errors, 1 skipped).
- [x] fresh onboarding production output을 HRNS production adapter가 `Success`로 읽음.
- [x] 모든 지원 writer가 동일한 guaranteed envelope를 생성함.
- [x] 공용 validator + `validate-ops.ps1`이 malformed envelope에서 fail함.
- [x] native Doctor/Validate-Ops/closure의 nonzero exit가 caller에 반영됨.
- [x] ops 실패가 execution wrapper 실행을 막음.
- [x] closure 실패가 clean closure로 기록되지 않음.
- [x] resolver가 `enter-project.ps1` 부재 Kit를 거부함.
- [x] console 위험은 별도 `UNVERIFIED`로 유지하고 숨기지 않음.

### 7.2 2차 remediation(closure-validation)

- [x] StrictMode scalar/array 결함 수정(근본 원인 + 방어적 이중화 둘 다).
- [x] valid native closure가 실제로 성공(순수 production 산출물, fixture 조작 없음, §4.2).
- [x] invalid native closure가 부모 nonzero exit로 전파(§4.2).
- [x] 성공/실패 State와 run_log가 각각 정확(§4.2).
- [x] HRNS production adapter/policy/projection 해석이 일관됨(`HarnessProductionContractTest` 5번째 테스트, `ClosurePolicy`/`CockpitProjectionAssembler`/`RunStatusProjectionAssembler` 코드 검토로 이미 State 기반임을 확인 — 프로덕션 코드 변경 불필요).
- [x] positive와 negative 회귀 테스트 존재(`smoke-run-cycle-closure-success-contract.ps1`, `smoke-run-cycle-closure-failure-propagation.ps1`).
- [x] Harness 전체 offline suite 통과(84/84, §4.2).
- [x] HRNS 전체 Gradle check 통과(443 tests, 0 failures, 0 errors, 2 skipped, §4.2).
- [x] 보고서와 현행 문서의 verdict/count/finding 정합(이 문서 + `documentation_guide.md` + `current_validation_reports.md` + `README.md`, §11).
- [x] scratch와 live root 위생 확인(§4.2, §10).
- [x] doctor.ps1 install-completeness가 run-cycle.ps1 실제 필수 wrapper/lib를 모두 error severity로 검증(§5, §6.7).
- [x] 단일 canonical dependency inventory 재사용 여부 사전 검토 및 구현(§6.7).

두 remediation의 모든 조건이 충족되어 verdict를 `COMPATIBLE_WITH_NONBLOCKING_GAPS`로 유지한다(§1).

## 8. Owner 분리 및 향후 결정 사항

**Harness Kit(완료)**: guaranteed envelope 전 writer 정렬, 공용 validator 신설, native child exit 전파 통합, ops/closure fail-closed gate, 신규 회귀 smoke 5개(1차); dot-source StrictMode 스코프 누출 근본 원인 제거, closure 실패 exit-code 계약 수정, doctor.ps1 install-completeness 확장, 신규/강화 회귀 smoke 4개(2차).

**HRNS-NOW(완료)**: resolver entrypoint 보강, production-to-production contract test 신설(1차); 동일 contract test에 정상 closure 성공 경로 5번째 테스트 추가(2차).

**문서/공동 결정(완료)**: `-UsePythonSidecars`는 optional로 확정(HRNS encoder의 실제 사용 패턴이 근거).

**신규 후속 작업(1차 remediation 시점, 이제 해소됨)**:
- ~~`pre_handoff_validate.ps1`이 `run-cycle.ps1` 경유 시에만 겪는 StrictMode `.Count` 크래시의 근본 원인 조사 및 수정~~ → 2차 remediation(closure-validation)에서 FIXED(§5, §6.6).

**남은 후속 작업(2차 remediation 이후에도 유지, 별도 트래킹 필요)**:
- `invoke-document-execution.ps1`/`invoke-code-execution.ps1`을 감싸는 `Invoke-RunCycleWrapper`(native-only, python 대조군 없음) 자체의 exit code 처리는 1차·2차 모두 인벤토리만 하고 변경하지 않았다(§6.2 #5) — 상위 ops-validation gate가 wrapper 호출 자체를 막으므로 compatibility 위반은 아니지만, wrapper 내부 실행 중 실패가 발생하는 경로는 여전히 미확인 상태다. 필요성이 확인되면 별도 결정.

## 9. 실행하지 않은 항목

- Native Compose UI QA(실제 packaged 앱 클릭 재현): 사용자 요청 없는 입력 합성을 금지하는 이 환경의 규칙에 따라 `NOT_EXECUTED`로 남긴다. console-window-flash는 그 대신 근접 프록시로 재현을 시도했다(§6.4, 1차·2차 모두 동일 상태 유지).
- `kit-version.json` malformed/higher-minor 변형 시나리오: 기존 `JsonKitVersionManifestAdapterTest.kt`/`CompatibilityPolicyTest.kt`가 두 remediation의 변경으로 영향받지 않았음을 `gradlew check`의 443 tests 전체 통과로만 간접 확인했다 — 별도 신규 시나리오는 추가하지 않았다(두 remediation의 대상 결함과 무관).
- `Invoke-RunCycleWrapper`(§6.2 #5, §8) 자체의 exit code 강화는 증거 부족으로 계속 보류했다.

## 10. scratch·Git·live Kit 무변경 증거

### 10.1 1차 remediation

- scratch root `S:\tmp\hrns-now-harness-remediation-20260806-132933\`는 완전히 제거했다(HRNS JVM 검증기 컴파일 산출물, ASCII/한글 fixture, smoke 러너 스크립트, gradle/smoke 로그 전부 포함) — `Test-Path` → `False` 확인.
- `D:\harness-kit\scratch\`에 반복 실행 중 남은 `smoke-planning-once-guard-*` 잔여 디렉터리 2개를 추가로 발견해 제거했다(다른 스크립트가 만든 `diag-planning-hang`/`manual-o7-bind-*`는 이 remediation 시작 이전인 2026-06-12/2026-07-13 생성이므로 손대지 않았다). `D:\harness-kit\logs\`에는 이 remediation 시작 이후 생성된 잔여 파일이 없음을 확인했다.
- git 조작 없음: `git add`/`commit`/`push`/`reset`/`restore` 전부 미실행. HRNS-NOW working tree의 병행 Codex 변경은 건드리지 않았다.
- `D:\harness-workspaces`(실 사용자 워크스페이스)와 `%APPDATA%\hrns-now\projects.json`은 이번 remediation에서 전혀 접근하지 않았다.
- Harness Kit(`D:\harness-kit`) 변경은 §3.1에 전수 기록했으며, 그 외 파일은 수정·삭제·이동하지 않았다.

### 10.2 2차 remediation(closure-validation)

- scratch root `S:\tmp\hrns-now-closure-remediation-20260807-093930\`는 이 보고서 갱신 직후 정확한 경로 검증(`Test-Path`)을 거쳐 제거한다(§4.2에서 이미 위생 검사 완료, 실제 제거는 최종 응답 직전에 수행).
- `D:\harness-kit\scratch\`/`logs\`는 §4.2에서 이미 확인했다 — 이번 remediation 시작 이후 생성된 신규 잔여물 없음, 1차 remediation 이전부터 있던 무관 디렉터리 2개는 그대로 유지.
- git 조작 없음: `git add`/`commit`/`push`/`reset`/`restore`/`checkout`/`stash`/`clean` 전부 미실행(§4.2의 `git status --short` 결과가 이를 증빙).
- `D:\harness-workspaces`, `%APPDATA%\hrns-now\projects.json` 무변경을 §4.2에서 mtime 기준으로 확인했다.
- Harness Kit 변경은 §3.1b, HRNS-NOW 변경은 §3.2b에 전수 기록했으며, 그 외 파일은 수정·삭제·이동하지 않았다.

실행 지시서는 1차 `doc/claude_prompts/harness-kit-live-compatibility-remediation.md`, 2차 `doc/claude_prompts/harness-kit-closure-validation-remediation.md`다. 이전 감사 보고서는 `git log`로 조회 가능하다(이 파일을 in-place로 갱신, 두 remediation 모두 동일 파일에 누적 반영).

## 11. 2026-09-02 Codex 최종 reconciliation

8월 7일 runtime 검증은 통과했지만 Codex의 commit gate에서 live Harness 문서가 여전히 이전 기준선(87/76 smoke, Doctor 167, Validate-Ops 19, PowerShell 164, BOM audit 230)을 현재값으로 서술하고 `kit_version=2026.07.23`을 유지한 사실이 확인됐다. 따라서 당시 `DOC 0`과 commit-ready 선언은 보류했고, 이 항목을 별도 DOC/version·rollback gap으로 다시 열었다.

이번 최종 정리에서 live inventory를 재계산해 다음과 같이 닫았다.

- `D:\harness-kit\README.md`, `docs/HARNESS_KIT_MAP.en.md`, `docs/HARNESS_KIT_MAP.ko.md`, `docs/ROADMAP.md`를 95 total / 84 offline / 11 manual-live / Secondary LLM 36, Doctor 193, Validate-Ops 20, PowerShell 173, BOM audit 239로 정렬했다.
- `kit-version.json`의 표시용 CalVer를 `2026.09.02`로 갱신했다. `state_schema_version=1.0`, `ui_contract_version=1.0`은 계약 변경이 없어 유지했다.
- 기존 `smoke-smoke-index-consistency.ps1`을 확장해 live inventory를 직접 계산하고 README·양 언어 map·ROADMAP의 current-baseline section과 manifest가 일치하는지 검사한다. historical snapshot은 current 판정에서 분리한다.

같은 시점에 기존 GitHub Actions 실패도 조사했다. 28건 중 27건은 `powershell.exe`, `cmd.exe`, Windows path를 의도적으로 검증하는 infra 전체 suite를 `ubuntu-latest`에서 실행한 CI 구성 오류였다. CI를 Ubuntu portable `:core:check`와 `windows-latest` 전체 `check`로 분리했다. 나머지 lock 경쟁 1건은 임시 파일 move를 lock 획득 compare-and-set처럼 사용한 이식성 결함과 새 파일 JSON publication window가 원인이었다. `LocalProcessLockAdapter`를 `CREATE_NEW` 원자 생성 후 짧은 publication retry 방식으로 수정했고 동일 경쟁 테스트를 Ubuntu WSL과 Windows에서 각각 통과시켰다.

원격 GitHub Actions 실행은 push 전에는 발생하지 않으므로 이 보고서의 CI 증거는 로컬 Ubuntu WSL과 Windows 실행까지다. 다음 push의 두 Actions job 결과를 최종 원격 증거로 추가할 수 있다. 이 reconciliation으로 재현된 compatibility BLOCKER/HIGH와 commit을 막았던 DOC/version gap은 닫혔으며 verdict는 `COMPATIBLE_WITH_NONBLOCKING_GAPS`를 유지한다. 남은 non-blocking gap은 기존 두 건(console-window-flash `UNVERIFIED`, `Invoke-RunCycleWrapper` 자체 exit code 미확정)뿐이다.

최종 commit gate 결과는 다음과 같다.

- live Harness `SMOKE_INDEX.md` §3.2를 직접 추출해 공식 offline suite **84/84 PASS**를 재확인했다. live Claude/Ollama 호출은 0건이다.
- HRNS Windows 전체 `gradlew.bat check --rerun-tasks --no-daemon`은 **443 tests / 0 failures / 0 errors / 2 skipped**로 통과했다. D:\harness-kit을 실제 지정한 opt-in `HarnessProductionContractTest`는 **5/5 PASS, skipped 0**이다.
- Harness PowerShell 173개는 parser error 0, release 대상 239개는 UTF-8 BOM 0이다. current-baseline 회귀 smoke는 13/13 PASS다.
- Git 저장소가 아닌 live Harness의 rollback 기준으로 제외 규칙을 적용한 clean snapshot `D:\backup\harness-kit-2026.09.02-verified.zip`을 새로 생성했다. ZIP은 243 entries와 필수 빈 `logs/`, `scratch/`를 포함하며 SHA-256은 `FE1642F706690C947417D6873B16F0E31616F51014B1B931925E7126348A9E94`다. 기존 백업은 덮어쓰지 않았고 staging 경로는 검증 후 제거했다.
- clean snapshot scan은 copied 239 / pollution 0 / excluded 0 / protected-path violation 0이었다. clean-root onboarding의 실제 onboarding·Doctor·Validate-Ops 계약 검사는 모두 통과했으며, 해당 smoke의 유일한 실패 assertion은 live source에 release 제외 대상이 1개 이상 있어야 한다는 전제라 이미 제외물을 제거한 clean payload에는 적용하지 않았다.
