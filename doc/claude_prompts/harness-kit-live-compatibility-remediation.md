# HRNS-NOW ↔ Live Harness Kit 호환성 수정 프롬프트

## 역할

당신은 Windows/PowerShell/Kotlin/JVM 경계를 함께 다루는 senior maintainer다. 감사 보고서에서 확정된 호환성 결함을 실제 source와 회귀 테스트로 수정하고, production-to-production 증거로 verdict를 다시 판정한다.

이번 작업은 추가 감사가 아니라 **수정과 검증**이다. 다만 보고서의 제안이 source와 충돌하면 source를 다시 확인하고 더 안전한 최소 변경을 선택한다. false success를 만드는 호환 shim이나 HRNS parser 완화는 금지한다.

## 작업 루트

- HRNS-NOW: `S:\dev\project\hrns_now`
- live Harness Kit: `D:\harness-kit`
- 수정된 감사 보고서: `S:\dev\project\hrns_now\doc\phase_reports\harness-kit-live-compatibility-audit-report.md`
- 현행 HRNS 문서:
  - `doc/documentation_guide.md`
  - `doc/hrns_now_claude_plan.md`
  - `doc/hrns_now_design_pattern.md`
  - `doc/native_qa_checklist.md`

먼저 위 보고서를 끝까지 읽고, 보고서에 명시된 모든 source line을 현재 파일과 다시 대조하라. 삭제된 phase prompt/report를 복원하거나 과거 문서를 정본으로 사용하지 마라.

## 허용 범위와 보호 규칙

허용:

- `D:\harness-kit`의 production scripts/templates/docs/smoke 수정
- HRNS-NOW의 `core`, `infra`, `composeApp`, tests, 현행 문서 수정
- `S:\tmp\hrns-now-harness-remediation-<timestamp>` 아래 격리 fixture 생성
- offline Gradle test와 Harness smoke 실행

금지:

- `D:\harness-workspaces` 아래 실제 사용자 workspace 수정
- `%APPDATA%\hrns-now\projects.json` 수정
- live Claude/Ollama/외부 LLM 호출
- raw session id/token/secret/log를 보고서에 기록
- `git add`, `commit`, `push`, reset, restore
- 현재 작업트리의 사용자/Codex 변경 덮어쓰기
- `doc/QA_captures/`, `doc/hrns_now_packaging_plan.md`, `doc/user_workflow_qa_notes.md` 수정
- 실패를 숨기기 위한 HRNS `WorkflowStateMapper` requiredness 완화

시작할 때 양쪽 root의 파일 목록, HRNS `git status --short`, 핵심 파일 hash/mtime를 기록한다. 병행 변경이 감지되면 해당 파일을 덮어쓰지 말고 재읽어 병합한다.

## P0-1. WORKFLOW_STATE UI envelope writer drift 수정

### 계약 결정

`docs/STATE_MODEL.md` §6.3이 보장하는 envelope를 정본으로 사용한다. 특히 top-level `required_next_action`의 fresh `request_intake_pending` 의미를 먼저 확정하라.

- `cutover.required_operator_action`의 legacy 문구를 근거 없이 복사하지 마라.
- blank가 계약상 허용되는지, 실제 사용자 행동 문구가 필요한지 source와 소비 경로로 결정하고 문서화하라.
- HRNS mapper는 필드 존재를 계속 fail-closed 요구해야 한다.

### 수정 대상

지원되는 모든 writer가 같은 envelope를 만들도록 최소한 다음을 검토·수정한다.

- `templates/workspace/WORKFLOW_STATE.json.tpl`
- `scripts/init-workspace.ps1`
  - `Ensure-WorkflowStateBaseline`
  - 기존 불완전 State의 idempotent backfill
- `scripts/run-cycle.ps1`
  - `Ensure-RunCycleWorkflowStatePrimaryCandidate`
  - 최종 write-back과 중간 장애 시 남는 State
- `scripts/lib/state-surface.ps1`
  - `New-HarnessWorkflowStateModel`
  - manual/materialization 계열이 지원 대상인지 확인
  - 지원 대상이면 envelope 보장, dead path면 명시적 폐기 또는 비지원 처리

STATE_MODEL.md §6.3의 stable `state`/`queue` 필드도 fresh State에 실제로 존재하는지 확인하라. HRNS가 현재 소비하지 않는 필드도 Harness가 guaranteed라고 선언했다면 writer와 문서 중 하나를 명시적으로 정렬해야 한다.

### shared validator

`Get-HarnessWorkflowStateCompatibilityFindings`를 guaranteed envelope 검사의 단일 정본으로 만든다.

- top-level, `state`, `queue` 필드의 존재와 기본 타입을 검사
- unknown field는 보존/허용
- missing/malformed guaranteed field는 `fail`
- `validate-ops.ps1`은 이 shared finding을 error severity와 nonzero exit로 반영
- 같은 규칙을 여러 script에 복제하지 않음

## P0-2. run-cycle native child failure propagation 수정

먼저 `run-cycle.ps1`의 모든 `& $...Script`/외부 process 호출을 inventory하고 다음을 표로 기록한다.

- child 종류
- Python/native branch
- 실패 신호 방식: throw 또는 exit code
- 현재 caller의 확인 방식
- 실패 후 허용되는 mutation

최소 확정 결함은 다음 세 경로다.

1. native Doctor: `run-cycle.ps1:3299-3304` ↔ `doctor.ps1`의 `exit 1`
2. native Validate-Ops: `run-cycle.ps1:3391-3397` ↔ `validate-ops.ps1`의 `exit 1`
3. native closure validator: `run-cycle.ps1:4033-4041` ↔ `pre_handoff_validate.ps1`의 `exit 2`

### 구현 원칙

- native PowerShell child 호출은 가능하면 공통 checked-invocation helper로 통합한다.
- `$LASTEXITCODE`는 child 호출 직후 다른 native command 전에 캡처한다.
- Python/native branch의 성공·실패 의미를 대칭화한다.
- stdout/stderr와 기존 sanitized logging 계약을 훼손하지 않는다.
- 단순히 `throw`를 기존 catch로 보내고 끝내지 않는다. catch 이후 제어 흐름까지 fail-closed여야 한다.

### command별 필수 의미

- Doctor failure: cycle의 후속 planning/execution/closure를 진행하지 않는다.
- Ops failure:
  - `state.ops_validation.passed=false`와 진짜 실패 사유를 저장
  - `RunExecution(code|doc|auto)`는 wrapper 호출 전에 중단
  - execution wrapper sentinel이 생성되지 않아야 함
  - Planning/Replan은 초기 artifact를 만들어 validation을 회복시키는 lane일 수 있으므로, 계속 허용할지 source 의도를 확인해 명시적으로 결정하고 테스트
- Closure failure:
  - `closure.validated=false`
  - `closure.is_clean_handoff=false`
  - top-level closure/clean-handoff projection도 false
  - `pre_handoff_validated`, `closure_validated`, clean success log를 기록하지 않음
  - 요청 결과를 성공으로 위장하지 않음

`execution_wrapper`/queue mismatch는 별도 임시 fix로 우회하지 말고 이 failure-propagation 수정의 필수 회귀 fixture로 사용한다.

## P0-3. Harness offline 회귀 테스트 추가

기존 smoke style과 `scripts/SMOKE_INDEX.md` 규칙을 따른다. 이름은 기존 naming과 충돌하지 않는 범위에서 다음 역할을 명확히 드러내라.

- fresh onboarding UI envelope smoke
- validate-ops guaranteed-field table-driven failure smoke
- run-cycle native child exit propagation smoke
- run-cycle execution fail-closed sentinel smoke
- run-cycle closure failure propagation smoke

fixture는 scratch에 격리하며 live Kit 또는 사용자 workspace의 script를 바꿔치기하지 않는다. 필요하면 scratch Kit 복사본 또는 명시적 test seam을 사용하되 production API를 테스트 편의 때문에 넓히지 않는다.

## P1-1. HRNS runtime resolver completeness

`DefaultKitRuntimeResolver.REQUIRED_ENTRYPOINTS`에 HRNS가 직접 호출하는 `scripts/enter-project.ps1`을 추가한다.

함께 수정할 범위:

- resolver KDoc
- `RuntimeIssue.MissingEntrypoint` 사용자 메시지
- `CockpitProjectionAssembler`, `ViewModelStrings`, `RegisterProjectUseCase`의 관련 문구
- `DefaultKitRuntimeResolverTest`와 등록/use-case tests

`scripts/init-workspace.ps1` 같은 전이 의존성을 resolver가 직접 확인할지, Doctor를 설치 완전성 gate로 사용할지 결정하고 근거를 남긴다. 임의로 Doctor의 60개 이상 파일 목록을 HRNS에 복제하지 않는다.

## P1-2. production-to-production contract test

실제 Harness onboarding output을 실제 HRNS `JsonWorkflowStateAdapter`/`WorkflowStateMapper`로 읽는 재현 가능한 contract test 또는 검증 runner를 저장소에 추가한다.

- production/test source에 `D:\harness-kit` 절대경로를 하드코딩하지 않는다.
- `-KitRoot` 또는 환경변수로 live Kit root를 주입할 수 있어야 한다.
- offline deterministic fixture 모드와 live local Kit 검증 모드를 구분한다.
- fresh onboarding 직후, run-cycle 1회 후, missing guaranteed field 변형을 최소 표본으로 포함한다.
- live LLM 호출 없이 실행돼야 한다.

## P1-3. packaged child console risk

`composeApp`의 `console=false`는 앱 자체 설정이고 child `powershell.exe` 창 미노출을 자동 보장한다고 가정하지 마라.

1. 가능하면 packaged/native 앱 또는 동등한 GUI-parent 실행 조건에서 실제 콘솔 창 노출을 먼저 관찰한다.
2. 재현되면 지원 가능한 Windows no-window spawn 방식을 구현한다.
3. Doctor, ValidateOps, OnboardProject, Bootstrap, Planning, Replan, Execution, Closure 전체에서 검증한다.
4. 재현/관찰하지 못하면 `FIXED`로 표시하지 말고 `UNVERIFIED`로 남긴다.

`-WindowStyle Hidden` 문자열을 추가했다는 사실만으로 완료 처리하지 않는다. 프로세스 argument 안전성, stdout/stderr capture, timeout/cancellation, process-tree termination을 모두 보존해야 한다.

## DOC. sidecar 호출 관례 정렬

`docs/OPERATING_GUIDE.md`의 wrapper 예시에서 `-UsePythonSidecars`가 optional example인지 canonical requirement인지 source와 owner intent로 결정한다.

- optional이면 예시를 optional로 명시하고 HRNS encoder는 변경하지 않는다.
- required이면 모든 wrapper command 영향과 packaged Python dependency를 먼저 증명한 뒤 변경한다.
- 근거 없이 sidecar flag를 전 command에 추가하지 않는다.

## 검증 순서

### Harness Kit targeted

최소한 다음을 실행한다.

```powershell
D:\harness-kit\scripts\smoke\smoke-clean-root-onboarding.ps1 -KitRoot D:\harness-kit
D:\harness-kit\scripts\smoke\smoke-doctor-json-contract.ps1 -KitRoot D:\harness-kit
D:\harness-kit\scripts\smoke\smoke-validate-ops-json-contract.ps1 -KitRoot D:\harness-kit
```

추가한 failure-injection/envelope smoke를 각각 직접 실행한다. 이후 `SMOKE_INDEX.md`가 정의하는 automatic/offline 전체 suite를 실행한다. 새 smoke가 추가되면 예전 76개 숫자를 하드코딩하지 말고 index에서 현재 개수를 산출한다. live/manual 11개와 Claude/Ollama 호출은 실행하지 않는다.

### HRNS-NOW

```powershell
cd S:\dev\project\hrns_now
.\gradlew.bat check --rerun-tasks --no-daemon
```

XML 결과를 모듈별로 재합산하고 test/failure/error/skipped를 보고한다.

### production-to-production

격리 scratch에서 현재 `D:\harness-kit`으로 fresh onboarding을 수행하고 다음을 증명한다.

1. bridge 3개와 daily required surface 4개 생성
2. generated State가 전체 UI envelope를 가짐
3. HRNS production adapter read가 `Success`
4. Doctor/Validate-Ops JSON 결과가 기대 계약과 일치
5. required field 제거 fixture는 Validate-Ops nonzero 및 HRNS `Malformed`
6. ops mismatch fixture는 execution wrapper sentinel 미호출
7. closure failure fixture는 모든 closure success flag/log가 false/absent

한글+공백 경로 표본도 1개 포함한다.

## 완료 기준

다음이 모두 참이어야 compatibility BLOCKER를 닫을 수 있다.

- fresh onboarding State를 HRNS production adapter가 `Success`로 읽음
- 모든 supported writer가 같은 guaranteed envelope를 생성
- malformed envelope를 shared validator와 Validate-Ops가 fail
- native Doctor/Validate-Ops/closure nonzero exit가 caller에 반영
- ops failure 뒤 execution wrapper가 실행되지 않음
- closure failure가 clean closure로 기록되지 않음
- resolver가 `enter-project.ps1` 없는 Kit을 거부
- Harness targeted/new/full offline smoke 통과
- HRNS full Gradle check 통과
- live Kit과 사용자 workspace에 검증 fixture/backup/log가 남지 않음

console risk는 동적 관찰 전까지 별도 `UNVERIFIED`로 유지한다. 이 한 항목을 정적으로 추정해 core compatibility fix를 실패로 돌리지는 않되, 최종 보고서에서 숨기지 않는다.

## 보고서 갱신

새 phase report를 만들지 말고 기존 파일을 현행화한다.

`S:\dev\project\hrns_now\doc\phase_reports\harness-kit-live-compatibility-audit-report.md`

반드시 기록할 것:

- 변경한 파일 목록을 HRNS/Harness로 분리
- 각 finding의 `FIXED`/`OPEN`/`BLOCKED_BY_ENVIRONMENT`
- before/after 동적 증거
- 새 regression test 이름과 검증 대상
- targeted/full suite 및 Gradle 실제 결과
- 실행하지 않은 live/manual/GUI 항목
- scratch 제거 여부
- 최종 verdict와 잔여 위험

모든 P0 완료 조건과 production-to-production 검증이 통과하기 전에는 `COMPATIBLE`로 바꾸지 마라. 환경 때문에 필수 증거를 만들 수 없으면 정확히 `BLOCKED_BY_ENVIRONMENT`로 남기고 추정으로 닫지 않는다.

## 최종 응답 형식

1. 최종 verdict
2. 수정한 핵심 결함
3. HRNS-NOW 변경 파일
4. Harness Kit 변경 파일
5. 추가한 회귀 테스트
6. 실제 실행 결과
7. 남은 open/unverified 항목
8. 보고서 경로

Git 조작은 하지 않는다. 종료 전에 scratch를 제거하고 양쪽 root의 최종 변경 범위를 다시 확인한다.
