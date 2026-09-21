# HRNS-NOW Agent Runtime Evolution — Integrated Plan Adversarial Review (Claude, Final)

- 문서 성격: Final Adversarial Integrated Plan Review (Claude 제출본)
- 역할: Harness Contract Auditor / Security & Failure-path Reviewer / Compatibility Reviewer / Adversarial Architecture Reviewer. Git Owner 아님.
- 감사 대상: `doc/hrns_now_agent_runtime_evolution_plan.md` (970줄, 2026-09-04)
- 대조: `codex_capability_experiment_v3.md`, `claude_capability_review_v3.md`, V1/V2 실험·감사, `revOpinion_{claude,codex}03/04.md`, **실제 `D:\harness-kit` source** (read-only 직접 확인)
- 목적: 이 계획이 실제 `D:\harness-kit` 구현에 들어가기 전에 architecture / security / compatibility / rollback / scope 문제를 찾는 것. 예쁘게 다듬는 것이 아님.
- 경계: capability experiment loop는 종료됐다. 새 experiment를 요구하지 않는다. 사소한 미확정은 implementation / offline test / manual-live / graduation / documentation / deferred-architecture gate로 분류한다.
- 상태: 분석 단계. 이 문서 저장 외 mutation 0. `git add`/`commit`/`push` 없음. live provider 호출 0. `Read` + read-only `grep`/`find`/`sed`/`git status`만 사용.

---

# 1. Final Decision

## `PLAN_REQUIRES_REVISION`

**architecture / security / compatibility / rollback BLOCKER는 0**이다. core architecture(§2)는 V3 evidence와 정확히 일치하고, provider가 production repo를 working root로 얻거나 absolute source path가 누출되거나 provider가 Git scope를 재결정하거나 accepted finding이 workflow truth로 승격되는 경로는 **발견되지 않았다**. scope creep도 없다(§6 non-goals + §26.1 disposition이 전 항목을 커버).

그러나 **HIGH 1건 + MEDIUM 6건**이 있고, 그중 HIGH는 계획서 자체를 수정해야 하는 내부 불일치다:

> **§18의 `scripts/lib/secondary-llm/**` "변경 0" 제외 주장이, 계획이 smoke 6개를 추가하는 것과 양립하지 않는다.** `smoke-secondary-llm-docs-scan.ps1`(line 162, 169, 179–186)이 live `scripts/smoke/*.ps1` 개수를 `secondary-llm-docs-calibration.ps1`의 **하드코딩 상수**(`scripts_smoke_total=95`, `automatic_offline_unique=84`, `manual_live=11`, `secondary_llm_smoke_total=36`)와 대조하고, SMOKE_INDEX.md 텍스트가 그 상수값을 명시하는지도 검사한다. smoke를 추가하면 이 상수(→ `scripts/lib/secondary-llm/`)와 `$SmokeTotal` 기본값(→ `scripts/smoke/smoke-secondary-llm-docs-scan.ps1`)을 반드시 갱신해야 한다. 이는 audit 보고서 §11(2026-09-02)에 Codex가 87/76→95/84로 실제 수행한 편집과 동일한 종류다. 계획서 §22의 "live inventory에서 recompute"는 이 gate의 실제 동작(사람이 기대 상수를 갱신)과 다르다.

프로토콜상 계획서 자체를 수정해야 하는 HIGH가 있으므로 `PLAN_APPROVED_FOR_EXTERNAL_CONFIRM`이 아니다. `PLAN_BLOCKED`도 아닌 이유: 요구되는 수정이 **targeted**(§18 제외목록 carve-out, §26.1 REJECT/DEFER 재라벨, §4/§26.1 invariant 문구 분리, §9 event allowlist 명시, §27 재분류, §2 scratch-root 재프레이밍, §8.1 envelope shape 보강)이고 architecture를 바꾸지 않으며, 계획서의 §18/§28 stop-and-reapprove gate가 최악의 경우에도 안전망이 된다.

수정 후 → 외부/사용자 확인 → 명시적 production implementation 승인 순서를 유지한다.

---

# 2. Architecture Findings

## 2.1 Core architecture는 V3 evidence와 일치 — `ACCEPT`

계획 §2의 정본 흐름을 V3와 대조한 결과:

| 계획 §2 단계 | V3 근거 | 판정 |
|---|---|---|
| Parent-owned deterministic evidence | V3 §3.1/§6, 10,473-byte inline로 review 완료 | ✓ |
| bounded strict UTF-8 no-BOM raw-byte stdin | V3 §8: `UTF8Encoding(false,true)` + `BaseStream` raw byte + BOM byte 검사 | ✓ (§8.2가 `StandardInputEncoding` 미사용 명시 — PS 5.1 .NET Framework 제약 반영) |
| ASCII disposable working root | V3 §3.1: `...\hrns-codex-cap-v3-...` ASCII-only | ✓ (§2가 overlap/symlink/empty/AGENTS-free 조건 명시) |
| version/feature preflight | V3 §3.2: 26 feature effective-false 확인 | ✓ (§9 네 단계 preflight) |
| generic tool-disabled Codex exec | V3 §4: tool event 0 × 3 | ✓ |
| unexpected tool-event fail-closed | V3 §4: router denial → fail-closed | ✓ (§15.1) |
| last completed structured result | V3 §7: placeholder 첫 message 미선택 | ✓ (§10, §20.1) |
| deterministic finding audit | V2 6/6 거짓 reject + V3 3×3 accept | ✓ (§11, "semantic truth 미증명" 명시) |
| Human-visible non-authoritative | invariant 2, §10 `authoritative=false` | ✓ |

**5개 공격 지점 전수 확인:**

1. **production repo → provider working root 은닉 경로**: 없음. 진입점(§5)은 `-ProjectRoot <local-git-root>`를 **Parent가** 읽고, provider는 `-C <scratch>` + 16KB inline stdin만 본다(§5 "provider가 이 파일이나 `ProjectRoot`를 직접 보지 않는다"). §2 `-C` 조건 + §20.3 overlap 거부 smoke + §28 재확인. **차단됨.**
2. **absolute source path 누출**: 없음. invariant 9, §8.1 "local locator와 절대 경로를 제거", §8.3, §20.1 "absolute source path가 envelope/result에 들어오면 거부". **차단됨.**
3. **provider가 Git scope 재결정**: 없음. invariant 13, §12 "초기 authoritative review scope는 Parent가 explicit하게 생성한 changed-file manifest다. `codex review --uncommitted`의 자체 범위 해석은 사용하지 않는다." **차단됨.**
4. **provider prose를 event fail-closed보다 신뢰**: 없음. invariant 12, §10 "provider exit code 0, schema 통과 또는 finding audit 통과 어느 하나만으로 review가 진실이라고 간주하지 않는다", §11 hard gate. **차단됨.** (단 §2.4 M4 참고 — 허용 event 목록이 미명시.)
5. **accepted finding → authoritative workflow truth**: 없음. invariant 2/3/4, §14 `completed` status가 State 전이 없음, §26.1 auto commit/State `agent_runtime` REJECT, §23 "State/write로 전파되지 않음". **차단됨.**

## 2.2 [MEDIUM] M3 — `single-provider` invariant가 현재 사실과 architecture invariant를 혼동

§4 invariant 14 / §26.1:

```text
single paid provider:
  baseline workflow는 어느 하나의 유료 frontier provider만으로도 운용 가능
```

**현재 사실이 아니다.** Kit의 planning/code/doc runner는 Claude CLI 전용이다(`scripts/lib/claude/claude-invoke-core.ps1`, `*-claude-runner.ps1`). baseline은 **Claude 단독**으로만 운용 가능하며 "어느 하나의 유료 provider"로는 불가하다. 보호하려는 요구사항은 "baseline이 복수 유료 frontier 동시 구독을 **필수로 요구하지 않는다**"이지 "임의의 단일 provider로 대체 가능"이 아니다.

**수정 제안**: 두 줄로 분리.

```text
현재 사실: baseline workflow는 Claude 단독으로 운용 가능하며 다른 유료 provider를 요구하지 않는다.
장기 불변식: baseline은 복수 유료 frontier provider의 동시 구독을 필수로 요구해서는 안 된다.
```

## 2.3 [MEDIUM] M2 — §26.1 `REJECT`가 영구 거부와 "이번 POC 거부"를 혼용

§26.1 `State agent_runtime → REJECT`가 §26.2 "나중에만 검토할 것 ... optional diagnostic State extension 또는 HRNS projection"과 **모순**된다. 영구 `REJECT`이면 §26.2 재검토 목록에 있으면 안 되고, `REJECT_FOR_THIS_POC` / `DEFER`이면 §26.1이 그렇게 표기해야 한다. revOpinion 03/04는 `agent_runtime`을 "지금은 아님, 안정화 후 재판정" = DEFER로 다뤘다.

동일 문제: `Codex write lane → REJECT`("별도 initiative 필요" = REJECT_FOR_THIS_POC), `Ollama runtime integration → REJECT`(revOpinion은 "기존 lane을 normalized reader로 연결" = 장기 DEFER). `Native review --uncommitted`, `Bundle/filesystem review`, `Multi-provider required subscription`, `Automatic debate`, `Auto commit`은 진짜 영구 설계 거부 = `REJECT` 유지.

**수정 제안**: §26.1을 3-value로 — `REJECT`(영구 설계 결정) / `REJECT_FOR_THIS_POC`(별도 initiative에서 재검토) / `DEFER`(계획된 후속). `agent_runtime`은 `DEFER` 또는 `REJECT_FOR_THIS_POC`.

## 2.4 [MEDIUM] M4 — 허용 JSONL event 목록이 계획서에 없음

§15.1은 **금지** event(`command_execution`, `mcp_tool_call`, `web_search`, `file_change`, filesystem/shell/function/computer/browser/image tool, allowlist 밖 tool)를 열거하고 "allowlist에 없는 tool 또는 side-effect event → 폐기"라 하지만, **allowlist 자체를 명시하지 않는다**. §20.2가 "허용/금지 JSONL event table-driven matrix"로 smoke에 위임한다. V3 §4 관측상 정상 항목은 `agent_message`와 `error`(known code-mode diagnostic)뿐이었다.

**수정 제안**: §9 versioned invocation spec 또는 §15.1에 허용 event allowlist를 명시 — `agent_message`, `turn.completed`, session/init metadata, versioned known-diagnostic `error` subset. 그 밖은 전부 `contract_invalid`. smoke만이 아니라 계약에 pin해야 CLI 업그레이드 시 새 event가 fail-closed된다.

## 2.5 나머지 architecture — `ACCEPT`

- process supervisor(§13): Claude launcher의 gap 12종(no-shell/no-window/raw stdin/concurrent drain/explicit encoding/timeout/cancel/tree-kill/residual/byte-cap/truncation/typed result)을 전부 커버. 실제 `codex.cmd → node` 트리 검증은 §21 #6 manual/live gate + §19 step 9(implementation 중, release 전) — **architecture blocker 아님**(프로토콜 §12 지침 준수).
- event/result parser(§10, §14): exit 0 + malformed/no-turn-completed/multi-terminal/placeholder-first/schema-invalid/unexpected-event/known-error/unknown-error/truncation/stderr-limit/decode-error/nonzero — **fail-open 경로 없음.** §10 cross-field 규칙 견고.
- finding audit(§11): V2/V3 capability를 과대해석하지 않음. "accept해도 semantic truth 미증명", "비권위 권고" 프레이밍이 §1/§10/§11/§23 전반에 일관.

---

# 3. Security Findings

## 3.1 Security 설계 — `ACCEPT`

| 공격 지점 | 계획 대응 | 판정 |
|---|---|---|
| `--sandbox read-only`만 안전 경계로 취급 | §15 "OS/ACL sandbox를 선행 requirement로 두지 않지만 아래 방어는 모두 필수" + §15.4 "prompt의 '도구를 쓰지 말라'는 보안 통제로 계산하지 않는다" | ✓ |
| feature disable + raw event audit가 hard gate | §9 preflight #3 + §15.1 event 폐기 | ✓ |
| CLI upgrade = revalidation trigger | §7, §9, invariant 11 | ✓ |
| unknown feature/event/error fail-closed | §9(`unified_exec`는 이름 아닌 event 감사), §15.1(unknown error → 보수적 `contract_invalid`/`failed`) | ✓ |
| raw JSONL/stderr retention default-off | §16 "기본 정책은 raw JSONL 비보존" | ✓ |
| redaction이 구현 gate에 포함 | §16 redaction 목록, §18.1 audit module, §20.5 | ✓ |
| scratch root가 production/Kit/HRNS/project와 overlap | §2, §20.3, §28 재확인 | ✓ |
| broad recursive cleanup | §2/§24 "broad recursive root 삭제 금지", §19/§24 기존 `diag-planning-hang`/`manual-o7-...` 보호 | ✓ |
| canary/release gate | §15.3 negative-read chain(marker 미제공, tool event 0 + marker match 0 + hash 불변), "credential 내용은 절대 읽거나 출력하지 않는다" | ✓ |

`docs/SECURITY_MODEL.md` §8이 이미 "protection against external provider risk"를 **미보장** 항목으로 명시 → Codex reviewer 추가는 기존 모델 경계 안. §7 "Encoding as a Security and Trust Issue"와 계획의 strict no-BOM 계약이 정합.

## 3.2 [MEDIUM] M1 — 기본 `-ScratchRoot`(system temp)가 한글 profile 사용자 대다수에서 실패

계획 §2: "시스템 temp가 [ASCII] 조건을 만족할 때만 기본 후보가 될 수 있다. 그렇지 않으면 operator가 `-ScratchRoot`를 명시해야 하며 ... 호출 전에 차단한다."

HRNS-NOW/Harness의 실제 사용자층은 한글 Windows profile을 쓴다(`C:\Users\홍길동\AppData\Local\Temp` → **non-ASCII**). V1/V2/V3 실험은 `C:\Users\Public\Documents\ESTsoft\CreatorTemp\`(ASCII)를 썼지만 이는 일반 사용자 기본 temp가 아니다. 따라서 **대다수 사용자에게 명시적 `-ScratchRoot`가 사실상 필수**이고, "예외"가 아니라 "일반 경로"다.

**수정 제안**: §2/§5에 "한글/공백 profile에서는 `-ScratchRoot`가 사실상 필수"임을 명시하고, 유효 ASCII scratch leaf를 만드는 helper 또는 정확한 사용자 가이드(§18.3 `CODEX_READONLY_REVIEWER.md`)를 제공. invariant 8/§20.3 검증은 유지.

## 3.3 [LOW] L4 — child environment "필요한 최소값"이 미정의

§15.4 "child environment는 필요한 최소값으로 구성하고 task-specific secret env를 제거하되 auth plumbing을 임의 파괴하지 않는다." 최소 env allowlist(`PATH`, `CODEX_HOME`, `SystemRoot`, `TEMP` 등)가 미명시. **수정 제안**: §27 owner decision 또는 §19 implementation gate에 "child env allowlist" 항목 추가.

---

# 4. Compatibility Findings

## 4.1 Compatibility 경계 — `ACCEPT` (source-verified)

계획 §17 표의 "0 변경" 주장을 실제 Kit source로 검증:

| Surface | 계획 주장 | source 확인 | 판정 |
|---|---|---|---|
| `scripts/run-cycle.ps1` | 0 | standalone 진입점은 `run-cycle.ps1`이 호출 안 함(run-cycle은 `invoke-{planning-cycle,code-execution,document-execution}.ps1`만 dispatch) | ✓ achievable |
| Claude runner / `claude-invoke-core.ps1` | 0 | Codex 파일이 이를 import할 이유 없음(§13 "Claude launcher 복사 금지") | ✓ |
| `WORKFLOW_STATE` writer/schema | 0 | reviewer는 State를 읽지도 쓰지도 않음(invariant 4) | ✓ |
| daily 4-file / bridge 3-file | 0 | 미접촉 | ✓ |
| HRNS-NOW source | 0 | §25 "HRNS-NOW source 전체" 제외 | ✓ |
| `doctor.ps1` | 0 | **source 확인**: `$requiredKitFiles`(정적 리스트, line 515) + `harness-runtime-dependency-inventory`(run-cycle deps) 외 `scripts/lib`/`scripts/report` 재귀 스캔 없음(line 692의 유일한 `-Recurse`는 target project 대상). Codex 파일을 정적 리스트/inventory에 안 넣으면 `doctor.ps1` 0 변경 성립 | ✓ achievable |
| `harness-runtime-dependency-inventory.ps1` | 0 | run-cycle이 로드하지 않는 standalone이므로 미포함이 맞음. `smoke-run-cycle-required-dependency-inventory.ps1` lockstep 무영향 | ✓ |
| automatic offline suite live calls = 0 | 0 | §20 hard invariant, §22 gate | ✓ |
| Codex 부재 → Harness 정상 | — | **source 확인**: `smoke-clean-root-onboarding.ps1`이 fresh clean Kit에서 daily 4-file + bridge 3 + `doctor_overall_is_ok`를 검사. Codex 파일이 순수 additive이고 정적 리스트에 없으면 이 smoke가 Codex 유무와 무관하게 PASS | ✓ testable |

## 4.2 [HIGH] H1 — `scripts/lib/secondary-llm/**` 제외 주장이 smoke 추가와 양립 불가

**source로 확정** (`scripts/lib/secondary-llm/secondary-llm-docs-scan.ps1` + `secondary-llm-docs-calibration.ps1` + `scripts/smoke/smoke-secondary-llm-docs-scan.ps1`):

- `secondary-llm-docs-calibration.ps1:71` `Get-SecondaryLlmDocsMismatchExpectedSmokeInventory` = **하드코딩** `{ scripts_smoke_total=95; secondary_llm_smoke_total=36; automatic_offline_unique=84; manual_live=11; scripts_root_smoke_total=0 }`.
- `secondary-llm-docs-scan.ps1:162` `if($smokeFiles.Count -ne [int]$expected.scripts_smoke_total)` → **live `scripts/smoke/*.ps1` 개수 vs 상수** 대조, 불일치 시 `error` finding.
- `secondary-llm-docs-scan.ps1:179–186` → `SMOKE_INDEX.md` 텍스트가 상수값("Current smoke inventory count is 95" 등)을 명시하는지 regex 검사, 불일치 시 `error`/`warning`.
- `smoke-secondary-llm-docs-scan.ps1:37` `[int]$SmokeTotal = 95` 기본값.
- `smoke-smoke-index-consistency.ps1`도 `New-SecondaryLlmDocsMismatchCalibration`을 소비.

계획이 smoke **6개**(5 offline `smoke-codex-readonly-review-*.ps1` + 1 `smoke-codex-readonly-review-live-manual.ps1`)를 `scripts/smoke/`에 추가하면:
- live count 95 → **101**, `automatic_offline_unique` 84 → 89, `manual_live` 11 → 12
- `secondary-llm-docs-scan.ps1`이 `error` findings 생성 → **`smoke-secondary-llm-docs-scan.ps1` FAIL**
- 통과시키려면 **`secondary-llm-docs-calibration.ps1`의 상수 3개 + `smoke-secondary-llm-docs-scan.ps1`의 `$SmokeTotal` 기본값 + SMOKE_INDEX/MAP/ROADMAP 텍스트**를 갱신해야 함.

**계획 §18(line 687) + §26.1(line 912)은 `scripts/lib/secondary-llm/**`를 명시적 "변경 0 / DO NOT CHANGE"로 열거한다.** 계획 §18.3의 수정 문서 목록에도 `secondary-llm-docs-calibration.ps1` / `smoke-secondary-llm-docs-scan.ps1`이 없다. 계획 §22 "live inventory에서 recompute"는 이 gate가 **사람이 기대 상수를 수동 갱신**하는 방식이라는 실제 동작과 다르다. audit 보고서 §11(2026-09-02)에 Codex가 정확히 이 함수를 87/76→95/84로 편집한 선례가 있다.

**이것은 계획서 내부 불일치다** (§18 제외 vs §18.3 문서 갱신 vs 실제 gate 메커니즘). 구현자가 §18의 벽에 부딪혀 stop-and-reapprove하거나, 제외를 어기게 된다.

**수정 제안 (택1)**:
1. §18.3에 `scripts/lib/secondary-llm/secondary-llm-docs-calibration.ps1`(smoke-count 상수만) + `scripts/smoke/smoke-secondary-llm-docs-scan.ps1`(`$SmokeTotal` 기본값만)을 **narrow count-reconciliation 예외**로 명시 추가하고, §18 제외목록에서 `scripts/lib/secondary-llm/**`를 "smoke-count 상수 reconciliation 제외"로 한정. 이 편집은 semantically inert(정수 3개 + 기본값 + 문서 텍스트)이고 documented precedent가 있음.
2. 또는 `smoke-smoke-index-consistency.ps1` 계열을 live-compute로 리팩터(→ `scripts/smoke/` 변경, `scripts/lib/secondary-llm/` 불변) — 더 큰 변경, 별도 검토 필요.
3. §28에 entry 항목 추가: "smoke-index consistency 메커니즘 검토 완료 — live-compute 확인 OR narrow count-only 예외 승인."

## 4.3 [LOW] L2 — `scripts/` 아래 첫 `.json` 파일

`find scripts -name '*.json'` = **0건**. `codex-readonly-review-result.schema.json`(§18.1)이 Kit 전체에서 `scripts/` 아래 첫 `.json`. `release-package-hygiene.ps1` 제외 패턴(`*.rej`/`*.pyc`/`__pycache__`/`scratch/**`/`logs/**`/`.docker-config/**`/`*.gguf`/`ollama*.exe`/`*.proof.json`/`hardware-capability.result.json`/`.env`/`.pem`/`.pfx`/`.key`/`credentials.*`)에 **매치 안 됨** → 제외되지 않음. `scripts/lib`는 protected 디렉터리(line 64, 157)이므로 `scripts/lib/codex/*.schema.json`은 protected-path 커버 대상. 계획 §22 line 814 "신규 ... schema ... 빠짐없이 존재"가 존재 검사는 하나, **"hygiene 제외 안 됨" + "protected tree 포함"을 명시 assert**하는 smoke case를 §20.5 또는 §18.2 security smoke에 추가 권장.

## 4.4 [LOW] L6 — `TASK_CLASS_TOKEN_POLICY.md` §16이 Secondary-LLM 전용

§16 "Secondary LLM Advisory Extension"은 "Local LLM output", "Ollama" 전용 텍스트. 계획 §18.3의 "기존 review 성격 task에 optional provider와 usage 측정 원칙만 연결"은 **새 §16.x 하위 섹션 또는 새 §**이 필요(§16 본문에 Codex를 섞으면 lane 혼동). 계획이 우려한 "단일 enum 병합"은 피하고 있음(§18.3 명시) — 배치만 명확화.

---

# 5. Scope / File Graph Findings

## 5.1 Scope creep 공격 — 없음

§5 (protocol)의 12개 은닉 항목(generic Provider Runtime / Router / provider interface framework / Claude runner refactor / common process runtime extraction / `run-cycle.ps1` hook / State field / HRNS DTO·UI / Codex write / Ollama / auto debate / Git auto mutation) 전부 §6 Non-goals에 명시 제외 + §26.1 disposition. §13 "공통 runtime으로 추출하지 않는다". §18.1 신규 파일은 전부 `scripts/lib/codex/` + `scripts/report/` + `prompts/` + `scripts/smoke/` + `docs/`. **은닉 scope creep 없음.**

## 5.2 File Change Graph — Kit convention 대조

| 계획 파일 | Kit analog | 판정 |
|---|---|---|
| `scripts/report/invoke-codex-readonly-review.ps1` | `scripts/report/invoke-secondary-llm-advisory.ps1` | **REQUIRED** — 네이밍/위치 일치 |
| `scripts/lib/codex/codex-readonly-review-{contract,evidence,process,audit}.ps1` | `scripts/lib/secondary-llm/secondary-llm-*.ps1` (dash 네이밍), 신규 subdir는 `claude/code/doc/plan/release/secondary-llm/smoke` 패턴과 일치 | **REQUIRED** — 일치 |
| `scripts/lib/codex/codex-readonly-review-result.schema.json` | (없음 — Kit 최초 `.json` under `scripts/`) | **REQUIRED** — L2 참고 |
| `prompts/codex-readonly-review.md` | `prompts/` = `append-{plan,execute-code,execute-doc}-system.md` **3개뿐, 전부 Claude append addenda** | **REQUIRED but L1** — 네이밍 관례 위반 |
| 5 offline smoke (`smoke-codex-readonly-review-*.ps1`) | `smoke-secondary-llm-*.ps1` | **REQUIRED** — regression closure(contract/audit/process/git/security)에 각각 필요, over-scoped 아님 |
| 1 live/manual smoke | `*-live-manual.ps1` | **REQUIRED** — automatic array 제외 명시(§18.2), 일치 |
| `docs/CODEX_READONLY_REVIEWER.md` (신규) | `docs/SECONDARY_LLM_LANE.md` | **REQUIRED** — 일치 |
| `SMOKE_INDEX.md` | — | **REQUIRED** (H1) |
| `SECURITY_MODEL.md` | §8이 이미 external provider risk 미보장 명시 | **REQUIRED** — additive subsection, 저위험 |
| `TASK_CLASS_TOKEN_POLICY.md` | §16 Secondary-LLM 전용 | **REQUIRED but L6** — 새 subsection |
| `HARNESS_KIT_MAP.en/ko.md`, `ROADMAP.md`, `README.md` | — | **REQUIRED** (H1 count consistency + docs-mismatch map-presence 검사: `secondary-llm-docs-scan.ps1:209` map presence) |
| `kit-version.json` (조건부) | — | **REQUIRED but release-time만** (§18.4) |

**OVER-SCOPED 파일: 없음.** OPTIONAL로 낮출 파일: 없음(5 offline smoke는 각각 별개 regression 표면). 임의 "파일 수 제한"은 만들지 않음 — 프로토콜 §6 준수.

## 5.3 [LOW] L1 — `prompts/codex-readonly-review.md` 네이밍

`prompts/`의 3개 파일은 전부 `append-<phase>-system.md`(Claude `--append-system-prompt-file` 자산). Codex reviewer의 fixed instruction을 여기 두면 (a) 네이밍 패턴 이탈, (b) Claude/Codex prompt 자산 혼재. `prompts/`는 `release-package-hygiene.ps1` protected 디렉터리이므로 위치 자체는 안전.

**수정 제안**: `prompts/codex-readonly-review-system.md`로 네이밍 일관화, 또는 `scripts/lib/codex/` 아래 두고 `prompts/`는 Claude append 전용으로 유지하는 근거를 §18.1에 명시.

---

# 6. Input / Git / TOCTOU Findings

## 6.1 Input contract — `ACCEPT` (V3 정합)

- 16KB cap이 instruction + envelope 전체에 적용(§8.2) ✓ 일관
- oversize → 자동 truncation 안 함, `blocked/inline_input_too_large` 또는 Parent split(§8.2) ✓ (V3 §8: 16,385 synthetic → `inline_input_too_large`)
- SHA-256: 계산 후 "process start 직전에 다시 대조"(§8.2) + §12 snapshot sequence의 "invocation 직전 재검증" + "finding audit 직전 재검증" → **두 recheck 지점** ✓ TOCTOU 방어 적절
- BOM 검사가 한 정본: `codex-readonly-review-contract.ps1`이 소유(§18.1) ✓
- `StandardInputEncoding` 미사용 + `BaseStream` raw byte(§8.2, §13) — V3 §2/§8이 발견한 PS 5.1 .NET Framework 제약과 정합 ✓
- logical path의 filesystem 재해석: §8.3 "provider 단계에서 filesystem resolve/normalize하지 않는다" + invariant 10 + §8.3 "보안 preflight에서만 absolute/traversal/NUL/drive-qualified 거부" ✓

## 6.2 [MEDIUM] M6 — envelope `files[]` schema가 staged+unstaged same-path를 표현 못 함

§8.1 provider envelope의 `files[]`는 `sha256` + `base_sha256?` 각 1개. §12는 "same path가 staged+unstaged일 때 source basis 보존"(§20.3도)이라 하지만, 파일이 `base → staged → worktree` 3-상태일 때 (a) provider가 보는 "current source"는 무엇인지(worktree?), (b) `declared_changed_lines[]`가 두 diff에 걸칠 때 어느 basis 기준인지, (c) `sha256` 하나로 어떻게 두 상태를 나타내는지 **데이터 shape가 미정의**.

**수정 제안**: §8.1 `files[]`에 `staged_sha256?` / `worktree_sha256?` 또는 같은 `logical_path`에 대한 복수 entry(source basis 태그 포함)를 명시. §27 owner decision으로 옮겨도 되지만, envelope schema 자체를 고쳐야 하므로 계획서 수정.

## 6.3 Git preflight — `ACCEPT`

- fail-closed 목록(§12): binary/NUL, submodule/gitlink, LFS, sparse/partial, malformed porcelain, traversal/absolute/reserved, HEAD/index/worktree drift, TOCTOU, reparse/junction/symlink escape — **fail-open 경로 미발견**
- 명령: `git status --porcelain=v2 -z`, `git diff --raw -z`, `git diff --cached --raw -z`, `git ls-files --others --exclude-standard -z`(§12) — NUL-safe 정본 ✓ (claude04 RC1과 정합)
- rename/copy: lineage + hash 모두 명시된 경우만 허용, 아니면 block. §27 #5 권장값 = fail-closed ✓ 안전 기본
- concurrent modification → `blocked/scope_drift`(§12) ✓
- [LOW] L5: §12가 base/current content **bytes 캡처 명령**(`git cat-file` / `git show :FILE` / `git show HEAD:FILE`)을 명명 안 함 — implementation detail

---

# 7. Process / Failure Findings

## 7.1 Process supervisor — `ACCEPT`

§13 필수 계약이 Claude launcher gap을 전부 커버(§2.5 참고). 상한(§13 표): stdin 16KB / stdout 256KB / stderr 64KB / timeout 120s — "초기 후보, owner 확정, test constant와 문서를 한 정본에서" ✓ 적절.

- [LOW] L3: §13 "`StandardOutputEncoding`/`StandardErrorEncoding`을 strict UTF-8로 **설정 가능한 환경에서** 설정" — 불필요한 hedge. PS 5.1 = .NET Framework 4.x는 `ProcessStartInfo.StandardOutputEncoding`/`StandardErrorEncoding`을 **지원한다**(.NET 4.5+). 미지원인 것은 `StandardInputEncoding`뿐. 무조건 설정으로 수정 권장.
- 실제 `codex.cmd → node` 트리 tree-kill/residual/no-window: §21 #6/#7 manual/live gate + §19 step 9(release 전) → **architecture blocker 아님.** §13 "실행 전 process snapshot, parent PID/creation time"으로 기존 세션 codex/node 오인 방지 ✓ (V1 §8이 관측한 미귀속 residual 대응).

## 7.2 Failure taxonomy — `ACCEPT`

§14 top-level status(`completed`/`unavailable`/`blocked`/`timed_out`/`cancelled`/`usage_limited`/`contract_invalid`/`failed`)가 provider-neutral. "실제 auth/quota/network 메시지를 고정 문자열 하나로 추측하지 않는다 ... 초기 live gate에서 exit code, JSONL item, stderr shape를 redacted fixture로 수집하여 classifier를 고정한다"(§14) + §21 #11("안전하게 유발할 수 없으면 `UNVERIFIED`로 두되 workflow 연결은 금지") → **classifier 수집이 implementation-time gate, workflow 연결 차단이 명시.** 적절.

---

# 8. Result / Finding Audit Findings

## 8.1 Finding audit 경계 일관성 — `ACCEPT`

Audit가 검증: anchor / file·path·hash / line / scope / evidence / test claim (§11 표). 검증 안 함: semantic truth (§11 "Audit가 accept한 finding도 semantic truth 전체가 증명된 것은 아니다").

이 경계가 §1("어느 하나만으로 진실 간주 안 함"), §10(`authoritative=false`), §11, §23("unauditable finding 자동 수용 0", "clean sample 오탐이 State/write로 전파되지 않음")에 **일관**. §11 offline 8종 false-finding + hash tamper가 V2(6/6 + tamper) / V3(3×3 accept)와 정합 — **과대해석 없음.**

## 8.2 accepted finding → apply/commit/State — 없음

§6 non-goals, invariant 2/3/4, §14 `completed`가 State 전이 없음, §23 safety graduation "State/write로 전파되지 않음", §26.1 auto commit/State field REJECT. **BLOCKER 경로 미발견.**

---

# 9. Provider Independence Findings

§8 (protocol) 분석 결과 = §2.2 M3. 두 문장(`single paid provider ... 운용 가능` / `Codex-only baseline execution ... aspiration`)은 **논리적으로 모순 아님**(둘 다 "복수 동시 구독 불필요"와 정합) — 그러나 첫 문장이 **현재 사실을 과장**(baseline은 Claude 전용). 이번 POC에서 Codex-only execution을 구현 대상으로 승격하지 않는 것은 §6/§26.1에서 확인됨. **M3 문구 수정 필요, architecture 이슈 아님.**

---

# 10. Rollback Findings

## 10.1 Rollback 설계 — `ACCEPT`

`D:\harness-kit` = Git 저장소 아님(확인). §24 file-level rollback:
- verified clean zip + SHA-256 + inventory/hash(§19 step 2, §24, §28) ✓
- exact add/modify manifest(§18, §19 step 3) ✓
- 원본 hash·bytes 보존(§24) ✓
- 기존 scratch/log residue 분리(§19 note, §24 `diag-planning-hang`/`manual-o7-...` 보호) ✓
- exact new-file 삭제 + exact modified-file 복원(§24 step 3–4) ✓
- broad delete 금지(§24) ✓
- 복원 후 regression(§24 step 5–6) ✓
- **scope escape gate**: §18 "제외 파일 변경 필요 시 ... 중단한 뒤 plan 재승인", §28 final para — **명시적이고 충분** ✓

## 10.2 [HIGH 연쇄] H1이 rollback 완전성에 파급

§24 step 4는 "§18.3/18.4의 수정 파일만 snapshot의 exact bytes로 복원"이다. H1이 요구하는 `secondary-llm-docs-calibration.ps1` + `smoke-secondary-llm-docs-scan.ps1` 편집이 §18.3에 없으면, 이 파일들은 **change manifest에도 rollback 대상에도 안 들어가** rollback 후 stale 상태로 남는다. → H1 수정 시 §18.3 + §24 rollback 목록에 함께 반영해야 함.

---

# 11. Owner Decision Classification (protocol §18)

계획 §27의 9개 owner decision을 재분류:

| # | 항목 | 계획 §27 표기 | 재분류 | 근거 |
|---|---|---|---|---|
| 1 | 16KB input cap 값 | "production 시작 전 고정" | **implementation-time** | "bounded"는 불변(§27), 숫자는 smoke 중 조정 가능 |
| 2 | stdout 256KB / stderr 64KB / timeout 120s | "production 시작 전 고정" | **implementation-time** | 동일 |
| 3 | `-OutputPath` 허용 vs stdout-only | "production 시작 전 고정" | **implementation-time (safe default 있음)** | §27이 stdout-only 권장 |
| 4 | npm global bin fallback | "production 시작 전 고정" | **implementation-time (safe default 있음)** | §27이 explicit/PATH 우선 권장 |
| 5 | rename/copy 지원 vs fail-closed | "production 시작 전 고정" | **implementation-time (safe default 있음)** | §27이 fail-closed 권장 |
| 6 | n≥5 sample + 품질/비용 threshold | "production 시작 전 고정" | **GRADUATION GATE** | §23. default-on/workflow 연결 전에만 필요, 구현 시작 전 불필요 |
| 7 | manual/live 호출 승인 횟수 + fixture | "production 시작 전 고정" | **PRODUCTION-ENTRY** | §28 checklist 항목, manual/live gate 실행 전 필수 |
| 8 | 기존 scratch 2 디렉터리 소유/보존/정리 | "production 시작 전 고정" | **PRODUCTION-ENTRY** | §28 checklist 항목 |
| 9 | Kit version 번호 + archive 위치 | "production 시작 전 고정" | **RELEASE GATE** | §18.4 "release approval 이후". 구현 시작과 무관 |

**수정 제안**: §27 서두 문구를 "아래 값의 분류는 다음과 같다"로 바꾸고 항목별로 (implementation-time / production-entry / graduation / release)를 표기. 프로토콜 §18 "불필요하게 모든 결정을 production-entry blocker로 만들지 않는다" 준수. **진짜 production-entry blocker는 #7, #8 두 개.** #1–5는 safe default가 있어 "기본값 확정" 수준.

---

# 12. Production Entry Criteria Review (protocol §19)

계획 §28의 15개 checkbox 평가:

## 12.1 너무 약한가? — 아니다

15개가 adversarial review + owner 승인 + scope 고정 + exact file manifest + inventory/hash + verified clean zip(**실제로 열어 검증**) + residue 분리 + rollback 절차 + CLI pin/resolver/feature matrix + caps/retention + manual/live provenance + no-production-root 재확인 + offline-0-calls 구조 + agent overlap + git-boundary 재확인을 커버. **unsafe implementation을 허용할 구멍 미발견.** "재확인" 항목들은 test가 아니라 재확인이라 다소 soft하지만 §22 regression gate와 결합하면 충분.

## 12.2 너무 강한가 (graduation 항목 섞임)? — 아니다

§28은 §23의 n≥5 quality를 **포함하지 않는다** — 올바르게 production-ENTRY로 한정. version bump는 checkbox 아님. ✓

## 12.3 [HIGH 연쇄] 누락 항목

§28에 **"smoke-index consistency 메커니즘 검토 완료 — `secondary-llm-docs-scan.ps1`/calibration이 live-compute인지 상수인지 확인, 상수면 narrow count-only 예외 승인"** checkbox가 없다. H1 때문에 이것이 entry criterion이어야 한다.

## 12.4 최소 entry gate 판정

실제 `D:\harness-kit` 구현 시작 가능한 최소 gate:

1. **이 adversarial review의 필수 수정(H1 + M1–M6)이 계획서에 반영됨**
2. 사용자/외부 owner가 수정된 계획을 확인하고 production implementation을 명시 승인
3. §18 exact add/modify/exclude manifest(H1 carve-out 포함) owner 승인
4. verified clean source zip + SHA-256 생성 + 실제로 열어 inventory 검증
5. `codex-cli 0.153.2` pin + resolver 순서 + required option + expected-feature matrix 확정
6. input/output/time cap + retention policy 확정 (§27 #1–3, safe default 채택 가능)
7. manual/live 호출 승인 provenance + 상한 + disposable fixture 기록 (§27 #7)
8. 기존 scratch residue 소유/정리 권한 확인 (§27 #8)
9. rollback exact-file 절차(H1 파급 포함) 검토
10. automatic/offline suite live-0 test 구조 확정
11. 동시 작업 agent write set overlap 없음 확인

§28의 나머지("재확인" 항목)는 belt-and-suspenders로 유지.

---

# 13. Required Plan Corrections

계획서를 **반드시** 수정해야 하는 항목 (수정 후 → 외부 확인):

| ID | Severity | 위치 | 수정 |
|---|---|---|---|
| **H1** | HIGH | §18, §18.3, §22, §24, §26.1, §28 | `scripts/lib/secondary-llm/**` 제외 주장을 smoke-count reconciliation에 대해 carve-out. §18.3에 `secondary-llm-docs-calibration.ps1`(smoke-count 상수만) + `smoke-secondary-llm-docs-scan.ps1`(`$SmokeTotal` 기본값만)을 narrow 예외로 추가. §22를 실제 gate 메커니즘(사람이 상수 갱신)에 맞게 수정. §24 rollback 목록에 반영. §28에 검토 checkbox 추가. |
| **M1** | MEDIUM | §2, §5, §18.3 | 기본 `-ScratchRoot`(system temp)가 한글/공백 profile에서 실패함을 "예외"가 아니라 "일반 경로"로 명시. ASCII scratch helper 또는 사용자 가이드 제공. |
| **M2** | MEDIUM | §26.1 | `REJECT` / `REJECT_FOR_THIS_POC` / `DEFER` 3-value 도입. `State agent_runtime`은 §26.2와 정합하도록 `DEFER`. `Codex write lane`/`Ollama`는 "이번 initiative 거부 + 장기 별도 검토" 명확화. |
| **M3** | MEDIUM | §4 inv.14, §26.1 | "single paid provider ... 운용 가능"을 현재 사실("Claude 단독 운용 가능, 다른 provider 미요구")과 장기 불변식("복수 유료 frontier 동시 구독 필수 요구 금지")으로 분리. |
| **M4** | MEDIUM | §9, §15.1 | 허용 JSONL event allowlist를 계획서/versioned invocation spec에 명시(`agent_message`, `turn.completed`, session/init metadata, versioned known-diagnostic `error` subset). smoke에만 위임하지 않음. |
| **M5** | MEDIUM | §27 | owner decision 9개를 implementation-time / production-entry / graduation / release로 재분류. 진짜 production-entry blocker는 #7, #8. |
| **M6** | MEDIUM | §8.1 | envelope `files[]` schema에 staged+unstaged same-path(`base → staged → worktree`) 표현 추가(`staged_sha256?`/`worktree_sha256?` 또는 source-basis 태그 복수 entry). |

---

# 14. Non-blocking Recommendations

| ID | Severity | 수정 |
|---|---|---|
| L1 | LOW | `prompts/codex-readonly-review.md` → `prompts/codex-readonly-review-system.md` (네이밍 일관) 또는 `scripts/lib/codex/`로 이동 + `prompts/`는 Claude 전용 유지 근거 명시 |
| L2 | LOW | `scripts/lib/codex/*.schema.json`이 hygiene 제외 안 됨 + protected tree 포함임을 §18.2 security smoke에 명시 assert |
| L3 | LOW | §13 "`StandardOutputEncoding`/`StandardErrorEncoding` ... 설정 가능한 환경에서" hedge 제거 — PS 5.1은 지원, 무조건 설정 |
| L4 | LOW | §15.4 child env "필요한 최소값"을 명시적 allowlist로(§27 또는 §19 gate) |
| L5 | LOW | §12에 base/current content bytes 캡처 명령(`git show :FILE` / `git show HEAD:FILE` / `git cat-file`) 명명 |
| L6 | LOW | `TASK_CLASS_TOKEN_POLICY.md` Codex reviewer 텍스트를 새 subsection/section으로(§16 Secondary-LLM 본문에 혼재 금지). "단일 enum 병합 금지"는 이미 §18.3에 있음 — 배치만 |
| — | — | §28에 12.4의 최소 gate 11항을 반영해 "재확인" soft 항목과 구분 |

---

# 15. Final Production-Planning Verdict

## `PLAN_REQUIRES_REVISION`

- **BLOCKER: 0.** architecture / security / compatibility / rollback / scope에 구현을 막는 결함 없음. core architecture는 V3 evidence와 정합. provider가 production repo/absolute path/Git scope 결정 권한/authoritative truth 승격 경로를 얻는 곳 없음. scope creep 없음. `run-cycle.ps1`/`doctor.ps1`/`harness-runtime-dependency-inventory.ps1`/`scripts/lib/claude/**` 제외는 source로 achievable 확인.
- **HIGH: 1 (H1).** `scripts/lib/secondary-llm/**` "변경 0" 제외 주장이 smoke 6개 추가 + `secondary-llm-docs-scan.ps1`의 하드코딩 상수 gate와 양립 불가. 계획서 내부 불일치 — §18/§18.3/§22/§24/§28 수정 필요. semantically inert한 count reconciliation이며 documented precedent 있음.
- **MEDIUM: 6 (M1–M6).** scratch-root 한글 profile 실패 프레이밍 / REJECT-DEFER 혼용 / single-provider invariant 문구 / event allowlist 미명시 / owner decision 과대분류 / envelope staged+unstaged shape.
- **LOW: 6 (L1–L6).** 네이밍·hedge·문서 배치·명령 명명.

수정은 **targeted**이며 rewrite가 아니다. §13의 7개 필수 수정을 계획서에 반영하면 architecture는 그대로 유지된다.

## 다음 순서

```text
Codex가 §13 필수 수정(H1 + M1–M6) 반영
→ Claude 재검토 (수정 확인)
→ 사용자 / 외부 확인
→ 명시적 production implementation 승인
→ D:\harness-kit implementation (§18 파일 + §19 순서만)
```

이 review가 승인되더라도 **production 구현을 시작하지 않는다.** capability experiment를 다시 시작하거나 HRNS-NOW를 먼저 수정하지 않는다.

최종 구현 원칙(계획 §970)은 그대로 유효하다:

> **Parent-owned bounded evidence → tool-free structured Codex review → deterministic audit → Human recommendation.**

---

## Mutation / Hygiene

- HRNS-NOW production source / `D:\harness-kit` / WORKFLOW_STATE / Registry / Claude·Codex global config / bridge / Git index·history: **변경 없음**
- `git add`/`commit`/`push`: 없음
- build / test / smoke: 없음
- **live provider(Codex/Claude/Ollama) 호출: 없음**
- 사용한 read-only 명령: `Read`(plan + V3 실험/감사 + V1/V2 + revOpinion), `git status --porcelain`, `find`, `grep`, `sed` (`D:\harness-kit` source 직접 확인: `scripts/lib/`, `scripts/report/`, `prompts/`, `doctor.ps1`, `release-package-hygiene.ps1`, `secondary-llm-docs-calibration.ps1`, `secondary-llm-docs-scan.ps1`, `smoke-secondary-llm-docs-scan.ps1`, `smoke-clean-root-onboarding.ps1`, `smoke-smoke-index-consistency.ps1`, `SECURITY_MODEL.md`, `TASK_CLASS_TOKEN_POLICY.md`)
- 새로 작성한 파일: `doc/revolution/claude_integrated_plan_adversarial_review.md` (사용자 지시)
- 이 문서의 staging/commit은 Git Owner(Codex)와 사용자의 판단이다.
- 다음 단계: Codex가 §13 필수 수정을 계획서에 반영 → Claude 재검토 → 외부 확인. 그 전까지 production 구현 미착수.
