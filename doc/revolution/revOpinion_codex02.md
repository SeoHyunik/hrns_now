# HRNS-NOW Agent Runtime 진화 — Claude Round 1에 대한 Codex 교차검토 02

## 문서 상태

- 작성일: 2026-09-04 (Asia/Seoul)
- 검토 단계: Round 2 — Cross Review
- 검토 주체: Codex
- 검토 대상: `doc/revolution/revOpinion_claude01.md`
- 비교 기준: `doc/revolution/revOpinion_codex01.md`, 현재 HRNS-NOW source, live `D:\harness-kit`
- 문서 성격: 상대 독립 분석에 대한 비판적 수용·수정 의견
- 구현 승인 여부: 미승인
- Production source 수정 여부: 없음

이 문서는 Claude의 Round 1 분석 전체를 다시 작성하는 계획서가 아니다. 제안서가 Round 2에 요구한 대로 다음 항목만 판별한다.

- 사실과 다른 부분
- 불필요하거나 너무 이른 abstraction
- 현재 source와 충돌하는 부분
- backward compatibility 위험
- token/cost 문제를 악화시킬 수 있는 부분
- missing test/gate
- 더 작은 reversible vertical slice

각 쟁점은 `ACCEPT`, `REVISE`, `REJECT`, `DEFER`로 판정한다. 이 판정은 Round 3 통합 계획서의 입력이며, 그 자체로 production 구현을 허가하지 않는다.

---

# CROSS-REVIEW VERDICT

## `ACCEPT_WITH_REQUIRED_REVISIONS`

Claude 문서의 중심 결론은 수용한다.

> HRNS-NOW는 provider-blind Control Plane으로 유지하고, 첫 POC는 `run-cycle.ps1` 밖의 Codex read-only reviewer로 제한한다.

이 결론은 Codex Round 1과 일치한다. 특히 다음 판단은 강하게 수용한다.

- Claude 결합의 실제 중심은 HRNS가 아니라 Harness 내부다.
- 최초 Codex lane은 bridge-free, State-free, HRNS-free여야 한다.
- 기존 Claude-only workflow와 Secondary LLM의 비권위 경계를 보존해야 한다.
- Router, Codex write, 자동 commit, 병렬 Agent는 후속 Gate로 미뤄야 한다.
- live 경제성 측정은 사용자의 명시적 실행과 실제 task가 필요하다.

그러나 다음 네 가지는 통합 계획 전에 반드시 수정해야 한다.

1. `context-diet-packet.json`은 reviewer의 주 입력 계약으로 부족하다.
2. 기존 Secondary LLM evidence builder는 staged/untracked/commit diff를 포괄하지 않으므로 그대로 재사용할 수 없다.
3. `usage_guard` 또는 `role_sliced`에 provider runtime 상태를 넣는 것은 의미적 과적재이므로 수용하지 않는다.
4. `claude-invoke-core.ps1`은 Claude adapter 전체가 아니며, 이를 공통화 seam으로 삼아서는 안 된다.

---

# 1. 그대로 수용하는 판단

## 1.1 HRNS-NOW의 provider-blind 경계 — `ACCEPT`

Claude는 HRNS의 현재 실행 흐름을 다음과 같이 정확히 추적했다.

```text
UiAction
→ ActionPolicy
→ HarnessCommandMapper
→ HarnessCommand
→ HarnessCommandEncoder
→ HarnessRunnerPort
→ PowerShell process
→ WorkflowStatePort.read()
→ projection
```

HRNS가 Claude/Codex/Ollama를 직접 실행하지 않고, typed PowerShell command와 State reread만 소유한다는 결론은 정확하다. Provider expansion을 이유로 `HarnessCommand`, `HarnessRunnerPort`, encoder, `ActionPolicy`, UI를 먼저 바꾸지 않는다는 제약을 Round 3에 그대로 반영해야 한다.

## 1.2 실제 Claude 결합은 Harness 내부에 국한 — `ACCEPT`

“HRNS 전체가 Claude에 구조적으로 고착됐다”는 프레이밍은 과장이라는 Claude의 지적을 수용한다.

HRNS에는 Claude 이름을 가진 stop/status taxonomy와 `.claude` bridge 표면이 일부 존재하지만, 실제 provider process launch, session, stream-json, prompt, telemetry 결합은 Harness runner에 있다. 따라서 초기 변경 범위가 HRNS로 번지면 안 된다.

## 1.3 `run-cycle.ps1` 밖의 POC — `ACCEPT`

최초 POC를 독립 report lane으로 두자는 결론은 Codex Round 1과 완전히 일치한다.

POC가 다음을 건드리지 않는다는 조건을 유지한다.

- `run-cycle.ps1`
- planning/code/doc wrapper
- `WORKFLOW_STATE.json`
- 4-file daily required surface
- 3-file repository bridge
- HRNS Kotlin source
- Claude session continuity
- 자동 resolution/commit

## 1.4 Router 소유자는 장기적으로 Harness — `ACCEPT_WITH_BOUNDARY`

Provider availability, usage observation, deterministic preflight, provider invocation이 Harness에 있으므로 provider/lane Router의 장기 소유자를 Harness로 두자는 판단은 타당하다.

다만 다음 경계를 명시해야 한다.

- HRNS `ActionPolicy`는 사용자가 어떤 workflow action을 실행할 수 있는지를 결정한다.
- Harness Router는 이미 허가된 action 안에서 어떤 provider/lane을 사용할지를 결정한다.
- Router가 HRNS의 action authorization이나 State readiness를 우회하면 안 된다.
- 첫 POC에는 Router 자체가 존재하지 않는다.

## 1.5 사용자 전역 설정과 bridge 무변경 — `ACCEPT`

다음은 초기 규칙이 아니라 지속적인 hard rule로 취급한다.

- `%USERPROFILE%\.codex\config.toml` 자동 수정 금지
- 사용자/프로젝트 기존 `AGENTS.md` 덮어쓰기 금지
- 사용자 `CLAUDE.md` 수정 금지
- Codex bridge 파일을 현재 3-file bridge에 자동 추가하지 않음
- provider-specific 설정을 HRNS Registry에 저장하지 않음

Claude가 3-file bridge 변경의 파급 범위로 `DefaultKitRuntimeResolver`, `RepositoryBridgeProbe`, 문서, Native QA를 지적한 부분도 수용한다.

## 1.6 Codex write와 commit authority 후순위 — `ACCEPT`

Read-only reviewer 안정성 증명 전에 Codex bounded write를 연결해서는 안 된다. 또한 bounded write가 나중에 도입되더라도 commit authority와 같은 Phase로 묶지 않는다.

현재 협업 모델에서 Git integration/commit은 사용자 승인 아래 Codex가 수행하는 별도 책임이다. Agent Runtime의 자동 권한으로 승격하지 않는다.

## 1.7 Native UI QA와 packaging 분리 — `ACCEPT_WITH_SEQUENCE_CLARIFICATION`

현재 UI QA가 열린 상태이고, provider runtime이 안정되기 전에 bundled runtime/MSI를 최종 확정하면 재작업이 생길 수 있다는 판단은 타당하다.

단, 순서는 다음처럼 구분해야 한다.

- 문서 분석과 fixture-only capability spike는 Native UI QA와 병행 가능
- live `D:\harness-kit` mutation 전에는 현재 Native UI baseline 기록을 완료하는 것이 안전
- MSI 최종화는 provider dependency 정책 안정화 후
- 기존 MSI regression과 console-window-flash gap은 별도로 계속 추적

---

# 2. 사실관계 수정

## 2.1 대상 host의 Codex CLI 가용성 — `REVISE`

Claude 문서는 “두 repo 어디에도 Codex CLI 설치·인증 증거가 없다”고 기록하고 이를 열린 환경 사실로 남겼다. Repository 안에 설치 증거가 없다는 문장은 맞지만, 현재 host에 대한 가용성은 이번 Codex 검토에서 한 단계 더 확인됐다.

확인된 사실:

```text
실행 경로: C:\Users\LG\AppData\Roaming\npm\codex.ps1
버전: codex-cli 0.153.2
확인된 command: codex exec, codex exec review
확인된 option: --sandbox read-only, --ephemeral,
                 --ignore-user-config, --output-schema, --json, -C
```

따라서 “CLI 설치 미확인”은 닫혔다.

아직 확인되지 않은 사실:

- 인증이 유효한지
- 실제 noninteractive provider call이 성공하는지
- 실제 quota/usage failure가 어떤 exit/output으로 나타나는지
- Windows sandbox의 실제 read/write 경계
- `--ephemeral`이 host에서 남기는 모든 흔적

Round 3에서는 `installed=true`, `live_capability=unverified`로 구분해야 한다.

## 2.2 `claude-invoke-core.ps1`을 “곧 Claude Code CLI adapter”로 보는 표현 — `REVISE`

Claude 문서는 `claude-invoke-core.ps1`을 사실상 Claude Code CLI adapter라고 규정했다. 이 표현은 너무 넓다.

현재 source에서 실제 process launch는 다음 세 runner의 `Start-ClaudeProcess`가 소유한다.

```text
scripts/lib/plan/planning-claude-runner.ps1
scripts/lib/code/code-claude-runner.ps1
scripts/lib/doc/doc-claude-runner.ps1
```

`claude-invoke-core.ps1`은 다음을 공유한다.

- stop classifier lazy load/projection
- optional `claude_runtime` gate inspection
- session continuity broker
- usage ledger
- stream-json telemetry 및 capture helper

즉 이는 **Claude-specific cross-cutting core**이지 process launch까지 포함한 완결 adapter가 아니다.

수정된 해석:

```text
Claude adapter 전체
= runner별 CLI invocation/argument/process handling
+ claude-invoke-core의 session/telemetry/classification helper
+ wrapper별 prompt/result semantics
```

장기 provider seam은 `claude-invoke-core.ps1` 내부가 아니라, 기존 Claude 실행기를 하나의 provider implementation으로 감싸는 상위 경계에 있어야 한다.

## 2.3 실제 public wrapper 파일명 — `ACCEPT / CODEX01 SELF-CORRECTION`

Claude 문서가 사용한 public wrapper 파일명은 현재 source와 일치한다.

```text
scripts/invoke-planning-cycle.ps1
scripts/invoke-code-execution.ps1
scripts/invoke-document-execution.ps1
```

Codex Round 1 문서에는 일부 위치에서 이를 `invoke-planning.ps1`, `invoke-code.ps1`, `invoke-documentation.ps1`로 잘못 적었다. Round 3에서는 Claude가 확인한 실제 파일명을 사용해야 한다.

이 오류는 아키텍처 판단을 바꾸지는 않지만, 변경 대상 목록과 smoke 경로를 틀리게 만들 수 있으므로 명시적으로 정정한다.

## 2.4 Secondary LLM evidence builder의 Git 범위 — `REVISE`

Claude 문서는 기존 Secondary LLM lane의 evidence/audit 자산을 “직접 재사용”할 수 있다고 평가했다. 패턴은 재사용 가능하지만 현재 Git evidence 구현은 production code review 입력으로 불충분하다.

`build-secondary-llm-evidence-manifest.ps1`이 현재 실행하는 것은 다음이다.

```text
git diff --name-only
git diff --stat
git diff --check
```

이 기본 명령은 working tree의 unstaged tracked diff만 대상으로 한다. 다음은 완전하게 포괄하지 않는다.

- staged changes
- untracked files
- 특정 commit의 diff
- base branch 대비 diff
- rename/copy의 명시적 처리
- binary/submodule/LFS 의미
- 사용자 지정 changed-file allowlist

따라서 기존 builder의 path containment, safe text, evidence/audit 구조는 재사용할 수 있지만, 현재 Git summary를 Codex review의 authoritative change set으로 사용해서는 안 된다.

## 2.5 `context-diet-packet.json`의 성격 — `REVISE`

Claude는 이를 proto-Task-Packet으로 정확히 발견했지만, 최소 POC 입력으로 거의 그대로 사용하자는 제안은 수정해야 한다.

현재 packet에는 다음이 있다.

- thread identity
- current status/work key
- plan generation/request revision hash
- allowed files/authorized target 요약
- latest continuity stage
- context-size reduction 진단

반면 read-only code review에 필수적인 다음 정보는 없다.

- 구체 task goal
- acceptance criteria
- required evidence
- validation plan 및 실제 exit evidence
- review base
- complete changed-file manifest
- actual diff 또는 source snapshot
- forbidden read scope
- finding output contract

또한 context-diet `active` production replacement는 현재 execution-disabled다. 이 packet은 continuity/context 진단 artifact이며 reviewer authority packet이 아니다.

수정된 결론:

> `context-diet-packet.json`은 optional input source로 참조할 수 있지만, 별도의 얇은 Review Packet builder가 필요한 정보를 명시적으로 projection해야 한다.

## 2.6 Secondary LLM output 위치를 본뜬 경로 — `ACCEPT_WITH_EXACT_PATH`

Claude가 제안한 `logs/external-review/<run-id>` 방향은 현재 daily workspace 구조와 맞지만, 기준 root를 명시해야 한다.

권장 개념 경로:

```text
<workspace-root>/<date>/logs/codex-review/<run-id>/
```

전역 `<workspace-root>/logs`나 Kit root의 `logs/`로 오해되지 않도록 한다. 현재 Secondary LLM도 `<workspace-root>/<date>/logs/secondary-llm/<run-id>`를 사용한다.

---

# 3. 불필요하거나 너무 이른 추상화

## 3.1 `external-review`라는 generic subsystem 이름 — `REVISE`

Claude가 제안한 다음 이름은 첫 POC부터 다중 provider를 전제한다.

```text
scripts/report/invoke-external-review.ps1
scripts/lib/external-review/*
```

첫 POC provider는 Codex 하나뿐이며, provider-neutral 공통부가 실제로 존재하는지도 아직 검증되지 않았다. Generic 이름을 먼저 만들면 Codex 고유 sandbox/output/config semantics가 다시 공통 contract로 새어 들어갈 수 있다.

첫 POC 권장 이름:

```text
scripts/report/invoke-codex-review.ps1
scripts/lib/codex/codex-review-adapter.ps1
```

두 번째 provider adapter 또는 guarded runtime 연결 후 실제 공통 부분만 `agent-review` 같은 중립 계층으로 추출한다.

## 3.2 Claude stop taxonomy를 provider-neutral result의 기반으로 직접 사용 — `REJECT`

Claude는 AR-1 result shape가 `claude-stop-classification.ps1` 필드와 ledger 1.1을 재사용할 수 있다고 했다. Claude adapter 내부의 source material로 재사용하는 것은 맞지만, provider-neutral result의 정본으로 직접 사용하면 안 된다.

Claude taxonomy에는 다음 provider-specific 의미가 있다.

- `claude_context_limit`
- `claude_call_timeout`
- `claude_response_empty`
- `transient_claude_overloaded`
- Claude stream-json subtype/session 의미

Provider-neutral 상위 결과는 다음과 같은 좁은 category만 가져야 한다.

```text
completed
unavailable
blocked
timed_out
cancelled
usage_limited
contract_invalid
failed
```

그리고 별도 필드로 `provider_failure_code`와 bounded/redacted raw evidence를 보존한다. Claude stop taxonomy는 Claude adapter가 이 상위 category로 mapping할 때만 사용한다.

## 3.3 `ProviderCapability/Request/Router` 전체 선설계 반대 — `ACCEPT`

Claude가 AR-1을 normalized result shape로 축소하자는 방향은 수용한다. 다만 request가 전혀 없어도 되는 것은 아니다. Codex POC를 호출하려면 최소한의 read-only `ReviewRequest` 계약은 필요하다.

초기 필요 범위:

- ReviewRequest
- ReviewResult
- ProviderAvailability
- ProviderFailure
- UsageObservation

초기 제외 범위:

- 범용 planning/implementation request
- 모든 provider capability matrix의 product contract화
- role-binding profile
- Router
- continuity/session 공통화

## 3.4 `claude-invoke-core` generic rename/refactor — `ACCEPT: 하지 않음`

Claude와 Codex 모두 POC 단계에서 Claude core를 generic화하지 않는 데 동의한다. 이 합의는 Round 3에서 명시적 non-goal이어야 한다.

---

# 4. State와 taxonomy에 대한 비판

## 4.1 `usage_guard`/`role_sliced`에 Agent Runtime 상태를 넣는 방안 — `REJECT`

Claude는 새 `agent_runtime` top-level key보다 기존 `usage_guard` 또는 `role_sliced` opaque `RawJsonValue` slot 재사용을 선호한다. 이는 wire compatibility만 보고 의미적 compatibility를 놓친 판단이다.

각 필드는 이미 고유 의미가 있다.

- `usage_guard`: usage/budget 관련 진단
- `role_sliced`: role-sliced execution 관련 진단

여기에 provider binding, reviewer, debate, fallback, availability까지 넣으면 다음 문제가 생긴다.

- 서로 다른 관심사의 schema가 한 필드에서 충돌
- role-sliced가 아닌 planning/doc/review 결과를 표현하기 어려움
- usage와 provider execution 상태를 분리해 읽을 수 없음
- 향후 typed 승격 시 migration이 더 어려움
- 필드 이름과 실제 의미가 달라져 문서 drift 발생

또한 새 optional `agent_runtime` key가 과거 `required_next_action` BLOCKER와 같은 변경 class라는 비유는 정확하지 않다.

- `required_next_action`은 guaranteed envelope로 문서화됐고 HRNS가 required로 읽었다.
- 미래 `agent_runtime`은 key 부재 허용, readiness 영향 0, old reader ignore를 전제로 할 수 있다.
- `ignoreUnknownKeys=true`인 기존 HRNS parser는 알 수 없는 top-level key를 무시한다.

수정된 단계:

1. POC: State에 아무것도 쓰지 않고 daily log artifact만 사용
2. Guarded lane 안정화: 필요할 경우 새 optional `agent_runtime` extension 검토
3. 충분한 evidence 후에만 HRNS DTO/raw projection 추가
4. readiness-required typed field 승격은 별도 compatibility phase

## 4.2 lane과 task-class taxonomy의 “문자 그대로 병합” — `REJECT_AS_STATED`

Claude는 D0/L1/F1/G2/C3 lane 모델을 기존 task class A–G와 budget tier -1..3에 문자 그대로 병합해야 한다고 주장했다. 두 taxonomy를 별도로 중복 운영해서는 안 된다는 문제의식은 맞지만, 두 개념은 같은 축이 아니다.

```text
Task class / budget tier
= 작업 복잡도, 위험, 예상 effort와 budget

Execution lane
= deterministic/local/frontier/dual/critical 실행 전략
```

예를 들어 같은 task class에서도 security 영향이나 provider unavailable 상태에 따라 single-frontier와 guarded-dual이 달라질 수 있다. 반대로 G2가 항상 특정 A–G class를 의미하지도 않는다.

권장:

- 하나의 Kit policy 문서가 두 축과 mapping rule을 소유
- `task_class`, `budget_tier`, `execution_lane`은 구분된 필드
- Router 도입 전에는 `execution_lane`을 runtime contract로 만들지 않음
- 중복된 별도 정책 문서를 만들지 않음

즉 **단일 정책 정본에는 동의하지만 단일 enum/taxonomy로의 병합에는 동의하지 않는다.**

---

# 5. Backward compatibility와 rollback 위험

## 5.1 `build-context-diet-packet.ps1` additive 확장 — `DEFER`

Claude는 기존 packet을 그대로 읽거나 additive 필드로 확장할 수 있다고 제안했다. 첫 POC에서는 기존 builder를 변경하지 않는 편이 안전하다.

이유:

- 현재 context-diet는 별도 진단 목적과 smoke가 있다.
- reviewer-specific goal/diff/acceptance를 넣으면 책임이 섞인다.
- additive라도 packet size와 consumer 기대가 바뀐다.
- context-diet active graduation과 Codex reviewer 도입의 효과를 분리하기 어려워진다.

POC는 기존 packet을 optional evidence로 읽을 수 있지만 자체 Review Packet을 생성해야 한다.

## 5.2 “POC compatibility 영향 0” 표현 — `REVISE`

`run-cycle.ps1` 밖의 default-off POC는 production runtime 영향이 매우 작지만 문자 그대로 0은 아니다.

Live Kit에 public script를 추가하면 다음 표면이 바뀐다.

- release file set
- dependency inventory 판단
- docs/map/roadmap
- smoke inventory/count
- pollution/packaging scan
- optional executable availability 진단
- daily logs retention

따라서 compatibility verdict 전체를 다시 여는 수준은 아니더라도 다음 검증은 필요하다.

- PowerShell parser check
- 신규 offline smoke
- 기존 전체 offline suite
- Doctor/Validate-Ops regression
- release hygiene/pollution scan
- HRNS `gradlew check` 또는 변경 없음에 대한 정당화

## 5.3 “스크립트 디렉터리와 로그 디렉터리 삭제로 완전 복구” — `REVISE`

Rollback은 삭제할 디렉터리 두 개로 표현하면 부족하다.

구현 시 예상되는 변경에는 다음도 포함될 수 있다.

- `SMOKE_INDEX.md`
- README/MAP/ROADMAP
- runtime dependency inventory
- optional doctor check
- schema/test fixture

또한 live `D:\harness-kit`은 Git repository가 아니므로 단순 삭제 전에 정확한 file manifest와 verified snapshot이 필요하다.

권장 rollback contract:

```text
clean before-snapshot
+ exact changed-file manifest
+ before/after SHA-256
+ isolated scratch verification
+ explicit restoration procedure
```

Runtime log는 source rollback과 분리해 보존/정리 정책에 따라 처리한다. broad recursive delete를 rollback 방법으로 삼지 않는다.

## 5.4 기존 Claude path byte-identical 보장 — `ACCEPT`

POC 미호출 시 기존 wrapper/process/State behavior가 동일해야 한다. 기존 shared lib를 수정하지 않으면 가장 쉽게 달성된다.

---

# 6. Token과 비용 관점의 비판

## 6.1 AR-0 10–20개 task를 AR-2 hard prerequisite로 두는 방안 — `REVISE`

Claude는 10–20개 실제 bounded task의 경제성 측정을 사용자 소유 병행 업무로 제안한다. 확대 측정 자체는 필요하지만 최초 read-only capability POC보다 먼저 전부 끝낼 필요는 없다.

이유:

- 10–20개 실제 task는 시간과 provider quota를 많이 사용한다.
- 아직 Review Packet과 Codex result parse가 안정되지 않아 비교 대상 자체가 없다.
- 대규모 측정 전 작은 표본으로 측정 절차의 오류를 먼저 발견해야 한다.

권장 Gate:

1. 기존 usage ledger로 current baseline 수집
2. small/medium/heavy 각 1개 정도의 고정 표본으로 측정 절차 검증
3. read-only POC capability 확인
4. 3–5개 bounded review로 초기 가치 판단
5. production graduation 전에 10–20개로 확대

가치 주장은 확대 측정 전까지 보류한다.

## 6.2 기존 경량화 opt-in을 먼저 default로 올리는 방안 — `DEFER`

Claude는 packet-first, parent-deterministic navi, compact prompt가 새 provider보다 싼 레버라고 지적했다. 사실이지만 이 옵션들은 live A/B 없이 default graduation이 금지된 상태다.

따라서 다음을 분리한다.

- 현재 Claude 비용 baseline 측정에는 포함 가능
- Codex reviewer POC의 prerequisite로 default-on하지 않음
- 각각 별도 semantic parity/live Gate 유지

## 6.3 POC의 기본 provider `none` — `REVISE`

Codex 하나만 검증하는 entrypoint에 범용 `provider=none|codex` 설정을 만들 필요는 없다. `invoke-codex-review.ps1`을 명시적으로 실행하는 행위 자체가 provider 선택이다.

필요한 Gate는 다음 정도다.

- fake invoker가 기본인 offline smoke
- live 호출 시 명시적 `-AllowLiveCodex`
- unattended runtime 자동 호출 없음

Provider selection profile은 Router Phase까지 미룬다.

---

# 7. 누락된 Test와 Gate

Claude 문서가 주요 검증 항목을 잘 열거했지만, 다음 Gate를 더 구체화해야 한다.

## 7.1 Read-access boundary Gate — `ADD`

Repository write가 없다는 것만으로 충분하지 않다.

- project root 밖 read 시도
- 다른 drive read 시도
- environment variable 열람
- user config/credential 파일 열람
- symlink/junction을 통한 escape

최초에는 저민감 fixture에서 관찰하고, read allowlist를 보장할 수 없으면 sanitized review bundle 방식을 유지한다.

## 7.2 Complete change-set Gate — `ADD`

다음 입력을 table-driven fixture로 검증한다.

- unstaged tracked
- staged tracked
- untracked
- deleted
- renamed
- commit diff
- branch-base diff
- binary
- submodule
- unrelated pre-existing dirty file

명시한 scope와 실제 packet이 다르면 Codex를 호출하기 전에 fail-closed한다.

## 7.3 Instruction precedence Gate — `ADD`

Fixture에 root/nested `AGENTS.md`를 두고 다음을 확인한다.

- 기존 project instruction 보존
- Harness read-only restriction과 충돌 탐지
- scope 확장 지시 거부
- global config 미수정
- repository bridge 무생성

현재 HRNS repository에는 filesystem `AGENTS.md`가 없으므로 별도 fixture가 필요하다.

## 7.4 Prompt-injection Gate — `ADD`

Diff/source/test output 안에 다음 문자열을 넣고 evidence로만 취급하는지 확인한다.

- 다른 파일을 읽으라는 명령
- sandbox를 우회하라는 명령
- secret을 출력하라는 명령
- Git mutation을 요구하는 명령
- 성공 verdict를 강제하는 명령

## 7.5 Claim audit Gate — `ADD`

각 finding에 최소 다음을 요구한다.

- finding id
- severity
- file path
- line 또는 evidence anchor
- claim
- supporting evidence
- suggested correction

부모 audit는 path가 allowlist 안인지, line/anchor가 존재하는지, diff와 관련 있는지 확인한다. audit가 의미적 correctness를 증명한다고 과장하지 않는다.

## 7.6 Windows process Gate — `ADD`

- 공백·한글 경로
- quote/newline prompt
- UTF-8 stdout/stderr
- 동시 대량 출력
- timeout
- cancellation
- child process 잔존
- output truncation
- console window visibility

기존 Claude runner의 구현을 무비판적으로 복사하지 말고 fake executable로 동작을 증명한다.

## 7.7 Output/retention Gate — `ADD`

- JSONL 최대 크기
- raw stdout/stderr 보존 여부
- absolute path masking
- secret-shaped value masking
- session/account identifier 비저장
- release package에서 runtime evidence 제외
- 날짜/run-id containment 및 traversal 방지

## 7.8 CLI version/capability Gate — `ADD`

- executable resolution
- `--version` parse
- required option 존재
- unsupported option/version
- auth unavailable
- live invocation disabled by default

CLI가 설치돼 있어도 live capability를 자동으로 true로 두지 않는다.

## 7.9 Regression Gate — `ADD`

Live Kit 변경 후 최소:

- changed PS1 parser check
- 신규 mock smoke
- official offline suite 전체
- Doctor/Validate-Ops
- release hygiene/pollution scan
- live provider 0건인 자동 suite 확인

HRNS source가 바뀌지 않더라도 production-to-production boundary가 영향받지 않았는지 근거를 남긴다.

---

# 8. 더 작은 Vertical Slice

Claude가 제안한 Seam 0을 더 줄여 다음처럼 확정 후보로 삼는다.

## 8.1 Slice 이름

```text
Codex Read-only Review Capability POC
```

`external-review framework`나 `provider runtime`을 첫 이름으로 사용하지 않는다.

## 8.2 단계 A — Fixture-only capability spike

- local Codex CLI version/help probe
- fake executable로 process/output/failure 계약 검증
- low-sensitivity isolated fixture
- repository/State/run-cycle 변경 없음
- global config 변경 없음

현재 version/help probe는 이미 읽기 전용으로 확인됐다. live auth/call은 미검증이다.

## 8.3 단계 B — Codex-specific Review Packet

새 packet은 다음 explicit input으로만 생성한다.

- task id/goal
- review base
- exact changed-file manifest
- actual diff/source snapshot
- acceptance criteria
- validation evidence와 exit code
- allowed/forbidden scope
- source/State/target hashes
- relevant project instruction evidence
- timeout/output budget

기존 context-diet packet은 optional source이며 수정하지 않는다.

## 8.4 단계 C — Offline adapter smoke

Codex-specific adapter를 fake executable로 검증한다. 이 단계에는 provider network 호출이 없다.

## 8.5 단계 D — Manual opt-in live review 1건

- 공개 가능하거나 저민감인 작은 fixture
- `read-only`, `ephemeral`, global config 미사용 후보 옵션
- no Git/repository mutation audit
- normalized result와 bounded raw evidence
- usage는 실제 제공된 값만 기록

## 8.6 POC 산출물

개념 경로:

```text
<workspace>/<date>/logs/codex-review/<run-id>/
  review.packet.json
  evidence.manifest.json
  codex.events.jsonl        # 보존 승인 시에만
  codex-review.result.json
  codex-review.audit.json
```

필수 invariant:

```text
authoritative=false
safe_to_auto_adopt=false
runtime_integrated=false
repository_write=false
git_mutation=false
```

## 8.7 첫 Slice에서 변경하지 않는 것

- `build-context-diet-packet.ps1`
- `claude-invoke-core.ps1`
- `run-cycle.ps1`
- `WORKFLOW_STATE.json`
- Secondary LLM schema
- HRNS source/UI
- role-binding profile
- Router
- Claude resolution
- Codex write
- commit automation
- MSI/runtime bundle

---

# 9. Claude 문서의 항목별 판정

| Claude Round 1 주장 | 판정 | Codex 교차검토 결론 |
|---|---|---|
| HRNS에 대한 provider lock-in 표현이 과장됨 | `ACCEPT` | 실제 결합은 Kit runner/Claude helper에 집중돼 있다. |
| Provider Runtime/Router 전체 설계는 이름 | `ACCEPT` | 최소 ReviewRequest/Result만 먼저 필요하다. |
| `usage_guard`/`role_sliced` slot 재사용 선호 | `REJECT` | 의미적 과적재다. POC는 State-free, 이후 새 optional `agent_runtime`이 더 깨끗하다. |
| 첫 slice에 role-binding profile을 두지 않음 | `ACCEPT` | Codex-specific explicit entrypoint로 충분하다. |
| Ollama 기존 자산을 과소평가하면 안 됨 | `ACCEPT_WITH_REVISION` | evidence/audit 패턴은 재사용하되 Ollama schema/client/capability는 adapter 내부에 둔다. |
| Codex write/debate를 hard-gate해야 함 | `ACCEPT` | production write와 commit authority는 훨씬 뒤의 별도 승인 사항이다. |
| 실행 증거가 문서보다 우선 | `ACCEPT` | source와 재현 가능한 실행 결과가 current truth다. |
| `claude-invoke-core.ps1`이 곧 Claude adapter | `REVISE` | launcher는 세 runner에 남아 있어 전체 adapter의 일부일 뿐이다. |
| context-diet packet을 첫 reviewer 입력으로 사용 | `REVISE` | optional source로만 사용하고 explicit Review Packet을 만든다. |
| Secondary LLM subsystem을 그대로 모델로 사용 | `REVISE` | 구조는 모델이지만 Git evidence 범위와 Ollama-specific schema를 그대로 쓰면 안 된다. |
| `external-review` generic subsystem부터 생성 | `REVISE` | 첫 구현은 `codex-review`로 provider-specific하게 둔다. |
| AR-0 10–20개 task 병행 | `ACCEPT_WITH_REVISION` | 확대 측정은 graduation 전, 초기에는 소규모 고정 표본으로 측정 절차부터 검증한다. |
| POC compatibility 영향 0 | `REVISE` | runtime 영향은 낮지만 release/docs/smoke/retention 표면은 바뀐다. |
| Native QA는 AR과 병행 가능 | `ACCEPT_WITH_GATE` | 분석/fixture spike는 병행 가능, live Kit mutation 전 baseline 기록 권고. |
| Router는 Harness 소유 | `ACCEPT` | 단 HRNS ActionPolicy와 Harness provider selection의 책임을 분리한다. |

---

# 10. Claude 문서를 수용하며 Codex Round 1을 수정하는 부분

Claude 분석은 Codex Round 1에도 다음 보완을 요구한다.

## 10.1 파일명 정정

Codex Round 1의 잘못된 wrapper 파일명을 실제 이름으로 정정한다.

```text
invoke-planning-cycle.ps1
invoke-code-execution.ps1
invoke-document-execution.ps1
```

## 10.2 3-file bridge 파급 범위 강화

Codex bridge를 추가하면 단순 문서 변경이 아니라 HRNS resolver/probe/test/native-QA 계약까지 영향을 준다는 Claude 지적을 수용한다. 따라서 POC bridge-free를 hard acceptance criterion으로 올린다.

## 10.3 AR-0의 실제 소유자 명시

Direct CLI와 live provider를 사용하는 경제성 표본 실행은 자동 offline 검증이 아니라 사용자 승인 활동이다. 계획서에서 이를 명시해야 한다.

## 10.4 HRNS의 provider-shaped taxonomy 인정

HRNS가 구조적으로 provider coupled되지는 않았지만 `StopReason` 등에 Claude 이름이 남아 있다는 Claude의 세밀한 지적을 수용한다. 다만 POC에서 이를 generic rename하지 않고, 훗날 실제 다중 provider State를 읽어야 할 때만 migration을 검토한다.

## 10.5 existing Task/Token policy와의 관계 강화

새 lane 정책은 별도 독립 문서로 병렬 생성하지 않고 기존 `TASK_CLASS_TOKEN_POLICY.md`가 mapping 규칙을 소유해야 한다. 단 task class/budget tier/lane은 서로 다른 축으로 유지한다.

---

# 11. Round 3으로 넘길 합의점과 미합의점

## 11.1 합의된 방향

1. HRNS는 provider-blind Control Plane으로 유지한다.
2. Provider invocation과 장기 Router는 Harness가 소유한다.
3. 첫 POC는 Codex read-only reviewer다.
4. 첫 POC는 `run-cycle.ps1`, State, HRNS, bridge 밖에 둔다.
5. 기존 Claude-only path는 기본 및 rollback 경로다.
6. global config/AGENTS/CLAUDE 파일은 수정하지 않는다.
7. automatic commit, Codex write, parallel execution은 제외한다.
8. Secondary LLM의 evidence/audit 및 containment 패턴은 참고한다.
9. Ollama의 non-authoritative/default-off/hardware gate를 유지한다.
10. 사용량 미측정 값은 unknown/null로 남긴다.
11. Native UI baseline과 provider POC 문제를 분리한다.
12. 최종 구현은 Round 3–5와 사용자 확인 뒤에만 시작한다.

## 11.2 통합 계획에서 Codex가 요구하는 수정

1. POC naming과 source는 Codex-specific하게 시작한다.
2. context-diet packet을 수정하거나 primary contract로 사용하지 않는다.
3. complete change-set용 explicit manifest/diff builder를 둔다.
4. Claude stop taxonomy를 provider-neutral schema로 복사하지 않는다.
5. State는 POC에서 변경하지 않는다.
6. 후속 State가 필요하면 `usage_guard` 과적재 대신 optional `agent_runtime`을 우선 비교한다.
7. task class/budget/lane을 하나의 enum으로 합치지 않는다.
8. read isolation, prompt injection, dirty tree, retention Gate를 추가한다.
9. 10–20개 A/B는 graduation Gate로 이동한다.
10. live Kit rollback을 exact manifest/hash/snapshot으로 정의한다.

## 11.3 아직 사용자 또는 추가 실험이 필요한 항목

- 실제 Codex 인증/noninteractive 호출 가능 여부
- Windows read-only sandbox의 read 범위
- project `AGENTS.md`와 ephemeral review instruction의 precedence
- raw JSONL 보존 여부
- live Codex review에 허용할 첫 fixture
- Native UI QA를 live Kit POC mutation의 hard prerequisite로 둘지
- optional `agent_runtime`을 State에 넣는 정확한 시점
- provider executable의 host dependency/bundle 정책
- A/B graduation threshold

---

# 최종 Cross-Review 결론

Claude의 Round 1은 제안서의 범위를 줄이고 현재 Harness의 실제 자산을 발견했다는 점에서 유효하다. 특히 “HRNS를 건드리지 말고 `run-cycle.ps1` 밖에서 read-only reviewer를 시작한다”는 결론은 그대로 수용한다.

그러나 existing asset 재사용을 다음처럼 구분해야 한다.

```text
재사용 가능
  evidence/audit 패턴
  path containment
  deterministic validation 원칙
  usage known/unknown 원칙
  daily log artifact 구조

직접 재사용 불가
  context-diet packet을 reviewer 주 계약으로 사용
  Secondary LLM의 현재 git diff summary를 complete change set으로 사용
  Ollama-specific schema/client/capability
  Claude stop taxonomy를 provider-neutral result로 사용
  usage_guard/role_sliced를 provider runtime State로 과적재
```

Round 3 통합 계획의 가장 작은 시작점은 다음이어야 한다.

```text
Codex-specific read-only report entrypoint
→ explicit Review Packet과 complete change manifest
→ fake-invoker offline validation
→ 저민감 fixture의 manual opt-in live 1건
→ deterministic claim/mutation audit
```

이 결과가 안정되기 전에는 generic Provider Runtime, Router, State/UI, Codex write, Ollama 연결을 설계·구현 범위로 올리지 않는다.

---

## Mutation 및 검증 기록

- 읽은 문서: `revOpinion_claude01.md`, `revOpinion_codex01.md`
- 추가 확인: live Secondary LLM advisory 경로, evidence/input builder, context-diet packet builder, Claude invoke core header, 실제 public wrapper 파일명
- HRNS-NOW production source 변경: 없음
- `D:\harness-kit` 변경: 없음
- global Codex/Claude 설정 변경: 없음
- Git add/commit/push/reset/restore/stash: 없음
- build/Gradle/Harness smoke 실행: 없음
- live Claude/Codex/Ollama 호출: 없음
- 새로 작성한 파일: `doc/revolution/revOpinion_codex02.md`
