# HRNS-NOW Agent Runtime Evolution — Claude 독립 분석 (Round 1)

- 문서 성격: `doc/claude_prompts/hrns_now_agent_runtime_evolution_proposal.md` §31 협업 프로토콜의 **Round 1 — Independent Architecture Review (Claude 제출본)**
- 작성 주체: Claude (독립 분석·검증 및 Harness 계약 전문 파트너)
- 작성 시각 기준일: 2026-09-04
- 상태: 분석 단계. 이 문서 저장을 제외하면 파일/Git/네트워크 mutation 0. 테스트·Harness entrypoint·smoke·live Claude/Codex/Ollama 호출 미실행
- 다음 단계: Codex의 독립 Round 1 → Round 2 상호 비판 → Round 3 통합 계획서 → Round 4 Claude adversarial 검토 → Round 5 사용자/외부 확인 → 구현

> 이 문서는 구현 명세가 아니다. 제안서를 가설로 취급하고 현재 local source·live Harness Kit·Git 상태로 교차 확인한 결과이며, 변경량 대비 제품가치와 rollback 가능성을 기준으로 작성했다.

---

## 기준선 (지금 검증함)

| 항목 | 값 |
|---|---|
| HRNS-NOW branch / HEAD | `harness-dev` / `8757527` "docs: define Codex Claude collaboration workflow" (2026-09-02). working tree는 untracked `doc/claude_prompts/hrns_now_agent_runtime_evolution_proposal.md` 외에는 clean, `origin/harness-dev`와 동기화됨 |
| Live Kit | `D:\harness-kit`, `kit_version=2026.09.02`, `state_schema_version=1.0`, `ui_contract_version=1.0`, `.git` 없음 |
| 현재 verdict | `COMPATIBLE_WITH_NONBLOCKING_GAPS` |
| 비고 1 | 제안서 §1이 명시한 기준선(문서 기준 2026-09-02, Kit `2026.09.02`, branch `harness-dev`)과 현재 상태가 일치한다. |
| 비고 2 | 제안서 §1의 신뢰 순서는 "2026-09-02 현행 문서"를 "현재 로컬 테스트 및 production-to-production 실행 결과"보다 위에 둔다. 이 두 항목의 순서를 뒤집어야 한다 — 재현 가능한 실행 증거가 문서 텍스트보다 우선한다(감사 보고서 자체가 이미 확립한 원칙). |

읽은 범위(Round 0 baseline):

- HRNS-NOW: `README.md`, `doc/documentation_guide.md`, `doc/hrns_now_claude_plan.md`, `doc/hrns_now_design_pattern.md`, `doc/native_qa_checklist.md`, `doc/phase_reports/current_validation_reports.md`, `doc/phase_reports/harness-kit-live-compatibility-audit-report.md`, `doc/claude_prompts/hrns-now-codex-claude-collaboration-onboarding.md`, 제안서 전문
- HRNS-NOW source: `core`의 실행 경로(`HarnessCommand.kt`, `HarnessRunnerPort.kt`, `ActionPolicy.kt`, `ExecuteHarnessActionUseCase.kt`, `StopReason.kt`, `config/WorkspaceConfig.kt`), `infra`의 `HarnessCommandEncoder.kt`, `serialization/HarnessWorkflowStateDto.kt`, `serialization/WorkflowStateMapper.kt`, provider 관련 grep 전수
- Harness Kit: `docs/HARNESS_KIT_MAP.en.md`, `docs/ROADMAP.md`, `docs/TASK_CLASS_TOKEN_POLICY.md`, `docs/adr/session-continuity-task-lineage-and-role-sliced-resume.md`, `scripts/run-cycle.ps1`(구조/dispatch), `scripts/lib/claude/claude-invoke-core.ps1`(구조), `scripts/lib/claude-stop-classification.ps1`, `scripts/lib/harness-context-diet-mode.ps1`, `scripts/report/build-context-diet-packet.ps1`, `scripts/lib/secondary-llm/{secondary-llm-config,secondary-llm-policy,secondary-llm-schema}.ps1`, `scripts/invoke-planning-cycle.ps1` / `scripts/invoke-code-execution.ps1`(dispatch grep), 디렉터리 전수 구조

---

## VERDICT (판정)

**방향은 타당하다. 다만 착수 전 범위를 크게 줄여야 한다.**

제안서가 보존하려는 제품 경계(HRNS = read-only Control Plane, Kit = 실행 엔진 + provider adapter, `WORKFLOW_STATE.json` = 단일 진실)는 *이미* 현재 아키텍처다. 실질적인 Claude 결합은 Kit의 세 wrapper runner + `claude-invoke-core.ps1` 안에만 있다. 권장 첫 행동:

> **AR-0 baseline economics(사용자 실행) + `run-cycle.ps1` 완전 바깥에 사는 Codex read-only reviewer POC.** 기존 Secondary LLM lane을 그대로 본떠 구현한다.

AR-0 증거가 나오기 전에는 provider-neutral request/router contract, HRNS Kotlin 변경, `WORKFLOW_STATE` typed field를 만들지 않는다.

---

## 1. 현재 아키텍처 이해 (Current Architecture Understanding)

### 1.1 HRNS-NOW (HEAD `8757527` source로 검증)

실행 경로:

```text
UiAction
  → ActionPolicy (순수)
  → HarnessCommandMapper
  → HarnessCommand (sealed interface)
  → HarnessCommandEncoder
  → EncodedProcessInvocation{ executable, arguments }
  → HarnessRunnerPort (PowerShellHarnessAdapter / JvmProcessExecutor,
                        SecretMaskingProcessRunner 로 wrap)
  → process
  → WorkflowStatePort.read()  (lock 보유 상태에서)
  → projection
```

- 유일한 외부 실행 대상은 `powershell.exe -NoProfile -ExecutionPolicy Bypass -File <kitRoot>/scripts/{doctor,validate-ops,enter-project,run-cycle}.ps1 <args>`다. **HRNS-NOW 어디에도 "provider" 개념이 없다.** HRNS-NOW는 어떤 LLM도 직접 호출하지 않는다.
- `RuntimeConfig.claudeCommand`는 환경변수 문자열이며 `WorkspacePathProbe`가 probe하고 화면에 표시할 뿐, 어떤 `HarnessCommand`/encoder 경로에도 주입되지 않는다. Claude 호출은 전적으로 Kit이 소유한다.
- `core`에 실제로 존재하는 provider-shaped 누출:
  - `StopReason.{ClaudeContextLimit, ClaudeCallTimeout, ClaudeResponseEmpty, ClaudeResponseTooShort, TransientClaudeOverloaded}`
  - `WorkflowStatus.{UsageLimitBlocked, RoleSlicedWrapperException}`
  - repository bridge 3파일 중 2개가 Claude 이름(`.claude/settings.local.json`, `.claude/CLAUDE.md`)
  - 이것들은 **Kit에서 미러링한 taxonomy 문자열**이지 통합 코드가 아니다.
- `current_slice` / `slice_queue` / `role_sliced` / `usage_guard`는 이미 **sanitize된 opaque `RawJsonValue`**로 운반되고, `WorkflowStateMapper`가 readiness/CTA 판단에서 명시적으로 제외한다(필드 누락과 명시적 null을 구분하지 않고 동일하게 도메인 null로 처리). 이것이 제안서 §19가 말하는 "raw diagnostic extension 선례"의 실체다.
- `kit-version.json` → `CompatibilityPolicy`. `ui_contract_version`은 있으나 `min_ui_version` 같은 역방향 필드는 의도적으로 없다 — Kit이 UI 버전을 지시하지 않는다.

### 1.2 Harness Kit (`D:\harness-kit`, `kit_version=2026.09.02` 로 검증)

- `run-cycle.ps1`(4180줄)이 orchestrator다. `Invoke-RunCycleWrapper`(native `& $ScriptPath @wrapperArgs`)로 `scripts/invoke-{planning-cycle,code-execution,document-execution}.ps1`에 dispatch한다.
- 각 wrapper: deterministic local preflight(기본) → `{planning,code,doc}-claude-runner.ps1` / `code-role-sliced-runner.ps1`(3282줄) → `scripts/lib/claude/claude-invoke-core.ps1`(1150줄).
- `claude-invoke-core.ps1`이 **곧 Claude Code CLI adapter**다: `--resume <session-id>`(`--continue`는 금지), `--output-format stream-json`, session-continuity sidecar, usage-ledger schema 1.1(`workspace_root/logs/usage-ledger/<date>.jsonl`, hashed root, 측정된 input/output/cache token + turns + `total_cost_usd`, 없는 값은 생략).
- **비용 통제는 이미 구현·독립 검증되어 있다**(ROADMAP §4.13, MAP §1.9): deterministic preflight + "최대 1 / 2 / 1"회 Claude 호출(planning / code-role-sliced / doc). reviewer + dockeeper는 parent-deterministic(Claude 호출 0). 모드 `claude|local|skip`, navi 모드 `claude|packet_first|parent_deterministic`(뒤 둘은 opt-in).
- `TASK_CLASS_TOKEN_POLICY.md`: task class A–G, budget tier -1(No-Claude Deterministic) … 3(Heavy), 기본 자동화는 -1..2에 머문다. per-subagent model routing, 영구 model 지정, per-profile routing을 **이미 명시적으로 defer**한다(§13, §15).
- **Secondary LLM lane**(`scripts/lib/secondary-llm/*` 17개 파일, `scripts/report/*secondary-llm*` 11개 runner, smoke 36개): 완전히 분리된 subsystem. provider 기본값 `none`, loopback-only Ollama, capability gate(GPU + VRAM ≥ 8GB + same-run acceleration proof), `runtime_integrated=false`, `safe_to_auto_adopt=false`, **`run-cycle.ps1` 통합 없음**. task type(`secondary-llm-schema.ps1`): `client_check, diff_summary, validation_summary, next_session_prompt, docs_mismatch, reviewer_advice`. 각 type별 candidate/audit/claims 파일 스키마 존재. 현재 개발 host는 부적격(`effective_live_enabled=false`).
- **proto-Task-Packet이 이미 존재한다**: `context-diet-packet.json` schema 1.0(`request_thread_id, task_thread_id, current_status, active_work_key, plan_generation, request_revision_hash, allowed_files, target_contract{authorized_target_file, allowed_write_files_count}, latest_stage_summary, continuity_summary, prompt_budget{source_size_bytes, packet_size_bytes, estimated_reduction_ratio}`) + "execution packet v2"(freshness, State/strategy/target hash, queue lineage, criteria, evidence, validation plan, write boundary, fallback read, continuation risk).
- ROADMAP은 **이 initiative를 이미 candidate로 등재**하고 있다: §7 Candidate A "Application-Facing Control Surface"(= HRNS-NOW), B "Role / Skill Activation", D "Application-Facing Cost and Call Evidence". 이미 명시된 non-goal: raw Messages API rewrite, Agent SDK migration을 즉시 대체로 쓰기, Python mainline cutover, broad wrapper rewrite, 기본 subagent fan-out.
- `claude/agents/*.md`(navi, worker, reviewer, planner, dockeeper, gitter, curator)가 role 정의다. MAP §3: `navi.md`/`worker.md`는 role-sliced runner가 읽지만 결과 변수를 live inline prompt에 주입하지는 않는다.

### 1.3 현재 책임 경계

```text
Kit             = 실행 엔진 + Claude adapter + deterministic gate + telemetry/ledger
WORKFLOW_STATE  = runtime truth
HRNS-NOW        = 실행 의미를 복제하지 않는 read-only Control Plane
```

협업 문서(`hrns-now-codex-claude-collaboration-onboarding.md`) 기준으로:

- **Codex = Integration Owner + Git Owner** (staging/commit/working-tree 위생, CI, Kotlin/Compose/Gradle, 최종 release gate, 아키텍처·계약·문서 일관성 최종 판단)
- **Claude = 독립 분석·검증 + Harness 계약 전문 파트너** (deep audit, root-cause, failure-path, 누락 회귀 테스트·문서 drift 탐지. git mutation 미수행. bounded task에서만 수정)

---

## 2. 제안서 동의 지점 (Proposal Agreements)

- **Decision 1** — HRNS는 Claude/Codex/Ollama를 직접 호출하지 않고 provider 실행은 Harness Runtime 내부 adapter 책임. 이미 사실이며, 지켜야 할 가장 중요한 불변식. 전적으로 동의.
- **Decision 2** — role ≠ model. provider binding은 policy/profile에서 결정. 동의. Kit은 이미 영구 model을 지정하지 않는다(`TASK_CLASS_TOKEN_POLICY` §13, §15).
- **Decision 3 / 4** — provider-native config는 adapter 안에 격리. `%USERPROFILE%\.codex\config.toml` / 사용자 `AGENTS.md` / 사용자 `CLAUDE.md`를 자동 수정하지 않는다. 동의 — Decision 4는 "초기 단계 금지"가 아니라 **영구 hard rule**이어야 한다.
- **§7 Deterministic First / §5 Task Packet 재사용 / §6 Progressive Context Disclosure / §19 typed 승격 전 optional diagnostic extension** — 동의. 네 항목 모두 이미 Kit에 구체 자산이 있다(preflight topology, `context-diet-packet.json` + packet v2, HRNS의 `RawJsonValue` slot).
- **§8** — lane 모델은 `TASK_CLASS_TOKEN_POLICY`(class A–G, tier -1..3)에 **병합**해야 하며 병렬 추가는 안 된다. 선택이 아니라 필수.
- **§AR-0 / §17 / §18 / §23** — provider 추가 전에 현재 경제성 baseline 측정. parallel execution은 최후. 강하게 동의.
- **§33 / §34** — 첫 vertical slice = Codex read-only reviewer. 옳다. 현재 수동 Claude→Codex handoff와 1:1로 대응하고, Secondary LLM `reviewer_advice` task type과도 대응한다.
- **§27** — Native UI QA baseline을 먼저. provider 방향이 열려 있는 동안 bundled runtime / MSI 계약을 고정하지 않는다. 동의.
- **§36** — 이 문서를 구현 명세로 취급하지 말고 source로 검증, 변경량 대비 제품가치·rollback 기준으로 판단. 이 리뷰가 그 지시를 따른다.

---

## 3. 제안서 이견 (Proposal Disagreements)

### a. "Claude 중심 고착 / provider lock-in"은 HRNS-NOW에 대해 과장이다

HRNS 쪽 lock-in은 enum 값 약 5개 + bridge 파일명 2개이지 구조적 결합이 아니다. 진짜 결합은 전부 Kit 내부에 있다: `*-claude-runner.ps1`, `claude-invoke-core.ps1`, `code-role-sliced-runner.ps1`, `claude/agents/*.md`, stream-json/`--resume`. 제안서의 프레이밍은 Kit 전역 "generic rename"을 유도하는데(§30.3이 스스로 금지하는 바로 그것), seam을 정확히 명명해야 계획이 그쪽으로 표류하지 않는다. **seam은 `claude-invoke-core.ps1`과 세 개의 `*-claude-runner.ps1`, 그 위는 아니다.**

### b. §10 "Provider Runtime Contract" / §4 Router 소유자 설계는 지금 하기엔 이르다

제안서는 "daemon/service를 만들지 않는다"(Decision 1)면서 `AgentInvocationRequest / AgentInvocationResult / ProviderCapability / ProviderAvailability / UsageObservation`(§10) + Router(§AR-4)를 스케치한다. AR-0 증거 전에 그 계약을 설계하는 것은 §30이 경고하는 "excessive abstraction" 그 자체다. 권장: AR-1은 **normalized *result* shape만** 정의하고(`claude-stop-classification.ps1` 필드 + ledger 1.1 재사용), request/capability/router 계약은 실제 Codex POC 이후로 미룬다.

### c. §19 State 확장 — Option A(기존 `usage_guard`/role-sliced opaque slot 확장) 선호. 새 `agent_runtime` top-level key는 아직 만들지 않는다

새 key는 Kit writer, `validate-ops.ps1`, `state-surface.ps1`, HRNS DTO, 모든 guaranteed-envelope smoke에 새 surface다 — `required_next_action` BLOCKER를 만든 것과 정확히 같은 변경 class다. HRNS는 이미 `usage_guard`/`role_sliced`를 readiness 영향 0으로 opaque `RawJsonValue` 운반한다. 그 slot을 재사용하고, 계약이 안정된 뒤에만 typed key로 승격한다.

### d. §Decision 2 기본 profile("Claude=Worker, Codex=Reviewer")을 첫 slice에 "default profile"로도 encode하지 않는다

첫 slice의 binding은 정확히 하나(Codex = read-only reviewer)다. role-binding 표는 그것을 소비하는 Router를 함의한다. 첫 slice의 binding은 hard-code로 두고, profile 기반 binding은 AR-4에서 도입한다.

### e. §13/§14의 Ollama 프레이밍이 기존 자산을 과소평가한다

Secondary LLM lane은 이미 6개 task type(`reviewer_advice` 포함), capability gate, audit-first, non-authoritative 계약, candidate/audit/claims 파일 스키마를 구현했다. §AR-6은 "Ollama를 연결한다"가 아니라 "**기존 lane을 동일한 normalized-result reader에 태운다**"여야 하며, 기존 `effective_live_enabled` gate 뒤에 유지한다(현재 개발 host는 이 gate를 통과하지 못하고, 그 사실이 무엇도 막아서는 안 된다).

### f. §28 AR-5(Codex bounded write)와 §9 debate protocol이 하나의 긴 문서 안에서 너무 가볍게 다뤄진다

지도로는 괜찮다. 하지만 계획 산출물은 AR-5를 "AR-0 경제성 + AR-2/AR-3가 N개 실제 task에서 안정" 뒤로 **hard-gate**해야 하고, `%APPDATA%\hrns-now\projects.json` / 실제 workspace 노출을 배제해야 하며, 모든 AR phase에서 commit authority를 빼야 한다(협업 문서가 이미 Codex를 Git Owner로 지정). 제안서도 같은 취지를 말하지만, 계획서는 이를 prose가 아니라 gate로 만들어야 한다.

### g. §1 신뢰 순서

"재현 가능한 로컬 실행 / production-to-production 결과"를 "2026-09-02 문서 텍스트"보다 위에 둔다.

---

## 4. 숨은 위험 (Hidden Risks)

- **두 개의 비용 taxonomy.** lane 모델(D0/L1/F1/G2/C3)을 `TASK_CLASS_TOKEN_POLICY`(A–G, tier -1..3)에 **문자 그대로 병합**하지 않으면 모든 wrapper·smoke·문서·HRNS projection이 둘 다 추적해야 한다. 발생 확률이 가장 높은 실패.
- **stream-json / session-continuity는 Claude Code CLI 전용이고 load-bearing이다.** `claude-invoke-core.ps1`은 stream-json object(`session_id`, `num_turns`, `total_cost_usd`, `is_error`, `subtype`)에서 stop classification, resume decision, usage telemetry를 도출한다. Codex CLI는 출력/exit/session 의미가 다르다. Codex에 대해 continuity + telemetry parity를 재현해야 하는 "provider adapter"는 "reviewer 하나 추가"보다 훨씬 크다 — POC는 continuity/telemetry parity를 요구하지 않도록(read-only, 단일 호출, resume 없음) 범위를 좁혀야 한다.
- **3파일 bridge 계약을 HRNS resolver + 테스트 + 메시지 문자열 4곳이 의존한다.** Codex bridge 파일을 추가하면(§4/§12가 경고) `DefaultKitRuntimeResolver.REQUIRED_ENTRYPOINTS`, `RepositoryBridgeProbe`, `documentation_guide.md`의 "정확히 3개", native-QA 항목 3으로 파급된다. POC는 bridge-free여야 한다(CLI arg / env / ephemeral packet만).
- **Native QA / MSI gate(P1/P2/P3)가 아직 열려 있다.** Kit wrapper의 stdout·exit code·4-file surface를 바꾸는 변경은 compatibility verdict를 다시 열고 대기 중인 Native QA baseline을 무효화한다. POC는 Kit에서 additive + default-off이거나 `run-cycle.ps1` 완전 바깥에 있어야 한다.
- **git authority 충돌.** 협업 문서는 Codex를 Git Owner로 만든다. AR phase가 commit을 자동화하기 시작하면 이미 합의된 human/Codex 경계와 모순된다. 모든 AR phase에서 commit authority를 뺀다.
- **측정의 유효성.** AR-0 A/B("Direct CLI vs Current Harness vs Lean Harness")는 live Claude 호출 + 실제 task가 필요하다 — 분석 단계 규칙("no live Claude/Ollama")과 이 환경/host가 막는다. AR-0은 **사용자 실행** 활동이며 계획서가 이를 명시하지 않으면 stall한다.
- **대상 환경의 Codex CLI 가용성이 미확인이다.** 두 repo 어디에도 Codex CLI가 설치·인증되어 있다는 증거가 없다. AR-2의 첫 단계는 "이 host에서 non-interactive로 실행이나 되는가"이고, 이는 설계가 아니라 사용자/환경 사실이다.

---

## 5. Provider 추상화 경계 (Provider Abstraction Boundary)

좁은 것 → 넓은 것 순서:

### Seam 0 (POC, 권장)

`run-cycle.ps1` **완전 바깥**. 새 `scripts/report/invoke-external-review.ps1`이 compact Review Packet(diff + task result + acceptance criteria; `context-diet-packet.json` + `git diff`에서 구성)을 받아 Codex CLI를 read-only로 호출하고, structured critique + deterministic audit를 `logs/external-review/<run-id>/`에 쓴다. `scripts/report/invoke-secondary-llm-advisory.ps1`이 runtime *옆에* 있는 방식과 정확히 동일. `run-cycle.ps1`·wrapper·State·HRNS 변경 0.

### Seam 1 (이후)

`claude-invoke-core.ps1`과 Codex adapter가 함께 emit하는 `Get-HarnessAgentInvocationResult` 형태의 normalizer. `New-HarnessClaudeStopClassification` 필드 + usage-ledger 1.1 재사용. Additive. 제안서 §10의 "normalized result"는 여기에 속한다.

### Seam 2 (AR-0 + AR-3 이후에만)

각 `invoke-*.ps1` wrapper 내부의 role→runner indirection(`$runner = Resolve-HarnessRoleRunner -Role implementer`). `claude`가 기본, `codex`는 opt-in. Router가 정당화되는 첫 지점.

### 절대 seam이 아닌 곳

HRNS-NOW Kotlin. `HarnessCommand` / `HarnessRunnerPort` / `HarnessCommandEncoder`는 provider-blind로 유지. HRNS에 필요한 유일한 변경은 표시용으로 안정화된 provider/lane/budget projection을 State에서 *읽는* 것뿐(AR-7).

### Router 소유자

**Harness Runtime.** usage telemetry, provider invocation, deterministic preflight가 전부 거기 있다. provider 변경마다 Kotlin release를 내는 것은 HRNS의 "외부 runtime 실행 의미를 복제하지 않는다" 불변식 위반이다.

---

## 6. 최소 첫 POC (Minimal First POC)

*Codex read-only reviewer, `run-cycle.ps1` 바깥:*

- **입력:** `context-diet-packet.json`(이미 `build-context-diet-packet.ps1`이 생성) + 그날 authorized target(들)의 `git diff` + planning output의 `acceptance_criteria`.
- **신규:** `scripts/report/invoke-external-review.ps1` + `scripts/lib/external-review/*`(packet build, result schema, deterministic audit) + mock-invoker smoke. `invoke-secondary-llm-advisory.ps1`을 모델로 한다. 기본 provider `none`; Codex는 명시적 `-AllowLiveCodex` / `HARNESS_EXTERNAL_REVIEW_ALLOW_LIVE=1` 필요.
- **출력:** `logs/external-review/<run-id>/codex-review.candidate.json` + `.audit.json`, `safe_to_auto_adopt=false`, `runtime_integrated=false`.
- **검증(= 제안서 §11 체크리스트):** CLI launch/exit, non-interactive 실행, timeout/cancel, stdout/stderr 분리, 출력 parse 안정성, quota/limit 실패 분류, 기존 `AGENTS.md` 비간섭, global config 무수정, repo write 없음, git mutation 없음.
- **POC에 포함하지 않음:** `run-cycle.ps1` 통합, session continuity, telemetry parity, Claude→resolution 루프, HRNS UI, State field, profile/role 표, 모든 Router.
- **가역성:** 스크립트 디렉터리 1개 + `logs/external-review/` 삭제로 완전 복구. production 경로 무접촉.

**병행(사용자 소유):** 10–20개 실제 bounded task에 대한 AR-0 경제성 측정(Direct CLI vs 현재 Harness). POC의 가치 주장("Human Message Bus 시간 감소, 품질 유지")은 이것 없이는 반증 불가.

---

## 7. 변경 예상 파일/계약 (POC + AR-0…AR-1)

### Harness Kit (전부 additive, default-off)

- 신규 `scripts/report/invoke-external-review.ps1`, `scripts/lib/external-review/*.ps1`, `scripts/smoke/smoke-external-review-*.ps1`(mock invoker, live 호출 0).
- 신규 `docs/EXTERNAL_REVIEW_LANE.md`(`SECONDARY_LLM_LANE.md` 미러). `SMOKE_INDEX.md` count. MAP/ROADMAP의 candidate A/B status 줄.
- `build-context-diet-packet.ps1` 출력을 재사용/확장(additive 필드만) 또는 있는 그대로 읽기.

### HRNS-NOW

- POC에 대해 **없음.** (AR-7에서만, 훨씬 나중에: read-only projection 필드 하나.)

### 이 repo 문서

- Round 2–5 이후 `doc/hrns_now_agent_runtime_evolution_plan.md`(Appendix C 이름). slice가 실제 ship되기 전에는 `hrns_now_claude_plan.md` / `hrns_now_design_pattern.md` / `documentation_guide.md` 변경 없음(`documentation_guide.md`: "정본 문서를 이 proposal 내용으로 선제 변경하지 않는다", 제안서 Appendix C).

---

## 8. 변경 금지 파일/계약 (각자의 gate 없이는)

- HRNS `core`: `HarnessCommand`, `HarnessCommandKind`, `HarnessRunnerPort`, `HarnessCommandEncoder`, `ActionPolicy`, `ClosurePolicy`, `CompatibilityPolicy`, `WorkflowState`/mapper requiredness, `UiAction`. `core`에 provider 개념 진입 금지.
- 4-file daily surface. 3-file bridge(`.claude/settings.local.json`, `.claude/CLAUDE.md`, `tools/run-cycle.ps1`). `enter-project.ps1` 계약. `DefaultKitRuntimeResolver.REQUIRED_ENTRYPOINTS`.
- `run-cycle.ps1` orchestration, wrapper stdout/exit/`-Json` 계약, `WORKFLOW_STATE.json` schema 1.0 + guaranteed envelope, `kit-version.json` schema(`min_ui_version` 없음).
- `claude-invoke-core.ps1` 동작, stop taxonomy(`claude-stop-classification.ps1`), usage-ledger schema 1.1, session-continuity default-off + `--resume`-only + `--continue` 금지.
- Secondary LLM lane 불변식(`runtime_integrated=false`, `safe_to_auto_adopt=false`, capability gate, loopback-only, `run-cycle.ps1` 미연결).
- 사용자 global config: `%USERPROFILE%\.codex\config.toml`, 사용자 `AGENTS.md`, 사용자 `CLAUDE.md` — 어떤 phase도 쓰지 않는다.
- `%APPDATA%\hrns-now\projects.json`, `%LOCALAPPDATA%\hrns-now\*`, `D:\harness-workspaces\*`.
- Git authority: 어떤 AR phase도 `git add/commit/push`를 자동화하지 않는다.

---

## 9. Migration Phases (§28의 재구성 — 더 좁은 gate)

| Phase | 범위 | 다음으로 가는 gate |
|---|---|---|
| **AR-0** Baseline Economics | 사용자 실행, 코드 변경 없음. Direct-CLI vs 현재-Harness를 10–20개 실제 task로 비교 | task class별 tokens/agent-calls/human-minutes/first-pass-accept 수치가 존재 |
| **AR-1** Normalized *result* shape만 | Kit, additive. stop-classification + ledger 1.1 재사용. request/capability/router 계약 없음. live Codex 없음 | result shape 리뷰 완료 |
| **AR-2** Codex Read-only Reviewer POC | Seam 0, `run-cycle.ps1` 바깥, default-off. §11 체크리스트 증명 | N개 실제 review, 안정적 parse, 분류된 실패, 측정된 overhead |
| **AR-3** Guarded Claude→Codex one-round review lane | opt-in. Proposal→Critique→Resolution, compact artifact만(§9.2), 명시 승인 시 최대 +1 round | human-review-time 또는 first-pass-accept 개선 입증(§18) |
| **AR-4** rule-based Router | Kit 소유, deterministic, opt-in. lane 모델을 `TASK_CLASS_TOKEN_POLICY`에 **병합** | 단일 정본 taxonomy |
| **AR-5** Codex bounded write | scratch/worktree만. production repo는 명시적 opt-in. commit authority 없음 | AR-0 + AR-2/3 안정 |
| **AR-6** 기존 Secondary LLM lane을 AR-1 result reader에 태움 | `effective_live_enabled` gate 뒤. 개발 host 부적격 — non-blocking | — |
| **AR-7** HRNS State/UI projection | 안정화된 필드만, read-only, 먼저 `RawJsonValue`/`usage_guard` slot 경유. 첫 HRNS Kotlin 변경 | — |
| **AR-8** packaging | runtime 계약 안정 후. 이 전에 MSI/bundled runtime 최종화하지 않음 | — |

Native UI QA baseline(현재 P1)은 병행하며 어떤 AR phase에도 막히지 않는다.

---

## 10. 열린 결정 사항 (Open Decisions — Codex 교차검토 / 사용자)

1. Router 소유자: Harness Runtime(내 권고) vs HRNS core.
2. AR-0 경제성이 AR-2의 hard 선행조건인가, 아니면 병행 가능한가(POC는 만들되 가치 주장은 AR-0까지 보류 — 내 권고)?
3. lane 모델 vs `TASK_CLASS_TOKEN_POLICY`: 단일 병합 taxonomy(내 입장). 병합 문서 소유자 = Kit인가? (tier가 wrapper 동작을 좌우하므로 Kit이 타당.)
4. State 확장: `usage_guard`/opaque `RawJsonValue` slot 재사용(내 입장) vs 새 `agent_runtime` key. Kit-writer 복잡도 판단 필요.
5. 대상 host에 Codex CLI가 설치 + non-interactive 사용 가능한가? (환경 사실, AR-2를 막음.)
6. "external review"가 영구히 `run-cycle.ps1` 바깥에 사는가(Secondary LLM처럼), 아니면 AR-3/AR-4에서 안으로 들어오는가? Seam 0 vs Seam 2 설계에 영향.
7. Codex가 `block`을 내고 Claude가 반대할 때 resolution 권한: 항상 사람에게 escalate인가, 아니면 safety/acceptance/source-evidence 충돌에 한해서인가(제안서 §9.1)?
8. 산출물 소유자: 제안서 Appendix C는 `doc/hrns_now_agent_runtime_evolution_plan.md`를 "Codex 또는 지정된 Integration Owner"가 작성(Round 3)한다고 한다. Codex가 작성하고 Claude는 Round 4 adversarial 검토만 하는 것으로 확정.

---

## §36 답변 (제안서가 읽은 Claude/Codex에게 요구한 마지막 지시)

1. **잘못 이해한 현재 구조?** 있음 — HRNS-NOW는 "Claude 고착"이 아니다. provider 개념이 전혀 없다. 결합은 Kit wrapper runner 안에 국한된다.
2. **이미 구현된 제안 기능?** deterministic-first + call-count topology(§7), proto-Task-Packet(`context-diet-packet.json` + packet v2, §5), 없는 값을 만들지 않는 usage/cost telemetry(§10, §16), `reviewer_advice`를 가진 non-authoritative advisory-provider lane(§13/§14), 그리고 이 initiative 자체가 ROADMAP candidate(§27).
3. **현재 계약 재사용으로 더 작게?** State 확장 → 기존 opaque `RawJsonValue` slot(새 key 아님). result 계약 → `claude-stop-classification.ps1` + ledger 1.1 필드. Review Packet → `build-context-diet-packet.ps1` 출력.
4. **가장 자연스러운 실제 seam?** `claude-invoke-core.ps1` + 세 개의 `*-claude-runner.ps1` — 그리고 POC에서는 그것들 *옆*(신규 `scripts/report/invoke-external-review.ps1`), HRNS Kotlin은 절대 아님.
5. **generic화해야 할 최소 Claude-specific 범위?** POC에는 없음. 장기적으로는 normalized *result* object 하나뿐. Claude CLI adapter는 Claude-specific으로 유지.
6. **standard workflow를 거의 안 건드리고 Codex reviewer 추가 가능?** 가능 — `invoke-secondary-llm-advisory.ps1`처럼 `run-cycle.ps1` 바깥의 `scripts/report/` runner로.
7. **기존 Secondary LLM 자산을 새 Provider abstraction이 재사용?** 가능 — config/policy/schema/validator/capability-gate/audit 패턴과 `reviewer_advice` task type을 직접 재사용. AR-6이 그 lane을 AR-1 result reader에 태운다.
8. **현재 compatibility/native QA/MSI Gate 영향?** POC가 Kit에서 additive + default-off이거나 `run-cycle.ps1` 바깥이면 0. wrapper stdout/exit/4-file surface를 바꾸면 verdict와 대기 중 Native QA baseline이 다시 열린다.
9. **provider 추가보다 먼저 고칠 token cost 경로?** 먼저 AR-0 측정. 그다음, 이미 만들어졌으나 default가 아닌 opt-in들(compact planning prompt, `packet_first`/`parent_deterministic` navi)이 새 provider보다 싼 레버.
10. **가장 작은 reversible vertical slice?** Seam-0 Codex read-only reviewer runner — 스크립트 디렉터리 1개 + 로그 디렉터리 1개 제거로 완전 원복.

---

## 무결성 확인 (Mutation / Hygiene)

- 이 분석을 산출하는 과정(Round 0 읽기 + Round 1 작성)에서 HRNS-NOW / `D:\harness-kit`의 파일 변경 0, Git mutation 0, 네트워크/live LLM 호출 0, 테스트·Harness entrypoint·smoke 실행 0.
- **이 문서 파일(`doc/revolution/revOpinion_claude01.md`) 생성은 사용자(제품 Owner)의 명시적 저장 지시에 따른 것이다.** 그 외 어떤 파일도 생성·수정·삭제·이동·rename하지 않았다.
- `git add`/`commit`/`push`/`amend`/`reset`/`restore`/`checkout`/`stash`/`rebase`/`clean` 미수행. 이 문서의 staging/commit 여부는 Git Owner(Codex)와 사용자의 판단이다.
- 다음 단계는 Codex의 독립 Round 1 제출이며, 그 전까지 Claude는 추가 작업을 시작하지 않고 대기한다.
