# HRNS-NOW Agent Runtime Evolution Proposal

> **Claude 중심 Harness를 모델 독립적·비용 인지형 Agent Control Plane으로 진화시키기 위한 설계 제안서**
>
> 문서 상태: **PROPOSAL / NON-NORMATIVE**  
> 작성 기준: 2026-09-03  
> 권장 저장 위치: `doc/hrns_now_agent_runtime_evolution_proposal.md`

---

# 0. 문서 목적

이 문서는 현재 HRNS-NOW와 Harness Kit을 즉시 수정하기 위한 구현 지시서가 아니다.

목적은 다음 세 가지다.

1. 현재 HRNS-NOW의 안전한 Control Plane 구조와 Harness Kit의 검증 자산을 최대한 보존한다.
2. Claude 중심으로 고착된 Harness 실행 구조를 **Claude / Codex / 선택적 Ollama를 수용할 수 있는 provider-independent Agent Runtime 구조**로 발전시키는 방향을 제안한다.
3. 이 문서를 Claude와 Codex가 각각 독립 분석한 뒤 상호 비판·교차검증하여 **최종 구현 계획서**를 만들 수 있는 공통 논의 기준으로 사용한다.

이 문서가 최종 결정을 대신하지 않는다.

다음 단계는 반드시:

```text
이 제안서
→ Claude 독립 분석
→ Codex 독립 분석
→ 상호 비판
→ 통합 계획서
→ 사용자 검토
→ 외부 최종 확인
→ 구현
```

순서를 따른다.

---

# 1. 분석 기준과 Source of Truth

현재 공개 GitHub와 첨부 문서의 시점이 동일하지 않다.

첨부된 현행 HRNS-NOW 문서는 다음 로컬 기준선을 전제로 한다.

```text
repository : S:\dev\project\hrns_now
branch     : harness-dev
문서 기준  : 2026-09-02
live Kit   : D:\harness-kit
Kit version: 2026.09.02
```

반면 현재 공개 GitHub `SeoHyunik/hrns_now`의 `master`는 이보다 이전 시점의 commit history를 가진다.

따라서 Claude/Codex가 실제 계획을 만들 때 신뢰 순서는 다음과 같이 한다.

1. **현재 로컬 HRNS-NOW production source**
2. **현재 live Harness Kit production source**
3. 이 ZIP에서 제공된 2026-09-02 현행 문서
4. 현재 로컬 테스트 및 production-to-production 실행 결과
5. 공개 GitHub의 현재 source/history
6. 과거 Phase report와 과거 prompt
7. 이 제안서

이 제안서와 production source가 충돌하면 production source가 우선한다.

또한 Claude와 Codex는 구현 계획 수립 전에 현재 branch, HEAD, working tree, live Kit version을 다시 확인해야 한다.

---

# 2. 현재 HRNS-NOW에 대한 판단

## 2.1 현재 제품 경계는 유지할 가치가 높다

현재 HRNS-NOW의 가장 중요한 제품 정의는 다음이다.

```text
Harness Kit      = 실행 엔진
WORKFLOW_STATE   = machine-readable runtime truth
HRNS-NOW         = 안전한 Desktop Control Plane
```

이 책임 분리는 이번 고도화에서도 유지하는 것을 기본안으로 한다.

특히 다음 현재 설계는 그대로 보존할 가치가 높다.

- `WORKFLOW_STATE.json` 단일 runtime truth
- typed `UiAction`
- typed `HarnessCommand`
- `ActionPolicy`
- fail-closed
- runtime / workspace / repository boundary
- per-project/day process lock
- process 종료 후 State reread
- stdout 문자열이 아닌 State 기반 완료 판정
- atomic write / optimistic concurrency
- secret masking
- Recovery / Closure 분리
- Production-to-production contract test
- Hexagonal Architecture와 port/adapter 경계

이 영역을 multi-provider 도입을 이유로 재작성하지 않는다.

---

## 2.2 현재 가장 큰 구조적 한계

현재 Harness Kit은 실행·Planning·검증의 많은 부분에서 Claude 중심으로 진화했다.

이 구조는 현재 동작하지만 장기적으로 다음 위험이 있다.

### A. Provider lock-in

```text
Harness workflow
→ Claude-specific wrapper
→ Claude-specific configuration/context
→ Claude-specific telemetry
```

형태가 강해질수록 새로운 provider를 붙일 때 기존 core contract까지 수정해야 한다.

### B. 사람의 Message Bus 역할

사용자가 실무에서 사용하는 방식은 다음과 같다.

```text
Claude 분석/구현
      ↓
Human handoff
      ↓
Codex 검증/보완
      ↓
Human handoff
      ↓
Claude 후속 구현
```

이 방식의 품질상 장점은 크지만 사용자가 직접:

- 결과를 복사하고
- 상대 모델에게 전달하고
- 다음 prompt를 정리하고
- 역할을 기억하고
- 상태를 판단하고
- commit 타이밍을 관리한다.

HRNS-NOW의 핵심 제품가치는 이 Human Message Bus 역할을 제거하는 것이다.

### C. Token overhead

현재 Harness는 안전성과 재현성을 위해 많은 contract와 문서를 보유한다.

그러나 각각의 Agent가 큰 공통 context를 반복 소비하면:

```text
Agent A context
+ Agent B context
+ Agent C context
```

형태로 비용이 선형 또는 그 이상 증가할 수 있다.

Multi-agent 사용 자체가 목적이 되어서는 안 된다.

### D. Claude quota 집중

Claude 한 provider에 planning, implementation, review, documentation을 집중하면 동일 subscription quota에 부하가 몰린다.

정확한 잔여 quota를 기계적으로 신뢰성 있게 조회하기 어려운 환경에서는 provider diversification이 의미가 있다.

단, **quota 분산만을 위해 불필요한 Agent call을 추가해서는 안 된다.**

---

# 3. 이번 고도화의 최종 방향

HRNS-NOW를 다음처럼 정의하는 방향을 권장한다.

> **HRNS-NOW는 특정 AI 모델을 호출하는 앱이 아니라, Task의 위험도·상태·예산·검증 요구에 따라 Agent Runtime을 선택하고 실행 결과를 통제하는 Desktop Agent Control Plane이다.**

이를 위해 Harness Kit은 장기적으로 다음 역할로 좁혀진다.

> **Provider-independent Agent Runtime + deterministic policy/gate engine**

목표 구조:

```text
                         Human
                           │
                  Goal / Approval / Risk
                           │
                           ▼
                ┌─────────────────────┐
                │      HRNS-NOW       │
                │   Desktop Control   │
                │       Plane         │
                ├─────────────────────┤
                │ Task / State        │
                │ Risk / Budget       │
                │ Allowed Action      │
                │ Recovery / Review   │
                └──────────┬──────────┘
                           │ typed command
                           ▼
                ┌─────────────────────┐
                │    Harness Runtime  │
                │ Orchestration Engine│
                ├─────────────────────┤
                │ Task Packet         │
                │ Runtime Router      │
                │ Provider Adapter    │
                │ Validation Gate     │
                │ Usage Telemetry     │
                └───────┬────┬───────┘
                        │    │
          ┌─────────────┘    └─────────────┐
          ▼                                ▼
    Frontier Providers                 Local/cheap
   ┌───────────────┐                   provider
   │ Claude Code   │                 ┌──────────┐
   │ Codex         │                 │ Ollama   │
   │ Future Agent  │                 └──────────┘
   └───────┬───────┘
           │ native subagents / tools
           ▼
        Worktree / Repository
           │
           ▼
   Build / Test / Policy / Git Gates
           │
           ▼
     WORKFLOW_STATE update
```

---

# 4. 가장 중요한 설계 결정

## Decision 1 — HRNS-NOW가 Claude/Codex API를 직접 호출하지 않는다

초기 multi-provider 구현에서 HRNS-NOW Kotlin application이 Claude/Codex/Ollama를 직접 호출하는 구조는 권장하지 않는다.

현재 제품 불변식:

```text
HRNS-NOW = Control Plane
Harness   = Execution Engine
```

을 지킨다.

Provider 실행은 Harness Runtime 내부 adapter 책임으로 둔다.

이유:

- 현재 `HarnessCommand` / PowerShell adapter / State reread 구조를 재사용할 수 있다.
- Kotlin에 Claude/Codex/Ollama별 인증·세션·CLI 차이가 새어 들어오지 않는다.
- 기존 production-to-production test 경계를 유지할 수 있다.
- Provider 교체가 UI domain을 흔들지 않는다.
- 현재 Hexagonal Architecture와 자연스럽게 맞는다.

장기적으로 별도 Agent Runtime service를 추출할 수는 있지만, 처음부터 별도 daemon/service를 만들지 않는다.

---

## Decision 2 — Role과 Model을 분리한다

현재 사용 패턴의:

```text
Claude = Worker
Codex  = Reviewer
```

는 초기 default profile로는 사용할 수 있지만 core contract로 고정하지 않는다.

안정된 개념은 provider가 아니라 **role**이다.

예:

```text
Planner
Implementer
Reviewer
Auditor
Researcher
Documenter
```

Provider binding은 policy/profile에서 결정한다.

예:

```text
Planner      → Claude
Implementer  → Claude
Reviewer     → Codex
Auditor      → Codex
CheapSummary → Ollama
```

향후 모델 성능이 바뀌면:

```text
Planner      → Codex
Implementer  → Codex
Reviewer     → Claude
```

로 바꿀 수 있어야 한다.

Harness contract가 특정 provider의 현재 우열을 전제로 해서는 안 된다.

---

## Decision 3 — Provider별 설정 차이는 Adapter 안에 격리한다

Claude와 Codex는 동일한 configuration system을 사용하지 않는다.

Claude는 Claude Code의 자체 project/user configuration과 `CLAUDE.md` 계층을 사용하고, Codex는 `config.toml`, `AGENTS.md` 계층과 자체 sandbox/approval 설정을 사용한다.

이 차이를 공통 설정 파일 하나로 강제 통합하지 않는다.

금지 방향:

```text
CLAUDE.md → config.toml 자동 변환
Codex AGENTS.md → Claude config 자동 변환
모든 provider 설정을 하나의 거대 config로 재구현
```

권장 방향:

```text
Stable Task Contract
        │
        ├── ClaudeProviderAdapter
        │      └ native Claude configuration/context
        │
        ├── CodexProviderAdapter
        │      └ native Codex configuration/context
        │
        └── OllamaProviderAdapter
               └ local HTTP/model configuration
```

Provider-native configuration은 adapter 구현 세부사항이다.

---

## Decision 4 — `%USERPROFILE%`의 AI 설정을 자동 수정하지 않는다

특히 Codex integration을 위해 다음을 자동 수정하는 구현은 초기 단계에서 금지한다.

```text
%USERPROFILE%\.codex\config.toml
사용자 기존 AGENTS.md
사용자 기존 CLAUDE.md
```

이유:

- 사용자 전역 설정을 제품이 소유하면 안 된다.
- 기존 프로젝트 정책과 충돌할 수 있다.
- config schema가 provider 업데이트로 변할 수 있다.
- rollback과 provenance가 불명확해진다.

가능하면:

- 명시적 CLI option
- 환경변수
- ephemeral generated packet
- provider가 이미 읽는 project instruction

을 사용한다.

추가 bridge 파일이 정말 필요하다면 별도 Phase에서 contract를 확정한다.

기존 bridge의 정확한 3-file 계약을 무심코 깨뜨리지 않는다.

---

# 5. Stable Interop Contract: Task Packet

Multi-provider 설계의 중심은 prompt가 아니라 **Task Packet**이어야 한다.

현재 Harness의 execution packet/context-diet 자산을 최대한 재사용하고 새로운 parallel contract를 중복 생성하지 않는다.

최소 안정 표면 후보:

```json
{
  "task_id": "...",
  "goal": "...",
  "role": "reviewer",
  "risk": "guarded",
  "allowed_scope": [],
  "forbidden_scope": [],
  "allowed_write_files": [],
  "acceptance_criteria": [],
  "validation_plan": [],
  "required_evidence": [],
  "dependencies": [],
  "context_pointers": [],
  "budget_policy": {},
  "state_hash": "...",
  "target_hash": "..."
}
```

중요한 원칙:

### Task Packet에는 필요한 사실만 넣는다

각 Agent에게 Harness 설명서 전체를 전달하지 않는다.

### 문서 전체 대신 pointer를 전달한다

예:

```text
context_pointers:
- AGENTS.md
- src/.../TargetService.java
- test/.../TargetServiceTest.java
```

Agent가 실제로 필요할 때만 추가 문서를 읽는다.

### Same contract, provider-specific projection

Claude adapter와 Codex adapter는 동일 Task Packet을 각각 provider에 적합한 prompt/input으로 projection한다.

---

# 6. Progressive Context Disclosure

향후 Harness의 token 최적화 목표는 문서 삭제가 아니다.

> **LLM이 매번 읽어야 하는 문서를 줄이는 것**

이다.

현재 많은 문서는 사람과 유지보수자를 위한 정본으로 남아도 된다.

Runtime input은 다음처럼 만든다.

```text
Task Packet
    │
    ├─ goal
    ├─ scope
    ├─ acceptance
    ├─ risk
    ├─ hashes
    └─ context pointers
          │
          ▼
Provider Agent
    │
    └─ 필요한 경우에만 referenced context read
```

따라서 multi-provider 작업 전에 현재 Harness의:

- context diet
- packet-first execution
- deterministic Navi bypass
- task class token policy

가 실제 production path에서 어느 정도 사용 가능한지 다시 측정해야 한다.

새 provider를 붙이기 전에 기존 Claude path의 불필요한 context overhead를 먼저 측정하지 않으면 원인과 효과를 분리할 수 없다.

---

# 7. Deterministic First 원칙

비용 최적화의 1순위는 저렴한 LLM이 아니다.

> **LLM을 호출하지 않는 것**

이다.

Router의 기본 우선순위는 다음으로 한다.

```text
1. Deterministic logic로 해결 가능한가?
      YES → LLM 없음
      NO
       ↓
2. Local advisory로 충분한가?
      YES → Ollama 후보
      NO
       ↓
3. Frontier Agent 하나로 충분한가?
      YES → Claude 또는 Codex 하나
      NO
       ↓
4. 독립 검증이 필요한가?
      YES → Dual Provider
       ↓
5. Critical risk인가?
      YES → Human approval 포함
```

Build, test, lint, schema validation, hash comparison, file classification 등 deterministic하게 할 수 있는 일을 LLM에게 맡기지 않는다.

---

# 8. Risk-based Execution Lane

기존 `TASK_CLASS_TOKEN_POLICY`와 겹치는 새 분류 체계를 무턱대고 만들지 않는다.

Claude/Codex는 기존 task class와 이번 lane model을 비교하고 하나의 정본으로 통합하는 계획을 제시해야 한다.

개념적 lane은 다음과 같다.

## Lane D0 — Deterministic

```text
Request
→ deterministic transform/check
→ validation
→ State update
```

Agent call: 0

대상 후보:

- schema check
- hash check
- smoke inventory
- known metadata extraction
- deterministic docs scan

---

## Lane L1 — Local Advisory

```text
Task Packet
→ Ollama
→ candidate only
→ deterministic audit
```

Agent call: local only

대상 후보:

- diff summary
- validation summary
- docs mismatch candidate
- next-session prompt draft
- 단순 classification

이 lane은 현재 host capability가 허용될 때만 사용한다.

---

## Lane F1 — Single Frontier

```text
Task Packet
→ Claude OR Codex
→ deterministic validation
→ result
```

Agent call: 1

대상 후보:

- 일반 code implementation
- 작은 refactor
- 일반 planning
- 일반 review

이 lane이 **일반적인 기본값**이어야 한다.

---

## Lane G2 — Guarded Dual

```text
Primary Agent
→ Proposal / Change
→ Secondary Agent critique
→ Primary resolution
→ deterministic acceptance gate
```

Agent call: 2~3

대상 후보:

- transaction
- external integration
- migration
- broad refactor
- 중요한 architecture decision
- 복잡한 regression remediation

---

## Lane C3 — Critical

```text
Independent Planning
→ adversarial review
→ Human approval
→ bounded implementation
→ independent evaluation
→ deterministic gates
→ Human merge/release
```

대상 후보:

- credential/security
- destructive migration
- payment/billing critical path
- release/runtime contract
- repository-wide architecture migration

이 lane도 Agent 숫자를 늘리는 것이 목적이 아니다.

---

# 9. Claude ↔ Codex Debate Protocol

사용자가 현재 수행하는 “티키타카”의 장점은 유지하되 토큰 폭증을 막기 위해 **bounded debate**로 제한한다.

## 9.1 기본 round 수

기본값:

```text
Round 1: Proposal
Round 2: Adversarial Critique
Round 3: Resolution
END
```

무한 토론을 허용하지 않는다.

추가 round는 다음 경우에만 Human 또는 policy가 승인한다.

- unresolved blocker
- safety disagreement
- acceptance criteria 충돌
- source evidence 충돌

---

## 9.2 전체 transcript를 서로 전달하지 않는다

Agent 간 handoff는 compact structured artifact를 기본으로 한다.

### Proposal

```json
{
  "decision": "...",
  "assumptions": [],
  "changes": [],
  "risks": [],
  "acceptance": [],
  "open_questions": []
}
```

### Critique

```json
{
  "verdict": "accept|revise|block",
  "incorrect_assumptions": [],
  "missing_cases": [],
  "safety_risks": [],
  "test_gaps": [],
  "required_changes": []
}
```

### Resolution

```json
{
  "accepted": [],
  "rejected": [],
  "reasoning_summary": [],
  "final_change": [],
  "remaining_risks": []
}
```

Agent에게 상대방의 전체 chain/context를 복사하지 않는다.

---

# 10. Provider Runtime Contract

Harness 내부에는 장기적으로 provider별 wrapper보다 한 단계 위의 안정 계약이 필요하다.

개념 모델:

```text
AgentInvocationRequest
AgentInvocationResult
ProviderCapability
ProviderAvailability
UsageObservation
```

예시 결과:

```json
{
  "provider": "codex",
  "role": "reviewer",
  "status": "completed",
  "result_artifact": "...",
  "changed_files": [],
  "validation": [],
  "usage": {
    "input": null,
    "output": null,
    "cost": null,
    "source": "provider_reported|estimated|unknown"
  },
  "failure": null
}
```

Provider가 정확한 token/cost 정보를 제공하지 못하면 `null/unknown`을 허용한다.

없는 값을 만들어내지 않는다.

---

# 11. Codex Adapter에 대한 초기 원칙

Codex integration의 첫 목표는 Claude를 대체하는 것이 아니다.

> **독립 reviewer/auditor provider를 하나 추가하는 것**

부터 시작한다.

이유:

- 현재 사용자가 실제로 검증된 방식과 가장 가깝다.
- write authority가 없어 초기 위험이 낮다.
- 기존 Claude execution path를 거의 건드리지 않고 비교 가능하다.
- Codex provider contract를 먼저 검증할 수 있다.

## 초기 Codex 권한

권장 첫 단계:

```text
Role             : Reviewer / Auditor
Repository write : prohibited
Git mutation     : prohibited
Commit           : prohibited
Network          : provider policy에 따름
Output           : structured critique only
```

이 단계에서 다음을 검증한다.

- CLI launch/exit semantics
- cancellation/timeout
- stdout/stderr/structured output
- noninteractive execution 가능성
- sandbox boundary
- current project instructions와 충돌 여부
- failure classification
- quota/usage limit 발생 시 결과

검증 전에는 Codex write lane을 production workflow에 연결하지 않는다.

---

# 12. Codex의 TOML / AGENTS.md 문제에 대한 판단

Codex가 `config.toml`을 사용한다는 사실은 Harness 전체를 재작성해야 하는 이유가 아니다.

Codex의 provider-specific 설정은 adapter의 문제다.

특히 다음을 분리한다.

```text
Harness Task Contract
≠ Codex config.toml
≠ AGENTS.md
```

Task Contract는 무엇을 수행할지 정의한다.

Codex configuration은 Codex가 어떤 sandbox/approval/tool/instruction 환경에서 실행될지를 정의한다.

초기 POC에서 다음을 반드시 조사한다.

1. 기존 프로젝트 `AGENTS.md`가 있을 때 이를 그대로 존중할 방법
2. 별도 Harness instruction을 추가로 전달하는 최소 방법
3. 사용자 global `config.toml`을 수정하지 않는 방법
4. CLI argument 또는 ephemeral environment로 필요한 제한을 강제할 수 있는 범위
5. provider output을 안정적으로 parse할 방법

이 결과가 나오기 전에 HRNS repository bridge에 Codex 전용 파일을 추가하지 않는다.

---

# 13. Ollama의 위치

현재 Ollama Secondary LLM lane은 잘못된 시도가 아니다.

현재 구현에서 이미 지켜진 다음 원칙은 유지한다.

- default-off
- loopback-only
- advisory-only
- non-authoritative
- safe-to-auto-adopt = false
- runtime-integrated = false
- hardware capability gate
- CPU-only default disable
- explicit diagnostic override
- evidence/audit first

현재 host에서는 trusted GPU VRAM과 effective acceleration이 증명되지 않았으므로 live execution 기본 비활성화가 합리적이다.

따라서 이번 multi-provider phase에서 Ollama를 억지로 production executor로 승격하지 않는다.

Ollama의 역할은:

```text
Optional Cheap Advisory Provider
```

로 유지한다.

새 Runtime Router가 생기더라도:

```text
Provider unavailable
→ graceful skip/fallback
```

이어야 하며 Ollama 부재가 standard workflow를 막으면 안 된다.

---

# 14. Ollama에 적합한 업무

현재 Secondary LLM 자산과 향후 Router를 고려하면 다음이 적합하다.

### 적합

- diff summary
- test/validation summary
- documentation mismatch candidate
- 로그 분류
- simple artifact classification
- next-session prompt draft
- deterministic finding의 자연어 설명

### 신중

- simple code review candidate
- small documentation draft
- obvious low-risk code suggestion

### 기본 금지

- architecture final decision
- security decision
- release approval
- transaction final review
- 자동 commit 승인
- authoritative workflow transition
- final completion judgment

향후 더 강한 로컬 모델과 검증 가능한 hardware가 생기면 lane을 재평가한다.

---

# 15. Provider Budget Model

정확한 subscription 잔여 token API가 항상 제공된다고 가정하지 않는다.

Provider budget 상태는 soft state로 설계한다.

예:

```text
AVAILABLE
CONSERVE
LIMITED
EXHAUSTED
UNKNOWN
```

이 상태의 source는 다음 중 하나일 수 있다.

- provider reported usage
- CLI error/status
- Harness usage ledger
- rolling local estimate
- 사용자 수동 상태 지정

정확하지 않은 추정치를 정밀한 퍼센트처럼 표시하지 않는다.

금지:

```text
Claude remaining = 37.28%
```

근거가 없으면 다음처럼 표현한다.

```text
Claude budget state = CONSERVE
reason = recent usage threshold / user override
```

---

# 16. 비용 최적화의 핵심 지표

단순 total token만으로 성공을 판단하지 않는다.

최소 다음 지표를 같이 본다.

```text
Agent calls per accepted task
Input tokens
Output tokens
Cache-read tokens (가능할 경우)
Estimated/known cost
Wall time
Human intervention count
Human active minutes
Retry count
First-pass acceptance
Regression found after completion
```

가장 중요한 합성 지표 후보:

> **Tokens per Accepted Change**

그리고:

> **Human Minutes per Accepted Change**

이다.

Harness가 token을 조금 더 사용하더라도 Human Message Bus 시간을 크게 줄이면 제품가치가 있다.

반대로 Human time 절감 없이 token만 2~3배 증가한다면 해당 lane은 실패다.

---

# 17. A/B 측정을 구현보다 먼저 한다

Provider expansion 전에 현재 baseline 비용을 측정한다.

## 비교군

### A — Direct CLI

사용자가 평소 Claude 또는 Codex CLI를 직접 사용하는 방식.

### B — Current Harness

현재 Claude-centered standard workflow.

### C — Lean Harness

가능한 경우:

- packet-first
- context diet
- deterministic step
- 불필요한 Navi 제거
- one-agent path

를 적용한 후보.

## Task sample

최소 다음으로 나눈다.

### Small

- 단일 파일 bug
- validation 추가
- 작은 docs 수정

### Medium

- controller/service 변경
- test 포함 기능
- API contract 변경

### Heavy

- multi-file integration
- transaction
- architecture-impacting refactor

가능하면 10~20개의 실제 bounded task를 사용한다.

---

# 18. Multi-provider 도입 전 Graduation 기준

정확한 숫자는 실제 A/B 결과를 보고 확정한다.

초기 heuristic은 다음 정도로 사용할 수 있다.

### Small task

Harness가 Direct 대비 2배 이상의 token을 지속적으로 사용하면서 Human time/quality 개선이 없다면 heavy orchestration을 사용하지 않는다.

### Standard task

추가 provider review 비용은 다음 중 하나를 증명해야 한다.

- Human review time 감소
- first-pass acceptance 향상
- regression 발견률 향상
- 재작업 감소

### Guarded/Critical

비용보다 defect avoidance와 auditability를 우선할 수 있다.

단순 평균 token 절약을 이유로 critical review를 제거하지 않는다.

---

# 19. WORKFLOW_STATE 계약 확장 원칙

현재 HRNS parser는 Harness의 진화 중인 세부 필드를 raw diagnostic extension으로 보존하는 선례가 있다.

Multi-provider 정보도 처음부터 readiness-required typed contract로 올리지 않는다.

초기에는 optional diagnostic extension으로 실험하는 것이 안전하다.

개념 후보:

```json
{
  "agent_runtime": {
    "contract_version": "0.1",
    "lane": "guarded_dual",
    "primary_provider": "claude",
    "secondary_provider": "codex",
    "role_bindings": {
      "implementer": "claude",
      "reviewer": "codex"
    },
    "budget_state": {
      "claude": "conserve",
      "codex": "available",
      "ollama": "unavailable"
    },
    "debate": {
      "round": 1,
      "max_rounds": 1,
      "status": "review_pending"
    }
  }
}
```

이 shape는 **예시일 뿐 확정 schema가 아니다.**

Claude와 Codex는 다음 두 대안을 비교해야 한다.

### Option A

기존 `usage_guard` / role-sliced extension을 확장한다.

### Option B

새 `agent_runtime` optional extension을 추가한다.

판단 기준:

- semantic cohesion
- backward compatibility
- parser churn
- Harness writer complexity
- UI 필요성
- future provider extensibility

초기에는 HRNS CTA가 이 extension에 의존하지 않는다.

계약이 안정화된 뒤에만 typed model로 승격한다.

---

# 20. HRNS-NOW UI에 처음부터 많은 기능을 넣지 않는다

Multi-provider runtime이 안정되기 전 UI에서 provider 선택 panel부터 만드는 것은 금지한다.

구현 순서:

```text
Runtime contract
→ provider adapter
→ automated test
→ actual task A/B
→ State contract stabilization
→ HRNS projection
→ UI
```

UI는 마지막 단계다.

초기 UI에서 필요한 최소 정보 후보:

- 선택된 execution lane
- 실제 실행 provider
- reviewer provider
- provider availability
- budget state
- execution cost/usage known/unknown
- fallback 여부

사용자에게 provider 내부 configuration 세부사항을 노출하지 않는다.

---

# 21. HRNS-NOW ActionPolicy와 Provider Routing을 섞지 않는다

현재 `ActionPolicy`는:

> 지금 이 workflow에서 어떤 행동이 안전하게 허용되는가?

를 결정한다.

Provider Router는:

> 허용된 행동을 어떤 runtime/provider 조합으로 수행할 것인가?

를 결정한다.

두 책임을 처음부터 하나의 policy class에 합치지 않는다.

개념:

```text
ActionPolicy
    ↓
Allowed Action
    ↓
Execution Routing Policy
    ↓
Provider Plan
```

다만 실제 Routing Policy의 위치가 HRNS core인지 Harness Runtime인지는 구현 전에 별도 결정한다.

현재 추천은:

> **초기 Router는 Harness Runtime 소유**

이다.

이유:

- 실제 provider invocation과 usage telemetry가 Harness에 있다.
- HRNS는 현재 외부 runtime의 실행 의미를 복제하지 않는 것이 불변식이다.
- Provider 변경 시 Kotlin release가 필요하지 않게 만들 수 있다.

향후 안정된 provider plan만 State를 통해 HRNS에 노출한다.

---

# 22. Native Subagent를 Harness가 재구현하지 않는다

Claude Code와 Codex는 각각 자체 Agent/subagent/worktree/tool 기능이 계속 변화한다.

Harness가 provider 내부 Agent framework를 다시 구현하면 maintenance burden이 폭증한다.

따라서 책임을 다음과 같이 나눈다.

### Harness가 결정

- Task
- Role
- Scope
- Risk
- Budget
- Acceptance
- Provider
- Provider에게 허용할 큰 경계

### Provider runtime이 결정 가능

- native subagent 사용 여부
- 내부 탐색 전략
- 자체 compaction
- 자체 tool scheduling

단 provider가 Harness가 정한 allowed scope와 acceptance를 우회해서는 안 된다.

---

# 23. Parallel Execution은 후순위다

“그림자 분신술”의 최종 목표가 parallel execution일 수 있지만 첫 multi-provider Phase에서 병렬 쓰기를 추가하지 않는다.

먼저:

```text
Provider abstraction
→ single provider path
→ reviewer path
→ debate path
```

를 안정화한다.

그 뒤 독립성이 증명된 task만 worktree 기반 병렬 실행을 검토한다.

초기 금지:

```text
Claude → same files
Codex  → same files
동시 write
```

병렬화 후보:

```text
Agent A → implementation
Agent B → independent research/read-only analysis
Agent C → tests on separate worktree
```

실제 merge owner와 conflict policy를 먼저 정의해야 한다.

---

# 24. Git Authority

현재 협업 방식에서 Codex가 Git Owner 역할을 맡아온 것은 안정적인 초기 default가 될 수 있다.

그러나 최종 core contract는 provider 이름이 아니라 authority로 표현해야 한다.

예:

```text
GitAuthority:
  none
  stage
  commit
  integrate
```

초기 권장값:

```text
Claude Implementer : none
Codex Reviewer     : none
Integration Owner  : Harness-controlled / explicit user-approved lane
```

현재 사용자가 만족하는 Codex commit workflow를 자동화하려면 별도 단계에서 commit authority를 설계한다.

Multi-provider POC와 자동 commit을 같은 Phase에서 구현하지 않는다.

---

# 25. Failure / Fallback Policy

Provider가 실패했을 때 자동으로 다른 provider에게 동일 작업을 넘기는 것은 편리하지만 비용과 안전성 위험이 있다.

초기 fallback 정책은 보수적으로 한다.

## Safe fallback

```text
optional Ollama advisory unavailable
→ skip
```

## Conditional fallback

```text
Codex reviewer quota unavailable
→ Claude self-review가 아니라
→ deterministic validation + human review 요청
```

또는 user-approved fallback profile을 사용한다.

## 금지

Critical task에서 provider failure를 조용히 숨기고 낮은 품질 lane으로 자동 downgrade하지 않는다.

State에 다음을 남길 수 있어야 한다.

```text
requested lane
actual lane
fallback occurred
fallback reason
human approval required
```

---

# 26. Security Boundary

Multi-provider는 새로운 credential surface를 만든다.

따라서 다음 원칙을 유지한다.

- Claude credential을 HRNS Registry에 저장하지 않는다.
- Codex credential을 HRNS Registry에 저장하지 않는다.
- Ollama는 현재 loopback-only 유지.
- raw auth output 저장 금지.
- raw model response의 영구 저장 최소화.
- provider-specific secret은 provider-native credential store가 소유.
- Harness usage telemetry에는 secret-shaped 값 제거.
- generated prompt artifact가 source code/secret을 과도하게 복제하지 않는지 검사.

가능하면 Provider Adapter는 인증 자체보다 이미 인증된 CLI/runtime을 호출한다.

---

# 27. 기존 현재 Roadmap과의 관계

현재 현행 계획의 주요 열린 Gate는:

```text
P1 Native UI QA
P2 Clean Windows MSI lifecycle
P3 Approved bundled runtime
```

이다.

이번 Agent Runtime evolution을 이 Gate들과 무작정 섞지 않는다.

권장 순서:

## 먼저 수행

### Current Native UI QA

현재 Control Plane UX를 실제 사용자 흐름으로 검증한다.

이 결과는 multi-provider 이후에도 유효한 UX baseline이 된다.

## 잠시 보류 가능

### Clean Windows MSI final lifecycle
### Bundled Runtime finalization

Provider architecture가 크게 바뀔 가능성이 있는 동안 Claude-only runtime을 최종 bundle contract로 고정하면 재작업이 발생할 수 있다.

따라서:

```text
Native UI QA baseline
→ Multi-provider architecture decision/POC
→ runtime contract stabilization
→ clean MSI / bundled runtime finalization
```

순서를 Claude/Codex가 검토해야 한다.

단 현재 MSI regression 자체가 깨지도록 방치하지 않는다.

---

# 28. 권장 Migration Phases

이 Phase 번호는 현행 HRNS Phase와 충돌하지 않도록 최종 계획에서 별도 namespace로 다시 정한다.

여기서는 임시 `AR-*`를 사용한다.

---

## AR-0 — Baseline Economics

### 목표

현재 Claude-only Harness의 실제 비용/사용성 baseline을 측정한다.

### 변경

가능하면 production behavior 변경 없음.

### 산출

- Direct vs Current Harness vs Lean Harness
- task class별 usage
- Human intervention
- retry
- acceptance

### 완료 조건

“Harness가 비싸다”를 체감이 아니라 task class별 데이터로 설명할 수 있다.

---

## AR-1 — Provider-neutral Contract Design

### 목표

Provider-independent request/result/capability contract만 정의한다.

### 범위

- Task Packet 재사용 여부
- provider role binding
- normalized result
- failure taxonomy
- budget state

### 금지

- live Codex write
- HRNS UI 변경
- global config 변경

---

## AR-2 — Codex Read-only Reviewer POC

### 목표

Codex를 production task의 독립 reviewer로 안전하게 호출할 수 있음을 증명한다.

### 권한

```text
read-only
no git mutation
no repository write
```

### 검증

- current AGENTS/config interaction
- process cancel/timeout
- noninteractive execution
- normalized result
- quota failure
- output sanitization

---

## AR-3 — Guarded Claude→Codex Review Lane

### 목표

실제 현재 사용자 workflow를 Harness 안에서 재현한다.

```text
Claude implement
→ Codex review
→ Claude resolution OR human decision
→ deterministic gate
```

### 중요

Debate 최대 round 제한.

전체 transcript 전달 금지.

---

## AR-4 — Provider Router

### 목표

Task risk / complexity / provider availability / budget state에 따라 lane을 선택한다.

### 초기 Router

rule-based deterministic router.

LLM에게 Router 결정을 맡기지 않는다.

---

## AR-5 — Codex Bounded Write Lane

### 전제

AR-2/3이 충분히 안정적이어야 한다.

### 목표

Codex를 Implementer로 선택할 수 있도록 한다.

### 첫 실행

scratch fixture 또는 isolated worktree 우선.

Production repository는 explicit opt-in.

---

## AR-6 — Ollama Router Integration

### 전제

host capability가 실제로 eligible해야 한다.

### 목표

현재 Secondary LLM lane을 Router의 optional cheap advisory provider로 연결한다.

현재 PC가 부적격이면 이 Phase는 계속 disabled 상태여도 전체 제품 완성에 영향을 주지 않는다.

---

## AR-7 — State/UI Projection

### 목표

stabilized provider runtime contract만 HRNS-NOW에 typed projection한다.

UI 후보:

- lane
- active provider
- reviewer
- budget state
- fallback
- usage

Provider configuration editor는 별도 판단한다.

---

## AR-8 — Runtime Packaging / Release

Multi-provider runtime의:

- dependencies
- manifest
- checksum
- provider capability
- compatibility

가 정리된 뒤 bundled runtime과 clean Windows lifecycle을 최종화한다.

---

# 29. 구현 전에 Claude와 Codex가 반드시 결정해야 하는 질문

다음 질문이 열린 상태에서는 implementation을 시작하지 않는다.

## Architecture

1. Provider Router의 소유자는 Harness인가 HRNS core인가?
2. 기존 Task Packet을 provider-independent contract로 승격할 수 있는가?
3. 새 contract를 만들 필요가 있다면 중복을 어떻게 피할 것인가?
4. Provider result의 최소 안정 schema는 무엇인가?
5. `WORKFLOW_STATE`에 어떤 provider 정보까지 들어가야 하는가?

## Codex

6. 기존 AGENTS.md를 침범하지 않고 project-specific Harness instruction을 어떻게 전달할 것인가?
7. global `config.toml`을 수정하지 않고 필요한 sandbox/approval 경계를 어떻게 강제할 것인가?
8. noninteractive output 계약을 어떻게 안정적으로 parse할 것인가?
9. Codex quota/usage limit을 어떤 failure taxonomy로 정규화할 것인가?

## Claude

10. 현재 Claude wrapper 중 provider-neutral 영역과 Claude-specific 영역을 어떻게 분리할 것인가?
11. 현재 telemetry/session continuity 중 어떤 기능이 provider-neutral이어야 하는가?
12. Claude native subagent와 Harness role-sliced가 중복되는 영역은 무엇인가?

## Ollama

13. 기존 Secondary LLM contract를 범용 Provider interface에 연결하면서 non-authoritative 경계를 어떻게 보존할 것인가?
14. hardware ineligible host에서 불필요한 probe/call을 어떻게 막을 것인가?

## Debate

15. 어떤 task class에서 dual-provider를 사용해야 하는가?
16. 최대 round는 몇 개인가?
17. disagreement를 누가 resolve하는가?
18. 어떤 disagreement는 무조건 Human에게 올라가야 하는가?

## Cost

19. 정확한 provider quota가 없을 때 BudgetState는 어떤 데이터로 계산할 것인가?
20. A/B measurement의 graduation threshold는 무엇인가?

## Release

21. provider executable 자체를 bundled runtime에 포함하는가, host dependency로 보는가?
22. Codex/Claude CLI version compatibility를 어떻게 진단할 것인가?
23. provider CLI update가 Harness compatibility에 미치는 영향을 어떻게 차단할 것인가?

---

# 30. 이 프로젝트에서 하지 말아야 할 것

이번 진화 과정에서 다음 Anti-pattern을 피한다.

## 30.1 AI 3개를 붙이는 것이 목표가 되는 것

```text
Claude + Codex + Ollama = 무조건 우수
```

가 아니다.

작업에 필요한 최소 Agent 수가 정답이다.

---

## 30.2 Provider 설정 통합을 위해 거대 configuration framework 생성

Provider native configuration을 가능한 그대로 이용한다.

---

## 30.3 Claude-specific Harness를 한 번에 전부 generic rename

먼저 실제 seam을 찾고 contract를 검증한다.

광범위 rename/restructure는 마지막이다.

---

## 30.4 Multi-provider와 Parallel Agent를 동시에 구현

원인분리가 불가능해진다.

---

## 30.5 Provider 간 full transcript forwarding

Token overhead와 prompt contamination이 증가한다.

---

## 30.6 정확하지 않은 quota 수치를 product truth로 사용

Unknown은 Unknown으로 유지한다.

---

## 30.7 Ollama GPU 부재를 이유로 CPU production execution 강제

현재 O-13 fail-safe를 유지한다.

---

## 30.8 HRNS에서 Harness execution semantics 복제

현재 Anti-Corruption Layer를 무너뜨리지 않는다.

---

# 31. Claude–Codex 계획 수립 프로토콜

이 문서는 아래 방식으로 사용하는 것을 권장한다.

## Round 0 — 동일 baseline 읽기

Claude와 Codex 모두 다음을 읽는다.

### HRNS-NOW

- `README.md`
- `doc/documentation_guide.md`
- `doc/hrns_now_claude_plan.md`
- `doc/hrns_now_design_pattern.md`
- `doc/native_qa_checklist.md`
- `doc/phase_reports/current_validation_reports.md`
- `doc/phase_reports/harness-kit-live-compatibility-audit-report.md`
- 이 문서

### Harness Kit

실제 live source에서:

- current README / map / roadmap
- task class token policy
- context diet 관련 문서/코드
- current task/execution packet
- Claude invocation core
- Secondary LLM lane
- usage ledger/telemetry
- `run-cycle.ps1`

을 필요한 호출관계만큼 읽는다.

과거 Phase 문서를 무작정 전부 읽지 않는다.

---

## Round 1 — Independent Architecture Review

Claude와 Codex는 서로의 결과를 보지 않고 아래 형식으로 제출한다.

```text
VERDICT

1. Current Architecture Understanding
2. Proposal Agreements
3. Proposal Disagreements
4. Hidden Risks
5. Provider Abstraction Boundary
6. Minimal First POC
7. Files/Contracts Expected To Change
8. Files/Contracts That Must Not Change
9. Migration Phases
10. Open Decisions
```

이 단계에서는 파일을 수정하지 않는다.

---

## Round 2 — Cross Review

각 Agent에게 상대 결과를 제공한다.

요청:

```text
- 상대 계획에서 사실과 다른 부분
- 불필요한 abstraction
- 현재 source와 충돌하는 부분
- backward compatibility 위험
- token/cost 문제를 악화시키는 부분
- missing test/gate
- 더 작은 vertical slice 대안
```

만 제출한다.

전체 계획을 처음부터 다시 작성하지 않는다.

---

## Round 3 — Integrated Plan

Codex 또는 지정된 Integration Owner가:

- 두 분석의 합의점
- unresolved disagreement
- source 검증 결과

를 사용해 하나의 계획서를 작성한다.

계획서는 최소 다음을 포함한다.

```text
1. Target Architecture
2. Non-goals
3. Contract Changes
4. Harness Changes
5. HRNS Changes
6. Codex Adapter Design
7. Claude Adapter Refactor
8. Ollama Reuse Plan
9. Routing Policy
10. State Contract
11. Security
12. Telemetry / Budget
13. Migration Phases
14. Tests
15. Rollback
16. A/B Measurement
17. Release Impact
18. Open Owner Decisions
```

---

## Round 4 — Adversarial Final Review

Claude가 통합 계획을 독립 검토한다.

특히:

- 현재 불변식 위반
- excessive abstraction
- missing failure path
- migration blast radius
- token overhead
- unsafe fallback
- user global config mutation
- test blind spot

을 찾는다.

Codex는 finding별로:

```text
ACCEPT
REJECT + reason
DEFER + reason
```

를 작성해 최종 계획을 갱신한다.

---

## Round 5 — Human / External Confirmation

이 시점까지 **production code 수정 없음**이 원칙이다.

최종 계획서를 사용자에게 제출하고 외부 확인을 거친 뒤에만 구현 Phase를 시작한다.

---

# 32. 최종 계획서의 품질 Gate

최종 계획은 다음 질문에 모두 답해야 한다.

### 제품

- HRNS-NOW가 왜 필요한지 더 명확해졌는가?
- 사용자의 Message Bus 역할이 실제로 줄어드는가?

### 아키텍처

- Claude/Codex/Ollama 차이가 core domain에 새지 않는가?
- 기존 Harness/HRNS 책임 경계를 지키는가?
- provider 교체가 가능한가?

### 비용

- 단순 업무가 더 비싸지는 것을 방지하는가?
- multi-agent 사용 조건이 명확한가?
- A/B measurement가 있는가?

### 안전

- unknown/failure에서 fail-closed가 유지되는가?
- provider unavailable이 잘못된 success로 변하지 않는가?
- critical task가 자동 downgrade되지 않는가?

### 호환성

- 기존 Claude-only standard workflow를 rollback path로 유지할 수 있는가?
- 현재 State/parser/CTA를 한 번에 깨뜨리지 않는가?

### Ollama

- GPU 부적격 host에서도 core workflow가 정상 동작하는가?
- local result가 authoritative가 되지 않는가?

### Codex

- 사용자 global config를 제품이 소유하지 않는가?
- 기존 AGENTS.md를 침범하지 않는가?
- provider-specific TOML을 core contract로 만들지 않는가?

### 운영

- 실행 provider와 fallback을 사후 설명할 수 있는가?
- usage/cost가 known/estimated/unknown으로 구분되는가?

---

# 33. 추천하는 첫 실제 Vertical Slice

현재 전체 아이디어 중 가장 먼저 구현할 가치가 높은 Slice는 다음이다.

> **Codex Read-only Reviewer Adapter POC**

이 Slice를 추천하는 이유:

1. 사용자가 현재 수동으로 수행하는 검증 workflow와 가장 가깝다.
2. Claude execution lane을 거의 변경하지 않는다.
3. Codex configuration 문제를 작은 범위에서 실제 측정할 수 있다.
4. repository write가 없어 실패 blast radius가 작다.
5. multi-provider normalized result contract를 먼저 검증할 수 있다.
6. “티키타카”의 품질 개선 효과와 token overhead를 측정할 수 있다.

### POC 개념 흐름

```text
Claude existing execution
      ↓
current diff / task result
      ↓
compact Review Packet
      ↓
Codex read-only adapter
      ↓
structured critique
      ↓
deterministic audit
      ↓
Human-visible recommendation
```

이 첫 POC에서는 Claude에게 자동 재작업까지 명령하지 않는다.

먼저 reviewer 결과의 품질·비용·안정성을 측정한다.

그 결과가 충분히 좋을 때만:

```text
Codex critique
→ Claude resolution
```

을 연결한다.

---

# 34. 추천 우선순위

현재 전체 프로젝트 관점의 우선순위 제안:

```text
1. Current Native UI QA baseline 완료
2. Claude-only 현재 workflow 비용 baseline 측정
3. Provider-neutral contract 설계
4. Codex read-only Reviewer POC
5. Guarded Claude↔Codex one-round workflow
6. Risk/Cost Router
7. Codex bounded implementer
8. Ollama optional router 연결
9. HRNS provider/budget projection
10. Clean MSI / Bundled multi-provider runtime finalization
11. Parallel worktree / advanced orchestration
```

Native QA와 경제성 baseline은 일부 병행할 수 있다.

다만 큰 provider refactor와 MSI finalization을 동시에 수행하지 않는다.

---

# 35. 최종 제안

HRNS-NOW의 방향을 폐기하거나 전면 재설계할 필요는 없다.

현재 제품의 가장 좋은 부분은 이미 미래 방향과 맞는다.

```text
State-driven
Typed
Fail-closed
Control Plane
External Runtime
Recovery-aware
```

문제는 HRNS-NOW의 구조가 아니라 **그 아래 Harness Runtime이 Claude 중심으로 과도하게 결합되어 있고, 안전성을 위한 context가 token 비용으로 전환되는 부분**이다.

따라서 다음 진화가 가장 합리적이다.

```text
Claude-specific Harness
        ↓
Provider-independent Task / Result Contract
        ↓
Claude + Codex adapters
        ↓
Risk/Cost based Router
        ↓
Optional Ollama advisory
        ↓
HRNS-NOW = Agent Control Plane
```

그러나 이를 한 번에 구현하지 않는다.

첫 목표는 새로운 거대한 multi-agent framework가 아니다.

> **현재 사용자가 직접 수행하는 Claude → Codex 독립 검증 handoff 하나를 안전하고 측정 가능하게 자동화하는 것**

이다.

이 vertical slice가 실제로:

- Human Message Bus 시간을 줄이고
- accepted change 품질을 유지 또는 향상하며
- token overhead가 합리적인 범위에 있고
- 현재 fail-closed contract를 깨뜨리지 않는다는 것

을 증명한 뒤 다음 단계로 확장한다.

---

# 36. 이 문서를 읽은 Claude/Codex에게 요구하는 마지막 지시

이 문서를 구현 명세로 취급하지 마라.

먼저 현재 local source와 live Harness를 읽고 다음을 검증하라.

1. 이 문서가 잘못 이해한 현재 구조가 있는가?
2. 이미 구현되어 있는데 새로 만들자고 제안한 기능이 있는가?
3. 현재 contract를 재사용하면 더 작은 수정으로 가능한 부분이 있는가?
4. provider abstraction을 넣기에 가장 자연스러운 실제 seam은 어디인가?
5. Claude-specific code 중 정말 generic화해야 하는 최소 범위는 어디까지인가?
6. Codex Reviewer POC를 current standard workflow를 거의 건드리지 않고 추가할 수 있는가?
7. 기존 Secondary LLM 자산을 새 Provider abstraction이 재사용할 수 있는가?
8. 이번 변경이 현재 compatibility/native QA/MSI Gate에 어떤 영향을 주는가?
9. token cost를 줄이기 위해 provider 추가보다 먼저 고쳐야 할 current path가 있는가?
10. 구현한다면 가장 작은 reversible vertical slice는 무엇인가?

답변은 새 기능의 양이 아니라 **변경량 대비 제품가치와 rollback 가능성**을 기준으로 작성한다.

---

# Appendix A — Preserve / Change / Defer

| 영역 | 판단 |
|---|---|
| `WORKFLOW_STATE` runtime truth | Preserve |
| `ActionPolicy` | Preserve |
| typed `HarnessCommand` | Preserve |
| process lock / State reread | Preserve |
| fail-closed | Preserve |
| Recovery / Closure | Preserve |
| Hexagonal Architecture | Preserve |
| production-to-production test | Preserve |
| Claude-only provider coupling | Change incrementally |
| full static context pre-read | Reduce |
| packet-first | Promote after evidence |
| deterministic bypass | Promote after evidence |
| provider routing | Add |
| Codex reviewer | Add first |
| Codex implementation | Defer until reviewer stable |
| Ollama authoritative execution | Do not add now |
| Ollama advisory | Preserve / later route |
| parallel same-file agents | Defer / avoid |
| worktree parallelism | Defer |
| global AI config mutation | Prohibit |
| clean MSI final runtime bundle | Defer until runtime direction stable |

---

# Appendix B — Minimum Provider Capability Matrix

초기 계획에서 다음 표를 실제 source/CLI 실험 결과로 채운다.

| Capability | Claude | Codex | Ollama |
|---|---|---|---|
| availability probe |  |  | implemented |
| read-only review |  |  | advisory |
| bounded write | existing | POC later | no |
| structured result |  |  | implemented candidate/audit |
| timeout/cancel |  |  |  |
| sandbox/write boundary |  |  | local advisory |
| usage telemetry |  |  |  |
| exact remaining quota | unknown | unknown | local/no quota |
| native subagents |  |  | no |
| project instructions | Claude native | Codex native | packet prompt |
| global config mutation required | should be no | should be no | no |
| safe fallback |  |  | skip |

빈칸을 추측으로 채우지 않는다.

---

# Appendix C — Final Plan Deliverable Name

Claude/Codex 티키타카 이후 합의된 최종 구현 계획은 별도 문서로 작성한다.

권장 이름:

```text
doc/hrns_now_agent_runtime_evolution_plan.md
```

이 제안서와 최종 계획서를 구분한다.

- `proposal` = 문제정의와 설계 가설
- `plan` = source 검증 후 확정된 구현 순서와 변경 범위

최종 plan이 승인되면 필요한 내용만 현행 정본 문서:

```text
doc/hrns_now_claude_plan.md
doc/hrns_now_design_pattern.md
doc/documentation_guide.md
```

에 반영한다.

정본 문서를 이 proposal의 내용으로 선제 변경하지 않는다.
