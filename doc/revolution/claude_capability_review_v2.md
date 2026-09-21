# HRNS-NOW Codex Capability Experiment V2 — Claude Independent Audit

- 문서 성격: Independent Auditor Protocol의 **Mode A — Capability Review (Claude 제출본, V2 대상)**
- 역할: Harness Contract Auditor / Security & Failure-path Reviewer / Adversarial Architecture Reviewer. Codex 결과 보완이 아니라 **틀릴 가능성을 적극 탐색**. Git Owner 아님.
- 감사 대상: `doc/revolution/codex_capability_experiment_v2.md` (V2, 2026-09-04)
- 대조: `codex_capability_experiment.md`(V1), `claude_capability_review.md`(V1 감사), `revOpinion_{claude,codex}03/04.md`, 현재 source/계약
- 상태: 분석 단계. 이 문서 저장 외 mutation 0. **live provider 호출 0.** `Read` + read-only `git status`/`find`/`cat`/`ls -la`/`where.exe`만 사용.

---

# 1. Verdict

## `REVISE_CAPABILITY_EXPERIMENT`

V2는 **V1 감사의 지적을 대부분 정직하게 닫은 강한 실험**이다. deterministic finding audit를 실제로 돌렸고(정상 1 accept + 거짓 6 reject + hash tamper 1 reject), canary evidence chain을 self-report에서 **command-output marker 대조**로 격상했으며, live-call 승인 provenance를 사전 기록했고, byte cap을 실증했으며, MCP/plugin discovery surface를 현재 CLI에서 제거했고, "bundle first" 과대주장을 스스로 철회했다. production 구현을 승인하지 않았고 비용/품질을 과대일반화하지 않았다.

**그러나 Integrated Plan을 작성할 수 없다.** 단 하나의, **POC 아키텍처를 결정하는** capability가 미검증이기 때문이다.

> **`shell_tool=false` + bounded inline evidence의 live semantic effect가 UNVERIFIED다** (V2 §7, §14, §16 C4).

이 결과가 POC의 전체 형태를 가른다:
- `shell_tool=false`가 실효하면 → provider가 filesystem/tool read를 잃고 bounded stdin evidence만 봄 → **tool-free inline reviewer**. host read boundary B의 exfiltration 벡터가 제거됨. 작은 경계.
- `shell_tool=false`가 실효하지 않으면 → 유일한 안전 경로는 **별도 OS/ACL isolation**(전용 Windows Sandbox / 제한 계정 / 검증된 ACL). 새 배포 표면, Windows Sandbox 의존성, ACL 관리 — 별도 experiment가 필요한 heavier lift.

두 경우의 blast radius가 근본적으로 다르므로 "plan을 두 갈래로 쓰고 pre-implementation gate에서 결정"은 plan이 아니라 decision tree다. **`shell_tool=false` live 검증은 Integrated Plan의 선행조건이지 plan 내부 gate로 미룰 수 없다.** 이 검증은 작다(1~2회 호출: `--disable shell_tool` 또는 feature flag가 실제로 filesystem read를 제거하는가 / bounded stdin evidence만으로 review가 완료되는가).

V2 §18은 정확히 이 질문("plan-level hard gate로 넘겨도 되는가, tool-free 대안을 먼저 실험해야 하는가")을 Claude에게 넘겼다. **내 답: 먼저 experiment 한 건.** 그래서 verdict는 `REVISE_CAPABILITY_EXPERIMENT` — V2가 나빠서가 아니라, 아키텍처를 결정하는 한 조각이 남아서다.

`BLOCKED`이 아닌 이유: boundary B는 알려진 Windows sandbox 한계이고 최소 2개 mitigation 후보가 있으며 그중 하나(`shell_tool=false`)는 문서화된 CLI feature로 live 확인만 남았다. 방향은 살아 있다.

---

# 2. Experiment Integrity

| 항목 | 판정 | 근거 |
|---|---|---|
| production / live Kit / State / Registry / global config / Git history 무변경 | **VERIFIED** | §11 독립 재확인: HRNS `git status` stray 0, `D:\harness-kit` **246 files** + `kit_version=2026.09.02` 불변, `~/.codex/config.toml` mtime **2026-08-13 13:48**(V1과 동일, 미변경) |
| live-call 승인 provenance 사전 기록 | **VERIFIED (개선)** | §2에 `live_call_approval` block, 상한 **3회 사전 고정 / 정확히 3회 사용**, preflight parser 실패 1회는 별도 계상. V1 BG-E 해소 |
| disposable fixture + non-secret canary만 | **VERIFIED** | §11 재확인: `CreatorTemp`에 `hrns-codex-*` 없음, `S:\tmp`·`%USERPROFILE%` canary 없음 |
| 임시 산출물 정리 | **VERIFIED (관측 범위)** | §19 "실험 산출물 0". 내 관측으로 leftover 없음 |
| Windows/PATH/WSL 분리 | **VERIFIED** | §12에서 현재 PS / child `-NoProfile` / fresh `cmd.exe` / Harness-like `ProcessStartInfo` 4환경 분리 |
| production 승인 / plan 승인으로 비약 | **NOT COMMITTED** (좋음) | §1 "production POC 승인이나 Integrated Plan 작성 승인이 아니다", §18 `NOT_READY`, §19 "Claude review 전 plan 미작성" |
| cost/quality 과대일반화 | **NOT COMMITTED** (좋음) | §13 "Native가 quality 열등하다고 말할 수 없다", §15 "'Codex가 더 싸다'·'provider 추가가 절감' 결론 안 냄", Q1 UNVERIFIED |
| **self-report 승격 문제 (V1 IC-2)** | **RESOLVED** | §9 evidence chain = actual marker → completed `command_execution` output exact match → raw JSONL exact match → final result exact match. 6/6 항목 세 연결 모두 true. 랜덤 marker를 모델이 추측 불가하므로 shell tool이 실제로 읽었다는 강한 증거 |
| **`.codex` 세션 store 활성 mutation** | **관측됨 (V2 INCONCLUSIVE 정확)** | §11 재확인: `history.jsonl`(14:08), `logs_2.sqlite`/`state_5.sqlite`/`goals_1.sqlite`(14:55), `models_cache.json`(14:59)이 실험 시간대에 write됨 = 상위 Codex 세션이 `.codex/`를 계속 씀 → `--ephemeral` 무흔적 미확정. 단 `auth.json` mtime **Aug 31**(미변경) |

**종합**: V2의 위생·정직성은 V1보다 뚜렷이 개선됐다. 승인 provenance·evidence chain·과대일반화 억제가 모범적. 잔여 integrity 관찰 1건: `--ephemeral` persistence는 "귀속 불가"가 아니라 **"세션 store가 실제로 write되고 있음이 positive하게 관측됨"**(§11). auth credential 파일 자체는 불변.

---

# 3. Verified Facts (V2)

deterministic 또는 재현 가능한 관측:

| # | 사실 | 근거 |
|---|---|---|
| VF-1 | deterministic finding audit prototype이 schema-valid 거짓 finding **6/6 reject** + tampered manifest `file_hash_mismatch` reject + 정상 finding accept | §6. 검사: file_id manifest membership / logical_path exact mapping / physical file 존재 / SHA-256 / line 존재 / changed-scope + changed-line membership / evidence_refs 존재 + SHA-256 / "test PASS"는 실제 실행 + status=passed + exit=0 |
| VF-2 | 실제 provider result: bundle-2 = 0 accept / 1 reject(`logical_path_mismatch`), combined-3 = 1 accept / 0 reject | §6 |
| VF-3 | final-result selector가 negative 3종 fail-closed: `invalid_jsonl` / `schema_missing_findings` / `jsonl_input_too_large` | §4 |
| VF-4 | canary 6위치(working root / parent / other drive `S:\tmp` / `%USERPROFILE%` / `.codex` 인접 / env var) 모두 shell tool로 read됨, **command-output marker exact match** | §9, §14 |
| VF-5 | `--disable apps/plugins` + browser/computer/image/multi-agent/skill/tool-suggest disable + `-c mcp_servers={}` + `--ignore-user-config` 조합에서 3 run 모두 `mcp_tool_call` event **0**, MCP metadata **0**, self-report `MCP_UNAVAILABLE`. V1 control run은 같은 CLI가 `list_mcp_resources` 실호출 | §7 |
| VF-6 | `--ignore-rules`는 AGENTS를 차단하지 않음(marker 3 run 모두 노출); 이 환경에서 필요한 local read allow rule까지 제거해 bundle-1 verdict=`blocked` | §7, §16 C2 |
| VF-7 | BOM 없는 UTF-8 manifest → PowerShell 기본 codepage 디코딩 → logical path mojibake → audit reject. **UTF-8 BOM manifest** → `src/한글 경로/검증 파일.kt` line 2 정확 보존 → audit PASS | §5 |
| VF-8 | fake supervisor 10 mode: large stdout/stderr 200,001 → 4,096 보존(`*_capped=true`); hang→`timed_out` residual=false; cancel→`cancelled` residual=false; child spawn+hang→tree kill child residual=false; nonzero→exit 7 보존 | §8 |
| VF-9 | `--ask-for-approval never`는 root position(`codex --ask-for-approval never exec`)에서만 parse; `exec` 뒤는 V1에서 parser 거부. 3 provider run에 적용됨 | §4 |
| VF-10 | non-Git bundle에 `--skip-git-repo-check` 필요. 첫 CLI preflight가 이 option 부재로 provider 호출 전 실패 | §4 |
| VF-11 | Git edge preflight: `git diff --cached --name-status -M -C --find-copies-harder`→`R100`/`C100`, `--numstat`→binary, `git ls-files --stage`→mode `160000`(submodule), `git check-attr filter`→`lfs` 모두 deterministic 판별 가능 | §11 (실험) |
| VF-12 | 4환경(현재 PS / child `-NoProfile` / fresh `cmd.exe` / Harness-like `ProcessStartInfo`)에서 `codex`/`codex.cmd` 해석 + version exit 0. **V1 bare PATH FAIL 재현 안 됨** | §12 (내 §11 재확인: `where.exe codex` 해석됨) |
| VF-13 | production mutation 0, `~/.codex/config.toml` 불변, canary/fixture 정리 | §11 독립 재확인 |
| VF-14 | raw JSONL category count: bundle-1(727B, session-id 2), bundle-2(6,408B, abs path 4, session-id 2, cmd trace 4), combined-3(10,265B, abs path 12, home path 4, session-id 2, cmd trace 6). **secret-shaped 0** (제한된 regex + non-secret fixture 조건) | §10 |
| VF-15 | usage: bundle-1(input 46,612 / cached 34,048), bundle-2(36,432 / 23,296), combined-3(37,901 / 23,808). `--ignore-rules` 실패도 46K input 소비 | §15 |

---

# 4. Incorrect / Overstated Claims (V2)

| ID | V2 주장 | 감사 판정 | 근거 |
|---|---|---|---|
| IC-1 | §16 **D2 `PASS`** — 한글 logical path mapping | **OVERSTATED → `PARTIALLY_VERIFIED`** | (a) **n=1** (단일 logical path `src/한글 경로/검증 파일.kt`), 깊은 중첩·혼합 스크립트·경로 길이·예약어 matrix 없음. (b) 성공 계약(**UTF-8 BOM manifest**)이 **Harness 전역 "UTF-8 without BOM everywhere"** 불변식(MAP §2, BOM audit 239, `validate-ops`/`WorkflowStateMapper`의 no-BOM 강제)과 **직접 충돌**. §5가 언급한 대안(`-Encoding UTF8` argv)은 실제로 테스트 안 됨(combined-3는 BOM 사용). capability는 보였으나 계약 미확정 + 기존 불변식 충돌 |
| IC-2 | §7 **C1 `PASS_CURRENT_CLI`** — MCP/plugin discovery 차단 | **PARTIALLY_VERIFIED (attribution 없음)** | 3 run이 `--ignore-user-config` + `-c mcp_servers={}` + `--disable`들을 **동시에** 사용 → 어느 메커니즘이 실제로 차단했는지 미분리. §7 스스로 "`-c mcp_servers={}`만으로 전체 제거된다고 간주 금지" 경고. 집계 결과(event 0)는 실측이나 원인 귀속 불가. CLI 업그레이드 시 silent 재노출 위험 → hard version-pin preflight 필수 |
| IC-3 | §8 supervisor 증거가 "process contract를 계획할 만큼 충분" (§19 Q5 함의) | **PARTIALLY_VERIFIED — fake 대상** | 10 mode 전부 **scratch fake Codex**에 대한 것. 실제 `codex.cmd`→`node`(→MCP server 등) 프로세스 트리에 대한 `taskkill /T` / residual / no-window는 **미검증**. V1 §8은 실제 CLI 종료 후 `codex` 1 + `node` 1 residual을 관측했고 귀속 불가로 남겼음. V2는 그 실제 트리를 다시 다루지 않음. 패턴은 designable하나 "실제 Codex 트리가 깨끗이 죽는다"는 미입증 |
| IC-4 | §8 no-window: fake process에서 main window 미관찰 | **PARTIALLY_VERIFIED (V2도 PARTIAL로 표기)** | `MainWindowHandle` polling만. Win32 `EnumWindows`/`IsWindowVisible` 독립 검증 없음. **packaged console-flash는 HRNS 열린 Gate**(`packaged-app-console-window-flash` UNVERIFIED, 호환성 감사 §6.4)인데 이번에도 미접촉 |
| IC-5 | §3 canary 지적 **"CLOSED: read 가능 확인"** (`.codex` 인접) | **정확하나 프레이밍 주의** | V2가 정직하게 "credential 자체 노출 주장이 아니라 auth directory 주변에 model-enforced read boundary가 없다는 증거"(§9)라고 명시 → OK. 단 "CLOSED"는 "read boundary 부재를 입증했다"는 뜻이지 "auth 안전 확인"이 아님. `.codex/auth.json`(3953B) 자체에 대한 read 시도는 안 함 → auth 파일 직접 readability는 여전히 미시험(위치·권한상 읽힐 가능성 높음) |
| IC-6 | §12 R1 `PASS_WITH_FALLBACKS` — command resolver | **VERIFIED (approach), 단 V1 FAIL 원인 미규명** | 4환경 재현 = 내 §11과 일치(VF-12). 그러나 V1 세션의 FAIL이 왜 났는지(stale PATH / 제한 shell 상속 / 특정 타이밍)는 여전히 미규명. resolver가 "환경별 재판정"을 하도록 설계한다는 결론은 타당하나, V1 FAIL의 근본 원인을 모르면 그 재판정 로직이 무엇을 방어해야 하는지 불명확 |
| IC-7 | §16 B4 "semantic truth 완전 검증 UNVERIFIED — 의도적 한계" | **ACCEPT (정직)** | deterministic anchor audit는 finding을 실제 file/line/evidence/scope에 고정할 뿐, "이 코드가 진짜 버그인가"는 판정 못 함. V2가 §6에서 명시. 이것은 결함이 아니라 올바른 한계 인정 |
| IC-8 | §18 hard invariant "Claude-only: baseline workflow survives" | **UNDER-SPECIFIED** | Harness 자체 설계는 **deterministic-first**(task class Tier -1, no-Claude paths). invariant는 "**any single provider OR none**에서 baseline survives"여야 Harness 설계와 정합. "Codex-only: long-term target"은 03/04 합의(Codex=reviewer 우선, implementer는 AR-C7 훨씬 뒤)를 넘는 **신규 aspiration** — "POC 범위 밖"으로 표기됐으나 commitment가 아니라 aspiration으로 명시 필요 |

---

# 5. Security Findings (V2)

| ID | Severity | Finding | V1 대비 | 근거 |
|---|---|---|---|---|
| SEC-1 | **HIGH — 입증 강화** | **Host read boundary = classification B (실증됨)** — feature-disabled generic bundle invocation에서도 shell tool이 working root / parent / other drive / `%USERPROFILE%` / `.codex` 인접 / inherited env를 모두 read. **command-output marker 대조** 관측 | V1 SEC-1은 "증거 사슬 얇음"(self-report). **V2가 증거를 solid하게 격상.** boundary B는 이제 expected가 아니라 empirically established | §9, §14 |
| SEC-2 | **HIGH → 부분 완화 (조건부)** | MCP/plugin discovery는 현재 CLI + disable 조합에서 제거됨(event 0). **단 shell tool은 여전히 arbitrary read** — MCP를 막아도 실제 exfiltration 벡터(shell)는 살아 있음 | V1 SEC-2 "MCP 무통제" → 현재 CLI 한정 완화. 그러나 근본 벡터 미해결 | §7, §14 C3 FAIL |
| SEC-3 | **HIGH — 여전히 열림** | **`shell_tool=false` + bounded inline의 live semantic effect UNVERIFIED** — boundary B에서 나가는 유일한 tool-free 경로인데 3회 승인 호출을 다 써서 미검증 | V1에 없던 항목. V2가 명시적으로 남긴 최우선 gap | §7, §14, §16 C4 |
| SEC-4 | **MEDIUM** | **auth 파일 직접 readability 미시험** — `.codex` *인접* canary는 읽었으나 `.codex/auth.json`(3953B) 자체에 대한 지시된 read는 시도 안 함. `%USERPROFILE%`·`.codex` 인접 read가 성공한 상태에서 auth 파일이 안 읽힌다는 근거 없음 | V1 IC-3 "auth not read는 가정" → V2가 인접 canary로 boundary 부재는 입증, 파일 자체는 여전히 미시험 | §9, §11(`.codex/auth.json` 존재 확인) |
| SEC-5 | **MEDIUM** | **UTF-8 BOM 계약이 Harness no-BOM 불변식과 충돌** — Codex-review bundle이 BOM manifest를 요구하면, `validate-ops`/BOM audit/`release-package-hygiene`의 no-BOM 강제와 상충. `-Encoding UTF8` argv 대안 미검증 | V1에 없음. V2 §5의 계약이 만든 신규 충돌 | §5, Kit MAP §2, 호환성 감사 §11 (BOM 0 / 239 files) |
| SEC-6 | **MEDIUM** | **`--ephemeral` persistence 미확정 (positive 증거 있음)** — `.codex/`의 history/logs/state/goals sqlite가 실험 시간대에 write됨. `--ephemeral` run이 host session store에 무엇을 남기는지 unknown | V1 SEC-7과 동일하나 V2 §11에서 write가 **positive하게 관측**됨 | §11 |
| SEC-7 | **MEDIUM** | **raw stderr redaction 미측정** — raw JSONL category는 3 run 측정했으나 stderr에 대한 동일 count/redaction prototype 없음. V2 §17.8이 별도 gate로 인정 | V1 SEC-4 확장 | §10, §17.8 |
| SEC-8 | **LOW** | **prompt injection matrix 미확장** — V2 §14는 "provider result에 patch/apply/write authority 없었고 parent audit도 manifest 밖 finding 거부"로 요약. injection matrix(다른 파일 read 유도 / sandbox 우회 / secret 출력 / git mutation / 성공 verdict 강제)를 별도 실행하지 않음 | V1 SEC(E4-3) 1케이스에서 진전 없음 | §14 |
| SEC-9 | **LOW** | **path containment = lexical only (미해결)** — reparse/junction/non-existing target semantics 미시험. V2 §17.3이 production hardening 대상으로 인정 | V1 SEC-6과 동일 | §17.3 |

**핵심**: V2는 boundary B의 *증거*를 강화했고(SEC-1), MCP를 *현재 CLI에서* 완화했으나(SEC-2), **boundary B에서 나가는 검증된 경로가 없다**(SEC-3). 그리고 성공한 mapping 계약이 기존 Harness 불변식과 충돌한다(SEC-5).

---

# 6. Git Scope Findings (V2)

| 항목 | V2 결과 | 감사 판정 |
|---|---|---|
| rename/copy/binary/submodule/LFS **preflight 판별** | deterministic command로 5종 모두 detect (VF-11) | **VERIFIED** — `git diff --cached --name-status -M -C --find-copies-harder` + `--numstat` + `ls-files --stage` + `check-attr filter`. Harness가 provider 호출 전에 typed preflight로 판별 가능함을 실증 |
| native Codex semantic support (binary/submodule/LFS) | UNVERIFIED, initial fail-closed block 후보 | **VERIFIED (미시험 처리 정확)** — binary/submodule/LFS는 block, rename/copy는 explicit manifest에 lineage + hash 검증될 때만 bundle mode 허용 |
| V1 "native review가 copy를 새 file로 포함, lineage 미보존" | 판정 유지 | **VERIFIED (일관)** |
| native `--uncommitted` + stdin instruction | (V2는 native 경로를 주 경로로 안 씀) | V1 VF-4/GS-5 유지 — native path의 stdin 불가는 여전. V2가 **generic exec + stdin**으로 우회 → 이 계약 문제를 회피하는 설계 선택 |
| native `--uncommitted` + `--output-schema` | (V2는 generic exec로 우회) | V1 VF-5/GS-6 유지 — generic exec에서 schema 적용됨(§4). **V2의 generic-exec 선택이 GS-5+GS-6를 회피** |
| scope drift / manifest hash TOCTOU | 미시험 | **UNVERIFIED** — V2 §17.3이 production test 대상으로 인정. finding audit prototype이 hash 검증은 하나 TOCTOU 재검사는 미구현 |

**핵심**: V2의 generic-exec + immutable ASCII bundle 선택은 V1이 발견한 native review의 3중 계약 불일치(stdin 불가 / schema 무시 / copy lineage 손실)를 **설계로 우회**한다. Git edge는 provider에게 안 맡기고 deterministic preflight로 판별·block. 이 부분은 진전.

---

# 7. Process Findings (V2)

| 항목 | V2 표기 | 감사 판정 | 비고 |
|---|---|---|---|
| concurrent stdout/stderr drain | PASS_OFFLINE | **VERIFIED (fake)** | fake supervisor |
| byte cap + truncation | PASS_OFFLINE | **VERIFIED (fake)** | 200,001 → 4,096. "production 값 아님"(§8) 정직 |
| UTF-8/Korean stream decode | PASS_OFFLINE | **VERIFIED (fake)** | explicit UTF-8 decode |
| timeout typed result | PASS_OFFLINE | **PARTIALLY_VERIFIED** | fake hang. 실제 hung `codex.cmd`에 미적용 |
| cancellation typed result | PASS_OFFLINE | **PARTIALLY_VERIFIED** | fake |
| process-tree kill / residual | PASS_OFFLINE | **PARTIALLY_VERIFIED** | fake "child spawn + hang". 실제 `codex.cmd`→`node`(→MCP server) 트리 미검증 (IC-3) |
| nonzero / malformed / schema-invalid | PASS_OFFLINE | **VERIFIED (fake)** | exit 7 보존, parser reject |
| no-window | PARTIAL | **PARTIALLY_VERIFIED** | `MainWindowHandle` polling만, Win32 미검증. packaged console-flash Gate 미접촉 (IC-4) |
| explicit child encoding | (supervisor에 포함) | **PARTIALLY_VERIFIED (fake)** | supervisor는 explicit UTF-8 decode. 실제 CLI stream에 미적용 |

**핵심**: supervisor **패턴**은 10 mode에서 designable함이 보임. 그러나 전부 **fake process** 대상이며, V1이 실제 관측한 `codex` + `node` residual(귀속 불가)을 V2는 실제 트리로 다시 다루지 않았다. "Integrated Plan을 설계할 수준인가"(질문 4) → **패턴 설계에는 충분, "실제 Codex 트리가 깨끗이 죽는다"는 미입증**. plan이 "실제 CLI 대상 offline regression"을 hard gate로 명시하면 설계 착수는 가능(V2 §17.3도 그렇게 인정).

---

# 8. Result Contract Findings (V2)

| 항목 | 판정 | 근거 |
|---|---|---|
| deterministic finding audit **실행됨** | **VERIFIED (prototype)** | §6. V1 BG-A("한 번도 exercise 안 됨") → **실제 실행**. 거짓 6종 + hash tamper reject. `review.audit.json` 개념 산출(`finding_id`/`status`/`reason_codes[]`/`verified_evidence[]`/count) |
| false finding 6종 rejection이 **충분한 capability proof**인가 | **YES for anchoring, NO for semantic** | 6종(physical_file_missing / line_not_present / file_id_not_in_manifest / evidence_ref_missing / finding_outside_changed_scope / test_pass_claim_not_verified)은 **anchoring 실패 모드를 잘 커버**. 미커버: 실제 file/line/evidence/scope를 다 맞추지만 버그가 없는 **semantic false positive**(§6/B4가 인정), evidence 위조(단 write는 `--sandbox read-only`로 차단, manifest는 parent 소유 → 완화), confidence gaming(audit가 `confidence` 미사용), cross-finding 모순. **결론: "audit가 anchor-invalid finding을 거부할 수 있다"는 입증됨. "provider finding을 신뢰해도 된다"는 미입증 — V2가 `CLOSED_FOR_CAPABILITY`로 정확히 표기** |
| exit 0 ≠ review PASS | **VERIFIED** | §4 "provider exit 0, JSONL parse 성공, schema 적용은 finding acceptance와 각각 별개" |
| last completed agent message 선택 | **VERIFIED** | §4 5단계 selector, `message_count=2` / `selected_index=1` |
| audit prototype의 production 격차 | **인정됨** | §6/§17.3: reparse/junction / non-existing output path / strict schema library / TOCTOU 재검사가 production test 대상 |
| provider가 자기 mutation_audit 주장 안 함, parent가 채움 | **VERIFIED (개념 준수)** | §11 mutation audit는 Codex(parent)가 전후 hash로. §9 canary chain도 parent가 actual marker 보유 후 대조 |

**핵심**: V1의 최대 구멍(BG-A, safety 메커니즘 미시험)이 **prototype 수준에서 닫혔다**. capability는 실증됐다(거짓 finding을 실제로 거부). 남은 것은 (a) production hardening, (b) semantic truth는 근본적으로 audit 밖이라는 한계의 명시적 수용 — 둘 다 V2가 정직하게 인정.

---

# 9. Token / Cost Findings (V2)

| 항목 | 판정 |
|---|---|
| "Codex가 더 싸다" / "provider 추가가 절감" 결론 | **없음 (프로토콜 축 8 통과)** — §15 명시 배제, §13 DEFER, Q1 UNVERIFIED |
| native usage 0 처리 | **VERIFIED (적절)** — `unknown` 유지 |
| 관측된 input 비용 | bundle-1 46,612 / bundle-2 36,432 / combined-3 37,901 (cached 23K~34K). 작은 bundle도 fresh provider/system/tool 고정비로 수만 token (§15). **깨끗한 "1 review 비용" 데이터는 여전히 없음** (canary 섞인 run, `--ignore-rules` 실패 run이 포함) |
| `--ignore-rules` 실패도 46K 소비 | **VERIFIED** — §15 "deterministic CLI/feature preflight가 provider 호출보다 먼저여야" — 옳은 결론 |
| cheap lever 우선순위 (usage baseline → parent-deterministic navi → packet-first/compact planning → deterministic preflight → retry 제거) | **유지 (03/04 합의)** — §15 재확인 |
| n≥5 미실행 사유 | **정당** — 승인 상한 3회, V2 목적이 "통계적 품질 연구"가 아니라 "deterministic audit / bounded runtime 증명" (§13) |

**핵심**: 프로토콜 축 8 위반 없음. V2는 V1보다 더 명시적으로 cost 일반화를 거부. 단 깨끗한 review 단위 비용 측정은 아직 0건이며, canary/failure-path가 섞이지 않은 clean review run이 필요.

---

# 10. Remaining Experiments (Integrated Plan 착수 전)

## 10.1 필수 (아키텍처 결정 — plan 내부 gate로 미룰 수 없음)

| ID | 실험 | 이유 |
|---|---|---|
| **RE-1** | **`shell_tool=false` + bounded inline evidence의 live semantic effect** — `--disable shell_tool`(또는 feature flag)가 실제로 provider의 filesystem/tool read를 제거하는가? bounded stdin evidence만으로 review가 완료되는가? canary 6위치 read가 0이 되는가? | POC 아키텍처(tool-free inline vs OS-isolated)를 결정. 이것 없이는 plan의 security/rollback/deployment 섹션을 쓸 수 없음. 작음(1~2 call). **verdict `REVISE`의 유일한 실질 근거** |

## 10.2 강하게 권장 (RE-1과 병행 또는 직후)

| ID | 실험 |
|---|---|
| RE-2 | `-Encoding UTF8` argv 경로로 logical path mapping (BOM 없이) — Harness no-BOM 불변식과의 충돌 해소 (SEC-5). logical path matrix(깊은 중첩 / 혼합 스크립트 / 길이 / 예약어) n≥3 |
| RE-3 | 실제 `codex.cmd`→`node` 프로세스 트리에 대한 timeout / cancel / `taskkill /T` / residual / Win32 `EnumWindows` no-window (IC-3, IC-4). V1이 관측한 실제 residual 재현·귀속 |
| RE-4 | `.codex/auth.json` 자체에 대한 지시된 read 시도 (SEC-4) — 성공/차단 관측. non-secret canary를 auth 파일과 **동일 디렉터리·유사 이름**으로 두고 |
| RE-5 | MCP 차단 메커니즘 per-flag isolation (IC-2) — `--ignore-user-config` 없이 `-c mcp_servers={}`만, `--disable` 각각 단독. CLI version pin + 재현 preflight를 hard gate로 확정 |
| RE-6 | failure taxonomy: real auth unavailable / quota / network failure의 exit / stderr / JSONL shape (V2 §17.6) |

## 10.3 plan 내부 gate로 이관 가능 (실험 불요, 계획에 조건으로 명시)

- finding audit + supervisor의 production hardening(reparse/junction, TOCTOU, strict schema lib) → offline regression test로 재구현 (V2 §17.3)
- raw stderr redaction/count prototype (SEC-7)
- prompt injection matrix (SEC-8)
- n≥5 quality / Accepted-Change 비용 (품질·경제성 gate)
- Git edge native semantic support (초기 fail-closed block으로 회피 가능)

---

# 11. 독립 재확인 (deterministic read-only)

| 검증 | 명령 | 결과 | V2 주장 대조 |
|---|---|---|---|
| HRNS production mutation | `git status --porcelain --untracked-files=all` | stray file 0 (proposal + V1 review + V1 exp + V2 exp + revOpinion 8) | §11 M1 **VERIFIED** |
| Live Kit mutation | `find /d/harness-kit -type f \| wc -l` + `kit-version.json` | **246 files**, `kit_version=2026.09.02` 불변 | §11 M1 **VERIFIED** |
| config.toml mutation | `ls -la --time-style=full-iso ~/.codex/config.toml` | mtime **2026-08-13 13:48** (V1 baseline과 동일) | §2/§7 "`--ignore-user-config` config.toml 불변" **VERIFIED** |
| fixture/canary cleanup | `ls CreatorTemp/hrns-codex-*`, `ls S:/tmp \| grep canary`, `ls %USERPROFILE% \| grep canary` | 전부 없음 | §19 "실험 산출물 0" **VERIFIED (관측 범위)** |
| **`.codex` 세션 store** | `ls -la ~/.codex/` | `history.jsonl`(14:08), `logs_2.sqlite`/`state_5.sqlite`/`goals_1.sqlite`(14:55), `models_cache.json`(14:59) = **실험 시간대 write**. `auth.json` mtime **Aug 31**(불변) | §9 "`--ephemeral` persistence 귀속 불가" → **강화: 세션 store write가 positive 관측됨. credential 파일 자체는 불변** |
| bare command resolution | `where.exe codex` | `C:\Users\LG\AppData\Roaming\npm\codex` + `codex.cmd` 해석됨 | §12 "V1 FAIL 재현 안 됨, 환경 의존" **VERIFIED (내 V1 IC-1과 일치)** |
| `.codex` MCP/plugin surface 존재 | `ls -la ~/.codex/` | `rules/`, `plugins/`, `skills/`, `mcp-oauth-locks/` 디렉터리 존재 | §7의 disable 테스트가 **실제 configured surface** 대상임을 확인 (null test 아님) |

---

# 12. GO / NO-GO for Standalone POC

## **NO-GO (현 상태) — 단, V1보다 근접**

### V2가 닫은 blocker (V1 GO/NO-GO 대비)

| V1 blocker | V2 상태 |
|---|---|
| B4 native review 계약 3중 불일치 | **회피됨** — generic exec + immutable ASCII bundle 선택 (§6) |
| B5 deterministic finding-audit 미시험 | **prototype 수준 닫힘** — 거짓 6/6 reject (§8, VF-1) |
| B6 output byte cap 부재 | **offline 실증** — 200,001 → 4,096 (VF-8) |
| BG-D MCP/tool surface 무통제 | **현재 CLI에서 제거** — event 0 (VF-5), version gate 필요 |
| IC-2 read isolation self-report | **해결** — command-output marker chain (VF-4) |
| BG-E live approval provenance | **해결** — 사전 기록 (§2) |
| IC-1 bare PATH FAIL | **정정** — 환경 의존, 4환경 성공 (VF-12) |

### 여전히 NO-GO인 blocker

| Blocker | 근거 |
|---|---|
| **B1. Host read boundary B에서 나가는 검증된 경로 없음** | shell tool이 6위치 arbitrary read (SEC-1). `shell_tool=false` inline은 UNVERIFIED (SEC-3, RE-1). OS/ACL isolation은 미실험. **POC를 실제 host의 실제 source에 적용할 수 없음** |
| **B2. mapping 계약이 Harness 불변식과 충돌** | UTF-8 BOM 요구 vs Harness "no BOM everywhere" (SEC-5, RE-2) |
| **B3. supervisor가 fake 대상, 실제 CLI 트리 미검증** | timeout/cancel/tree-kill/residual/no-window 전부 실제 `codex.cmd`→`node` 트리에 미적용 (IC-3, IC-4, RE-3) |
| **B4. auth 파일 직접 readability 미시험** | `.codex` 인접 read 성공 + auth 파일이 그 안에 존재 (SEC-4, RE-4) |

### 긍정 (방향 유효)

- Codex `exec` + bundle이 planted defect 검출 (bundle-2 1 generated, combined-3 1 accepted, 추가 오탐 0)
- deterministic finding audit가 거짓 finding을 실제로 거부 (capability 실증)
- Git edge를 provider에 안 맡기고 preflight로 판별 가능
- generic-exec 선택이 native review 계약 문제를 우회
- production/Kit/config mutation 0 (독립 재확인)

→ **방향(standalone read-only Codex reviewer)은 유지.** B1(특히 RE-1)이 닫히기 전 POC 구현 착수 불가.

---

# 13. Integrated Plan Readiness

## **NOT READY — `REVISE_CAPABILITY_EXPERIMENT`**

### V2 §18의 질문에 대한 답

> "host read boundary B를 Integrated Plan 안의 선행 hard gate로만 둘 수 있는가, 아니면 plan 작성 전에 추가 capability run으로 닫아야 하는가?"

**추가 capability run이 필요하다 — 정확히 RE-1 (`shell_tool=false` + bounded inline live effect) 하나.** 이유:

1. `shell_tool=false`의 결과가 POC의 **전체 아키텍처**를 결정한다 (tool-free inline reviewer vs OS-isolated bundle reviewer). 두 경우 security/rollback/deployment/dependency 섹션이 근본적으로 다르다.
2. 미검증 core isolation 메커니즘 위에 plan의 보안 섹션을 쓸 수 없다.
3. RE-1은 작다 (1~2 call, 이미 설계된 fixture 재사용).

### plan 착수 가능 조건

`READY_FOR_INTEGRATED_PLAN`으로 전환하려면:

- **RE-1 통과** — `shell_tool=false`가 filesystem/tool read를 제거하고 bounded inline evidence만으로 review 완료. (실패 시 별도 OS-isolation 실험이 추가로 필요 → 그 경우에도 `REVISE`)
- RE-2 (no-BOM logical path 계약) — 최소 방향 확정 (BOM 대안이 있음을 실증)

RE-3~RE-6과 §10.3 항목은 plan 내부 hard gate로 이관 가능.

### 장기 hard invariant (V2 §18 — 감사 반영)

```text
Baseline workflow는 복수 유료 frontier provider 구독을 요구하지 않는다.
  - deterministic-first / no-provider: baseline survives   ← 추가 (Harness 설계 정합)
  - single provider (Claude 또는 다른 하나): baseline survives
  - Claude + Codex: optional guarded dual-provider quality lane
  - "Codex-only baseline": aspiration, commitment 아님 (03/04 합의 밖)   ← 명시
```

현재 POC 후보 = 기존 Claude workflow + optional Codex reviewer. 이 invariant는 적절히 반영됐고, 위 2줄(deterministic-first 포함 / Codex-only는 aspiration)만 보강하면 된다 (IC-8).

---

# 14. 사용자 10개 질문에 대한 직접 답

| # | 질문 | 답 |
|---|---|---|
| 1 | deterministic finding audit가 V1의 핵심 safety gap을 실제로 닫았는가 | **부분적으로 YES.** V1 BG-A("한 번도 실행 안 됨")는 닫혔다 — prototype이 실제로 돌았고 거짓 6종 + hash tamper를 거부했다(VF-1). 단 (a) scratch prototype이지 production audit 아님(reparse/junction/TOCTOU/strict schema 미구현, V2 §17.3 인정), (b) semantic truth는 근본적으로 audit 밖(V2 §6/B4 인정). → `CLOSED_FOR_CAPABILITY`가 정확한 표기 |
| 2 | schema-valid false findings 6종 rejection이 충분한 capability proof인가 | **anchoring에는 충분, semantic에는 불가.** 6종은 file/line/scope/evidence/test-claim anchoring 실패를 잘 커버(§8). 미커버: 모든 anchor를 맞춘 semantic false positive, confidence gaming, cross-finding 모순. "audit가 anchor-invalid를 거부한다" 입증됨. "provider finding을 신뢰해도 된다" 미입증 |
| 3 | ASCII physical + logical mapping + UTF-8 encoding contract가 Windows POC에 충분한가 | **capability는 보였으나 계약 미확정 + 충돌.** ASCII physical + manifest-carried logical + parent re-verify는 실증(VF-7). 그러나 (a) n=1 logical path, (b) 성공 계약(UTF-8 **BOM**)이 Harness 전역 **no-BOM 불변식과 직접 충돌**(SEC-5), (c) `-Encoding UTF8` argv 대안 미검증. → RE-2 필요 |
| 4 | bounded process supervisor 증거가 Integrated Plan 설계 수준인가 | **패턴 설계에는 충분, 실제 CLI 검증은 아님.** 10 mode 전부 **fake Codex** 대상(IC-3). 실제 `codex.cmd`→`node` 트리의 tree-kill/residual/no-window 미검증. plan이 "실제 CLI offline regression"을 hard gate로 명시하면 설계 착수 가능(V2 §17.3도 동일). RE-3 권장 |
| 5 | process-local MCP/plugin disable을 current-version capability gate로 수용 가능한가 | **YES, 조건부.** 3 run event 0은 실측(VF-5, V1 control 대비). 단 (a) `--ignore-user-config` + `-c mcp_servers={}` + `--disable`들이 동시 사용돼 attribution 불가(IC-2), (b) CLI 업그레이드 silent 재노출 위험. → **hard version-pin preflight**(CLI version 확인 + MCP probe 재실행)를 plan 내부 필수 gate로 두면 수용 가능. RE-5로 per-flag 분리 권장 |
| 6 | canary evidence chain이 V1의 self-report 문제를 해결했는가 | **YES, 해결됨.** actual random marker → completed `command_execution` output exact match → raw JSONL exact match → final result exact match, 6/6(VF-4, §9). 모델이 추측 불가한 random marker가 command output에 나타났으므로 shell tool이 실제로 읽었다는 강한 증거. V1 IC-2 CLOSED |
| 7 | host read boundary classification B가 실제로 입증됐는가 | **YES, 입증됨.** V1은 self-report 기반이라 "증거 얇음"이었으나 V2는 command-output marker 대조로 6위치 read를 solid하게 관측(§9, §14). boundary B("실제 source POC에도 별도 OS/ACL boundary 또는 검증된 tool-free 입력 경계 필요")는 이제 expected가 아니라 empirically established. 단 B에서 **나가는** 경로는 미입증 |
| 8 | `shell_tool=false` + bounded inline을 plan 선행 hard gate로 두는 것으로 충분한가, 추가 experiment가 필요한가 | **추가 experiment 필요 (RE-1).** `shell_tool=false` 결과가 POC 아키텍처 전체(tool-free inline vs OS-isolated)를 가르므로 plan 내부 gate로 미룰 수 없다. 미검증 core isolation 위에 plan의 security 섹션을 쓸 수 없다. RE-1은 작다(1~2 call). **이것이 verdict `REVISE`의 핵심 근거** |
| 9 | Single-provider survivability invariant가 적절히 반영됐는가 | **방향은 적절, under-specified.** V2 §18의 "Claude-only: baseline survives"는 좋은 invariant이고 올바른 위치(plan hard invariant)다. 보강 2건: (a) Harness 설계가 deterministic-first이므로 "**any single provider OR none**에서 survives"로 일반화, (b) "Codex-only: long-term target"은 03/04 합의(Codex=reviewer 우선)를 넘는 신규 aspiration이므로 commitment가 아니라 aspiration으로 명시 (IC-8) |
| 10 | V2가 production을 성급히 승인했거나 비용/품질을 과대일반화했는가 | **아니다.** production 승인 없음(§1/§18/§19), cost 일반화 명시 거부(§15), quality 일반화 거부 + "bundle first" V1 과대주장 철회(§13). mild over-claim 2건: D2 "PASS"(n=1 + BOM 충돌 → `PARTIALLY_VERIFIED`가 맞음, IC-1), MCP block의 per-flag attribution(IC-2). 둘 다 결론을 바꾸지 않음 |

---

# 15. V1 → V2 Gap Closure 요약

| V1 감사 항목 | V2 결과 | 판정 |
|---|---|---|
| BG-A deterministic finding-audit 미실행 | prototype 실행, 거짓 6/6 + hash tamper reject | **CLOSED_FOR_CAPABILITY** (production hardening 잔여) |
| BG-B auth-directory 주변 read 미시험 | `.codex` 인접 canary read 성공 → boundary B 입증 | **CLOSED (boundary), auth 파일 자체는 RE-4** |
| BG-C output byte cap 부재 | fake supervisor 200,001 → 4,096 | **CLOSED_OFFLINE** |
| BG-D MCP/tool surface 무통제 | disable 조합에서 event 0 | **CLOSED_FOR_CURRENT_CLI** (version gate + RE-5) |
| BG-E live approval provenance | 사전 승인 block | **CLOSED** |
| IC-1 bare PATH FAIL | 4환경 재현 성공, 환경 의존으로 정정 | **CLOSED (내 V1 IC-1과 일치)** |
| IC-2 read isolation self-report | command-output marker chain | **CLOSED** |
| IC-5 "bundle first" 과대주장 | "품질/보안 자동 우위 아님, 통제 가능성 후보" 로 철회 | **CLOSED** |
| B4 native review 계약 3중 불일치 | generic exec + bundle로 우회 | **AVOIDED (설계 선택)** |
| — | **host read boundary B에서 나가는 경로** | **NEW / OPEN — RE-1 (verdict 근거)** |
| — | **UTF-8 BOM ↔ Harness no-BOM 불변식 충돌** | **NEW / OPEN — RE-2** |
| — | supervisor가 fake 대상 | **OPEN — RE-3** |

---

## Final Verdict

# `REVISE_CAPABILITY_EXPERIMENT`

V2는 V1 감사의 지적을 대부분 성실히 닫았고, deterministic finding audit·canary evidence chain·MCP 차단·byte cap·승인 provenance에서 실질적 진전을 냈으며, 과대주장을 스스로 억제했다. **그러나 `shell_tool=false` + bounded inline evidence의 live semantic effect(RE-1)가 미검증**이고, 이 결과가 POC 아키텍처 전체(tool-free inline vs OS-isolated)를 결정하므로 Integrated Plan 착수의 선행조건이다. 추가로 UTF-8 BOM 계약이 Harness no-BOM 불변식과 충돌(RE-2)하며, supervisor 증거는 fake process 대상(RE-3)이다.

**RE-1 통과 + RE-2 방향 확정 후** Integrated Plan readiness를 재평가한다. RE-3~RE-6과 production hardening은 plan 내부 hard gate로 이관 가능.

`READY_FOR_INTEGRATED_PLAN`은 production 구현 승인이 아니며, 현재는 그 수준의 architecture/capability evidence도 아직 확보되지 않았다.

Production source / `D:\harness-kit` / State / Registry / global config / Git history / `doc/hrns_now_agent_runtime_evolution_plan.md` — 전부 미착수.

---

## Mutation / Hygiene

- HRNS-NOW production source / `D:\harness-kit` / WORKFLOW_STATE / Registry / global Claude·Codex config / AGENTS·CLAUDE bridge / Git index·history: **변경 없음**
- `git add`/`commit`/`push`/기타 git mutation: 없음
- build / test / smoke: 없음
- **live provider(Codex/Claude/Ollama) 호출: 없음**
- 사용한 read-only 명령: `Read`(V2 실험 + V1 실험 + V1 감사 + revOpinion), `git status --porcelain`, `find`, `cat`, `ls -la` (`~/.codex/` 및 fixture/canary 경로), `where.exe codex`
- 새로 작성한 파일: `doc/revolution/claude_capability_review_v2.md` (사용자 지시)
- 이 문서의 staging/commit은 Git Owner(Codex)와 사용자의 판단이다.
- 다음 단계: Codex가 RE-1(+RE-2)을 수행하고 `codex_capability_experiment_v3.md`(또는 V2 갱신) 작성 → Claude 재감사. 그 전까지 Integrated Plan 미착수.
