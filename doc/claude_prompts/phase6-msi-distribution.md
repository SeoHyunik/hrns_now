# Claude 작업 지시 — Phase 6A: 외부 Kit Windows MSI MVP

## 역할과 입력 문서

당신은 HRNS-NOW의 **Phase 6A 구현 담당자**다. 다음 문서를 모두 읽고, 6A 범위만 구현·검증한다.

- `doc/hrns_now_claude_plan.md` — 특히 §0.4, Phase 6A/6B, G6A/G6B와 Post-MVP 배포 확장
- `doc/hrns_now_design_pattern.md` — 특히 §20의 Phase 6A/6B 경계
- `doc/hrns_now_packaging_plan.md` — 사용자 소유의 비추적 초안; 설계 입력으로만 읽는다
- `doc/phase_reports/phase5-report.md`
- 이 문서

저장소는 `S:\dev\project\hrns_now`, branch는 `harness-dev`다. Git commit, amend, rebase, reset, stash, push를 수행하지 않는다. Codex만 commit한다. 완료 증거는 `doc/phase_reports/phase6-report.md`에 **UTF-8 without BOM**으로 작성한다.

`doc/hrns_now_packaging_plan.md`는 이번에 Codex가 분석해 Phase 6A/6B 경계로 계획서에 편입한 사용자 파일이다. 수정·삭제·stage하지 않는다. `D:\harness-kit`도 read-only이며 수정·복사·zip backup 생성을 하지 않는다.

## 선행 Gate와 Codex가 보존한 계약

Phase 5는 `PASS_WITH_FIXES`로 통과했다. Codex 커밋은 다음과 같다.

```text
9e6b267 feat: Phase 5 마감 검증과 복구 센터 구현
0997dfd docs: Phase 5 검증 보고서와 Phase 6 작업 지시
6212d45 docs: 패키징 로드맵과 Phase 6A 지시 정렬
```

이후 Codex는 `hrns_now_packaging_plan.md`를 검토해 최종 계획과 설계 문서를 다음처럼 정정했다. 이 변경을 보존한다.

- Phase 6A는 **외부 Kit을 참조하는 MSI + 번들 JRE**만 다룬다. Phase 6 전체 완료가 아니며 G6A만 통과한다.
- `D:\harness-kit` 개발 트리나 private Harness 원본을 Installer/Git/staging에 넣지 않는다. 내장 Runtime은 Harness release artifact·manifest/checksum·smoke·재배포 승인이라는 별도 소유권 Gate가 갖춰진 **Phase 6B**만의 일이다.
- `%APPDATA%\hrns-now\projects.json`과 `%LOCALAPPDATA%\hrns-now\locks`의 기존 사용자 데이터 위치를 보존한다. 제품 표시용 `HRNS-NOW` 표기 때문에 경로를 자동 이관하거나 대소문자를 변경하지 않는다.
- Runtime root, repository root, project workspace root는 서로 분리된다. Registry·lock 등 UI 소유 파일은 Harness workspace에 만들지 않는다.
- workspace 자동 생성·이름+short UUID·repair/재설치의 전체 복구 UX는 6B다. 6A에서는 기존의 검증된 workspace 선택·등록 흐름을 유지하고 UI가 daily 4-file을 직접 만들지 않는다.
- 코드 서명, 자동 업데이트, side-by-side Runtime, CD Key/라이선스, portable data mode, 암호화/난독화는 Phase 7과도 분리된 Post-MVP 배포 확장이다. 구현하거나 기존 CTA 권한에 연결하지 않는다.

기존 Phase 5 계약도 변경하지 않는다.

- `ClosurePolicy`와 `ActionPolicy`는 독립 정책이다. closure validation은 두 정책의 교집합에서만 실행한다.
- `ExecuteHarnessActionUseCase`의 policy 재검증 → typed command → lock → runner → lock 보유 중 State reread → release 순서를 변경하지 않는다.
- `WORKFLOW_STATE.json`은 UI가 절대 쓰지 않으며 Markdown/log/diagnostic은 CTA·Closure의 권위가 아니다.
- raw session ID, secret, token, raw log를 Registry·installer 설정·report·UI에 저장 또는 표시하지 않는다.

## Phase 6A 목표

깨끗한 Windows 환경에서 MSI를 설치하고, 외부의 호환 가능한 Harness Kit로 다음 흐름을 검증한다.

```text
MSI 설치 → 외부 Kit root 지정 → 프로젝트/기존 workspace 등록
→ doctor → State 조회 → 표준 일일 cycle
```

이 목표는 `G6A`다. “Kit 경로 수동 지정 없이 Runtime을 포함한 제품”은 6B 전에는 주장하거나 완료 선언하지 않는다.

## 필수 구현

1. Compose Desktop packaging 계약을 실측한다.
   - 먼저 `./gradlew.bat tasks`로 실제 packaging task를 확인한다.
   - `composeApp/build.gradle.kts`의 target은 `TargetFormat.Msi`만 남긴다. DMG/DEB를 build 또는 검증하지 않는다.
   - 사용자 노출 앱 이름, package name, version, Windows icon을 실제 MSI 산출물 기준으로 정리한다. version은 한 곳의 source of truth를 사용하고 임의 날짜/개인 경로를 production code에 넣지 않는다.
   - JRE bundle/jlink 옵션은 현재 Gradle/Compose 버전의 실제 계약으로 확인한다. module pruning은 clean smoke가 증명할 때만 적용하고, 확신이 없으면 실행 가능한 안전한 bundled runtime을 선택한다.
2. 설치 소유 영역과 사용자 데이터 영역을 지킨다.
   - Program Files에는 MSI가 설치한 app/JRE만 있고 정상 실행 중 Registry, lock, workspace, process output, log, cache를 만들지 않는다.
   - Registry/lock의 기존 composition-root 경로와 project boundary 검사를 보존한다. 화면이나 domain이 `%APPDATA%`, `%LOCALAPPDATA%`, Program Files 문자열을 곳곳에서 조합하지 않게 한다.
   - 별도 abstraction이 정말 필요하면 composition root에 주입되는 작은 typed configuration만 추가한다. `KitVersionManifestPort`/`CompatibilityPolicy`의 책임을 복제하거나 Runtime/Registry/Boundary/Workspace를 한 service에 합치지 않는다.
3. 외부 Kit 해석·호환성·경계를 회귀 검증한다.
   - 순서는 `Registry → 환경변수 fallback → 사용자 선택`이며, compatibility mismatch/unknown은 기존과 같이 fail-closed다.
   - `D:\harness-kit`, fixture 날짜, 특정 drive letter를 production에 hardcode하지 않는다.
   - Runtime/repository/workspace의 상호 포함 및 junction/symlink 검사는 `BoundaryPolicy`를 우회하지 않는다.
4. 실제 MSI 및 설치 smoke를 수행한다.
   - MSI build task를 실행하고, 산출물 경로와 JRE 포함 여부를 report에 남긴다.
   - 가능한 깨끗한 Windows 환경(별도 VM/계정/명시된 test location)을 사용해 install → launch → 외부 Kit 등록 → project/workspace 등록 → doctor → State read → 표준 cycle을 검증한다.
   - Kit/workspace/repository에 공백·한글을 포함한 경로와 가능한 경우 다른 drive letter를 사용한다. installer 또는 app 실행 뒤 Program Files에 사용자 산출물이 생기지 않고 Harness 산출물은 선택 workspace에만 생성되는지 확인한다.
   - uninstall 뒤 `%APPDATA%\hrns-now` Registry와 `%LOCALAPPDATA%\hrns-now` workspace/lock 등 사용자 데이터가 기본 보존되는지 smoke한다. 사용자 data purge 옵션·자동 cleanup은 만들지 않는다.
5. 배포 제한을 정직하게 문서화한다.
   - 미서명 MSI의 SmartScreen 경고와 코드 서명 필요성을 기록하되 인증서·private key·secret을 생성·저장·commit하지 않는다.
   - installer, runtime image, build output, 실행 로그, IDE cache, local test workspace를 Git에 추가하지 않는다.

## 명시적 금지

- Phase 6B: bundled Harness Runtime, Runtime manifest/checksum/staging/secret-scan, `D:\harness-kit` 복사, private runtime artifact 제작
- Harness Kit 수정, zip backup 생성, Harness 4-file surface·PowerShell parameter 계약 변경
- workspace 자동 생성, daily 4-file 직접 작성, workspace migration/repair, uninstall data-delete UX
- Phase 7 기능 및 Post-MVP 라이선스/CD Key/자동 업데이트/portable mode/암호화·난독화
- `WORKFLOW_STATE.json` 또는 Harness 소유 Markdown/로그 직접 쓰기, `--continue`, 자동 resume, 임의 PowerShell 입력, stdout 성공 문구만으로 완료 판정
- 테스트 삭제·skip·계약 약화·fixture/mock fallback으로 설치 smoke 성공을 꾸미기

## 검증 순서

실제 task 이름을 확인한 뒤, 변경 범위에 맞춰 targeted → module → full 순서로 실행한다.

```powershell
.\gradlew.bat :core:test
.\gradlew.bat :infra:test
.\gradlew.bat :composeApp:jvmTest
.\gradlew.bat check
```

MSI build와 install smoke는 필수다. WiX/jpackage/권한/clean machine이 없어 수행하지 못하면 성공으로 바꾸지 말고, 실행한 명령·출력·정확한 막힘과 G6A 미충족을 report에 남긴다.

## 보고서 필수 내용

`doc/phase_reports/phase6-report.md`에 다음을 기록한다.

- 변경 파일과 MSI/JRE/external-Kit 설계 근거, 6A와 6B를 섞지 않았다는 증거
- 실제 Gradle packaging task, MSI 산출물 위치, JRE 포함 여부, 앱 metadata/icon/version source
- clean install/launch/uninstall 보존 smoke의 환경·절차·결과
- 공백·한글·drive-letter 경로, Program Files 무쓰기, Registry/lock/workspace 경계 검증 근거
- SmartScreen/서명 상태와 알려진 배포 제한
- 실행한 테스트 및 미실행·실패 사유
- Harness 변경 없음, `doc/hrns_now_packaging_plan.md` 변경 없음, Git 작업 없음 명시

Codex가 live 파일, diff, MSI output, 설치 결과, 테스트와 두 설계 문서를 독립 검증한 뒤에만 G6A 및 Phase 6B 착수 가능 여부를 판정한다.
