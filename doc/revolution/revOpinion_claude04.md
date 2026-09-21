# HRNS-NOW Agent Runtime Evolution — Claude Cross Review (Round 04)

- 문서 성격: 보강 감사 프로토콜의 **Cross Review 04 (Claude 제출본)**. SELF = claude, OTHER = codex. Mode = **B** (`revOpinion_claude03.md` + `revOpinion_codex03.md` 둘 다 존재 확인).
- 입력: `revOpinion_codex03.md`(정독), `revOpinion_claude03.md`(자기 03), 필요 시 01/02 및 실제 source 재확인.
- 목적(§31): 내 03을 방어하는 문서가 아니다. codex03에서 더 정확한 사실·내가 놓친 파일·더 작은 구현안·더 안전한 gate·token 절감 아이디어·compat 위험을 **적극 수용**하고, 각 쟁점을 `ACCEPT / REVISE / REJECT / DEFER`로 판정한다.
- 판단 기준 순서(§33): (1) 실제 source (2) deterministic observation (3) 현재 contract (4) regression blast radius (5) rollback 가능성 (6) token/cost (7) 구현 단순성 (8) 모델 선호(**근거 아님**).
- 이 문서는 **Integrated Plan을 작성하지 않는다(§36).** 마지막은 "통합 계획을 작성하기 전에 남은 결정"까지만.
- 상태: 분석 단계. 이 문서 저장 외 mutation 0. build·Gradle·smoke·live Claude/Codex/Ollama·네트워크 호출 미실행. 읽기 전용 명령만 사용.

---

# 1. Cross Review Verdict

## `CONVERGED — REVISE_BEFORE_INTEGRATED_PLAN`

두 독립 03이 **거의 완전히 수렴**했다. codex03(Mode A, 내 03 미열람)과 claude03이 서로 보지 않고 같은 verdict(`REVISE_BEFORE_INTEGRATED_PLAN`), 같은 3대 사실 수정, 같은 POC 형태, 같은 열린 실험 목록에 도달했다. §10.3의 prompt-injection 7-layer 분류는 양측이 **독립적으로 동일하게** 도출했다.

이번 cross review에서 내가 **수용(REVISE)**하는 것 7건:

1. Secondary LLM 자산 중 **"그대로 재사용" 함수는 없다** (claude03 §7.1의 "그대로" 등급을 하향). codex03 §7.1의 ~55개 함수 전수 판정이 옳다.
2. **change-set 수집기를 새로 만들 필요 없다** — codex CLI의 `review --uncommitted`가 scope 해석을 담당하고, `git status --porcelain=v2 -z` 기반 explicit manifest+hash는 **감사·drift 검출용**. claude03 §7.3/§9의 "complete change-set builder 신규"보다 작다.
3. Claude runner의 **미구현 gap**(cancellation token 없음, output byte cap 없음, child encoding 미지정, residual-process 검증 없음)을 codex03 §6.1이 정확히 잡았다 — Codex adapter는 이를 복사하지 말고 typed timeout/cancel/residual/byte-cap/encoding을 처음부터 갖춰야 한다.
4. `--ignore-user-config`가 `CODEX_HOME`/auth까지 격리한다고 가정하지 않는다. `--ignore-rules`는 exec-policy option이지 AGENTS/auth 격리 증거가 아니다 (codex03 §10.1).
5. `authorized_target_file`는 read-scope pointer로만 등장, `write_target`/`authorized_write` 필드로 절대 노출 안 함 (codex03 §8.2 — claude03 §8.2의 "read+write authority"보다 명확).
6. HRNS 결합 표현: "실행 제어 경계는 provider-blind이나 **진단·복구·bridge observability는 Claude-coupled**" (codex03 §3.1 vocabulary 채택 + claude03 §3.5의 file:line 8곳 병합).
7. Review Packet request/result 후보 schema: codex03 §8.2의 `codex_review_request` shape + result(`verdict=pass|findings|blocked`, `findings[{id,severity,file,line,summary,evidence_refs,confidence}]`)를 AR-C2b 출발점으로 채택(양측 모두 non-final).

**REJECT/조건부** 2건: codex03 §9.5의 구체 CLI 명령 문자열은 미검증(문법·순서 canary 필요) — 접근은 accept, 문자열은 미확정 취급. review base를 `uncommitted` **하나로 확정**하는 것은 조건부 — Codex가 Git Owner(commit 수행)이므로 "그날 이미 커밋된 작업"에는 base/commit review가 필요할 수 있어 열린 결정 유지.

**Integrated Plan 단계로 넘어가면 안 된다.** §12의 8개 실험 항목 미해결 + 외부 확인 미완.

---

# 2. codex03에서 새롭게 확인된 사실 (claude03에 없던 것)

| # | codex03 근거 | 사실 | 채택 |
|---|---|---|---|
| 2.1 | §6.1 표 "cancellation" 행 | 3개 `Start-ClaudeProcess` 전부 **명시적 cancellation token 미구현** — timeout + heartbeat wait만 존재 | ACCEPT |
| 2.2 | §6.1 표 "output byte cap" / "encoding" 행 | Claude runner는 stdout/stderr를 `StringBuilder`에 **전량 보관**(byte cap 없음), child stdout/stderr **encoding property 미지정**(decoding gap). log write만 UTF-8 no BOM | ACCEPT — Codex adapter는 byte cap + explicit encoding 필수 |
| 2.3 | §6.1 마지막 문단 | role-sliced navi `--allowedTools`는 `Read,Glob,Grep`, worker는 write tool까지. output-format env는 planning/code/doc별 분리 | ACCEPT (claude03의 base-args 관찰에 세부 추가) |
| 2.4 | §8.3 | `codex exec review --uncommitted`는 help 상 **staged + unstaged + untracked**를 검토한다고 명시. `--base`, `--commit`은 별도 scope | **ACCEPT — 이번 라운드 최대 POC 축소 근거.** claude03 §7.3의 "complete change-set builder 신규"를 §3.2대로 REVISE |
| 2.5 | §10.1 | `--ignore-rules` = exec policy rule 무시(AGENTS/auth 아님). `--ignore-user-config`가 auth 있는 `CODEX_HOME`까지 제거한다는 증거 없음 | ACCEPT (claude03 §10.6 강화) |
| 2.6 | §2.2 Full Read 61 | codex는 `state-surface.ps1`(88KB), `validate-ops.ps1`(56KB), `invoke-secondary-llm-advisory.ps1`, `audit-secondary-llm-candidate.ps1`, `STATE_MODEL.md`, `SECURITY_MODEL.md`, `WORKSPACE_SPEC.md`, `PROJECT_ONBOARDING.md`, `PYTHON_SIDECAR_BOUNDARY.md`, `HARNESS_KIT_MAP.ko.md`, secondary-llm lib **17개 전부**를 정독 — claude03(24개 full)보다 넓다 | 인정 (§5) |
| 2.7 | §7.1 | Secondary LLM 자산 ~55개 함수 전수 판정, 결론 "그대로 재사용 가능한 함수 없음" — claude03 §7.1(~25개, 일부 "그대로")보다 엄격·정확 | ACCEPT (§3.1) |
| 2.8 | §2.3 | term 불일치 5개 파일: `kit-version.json`(version contract → full read 승격), `python/hooks/encoding_guard.py`, `python/scripts/validate_encoding.py`, `scripts/validate-encoding.ps1`, `scripts/lib/smoke/smoke-scratch-lifecycle.ps1` — encoding/scratch 전용, Agent Runtime semantic dependency 아님 | 인정 |
| 2.9 | §8.2 | Review Packet result 후보에 `scope_observed`, `risks`, `open_questions` 포함, `confidence` per finding — claude03 §9.4보다 구체 | ACCEPT |

---

# 3. 내가 수정하는 claude03 판단

## 3.1 §7.1 Secondary LLM "그대로 재사용" 등급 → **하향 (REVISE)**

claude03 §7.1은 `ConvertTo-SecondaryLlmSafeText`, `ConvertTo-SecondaryLlmSha256Text`, `Assert-SecondaryLlmRunId`, `Test-SecondaryLlmPathInside`, `Resolve-SecondaryLlmOutputPath`, `Get-SecondaryLlmJsonBytes`, `New-SecondaryLlmEvidenceIndex`, `Get-SecondaryLlmClaimSummary` 등을 "그대로"로 표기했다. codex03 §7.1의 지적이 타당하다:

- `ConvertTo-SecondaryLlmSafeText`는 **regex-only redaction = 보조 방어**이지 완전한 secret 경계가 아니다.
- `Test-SecondaryLlmPathInside`는 **junction/symlink 및 non-existing target 정책이 없다** (HRNS가 `RealPathGateway`를 별도로 두는 이유와 동일 — lexical containment만으로는 부족).
- `Assert-SecondaryLlmRunId`/`New-SecondaryLlmRunId`/`Resolve-*Path`는 **Codex namespace + collision/trace-id 정책**이 필요하다.
- `Invoke-SecondaryLlmGitCommand`는 **NUL-safe argv / output cap / scope typing이 없다**.

**개정된 판정**: Secondary LLM 자산에서 **"그대로" 재사용 함수는 없다.** 가장 재사용 가치가 높은 것은 evidence-index + claim-evidence **엔진**이며, 그것조차 (a) 신규 `finding_anchor` claim_type + (파일이 review scope에 있음) AND (line이 diff hunk/스냅샷에 존재) 검증, (b) Codex namespace, (c) junction/symlink 경로 정책, (d) byte cap + explicit encoding, (e) fail-closed schema/size 검사 추가가 필요하다. 나머지는 "패턴만".

## 3.2 §7.3 / §9 "complete change-set builder는 신규" → **REVISE (더 작게)**

codex03 §8.3의 관찰(`review --uncommitted`가 staged+unstaged+untracked 표방)을 채택한다. **개정된 설계**:

```
native scope 해석:  codex exec review --uncommitted   (staged + unstaged + untracked)
deterministic 감사:  git status --porcelain=v2 -z 기반 explicit manifest + SHA-256
                     → native review가 관측한 scope와 대조, drift/불일치 검출용
미검증 Git object:   rename/copy, binary, submodule, LFS의 native review 실제 의미는
                     canary 전까지 미확인 → typed preflight로 발견 시 block, 지원 추측 금지
base/commit review:  후속
```

이것은 claude03의 "새 change-set 수집 builder"보다 작다. change-set **수집**은 native CLI가, **감사**는 deterministic manifest가 담당.

## 3.3 §13 Claude runtime 표 → gap 4건 추가 (REVISE)

claude03 §13 3-way 비교표에 codex03 §6.1이 잡은 gap을 추가한다: **cancellation token 없음 / output byte cap 없음 / child stdout·stderr encoding 미지정 / residual-process typed 검증 없음.** 프레이밍: 이 4건은 Claude runner의 *현재 gap*이며, **Codex adapter는 복사하지 말고 처음부터 typed timeout/cancel/residual/byte-cap/explicit-encoding 결과를 갖춰야 한다** (codex03 §6.2).

## 3.4 §9.4 POC acceptance criteria → 1항 추가 (REVISE)

claude03 §9.4에 codex03 §9.7을 추가: **"provider exit 0과 review pass를 분리한다. parse/scope/schema/instruction conflict는 fail-closed `blocked`다."**

## 3.5 §10.6 auth isolation → 강화 (REVISE)

claude03 §10.6("auth는 provider-native store")을 codex03 §10.1로 강화: `--ignore-user-config`가 `CODEX_HOME`(auth) 격리를 **보장하지 않는다고 가정**하고, 실행 후 어떤 config/auth/network가 실제 적용됐는지 기록. `--ignore-rules`는 AGENTS/auth 증거가 아님.

## 3.6 §8.2 `authorized_target_file` 분류 → 정정 (REVISE)

claude03 §8.2의 "read+write authority, Review Packet은 read scope로만 참조"를 codex03 §8.2로 정정: **경로 문자열은 read-scope pointer로만 등장 가능, `write_target`/`authorized_write` 명칭 필드로는 절대 노출 안 함.**

## 3.7 유지 (claude03가 codex03보다 정확/상세한 것 — §5에서 상술)

`SECONDARY_LLM_LANE.md:88` line 인용(slot 재사용 금지의 *source 규칙*), HRNS provider-shaped surface 8곳 file:line, usage-ledger schema 1.1 필드별 전수, `Get-ClaudeSessionResumeDecision`의 14개 rejection reason, smoke 인벤토리 drift 95→100, `WorkspaceRecoveryDiagnosticsAdapter.kt:32` — 이들은 claude04에서도 유지.

---

# 4. codex03에서 수용하지 않는 판단

## 4.1 §9.5 구체 CLI 명령 문자열 — **조건부 REJECT (접근은 ACCEPT)**

codex03 §9.5: `codex exec --sandbox read-only --ephemeral --ignore-user-config -C <ProjectRoot> --json --output-schema <schema> review --uncommitted ...`

- codex03 §2.2가 "확인된 command: `codex exec`, `codex exec review`"라 했으므로 `--output-schema <schema>`가 `review` **앞**에 오는 이 순서가 실제 파싱되는지 미확인. codex03 스스로 "실제 option 순서와 Windows quoting은 fake CLI와 harmless live canary로 먼저 확정한다"고 함.
- **판정**: 접근(`codex exec review --uncommitted` + `--sandbox read-only` + `--ephemeral` + `-C` + `--json` + `--output-schema`)은 ACCEPT. **구체 명령 문자열은 미확정** — AR-C2a canary가 문법·순서·quoting을 확정하기 전까지 계획서에 고정하지 않는다.

## 4.2 §8.3 / §9.4 "review scope는 `uncommitted` 하나" 확정 — **조건부 (열린 결정 유지)**

- codex03 §8.3: "첫 POC 후보는 native `review --uncommitted` 하나로 제한 ... base/commit review는 후속이다."
- 협업 온보딩 문서상 **Codex가 Git Owner이며 staging/commit을 수행**한다. 그날의 authorized 작업이 이미 커밋된 상태에서 Codex reviewer를 호출하면 `--uncommitted`는 빈 diff를 볼 수 있다.
- **판정**: 첫 POC의 *최소* native 경로가 `--uncommitted`라는 데 동의(ACCEPT). 다만 "**authoritative review base**(uncommitted / staged / commit / base-branch / explicit manifest)"는 **열린 결정**으로 유지(§12 O, codex03 §14.6도 관련 항목을 open으로 둠). `--uncommitted`를 유일 정본으로 확정하지 않는다.

## 4.3 §2.1 Coverage 표에 "Partial Read" 열 없음 — **방법론 차이 (REJECT 아님)**

codex03는 `run-cycle.ps1`/3개 wrapper/`code-role-sliced-runner.ps1`/`doctor.ps1`을 "호출부·함수·계약 구간은 읽었으나" 보수적으로 **Search Only**로 분류. claude03는 이를 **Partial Read**로 별도 표기. 둘 다 정직하나 claude03가 약간 더 granular. — 이건 판정 대상 아님, §5에 coverage 방법론 차이로 기록.

## 4.4 §7.1 표의 일부 "제한적/불가" — 대체로 동의하나 2건 재평가

- `Test-SecondaryLlmValidationEvidenceContradictsPass` — codex03 "수정 후 / 좋은 fail-closed 패턴". 동의하되 **claude04 강조**: 이 함수의 로직(evidence result가 fail/error거나 exit_code≠0이면 pass 주장 모순)은 Codex finding audit의 `finding_contradicted_by_evidence` 검사에 **거의 그대로** 이식 가능 — "수정 후"보다 "패턴 + 소폭 수정".
- `Test-SecondaryLlmClaimEvidence`의 `changed_file_not_in_git_diff_evidence` contradicted 패턴 — codex03 "패턴만 / Codex finding line/evidence 검증으로 재설계". 동의. claude03 §10.4가 이미 "이 패턴을 `finding_anchor`로 확장"이라 했고 codex03도 같은 방향 — **수렴**.

---

# 5. Coverage 차이

## 5.1 codex03가 읽었고 claude03가 놓친 영역

| 영역 | codex03 Full Read | claude03 |
|---|---|---|
| `state-surface.ps1` (88KB) | FULL | grep only |
| `validate-ops.ps1` (56KB) | FULL | grep only |
| `scripts/report/invoke-secondary-llm-advisory.ps1` (33KB) | FULL | 미열람 |
| `scripts/report/audit-secondary-llm-candidate.ps1` | FULL | 미열람 |
| `secondary-llm` lib **17개 전부** | FULL | 8개 FULL + 1개 PARTIAL |
| `STATE_MODEL.md`, `SECURITY_MODEL.md`, `STATE_SURFACE_REFERENCE.md`, `PYTHON_SIDECAR_BOUNDARY.md`, `WORKSPACE_SPEC.md`, `PROJECT_ONBOARDING.md`, `HARNESS_KIT_MAP.ko.md` | FULL | 미열람 / grep (WORKSPACE_SPEC) |
| Secondary LLM 함수 전수(~55) | §7.1 | ~25 |

→ **Secondary LLM subsystem과 State surface 검증에서 codex03의 coverage가 더 넓다.** claude04는 codex03 §7.1 함수 판정과 §4.3 lane 속성을 사실 기반으로 채택.

## 5.2 claude03가 읽었고 codex03가 (덜) 다룬 영역

| 영역 | claude03 | codex03 |
|---|---|---|
| `claude-invoke-core.ps1` 세부 | 14개 resume rejection reason, usage-ledger 1.1 필드 전수, V2 gate 정확한 조건 | §6.1 요약 수준 |
| HRNS provider-shaped surface | 8곳 file:line (`RecoveryProjections.kt`, `ReasonKeyStrings.kt`, `WorkspaceRecoveryDiagnosticsAdapter.kt:32` 포함) | §3.1 목록(덜 정밀) |
| `SECONDARY_LLM_LANE.md:88` line 인용 | slot 재사용 금지의 *source 규칙* 명시 | §3.5 결론 동일하나 line 인용 없음 |
| smoke 인벤토리 drift | 실측 100 vs 문서 95 명시 + backlog 항목화 | 미언급 |
| HRNS `queue.active.*`/`blocked_reason`/`last_updated_at` requiredness gap | §3.7 backlog 항목화 | 미언급 |
| `00-wrapper-input.json` 생성 코드 line (`code-role-sliced-runner.ps1:2343`) | 명시 | §8.1 필드 목록(line 없음) |

→ **claude04 결론**: 두 03을 합치면 coverage가 상호 보완적으로 완성된다. claude03의 정밀 인용 + codex03의 넓은 정독을 통합 계획서 사실 기반으로 병합.

## 5.3 양측 공통 미검토 (정직하게 유지)

`run-cycle.ps1`/`code-role-sliced-runner.ps1`/3개 wrapper/`doctor.ps1` 전체 완독 안 함(관련 섹션만). smoke ~89–100개 전체 본문 완독 안 함(회귀 gate로만 관련). python sidecar 13개 본문 미검토(`PYTHON_SIDECAR_BOUNDARY.md`가 PS1 authoritative 확인). → **양측 03 모두 "Harness 전체 regression closure 완독"을 주장하지 않는다.** 이 한계는 통합 계획서에 명시.

---

# 6. POC 범위 비교

| 항목 | claude03 §9 | codex03 §9 | claude04 판정 |
|---|---|---|---|
| 형태 | `scripts/report/` 독립 entrypoint, default-off, `-AllowLiveCodex` | 수동 report command, default-off, 명시 opt-in | **CONSENSUS** |
| 격리 | `run-cycle.ps1`/State/daily 4-file/bridge/HRNS 무접촉 | 동일 + `%USERPROFILE%\.codex\config.toml`/auth store 무접촉 명시 | **CONSENSUS** (codex의 auth-store 명시 채택) |
| review scope | 열린 결정(uncommitted default) | `uncommitted` 하나로 확정 | **REVISED CONSENSUS**: uncommitted가 첫 최소 native 경로(ACCEPT), authoritative base는 열린 결정 유지(§4.2) |
| change-set 수집 | 새 builder | native `review --uncommitted` + deterministic `git status --porcelain=v2 -z` manifest(감사용) | **REVISED CONSENSUS**: codex 설계 채택(§3.2) — 더 작음 |
| 신규 파일 | ≤3 (`invoke-codex-review.ps1`, `codex-review-adapter.ps1`, `codex-review-contract.ps1`) + smoke | `invoke-codex-review.ps1`, `codex-review-runner.ps1`, result schema + smoke | **CONSENSUS** (≤3, 이름 cosmetic — 통합 계획서에서 확정) |
| 단계 | AR-C2a spike(파일 0) → AR-C2b 계약 → AR-C3 offline → AR-C3-live | 8개 POC 속성 나열(단계 미번호) | **CONSENSUS**: spike-first, 관찰 후 계약. namespace/번호는 통합 계획서 |
| Review Packet | 얇은 파생 계약, write-scope 필드 **제거** | `codex_review_request` schema 후보(§8.2), write affordance 필드 제거 | **REVISED CONSENSUS**: codex §8.2 schema를 AR-C2b 출발점 채택 |
| 산출물 | `<workspace>/<date>/logs/codex-review/<run-id>/` (request/evidence.manifest/events.jsonl/result/audit) | 동일 경로 (request/change manifest/process result/structured review result) | **CONSENSUS** |
| 불변식 | `authoritative=false`, `safe_to_auto_adopt=false`, `runtime_integrated=false`, `repository_write=false`, `git_mutation=false` | 동일 취지 + "provider exit 0 ≠ review pass" | **REVISED CONSENSUS**: exit 0 ≠ pass 추가(§3.4) |
| 제외 | Router/State writer/HRNS/Claude resolution/Codex write/commit/MSI/`agent-runtime` namespace | Router/common agent runtime/State projection/debate/Claude·Secondary·HRNS 연결 | **CONSENSUS** |
| Human Message Bus 감소 | acceptance에서 제외(AR-C4 지표) | §16이 "안전·경제적인지 먼저 증명"으로만 규정 | **CONSENSUS** |

**결론**: POC 범위는 codex03가 claude03보다 조금 더 작다(change-set 수집을 native CLI에 위임). 그 축소를 채택한다.

---

# 7. Security / Compatibility 비교

## 7.1 Security

| gate | claude03 §10 | codex03 §10 | claude04 |
|---|---|---|---|
| sandbox read-scope canary (working root/parent/other drive/`%USERPROFILE%`/env var) | ✔ | ✔ (marker = non-secret random, "write denied"/"read denied" 별도 필드, 전후 fs hash) | **CONSENSUS** (codex의 필드 분리 + fs hash 채택) |
| "write denied ≠ read denied" | ✔ | ✔ | **CONSENSUS** |
| `AGENTS.md` fixture (root + nested, 충돌 지시, read-only/no-git rule) | ✔ | ✔ (nested AGENTS marker fixture로 precedence 검증) | **CONSENSUS** |
| 기존 `AGENTS.md` 무수정/무생성 | ✔ | ✔ | **CONSENSUS** (bridge-free = hard acceptance) |
| prompt injection 7-layer | Harness instr / provider-native / project instr / task instr / evidence / source / test-output | **동일 7층** (독립 도출) | **CONSENSUS** — evidence/source/test-output = untrusted data, "ignore previous instructions"·shell·patch 요구 미실행, 충돌 시 `instruction_conflict`/`blocked` |
| process env allowlist (전체 상속 금지) | §10.3 | §10.3, §11 | **CONSENSUS** |
| raw transcript handoff 금지, structured critique만 | ✔ | ✔ (file/line/severity/summary/evidence/confidence) | **CONSENSUS** |
| auth 격리 가정 | "provider-native store" | `--ignore-user-config`가 `CODEX_HOME` 격리 **미보장** 가정, `--ignore-rules` ≠ AGENTS/auth | **REVISED CONSENSUS**: codex 강화 채택(§3.5) |
| structured output 허위 확신 | §10.4 (존재하지 않는 파일/라인, diff에 없는 변경, 미실행 테스트, State 불일치, 근거 없는 verdict) | §13.1 (동일 목록) | **CONSENSUS** |
| retention (JSONL max byte, raw 보존 여부, path/secret masking, session/account id 비저장, release 제외, run-id containment·traversal) | §10.5 | §9.6, §13.1 | **CONSENSUS** |
| CLI version drift (option 이름/JSONL shape/exit/error 문구) | §10.6 | §14.1 | **CONSENSUS** |

## 7.2 Compatibility

| surface | claude03 §12 | codex03 §5.2/§12 | claude04 |
|---|---|---|---|
| 3-file bridge | 파급: `enter-project`/템플릿 + HRNS resolver/probe/port/UI/test + docs + native-QA. POC bridge-free = hard criterion | 동일 | **CONSENSUS** |
| 4-file daily surface | 파급 목록 + POC 무변경 | 동일 ("reviewer log는 required operational truth 아님") | **CONSENSUS** |
| `WORKFLOW_STATE` | writer + validate-ops + doctor + HRNS DTO/mapper/`HarnessProductionContractTest` + guaranteed-envelope. AR-C9 진입 시 규율 적용 | 동일 ("POC 결과는 non-authoritative diagnostic, schema 변경 근거 없음") | **CONSENSUS** |
| provider executable | optional host dependency, doctor required 미추가, `harness-runtime-dependency-inventory.ps1` 미추가 | 동일 명시 | **CONSENSUS** |
| "POC compat 영향 0" 표현 | 정정: release file set/dependency inventory/docs/smoke count/pollution scan/retention 표면은 바뀜 | §12 ("additive·standalone·optional") | **CONSENSUS** |
| rollback 단위 | 신규 파일 + 문서 count 줄 + 사전 zip snapshot + change manifest + hash. broad recursive delete 금지 | 동일 ("기존 production 파일 수정/State migration 없으면 rollback 후 data migration도 없음") | **CONSENSUS** |
| release 포함 여부 | 미명시 | §5.2/§14.7: release-hygiene + clean-root smoke로 증명, developer-only vs release는 open | **REVISED CONSENSUS**: codex의 "release 포함 여부는 열린 결정" 채택(§12 O3) |

**Compat 관점 이견 0.** 모든 항목 수렴.

---

# 8. Token / Cost 비교

| 항목 | claude03 §11 | codex03 §11 | claude04 |
|---|---|---|---|
| 평가식 | Accepted Change당 총 token + Human intervention + Finding quality + Failure rate | Accepted Change당 총 token + Human intervention 시간 + finding quality/재현율 + false-positive·failure/retry rate | **CONSENSUS** (codex의 재현율·false-positive 세분 채택) |
| 고정비 | Kit context + append-system-prompt(18.7/6.0/4.3KB) + `--add-dir`; deterministic preflight로 planning/code/doc 최대 1/2/1 | core/profile/project instruction + daily strategy/state + rendered prompt + packet + provider bootstrap/session | **CONSENSUS** |
| 더 싼 lever (Codex 추가 전) | `packet_first` / `parent_deterministic` navi / compact planning / context-diet `active` | (1) usage-ledger 1.1 baseline (2) parent-deterministic navi A/B (3) packet-first/compact planning A/B (4) deterministic no-provider preflight (5) 불필요한 retry/preflight provider call 제거 | **REVISED CONSENSUS**: codex의 **우선순위 목록**(baseline 측정 먼저 → navi → planning → preflight → retry 제거) 채택. 전부 live A/B gate 필요, POC prerequisite로 default-on 금지 |
| 신규 절감 원칙 | (없음 — 별도 안 냄) | packet-first(body 대신 path/hash) / structured critique(raw transcript 대신 small finding set) / **unchanged scope cache**(manifest hash 동일 시 review 재호출 금지) / **accepted-change linkage**(usage ledger ↔ 최종 acceptance 연결) | **ACCEPT** — codex §11의 "unchanged scope cache"와 "accepted-change linkage"는 claude03에 없던 유용한 절감 원칙. 통합 계획서 A/B 설계에 포함 |
| "Codex 추가 = 절감" 전제 | 배격 | 배격 | **CONSENSUS** |

**codex03가 token 분석에서 claude03보다 정교**: unchanged-scope cache(manifest hash 재사용), accepted-change linkage(ledger ↔ acceptance), 5단계 lever 우선순위. 전부 채택.

---

# 9. Tests / Gates 비교

두 03의 gate 목록은 **거의 동일**. 병합하면:

## 9.1 Offline deterministic (양측 합집합)

fake `codex` executable — argv 순서 / spaces·Korean path / stdin 정책 · stdout·stderr 동시 대량 출력 / UTF-8 / timeout / **cancellation** / tree kill / **residual process** / **byte truncation** · output schema parse fail / malformed JSONL / **exit 0 + invalid result** / nonzero exit / auth failure / usage·quota 제한 · output path traversal / run-id / **junction·symlink** / project·workspace·kit root containment · **process env allowlist + secret redaction** · staged/unstaged/untracked 각각 및 혼합 scope manifest · **rename/copy/binary/submodule/LFS 발견 시 first POC block** · **manifest hash TOCTOU + review 전후 change-set drift** · source/diff/test-output prompt injection fixture · root/nested AGENTS conflict fixture · State/bridge/Registry/repository content 무변경 assertion · "신규 bridge 파일 없음" 단언 · release-package-hygiene + clean-root-onboarding · `smoke-run-cycle-required-dependency-inventory` 불변 · `smoke-smoke-index-consistency` (실측 100) · 기존 official offline suite 전체 통과(live 호출 0)

**굵게** 표시 = codex03가 추가로 명시(claude03 §13.1엔 없거나 약함): cancellation / residual process / byte truncation / exit 0 + invalid result / junction·symlink / process env allowlist / rename·copy·binary·submodule·LFS block / manifest hash TOCTOU. — **전부 ACCEPT.**

## 9.2 Manual/live capability (양측 합집합, harmless fixture only)

현재 CLI option grammar + `review --uncommitted` 실제 scope · working root/parent/other drive/home/env canary read·write 관측 matrix · no-window 실제 packaged/PowerShell launch · authentication/config leakage 여부 · structured finding quality + false-positive baseline · Claude-only 대비 Claude+Codex Accepted-Change당 total token·시간 · rename/copy/binary/submodule/LFS의 native review 실제 의미(발견 시 block)

**live gate는 harmless fixture에서만, production repository·real secret·global config 미사용** (양측 합의).

## 9.3 claude04 추가 gate (양측 03에 없음)

- **AR-C9 진입 시** `HarnessProductionContractTest` + guaranteed-envelope smoke 규율 (claude03 §12.1) — `agent_runtime` optional raw extension이 HRNS DTO에 들어갈 때 `required_next_action` BLOCKER 재발 방지. codex03 미언급.

---

# 10. Consensus Map

## 10.1 Consensus (양측 독립 도달 + source 확인)

1. 방향 불변; Integrated Plan 단계 아님; 다음 = 04 cross review → 외부 확인 → harmless Codex capability 실험.
2. 3대 사실 수정: (a) HRNS 실행 제어 경계는 provider-blind, 진단·복구·bridge observability는 Claude-coupled. (b) `claude-invoke-core.ps1`은 process supervisor 아님 — launch는 각 runner의 `Start-ClaudeProcess`; core는 stop classification / `claude_runtime` gate / session broker / continuity / usage ledger 1.1 / stream-json telemetry 소유. (c) Secondary LLM Git evidence = `diff --name-only/--stat/--check` = unstaged tracked only.
3. POC = `scripts/report/` 독립 command, default-off, 명시 opt-in, State-free, bridge-free, HRNS-free, no router/write/commit/debate/Ollama/Claude-connection.
4. 산출물 `<workspace>/<date>/logs/codex-review/<run-id>/`: request + change manifest + process result + structured review result; raw output은 redaction·byte cap 진단용, handoff 금지.
5. `00-wrapper-input.json` v2 통째 복제 금지 — path/hash/acceptance/evidence pointer만 참조; write authority(`allowed_write_files`) + continuation(`budget_continuation_prompt`) 필드 제외.
6. `usage_guard`/`role_sliced`/`claude_runtime`은 provider-state slot 아님. `agent_runtime` optional field 결정은 POC 범위 밖.
7. Task Class / Budget Tier / Lane / Role / Provider / Risk = 직교 축. `TASK_CLASS_TOKEN_POLICY.md`가 mapping 정본. 단일 enum 병합 금지.
8. `.codex/config.toml` / `AGENTS.md` / nested `AGENTS.md` / `CLAUDE.md` / auth store 자동 생성·수정 금지. bridge-free = hard acceptance criterion.
9. provider executable = optional host dependency. doctor required 미추가. `harness-runtime-dependency-inventory.ps1` 미추가.
10. rollback = 신규 파일 + 문서 count 줄 + 사전 zip snapshot + change manifest + SHA-256. 기존 production 파일 무편집(편집 필요 시 POC 아님). broad recursive delete 금지. `D:\harness-kit` 비-git.
11. canary probe: working root / parent / other drive / `%USERPROFILE%` / env var. non-secret marker. "write denied"/"read denied" 별도 필드. 전후 fs hash. write 차단만으로 read isolation 결론 금지.
12. prompt injection 7-layer 분류. evidence/source/test-output = untrusted data. 충돌 시 `instruction_conflict`/`blocked` (자동 추측 금지). structured critique만 handoff.
13. provider exit 0 ≠ review pass. parse/scope/schema/instruction conflict → fail-closed `blocked`.
14. 더 싼 lever 우선(usage-ledger baseline → parent_deterministic navi → packet_first/compact planning → deterministic preflight → retry 제거). 전부 live A/B gate. POC prerequisite로 default-on 금지.
15. Codex 추가 ≠ token 절감. Accepted-Change당 총비용으로 실측.
16. 이번 라운드 test/smoke/provider 호출 0. 기존 84/84 또는 HRNS test를 이번 PASS로 재주장 안 함.
17. HRNS `HarnessCommand`/`HarnessCommandEncoder`/`HarnessRunnerPort`/`ActionPolicy`/`ClosurePolicy`/`CompatibilityPolicy`/`WorkflowStateMapper` = provider-blind, POC 무변경.
18. Codex는 Kit source에 전무(clean slate). CLI `codex-cli 0.153.2` installed, live_capability unverified.

## 10.2 Revised Consensus (한쪽이 상대를 수용)

| ID | 내용 | 수용 방향 |
|---|---|---|
| RC1 | change-set 수집 = native `codex exec review --uncommitted`(staged+unstaged+untracked 표방) + `git status --porcelain=v2 -z` deterministic manifest(감사·drift 검출). rename/copy/binary/submodule/LFS는 canary 전까지 typed preflight로 block. | **claude ← codex** (claude03 "새 builder" 철회, §3.2) |
| RC2 | Secondary LLM 자산: **"그대로" 재사용 함수 없음.** evidence-index + claim-evidence 엔진이 최선이나 신규 `finding_anchor` claim_type + Codex namespace + junction/symlink 경로 정책 + byte cap + explicit encoding + fail-closed 필요. | **claude ← codex** (claude03 §7.1 등급 하향, §3.1) |
| RC3 | Claude runner gap 4건(cancellation token / output byte cap / child encoding / residual verification) — Codex adapter는 복사 금지, 처음부터 typed. | **claude ← codex** (§3.3) |
| RC4 | `authorized_target_file`는 read-scope pointer로만, `write_target`/`authorized_write` 필드로 노출 금지. | **claude ← codex** (§3.6) |
| RC5 | auth 격리: `--ignore-user-config`가 `CODEX_HOME` 격리 미보장 가정. `--ignore-rules` ≠ AGENTS/auth. | **claude ← codex** (§3.5) |
| RC6 | HRNS 결합 vocabulary: "실행 제어 경계 provider-blind / 진단·복구·bridge observability Claude-coupled". claude03의 8곳 file:line + codex03 vocabulary 병합. | **양방 병합** |
| RC7 | Review Packet: codex03 §8.2의 `codex_review_request` + result(`verdict`/`findings[{id,severity,file,line,summary,evidence_refs,confidence}]`/`scope_observed`/`risks`/`open_questions`)를 AR-C2b 출발점 채택(non-final). write affordance 필드 제외. | **claude ← codex** (§3.6, §6) |
| RC8 | token 절감: codex03 §11의 5단계 lever 우선순위 + "unchanged scope cache"(manifest hash 재사용) + "accepted-change linkage"(usage ledger ↔ acceptance) 채택. | **claude ← codex** (§8) |
| RC9 | offline gate: codex03 §13.1의 8개 추가 케이스(cancellation/residual/byte truncation/exit0+invalid/junction·symlink/env allowlist/rename·copy·binary·submodule·LFS block/manifest hash TOCTOU) 채택. | **claude ← codex** (§9.1) |
| RC10 | Secondary LLM subsystem + State surface 사실 기반 = codex03 §7.1/§4.3 정독 결과 채택. `claude-invoke-core.ps1` 세부(14 rejection reason, ledger 필드 전수) + `SECONDARY_LLM_LANE.md:88` line 인용 = claude03 유지. | **양방 병합** |

## 10.3 Remaining Divergence (미해결 — 통합 계획서/owner)

| ID | claude | codex | 성격 |
|---|---|---|---|
| D1 | authoritative review base = 열린 결정(uncommitted default) | `uncommitted` 하나로 확정, base/commit은 후속 | Codex가 Git Owner(commit 수행)이므로 커밋된 작업엔 base/commit review 필요. **첫 최소 native 경로 = uncommitted 합의; 정본 base는 open** (§4.2) |
| D2 | (미정) | acceptance criteria를 text vs pointer/hash — quality/cost 차이 open (§14.6) | **open** (통합 계획서 A/B) |
| D3 | (미정) | POC artifact를 release 포함 vs developer-only — open (§14.7) | **open** |
| D4 | phase namespace AR-C0..C10 (~8 압축) | 미번호, 8개 POC 속성 | 제안서 §28: 어느 쪽도 정본 아님. **통합 계획서에서 단일 namespace 확정** |
| D5 | 파일명 `invoke-codex-review.ps1` + `codex-review-adapter.ps1` + `codex-review-contract.ps1` | `invoke-codex-review.ps1` + `codex-review-runner.ps1` + result schema | cosmetic. ≤3 합의. **통합 계획서** |
| D6 | Coverage 표에 Partial Read 열 유지 | Partial을 Search Only로 보수적 통합 | 방법론 차이, 판정 대상 아님 |

## 10.4 Requires Experiment (source로 확정 불가 — harmless fixture only)

| ID | 질문 |
|---|---|
| E1 | `codex exec review --uncommitted`가 rename/copy/binary/submodule/LFS를 어떤 형태로 실제 review하는가 |
| E2 | `--sandbox read-only`가 working root **밖 read**를 제한하는가, write만 제한하는가 (canary matrix) |
| E3 | `--ignore-user-config` + `--ephemeral`이 project/nested `AGENTS.md`, auth(`CODEX_HOME`), session에 미치는 정확한 영향 |
| E4 | JSONL event ↔ final structured output의 안정적 분리 + 정확한 CLI 호출 문법·순서·Windows quoting |
| E5 | CLI timeout 후 Windows child process 잔존 여부 |
| E6 | live auth 유효성 + 이 host에서 noninteractive 호출 성공 여부 (installed=true, live_capability=unverified) |
| E7 | quota/usage failure의 exit/output shape |
| E8 | structured finding quality + false-positive baseline; Claude-only vs Claude+Codex Accepted-Change당 total token·시간 |

## 10.5 External Owner Decision (사용자 / Integration Owner)

| ID | 결정 |
|---|---|
| O1 | Native UI QA를 live Kit POC mutation의 hard prerequisite로 둘지 (분석·spike·경제성 baseline은 병행 합의) |
| O2 | A/B graduation threshold (표본 수, task class 분류, human review time 측정법, finding precision/recall + regression 발견률, reviewer 추가 비용 허용치) |
| O3 | POC artifact release 포함 vs developer-only (D3) |
| O4 | dual-provider opt-in 조건 (어떤 risk/task class부터; 기본 1 round 초과 조건; disagreement resolver — codex 권고: high-risk·contract/security/transaction/compatibility 변경부터, safety·acceptance·source-evidence 충돌은 Human) |
| O5 | Router 소유자 = Harness Runtime 명문 확정 + HRNS `ActionPolicy`(허용 action) ↔ Harness Router(허용 action 내 provider/lane 선택) 책임 분리 (codex03 §1.4 boundary 조건) |
| O6 | provider executable = bundled runtime vs host dependency (양측 lean: 초기 optional host dependency; packaging은 runtime contract 안정 후) |
| O7 | `agent_runtime` optional State extension 시점 (AR-C9 / 훨씬 뒤; POC 아님) |
| O8 | "external review"가 영구히 `run-cycle.ps1` 밖(Secondary LLM처럼) vs AR-C4+에서 안으로 |
| O9 | 분리된 backlog 2건 확정: HRNS `queue.active.*`/`blocked_reason`/`last_updated_at` requiredness hardening (claude03 §3.7); Kit smoke 인벤토리 문서 drift 95→100 (claude03 §3.7). 둘 다 Agent Runtime과 무관한 독립 task |

---

# 11. Remaining Divergence (통합 계획서 진입 전 정리)

§10.3의 D1–D6이 전부. **아키텍처 수준 발산은 0.** 남은 것은:
- D1(review base 정본) — 실험 E1과 결합해 통합 계획서에서 결정
- D2/D3(acceptance criteria 전달 형태, release 포함) — A/B 설계와 함께
- D4/D5(phase namespace, 파일명) — cosmetic, 통합 계획서 owner(Codex) 확정
- D6(coverage 방법론) — 판정 불요, 두 표현 다 정직

즉 **통합 계획서 착수를 막는 아키텍처 이견은 없다.** 막는 것은 실험(E1–E8)과 owner 결정(O1–O9)이다.

---

# 12. Requires Experiment (재확인)

§10.4의 E1–E8. 이 중 **POC 구조를 좌우하는 것**: E2(sandbox read 범위 → sanitized bundle vs 실 repo `-C`), E6(live 호출 가능성 → POC가 offline로만 끝나는지), E1(native review의 Git object 처리 → preflight block 범위). 나머지(E3/E4/E5/E7/E8)는 계약 세부·품질 baseline.

**실험은 harmless 임시 Git repo + non-secret marker에서만. production repository / real secret / global config 미사용.**

---

# 13. External Confirmation Questions (사용자/외부 reviewer)

1. AR-C2a Codex capability spike(신규 파일 0, harmless fixture, canary E1–E5/E7)를 진행해도 되는가? live Codex 호출을 이 host에서 1회 이상 수행하는 것을 승인하는가(E6)?
2. Native UI QA 완료를 live Kit POC mutation의 hard prerequisite로 둘 것인가, 아니면 분석·spike와 병행 허용인가(O1)?
3. `D:\harness-kit`에 신규 파일을 추가하는 시점의 rollback contract(사전 verified zip snapshot + change manifest + SHA-256)를 승인하는가?
4. review base 정본: `uncommitted` 우선(첫 POC) + base/commit은 후속으로 확정해도 되는가, 아니면 explicit manifest를 첫 POC부터 정본으로 요구하는가(D1)?
5. Router 소유자 = Harness Runtime, HRNS `ActionPolicy` ↔ Harness Router 책임 분리를 명문 원칙으로 확정하는가(O5)?
6. 분리된 backlog 2건(HRNS queue requiredness hardening / Kit smoke 인벤토리 문서 95→100)을 Agent Runtime과 별개 bounded task로 각각 처리하는 데 동의하는가(O9)?
7. 통합 계획서(Round 3 원래 단계, `doc/hrns_now_agent_runtime_evolution_plan.md`)를 Codex(Integration Owner)가 작성하고 Claude가 adversarial 최종 검토만 하는 순서를 유지하는가?

---

# 14. Final Cross-Review Recommendation

**두 독립 보강 감사가 수렴했다.** 아키텍처 이견 0. 공통 사실 기반은 다음 한 문장으로 요약된다:

> Claude production path·HRNS·State·bridge를 그대로 둔 채, `codex exec review --uncommitted`를 native scope 해석기로, `git status --porcelain=v2 -z` deterministic manifest+SHA-256을 감사·drift 검출기로 쓰는 **수동·default-off·read-only Codex reviewer**가 실제로 안전(sandbox read 격리·instruction precedence·mutation 부재)하고 경제적(Accepted-Change당 총비용)인지 먼저 증명한다.

**다음 순서**(이 문서로 04 완료):

```
04 cross review (완료 — 아키텍처 이견 0, 실험·owner 결정만 남음)
  → 사용자/외부 확인 (§13의 7문항)
  → harmless Codex capability 실험 (E1–E8, 신규 파일 0 spike부터)
  → "standalone read-only reviewer POC를 구현할지" 단일 결정
  → (승인 시) Codex(Integration Owner)가 doc/hrns_now_agent_runtime_evolution_plan.md 작성
  → Claude adversarial 최종 검토
```

**이 문서는 Integrated Plan을 확정하지 않는다(§36).** generic provider framework, provider router, Codex write lane, State required field, 자동 debate, 자동 commit, Ollama runtime 통합, Claude runner 즉시 공통화 — 전부 범위 밖. POC가 안전·품질·비용 증거를 낸 뒤에만 확장 순서를 논의한다.

---

## 프로토콜 §37 12문항 답변

1. **`D:\harness-kit` 전체 inventory를 실제로 확인했는가?** — 예(claude03 §2.1, 246개). 이번 04에서는 codex03 §2의 inventory(동일 246개, 다른 범주 분할)와 대조해 두 표가 정합함을 확인.
2. **본문을 끝까지 읽은 파일은 몇 개인가?** — claude03: Kit FULL 24 + PARTIAL 3. codex03: Kit FULL 61. 04에서 codex03의 넓은 정독(state-surface/validate-ops/secondary-llm lib 17/추가 docs 7)을 사실 기반으로 채택(§5.1).
3. **search-only 파일은 몇 개인가?** — claude03: Kit 173개 .ps1 전부 term sweep. codex03: 178개. 두 스윕이 동일 term list로 수렴.
4. **내용 미검토 파일은 몇 개인가, 왜인가?** — 양측 03 모두 "미검토 0"(전 파일 최소 grep) 표방하되, **본문 미완독**: Kit `.ps1` ~100 smoke + 대형 wrapper/queue/strategy 모듈. 사유: POC dependency closure 밖(회귀 gate로만 관련) / provider seam이 그 위·아래 / python은 sidecar(PS1 authoritative). §5.3에 양측 공통 한계로 명시.
5. **Agent Runtime 변경 dependency closure를 모두 읽었는가?** — POC의 직접 source/contract/release/doc closure는 양측이 각자 읽음(claude03 §5, codex03 §5.1). 인접 기존 smoke ~89–100개 전체 본문 완독은 양측 모두 미수행 — Harness 전체 regression closure 완독은 주장하지 않음.
6. **Claude runtime의 실제 process launch/seam을 함수 수준에서 확인했는가?** — 예. 양측 03이 독립적으로 3개 `Start-ClaudeProcess` + `claude-invoke-core.ps1` 함수 경계 확인. 04에서 codex03이 잡은 gap 4건(cancellation/byte cap/encoding/residual)을 claude 표에 병합(§3.3).
7. **Secondary LLM helper의 함수 수준 재사용 가능성을 확인했는가?** — 예. claude03 ~25개 + codex03 ~55개 전수 판정. 04 결론: **"그대로" 재사용 함수 없음**, evidence-index/claim-evidence 엔진이 최선(신규 `finding_anchor` claim_type + hardening 필요) (§3.1).
8. **execution packet schema 2.0을 실제 source에서 확인했는가?** — 예. 양측이 `code-role-sliced-runner.ps1`의 `00-wrapper-input.json` v2 생성 field 전수 + `Test-RoleSlicedWrapperInputPacketIdentity` 재검증 항목 확인. `authorized_target_file`은 read-scope pointer로만(§3.6).
9. **01/02에서 수정해야 할 가장 중요한 사실 3개는?** — 양측 03이 독립적으로 동일: (1) HRNS는 실행 경계에선 provider-blind이나 진단·복구·bridge는 Claude-coupled. (2) `claude-invoke-core.ps1`은 process supervisor 아님(launch는 runner별 `Start-ClaudeProcess`). (3) Secondary LLM Git evidence(`diff --name-only/--stat/--check`)는 complete change set 아님(unstaged only).
10. **현재 제안보다 더 작은 첫 POC가 가능한가?** — 예. router/State/HRNS/run-cycle 없이 **수동 `codex exec review --uncommitted` lane 하나** + `git status --porcelain=v2 -z` 감사 manifest. AR-C2a spike는 신규 파일 0. codex03의 native-CLI 위임으로 claude03보다 더 작아짐(§3.2, §6).
11. **Codex 추가보다 먼저 token cost를 낮출 기존 Harness 기능은?** — usage-ledger 1.1 baseline 측정(1순위) → parent-deterministic navi → packet-first/compact planning → deterministic no-provider preflight → 불필요한 retry/preflight provider call 제거. 전부 구현됨, default 아님, live A/B gate 필요(§8, codex03 §11 우선순위 채택).
12. **현재 evidence만으로 Integrated Plan 작성 단계로 넘어가도 되는가?** — **아니다.** 아키텍처 이견은 0이나 §12의 8개 실험(특히 E2 sandbox read 범위, E6 live 호출 가능성, E1 native review Git object 처리)과 §10.5의 owner 결정 9건이 미해결. 04 → 외부 확인 → harmless 실험 → 단일 POC 결정 순서.

---

## Mutation / Hygiene

- HRNS-NOW production source 변경: 없음
- `D:\harness-kit` 변경: 없음
- 사용자 global config / Registry / 외부 workspace: 접근·변경 없음
- `git add`/`commit`/`push`/`amend`/`reset`/`restore`/`checkout`/`stash`/`rebase`/`clean`: 없음
- build / Gradle / Harness smoke / test 실행: 없음
- live Claude / Codex / Ollama / 네트워크 호출: 없음
- 사용한 명령: `Read`(문서 대조) — 이번 04는 두 03 문서 정독 + 01/02 참조가 주 작업. 추가 source 명령 미실행(claude03의 source 감사 결과 재사용).
- 새로 작성한 파일: `doc/revolution/revOpinion_claude04.md` (사용자 지시)
- 이 문서의 staging/commit 여부는 Git Owner(Codex)와 사용자의 판단이다.
- 다음 단계: §13의 외부 확인 → harmless Codex capability 실험. 그 전까지 production code 수정 없음. Integrated Plan 미작성.
