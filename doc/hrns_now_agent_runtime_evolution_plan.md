# HRNS-NOW Agent Runtime Evolution — Standalone Codex Read-only Reviewer 통합 구현 계획

문서 상태: **Integrated Plan / production 구현 미승인**

작성 기준일: 2026-09-04

HRNS-NOW 기준 HEAD: `87575273402fb2b50a83c0a6ab4689bc3ea6e311`

Live Harness Kit 기준: `D:\harness-kit`, `kit_version=2026.09.02`, Git 저장소 아님

Capability 기준: `codex-cli 0.153.2`

선행 독립 감사: `claude_capability_review_v3.md`의 `READY_FOR_INTEGRATED_PLAN`

계획 보강 상태: `claude_integrated_plan_adversarial_review.md`의 `PLAN_REQUIRES_REVISION` 중 BLOCKER 0, HIGH 1, MEDIUM 6, LOW 6을 반영한 targeted revision

Targeted revision 재검증일: 2026-09-07 — live Harness의 H1 smoke calibration baseline `95/84/11/36/0`과 계획 반영 항목 H1·M1–M6·L1–L6을 재대조함

이 문서는 미래의 범용 Agent Runtime을 설계하지 않는다. 검증된 최소 단위인 **Harness-owned standalone Codex read-only reviewer** 하나를 구현하기 위한 실행 계획만 확정한다. 이 문서 작성은 `D:\harness-kit` production 구현 승인, Git 커밋 승인 또는 workflow 연결 승인을 뜻하지 않는다.

# 1. Executive Decision

확정하는 첫 구현 단위는 다음 하나다.

> Parent가 만든 bounded evidence를 tool-free Codex에 전달하고, 구조화된 결과를 Parent가 결정론적으로 감사한 뒤 사람에게 비권위 권고로 보여 주는 standalone reviewer.

결정은 다음과 같다.

- 구현 소유자는 Harness Kit다. HRNS-NOW source 변경은 0이다.
- 기존 Claude planning/code/doc/closure 경로는 호출·수정·공통화하지 않는다.
- reviewer는 standalone report 명령이며 `run-cycle.ps1`에서 호출하지 않는다.
- 기본값은 off다. 명시적 opt-in 없이는 provider process를 시작하지 않는다.
- provider는 repository를 탐색하지 않는다. Parent가 선택·검증한 inline evidence만 본다.
- 결과는 workflow truth, 완료 증거, 자동 적용 권한이 아니다.
- provider exit code 0, schema 통과 또는 finding audit 통과 어느 하나만으로 review가 진실이라고 간주하지 않는다.
- Capability experiment loop는 종료한다. 남은 실제 process-tree/no-window/failure-shape 확인은 구현 초기 manual/live gate로 이동한다.

Production 구현 시작 전 흐름은 고정한다.

```text
Codex targeted plan revision
→ Claude revision verification
→ 외부/사용자 확인
→ 명시적 production implementation 승인
→ D:\harness-kit implementation
```

# 2. Architecture

정본 흐름은 다음과 같다.

```text
Operator의 명시적 opt-in
→ Parent Git/scope preflight
→ Parent-owned deterministic evidence 작성
→ strict UTF-8 no-BOM 직렬화, 16KB cap, SHA-256
→ production root와 분리된 ASCII disposable working root
→ version/option/effective-feature preflight
→ raw bytes를 stdin BaseStream에 전달
→ generic codex exec, read-only/ephemeral/tool-disabled
→ concurrent bounded stdout/stderr 수집
→ JSONL 및 unexpected-event 감사
→ 마지막 completed agent result 선택
→ strict result schema 검증
→ deterministic finding audit
→ redacted typed result
→ Human-visible non-authoritative recommendation
```

책임 경계는 다음처럼 둔다.

| 단계 | 소유자 | 신뢰하는 입력 | Hard gate | 실패 결과 |
| --- | --- | --- | --- | --- |
| Git/scope 수집 | Parent | local Git와 명시적 요청 | 지원 scope, hash, TOCTOU | `blocked` |
| Evidence 구성 | Parent | 검증된 source/test/contract | ID, size, secret, absolute-path 검사 | `blocked` |
| Provider 호출 | Codex-specific supervisor | 검증된 raw bytes와 versioned invocation spec | version/features/caps/timeout | typed process failure |
| JSONL 해석 | Parent | bounded stdout | terminal turn, event allowlist, schema | `contract_invalid` |
| Finding 감사 | Parent | manifest/result/evidence index | path/hash/line/scope/ref/test claim | finding별 accept/reject |
| 사용자 표시 | standalone report | redacted typed result | `authoritative=false` | 상태 전이 없음 |

Codex의 `-C`는 다음과 같아야 한다.

- ASCII 문자만 포함한 disposable leaf directory
- HRNS-NOW, `D:\harness-kit`, production repository, 실제 사용자 project root와 같지 않고 어느 쪽의 하위도 아님
- symlink/reparse point/junction이 아닌 실경로
- 실행 전 비어 있고 `AGENTS.md`, `.codex`, source file이 없음
- 실행 종료 후 exact leaf만 정리하며, broad recursive root 삭제를 하지 않음

시스템 temp는 위 조건을 만족할 때만 사용할 수 있는 **조건부 후보**다. Parent는 선택된 경로가 ASCII-only인지 deterministic preflight하고, 실패하면 provider를 호출하기 전에 차단한다. `C:\Users\<non-ascii-user>\AppData\Local\Temp`처럼 Windows profile 경로가 ASCII-only 조건을 만족하지 않는 환경에서는 explicit `-ScratchRoot`가 예외 복구가 아니라 정상적인 사용 경로가 될 수 있다. 이번 POC는 automatic scratch provisioning을 추가하지 않는다.

# 3. Verified Capability Evidence

계획은 의견서보다 실제 source·관찰을 우선한다. 확인된 핵심 근거는 다음과 같다.

| 근거 | 확인된 사실 | 계획에 미치는 영향 |
| --- | --- | --- |
| V1 | native `codex exec review --uncommitted`는 custom stdin 및 strict output schema와 맞지 않았고 Git copy lineage, binary/submodule/LFS 지원이 불완전했다 | native review를 authoritative 경로로 사용하지 않음 |
| V1 | `--ignore-user-config` 단독으로 MCP/tool surface가 닫히지 않았고 host read 경계도 보장하지 못했다 | process-local full disable + event audit가 필요 |
| V2 | generic exec + output schema, deterministic finding audit, false-finding rejection, bounded fake process supervisor가 성립했다 | contract/parser/audit/supervisor를 별도 Codex namespace로 구현 가능 |
| V2 | fake executable 10개 mode에서 timeout/cancel/tree kill/caps/malformed output을 다룰 수 있었다 | pattern은 채택하되 실제 `codex.cmd → node` 검증을 별도 gate로 유지 |
| V3 | 3회 live 호출 모두 provider-directed `command_execution`, `mcp_tool_call`, `web_search`, `file_change`, 기타 read/shell tool event가 0이었다 | tool-free inline architecture 채택 |
| V3 | canary marker match 0, 5개 file hash 불변, MCP/plugin metadata event 0이었다 | 첫 POC에 OS/ACL sandbox를 선행 필수로 두지 않음 |
| V3 | 동일한 10,473-byte inline evidence만으로 알려진 결함 3/3을 찾고 Parent audit 3/3을 통과했다 | bounded inline review가 기능적으로 가능 |
| V3 | `UTF8Encoding(false, true)` + stdin `BaseStream` raw-byte write로 BOM 0 및 세 한글 logical path의 ordinal exact round-trip을 3회 확인했다 | BOM workaround 없이 strict no-BOM 계약 채택 |
| V3 | 첫/중간 `agent_message` 뒤 실제 final message가 올 수 있었다 | 마지막 completed result selector가 필수 |
| Claude V3 감사 | architecture blocker 0, verdict `READY_FOR_INTEGRATED_PLAN` | 계획 작성 가능; production 구현 승인은 아님 |
| Claude plan adversarial audit + live Kit source | smoke inventory expectation이 Secondary LLM docs calibration에 hard-code되어 있고 Codex smoke prefix는 Secondary LLM count에 포함되지 않음 | §18.3의 count-only carve-out과 101/89/12/36/0 reconciliation 필요 |

제약도 그대로 보존한다.

- 위 tool-disable 의미는 `codex-cli 0.153.2`에 한정된다.
- provider-visible affordance가 완전히 사라진 것은 아니다. 실제 tool/file-change event 0과 fail-closed 감사가 안전 경계다.
- 실제 `codex.cmd → node` timeout/cancel/tree-kill/residual/no-window는 아직 production supervisor로 검증되지 않았다.
- V3 usage는 같은 10,473 bytes에서도 input token이 9,177~76,903으로 크게 달랐다. 비용 우위를 일반화할 수 없다.
- 설치판 0.153.2의 세부 feature semantics를 확정해 주는 공식 공개 문서는 이번 조사에서 확보하지 못했다. 따라서 구현 정본은 installed CLI help/features와 재현 가능한 capability probe이며, CLI 변경은 재검증 trigger다.

# 4. Hard Invariants

다음은 협상하거나 편의상 낮출 수 없는 불변식이다.

1. **Optional/default-off**: 명시적 `-EnableCodexReview` 없이는 resolver와 offline preflight까지만 수행하고 network/provider 호출은 하지 않는다.
2. **Non-authoritative**: 모든 결과에 `authoritative=false`, `safe_to_auto_adopt=false`를 고정한다.
3. **No mutation authority**: patch, apply token, write command, commit instruction, State transition을 계약에서 허용하지 않는다.
4. **State-free/HRNS-free/bridge-free**: `WORKFLOW_STATE`, daily 4-file surface, repository 3-file bridge, Registry, HRNS command/DTO/UI를 읽거나 쓰지 않는다.
5. **Existing Claude path unchanged**: `run-cycle.ps1`, planning/code/doc wrapper, Claude runner와 `claude-invoke-core.ps1`는 수정하지 않는다.
6. **Bounded input/output/process**: input, stdout, stderr, time, child process 수명은 모두 상한을 가진다.
7. **Strict no-BOM UTF-8**: input은 byte-level BOM 검사와 strict decode/re-encode 검사를 통과해야 한다.
8. **No production working root**: provider `-C`에 production/Kit/HRNS/project root 또는 그 하위를 주지 않는다.
9. **No original absolute source path**: provider input·result schema·prompt에 원본 절대 경로를 넣지 않는다.
10. **Opaque logical path**: logical path는 ordinal, case-sensitive data이며 provider 단계에서 filesystem resolve/normalize하지 않는다.
11. **Fail closed on capability drift**: command/version/option/effective feature가 기대와 다르면 provider를 호출하지 않는다.
12. **Fail closed on unexpected event**: versioned allowlist 밖 event/item이 1건이라도 있으면 final result를 폐기한다.
13. **Parent owns Git scope**: provider가 `git status`, diff 범위 또는 changed-file 집합을 결정하지 않는다.
14. **Provider independence**:

```text
Current production fact:
  Existing baseline Harness workflow는 Claude만으로 운용 가능하다.
  기존 baseline에 Codex subscription은 필요하지 않다.

deterministic-first / no-provider:
  결정론적으로 처리 가능한 task class는 frontier provider 없이 계속 동작한다.

Non-negotiable long-term invariant:
  Baseline product operation은 여러 유료 frontier provider의 동시 subscription을
  절대 필수로 요구해서는 안 된다.

Claude + Codex:
  optional guarded dual-provider quality lane으로만 사용할 수 있다.

Codex-only baseline execution:
  장기 aspiration이며 현재 commitment 또는 이번 POC의 약속이 아니다.
```

# 5. Scope

POC가 구현하는 범위는 아래뿐이다.

- standalone PowerShell entrypoint와 명시적 opt-in
- deterministic command resolver와 0.153.2 version/option/feature gate
- explicit Git changed-scope manifest와 evidence envelope 생성
- bounded UTF-8 no-BOM inline serializer
- Codex-specific process supervisor
- raw JSONL parser, event audit, final-result selector
- strict result schema와 finding audit
- redacted typed result의 stdout 출력 및 선택적 explicit output file
- fake-process 기반 offline smoke와 소수의 명시적 live/manual smoke
- 기능·보안·운영 문서와 smoke index 현행화

Standalone invocation의 후보 operator surface는 다음과 같다.

```powershell
scripts/report/invoke-codex-readonly-review.ps1 `
  -ProjectRoot <local-git-root> `
  -ReviewRequestPath <request-json> `
  -ScratchRoot <separate-ascii-root> `
  -EnableCodexReview `
  [-CodexCommand <explicit-command>] `
  [-OutputPath <parent-owned-result-path>] `
  [-Json]
```

`ReviewRequestPath`는 Parent가 읽는 요청이다. provider가 이 파일이나 `ProjectRoot`를 직접 보지 않는다. `OutputPath`를 생략하면 redacted typed result만 stdout에 내보내며 raw JSONL은 보존하지 않는다. 지정할 경우에도 Parent가 승인한 output root containment를 통과한 final result/audit만 기록한다.

`-ScratchRoot`는 non-ASCII Windows profile에서 정상적으로 요구될 수 있다. system temp는 deterministic ASCII/containment/reparse preflight를 통과할 때만 사용하며, 실패 시 다른 경로를 자동 생성하거나 추측하지 않고 호출을 차단한다.

# 6. Non-goals

이번 POC에 포함하지 않는다.

- generic Provider Runtime 또는 provider interface framework
- Router, 자동 provider 선택, fallback chain
- Claude-only/Codex-only production workflow routing
- Codex code/source write lane
- Claude↔Codex 자동 토론 또는 자동 handoff
- automatic apply/add/commit/push
- `run-cycle.ps1` 연결, closure/State 자동 반영
- `agent_runtime`, `provider`, `reviewer`, `budget`, `lane` State field
- HRNS provider selector, badge, usage UI, command 추가
- Claude runner supervisor 공통 추출
- Secondary LLM/Ollama runtime 통합
- Execution Packet schema 2.0 변경 또는 재사용 강제
- binary/submodule/LFS의 완전 지원
- MSI/ZIP packaging pipeline 구현
- OS sandbox/ACL architecture 구현
- long-term token/cost 우위 결론

# 7. Provider Availability / Independence

Codex executable은 optional host dependency다. 없거나 사용할 수 없어도 기존 Harness와 Claude workflow는 그대로 작동해야 한다. Doctor의 required dependency 또는 `harness-runtime-dependency-inventory.ps1`에 추가하지 않는다.

Resolver 순서는 다음으로 고정한다.

1. 호출자가 `-CodexCommand`로 제공한 Windows native command를 검증한다.
2. child process가 상속할 PATH에서 `codex.cmd`, 그 다음 `codex`를 해석한다.
3. npm이 해석될 때만 `npm prefix -g` 아래 `codex.cmd` 후보를 검증한다.
4. 그 밖은 `unavailable/executable_not_found`다.

다음은 resolver 후보에서 제외한다.

- WSL/Linux shim 자동 fallback
- user profile의 위치를 고정한 absolute path
- 다운로드 또는 자동 설치
- global PATH, config, auth file 변경

Resolver는 단순 파일 존재가 아니라 별도의 bounded child process로 `--version`을 실행하고 exact version string을 파싱해야 한다. 초기 지원 allowlist는 `codex-cli 0.153.2` 하나다. 다른 버전은 자동 호환으로 추정하지 않고 `blocked/unsupported_cli_version`으로 끝낸다.

Availability는 다음을 분리해 보고한다.

```text
package_present
command_resolved
version_supported
required_options_present
effective_features_safe
noninteractive_capable
auth_available
```

Auth는 global auth store를 수정하거나 내용을 읽어 보고하지 않는다. 실제 호출에서 인증 불가가 관찰되면 `unavailable/auth_unavailable`로 분류한다. Codex가 없거나 제한돼도 baseline workflow를 실패로 만들지 않는다.

# 8. Evidence Input Contract

## 8.1 Parent request와 provider envelope 분리

Parent request는 local-only 입력이며 다음을 포함할 수 있다.

- project root locator
- Git scope basis
- expected HEAD와 status digest
- task goal과 acceptance criteria
- 검토할 logical paths와 changed-file intent
- 관련 contract/test evidence locator

Provider envelope에는 local locator와 절대 경로를 제거하고 아래 값만 넣는다.

```text
schema_version
kind = codex_readonly_review_evidence
task_goal
acceptance_criteria[]
scope_id
source_snapshot_hash
files[]:
  file_id
  logical_path
  change_kind
  old_logical_path?       # rename/copy를 명시 지원할 때만
  review_basis             # staged | unstaged | untracked | deleted
  before:
    basis                  # base | index | absent
    present
    sha256?
    line_count
  after:
    basis                  # index | worktree | absent
    present
    sha256?
    line_count
  declared_changed_lines:
    before[]
    after[]
  changed_hunks/before_snippets/after_snippets
evidence[]:
  evidence_id
  kind
  sha256
  content
  executed?
  status?
  exit_code?
```

초기 POC는 한 logical path에 staged와 unstaged 변경이 동시에 존재하면 이를 하나의 hash로 합치지 않고 `blocked/ambiguous_mixed_source_basis`로 preflight block한다. staged는 `base → index`, unstaged는 `index → worktree`, untracked는 `absent → worktree`, deleted는 명시된 basis의 `present → absent`로만 표현한다. 복합 3-state envelope는 필요성이 입증된 후 별도 확장한다.

## 8.2 Size, encoding, hash

- 초기 input hard cap은 **16,384 bytes**다.
- cap에는 고정 reviewer instruction과 provider envelope 전체가 포함된다.
- 초과 시 자동 truncation하지 않는다. Parent가 scope를 더 작게 나누거나 `blocked/inline_input_too_large`로 끝낸다.
- `.NET`의 `UTF8Encoding(encoderShouldEmitUTF8Identifier=false, throwOnInvalidBytes=true)`로 byte array를 만든다.
- 첫 세 byte가 `EF BB BF`이면 호출을 거부한다.
- strict decode 후 같은 encoder로 재직렬화한 bytes가 원본과 byte-for-byte 같아야 한다.
- exact bytes의 SHA-256을 계산하고 process start 직전에 다시 대조한다.
- `StandardInputEncoding`이나 PowerShell 기본 encoding에 의존하지 않고 `StandardInput.BaseStream`에 exact bytes를 직접 쓴 후 닫는다.

## 8.3 Logical path

- `file_id`는 한 request 안에서 유일한 `F0001` 형식 식별자다.
- `logical_path`는 repository-relative 표시 문자열이며 원본 case와 Unicode code point를 그대로 유지한다.
- physical provider path와 관계가 없다.
- provider 반환값은 `StringComparison.Ordinal`로 manifest와 exact match해야 한다.
- Parent는 보안 preflight 단계에서만 `logical_path`의 absolute/traversal/NUL/drive-qualified 형태를 거부한다. provider 결과를 맞추기 위해 case-folding, Unicode normalization 또는 separator rewrite를 하지 않는다.

## 8.4 Evidence 최소화

전체 repository, raw transcript, daily docs 전체, source tree 전체를 넣지 않는다. 각 finding을 판단하는 데 필요한 changed hunk, caller/contract snippet, 실제 deterministic test 결과만 evidence ID와 함께 넣는다. instruction-like 문자열이 source/test output에 있더라도 `evidence data`로 구획하며 task instruction과 합치지 않는다.

# 9. Invocation Contract

초기 versioned invocation spec은 아래 순서를 따른다.

```text
codex --ask-for-approval never exec
  -C <validated-ascii-disposable-root>
  --skip-git-repo-check
  --sandbox read-only
  --ephemeral
  --ignore-user-config
  --strict-config
  --disable <versioned-disabled-feature-list>
  -c mcp_servers={}
  -c web_search="disabled"
  --json
  --output-schema <ascii-scratch-schema-copy>
  -
```

문자열 하나를 산문에서 copy/paste해 hard-code하지 않는다. 구현에는 version별 invocation specification object를 두고 root/subcommand option 순서까지 단위 테스트한다.

0.153.2의 초기 expected-false feature 목록은 다음이다.

```text
shell_tool
code_mode_host
apps
plugins
remote_plugin
browser_use
browser_use_external
browser_use_full_cdp_access
computer_use
image_generation
view_image
multi_agent
goals
skill_search
tool_suggest
hooks
workspace_dependencies
auth_elicitation
tool_call_mcp_elicitation
skill_mcp_dependency_install
sleep_tool
standalone_web_search
in_app_browser
shell_snapshot
shell_snapshot_v2
```

`web_search_request`는 V3 call 1에서 deprecated로 관찰됐으므로 최종 invocation에 넣지 않는다. web 차단은 top-level `web_search="disabled"`로 검증한다. `unified_exec`처럼 이름만으로 안전을 추정할 수 없는 feature는 expected value가 아니라 raw event 감사로 방어한다.

Provider 호출 전 네 단계 preflight가 모두 성공해야 한다.

1. `--help`/`exec --help`에서 required option grammar 확인
2. exact CLI version 확인
3. 동일 process-local overrides를 적용한 `features list`에서 expected false 확인
4. scratch/schema/input hash와 containment 재확인

어느 하나라도 실패하면 live review를 시작하지 않는다. CLI upgrade, feature rename/default 변화, option grammar 변화는 capability re-validation trigger다.

## 9.1 Versioned JSONL event allowlist

정상 result가 통과할 수 있는 JSONL surface도 invocation specification의 정본에 명시한다. `codex-cli 0.153.2`의 V3 retained evidence가 직접 확인한 terminal은 `turn.completed`, completed item type은 `agent_message`와 `error`다. 초기 production contract 후보는 다음으로 좁힌다.

| Top-level event | 허용 cardinality/shape |
| --- | --- |
| `thread.started` | 정확히 1회. init에 필요한 thread/session 식별자 subset만 parse하고 값은 result에 보존하지 않음 |
| `turn.started` | 정확히 1회. 알려진 0.153.2 shape만 허용 |
| `item.completed` | 1회 이상. 아래 허용 item type만 허용 |
| `turn.completed` | 정확히 1회인 terminal. 뒤따르는 event 금지; 알려진 usage subset만 선택적으로 parse |

`item.completed.item.type` allowlist는 다음뿐이다.

- `agent_message`: bounded text를 final-result selector 후보로 사용
- `error`: **version + exact category/message shape**가 pin된 known diagnostic subset만 classified diagnostic으로 기록. V3에서 관찰된 disabled code-mode fail-closed diagnostic을 포함할지는 implementation fixture가 exact shape를 재확인한 뒤 specification에 고정한다.

V3 report에는 init/container event의 전체 raw line을 보존하지 않았으므로 `thread.started`/`turn.started`/`item.completed`의 exact field shape는 첫 provider 호출 전에 0.153.2 disposable fixture로 재확인해 specification과 golden fixture에 pin한다. wildcard metadata나 unknown field를 허용하지 않는다. 위 이름·shape·cardinality에 없는 event/item, unknown `error`, terminal 뒤 event는 `contract_invalid/unexpected_jsonl_event`로 result를 폐기한다. 이 allowlist는 smoke 전용 표가 아니라 production contract의 versioned 정본이다.

다음 event/item은 명시적으로 금지한다.

- `command_execution`, `mcp_tool_call`, `web_search`, `file_change`
- filesystem read, shell/function, browser/computer/image tool
- 그 밖의 side-effect/tool event와 알려지지 않은 event/item

# 10. Result Contract

정적 JSON schema 파일은 `additionalProperties=false`를 사용하고 최소 다음 계약을 강제한다.

```text
verdict: pass | findings | blocked

findings[]:
  id                 # request 안에서 unique
  severity           # blocker | high | medium | low | info
  file_id
  logical_path
  source_basis        # before | after
  line               # positive integer
  summary             # bounded human-readable text
  evidence_refs[]
  confidence          # high | medium | low

scope_observed[]      # file_id 목록
risks[]               # bounded strings
open_questions[]      # bounded strings
```

교차 필드 규칙은 Parent validator가 보완한다.

- `verdict=pass`이면 `findings`는 비어 있어야 한다.
- `verdict=findings`이면 finding이 하나 이상이어야 한다.
- `verdict=blocked`는 근거가 `risks` 또는 `open_questions`에 있어야 한다.
- finding ID와 `scope_observed`는 중복될 수 없다.
- `scope_observed`와 finding의 `file_id`는 manifest subset이어야 한다.
- summary/risk/question 문자열별·전체 byte cap을 적용한다.

Schema에 patch blob, diff, apply token, shell/Git command, write authority 필드를 만들지 않는다. Human-readable remediation 제안은 `summary`에 허용하지만 기계 적용 가능한 payload로 승격하지 않는다.

Parent의 top-level output은 provider result와 분리한다.

```text
status
reason_code
authoritative = false
safe_to_auto_adopt = false
provider:
  name = codex
  resolved_command_kind
  cli_version
  exit_code
process:
  timed_out/cancelled/residual/truncated flags
contract:
  input_sha256
  feature_state_digest
  event_audit_status
  schema_status
review:
  provider_verdict
  accepted_findings[]
  rejected_findings[]
usage: optional observed fields only
```

# 11. Finding Audit

Finding audit는 provider 결과를 수용하기 전의 hard gate다. 각 finding을 독립적으로 검사한다.

| 검사 | 수용 조건 | reject reason 예시 |
| --- | --- | --- |
| Manifest membership | `file_id`가 request manifest에 존재 | `file_id_not_in_manifest` |
| Logical mapping | 반환 path가 ordinal exact match | `logical_path_mismatch` |
| Source identity | `source_basis`가 명시된 before/after snapshot과 일치하고 해당 captured hash가 manifest와 같음 | `source_basis_mismatch`, `file_hash_mismatch` |
| Line | 선택된 source basis에서 positive, line count 안, declared changed scope에 포함 | `line_not_present`, `finding_outside_changed_scope` |
| Evidence ref | 모든 ID가 evidence index에 존재 | `evidence_ref_missing` |
| Evidence identity | evidence content hash가 captured hash와 같음 | `evidence_hash_mismatch` |
| Test claim | `executed=true`, 실제 status/exit가 claim을 지지 | `test_claim_not_verified` |
| Scope relation | finding이 해당 changed hunk/contract evidence와 연결 | `finding_outside_evidence_scope` |

Audit output 후보는 다음 정도로 제한한다.

```text
finding_id
status = accepted | rejected
reason_codes[]
verified_evidence[]
```

Offline test에는 최소 다음 거짓 finding을 의도적으로 주입한다.

1. 존재하지 않는 file ID
2. 존재하지 않는 line
3. manifest 밖 file
4. 없는 evidence ref
5. changed/evidence scope와 무관한 claim
6. 실행하지 않은 test를 PASS라고 한 claim
7. logical path case/Unicode mismatch
8. source/evidence hash mismatch

Audit가 accept한 finding도 semantic truth 전체가 증명된 것은 아니다. 표시 문구는 항상 “검증된 anchor를 가진 비권위 권고”로 유지한다.

# 12. Git Scope / Preflight

초기 authoritative review scope는 **Parent가 explicit하게 생성한 changed-file manifest**다. `codex review --uncommitted`의 자체 범위 해석은 사용하지 않는다.

지원 후보는 다음으로 제한한다.

- ordinary UTF-8 text의 modified/added/untracked/deleted 파일
- staged와 unstaged를 구분한 explicit source basis. 초기 POC는 same-path staged+unstaged overlap을 지원하지 않음
- rename/copy는 old/new logical path, source basis, before/after hash와 lineage가 모두 명시된 경우만

다음은 fail-closed한다.

- binary 또는 NUL-containing content
- submodule/gitlink
- Git LFS pointer/object
- sparse/partial object 때문에 base/current content를 확정할 수 없는 경우
- malformed porcelain/raw output
- 한 logical path의 staged+unstaged 동시 변경인 `ambiguous_mixed_source_basis`
- path traversal, absolute path, reserved device path
- manifest 작성 뒤 HEAD/index/worktree가 바뀐 scope drift
- hash/mtime/length 재검증에서 발견한 TOCTOU
- reparse point/junction/symlink를 통한 root escape

Parent는 NUL-safe Git 출력으로 staged/unstaged/untracked 집합을 얻는다. 초기 POC에서는 같은 logical path가 staged와 unstaged 양쪽에 나타나면 합치지 않고 block한다. 구현 후보 명령은 `git status --porcelain=v2 -z`, `git diff --raw -z`, `git diff --cached --raw -z`, `git ls-files --others --exclude-standard -z`이며, 실제 parser smoke가 성공하기 전까지 산문 parsing으로 대체하지 않는다.

bytes capture 후보는 HEAD/base에 `git cat-file blob HEAD:<path>` 또는 동등한 `git show HEAD:<path>`, index에 `git cat-file blob :<path>` 또는 동등한 `git show :<path>`, worktree에 containment/reparse 검사를 통과한 .NET raw file API를 사용한다. Git argument는 shell 문자열로 이어 붙이지 않고 argument list로 전달하며, path discovery는 NUL-safe record를 유지한다. 각 capture는 before/after basis와 SHA-256을 함께 기록한다.

Snapshot sequence는 다음이다.

```text
HEAD/index/worktree identity capture
→ changed-file manifest 생성
→ source/base/evidence bytes와 hash capture
→ provider envelope 생성
→ invocation 직전 HEAD/index/status/source hash 재검증
→ 실행
→ finding audit 직전 source/evidence hash 재검증
```

어느 지점에서든 drift가 있으면 result를 폐기하고 `blocked/scope_drift`로 끝낸다.

# 13. Process Supervision

Codex-specific supervisor는 V2 pattern을 production 수준으로 옮기되 Claude launcher를 복사하거나 공통 runtime으로 추출하지 않는다.

필수 계약:

- `UseShellExecute=false`
- `CreateNoWindow=true`
- stdin/stdout/stderr redirect
- stdin raw-byte write 후 명시적 close
- target Windows/.NET contract에서 `StandardOutputEncoding`과 `StandardErrorEncoding`을 명시적 strict UTF-8로 설정하고, 실제 bytes/decoder 오류를 typed 처리. 이는 stdin raw-byte `BaseStream` 계약과 별개다.
- stdout/stderr 비동기 동시 drain
- timeout과 caller cancellation 분리
- timeout/cancel 시 Windows process tree 종료, fallback kill, residual 확인
- stdout/stderr byte cap 도달 즉시 truncation flag와 fail-closed 종료
- provider exit와 review verdict 분리
- `codex.cmd`를 `UseShellExecute=false`로 직접 시작할 수 있는 현재 관찰을 회귀 테스트하고, resolver가 돌려준 exact command를 사용

초기 후보 상한은 다음과 같다. 관련 process/contract module과 smoke를 구현하는 시점에 owner가 확정하고 test constant와 문서 값을 한 정본에서 읽게 한다.

| 항목 | 초기 후보 | 동작 |
| --- | ---: | --- |
| stdin | 16KB | 초과 시 호출 전 block |
| stdout JSONL | 256KB | 초과 시 tree kill, `contract_invalid/stdout_limit_exceeded` |
| stderr | 64KB | 초과 시 tree kill, `failed/stderr_limit_exceeded` |
| wall timeout | 120초 | `timed_out`, tree kill, residual 검사 |

Process result는 적어도 다음을 가진다.

```text
started
exit_code
timed_out
cancelled
tree_kill_attempted
tree_kill_succeeded
residual_detected
stdout_bytes/stderr_bytes
stdout_capped/stderr_capped
truncated
decode_error
elapsed_ms
```

실제 `codex.cmd → node` descendant attribution은 실행 전 process snapshot, parent PID/creation time 및 실행 후 descendant/residual 확인으로 검증한다. 기존 세션의 codex/node process를 이번 호출의 residue로 오인하지 않는다.

# 14. Failure Taxonomy

Top-level status는 provider-neutral하게 유지한다.

| Status | 의미 | 대표 reason |
| --- | --- | --- |
| `completed` | process/event/schema/audit가 완료됨 | `review_pass`, `review_findings`, `review_blocked` |
| `unavailable` | optional provider를 시작할 수 없음 | `executable_not_found`, `auth_unavailable` |
| `blocked` | 안전 preflight가 실행을 금지 | `unsupported_cli_version`, `unsafe_feature_state`, `unsupported_git_scope`, `ambiguous_mixed_source_basis`, `scope_drift`, `inline_input_too_large` |
| `timed_out` | wall timeout 도달 | `provider_timeout` |
| `cancelled` | caller cancellation | `operator_cancelled` |
| `usage_limited` | quota/rate/usage limit가 식별됨 | `provider_usage_limit` |
| `contract_invalid` | transport는 끝났으나 결과 계약을 신뢰할 수 없음 | `malformed_jsonl`, `missing_turn_completed`, `schema_invalid`, `unexpected_jsonl_event`, `unexpected_tool_event`, `output_limit_exceeded` |
| `failed` | 위 분류에 들지 않는 process/provider 실패 | `network_failure`, `nonzero_exit`, `decode_error`, `residual_process` |

실제 auth/quota/network 메시지를 고정 문자열 하나로 추측하지 않는다. 초기 live gate에서 exit code, JSONL item, stderr shape를 redacted fixture로 수집하여 classifier를 고정한다. 알 수 없는 오류는 `failed/provider_error_unknown`이며 success로 내리지 않는다.

Provider-specific 원문은 별도 bounded/redacted evidence로만 보관하고 top-level taxonomy를 Codex 문자열에 결합하지 않는다.

# 15. Security

첫 POC threat model은 explicit opt-in, read-only, non-authoritative, no-apply, no-commit, no-State-transition이다. 이 범위에서는 OS/ACL sandbox를 선행 architecture requirement로 두지 않지만 아래 방어는 모두 필수다.

## 15.1 Tool/event gate

§9.1의 versioned allowlist가 production 정본이다. 다음 JSONL event/item이 한 건이라도 있으면 final result를 폐기한다.

- `command_execution`
- `mcp_tool_call`
- `web_search`
- `file_change`
- filesystem read tool
- shell/function/computer/browser/image tool
- 알려진 allowlist에 없는 tool 또는 side-effect event

`error` item은 actual access와 구분하여 code/category/count를 기록한다. 알려진 fail-closed code-mode diagnostic도 자동 무시하지 않고 versioned allowlist와 exact shape를 검증한다. 알 수 없는 error 또는 allowlist 밖 event/item은 `contract_invalid` 또는 `failed`로 보수적으로 분류하고 review result를 폐기한다.

## 15.2 Instruction boundary

입력 layer를 다음처럼 분리한다.

```text
Harness fixed reviewer instruction
Provider-native CLI contract
Task goal / acceptance criteria
Evidence data
Source data
Test output
```

Evidence/source/test 안의 “이 지시를 무시하라”, command 또는 secret 요구 문장은 data로만 취급한다. 충돌을 자동 해결하지 못하면 `verdict=blocked`를 요구한다. Scratch에 `AGENTS.md`나 project instruction을 만들지 않고 `--ignore-user-config --strict-config`를 사용한다.

## 15.3 Canary/release gate

지원 CLI 버전 최초 승인 및 upgrade 때 non-secret random marker로 working-root/parent/other-drive/user-profile/`.codex` 인접/environment canary negative-read test를 수행한다. Parent가 marker value를 provider에 주지 않은 상태에서 tool event 0, raw/final marker match 0, file hash 불변을 함께 확인한다. 실제 credential 내용은 절대 읽거나 출력하지 않는다.

## 15.4 Secret/path controls

- Parent가 evidence를 만들기 전에 token/key/session/cookie/private-key 모양을 redaction/reject한다.
- original absolute path, user-home path, auth path는 provider input에서 금지한다.
- child environment의 초기 allowlist 후보는 `SystemRoot`, `WINDIR`, `COMSPEC`, `PATH`, `PATHEXT`, `TEMP`, `TMP`, 그리고 검증된 경우에만 `CODEX_HOME` 또는 필요한 auth plumbing과 최소 runtime 변수다. task-specific secret env는 제거하며 값 자체는 로그에 남기지 않는다. 최종 allowlist는 implementation-time gate에서 disposable fixture로 검증한다.
- schema/instruction 파일은 static, no-secret이며 disposable scratch에 copy한 hash를 확인한다.
- sandbox read-only와 event/mutation audit를 함께 사용한다. prompt의 “도구를 쓰지 말라”는 보안 통제로 계산하지 않는다.

# 16. Retention / Redaction

기본 정책은 **raw JSONL 비보존**이다.

- Parser는 bounded stream을 memory에서 처리하고 typed result/audit만 남긴다.
- capability/debug mode에서만 explicit opt-in으로 raw stdout/stderr를 disposable scratch에 잠시 보존할 수 있다.
- 보존물은 input/output caps를 넘을 수 없고, 종료 시 retention manifest에 생성·삭제 결과를 기록한다.
- absolute Windows path, user-home path, session/thread ID, MCP/plugin metadata, command trace, secret-shaped value를 redaction한다.
- usage 값은 실제 `turn.completed.usage`에 있는 필드만 보존하며 누락값을 0으로 만들지 않는다.
- raw transcript를 Claude↔Codex 또는 다음 세션 handoff로 전달하지 않는다.
- release payload는 scratch/log/debug artifact를 포함하지 않는다.

Human-visible 결과에는 accepted/rejected finding 요약, reject reason, typed failure 및 실제 관찰 usage만 포함한다. Provider raw prose 전체를 정본 문서로 승격하지 않는다.

# 17. Compatibility

첫 POC의 compatibility 경계는 다음과 같다.

| Surface | 변경 | 이유 |
| --- | ---: | --- |
| `scripts/run-cycle.ps1` | 0 | standalone 유지 |
| planning/code/doc wrapper 및 Claude runner | 0 | 기존 production path 불변 |
| `claude-invoke-core.ps1` | 0 | premature common runtime 추출 금지 |
| Secondary LLM runtime/schema/authority semantics | 0 | 의미와 authority가 다른 lane. 단, 신규 Codex smoke가 바꾸는 전역 smoke inventory count를 맞추기 위한 §18.3의 count-only calibration 예외는 허용 |
| `WORKFLOW_STATE` writer/schema | 0 | reviewer는 runtime truth 아님 |
| daily required 4-file contract | 0 | POC artifact를 daily surface로 승격하지 않음 |
| repository 3-file bridge | 0 | bridge-free invocation |
| HRNS command/state/Registry/UI | 0 | Harness 안정화 전 projection 금지 |
| required runtime dependency inventory | 0 | Codex는 optional host dependency |
| Doctor/Validate-Ops success semantics | 0 | Codex 부재가 기존 진단을 fail시키면 안 됨 |

따라서 신규 Codex 파일을 제거하고 §18.3의 count·문서·index 변경을 snapshot의 exact bytes로 복원하면 기존 workflow가 동일하게 작동해야 한다. Compatibility 회귀는 current source에서 계산한 smoke inventory expectation과 모든 indexed offline smoke가 일치·통과하고 live provider call이 0인지로 판정한다.

# 18. Expected File Changes

Production 승인 후의 직접 변경 예상 파일은 아래와 같다. 이는 구현 전에 exact change manifest로 다시 고정한다.

## 18.1 신규 production 파일

| 파일 | 역할 |
| --- | --- |
| `scripts/report/invoke-codex-readonly-review.ps1` | standalone opt-in entrypoint, orchestration, typed stdout/output |
| `scripts/lib/codex/codex-readonly-review-contract.ps1` | request/envelope/result 검증, UTF-8 bytes, JSONL final selector |
| `scripts/lib/codex/codex-readonly-review-evidence.ps1` | Git preflight, manifest, hash, snippets/test evidence, TOCTOU |
| `scripts/lib/codex/codex-readonly-review-process.ps1` | resolver/version/features, invocation args, bounded supervisor, event capture |
| `scripts/lib/codex/codex-readonly-review-audit.ps1` | unexpected-event 및 deterministic finding audit, typed reasons/redaction |
| `scripts/lib/codex/codex-readonly-review-result.schema.json` | `--output-schema`에 전달할 strict schema |
| `prompts/codex-readonly-review-system.md` | 기존 Kit의 system prompt naming convention을 따르는 fixed reviewer instruction; evidence data와 분리, no write authority |

## 18.2 신규 smoke

| 파일 | 분류/목적 |
| --- | --- |
| `scripts/smoke/smoke-codex-readonly-review-contract.ps1` | encoding/BOM/cap/schema/selector/logical path |
| `scripts/smoke/smoke-codex-readonly-review-finding-audit.ps1` | valid 및 false-finding reason matrix |
| `scripts/smoke/smoke-codex-readonly-review-process.ps1` | fake process drain/UTF-8/hang/cancel/tree/nonzero/malformed/caps |
| `scripts/smoke/smoke-codex-readonly-review-git-preflight.ps1` | staged/unstaged/untracked/delete/rename/copy와 blocked edge/TOCTOU |
| `scripts/smoke/smoke-codex-readonly-review-security.ps1` | resolver/version/features/unexpected event/redaction/containment |
| `scripts/smoke/smoke-codex-readonly-review-live-manual.ps1` | 실제 Codex/auth/tool/canary/tree/no-window/failure manual gate |

앞의 5개만 automatic/offline array에 넣는다. 마지막 파일은 live/manual로 분류하며 default suite에서 절대 실행하지 않는다.

`smoke-codex-readonly-review-security.ps1`는 `scripts/lib/codex/*.schema.json`이 protected production tree에 있고 release hygiene에서 제외되지 않으며 staged release payload에 존재하는지도 assert한다.

## 18.3 현행화할 문서/인덱스

- `scripts/SMOKE_INDEX.md`
- `docs/CODEX_READONLY_REVIEWER.md` 신규
- `docs/SECURITY_MODEL.md`
- `docs/TASK_CLASS_TOKEN_POLICY.md`
- `docs/HARNESS_KIT_MAP.en.md`
- `docs/HARNESS_KIT_MAP.ko.md`
- `docs/ROADMAP.md`
- `README.md`

`docs/CODEX_READONLY_REVIEWER.md`는 `C:\hrns-codex-scratch\<run-id>` 같은 안전한 ASCII scratch root 예시, production/project/Kit/HRNS root와의 양방향 overlap 금지, reparse point/junction 금지, exact disposable leaf 생성·정리 정책을 설명한다. non-ASCII Windows profile에서는 explicit `-ScratchRoot`가 정상 사용 경로임을 명시하고 automatic provisioning은 약속하지 않는다.

`TASK_CLASS_TOKEN_POLICY.md`에는 기존 `Secondary LLM Advisory Extension` §16에 Codex 내용을 섞지 않고, standalone Codex reviewer용 별도 subsection 또는 section을 추가한다. task class/budget tier/lane/role/provider/risk를 하나의 enum으로 합치지 않으며 optional provider와 실제 usage 측정 원칙만 연결한다.

신규 Codex smoke 6개로 인해 전역 smoke inventory의 하드코딩 expectation도 좁게 현행화한다. 현재 source rule을 직접 대조한 기준은 다음과 같다.

| 파일 | 허용 변경 |
| --- | --- |
| `scripts/lib/secondary-llm/secondary-llm-docs-calibration.ps1` | smoke inventory expectation만 `scripts_smoke_total: 95→101`, `automatic_offline_unique: 84→89`, `manual_live: 11→12`로 reconciliation |
| `scripts/smoke/smoke-secondary-llm-docs-scan.ps1` | fixture default `$SmokeTotal: 95→101` 및 실제 inventory expectation reconciliation만 |

`secondary_llm_smoke_total`은 scan이 `smoke-secondary-llm-*` prefix만 세고 신규 파일은 `smoke-codex-*`이므로 **36 유지**, `scripts_root_smoke_total`도 **0 유지**다. `scripts/lib/secondary-llm/secondary-llm-docs-scan.ps1`의 계산·비교 semantics는 이미 이 구분을 정확히 구현하므로 변경하지 않는다. 이 예외는 Secondary LLM runtime/schema/authority를 Codex와 결합하는 변경이 아니며, 오직 전역 smoke count consistency를 위한 count-only reconciliation이다.

## 18.4 조건부 release 파일

- `kit-version.json`: 구현·전체 회귀·문서 정합·release approval이 모두 끝난 시점에만 version을 올린다.

다음 파일은 의도적으로 예상 변경 목록에 넣지 않는다.

- `scripts/run-cycle.ps1`
- `scripts/doctor.ps1`
- `scripts/validate-ops.ps1`
- `scripts/lib/harness-runtime-dependency-inventory.ps1`
- `scripts/lib/claude/**`
- `scripts/lib/secondary-llm/**`의 runtime/schema/authority semantics. 단, `secondary-llm-docs-calibration.ps1`의 §18.3 count-only reconciliation은 예외
- HRNS-NOW source 전체

구현 중 이 제외 파일의 변경이 필요해지면 additive standalone 범위를 벗어난 것으로 간주하고 중단한 뒤 plan 재승인을 받는다. §18.3의 두 count-only 파일도 허용된 상수/fixture expectation 외 semantic diff가 생기면 즉시 중단한다.

# 19. Implementation Order

승인 후 작업 순서는 다음과 같다.

1. `D:\harness-kit` full inventory, version, hashes, pollution scan을 기록한다.
2. source rollback용 verified clean zip snapshot과 SHA-256을 만든다. `logs/`와 `scratch/` runtime contents는 release/source snapshot에서 제외하되 현재 존재 상태를 별도 manifest로 남긴다.
3. §18.3의 count-only calibration 두 파일과 원본 bytes까지 포함해 exact planned change manifest와 rollback file list를 확정한다.
4. static result schema와 contract module부터 작성한다.
5. fake process를 주입할 수 있는 Codex-specific supervisor를 작성한다.
6. Git/evidence preflight와 finding/event audit를 작성한다.
7. standalone report entrypoint와 fixed instruction을 연결한다.
8. offline smokes를 추가하고 개별 실행한다.
9. 실제 `codex.cmd` 대상 early manual/live gates를 수행한다.
10. 전체 official Harness offline suite, Doctor/Validate-Ops, clean-root, release hygiene를 재검증한다.
11. live inventory를 재계산하고 expected smoke constants를 명시적으로 reconciliation한 뒤 `SMOKE_INDEX`·MAP·ROADMAP·README와 일치시킨다.
12. fixed sample n≥5로 quality/cost를 관찰한다.
13. 독립 review와 owner acceptance 뒤에만 release/version 결정을 한다.
14. guarded workflow integration은 별도 제안/승인 전까지 시작하지 않는다.

현재 `D:\harness-kit\scratch`에는 baseline 관찰 시점에 `diag-planning-hang`와 `manual-o7-bind-af2bfcaf54d3428a9f17f05a18ccce0d`가 이미 있다. 이를 POC가 만든 residue로 세지 않는다. 소유자 승인 없이 삭제하지 않으며, snapshot 전 기존 runtime residue로 명시적으로 분류한다.

# 20. Offline Tests

Offline suite는 real Codex/Claude/Ollama/network 호출 0을 hard invariant로 둔다.

## 20.1 Contract/encoding

- 0/1/16,384/16,385-byte boundary
- UTF-8 BOM 거부
- malformed UTF-8 거부
- strict decode/re-encode byte equality
- SHA mismatch와 stdin write 전 TOCTOU
- 한글/영문 혼합/깊은 중첩·공백 logical path ordinal round-trip
- absolute source path가 envelope/result에 들어오면 거부
- strict JSON schema, additional property, enum, duplicate ID, cross-field rules
- 여러 `agent_message`, terminal 없음/복수/뒤따르는 event, 마지막 completed selector

## 20.2 Finding/event audit

- 정상 finding accept
- §11의 false-finding 8종 reject와 exact reason code
- test status/exit contradiction
- source/evidence hash mismatch
- §9.1의 exact 허용 event/item name·shape·cardinality와 금지 JSONL event table-driven matrix
- known diagnostic `error`와 actual tool event의 분리
- exit 0 + malformed JSONL/schema-invalid/unexpected event의 fail-closed

## 20.3 Git/path preflight

- staged/unstaged/untracked/deleted ordinary text
- same path가 staged+unstaged일 때 `blocked/ambiguous_mixed_source_basis`로 fail-closed
- rename/copy explicit lineage와 hash
- binary, gitlink/submodule, LFS pointer preflight block
- malformed NUL-safe Git record
- traversal/absolute/reserved-name path
- symlink/reparse/junction escape
- HEAD/index/worktree drift, file replacement, hash/length/mtime TOCTOU
- parent/project/Kit/HRNS와 scratch root overlap 거부

## 20.4 Process supervisor

Fake executable로 다음 mode를 모두 검증한다.

- 정상 stdout, 정상 stderr, 동시 대량 stdout/stderr
- UTF-8/Korean output
- hang/timeout
- caller cancellation
- child spawn 후 hang, tree kill과 residue
- nonzero exit
- exit 0 + malformed JSONL
- exit 0 + schema-invalid result
- stdout/stderr cap과 truncation flag
- stdin close/EOF
- `CreateNoWindow` start configuration

## 20.5 Resolver/security/retention

- explicit command/PATH/npm fallback/unsupported/WSL exclusion
- supported/unsupported/malformed version
- missing required option와 feature state mismatch
- command argument ordering/quoting, 한글과 공백이 provider path가 아닌 data로 보존
- redaction categories와 no-secret output
- raw retention default off/debug opt-in/cleanup
- release exclusion과 scratch exact-leaf cleanup
- `scripts/lib/codex/*.schema.json`이 protected production tree와 staged release payload에 포함되고 release hygiene에서 제외되지 않음

# 21. Manual / Live Gates

Live smoke는 사용자 승인, 호출 상한, disposable non-secret fixture를 명시하고 automatic suite와 분리한다.

POC 초기에 실제 `codex.cmd`로 반드시 확인한다.

1. Resolver 네 환경: interactive shell, child PowerShell `-NoProfile`, fresh `cmd.exe`, Harness-like `ProcessStartInfo`.
2. exact 0.153.2 version/option/effective-feature preflight.
3. generic structured inline review, final selector, schema, finding audit.
4. working/parent/other-drive/user-profile/`.codex` 인접/env canary negative-read와 raw event 0.
5. actual timeout과 operator cancellation.
6. `codex.cmd → node` descendant tree kill, fallback, residual 0.
7. Win32 `EnumWindows`/visibility 관찰을 포함한 no-window 확인.
8. Korean structured output와 세 logical path exact round-trip.
9. bounded large stdout/stderr 또는 안전한 equivalent failure fixture.
10. malformed/schema-invalid/unexpected-event result 폐기.
11. auth unavailable, quota/usage limit, network failure 각각의 typed shape. 실제 계정 상태를 위험하게 변경하지 않고 안전하게 유발할 수 없으면 `UNVERIFIED`로 두되 workflow 연결은 금지한다.

Manual result는 PASS/FAIL/UNVERIFIED와 raw evidence hash를 분리한다. 실제 실행하지 않은 항목을 PASS로 기록하지 않는다.

# 22. Regression Gates

구현 완료 후보는 다음을 모두 통과해야 한다.

- 변경된 모든 PS1 parser check
- 새 offline smokes 전부 PASS
- `scripts/SMOKE_INDEX.md`에서 live/manual 분리가 정확함
- index에서 추출한 기존+신규 official offline suite 전부 PASS
- automatic suite의 실제 live Claude/Codex/Ollama 호출 0
- Doctor JSON/text semantics 불변; Codex 부재는 기존 Doctor overall을 낮추지 않음
- Validate-Ops JSON/text semantics 불변
- fresh clean-root onboarding 성공
- required daily file 수 정확히 4
- repository bridge 수 정확히 3
- docs mismatch 0 및 EN/KO map 일치
- 모든 text/PS1/JSON/Markdown UTF-8 no-BOM
- release pollution 0, scratch/log/debug/raw provider artifact package 제외
- staged clean Kit에 신규 standalone entrypoint, Codex libs, schema, prompt가 빠짐없이 존재
- `run-cycle.ps1`, Claude paths, State template/writer, HRNS source의 semantic diff 0

현재 source-verified baseline은 `scripts_smoke_total=95`, `automatic_offline_unique=84`, `manual_live=11`, `secondary_llm_smoke_total=36`, `scripts_root_smoke_total=0`이다. 계획된 Codex smoke 5 automatic/offline + 1 manual/live만 추가하면 최소 expectation은 각각 **101 / 89 / 12 / 36 / 0**이다. 다른 승인된 smoke 변경이 동시에 없다면 이 값이 구현 reconciliation의 정확한 목표다.

회귀 순서는 다음과 같이 명시한다.

```text
live inventory recompute
→ hard-coded expected smoke constants 명시적 reconciliation
→ SMOKE_INDEX / HARNESS_KIT_MAP.en·ko / ROADMAP / README consistency update
→ secondary-llm docs-scan 및 smoke-index consistency smoke PASS
→ index에서 추출한 전체 automatic/offline suite PASS, live provider call 0
```

동시에 승인된 다른 smoke 변경이 있다면 live inventory로 다시 계산하되, 상수를 암묵적으로 방치하지 않는다. PowerShell 총계나 BOM 같은 다른 파생 수치도 live source에서 재계산하며 역사적 숫자를 맹목적 acceptance 값으로 사용하지 않는다.

# 23. Quality / Cost Graduation

Standalone POC 코드가 실행된다고 해서 default-on, workflow 연결 또는 dual-provider lane으로 graduation하지 않는다.

고정 sample n≥5를 사전에 정의한다.

- 알려진 defect가 있는 sample 최소 3개
- clean/no-finding sample 최소 2개
- 동일 evidence cap과 prompt/schema/version 사용
- 각 sample의 expected finding과 human adjudication 기준을 호출 전에 고정

측정값:

- finding precision과 false-positive 수
- known-defect recall과 false-pass 수
- finding audit accept/reject 및 reason
- process/contract 실패와 retry 수
- 실제 제공될 때만 input/output/cache/reasoning token
- wall time
- Human review time
- Accepted Change당 총 비용

Safety graduation 조건은 다음이다.

- unexpected tool/file-change event 0
- scope/TOCTOU/contract fail-open 0
- seeded known defect false-pass 0
- unauditable finding 자동 수용 0
- clean sample의 오탐이 전부 사람에게 명확히 식별 가능하고 State/write로 전파되지 않음

품질/비용 수치 threshold는 sample 실행 전에 owner가 고정하고 사후에 유리하게 바꾸지 않는다. Provider가 하나 늘면 비용이 줄어든다고 가정하지 않는다. 다음 기존 cheap lever는 별도 A/B 대상으로 유지한다.

1. usage ledger baseline
2. parent-deterministic navi
3. packet-first navi
4. compact planning/context reduction
5. deterministic preflight
6. unnecessary provider retry 제거

이 lever를 첫 Codex POC의 prerequisite나 default-on 변경으로 묶지 않는다.

# 24. Rollback

`D:\harness-kit`가 Git 저장소가 아니므로 rollback은 파일 단위로 설계한다.

구현 전 반드시 보존한다.

- verified clean source zip snapshot
- zip SHA-256
- Kit version과 전체 inventory/hash manifest
- 현재 `logs/`/`scratch/` runtime residue inventory
- exact planned add/modify manifest
- 수정 예정 문서의 원본 hash와 bytes
- `scripts/lib/secondary-llm/secondary-llm-docs-calibration.ps1`과 `scripts/smoke/smoke-secondary-llm-docs-scan.ps1`의 원본 hash와 exact bytes

Rollback 순서:

1. 실행 중 reviewer process가 없음을 PID/creation-time으로 확인한다.
2. POC가 만든 disposable leaf, debug raw artifact, final result를 exact path로 정리한다.
3. §18.1/18.2의 신규 파일만 exact path로 제거한다.
4. §18.3/18.4의 수정 파일만 snapshot의 exact bytes로 복원한다. 여기에는 count-only `secondary-llm-docs-calibration.ps1`과 `smoke-secondary-llm-docs-scan.ps1`이 명시적으로 포함된다.
5. inventory/hash, parser, existing official offline suite, Doctor/Validate-Ops, release hygiene를 재실행한다.
6. 기존 Claude workflow와 3-file/4-file count가 원래와 같음을 확인한다.

계산된 broad path, `D:\harness-kit` root, `scratch` 전체에 recursive delete를 하지 않는다. 기존 `diag-planning-hang` 및 `manual-o7-...` 디렉터리는 POC rollback 대상이 아니다. 기존 production runtime 파일을 수정하게 됐다면 이 rollback 모델로 충분한지 먼저 재승인한다.

# 25. Release Impact

- Codex는 optional host dependency이며 binary/npm package/auth를 Kit에 번들하지 않는다.
- release payload에는 entrypoint/libs/schema/prompt/docs/smokes가 포함돼야 한다.
- raw JSONL, canary, provider session/thread, auth, debug output, scratch/log contents는 포함하지 않는다.
- `release-package-hygiene.ps1`의 현재 `scripts/lib` 보호와 scratch/log exclude 원칙을 유지한다.
- 실제 ZIP/MSI/manifest pipeline을 이번 POC에서 구현하지 않는다.
- Kit version bump는 full regression과 독립 review 이후 release 단계에서만 한다.
- clean Windows에서 Codex가 없을 때 기존 Kit install/onboarding/Doctor/Validate-Ops가 그대로 성공해야 한다.
- Codex가 있을 때도 standalone live smoke는 수동 opt-in이며 installer나 첫 실행이 network call을 만들지 않는다.
- HRNS MSI와 application package에는 변화가 없다.

# 26. Deferred Architecture

## 26.1 Prior divergence disposition

판정 어휘는 다음 의미로 사용한다.

- `ACCEPT`: 이 POC의 검증된 architecture 또는 구현 원칙으로 채택한다.
- `REVISE`: 핵심 원칙은 수용하되 현재 source와 최소 slice에 맞게 범위를 바꾼다.
- `DEFER`: 검증된 후속 순서에 있지만 현재 source graph에는 선반영하지 않는다.
- `REJECT_FOR_THIS_POC`: standalone reviewer slice에는 넣지 않으며 별도 initiative에서 다시 검토할 수 있다.
- `REJECT`: 장기 architecture 원칙에서도 명시적으로 채택하지 않는다.

| 기존 proposal | 판정 | 이 POC에서의 처리 |
| --- | --- | --- |
| Generic Provider Runtime | `DEFER` | 두 production adapter가 생기기 전 framework 추출 금지 |
| Router | `DEFER` | standalone explicit command만 제공 |
| State `agent_runtime` | `DEFER` | POC에서 State 변경 0; Harness contract graduation 뒤 optional diagnostic projection 필요성을 별도 재판정 |
| Codex write lane | `REJECT_FOR_THIS_POC` | read-only recommendation 계약과 양립하지 않으며 별도 initiative와 승인이 필요 |
| Claude runner common supervisor extraction | `DEFER` | Codex 실제 supervisor가 안정화된 뒤 실재 공통부만 추출 |
| Ollama runtime integration | `REJECT_FOR_THIS_POC` | 기존 Secondary LLM lane은 그대로 유지; 공통 read/result layer도 지금 만들지 않으며 별도 initiative에서만 재검토 |
| Native `review --uncommitted` | `REJECT` | authoritative POC 경로로 사용하지 않음; quality reference에만 사용 가능 |
| Bundle/filesystem review | `REJECT` | provider filesystem read 대신 bounded inline evidence 사용 |
| Tool-free inline review | `ACCEPT` | V3 source-verified architecture |
| Execution Packet schema 2.0 reuse | `REVISE` | identity/pointer/hash/freshness 원칙만 참고; write authority/history/budget packet을 복제하지 않음 |
| Secondary LLM helper reuse | `REVISE` | no-BOM write, containment, redaction, non-authoritative audit pattern만 참고; schema/module 직접 결합 금지. §18.3 count-only calibration은 helper reuse나 runtime coupling이 아님 |
| Multi-provider required subscription | `REJECT` | hard provider-independence invariant 위반 |
| Automatic Claude↔Codex debate as default architecture | `REJECT` | token·failure surface 확대, human message bus 감소가 입증되지 않았고 baseline에 다중 provider를 강제함 |
| Auto commit | `REJECT` | Git Owner/사용자 승인 경계 위반 |
| HRNS provider UI | `DEFER` | Harness contract와 graduation 이후 별도 vertical slice |

## 26.2 나중에만 검토할 것

- standalone quality/cost가 입증된 뒤 guarded workflow review hook
- Claude와 Codex 두 구현에서 확인된 공통 process/result 부분 추출
- provider-neutral failure projection
- Codex-only baseline execution의 실현 가능성
- optional diagnostic State extension 또는 HRNS projection
- OS/ACL sandbox가 더 높은 threat model에서 필요한지
- binary/submodule/LFS 지원

어느 것도 이번 POC의 source graph에 선반영하지 않는다.

# 27. Open Owner Decisions

모든 결정을 production 진입 조건으로 뭉치지 않는다. `bounded input/output/time`은 변경할 수 없는 invariant지만 정확한 숫자는 implementation-time에 고정한다.

| 분류 | 결정 항목 | 현재 safe initial default / 시점 |
| --- | --- | --- |
| `PRODUCTION-ENTRY` | manual/live 호출 승인 횟수와 non-secret fixture | 구현 시작 전 사용자 provenance와 call limit를 기록 |
| `PRODUCTION-ENTRY` | 기존 `D:\harness-kit\scratch` residue의 소유/보존/정리 권한 | 구현자가 임의 삭제하지 않으며 시작 전에 owner 확인 |
| `IMPLEMENTATION-TIME` | input/output caps와 timeout | 후보 `16KB / 256KB / 64KB / 120초`; 관련 module과 smoke를 작성하기 전에 단일 정본으로 확정 |
| `IMPLEMENTATION-TIME` | `-OutputPath` 지원 | 첫 slice는 stdout-only final result 권장; raw JSONL 보존은 별도 debug opt-in |
| `IMPLEMENTATION-TIME` | npm global bin fallback | explicit command/PATH 우선; npm fallback은 resolver fixture가 닫힐 때만 포함 |
| `IMPLEMENTATION-TIME` | rename/copy 지원 | 첫 slice는 fail-closed 권장; same-path staged+unstaged overlap은 항상 초기 preflight block |
| `IMPLEMENTATION-TIME` | child environment final allowlist | §15.4 후보에서 실제 Codex/auth fixture에 필요한 최소값만 확정 |
| `GRADUATION` | fixed n≥5 sample과 품질/비용 threshold | standalone POC 구현 뒤, sample 호출 전에 고정 |
| `RELEASE` | Kit version과 archive 보존 위치 | full regression·독립 review·release 승인 단계에서 결정 |

이 분류는 결정 시점을 분리할 뿐 hard gate를 완화하지 않는다. 구현 시점의 선택이 §4 invariant나 approved change manifest를 바꾸면 작업을 중단하고 재승인한다.

# 28. Production Implementation Entry Criteria

다음 production-entry 체크리스트가 모두 충족돼야 실제 `D:\harness-kit` 구현을 시작할 수 있다. implementation-time, graduation, release 결정은 §27의 시점에 별도로 닫는다.

- [ ] Claude adversarial review의 필수 H1, M1–M6, LOW 수정이 이 plan에 반영됐다.
- [ ] Claude가 targeted revision을 검증하고 architecture/security/compatibility blocker가 없다고 판정했다.
- [ ] 사용자 또는 외부 owner가 수정 plan과 revision verification을 확인하고 production implementation을 명시적으로 승인했다.
- [ ] 구현 범위가 standalone Codex read-only reviewer 하나로 고정됐다.
- [ ] §18의 exact add/modify/exclude file manifest를 owner가 승인했으며 H1 count-only carve-out 두 파일이 포함됐다.
- [ ] 현재 smoke inventory consistency mechanism이 live-compute인지 hard-coded calibration인지 source로 확인했다.
- [ ] hard-coded calibration일 경우 narrow smoke-count reconciliation 대상 파일과 rollback bytes가 exact change manifest에 포함됐다.
- [ ] 현재 Kit version, full inventory, file hashes와 pollution/release-hygiene 결과를 기록했다.
- [ ] verified clean source zip snapshot과 SHA-256을 만들고 실제로 열어 inventory를 검증했다.
- [ ] 기존 scratch/log residue를 POC residue와 구분해 기록했고 정리 권한을 확인했다.
- [ ] rollback exact-file 절차와 복원 후 gate를 검토했으며 H1 count reconciliation 파일의 exact-byte 복원도 포함했다.
- [ ] `codex-cli 0.153.2` 지원 pin, resolver 순서, required option 및 expected feature matrix를 확정했다.
- [ ] initial cap/retention safe defaults를 승인했으며 exact numeric values는 관련 module 구현 시 단일 정본으로 확정하도록 분류했다.
- [ ] manual/live provider call의 사용자 승인 provenance, 호출 상한, disposable fixture를 기록했다.
- [ ] actual production/project/Kit/HRNS root를 provider `-C` 또는 inline absolute path로 사용하지 않는 설계를 재확인했다.
- [ ] automatic/offline suite가 live provider 호출 0을 유지하는 test 구조를 확정했다.
- [ ] 동시에 작업 중인 다른 agent의 write set과 overlap이 없음을 확인했다.
- [ ] Git add/commit/push는 구현·검증 결과를 owner가 확인하기 전 수행하지 않는다는 경계를 재확인했다.

이 조건이 충족되더라도 승인 범위는 §18 파일과 §19 순서뿐이다. `run-cycle.ps1`, State, Claude path, Secondary LLM runtime/schema/authority semantics, HRNS 또는 Router로 범위를 넓혀야 한다면 구현을 중단하고 새 계획과 승인을 받아야 한다. 허용된 유일한 Secondary LLM 영역 예외는 §18.3의 smoke inventory count-only reconciliation이다.

최종 구현 원칙은 다음 한 줄로 유지한다.

> **Parent-owned bounded evidence → tool-free structured Codex review → deterministic audit → Human recommendation.**
