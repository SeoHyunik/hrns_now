# Claude 작업 지시 — Phase 6B: 승인된 Harness Runtime 릴리스 통합

## 역할·입력 문서·Git

당신은 HRNS-NOW의 **Phase 6B 구현 담당자**다. 승인된 Harness Runtime artifact를 Windows MSI에 안전하게 통합하고, 설치 후 외부 Kit 경로 입력 없이 동작하는 MVP 경로를 준비한다. Phase 7이나 Post-MVP 배포 기능을 구현하지 않는다.

작업 전 다음을 실제로 읽는다.

- `doc/hrns_now_claude_plan.md` — §0.4, Phase 6B, G6B, Phase 7/Post-MVP 경계
- `doc/hrns_now_design_pattern.md` — §20의 Runtime distribution adapter/typed configuration 규칙
- `doc/hrns_now_packaging_plan.md` — 사용자 소유 untracked 설계 입력. 읽기만 하고 수정·삭제·stage하지 않는다.
- `doc/phase_reports/phase5-report.md`, `doc/phase_reports/phase6-report.md`
- `doc/claude_prompts/phase6-msi-distribution.md`
- 이 문서

저장소는 `S:\dev\project\hrns_now`, branch는 `harness-dev`다. Git commit, amend, rebase, reset, stash, clean, push는 수행하지 않는다. 모든 commit은 Codex만 수행한다. 완료 보고서는 `doc/phase_reports/phase6b-report.md`에 UTF-8 without BOM으로 작성한다.

`D:\harness-kit`은 별도 live repository다. 아래의 승인 artifact 검증 외에는 수정·복사·zip backup 생성·Git 작업을 하지 않는다. HRNS-NOW와 Harness 저장소의 변경·보고서·commit을 섞지 않는다.

## 현재 Gate와 사용자 승인

- `e16f49a`(Phase 6A MSI 보정), `c59b615`(clean-smoke test 보강), `c8e3fe3`(clean-profile smoke 기록)을 보존한다.
- G6A는 clean HRNS-NOW profile 설치/제거/재설치 smoke까지는 확인됐지만, actual clean Windows·시스템 JDK 부재·표준 daily cycle 증거가 없어 **BLOCKED**다.
- 제품 소유자는 2026-07-28에 G6A를 PASS로 바꾸지 않은 채 **Phase 6B 착수**를 명시 승인했다. 이 예외를 보고서에 기록하되 G6A/G6B PASS나 Phase 7 진입을 주장하지 않는다.

## 최우선 선행 조건: Harness 승인 Runtime artifact

Phase 6B는 `D:\harness-kit` 개발 트리를 MSI에 복사하는 작업이 아니다. Harness 소유자가 제공한 **재현 가능한 승인 release artifact**가 있는지 먼저 확인한다. 아래 중 하나라도 없으면 Runtime packaging 구현을 추측으로 시작하지 말고, 확인 결과와 정확한 blocker를 보고서에 기록한다.

1. 개발 트리와 분리된 artifact root와 Runtime version.
2. 공개 `run-cycle.ps1`, `doctor.ps1`, `validate-ops.ps1` 및 실제 실행에 필요한 script/template의 명시적 allowlist.
3. immutable manifest: `runtime_version`, `ui_contract_version`, `state_schema_version`, 공개 entrypoint, 포함 파일 목록.
4. SHA-256 checksum, Runtime smoke, 금지 파일 검사, secret scan 결과.
5. `.git`, Harness 개발 source, 실제 workspace, logs, raw session ID, secret/token, fixture/private data가 artifact에 없다는 증거.
6. 모든 가변 출력과 daily surface가 전달된 workspace에만 생성되고 Program Files Runtime root에는 쓰지 않는다는 Harness 계약.

개발 root, 임의 zip, fixture, 또는 UI가 생성한 manifest/checksum을 승인 artifact로 취급하지 않는다. Harness artifact 생산·승인은 Harness 소유권 작업이며 이 프롬프트가 그 소유권을 우회하도록 허가하지 않는다.

## 승인 artifact가 있을 때만 구현할 범위

### 1. Runtime distribution 경계

- core에는 Path/PowerShell/Compose에 의존하지 않는 typed Runtime installation/configuration 값과 fail-closed 정책만 둔다.
- infra adapter가 approved manifest/checksum을 읽고 검증한다. unknown contract version, 누락 파일, checksum mismatch, 금지 파일/secret scan 실패는 실행 가능 상태로 projection하지 않는다.
- Composition root가 bundled Runtime과 기존 external Kit source를 명시적으로 선택·주입한다. UI가 Program Files/AppData/manifest 경로/checksum/PowerShell 인자를 직접 조립하지 않는다.
- `KitVersionManifestPort`/`CompatibilityPolicy`를 복제하지 않는다. Runtime integrity와 UI compatibility는 근거가 다른 별도 결과다.

### 2. 재현 가능한 staging·MSI

- MSI 입력은 승인 artifact allowlist만으로 staging한다. 개발 Harness, `.git`, workspace, logs, fixture, secret, session ID, raw output은 source tree·Git index·MSI 모두에 포함되면 안 된다.
- artifact/manifest/checksum이 없거나 검증 실패하면 packaging은 fail-closed로 중단한다. 단순 경고나 external Kit fallback으로 성공 처리하지 않는다.
- release MSI에 bundled Runtime 포함 및 external Harness source 미포함을 artifact inspection test로 검증한다.
- Program Files의 app/JRE/Runtime은 read/execute-only다. Registry는 `%APPDATA%\hrns-now`, lock·UI workspace는 `%LOCALAPPDATA%\hrns-now` 경계를 유지한다.

### 3. project·workspace·복구

- bundled Runtime 정상 상태에서는 Kit 경로 수동 입력 없는 기본 UX를 구현하되 Runtime source 선택·Registry·workspace·BoundaryPolicy·bootstrap을 God service로 합치지 않는다.
- project 등록 시 LocalAppData 기본 workspace를 이름+short UUID로 안전하게 생성하고 Registry에 연결한다. runtime/repository/workspace의 양방향 BoundaryPolicy와 canonical path 검사를 우회하지 않는다.
- UI는 daily 4-file이나 `WORKFLOW_STATE.json`을 직접 만들지 않는다. 날짜 준비는 기존 typed Harness bootstrap으로만 한다.
- repair/reinstall은 AppData Registry와 선택 workspace를 보존·재연결한다. 자동 migration, overwrite, repository 수정, 사용자 데이터 삭제는 금지한다.

### 4. 기존 실행 계약

- `ActionPolicy`, `ClosurePolicy`, `ExecuteHarnessActionUseCase`의 policy 재검증 → typed command → lock → runner → lock 보유 중 State reread → release 순서를 바꾸지 않는다.
- stdout 성공 문구로 완료 처리하지 않고 UI는 `WORKFLOW_STATE.json`을 절대 쓰지 않는다.
- 자동 resume/`--continue`, raw session ID·secret·token·raw log의 저장·표시를 추가하지 않는다.

## 설계·금지 사항

- Ports and Adapters, composition root, typed configuration, Repository, BoundaryPolicy를 현재 책임 경계에서 사용한다. core는 Compose/file system/ProcessBuilder/MSI 구현에 의존하지 않는다.
- ViewModel은 orchestration과 `StateFlow`만 담당하며 staging/file I/O/checksum parsing을 수행하지 않는다.
- checksum은 artifact 변조 감지 근거일 뿐 코드 서명·배포 채널 신뢰를 대체한다고 과장하지 않는다.
- 코드 서명, SmartScreen 우회, 자동 업데이트, side-by-side Runtime, CD Key/라이선스, portable mode, 암호화·난독화, Phase 7 실험 기능, UI 재설계, CTA 권한 변경을 구현하지 않는다.
- generated MSI/staging/runtime/log/cache/실제 workspace를 Git에 추가하지 않는다.

## 최소 테스트·검증

승인 artifact가 제공된 경우 다음을 구현·실행한다.

1. core: valid/missing/unknown manifest, checksum mismatch, contract mismatch fail-closed 정책.
2. infra: manifest/checksum parser, allowlist/forbidden-file/secret-scan/staging 실패, UTF-8/BOM·공백/한글 path.
3. composeApp: composition-root source 선택·Runtime integrity projection·invalid Runtime execute CTA 잠금.
4. packaging: artifact 부재 fail-closed, 승인 artifact 포함 MSI, 개발 Harness/`.git`/workspace/log/secret 미포함.
5. runtime smoke: bundled Runtime으로 doctor → Bootstrap → State read → 허용된 표준 cycle, 종료 뒤 State reread. daily file 직접 생성이나 stdout 성공으로 대체하지 않는다.
6. uninstall/reinstall: Program Files만 제거되고 AppData/LocalAppData Registry/workspace가 보존·재연결됨.

```powershell
.\gradlew.bat :core:test
.\gradlew.bat :infra:test
.\gradlew.bat :composeApp:jvmTest
.\gradlew.bat check
.\gradlew.bat :composeApp:packageReleaseMsi --rerun-tasks
.\gradlew.bat :composeApp:createReleaseDistributable --rerun-tasks
```

clean Windows/new-account smoke가 불가능하면 사실대로 기록한다. 승인 artifact·staging·integrity·재현 가능한 MSI 검증은 현재 환경에서도 생략하지 않는다.

## 보고서

`doc/phase_reports/phase6b-report.md`에 다음을 기록한다.

- artifact 출처·version·manifest/checksum/secret scan/runtime smoke 증거 또는 정확한 precondition blocker
- 변경 파일, 6A/6B/7 경계, `D:\harness-kit` 변경 여부, Git 작업 없음
- Runtime/Registry/workspace/Program Files 소유권과 SOLID·pattern 판단
- 실행 test/package/install/uninstall 명령과 결과, 미실행 사유
- G6A owner waiver 상태, G6B verdict, Codex 독립 검증 필요성

완료 시 코드 변경 요약, 테스트 출력, artifact 위치와 hash, known risk를 보고한다. G6B와 다음 Phase 허용 여부는 Codex만 판정한다.
