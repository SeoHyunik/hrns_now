# HRNS-NOW Agent Runtime Evolution — Integrated Plan Targeted Revision Verification (Claude)

- 문서 성격: Targeted Revision Verification. 새 architecture review 아님.
- 역할: Harness Contract / Security / Compatibility Auditor.
- 검증 대상: `doc/hrns_now_agent_runtime_evolution_plan.md` (1064줄, 헤더 기준일 2026-09-04, targeted revision 재검증일 2026-09-07)
- 기준 finding 문서: `doc/revolution/claude_integrated_plan_adversarial_review.md` (`PLAN_REQUIRES_REVISION`, BLOCKER 0 / HIGH 1 / MEDIUM 6 / LOW 6)
- 확인 범위: H1·M1–M6·L1–L6이 (1) 실제 plan에 반영됐는가 (2) 이전 finding을 닫는가 (3) 새 architecture/security/compatibility/rollback contradiction을 만들지 않았는가. 그 세 가지만.
- source 확인: `D:\harness-kit` (Git 저장소 아님, `kit_version=2026.09.02`) read-only 직접 확인. H1처럼 source-dependent한 항목은 추측하지 않음.
- 상태: 분석 단계. 이 문서 저장 외 mutation 0. `git add`/`commit`/`push` 없음. live provider 호출 0. `Read` + read-only `grep`/`ls`/`git status`만 사용.

---

# 1. Final Verdict

## `PLAN_APPROVED_FOR_EXTERNAL_CONFIRM`

이전 review가 계획서 수정을 요구한 **H1 + M1–M6이 전부 닫혔고**, L1–L6도 반영됐다. targeted revision이 새로운 architecture / security / compatibility / rollback blocker 또는 plan-level HIGH를 만들지 않았다. 남은 것은 LOW 수준의 문구 정밀도와 implementation-time detail뿐이다.

- **BLOCKER: 0** — 구현을 막는 결함 없음
- **HIGH: 0** — H1이 닫혔고 신규 HIGH 없음
- **MEDIUM: 0** — M1–M6 전부 닫힘
- **LOW: 잔여 1건 (L3 문구 뉘앙스, non-blocking)** + 구현자 참고 note 1건

`PLAN_APPROVED_FOR_EXTERNAL_CONFIRM`은 **production implementation 승인이 아니다.** 다음 순서는 그대로다:

```text
Claude revision verification (이 문서)
→ user / external confirmation
→ explicit production implementation approval
→ D:\harness-kit implementation (§18 파일 + §19 순서만)
```

Capability experiment는 재개하지 않는다. HRNS-NOW를 먼저 수정하지 않는다.

---

# 2. H1 Closure

## 2.1 이전 finding

> §18의 `scripts/lib/secondary-llm/**` "변경 0" 제외 주장이, 계획이 smoke 6개를 추가하는 것과 양립하지 않는다. `secondary-llm-docs-scan.ps1`이 live `scripts/smoke/*.ps1` 개수를 `secondary-llm-docs-calibration.ps1`의 하드코딩 상수와 대조하고, SMOKE_INDEX/MAP 텍스트가 그 상수값을 명시하는지도 검사한다. smoke 추가 시 상수·fixture default·문서 텍스트를 갱신해야 하며, 계획서 §18 제외목록과 §22 표현이 실제 gate 메커니즘과 불일치한다.

## 2.2 Source 재확인 (D:\harness-kit, read-only)

| 확인 항목 | Source | 관측값 | 계획 반영 |
|---|---|---|---|
| calibration 상수 정본 | `secondary-llm-docs-calibration.ps1:71-79` `Get-SecondaryLlmDocsMismatchExpectedSmokeInventory` | `scripts_smoke_total=95`, `secondary_llm_smoke_total=36`, `automatic_offline_unique=84`, `manual_live=11`, `scripts_root_smoke_total=0` (하드코딩) | §22 baseline `95/84/11/36/0` 과 **정확히 일치** |
| live 전체 smoke 개수 대조 | `secondary-llm-docs-scan.ps1:158,162` `Get-ChildItem scripts/smoke -Filter '*.ps1'` → 상수와 `-ne` 비교 | live `ls scripts/smoke/*.ps1` = **95** | Codex smoke 6 추가 → 101 → 상수 `95→101` 필요. §18.3 반영 |
| Secondary LLM prefix 개수 대조 | `secondary-llm-docs-scan.ps1:160,168` `Where-Object { $_.Name -like 'smoke-secondary-llm-*' }` → 상수와 `-ne` 비교 | live `smoke-secondary-llm-*.ps1` = **36**; 신규는 `smoke-codex-readonly-review-*` prefix | prefix 불일치이므로 **36 유지**. §18.3 line 740 claim = **source-accurate** |
| scripts 루트 smoke 개수 대조 | `secondary-llm-docs-scan.ps1:159,165` `Get-ChildItem scripts -Filter 'smoke-*.ps1'` (루트 한정) | live 루트 `smoke-*.ps1` = **0**; 신규는 `scripts/smoke/` 아래 | **0 유지**. §18.3 line 740 claim = source-accurate |
| SMOKE_INDEX.md 텍스트 상수 명시 검사 | `secondary-llm-docs-scan.ps1:179-187` regex: `Current smoke inventory count is <95>` (error), `automatic/offline smoke suite count is <84>` 또는 `final count is <84>` (error), `Current live/manual smoke count is <11>` (warning) | `SMOKE_INDEX.md:9-12,140,237` 에 실제 존재 | SMOKE_INDEX.md 텍스트 갱신 필요. §18.3 문서목록에 포함 |
| scan-scope 문서 count 문장 검사 | `secondary-llm-docs-scan.ps1:115-150` `Test-SecondaryLlmCurrentSmokeCountStatements` — 6개 scan-scope 문서 전수 regex | `HARNESS_KIT_MAP.ko.md:172-175`, `HARNESS_KIT_MAP.en.md:40,178-181`, `SMOKE_INDEX.md` 에만 매칭 문장 존재. `SECONDARY_LLM_LANE.md`(line 255: SMOKE_INDEX로 위임), `WORKSPACE_SPEC.md`, `ROADMAP.md` 에는 매칭 count 문장 **없음** | MAP.en/ko + SMOKE_INDEX만 갱신 대상. 셋 다 §18.3 포함. 나머지 3개 문서는 강제 편집 불필요 |
| fixture default | `smoke-secondary-llm-docs-scan.ps1:37` `New-DocsScanFixture` param `[int]$SmokeTotal = 95` — clean fixture(`:155`)가 이 값으로 95개 fixture 파일 생성, 상수와 비교 | clean fixture는 상수 bump 시 lockstep 필요 | §18.3 table row 2 `$SmokeTotal: 95→101` 반영. **source-accurate** |
| calibration smoke가 count 값 assert 하는가 | `smoke-secondary-llm-docs-calibration.ps1` 전체 | kind/schema_version/categories/severities/forbidden_patterns/artifacts 만 검사. **count 값 비검사** | carve-out 불필요. plan이 이 파일을 목록에 안 넣은 것이 옳음 |
| `secondary-llm-docs-scan.ps1` 자체 코드 변경 필요 여부 | `Test-SecondaryLlmSmokeInventoryDocs`, `Test-SecondaryLlmCurrentSmokeCountStatements`, `expected_smoke_names` 루프(`:189`) | 신규 codex smoke를 자동 포함(전체 `*.ps1` count), prefix/루트 분리 이미 정확, `expected_smoke_names`는 하드코딩 Secondary 목록만 순회 | **코드 변경 0**. §18.3 line 740 "계산·비교 semantics는 변경하지 않는다" = **source-accurate** |
| `smoke-smoke-index-consistency.ps1` (offline suite, `scripts/smoke/`) | `:164-230` `expected_smoke_counts` 소비 + live `scripts/smoke/*.ps1` 재계산(`:172`) + SMOKE_INDEX `### 3.2 Automatic/offline execution array` 파싱(`:174-178`)으로 `$offlineCount` + kit-wide `$powerShellCount`(`:181`)·`$bomAuditFiles`(`:187`) 재계산 + README `## Current Validated Baseline` / MAP.en `### 1.0` / MAP.ko `### 1.0` / ROADMAP `## 1.2 Current Validated State` 섹션이 그 수치를 담는지 검사(`:207-226`) | **코드 변경 0.** (constants + SMOKE_INDEX.md 오프라인 배열에 codex offline smoke 5개 등재 + README/MAP.en/MAP.ko/ROADMAP baseline 섹션 수치 재계산) 으로 충족. `Doctor...193`/`Validate-Ops...20` 하드코딩은 불변(§17=0), known-stale regex(87/76/167/19/164/230)는 101/89와 비충돌 | §18.3에 4개 baseline 문서 모두 포함, §22 line 892에서 이 smoke를 명시적으로 regression sequence에 포함, §22 line 896에서 "PowerShell 총계나 BOM 같은 다른 파생 수치도 live source에서 재계산" 명시 |

## 2.3 계획 반영 상태 (전 항목)

| plan 위치 | 반영 내용 | 판정 |
|---|---|---|
| §3 evidence table (line 107) | "smoke inventory expectation이 Secondary LLM docs calibration에 hard-code / Codex smoke prefix는 Secondary LLM count에 미포함 / §18.3 count-only carve-out과 101/89/12/36/0 reconciliation 필요" | ✓ 정확 |
| 헤더 (line 15-17) | revision 상태 + 2026-09-07 재검증일 + `95/84/11/36/0` baseline 명시 | ✓ |
| §17 표 (line 677) | "Secondary LLM runtime/schema/authority semantics 0 — 단, 신규 Codex smoke가 바꾸는 전역 smoke inventory count를 맞추기 위한 §18.3의 count-only calibration 예외는 허용" | ✓ 실제 gate 메커니즘과 정합 |
| §17 (line 685) | "신규 Codex 파일을 제거하고 §18.3의 count·문서·index 변경을 snapshot의 exact bytes로 복원하면 기존 workflow가 동일하게 작동" | ✓ |
| §18.3 표 (line 736-740) | `secondary-llm-docs-calibration.ps1`: 상수 `95→101 / 84→89 / 11→12`만. `smoke-secondary-llm-docs-scan.ps1`: `$SmokeTotal 95→101` + inventory expectation reconciliation만. `secondary_llm_smoke_total=36`·`scripts_root_smoke_total=0` 유지 근거를 prefix counting으로 명시. `secondary-llm-docs-scan.ps1` 계산 semantics 불변 | ✓ 전부 source-accurate. "count-only reconciliation"으로 한정 |
| §18 제외목록 (line 753) | "`scripts/lib/secondary-llm/**`의 runtime/schema/authority semantics. 단, `secondary-llm-docs-calibration.ps1`의 §18.3 count-only reconciliation은 예외" | ✓ |
| §18 (line 756) | "§18.3의 두 count-only 파일도 허용된 상수/fixture expectation 외 semantic diff가 생기면 즉시 중단한다" | ✓ semantic drift stop-gate |
| §19 step 3 (line 764) | "§18.3의 count-only calibration 두 파일과 원본 bytes까지 포함해 exact planned change manifest와 rollback file list를 확정" | ✓ |
| §19 step 11 (line 772) | "live inventory를 재계산하고 expected smoke constants를 명시적으로 reconciliation한 뒤 SMOKE_INDEX·MAP·ROADMAP·README와 일치" | ✓ 사람이 상수를 갱신하는 실제 동작으로 서술 |
| §22 (line 884-896) | baseline `95/84/11/36/0` + target `101/89/12/36/0` + reconciliation sequence(`live inventory recompute → hard-coded expected smoke constants 명시적 reconciliation → SMOKE_INDEX/MAP/ROADMAP/README consistency update → secondary-llm docs-scan 및 smoke-index consistency smoke PASS`) + "PowerShell 총계나 BOM 같은 다른 파생 수치도 live source에서 재계산" | ✓ 이전 review가 지적한 "live에서 recompute" 오해 문구가 "사람이 하드코딩 상수를 명시적 reconciliation" 으로 교정됨 |
| §24 rollback (line 951, 958) | 보존 목록에 `secondary-llm-docs-calibration.ps1` + `smoke-secondary-llm-docs-scan.ps1` 원본 hash·bytes 추가. step 4에 "count-only `secondary-llm-docs-calibration.ps1`과 `smoke-secondary-llm-docs-scan.ps1`이 명시적으로 포함된다" | ✓ rollback 완전성 확보 |
| §26.1 (line 1000) | "Secondary LLM helper reuse … §18.3 count-only calibration은 helper reuse나 runtime coupling이 아님" | ✓ |
| §28 (line 1045-1046) | 신규 checkbox 2개: "smoke inventory consistency mechanism이 live-compute인지 hard-coded calibration인지 source로 확인" + "hard-coded calibration일 경우 narrow smoke-count reconciliation 대상 파일과 rollback bytes가 exact change manifest에 포함" | ✓ 이전 review §12.3 누락 항목 반영 |
| §28 (line 1050, 1059) | rollback checkbox에 "H1 count reconciliation 파일의 exact-byte 복원" 포함. "허용된 유일한 Secondary LLM 영역 예외는 §18.3의 smoke inventory count-only reconciliation이다" | ✓ |

## 2.4 세부 확인 결과

- calibration 허용 수정이 **smoke inventory count-only인가** → **예.** 상수 3개(`scripts_smoke_total`, `automatic_offline_unique`, `manual_live`) 및 fixture default 1개(`$SmokeTotal`). category/severity/forbidden_pattern/expected_artifact/expected_implementation_files/expected_smoke_names/scan_scope는 불변. plan §18.3·§18 line 756이 이 경계를 명시.
- `smoke-secondary-llm-docs-scan.ps1` 허용 수정이 **fixture/count reconciliation에 한정되는가** → **예.** `$SmokeTotal` 기본값 + clean fixture가 상수와 lockstep 되도록 하는 것뿐. runner 로직·case 구조 불변.
- `secondary_llm_smoke_total=36` 유지가 **실제 prefix counting source와 맞는가** → **예.** `secondary-llm-docs-scan.ps1:160` `-like 'smoke-secondary-llm-*'`. 신규 `smoke-codex-readonly-review-*`는 비매칭. live 관측 36 확인.
- Secondary LLM runtime/schema/authority semantics **변경 0인가** → **예.** `secondary-llm-docs-scan.ps1`(scan 엔진), `secondary-llm-docs-mismatch.ps1`(candidate), `secondary-llm-*` runtime/advisory/claim-evidence/capability 모듈 전부 미접촉. schema_version·authoritative·runtime_integrated 필드 불변.
- §18 / §19 / §22 / §24 / §28이 **같은 carve-out을 일관되게 반영하는가** → **예.** 위 표 참조. 6개 지점 모두 "count-only reconciliation, semantic drift 시 stop-and-reapprove" 로 정합.
- rollback에 **두 count-only 파일의 exact original bytes가 포함되는가** → **예.** §24 line 951 (보존) + line 958 (복원 step 4) + §28 line 1050.

## 2.5 잔여 note (non-blocking, 구현자 참고)

`smoke-smoke-index-consistency.ps1:207-226` `current_baseline_*_matches_live_inventory` case는 재계산된 **PowerShell 스크립트 총계**(신규 production `.ps1` 5개 + smoke `.ps1` 6개 = +11)와 **BOM audit 파일 총계**(신규 `.ps1`/`.schema.json`/`.md`)가 다음 **특정 섹션**에 나타날 것을 요구한다:

- `README.md` → `## Current Validated Baseline` … `## Directory Overview`
- `docs/HARNESS_KIT_MAP.en.md` → `### 1.0 …` … `### 1.9 …`
- `docs/HARNESS_KIT_MAP.ko.md` → `### 1.0 …` … `### 1.9 …`
- `docs/ROADMAP.md` → `## 1.2 Current Validated State` … `## 2. Build Principle`

plan §18.3은 이 4개 문서를 모두 목록에 넣었고 §22는 "PowerShell 총계나 BOM 같은 다른 파생 수치도 live source에서 재계산" 을 명시했으므로 **파일 수준에서는 커버됨.** 다만 어느 섹션·어느 파생 수치가 load-bearing인지가 plan 본문에 열거돼 있지 않다. 구현 시 §19 step 11 / §22 reconciliation sequence 실행에서 이 4개 섹션의 `smoke / offline / PowerShell / BOM` 수치를 함께 갱신하면 된다. 추가로 `HARNESS_KIT_MAP.en.md:40` ("the live smoke inventory is 95 total / 84 automatic-offline / 11 manual-live / 36 Secondary LLM … passes 84/84") 는 scanner-gated 문장은 아니지만 사람이 읽는 baseline 진술이므로 같이 `101/89/12/36` + `89/89` 로 맞춰야 한다.

이것은 revision이 새로 만든 문제가 아니라 H1이 원래 가리키던 메커니즘의 세부이며, plan의 문서 목록·regression sequence로 이미 도달 가능하다. verdict를 바꾸지 않는다.

## 2.6 판정

## `H1 = CLOSED`

새 contradiction 없음. carve-out은 count-only로 최소화됐고, source로 검증한 실제 gate 대상 파일 집합(calibration 상수 + `$SmokeTotal` default + SMOKE_INDEX.md + MAP.en/ko + ROADMAP/README baseline 섹션)을 plan §18.3 + §22 + §24 + §28이 전부 포함한다. `36`/`0` 유지는 실제 prefix/루트 counting 로직과 일치. rollback 완전.

---

# 3. M1–M6 Closure Matrix

| ID | 이전 finding | plan 반영 위치 | 반영 내용 | 새 contradiction | 판정 |
|---|---|---|---|---|---|
| **M1** | 기본 `-ScratchRoot`(system temp)가 한글/공백 profile에서 사실상 실패 — "예외"가 아니라 "일반 경로" | §2 (line 89), §5 (line 176, 185), §18.3 (line 729), §27, §28 | system temp = **조건부 후보**로 재프레이밍. 비-ASCII Windows profile에서 explicit `-ScratchRoot`가 "예외 복구가 아니라 정상적인 사용 경로". ASCII/containment/reparse preflight 실패 시 provider 호출 전 차단, 자동 생성·추측 금지. automatic scratch provisioning은 이번 POC에 추가하지 않음 명시. `docs/CODEX_READONLY_REVIEWER.md`가 안전한 ASCII scratch 예시(`C:\hrns-codex-scratch\<run-id>`)·양방향 overlap 금지·reparse 금지·leaf 생성/정리 정책 설명 | 없음. invariant 8, §20.3 overlap-reject smoke 유지 | **CLOSED** |
| **M2** | §26.1 `REJECT`가 영구 거부와 "이번 POC 거부"를 혼용; §26.2와 모순 | §26.1 (line 982-1004) | 판정 어휘 5-value 정의: `ACCEPT` / `REVISE` / `DEFER` / `REJECT_FOR_THIS_POC` / `REJECT`. `State agent_runtime` → **`DEFER`** ("Harness contract graduation 뒤 optional diagnostic projection 필요성을 별도 재판정") — §26.2 line 1012와 정합. `Codex write lane` → `REJECT_FOR_THIS_POC`. `Ollama runtime integration` → `REJECT_FOR_THIS_POC`. 영구 설계 거부(`Native review --uncommitted`, `Bundle/filesystem review`, `Multi-provider required subscription`, `Automatic debate`, `Auto commit`)만 `REJECT` 유지 | 없음. §26.1 ↔ §26.2 모순 해소 (영구 `REJECT` 항목 중 §26.2 재검토 목록에 있는 것 없음) | **CLOSED** |
| **M3** | invariant 14 "single paid provider … 운용 가능"이 현재 사실(Claude 전용)을 과장 | §4 invariant 14 (line 134-153), §26.1 (line 1001) | 5-part 구조로 분리: **Current production fact** ("baseline Harness workflow는 Claude만으로 운용 가능, Codex subscription 불필요"), **deterministic-first / no-provider**, **Non-negotiable long-term invariant** ("여러 유료 frontier provider의 동시 subscription을 절대 필수로 요구해서는 안 된다"), **Claude + Codex** ("optional guarded dual-provider quality lane"), **Codex-only baseline execution** ("장기 aspiration, 현재 commitment 또는 이번 POC의 약속 아님"). 이전의 "어느 하나의 유료 frontier provider만으로도 운용 가능" 문구 삭제됨 | 없음. §7 Provider Availability와 정합, Codex-only 실행을 구현 대상으로 승격하는 문구 없음 (§6 non-goal line 193, §26.1 line 992-993) | **CLOSED** |
| **M4** | 허용 JSONL event allowlist가 계획서에 없음 (금지 목록만); smoke에 위임 | §9.1 신규 subsection (line 382-405), §15.1 (line 615) | versioned event allowlist를 **production contract 정본**으로 명시: `thread.started`(정확히 1) / `turn.started`(정확히 1) / `item.completed`(1+) / `turn.completed`(정확히 1, terminal, 뒤 event 금지). `item.completed.item.type` allowlist = `agent_message`, `error`(version + exact category/message shape pin된 known diagnostic subset). exact field shape 미관찰분(`thread.started`/`turn.started`/`item.completed`)은 **첫 provider 호출 전 0.153.2 disposable fixture로 재확인해 specification + golden fixture에 pin, wildcard/unknown field 불허**. "이 allowlist는 smoke 전용 표가 아니라 production contract의 versioned 정본이다"(line 398). 위반 시 `contract_invalid/unexpected_jsonl_event`로 result 폐기 | 없음. invariant 12, §14 taxonomy(`unexpected_jsonl_event`), §15.1과 정합. smoke(§20.2)는 이 정본을 table-driven으로 재검증 | **CLOSED** |
| **M5** | §27 owner decision 9개를 전부 "production 시작 전 고정"으로 과대분류 | §27 표 (line 1022-1034), §28 intro (line 1038) | "분류" 컬럼 추가: **PRODUCTION-ENTRY** = manual/live 호출 승인 횟수+fixture, 기존 scratch residue 소유/정리 권한 (2개, 이전 review의 #7·#8과 일치). **IMPLEMENTATION-TIME** = caps/timeout, `-OutputPath`, npm fallback, rename/copy, child env allowlist (safe default 명시). **GRADUATION** = n≥5 sample + 품질/비용 threshold. **RELEASE** = Kit version + archive 위치. §28 intro: "implementation-time, graduation, release 결정은 §27의 시점에 별도로 닫는다" | 없음. §28 checklist에 n≥5·version bump 미포함 확인. §28 line 1052 cap 항목이 "safe defaults 승인 + exact values는 구현 시 확정"으로 정합 | **CLOSED** |
| **M6** | envelope `files[]` schema가 staged+unstaged same-path(`base→staged→worktree`)를 단일 `sha256`로 표현 불가 | §8.1 (line 257-294), §10 (line 419-420), §11 (line 474-475), §12 (line 510, 520), §14 (line 598), §20.3 (line 808), §27 (line 1029) | 초기 POC는 same-path staged+unstaged 동시 변경을 **`blocked/ambiguous_mixed_source_basis`로 preflight block** (합치지 않음). basis 모델 고정: staged=`base→index`, unstaged=`index→worktree`, untracked=`absent→worktree`, deleted=명시 basis의 `present→absent`. envelope `files[]`를 `before:{basis, present, sha256?, line_count}` + `after:{basis, present, sha256?, line_count}` + `review_basis` + `declared_changed_lines:{before[], after[]}` 구조로 재정의 → 단일 SHA-256이 여러 상태를 대표하던 모호성 제거. finding audit는 `source_basis`(before\|after)와 해당 captured hash·line에 고정(§11). "복합 3-state envelope는 필요성이 입증된 후 별도 확장"(line 294) | 없음. initial POC에서 3-state 미지원 + 안전 block. 이전 review가 "안전하게 block한다면 ACCEPT 가능" 이라고 명시했던 조건 충족 | **CLOSED** |

---

# 4. LOW Closure Matrix

| ID | 이전 finding | plan 반영 | 판정 |
|---|---|---|---|
| **L1** | `prompts/codex-readonly-review.md`가 `prompts/` 네이밍 관례(전부 `*-system.md`) 위반 | §18.1 (line 701): `prompts/codex-readonly-review-system.md` 로 rename. "기존 Kit의 system prompt naming convention을 따르는 fixed reviewer instruction; evidence data와 분리, no write authority" | **CLOSED** |
| **L2** | `scripts/lib/codex/*.schema.json`(Kit 최초 `scripts/` 아래 `.json`)의 hygiene 비제외 + protected tree 포함을 명시 assert 안 함 | §18.2 (line 716) + §20.5 (line 842): `smoke-codex-readonly-review-security.ps1`가 "protected production tree에 있고 release hygiene에서 제외되지 않으며 staged release payload에 존재하는지" assert | **CLOSED** |
| **L3** | §13 "`StandardOutputEncoding`/`StandardErrorEncoding` … 설정 가능한 환경에서" — 불필요한 hedge (PS 5.1은 지원, `StandardInputEncoding`만 미지원) | §13 (line 554): "**target Windows/.NET contract에서** `StandardOutputEncoding`과 `StandardErrorEncoding`을 명시적 strict UTF-8로 설정하고, 실제 bytes/decoder 오류를 typed 처리. 이는 stdin raw-byte `BaseStream` 계약과 별개다." — "설정 가능한 환경에서" hedge 제거, stdin BaseStream 계약과 분리 명시 | **CLOSED (문구 뉘앙스 잔여, non-blocking)** — "target … contract에서"가 여전히 약한 조건절로 읽힐 수 있으나 오해 소지였던 "지원되면" hedge는 사라짐. §13이 LOW로 유지 |
| **L4** | §15.4 child env "필요한 최소값" 미정의 | §15.4 (line 650): 초기 allowlist 후보 명시 — `SystemRoot`, `WINDIR`, `COMSPEC`, `PATH`, `PATHEXT`, `TEMP`, `TMP`, + 검증 시에만 `CODEX_HOME`/auth plumbing/최소 runtime 변수. "최종 allowlist는 implementation-time gate에서 disposable fixture로 검증". §27에 IMPLEMENTATION-TIME 항목으로도 등재 | **CLOSED** |
| **L5** | §12가 base/current content bytes 캡처 명령을 명명 안 함 | §12 (line 528): "HEAD/base에 `git cat-file blob HEAD:<path>` 또는 `git show HEAD:<path>`, index에 `git cat-file blob :<path>` 또는 `git show :<path>`, worktree에 containment/reparse 검사를 통과한 .NET raw file API. Git argument는 shell 문자열로 이어 붙이지 않고 argument list로 전달" | **CLOSED** |
| **L6** | `TASK_CLASS_TOKEN_POLICY.md` §16이 Secondary-LLM(Ollama) 전용 — Codex 텍스트 혼재 위험 | §18.3 (line 731): "기존 `Secondary LLM Advisory Extension` §16에 Codex 내용을 섞지 않고, standalone Codex reviewer용 별도 subsection 또는 section을 추가한다. task class/budget tier/lane/role/provider/risk를 하나의 enum으로 합치지 않으며 optional provider와 실제 usage 측정 원칙만 연결" | **CLOSED** |
| (rec.) | §28에 최소 gate와 "재확인" soft 항목 구분 | §28 intro(line 1038)가 production-entry vs implementation-time/graduation/release 분리를 서술. checkbox 자체의 11항 재구조화는 미채택 | **부분 반영, non-blocking** |

---

# 5. Internal Consistency

targeted revision이 기존 invariant를 깨지 않았는지 재확인:

| invariant | 확인 | 결과 |
|---|---|---|
| `run-cycle.ps1` change = 0 | §4 inv 5, §17, §18 제외목록(line 748), §22(line 882) | 유지 |
| Claude runtime semantic change = 0 | §4 inv 5, §17, §18(line 752 `scripts/lib/claude/**`) | 유지 |
| `WORKFLOW_STATE` change = 0 | §4 inv 4, §17, §26.1 `agent_runtime` DEFER("POC에서 State 변경 0") | 유지 — M2가 라벨만 `REJECT→DEFER`로 바꿨을 뿐 State 불변은 그대로 |
| daily required files = 4 | §17, §22(line 876) | 유지 |
| repository bridge = 3 | §17, §22(line 877) | 유지 |
| HRNS-NOW source change = 0 | §1, §4 inv 4, §17, §18(line 754), §25 | 유지 |
| Codex = optional host dependency | §7(line 210), §17(line 682), §25 | 유지 |
| automatic/offline suite live provider call = 0 | §20(line 781), §22(line 872) | 유지 |
| no auto apply / commit / State transition | §4 inv 2/3, §6, §14(line 596 `completed` = 상태 전이 없음), §26.1(auto commit REJECT) | 유지 |
| Secondary-LLM count-only reconciliation ≠ runtime integration | §17(line 677), §18.3(line 740), §26.1(line 1000), §28(line 1059) | 명시적으로 4곳에서 구분 — runtime/schema/authority 결합 아님 확인 |

M4의 event allowlist 추가는 **계약 강화**(unknown → fail-closed)이지 표면 확대가 아니다. M6의 envelope 재구조화는 **동일 데이터의 shape 개선**이며 initial POC scope를 넓히지 않는다(3-state는 여전히 block). M2의 5-value 어휘는 **라벨링**이지 새 architecture 항목이 아니다.

---

# 6. New Regression Findings

**없음.**

targeted revision이 새로 도입한 architecture / security / compatibility / rollback contradiction은 발견되지 않았다. 구체적으로 확인한 잠재 회귀 지점:

1. **§8.1 `sha256?` optional화** — `present=false` / `basis=absent`(deleted의 after, untracked의 before)일 때 hash 부재는 `present` 불리언으로 표현되며 §11 audit이 `source_basis` 명시 snapshot 기준으로 검사하므로 정합. 모순 없음.
2. **§22 숫자 `101/89/12/36/0`** — source calibration 상수(`95/84/11/36/0`) + Codex smoke(5 offline + 1 manual) 산술과 일치(95=84+11, 101=89+12). §3·§17·§18.3·§28과 교차 일관.
3. **§9.1 event allowlist vs §14 taxonomy** — `unexpected_jsonl_event`가 §14 `contract_invalid` reason에 이미 존재. 신규 모순 없음.
4. **§26.1 5-value 어휘** — 모든 행이 5개 값 중 하나 사용. §6 non-goals(Execution Packet 재사용 강제 금지)와 `REVISE`("원칙만 참고, 복제하지 않음")가 정합.
5. **§27 분류 컬럼 vs §4 invariant** — "bounded input/output/process"는 §4 inv 6에서 불변, §27은 숫자만 implementation-time로 분류. §27 line 1034 "이 분류는 결정 시점을 분리할 뿐 hard gate를 완화하지 않는다" 명시.
6. **§18.3 carve-out vs §18 제외목록** — `smoke-secondary-llm-docs-scan.ps1`은 `scripts/smoke/` 파일이라 `scripts/lib/secondary-llm/**` 제외 bullet 범위 밖이며 다른 곳에서도 제외되지 않음. §18 line 756이 두 파일을 함께 stop-gate로 묶음. 모순 없음.

§12 (No Scope Expansion) 재확인: generic Provider Runtime / Router / common runtime / run-cycle integration / State projection / HRNS UI / Codex write / Ollama integration / automatic debate / auto commit — 전부 §6 non-goal + §26.1 disposition(DEFER 또는 REJECT_FOR_THIS_POC 또는 REJECT)로 유지. revision이 이 중 어느 것도 source graph에 선반영하지 않았다.

---

# 7. Production Entry Readiness

`PLAN_APPROVED_FOR_EXTERNAL_CONFIRM`은 production 구현 승인이 아니다. §28 entry checklist 평가:

- **H1 관련 checkbox (line 1045-1046, 1050)**: 반영됨. "smoke inventory consistency mechanism source 확인" + "narrow reconciliation 대상 파일·rollback bytes가 exact change manifest 포함" + "H1 count reconciliation 파일 exact-byte 복원". 이 문서가 그 source 확인(§2.2)을 완료했다.
- **이 verification checkbox (line 1040-1041)**: "Claude adversarial review의 필수 H1, M1–M6, LOW 수정이 이 plan에 반영됐다" + "Claude가 targeted revision을 검증하고 blocker 없음 판정" → 이 문서로 충족.
- **남은 production-entry 항목**: 사용자/외부 owner의 명시적 승인(line 1042), §18 exact manifest owner 승인(line 1044), verified clean zip + 실제 열람 검증(line 1048), 기존 scratch residue 소유권 확인(line 1049), `codex-cli 0.153.2` pin/resolver/feature matrix(line 1051), manual/live provenance(line 1053), no-production-root 재확인(line 1054), offline-0-calls 구조(line 1055), agent overlap 없음(line 1056), git-boundary 재확인(line 1057). 전부 **구현 착수 전 별도 절차**이며 이 verification의 범위 밖.

즉 plan은 **외부/사용자 확인 단계로 넘어갈 준비가 됐다.** 그 확인과 명시적 production 승인 전까지 `D:\harness-kit` 구현은 시작하지 않는다.

---

# 8. Final Recommendation

## `PLAN_APPROVED_FOR_EXTERNAL_CONFIRM`

1. **H1 = CLOSED.** carve-out이 count-only로 최소화됐고, source로 검증한 실제 gate 대상(`secondary-llm-docs-calibration.ps1` 상수 3개 + `smoke-secondary-llm-docs-scan.ps1` `$SmokeTotal` + `SMOKE_INDEX.md` + `HARNESS_KIT_MAP.en/ko.md` + `ROADMAP.md`/`README.md` baseline 섹션)을 plan §18.3·§22·§24·§28이 전부 포함한다. `secondary_llm_smoke_total=36`·`scripts_root_smoke_total=0` 유지는 `secondary-llm-docs-scan.ps1:159-160`의 prefix/루트 counting과 일치(live 관측 36/0 확인). `secondary-llm-docs-scan.ps1` 코드 변경 0 주장도 source-accurate. rollback에 두 파일 exact bytes 포함.
2. **M1–M6 = 전부 CLOSED.** scratch-root 재프레이밍 + 3-value→5-value disposition + provider-independence 5-part 분리 + versioned event allowlist 정본화 + owner decision 4-way 분류 + envelope before/after-with-basis 재구조화 + same-path staged/unstaged fail-closed. 각 수정이 이전 finding을 닫고 새 모순을 만들지 않는다.
3. **L1–L6 = 전부 반영.** L3만 문구 뉘앙스가 LOW로 잔존(비차단).
4. **신규 regression 0. scope expansion 0. 기존 invariant 유지.**
5. **잔여 note 1건 (§2.5)**: `smoke-smoke-index-consistency.ps1`의 `current_baseline_*` case가 4개 baseline-prose 섹션의 `smoke/offline/PowerShell/BOM` 파생 수치를 load-bearing으로 요구한다. plan §18.3 문서 목록 + §22 파생수치 재계산 문구로 파일 수준 커버됨. 구현 시 §19 step 11 실행에서 함께 갱신. verdict 불변.

## 다음 순서

```text
Claude revision verification (이 문서, 완료)
→ user / external confirmation
→ explicit production implementation approval
→ D:\harness-kit implementation (§18 파일 + §19 순서만)
```

이 verification이 통과했더라도 production 구현을 시작하지 않는다. capability experiment를 재개하지 않는다. HRNS-NOW를 먼저 수정하지 않는다.

최종 구현 원칙(plan §1064)은 유효하다:

> **Parent-owned bounded evidence → tool-free structured Codex review → deterministic audit → Human recommendation.**

---

## Mutation / Hygiene

- HRNS-NOW production source / `D:\harness-kit` / `WORKFLOW_STATE` / Registry / Claude·Codex global config / bridge / Git index·history: **변경 없음**
- `git add` / `commit` / `push`: 없음
- build / test / smoke 실행: 없음
- **live provider(Codex/Claude/Ollama) 호출: 없음**
- 사용한 read-only 관측: `Read`(plan 1064줄 전체, `claude_integrated_plan_adversarial_review.md`), `D:\harness-kit` source 직접 확인(`scripts/lib/secondary-llm/secondary-llm-docs-calibration.ps1`, `secondary-llm-docs-scan.ps1`, `scripts/smoke/smoke-secondary-llm-docs-scan.ps1`, `smoke-secondary-llm-docs-calibration.ps1`, `smoke-smoke-index-consistency.ps1`, `docs/HARNESS_KIT_MAP.en.md`/`.ko.md`, `docs/SECONDARY_LLM_LANE.md`, `docs/WORKSPACE_SPEC.md`, `docs/ROADMAP.md`, `scripts/SMOKE_INDEX.md`), `ls scripts/smoke`, `git rev-parse HEAD` / `git status --porcelain=v2`
- HRNS HEAD 확인: `87575273402fb2b50a83c0a6ab4689bc3ea6e311` (plan 헤더 기준과 일치), 작업트리에 revolution 문서는 untracked
- `D:\harness-kit` 확인: `kit_version=2026.09.02`, Git 저장소 아님, calibration baseline `95/84/11/36/0` (plan §22와 일치)
- 새로 작성한 파일: `doc/revolution/claude_integrated_plan_revision_verification.md` (이 문서, 사용자 지시)
- 이 문서의 staging/commit은 Git Owner(Codex)와 사용자의 판단이다.
