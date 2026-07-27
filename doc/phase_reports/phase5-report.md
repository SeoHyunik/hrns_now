# Phase 5 Codex 독립 검증 보고서

## 진척도

- 대상 Phase: Phase 5 — 검증·Closure·복구 센터
- Verdict: PASS_WITH_FIXES
- 다음 Phase 진행 가능: 예

## 1. 검증 대상

- 저장소/브랜치: `S:\dev\project\hrns_now` / `harness-dev`
- Claude 커밋: 없음 (Claude는 Git 작업을 수행하지 않음)
- Codex 보정·구현 커밋: `9e6b267 feat: Phase 5 마감 검증과 복구 센터 구현`
- 검토 파일: `doc/hrns_now_claude_plan.md`, `doc/hrns_now_design_pattern.md`, `doc/phase_reports/phase5-report.md`, Phase 5 core/infra/composeApp diff, `D:\harness-kit\scripts\run-cycle.ps1`, `D:\harness-kit\claude\hooks\pre_handoff_validate.ps1`, `D:\harness-kit\python\hooks\pre_handoff_validate.py`
- 기준 계획 절: 최종 계획 Phase 5, 설계 문서 §11·§16·§17·§19·§20·§21
- Phase 식별 방식: 사용자 제공 Phase 5 요약과 `doc/phase_reports/phase5-report.md`

## 2. 핵심 판정

`-ValidateForClosure`는 실제 `run-cycle.ps1`에서 wrapper gate와 같은 pass에 결합하면 예외를 내며, Python/PowerShell pre-handoff validator만 실행한 뒤 `closure.validated`, `closure.is_clean_handoff`, 상태 projection을 갱신한다. `ValidateClosure` command/encoder/mapper는 이 계약과 일치한다.

Closure 판단은 `ClosurePolicy`의 순수 typed decision으로 분리했고, UI·ViewModel 경계에서도 ActionPolicy와 교집합으로 적용했다. Codex는 disabled CTA만으로는 stale/direct event를 막지 못하는 결함을 보정했다. dirty repository는 확인 전 실행되지 않으며 Recovery 화면의 명시적 acknowledgement event로만 실행된다.

Recovery는 stop reason/queue marker별 3분리 안내와 closure checklist를 표시한다. continuity·usage ledger·failure history는 raw session ID, request thread, path, payload를 옮기지 않는 읽기 전용 집계 projection으로만 표시하며 CTA 근거로 사용하지 않는다.

## 3. 발견 사항

### Critical

- 없음

### Major

- 수정 완료 — `AppViewModel`은 일반 `ActionRequested(RunClosureValidation)`을 곧바로 process 실행으로 연결해, ClosurePolicy가 `Blocked`인 상태에서도 direct/stale event가 CTA UI 비활성화를 우회할 수 있었다. `ClosureValidationRequested(acknowledged)`와 ViewModel 경계 재검사를 추가했다. `composeApp/src/jvmMain/kotlin/io/hrns_now/app/presentation/viewmodel/AppViewModel.kt`
- 수정 완료 — `CommandLineGitStatusAdapter`가 stdout 후 stderr를 순차 drain해 stderr가 큰 child에서 교착될 수 있었다. 두 stream을 병렬 drain하고 interrupt flag를 보존했다. 또한 지정 root의 `.git` 확인으로 ambient parent repository 탐색을 막고 회귀 테스트를 추가했다. `infra/src/main/kotlin/io/hrns_now/infra/git/CommandLineGitStatusAdapter.kt`
- 수정 완료 — Recovery 화면에 계획서가 요구한 continuity/usage ledger/failure history 진단 요약이 없었다. raw 식별자를 보존하지 않는 `RecoveryDiagnosticsPort`와 filesystem adapter/projection을 추가했다. `core/src/main/kotlin/io/hrns_now/core/port/RecoveryDiagnosticsPort.kt`, `infra/src/main/kotlin/io/hrns_now/infra/recovery/WorkspaceRecoveryDiagnosticsAdapter.kt`

### Minor

- Claude 보고서의 `328` test 수 주장은 현재 JVM test XML의 실제 관측치(`core 73`, `infra 82`, `composeApp 68`, 합계 `223`)와 일치하지 않았다. 테스트 통과 여부는 실제 Gradle 실행으로 확인했으며, 최종 보고서에는 관측치를 사용한다.
- live Harness pre-handoff validator는 `DAILY_HANDOFF.md`의 존재·UTF-8·non-empty만 보며 template placeholder를 구조적으로 판별하지 않는다. UI가 Markdown 문구로 Closure를 판정하는 것은 설계 불변식 위반이므로 임의 보정하지 않았다.

## 4. SOLID·설계 패턴 평가

| 항목 | 판정 | 근거 |
|---|---|---|
| SRP | PASS | ClosurePolicy, Git 상태 adapter, recovery diagnostics adapter, Compose projection 책임을 분리했다. |
| OCP | PASS | ClosureDecision sealed type과 typed `UiAction`/`HarnessCommand`로 분기 확장을 국소화했다. |
| LSP | PASS | `GitStatusPort`와 `RecoveryDiagnosticsPort`는 실패 시 typed non-authoritative 결과를 반환한다. |
| ISP | PASS | Git 상태와 recovery diagnostics는 소비 목적별 작은 read-only port다. |
| DIP | PASS | core는 Compose, ProcessBuilder, filesystem, JSON 구현에 의존하지 않는다. |
| 계층 의존 방향 | PASS | Compose → core port/use case ← infra adapter 방향을 유지했다. |
| 패턴 적정성 | PASS | Policy/Command/Adapter/Projection을 필요한 경계에서만 사용했다. |
| 과도한 추상화 | PASS | diagnostics는 단일 Recovery 소비자의 응집된 read-only summary로 제한했다. |

## 5. 수행한 수정

- `ClosurePolicy`에 unknown execution wrapper fail-closed 조건과 회귀 테스트를 추가했다.
- acknowledgement를 typed UI event로 승격해 UI local checkbox가 execution guard를 우회하지 못하게 했다.
- Recovery diagnostics를 optional·비권위적·raw 식별자 비보존 집계로 추가했다.
- Git 상태 process의 두 stream 동시 drain, interruption 보존, ambient parent repository 방지를 보강했다.

모든 수정은 Phase 5 Closure/Recovery 범위에 한정했으며 `WORKFLOW_STATE.json`과 Harness workspace에는 쓰지 않는다.

## 6. 검증 결과

| 검증 | 명령 | 결과 |
|---|---|---|
| Targeted | `:core:test --tests ClosurePolicyTest` | PASS |
| Targeted | `:infra:test --tests CommandLineGitStatusAdapterTest --tests WorkspaceRecoveryDiagnosticsAdapterTest` | PASS |
| Targeted | `:composeApp:jvmTest --tests AppViewModelTest --tests RecoveryProjectionsTest` | PASS |
| Module | `:core:test :infra:test :composeApp:jvmTest --rerun-tasks` | PASS |
| Full | `./gradlew.bat check` | PASS |
| Harness contract | live `run-cycle.ps1`, PowerShell/Python pre-handoff validator read-only inspection | PASS |

## 7. Git 상태와 커밋

- 작업 전 상태: Phase 5 구현 변경과 report가 미커밋 상태였음
- 작업 후 상태: Phase 5 코드·테스트는 `9e6b267`에 커밋됨
- 커밋 SHA: `9e6b267`
- 커밋 메시지: `feat: Phase 5 마감 검증과 복구 센터 구현`
- 미커밋 잔여: 사용자 소유 untracked `doc/hrns_now_packaging_plan.md`, 본 검증 report와 다음 Claude prompt
- push 여부: 수행하지 않음

## 8. 잔여 위험

- Harness는 현재 handoff template placeholder를 structured closure signal로 제공하지 않는다. UI는 Markdown prose로 Closure 가능 여부를 판단하지 않으며, 이를 보완하려면 Harness 측 validator/state contract 변경 승인이 필요하다.
- recovery diagnostics는 optional reference information이며 State/CTA의 권위가 아니다.

## 9. 다음 단계

- NEXT_ALLOWED_PHASE: Phase 6 — Windows 패키징·배포
- Claude에게 전달할 다음 작업: MSI 전용 MVP packaging, clean Windows install smoke, kit 외부 참조와 경로 이식성 검증
- 다음 Phase 진입 전 조건: `9e6b267`의 Phase 5 회귀를 유지하고 `doc/claude_prompts/phase6-msi-distribution.md` 전체를 읽을 것
