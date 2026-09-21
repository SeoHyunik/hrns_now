# HRNS-NOW Agent Runtime 진화 제안에 대한 Codex 독립 아키텍처 검토 01

## 문서 상태

- 작성일: 2026-09-04 (Asia/Seoul)
- 검토 단계: Round 1 — Independent Architecture Review
- 검토 주체: Codex
- 대상 제안서: `doc/claude_prompts/hrns_now_agent_runtime_evolution_proposal.md`
- 문서 성격: 분석 및 교차검토 입력 자료
- 구현 승인 여부: 미승인
- Production source 수정 여부: 없음

이 문서는 제안서를 구현 명세로 취급하지 않고, HRNS-NOW와 live Harness Kit의 현재 코드·문서·계약을 먼저 대조한 뒤 작성한 Codex의 독립 검토 결과다. Claude의 Round 1 결과와 교차검토하기 전의 의견을 보존하는 것이 목적이며, 이 문서 자체는 최종 구현 계획이나 구현 승인을 의미하지 않는다.

---

## 기준선

| 항목 | 확인값 |
|---|---|
| HRNS-NOW branch | `harness-dev` |
| HRNS-NOW HEAD | `8757527` (`docs: define Codex Claude collaboration workflow`) |
| 원격 기준선 | `origin/harness-dev`와 동일 HEAD |
| Working tree | 제안서 `doc/claude_prompts/hrns_now_agent_runtime_evolution_proposal.md`만 untracked |
| Live Harness Kit | `D:\harness-kit` |
| Kit version | `2026.09.02` |
| State schema | `1.0` |
| UI contract | `1.0` |
| 현재 호환성 verdict | `COMPATIBLE_WITH_NONBLOCKING_GAPS` |
| 확인한 Codex CLI | `codex-cli 0.153.2` |

기존 호환성 감사에서 재현됐던 BLOCKER/HIGH는 닫혀 있다. 남은 호환성 non-blocking gap은 packaged-app console-window-flash의 미검증과 `Invoke-RunCycleWrapper` 내부 exit-code 처리의 미확정이다. 별도 제품 Gate로는 Native UI QA, clean Windows MSI lifecycle, bundled runtime 최종화가 남아 있다.

이번 검토에서는 production code, live Kit, 전역 AI 설정, Registry, 외부 workspace를 변경하지 않았다. build·Gradle·Harness smoke·live Claude·live Codex·live Ollama 호출도 수행하지 않았다. Codex CLI에 대해서는 로컬 `--version`과 `exec --help`, `exec review --help`만 읽기 전용으로 확인했다.

---

# VERDICT

## `REVISE_BEFORE_INTEGRATED_PLAN`

제안의 방향은 타당하다. HRNS-NOW를 Control Plane으로 유지하고, Harness를 실행 엔진으로 유지하며, provider와 role을 분리하고, Codex를 read-only reviewer부터 도입하려는 핵심 방향에는 동의한다.

다만 현재 제안 범위를 그대로 구현하면 기존 실행 패킷, Claude 전용 코어, Secondary LLM 자산을 성급하게 하나의 범용 Provider Runtime으로 묶을 위험이 있다. 첫 구현은 범용 Router나 거대한 Provider abstraction이 아니라, 현재 표준 실행 경로와 분리된 **Codex Read-only Reviewer report POC**로 축소해야 한다.

따라서 현재 결론은 다음과 같다.

1. 방향 검토는 계속 진행한다.
2. 아직 production 구현을 시작하지 않는다.
3. Claude의 독립 Round 1 결과와 Cross Review를 먼저 수행한다.
4. 첫 구현 후보는 `run-cycle.ps1`, `WORKFLOW_STATE.json`, HRNS UI를 건드리지 않는 독립 report lane으로 제한한다.
5. POC 결과가 충분할 때만 공통 계약, guarded one-round 연결, Router 순서로 확장한다.

---

# 1. 현재 아키텍처 이해

## 1.1 HRNS-NOW의 책임

HRNS-NOW는 Provider 실행기가 아니라 안전한 데스크톱 Control Plane이다.

현재 핵심 실행 흐름은 다음과 같다.

```text
UiAction
→ ActionPolicy
→ HarnessCommandMapper
→ typed HarnessCommand
→ HarnessCommandEncoder
→ PowerShellHarnessAdapter / JvmProcessExecutor
→ Harness script 실행
→ WORKFLOW_STATE.json 재조회
→ typed State 및 UI projection
```

중요한 현재 불변식은 다음과 같다.

- HRNS-NOW는 Claude, Codex, Ollama API를 직접 호출하지 않는다.
- HRNS-NOW는 provider credential이나 native provider 설정을 소유하지 않는다.
- shell command 문자열을 조립하지 않고 typed command를 argument 목록으로 encoding한다.
- 실행 성공 여부를 stdout 산문만으로 판단하지 않고 exit code, JSON contract, State 재조회로 판단한다.
- readiness가 불명확하거나 schema가 malformed/unsupported이면 fail-closed한다.
- Kit root, workspace root, repository root의 경계를 유지한다.
- HRNS UI는 `WORKFLOW_STATE.json`을 직접 수정하지 않는다.

따라서 multi-provider 도입을 이유로 HRNS의 `HarnessCommand`, `ActionPolicy`, PowerShell adapter, State reread 구조를 먼저 변경할 이유가 없다.

## 1.2 Harness Kit의 책임

Harness Kit은 실행 엔진이며 `WORKFLOW_STATE.json`의 유일한 writer다. planning, code/doc execution, validation, closure, queue 전이, stop/failure 기록을 소유한다.

현재 daily required surface는 정확히 다음 네 파일이다.

```text
REQUEST_INBOX.md
TODAY_STRATEGY.md
DAILY_HANDOFF.md
WORKFLOW_STATE.json
```

Repository bridge는 정확히 다음 세 파일이다.

```text
.claude/settings.local.json
.claude/CLAUDE.md
tools/run-cycle.ps1
```

Provider 실행을 추가하더라도 이 표면을 무심코 확장하거나 legacy `WORK_QUEUE.json`, `WORKDAY_STATE.json`, `REQUEST_STRUCTURED.md`를 기본 required artifact로 되돌리면 안 된다.

## 1.3 현재 Claude 결합 지점

Claude 결합은 주로 다음 위치에 존재한다.

- `scripts/invoke-planning.ps1`
- `scripts/invoke-code.ps1`
- `scripts/invoke-documentation.ps1`
- `scripts/lib/plan/planning-claude-runner.ps1`
- `scripts/lib/code/code-claude-runner.ps1`
- `scripts/lib/code/code-role-sliced-runner.ps1`
- `scripts/lib/doc/doc-claude-runner.ps1`
- `scripts/lib/claude/claude-invoke-core.ps1`

Planning/code/doc runner에는 각각 `Start-ClaudeProcess`가 존재하며 timeout, stdout/stderr 동시 수집, no-window 실행, response projection을 처리한다. 이 가운데 process supervision에는 공통화 가능성이 있지만, 실제 launcher와 argument/output semantics는 아직 Claude에 묶여 있다.

`claude-invoke-core.ps1`은 단순한 generic process runner가 아니다. 다음 Claude 고유 의미를 가진다.

- Claude stop classification
- stream-json telemetry 및 response projection
- Claude session id capture
- `--resume <session-id>` 기반 continuity broker
- usage ledger 기록
- Claude context/usage/budget stop reason

그러므로 해당 파일을 처음부터 `provider-invoke-core.ps1` 같은 범용 이름과 계약으로 바꾸는 것은 잘못된 첫 seam이다.

## 1.4 이미 존재하는 execution packet

`code-role-sliced-runner.ps1`의 `00-wrapper-input.json` schema 2.0에는 이미 다음이 포함된다.

- run/step identity
- execution wrapper 및 orchestration mode
- project/workspace/kit/today root
- workflow/strategy/request/handoff pointer
- active card/slice lineage
- authorized target
- allowed/forbidden write files
- current micro-slice 및 continuation
- budget continuation 정보
- generated time 및 freshness
- workflow/strategy/target hash
- plan generation 및 request revision hash
- acceptance criteria
- required evidence
- validation plan
- fallback read paths
- continuation history risks

부모 프로세스는 packet 생성 후와 worker 실행 직전에 live State, strategy, target, queue lineage, hash와 freshness를 다시 비교한다. 이는 강한 TOCTOU 및 write-authority 계약이다.

그러나 이 패킷은 `execution_wrapper=code`, `orchestration_mode=role_sliced`인 변경 작업용 패킷이다. Read-only reviewer, planning, documentation, Ollama advisory가 모두 공유하는 범용 Task Packet으로 그대로 승격하면 불필요한 필드와 write semantics가 core contract에 섞인다.

## 1.5 Context diet와 deterministic 경로

현재 Harness에는 다음 경량화 자산이 이미 있다.

- planning/code/doc local deterministic preflight
- execution packet v2
- packet-first navi opt-in
- parent-deterministic navi opt-in
- context-diet diagnostic packet
- usage ledger schema 1.1
- task-class/token policy

다만 `HARNESS_CONTEXT_DIET_MODE=active`는 인식되지만 production prompt 대체는 실행-disabled다. packet-first와 parent-deterministic navi도 opt-in이며 기본은 Claude다. 따라서 제안서가 언급한 progressive context와 packet-first 방향은 완전히 새로운 기능이 아니지만, production 기본 경로로 졸업한 기능도 아니다.

## 1.6 Secondary LLM 자산

현재 Secondary LLM lane은 다음을 이미 구현한다.

- evidence manifest
- sanitized input packet
- run-root containment
- structured candidate
- deterministic audit
- docs mismatch, diff summary, validation summary, reviewer advice, next-session prompt 후보
- loopback-only 및 GPU/acceleration capability gate
- mock 기반 offline smoke

동시에 다음 불변식을 가진다.

```text
authoritative=false
safe_to_auto_adopt=false
runtime_integrated=false
default-off
```

이는 provider-neutral execution adapter라기보다 안전한 **evidence/candidate/audit pipeline**이다. 새 Codex reviewer는 이 패턴을 재사용할 수 있지만 Ollama의 model, HTTP, hardware, response-kind 계약을 상속해서는 안 된다.

## 1.7 기존 문서와 코드의 정밀도 gap

별도 기존 backlog로 다음 정밀도 gap이 있다.

- Harness 문서는 `queue.status`, `queue.active.card_id`, `queue.active.slice_id`, `queue.blocked_reason`, `queue.last_updated_at`을 stable queue pointer로 함께 설명한다.
- 현재 HRNS `WorkflowStateMapper`는 `queue.status`만 key 부재 시 failure로 처리한다.
- 나머지 네 필드는 key가 없어도 null/blank로 흡수한다.

현재 Harness writer가 필드를 항상 쓰므로 관측 가능한 오작동은 없고 새로운 BLOCKER/HIGH도 아니다. 다만 State 계약을 수정하는 Agent Runtime 작업과 이 hardening을 함께 섞으면 원인과 회귀 범위를 분리하기 어려우므로 별도 bounded task로 유지해야 한다.

---

# 2. 제안서에 동의하는 부분

1. **HRNS-NOW와 Harness 책임 경계 유지**  
   Provider invocation은 Harness 내부 책임이며 Kotlin UI/domain으로 Claude/Codex/Ollama 세부사항이 새면 안 된다.

2. **Role과 Provider 분리**  
   Planner, Implementer, Reviewer, Auditor 같은 역할이 안정 개념이고 Claude/Codex/Ollama binding은 policy/profile의 선택이어야 한다.

3. **Provider native configuration 격리**  
   Claude와 Codex의 설정 체계를 거대한 공통 configuration framework로 다시 만들면 안 된다.

4. **사용자 전역 설정 무변경**  
   `%USERPROFILE%\.codex\config.toml`, 사용자 `AGENTS.md`, Claude 전역 설정을 자동 수정하지 않는다.

5. **Deterministic first**  
   build, test, lint, hash, schema, file classification처럼 deterministic하게 처리할 수 있는 작업을 LLM에게 넘기지 않는다.

6. **Codex read-only reviewer 우선**  
   write authority 없이 launch, output, timeout, sandbox, failure semantics를 먼저 증명하는 것이 가장 작은 안전한 도입 순서다.

7. **Bounded debate**  
   기본 한 번의 critique/resolution 왕복으로 제한하고 전체 transcript 대신 compact structured artifact를 전달한다.

8. **불확실한 usage를 조작하지 않음**  
   측정되지 않은 token, cost, quota는 `null/unknown`으로 남기고 fabricated precision을 만들지 않는다.

9. **Ollama의 현재 경계 유지**  
   default-off, loopback-only, advisory-only, non-authoritative, hardware-gated 상태를 유지한다.

10. **State/UI 후순위**  
    provider runtime contract가 안정되기 전에 HRNS provider 선택 UI나 readiness-required State field를 추가하지 않는다.

11. **자동 commit 분리**  
    multi-provider POC와 commit authority 자동화를 같은 Phase에 넣지 않는다.

12. **병렬 fan-out 후순위**  
    provider 확대와 same-file parallel agent/worktree orchestration을 동시에 구현하지 않는다.

13. **기존 Claude-only path 보존**  
    새로운 lane은 opt-in이며 실패 시 기존 검증된 경로로 명시적으로 rollback할 수 있어야 한다.

---

# 3. 제안서에서 수정이 필요한 부분

## 3.1 기존 execution packet의 범용 승격은 제한해야 한다

제안서는 기존 packet 자산 재사용을 강조한다. 방향은 맞지만 execution packet v2 전체를 범용 Provider Task Packet으로 승격하는 것은 적절하지 않다.

권장 방식은 다음과 같다.

```text
기존 State/execution/evidence contract
→ read-only review에 필요한 최소 projection
→ Review Packet
```

즉 기존 패킷을 복제된 두 번째 runtime truth로 만들지 않고, 원본 artifact와 hash를 참조하는 얇은 파생 계약을 만들어야 한다.

## 3.2 첫 계약 설계 범위를 줄여야 한다

첫 POC 전에 다음 전체를 완성하려 하면 구현보다 abstraction 검증이 더 커진다.

- 범용 ProviderCapability
- 범용 Router
- 모든 role binding
- BudgetState 전체
- Claude/Codex/Ollama 공통 session model

최초에는 다음 최소 계약만 필요하다.

```text
ReviewRequest
ReviewResult
ProviderAvailability
ProviderFailure
UsageObservation
```

실제 두 번째 adapter와 runtime 연결 경험이 생긴 뒤 공통 영역을 넓혀야 한다.

## 3.3 Claude core의 성급한 generic화 금지

`claude-invoke-core.ps1`은 Claude의 session, resume, stream-json, stop taxonomy를 소유한다. 이를 첫 단계에서 범용화하면 provider 공통 의미와 Claude 고유 의미를 잘못 혼합한다.

Provider 공통 후보는 다음 정도다.

- process start/exit
- stdout/stderr 동시 수집
- timeout/cancel
- process-tree termination
- output byte limit
- no-window 실행

이조차 Codex adapter를 실제로 만든 뒤 두 구현의 동일성을 확인하고 추출해야 한다.

## 3.4 Secondary LLM schema를 범용 schema로 사용하지 않는다

Secondary LLM의 안전 자산 중 재사용할 것은 다음이다.

- evidence manifest
- sanitized packet builder
- contained output path
- candidate/audit 분리
- deterministic claim verification
- `authoritative=false` 경계

재사용하지 않을 것은 다음이다.

- Ollama provider 고정값
- BaseUrl/model/HTTP semantics
- GPU/VRAM acceleration contract
- `secondary_llm_*` response kind
- local candidate 전용 naming

## 3.5 A/B 측정을 POC의 과도한 선행조건으로 만들지 않는다

경제성 baseline은 반드시 필요하지만 10~20개 task의 Direct/Current/Lean 전체 비교가 끝날 때까지 read-only capability POC를 막을 필요는 없다.

권장 분리는 다음과 같다.

- POC 전: 기존 usage ledger와 소규모 고정 표본으로 현재 비용 및 human handoff 기준선 확보
- POC 중: 같은 표본에서 reviewer 추가 비용, finding 품질, human review 시간 측정
- graduation 전: task class별 충분한 표본으로 확대

## 3.6 Human Message Bus 제거는 POC 성공 조건이 아니다

독립 report POC는 안전성과 result contract를 검증할 뿐이다. 사용자의 수동 복사·전달이 실제로 줄어드는지는 guarded Claude→Codex 연결 이후에만 측정할 수 있다.

---

# 4. 숨은 위험

## 4.1 Codex sandbox의 read 범위

`--sandbox read-only`가 repository write를 막더라도 민감 파일의 read 범위까지 제한한다고 단정할 수 없다. 실제 Windows 환경에서 다음을 검증해야 한다.

- working root 밖 파일 접근 가능 여부
- 추가 drive/root 접근 가능 여부
- environment variable 노출 범위
- shell/tool을 통한 비의도 파일 열람 가능성

이 검증 전에는 실제 비밀정보를 포함할 수 있는 production repository를 Codex POC working root로 직접 사용하지 않는다.

## 4.2 사용자 global config와 실행 재현성

현재 CLI는 `--ignore-user-config`를 제공하지만 auth는 여전히 provider-native store를 사용한다. 다음을 구분해야 한다.

- 사용자 config 미로딩
- credential 사용
- project instruction 로딩
- execpolicy/rules 적용
- provider/network 호출

POC는 global config를 수정하지 않아야 할 뿐 아니라, 실행 후 어떤 설정이 실제로 적용됐는지 설명할 수 있어야 한다.

## 4.3 `AGENTS.md` 상속과 Harness instruction 충돌

현재 HRNS-NOW repository에는 filesystem `AGENTS.md`가 없다. 따라서 현재 repository만으로는 기존 project `AGENTS.md`와 Harness review instruction의 precedence를 검증할 수 없다.

최소 fixture가 필요하다.

- root `AGENTS.md`
- nested `AGENTS.md`
- Harness task packet과 충돌하는 지시
- read-only/no-git rule
- 허용 범위 밖 파일에 대한 지시

기존 repository의 `AGENTS.md`를 덮어쓰거나 bridge 파일로 자동 생성해서는 안 된다.

## 4.4 Dirty working tree와 사용자 소유 변경

`codex exec review --uncommitted` 같은 편의 경로는 staged, unstaged, untracked 변경을 모두 검토할 수 있다. 이 경우 current task와 무관한 사용자 변경이나 비밀 파일이 review input에 포함될 수 있다.

따라서 production POC는 다음을 명시해야 한다.

- review base
- 허용 changed files
- diff artifact
- untracked 포함 여부
- binary/submodule/LFS 처리
- 실행 전후 git status/hash

## 4.5 Prompt injection과 instruction/data 경계

source, diff, 문서, test output 안에 Agent에게 명령하는 문자열이 있을 수 있다. Review Packet은 evidence를 instruction으로 취급하지 않도록 명확한 delimiter와 source classification을 가져야 한다.

## 4.6 Structured output의 허위 확신

JSON Schema에 맞는 출력도 사실과 다를 수 있다. 다음 finding은 deterministic audit 없이는 채택하지 않는다.

- 존재하지 않는 파일/라인
- diff에 없는 변경 주장
- 실행하지 않은 테스트 성공 주장
- State와 다른 queue/target 주장
- 보안·호환성 근거 없는 verdict

## 4.7 CLI 버전 및 output drift

현재 확인한 버전은 `codex-cli 0.153.2`일 뿐 장기 계약이 아니다. 다음 drift를 대비해야 한다.

- option 이름 또는 지원 범위 변경
- JSONL event shape 변경
- output schema 처리 변경
- exit code 의미 변경
- quota/auth/network 오류 문구 변경
- session persistence 동작 변경

## 4.8 Windows process/encoding 위험

현재 Harness의 Claude runner가 이미 해결한 다음 문제를 Codex adapter가 다시 만들 수 있다.

- stdout/stderr 대량 출력 deadlock
- 한글과 공백이 포함된 경로
- quote/newline이 포함된 prompt
- timeout 뒤 child process 잔존
- console window flash
- UTF-8 decoding과 BOM
- 장문 argument line 제한

Codex prompt는 가능하면 거대한 command-line argument보다 stdin 또는 명시적 packet file로 전달하는 것이 낫다. 최종 방식은 동적 검증 후 확정해야 한다.

## 4.9 Raw output과 개인정보 보존

`--json` event stream, prompt, provider response에는 다음이 섞일 수 있다.

- 절대 사용자 경로
- source 내용
- 환경 정보
- provider 내부 identifier
- usage/account 관련 메시지

원시 출력 보존 기간, redaction, 최대 크기, release exclusion을 먼저 정의해야 한다.

## 4.10 경제성 측정 편향

Direct CLI, Current Harness, Lean Harness, Dual Review를 비교할 때 task 난이도, cache 효과, 반복 학습, reviewer 품질 기준이 다르면 결과가 무의미해진다. completed-and-validated task를 동일 단위로 비교해야 한다.

## 4.11 Live Kit의 변경 추적성

`D:\harness-kit`은 Git repository가 아니다. Provider Runtime POC를 live tree에서 직접 구현할 경우 rollback과 provenance가 약하다. 실제 구현 전에는 검증된 backup, 변경 manifest, before/after hash, scratch 검증, release hygiene 절차를 명시해야 한다.

---

# 5. Provider 추상화 경계

## 5.1 권장 위치

자연스러운 첫 경계는 HRNS가 아니라 Harness 내부, 그리고 기존 planning/execution wrapper 아래가 아니라 **독립 review report entrypoint 위**다.

```text
명시적 review scope와 evidence
        ↓
Provider-neutral Review Packet
        ↓
Codex-specific Adapter
        ↓
Normalized Review Result
        ↓
Deterministic Audit
        ↓
Human-visible Recommendation
```

POC 단계에서는 Router가 필요하지 않다. 사용자가 명시적으로 Codex reviewer lane을 호출한다.

## 5.2 Provider-neutral 최소 입력

초기 Review Packet 후보는 다음 범위면 충분하다.

```json
{
  "schema_version": "0.1",
  "task_id": "...",
  "role": "reviewer",
  "mode": "read_only",
  "goal": "...",
  "risk": "...",
  "allowed_scope": [],
  "forbidden_scope": [],
  "acceptance_criteria": [],
  "required_evidence": [],
  "validation_evidence": [],
  "context_pointers": [],
  "evidence_manifest": "...",
  "state_hash": "...",
  "target_hashes": {},
  "timeout_seconds": 0
}
```

이는 확정 schema가 아니다. 특히 write-authority 필드는 reviewer POC에 넣지 않거나 항상 빈 배열로 고정해야 한다.

## 5.3 Provider-neutral 최소 결과

```json
{
  "schema_version": "0.1",
  "provider": "codex",
  "role": "reviewer",
  "status": "completed|unavailable|blocked|timed_out|cancelled|contract_invalid|usage_limited|failed",
  "verdict": "accept|revise|block|null",
  "findings": [],
  "open_questions": [],
  "changed_files": [],
  "usage": {
    "input": null,
    "output": null,
    "cost": null,
    "source": "provider_reported|estimated|unknown"
  },
  "failure": null,
  "mutation_audit": {
    "repository_unchanged": false,
    "git_unchanged": false
  }
}
```

`mutation_audit`은 provider가 자기 자신에 대해 주장하게 해서는 안 된다. 부모 Harness가 실행 전후 snapshot을 비교해 채워야 한다.

## 5.4 Provider 내부에 남겨야 하는 것

### Claude adapter 내부

- Claude CLI argument
- stream-json event 해석
- Claude session capture
- resume broker
- Claude-specific stop classification

### Codex adapter 내부

- `codex exec` invocation
- sandbox/config/ephemeral option
- Codex JSONL 및 final result 추출
- Codex-specific auth/quota/version failure
- Codex project instruction interaction

### Ollama adapter 내부

- loopback BaseUrl
- model selection
- tags/chat HTTP contract
- GPU/VRAM 및 acceleration proof
- CPU diagnostic override

## 5.5 나중에 공통화할 수 있는 것

두 adapter에서 실제 동일성이 확인된 후 다음을 작게 추출할 수 있다.

- bounded process supervisor
- normalized availability probe
- result envelope
- usage observation envelope
- artifact containment/redaction
- failure category의 상위 집합

Provider-native 세부 failure code와 session 의미는 끝까지 adapter 내부에 남겨야 한다.

---

# 6. 최소 첫 POC

## 6.1 목표

기존 Claude execution path를 변경하지 않고 Codex를 production task의 독립 read-only reviewer로 호출할 수 있는지 증명한다.

POC가 증명할 것은 다음뿐이다.

- Codex CLI availability/version 탐지
- noninteractive launch
- read-only/no-git/no-write 경계
- timeout/cancel/process 종료
- structured result parse
- malformed/empty/quota/auth/unavailable 실패 분류
- evidence 기반 finding audit
- 실행 전후 repository mutation 없음
- human review 시간과 추가 usage 측정 가능성

## 6.2 POC 위치

`run-cycle.ps1`에 연결하지 않은 `scripts/report/` 독립 entrypoint로 둔다.

개념 후보:

```text
scripts/report/build-agent-review-packet.ps1
scripts/report/invoke-codex-review.ps1
scripts/lib/codex/codex-review-adapter.ps1
scripts/lib/agent-runtime/agent-review-contract.ps1
scripts/smoke/smoke-codex-review-*.ps1
```

실제 파일 수와 이름은 Cross Review와 최종 계획에서 더 줄일 수 있다.

## 6.3 입력 생성

Review Packet builder는 다음을 명시적으로 입력받아야 한다.

- project root
- review base 또는 explicit diff
- 허용 changed-file 목록
- task goal
- acceptance criteria
- deterministic validation 결과 경로
- 관련 State/queue/target hash
- 허용 context pointer

전체 repository나 전체 Harness 문서를 무조건 packet에 넣지 않는다.

## 6.4 격리 방식

최초 live POC는 실제 repository를 직접 working root로 사용하기보다, 저민감 fixture 또는 sanitized review bundle을 우선한다.

Review bundle에는 다음만 포함한다.

- task packet
- explicit diff
- 검토 허용된 source snapshot
- relevant project instruction 사본 또는 pointer
- deterministic test result
- evidence manifest와 hashes

Codex sandbox의 실제 read 범위가 충분히 검증되지 않으면 production source를 직접 노출하는 단계로 넘어가지 않는다.

## 6.5 Codex invocation 후보

로컬 CLI help에서 확인한 다음 option을 조합 후보로 삼을 수 있다.

```text
codex exec
--sandbox read-only
--ephemeral
--ignore-user-config
--output-schema <schema>
--json
-C <isolated-review-root>
-
```

이 명령은 확정 contract가 아니다. option 조합, auth, project instructions, stdout/stderr, exit semantics를 fixture에서 동적으로 검증한 후 확정한다. `--dangerously-bypass-approvals-and-sandbox`는 사용하지 않는다.

## 6.6 Offline smoke

자동 smoke는 live Codex 호출 없이 fake executable/invoker로 다음을 검증한다.

1. executable 없음
2. unsupported version
3. 정상 structured result
4. empty output
5. invalid JSON
6. output schema 위반
7. non-zero exit
8. auth failure
9. usage/quota 제한
10. timeout
11. cancellation
12. stdout/stderr 대량 출력
13. UTF-8 한글 출력
14. 공백·한글·quote 경로
15. output byte limit
16. child process 잔존
17. 실행 전후 파일/hash/status 불변
18. scope 밖 finding reject
19. 존재하지 않는 line/path finding reject
20. raw secret-shaped 값 redaction

## 6.7 Manual live test

Offline smoke 통과 후 explicit opt-in으로 한 건만 실행한다.

- low-risk fixture 또는 공개 가능한 작은 diff
- repository write 없음
- git mutation 없음
- global config 변경 없음
- session persistence 없음 또는 실제 persistence 결과 확인
- structured result와 raw output의 차이 확인
- usage가 없으면 unknown으로 기록

## 6.8 POC acceptance criteria

- 기존 Claude-only `run-cycle` 결과가 byte/behavior 수준에서 영향받지 않는다.
- POC 미호출 시 추가 process/network/token 비용이 0이다.
- Codex unavailable이 workflow success로 오인되지 않는다.
- repository와 Git 상태가 실행 전후 동일하다.
- structured output이 deterministic validator를 통과한다.
- finding이 허용 evidence에 근거하지 않으면 채택되지 않는다.
- failure 원인과 provider/CLI version을 사후 설명할 수 있다.
- raw secret/session identifier가 일반 report에 노출되지 않는다.
- POC 제거만으로 완전히 rollback할 수 있다.

## 6.9 POC에서 제외할 것

- Codex source write
- Claude 자동 재작업
- 자동 debate 반복
- automatic commit
- run-cycle 자동 연결
- WORKFLOW_STATE writer 추가
- HRNS UI/provider selector
- Router
- Ollama 통합
- parallel agent/worktree
- MSI/runtime bundle 변경

---

# 7. 변경이 예상되는 파일과 계약

## 7.1 최초 POC에서 예상되는 Harness 변경

- Codex reviewer 전용 report entrypoint
- Codex adapter
- review packet/result/failure validator
- evidence manifest 또는 Secondary LLM evidence helper의 provider-neutral한 최소 재사용
- fake invoker 기반 offline smoke
- optional manual-live smoke
- `scripts/SMOKE_INDEX.md`
- 구현 사실에 맞춘 `README.md`, `HARNESS_KIT_MAP`, `ROADMAP`의 최소 갱신

Codex executable은 최초 POC에서 required Harness dependency로 올리지 않는다. 사용자가 lane을 명시적으로 호출할 때만 availability를 판정하는 optional host dependency로 다룬다.

## 7.2 후속 guarded lane에서 예상되는 변경

POC가 졸업한 뒤에만 다음을 검토한다.

- Claude 결과를 review packet으로 projection하는 adapter
- one-round review 상태 artifact
- deterministic resolution gate
- explicit human escalation result
- provider-neutral usage observation
- 공통 process supervisor 추출 여부

## 7.3 훨씬 뒤의 HRNS 변경 후보

Provider runtime contract가 안정된 후에만 다음을 검토한다.

- `HarnessWorkflowStateDto`의 optional `agent_runtime` raw extension
- `WorkflowStateMapper`의 optional sanitized projection
- active lane/provider/reviewer/fallback/usage read-only UI
- readiness와 무관한 diagnostic 표시

처음부터 provider 설정 editor나 provider-specific command를 HRNS core에 추가하지 않는다.

## 7.4 최종 계획 문서

Claude/Codex Cross Review와 Integrated Plan, Adversarial Final Review, 사용자/외부 확인을 마친 뒤에만 별도 정본 계획 문서를 작성한다.

권장 이름:

```text
doc/hrns_now_agent_runtime_evolution_plan.md
```

현재 `proposal`은 문제정의와 설계 가설이며, `plan`은 source 검증 후 승인된 구현 순서와 변경 범위여야 한다.

---

# 8. 변경해서는 안 되는 파일과 계약

최초 POC에서는 다음을 변경하지 않는다.

## HRNS-NOW

- `HarnessCommand` 종류와 의미
- `HarnessCommandMapper`
- `HarnessCommandEncoder`
- `PowerShellHarnessAdapter`
- `ActionPolicy` 및 `ClosurePolicy`
- 현재 State requiredness
- readiness/CTA 정책
- Registry schema
- UI navigation 및 실행 button 정책

## Harness runtime

- `run-cycle.ps1` 기본 실행 흐름
- planning/code/doc wrapper의 기본 Claude binding
- Claude session continuity semantics
- stop/failure taxonomy
- execution packet v2 write-authority 의미
- current 4-file daily surface
- current 3-file repository bridge
- queue.active pointer-only 규칙
- closure/ops fail-closed gate

## Default-off 기능

- context-diet active execution
- packet-first navi default
- parent-deterministic navi default
- automatic session resume
- deterministic backfill graduation

## Secondary LLM

- `authoritative=false`
- `safe_to_auto_adopt=false`
- `runtime_integrated=false`
- loopback-only
- hardware/acceleration gate
- CPU-only default disable

## 사용자 및 운영 환경

- `%USERPROFILE%\.codex\config.toml`
- 사용자/프로젝트 기존 `AGENTS.md`
- Claude global/project 설정
- Registry
- `D:\harness-workspaces`
- 기존 verified backup
- 자동 commit/push

---

# 9. Migration 단계

## AR-C0 — Current Baseline Freeze

목표:

- 현재 Native UI QA를 사용자 실제 클릭으로 완료한다.
- 현재 compatibility verdict와 non-blocking gap을 보존한다.
- Agent Runtime 작업 전 baseline commit/Kit version/문서 상태를 고정한다.

완료 조건:

- Native QA checklist 결과가 PASS/FAIL/BLOCKED로 기록됨
- Agent Runtime 문제와 기존 UI 문제를 구분할 수 있음

## AR-C1 — 최소 경제성 및 Human Bus 기준선

목표:

- 기존 usage ledger로 Claude call/token/cache/cost의 known/unknown을 집계한다.
- 수동 Claude→Codex 전달에 걸리는 human time과 누락 위험을 기록한다.
- small/medium/heavy에서 소수의 고정 task를 정한다.

완료 조건:

- “Harness가 비싸다”는 감상이 아니라 task 단위 비교 기준이 있음
- 측정되지 않은 값이 0으로 기록되지 않음

## AR-C2 — Reviewer 최소 계약 및 Capability Spike

목표:

- ReviewRequest/ReviewResult/Failure/Usage 최소 schema를 정의한다.
- Codex CLI version, option, output, sandbox, config interaction을 fixture에서 확인한다.

금지:

- live production write
- Router
- State/UI 변경

## AR-C3 — Standalone Codex Read-only Reviewer POC

목표:

- 독립 report entrypoint로 Codex review를 실행한다.
- 기존 run-cycle과 완전히 분리한다.
- offline fake smoke와 opt-in manual live evidence를 확보한다.

Rollback:

- 신규 report/lib/smoke 파일 제거만으로 원상복구

## AR-C4 — Guarded One-round Claude→Codex Review

전제:

- AR-C3의 read/write boundary, output parse, timeout, failure taxonomy가 검증됨

목표:

```text
Claude 기존 실행 결과
→ compact review packet
→ Codex critique 1회
→ Human decision 또는 명시적 Claude resolution
→ deterministic gate
```

기본적으로 자동 resolution과 추가 round는 금지한다.

## AR-C5 — Proven Common Runtime Extraction

목표:

- Claude와 Codex adapter에서 실제로 중복된 순수 process/telemetry 계약만 추출한다.
- Claude-specific session/stop semantics는 보존한다.

이 Phase 전에는 광범위한 generic rename/refactor를 하지 않는다.

## AR-C6 — Deterministic Router

목표:

- task risk/class, provider availability, budget observation을 기반으로 rule-based lane을 선택한다.
- 기본값은 기존 Claude-only lane으로 유지한다.
- critical provider failure를 낮은 품질 lane으로 자동 downgrade하지 않는다.

## AR-C7 — Codex Bounded Write

전제:

- reviewer lane이 충분히 안정됨
- explicit user approval

첫 대상:

- scratch fixture 또는 isolated worktree
- 좁은 allowed write set
- no auto commit

## AR-C8 — Optional Ollama Connection

목표:

- 기존 Secondary LLM advisory pipeline을 optional cheap lane으로 연결한다.
- 부적격 host에서는 core workflow 영향 없이 skip한다.

## AR-C9 — State/UI Projection

목표:

- 안정화된 runtime contract만 optional State extension과 read-only UI로 투영한다.
- provider 설정 UI는 별도 결정한다.

## AR-C10 — Packaging/MSI Finalization

목표:

- provider executable을 bundled/host dependency 중 무엇으로 할지 확정한다.
- version manifest, doctor, installer lifecycle을 검증한다.
- 기존 console-flash 및 runtime-bundle Gate와 함께 clean Windows에서 확인한다.

---

# 10. 열린 결정사항

아래 항목이 결정되기 전에는 production implementation을 시작하지 않는다.

## 10.1 Review scope

결정할 사항:

- commit, base branch diff, uncommitted diff, explicit manifest 중 어떤 입력을 정본으로 할 것인가?
- untracked, binary, submodule, LFS 파일을 어떻게 처리할 것인가?

Codex 권고:

- 최초 POC는 explicit changed-file manifest와 생성된 diff artifact를 사용한다.
- `--uncommitted` 전체 자동 수집은 사용하지 않는다.

## 10.2 Read isolation

결정할 사항:

- Windows `read-only` sandbox가 실제로 어디까지 읽을 수 있는가?
- production source를 직접 working root로 허용할 수 있는가?

Codex 권고:

- capability 확인 전에는 sanitized fixture/review bundle만 사용한다.

## 10.3 Project instructions

결정할 사항:

- 기존 root/nested `AGENTS.md`를 어떻게 존중할 것인가?
- Harness task restriction과 충돌할 때 누가 우선하는가?
- repository bridge를 늘리지 않고 instruction을 어떻게 전달할 것인가?

Codex 권고:

- repository 파일을 수정하지 않고 task packet 및 격리 bundle evidence로 전달한다.
- 충돌은 자동 해석하지 말고 blocked/human escalation으로 처리한다.

## 10.4 Codex CLI compatibility

결정할 사항:

- 최소/검증 version 범위
- option capability probe 방식
- unsupported version의 fail/skip 정책

현재 근거:

- 로컬 `codex-cli 0.153.2`에서 `exec`, `read-only`, `ephemeral`, `ignore-user-config`, `output-schema`, `json`이 확인됐다.

## 10.5 Structured result와 failure taxonomy

결정할 사항:

- final response와 JSONL 중 어느 것을 authoritative parse source로 할 것인가?
- auth, quota, network, timeout, cancellation, malformed output을 어떻게 구분할 것인가?

Codex 권고:

- exit code와 bounded raw evidence를 보존하고 normalized result를 별도로 생성한다.
- unknown failure를 success로 바꾸지 않는다.

## 10.6 Usage/cost

결정할 사항:

- Codex가 실제 token/cost를 제공하는 경우의 source validation
- 제공하지 않는 경우의 `unknown` 처리
- Claude ledger와 공통 envelope로 묶는 시점

Codex 권고:

- provider-reported/estimated/unknown을 명시적으로 구분한다.
- quota 잔량의 정밀 퍼센트를 만들지 않는다.

## 10.7 Raw artifact retention

결정할 사항:

- raw JSONL/stdout/stderr 보존 여부와 기간
- secret/path/session redaction
- 최대 byte 크기
- release package 제외 규칙

## 10.8 A/B graduation

결정할 사항:

- 표본 수
- task class 분류
- human review time 측정 방식
- finding precision/recall과 regression 발견률
- reviewer 추가 비용 허용 기준

Codex 권고:

- POC는 소수 고정 표본, production graduation은 확대 표본으로 분리한다.

## 10.9 Dual-provider 적용 조건

결정할 사항:

- 어떤 risk/task class에서 Codex reviewer를 호출할 것인가?
- 기본 1회 round를 넘길 수 있는 조건은 무엇인가?
- disagreement를 누가 해결하는가?

Codex 권고:

- high-risk, contract/security/transaction/compatibility 변경부터 opt-in한다.
- safety, acceptance, source evidence 충돌은 Human에게 올린다.

## 10.10 Provider executable과 packaging

결정할 사항:

- Claude/Codex CLI를 bundled runtime에 포함할지 host dependency로 둘지
- doctor/manifest에서 version compatibility를 어떻게 표시할지
- CLI update로 인한 contract drift를 어떻게 차단할지

Codex 권고:

- 초기에는 optional host dependency로 유지한다.
- packaging 결정은 runtime contract 안정화 후 진행한다.

## 10.11 Live Kit 변경 관리

결정할 사항:

- Git이 없는 `D:\harness-kit`에서 POC 변경을 어떻게 추적·복구할지
- verified backup과 release manifest를 언제 갱신할지

Codex 권고:

- 구현 전 clean snapshot, 변경 manifest, file hash, scratch 검증 절차를 task card에 명시한다.

## 10.12 기존 queue requiredness gap

결정할 사항:

- `queue.active.*`, `queue.blocked_reason`, `queue.last_updated_at`을 HRNS에서 guaranteed로 강제할지

Codex 권고:

- Agent Runtime과 분리된 별도 bounded hardening task로 처리한다.

---

# Compatibility, Native QA, MSI에 대한 영향

## 현재 compatibility

분리된 report POC는 다음을 지키면 현재 `COMPATIBLE_WITH_NONBLOCKING_GAPS` verdict를 변경하지 않는다.

- run-cycle 기본 경로 무변경
- State writer 무변경
- HRNS command/adapter 무변경
- POC default-off 및 explicit invocation
- Codex unavailable 시 core workflow 영향 없음

## Native UI QA

Native UI QA는 Agent Runtime POC 전에 현재 baseline으로 완료하는 것이 좋다. 그래야 이후 UI 문제와 runtime 변화 문제를 구분할 수 있다. 다만 read-only architecture review와 economics 자료 수집은 병행할 수 있다.

## MSI 및 bundled runtime

Clean MSI lifecycle과 bundled runtime 최종화는 provider dependency 정책이 안정된 뒤 진행하는 편이 재작업을 줄인다. 그러나 기존 MSI 회귀나 console-window-flash gap을 숨기거나 악화시켜서는 안 된다.

---

# Cross Review에 전달할 핵심 주장

Claude는 Round 2에서 특히 다음을 반박 또는 보완해야 한다.

1. execution packet v2 전체가 범용 Task Packet이 될 수 있는가, 아니면 thin review projection이 필요한가?
2. `claude-invoke-core.ps1`에서 지금 generic화할 수 있는 실제 최소 범위는 어디까지인가?
3. Secondary LLM evidence/audit helper 중 provider-neutral하게 추출할 정확한 함수와 파일은 무엇인가?
4. sanitized review bundle이 기존 project instruction과 source fidelity를 충분히 보존하는가?
5. Codex read-only sandbox의 file-read 범위를 어떻게 동적으로 증명할 것인가?
6. 첫 POC가 `run-cycle.ps1`과 완전히 분리돼도 Human Message Bus 감소 효과를 측정할 수 있는가?
7. Native UI QA와 경제성 baseline 중 무엇을 hard prerequisite로 둘 것인가?
8. live `D:\harness-kit`에서 최소 rollback 단위를 어떻게 확보할 것인가?

---

# 최종 권고

현재 목표는 거대한 multi-agent framework가 아니다.

가장 작은 가치 단위는 다음이다.

```text
현재 Claude-only workflow 유지
→ 명시적인 review evidence 생성
→ 독립 Codex read-only review
→ deterministic audit
→ Human-visible recommendation
```

이 흐름이 안전성, 품질, 비용, 사용자 개입 감소 측면에서 실제 가치를 증명한 뒤에만 Claude resolution, Router, Codex write, Ollama 연결, State/UI, packaging 순서로 확장한다.

다음 정식 단계는 Claude가 Codex의 이 결과를 보지 않은 상태에서 독립 Round 1을 완료한 뒤, 양쪽 결과만 사용해 Round 2 Cross Review를 수행하는 것이다. 그 시점까지 production code를 수정하지 않는다.

---

## 이번 검토의 mutation 및 검증 기록

- HRNS-NOW production source 변경: 없음
- `D:\harness-kit` 변경: 없음
- 사용자 global config 변경: 없음
- Git add/commit/push/reset/restore/stash: 없음
- build/Gradle 실행: 없음
- Harness smoke 실행: 없음
- live Claude/Ollama 호출: 없음
- live Codex provider 호출: 없음
- 수행한 Codex 확인: 로컬 CLI version/help 조회만 수행
- 새로 작성한 파일: `doc/revolution/revOpinion_codex01.md`
