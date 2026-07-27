# Phase 2 독립 검증 보고서 — Harness JSON Contract / Compatibility Handshake

검증일: 2026-07-27
검증자·보정·커밋: Codex
대상: `S:\dev\project\hrns_now` 및 canonical live Harness `D:\harness-kit`

## 진척도

- 대상 Phase: Phase 2 — Harness JSON Contract / Compatibility Handshake
- Verdict: PASS_WITH_FIXES
- 다음 Phase 진행 가능: 예

## 1. 검증 대상

- 저장소/브랜치: `S:\dev\project\hrns_now`, `harness-dev`
- Claude 커밋: 없음 — Claude는 커밋 권한이 없으며 Phase 2 변경은 미커밋 working tree와 `D:\harness-kit` live tree에서 검토했다.
- Codex 보정 커밋: `e8a268c` — `feat: Phase 2 Harness JSON 호환성 계약 구현 및 보정`
- 검토 파일:
  - HRNS-NOW: `core`의 manifest/domain/policy/port, `infra` JSON adapter, Cockpit projection/assembler, `AppViewModel`, Compose 화면과 회귀 테스트
  - Harness: `scripts/doctor.ps1`, `scripts/validate-ops.ps1`, `scripts/lib/state-surface.ps1`, JSON contract smoke 3종, `kit-version.json`, `scripts/SMOKE_INDEX.md`, calibration 및 Kit map/운영/상태 문서
- 기준 계획 절: `doc/hrns_now_claude_plan.md`의 Phase 2 및 Harness 불변 계약
- 설계 기준: `doc/hrns_now_design_pattern.md`의 Hexagonal/ACL 경계, 순수 Policy, Projection, ViewModel orchestration 규칙
- Phase 식별 방식: 사용자가 제공한 Phase 2 완료 보고서와 `doc/phase_reports/phase2-report.md`를 실제 파일·diff·smoke 결과와 대조해 확정했다.

Harness는 의도적으로 Git 저장소가 아니다. Claude의 수정 전 백업 `D:\backup\harness-kit0724-2.zip`과 Codex 보정 전 백업 `D:\backup\harness-kit0727-1.zip`(1,069,307 bytes, 2026-07-27 10:08)을 확인했다. `git init`·push는 수행하지 않았다.

## 2. 핵심 판정

Harness의 `doctor.ps1`/`validate-ops.ps1`는 기본 텍스트 모드·exit code를 유지하면서 `-Json`에서 단일 JSON stdout을 제공한다. 실제 `kit-version.json`의 `kit_version=2026.07.23`, `state_schema_version=1.0`, `ui_contract_version=1.0`은 smoke와 HRNS-NOW typed reader가 모두 검증했다.

HRNS-NOW는 manifest file I/O를 infra adapter에 격리하고, core의 `CompatibilityPolicy`가 순수 typed 결과로 fail-closed 판정을 한다. Compose는 projection만 렌더링하고, `AppViewModel`은 IO dispatcher에서 읽기·polling을 orchestration한다. Phase 3 process runner나 Phase 4 실행/state write는 선구현되지 않았다.

다만 최초 구현에는 호환성 진단 미표시, manifest 변경 polling 미감지, 민감 형식 문자열이 Harness 진단에 그대로 남을 수 있는 문제, StrictMode 후 profile-aware validate 회귀 가능성이 있었다. 모두 현재 Phase 범위에서 보정했고 회귀 검증을 통과했다.

## 3. 발견 사항

### Critical

- 없음

### Major

- 해결됨 — `CockpitProjection.compatibilityDiagnostics`가 조립되지만 `Screens.kt`에서 렌더링되지 않았다. `composeApp/src/jvmMain/kotlin/io/hrns_now/app/ui/Screens.kt`에 별도 호환성 진단 card를 추가했다.
- 해결됨 — `AppViewModel`의 polling이 `WORKFLOW_STATE.json` mtime만 봐서 `kit-version.json`만 변경된 경우 stale compatibility를 유지했다. manifest detail을 별도로 비교하고 같은 mtime에서도 reload하도록 보정·회귀 테스트했다.
- 해결됨 — Harness JSON diagnostics가 `sk-` 등 secret-shaped 입력을 echo할 수 있었다. `D:\harness-kit\scripts\doctor.ps1`, `validate-ops.ps1`의 text/JSON check message와 header에 공통 masking을 적용하고 failure smoke로 확인했다.
- 해결됨 — `Set-StrictMode -Version Latest` 적용 뒤 optional artifact/property의 scalar/null `.Count` 및 dictionary 접근으로 `validate-ops.ps1` profile-aware 검증이 fallback되거나 text warning이 달라질 수 있었다. `validate-ops.ps1`과 `state-surface.ps1`을 strict-safe하게 보정하고, live fixture의 변경 전/후 text·stderr·exit code를 Kit root 경로만 정규화하여 동등함을 확인했다.

### Minor

- 해결됨 — adapter test가 필수 field 전부를 검사한다고 주장하면서 `state_schema_version`, `ui_contract_version` 누락 case가 없었다. 두 case를 추가했다.
- 해결됨 — JSON contract smoke의 no-write 검증이 file count만 비교했다. size/mtime/SHA-256 snapshot 비교로 강화했다.
- 해결됨 — `OPERATING_GUIDE.md`의 wrapper log subtree와 sibling continuity/usage log를 혼동할 수 있는 표현, `STATE_MODEL.md`의 UI 보증 field/queue 권위 표현을 live 계약에 맞춰 정정했다.

## 4. SOLID·설계 패턴 평가

| 항목 | 판정 | 근거 |
|---|---|---|
| SRP | PASS | JSON read/decode는 `JsonKitVersionManifestAdapter`, compatibility 결정은 `CompatibilityPolicy`, UI 표현은 assembler/Compose, lifecycle·polling은 ViewModel로 분리됐다. |
| OCP | PASS | manifest read result와 compatibility detail이 sealed type이므로 새 원인/버전 case를 문자열 분기 확산 없이 추가할 수 있다. |
| LSP | PASS | `KitVersionManifestPort`의 Missing/Malformed/Success 의미를 fake와 JSON adapter test가 동일하게 검증한다. |
| ISP | PASS | manifest 조회 port는 단일 read operation이며 write/process API를 포함하지 않는다. |
| DIP | PASS | core는 Compose, `Json`, filesystem 구현을 참조하지 않고 infra가 core port를 구현한다. |
| 계층 의존 방향 | PASS | `core ← infra`, `core ← composeApp` 방향을 유지했고 Compose가 파일/JSON을 직접 읽지 않는다. |
| 패턴 적정성 | PASS | Harness manifest DTO는 ACL/Adapter, 호환성 판단은 Policy, 화면 값은 Projection을 사용한다. |
| 과도한 추상화 | PASS | Phase 2에 필요한 manifest read port 하나만 추가했으며 Process/Lock 등 Phase 3 계층은 만들지 않았다. |

## 5. 수행한 수정

- HRNS-NOW (`e8a268c`): typed manifest reader/policy/port와 tests를 커밋하고, compatibility detail의 fail-closed projection·화면 표시·manifest polling 및 missing field 테스트를 보강했다. ViewModel의 file access는 계속 IO dispatcher에만 남고, late-write generation guard와 기존 `StateFlow` 단일 흐름을 유지한다.
- Harness (Git 비관리 live tree): secret-shaped diagnostic masking, StrictMode 안전성, 초기 실패 text mode 보존, content-aware no-write smoke, UI 소비 문서의 계약 표현을 최소 보정했다. `WORKFLOW_STATE.json` 쓰기나 wrapper mode 추가는 하지 않았다.
- 부작용 검토: 실제 `D:\harness-workspaces\auziraum\2026-06-04` fixture와 미존재 root/day failure path에서 text/JSON/exit contract를 확인했다. Harness normal text output은 backup tree와 Kit root 경로 차이만 정규화하면 stdout/stderr/exit가 동등하다.

## 6. 검증 결과

| 검증 | 명령 | 결과 |
|---|---|---|
| Targeted | `./gradlew.bat :core:test --tests KitVersionTest --tests CompatibilityPolicyTest` | PASS |
| Targeted | `./gradlew.bat :infra:test --tests JsonKitVersionManifestAdapterTest` | PASS |
| Targeted | `./gradlew.bat :composeApp:jvmTest --tests CockpitProjectionAssemblerTest --tests AppViewModelTest` | PASS |
| Module | `./gradlew.bat :core:test :infra:test :composeApp:jvmTest` | PASS |
| Full | `./gradlew.bat check` | PASS |
| Harness contract smoke | `smoke-doctor-json-contract.ps1`, `smoke-validate-ops-json-contract.ps1`, `smoke-kit-version-contract.ps1` | PASS — 13 + 15 + 13 cases |
| Harness automatic/offline smoke | `scripts/SMOKE_INDEX.md`의 실제 64개 목록을 추출해 순차 실행 | PASS — 64/64, 0 failure |
| Docs scan | `scan-secondary-llm-docs-mismatch.ps1 -WorkspaceRoot D:\harness-workspaces\auziraum -Date 2026-07-27 -KitRoot D:\harness-kit` | PASS — status=ok, blocker/error/warning=0 |

## 7. Git 상태와 커밋

- 작업 전 상태: HEAD `ee75a95` (`fix: Phase 1D Registry와 날짜 선택 안전성 보강`), Phase 2 HRNS-NOW 변경은 미커밋이었다. `doc/hrns_now_packaging_plan.md`는 관련 없는 사용자 untracked 파일이었다.
- 작업 후 상태: Phase 2 코드·테스트 보정은 `e8a268c`에만 포함했다. 보고서/다음 prompt 문서는 별도 docs 커밋으로 기록한다.
- 커밋 SHA: `e8a268c`
- 커밋 메시지: `feat: Phase 2 Harness JSON 호환성 계약 구현 및 보정`
- 미커밋 잔여: `doc/hrns_now_packaging_plan.md`는 사용자 파일로 유지하며 stage하지 않는다.
- push 여부: 수행하지 않음

## 8. 잔여 위험

- 현재 Phase 미완료: 없음.
- 운영 위험: Harness live tree는 Git 이력이 없으므로 향후 Harness 변경 전에도 반드시 zip backup을 남겨야 한다. Phase 2 보정 전 snapshot은 `D:\backup\harness-kit0727-1.zip`이다.
- 후속 Phase 항목: Windows process tree cancellation, masking이 적용된 process output, per-machine lock/stale lock, external State-change heuristic은 Phase 3 범위이며 아직 구현하지 않았다.

## 9. 다음 단계

- NEXT_ALLOWED_PHASE: Phase 3 — Process Adapter + Lock
- Claude에게 전달할 다음 작업: `doc/claude_prompts/phase3-process-adapter-lock.md`에 따라 typed read-only Doctor/Validate process adapter와 lock 기반을 구현하고 `doc/phase_reports/phase3-report.md`에 증거를 남긴다. Claude는 commit하지 않는다.
- 다음 Phase 진입 전 조건: `e8a268c` 이후 상태에서 시작하고, `doc/hrns_now_packaging_plan.md`를 수정·stage하지 않으며, ProcessBuilder/lock/secret handling의 테스트 가능한 경계를 Phase 3 안에서만 추가한다.