# Phase 6B 작업 보고서 — 승인된 Harness Runtime 릴리스 통합 (선행 조건 검증)

> `Codex 독립 검증·보정 — 2026-07-28` 절을 이 보고서의 최신 판단으로 우선한다.

## Codex 독립 검증·보정 — 2026-07-28

### 검증 기준

- 검증 시작 HEAD: `40213a3 docs: Phase 6B 런타임 통합 작업 지시 추가`.
- `git diff --name-only HEAD`와 staged diff는 모두 비어 있었다. 이 보고서 외 core/infra/composeApp/Gradle source 변경은 없었다.
- 직전 `40213a3`은 `doc/claude_prompts/phase6b-bundled-runtime.md`, `doc/phase_reports/phase6-report.md`만 바꾼 문서 커밋임을 `git show --name-only`로 재확인했다.
- `doc/hrns_now_packaging_plan.md`는 사용자 소유 untracked 파일로 유지했고 수정·stage하지 않았다.

### Harness live tree 재검증

Codex가 PowerShell 읽기 전용 검사를 다시 실행했다.

| 승인 artifact 조건 | 독립 결과 | 판정 |
|---|---|---|
| 분리된 artifact root/version | `D:\harness-kit`에 `.git`, `dist`, `release`, `releases`, `artifacts`, `runtime`이 모두 없음 | FAIL |
| 공개 entrypoint/allowlist | 세 entrypoint는 존재하지만 재배포 allowlist 없음 | FAIL |
| immutable manifest | `kit-version.json` field는 `kit_version`, `state_schema_version`, `ui_contract_version`뿐 | FAIL |
| checksum/smoke/scan | D:·S: 전체에서 `checksums.sha256` 0건, Runtime smoke·금지 파일/secret scan 증거 없음 | FAIL |
| 제외 대상 부재 | 개발 tree에 `.claude`, `logs`, `scratch`가 실제 존재 | FAIL |
| Program Files 무쓰기 Runtime 계약 | bundled Runtime artifact가 없어 검증 대상 자체가 없음 | BLOCKED |

`*manifest*.json`은 D:·S: 전체에서 9건을 찾았다. 그러나 Harness 개발 backup 2건, Harness workspace evidence 4건, 무관한 S: 프로젝트/도구 manifest 3건뿐이며, 요구되는 Runtime version·entrypoint allowlist·포함 파일 목록·checksum을 가진 release artifact manifest는 0건이다. 따라서 원 보고서의 "두 후보만"이라는 좁은 검색 서술은 이 독립 검사 결과로 보정한다.

### 설계·테스트 판단

- 승인 artifact가 없으므로 Runtime distribution adapter, manifest/checksum parser, staging, MSI runtime 포함, workspace 자동 생성/repair를 구현하지 않은 판단은 Phase 6B prompt·최종 계획·설계 문서의 fail-closed 경계와 일치한다.
- 코드 변경이 없으므로 Gradle test/package를 재실행하지 않은 것은 합리적이다. 직전 Codex 검증 `c8e3fe3`의 동일 source 상태에서 333 tests 및 release package/distributable PASS가 기록되어 있다.
- 이 세션의 검증은 report UTF-8 no BOM, Git source 무변경, Harness live tree precondition 6개, artifact 후보/manifest/checksum 검색을 포함한다.

### Gate 판정

**Verdict: BLOCKED**

**G6A: BLOCKED (owner sequencing waiver 유지)**

**G6B: BLOCKED (승인 Runtime artifact 선행 조건 미충족)**

**NEXT_ALLOWED_PHASE: 승인 Harness Runtime artifact 제공 후 Phase 6B 재검증**

Phase 7은 허용하지 않는다. Harness 소유자가 artifact root, immutable manifest, checksum, allowlist, forbidden-file/secret scan, Runtime smoke 증거를 제공한 뒤에만 Phase 6B 구현을 재개한다.

## 0. 범위 선언 및 결론 요약

이 보고서는 `doc/claude_prompts/phase6b-bundled-runtime.md`의 지시에 따라 Phase 6B 작업을 시작하기 전에 문서가 요구하는 **최우선 선행 조건**("Harness 승인 Runtime release artifact"의 존재)을 먼저 검증한 결과를 다룬다.

**핵심 결론: 이 선행 조건은 충족되지 않았다.** 프롬프트가 명시한 6가지 항목(§1) 중 단 하나도 충족되지 않았으며, 문서는 이 경우 "Runtime packaging 구현을 추측으로 시작하지 말고, 확인 결과와 정확한 blocker를 보고서에 기록한다"고 명시한다. 이에 따라 이번 세션에서는 Phase 6B의 "승인 artifact가 있을 때만 구현할 범위"(§1 Runtime distribution 경계, §2 재현 가능한 staging·MSI, §3 project·workspace·복구, §4 기존 실행 계약 확장)를 **전혀 구현하지 않았다.**

- Phase 6B의 어떤 core/infra/composeApp 프로덕션 코드도 변경하지 않았다. Runtime distribution adapter, manifest/checksum parser, staging 로직, workspace 자동 생성, repair/재설치 복구 UX를 포함해 어떤 항목도 새로 작성하지 않았다.
- Phase 7, Post-MVP(코드 서명, 자동 업데이트, CD-Key/라이선스, portable mode, 암호화/난독화) 항목은 이번에도 손대지 않았다.
- `D:\harness-kit`은 선행 조건 확인을 위한 **읽기 전용 조회**만 수행했다(§2). 수정·복사·zip backup 생성·Git 작업(`git init`/`add`/`commit` 등)을 전혀 수행하지 않았다. `D:\harness-kit`은 여전히 `.git`이 없는 non-repository 상태임을 재확인했다.
- `doc/hrns_now_packaging_plan.md`는 설계 입력으로 다시 읽었을 뿐, 수정·삭제·stage하지 않았다. `git status`상 이 파일은 여전히 untracked 상태로 남아 있다(§6).
- 이 세션 동안 `git add`/`commit`/`amend`/`rebase`/`reset`/`stash`/`clean`/`push`를 수행하지 않았다. 커밋은 Codex 담당이다.

## 1. 선행 조건 검증 — Harness 승인 Runtime artifact 부재 (핵심 발견)

`phase6b-bundled-runtime.md` §"최우선 선행 조건"이 요구하는 6개 항목을 하나씩 실측했다.

| # | 요구 항목 | 확인 결과 | 근거 |
|---|---|---|---|
| 1 | 개발 트리와 분리된 artifact root와 Runtime version | **미충족** | `D:\harness-kit`은 여전히 개발 트리 그 자체이며(`.git` 없음, 최근 수정 파일들이 실 작업 산출물), 별도 artifact root(zip, tagged release 폴더, `dist/`, `release/` 등)가 존재하지 않는다. `D:`, `S:` 드라이브 전체를 `*harness-runtime*`, `*manifest*.json`, `checksums.sha256` 패턴으로 검색한 결과 관련 항목은 `D:\ai-workspaces\harness-kit-phase11_5-stabilizer-backup\manifest.json`, `D:\ai-workspaces\phase11-closure-final-2026-06-15\project-manifest.json`뿐이었고, 둘 다 개발용 backup/fixture 폴더이지 승인된 release artifact가 아니다(프롬프트가 명시적으로 "개발 root, 임의 zip, fixture... 승인 artifact로 취급하지 않는다"). |
| 2 | 공개 `run-cycle.ps1`/`doctor.ps1`/`validate-ops.ps1` 및 필요한 script/template의 명시적 allowlist | **미충족** | 세 entrypoint 스크립트 자체는 `D:\harness-kit\scripts\`에 실재한다(§2). 그러나 "이 파일들만이 재배포 대상"이라고 못박는 별도의 명시적 allowlist 문서/manifest는 없다. `docs/HARNESS_KIT_MAP.*.md`는 내부 구조 설명 문서이지 재배포 허용 목록이 아니다. |
| 3 | immutable manifest: `runtime_version`, `ui_contract_version`, `state_schema_version`, 공개 entrypoint, 포함 파일 목록 | **미충족** | `D:\harness-kit\kit-version.json`은 `{"kit_version": "2026.07.23", "state_schema_version": "1.0", "ui_contract_version": "1.0"}`만 담고 있다(§2). 요구 스키마의 `runtime_version` 필드가 없고(`kit_version`과 의미·용도가 다르다), `entrypoints` 맵도 없으며, 포함 파일 목록도 없다. 이 파일은 개발 중 계속 갱신되는 live 파일이라 "immutable"이라 볼 근거도 없다. |
| 4 | SHA-256 checksum, Runtime smoke, 금지 파일 검사, secret scan 결과 | **미충족** | `checksums.sha256` 파일은 시스템 어디에서도 발견되지 않았다. Runtime artifact에 대한 smoke 실행 기록, 금지 파일 검사 결과, secret scan 결과도 존재하지 않는다. |
| 5 | `.git`, Harness 개발 source, 실제 workspace, logs, raw session ID, secret/token, fixture/private data가 artifact에 없다는 증거 | **미충족(오히려 반증)** | 별도 artifact가 없으므로 이 증거 자체가 성립할 수 없다. 더 나아가 `D:\harness-kit` 최상위에는 `logs\`, `scratch\`, `.claude\` 폴더가 실제로 존재한다(§2) — 이는 정확히 release artifact에서 **제외되어야 할** 대상이다. 즉 개발 트리를 그대로 artifact로 간주하는 것은 이 요구사항을 정면으로 위반한다. |
| 6 | 모든 가변 출력과 daily surface가 전달된 workspace에만 생성되고 Program Files Runtime root에는 쓰지 않는다는 Harness 계약 | **검증 불가(대상 없음)** | 이는 "번들된 Runtime"이 Program Files 안에서 실행될 때의 계약이다. 현재 6A는 외부 Kit(`D:\harness-kit`)을 절대경로로만 참조하며 어떤 Runtime도 Program Files에 번들되어 있지 않으므로, 이 항목을 검증할 대상 자체가 아직 없다. (참고: 외부 Kit 참조 방식에서의 "Program Files 무쓰기"는 이미 Phase 6A 보고서 §6.3/§12에서 별도로 실측·확인된 사실이며, 이것과 혼동하지 않는다.) |

**결론**: 6개 항목 중 충족된 항목이 없다. `phase6b-bundled-runtime.md`의 명시적 지시("개발 root, 임의 zip, fixture, 또는 UI가 생성한 manifest/checksum을 승인 artifact로 취급하지 않는다. Harness artifact 생산·승인은 Harness 소유권 작업이며 이 프롬프트가 그 소유권을 우회하도록 허가하지 않는다")에 따라, 이번 세션에서는 Phase 6B의 실제 구현 범위(§1~§4)에 착수하지 않았다.

## 2. 검증 근거(실행한 명령과 원본 출력 요약)

```text
$ cd D:\harness-kit && git status
fatal: not a git repository (or any of the parent directories): .git

$ ls -la D:\harness-kit
.claude/  .docker-config/  README.md  claude/  core/  docs/  kit-version.json
logs/  profiles/  prompts/  python/  scratch/  scripts/  templates/

$ cat D:\harness-kit\kit-version.json
{
  "kit_version": "2026.07.23",
  "state_schema_version": "1.0",
  "ui_contract_version": "1.0"
}

$ ls D:\harness-kit\scripts
SMOKE_INDEX.md  SMOKE_INDEX.md.rej  discover-project.ps1  doctor.ps1
enter-project.ps1  init-workspace.ps1  invoke-append-execute-code-step.ps1
invoke-append-execute-doc-step.ps1  invoke-append-plan-micro-loop.ps1  lib
load-profile.ps1  promote-project-brief.ps1  report  run-cycle.ps1
smoke  validate-encoding.ps1  validate-ops.ps1

$ find /d /s /c -maxdepth 3 -iname "*harness-runtime*"
(no matches)

$ find /d /s /c -maxdepth 3 -iname "*manifest*.json"
/d/ai-workspaces/harness-kit-phase11_5-stabilizer-backup/manifest.json
/d/ai-workspaces/phase11-closure-final-2026-06-15/project-manifest.json
/c/Windows/BrowserCore/manifest.json   (OS 구성 요소, Harness와 무관)

$ find /d /s /c -maxdepth 3 -iname "checksums.sha256"
(no matches)
```

`D:\harness-kit`에 대해 수행한 작업은 위 읽기 전용 조회(`git status`, 디렉터리 나열, 파일 읽기)뿐이다. 어떤 파일도 쓰거나 옮기거나 복사하지 않았다.

## 3. 이번 세션 변경 파일

- `doc/phase_reports/phase6b-report.md` (이 문서, 신규 작성) 외에는 **어떤 파일도 변경하지 않았다.**
- `core`/`infra`/`composeApp`의 프로덕션 코드, 테스트 코드, `build.gradle.kts`, `gradle/libs.versions.toml` 등은 이번 세션에서 전혀 수정하지 않았다.
- 근거: 작업 시작 시점 `git status`는 `doc/hrns_now_packaging_plan.md`(사용자 소유 untracked 파일, 기존과 동일)만 untracked로 표시했고 `git diff --stat HEAD`는 변경 없음을 반환했다. HEAD는 `40213a3`(`docs: Phase 6B 런타임 통합 작업 지시 추가`)이며, 이 커밋도 `doc/claude_prompts/phase6b-bundled-runtime.md`·`doc/phase_reports/phase6-report.md`만 변경한 문서 전용 커밋임을 `git show --stat`으로 확인했다 — 즉 직전 검증 시점(`c8e3fe3`) 이후 소스 트리에 어떤 드리프트도 없다.

## 4. 6A/6B/7 경계 준수

- Phase 6A(`e16f49a`, `c59b615`, `c8e3fe3`)가 보존한 계약 — 외부 Kit 참조(Registry→환경변수→사용자 선택), 기존 workspace 선택 흐름, `%APPDATA%\hrns-now`/`%LOCALAPPDATA%\hrns-now` 소문자 경로, `BoundaryPolicy`, `ClosurePolicy`/`ActionPolicy` 독립성, `ExecuteHarnessActionUseCase`의 재검증→typed command→lock→runner→lock 보유 중 재읽기→release 순서 — 를 전혀 건드리지 않았다.
- Phase 6B 범위(Runtime distribution adapter, 승인 artifact staging, MSI에 Runtime 포함, project 등록 시 LocalAppData workspace 자동 생성, repair/재설치 UX)는 §1의 이유로 착수하지 않았다.
- Phase 7(실험 기능)과 Post-MVP(코드 서명, 자동 업데이트, side-by-side Runtime, CD Key/라이선스, portable mode, 암호화/난독화)는 이번에도 구현하거나 기존 CTA 권한에 연결하지 않았다.
- `WORKFLOW_STATE.json` 또는 Harness 소유 Markdown/로그를 직접 쓰지 않았고, `--continue`/자동 resume/임의 PowerShell 입력을 추가하지 않았다.

## 5. Runtime/Registry/workspace/Program Files 소유권과 SOLID·pattern 판단

구현이 없으므로 코드 수준 SOLID 평가는 해당하지 않는다. 다만 §1의 검증 결과가 향후 설계에 갖는 함의는 다음과 같다.

- `doc/hrns_now_design_pattern.md` §20은 "Phase 6의 runtime source 선택은 composition root의 작은 typed configuration으로 한정한다. `KitVersionManifestPort`/`CompatibilityPolicy`의 방향은 유지한다: infra adapter가 manifest를 읽고, core policy가 호환성을 판정하며, UI는 manifest·경로·PowerShell 인자를 직접 조합하지 않는다"고 규정한다. 승인 artifact가 없는 현재 시점에는 이 adapter/port 자체를 만들 근거(무엇을 읽을지, 어떤 스키마를 신뢰할지)가 없으므로, 코드를 먼저 작성하는 것은 오히려 존재하지 않는 계약을 추측으로 고정시키는 위험이 있다. 따라서 이번에는 어떤 typed configuration도, `RuntimeManifestPort` 유사 코드도 작성하지 않았다.
- 현재 `KitVersionManifestPort`/`CompatibilityPolicy`(Phase 2~3에서 구현된 외부 Kit 호환성 판정 계약)는 Phase 6B의 Runtime integrity 검사와 **근거가 다른 별도 결과**여야 한다는 프롬프트 지시를 그대로 존중했다 — 즉 이 둘을 미리 합치거나 복제하는 코드를 만들지 않았다.
- Program Files/Registry(`%APPDATA%\hrns-now`)/LocalAppData(`%LOCALAPPDATA%\hrns-now`) 소유권 경계는 Phase 6A 상태 그대로 유지된다. Runtime 번들이 실제로 도입되기 전까지 "Program Files의 app/JRE/Runtime은 read/execute-only" 요건은 현재도 "Runtime이 아예 없다"는 자명한 형태로 계속 성립한다.

## 6. 실행한 test/package/install/uninstall 명령과 미실행 사유

이번 세션에서는 소스 코드를 전혀 변경하지 않았으므로(§3), Gradle test/packaging task를 다시 실행하지 않았다. 근거:

- 직전 Codex 검증(`c8e3fe3`, 2026-07-28)이 동일 소스 상태에서 `:core:test :infra:test :composeApp:jvmTest check --rerun-tasks`(333 tests, 실패/에러/스킵 0), `:composeApp:packageReleaseMsi --rerun-tasks`, `:composeApp:createReleaseDistributable --rerun-tasks`를 모두 PASS로 이미 기록했다(`doc/phase_reports/phase6-report.md` §12 "테스트 결과").
- 이후 유일한 커밋(`40213a3`)은 문서 전용 커밋이며 `git show --stat`으로 소스 변경이 없음을 재확인했다(§3).
- 따라서 위 명령들을 다시 실행해도 동일한 결과 외에는 새로운 정보를 얻을 수 없다. 코드 변경이 없는 재검증 반복 대신, 이번 세션의 실제 작업 대상인 §1의 선행 조건 검증에 집중했다.
- `install`/`uninstall` clean smoke 역시 재수행하지 않았다 — 이는 Phase 6A 범위의 검증이며 Phase 6B 구현이 없는 이번 세션에서 반복할 대상이 아니다. 최신 clean-profile install/uninstall/reinstall 증거는 `phase6-report.md`의 2026-07-28 Codex 기록(§"Codex 독립 검증과 보정 — 2026-07-28")에 이미 있다.
- Phase 6B 전용 신규 검증 항목(승인 artifact staging fail-closed test, manifest/checksum parser test, Runtime smoke 등)은 대상 코드가 존재하지 않으므로 작성·실행하지 않았다. 이를 "테스트 생략"으로 얼버무리지 않기 위해, 애초에 무엇을 테스트할 대상 코드가 없다는 사실을 §1·§3에 명시했다.

## 7. G6A owner waiver 상태 / G6B 판정 / Codex 독립 검증 필요성

- **G6A**: `phase6-report.md`의 최신(2026-07-28) Codex 기록 기준으로 여전히 **BLOCKED**다. clean Windows/새 계정, 시스템 JDK 부재 환경, 표준 daily cycle 증거가 없다는 동일한 이유가 유지되며, 이번 세션은 G6A 상태를 바꿀 어떤 새 증거도 만들지 않았다.
- **Owner waiver**: 2026-07-28에 제품 소유자가 G6A를 PASS로 전환하지 않은 채 Phase 6B 착수를 명시 승인한 sequencing exception이 `phase6-report.md`에 기록되어 있고, 이번 세션은 그 승인 범위 안에서 Phase 6B의 **선행 조건 검증 단계**를 수행했다. 이 waiver는 G6A를 PASS로 바꾸지 않으며, G6B의 승인 artifact/manifest/checksum/secret-scan/runtime-smoke 요건을 완화하지 않는다 — 이번 검증 결과가 바로 그 요건이 여전히 완전히 살아있음을 재확인한다.
- **G6B verdict: BLOCKED (선행 조건 단계에서 차단)**. Phase 6B는 구현에 들어가기도 전, 가장 이른 차단 지점인 "승인된 Harness Runtime release artifact 존재 확인" 단계에서 막혔다. `NEXT_ALLOWED_PHASE`를 앞당길 근거가 없으므로 이번 세션 기준으로도 **Phase 6A Gate 보완 / Phase 6B 선행 조건 대기**가 다음 허용 단계다.
- **Codex 독립 검증 필요성**: Codex가 다음을 독립적으로 재확인해야 한다 — (a) 이번 세션이 실제로 소스 코드를 변경하지 않았다는 것(§3의 `git diff`/`git show --stat` 근거), (b) `D:\harness-kit`에 승인된 release artifact가 정말 없다는 §1의 6개 항목별 결론, (c) `doc/hrns_now_packaging_plan.md`가 이번에도 수정되지 않았다는 것. Phase 6B의 실제 구현 착수 여부와 Gate 판정은 이번에도 Codex만 결정한다.

## 8. Known risk / 다음 단계 권고

- **위험**: `D:\harness-kit` 개발 트리를 "일단 있으니까" 승인 artifact로 간주하고 싱글벙글 staging을 시작하면, `logs\`/`scratch\`/`.claude\` 등 실제 세션 산출물·private 데이터가 MSI에 흘러 들어갈 수 있는 구조적 위험이 있다(§1 항목 5). 이번 검증은 이 위험을 사전에 차단하기 위해 구현 착수 전에 멈춘 것이다.
- **권고**: Phase 6B 구현은 Harness 소유자가 다음을 갖춘 뒤에만 재개해야 한다 — (1) 개발 트리와 분리된 재현 가능한 artifact root, (2) `runtime_version`/`ui_contract_version`/`state_schema_version`/공개 entrypoint/포함 파일 목록을 담은 immutable manifest, (3) `checksums.sha256`, (4) Runtime smoke·금지 파일 검사·secret scan 결과, (5) `.git`/개발 source/실 workspace/로그/session ID/secret/fixture가 제외됐다는 증거. 이는 `doc/hrns_now_packaging_plan.md` §9·§16과 `doc/hrns_now_claude_plan.md`의 "Phase 6B — 승인된 Harness Runtime 릴리스 통합" 선행 소유권 Gate가 이미 요구하는 것과 정확히 같다.
- 이 다섯 가지가 준비되면, 다음 Claude 세션은 §"승인 artifact가 있을 때만 구현할 범위"(Runtime distribution 경계 → 재현 가능한 staging·MSI → project/workspace/복구 → 기존 실행 계약)를 순서대로 구현할 수 있다.

## 9. Harness/문서/Git 관련 명시

- `D:\harness-kit`은 이번 세션 동안 어떤 방식으로도 수정·복사·zip backup하지 않았다. 선행 조건 확인을 위한 읽기 전용 조회만 수행했다.
- `doc/hrns_now_packaging_plan.md`는 설계 입력으로 다시 읽었을 뿐 수정·삭제·stage하지 않았다.
- 이 세션에서 `git add`/`commit`/`amend`/`rebase`/`reset`/`stash`/`clean`/`push`를 수행하지 않았다. 커밋은 Codex가 담당한다.
