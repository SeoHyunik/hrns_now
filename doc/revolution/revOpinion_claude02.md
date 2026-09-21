# HRNS-NOW Agent Runtime Evolution — Claude Cross Review (Round 2)

- 문서 성격: 제안서 `doc/claude_prompts/hrns_now_agent_runtime_evolution_proposal.md` §31 협업 프로토콜의 **Round 2 — Cross Review (Claude 제출본)**
- 입력: `doc/revolution/revOpinion_claude01.md` (Claude Round 1), `doc/revolution/revOpinion_codex01.md` (Codex Round 1)
- 작성 주체: Claude
- 작성 기준일: 2026-09-04
- 범위 규칙(§31 Round 2): 사실 오류 / 불필요한 abstraction / 현재 source 충돌 / backward compatibility 위험 / token·cost 악화 / 누락된 test·gate / 더 작은 vertical slice 대안만 제출한다. **전체 계획을 새로 쓰지 않는다.** 통합 계획서(Round 3)는 Codex(Integration Owner)가 작성한다.
- 상태: 분석 단계. 이 문서 저장을 제외하면 파일/Git/네트워크 mutation 0. 테스트·Harness entrypoint·smoke·live Claude/Codex/Ollama 호출 미실행.

---

## 0. Verdict 정합

| 항목 | Claude Round 1 | Codex Round 1 | Round 2 결론 |
|---|---|---|---|
| 방향 | "타당하나 착수 전 범위 축소" | `REVISE_BEFORE_INTEGRATED_PLAN` | **동일. 수렴.** |
| 첫 slice | Seam-0: `run-cycle.ps1` 바깥 Codex read-only reviewer report lane, default-off, Secondary LLM lane 패턴 차용 | AR-C3: `run-cycle.ps1` 미연결 독립 report entrypoint | **동일. 수렴.** |
| Router / 범용 Provider Runtime contract / `claude-invoke-core.ps1` 범용화 | 지금 하지 않음 | 지금 하지 않음 | **동일. 수렴.** |
| HRNS Kotlin 변경 | POC에서 없음 | POC에서 없음 | **동일. 수렴.** |

두 Round 1은 결론이 일치한다. 이 Cross Review의 목적은 **Codex가 더 정확하거나 더 안전하게 지적한 지점을 Claude Round 1에 반영(수용)하고, Codex Round 1의 사실 오류·과한 ceremony를 교정하며, Round 3 통합 계획서에 넘길 수렴/발산 지도를 확정**하는 것이다.

---

## 1. 수용 — Codex가 옳고 Claude Round 1을 개정한다

### 1.1 execution packet v2를 범용 Task Packet으로 승격하지 않는다 (Codex §1.4, §3.1 수용)

Claude Round 1 §1.2 / §5 / §6은 `context-diet-packet.json`과 "execution packet v2"를 하나로 뭉쳐 "proto-Task-Packet이 이미 존재한다"고 서술했다. **이는 부정확했다.** 두 산출물은 별개다.

- `context-diet-packet.json` (schema **1.0**, `scripts/report/build-context-diet-packet.ps1` 생성): diagnostic packet. `request_thread_id`, `task_thread_id`, `allowed_files`, `target_contract`, `prompt_budget{…bytes, reduction ratio}` 등.
- `00-wrapper-input.json` (schema **2.0**, `code-role-sliced-runner.ps1`, Codex §1.4가 직접 확인): `execution_wrapper=code`, `orchestration_mode=role_sliced`인 **변경 작업용** 패킷. `allowed/forbidden write files`, budget continuation, worker 실행 직전 TOCTOU 재검증 등 write-authority 의미를 가진다.

Codex의 결론이 맞다: packet v2 전체를 read-only reviewer 계약으로 올리면 write semantics와 role-sliced continuation 필드가 core contract에 섞인다. **개정: Review Packet은 원본 artifact·hash를 참조하는 얇은 파생 계약(thin derived projection)이며, 두 패킷 어느 쪽의 승격도 아니다.** 이 개정은 Claude Round 1 §5의 Seam-0 권고를 오히려 강화한다.

단, Codex §5.2 Review Packet 후보는 `allowed_scope`/`forbidden_scope`를 "빈 배열로 고정"으로 남긴다 — 이 부분은 §3.2에서 되돌려준다.

### 1.2 A/B 측정은 POC의 hard 선행조건이 아니다 (Codex §3.5, §3.6 수용)

Claude Round 1은 AR-0을 "사용자 소유·병행"으로 뒀으나, AR-0→AR-1 gate를 "수치가 존재"로 걸고 POC 가치를 "full A/B 없이는 반증 불가"로 규정했다. **너무 엄격했다.** Codex의 3단 분리를 채택한다.

- POC 이전: 기존 usage ledger + 소규모 고정 표본으로 현재 비용·human handoff 기준선 확보.
- POC 중: 같은 표본에서 reviewer 추가 비용·finding 품질·human review 시간 측정.
- graduation 이전: task class별 확대 표본으로 Direct/Current/Lean/Dual 비교.

**개정: 10–20 task 전체 A/B는 graduation gate이지 POC 선행조건이 아니다.**

### 1.3 독립 report POC는 Human Message Bus 감소를 측정할 수 없다 (Codex §3.6 수용)

Claude Round 1 §6은 병행 AR-0의 목적을 "POC의 가치 주장(Human Message Bus 시간 감소, 품질 유지)"으로 적었다. **standalone report POC는 이를 증명할 수 없다.** 사용자의 수동 복사·전달 감소는 Claude→Codex를 실제로 연결하는 guarded one-round(AR-C4) 이후에만 측정된다. **개정: POC acceptance criteria에서 "human handoff 시간 감소"를 제거한다.** POC는 안전성 + result contract + reviewer overhead + finding 품질만 증명한다.

### 1.4 Codex sandbox의 read 범위는 미검증 위험이다 (Codex §4.1, §6.4, §10.2 수용)

Claude Round 1은 "Codex CLI의 output/exit/session 의미 차이"는 언급했으나 **read-scope 위험을 놓쳤다**: `--sandbox read-only`가 repository write를 막더라도 working root 밖 파일, 다른 drive, `%USERPROFILE%`, 환경변수 노출까지 제한한다는 보장이 없다. **개정: POC의 첫 live 단계는 sandbox read-scope probe(§8.5)이며, 통과 전에는 production source를 Codex working root로 직접 노출하지 않고 sanitized review bundle / 저민감 fixture만 사용한다.**

### 1.5 `AGENTS.md` precedence는 현재 repo로 검증 불가 (Codex §4.3, §10.3 수용)

현재 `S:\dev\project\hrns_now`에는 filesystem `AGENTS.md`가 없다. Claude Round 1의 §11-답변 체크리스트는 "AGENTS.md non-interference"를 항목으로 넣었으나 검증 대상이 없다는 점을 적지 않았다. **개정: 최소 fixture(root + nested `AGENTS.md`, Harness task와 충돌하는 지시, read-only/no-git rule, 허용 범위 밖 파일 지시)가 필요하다. 기존 `AGENTS.md`를 덮어쓰거나 bridge 파일로 자동 생성하지 않는다.**

### 1.6 `D:\harness-kit`은 git repo가 아니다 → 명시적 rollback 절차 필요 (Codex §4.11, §10.11 수용)

Claude Round 1 §6은 "스크립트 디렉터리 1개 + 로그 디렉터리 1개 삭제로 원복"으로만 적었다. Codex의 규율이 더 강하다. **개정: POC task card에 (a) 사전 clean snapshot(직전 remediation이 `D:\backup\harness-kit-2026.09.02-verified.zip` + SHA-256로 확립한 패턴), (b) 신규 경로 전수 change manifest + 각 hash, (c) 기존 runtime 스크립트 무편집 원칙(편집이 필요하면 POC 아님), (d) rollback = manifest 경로 삭제 + 문서 count 줄 revert 를 명시한다.**

### 1.7 Claude Round 1이 누락한 항목 — 전부 수용

- **Prompt injection / instruction-vs-data 경계** (Codex §4.5): Review Packet은 diff·source·test output을 instruction으로 취급하지 않도록 명시적 delimiter + source classification을 가진다.
- **Raw artifact retention** (Codex §4.9, §10.7): raw JSONL/stdout/stderr 보존 기간, redaction, 최대 byte, release exclusion을 사전 정의. Secondary LLM lane의 redaction + release-hygiene 패턴 재사용.
- **Codex CLI version drift** (Codex §4.7): option 이름, JSONL event shape, output schema 처리, exit code 의미, quota/auth/network 오류 문구, session persistence 동작 변경에 대비. min/verified version 범위 + capability probe + unsupported 시 fail/skip 정책 필요.
- **Windows process/encoding 재구현 위험** (Codex §4.8): stdout/stderr deadlock, 한글·공백 경로, quote/newline prompt, timeout 후 child 잔존, console flash, BOM, 장문 argument line. Claude runner가 이미 해결한 문제다 — §3.3에서 "언제 공통화하나"를 조정한다.

### 1.8 Native UI QA 순서 (Codex §9 AR-C0, "Compatibility/Native QA/MSI" 절 — 부분 수용)

Claude Round 1은 "Native UI QA는 병행하며 어떤 AR phase에도 막히지 않는다"고 했다. Codex의 뉘앙스가 맞다. **개정: Round 2–5(분석·계획)와 경제성 baseline(AR-C1)·capability spike(AR-C2a)는 Native UI QA와 병행 가능하나, POC *구현*(AR-C3)은 Native UI QA baseline이 기록된 뒤 시작한다.** 이유는 §30.4 원인 분리 — 이후 UI 회귀와 runtime 변화 문제를 구분하기 위해.

### 1.9 `queue.active.*` / `queue.blocked_reason` / `queue.last_updated_at` requiredness gap (Codex §1.7, §10.12 수용)

`WorkflowStateMapper`는 `queue.status`만 key 부재 시 `MapResult.Failure`로 처리한다. `queue.active.cardId`/`sliceId`, `queue.blockedReason`, `queue.lastUpdatedAt`은 key가 없어도 null/blank로 흡수한다(`HarnessWorkflowStateDto` + `WorkflowStateMapper` 확인). `hrns_now_claude_plan.md` 부록 A는 이 다섯 필드를 "queue 안정 표면"으로 함께 나열한다 → 문서/코드 정밀도 gap이 실재한다. Codex의 판단이 맞다: 현재 writer가 항상 쓰므로 관측 가능한 오작동은 없고 BLOCKER/HIGH가 아니다. **수용: Agent Runtime 작업과 분리된 별도 bounded hardening task로 유지한다.** Claude Round 1이 놓친 정당한 발견이다.

---

## 2. 사실 오류 / 정밀도 — Codex Round 1을 교정한다

### 2.1 wrapper 스크립트 파일명 (Codex §1.3)

Codex §1.3은 `scripts/invoke-planning.ps1`, `scripts/invoke-code.ps1`, `scripts/invoke-documentation.ps1`로 적었다. 실제 파일명(확인: `D:\harness-kit\scripts\` 목록)은:

```text
scripts/invoke-planning-cycle.ps1
scripts/invoke-code-execution.ps1
scripts/invoke-document-execution.ps1
```

Round 3 통합 계획서는 정확한 이름을 써야 한다.

### 2.2 `Start-ClaudeProcess` 함수명 (Codex §1.3)

Codex는 "Planning/code/doc runner에는 각각 `Start-ClaudeProcess`가 존재"라고 단정한다. Claude는 이 함수명을 직접 확인하지 않았다(`invoke-planning-cycle.ps1`이 `planning-claude-runner.ps1`을 dot-source하는 것, `claude-invoke-core.ps1`의 함수 목록은 확인했으나 각 runner 내부의 `Start-ClaudeProcess`는 미확인). **process-supervision이 세 runner에 공통 존재한다는 취지는 타당하나 정확한 함수명은 Round 3에서 실파일로 확인 필요.**

### 2.3 packet schema 출처 (Codex §1.4) — 오류 아님, 상호 보완

Codex는 `00-wrapper-input.json` schema 2.0을 `code-role-sliced-runner.ps1`에서 직접 확인했다. Claude는 그 파일(3282줄)을 열지 않았고 `context-diet-packet.json` schema 1.0만 확인했다. Codex가 나열한 packet v2 필드는 MAP §1.9 / §7.1의 "execution packet v2" 서술과 일치한다. **오류가 아니라 Codex가 확인한 파일을 Claude가 확인하지 않은 것이다. §1.1의 개정으로 정합된다.**

### 2.4 Codex CLI 가용성 — Claude Round 1 열린 결정 #5 해소

Codex baseline 표: `codex-cli 0.153.2` 확인, `exec`/`read-only`/`ephemeral`/`ignore-user-config`/`output-schema`/`json` option 확인(read-only help 조회). **Claude Round 1 §10 #5("Codex CLI가 설치·사용 가능한가")는 이로써 닫힌다.** 다만 하위 질문(min version 범위, capability probe 방식, unsupported version fail/skip 정책)은 Codex §10.4대로 열린 상태 유지.

### 2.5 phase 개수/namespace (Codex §9: AR-C0..AR-C10, 11개)

오류가 아니라 divergence다. 제안서 §28은 "Phase 번호는 최종 계획에서 별도 namespace로 다시 정한다"고 명시하므로 Claude의 AR-0..AR-8도 Codex의 AR-C0..AR-C10도 정본이 아니다. 실질 delta는 §9.D5에서 다룬다.

---

## 3. 불필요하거나 과한 abstraction / ceremony — Codex Round 1에 되돌려주는 지점

### 3.1 process supervisor 공통화 시점 (Codex §5.5)

Codex는 bounded process supervisor를 "두 adapter에서 실제 동일성 확인 후 추출"로 §5.5 후순위에 뒀다. 그런데 §4.8은 Codex adapter가 Claude runner의 stdout/stderr deadlock·한글 경로·child 잔존을 **다시 만들 위험**을 정확히 지적한다. 두 지점이 상충한다: 공통화를 끝까지 미루면 Codex adapter가 process supervision을 손으로 재구현하게 된다.

**Claude 입장: 추출은 미루되 재구현은 하지 않는다.** Codex adapter는 기존 supervision 패턴(start/exit, 동시 drain, timeout/cancel, process-tree kill, output byte cap, no-window)을 **의도적으로 복사**하고 "AR-C5에서 수렴" TODO를 명시한다. 새 모듈로 성급히 추출하지도(잘못된 abstraction), 손으로 새로 짜지도(§4.8 회귀) 않는다.

### 3.2 Review Packet의 write-scope 필드 (Codex §5.2)

Codex §5.2는 `allowed_scope`/`forbidden_scope`를 "reviewer POC에 넣지 않거나 **항상 빈 배열로 고정**"으로 남긴다. **항상 `[]`인 필드는 잠재적 affordance다.** reviewer POC의 Review Packet에서는 write-scope 필드를 **아예 제거**한다(0으로 고정이 아님). 나중에 write lane(AR-C7)이 생기면 그때 별도 계약에 추가한다.

### 3.3 phase 수 (Codex §9: 11개)

AR-C0..AR-C10 11단계는 ceremony가 많다. Claude 제안: AR-C1(경제성 baseline)을 AR-C0(baseline freeze)에 흡수, AR-C2를 spike(AR-C2a)/contract(AR-C2b) 하위 단계로 두어 순 ~8단계. **Round 3(Codex 소유)이 최종 결정하되, "phase가 너무 잘게 쪼개졌다"는 우려를 등록한다.**

### 3.4 POC 신규 파일 수 (Codex §6.2: 5개 후보)

```text
scripts/report/build-agent-review-packet.ps1
scripts/report/invoke-codex-review.ps1
scripts/lib/codex/codex-review-adapter.ps1
scripts/lib/agent-runtime/agent-review-contract.ps1
scripts/smoke/smoke-codex-review-*.ps1
```

`scripts/lib/agent-runtime/`라는 새 디렉터리를 POC 단계에 만드는 것은 "agent runtime" 범용 계층을 조기 선언하는 셈이다. **Claude 제안: POC는 ≤3개 신규 파일** — `scripts/report/invoke-codex-review.ps1`(packet build 포함), `scripts/lib/codex/codex-review-adapter.ps1`, `scripts/lib/codex/codex-review-contract.ps1`. `agent-runtime/` 네임스페이스는 두 번째 adapter가 실재할 때(AR-C5) 만든다. Codex도 §6.2에서 "파일 수·이름은 더 줄일 수 있다"고 했으므로 이 방향에 열려 있다.

### 3.5 offline smoke 20개 사전 명세 (Codex §6.6)

20개 case 목록은 커버리지로는 훌륭하다(전부 유효). 다만 "POC 전에 20개를 명세"하는 것보다 **실제 Codex 동작 관찰(AR-C2a spike) 후 case를 도출**하는 편이 맞다. case 수는 결과이지 사전 목표가 아니다. Codex의 20개 목록은 AR-C3 smoke 설계의 체크리스트로 보존하되 "정확히 20개"로 고정하지 않는다.

---

## 4. Backward-compatibility & 보안 위험 — 합의 및 추가

### 4.1 합의 (두 Round 1 모두 커버, 그대로 Round 3 입력)

- run-cycle 기본 경로·State writer·HRNS command/adapter 무변경 시 `COMPATIBLE_WITH_NONBLOCKING_GAPS` verdict 유지 (Codex "Compatibility" 절, Claude §8).
- 3-file bridge(`.claude/settings.local.json`, `.claude/CLAUDE.md`, `tools/run-cycle.ps1`) 무확장, 4-file daily surface 무확장, legacy artifact 미복원.
- 사용자 global config(`%USERPROFILE%\.codex\config.toml`, 사용자 `AGENTS.md`, Claude 전역 설정) 무변경.
- `claude-invoke-core.ps1` 동작, stop/failure taxonomy, session continuity(`--resume` only, `--continue` 금지), usage-ledger schema 1.1 무변경.
- Secondary LLM 불변식(`authoritative=false`, `safe_to_auto_adopt=false`, `runtime_integrated=false`, loopback-only, hardware gate) 무변경.
- 자동 commit/push 없음, Router 없음, `WORKFLOW_STATE` writer 추가 없음, HRNS UI/provider selector 없음.

### 4.2 Codex가 옳게 지적했고 Claude가 놓친 위험 (§1.4–§1.7에서 이미 수용)

sandbox read-scope, `AGENTS.md` fixture, prompt injection, raw-artifact retention, `D:\harness-kit` rollback 절차, Windows process/encoding 재구현.

### 4.3 Claude Round 1이 제기했고 Codex가 다루지 않은 위험 (Round 3에 유지)

- **HRNS `core`의 provider-shaped 누출 인벤토리**: `StopReason.{ClaudeContextLimit, ClaudeCallTimeout, ClaudeResponseEmpty, ClaudeResponseTooShort, TransientClaudeOverloaded}`, `WorkflowStatus.{UsageLimitBlocked, RoleSlicedWrapperException}`, bridge 3파일 중 2개가 Claude 이름. **결론: HRNS 쪽 결합은 enum 값 ~5개 + 파일명 2개뿐 — "Claude 고착"이 아니다.** Codex §1.1도 "HRNS는 Provider 실행기가 아니다"로 같은 취지지만, 이 인벤토리는 "HRNS가 실제로 얼마나 결합됐나"에 대한 정량 답이므로 Round 3에서 유지한다.
- **`kit-version.json`에 `min_ui_version` 역방향 필드가 없다** — Kit이 UI 버전을 지시하지 않는다. AR-C9 State/UI projection 설계 시 HRNS 독립성의 근거로 유지.
- **lane 모델은 `TASK_CLASS_TOKEN_POLICY`(class A–G, tier -1..3)에 병합해야 하며 병렬 추가 금지.** Codex §9 AR-C6은 "rule-based lane 선택"을 언급하나 taxonomy 병합 위험을 Claude만큼 강하게 명시하지 않았다. **Round 3 flag로 유지: 두 taxonomy 공존이 최고 확률 실패.**

### 4.4 Claude가 새로 추가하는 backward-compat gate

- **"신규 bridge 파일 없음" 회귀 smoke**: review lane이 3-file bridge count를 바꾸지 않음을 단언. 두 Round 1 모두 위험은 인지했으나 test로 만들지 않았다.
- **AR-C9 진입 시 `HarnessProductionContractTest` 규율**: `agent_runtime` raw extension을 `HarnessWorkflowStateDto`에 추가하는 순간, 직전 remediation이 확립한 guaranteed-envelope smoke + production-to-production contract test를 동일하게 적용한다(`required_next_action` BLOCKER 재발 방지).

---

## 5. Token / Cost 악화 지점

- POC 자체는 opt-in·default-off이므로 미호출 시 추가 process/network/token 비용 0 — 두 Round 1 합의(Codex §6.8, Claude §6). 이견 없음.
- 추가 관찰: Review Packet build는 `build-context-diet-packet.ps1` 경로에서 `harness-continuity-doctor.ps1`를 자식 `powershell`로 spawn한다(해당 스크립트 line 102). deterministic·no-LLM·저비용이나, Review Packet builder가 이를 재사용한다면 child process 비용을 문서화한다.
- **provider 추가보다 먼저 쓸 수 있는 더 싼 레버**(Claude §36 답변 #9 유지): 이미 구현됐으나 default가 아닌 opt-in들 — compact planning prompt, `packet_first` navi, `parent_deterministic` navi. AR-0/AR-C1 측정이 이들의 실효를 먼저 보여줄 수 있다. Codex §1.5도 "졸업하지 않은 기능"으로 같은 사실을 인지.
- 경제성 측정 편향(Codex §4.10): completed-and-validated task를 동일 단위로 비교. 수용, Round 3 A/B 설계 조건에 포함.

---

## 6. 누락된 test / gate (양측)

| gate | 출처 | 상태 |
|---|---|---|
| sandbox read-scope probe (canary 파일: working root / 상위 / 타 drive / `%USERPROFILE%` / env var) | Codex §4.1, Claude §8.5(본 문서) | **신규 hard gate — AR-C2a. production source 노출의 선행조건.** |
| `AGENTS.md` precedence fixture (root + nested + 충돌 지시) | Codex §4.3 | 신규 — AR-C2b/AR-C3 |
| Codex CLI auth/config 실제 적용 상태 사후 설명 | Codex §4.2 | 신규 — `--ignore-user-config`여도 auth는 provider-native store. 실행 후 "무엇이 적용됐나" 기록 의무 |
| "신규 bridge 파일 없음" 회귀 smoke | Claude(본 문서 §4.4) | 신규 |
| AR-C9 `agent_runtime` extension에 guaranteed-envelope + production-to-production contract test | Claude(본 문서 §4.4) | 신규 |
| lane 모델 ↔ `TASK_CLASS_TOKEN_POLICY` 단일 정본 병합 검증 | Claude §1 Round 1, 본 문서 §4.3 | AR-C6 |
| `queue.active.*` / `queue.blocked_reason` / `queue.last_updated_at` requiredness hardening | Codex §1.7 | **Agent Runtime과 분리된 독립 bounded task** |
| raw artifact redaction / retention / max-byte / release-exclusion smoke | Codex §4.9 | AR-C3 smoke에 포함 |
| Codex unavailable → workflow success 오인 금지 단언 | Codex §6.8, Claude §6 | AR-C3 smoke |
| 실행 전후 repository/git status·hash 불변 단언 | Codex §6.6 #17, §6.8 | AR-C3 smoke |

---

## 7. 더 작은 vertical slice 대안

### 7.1 AR-C2a — capability spike를 "버리는 조사"로 먼저

Codex §9 AR-C2는 "Reviewer 최소 계약 정의 + capability spike"를 묶는다. **더 작은 첫 단계**: 계약·smoke·commit 파일 없이 **순수 조사**만.

- 질문: 이 Windows host에서 `codex exec`가 non-interactive로 도는가 / JSONL event shape는 무엇인가 / `--sandbox read-only`의 실제 read 범위는 어디까지인가(§6 canary probe) / 한글·공백 경로에서 encoding은 어떻게 되는가.
- 산출: 신규 파일 0, 검증 fixture는 scratch에서만, 결과는 findings 노트 1건.
- 이후 AR-C2b가 **실제 관찰된 output을 근거로** ReviewRequest/ReviewResult/Failure/Usage 최소 schema를 설계.

계약을 관찰 전에 설계하면 Codex §4.7(version/output drift)에 취약해진다.

### 7.2 POC live 입력은 sanitized bundle만 (Codex §6.4 채택 — Claude Round 1보다 작고 안전)

Claude Round 1 §6은 입력을 "그날 authorized target의 `git diff`"로 적어 실제 working tree 실행을 함의했다. **Codex §6.4가 더 작다**: 최초 live POC는 sanitized review bundle / 저민감 fixture만. bundle 구성 = task packet + explicit diff + 검토 허용 source snapshot + project instruction 사본/pointer + deterministic test result + evidence manifest·hashes. 실제 repo `-C` 직접 노출은 read-scope probe(§7.1) 통과 후 AR-C4+로 미룬다. **채택.**

### 7.3 파일 수 축소 (§3.4)

`scripts/lib/agent-runtime/` 없이 `scripts/lib/codex/` 아래 ≤3파일. 범용 네임스페이스는 두 번째 adapter(AR-C5)에서.

---

## 8. Codex가 Round 2에 넘긴 8개 질문에 대한 Claude 답변

Codex Round 1 "Cross Review에 전달할 핵심 주장" 8항목:

### 8.1 execution packet v2 전체가 범용 Task Packet이 될 수 있는가, thin review projection이 필요한가?

**Thin projection.** §1.1에서 수용함. packet v2(`00-wrapper-input.json` schema 2.0)는 write-authority + role-sliced continuation 의미를 가지며 read-only reviewer 계약에 들어가면 안 된다. Review Packet은 원본 artifact + hash를 **참조**하는 파생 계약이지 복제된 두 번째 runtime truth가 아니다.

### 8.2 `claude-invoke-core.ps1`에서 지금 generic화할 최소 범위는?

**`claude-invoke-core.ps1` 자체에서는 지금 아무것도 아니다.** 지금 factor할 수 있는 유일한 후보는 **bounded process supervisor**(start/exit, 동시 stdout/stderr drain, timeout/cancel, process-tree kill, output byte cap, no-window)뿐인데, 이조차 §3.1대로 **추출이 아니라 Codex adapter가 패턴을 의도적으로 복사 + AR-C5 수렴 TODO**로 처리한다. stop classification, stream-json 해석, session capture, resume broker, usage ledger는 100% Claude 내부에 남긴다. Codex §3.3·§5.4와 동일 결론.

### 8.3 Secondary LLM evidence/audit helper 중 provider-neutral 추출 대상 — 정확한 함수/파일?

**Claude Round 0에서 이 파일들을 함수 단위로 읽지 않았다(읽은 것: `secondary-llm-config.ps1`, `secondary-llm-policy.ps1`, `secondary-llm-schema.ps1`).** 정직한 답: 재사용 개념은

- `scripts/report/build-secondary-llm-evidence-manifest.ps1`, `scripts/report/build-secondary-llm-input-packet.ps1` — sanitized packet builder + run-root containment
- `scripts/lib/secondary-llm/secondary-llm-audit.ps1`, `secondary-llm-claim-evidence.ps1` — deterministic claim verification
- `scripts/lib/secondary-llm/secondary-llm-validator.ps1` — schema validation

비재사용: `secondary-llm-config.ps1`(Ollama env, `HARNESS_OLLAMA_BASE_URL`, loopback assert), `secondary-llm-ollama-client.ps1`(HTTP), `secondary-llm-capability.ps1`(GPU/VRAM), `secondary-llm-schema.ps1`의 `secondary_llm_*` response kind.

**단, 정확한 함수 시그니처 추출 목록은 두 Round 1 어느 쪽도 만들지 않았다. Round 3에서 이 4개 helper 파일을 실제로 열어 함수 단위 추출 목록을 만드는 것을 명시적 assigned task로 둔다.** 지금 손으로 지어내지 않는다.

### 8.4 sanitized review bundle이 project instruction·source fidelity를 충분히 보존하는가?

**빌드해 보기 전에는 알 수 없다.** 따라서 bundle 방식은 자체 acceptance check가 필요하다: read-scope probe 통과 후, 동일 내용에 대해 "bundle 기반 review" vs "in-repo review" 결과를 비교해 fidelity 손실을 측정. 그 비교가 나오기 전까지 bundle 기반 finding은 낮은 신뢰도로 취급. **해결된 것이 아니라 열린 위험.**

### 8.5 Codex read-only sandbox의 file-read 범위를 어떻게 동적으로 증명하나?

canary probe fixture: (a) working root, (b) working root의 상위, (c) 두 번째 drive, (d) `%USERPROFILE%`, (e) canary 값을 담은 환경변수 — 각 위치에 표식 파일/값을 두고, `codex exec --sandbox read-only`에 "어떤 canary를 읽을 수 있는지 보고하라"는 prompt로 실행, JSONL의 tool-call 시도와 최종 답변을 검사. **통과 = (a)만 읽힘, working root 밖 tool-call 없음.** 이 probe가 AR-C2a이며 모든 production-source 노출의 gate. `--dangerously-bypass-approvals-and-sandbox`는 사용하지 않는다(Codex §6.5와 동일).

### 8.6 첫 POC가 `run-cycle.ps1`과 완전 분리돼도 Human Message Bus 감소를 측정할 수 있나?

**아니다.** §1.3에서 수용함. standalone report POC는 안전성·result contract·reviewer overhead·finding 품질만 측정한다. Human Message Bus 감소는 AR-C4(guarded Claude→Codex one-round) 지표다. POC acceptance criteria에서 이 항목을 제거한다.

### 8.7 Native UI QA와 경제성 baseline 중 무엇을 hard prerequisite로?

- **Native UI QA baseline** = AR-C3 *구현*의 hard prerequisite (UI 회귀와 runtime 변화 분리, §30.4).
- **경제성 baseline(AR-C1)** = 모든 *savings 주장*과 AR-C4 graduation의 hard prerequisite. AR-C3 *구현*의 선행조건은 아님.
- Round 2–5 계획 작업 + AR-C1 + AR-C2a spike는 Native UI QA와 병행 가능.

이것이 Claude Round 1의 "병행"과 Codex Round 1의 "먼저"를 정합한다.

### 8.8 live `D:\harness-kit`에서 최소 rollback 단위는?

git repo가 아니므로: (a) 사전 `verified` zip snapshot + SHA-256(직전 remediation의 `D:\backup\harness-kit-2026.09.02-verified.zip` 패턴), (b) POC는 **신규 파일만** 추가(`scripts/report/`, `scripts/lib/codex/`, `scripts/smoke/` 신규 항목) + 문서 count 줄 최소 편집 — 기존 runtime 스크립트 무편집, (c) 모든 신규 경로 + hash의 change manifest, (d) rollback = manifest 경로 삭제 + 문서 count 줄 revert. 기존 파일 편집이 필요해지면 POC가 아니며 자체 gate 필요. Codex §4.11/§10.11과 동일.

---

## 9. 수렴 / 발산 지도 (Round 3 통합 계획서 입력)

### 9.1 수렴 — 통합 계획서에 그대로 반영

1. 방향 타당, 착수 전 범위 축소 (`REVISE_BEFORE_INTEGRATED_PLAN`).
2. 첫 slice = `run-cycle.ps1` 미연결 standalone Codex read-only reviewer report lane, default-off, opt-in.
3. Router / 범용 Provider Runtime contract / `claude-invoke-core.ps1` 범용화 = 지금 안 함.
4. HRNS Kotlin = POC에서 무변경. provider 실행은 Kit 내부.
5. Secondary LLM의 **안전 패턴**(evidence manifest, sanitized packet, run-root containment, candidate/audit 분리, deterministic claim verification, `authoritative=false`) 재사용. Ollama/HTTP/GPU/`secondary_llm_*` response kind는 비재사용.
6. 사용자 global config 무변경, 신규 bridge 파일 없음, 자동 commit 없음, POC에서 `WORKFLOW_STATE` writer 변경 없음.
7. `agent_runtime`은 훨씬 뒤(AR-C9)에 `HarnessWorkflowStateDto`의 optional raw extension + sanitized read-only projection으로만. readiness 무관 diagnostic.
8. bounded debate(기본 1 round, compact artifact) = guarded lane phase에서.
9. Ollama = default-off/loopback/advisory 유지, 기존 lane 연결은 후순위, 부적격 host가 무엇도 막지 않음.
10. seam 위치: `claude-invoke-core.ps1` + 세 개의 `*-claude-runner.ps1`, 그 위는 아님 (Codex §3.3, Claude §5 동일).
11. Router 소유자 = Harness Runtime (Codex §5.1 함의, Claude §5 명시 — §9.2 D7에서 명시 확인).
12. Native UI QA는 POC 구현 전 완료. 계획·경제성·spike는 병행.

### 9.2 발산 — Round 3가 해소해야 할 결정

| ID | Claude Round 1 | Codex Round 1 | Round 2 권고 |
|---|---|---|---|
| D1 A/B gating | 수치 존재가 AR-1 gate | 3단 분리, POC 미차단 | **Codex 채택** (§1.2) |
| D2 Native UI QA 순서 | 완전 병행, 무차단 | POC 전 완료 | **Codex 채택 (구현에 한해)**; 계획은 병행 (§1.8, §8.7) |
| D3 Review Packet shape | `context-diet-packet.json` + `git diff` + acceptance | 15필드 파생 packet, packet-v2 미승격 | **thin derived projection**; 모든 필드가 기존 artifact에 매핑되지 않으면 제거. write-scope 필드는 제거(0 고정 아님) (§1.1, §3.2) |
| D4 POC 입력 대상 | 실제 working tree `git diff` 함의 | sanitized bundle / 저민감 fixture 우선 | **Codex 채택** (§7.2) |
| D5 phase namespace/개수 | AR-0..AR-8 (9) | AR-C0..AR-C10 (11) | Codex 구조 채택(AR-C0 baseline freeze + spike/contract 분리), 단일 namespace로 통일, phase 수는 ~8로 압축 검토 (§2.5, §3.3) |
| D6 process supervisor 추출 시점 | Seam 1 "나중" | §5.5 "동일성 확인 후" | 추출은 미루되 **재구현 금지** — 패턴 복사 + AR-C5 TODO (§3.1) |
| D7 Router 소유자 | Harness Runtime 명시 | Harness 내부 함의, 소유권 미명시 | **Harness Runtime으로 명시 확정** |
| D8 계획서 작성자 | Codex(Round 3), Claude는 Round 4 adversarial | (Codex 문서라 미언급) | Codex 작성, Claude Round 4 검토 — 제안서 Appendix C·협업 온보딩 문서와 일치 |

### 9.3 Round 3에서 실제로 수행해야 할 조사 (두 Round 1 모두 미완)

- **Secondary LLM 4개 helper 파일 함수 단위 추출 목록** (§8.3): `build-secondary-llm-evidence-manifest.ps1`, `build-secondary-llm-input-packet.ps1`, `secondary-llm-audit.ps1`, `secondary-llm-claim-evidence.ps1`, `secondary-llm-validator.ps1`를 실제로 열어 재사용 가능 함수 시그니처를 목록화.
- **각 `*-claude-runner.ps1`의 process-supervision 함수 실체 확인** (§2.2): `Start-ClaudeProcess` 존재/시그니처, 세 runner 간 공통 부분.
- **`code-role-sliced-runner.ps1`의 `00-wrapper-input.json` schema 2.0 필드 전수** (§2.3): Review Packet이 참조할 hash·pointer 필드 확정.

### 9.4 Agent Runtime과 분리해 독립 처리할 항목

- `queue.active.*` / `queue.blocked_reason` / `queue.last_updated_at` HRNS requiredness hardening (Codex §1.7). 별도 bounded task. Agent Runtime 변경과 절대 섞지 않는다(원인·회귀 범위 분리).

---

## 10. Round 3 통합 계획서(Codex 소유)에 대한 Claude의 최소 요구

1. wrapper 파일명·함수명·schema 버전을 실파일로 재확인해 정확히 기재(§2.1–§2.3).
2. Phase 0에 "현재 baseline freeze(Native UI QA 완료 + commit/Kit version/문서 상태 고정)"를 명시(§1.8).
3. AR-C2를 spike(관찰) → contract(설계) 순으로 분리하고, sandbox read-scope canary probe를 production-source 노출의 hard gate로 명시(§7.1, §8.5).
4. Review Packet을 "기존 artifact + hash 참조 thin projection"으로 정의하고 write-scope 필드를 제거(§1.1, §3.2).
5. POC 신규 파일 ≤3, `scripts/lib/agent-runtime/` 네임스페이스는 AR-C5로 연기(§3.4).
6. POC acceptance criteria에서 "Human Message Bus 시간 감소"를 제거하고 안전성/result contract/overhead/finding 품질로 한정(§1.3, §8.6).
7. §6 표의 신규 gate 전부와 §9.3 조사 3건을 계획에 포함.
8. `agent_runtime` State extension 진입 시 `HarnessProductionContractTest` + guaranteed-envelope smoke 규율을 명시(§4.4).
9. lane 모델은 `TASK_CLASS_TOKEN_POLICY`에 병합(별도 taxonomy 금지)을 AR-C6 완료 조건으로 명시(§4.3).
10. 이 문서와 `revOpinion_codex01.md`, `revOpinion_claude01.md`의 발산 항목(§9.2)에 대해 항목별 `ACCEPT / REJECT+이유 / DEFER+이유`를 표기.

---

## 이번 Cross Review의 mutation 기록

- HRNS-NOW production source 변경: 없음
- `D:\harness-kit` 변경: 없음
- 사용자 global config / Registry / 외부 workspace 변경: 없음
- `git add`/`commit`/`push`/`amend`/`reset`/`restore`/`checkout`/`stash`/`rebase`/`clean`: 없음
- build / Gradle / Harness smoke 실행: 없음
- live Claude / Codex / Ollama 호출: 없음
- 새로 작성한 파일: `doc/revolution/revOpinion_claude02.md` (사용자 지시)
- 이 문서의 staging/commit 여부는 Git Owner(Codex)와 사용자의 판단이다.
- 다음 단계: Codex가 이 Cross Review와 `revOpinion_claude01.md`를 사용해 Round 3 통합 계획서를 작성한다. 그 전까지 production code 수정 없음.
