# HRNS-NOW Agent Runtime Evolution — Codex 교차검토 04

- 작성 주체: Codex (`SELF=codex`, `OTHER=claude`)
- 기준일: 2026-09-04 (Asia/Seoul)
- 실행 모드: Mode B — `revOpinion_claude03.md`와 `revOpinion_codex03.md`를 모두 끝까지 읽고 상호 대조했다.
- 기준선: HRNS-NOW `harness-dev` / `87575273402fb2b50a83c0a6ab4689bc3ea6e311`, live Kit `D:\harness-kit` / `2026.09.02` / state·UI contract `1.0/1.0`
- mutation: 이 문서 한 파일만 생성했다. HRNS-NOW production source, live Kit, State, Registry, global config, Git index/history는 변경하지 않았다. build·smoke·live provider 호출도 수행하지 않았다.

# 1. Cross Review Verdict

최종 교차검토 verdict는 **`CONVERGED_WITH_CORRECTIONS__NOT_READY_FOR_INTEGRATED_PLAN`**이다.

두 03은 핵심 방향에서 이미 수렴했다.

- 기존 Claude planning/code/doc production path를 건드리지 않는다.
- 첫 Codex 검증은 `run-cycle.ps1`, HRNS-NOW, `WORKFLOW_STATE`, daily 4-file, repository 3-file bridge 밖의 수동·default-off·read-only lane이어야 한다.
- generic Provider Runtime, Router, State projection, Codex write, 자동 debate/commit, Ollama runtime 통합은 첫 단계가 아니다.
- `claude-invoke-core.ps1`은 공통 process supervisor가 아니며 Claude-specific session/usage/stream core다.
- 기존 Secondary LLM Git evidence는 complete change set이 아니다.
- packet과 pointer/hash를 우선하고 raw transcript와 전체 문서 복제를 피한다.

그러나 양쪽 문서를 그대로 합치면 안 된다. 이번 교차검토에서 다음 네 가지를 수정한다.

1. **Claude 03의 smoke inventory 100개 및 문서 drift 주장은 사실이 아니다.** 현재 `D:\harness-kit\scripts\smoke`에는 정확히 95개 regular file이 있고 모두 `smoke-*.ps1`이다. `SMOKE_INDEX.md`, MAP, ROADMAP의 95는 현재 source와 일치한다.
2. **Codex CLI의 `installed=true`는 실행 가능성을 뜻하지 않는다.** npm package 0.153.2와 Windows shim은 존재하지만 현재 PowerShell PATH에서는 `codex`가 해석되지 않는다. 명시적 `codex.cmd` 경로는 동작하고, WSL에서 발견되는 Windows shim은 Linux optional dependency 부재로 실패한다.
3. **Secondary LLM helper의 “그대로 재사용” 범위는 Claude 03보다 훨씬 좁다.** schema/kind/함수명이 subsystem에 결합돼 있고, path containment는 lexical 검사이며, regex redaction은 완전한 secret boundary가 아니다. POC에서는 직접 import보다 패턴 차용과 Codex-specific 최소 구현이 안전하다.
4. **authoritative Git scope는 아직 수렴하지 않았다.** native `codex exec review --uncommitted`와 explicit immutable diff/bundle은 서로 다른 장단점이 있다. 도움말이 staged·unstaged·untracked 포함을 선언해도 rename/copy/binary/submodule/LFS 및 scope drift semantics는 실험 전 확정할 수 없다.

따라서 04 이후에도 바로 통합 구현계획을 확정하지 않는다. 먼저 외부 확인과 harmless capability experiment가 필요하다.

# 2. 상대 03에서 새롭게 확인된 사실

Claude 03에서 다음 항목을 **ACCEPT**한다.

| 상대 03의 사실 | 판정 | 수용 내용 |
| -- | -- | -- |
| `usage_guard`/`role_sliced`를 provider 상태 슬롯으로 쓰자는 과거 의견 철회 | ACCEPT | 기존 의미를 보존한다. 장래 optional diagnostic block이 필요하더라도 전용 namespace와 자체 schema를 검토해야 한다. 단 이름을 지금 `agent_runtime`으로 확정하지는 않는다. |
| `context-diet-packet.json` 1.0의 실제 필드와 한계 | ACCEPT | goal, acceptance, review base, complete change manifest, actual diff, finding contract가 없으므로 Review Packet 정본이 될 수 없다. |
| Secondary `reviewer_advice`의 실제 의미 | ACCEPT | 프로젝트 source review가 아니라 같은 Secondary run directory의 advisory 산출물에 대한 메타 검토다. Codex code review와 같은 개념이 아니다. |
| HRNS `WorkspaceRecoveryDiagnosticsAdapter`의 Claude continuity 경로 소비 | ACCEPT | HRNS 실행 명령 경계는 provider-blind지만 recovery observability에는 실제 Claude-specific filesystem 결합이 있다. |
| usage ledger 1.1의 absent≠0 규칙 | ACCEPT | provider 공통화 후보는 Claude ledger 자체가 아니라 “관측되지 않은 값을 0으로 조작하지 않는다”는 의미 규칙이다. |
| planning/code/doc `Start-ClaudeProcess`의 세부 divergence | ACCEPT | transport 골격은 유사하지만 quote regex, heartbeat, result shape, core hard/soft loading, retry 분류가 다르다. 즉시 shared abstraction을 만들 근거는 부족하다. |
| live Kit POC의 compatibility 영향은 문자 그대로 0이 아님 | ACCEPT | standalone 신규 파일도 release set, smoke index, hygiene, docs, retention과 rollback 표면을 만든다. |
| live Kit가 비-Git tree라는 rollback 제약 | ACCEPT | 구현 전 파일별 manifest/hash와 복구 가능한 snapshot이 필요하다. broad directory delete를 rollback으로 사용하지 않는다. |
| capability spike를 production POC보다 먼저 분리 | ACCEPT_WITH_REFINEMENT | HRNS/Kit repo 신규 파일 0으로 실행하되 임시 scratch fixture/artifact는 명시적으로 허용하고 종료 시 검증 후 제거해야 한다. |

특히 Claude 03이 `context-diet-packet` 1.0과 `00-wrapper-input.json` 2.0을 분리하고, `reviewer_advice`를 code reviewer와 구별한 점은 01/02의 개념 혼동을 실질적으로 해소했다.

# 3. 내가 수정하는 03 판단

내 Codex 03의 다음 표현을 수정한다.

## 3.1 CLI availability

기존 표현 “local CLI 0.153.2가 설치돼 있다”는 불충분했다. 현재 deterministic observation은 다음처럼 분리해야 한다.

| 축 | 현재 관측 |
| -- | -- |
| npm package | `C:\Users\LG\AppData\Roaming\npm\node_modules\@openai\codex`, version `0.153.2` 존재 |
| Windows shim | `codex.cmd`/`codex.ps1` 존재 |
| 현재 PowerShell PATH | `where.exe codex` 실패, bare `codex` 실행 불가 |
| 명시적 Windows shim | 전체 경로로 `--version`/`--help` 성공 |
| WSL | Windows shim을 찾지만 `@openai/codex-linux-x64` 부재로 실행 실패 |
| auth/live capability | 이번 단계에서 호출하지 않아 미검증 |

따라서 향후 availability contract는 최소한 `package_present`, `command_resolved`, `version_observed`, `help_capable`, `noninteractive_capable`, `auth_capable`을 구분해야 한다. 이름은 후보일 뿐 schema 확정이 아니다.

## 3.2 정확한 noninteractive review command 후보

설치된 0.153.2의 local help를 읽은 결과:

- `codex review --uncommitted`는 staged·unstaged·untracked review를 선언하지만 sandbox, ephemeral, JSON/schema option을 직접 노출하지 않는다.
- `codex exec review`는 `exec`의 sandbox/approval/working-root/ephemeral/config/output option과 review의 scope option을 함께 사용할 수 있다.
- `--ignore-user-config` 설명은 config만 무시하며 **auth는 계속 `CODEX_HOME`을 사용**한다고 명시한다.
- review prompt `-`는 stdin을 읽는다.

그러므로 실험 후보는 다음 grammar다. 아직 실행 승인된 production contract가 아니다.

```text
<explicit-codex-command> exec
  -C <fixture-root>
  --sandbox read-only
  --ask-for-approval never
  --ephemeral
  --ignore-user-config
  --json
  --output-schema <result-schema>
  review --uncommitted -
```

`--add-dir`는 도움말상 추가 writable directory이므로 read-only POC에서 쓰지 않는다. exact argv, stdin EOF, Windows quoting은 fake command와 harmless fixture로 증명해야 한다. [공식 Codex use cases](https://developers.openai.com/codex/use-cases)는 local diff review라는 사용 사례를 확인해 주지만, 위 option 조합의 정본은 설치된 버전의 local help와 실제 canary여야 한다.

## 3.3 coverage 표현

Codex 03의 `미검토=0`은 inventory/검색 누락 0이라는 뜻이었지만 “본문 미검토 0”으로 읽힐 수 있다. 정확한 표현은 다음이다.

- inventory 누락: 0
- Full Read: 61
- Search Only: 178
- Metadata Only: 7
- 본문 완독하지 않은 파일: 185

또한 Codex 03의 `smoke/test=96`은 실제 smoke script 95개와 `scripts/SMOKE_INDEX.md` 1개를 동일 범주로 묶은 상호배타 분류였다. 실제 smoke script 수가 96이라는 뜻이 아니다.

## 3.4 Review Packet과 remediation text

Codex 03은 patch/apply instruction을 제외하자고 했지만 이를 “수정 제안 자체 금지”로 확대하면 code review의 가치가 사라진다. read-only는 mutation 권한의 부재이지 지적·설명 권한의 부재가 아니다.

- 허용 후보: 짧은 human-readable correction, file/line anchor, 재현 또는 검증 제안
- 금지 후보: machine-applicable patch blob, 자동 apply 명령, mutation 승인처럼 보이는 token, 실행 지시

따라서 Secondary의 `Test-SecondaryLlmForbiddenCandidateContent`는 한국어 “코드/소스/파일 + 수정”만으로도 finding을 차단할 수 있어 Codex reviewer에 그대로 재사용하면 안 된다.

## 3.5 dependency closure

Codex 03의 “prospective minimum POC direct closure를 읽었다”는 제한된 표현은 유지하되, 최종 authoritative scope와 artifact/retention 방식이 미정이므로 **최종 dependency closure를 모두 읽었다고 말할 수 없다.** scope 결정 뒤 새 closure를 다시 만들고 그 안의 text를 본문 정독해야 한다.

# 4. 상대 03에서 수용하지 않는 판단

| 상대 판단 | 판정 | 이유 |
| -- | -- | -- |
| smoke inventory 100, 문서 95는 stale | REJECT | 현재 실측은 `scripts/smoke` 95 files / 95 `smoke-*.ps1`; `SMOKE_INDEX`, MAP, ROADMAP과 일치한다. Claude가 열거한 5개 파일도 95 안에 이미 포함된다. |
| `ConvertTo-SecondaryLlmSafeText` 그대로 재사용 | REVISE | regex는 알려진 문자열을 줄이는 보조 수단일 뿐 비밀 비노출 보장이 아니다. legitimate path/evidence를 과도하게 지울 수도 있다. |
| `Assert-SecondaryLlmRunId` 그대로 재사용 | REVISE | 값 규칙은 좋은 선례지만 직접 import하면 Codex가 Secondary namespace와 script loading order에 결합된다. Codex-specific contract로 검증해 사용하고 두 소비자가 안정된 뒤 추출한다. |
| `Test-SecondaryLlmPathInside`/resolve 함수 그대로 재사용 | REJECT_AS_SECURITY_BOUNDARY | `GetFullPath` prefix의 lexical containment다. junction/symlink/reparse point와 non-existing target semantics를 증명하지 못한다. 패턴만 참고한다. |
| `New-SecondaryLlmEvidenceIndex` 그대로 재사용 | REJECT | 내부가 schema `1.0`과 kind `secondary_llm_evidence_manifest`를 직접 검사한다. Codex manifest에 의미 그대로 적용되지 않는다. |
| Secondary claim/audit “엔진” 직접 재사용 | REVISE | non-authoritative·evidence-backed 원칙은 수용하지만 claim types, kind, severity, changed-file semantics가 Secondary 전용이다. 첫 POC에서는 작게 별도 검증하고 실제 중복이 확인된 뒤 공통화한다. |
| Review Packet에 `authorized_target_file`을 read scope로 참조 | REVISE | 첫 uncommitted reviewer의 정본 scope는 Git change set이다. 필요한 경우 `expected_change_scope` 같은 reviewer 의미로 파생할 수 있지만 write-worker 이름을 그대로 노출하지 않는다. |
| 신규 파일 `≤3 + smoke` | REJECT_AS_ARBITRARY | 세 production 파일과 smoke를 이미 합치면 3을 넘는다. 파일 수보다 seam, authority, rollback, 검증 완결성이 기준이어야 한다. |
| dependency closure text를 전부 정독 완료 | REVISE | Claude coverage상 release hygiene와 여러 smoke/doc dependency는 Full Read 목록에 없다. Codex coverage가 일부를 보완했지만 scope 미정 때문에 최종 closure는 아직 닫히지 않았다. |
| 장래 State key 이름은 `agent_runtime` | DEFER | 전용 optional namespace 원칙에는 동의하나 key 이름·owner·consumer가 아직 없다. POC에서는 State를 건드리지 않는다. |
| production source 노출 전 read-scope canary 통과가 필요 | ACCEPT_WITH_NUANCE | 실제 secret·민감 source에는 hard gate다. 일반 fixture source를 working root에서 읽는 실험 자체는 gate의 일부다. read-only가 root 밖 read를 차단한다고 미리 가정하지 않는다. |

# 5. Coverage 차이

## 5.1 Claude가 더 잘 확인한 영역

- `context-diet-packet.json` 1.0의 field-level inventory
- usage ledger 1.1의 필드와 absent≠0 규칙
- 세 `Start-ClaudeProcess` 사이의 quote regex, heartbeat, core hard/soft loading, retry divergence
- `secondary-llm-reviewer-advice.ps1`가 같은 run의 advisory artifact만 읽는다는 의미
- HRNS recovery diagnostics의 `logs/claude-session-continuity` 기능적 결합
- live Kit 비-Git rollback 조건

이 항목들은 Codex 03의 압축된 기술을 보강하므로 모두 종합본 사실로 수용한다.

## 5.2 Codex가 더 넓게 확인한 영역

- Kit Full Read 61개: Secondary lib 전체, 관련 report, 주요 docs, state/validate/release helper 및 직접 연결 smoke
- HRNS command/DTO/mapper/adapter/policy/projection 경계
- Execution Packet 2.0의 전체 field 분류와 review/write authority 분리
- Secondary helper 52개 함수에 대한 보수적 재사용 판정
- junction/symlink, output cap, encoding, residual process 등 process/evidence security gap
- 설치된 Codex CLI 0.153.2의 현재 Windows/WSL resolution 및 `exec review` help

## 5.3 양쪽 모두의 coverage 한계

- 246개 inventory 자체는 재확인됐지만 모든 source와 95개 smoke 본문을 완독한 것은 아니다.
- Claude 03의 Full Read 24개는 Codex 03의 61개와 상당 부분 겹치므로 합계 85개로 더하면 안 된다.
- authoritative review scope가 미정이어서 최종 POC의 import/contract/test/release closure도 미정이다.
- 실제 Codex provider call, auth, sandbox read isolation, AGENTS precedence, JSONL shape는 관측하지 않았다.
- 이번 04에서도 test PASS를 새로 주장하지 않는다.

# 6. POC 범위 비교

두 제안의 공통 최소 단위는 “standalone read-only review”다. 차이는 입력 scope다.

| 쟁점 | Claude 03 | Codex 03 | 04 판정 |
| -- | -- | -- | -- |
| 시작 단계 | 신규 파일 0 fixture spike | manual standalone POC 후보 | **REVISE** — repo/Kit 신규 파일 0의 disposable capability experiment가 먼저 |
| Git scope | explicit manifest + generated diff/bundle 선호, 결정은 owner | native `exec review --uncommitted` + manifest/hash | **DEFER** — 동일 fixture로 두 방식의 scope·cost·finding quality를 비교 |
| manifest 역할 | authoritative input 후보 | native scope의 audit/drift detector | **REVISE** — native mode에서는 detector이지 제어권자가 아님; bundle mode에서만 manifest/diff가 입력 정본 가능 |
| Review Packet | complete manifest/diff/source snapshot 포함 | pointer/hash 중심 최소 request | **REVISE** — raw 전체 복제 금지, 그러나 native/bundle mode에 따라 최소 evidence가 달라짐 |
| artifact | packet/manifest/events/result/audit | request/manifest/process/result | **DEFER** — raw event retention과 audit 분리 필요성이 실험 후 결정됨 |
| live Kit 연결 | 나중 | 나중 | **ACCEPT** — capability가 증명되기 전 D:\harness-kit mutation 없음 |

04가 허용하는 더 작은 첫 단계는 다음 관찰뿐이다. 이는 Integrated Plan이 아니다.

```text
임시·비민감 Git fixture
→ explicit Windows codex.cmd resolution 확인
→ exec review --uncommitted capability 관찰
→ sandbox/write/read canary 및 AGENTS precedence 관찰
→ 같은 change set의 immutable bundle 방식과 결과·비용 비교
→ scratch artifact와 filesystem/git 상태 검증
→ 완전 정리
```

이 실험 결과가 없으면 native review인지 bundle review인지, 필요한 schema가 무엇인지 확정할 수 없다.

# 7. Security / Compatibility 비교

## 7.1 합의된 security boundary

- `--sandbox read-only`와 `--ask-for-approval never`를 함께 검증한다.
- write denial과 out-of-root read denial은 별개의 관측값이다.
- `%USERPROFILE%`, parent, 다른 drive, environment canary는 비밀이 아닌 random marker만 사용한다.
- `--ignore-user-config`는 auth를 격리하지 않는다. 인증 material을 복사·출력·audit artifact에 저장하지 않는다.
- project/nested `AGENTS.md`를 만들거나 고치지 않는다. fixture에서 precedence만 관찰한다.
- source/diff/test output은 untrusted data다. task/project/provider instruction과 충돌하면 `blocked` 후보로 처리한다.
- process environment는 allowlist 후보이며 전체 environment dump는 금지한다.
- raw event/transcript는 handoff가 아니며 retention은 별도 승인 사항이다.

## 7.2 새로 강화할 점

- command resolver는 bare PATH만 신뢰하지 않는다. 명시 설정, Windows shim, version/help 실행 가능성을 분리한다.
- WSL shim을 Windows CLI fallback으로 자동 채택하지 않는다. 현재 관측상 잘못된 platform dependency로 실패한다.
- lexical path prefix는 junction/symlink security proof가 아니다. output root는 pre-created trusted directory, reparse-point 검사, create/write 직전 재검증을 별도 시험한다.
- structured schema 통과는 사실 검증이 아니다. parent가 path 존재, line anchor, manifest membership, test exit evidence를 재검사한다.
- human-readable 수정 제안과 machine-applicable patch를 구분한다. 둘을 같은 regex로 금지하지 않는다.

## 7.3 compatibility와 rollback

scratch-only capability experiment는 HRNS/Kit compatibility surface를 바꾸지 않는다. 이후 live Kit POC는 다음을 건드리지 않는 조건을 유지한다.

- `run-cycle.ps1`, 세 invoke wrapper, Claude runner/core
- `WORKFLOW_STATE` template/writer/validator/doctor contract
- daily 4-file, repository 3-file bridge
- HRNS command/state/Registry/UI
- Secondary LLM schema/task/runtime
- required runtime dependency inventory

live Kit POC가 생길 경우 rollback은 “새 파일 삭제”만으로 끝난다고 미리 단정하면 안 된다. 새 파일 manifest/hash, 수정한 기존 문서의 exact reverse patch 또는 사전 snapshot, 생성 log의 별도 retention/cleanup 기록이 필요하다.

# 8. Token / Cost 비교

두 03의 경제성 판단은 사실상 합의됐다.

```text
Accepted Change당 총 token
+ human intervention time
+ finding quality/재현율
+ false-positive/false-negative
+ failure/retry rate
```

04에서 다음을 보강한다.

1. capability experiment 한두 건은 경제성 결론이 아니라 고정비와 failure shape 관찰이다.
2. native review는 diff 전달 비용을 줄일 가능성이 있지만 scope가 opaque할 수 있다.
3. immutable bundle은 scope 감사성이 높지만 diff/source 복제와 packet 생성 비용이 증가한다.
4. manifest hash가 같을 때 재호출을 피하는 cache는 correctness와 invalidation rule이 먼저다.
5. raw Claude→Codex transcript는 전달하지 않는다. task/acceptance/evidence pointer와 structured finding만 교환한다.
6. Codex 추가 전 cheap lever는 별도 A/B로 먼저 평가한다. POC의 prerequisite로 default-on하지 않는다.

먼저 graduation할 후보의 우선순위는 유지한다.

- usage ledger 1.1 기반 baseline
- `parent_deterministic` navi
- `packet_first` navi
- compact planning/context diet의 semantic-parity 실험
- deterministic preflight와 불필요한 retry/provider call 제거

# 9. Tests / Gates 비교

향후 bounded task의 gate 후보를 중요도순으로 정리하면 다음과 같다. 이번 04에서는 실행하지 않았다.

| 우선 | gate | 판정 |
| --: | -- | -- |
| 1 | resolver matrix: bare PATH 없음, explicit Windows shim 성공, missing command, wrong-platform/WSL shim 실패, version/help parse | 신규 필수 |
| 2 | fake CLI argv/stdin/EOF: `exec` option과 `review --uncommitted` option 경계, 공백·한글·quote 경로 | 양쪽 제안 보강 |
| 3 | process: concurrent stdout/stderr, UTF-8, byte cap, timeout, cancellation, tree kill, residual typed result, no-window | 합의 |
| 4 | Git scope: staged/unstaged/untracked/deleted/rename/copy/binary/submodule/LFS와 scope drift/TOCTOU | 합의; unsupported object는 최초 POC에서 fail-closed 후보 |
| 5 | sandbox/read matrix와 pre/post filesystem·Git hash | 합의 |
| 6 | root/nested AGENTS 및 source/diff/test prompt-injection fixture | 합의 |
| 7 | schema와 claim audit: path/line/manifest/test-exit 재검증, provider exit와 review verdict 분리 | 합의 |
| 8 | retention/redaction/path traversal/junction/reparse/release pollution | 합의 |
| 9 | live Kit mutation 후 official offline suite, parser, release hygiene, clean-root onboarding, smoke index consistency | 합의; 현재 baseline은 95/84/11 |
| 10 | HRNS regression | HRNS/bridge/State 무변경이면 영향 근거를 기록하고 범위를 외부 owner가 결정 |

Claude 03의 “Doctor/Validate-Ops check 수 불변”은 **REVISE**한다. exact count는 optional diagnostic 추가에도 바뀔 수 있는 취약한 gate다. 첫 POC는 required dependency를 건드리지 않는 것이 우선이며, 필요 시 `overall`, severity, 기존 필수 check id와 변화 사유를 검증한다.

# 10. Consensus Map

## Consensus

- HRNS의 **Harness 실행 제어 경계**는 provider-blind다.
- HRNS 전체는 StopReason/config/bridge/UI/recovery observability에서 Claude-coupled다.
- provider routing이 장래 필요하다면 소유자는 Harness Runtime 쪽이어야 하고 HRNS `ActionPolicy`는 사용자 action 허용만 소유한다.
- `claude-invoke-core.ps1`은 공통 launcher가 아니다.
- process transport는 두 provider 구현 전 공통화하지 않는다.
- Secondary LLM은 advisory-only, default-off, loopback/hardware-gated, runtime 미통합이다.
- Secondary Git evidence는 unstaged tracked subset이며 complete change set이 아니다.
- Execution Packet 2.0은 write-worker 계약이므로 Review Packet으로 복제하지 않는다.
- 첫 Codex reviewer는 State/HRNS/bridge/run-cycle 밖의 optional read-only lane이어야 한다.
- State required field, Codex write, 자동 debate/commit, Ollama runtime 연결은 첫 범위가 아니다.
- provider 추가가 비용 절감을 보장하지 않는다.

## Revised Consensus

- “HRNS provider-neutral”은 “실행 제어 경계 provider-blind, 제품 일부 Claude-coupled”로 고친다.
- Secondary helper는 직접 재사용보다 non-authority/evidence/containment/redaction **패턴**을 참고한다.
- Review Packet은 기존 두 packet의 승격판이 아니라 mode별 최소 파생 계약 후보여야 한다.
- capability experiment는 repo/live Kit 신규 파일 0으로 먼저 수행하며 scratch는 임시·검증·정리 대상이다.
- CLI availability는 설치/해석/version/noninteractive/auth를 분리한다.
- read-only result는 remediation 설명을 허용하되 patch/apply authority는 갖지 않는다.
- POC compatibility 영향은 작지만 0이 아니며 release·docs·smoke·retention rollback을 포함한다.

# 11. Remaining Divergence

| 쟁점 | 현재 상태 | Codex 04 입장 |
| -- | -- | -- |
| 최초 authoritative review scope | native uncommitted vs immutable bundle | 아직 합의 불가. 같은 fixture 비교 필요 |
| manifest의 역할 | input 정본 vs audit detector | mode에 따라 다르다. native mode에서 manifest가 Codex 내부 scope를 통제한다고 주장하지 않는다 |
| raw JSONL retention | 진단 가치 vs 민감 정보/비용 | default 비보존을 우선 후보로 두되 parser/debug 가능성 실험 후 owner 결정 |
| Native UI QA 선행 여부 | live Kit mutation의 hard prerequisite인지 | scratch spike에는 불필요. live Kit POC 전 baseline QA를 권고하지만 외부 owner 결정 |
| 장기 State diagnostic key | `agent_runtime` 등 새 optional key | POC 밖이며 owner/consumer가 생기기 전 이름도 확정하지 않는다 |
| shared process supervisor 추출 시점 | Codex POC 직후 또는 더 뒤 | 최소 두 provider의 실제 tested transport가 안정된 뒤 별도 bounded refactor |

# 12. Requires Experiment

다음은 source/help만으로 결정할 수 없다.

1. explicit Windows shim으로 `codex exec review`가 실제 noninteractive/authenticated 실행되는가.
2. `--sandbox read-only`가 write를 막는 범위와 working root 밖 read를 막는 범위가 각각 무엇인가.
3. parent/other drive/home/env canary 접근이 tool call과 final output에 어떻게 나타나는가.
4. root/nested `AGENTS.md`와 stdin task instruction의 precedence 및 충돌 shape가 무엇인가.
5. `--ephemeral`이 session 외에 어떤 host artifact를 남기는가.
6. `--json` event와 `--output-schema` final result가 Windows에서 어떻게 분리되는가.
7. timeout/cancel 후 child process가 남는가.
8. `review --uncommitted`가 delete/rename/copy/binary/submodule/LFS와 concurrent change를 어떻게 처리하는가.
9. native review와 immutable bundle review 중 어느 쪽이 같은 finding quality에서 token·시간·감사성이 나은가.
10. auth/quota/usage failure의 exit code, stderr, JSONL shape가 무엇인가.

실험은 non-secret random canary와 disposable Git fixture에서만 수행하고, live Kit/HRNS/global config를 수정하지 않아야 한다.

# 13. External Confirmation Questions

사용자 또는 Integration Owner가 다음을 확인해야 한다.

1. zero-file scratch capability experiment를 먼저 수행해도 되는가.
2. experiment에서 기존 Codex authentication을 사용해 실제 network invocation 1~소수 건을 허용하는가.
3. native `exec review --uncommitted`와 immutable bundle 방식을 둘 다 비교할지, 하나만 우선할지.
4. raw JSONL을 임시 scratch에 저장 후 즉시 제거할지, redacted sample만 보존할지.
5. live Kit POC mutation 전에 미완료 Native UI QA baseline을 완료할지.
6. 초기 Codex command는 optional host dependency로 두고 명시 경로를 설정받을지.
7. finding quality/cost를 판단할 fixture와 acceptance threshold를 누가 승인할지.
8. 보안·acceptance·source evidence 충돌 시 최종 판정자는 Human이라는 원칙을 확정할지.

# 14. Final Cross-Review Recommendation

양쪽 03의 교차검토는 공통 사실 기반을 충분히 좁혔지만, 아직 Integrated Implementation Plan을 쓸 단계는 아니다.

가장 중요한 수렴 결과는 다음 한 문장이다.

> 기존 Claude production path와 HRNS/State/bridge를 보존한 채, Windows에서 실제 해석 가능한 명시적 Codex command를 사용하여 disposable fixture의 read-only review scope·isolation·instruction·cost를 먼저 관찰하고, 그 증거로 native review와 immutable bundle 중 첫 POC 방식을 선택한다.

외부 확인 후 다음 행동은 production 구현이 아니라 **bounded capability experiment task card 작성·승인**이어야 한다. 실험 결과가 command resolution, noninteractive/auth, scope, mutation 없음, structured result, read isolation을 충족할 때만 standalone POC의 dependency closure를 다시 확정한다.

## 프로토콜 공통 질문에 대한 답

1. **`D:\harness-kit` 전체 inventory를 실제로 확인했는가?** 예. 이번 04에서 현재 regular file 246개와 확장자 분포를 다시 확인했다.
2. **그중 본문을 끝까지 읽은 파일은 몇 개인가?** Codex 03 기준 61개다. Claude의 24개는 대부분 중복되므로 합산하지 않는다.
3. **search-only 파일은 몇 개인가?** Codex의 상호배타 분류 기준 178개다.
4. **내용 미검토 파일은 몇 개인가, 왜인가?** inventory/키워드 감사 누락은 0이지만 본문 미완독은 Search Only 178개와 Metadata Only 7개, 합계 185개다. 직접 POC closure 밖의 smoke·template·Python sidecar·대형 내부 orchestration 및 generated diagnostics이기 때문이다.
5. **Agent Runtime 변경 dependency closure를 모두 읽었는가?** 아니오, 최종 의미로는 아직 아니다. prospective standalone closure는 넓게 읽었지만 authoritative scope·artifact·retention 결정이 남아 closure 자체가 확정되지 않았다.
6. **Claude runtime의 실제 process launch/seam을 함수 수준에서 확인했는가?** 예. 세 runner의 `Start-ClaudeProcess`와 Claude core의 session/usage/stream 경계를 확인했다.
7. **Secondary LLM helper의 함수 수준 재사용 가능성을 확인했는가?** 예. 결론은 “직접 그대로 재사용”보다 non-authority/evidence/redaction/containment 패턴을 수정해 사용하고, 보안 경계는 새 검증이 필요하다는 것이다.
8. **execution packet schema 2.0을 실제 source에서 확인했는가?** 예. 전 필드와 TOCTOU identity 검사를 확인했다.
9. **현재 01/02/03에서 수정해야 할 가장 중요한 사실 3개는 무엇인가?** smoke는 100이 아니라 95다; Codex package 존재와 Windows/WSL 실행 가능성은 다르다; Secondary helper는 Codex security/contract 경계로 그대로 재사용할 수 없다.
10. **현재 제안보다 더 작은 첫 POC가 가능한가?** 예. repo/live Kit 신규 파일 0의 disposable capability experiment가 production POC보다 먼저다.
11. **Codex 추가보다 먼저 token cost를 낮출 기존 Harness 기능은 무엇인가?** usage baseline, parent-deterministic/packet-first navi, compact planning/context diet A/B, deterministic preflight와 retry 감소다.
12. **현재 evidence만으로 Integrated Plan 작성 단계로 넘어가도 되는가?** 아니오. 외부 승인과 §12 capability experiment가 먼저다.

## Mutation / Hygiene

- 생성 파일: `doc/revolution/revOpinion_codex04.md` 하나
- production source, `D:\harness-kit`, State, Registry, global Codex/Claude config: 변경 없음
- Git add/commit/push/checkout/restore/reset/stash/clean: 없음
- build/test/smoke/provider network invocation: 없음
- 읽기 전용 관측: 두 03 전문, Kit inventory/count, targeted source functions, local CLI package metadata와 `--help`
