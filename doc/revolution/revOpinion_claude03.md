# HRNS-NOW Agent Runtime Evolution — Claude Reinforcement Audit (Round 3)

- 문서 성격: `doc/claude_prompts/hrns_now_agent_runtime_evolution_proposal.md` 및 후속 프로토콜의 **Reinforcement 03 (Claude 제출본)**
- SELF = claude, OTHER = codex. Mode = **A** (`revOpinion_claude03.md` / `revOpinion_codex03.md` 둘 다 부재 확인 후 착수, 상대 03을 기다리지 않음)
- 입력: `revOpinion_claude01.md`, `revOpinion_codex01.md`, `revOpinion_claude02.md`, `revOpinion_codex02.md`, 최초 제안서 — **조사 대상이지 정본이 아니다**
- 정본 우선순위: (1) 현재 local source + 재현 가능한 deterministic observation → (2) `D:\harness-kit` source → (3) HRNS-NOW source → (4) 공식 문서 → (5) 01/02 의견서
- 상태: 분석 단계. 이 문서 저장 외 파일/Git/네트워크 mutation 0. build·Gradle·smoke·live Claude/Codex/Ollama 호출 미실행. 읽기 전용 명령(`find`, `rg`, `grep`, `git status/log`, `Read`)만 사용.
- 이 문서는 **최종 구현계획을 확정하지 않는다.** 목적은 통합 계획 전에 source로 확인된 공통 사실 기반을 만드는 것이다.

---

# 1. Executive Verdict

**기존 두 축 결론은 유지된다. `REVISE_BEFORE_INTEGRATED_PLAN`.** source 재감사 결과 방향(HRNS provider-blind 유지, `run-cycle.ps1` 밖 Codex read-only reviewer POC, Router·범용 Provider Runtime·`claude-invoke-core.ps1` 범용화 보류)은 그대로다.

**이번 감사가 바꾸는 것(핵심 3):**

1. **`usage_guard`/`role_sliced` opaque slot에 provider runtime 상태를 넣자는 claude02 §3.2를 철회한다.** `docs/SECONDARY_LLM_LANE.md` line 88이 명시적 Kit 규칙을 갖는다: *"Secondary LLM artifacts use their own `schema_version` and must not reuse `claude_runtime.version`, which remains Claude-runtime metadata."* Kit의 실제 관례는 **관심사마다 전용 namespaced key**다. Codex의 `REJECT`(codex02 §4.1)가 옳다 — 다만 HRNS *운반* 메커니즘(unknown key를 `ignoreUnknownKeys`로 무시 → 필요 시 nullable `JsonElement?`로 opaque 운반)은 그대로다.

2. **`context-diet-packet.json`(schema 1.0)을 Review Packet 입력으로 거의 그대로 쓰자는 claude01 뉘앙스를 철회한다.** 실제 `build-context-diet-packet.ps1`을 읽은 결과, 이 packet에는 goal·acceptance criteria·review base·complete changed-file manifest·actual diff·finding output contract가 없다. Codex codex02 §2.5 `REVISE`가 옳다. Review Packet은 **별도의 얇은 builder**가 필요하다(기존 packet은 optional source).

3. **기존 Secondary LLM evidence builder(`build-secondary-llm-evidence-manifest.ps1`)를 change-set 정본으로 재사용 불가**를 source로 확정한다. 이 스크립트가 실행하는 git 명령은 정확히 `git diff --name-only`, `git diff --stat`, `git diff --check` 3개뿐 — **unstaged tracked diff만** 대상이다. staged/untracked/commit diff/base diff/rename/binary/submodule/LFS 미포괄. Codex codex02 §2.4가 옳다. **재사용 가능한 것은 evidence/claim/audit *엔진*이지 git 수집 범위가 아니다.**

이 외 다수 세부(파일명 정정, seam 위치, POC naming, phase 수)는 §3/§15에서 항목별 판정한다.

**Integrated Plan 단계로 넘어가도 되는가?** — **아직 아니다.** §14 열린 결정 중 3건(Codex sandbox read 범위, `AGENTS.md` precedence, live Codex noninteractive 호출 가능성)은 fixture/실험 없이 결정 불가이며, Round 4(상호 03 교차검토) 후 사용자 확인이 남아 있다.

---

# 2. Audit Coverage

## 2.1 Level 1 — Full Inventory (`D:\harness-kit`, `.git` 제외)

`find . -type f -not -path './.git/*'` 기준 **총 246개 파일.**

| 확장자 | 개수 | 범주 |
|---|---:|---|
| `.ps1` | 173 | source/script (root entrypoint 13, `lib/` 49, `report/` 11, `smoke/` 100) |
| `.md` | 33 | docs/reference (docs/ 14, core/ 5, claude/agents/ 7, claude/skills/ 1, prompts/ 3, profiles/architecture/ 1, README 1, SMOKE_INDEX 1) |
| `.tpl` | 16 | template (bridge 3, workspace 13) |
| `.py` | 13 | python sidecar/parity track |
| `.txt` | 4 | generated/runtime artifact (전부 `scratch/diag-planning-hang/`) |
| `.jsonl` | 3 | generated/runtime artifact (전부 `scratch/diag-planning-hang/`) |
| `.yaml` | 2 | configuration (`profiles/corp-default.yaml`, `corp-springboot.yaml`) |
| `.json` | 2 | schema/config (`kit-version.json`, `claude/settings/user.settings.json`) |
| binary | 0 | — |
| backup/reference | 0 | (`D:\backup`는 Kit tree 밖. 이번 감사에서 접근 안 함) |

`smoke/` 100개 산정: `find` 목록에서 `scripts/smoke/smoke-*.ps1` 실계수. (MAP §3의 "95"는 2026-09-02 시점 수치이며 `smoke-claude-stdin-redirect.ps1`, `smoke-continuity-doctor.ps1`, `smoke-doc-preflight-graduation.ps1`, `smoke-code-local-preflight.ps1`, `smoke-planning-preflight-graduation.ps1` 등이 추가되어 실측 100 — **문서/실측 drift 1건, §3.7에 기록**.)

## 2.2 Level 2 — 변경 영향권 본문 정독

| 파일 | 크기 | 정독 범위 |
|---|---:|---|
| `scripts/lib/claude/claude-invoke-core.ps1` | 54.9KB | **FULL** (1–1150) |
| `scripts/lib/plan/planning-claude-runner.ps1` | 22.1KB | **FULL** (1–491) |
| `scripts/lib/code/code-claude-runner.ps1` | 21.1KB | **FULL** (1–455) |
| `scripts/lib/doc/doc-claude-runner.ps1` | 26.8KB | **FULL** (1–596) |
| `scripts/lib/claude-stop-classification.ps1` | 11.8KB | **FULL** (1–180) |
| `scripts/lib/harness-context-diet-mode.ps1` | 3.6KB | **FULL** (1–67) |
| `scripts/lib/harness-runtime-dependency-inventory.ps1` | 2.9KB | **FULL** (1–56) |
| `scripts/report/build-context-diet-packet.ps1` | 9.8KB | **FULL** (1–206) |
| `scripts/report/build-secondary-llm-evidence-manifest.ps1` | 21.9KB | **FULL** (1–404) |
| `scripts/report/build-secondary-llm-input-packet.ps1` | 12.5KB | **FULL** (1–246) |
| `scripts/lib/secondary-llm/secondary-llm-audit.ps1` | 11.6KB | **FULL** (1–246) |
| `scripts/lib/secondary-llm/secondary-llm-claim-evidence.ps1` | 15.5KB | **FULL** (1–302) |
| `scripts/lib/secondary-llm/secondary-llm-validator.ps1` | 23.3KB | **FULL** (1–424) |
| `scripts/lib/secondary-llm/secondary-llm-evidence-index.ps1` | 9.9KB | **FULL** (1–209) |
| `scripts/lib/secondary-llm/secondary-llm-config.ps1` | 2.4KB | **FULL** |
| `scripts/lib/secondary-llm/secondary-llm-policy.ps1` | 2.6KB | **FULL** |
| `scripts/lib/secondary-llm/secondary-llm-schema.ps1` | 3.3KB | **FULL** |
| `scripts/lib/secondary-llm/secondary-llm-reviewer-advice.ps1` | 25.7KB | **PARTIAL** (1–160 + 기대 source-file 목록) |
| `scripts/run-cycle.ps1` | 181.9KB | **PARTIAL** (177–296 wrapper helper/`Assert-RunCycleNativeChildSucceeded`, 3137–3160 wrapper 경로, 3730–3970 planning/replan/exec dispatch + ops gate; 약 450/4180행) |
| `scripts/lib/code/code-role-sliced-runner.ps1` | 175.9KB | **PARTIAL** (2320–2410 `00-wrapper-input.json` v2 생성부, 215–260/428–455 identity/TOCTOU 참조; 약 130/3282행) |
| `templates/workspace/WORKFLOW_STATE.json.tpl` | 3.5KB | **FULL** |
| `docs/HARNESS_KIT_MAP.en.md` | 31.6KB | **FULL** (Round 0) |
| `docs/ROADMAP.md` | 25.7KB | **FULL** (Round 0) |
| `docs/TASK_CLASS_TOKEN_POLICY.md` | 20.0KB | **FULL** (Round 0) |
| `docs/SECONDARY_LLM_LANE.md` | 21.7KB | **FULL** |
| `docs/adr/session-continuity-...md` | 2.9KB | **FULL** (Round 0) |
| `kit-version.json` | 99B | **FULL** (Round 0) |
| HRNS: `HarnessCommand.kt`, `HarnessCommandEncoder.kt`, `HarnessRunnerPort.kt`, `ActionPolicy.kt`, `ExecuteHarnessActionUseCase.kt`, `StopReason.kt`, `WorkspaceConfig.kt`, `HarnessWorkflowStateDto.kt`, `WorkflowStateMapper.kt` | — | **FULL** (Round 0–2) |
| HRNS docs: `README.md`, `documentation_guide.md`, `hrns_now_claude_plan.md`, `hrns_now_design_pattern.md`, `native_qa_checklist.md`, `current_validation_reports.md`, `harness-kit-live-compatibility-audit-report.md` | — | **FULL** (Round 0) |

## 2.3 Level 3 — 나머지 text file 검색 감사

term list(`claude`, `codex`, `ollama`, `provider`, `agent`, `runtime`, `role_sliced`, `usage_guard`, `secondary`, `context-diet`, `packet`, `review(er)`, `usage`, `token`, `budget`, `session`, `resume`, `bridge`, `WORKFLOW_STATE`, `queue`, `authorized_target`, `allowed_write`, `evidence`, `audit`, `git diff`)를 `rg`로 전 tree 스윕.

- **`codex`는 Kit 전체에서 단 1건** — `scripts/smoke/smoke-release-universal-language.ps1:107`의 정규식(`Codex`를 release 언어에 새면 안 되는 고유명사로 차단). **Kit에는 Codex 통합이 전무하다 = clean slate.**
- `provider`는 `secondary-llm-*` (provider 기본값 `none`) 및 `.NET` API 명칭(`file-system provider`)에 국한. `agent`는 `claude/agents/*.md` role 정의에 국한. `agent runtime`이라는 계층 개념은 Kit source에 없다.
- `usage_guard` / `role_sliced` / `context-diet` / `packet` / `session` / `resume` 매치는 전부 Level 2에 이미 포함된 파일 계열로 승격됨.

## 2.4 Coverage Manifest

| 범주 | 전체 | Full Read | Partial Read | Search Only | Metadata Only | 본문 미검토 |
|---|---:|---:|---:|---:|---:|---:|
| `.ps1` (Kit) | 173 | 17 | 3 | 173 (grep) | 0 | ~153 |
| `.md` (Kit) | 33 | 5 | 1 (`WORKSPACE_SPEC` grep) | 33 (grep) | 0 | 27 |
| `.tpl` | 16 | 1 (`WORKFLOW_STATE.json.tpl`) | 0 | 16 (grep) | 15 | 15 |
| `.py` | 13 | 0 | 0 | 13 (grep) | 13 | 13 |
| `.json`/`.yaml` | 4 | 1 (`kit-version.json`) | 0 | 4 (grep) | 3 | 3 |
| `scratch` `.txt`/`.jsonl` | 7 | 0 | 0 | 0 | 7 | 7 |
| HRNS 변경경계 `.kt` | ~15 | ~15 | 0 | 전체 grep sweep | 0 | 0 |

**본문 미검토 사유(범주별):**

- **Kit `.ps1` ~153개**: 대부분(100개)이 `scripts/smoke/` 테스트 파일 — Codex reviewer POC의 **dependency closure(§5) 밖**이며, 관련성은 "기존 offline suite가 계속 통과해야 한다"는 회귀 gate로만 존재(§13). 나머지 미검토(`invoke-{planning-cycle,code-execution,document-execution}.ps1` 89–163KB, `state-surface.ps1` 88KB, `validate-ops.ps1` 56KB, `doctor.ps1` 42KB, `discover-project.ps1` 61KB, `code-workqueue.ps1` 147KB, `planning-queue-projection.ps1` 125KB, `planning-strategy-parser.ps1` 56KB, `code-deterministic-runner.ps1`, `doc-workqueue.ps1`, `planning-dispatch-parser.ps1` 등)는 **planning/execution 내부 로직**으로, provider seam(§17)이 그 위(`run-cycle.ps1` dispatch)와 그 아래(`*-claude-runner.ps1` launcher)에 있으므로 이번 POC 결정에 영향을 주지 않는다. wrapper 3개는 grep으로 dispatch 지점만 확인(§13).
- **`.py` 13개**: `docs/PYTHON_SIDECAR_BOUNDARY.md`와 MAP §2가 "Python = sidecar/parity track, not the authoritative entry surface"로 못박음. provider seam은 PS1 쪽이다.
- **`scratch/` 7개**: 2026-06 시점 `diag-planning-hang` 진단 산출물. runtime dependency 아님, release 포함 아님(`release-package-hygiene.ps1`이 `scratch/**` 제외). name/location/date만 확인.
- **`.tpl` 15개**: `WORKFLOW_STATE.json.tpl` 외 workspace/bridge 템플릿. `bridge/` 3개(§26 3-file bridge)는 grep으로 내용 확인, POC에서 변경 없음.
- **Kit `.md` 27개**: `STATE_MODEL.md`, `SECURITY_MODEL.md`, `OPERATING_GUIDE.md`, `WORKSPACE_SPEC.md`(grep), `HARNESS_KIT_MAP.ko.md`(en 정독), `core/*.md`, `prompts/*.md`, `claude/agents/*.md`(grep), `claude/hooks/*.ps1`(=`.ps1` 계열) 등. Round 0에서 en MAP/ROADMAP/TASK_CLASS/SECONDARY_LLM/ADR를 정독했고 나머지는 term sweep으로 provider 관련성 없음 확인.

**읽지 않은 것을 읽었다고 표현하지 않았다.** 위 표의 Partial/Search/Metadata 구분이 실제 수행 범위다.

---

# 3. Corrections to 01/02

## 3.1 Claude02 §3.2 "`usage_guard`/`role_sliced` opaque slot 재사용" — **철회**

`docs/SECONDARY_LLM_LANE.md:88` (실측): *"Secondary LLM artifacts use their own `schema_version` and must not reuse `claude_runtime.version`, which remains Claude-runtime metadata."* + `docs/SECONDARY_LLM_LANE.md:22`: *"[the lane does not] reuse `claude-invoke-core.ps1` for Ollama."* Kit의 실제 관례 = **관심사마다 전용 key + 전용 schema_version, 다른 provider가 Claude 인프라를 상속하지 않음.** → Codex `REJECT`(codex02 §4.1) 수용. 장래 필요 시 새 optional top-level `agent_runtime`(또는 `codex_review`) key + 자체 `schema_version`, key 부재 허용, readiness 영향 0.

## 3.2 Claude01 §1.2 / §5 / §6 — packet 혼동 (claude02 §1.1에서 부분 개정했으나 이번에 source로 확정)

`context-diet-packet.json`(schema **1.0**, `build-context-diet-packet.ps1`)과 `00-wrapper-input.json`(schema **2.0**, `code-role-sliced-runner.ps1`)은 완전히 별개다. 두 실제 스키마는 §8에 전수 기록. **어느 쪽도 Review Packet으로 승격하지 않는다.** Codex codex02 §2.5 수용.

## 3.3 Codex01 §1.3 wrapper 파일명 오류 (Codex codex02 §2.3에서 자기 정정함) — 확정

실측 파일명: `scripts/invoke-planning-cycle.ps1`, `scripts/invoke-code-execution.ps1`, `scripts/invoke-document-execution.ps1`. Codex01 §1.3의 `invoke-planning.ps1` / `invoke-code.ps1` / `invoke-documentation.ps1`은 오류. Round 3 통합 계획은 정확한 이름 사용.

## 3.4 Claude02 §2.2 "`Start-ClaudeProcess` 함수명 미검증" — **검증 완료, Codex01/codex02 §2.2가 옳음**

3개 runner 모두 `Start-ClaudeProcess`를 정의한다(`planning-claude-runner.ps1:216`, `code-claude-runner.ps1:120`, `doc-claude-runner.ps1:121`). §13에 3-way 비교표. claude02의 유보는 해소.

## 3.5 Claude01/02의 "HRNS provider 결합 = enum ~5개 + 파일명 2개" — **소폭 상향 정정**

실측 grep 결과 HRNS provider-shaped surface는 다음 **8곳**:
1. `core/config/WorkspaceConfig.kt:11` `RuntimeConfig.claudeCommand` + `WorkspaceProbe.kt:31` — env 구성, command 경로에 미주입
2. `core/domain/model/StopReason.kt` — `ClaudeContextLimit/ClaudeCallTimeout/ClaudeResponseEmpty/ClaudeResponseTooShort/TransientClaudeOverloaded` (+ `UsageLimitBlocked` UI 라벨이 "Claude 사용량 제한")
3. `composeApp/.../mapper/DomainLabels.kt:129–158` — 위 라벨 KO/EN
4. `composeApp/.../RecoveryProjections.kt:288–369` — 위 stop reason 복구 카드 문구
5. `composeApp/.../mapper/ReasonKeyStrings.kt` — 위 reason key
6. `infra/bridge/RepositoryBridgeProbe.kt:16–17` + `core/port/RepositoryBridgeProbePort.kt:7` + `composeApp/.../ui/Strings.kt:391,558` — `.claude/settings.local.json` + `.claude/CLAUDE.md`
7. **`infra/recovery/WorkspaceRecoveryDiagnosticsAdapter.kt:32`** — `logs/claude-session-continuity/<date>/` 경로를 실제로 읽음. **claude01/02가 놓친 유일한 *기능적* 결합** (라벨/문구가 아니라 실 경로 소비). `claude-invoke-core.ps1`이 쓰는 그 디렉터리다.
8. `composeApp` "Claude 명령" probe 표시 4곳

**결론은 불변**: `HarnessCommand`/`HarnessCommandEncoder`/`HarnessRunnerPort`/`ActionPolicy`/`ClosurePolicy`/`CompatibilityPolicy`/`WorkflowStateMapper`/`WorkflowState`에는 provider 개념 0. 결합은 taxonomy 라벨 + recovery 진단 경로 1개에 국한. **구조적 provider coupling 아님.** 단 #7은 "recovery 진단이 Claude 전용 경로에 묶여 있다"는 점을 명시해야 한다(codex02 §10.4 방향과 일치).

## 3.6 Claude01 "Ollama lane에 `reviewer_advice` task type이 있으니 Codex reviewer와 대응" — **부정확, 정정**

`secondary-llm-reviewer-advice.ps1`(실측)의 `reviewer_advice`는 **같은 `logs/secondary-llm/<run-id>/`의 다른 advisory 산출물(diff-summary/validation-summary/next-session-prompt/docs-mismatch candidate·audit)을 메타 검토**하는 것이다(`Get-SecondaryLlmReviewerAdviceExpectedSourceFiles` 목록). **프로젝트 소스/diff를 직접 리뷰하지 않는다.** `SECONDARY_LLM_LANE.md:200`: *"reads only existing artifacts in the same ... directory."* → Codex code reviewer는 이 task type의 재사용이 아니라 **신규 개념**이다. 재사용되는 것은 finding 앵커 issue shape(`New-SecondaryLlmReviewerAdviceIssue`의 `finding_id`)와 `must_not_auto_apply=true` 규약뿐.

## 3.7 신규 발견 drift 2건 (Agent Runtime과 무관, 별도 backlog)

- **smoke 인벤토리 실측 100 vs 문서 95** (`HARNESS_KIT_MAP.en.md:40`, `ROADMAP.md:58`, `SMOKE_INDEX.md` 헤더가 "95"). `smoke-smoke-index-consistency.ps1`이 이를 잡도록 되어 있으나 문서 텍스트 3곳이 stale. — Kit 자체 문서 유지보수 이슈.
- **HRNS `WorkflowStateMapper`의 queue requiredness gap** (codex01 §1.7 재확인): `WORKFLOW_STATE.json.tpl`은 `queue.active.card_id/slice_id`, `queue.blocked_reason`, `queue.last_updated_at`을 항상 쓰지만 `WorkflowStateMapper.kt`는 `queue.status`만 key 부재 시 `MapResult.Failure`. `hrns_now_claude_plan.md` 부록 A는 5개를 "queue 안정 표면"으로 나열. 관측 오작동 없음(writer가 항상 씀), BLOCKER 아님. **Agent Runtime 작업과 절대 섞지 않는 독립 bounded hardening task.**

---

# 4. Current Architecture — Verified

## 4.1 HRNS-NOW (실행 경로, source 확인)

```
UiAction → ActionPolicy(순수) → HarnessCommandMapper → HarnessCommand(sealed)
        → HarnessCommandEncoder → EncodedProcessInvocation{executable, arguments}
        → HarnessRunnerPort (PowerShellHarnessAdapter/JvmProcessExecutor, SecretMaskingProcessRunner wrap)
        → powershell.exe -NoProfile -ExecutionPolicy Bypass -File <kitRoot>/scripts/{doctor|validate-ops|enter-project|run-cycle}.ps1
        → WorkflowStatePort.read()(lock 보유) → projection
```

- provider 개념 전무. LLM 직접 호출 전무. `RuntimeConfig.claudeCommand`는 probe/표시용, encoder 미진입.
- ACL: `HarnessWorkflowStateDto`(전 필드 nullable, `ignoreUnknownKeys=true`) → `WorkflowStateMapper`(명시적 requiredness). `current_slice`/`slice_queue`/`role_sliced`/`usage_guard`는 `RawJsonValue`로 sanitize 후 opaque 운반, readiness/CTA 제외.
- provider-shaped surface 8곳(§3.5). 기능적 결합은 recovery 진단 경로 1개(#7)뿐.

## 4.2 Harness Kit (orchestration, source 확인)

```
run-cycle.ps1 (4180행)
  ├─ Invoke-RunCycleWrapper (native & $ScriptPath @wrapperArgs; HARNESS_TASK_THREAD_ID env 주입)
  │    ├─ scripts/invoke-planning-cycle.ps1  → planning-claude-runner.ps1
  │    ├─ scripts/invoke-code-execution.ps1  → code-deterministic-runner / code-local-preflight / code-role-sliced-runner / code-claude-runner
  │    └─ scripts/invoke-document-execution.ps1 → doc-*-runner
  ├─ ops-validation gate (line 3877): ops_validation.passed=false AND wrapper∈{doc,code} → wrapper="none" (planning/replan은 미게이트: fresh workspace deadlock 방지)
  ├─ Assert-RunCycleNativeChildSucceeded: & $child 직후 $LASTEXITCODE 확인 후 throw
  └─ lineage snapshot + usage ledger 기록
```

- 필수 runtime 의존성 12개 정본(`harness-runtime-dependency-inventory.ps1`): `init-workspace`, `load-profile`, `doctor`, `validate-ops`, `invoke-planning-cycle`, `invoke-document-execution`, `invoke-code-execution`, `pre_handoff_validate`, `harness-context-diet-mode`, `state-surface`, `wrapper-json-state`, `code-workqueue`. optional 2개: `harness-domain`, `build-context-diet-packet`.
- runtime 진실: `WORKFLOW_STATE.json`. 4-file daily surface: `REQUEST_INBOX`/`TODAY_STRATEGY`/`DAILY_HANDOFF`/`WORKFLOW_STATE.json`. 3-file bridge: `.claude/settings.local.json`/`.claude/CLAUDE.md`/`tools/run-cycle.ps1`.

## 4.3 Claude runtime (source 확인 — §13 상세)

- **process launch = 각 runner의 `Start-ClaudeProcess`** (3개, near-byte-identical). Claude Code CLI: `--output-format {text|stream-json}`, `--verbose`(stream-json 시), `--max-turns`, `--add-dir`(workspace+kit), `--allowedTools Read,Glob,Write,Edit,MultiEdit` 또는 `--dangerously-skip-permissions`, `--model`, `--append-system-prompt[-file]`, prompt는 `-p`. stdin 즉시 close(headless EOF).
- **`claude-invoke-core.ps1` = Claude-specific cross-cutting core** (launch 미포함): stop classification 라우팅, `claude_runtime` gate 블록 read-only inspection, session continuity broker(`--resume <id>` only, `--continue` 금지, 14개 rejection reason), usage ledger schema 1.1, stream-json telemetry 파싱/projection/capture(`.raw-stream-json.jsonl`/`.raw-stderr.txt`/`.session.json`). **파일 헤더 명시: "never writes to WORKFLOW_STATE.json ... Default gate state is OFF ... opt-in only."**
- V2 gate: `Test-ClaudeInvokeCoreEnabled` — env `HARNESS_USE_CLAUDE_RUNTIME_V2` truthy→ON / falsy→kill switch / else 지속 gate(`json_gate=passed AND activation_gate=passed AND automatic_cutover≠disabled`) / default OFF.

## 4.4 Secondary LLM (source 확인 — §7 상세)

- 완전 분리 subsystem. provider 기본 `none`. `run-cycle.ps1` 미통합("connecting requires a separately approved design phase" — `SECONDARY_LLM_LANE.md:247`).
- 파이프라인: `build-secondary-llm-evidence-manifest.ps1` → `evidence.manifest.json` → `build-secondary-llm-input-packet.ps1` → `input.packet.json` → (Ollama, opt-in) candidate → `secondary-llm-audit.ps1` → `*.audit.json`.
- 산출 경로: `<workspace-root>/<date>/logs/secondary-llm/<run-id>/` (codex02 §2.6 확정).
- 불변식: `authoritative=false`, `safe_to_auto_adopt=false`, `runtime_integrated=false`, loopback-only, hardware/acceleration gate. 현재 dev host 부적격.

## 4.5 Context diet / packet (source 확인 — §8 상세)

- `HARNESS_CONTEXT_DIET_MODE`: `off|diagnostic|active|invalid`(fail-closed). `active`는 인식되나 **execution-disabled**. `Get-HarnessContextDietMode`는 함수 스코프 내 `Set-StrictMode`(dot-source 누출 사고 후 이동 — 직전 remediation).
- `context-diet-packet.json` schema 1.0: `build-context-diet-packet.ps1`이 `harness-continuity-doctor.ps1`(자식 `powershell`)을 호출해 조립. 필드는 §8.
- `00-wrapper-input.json` schema 2.0: `code-role-sliced-runner.ps1:2343`. **v1 대비 순수 additive**, `HARNESS_ROLE_SLICED_NAVI_MODE=packet_first`에서만 소비. `Test-RoleSlicedWrapperInputPacketIdentity`가 parent에서 `schema_version` + `freshness_seconds` + 3개 content hash drift(TOCTOU)를 worker 실행 직전 재검증.

---

# 5. Change Impact Closure

Codex reviewer POC(§9)를 `scripts/report/` 독립 entrypoint로 구현한다고 가정한 dependency closure:

```
직접 변경(신규 파일):
  scripts/report/invoke-codex-review.ps1            [신규]
  scripts/lib/codex/codex-review-adapter.ps1        [신규]
  scripts/lib/codex/codex-review-contract.ps1       [신규]   (request/result/failure/usage 최소 schema)
  scripts/smoke/smoke-codex-review-*.ps1            [신규, fake invoker]

import/source dependency (읽기 재사용, 무변경):
  scripts/lib/secondary-llm/secondary-llm-evidence-index.ps1     (New-SecondaryLlmEvidenceIndex, Get-SecondaryLlmEvidenceById, Test-SecondaryLlmEvidenceSupportsClaimType)
  scripts/lib/secondary-llm/secondary-llm-claim-evidence.ps1     (Test-SecondaryLlmClaimEvidence, Test-SecondaryLlmClaimsEvidence, Get-SecondaryLlmClaimSummary)
  — 또는 위 두 파일의 provider-neutral 부분을 scripts/lib/agent-review/ 로 복제(§7 판정)

contract dependency (읽기, 무변경):
  scripts/lib/harness-runtime-dependency-inventory.ps1   (필수/optional 목록 — Codex executable을 required로 올리지 않음)
  templates/workspace/WORKFLOW_STATE.json.tpl            (State 무변경 확인 근거)

smoke/test dependency:
  scripts/smoke/smoke-smoke-index-consistency.ps1        (인벤토리 카운트 갱신)
  기존 offline suite 전체 (회귀: live Claude/Ollama/Codex 0건 유지)
  scripts/smoke/smoke-release-package-hygiene.ps1        (신규 파일이 pollution 규칙 위반 아닌지)
  scripts/smoke/smoke-run-cycle-required-dependency-inventory.ps1 (run-cycle 의존성 불변 확인)

documentation dependency:
  scripts/SMOKE_INDEX.md, docs/HARNESS_KIT_MAP.en/ko.md, docs/ROADMAP.md   (카운트/candidate 상태 최소 갱신)
  docs/EXTERNAL_REVIEW_LANE.md 또는 docs/CODEX_REVIEW_LANE.md              [신규, SECONDARY_LLM_LANE.md 미러]

packaging/release dependency:
  scripts/lib/release/release-package-hygiene.ps1        (Codex CLI를 bundle에 넣지 않음 — host dependency)
  doctor.ps1 install-completeness                        (Codex는 optional host dep이므로 required 목록 미추가)

절대 무변경:
  run-cycle.ps1 / invoke-*.ps1 / *-claude-runner.ps1 / claude-invoke-core.ps1 / code-role-sliced-runner.ps1
  WORKFLOW_STATE.json.tpl / state-surface.ps1 / validate-ops.ps1
  Secondary LLM schema/불변식
  3-file bridge / 4-file daily surface
  HRNS 전 source
```

**이 closure 안의 text file은 전부 본문 정독함**(§2.2), `run-cycle.ps1`/`code-role-sliced-runner.ps1`은 관련 섹션 정독 + 나머지는 seam이 그 밖에 있음을 확인.

---

# 6. Claude Runtime Deep Audit

## 6.1 `Start-ClaudeProcess` 3-way 비교

| 항목 | planning | code | doc | 공통? |
|---|---|---|---|---|
| 함수 위치 | `planning-claude-runner.ps1:216` | `code-claude-runner.ps1:120` | `doc-claude-runner.ps1:121` | 이름 동일 |
| `ProcessStartInfo` (`UseShellExecute=$false`, `Redirect*=$true`, `CreateNoWindow=$true`) | ✔ | ✔ | ✔ | **byte-identical** |
| `.ps1` → `powershell.exe -ExecutionPolicy Bypass -File` 래핑 | ✔ | ✔ | ✔ | **동일** |
| stdin 즉시 `.Close()` (headless EOF) | ✔ | ✔ | ✔ | **동일** |
| 이중 runspace stdout/stderr drain (`RunspaceFactory` + `readerScript`) | ✔ | ✔ | ✔ | **동일** (PS 5.1 deadlock 회피) |
| 인용 정규식 | `'\s'` | `'[\s"]'` | `'[\s"]'` | **미세 divergence** |
| heartbeat 소스 | `$Ctx.HeartbeatSeconds` | `$Ctx.HeartbeatSeconds` | `[int]$HeartbeatSeconds` param(기본 10) | divergence |
| timeout (`Get-ClaudeCallTimeoutSeconds`, 기본 1800s) | ✔ | ✔ | ✔ | **동일** |
| kill 체인 (`taskkill /PID /T /F` → `Stop-Process -Force` → `.Kill()` → `WaitForExit(5000)`) via `Stop-ClaudeProcessForTimeout` | ✔ | ✔ | ✔ | **byte-identical** |
| timeout exit code | 124 | 124 | 124 | **동일** |
| stream-json 처리 (`.raw-stream-json.jsonl`/`.raw-stderr.txt` sidecar, telemetry, projection) | core delegate | core delegate | core delegate | **동일** (전부 `claude-invoke-core.ps1`) |
| 결과 객체 `RawStreamJson` 필드 | 없음 | 있음 | 있음 | divergence |
| core 로딩 | **HARD** (부재 시 throw) | SOFT (부재 시 text fallback) | SOFT | divergence |
| retry 분류 | `Test-IsRetryableFailure` | `+ Test-IsTransientClaudeOverload` | `Test-IsRetryableFailure` | code가 더 풍부 |

**판정**: process supervision 골격(ProcessStartInfo, 이중 drain, kill 체인, timeout)은 **3개 runner에서 사실상 중복**. divergence는 사소(인용 regex, heartbeat param 방식, RawStreamJson 필드, core 로딩 hard/soft, retry 분류 풍부도).

## 6.2 `claude-invoke-core.ps1`가 소유하는 것 (전부 Claude-specific)

`Get-ClaudeInvokeCoreStopClassification`(→ `claude-stop-classification.ps1` 단일소스 라우팅), `New/Get-ClaudeRuntimeGateState`(WORKFLOW_STATE의 optional `claude_runtime` 블록 read-only: `version`/`automatic_cutover`/`json_gate`/`session_gate`/`law_gate`/`contract_gate`/`activation_gate`), `Test-ClaudeInvokeCoreEnabled`(V2 umbrella gate), session broker(`Get-ClaudeSessionBrokerMode` env `HARNESS_CLAUDE_SESSION_BROKER`={off|capture|resume|fresh}, `Get-ClaudeSessionResumeDecision` — rejection reasons: `broker_off`/`attempt_kind_preflight`/`missing_record`/`missing_session_id`/`fresh_required`/`context_limit`/`project_mismatch`/`workspace_mismatch`/`date_mismatch`/`wrapper_role_mismatch`/`session_scope_mismatch`/`active_work_key_mismatch`/`session_generation_mismatch`, eligible 시에만 `@('--resume',$sessionId)`), `Write-ClaudeUsageLedgerEntry`(schema 1.1), `Get-ClaudeCoreStreamJsonTelemetry`(파싱: `session_id`/`is_error`/`subtype`/`result`/`num_turns`/`total_cost_usd`/`usage.{input_tokens,output_tokens,cache_creation_input_tokens,cache_read_input_tokens}`), `Convert-ClaudeCoreStreamJsonToResponseText`, `Write-ClaudeCoreStreamJsonCaptureFiles`.

## 6.3 usage ledger schema 1.1 (실측 필드)

경로: `<WorkspaceRoot>/logs/usage-ledger/<date>.jsonl`. 항상: `schema_version='1.1'`, `captured_at`, `date`, `workspace_root_hash`(sha256 of normalized path), `project_root_hash`, `request_thread_id`/`task_thread_id`(env `HARNESS_TASK_THREAD_ID`, regex `^[A-Za-z0-9][A-Za-z0-9._:-]*$`, ≤128), `wrapper_role`, `stage_role`, `attempt_kind`, `output_format`, `prompt_size_estimate`, `session_id_present`(**bool만, id 미저장**), `stop_reason`, `rerun_trigger`, `resume_decision`, `record_path`, `resume_policy`, `session_capture_status`, `resume_eligible`, `actual_resume_applied`. 조건부(`*Present` flag true일 때만): `plan_generation`, `num_turns`, `total_cost_usd`, `input_tokens`, `output_tokens`, `cache_creation_input_tokens`, `cache_read_input_tokens`. `Test-ClaudeUsageTelemetryNumber`: string/bool/NaN/Inf/음수 거부, **"leaves the field absent rather than a fabricated zero"**(line 568 주석) — 제안서 §10 "없는 값을 만들어내지 않는다"와 정확히 일치.

## 6.4 Codex adapter에서 지금 재사용 가능한 것 vs 두 adapter 구현 후 공통화할 것

| | 지금 (POC) | AR-C5 이후 |
|---|---|---|
| process supervisor (ProcessStartInfo/이중 drain/kill 체인/timeout/no-window/stdin-close/byte-limit) | **패턴 복사** + "converge in AR-C5" TODO 주석. 손으로 재구현 금지(§4.8 회귀 위험) | 실제 3-way + Codex 4-way 동일성 확인 후 `scripts/lib/process/bounded-process-supervisor.ps1`로 추출 |
| `claude-stop-classification.ps1` | **재사용 불가** (Claude taxonomy — codex02 §3.2 `REJECT` 수용). Codex adapter가 자체 → 상위 category 매핑 | 상위 category 집합만 공유 (`completed/unavailable/blocked/timed_out/cancelled/usage_limited/contract_invalid/failed`) |
| session broker / `--resume` / continuity record | **재사용 절대 불가** (Claude CLI 전용, `SECONDARY_LLM_LANE.md:22` 원칙) | 없음 |
| usage ledger schema 1.1 + `Test-ClaudeUsageTelemetryNumber` | `UsageObservation` envelope 필드 **개념** 재사용(`*_present` flag, absent≠0) | 공통 `UsageObservation` envelope |
| stream-json telemetry | **재사용 불가** (Claude 출력 shape 전용) | 없음 |

---

# 7. Secondary LLM Deep Audit (함수 단위)

## 7.1 재사용 가능성 표

| 함수 | 파일 | 역할 | 재사용? | 판정 |
|---|---|---|---|---|
| `ConvertTo-SecondaryLlmSafeText` | evidence-manifest / input-packet (중복 정의) | redaction (bearer/api_key/token/secret/password/cookie/authorization/session_id/transcript_id/SOPS_AGE_KEY/`*_DO_NOT_LEAK`/`C:\Users\...`/`/home\|/Users/...`) | **그대로** | Codex review packet redaction에 직접 사용 |
| `ConvertTo-SecondaryLlmSha256Text` | evidence-manifest | sha256 hex (`sha256:` prefix) | **그대로** | packet hash |
| `Assert-SecondaryLlmRunId` | evidence-manifest / input-packet | run-id 검증 (regex `^[A-Za-z0-9][A-Za-z0-9._-]{0,79}$`, trailing dot/space 금지, `.`/`..` 금지, `CON\|PRN\|AUX\|NUL\|COM[1-9]\|LPT[1-9]` 금지) | **그대로** | traversal 방지 |
| `Test-SecondaryLlmPathInside` | evidence-manifest / input-packet | 경로 containment (GetFullPath + StartsWith base + sep) | **그대로** | 산출 경로 격리 |
| `Resolve-SecondaryLlmOutputPath` / `Resolve-SecondaryLlmContainedPath` | evidence-manifest / input-packet | 출력 경로가 run root 밖이면 throw | **그대로** | 산출 격리 |
| `Get-SecondaryLlmJsonBytes` | input-packet | JSON byte-count + sanitize | **그대로** | packet size budget |
| 다단 truncation 로직 (handoff 2000 → diff_stat 2000 → changed_files 50 → 제거 → throw) | input-packet | `MaxPacketBytes`(기본 65536) 맞춤 | **패턴** | Codex packet도 byte budget 필요 |
| `Read-SecondaryLlmEvidenceManifest` | evidence-index | manifest schema/kind 검증 + raw sensitive text 스캔 → `forbidden` issue | **수정 후** (kind 문자열만 교체) | |
| `New-SecondaryLlmEvidenceIndex` | evidence-index | `by_id`/`unavailable_by_id` 맵, missing/duplicate id 검출 | **그대로** | evidence 인덱싱 |
| `Test-SecondaryLlmEvidenceIdExists` / `Get-SecondaryLlmEvidenceById` / `Test-SecondaryLlmEvidenceResultCanSupportClaim` | evidence-index | id 존재/조회/result가 claim 지원 가능(`pass\|partial\|available\|ok\|success`) | **그대로** | |
| `Test-SecondaryLlmEvidenceSupportsClaimType` | evidence-index | claim_type → 허용 evidence id/type 매핑 | **수정 후** | claim_type 하드코딩(`completion`/`validation_result`/`changed_file`/`implemented_file`/`artifact_path`/`next_step`/`risk_note`) — **code-review finding 앵커 claim_type이 없음**. 신규 항목 추가 필요 |
| `Test-SecondaryLlmClaimEvidence` / `Test-SecondaryLlmClaimsEvidence` | claim-evidence | 단일/배치 claim ↔ evidence 검증. `claim_summary{evidence_backed, partially_evidence_backed, unverified, contradicted}`, `safe_to_reference`(blocking issue 또는 contradicted면 false), `safe_to_auto_adopt=false` 고정 | **엔진 재사용, 규칙 확장** | evidence_backed는 evidence_ids 필수, evidence는 존재+available+claim_type 지원, unverified 물질 claim(completion/validation/changed_file/implemented_file) unsafe, `changed_file`은 git diff evidence에 있어야 함(없으면 `changed_file_not_in_git_diff_evidence` contradicted). code-review는 `finding_anchor` claim_type + (파일이 review scope diff에 있음) AND (line이 diff hunk/스냅샷에 존재) 체크 신규 추가 |
| `Get-SecondaryLlmClaimSummary` | claim-evidence | status 카운트 요약 | **그대로** | |
| `Test-SecondaryLlmCommonCandidateFields` | validator | `schema_version=1.0`/`kind`/`task_type`/불변식(`authoritative=false`/`requires_source_verification=true`/`candidate_generated=true`/`runtime_integrated=false`)/`evidence_manifest_path`/`input_packet_path` | **그대로** | 결과 계약 공통 필드 |
| `Test-SecondaryLlmForbiddenCandidateContent` | validator | candidate JSON에서 DAILY_HANDOFF append / next-session prompt / source edit·patch 제안 스캔(KO 포함) | **수정 후** | Codex review 결과는 patch를 내면 안 됨 → source-edit 스캔 재사용, HANDOFF/next-prompt 스캔은 무관 |
| `New-SecondaryLlmCandidateAudit` / `Write-SecondaryLlmCandidateAudit` | audit | schema 검증 + evidence 인덱스 + claim 파생 + `safe_to_reference` 계산 + `required_next_action='verify_against_source_before_use'` | **패턴 재사용** | audit 산출 shape(`kind`/`task_type`/`authoritative=false`/`safe_to_reference`/`safe_to_auto_adopt=false`/`issues[]`/`claim_summary`/`required_next_action`) 그대로 |
| `New-SecondaryLlmCandidateClaimsForAudit` | audit | candidate → claim 파생 (**`diff_summary`/`validation_summary`만**) | **재사용 불가** | code-review finding → claim 파생은 신규 |
| `New-SecondaryLlmReviewerAdviceIssue` (`finding_id` 필드) | reviewer-advice | finding 앵커 issue shape (severity `schema\|unsafe\|forbidden\|warning`) | **그대로** | code-review finding issue |

## 7.2 재사용 불가 (adapter 내부에 남김)

`secondary-llm-config.ps1`의 Ollama env(`HARNESS_SECONDARY_LLM_PROVIDER`, `HARNESS_OLLAMA_BASE_URL`, `Assert-SecondaryLlmLoopbackOnly`), `secondary-llm-ollama-client.ps1`(HTTP tags/chat), `secondary-llm-capability.ps1`(GPU/VRAM/acceleration proof), `secondary-llm-schema.ps1`의 `secondary_llm_*` response kind, `Get-SecondaryLlmCandidateOutputFileName`의 Ollama 전용 파일명.

## 7.3 Git evidence 범위 — **source 확정**

`build-secondary-llm-evidence-manifest.ps1`의 `Get-SecondaryLlmProjectGitSummary`가 실행하는 git 명령 **전부**:
```
git -C <projectRoot> rev-parse --is-inside-work-tree
git -C <projectRoot> diff --name-only        # → changed_files (Select-First 200, sanitize, 절대경로/`..` 필터)
git -C <projectRoot> diff --stat             # → diff_stat_excerpt (12000자 cap)
git -C <projectRoot> diff --check            # → diff_check {exit_code}
```
**포괄하지 않음**: `--cached`(staged), `ls-files --others`(untracked), `<commit>` / `<base>...HEAD`(commit/branch diff), `-M`/`-C`(rename/copy), binary/submodule/LFS, 삭제 파일, 사용자 지정 changed-file allowlist.

→ Codex reviewer용 **complete change-set builder는 신규**. `codex01 §10.1` / `codex02 §7.2` 권고(explicit changed-file manifest + 생성 diff artifact, `--uncommitted` 전체 자동 수집 금지)를 채택하되, "무엇이 정본 review base인가"(commit / base diff / staged / unstaged / manifest)는 **열린 결정**(§14.2).

---

# 8. Packet / Evidence Deep Audit

## 8.1 `context-diet-packet.json` schema 1.0 (실측 전 필드 — `build-context-diet-packet.ps1`)

`schema_version='1.0'`, `generated_at`, `date`, `workspace_root`, `request_thread_id`, `task_thread_id`, `current_status`, `active_work_key`, `plan_generation`, `request_revision_hash`, `allowed_files[]`(정규화·정렬·유일), `target_contract{authorized_target_file, allowed_write_files_count}`, `latest_stage_summary{wrapper_role, stage_role, continuity_class, session_capture_status, request_thread_id, task_thread_id, would_resume, decision_reason}`, `continuity_summary{total_records, actual_resume_applied, capture_only, role_sliced_gate_off, no_capture, legacy_unclassified, thread_count, records_with_request_thread_id, dry_run_ready_threads}`, `prompt_budget{mode='foundation', source_size_bytes, packet_size_bytes, estimated_reduction_ratio}`.

**없음**: task goal, acceptance criteria, required evidence, validation plan + 실제 exit evidence, review base, complete changed-file manifest, actual diff / source snapshot, forbidden read scope, finding output contract. → **reviewer 입력으로 불충분**(codex02 §2.5 확정).

## 8.2 `00-wrapper-input.json` schema 2.0 (실측 전 필드 — `code-role-sliced-runner.ps1:2343`) + 분류

| 필드 | 분류 | Review Packet 참조? |
|---|---|---|
| `schema_version='2.0'`, `run_id`, `step_id` | identity | ✔ (task id) |
| `execution_wrapper='code'`, `orchestration_mode='role_sliced'` | mode | ✘ (role-sliced 전용) |
| `project_root`, `workspace_root`, `kit_root`, `today_root` | pointer | ✔ |
| `workflow_state_path`, `request_structured_path`, `today_strategy_path`, `daily_handoff_path` | pointer(read) | ✔ (pointer만) |
| `runtime_queue_source='WORKFLOW_STATE.queue'` | pointer | △ |
| `current_card_id`, `current_slice_id` | identity | ✔ |
| `authorized_target_file` | read+write authority | ✔ (read scope로만) |
| `current_micro_slice`, `next_continuation_slice` | continuation | ✘ (worker 연속 상태) |
| `allowed_write_files[]` | **write authority** | ✘ **절대 금지** |
| `forbidden_write_files[]` (=request_structured, today_strategy) | **write authority** | ✘ **절대 금지** |
| `budget_continuation_mode`, `budget_continuation_prompt`, `effective_worker_max_turns` | budget | ✘ |
| `generated_at`, `freshness_seconds` | freshness | ✔ (packet 신선도) |
| `workflow_state_hash`, `strategy_hash`, `active_target_hash` | hash | ✔ (drift 탐지) |
| `plan_generation`, `request_revision_hash` | identity/lineage | ✔ |
| `acceptance_criteria[]` (queue snapshot의 success_criteria) | acceptance | ✔ |
| `required_evidence[]` (=`active_target_hash`, `navi_result`, `worker_result`, `unauthorized_diff_review`) | evidence | △ (검증 대상 목록으로 참조) |
| `validation_plan[]` (=`authorized_target_exists`, `unauthorized_changes_absent`, `active_slice_success_criteria_reviewed`) | validation | ✔ (검증 계획으로) |
| `fallback_read_paths[]` | pointer(read) | ✔ |
| `history_risks[]` | history | △ (continuation risk 참조) |

**write authority 필드(`allowed_write_files`, `forbidden_write_files`)가 read-only reviewer 계약에 들어가면 안 된다** — codex02 §3.1/§8.2 확정, claude02 §3.2 재확인. **`00-wrapper-input.json`을 Review Packet으로 승격하지 않는다.** Review Packet은 hash·pointer·acceptance·validation_plan을 **참조**하는 얇은 파생 계약이며 write scope 필드는 **아예 제거**(0 고정 아님).

## 8.3 WORKFLOW_STATE 템플릿 (실측 — `WORKFLOW_STATE.json.tpl`) 과 확장 규칙

top-level: `schema_version`, `artifact_name`, `workflow_state_model`, `required_next_action`(""), `date`, `project_name`, `workspace_root`, `repo_root`, `profile`, `merge_source`, `materialized_from`, `write_mode`, `default_runtime_mode`, `runtime_activation_ready`, `runtime_cutover`, `automatic_cutover`, `compatibility_outputs_required`, `compatibility_outputs`, `state{...}`, `queue{...}`, `cutover{...}`, `notes`.

**템플릿에 `claude_runtime`/`usage_guard`/`role_sliced`/`current_slice` 없음** — 전부 runtime에 wrapper가 추가하는 optional 확장. `claude_runtime`은 V2 gate 개입 시에만 존재. → **Kit의 확장 관례 = runtime-added optional namespaced block, 자체 schema_version, 미개입 시 부재**(`SECONDARY_LLM_LANE.md:88` 규칙과 일관). 장래 `agent_runtime`도 동일 형태여야 한다(§25).

---

# 9. Codex Reviewer Minimum POC — Revised

이번 감사 근거로 codex01 §6 / codex02 §8을 병합·축소한다.

## 9.1 이름 (codex02 §3.1 채택)

`Codex Read-only Review Capability POC`. `external-review framework` / `provider runtime` / `agent-runtime` 네임스페이스를 첫 이름으로 쓰지 않는다. 신규 파일 ≤3 + smoke: `scripts/report/invoke-codex-review.ps1`, `scripts/lib/codex/codex-review-adapter.ps1`, `scripts/lib/codex/codex-review-contract.ps1`.

## 9.2 단계

- **AR-C2a — Fixture-only capability spike (버리는 조사, 신규 파일 0)**: 이 Windows host에서 `codex exec`가 non-interactive로 도는가 / JSONL event shape / `--sandbox read-only`의 실제 read 범위(§10.1 canary) / 한글·공백 경로 encoding. scratch fixture만. 산출 = findings 노트 1건.
- **AR-C2b — Codex-specific Review Packet + 최소 계약**: AR-C2a 관찰 결과로 `ReviewRequest`/`ReviewResult`/`ProviderAvailability`/`ProviderFailure`/`UsageObservation` 최소 schema 설계(codex02 §3.3 범위). Review Packet은 explicit input만 — task id/goal, review base, **complete changed-file manifest**, actual diff/source snapshot, acceptance criteria, deterministic validation 결과 + exit code, allowed read scope(**write scope 필드 없음**), source/State/target hash, relevant project instruction evidence, timeout/output budget, instruction-vs-data delimiter. 기존 `context-diet-packet.json`은 optional source, 무수정.
- **AR-C3 — Standalone offline adapter smoke**: fake executable로 §13 smoke 케이스. provider network 호출 0.
- **AR-C3-live — Manual opt-in live 1건**: sanitized review bundle / 저민감 fixture만(§10.1 통과 전 production `-C` 금지). `-AllowLiveCodex` 명시. no repo/git mutation audit(부모가 전후 snapshot 비교로 채움 — provider 자기주장 금지). usage는 실제 제공값만.

## 9.3 산출물 (codex02 §8.6 경로 확정)

```
<workspace>/<date>/logs/codex-review/<run-id>/
  review.packet.json
  evidence.manifest.json
  codex.events.jsonl        # 보존 승인 시에만 (§10.7 retention 정책)
  codex-review.result.json
  codex-review.audit.json
```
불변식: `authoritative=false`, `safe_to_auto_adopt=false`, `runtime_integrated=false`, `repository_write=false`, `git_mutation=false`.

## 9.4 POC acceptance criteria (claude02 §1.3 수용 반영)

- 기존 Claude-only `run-cycle` 결과가 byte/behavior 수준 무영향 (기존 shared lib 무수정으로 달성).
- POC 미호출 시 추가 process/network/token 0.
- Codex unavailable이 workflow success로 오인 안 됨.
- 실행 전후 repository/git status·hash 동일.
- structured output이 deterministic validator 통과.
- finding이 허용 evidence(diff scope + line 존재)에 근거하지 않으면 채택 안 됨.
- failure 원인 + provider/CLI version 사후 설명 가능.
- raw secret/session identifier가 일반 report에 미노출.
- POC 파일 제거만으로 완전 rollback.
- **"Human Message Bus 시간 감소"는 acceptance에서 제외** (standalone report는 측정 불가; AR-C4 지표 — claude02 §1.3).

## 9.5 POC 제외 (codex02 §8.7 + 확정)

`build-context-diet-packet.ps1` 수정, `claude-invoke-core.ps1` 접촉, `run-cycle.ps1` 연결, `WORKFLOW_STATE` writer 추가, Secondary LLM schema 접촉, HRNS source/UI, role-binding profile, Router, Claude resolution, Codex write, commit 자동화, MSI/runtime bundle, `scripts/lib/agent-runtime/` 네임스페이스 생성(AR-C5로 연기).

---

# 10. Security / Isolation / Instruction Risks

## 10.1 Codex sandbox read 범위 — **미검증, 실험 설계 (설계 아님)**

canary probe fixture: (a) working root, (b) working root 상위, (c) 두 번째 drive, (d) `%USERPROFILE%`, (e) canary 값 담은 환경변수 — 각 위치에 표식 파일/값. `codex exec --sandbox read-only`에 "어떤 canary를 읽을 수 있는지 보고" prompt로 실행, JSONL의 tool-call 시도 + 최종 답변 검사. **통과 = (a)만 읽힘, working root 밖 tool-call 없음.** `--dangerously-bypass-approvals-and-sandbox` 사용 안 함. 이 probe = AR-C2a, 모든 production-source 노출의 hard gate. "write 못 함"과 "허용 scope 밖 read 못 함"을 혼동하지 않는다(codex02 §7.1).

## 10.2 `AGENTS.md` precedence — **현재 repo로 검증 불가**

`S:\dev\project\hrns_now`에 filesystem `AGENTS.md` 없음(grep 확인). 최소 fixture 필요: root `AGENTS.md` + nested `AGENTS.md` + Harness task와 충돌하는 지시 + read-only/no-git rule + 허용 범위 밖 파일 지시. 기존 `AGENTS.md` 덮어쓰기/bridge 자동 생성 금지. 충돌은 자동 해석 말고 `blocked`/human escalation(codex02 §7.3).

## 10.3 Prompt injection / instruction-vs-data 경계

diff/source/test output 안에 Agent 명령 문자열이 있을 수 있음. Review Packet은 명시적 delimiter + source classification(`harness_instruction` / `provider_native_instruction` / `project_instruction` / `task_instruction` / `evidence_data` / `source_data` / `test_output`)을 갖고, evidence를 instruction으로 취급 안 함. 충돌 시 자동 추측 금지. **재사용 자산**: `Test-SecondaryLlmForbiddenCandidateContent`가 candidate 측 source-edit/patch 제안을 이미 스캔(§7.1).

## 10.4 Structured output 허위 확신

JSON Schema 부합 ≠ 사실. deterministic audit 없이 채택 금지: 존재하지 않는 파일/라인, diff에 없는 변경 주장, 실행하지 않은 테스트 성공 주장, State와 다른 queue/target 주장, 근거 없는 보안·호환성 verdict. **재사용 자산**: `Test-SecondaryLlmClaimEvidence`의 `changed_file_not_in_git_diff_evidence` contradicted 패턴을 `finding_anchor`로 확장.

## 10.5 Raw output / retention

`--json` event stream, prompt, provider response에 절대 사용자 경로/source 내용/환경 정보/provider 내부 id/usage·account 메시지 섞일 수 있음. **재사용 자산**: `ConvertTo-SecondaryLlmSafeText`(redaction), `Assert-SecondaryLlmRunId`(traversal 방지), `Test-SecondaryLlmPathInside`/`Resolve-SecondaryLlmContainedPath`(격리), `Read-SecondaryLlmEvidenceManifest`의 raw sensitive text 스캔 → `forbidden` issue. retention 정책(보존 여부/기간, 최대 byte, release exclusion via `release-package-hygiene.ps1`, run-id containment)은 AR-C2b 설계 항목.

## 10.6 CLI version / output drift

현재 `codex-cli 0.153.2`(codex 확인). min/verified version 범위, option capability probe, unsupported version fail/skip 정책은 열린 결정. `--ignore-user-config`여도 auth는 provider-native store — 실행 후 "무엇이 적용됐나"(config/auth/network) 기록 의무.

## 10.7 `D:\harness-kit` 비-git rollback

`SECONDARY_LLM_LANE.md:35`: *"`<kit-root>` may not itself be a Git repository."* POC task card에: (a) 사전 `verified` zip snapshot + SHA-256(직전 remediation의 `D:\backup\harness-kit-2026.09.02-verified.zip` 패턴), (b) **신규 파일만** 추가(기존 runtime script 무편집 — 편집 필요 시 POC 아님), (c) 신규 경로 전수 change manifest + hash, (d) rollback = manifest 경로 삭제 + 문서 count 줄 revert. runtime log는 source rollback과 분리. broad recursive delete를 rollback 방법으로 삼지 않음(codex02 §5.3).

---

# 11. Token / Cost Analysis

## 11.1 현재 고정비 (source 확인)

- Kit fixed context overhead: `run-cycle.ps1` 4180행이 orchestration이며 Claude에 전달되는 것은 wrapper별 rendered prompt + `--append-system-prompt[-file]`(`prompts/append-{plan,execute-code,execute-doc}-system.md` = 18.7KB/6.0KB/4.3KB) + `--add-dir`(workspace+kit 파일 접근 허용). deterministic preflight(default)로 planning/code/doc의 ordinary Claude 호출은 최대 1/2/1 (MAP §1.9).
- fresh provider call 고정비: Codex `codex exec` 1회 = Review Packet(≤ MaxPacketBytes, 기본 65536) + system/instruction. `build-context-diet-packet.ps1`이 `harness-continuity-doctor.ps1`을 자식 `powershell`로 spawn(deterministic, no-LLM, 저비용) — Review Packet builder가 재사용 시 문서화.

## 11.2 Codex 추가 전에 먼저 graduation하면 더 싼 기존 기능 (전부 구현됨, default 아님 — ROADMAP §4.13 / §1.9)

- `HARNESS_ROLE_SLICED_NAVI_MODE=packet_first` (navi Claude 호출 제거, `00-wrapper-input.json` v2 + parent identity 재검증)
- `HARNESS_ROLE_SLICED_NAVI_MODE=parent_deterministic` (navi bypass, typed create authority + 정확한 deterministic criteria)
- compact planning prompt (`planning-prompt-diet.ps1` — byte-preserving diagnostic, live prompt 미변경)
- `HARNESS_CONTEXT_DIET_MODE=active` (현재 execution-disabled)

이들은 **live A/B 없이 default graduation 금지**(ROADMAP "Do-Not-Do"). → **분리**: 현재 Claude 비용 baseline 측정(AR-C1)에는 포함 가능, Codex POC의 prerequisite로 default-on 하지 않음, 각각 별도 semantic-parity/live gate 유지(codex02 §6.2 `DEFER` 수용).

## 11.3 잘못된 전제 배격

"provider가 늘면 token이 분산되므로 비용이 무조건 준다" — 사용하지 않음. 판단은 `Accepted Change당 총 token + Human intervention + Finding quality + Failure rate`. standalone report POC는 안전성/result contract/reviewer overhead/finding 품질만 측정(§9.4). Human Message Bus 감소는 AR-C4 이후.

---

# 12. Compatibility / Rollback

## 12.1 blast radius (§26 프로토콜 항목)

| 변경 | 파급 |
|---|---|
| 3-file bridge 변경 | Harness(`enter-project.ps1`, 템플릿 3개) + HRNS(`DefaultKitRuntimeResolver.REQUIRED_ENTRYPOINTS`, `RepositoryBridgeProbe`, `RepositoryBridgeProbePort`, `documentation_guide.md` "정확히 3개", native-QA 항목 3, `ui/Strings.kt:391,558`). **POC bridge-free = hard acceptance criterion** (codex02 §10.2). |
| 4-file daily surface 변경 | `init-workspace.ps1`, `validate-ops.ps1`, `state-surface.ps1`, `templates/workspace/`, `smoke-clean-root-onboarding.ps1`, HRNS `ArtifactsState`/mapper/`ActionPolicy`, native-QA 항목 4. POC 무변경. |
| `WORKFLOW_STATE` 변경 | writer(`state-surface.ps1`, `wrapper-json-state.ps1`) + `validate-ops.ps1` + `doctor.ps1` + `smoke-workflow-state-fresh-ui-envelope.ps1` + `smoke-validate-ops-guaranteed-envelope.ps1` + HRNS `HarnessWorkflowStateDto`/`WorkflowStateMapper`/`HarnessProductionContractTest` + guaranteed-envelope 계약. **POC 무변경**; AR-C9에서 `agent_runtime` optional raw extension 진입 시 `HarnessProductionContractTest` + guaranteed-envelope smoke 규율 적용(직전 `required_next_action` BLOCKER 재발 방지). |
| provider executable dependency 추가 | `doctor.ps1` install-completeness + `harness-runtime-dependency-inventory.ps1` + `release-package-hygiene.ps1` + packaging + clean Windows. **Codex CLI는 optional host dependency**(required 목록 미추가, lane 명시 호출 시에만 availability 판정 — codex02 §7.1). |

## 12.2 "POC compatibility 영향 0" — **정정** (codex02 §5.2 `REVISE` 수용)

`run-cycle.ps1` 밖 default-off라도 문자 그대로 0은 아니다. Live Kit에 public script 추가 시 바뀌는 표면: release file set, dependency inventory 판단, docs/map/roadmap, smoke inventory/count, pollution/packaging scan, optional executable availability 진단, daily logs retention. → verdict 전체를 다시 여는 수준은 아니나 **회귀 검증 필요(§13)**.

## 12.3 rollback 단위 — **정정** (§10.7). "디렉터리 2개 삭제"로 표현하지 않음.

---

# 13. Tests / Gates

Live Kit 변경 후 최소 필수:

| gate | 근거 |
|---|---|
| changed PS1 parser check (`[Parser]::ParseFile`) | MAP §7 |
| 신규 fake-invoker offline smoke (§13.1 케이스) | codex02 §6.6 |
| 기존 official offline suite 전체 통과 (live Claude/Ollama/Codex 0건) | ROADMAP §1.2 |
| `doctor.ps1 -Json` / `validate-ops.ps1 -Json` 회귀 (`overall=ok`, check 수 불변) | MAP §1.7 |
| `smoke-run-cycle-required-dependency-inventory.ps1` (run-cycle 의존성 불변) | `harness-runtime-dependency-inventory.ps1` |
| `smoke-release-package-hygiene.ps1` / pollution scan (신규 파일이 규칙 위반 아님) | ROADMAP §1.2 |
| `smoke-smoke-index-consistency.ps1` (인벤토리 카운트 정합 — 현재 실측 100, §3.7) | MAP §1.8 |
| "신규 bridge 파일 없음" 단언 smoke | claude02 §4.4 |
| HRNS `gradlew check` 무변경 정당화 (HRNS source 무변경이면 production-to-production boundary 무영향 근거 기록) | codex02 §5.2 |
| sandbox read-scope canary probe (§10.1) — **production source 노출 hard gate** | codex01 §4.1 |
| complete change-set gate (unstaged/staged/untracked/deleted/renamed/commit/branch-base/binary/submodule/무관 dirty file table-driven) | codex02 §7.2 |
| instruction precedence gate (root/nested `AGENTS.md` fixture) | codex02 §7.3 |
| prompt-injection gate (다른 파일 read / sandbox 우회 / secret 출력 / git mutation / 성공 verdict 강제 명령을 evidence로만 취급) | codex02 §7.4 |
| claim audit gate (finding id/severity/file/line-anchor/claim/supporting evidence/suggested correction; 부모가 path allowlist·line 존재·diff 관련성 확인; audit가 의미적 correctness 증명 과장 금지) | codex02 §7.5 |
| Windows process gate (공백·한글 경로 / quote·newline prompt / UTF-8 stdout·stderr / 대량 출력 / timeout / cancellation / child 잔존 / truncation / console window) — fake executable로 증명, Claude runner 무비판 복사 금지 | codex02 §7.6 |
| output/retention gate (JSONL max byte / raw 보존 여부 / absolute path·secret masking / session·account id 비저장 / release 제외 / date·run-id containment·traversal 방지) | codex02 §7.7 |
| CLI version/capability gate (executable resolution / `--version` parse / required option 존재 / unsupported / auth unavailable / live default-off) | codex02 §7.8 |

## 13.1 offline smoke 케이스 (codex02 §6.6, "정확히 20개"로 고정하지 않고 AR-C2a 관찰 후 확정)

executable 없음 / unsupported version / 정상 structured result / empty output / invalid JSON / output schema 위반 / non-zero exit / auth failure / usage·quota 제한 / timeout / cancellation / 대량 stdout·stderr / UTF-8 한글 출력 / 공백·한글·quote 경로 / output byte limit / child process 잔존 / 실행 전후 파일·hash·status 불변 / scope 밖 finding reject / 존재하지 않는 line·path finding reject / raw secret-shaped 값 redaction.

---

# 14. Open Decisions

## 14.1 실험 없이 결정 불가

- **Codex `--sandbox read-only`의 실제 read 범위** (§10.1 canary probe 전까지 production source 노출 금지)
- **Windows에서 live Codex noninteractive 호출 성공 여부 + auth 유효성** (installed=true, live_capability=unverified — codex02 §2.1)
- **project `AGENTS.md` ↔ ephemeral review instruction precedence** (§10.2 fixture)
- **`--ephemeral`이 host에 남기는 흔적**
- **실제 quota/usage failure의 exit/output shape**

## 14.2 사용자 / Integration Owner 결정

- **Review base 정본**: explicit changed-file manifest + 생성 diff / commit diff / base-branch diff / staged / unstaged 중 무엇? (`--uncommitted` 전체 자동 수집은 미채택으로 수렴)
- **Native UI QA를 live Kit POC mutation의 hard prerequisite로 둘지** (claude02 §1.8: POC *구현*에 한해 yes; 계획·spike는 병행)
- **A/B graduation threshold** (표본 수, task class 분류, human review time 측정 방식, finding precision/recall + regression 발견률, reviewer 추가 비용 허용 기준)
- **Dual-provider 적용 조건**: 어떤 risk/task class에서 Codex reviewer opt-in? 기본 1 round 초과 조건? disagreement resolver? (codex 권고: high-risk·contract/security/transaction/compatibility 변경부터; safety·acceptance·source-evidence 충돌은 Human)
- **`agent_runtime` optional extension을 State에 넣는 정확한 시점** (AR-C9, §25)
- **provider executable = bundled runtime vs host dependency** (codex 권고: 초기 optional host dependency; packaging 결정은 runtime contract 안정 후)
- **Router 소유자 = Harness Runtime** 명시 확정 (양측 함의, 아직 명문화 안 됨 — codex02 §1.4 `ACCEPT_WITH_BOUNDARY`: HRNS `ActionPolicy`(허용 action) ↔ Harness Router(허용된 action 내 provider/lane 선택) 책임 분리)

## 14.3 Agent Runtime과 분리해 독립 처리

- HRNS `queue.active.*` / `queue.blocked_reason` / `queue.last_updated_at` requiredness hardening (§3.7, codex01 §1.7). 별도 bounded task. Agent Runtime 변경과 절대 혼합 금지.
- Kit smoke 인벤토리 문서 drift 95→100 (§3.7). Kit 자체 유지보수.

---

# 15. Changes Required to Future Integrated Plan

| 기존 판단 (출처) | 03 판정 | 이유 |
|---|---|---|
| 방향(HRNS provider-blind, `run-cycle.ps1` 밖 Codex reviewer POC, Router 보류) | **ACCEPT** | source 재감사로 불변 |
| claude02 §3.2: `usage_guard`/`role_sliced` slot에 provider 상태 | **REJECT** | `SECONDARY_LLM_LANE.md:88` 명시 규칙 — 전용 namespaced key + 자체 schema_version. codex02 §4.1 수용 |
| claude01: `context-diet-packet.json`을 reviewer 입력으로 거의 그대로 | **REVISE** | 실측 필드에 goal/acceptance/diff/review-base/finding-contract 없음. 별도 얇은 builder |
| claude01: Secondary LLM evidence builder 직접 재사용 | **REVISE** | git 범위 = `diff --name-only/--stat/--check` = unstaged only. **엔진 재사용, git 수집 신규** |
| claude01: Ollama `reviewer_advice` = Codex reviewer 대응 | **REVISE** | 실측 — 그건 advisory 산출물 메타 검토. Codex code reviewer는 신규 개념 |
| codex01 §1.3 wrapper 파일명 | **ACCEPT (정정)** | `invoke-planning-cycle/code-execution/document-execution.ps1` |
| claude02 §2.2: `Start-ClaudeProcess` 미검증 유보 | **ACCEPT (해소)** | 3 runner 모두 정의. §13 3-way 표 |
| claude01/02: HRNS 결합 = enum 5 + 파일명 2 | **REVISE** | +recovery 진단 경로 1개(`WorkspaceRecoveryDiagnosticsAdapter.kt:32` → `logs/claude-session-continuity/`). 결론(구조 결합 아님)은 불변 |
| claude02 §3.1: process supervisor 추출 미루되 재구현 금지 (패턴 복사 + TODO) | **ACCEPT** | §13 3-way 표가 near-identical 확인 — 복사 근거 충분 |
| claude02 §3.2: Review Packet write-scope 필드 제거(0 고정 아님) | **ACCEPT** | §8.2 — `allowed/forbidden_write_files`가 write authority |
| claude02 §3.3: phase 수 ~8로 압축, `agent-runtime/` 네임스페이스 AR-C5 연기 | **ACCEPT** | codex02 §3.1도 동일 방향 |
| claude02 §1.2: A/B 3단 분리, POC 미차단 | **ACCEPT** | codex02 §6.1도 동일. 확대 A/B는 graduation gate |
| claude02 §1.3: POC acceptance에서 "Human Bus 시간 감소" 제거 | **ACCEPT** | codex02 §3.6 — standalone report는 측정 불가 |
| claude02 §1.4/§1.5/§1.6/§1.7: sandbox read-scope / `AGENTS.md` fixture / prompt injection / retention / 비-git rollback | **ACCEPT** | §10 전부 실험/gate로 반영 |
| claude02 §1.8: Native UI QA는 POC 구현 전 완료(계획은 병행) | **ACCEPT** | codex02 §1.7도 동일 |
| Router 소유자 = Harness Runtime | **ACCEPT (명문화 필요)** | codex02 §1.4 boundary 조건 포함 |
| lane 모델을 task class/budget tier와 "문자 그대로 병합" (claude01) | **REVISE** | codex02 §4.2: 같은 축 아님. **단일 정책 정본(`TASK_CLASS_TOKEN_POLICY.md`가 mapping rule 소유)에는 동의, 단일 enum 병합에는 비동의.** `task_class`/`budget_tier`/`execution_lane` 별도 필드, Router 전에 `execution_lane`을 runtime contract화 안 함 |
| codex02 §6.3: POC 기본 provider `none` 불필요 (명시 실행이 곧 선택) | **DEFER** | `invoke-codex-review.ps1` 명시 실행 = provider 선택이 맞으나, `-AllowLiveCodex` gate + fake invoker 기본은 유지. 사소 — Round 4에서 확정 |
| Secondary LLM 함수 단위 추출 목록 | **DONE (§7.1)** | 5개 helper + evidence-index + reviewer-advice head 정독. 재사용/수정/불가 판정 완료 |
| execution packet schema 2.0 전수 | **DONE (§8.2)** | `code-role-sliced-runner.ps1:2343` 정독, 필드별 분류 완료 |

---

# 16. Final Recommendation

**지금 Integrated Plan 작성 단계로 넘어가면 안 된다.**

이유:
1. §14.1의 5개 항목(Codex sandbox read 범위, live noninteractive 호출 가능성, `AGENTS.md` precedence, `--ephemeral` 흔적, quota failure shape)은 **fixture/실험 없이 결정 불가**이며, 이 중 read-scope와 live 호출 가능성은 POC 구조(sanitized bundle vs 실 repo, 계약 형태)를 좌우한다.
2. 프로토콜상 **Round 4(상호 03 교차검토)** 후 **외부 확인**이 남아 있다. codex03가 아직 없다.
3. 03에서 3건의 실질 정정(state key 관례, packet 성격, git 범위)이 나왔으므로 codex03도 유사한 정정을 낼 가능성이 있고, 그 대조가 필요하다.

**Round 4로 넘어갈 준비는 됐다.** 공통 사실 기반은 다음으로 확정:
- HRNS provider-blind (결합 = taxonomy 라벨 + recovery 진단 경로 1개). `HarnessCommand`/encoder/policy/mapper에 provider 개념 0.
- Claude launch = 3 runner의 `Start-ClaudeProcess` (near-identical). `claude-invoke-core.ps1` = Claude-specific cross-cutting core (launch 미포함, WORKFLOW_STATE 무쓰기, default OFF).
- Secondary LLM = 분리 subsystem, `run-cycle.ps1` 미통합. 재사용 = evidence-index + claim-evidence + audit **엔진**; 재사용 불가 = git 수집 범위(unstaged only) + Ollama config/client/capability + `secondary_llm_*` response kind.
- `00-wrapper-input.json` v2 = write authority 포함 role-sliced 패킷 → Review Packet으로 승격 불가. `context-diet-packet.json` v1 = goal/acceptance/diff 부재 → 불충분. Review Packet은 신규 얇은 파생 계약.
- Kit State 확장 관례 = runtime-added optional namespaced block + 자체 schema_version. 장래 `agent_runtime`도 동일.
- Codex는 Kit에 전무(clean slate). CLI 0.153.2 installed, live capability unverified.

가장 작은 가치 단위:
```
현재 Claude-only workflow 유지
→ AR-C2a fixture-only capability spike (신규 파일 0)
→ AR-C2b Codex-specific Review Packet + 최소 계약 (write scope 필드 없음)
→ AR-C3 fake-invoker offline smoke
→ AR-C3-live sanitized bundle 1건 (read-scope probe 통과 후)
→ deterministic claim/mutation audit
→ Human-visible recommendation
```
이 결과가 안전성·result contract·overhead·finding 품질을 증명하기 전에는 generic Provider Runtime, Router, State/UI, Codex write, Ollama 연결, guarded Claude→Codex를 설계·구현 범위로 올리지 않는다.

---

# 17. 프로토콜 §37 12문항 답변

1. **`D:\harness-kit` 전체 inventory를 실제로 확인했는가?** — 예. `find` 기준 246개 파일 전수 목록화, 확장자별 분류 + 범주 매핑(§2.1).
2. **본문을 끝까지 읽은 파일은 몇 개인가?** — Kit: FULL 17개 .ps1 + 5개 .md + 1개 .tpl + 1개 .json = **24개**; PARTIAL 3개(`run-cycle.ps1` ~450/4180행, `code-role-sliced-runner.ps1` ~130/3282행, `secondary-llm-reviewer-advice.ps1` 160/~600행). HRNS: FULL ~15개 .kt + 7개 .md (Round 0–2).
3. **search-only 파일은 몇 개인가?** — Kit 전 tree(246개)에 term-list `rg` 스윕 적용. 그중 Level 2로 승격되지 않은 본문 미검토 = §2.4 표 기준 Kit `.ps1` ~153 + `.md` 27 + `.tpl` 15 + `.py` 13.
4. **내용 미검토 파일은 몇 개인가, 왜인가?** — §2.4. 범주별 사유: smoke 100개(POC dependency closure 밖, 회귀 gate로만 관련) / 대형 planning·execution 내부 로직(provider seam이 그 위·아래에 있음) / python sidecar(PS1이 authoritative) / scratch(2026-06 진단 산출물, non-dependency) / 나머지 template·profile·core doc(term sweep으로 provider 무관 확인).
5. **Agent Runtime 변경 dependency closure를 모두 읽었는가?** — 예(§5). 신규 파일 대상 + import/contract/smoke/doc/packaging dependency의 text file 전부 본문 정독. `run-cycle.ps1`/`code-role-sliced-runner.ps1`은 관련 섹션 정독 + seam이 그 밖임 확인.
6. **Claude runtime의 실제 process launch/seam을 함수 수준에서 확인했는가?** — 예. `Start-ClaudeProcess` × 3 (planning/code/doc) 전문 정독 + `claude-invoke-core.ps1` 전문 정독. §13 3-way 비교표. seam = 각 runner의 launcher(공통화 가능) + `claude-invoke-core.ps1`(Claude 전용, 공통화 불가).
7. **Secondary LLM helper의 함수 수준 재사용 가능성을 확인했는가?** — 예. `build-secondary-llm-evidence-manifest.ps1`, `build-secondary-llm-input-packet.ps1`, `secondary-llm-audit.ps1`, `secondary-llm-claim-evidence.ps1`, `secondary-llm-validator.ps1`, `secondary-llm-evidence-index.ps1` 전문 + `secondary-llm-reviewer-advice.ps1` head 정독. §7.1 함수별 그대로/수정후/패턴/불가 판정표.
8. **execution packet schema 2.0을 실제 source에서 확인했는가?** — 예. `code-role-sliced-runner.ps1:2343` `$wrapperInput` 리터럴 정독. §8.2 전 필드 + identity/pointer/read·write authority/continuation/freshness/hash/validation/acceptance/evidence/budget 분류.
9. **현재 01/02에서 수정해야 할 가장 중요한 사실 3개는?** — (a) `usage_guard`/`role_sliced` slot 재사용 철회 → Kit 관례는 전용 namespaced key(`SECONDARY_LLM_LANE.md:88`). (b) `context-diet-packet.json`은 reviewer 입력으로 불충분(goal/acceptance/diff/review-base 부재). (c) Secondary LLM git evidence = `diff --name-only/--stat/--check` = unstaged only → change-set builder 신규.
10. **현재 제안보다 더 작은 첫 POC가 가능한가?** — 예. AR-C2a "fixture-only capability spike"를 **신규 파일 0의 버리는 조사**로 분리(계약·smoke·commit 없이 관찰만) → 관찰 결과로 AR-C2b 계약 설계. live 입력은 sanitized bundle만, production `-C`는 read-scope probe 통과 후. 신규 파일 ≤3.
11. **Codex 추가보다 먼저 token cost를 낮출 기존 Harness 기능은?** — `packet_first` navi / `parent_deterministic` navi / compact planning prompt / context-diet `active`. 전부 구현됐으나 live A/B 없이 default graduation 금지. AR-C1 baseline 측정에는 포함, POC prerequisite로 default-on 하지 않음(§11.2).
12. **현재 evidence만으로 Integrated Plan 작성 단계로 넘어가도 되는가?** — **아니다.** §16. §14.1의 5개 실험 항목 미해결 + Round 4(상호 03 교차검토) + 외부 확인 미완. Round 4로 넘어갈 준비는 됨.

---

## Mutation / Hygiene

- HRNS-NOW production source 변경: 없음
- `D:\harness-kit` 변경: 없음
- 사용자 global config / Registry / 외부 workspace: 접근·변경 없음
- `git add`/`commit`/`push`/`amend`/`reset`/`restore`/`checkout`/`stash`/`rebase`/`clean`: 없음
- build / Gradle / Harness smoke / test 실행: 없음
- live Claude / Codex / Ollama / 네트워크 호출: 없음
- 사용한 명령: `find`, `rg`/`grep`, `git status`/`log`/`show`(읽기), `Read`
- 새로 작성한 파일: `doc/revolution/revOpinion_claude03.md` (사용자 지시)
- 이 문서의 staging/commit 여부는 Git Owner(Codex)와 사용자의 판단이다.
- 다음 단계: `revOpinion_codex03.md`가 생성되면 Mode B로 `revOpinion_claude04.md`(Cross Review)를 작성한다. 그 전까지 production code 수정 없음.
