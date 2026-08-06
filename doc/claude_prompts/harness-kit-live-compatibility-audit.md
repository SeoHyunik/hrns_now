# HRNS-NOW ↔ Live Harness Kit Exhaustive Compatibility Audit

## 역할과 최종 목표

너는 `HRNS-NOW`와 현재 live `D:\harness-kit` 사이의 호환성을 독립적으로 감사하는 담당자다.

이번 작업의 목적은 기존 테스트나 과거 완료 보고서를 다시 요약하는 것이 아니다. **현재 source가 기대하는 계약과 현재 live Kit이 실제로 제공·생성·실행하는 계약을 모든 연동 경계에서 양방향으로 대조하고, 신규 온보딩부터 일일 작업·복구·마감까지 실제로 호환되는지 증거로 판정하는 것**이다.

테스트가 모두 통과해도 실제 생성 artifact가 production parser를 통과하지 못하면 호환으로 판정하지 않는다. 반대로 문자열 이름이 다르다는 이유만으로 결함이라 단정하지 말고, public contract와 실제 소비 경로를 근거로 판단한다.

이 감사에서는 결함을 수정하지 않는다. source·Harness Kit·사용자 프로젝트를 변경하지 않고 조사와 검증만 수행한 뒤 보고서만 작성한다. 수정은 감사 결과를 Codex와 사용자가 승인한 뒤 별도 작업으로 진행한다.

## 대상 경로

```text
HRNS-NOW repository : S:\dev\project\hrns_now
Live Harness Kit    : D:\harness-kit
허용된 scratch      : S:\tmp\hrns-now-harness-compat-audit-<timestamp>
최종 보고서         : doc/phase_reports/harness-kit-live-compatibility-audit-report.md
```

경로를 추측하지 말고 작업 시작 시 실제 존재 여부와 resolved absolute path를 기록한다.

## 절대 준수할 안전 경계

1. `D:\harness-kit`은 `.git`이 없는 live tree다. 파일을 수정·삭제·이동·rename·format하지 않는다. 수동 백업이나 임의 archive도 만들지 않는다.
2. HRNS-NOW production/test source를 수정하지 않는다. 감사 결과 보고서 외 파일을 만들거나 고치지 않는다.
3. 다음 사용자 소유 항목은 존재할 경우 읽기만 하고 수정·삭제·stage하지 않는다.

```text
doc/QA_captures/
doc/hrns_now_packaging_plan.md
doc/user_workflow_qa_notes.md
```

4. `git add`, `commit`, `amend`, `reset`, `restore`, `checkout`, `stash`, `rebase`, `clean`, `push`를 수행하지 않는다.
5. 실제 등록 프로젝트와 실제 workspace에서는 Doctor와 Validate-Ops 같은 read-only 진단만 허용한다. `enter-project.ps1`, `init-workspace.ps1`, `run-cycle.ps1`, planning/replan/execution/closure 같은 쓰기 entrypoint를 실제 사용자 경로에 실행하지 않는다.
6. 쓰기 동작 검증은 매번 새로 만든 `S:\tmp\hrns-now-harness-compat-audit-<timestamp>` 아래의 격리된 repository/workspace fixture에서만 수행한다. Kit·repository·workspace는 서로 포함 관계가 아니어야 한다.
7. live Claude/Ollama 호출, 네트워크 호출, 실제 planning/execution wrapper의 비결정적 모델 호출을 금지한다. Harness의 공식 automatic/offline smoke만 허용하며 live 호출 횟수는 0이어야 한다.
8. 사용자 요청 없이 마우스·키보드 합성 입력으로 Compose UI를 조작하지 않는다. native UI 클릭 증거가 없으면 native QA는 `NOT_EXECUTED`로 남긴다.
9. raw session ID, token, secret, 원본 응답, 전체 raw log를 보고서나 console 요약에 노출하지 않는다. 진단 JSON은 contract version, overall, check 개수·severity·ID 중심으로 요약한다.
10. scratch를 지우기 전 resolved absolute path가 정확히 허용 prefix 아래인지 검증한다. 감사 종료 시 자신이 만든 scratch만 제거하고 `Test-Path`로 부재를 확인한다.

## 신뢰 순서와 독립 검증 원칙

신뢰 순서는 다음과 같다.

1. 현재 HRNS-NOW production source
2. 현재 `D:\harness-kit` production source와 실제 실행 결과
3. 두 시스템이 게시한 현재 public contract 문서
4. 현재 테스트와 fixture
5. 과거 보고서와 완료 선언

과거 `PASS`, `PASS_WITH_FIXES`, 테스트 개수, smoke 개수를 그대로 인용해 현재 호환성을 판정하지 않는다. 모든 핵심 수치와 계약은 live 파일에서 다시 계산한다. 테스트 fixture와 실제 Kit 생성물은 반드시 별도로 비교한다.

## 작업 시작 시 반드시 기록할 기준선

HRNS-NOW에서 다음을 읽기 전용으로 기록한다.

```powershell
git branch --show-current
git rev-parse HEAD
git status --short
git log -5 --oneline --decorate
```

추가로 다음을 기록한다.

- 현재 실행 중인 `HRNS-NOW` 프로세스 유무. 프로세스를 종료하지 않는다.
- live Kit의 `kit-version.json` 원문 필드와 UTF-8/BOM 상태.
- `scripts/smoke/smoke-*.ps1` 실제 총개수, automatic/offline·manual/live 분류 개수, Secondary LLM 개수.
- public PowerShell entrypoint의 PowerShell AST parse error 수와 parameter 목록.
- 감사 전후 HRNS-NOW `git status --short`가 동일한지.
- 감사 전후 live Kit의 중요 public file hash/size/mtime가 동일한지. 최소 대상은 `kit-version.json`, 아래 public entrypoint, State template이다.

## 먼저 읽을 HRNS-NOW 자료

문서:

- `README.md`
- `doc/README.md`
- `doc/hrns_now_claude_plan.md`
- `doc/hrns_now_design_pattern.md`
- `doc/native_qa_checklist.md`
- `doc/hrns_now_packaging_plan.md`와 `doc/user_workflow_qa_notes.md` — 존재할 경우 비정본 사용자 자료로만 읽기

완료된 Phase 실행 프롬프트와 시점별 보고서는 현재 계약이 아니다. 필요한 과거 사실은 Git 이력에서 확인하되, 현재 source와 위 현행 문서를 우선한다.

Production source와 직접 관련 테스트:

- `core/.../HarnessCommand.kt`
- `core/.../CompatibilityPolicy.kt`
- `core/.../ActionPolicy.kt`
- `core/.../BoundaryPolicy.kt`
- `core/.../OnboardProjectUseCase.kt`
- `core/.../ExecuteHarnessActionUseCase.kt`
- `infra/.../DefaultKitRuntimeResolver.kt`
- `infra/.../JsonKitVersionManifestAdapter.kt`
- `infra/.../HarnessCommandEncoder.kt`
- `infra/.../PowerShellHarnessAdapter.kt`
- `infra/.../JvmProcessExecutor.kt`
- `infra/.../HarnessWorkflowStateDto.kt`
- `infra/.../WorkflowStateMapper.kt`
- `infra/.../JsonWorkflowStateAdapter.kt`
- `infra/.../WorkspaceArtifactProbe.kt`
- `infra/.../RepositoryBridgeProbe.kt`
- `infra/.../JsonProjectRegistryAdapter.kt`
- `composeApp/.../App.kt`
- `composeApp/.../AppViewModel.kt`
- 위 클래스에 대응하는 모든 test와 `workflow-state-live-shape.json`

`...`는 실제 source tree에서 찾아 정확한 경로로 확장한다. 목록에 없더라도 호출 그래프에서 발견되는 mapper, DTO, enum, policy, port, adapter, projection, test는 함께 읽는다.

## 먼저 읽을 live Harness Kit 자료

- `README.md`
- `kit-version.json`
- `docs/STATE_MODEL.md`
- `docs/PROJECT_ONBOARDING.md`
- `docs/OPERATING_GUIDE.md`
- `docs/INSTALL.md`
- `docs/SECURITY_MODEL.md`
- `docs/HARNESS_KIT_MAP.ko.md`
- `docs/HARNESS_KIT_MAP.en.md`
- `scripts/SMOKE_INDEX.md`
- `scripts/doctor.ps1`
- `scripts/validate-ops.ps1`
- `scripts/enter-project.ps1`
- `scripts/init-workspace.ps1`
- `scripts/run-cycle.ps1`
- planning/replan/code/doc/closure가 실제 호출하는 public wrapper
- `scripts/lib/state-surface.ps1`
- `scripts/lib/harness-domain.ps1`
- `templates/bridge/**`
- `templates/workspace/**`
- `profiles/corp-default.yaml`
- 연동 계약을 검증한다고 주장하는 모든 smoke

문서 설명만 읽지 말고 production script, template, fixture, smoke assertion이 같은 계약을 검증하는지 대조한다.

## 알려진 시드 결함 — 독립 재현 필수

다음은 선행 조사에서 발견한 시드이며, 그대로 믿지 말고 현재 live tree에서 독립 재현한다.

1. `docs/STATE_MODEL.md`의 UI-consumption 1.x 계약은 최상위 `required_next_action`을 보장 필드로 선언한다.
2. HRNS-NOW `WorkflowStateMapper`는 이 필드를 필수로 읽고, 누락 시 `Malformed` 경로로 fail-closed한다.
3. 현재 `templates/workspace/WORKFLOW_STATE.json.tpl`과 `init-workspace.ps1`의 `Ensure-WorkflowStateBaseline`에는 이 필드가 없다는 선행 관찰이 있다.
4. fresh `enter-project.ps1`은 exit 0, bridge 3/3, daily 4/4를 만들고 Doctor/Validate-Ops도 `overall=ok`를 반환하지만, 초기 State에는 해당 필드가 없어 HRNS-NOW 온보딩의 `StateReadResult.Success` Gate가 충족되지 않는다는 선행 재현이 있다.
5. HRNS-NOW 437 tests와 Harness onboarding/Doctor/Validate-Ops smoke는 모두 통과했으나 이 차이를 검출하지 못했다는 선행 관찰이 있다.

반드시 다음을 밝힌다.

- 현재도 재현되는가.
- 어느 writer/template에서 처음 누락되는가.
- 이후 어떤 lifecycle에서 필드가 추가되거나 끝까지 누락되는가.
- Harness 문서, writer, Doctor, Validate-Ops, smoke, HRNS parser 중 무엇이 서로 불일치하는가.
- 사용자 화면과 CTA에 미치는 실제 영향은 무엇인가.
- 이 한 건 외에 같은 유형의 누락·alias·requiredness drift가 더 있는가.

**이 시드 결함을 확인했다고 감사를 종료하지 않는다.** 아래 전수 매트릭스를 모두 완료해야 한다.

## 전수 호환성 감사 매트릭스

### A. Runtime discovery와 version manifest

- External Kit `D:\harness-kit`이 현재 Registry와 runtime resolver에서 어떻게 선택·검증되는지 추적한다.
- DefaultKit의 repository-relative `.local/harness-kit`과 ExternalKit을 혼동하지 않는지 확인한다.
- packaged app에서 DefaultKit이 임의의 host 경로를 추측하지 않고 fail-closed하는지 확인한다.
- HRNS-NOW가 요구하는 파일 목록과 기능별 실제 필요 entrypoint 목록을 비교한다. 특히 runtime resolution은 성공하지만 onboarding 시 `enter-project.ps1`이 없어서 뒤늦게 실패할 수 있는 capability gap을 확인한다.
- `kit_version`, `state_schema_version`, `ui_contract_version`의 존재·형식·major/minor 정책·unknown field 허용이 실제 manifest와 맞는지 확인한다.
- manifest UTF-8 BOM, malformed JSON, missing field, higher minor, unsupported major가 typed 결과로 분리되는지 확인한다.

### B. Public entrypoint와 CLI 인자

HRNS-NOW의 모든 `HarnessCommand` variant를 live PowerShell AST와 일대일 표로 만든다.

```text
Doctor
ValidateOps
OnboardProject
BootstrapDay
RunPlanning
RunReplan
RunExecution(code)
RunExecution(doc)
ValidateClosure
```

각 항목에서 다음을 비교한다.

- script path
- mandatory/optional parameter
- parameter 순서가 아니라 실제 이름과 타입
- switch/string 의미
- default 값
- `ValidateSet`
- 허용 조합과 금지 조합
- working directory
- PowerShell 5.1 실행 가능성
- 공백·한글·drive-letter·UNC path argument 보존
- exit code 의미
- stdout/stderr 계약
- 실행 후 State 재조회 필요 여부

특히 다음 계약을 확인한다.

```text
execution wrapper: none|code|doc|auto
planning reason: live ValidateSet 전체
replan reason: 빈 값 제외 live ValidateSet 전체
closure validation: planning/replan/execution wrapper와 같은 pass에서 결합 금지
등록 직후 run-cycle/BootstrapDay 자동 실행 금지
--continue 같은 존재하지 않는 우회 인자 금지
```

### C. Diagnostic JSON 계약

`doctor.ps1 -Json`과 `validate-ops.ps1 -Json`을 live Kit 및 격리 fixture에서 실행한다.

- stdout이 단일 JSON document인지 확인한다.
- `contract_version`, `overall`, `checks[]` 필수 구조를 production `PowerShellHarnessAdapter`가 실제로 읽을 수 있는지 확인한다.
- severity와 overall의 알려진 값·unknown 값 처리, exit-code parity, stderr 혼입, 출력 truncation을 확인한다.
- JSON mode가 incident file을 만들거나 workspace/Kit을 수정하지 않는지 확인한다.
- 진단이 State의 UI 보장 필드를 실제로 검사하는지 확인한다. 단순히 `overall=ok`라는 이유로 State parser 호환성을 PASS 처리하지 않는다.
- check message에 secret-shaped value가 들어가도 UI 경계 전에 masking되는지 확인한다.

### D. WORKFLOW_STATE 전 lifecycle shape

HRNS-NOW DTO와 mapper에서 필수로 요구하는 필드를 기계적으로 목록화하고, Harness 문서·template·writer가 생성하는 필드와 비교한다.

최소 lifecycle 표본:

1. fresh `enter-project` 직후
2. bootstrap 직후
3. request intake pending
4. planning required/ready
5. replan required/ready
6. execution ready
7. code slice active
8. doc slice active
9. stopped
10. blocked
11. failed
12. execution completed
13. closure validation 전/후

live Claude/Ollama를 호출해야 하는 상태는 실행하지 말고, official offline smoke·결정적 fixture·production writer의 정적 경로를 조합해 검증한다. 각 표본에 대해 다음을 기록한다.

- 실제 writer와 생성 시점
- top-level/state/queue 필드 존재 여부와 JSON type
- empty string과 null과 missing의 구분
- HRNS parser 결과: `Success`, `Malformed`, `UnsupportedSchema`, `EncodingError` 등
- enum/taxonomy mapping 결과
- unknown field 보존·무시 정책
- CTA/readiness에 사용하는 값과 표시 전용 값의 구분

다음 불변 계약을 별도로 확인한다.

- `WORKFLOW_STATE.json`이 single runtime truth다.
- UI는 State를 직접 쓰지 않는다.
- `queue.active`는 `card_id`/`slice_id` pointer이며 wrapper나 authorized target을 임의 발명하지 않는다.
- `state.current_slice`, `slice_queue`, `role_sliced`, `usage_guard`는 readiness를 깨뜨리는 거짓 필수값이 아니다.
- malformed/unknown/partial write는 fail-closed한다.
- higher minor의 unknown field가 기존 major 호환을 불필요하게 깨뜨리지 않는다.

### E. Daily artifact와 날짜 선택

- required daily surface가 정확히 다음 4개인지 production code, live Kit, docs, smoke에서 각각 확인한다.

```text
REQUEST_INBOX.md
TODAY_STRATEGY.md
DAILY_HANDOFF.md
WORKFLOW_STATE.json
```

- `REQUEST_STRUCTURED.md`는 optional이며 readiness 필수로 승격되지 않았는지 확인한다.
- `WORK_QUEUE.json`과 `WORKDAY_STATE.json`은 명시적 legacy compatibility 외에는 required/readiness에 포함되지 않는지 확인한다.
- 오늘 폴더 없음, 최신 과거 날짜만 있음, 잘못된 날짜명, day path가 file인 경우, 빈 파일, BOM, partial write를 확인한다.
- 오늘과 과거 선택에서 쓰기 CTA가 과거 workspace를 변경하지 않는지 확인한다.
- `REQUEST_INBOX.md` 저장만 UI 소유 write이며 State·structured request를 건드리지 않는지 확인한다.

### F. Project registration과 onboarding

격리 fixture를 최소 두 세트 만든다.

1. ASCII 경로
2. 공백과 한글이 포함된 경로

각 fixture에서 다음을 검증한다.

- Kit/repository/workspace 비포함 경계
- profile ID `corp-default`
- `enter-project.ps1` exit code와 stderr
- repository bridge 정확히 3개
- external workspace 오늘 daily 정확히 4개
- bridge가 현재 `D:\harness-kit`을 참조하고 hardcoded 과거 path를 참조하지 않음
- 기존 bridge 비덮어쓰기와 재실행 idempotency
- workspace가 repository 내부일 때 명시적 거부
- `validate-ops -Json` overall과 check 구조
- 생성 State를 **HRNS-NOW production parser 계약으로** 읽은 결과
- 성공 판정의 5중 교집합: enter-project, Validate-Ops, bridge, daily, State
- 실패 후 Registry entry 보존과 재시도 가능성
- 단일 lock, heartbeat, release, 빠른 중복 요청 방지

raw JSON의 키 존재만 검사하지 말고 production mapper의 requiredness와 실제 생성물을 직접 연결한다. production adapter를 직접 호출할 임시 검증기가 필요하면 HRNS source tree가 아니라 scratch에서만 만들고, 사용한 classpath·명령·결과를 보고한다.

### G. Planning·replan·execution·closure 연결

- ActionPolicy가 만든 action이 정확한 typed command로 변환되는지 호출 그래프로 추적한다.
- planning과 replan이 서로 다른 public entrypoint option을 쓰는지 확인한다.
- `continue-existing-plan`이 planning 재호출이 아닌 skip/continuation 계약과 맞는지 확인한다.
- code/doc active slice와 wrapper가 일치하지 않으면 fail-closed하는지 확인한다.
- `authorized_target_file`, ops validation, active queue pointer가 실행 허용 전에 모두 검증되는지 확인한다.
- closure는 실제 `-ValidateForClosure` entrypoint를 사용하고, stdout 문구만으로 완료 처리하지 않는지 확인한다.
- 실행 lock을 보유한 채 process 완료 후 State를 다시 읽고 그 뒤 lock을 해제하는지 확인한다.
- timeout/cancel 시 child process tree와 lock이 남지 않는지 offline stub으로 검증한다.

실제 모델 호출이 필요한 wrapper는 실행하지 않는다. static contract와 official offline/stub smoke로 판정하고 실행하지 못한 항목을 숨기지 않는다.

### H. Registry·path·설치본 경계

- `%APPDATA%\hrns-now\projects.json` schema와 현재 entry를 read-only로 검사한다.
- 실제 active project가 있으면 Kit root, workspace, repository, profile, 선택 날짜를 요약하되 민감 경로를 보고서에 불필요하게 반복하지 않는다.
- 실제 프로젝트에는 Doctor/Validate-Ops만 실행하고, 최신 day State를 read-only로 production requiredness와 대조한다.
- Registry가 Kit/workspace/repository 내부에 들어가지 않는지 확인한다.
- ExternalKit과 DefaultKit wire value, legacy migration, unknown source type이 fail-closed하는지 확인한다.
- Gradle run과 packaged release app image가 같은 runtime selection 계약을 쓰는지 확인한다.
- bundled runtime이 승인되지 않은 상태라면 live `D:\harness-kit`을 MSI에 임의 복사하는 해결책을 제안하지 않는다.

### I. Encoding·process·security

- HRNS-NOW가 shell 문자열이 아니라 argument list로 실행하는지 확인한다.
- PowerShell 창을 강제로 노출하는 실행 경로가 없는지 확인한다.
- Windows native console encoding과 JSON UTF-8 처리 경계를 확인한다.
- UTF-8 BOM 허용/금지 정책을 manifest, State, Registry, daily Markdown별로 구분한다.
- stdout/stderr 동시 drain, timeout, cancel, residual process, lock stale 정책을 확인한다.
- raw session ID, token, secret, raw response, raw log가 Registry·projection·report에 들어가지 않는지 확인한다.
- path나 diagnostics message에 secret-shaped 문자열이 있어도 masking되는지 확인한다.

### J. 테스트와 실제 계약 사이의 사각지대

- HRNS 테스트 fixture와 fresh live Kit 생성 State를 field-by-field 비교한다.
- 테스트가 필드 누락을 검증하더라도 실제 writer가 그 필드를 만든다는 검증까지 있는지 확인한다.
- Harness smoke가 파일 존재·exit 0만 확인하고 UI guaranteed field를 검사하지 않는 구간을 찾는다.
- Doctor/Validate-Ops가 `overall=ok`여도 HRNS production parser가 실패하는 false-green 조합을 모두 찾는다.
- mock/stub에서만 통과하고 live entrypoint에서는 확인되지 않은 경로를 목록화한다.
- 테스트 개수보다 production-to-production contract test의 존재 여부를 우선 평가한다.

## 필수 실행 검증

### HRNS-NOW

최소 다음을 수행한다.

```powershell
Set-Location -LiteralPath 'S:\dev\project\hrns_now'
.\gradlew.bat check --rerun-tasks --no-daemon
```

모듈별 XML 결과를 UTF-8로 읽어 tests/failures/errors/skipped를 다시 합산한다. `UP-TO-DATE`만 보고 재실행으로 간주하지 않는다.

### Harness Kit

최소 다음 targeted smoke를 실행한다.

```text
smoke-clean-root-onboarding.ps1
smoke-doctor-json-contract.ps1
smoke-validate-ops-json-contract.ps1
```

그 뒤 `scripts/SMOKE_INDEX.md` §3.2의 **현재 live automatic/offline execution array를 그대로 사용해 전체 suite**를 실행한다. 목록을 과거 보고서에서 복사하지 말고 live index에서 읽는다. 실행 전후 실제 개수와 결과를 재계산하며 live Claude/Ollama 호출은 0이어야 한다.

공식 suite가 PASS하더라도 이 감사의 production-to-production fixture 비교가 실패하면 최종 호환 판정은 PASS가 아니다.

## 결함 분류

모든 finding에 다음을 포함한다.

```text
ID
Severity: BLOCKER | HIGH | MEDIUM | LOW | DOC
Surface
HRNS-NOW expectation
Harness actual behavior
Authoritative contract
Static evidence: path + line
Dynamic reproduction: command + exit + sanitized result
User-visible impact
Owner: HRNS-NOW | Harness Kit | Both
Minimal correction direction
Required regression test
```

Severity 기준:

- `BLOCKER`: 표준 onboarding/daily flow가 진행되지 않거나 안전하지 않은 쓰기·오판정이 발생함.
- `HIGH`: 특정 정상 상태에서 잘못된 명령 실행, false success, 데이터 경계 위반, lock/process 잔류 가능성이 있음.
- `MEDIUM`: 복구·과거 날짜·설치본·오류 경로가 잘못되거나 중요한 자동 검증 사각지대가 있음.
- `LOW`: 핵심 흐름을 막지 않는 제한적 호환성·진단 품질 문제.
- `DOC`: runtime 동작에는 직접 영향이 없지만 public 문서·수치·경로가 현재 사실과 다름.

결함을 단순 나열하지 말고 같은 원인의 파생 증상을 묶는다. 반대로 서로 다른 writer나 lifecycle에서 독립적으로 생기는 문제는 한 건으로 뭉개지 않는다.

## 완료 Gate

다음을 모두 충족하기 전에는 감사를 완료했다고 선언하지 않는다.

- [ ] branch/HEAD/status 기준선과 종료 상태 기록
- [ ] live Kit version·inventory·AST parameter 재계산
- [ ] 모든 HarnessCommand ↔ entrypoint 매트릭스 작성
- [ ] 모든 HRNS State 필수 필드 ↔ live writer/template 매트릭스 작성
- [ ] fresh ASCII 및 한글·공백 onboarding fixture 실행
- [ ] production parser 관점의 State 결과 확인
- [ ] 실제 active project read-only 진단과 State shape 확인
- [ ] HRNS tests 강제 재실행
- [ ] Harness targeted smoke 실행
- [ ] Harness 전체 automatic/offline suite 실행
- [ ] false-green 테스트 사각지대 목록화
- [ ] scratch 제거와 Kit/working tree 무변경 확인
- [ ] 미실행 항목과 이유 명시

환경 문제로 어떤 Gate를 실행하지 못하면 해당 항목을 PASS로 추정하지 말고 `BLOCKED_BY_ENVIRONMENT` 근거로 남긴다.

## 보고서 형식

UTF-8 without BOM으로 다음 파일을 작성한다.

```text
doc/phase_reports/harness-kit-live-compatibility-audit-report.md
```

보고서는 다음 순서를 따른다.

1. 최종 Verdict와 5줄 이내 요약
2. 감사 기준선(branch, HEAD, 날짜, Kit manifest, inventory)
3. 실행한 명령과 sanitized 결과
4. 전체 호환성 매트릭스
5. findings — severity 순
6. 알려진 시드 결함 독립 재현 결과
7. 테스트가 놓친 false-green 사각지대
8. HRNS-NOW 수정 후보와 Harness Kit 수정 후보를 분리한 최소 correction 방향
9. 실행하지 않은 항목과 잔여 위험
10. scratch·Git·live Kit 무변경 증거

최종 Verdict는 다음 중 하나만 사용한다.

```text
COMPATIBLE
COMPATIBLE_WITH_NONBLOCKING_GAPS
INCOMPATIBLE
BLOCKED_BY_ENVIRONMENT
```

`BLOCKER` 또는 `HIGH` finding이 하나라도 재현되면 `COMPATIBLE`을 사용할 수 없다. 표준 onboarding 직후 State가 production parser를 통과하지 못하면 최종 Verdict는 반드시 `INCOMPATIBLE`이다.

보고서 마지막에 다음 machine-readable marker를 정확히 한 줄로 남긴다.

```text
HRNS_HARNESS_LIVE_COMPATIBILITY_AUDIT: <COMPATIBLE|COMPATIBLE_WITH_NONBLOCKING_GAPS|INCOMPATIBLE|BLOCKED_BY_ENVIRONMENT>
```

## Claude의 최종 응답

최종 응답에는 다음만 간결하게 포함한다.

- Verdict
- BLOCKER/HIGH/MEDIUM/LOW/DOC 개수
- 가장 중요한 3개 finding
- HRNS tests와 Harness smoke 결과
- 보고서 경로
- Git mutation을 하지 않았고 live Kit을 수정하지 않았다는 확인

수정·commit·push를 수행했다고 말하지 않는다. 감사 범위를 넘어 packaging, CI, UI redesign, 성능 최적화를 섞지 않는다.
