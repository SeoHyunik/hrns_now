# HRNS-NOW Agent Runtime Evolution — Codex 보강 감사 03

- 작성 주체: Codex (`SELF=codex`)
- 감사 기준일: 2026-09-04 (Asia/Seoul)
- HRNS-NOW: `harness-dev`, `87575273402fb2b50a83c0a6ab4689bc3ea6e311`
- Live Harness Kit: `D:\harness-kit`, kit `2026.09.02`, state/UI contract `1.0/1.0`
- 실행 모드: Mode A — 감사 시작 시 `revOpinion_claude03.md`와 `revOpinion_codex03.md`가 모두 존재하지 않았으므로 Codex 03만 작성했다. 감사 도중 상대 03이 생성됐지만, 시작 시점 모드를 바꾸거나 상대 03을 읽지 않았다.
- mutation: 이 문서 한 파일만 생성. production source, live Kit, State, Registry, Git index/history는 변경하지 않았다.

# 1. Executive Verdict

기존 01/02의 핵심 방향, 즉 “Claude 실행 경로를 보존하면서 Codex를 독립 reviewer로 먼저 검증한다”는 결론은 유지한다. 다만 실제 source를 대조한 결과 첫 POC는 기존 제안보다 더 작아야 한다.

현재 근거로 허용할 수 있는 최소 후보는 다음뿐이다.

> `run-cycle.ps1`, `WORKFLOW_STATE.json`, HRNS-NOW, 3-file bridge를 건드리지 않는 수동·default-off·read-only Codex review lane

아직 허용할 수 없는 것은 generic provider framework, provider router, Codex write lane, State required field, 자동 debate, 자동 commit, Ollama runtime 통합이다.

중요한 사실 수정은 세 가지다.

1. HRNS-NOW는 Harness command 실행 경계에서는 provider-blind이지만 전체 제품은 provider-neutral이 아니다. `StopReason`, UI 문구, `RuntimeConfig.claudeCommand`, `.claude` bridge probe, Claude continuity 진단이 provider-shaped다.
2. `claude-invoke-core.ps1`은 실제 process supervisor가 아니다. planning/code/doc의 각 runner가 별도 `Start-ClaudeProcess`를 보유하며 launch, argument, stdin, stream drain, timeout, kill, no-window를 직접 처리한다. core는 stop classification, gate, session continuity, usage ledger, stream-json projection/capture를 소유한다.
3. Secondary LLM evidence manifest는 complete Git change set을 보장하지 않는다. 실제 Git 수집은 기본 `git diff --name-only`, `git diff --stat`, `git diff --check`뿐이므로 staged, untracked, commit/base scope를 놓친다.

따라서 Integrated Implementation Plan 단계로 바로 넘어가는 것은 아직 이르다. 양쪽 03의 04 cross review와 Codex CLI의 review scope·sandbox/read isolation·instruction precedence를 확인하는 제한된 실험이 먼저 필요하다.

# 2. Audit Coverage

## 2.1 D:\harness-kit 전체 inventory

`D:\harness-kit` 아래 regular file 246개를 모두 경로·크기·확장자 기준으로 inventory했다. NUL byte 검사를 기준으로 binary는 0개였고, 246개 모두 text로 판별됐다. 다만 그중 7개는 `scratch/diag-planning-hang/` 아래 생성/runtime 진단 산출물이므로 본문 의미 분석 대신 metadata만 확인했다.

| 범주 | 전체 | Full Read | Search Only | Metadata Only | 미검토 |
| -- | -: | --------: | ----------: | ------------: | --: |
| Markdown/docs | 32 | 14 | 18 | 0 | 0 |
| source/script | 90 | 39 | 51 | 0 | 0 |
| schema/json/config/template | 21 | 1 | 20 | 0 | 0 |
| smoke/test | 96 | 7 | 89 | 0 | 0 |
| generated/runtime artifact | 7 | 0 | 0 | 7 | 0 |
| binary | 0 | 0 | 0 | 0 | 0 |
| **합계** | **246** | **61** | **178** | **7** | **0** |

여기서 `미검토=0`은 “모든 파일 본문을 읽었다”는 뜻이 아니다. 61개는 본문 끝까지 읽었고, 178개는 전체 파일에 대한 지정 키워드 검색 및 경로/역할 분류까지만 했으며, 7개는 metadata만 확인했다.

## 2.2 Full Read manifest

본문을 끝까지 읽은 Kit 파일은 61개다.

- 문서 14개: root `README.md`, `docs/PROJECT_ONBOARDING.md`, `STATE_MODEL.md`, `OPERATING_GUIDE.md`, `HARNESS_KIT_MAP.en.md`, `HARNESS_KIT_MAP.ko.md`, `ROADMAP.md`, `TASK_CLASS_TOKEN_POLICY.md`, `SECONDARY_LLM_LANE.md`, `SECURITY_MODEL.md`, `STATE_SURFACE_REFERENCE.md`, `PYTHON_SIDECAR_BOUNDARY.md`, `WORKSPACE_SPEC.md`, `scripts/SMOKE_INDEX.md`.
- config 1개: `kit-version.json`.
- Secondary LLM lib 17개: `scripts/lib/secondary-llm/`의 현재 파일 전부.
- report 10개: `build-context-diet-packet.ps1`, `build-secondary-llm-evidence-manifest.ps1`, `build-secondary-llm-input-packet.ps1`, `invoke-secondary-llm-advisory.ps1`, `audit-secondary-llm-candidate.ps1`, `calibrate-secondary-llm-live.ps1`, `check-secondary-llm-capability.ps1`, `probe-secondary-llm-acceleration-live.ps1`, `scan-secondary-llm-docs-mismatch.ps1`, `write-secondary-llm-handoff-pointer.ps1`.
- Claude/공통 production 12개: `planning-claude-runner.ps1`, `code-claude-runner.ps1`, `doc-claude-runner.ps1`, `claude-invoke-core.ps1`, `claude-stop-classification.ps1`, `wrapper-io.ps1`, `doc-execution-support.ps1`, `harness-context-diet-mode.ps1`, `state-surface.ps1`, `validate-ops.ps1`, `release-package-hygiene.ps1`, `harness-runtime-dependency-inventory.ps1`.
- smoke 7개: `smoke-release-package-hygiene.ps1`, `smoke-clean-root-onboarding.ps1`, `smoke-claude-stdin-redirect.ps1`, `smoke-claude-invoke-core.ps1`, `smoke-role-sliced-packet-first-navi.ps1`, `smoke-secondary-llm-evidence-manifest.ps1`, `smoke-secondary-llm-input-packet.ps1`.

`run-cycle.ps1`, 세 invoke wrapper, `code-role-sliced-runner.ps1`, `doctor.ps1`, HRNS 연계에 필요한 대형 queue/state 모듈은 호출부·함수·계약 구간을 읽었지만 파일 전체를 끝까지 읽지는 않았으므로 보수적으로 Search Only에 넣었다. 특히 관련 smoke 89개를 모두 본문 완독했다고 주장하지 않는다. 이는 이번 감사의 coverage 한계이며, prospective minimum POC의 직접 dependency closure는 확인했지만 전체 기존 regression suite의 구현 세부를 전수 완독한 상태는 아니다.

## 2.3 Level 3 검색 결과

generated 7개를 제외한 239개 text file에 지정된 Agent Runtime 키워드를 검색했다. 234개가 하나 이상 일치했고 5개는 불일치했다.

- 불일치: `kit-version.json`, `python/hooks/encoding_guard.py`, `python/scripts/validate_encoding.py`, `scripts/validate-encoding.ps1`, `scripts/lib/smoke/smoke-scratch-lifecycle.ps1`.
- `kit-version.json`은 version contract라 Full Read로 승격했다.
- 나머지 네 파일은 encoding 또는 scratch lifecycle 전용이어서 Agent Runtime POC의 직접 semantic dependency가 아니다. 단 release hygiene 관점의 존재는 inventory에 남겼다.
- 키워드 일치가 곧 직접 영향 파일이라는 뜻은 아니다. 광범위한 `agent`, `queue`, `token`, `review` 용어 때문에 template, historical compatibility, smoke가 다수 포착됐고, 직접 closure가 아닌 파일은 Search Only로 유지했다.

generated 7개는 `scratch/diag-planning-hang/`의 A/B/C stdout·stderr 및 `empty.stdin`이다. 진단 생성물이며 release dependency가 아니고 `release-package-hygiene.ps1`의 scratch 제외 대상이다. live Kit 내부 backup/reference regular file과 binary는 없었다. `D:\backup`은 이번 Kit inventory 범위가 아니므로 순회하거나 압축 해제하지 않았다.

## 2.4 HRNS-NOW 변경 경계 read set

현재 source 기준으로 `HarnessCommand`, `HarnessCommandMapper`(`ExecuteHarnessActionUseCase.kt` 내), `HarnessCommandEncoder`, `HarnessRunnerPort`, `PowerShellHarnessAdapter`, `ActionPolicy`, `ClosurePolicy`, `CompatibilityPolicy`, `StopReason`, `WorkflowStatus`, `WorkflowState`, `HarnessWorkflowStateDto`, `WorkflowStateMapper`, `WorkflowStateParser`, `DefaultKitRuntimeResolver`, `RepositoryBridgeProbe`, `ProjectRegistryDto`/Registry adapter, `RuntimeConfig`, `WorkspaceRecoveryDiagnosticsAdapter`, cockpit/run-status projection 및 provider-shaped UI 문자열을 다시 확인했다.

# 3. Corrections to 01/02

## 3.1 HRNS-NOW provider neutrality 표현 수정

01/02의 “HRNS core는 provider-neutral”이라는 표현은 범위가 너무 넓다.

- provider-blind인 부분: `HarnessCommand`는 Doctor/Validate/Onboard/Bootstrap/Planning/Replan/Code/Doc/Closure라는 Harness operation만 표현한다. mapper/encoder/runner port/PowerShell adapter도 provider를 선택하지 않고 Kit entrypoint를 실행한다.
- provider-coupled인 부분: `StopReason.ClaudeContextLimit`, `ClaudeCallTimeout`, `ClaudeResponseEmpty`, `ClaudeResponseTooShort`, `TransientClaudeOverloaded`; `RuntimeConfig.claudeCommand`와 `HRNS_CLAUDE_COMMAND`; `.claude/settings.local.json`/`.claude/CLAUDE.md` probe; `logs/claude-session-continuity`; UI의 Claude-specific label/recovery 문구.

정정된 결론은 “HRNS 실행 제어 경계는 provider-blind이나 진단·복구·bridge observability는 Claude-coupled”다. 독립 reviewer POC에는 HRNS 변경이 필요 없지만, 장기 multi-provider runtime을 주장하려면 이 vocabulary까지 별도 migration해야 한다.

## 3.2 Claude invoke core 역할 수정

`claude-invoke-core.ps1`을 곧바로 provider adapter 또는 process core로 재사용하자는 판단은 REJECT한다. 이 파일은 다음을 소유한다.

- optional `WORKFLOW_STATE.claude_runtime` gate 해석
- 중앙 stop classification 진입점
- session broker와 `--resume` 판단
- continuity record
- usage ledger schema 1.1
- stream-json telemetry/projection/capture

반면 process launch와 supervision은 각 runner의 `Start-ClaudeProcess`에 중복돼 있다. Codex adapter에는 기존 core 자체가 아니라 process supervision 패턴 일부만 참고할 수 있다.

## 3.3 Secondary LLM Git evidence 완전성 수정

`Get-SecondaryLlmProjectGitSummary`가 만드는 `changed_files`를 complete review scope로 간주하면 안 된다. 기본 `git diff`는 unstaged tracked 변경만 본다. 기존 manifest는 “해당 명령에서 관측한 evidence catalog”이지 “전체 change-set manifest”가 아니다.

## 3.4 Execution Packet 2.0 재사용 범위 수정

`00-wrapper-input.json` 2.0은 role-sliced write worker용 packet이다. write authority, continuation prompt, Harness 내부 root와 daily path를 포함하므로 read-only reviewer packet으로 통째로 복제하면 최소권한 원칙을 위반한다. review packet은 이 packet의 path/hash와 acceptance/evidence pointer만 필요할 때 참조해야 한다.

## 3.5 State와 policy taxonomy 수정

- Kit에는 이미 optional `claude_runtime` gate metadata가 있다. 이를 이름만 `agent_runtime`으로 바꾸거나 Codex를 끼워 넣는 것은 POC 범위를 넘는다.
- `role_sliced`와 `usage_guard`는 현재 의미를 가진 optional state다. provider/lane 정보를 넣는 빈 슬롯이 아니다.
- Task Class, Budget Tier, Lane, Role, Provider, Risk는 서로 직교한다. `TASK_CLASS_TOKEN_POLICY.md`는 task class와 budget mapping의 정본으로 유지하되 여섯 축을 하나의 enum으로 합치지 않는다.

## 3.6 먼저 쓸 수 있는 더 싼 lever

Codex 추가 전에도 `parent_deterministic` navi, `packet_first`, compact planning, deterministic preflight, usage-ledger 기반 계측을 graduation하면 provider 한 번을 추가하는 것보다 싸게 token을 줄일 수 있다. 이들은 현재 default가 아니므로 실측 A/B 없이 “절감 완료”라고 표현해서는 안 된다.

# 4. Current Architecture — Verified

## 4.1 Harness orchestration

`run-cycle.ps1`이 state/ops/closure gate와 wrapper dispatch를 소유한다. 실제 wrapper entrypoint는 정확히 다음 세 개다.

- `scripts/invoke-planning-cycle.ps1`
- `scripts/invoke-code-execution.ps1`
- `scripts/invoke-document-execution.ps1`

role-sliced code path는 `code-role-sliced-runner.ps1`로 들어가며 navi/worker를 Claude로 실행하고 reviewer/dockeeper의 일부 판정을 parent deterministic으로 수행할 수 있다. default daily truth는 `WORKFLOW_STATE.json`, required daily surface는 4개, repository bridge는 3개다.

## 4.2 Claude runtime

planning/code/doc은 각자의 runner에서 같은 이름의 `Start-ClaudeProcess`를 중복 구현한다. role-sliced stage는 code runner의 함수를 사용한다. provider-specific argument와 prompt construction은 각 wrapper/runner에 분산돼 있고, session/usage/stream-json 정책은 `claude-invoke-core.ps1`에 모여 있다. 즉 현재 seam은 하나가 아니라 “세 wrapper + 세 process runner + Claude core/policy”다.

## 4.3 Secondary LLM/Ollama

현재 task type은 `client_check`, `diff_summary`, `validation_summary`, `next_session_prompt`, `docs_mismatch`, `reviewer_advice`다. lane은 다음 속성을 source에서 강제한다.

- default-off, explicit live opt-in
- HTTP loopback-only
- GPU 기본 요구, 최소 VRAM 8GB, 같은 run의 acceleration proof 요구
- CPU-only는 이중 opt-in diagnostic
- `authoritative=false`, `safe_to_auto_adopt=false`, `runtime_integrated=false`
- source/State/queue/closure를 쓰지 않는 advisory candidate
- `run-cycle.ps1` 미통합

이는 공통 result/read layer의 좋은 선례지만 Codex의 provider transport나 Git reviewer를 그대로 구현한 것은 아니다.

## 4.4 HRNS-NOW

HRNS-NOW는 typed Harness command를 PowerShell argument list로 encode하고 JSON diagnostic/Workflow State를 fail-closed로 읽는 desktop control plane이다. Kit root resolver는 `enter-project.ps1`, `doctor.ps1`, `validate-ops.ps1`, `run-cycle.ps1`, `kit-version.json` 존재를 확인한다. compatibility는 state/UI schema major 1을 본다. Registry에는 runtime source와 roots/profile이 있고 provider는 없다.

# 5. Change Impact Closure

## 5.1 첫 Codex reviewer 후보 dependency closure

아래는 최종 구현계획이 아니라 파일 영향권 후보다.

```text
수동 report entrypoint 후보
  -> Codex-specific runner 후보
  -> 경로 containment / UTF-8 / bounded process-result helper
  -> explicit Git change manifest + hash contract
  -> review request/result JSON schema 후보
  -> fake CLI offline smoke
  -> Git scope fixture smoke
  -> sandbox/instruction canary manual-live smoke
  -> SMOKE_INDEX + MAP(ko/en) + ROADMAP + TASK_CLASS_TOKEN_POLICY 문서 갱신
  -> release-package-hygiene + clean-root-onboarding 증명
```

직접 변경 후보 이름은 `scripts/report/invoke-codex-review.ps1`, `scripts/lib/codex/codex-review-runner.ps1`, review result schema, 전용 smoke 정도다. 실제 이름·위치는 04와 외부 확인 뒤 정한다. `run-cycle.ps1`이나 `claude-invoke-core.ps1`을 import할 이유는 없다.

## 5.2 기존 contract blast radius

| surface 변경 | 실제 영향권 | 첫 POC에서 피해야 하는 이유 |
| -- | -- | -- |
| 3-file bridge | `enter-project`/template, HRNS bridge model/probe/UI/test, docs, Native QA | 기존 Claude onboarding 계약을 바꾸고 Codex instruction 파일 생성 문제를 만든다. |
| 4-file daily surface | init/profile/template, doctor/validate/state surface, HRNS artifact/readiness policy, smoke | reviewer log는 required operational truth가 아니다. |
| `WORKFLOW_STATE` | 모든 writer, `state-surface`, `validate-ops`, doctor, HRNS DTO/mapper/compatibility, smoke, production-to-production test | POC 결과는 non-authoritative diagnostic이며 State schema 변경 근거가 없다. |
| provider executable required dependency | doctor/install inventory, resolver, package/manifest, clean Windows | optional reviewer를 core runtime requirement로 승격시킨다. |

Standalone POC 파일은 current release copy 규칙상 exclusion에 걸리지 않으면 포함될 수 있으나, 실제 inclusion은 release-hygiene와 clean-root smoke로 증명해야 한다. `harness-runtime-dependency-inventory.ps1`은 run-cycle 필수 dependency 정본이므로 standalone optional reviewer를 거기에 넣지 않는다.

# 6. Claude Runtime Deep Audit

## 6.1 process supervision 비교

| 기능 | Planning | Code | Doc | `claude-invoke-core` | 공통 여부 |
| -- | -- | -- | -- | -- | -- |
| launch 함수 | `planning-claude-runner.ps1::Start-ClaudeProcess` | `code-claude-runner.ps1::Start-ClaudeProcess` | `doc-claude-runner.ps1::Start-ClaudeProcess` | 없음 | 세 runner에 중복 |
| command resolve | `Get-Command(...).Source`, `.ps1`이면 `powershell.exe -ExecutionPolicy Bypass -File` | 동일 | 동일 | 없음 | 사실상 공통 |
| argument 조립 | runner `Get-BaseClaudeArgs` + planning wrapper | runner `Get-BaseClaudeArgs`; role-sliced는 `Get-RoleSlicedClaudeArgs` | `doc-execution-support.ps1::Get-BaseClaudeArgs` + doc wrapper | session broker가 `--resume`만 제공 | provider/lane별 |
| prompt 전달 | `-p` argument | `-p` argument | `-p` argument | 없음 | Claude-specific 공통 |
| stdin | redirect 후 즉시 `StandardInput.Close()` | 동일 | 동일 | 없음 | 공통 |
| stdout/stderr | 별도 PowerShell runspace에서 `ReadLine`, 동시에 drain·UTF-8 append | 동일 | 동일 | stream-json parse/projection helper | transport는 공통, parse는 Claude-specific |
| timeout | `HARNESS_CLAUDE_CALL_TIMEOUT_SECONDS`, 기본 1800초, heartbeat wait | 동일 | 동일 | 없음 | 공통 |
| cancellation | 명시적 cancellation token 없음 | 없음 | 없음 | 없음 | 미구현 |
| process-tree kill | `taskkill /PID /T /F` 후 `Stop-Process`, `Kill`, 5초 wait | 동일 | 동일 | 없음 | best-effort 공통 |
| 잔존 process 확인 | typed residual verification 없음 | 없음 | 없음 | 없음 | gap |
| no-window | `CreateNoWindow=true`, `UseShellExecute=false` | 동일 | 동일 | 없음 | 공통 |
| encoding | log write는 UTF-8 no BOM; child stdout/stderr encoding property 미지정 | 동일 | 동일 | JSON/text parser | decoding gap |
| output byte cap | 없음; `StringBuilder`에 전체 보관 | 없음 | 없음 | 없음 | 공통 gap |
| stream-json | planning core hard dependency | core 없으면 text fallback | core 없으면 text fallback | telemetry, projected response, raw/session sidecar | Claude-specific |
| session continuity | wrapper가 broker/update 호출 | wrapper/role path가 호출 | wrapper가 호출 | record, identity, resume decision | Claude-specific |
| usage ledger | wrapper 결과에서 호출 | wrapper/role 결과에서 호출 | wrapper 결과에서 호출 | schema 1.1 writer | Claude-specific |

planning/code/doc base argument는 `--output-format`, `--max-turns`, `--add-dir <workspace>`, `--add-dir <kit>`, permission flag, optional `--model`, append-system-prompt, `-p`를 조립한다. output-format 환경 변수는 planning/code/doc별로 나뉜다. role-sliced navi는 `Read,Glob,Grep`, worker는 write tools까지 허용한다.

## 6.2 Codex에서 지금 재사용 가능한 것

그대로 import할 수 있는 것은 거의 없다. 다음은 “패턴”으로 재사용할 수 있다.

- `UseShellExecute=false`, `CreateNoWindow=true`
- stdout/stderr 동시 drain
- explicit timeout과 tree-kill fallback
- 경로 containment, UTF-8 no BOM artifact
- fake invoker seam과 typed process result

다만 Codex runner는 시작부터 output byte cap, explicit encoding, typed timeout/cancel/residual-process 결과를 갖춰야 한다. Claude의 argument, stream-json parser, stop reason, session broker, usage ledger schema를 Codex에 억지로 공유하지 않는다.

두 provider 구현 뒤에만 추출할 공통부는 bounded process supervision transport다. 실제 두 구현이 공통으로 요구한 field만 추출하고 provider argument/parser/session semantics는 adapter 안에 남긴다.

# 7. Secondary LLM Deep Audit

## 7.1 지정 5개 자산 함수 단위 재사용 판정

`그대로`는 현재 Codex review에 의미·계약까지 동일함을 뜻한다. 아래 감사 결과 그대로 재사용 가능한 함수는 없으며, 대부분 “수정 후” 또는 “패턴만”이다.

| 함수 | 역할 | Codex Review 재사용 | 판정 | 이유 |
| -- | -- | -- | -- | -- |
| `Get-SecondaryLlmPropertyValue` (manifest) | 안전한 property lookup | 가능 | 패턴만 | provider 명칭과 manifest DTO가 다르다. |
| `ConvertTo-SecondaryLlmSha256Text` | SHA-256 text hash | 가능 | 수정 후 | generic 이름·byte input 지원이 필요하다. |
| `ConvertTo-SecondaryLlmSafeText` (manifest) | secret/home path redaction | 가능 | 패턴만 | regex-only redaction은 완전한 secret 경계가 아니다. |
| `Assert-SecondaryLlmRunId` (manifest) | run-id 검증 | 가능 | 수정 후 | 규칙은 유효하나 Codex namespace로 분리해야 한다. |
| `New-SecondaryLlmRunId` (manifest) | 시간 기반 id | 가능 | 수정 후 | collision/trace id 정책을 명시해야 한다. |
| `Test-SecondaryLlmPathInside` (manifest) | containment | 가능 | 패턴만 | junction/symlink 및 non-existing target 정책이 부족하다. |
| `Resolve-SecondaryLlmOutputPath` | run-root output 제한 | 가능 | 수정 후 | Codex log root·artifact names가 다르다. |
| `Add-SecondaryLlmEvidence` | evidence row 생성 | 가능 | 수정 후 | Codex finding/scope schema로 변경해야 한다. |
| `Add-SecondaryLlmUnavailable` | unavailable row 생성 | 가능 | 수정 후 | typed reason taxonomy가 별도다. |
| `Read-SecondaryLlmJsonFile` | JSON read | 가능 | 패턴만 | schema/size/encoding fail-closed가 추가돼야 한다. |
| `Get-SecondaryLlmWorkflowSummary` | State 축약 | 제한적 | 불가 | State-free POC에는 필요 없다. |
| `Invoke-SecondaryLlmGitCommand` | Git subprocess | 가능 | 수정 후 | NUL-safe argv/output cap·scope typing이 없다. |
| `Get-SecondaryLlmProjectGitSummary` | unstaged diff 요약 | 가능 | 수정 후 | complete change set을 누락한다. |
| `Get-SecondaryLlmRoleSlicedSummary` | role summary 축약 | 제한적 | 패턴만 | review request에는 optional pointer/hash만 필요하다. |
| `Get-SecondaryLlmHandoffTail` | handoff tail 수집 | 제한적 | 불가 | source review scope와 무관하고 injection/overhead를 늘린다. |
| `Get-SecondaryLlmPropertyValue` (packet) | property lookup | 가능 | 패턴만 | 위와 동일. |
| `ConvertTo-SecondaryLlmSafeText` (packet) | redaction | 가능 | 패턴만 | 위와 동일. |
| `Assert-SecondaryLlmRunId` (packet) | run-id 검증 | 가능 | 수정 후 | 중복 구현부터 제거해야 한다. |
| `New-SecondaryLlmRunId` (packet) | run-id 생성 | 가능 | 수정 후 | 위와 동일. |
| `Test-SecondaryLlmPathInside` (packet) | containment | 가능 | 패턴만 | 위와 동일. |
| `Resolve-SecondaryLlmContainedPath` | run-root path 제한 | 가능 | 수정 후 | Codex artifact contract에 맞춰야 한다. |
| `ConvertTo-SecondaryLlmArray` | scalar/array 정규화 | 가능 | 패턴만 | PowerShell utility로는 유용하나 schema-specific callsite가 필요하다. |
| `Get-SecondaryLlmJsonBytes` | packet byte 크기 | 가능 | 수정 후 | request/result 각각 hard cap이 필요하다. |
| `Resolve-SecondaryLlmAuditEvidenceManifest` | manifest resolve | 가능 | 수정 후 | Codex explicit manifest schema가 다르다. |
| `New-SecondaryLlmCandidateAuditClaim` | candidate→claim 변환 | 제한적 | 패턴만 | Codex output은 findings schema가 적합하다. |
| `New-SecondaryLlmCandidateClaimsForAudit` | candidate claim 추출 | 제한적 | 불가 | diff/validation candidate 전용이다. |
| `New-SecondaryLlmCandidateAudit` | 일반 candidate audit | 가능 | 패턴만 | non-authority 원칙은 유효하나 finding 검증이 달라진다. |
| `Write-SecondaryLlmCandidateAudit` | audit JSON write | 가능 | 수정 후 | Codex result path/schema로 분리해야 한다. |
| `New-SecondaryLlmNextSessionPromptAudit` | next prompt 감사 | 아니오 | 불가 | POC에 prompt handoff가 없다. |
| `Write-SecondaryLlmNextSessionPromptAudit` | next prompt audit write | 아니오 | 불가 | 동일. |
| `Get-SecondaryLlmClaimPropertyValue` | claim property lookup | 가능 | 패턴만 | finding DTO에 맞춰야 한다. |
| `ConvertTo-SecondaryLlmClaimArray` | claim array 정규화 | 가능 | 패턴만 | generic helper 후보일 뿐이다. |
| `New-SecondaryLlmClaimEvidenceIssue` | issue 생성 | 가능 | 수정 후 | severity/status taxonomy가 다르다. |
| `Normalize-SecondaryLlmClaimStatus` | claim status 정규화 | 제한적 | 불가 | Codex findings에는 별도 lifecycle이 필요하다. |
| `Get-SecondaryLlmClaimEvidenceIds` | evidence id 추출 | 가능 | 수정 후 | finding evidence refs로 바꿀 수 있다. |
| `Test-SecondaryLlmValidationEvidenceHasZeroExit` | zero-exit 근거 확인 | 가능 | 수정 후 | 테스트 evidence schema를 명확히 해야 한다. |
| `Test-SecondaryLlmValidationEvidenceContradictsPass` | pass 모순 검사 | 가능 | 수정 후 | 좋은 fail-closed 패턴이다. |
| `Get-SecondaryLlmClaimedFilePath` | changed-file claim path | 가능 | 수정 후 | rename pair와 null path를 지원해야 한다. |
| `Get-SecondaryLlmEvidenceChangedFiles` | manifest changed files | 가능 | 수정 후 | 기존 manifest 자체가 불완전하다. |
| `Test-SecondaryLlmClaimEvidence` | claim별 evidence 검증 | 가능 | 패턴만 | Codex finding line/evidence 검증으로 재설계해야 한다. |
| `Get-SecondaryLlmClaimSummary` | status 집계 | 가능 | 수정 후 | finding severity/disposition 집계로 변경한다. |
| `Test-SecondaryLlmClaimsEvidence` | 전체 claim 검증 | 가능 | 패턴만 | result schema 이후에만 구현 가능하다. |
| `Get-SecondaryLlmCandidatePropertyValue` | candidate lookup | 가능 | 패턴만 | generic helper 수준이다. |
| `ConvertTo-SecondaryLlmCandidateArray` | candidate array 정규화 | 가능 | 패턴만 | generic helper 수준이다. |
| `New-SecondaryLlmCandidateIssue` | issue 생성 | 가능 | 수정 후 | Codex result taxonomy가 필요하다. |
| `New-SecondaryLlmCandidateValidationResult` | validation result | 가능 | 수정 후 | POC result schema에 맞춰야 한다. |
| `Test-SecondaryLlmBooleanField` | boolean contract | 가능 | 수정 후 | 공통 schema validator 패턴이다. |
| `Test-SecondaryLlmCommonCandidateFields` | non-authority 공통 필드 | 가능 | 패턴만 | `candidate_generated/runtime_integrated`를 Codex에 복제할 필요는 없다. |
| `Test-SecondaryLlmForbiddenCandidateContent` | patch/write/handoff 금지 | 가능 | 패턴만 | regex는 보조 방어일 뿐 구조적 read-only 보장이 아니다. |
| `Test-SecondaryLlmDiffSummaryCandidate` | diff summary 검증 | 제한적 | 불가 | Codex reviewer finding과 계약이 다르다. |
| `Test-SecondaryLlmValidationSummaryCandidate` | validation summary 검증 | 제한적 | 불가 | 동일. |
| `Test-SecondaryLlmNextSessionPromptForbiddenContent` | prompt 위험 검사 | 아니오 | 불가 | POC에 next prompt가 없다. |
| `Test-SecondaryLlmNextSessionPromptClaimsObject` | next prompt claim 검증 | 아니오 | 불가 | 동일. |
| `Test-SecondaryLlmNextSessionPromptCandidate` | next prompt 전체 검증 | 아니오 | 불가 | 동일. |
| `Test-SecondaryLlmCandidateSchema` | task별 dispatcher | 제한적 | 패턴만 | Codex result kind 하나부터 시작하면 dispatcher가 불필요하다. |

## 7.2 Git evidence 지원 범위 최종 판정

| scope/형태 | 현재 builder 지원 | 근거/한계 |
| -- | -- | -- |
| unstaged tracked | 예 | 기본 `git diff --name-only/stat/check` |
| staged | 아니오 | `--cached` 호출 없음 |
| untracked | 아니오 | `git status`/`ls-files --others` 없음 |
| commit diff | 아니오 | revision 인자 없음 |
| base branch diff | 아니오 | merge-base/base 인자 없음 |
| rename/copy | 불충분 | name-only 결과만 저장하고 status/old-new pair가 없다. |
| binary | 불충분 | stat 문자열에 나타날 수 있으나 typed binary flag가 없다. |
| submodule | 아니오 | gitlink/submodule typed 처리 없음 |
| LFS | 아니오 | pointer/attribute 판별 없음 |
| explicit changed-file manifest | 아니오 | caller가 scope manifest를 주입·검증하는 contract가 없다. |

최종 판정: 현재 Secondary LLM evidence builder는 complete change set을 보장하지 않는다.

# 8. Packet / Evidence Deep Audit

## 8.1 Execution Packet schema 2.0 field inventory

`code-role-sliced-runner.ps1`이 실제 생성하는 `00-wrapper-input.json` 2.0 필드를 분류하면 다음과 같다.

| 분류 | 필드 |
| -- | -- |
| identity | `schema_version`, `run_id`, `step_id`, `execution_wrapper`, `orchestration_mode`, `runtime_queue_source`, `plan_generation`, `request_revision_hash` |
| pointer | `project_root`, `workspace_root`, `kit_root`, `today_root`, `workflow_state_path`, `request_structured_path`, `today_strategy_path`, `daily_handoff_path`, `current_card_id`, `current_slice_id` |
| read authority | `fallback_read_paths` |
| write authority | `authorized_target_file`, `allowed_write_files`, `forbidden_write_files` |
| continuation | `current_micro_slice`, `next_continuation_slice`, `budget_continuation_mode`, `budget_continuation_prompt` |
| freshness | `generated_at`, `freshness_seconds` |
| hash | `workflow_state_hash`, `strategy_hash`, `active_target_hash` |
| validation | `validation_plan` |
| acceptance | `acceptance_criteria` |
| evidence | `required_evidence` |
| budget | `effective_worker_max_turns` |
| history | `history_risks` |

`Test-RoleSlicedWrapperInputPacketIdentity`는 schema, freshness, 세 hash, generation/revision, queue identity/criteria/target, allowed/forbidden, fallback reads, continuation/history를 parent에서 재검증한다. packet-first에서는 navi 전과 worker 직전에 검사한다. 이는 좋은 TOCTOU pattern이지만 Git change-set completeness를 보장하지는 않는다. `Get-GitDiffNameOnly`도 기본 unstaged diff에 머문다.

## 8.2 Review Packet 재판정

Review Packet에 참조할 후보:

- review request/run id
- authoritative review scope mode
- explicit change manifest path와 SHA-256
- optional execution packet path/hash
- task goal, acceptance criteria pointer 또는 짧은 structured criteria
- test output path/hash와 실제 exit code
- source root 및 result byte limit
- `read_only=true`, `write_authority=false`, evidence/source/test-output은 data라는 instruction boundary

절대 넣지 않을 것:

- `allowed_write_files`, `authorized_target_file`를 write affordance로 노출하는 필드
- `budget_continuation_prompt`
- raw provider transcript, session id, auth/config, 환경 변수 dump
- daily handoff 전체, repository 전체 source dump
- complete/validated라는 검증되지 않은 서술

향후 write lane에서만 검토할 것:

- authorized write set
- patch/apply mode
- mutation token/approval
- rollback target
- post-write State/queue transition

후보 request schema는 확정본이 아니다.

```json
{
  "schema_version": "0.1-candidate",
  "kind": "codex_review_request",
  "run_id": "...",
  "scope": {
    "mode": "uncommitted",
    "manifest_path": "...",
    "manifest_sha256": "..."
  },
  "task": {
    "class": "...",
    "goal": "...",
    "acceptance_criteria": ["..."]
  },
  "evidence": {
    "execution_packet_ref": {"path": "...", "sha256": "..."},
    "test_result_refs": []
  },
  "authority": {
    "read_only": true,
    "write_authority": false,
    "source_is_data": true
  },
  "limits": {"max_result_bytes": 65536}
}
```

후보 result는 `verdict=pass|findings|blocked`, `findings[{id,severity,file,line,summary,evidence_refs,confidence}]`, `scope_observed`, `risks`, `open_questions` 정도면 충분하다. patch나 apply instruction은 넣지 않는다.

## 8.3 최초 authoritative review scope

현재 설치된 local CLI는 read-only help 기준 `codex-cli 0.153.2`다. `codex review --uncommitted`는 staged, unstaged, untracked를 검토한다고 명시한다. `--base`, `--commit`도 별도 scope다.

첫 POC 후보는 native `review --uncommitted` 하나로 제한하고, 별도로 `git status --porcelain=v2 -z` 기반 explicit manifest/hash를 deterministic preflight에 기록하는 방식이다. native review가 scope 해석을 담당하고 explicit manifest는 감사·불일치 검출을 담당한다. rename/copy, binary, submodule, LFS의 실제 native review 의미는 아직 검증되지 않았으므로 첫 POC에서는 typed preflight로 발견 시 block하고, 지원을 추측하지 않는다. base/commit review는 후속이다.

# 9. Codex Reviewer Minimum POC — Revised

다음은 통합계획이 아니라 실험 가능한 최소 후보다.

1. 수동 report command만 제공한다. default-off이고 실제 호출은 `-AllowLiveCodex` 같은 명시적 opt-in 없이는 실행하지 않는다.
2. `run-cycle.ps1` 밖에서 동작하고 `WORKFLOW_STATE.json`, daily 4-file, Registry, repository source를 쓰지 않는다.
3. `.claude`, `AGENTS.md`, nested `AGENTS.md`, `%USERPROFILE%\.codex\config.toml`, 인증 저장소를 생성·수정하지 않는다.
4. review scope는 `uncommitted` 하나다. deterministic manifest가 staged/unstaged/untracked를 구분하고 unsupported Git object를 block한다.
5. 후보 CLI 형태는 `codex exec --sandbox read-only --ephemeral --ignore-user-config -C <ProjectRoot> --json --output-schema <schema> review --uncommitted ...`다. 실제 option 순서와 Windows quoting은 fake CLI와 harmless live canary로 먼저 확정한다.
6. artifact는 `<workspace>/<date>/logs/codex-review/<run-id>/` 아래 request, change manifest, process result, structured review result만 둔다. raw output은 redaction·byte cap을 거친 진단용이고 handoff로 전달하지 않는다.
7. provider exit 0과 review pass를 분리한다. parse/scope/schema/instruction conflict는 fail-closed `blocked`다.
8. Claude 경로, Secondary LLM 경로, HRNS-NOW에는 연결하지 않는다.

이 후보가 기존 01/02보다 작은 이유는 provider router, common agent runtime, State projection, debate orchestration 없이 “Codex가 bounded read-only review 결과를 안정적으로 낼 수 있는가”만 검증하기 때문이다.

# 10. Security / Isolation / Instruction Risks

## 10.1 Codex configuration 경계

| 경계 | POC 처리 |
| -- | -- |
| CLI runtime config | 명시적 CLI option과 process-local env allowlist만 사용 |
| project instructions | 실제 repo의 `AGENTS.md`를 수정하지 않음; 적용 여부는 fixture로 측정 |
| global user config | `--ignore-user-config` 후보 사용, 파일 수정 금지 |
| authentication | 기존 인증에 의존할 수 있으나 읽기/복사/로그 금지; `--ignore-user-config`가 auth까지 격리한다고 가정하지 않음 |
| sandbox | `read-only` 명시, write 방지와 read isolation을 별개로 검증 |
| output format | JSONL/process event와 output schema를 구분하고 bounded parser 사용 |
| session/persistence | `--ephemeral`, resume/session 공유 없음 |

`--ignore-rules`는 exec policy rule 무시 option이지 AGENTS나 auth isolation 증거가 아니다. `--ignore-user-config`도 인증이 있는 `CODEX_HOME`까지 제거한다는 증거가 없다.

## 10.2 Sandbox/read isolation 열린 실험

실제 network/provider 호출은 이번 03에서 하지 않았다. harmless 임시 Git repo에서 다음 canary를 설계한다.

| canary | 관측 질문 |
| -- | -- |
| working root | source read 가능 여부, write 시도 실패 여부 |
| parent directory | working root 밖 read 가능 여부 |
| 다른 drive | 별도 drive read 가능 여부 |
| `%USERPROFILE%` | user home read 가능 여부 |
| environment variable | process environment 값이 model/tool에 노출되는지 |

각 위치에는 secret이 아닌 random marker만 둔다. 결과는 “write denied”와 “read denied”를 별도 필드로 기록한다. read-only sandbox가 쓰기를 막는다는 사실만으로 working root 밖을 읽지 못한다고 결론내리지 않는다. 실행 전후 filesystem hash로 mutation이 없음을 확인한다.

## 10.3 Prompt injection/instruction boundary

layer를 다음처럼 구분한다.

1. Harness instruction: scope, authority, artifact contract
2. Provider-native invariant: sandbox, session, output schema
3. Project instruction: `AGENTS.md` 등 provider가 자동 발견하는 규칙
4. Task instruction: 이번 review 목표/acceptance
5. Evidence data
6. Source data
7. Test output

5~7의 문자열은 명령이 아니라 untrusted data다. source/diff/test 안의 “ignore previous instructions”, shell command, patch 요구를 실행하지 않는다. 1~4가 충돌하거나 project instruction이 read-only review를 벗어나면 자동 추측하지 않고 `instruction_conflict`로 block한다. 실제 precedence는 nested AGENTS marker fixture로 검증한다.

환경 전체를 상속하면 secret이 노출될 수 있으므로 runner는 allowlist를 사용해야 한다. raw transcript는 모델 간 handoff로 넘기지 않고 file/line/severity/summary/evidence/confidence의 structured critique만 전달한다.

# 11. Token / Cost Analysis

provider 수 증가는 비용 절감을 보장하지 않는다. 평가식은 다음이다.

```text
Accepted Change당 총 token
+ Human intervention 시간
+ finding quality/재현율
+ false-positive와 failure/retry rate
```

현재 fixed overhead는 core/profile/project instruction, daily strategy/state, rendered prompt, packet, provider bootstrap/session 비용이다. fresh Codex reviewer를 매 slice 호출하면 Claude worker와 별개 고정비가 추가된다. raw transcript·전체 diff·daily docs를 prompt에 중복 넣으면 handoff 비용이 특히 커진다.

비용 절감 원칙:

- packet-first: source body를 복제하지 않고 path/hash 우선
- parent-deterministic navi: 검증 가능한 navigation은 provider call 제거
- compact planning: semantic parity를 유지한 prompt 축소
- deterministic preflight: provider에게 Git/status/schema 계산을 시키지 않음
- structured critique: raw transcript 대신 작은 finding set
- unchanged scope cache: manifest hash가 같으면 review 재호출 금지
- accepted-change linkage: usage ledger와 최종 acceptance를 연결해 provider별 실측

Codex보다 먼저 graduation할 cheap lever 우선순위는 (1) usage ledger 1.1로 baseline 측정, (2) parent-deterministic navi A/B, (3) packet-first/compact planning A/B, (4) deterministic no-provider preflight, (5) 불필요한 retry/preflight provider call 제거다.

# 12. Compatibility / Rollback

최소 POC의 compatibility 원칙은 additive·standalone·optional이다.

- 기존 Claude planning/code/doc argument와 core gate를 변경하지 않는다.
- Secondary LLM task/schema를 변경하지 않는다.
- HRNS command/State/bridge/Registry를 변경하지 않는다.
- daily 4-file과 3-file bridge를 변경하지 않는다.
- provider executable을 doctor required dependency로 추가하지 않는다.
- review artifact는 runtime truth가 아니다.

정확한 rollback 단위는 POC에서 새로 추가한 Codex report/lib/schema/smoke 파일과 그 파일을 나열하는 문서 줄뿐이어야 한다. 기존 production 파일 수정이나 State migration이 없으므로 rollback 후 data migration도 없어야 한다. 이 조건을 벗어나면 더 이상 첫 POC가 아니다.

# 13. Tests / Gates

이번 감사에서는 test/smoke/provider invocation을 실행하지 않았다. 따라서 기존 84/84 또는 HRNS test 결과를 이번 라운드의 PASS로 재주장하지 않는다. 필요한 신규 gate 후보는 다음과 같다.

## 13.1 Offline deterministic

- fake `codex` executable로 argv 순서, spaces/Korean path, stdin 정책 검증
- stdout/stderr 동시 대량 출력, UTF-8, timeout, cancellation, tree kill, residual process, byte truncation
- output schema parse fail, malformed JSONL, exit 0 + invalid result, nonzero exit
- output path traversal, run-id, junction/symlink, project/workspace/kit root containment
- process env allowlist와 secret redaction
- staged/unstaged/untracked 각각 및 혼합 scope manifest
- rename/copy/binary/submodule/LFS 발견 시 first POC block
- manifest hash 변경 TOCTOU와 review 전후 change-set drift
- source/diff/test-output prompt injection fixture
- root/nested AGENTS conflict fixture
- State/bridge/Registry/repository content 무변경 assertion
- release-package-hygiene와 clean-root-onboarding

## 13.2 Manual/live capability

- 현재 CLI option grammar와 `review --uncommitted` 실제 scope
- working root/parent/other drive/home/env canary의 read/write 관측 matrix
- no-window 실제 packaged/PowerShell launch
- authentication/config leakage 여부
- structured finding quality와 false-positive baseline
- Claude-only 대비 Claude+Codex의 Accepted Change당 total token/시간 비교

live gate는 harmless fixture에서만 실행하고 production repository, real secret, global config를 사용하지 않는다.

# 14. Open Decisions

다음은 source만으로 확정할 수 없다.

1. `codex review --uncommitted`가 rename/copy/binary/submodule/LFS를 어떤 형태로 실제 review하는가.
2. `--sandbox read-only`가 working root 밖 read를 제한하는가, 아니면 write만 제한하는가.
3. `--ignore-user-config`와 `--ephemeral`이 project/nested `AGENTS.md`, auth, session에 미치는 정확한 영향.
4. JSONL event와 final structured output의 안정적인 분리 방법.
5. CLI timeout 후 Windows child process 잔존 여부.
6. review request에 acceptance criteria text를 넣을지 pointer/hash만 넣을지의 quality/cost 차이.
7. POC artifact를 release에 포함할지 developer-only로 둘지.
8. false-positive/false-negative 허용치와 reviewer verdict가 실제 merge/closure에 영향을 줄 미래 gate.
9. 장기 provider diagnostic을 새 optional field로 둘지, 기존 opaque extension을 쓸지. POC에서는 결정하지 않는다.

# 15. Changes Required to Future Integrated Plan

| 기존 판단 | 03 판정 | 이유 |
| -- | -- | -- |
| HRNS core는 provider-neutral | REVISE | command boundary는 중립이나 StopReason/config/bridge/UI/continuity는 Claude-coupled다. |
| `claude-invoke-core`를 공통 provider adapter로 사용 | REJECT | 실제 process supervision이 없고 Claude session/usage/stream semantics를 소유한다. |
| 먼저 generic Agent Runtime framework 작성 | REJECT | 두 번째 실제 adapter 전에는 공통성 근거가 없다. |
| Secondary evidence manifest를 review scope로 재사용 | REVISE | redaction/containment/evidence pattern만 사용하고 complete Git manifest는 새로 필요하다. |
| Execution Packet 2.0을 Review Packet으로 복제 | REJECT | write authority와 continuation 내부 정보가 read-only reviewer에 과도하다. |
| explicit diff를 prompt에 모두 전달 | REVISE | native review + explicit scope manifest/hash가 더 작다. unsupported Git object는 block한다. |
| `codex review --uncommitted`를 첫 scope로 사용 | ACCEPT(실험 조건부) | staged/unstaged/untracked를 표방하며 가장 작은 native review 경로다. 실제 semantics는 canary가 필요하다. |
| `usage_guard`에 provider budget 저장 | REJECT | 기존 의미를 오염시킨다. POC는 State-free다. |
| Task Class/Lane/Role/Provider/Risk를 단일 enum으로 통합 | REJECT | 서로 직교하는 정책 축이다. |
| `.codex/config.toml` 또는 `AGENTS.md` 자동 생성 | REJECT | 사용자/global/project instruction을 침범한다. bridge-free여야 한다. |
| Codex reviewer를 `run-cycle`에 즉시 연결 | REJECT | 먼저 standalone quality·security·cost 증거가 필요하다. |
| Ollama를 공통 runtime에 연결 | DEFER | 현재 advisory result/read pattern만 참고하고 범위를 넓히지 않는다. |
| Claude process runner를 즉시 공통화 | DEFER | Codex 구현 뒤 transport 공통분모만 추출한다. |
| State에 `agent_runtime/provider/reviewer/lane` required field 추가 | REJECT | POC compatibility blast radius가 과도하다. |
| Codex 추가가 곧 token 절감 | REJECT | Accepted Change당 총비용으로 실측해야 한다. |
| parent-deterministic/packet-first를 먼저 graduation | ACCEPT(실험 조건부) | 이미 존재하는 더 싼 lever이며 별도 A/B gate가 필요하다. |

# 16. Final Recommendation

지금은 Integrated Implementation Plan을 확정할 단계가 아니다. 다음 순서는 양쪽 03의 04 cross review, 외부 확인, 그리고 harmless Codex capability experiment여야 한다. 그 결과가 모이면 “standalone read-only reviewer POC를 구현할지”만 먼저 결정할 수 있다.

현재 가장 안전한 공통 사실 기반은 다음 한 문장이다.

> Claude production path와 HRNS/State/bridge를 그대로 둔 채, explicit Git scope manifest로 감사 가능한 수동 read-only Codex reviewer가 실제로 안전하고 경제적인지 먼저 증명한다.

## 프로토콜 공통 질문에 대한 답

1. **`D:\harness-kit` 전체 inventory를 실제로 확인했는가?** 예. regular file 246개를 inventory했다.
2. **본문을 끝까지 읽은 파일은 몇 개인가?** Kit 기준 61개다.
3. **search-only 파일은 몇 개인가?** 178개다.
4. **내용 미검토 파일은 몇 개인가, 왜인가?** 미검토 0개다. 다만 generated/runtime 7개는 의미 본문이 아닌 metadata만 확인했다. search-only 178개는 본문 완독이 아니다.
5. **Agent Runtime 변경 dependency closure를 모두 읽었는가?** prospective minimum standalone POC의 직접 source/contract/release closure는 읽었다. 그러나 인접한 기존 smoke 89개 전체를 본문 완독하지 않았으므로 Harness 전체 regression closure까지 모두 읽었다고 주장하지 않는다.
6. **Claude runtime의 실제 process launch/seam을 함수 수준에서 확인했는가?** 예. 세 runner의 `Start-ClaudeProcess`, base/role argument builder, wrapper callsite, core의 session/usage/stream 함수 경계를 확인했다.
7. **Secondary LLM helper의 함수 수준 재사용 가능성을 확인했는가?** 예. 지정 5개 자산의 함수별 판정을 §7.1에 기록했다.
8. **execution packet schema 2.0을 실제 source에서 확인했는가?** 예. 생성 field 전부와 identity 검사 callsite를 확인했다.
9. **01/02에서 수정해야 할 가장 중요한 사실 3개는?** HRNS는 부분적으로 Claude-coupled다; `claude-invoke-core`는 process supervisor가 아니다; Secondary Git evidence는 complete change set이 아니다.
10. **현재 제안보다 더 작은 첫 POC가 가능한가?** 예. router/State/HRNS/run-cycle 없이 수동 `review --uncommitted` lane 하나가 가능하다.
11. **Codex 추가보다 먼저 token cost를 낮출 기존 Harness 기능은?** usage-ledger baseline, parent-deterministic navi, packet-first/compact planning, deterministic preflight와 retry 제거다.
12. **현재 evidence만으로 Integrated Plan 단계로 넘어가도 되는가?** 아니오. 04 cross review, external confirmation, Codex scope/sandbox/instruction/cost 실험이 먼저다.
