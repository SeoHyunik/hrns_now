# Claude 구현 프롬프트 — Phase 2 Harness JSON Contract와 Compatibility Handshake

## 역할과 작업 규칙

당신은 `hrns_now`와 Harness Kit 사이의 Phase 2 기계 판독 계약 구현 담당자다. 이번 작업은 Harness의 JSON 진단 표면, kit version 계약, 관련 문서·smoke 정합성, HRNS-NOW의 read-only compatibility handshake까지로 제한한다.

- HRNS-NOW 기준 저장소: `S:\dev\project\hrns_now`
- HRNS-NOW 기준 브랜치: `harness-dev`
- Harness live tree: `D:\harness-kit`
- 기준 문서: `doc/hrns_now_claude_plan.md`, `doc/hrns_now_design_pattern.md`
- 선행 보고서: `doc/phase_reports/phase1d-report.md`
- 이 프롬프트와 위 세 문서를 작업 전에 전체 읽는다.
- Claude는 어느 저장소에서도 Git commit, amend, rebase, reset, stash, clean, push를 수행하지 않는다. 구현·테스트·보고서만 working tree에 남기고 최종 검증·보정·커밋은 Codex가 수행한다.
- `S:\dev\project\hrns_now\doc\hrns_now_packaging_plan.md`는 관련 없는 사용자 파일이므로 읽거나 수정·삭제·stage하지 않는다.
- Phase 3 process/lock, Phase 4 request/planning/execution, Phase 5 Closure를 선구현하지 않는다.

## 작업 시작 전 필수 백업 확인 (Git 미사용 확정, 2026-07-24 사용자 결정)

`D:\harness-kit`은 **의도적으로** git 저장소로 관리하지 않는다 — Codex의 2026-07-24 사전 점검이 발견한 `.git` metadata 부재는 결함이 아니라 확정된 운영 방식이다. `D:\harness-kit`이 canonical live tree이며, 변경 이력·롤백은 git이 아니라 **수정 전 zip 백업**으로 관리한다(`D:\backup\harness-kit<MMDD>-<n>.zip` 명명 규칙은 이미 4월부터 사용 중인 기존 관례다). Phase 2는 Harness 측 변경과 HRNS-NOW 측 변경의 소유·기록을 분리해야 하므로 다음 순서를 지킨다.

1. `S:\dev\project\hrns_now`의 branch/status/HEAD를 확인한다. 선행 Codex Phase 1D 커밋이 HEAD인지 확인한다.
2. `D:\harness-kit` 소스를 수정하기 **전**, 현재 상태 전체를 `D:\backup\harness-kit<MMDD>-<n>.zip`(기존 관례상 다음 순번)으로 압축 백업한다. 백업 파일 경로·크기·시각을 보고서에 기록한다.
3. 백업 확인 후에는 `D:\harness-kit`을 canonical로 보고 그 자리에서 직접 수정한다. `git init`을 하지 않는다(git으로 관리하지 않기로 확정했으므로 불필요하다).
4. HRNS-NOW 쪽(`S:\dev\project\hrns_now`)과 Harness 쪽(`D:\harness-kit`)의 변경 파일 목록·diff·테스트 결과를 보고서에서 완전히 분리해 기록한다.
5. Claude는 어느 쪽에도 git commit을 하지 않는다 — HRNS-NOW는 기존 규칙대로 Codex가 검증 후 커밋하고, `D:\harness-kit`은 애초에 git 대상이 아니므로 zip 백업이 유일한 안전망이다.

## 선행 Phase 1D의 확정 상태

Codex가 Claude의 Phase 1D 초안을 검증하면서 다음을 보정했다. Phase 2에서 회귀시키지 않는다.

1. Registry mutation 결과는 typed이며 선택의 `markActive`, 삭제, 날짜 metadata 저장 실패를 성공으로 표시하지 않는다.
2. 등록·선택·삭제·날짜 변경 후 Registry 목록을 다시 읽는다. 등록은 active 선택이 영속화된 뒤에만 UI active로 반영한다.
3. 유효한 `yyyy-MM-dd` 폴더만 날짜 목록에 노출하며 명시 선택 > 오늘 > 최신 읽기 전용 fallback을 유지한다. 과거 날짜는 Harness write/execute CTA가 열리지 않는다.
4. Registry 손상은 원본 bytes를 quarantine한 뒤 유효 entry로 정본을 원자 재작성한다. 부분 손상 mutation도 원본 보존 전에 entry를 버리지 않는다.
5. Registry 정본·temp·backup이 Kit/Workspace/Repository root 아래 놓이는 구성은 fail-closed한다.
6. 프로젝트/날짜 context generation과 실제 read sequence를 분리했다. 변경 없는 poll tick은 진행 중 refresh를 무효화하지 않는다.
7. Registry/file/date/boundary I/O는 IO dispatcher에서 실행하고 Compose는 typed event와 immutable state만 다룬다.
8. Windows drive-letter/UNC Registry 경로는 Ubuntu CI에서도 판독하며 boundary 순수 정책 테스트는 host-independent path를 사용한다.
9. `CompatibilityStatus`는 Phase 2 전까지 `Unknown`이었다. 이번 Phase에서 실제 `kit-version.json` 결과로만 결정하며 임의 `Supported` 상수로 바꾸지 않는다.
10. `BoundaryStatus`는 Phase 1D 실제 `BoundaryPolicy` 결과만 사용한다.
11. Phase 1D 최종 로컬 검증은 core 66, infra 60, Compose 26, 총 152 tests와 전체 `check`가 통과했다.

## Phase 2 목표

Harness의 기존 사람용 텍스트 진단을 보존하면서 UI가 안정적으로 읽을 수 있는 JSON 계약을 추가한다. Kit root의 version manifest를 통해 UI가 자신이 지원하는 계약 범위를 판단한다. Harness docs/smoke 자기검증망과 HRNS-NOW compatibility policy를 함께 완성해 Gate G2를 통과할 수 있게 한다.

상태 진실과 daily surface는 변경하지 않는다.

- `WORKFLOW_STATE.json`이 유일한 상태 진실이다.
- required daily surface는 기존 4-file뿐이다.
- 로그는 required artifact가 아니다.
- UI/Harness 어느 쪽도 존재하지 않는 wrapper·상태 코드를 만들지 않는다.

## A. Harness Kit 변경 범위

### 1. `doctor.ps1 -Json`

live 파일과 호출 관계를 먼저 읽은 뒤 기존 텍스트 기본 출력을 그대로 유지하면서 명시적 `-Json` switch를 추가한다.

JSON stdout 계약:

```json
{
  "contract_version": "1.0",
  "overall": "ok|warn|fail",
  "checks": [
    { "id": "stable_machine_id", "severity": "info|warn|error", "message": "safe summary" }
  ]
}
```

- `-Json`에서는 stdout에 JSON 하나만 출력한다. progress/debug/`Write-Host` 혼입 금지.
- exit code 의미를 텍스트 모드와 정합하게 문서화하고 smoke로 고정한다.
- check ID는 표시 문구가 아니라 안정된 machine ID다.
- raw session ID, token, secret, response 원문, raw log를 JSON에 포함하지 않는다.
- JSON 모드 자체 때문에 workspace나 별도 결과 파일을 쓰지 않는다.
- 기존 텍스트 모드 문구·기본 동작을 제거하거나 JSON-only로 바꾸지 않는다.

### 2. `validate-ops.ps1 -Json`

`doctor.ps1`과 동일한 top-level 구조와 exit code 원칙을 사용한다. 기존 text default와 4-file daily 계약을 유지한다.

- `REQUEST_INBOX.md`, `TODAY_STRATEGY.md`, `DAILY_HANDOFF.md`, `WORKFLOW_STATE.json`만 required다.
- `REQUEST_STRUCTURED.md`와 두 로그 위치는 optional/informational이다.
- `WORKDAY_STATE.json`, `WORK_QUEUE.json`은 legacy fallback이며 readiness 성공 기준에 넣지 않는다.
- Markdown prose를 상태/실행 진실로 해석하지 않는다.

### 3. 구현 선례와 PowerShell 제약

- 반드시 `scripts/report/check-secondary-llm-capability.ps1`의 stdout JSON + 무파일-write 선례를 읽고 재사용 가능한 관례를 따른다.
- Windows PowerShell 5.1, `Set-StrictMode`, 기존 error semantics를 유지한다.
- 신규·수정 `.ps1`, JSON, Markdown은 repository 정책에 맞는 UTF-8 without BOM으로 저장한다.
- PSObject/hashtable → `ConvertTo-Json` 경계를 한 곳에 모으고 문자열로 JSON을 수작업 조립하지 않는다.
- 공백·한글·drive-letter 경로를 인자 목록 그대로 안전하게 처리한다.

### 4. `kit-version.json`

Kit root에 다음 정본 manifest를 추가한다.

```json
{
  "kit_version": "실제 Kit 버전",
  "state_schema_version": "실제 지원 WORKFLOW_STATE schema 버전",
  "ui_contract_version": "1.0"
}
```

- 값은 live 계약과 문서에서 근거를 확인한다. 과거 기억이나 임의 버전을 창작하지 않는다.
- `min_ui_version`처럼 Kit이 UI 구현 버전을 지시하는 역방향 필드를 만들지 않는다.
- unknown field 확장에는 관대하되 세 필드 누락/형식 오류는 smoke에서 실패한다.

### 5. 문서 계약

- `docs/OPERATING_GUIDE.md`: 이중 로그 구조와 권위 수준을 명시한다.
  - `<workspaceRoot>\logs\<yyyy-MM-dd>\` = wrapper 실행 로그
  - `<dayRoot>\logs\` = day 산출물
  - continuity/usage-ledger 진단 경로
  - 로그는 참고 정보이고 State 판단 권위가 아님
- `docs/STATE_MODEL.md`: UI 소비 보증 필드 절을 추가한다. `queue.active`는 `card_id`/`slice_id` pointer만 보증하며 wrapper/authorized target을 그 위치에 창작하지 않는다.
- `scripts/SMOKE_INDEX.md`, `docs/HARNESS_KIT_MAP.en.md`, `docs/HARNESS_KIT_MAP.ko.md`의 수치와 목록을 실제 smoke inventory에 맞춘다.

### 6. Harness smoke와 docs calibration

최소 신규 smoke 3종을 추가한다.

1. doctor JSON shape/exit/stdout purity/text default 회귀
2. validate-ops JSON shape/exit/stdout purity/text default/4-file 계약 회귀
3. kit-version 필수 field/version shape 회귀

smoke 추가 후 다음을 실제 계산값으로 함께 갱신한다.

- `scripts/SMOKE_INDEX.md`
- `scripts/lib/secondary-llm/secondary-llm-docs-calibration.ps1`의 하드코딩된 automatic/offline/manual/docs 수치
- `docs/HARNESS_KIT_MAP.en.md`
- `docs/HARNESS_KIT_MAP.ko.md`

과거 기준 `72/36/61/11/0`을 복사하지 않는다. 현재 inventory를 명령으로 계산하고 신규 smoke 반영 후 모든 문서에 동일하게 적용한다. automatic/offline smoke 전체와 docs mismatch scan을 실행해 `status=ok`, `findings=0`을 증명한다. 실제 runner/scan 명령은 `scripts/SMOKE_INDEX.md`, README, 기존 scripts에서 확인하고 보고서에 그대로 기록한다. 명령을 추측해 만들지 않는다.

## B. HRNS-NOW Compatibility Handshake

Harness 측 zip 백업이 완료되고 A 항목 구현이 진행된 같은 Phase에서 `S:\dev\project\hrns_now`에는 read-only compatibility reader/policy만 구현한다.

### 1. Domain과 Policy

- `KitVersion`, `ContractVersion` 또는 동등한 typed 모델
- version parse 결과를 `Success`, `Missing`, `Malformed`, `Unsupported` 등 sealed Result로 구분
- UI가 지원하는 `state_schema_version`/`ui_contract_version` 범위를 domain policy에 선언
- supported major → 정상
- 상위 minor + 같은 major → unknown field를 허용하되 명시 정책으로 판정
- 미지원 major → 원인을 표시하고 모든 Harness write/execute CTA 잠금
- version 파일 없음 → legacy/unknown으로 실행 잠금
- malformed/필수 field 누락 → fail-closed
- Kit이 UI 버전을 지시하지 않고 UI policy가 지원 범위를 판단

Compatibility policy는 파일·JSON·Compose를 참조하지 않는 순수 함수로 두고 표 기반 테스트를 작성한다. 기존 단순 `CompatibilityStatus`를 확장할 필요가 있으면 raw 외부 문자열을 화면 곳곳에 퍼뜨리지 않는 최소 typed 구조로 정교화한다.

### 2. Port/Adapter

- core port는 Kit root의 manifest 읽기 의미만 표현한다.
- infra JSON adapter는 `ignoreUnknownKeys=true`, UTF-8 BOM 허용 read, strict malformed/encoding 구분을 따른다.
- Kit root 밖의 파일을 탐색하거나 쓰지 않는다.
- compatibility read는 IO dispatcher에서 수행한다.
- last-known-good/stale를 도입한다면 Phase 1A `StateReadResult`/Projection 의미와 충돌하지 않게 명시한다.

### 3. Presentation 연결

- `CockpitUiStateAssembler`의 `CompatibilityStatus.Unknown` 하드코딩을 실제 compatibility 결과로 교체한다.
- 프로젝트 전환/수동 refresh/polling에서 선택된 프로젝트의 Kit root와 같은 context generation을 사용한다.
- unsupported/missing/malformed 상태는 안전한 설명을 표시하고 Phase 1B ActionPolicy가 실행을 잠그게 한다.
- raw session/token/secret 유사 원문, arbitrary JSON 원문을 화면에 표시하지 않는다. version 값처럼 안전하게 정형화된 진단만 표시한다.
- ProcessBuilder, PowerShell 실행 버튼, doctor/validate-ops 실행 연결은 Phase 3 범위이므로 추가하지 않는다.

## 필수 테스트

### Harness

1. doctor text default 회귀
2. doctor `-Json` stdout 단일 JSON, shape, overall/check severity, exit code
3. validate-ops text default 회귀
4. validate-ops `-Json` stdout 단일 JSON, shape, exit code
5. JSON 모드 불필요한 파일 write 0
6. 4-file required/optional logs/legacy 분류 회귀
7. 공백·한글 경로
8. secret/session/token 비노출
9. kit-version 필수 field/unknown field/invalid version
10. 신규 smoke 포함 automatic/offline 전체 PASS
11. docs calibration/KIT MAP/SMOKE_INDEX 실제 수치 일치
12. docs scan `findings=0`
13. PowerShell 5.1 + StrictMode + UTF-8 no BOM

### HRNS-NOW

1. kit-version 정상 round-trip
2. unknown field 허용
3. 필수 field 누락, malformed, invalid UTF-8/BOM
4. supported major, upper minor, unsupported major, missing file 결정표
5. compatibility unsupported/unknown에서 ActionPolicy execute/write 잠금
6. 프로젝트 전환 시 새 Kit root manifest 사용
7. 늦게 끝난 이전 compatibility read가 새 프로젝트 상태를 덮지 않음
8. compatibility I/O가 UI dispatcher에서 실행되지 않음
9. Phase 1A~1D 152-test 회귀
10. 전체 `.\gradlew.bat check`

## 금지 사항

- required daily 4-file 변경 또는 로그를 required로 승격
- 기존 text default 제거
- JSON 모드에서 불필요한 workspace/file write
- `min_ui_version` 또는 Kit이 UI 구현 버전을 지시하는 계약
- `queue.active.wrapper`/`authorized_target_file` 재도입
- 존재하지 않는 wrapper/state code/`validation` mode 창작
- `WORKFLOW_STATE.json` write
- compatibility를 파일 판독 없이 `Supported` 상수로 고정
- Phase 3 ProcessBuilder/lock/masking/doctor 실행 UI 선구현
- 자동 resume, `--continue`
- smoke 수치 일부 문서만 갱신
- 테스트 skip/삭제 또는 docs scan 약화
- 관련 없는 포맷·대규모 리팩터링
- Git init/commit/push

## 완료 보고서

`S:\dev\project\hrns_now\doc\phase_reports\phase2-report.md`를 UTF-8 without BOM으로 작성한다.

보고서에 반드시 포함한다.

- Harness 측 zip 백업 파일 경로·크기·시각(수정 전 스냅샷 증거)
- HRNS-NOW/Harness 변경 파일을 분리한 목록
- doctor/validate-ops text·JSON·exit code 계약
- kit-version 실제 값과 근거
- UI compatibility 지원 범위와 fail-closed 결정표
- 이중 로그와 UI 보증 field 문서화
- smoke 신규 목록, 변경 전/후 실제 inventory 수치
- docs calibration, 두 KIT MAP, SMOKE_INDEX 동시 갱신 근거
- automatic/offline smoke 및 docs scan 실제 명령·결과·건수
- HRNS-NOW targeted/module/full test 명령·건수
- Phase 1D Codex 보정사항 회귀 여부
- 미구현 Phase 3 경계
- Claude가 어느 저장소에서도 commit하지 않았다는 명시

zip 백업을 만들기 전에는 `D:\harness-kit` 소스를 수정하지 않는다. 구현 완료 선언만 하지 말고 actual file, diff, test/smoke 결과를 근거로 보고한다.