# HRNS-NOW ↔ Harness Kit Closure 정상 경로 복구 프롬프트

## 역할과 목표

당신은 Windows PowerShell 5.1, Kotlin/JVM, 프로세스 종료 계약을 함께 다루는 senior maintainer다.

이 작업의 목표는 `D:\harness-kit`의 `run-cycle.ps1 -ValidateForClosure` 정상 성공 경로를 복구하고, 실패 경로의 종료 코드·State·HRNS-NOW 표시가 서로 모순되지 않게 만든 뒤, false-green 테스트를 제거하고 호환성 verdict를 다시 판정하는 것이다.

현재 보고서가 선언한 `COMPATIBLE`을 전제로 작업하지 마라. 아래 재현 결함이 열린 동안에는 감사 규칙상 `COMPATIBLE`을 사용할 수 없다.

## 작업 위치와 정본

- HRNS-NOW: `S:\dev\project\hrns_now`
- live Harness Kit: `D:\harness-kit` (`.git` 없음)
- 감사 규칙: `S:\dev\project\hrns_now\doc\claude_prompts\harness-kit-live-compatibility-audit.md`
- 직전 수정 지시서: `S:\dev\project\hrns_now\doc\claude_prompts\harness-kit-live-compatibility-remediation.md`
- 갱신할 보고서: `S:\dev\project\hrns_now\doc\phase_reports\harness-kit-live-compatibility-audit-report.md`
- 현행 문서:
  - `doc/documentation_guide.md`
  - `doc/phase_reports/current_validation_reports.md`
  - `README.md`

정본 우선순위는 현재 production source, 실제 실행 결과, 현행 계약 문서, 보고서 순서다.

## 확정된 재현과 판정 제약

격리된 fresh onboarding fixture에서 다음이 독립 재현됐다.

1. `enter-project.ps1`이 daily surface와 `WORKFLOW_STATE.json`을 정상 생성한다.
2. `validate-ops.ps1`은 성공한다.
3. 같은 fixture에서 `run-cycle.ps1 -SkipDoctor -ValidateForClosure`를 실행하면 다음 경고가 발생한다.

```text
Pre-handoff closure validation failed.
The property 'Count' cannot be found on this object. Verify that the property exists.
```

4. 결과 State는 `closure.validated=false`, `closure.is_clean_handoff=false`다.
5. 그런데 부모 `run-cycle.ps1` 종료 코드는 `0`이다.

직접 원인 후보는 다음 경로다.

- `claude/hooks/pre_handoff_validate.ps1:Get-StringArray`
- `$loadedDailyRequiredArtifacts = Get-StringArray ...`
- `$loadedPersistentRequiredArtifacts = Get-StringArray ...`
- 단일 원소 결과가 PowerShell pipeline unrolling으로 scalar가 된 뒤 StrictMode 아래 `.Count` 접근이 실패
- `profiles/corp-default.yaml`의 `persistent_required`가 `HARNESS_FAILURES.md` 한 항목이라 기본 프로필에서 재현

감사 프롬프트의 판정 규칙도 반드시 지켜라.

- 표준 onboarding/daily flow가 진행되지 않으면 `BLOCKER`다.
- 재현된 `BLOCKER` 또는 `HIGH`가 하나라도 있으면 `COMPATIBLE`을 사용할 수 없다.
- 이 결함을 “HRNS가 false-success하지 않으므로 별도 축”이라고 분리해 verdict에서 제외하지 마라. HRNS-NOW가 공식 지원하는 `ValidateClosure`가 정상 상태에서도 완료되지 않는 것은 실제 통합 경로 결함이다.

## 시작 전 보호 절차

1. 양쪽 root의 파일 inventory를 기록한다.
2. HRNS-NOW에서 `git status --short`를 기록한다.
3. 수정 후보 파일의 SHA-256과 mtime을 기록한다.
4. 이미 존재하는 Codex/사용자 변경을 덮어쓰거나 되돌리지 않는다.
5. 작업 도중 후보 파일이 외부에서 바뀌면 이전 hash와 다시 비교하고 수동 병합한다.

현재 HRNS-NOW working tree에는 문서 재구성 및 기존 호환성 수정 결과가 함께 존재한다. `reset`, `restore`, `checkout`, `stash`, `clean`으로 정리하지 마라.

## 허용 범위와 금지 사항

허용:

- `D:\harness-kit`의 closure 관련 production script, smoke, `SMOKE_INDEX.md`, 관련 문서 수정
- 필요한 경우 HRNS-NOW의 process 결과 projection, 테스트, 현행 문서 수정
- `S:\tmp\hrns-now-closure-remediation-<timestamp>` 아래 격리 fixture 생성
- offline deterministic PowerShell smoke 및 Gradle 테스트 실행

금지:

- `D:\harness-workspaces` 아래 실제 사용자 workspace 접근 또는 수정
- `%APPDATA%\hrns-now\projects.json` 수정
- live Claude/Ollama/외부 LLM 호출
- 실제 사용자 프로젝트에서 onboarding, run-cycle, closure 실행
- HRNS parser requiredness 완화 또는 malformed State 허용
- 실패를 숨기기 위한 `try/catch` 삼키기, 무조건 exit 0, 테스트 skip
- `git add`, `commit`, `push`, `reset`, `restore`, `checkout`, `stash`, `clean`
- `.github/workflows/ci.yml` 수정: Ubuntu CI 문제는 별도 작업이며 이번 closure 수정과 섞지 않는다.

## P0-1. StrictMode scalar/array 결함 수정

`pre_handoff_validate.ps1`의 `Get-StringArray` 호출 계약을 먼저 확정하고 최소 수정하라.

필수 동작:

- 입력 `null` 또는 빈 값 → 안전한 0개 컬렉션
- 단일 문자열 또는 단일 원소 enumerable → 안전한 1개 컬렉션
- 복수 원소 enumerable → 모든 유효 문자열 보존
- blank/null 원소 제거
- Windows PowerShell 5.1 `Set-StrictMode -Version Latest`에서 `.Count` 접근 가능
- 기존 Python sidecar routing과 직접 실행 동작 보존

호출부에서 `@(...)`로 배열을 강제할지, 함수 반환 계약 자체를 고칠지는 실제 호출자 전수 검색 후 결정하라. 부분 수정으로 다른 호출자에 scalar 문제가 남지 않게 하되 불필요한 공용 API 확대는 금지한다.

## P0-2. 정상 closure 성공 계약 복구

완전히 유효한 격리 fixture를 만들어 실제 production 경로를 실행하라.

```powershell
D:\harness-kit\scripts\run-cycle.ps1 `
  -WorkspaceRoot <scratch-workspace> `
  -ProjectRoot <scratch-project> `
  -KitRoot D:\harness-kit `
  -Profile corp-default `
  -Date <date> `
  -SkipDoctor `
  -ValidateForClosure
```

정상 성공 시 다음을 모두 증명해야 한다.

- `pre_handoff_validate.ps1` 실제 native branch 실행
- 부모 exit code `0`
- `closure.validated=true`
- `closure.is_clean_handoff=true`
- top-level `closure_validated=true`
- top-level `clean_handoff=true`
- `pre_handoff_validated` 로그 존재
- `run_cycle_closure_validated` 로그 존재
- 실패 경고와 `pre_handoff_validation_failed` 로그 부재
- HRNS production `JsonWorkflowStateAdapter`가 결과를 `Success`로 읽음
- HRNS `ClosurePolicy`/projection이 clean closure로 일관되게 해석

fixture를 억지로 직접 완성 State로 쓰지 마라. 가능한 한 production onboarding/writer를 사용하고, 정상 execution-completed/queue-completed 조건에 필요한 최소 deterministic artifact만 명시적으로 준비하라. 어떤 필드를 fixture가 보강했는지 보고서에 기록한다.

## P0-3. 실패 종료 코드와 State 영속화 계약

현재 closure validation 실패는 State를 fail-closed로 남기지만 부모 `run-cycle.ps1`이 exit `0`으로 끝난다. 이 정책을 명시적으로 바로잡아라.

실패 fixture는 BOM, missing required artifact 또는 deterministic validator stub 중 production 계약을 가장 충실히 재현하는 방법을 사용한다.

필수 결과:

- child validator nonzero가 부모에 반영됨
- 부모 `run-cycle.ps1`도 nonzero로 종료
- 종료 전에 실패 State와 run log가 안전하게 영속화됨
- `closure.validated=false`
- `closure.is_clean_handoff=false`
- top-level closure/clean-handoff projection도 false
- `pre_handoff_validated`/`run_cycle_closure_validated` 성공 로그 부재
- `pre_handoff_validation_failed` 및 open-after-failure 로그 존재
- HRNS-NOW가 결과를 성공 tone/성공 알림으로 표시하지 않음
- exit code와 State 중 하나만 보고 서로 모순된 결론을 내리지 않음

단순히 catch 안에서 즉시 throw해 State 저장을 건너뛰지 마라. 실패를 기록한 뒤 최종 종료 코드가 nonzero가 되도록 control flow를 설계하고, timeout/cancel이나 다른 command의 종료 계약을 깨지 않는지 확인한다.

HRNS-NOW의 `ExecuteHarnessActionUseCase`, `PowerShellHarnessAdapter`, `RunStatusProjectionAssembler`, notification projection을 실제로 추적하라. 현재 코드가 nonzero + closure false를 이미 실패/경고로 안전하게 표시한다면 근거와 테스트만 추가하고 production 코드는 바꾸지 않는다. 성공처럼 보이는 경로가 있으면 최소 범위로 고친다.

## P0-4. false-green 테스트 제거

현재 offline suite는 closure 실패 전파만 검사하고 정상 성공을 검사하지 않아 81/81이면서 production closure가 항상 실패할 수 있다.

최소한 다음 회귀 검증을 추가하라.

1. Windows PowerShell 5.1 StrictMode에서 0/1/다중 원소 string-array 계약
2. valid fixture의 native `run-cycle -ValidateForClosure` 성공
3. invalid fixture의 child/parent nonzero 전파
4. 성공과 실패 각각의 persisted State 및 run_log
5. 가능하면 HRNS production adapter까지 연결한 성공/실패 읽기

새 smoke 예시 명칭:

```text
scripts/smoke/smoke-run-cycle-closure-success-contract.ps1
```

기존 `smoke-run-cycle-closure-failure-propagation.ps1`은 부모 exit code도 검증하도록 강화하라. 신규 smoke를 추가하면 `scripts/SMOKE_INDEX.md`의 inventory, automatic/offline array, 분류, 설명, 총 개수를 실제 파일 수에 맞춰 갱신한다. 관련 map/roadmap/calibration 파일에 하드코딩된 smoke 수가 있으면 전수 검색해 함께 정렬한다. 이전 숫자를 추정해 복사하지 말고 현재 inventory에서 재계산한다.

## P1. 보고서와 현행 문서 재판정

코드 수정 전 보고서 verdict는 적어도 `INCOMPATIBLE`이어야 한다. 새 정상 성공 테스트와 전체 검증이 모두 통과한 뒤에만 다시 판정한다.

다음 모순을 제거하라.

- 보고서 상단의 `HIGH 0`과 본문의 재현된 별도 HIGH 병존
- 재현된 HIGH가 있는데 `COMPATIBLE`인 상태
- `doc/documentation_guide.md`의 437 tests 및 해결 전 blocker 문구
- `doc/phase_reports/current_validation_reports.md`의 이전 verdict
- smoke total/offline count의 이전 수치

최종 verdict 선택 기준:

- closure 정상 경로가 여전히 실패하면 `INCOMPATIBLE`
- 재현된 BLOCKER/HIGH가 남으면 `COMPATIBLE` 금지
- 환경 때문에 필수 정상 성공 증거를 만들 수 없으면 `BLOCKED_BY_ENVIRONMENT`
- 모든 호환성 BLOCKER/HIGH가 닫히고 production-to-production 성공·실패 계약이 모두 검증된 경우에만 `COMPATIBLE` 또는 `COMPATIBLE_WITH_NONBLOCKING_GAPS`를 근거와 함께 선택

console-window-flash `UNVERIFIED`와 `Invoke-RunCycleWrapper` 미확정 exit-code 위험도 숨기지 말고 최종 verdict와의 관계를 명시한다.

보고서 마지막 줄의 marker는 본문 verdict와 정확히 일치해야 한다.

```text
HRNS_HARNESS_LIVE_COMPATIBILITY_AUDIT: <COMPATIBLE|COMPATIBLE_WITH_NONBLOCKING_GAPS|INCOMPATIBLE|BLOCKED_BY_ENVIRONMENT>
```

## 검증 순서

### 1. Targeted Harness 검증

- StrictMode 0/1/다중 배열 회귀
- 신규 정상 closure smoke
- 강화된 closure 실패 전파 smoke
- `smoke-run-cycle-native-child-exit-propagation.ps1`
- `smoke-workflow-state-fresh-ui-envelope.ps1`
- `smoke-validate-ops-guaranteed-envelope.ps1`

각 스크립트의 실제 case 수와 exit code를 기록한다.

### 2. Harness 전체 offline suite

`scripts/SMOKE_INDEX.md`의 현재 automatic execution array를 live 파일에서 직접 읽어 실행한다. 목록이나 개수를 별도 하드코딩하지 않는다.

필수 증거:

- 모든 listed smoke exit 0
- 최종 실행 수 = 현재 index 수
- live Claude/Ollama 호출 0
- live Kit root와 사용자 workspace에 fixture/log/backup 잔여물 없음

### 3. HRNS-NOW 전체 검증

```powershell
cd S:\dev\project\hrns_now
.\gradlew.bat check --rerun-tasks --no-daemon
```

모듈별 XML 결과를 다시 합산해 tests/failures/errors/skipped를 기록한다. 과거 442라는 수치를 하드코딩하지 않는다.

가능하면 다음 live opt-in contract test도 격리 fixture만 사용해 실행한다.

```powershell
$env:HRNS_LIVE_HARNESS_KIT_ROOT = 'D:\harness-kit'
.\gradlew.bat :infra:test --tests '*HarnessProductionContractTest*' --rerun-tasks --no-daemon
Remove-Item Env:HRNS_LIVE_HARNESS_KIT_ROOT
```

이 테스트가 closure 정상 경로를 다루지 않는다면 그것을 명시하고 신규 positive closure smoke로 증거를 보완한다.

### 4. 위생 검사

- HRNS `git diff --check`
- HRNS `git status --short`
- `D:\harness-kit\scratch` 및 `logs`의 이번 세션 생성물 확인
- 허용된 `S:\tmp\hrns-now-closure-remediation-*`만 정확한 경로 검증 후 제거
- 실제 사용자 workspace와 Registry 무변경 확인

## 완료 조건

다음이 모두 참이기 전에는 작업을 완료하거나 commit-ready라고 선언하지 마라.

- StrictMode scalar/array 결함 수정
- valid native closure가 실제로 성공
- invalid native closure가 부모 nonzero로 전파
- 성공/실패 State와 run log가 각각 정확
- HRNS production adapter/policy/projection 해석이 일관됨
- positive와 negative 회귀 테스트 존재
- Harness 전체 offline suite 통과
- HRNS 전체 Gradle check 통과
- 보고서와 현행 문서의 verdict/count/finding 정합
- scratch와 live root 위생 확인

## 최종 응답 형식

1. 최종 verdict
2. 재현한 근본 원인
3. 수정한 production 파일
4. 추가·수정한 회귀 테스트
5. 정상 closure 동적 증거
6. 실패 closure 동적 증거와 부모 exit code
7. Harness 전체 offline 결과
8. HRNS Gradle 결과
9. 남은 open/unverified 항목
10. 보고서 경로

Git 조작은 하지 않는다. 최종 응답에는 commit-ready 여부만 명시한다.
