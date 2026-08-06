# HRNS-NOW 현행 계획과 외부 계약

- 기준일: 2026-08-06
- 대상: 현재 `harness-dev` production source
- 외부 통합 기준: 현재 live `D:\harness-kit`
- 성격: 구현 연혁이 아니라 현재 불변식, 구현 상태, 우선순위와 완료 Gate의 정본

파일명은 production KDoc의 기존 참조를 보존하기 위해 유지한다. 완료된 Phase 실행 계획과 시점별 결과는 현재 계약이 아니며 Git 이력에서만 조회한다.

# 0. 현재 판정

## 0.1 구현된 제품 표면

- `WORKFLOW_STATE.json` typed reader와 fail-closed CTA policy
- Cockpit, Strategy, Run, Recovery, Closure projection
- 프로젝트 Registry, 활성 프로젝트 선택, 날짜 탐색
- Doctor, Validate-Ops, onboarding, bootstrap, planning, replan, code/doc execution, closure의 typed command
- per-project/day lock, heartbeat, timeout, cancel, Windows child tree 종료
- 요청 저장의 낙관적 동시성 제어
- Windows MSI, release app image, bundled JRE, 아이콘 패키징
- source checkout 상대 DefaultKit과 명시적 ExternalKit runtime source

## 0.2 검증 기준선

2026-08-06 강제 재실행 결과는 `core` 141, `infra` 174, `composeApp` 122로 총 437 tests이며 failures/errors/skipped는 0이다.

등록된 외부 Kit에서 Doctor는 167 checks, Validate-Ops는 19 checks로 각각 `overall=ok`였다. 이 진단 성공은 State production parser 호환성을 자동 보장하지 않는다.

## 0.3 현재 차단 결함

fresh `enter-project.ps1` 재현은 exit 0, bridge 3/3, daily 4/4, State schema 1.0, Doctor/Validate-Ops `overall=ok`였다. 그러나 초기 State에는 live Harness 문서가 UI 보장 필드로 선언하고 HRNS-NOW `WorkflowStateMapper`가 필수로 요구하는 최상위 `required_next_action`이 없었다.

따라서 온보딩의 5중 성공 Gate 중 `StateReadResult.Success`가 실패할 수 있다. 이 문제는 표준 onboarding을 막으므로 해결 전 현재 live Kit 조합을 완전 호환으로 판정하지 않는다. [전수 호환성 감사](./claude_prompts/harness-kit-live-compatibility-audit.md)가 같은 유형의 추가 drift를 조사한다.

# 1. 현재 제품 상태

## 1.1 확인된 영역

- 모듈 경계와 dependency 방향
- State, Registry, manifest JSON ACL
- typed policy와 action-to-command mapping
- public entrypoint argument encoding
- JSON diagnostics parsing과 secret masking
- workspace artifact와 bridge probe
- Registry atomic write와 corruption quarantine
- request inbox optimistic concurrency
- package build와 app image 기동

## 1.2 미완료 영역

1. live Harness Kit과의 production-to-production 전수 호환성
2. 호환성 blocker 해결 후 native onboarding과 daily flow 사용자 QA
3. clean Windows release MSI 설치 lifecycle
4. owner-approved Harness Runtime artifact 기반 bundle
5. 코드 서명, 업데이트/롤백, 라이선스, portable data mode

## 1.3 판정 원칙

- 테스트가 녹색이어도 live writer artifact가 production parser를 통과하지 못하면 호환이 아니다.
- 과거 보고서의 `PASS`나 테스트 수를 현재 판정으로 재사용하지 않는다.
- native UI는 실제 사용자 클릭·관찰 없이 완료하지 않는다.
- packaging 성공과 release 승인을 구분한다.

# 2. 제품 불변 원칙

## 2.1 상태 진실

- runtime truth는 `WORKFLOW_STATE.json`이다.
- UI는 State를 직접 쓰지 않는다.
- 화면 문구나 로그 문자열이 아니라 typed State와 policy로 CTA를 결정한다.
- unknown, malformed, unsupported, partial write는 fail-closed한다.
- `queue.active`는 `card_id`/`slice_id` pointer다. wrapper나 target을 여기서 발명하지 않는다.

## 2.2 파일 소유권

| 소유자 | 쓰기 허용 범위 |
|---|---|
| Harness Kit | external workspace의 State, Strategy, Handoff, 실행 로그와 onboarding bridge |
| HRNS-NOW | 앱 Registry, UI preference, lock, 사용자가 저장한 `REQUEST_INBOX.md` 항목 |
| Repository | 사용자 코드와 Harness bridge 3개 |

오늘 required surface는 `REQUEST_INBOX.md`, `TODAY_STRATEGY.md`, `DAILY_HANDOFF.md`, `WORKFLOW_STATE.json` 정확히 4개다. `REQUEST_STRUCTURED.md`는 optional이며 `WORK_QUEUE.json`, `WORKDAY_STATE.json`은 명시적 legacy compatibility 외에는 readiness 근거가 아니다.

## 2.3 실행 원칙

- UI action은 `ActionPolicy`를 통과한 typed action만 실행한다.
- action은 typed `HarnessCommand`와 argument list로 변환한다.
- shell 문자열이나 자유 형식 PowerShell console을 제공하지 않는다.
- 실행 전에 lock을 획득하고 process 종료 후 lock을 보유한 채 State를 다시 읽은 뒤 해제한다.
- stdout 성공 문구 하나로 성공을 판정하지 않는다.
- planning, replan, execution, closure는 각각 live public option을 사용한다.

## 2.4 경계 원칙

Harness Kit root, workspace root, repository root의 동일 경로 3종과 양방향 포함 관계 6종을 모두 검사한다. junction/symlink를 고려한 real path와 lexical absolute path를 함께 사용한다.

존재하지 않는 leaf는 가장 가까운 기존 ancestor 기준으로 비교한다. 경계를 증명할 수 없으면 mutating action을 허용하지 않는다. Registry, UI preference, lock은 세 root 밖의 앱 소유 경로에 둔다.

## 2.5 비밀정보 원칙

- raw session ID, token, secret, raw response, raw log를 Registry에 저장하지 않는다.
- 외부 JSON extension은 sanitizer를 통과한다.
- error, check, snippet은 projection 전에 masking한다.
- 보고서와 QA 캡처에도 secret-shaped 값을 남기지 않는다.
- Secondary LLM 결과는 default-off, non-authoritative이며 자동 채택하지 않는다.

# 3. 현재 아키텍처

## 3.1 모듈 구조

```text
core: domain model / policy / port / use case / typed result
  ↑
infra: JSON / filesystem / PowerShell / process / Registry / Git / lock adapter
  ↑
composeApp: composition root / ViewModel / projection / Compose UI
```

dependency는 바깥에서 안쪽으로 향한다. `core`는 Compose, JSON DTO, `Files`, `ProcessBuilder`, `%APPDATA%`를 알지 않는다.

## 3.2 핵심 도메인 모델

`HarnessProject`는 `ProjectId`, 표시명, `RuntimeSource`, workspace, repository, profile, 선택 날짜와 안전한 진단 요약만 가진다.

`RuntimeSource`는 `DefaultKit`과 `ExternalKit(root)`이다. DefaultKit는 source checkout 상대 `.local/harness-kit` 선택만 Registry에 저장하고 절대 경로를 저장하지 않는다. ExternalKit만 사용자가 선택한 absolute root를 저장한다. Registry schema 1.0의 legacy `kit_root` entry는 읽기 시점에 ExternalKit으로 해석한다.

Registry에는 secret, raw output, raw State를 저장하지 않는다.

## 3.3 typed CTA

안정된 machine ID인 `UiAction`과 locale-dependent label을 분리한다. UI event는 action ID를 전달하고 버튼 문구를 다시 파싱하지 않는다.

## 3.4 상태 소유자

- Harness: workflow State와 실행 artifact
- HRNS Registry: project metadata와 활성 선택
- UI preference: locale 같은 화면 설정
- process lock: 앱 실행 조율
- Compose: projection rendering

# 4. 현재 작업 순서

## 4.1 P0 — Live Harness Kit 호환성

완료 조건:

- 모든 `HarnessCommand`와 live AST parameter/ValidateSet 일치
- manifest와 runtime resolver 일치
- fresh ASCII 및 한글·공백 onboarding artifact가 production parser를 통과
- 주요 State lifecycle의 required field/type/taxonomy 일치
- Doctor/Validate-Ops false-green gap 제거
- production-to-production regression test 추가
- live Kit automatic/offline smoke와 HRNS 전체 test 통과

## 4.2 P1 — Native UI QA

P0 blocker가 닫힌 뒤 [Native QA 체크리스트](./native_qa_checklist.md)를 실제 사용자 클릭과 캡처로 수행한다.

## 4.3 P2 — Clean Windows MSI lifecycle

현재 MSI는 packaging 검증 산출물이다. release 승인에는 clean Windows/no system JDK에서 설치, 기동, external Kit 등록, standard daily cycle, 제거, AppData 보존까지의 독립 증거가 필요하다.

## 4.4 P3 — Approved bundled runtime

immutable runtime artifact, manifest, SHA-256, release allowlist, license/provenance, clean install smoke contract가 owner에게 승인되기 전 구현하지 않는다. live `D:\harness-kit`을 임의로 MSI에 복사하지 않는다.

## 4.5 P4 — Post-MVP

- 코드 서명
- 업데이트와 롤백
- 라이선스·provenance UI
- portable data mode
- main CTA와 분리된 opt-in diagnostics

# 5. 실행 모델

## 5.1 조회

```text
Registry/runtime resolve → manifest → boundary → day selection → State read
→ artifact/bridge/Git/lock probe → policy → projection
```

## 5.2 온보딩

```text
lock → enter-project → validate-ops JSON → bridge probe → daily probe
→ State reread → lock release
```

성공은 위 증거의 교집합이다. 등록 직후 `run-cycle`을 자동 호출하지 않는다.

## 5.3 daily action

```text
ActionPolicy → HarnessCommandMapper → lock → PowerShellHarnessAdapter
→ process result → State reread while locked → unlock → projection refresh
```

# 6. 테스트·검증 전략

## 6.1 필수 계층

1. core domain/policy/use-case test
2. infra adapter contract test
3. compose ViewModel/projection test
4. production-to-production Harness contract test
5. full `check --rerun-tasks`
6. native user QA
7. clean Windows MSI lifecycle

## 6.2 실제 artifact 우선

fixture는 live writer artifact와 field-by-field 비교한다. public 필드를 필수로 만들면 Harness 문서, template/writer, Doctor/Validate-Ops, smoke, HRNS DTO/mapper, cross-system test를 같은 계약으로 갱신한다.

## 6.3 수치의 의미

테스트 개수는 진단 정보일 뿐 완료 조건이 아니다. 최종 판정에는 실제 실행 여부, production artifact, 미실행 manual Gate를 함께 기록한다.

# 7. 배포 전 필수 Gate

| Gate | 상태 | 완료 조건 |
|---|---|---|
| Live Kit compatibility | 차단 | 전수 finding 해결과 fresh parser success |
| Native workflow QA | 대기 | 실제 사용자 증거와 필수 항목 PASS |
| Clean Windows MSI | 대기 | 설치부터 제거까지 lifecycle PASS |
| Bundled runtime | 차단 | owner-approved artifact와 manifest 제공 |
| Release approval | 차단 | 위 Gate와 signing/provenance 정책 승인 |

# 8. 하지 말아야 할 개발

- UI에서 State 직접 쓰기
- Harness planning/execution을 Kotlin으로 복제
- shell 문자열과 자유 형식 명령 실행
- label을 action ID로 사용
- queue pointer에서 wrapper/target 추론
- unknown 값을 성공이나 기본값으로 은폐
- registration 직후 자동 model 실행
- 실제 사용자 project를 fixture로 사용
- live Kit을 MSI runtime으로 임의 복사
- 녹색 테스트만으로 native/release 완료 선언
- raw secret/session/log 저장 또는 노출

# 9. 다음 수직 Slice

1. live compatibility 감사 완료
2. finding을 Harness/HRNS owner로 분류
3. 초기 State writer·diagnostic·smoke의 UI 보장 계약 수정
4. HRNS production parser를 사용하는 cross-system test 추가
5. 전체 offline/full test 재실행
6. native onboarding QA 재개

packaging, UI redesign, runtime bundle, 성능 최적화를 같은 변경에 섞지 않는다.

# 10. MVP 완료 정의

- live Kit standard workflow와 production parser 완전 호환
- onboarding부터 closure까지 native QA
- fail-closed와 secret masking 유지
- process/lock/cancel/timeout 검증
- clean Windows MSI lifecycle
- 사용자 데이터 경계와 제거 후 보존 확인
- 모든 blocker/high finding 해결

# 부록

## 부록 A — `WORKFLOW_STATE.json` UI 계약

Top-level 필수: `schema_version`, `date`, `project_name`, `workspace_root`, `repo_root`, `profile`, `required_next_action`, `state`, `queue`.

`state` 필수: `current_phase`, `current_status`, `next_action`, `execution_wrapper`, `stop_reason`, `blocked_reason`, `failed_reason`, `human_action_required`, `execution_completed`, `closure_validated`, `clean_handoff`, `resume_from_step_id`, `authorized_target_file`, `artifacts_state`, `ops_validation`, `closure`.

`artifacts_state` 필수: `request_inbox`, `today_strategy`, `daily_handoff`, `workflow_state`.

`queue` 안정 표면: `status`, `active.card_id`, `active.slice_id`, `blocked_reason`, `last_updated_at`.

`current_slice`, `slice_queue`, `role_sliced`, `usage_guard`는 diagnostic extension이며 readiness 필수값이 아니다. unknown key는 허용하지만 필수값 missing/null/type mismatch는 fail-closed한다.

## 부록 B — CTA 정책 요약

| 조건 | 허용 방향 |
|---|---|
| runtime/manifest/boundary 불명확 | 진단·설정만 |
| 오늘 State missing + artifact 준비 | Bootstrap 후보 |
| State malformed/unsupported/access denied | Recovery만 |
| request intake pending | 요청 작성/Planning |
| replan required | 명시적 Replan |
| execution ready + code/doc slice | 해당 wrapper execution |
| stopped/blocked/failed | reason 기반 Recovery |
| execution completed | Closure validation |
| 과거 날짜 | read-only 조회 |

실제 허용은 `ActionPolicy`가 runtime, boundary, artifact, lock, State를 종합해 결정한다.

## 부록 C — 실패·정지 의미

- 알려진 `stop_reason`은 typed enum, unknown은 raw value를 보존한다.
- `blocked_reason`, `failed_reason`의 빈 문자열은 없음이지만 missing은 malformed다.
- `human_action_required`를 nullable 기본값으로 숨기지 않는다.
- process failure/timeout/cancel과 Harness State stop은 다른 결과다.
- diagnostics `overall=ok`와 workflow 성공은 별개다.

## 부록 D — 경로·파일 계약

```text
Registry: %APPDATA%\hrns-now\projects.json
UI preference: %APPDATA%\hrns-now\ui-preferences.json
Lock: %LOCALAPPDATA%\hrns-now\locks\<project-id>\<date>.lock.json
DefaultKit: <source-checkout>\.local\harness-kit
ExternalKit: user-selected absolute path
Day root: <workspace>\<yyyy-MM-dd>
```

Bridge는 `.claude/settings.local.json`, `.claude/CLAUDE.md`, `tools/run-cycle.ps1` 정확히 3개다.

## 부록 E — Harness Kit correction backlog

- initial State writer/template와 UI guaranteed fields 일치
- Doctor/Validate-Ops에서 UI schema compatibility 검사
- onboarding smoke에서 production required fields와 type 검사
- HRNS parser 또는 공유 contract fixture를 포함한 cross-system test
- immutable release artifact/manifest/checksum/allowlist 정의

# 결론

HRNS-NOW의 내부 구조와 자동 테스트는 안정적이지만 완료 기준은 live Harness Kit이 생성하는 실제 artifact와의 호환성이다. P0 blocker를 먼저 닫고 native QA와 clean Windows 배포 Gate를 순서대로 진행한다.
