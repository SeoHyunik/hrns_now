# Phase 1B CTA Policy 검증·보정 보고서

검증일: 2026-07-24
검증자: Codex
대상 저장소: `S:\dev\project\hrns_now`
브랜치: `harness-dev`
검증 전 HEAD: `8543787` (`fix: Phase 1A 상태 리더 안전성 보강`)
Claude 커밋: 없음 — Claude는 working tree만 작성하고 Codex만 커밋한다.

---

## 진척도

- 대상 Phase: Phase 1B — CTA Policy
- Verdict: PASS_WITH_FIXES
- 다음 Phase 진행 가능: 예
- NEXT_ALLOWED_PHASE: Phase 1C — 실데이터 Cockpit

## 1. 검증 대상

- Claude 산출물: `ActionContext`, `UiAction`, `RecommendedActions`, `ActionPolicy`, `ActionPolicyTest` 및 최초 Phase 보고서
- 기준 계획: `doc/hrns_now_claude_plan.md` Phase 1B, 부록 B·C
- 설계 기준: `doc/hrns_now_design_pattern.md`의 Hexagonal Architecture, State/Policy, MVVM/UDF, CQRS-lite, unknown 보존, 계층 의존 규칙
- Phase 식별 방식: 사용자가 `doc/phase_reports/phase1b-report.md`를 명시했고 working tree 신규 파일도 Phase 1B 범위와 일치함
- 선행 Gate: Phase 1A Codex 커밋 `8543787` PASS_WITH_FIXES 확인

## 2. 핵심 판정

Claude 구현은 typed `UiAction`, 상태 없는 순수 `ActionPolicy`, `RecommendedActions` 불변식, 과거 날짜·compatibility·process lock·malformed fail-closed, validation-only의 별도 identity 등 Phase 1B의 기본 구조를 올바르게 잡았다. `core` 정책은 Compose, 파일 I/O, JSON DTO, process 구현에 의존하지 않아 설계 방향도 적합했다.

그러나 최초 구현은 `execution_ready`에서 `ActiveSliceKind.Code`만 주입하면 queue가 비어 있고 active card/slice와 authorized target이 없어도 실행 CTA를 허용했다. 일부 실존 blocking stop reason과 `QueueBlockedReason.Other`도 실행을 잠그지 않았고, 외부 parser/unknown 원문을 사용자용 `blockedReason`에 그대로 포함했다. 이는 Harness 상태 진실, authorized target, fail-closed, raw secret/session 비표시 계약을 위반하므로 PASS할 수 없었다.

Codex가 현재 Phase 범위에서 정책 guard와 결정표 테스트를 보정했다. 최종 정책은 active queue 계약과 안전 flag가 검증된 경우에만 code/doc/validation CTA를 열고, code/doc에는 nonblank authorized target을 추가 요구한다. validation-only는 source edit target 없이도 별도 typed action으로 표현하되 어떤 process command에도 연결하지 않는다. 모든 검증 통과 후 Phase 1C 진입을 허용한다.

## 3. 발견 사항

### Critical

- **실행 선행조건 누락** — 최초 `ActionPolicy.executionReadyActions`는 phase, queue status, active card/slice, authorized target, ops validation을 검사하지 않았다. `queue.status=empty`, pointer·target null인 상태에서도 `ActiveSliceKind.Code` 입력 하나로 `RunCodeSlice`가 허용됐다. `core/src/main/kotlin/io/hrns_now/core/domain/policy/ActionPolicy.kt`에서 보정하고 변형 회귀 테스트를 추가했다.

### Major

- **blocking stop reason 부분 처리** — 최초 정책은 context limit, usage limit, manual prerequisite, wrapper exception 네 종류만 차단했다. `dispatch_contract_mismatch`, timeout, empty/short response, budget stop, transient overload가 `execution_ready`와 결합하면 실행이 열릴 수 있었다. 실측된 blocking 계열 11종을 단일 exhaustive 분기로 차단했다.
- **알 수 없는 queue 차단 사유 미처리** — `QueueBlockedReason.Other(raw)`가 일반 상태 분기로 통과했다. unknown gate에 포함해 Recovery Center 외 mutating action을 차단했다.
- **외부 원문 표시 위험** — malformed/encoding/schema 및 unknown domain의 raw 문자열이 `blockedReason`에 포함됐다. raw 값은 domain 진단 데이터에 보존하되 사용자 정책 문구에는 포함하지 않도록 변경하고 secret 유사 문자열 비노출 테스트를 추가했다.
- **상태 완료 flag 불일치 허용** — `humanActionRequired`, `executionCompleted`, `closureValidated`, `closure.validated` 불일치가 CTA에 반영되지 않았다. 사용자 확인 상태와 불일치 상태는 fail-closed하고, 완료 상태에서는 중복 실행을 열지 않도록 보정했다.
- **결정표 exact 검증 부족** — 최초 테스트는 주로 primary와 일부 금지 action만 확인했다. 계획서 부록 B 20개 행을 데이터 기반으로 구성해 각 행의 `primary`와 전체 `allowed` set을 정확히 비교하도록 변경했다.

### Minor

- validation-only action을 `ReviewValidationOnlySlice`에서 계획서 용어와 맞는 `RunValidationSlice`로 정렬했다. 이 action은 `-RunExecutionWrapper validation`을 뜻하지 않으며 Phase 4 실계약 확인 전 process command와 연결하지 않는다.
- 사용되지 않는 `RecommendedActions.none` 편의 factory를 제거해 현재 Phase에 불필요한 API 표면을 줄였다.

## 4. SOLID·설계 패턴 평가

| 항목 | 판정 | 근거 |
|---|---|---|
| SRP | PASS | context/model/policy/result 책임이 분리되고 정책은 CTA 결정만 수행 |
| OCP | PASS | sealed domain 값과 exhaustive 분기, unknown fail-closed로 신규 외부 값에 안전 |
| LSP | PASS | Phase 1A `StateReadResult` 의미를 변경하지 않고 Success/실패 결과를 동일하게 소비 |
| ISP | PASS | Phase 1B에서 Reader·Registry·Process API를 정책에 혼합하지 않음 |
| DIP | PASS | core 정책이 Compose, kotlinx serialization, NIO 구현, ProcessBuilder에 의존하지 않음 |
| 계층 의존 방향 | PASS | 외부 상태는 Phase 1A ACL이 domain으로 변환하고 CTA는 domain만 소비 |
| 패턴 적정성 | PASS | Policy/State/Result 패턴을 필요한 수준으로만 적용 |
| 과도한 추상화 | PASS | 구현체 interface·factory 계층을 추가하지 않고 unused helper 제거 |

## 5. 수행한 수정

- `core/.../model/UiAction.kt`
  - `RunValidationSlice` typed action으로 계획서 명칭 정렬
  - fake validation wrapper와 command 미연결 경계 문서화
- `core/.../model/RecommendedActions.kt`
  - `primary ∈ allowed` 불변식 유지
  - 미사용 factory 제거
- `core/.../policy/ActionPolicy.kt`
  - active queue pointer, phase, ops validation, completion flag 기반 실행 guard 추가
  - code/doc에 authorized target 필수화; validation-only에는 source target을 강제하지 않음
  - 실존 blocking stop reason 전부 Recovery로 fail-closed
  - human action, execution/closure flag 불일치, unknown queue reason 차단
  - 외부 raw parser/domain 원문을 사용자 문구에서 제거
  - 공통 Recovery 결과 조립 중복 최소화
- `core/.../policy/ActionPolicyTest.kt`
  - 부록 B 20행 exact 결정표
  - 11종 blocking stop reason
  - queue/pointer/target/ops/phase 실행 계약 변형
  - 완료 flag·human action 불일치
  - unknown/raw 비노출
  - validation-only fake wrapper 방지
  - live `execution_blocked + dispatch_contract_mismatch` 동등 fixture

부작용 검토: Process 실행, Registry, 실제 command mapping, Compose ViewModel은 추가하지 않았다. `WORKFLOW_STATE.json` 읽기/쓰기 구현과 Harness Kit 저장소는 변경하지 않았다.

## 6. 검증 결과

| 검증 | 명령 | 결과 |
|---|---|---|
| Targeted | `.\gradlew.bat :core:test --tests "io.hrns_now.core.domain.policy.ActionPolicyTest" --rerun-tasks --no-daemon --console=plain` | PASS — 11 test methods, 실패 0 |
| Module | `.\gradlew.bat :core:test :infra:test :composeApp:jvmTest --rerun-tasks --no-daemon --console=plain` | PASS — core 32, infra 45, Compose 1, 총 78 tests |
| Full | `.\gradlew.bat check --no-daemon --console=plain` | PASS |
| CI/Smoke | GitHub Actions 원격 실행 | 미실행 — push하지 않음. 로컬 workflow task와 전체 check는 통과 |

## 7. Git 상태와 커밋

- 작업 전 상태: Phase 1B 소스 5개와 보고서 1개가 untracked인 Claude working tree
- Codex 보정 커밋: 본 보고서·Phase 1C prompt와 함께 별도 한글 Conventional Commit으로 생성
- 커밋 메시지: `fix: Phase 1B CTA 정책 안전성 보강`
- push 여부: 수행하지 않음
- 최종 SHA와 잔여 상태: Codex 최종 응답에 기록

## 8. 잔여 위험

- `ActiveSliceKind`는 Phase 1B 정책 입력이다. 실제 active slice 상세의 typed projection/조립은 Phase 1C에서 구현해야 하며 `RawJsonValue` 문자열 검색으로 추론하면 안 된다.
- `RunValidationSlice`의 실제 PowerShell mapping은 Phase 4 착수 시 live Harness 계약으로 확정한다. Phase 1C에서는 어떤 command에도 연결하지 않는다.
- compatibility와 boundary의 실제 adapter는 각각 Phase 2/1D 경계다. Phase 1C는 확인되지 않은 값을 Supported/Valid로 임의 승격하면 안 된다.
- remote GitHub Actions는 push 금지 규칙에 따라 실행하지 않았다.

## 9. 다음 단계

- NEXT_ALLOWED_PHASE: Phase 1C — 실데이터 Cockpit
- Claude에게 전달할 다음 작업: `doc/claude_prompts/phase1c-live-cockpit.md`
- 다음 Phase 진입 전 조건: 본 Codex 커밋을 HEAD로 유지하고 Phase 1B fail-closed guard와 exact 결정표를 회귀 통과할 것
- Phase 1D, Phase 2, Process Adapter, command 실행을 선구현하지 말 것
