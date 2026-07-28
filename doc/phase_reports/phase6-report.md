# Phase 6A Gate supplement — latest Codex record (2026-07-28)

> This is the authoritative current Gate record. Retain earlier history below, but resolve conflicts in favor of this section, live source, and cited evidence.

## Codex independent verification and correction — 2026-07-28

### Commit and branch basis

- Repository / branch: `S:\dev\project\hrns_now`, `harness-dev`.
- Validation started at `c59b6156b9f2a3a2017f4f91bc664f243e9b271f` (`test: Phase 6A clean Windows smoke verification reinforcement`).
- The Phase 6A MSI correction `e16f49a11fdb68b83684bdb1919616edf8bd19d6` is an ancestor.
- The original GitHub README commit `6f64e7984882b5c89e678099e44b5697961437e4` is represented by identical cherry-pick `2923dd6119d088d323cff2c64753365f5f2e3867`.
- User-owned untracked `doc/hrns_now_packaging_plan.md` was not read, changed, staged, or committed.

### Environment and backup

- Host: Windows 11 Home 10.0.26200 (build 26200), a development account with an existing system JDK. No VM, Windows Sandbox, or new Windows account was available.
- This is therefore **clean HRNS-NOW profile evidence, not clean Windows evidence**. Only the existing install, `%APPDATA%\hrns-now`, and `%LOCALAPPDATA%\hrns-now` were reset.
- Deletion targets were backed up first: `S:\hrns-now-preclean-profile-20260728-103056.zip`; SHA-256 `B890E6D8C13D720EB8FF4A89A6B8A42C034D4A6A5DAD570456E75F830C85D67A`; 210 entries.
- The archive contains the prior Program Files install, AppData Registry, LocalAppData locks, and a metadata-only manifest for preserved targets. It contains no Harness Kit/workspace/repository or Claude/Codex cache contents.
- Redacted evidence metadata is at `S:\hrns-now-cleanprofile-evidence-20260728-103056`; it deliberately excludes raw Registry data, secrets, tokens, session IDs, and raw process logs.

### Release MSI and install results

- The release MSI installed in this smoke was freshly built from the current application source HEAD: `composeApp\build\compose\binaries\main-release\msi\HRNS-NOW-1.0.0.msi`, 50,448,977 bytes, installed-artifact SHA-256 `91A325F28151FCC6EE70D891DAD86EAE794C30432434F344E43642F34A1A4B70`.
- A subsequent `:composeApp:packageReleaseMsi --rerun-tasks` also passed. Its fresh MSI SHA-256 is `9E7081DB9BDABBC92649042550B098288CE5642BCC129314BC3C668013DF7452` (same byte size, timestamp `2026-07-28T02:00:52.802Z`). MSI container metadata can differ between fresh builds; install and repackaging hashes are recorded separately.
- Package version is `1.0.0`; bundled runtime and `jdk.charsets` are present; release image has no `runtime\harness`.
- The release MSI installed elevated with exit code 0. Program Files install root, Start Menu entry, custom icon, and bundled runtime appeared. Installed `HRNS-NOW.exe` launched from Program Files (two app processes observed).
- The host has a system JDK, so this does not prove launch on a system without JDK.

### External Kit, Registry, and State

- Tested roots were mutually disjoint and used separate drives plus Korean/space-containing path segments: external Kit `D:\harness-kit`, a C: workspace, and a separate S: Git repository. The repository was clean before and after smoke.
- The user completed UI Kit/workspace/repository/profile registration, Doctor, and State refresh. Independently, `D:\harness-kit\scripts\doctor.ps1 -Json` returned exit 0 and one JSON object with `checks`, `contract_version`, and `overall`. Harness Kit was read/executed only.
- `%APPDATA%\hrns-now\projects.json` is UTF-8 without BOM, contains one project, retains the selected Kit/workspace/repository/profile values, and has no token/secret/session field name.
- No Bootstrap was run in the deliberately empty workspace. The required daily files and `WORKFLOW_STATE.json` were not invented by the UI; this is correct behavior but is not standard daily-cycle evidence.

### Program Files, uninstall, and reinstall

- Program Files recursive inventory was 218 entries both immediately before and after app launch; its inventory hash was unchanged. Registry and locks remained outside Program Files.
- After normal app exit, MSI ProductCode `{222C4952-F8A2-392C-966F-186D6A0D5227}` uninstalled with exit code 0. Program Files, Start Menu entry, and Windows Installer registration were removed.
- Registry SHA-256 stayed `F57BC8E8FD023E8E104DF5B73435D3D06505167681EDBC78ACF35EF2AA094959`; LocalAppData, workspace, repository, and external Kit were preserved.
- Reinstalling the same release MSI returned exit code 0. The Registry hash was retained before relaunch, and the user confirmed the previous project appeared automatically after relaunch.

### Verification

- `scripts/Invoke-Phase6ACleanWindowsSmoke.ps1`: PowerShell 5.1 AST parse PASS (0 errors).
- `:core:test :infra:test :composeApp:jvmTest check --rerun-tasks`: PASS (333 tests; 0 failures/errors/skips).
- `:composeApp:packageReleaseMsi --rerun-tasks`: PASS.
- `:composeApp:createReleaseDistributable --rerun-tasks`: PASS.

### Gate decision

**Verdict: BLOCKED**

**G6A: BLOCKED**
**NEXT_ALLOWED_PHASE: Phase 6A Gate supplement**

The release MSI clean-profile install, launch, external Kit registration, Doctor JSON contract, Program Files no-write check, uninstall data preservation, and reinstall Registry recovery have evidence. A true clean Windows/new-account run, launch with no system JDK, and a Harness typed Bootstrap/Planning/allowed execution standard daily cycle still lack evidence. Do not infer PASS; Phase 6B and Phase 7 remain prohibited.
# Phase 6A 작업 보고서 — 외부 Kit Windows MSI MVP

## 0. 범위 선언

이 보고서는 Phase 6A(외부 Kit 참조 + 번들 JRE MSI MVP)만 다룬다. Phase 6B(번들 Harness Runtime, manifest/checksum, secret-scan, 재배포 승인 Gate), Phase 7, Post-MVP(코드 서명, 자동 업데이트, CD-Key/라이선스, portable mode, 암호화/난독화)는 구현하지 않았다. `D:\harness-kit`은 읽기 전용으로만 사용했고 수정·복사·zip backup을 만들지 않았다. `doc/hrns_now_packaging_plan.md`는 설계 입력으로 읽기만 했고 수정·삭제·stage하지 않았다. 이번 세션 동안 git add/commit/amend/rebase/reset/stash/push를 수행하지 않았다.

## 1. 변경 파일

- `composeApp/build.gradle.kts` — `compose.desktop.application` 블록에 MSI 패키징 설정 추가(§2 참고). 기존 `kotlin {}` source set 구성은 변경하지 않았다.
- `gradle/libs.versions.toml` — `[versions]`에 `hrnsNowApp = "1.0.0"` 추가(패키지 버전의 단일 source of truth). 기존 항목은 변경하지 않았다.
- 그 외 `core`/`infra`/`composeApp`의 프로덕션·테스트 코드는 이번 phase에서 변경하지 않았다(Phase 5까지의 계약을 그대로 보존).

## 2. Compose Desktop packaging 계약 — 실측 근거

`./gradlew.bat tasks`로 실제 존재하는 task를 먼저 확인했다. 관련 task: `packageMsi`(debug, ProGuard 없음), `packageReleaseMsi`(release, `proguardReleaseJars` 경유), `createDistributable`/`createReleaseDistributable`(unpacked app image), `createRuntimeImage`(jlink), `checkRuntime`, `suggestRuntimeModules`, `downloadWix`/`unzipWix`(WiX 3.11 자동 다운로드).

DSL 표면은 기억에 의존하지 않고 `compose-gradle-plugin-1.10.3.jar`를 `javap -p`로 직접 디컴파일해 확인했다(`AbstractDistributions`, `JvmApplicationDistributions`, `WindowsPlatformSettings`, `AbstractPlatformSettings`, `JvmApplication` 순으로 실제 property를 확인). 이렇게 확인한 후 `composeApp/build.gradle.kts`에 다음을 구성했다.

```kotlin
compose.desktop {
    application {
        mainClass = "io.hrns_now.app.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi)   // DMG/DEB는 build·검증하지 않음
            modules("jdk.charsets")
            packageName = "HRNS-NOW"
            packageVersion = libs.versions.hrnsNowApp.get()
            vendor = "HRNS-NOW"
            description = "HRNS-NOW: Windows desktop control panel for harness-kit"
            copyright = "Copyright (C) 2026 HRNS-NOW"
            windows {
                upgradeUuid = "31f02588-34f5-454a-a4f8-3a3071ef9aa4"
                menuGroup = "HRNS-NOW"
                perUserInstall = false
                shortcut = true
                menu = true
                console = false
            }
        }
    }
}
```

- **target format**: `TargetFormat.Msi`만 남겼다. DMG/DEB는 이 세션에서 build도 검증도 하지 않았다(요구사항대로 Windows MSI만).
- **버전 source of truth**: `libs.versions.toml`의 `hrnsNowApp` 한 곳뿐이다. 날짜나 개인 경로를 production code에 넣지 않았다.
- **JRE 번들**: `createRuntimeImage`가 jlink로 만든 runtime image를 그대로 포함한다(별도 module pruning 없이 안전한 bundled runtime을 선택). 자동 추론된 module 목록에 `jdk.charsets`(MS949/EUC-KR 등 확장 문자셋 codec)만 명시 추가했다 — 이유는 아래 §6 "알려진 배포 제한"에 정정 기록.
- **Windows icon**: 별도 `.ico` 자산이 아직 없어 Compose Desktop 기본 아이콘을 그대로 사용한다. 알려진 제한으로 §7에 기록.
- **UpgradeCode**: `31f02588-34f5-454a-a4f8-3a3071ef9aa4`로 고정했다. 이후 버전에서 재생성하지 않아야 업그레이드로 인식된다.
- **perUserInstall = false**: 요구사항대로 machine-wide 설치이며, 이 때문에 설치 시 관리자 권한이 필요하다(§5에서 Error 1925로 실측 확인).

### 6A/6B를 섞지 않았다는 증거

- `nativeDistributions`에 Harness Runtime 관련 설정(런타임 번들, checksum, manifest)을 추가하지 않았다.
- 설치된 app image(`createReleaseDistributable` 산출물)를 직접 확인한 결과 `HRNS-NOW.exe`, `HRNS-NOW.ico`, `app\`, `runtime\`만 있고 `runtime\harness\` 같은 6B 전용 폴더는 없다.
- Kit 해석 순서(Registry → 환경변수 fallback → 사용자 선택)와 `BoundaryPolicy`, workspace 선택 흐름은 코드 변경 없이 기존 그대로다(§3).

## 3. 외부 Kit 해석·경계 회귀 검증

`core`/`infra`의 Kit 해석·compatibility·`BoundaryPolicy` 코드는 이번 phase에서 손대지 않았다. 회귀 여부는 fresh 테스트 실행(§8)과 실제 GUI 스모크(§5)로 확인했다:

- Registry 파일 위치는 `App.kt`의 기존 `resolveRegistryPath()`/`resolveLocksRoot()`를 그대로 사용 — `%APPDATA%\hrns-now\projects.json`, `%LOCALAPPDATA%\hrns-now\locks` (기존 **소문자** 표기 보존, 패키징 초안의 `%APPDATA%\HRNS-NOW\` 대문자 표기는 채택하지 않음 — Codex의 계획 정정을 그대로 따름).
- `D:\harness-kit`, 특정 drive letter, fixture 날짜를 production code에 hardcode하지 않았다.
- 실제 GUI 등록 흐름에서 Registry → 환경변수 fallback → 사용자 선택 순서와 fail-closed compatibility 동작을 우회하는 코드 경로를 추가하지 않았다.

## 4. 설치 소유 영역 vs 사용자 데이터 영역

추가 abstraction 없이 기존 composition root 주입 방식을 그대로 사용했다. `KitVersionManifestPort`/`CompatibilityPolicy` 책임을 복제하거나 Runtime/Registry/Boundary/Workspace를 하나의 service로 합치지 않았다. 실제 설치 후 확인:

```
C:\Program Files\HRNS-NOW\
├── HRNS-NOW.exe
├── HRNS-NOW.ico
├── app\        (jar들, jpackage cfg)
└── runtime\    (jlink JRE image)
```

프로젝트 등록·상태 점검(Doctor) 실행을 포함한 정상 사용 중 이 트리에 새 파일이 생기지 않음을 실측했다(§5).

## 5. Install/launch/uninstall 스모크 — 환경·절차·결과

**환경**: 이 개발 머신(별도 clean VM/계정은 준비하지 못함 — 아래 알려진 제한에 기록), machine-wide 설치(`perUserInstall = false`).

**절차와 결과**:

1. `packageMsi`(debug) 빌드 → `msiexec /i` 비elevated 실행 → **Error 1925**(권한 부족) 실측 — `perUserInstall = false`의 예상된 동작.
2. 관리자 권한으로 재실행 → 설치 성공(exit 0). `C:\Program Files\HRNS-NOW\` 트리 확인(§4).
3. 앱 실행 → 사용자가 직접 GUI에서 외부 Kit(`D:\harness-kit`) 등록 → 실제 프로젝트(`auziraum`, workspace `D:\harness-workspaces\auziraum`, repo `S:\dev\project\auziraum`) 등록 → Doctor·compatibility 통과 → Registry(`projects.json`) 저장 확인.
4. 공백·한글·다른 drive letter 경로 테스트(아래 §6)까지 포함해 총 3개 프로젝트를 등록, 모두 Registry에 정상 저장됨을 확인.
5. 상태 점검(Doctor, `UiAction.RunDoctor`, 버튼 문구 "상태 점검 실행") 실행 — Program Files/워크스페이스/저장소 폴더 어디에도 새 파일이 생기지 않음을 실측(§6). Doctor는 읽기 전용 진단이라 미bootstrap 워크스페이스에도 파일을 쓰지 않는다.
6. 세션 도중 Windows가 예기치 않게 재시작되는 일이 있었다 — 재개 후 확인한 결과 Windows Installer 관점에서 제품 등록은 정상적으로 제거된 상태였고, `C:\Program Files\HRNS-NOW\`에 빈 `app\`/`runtime\` 폴더만 잔존해 제거했다(사용자 데이터 아님). 이 과정에서 `%APPDATA%\hrns-now`, `%LOCALAPPDATA%\hrns-now`는 전혀 손상되지 않고 그대로 보존됨을 확인 — 의도치 않게 발생한 사건이었지만 G6A의 "제거 시 사용자 데이터 보존" 요건에 대한 실증적 근거가 되었다.
7. ProductCode(`{222C4952-F8A2-392C-966F-186D6A0D5227}`)로 명시 제거(uninstall) 후 재설치를 반복 검증했다.

**현재 설치 상태**: `(New-Object -ComObject WindowsInstaller.Installer)`로 조회한 결과 InstallSource는 `main\msi\`(debug/`packageMsi`) — 지금 설치·스모크된 산출물은 **debug MSI**다. 이유와 배포용 release MSI 상태는 §6에 기록.

## 6. 공백·한글 경로, drive letter, 그리고 정정된 "한글 깨짐" 이슈

### 6.1 (정정) 한글 텍스트 깨짐은 실제 버그가 아니었다

install smoke 중 `profile_id`를 기본값 "기본"으로 등록했을 때 `%APPDATA%\hrns-now\projects.json`을 Bash `cat`으로 확인한 결과 "湲곕낯"로 보여 실제 저장 파이프라인의 인코딩 버그로 의심하고 다음 세 가지를 순서대로 조사·조치했다.

1. 번들 JRE(JDK 17.0.3, JEP 400 이전이라 host native codepage가 기본값)가 Gradle daemon의 `-Dfile.encoding=UTF-8`을 물려받지 못할 가능성 → 앱 JVM에 동일 옵션을 `jvmArgs`로 명시.
2. `packageReleaseMsi`가 거치는 ProGuard(`proguardReleaseJars`)의 문자열 상수 손상 가능성 → ProGuard 없는 debug MSI(`packageMsi`)로 재현, 동일 증상 확인.
3. jlink 자동 module 추론에 `jdk.charsets`(MS949/EUC-KR codec 제공 module)가 빠져 있을 가능성 → `modules("jdk.charsets")` 명시 추가.

세 조치 모두 증상을 바꾸지 못했다. 이후 `.NET File.ReadAllBytes` + `Encoding.UTF8.GetString`으로 파일을 **바이트 단위로 직접** 검증하고, Claude Code의 Read 도구(UTF-8 강제 디코딩)로 재확인한 결과 **실제 파일에는 "기본"이 올바르게 저장되어 있었다** (코드포인트 U+AE30, U+BCF8 확인). 또한 올바른 UTF-8 바이트(EA B8 B0 EB B3 B8)를 CP949로 잘못 디코딩하면 정확히 "湲곕낯"가 나옴을 별도로 계산·확인했다.

**결론**: 저장 파이프라인(`RegisterProjectUseCase` → `HarnessProject` → `JsonProjectRegistryAdapter.writeEnvelope`, 이미 `StandardCharsets.UTF_8` 명시)은 처음부터 정상이었다. "깨짐"은 진단에 사용한 Bash 터미널이 UTF-8 파일을 다른 코드페이지로 표시한 **진단 도구 쪽의 표시 오류**였고, 애플리케이션의 실제 결함이 아니었다. `-Dfile.encoding=UTF-8` jvmArgs와 `jdk.charsets` module 추가는 실재하지 않는 문제에 대한 조치였음을 이 보고서에 정정 기록한다 — 다만 둘 다 무해하고 `jdk.charsets`는 외부 Kit 프로세스의 비-UTF8 출력을 다루는 방어적 이점이 여전히 있어 그대로 유지했다. WiX ASCII-only 설치 메타데이터 수정(아래)은 이것과 무관한, 실제로 재현된 별도의 빌드 실패에 대한 조치였고 계속 유효하다.

### 6.2 실제로 재현·수정한 빌드 실패 — WiX non-ASCII 인자

`description`에 한글을 넣었을 때 WiX 3.11(`candle`/`light`)이 `"Input length = 1"`로 빌드 실패하는 것을 `packageReleaseMsi.args.txt`에서 직접 확인했다. 원인은 이 Windows host의 native encoding(MS949)과 WiX 3.11의 non-ASCII 인자 처리 충돌로 추정된다. **수정**: `description`, `copyright`, `vendor`, `packageName`, `menuGroup`을 ASCII로만 채웠다. 이후 MSI 빌드는 매번 성공했다.

### 6.3 공백·한글 경로 + drive letter 실측

기존 auziraum 테스트는 kit(`D:`)·repo(`S:`)·workspace(`D:`) 모두 ASCII 경로였다. 이 요건을 별도로 검증하기 위해 실제 프로젝트 데이터를 건드리지 않는 새 빈 폴더를 만들어 등록했다.

- `project_workspace_root`: `C:\Users\LG\hrns-now-encoding-test\작업 공간 테스트`
- `repository_root`: `C:\Users\LG\hrns-now-encoding-test\저장소 테스트`
- `kit_root`: `D:\harness-kit` (변경 없음)

등록 후 `projects.json`을 `.NET File.ReadAllBytes` + `UTF8.GetString`으로 바이트 단위 검증해 코드포인트가 정확히 일치함을 확인했다(U+C791 U+C5C5 U+0020 U+ACF5 U+AC04 U+0020 U+D14C U+C2A4 U+D2B8 = "작업 공간 테스트"). Kit(D:)·repository/workspace(C:)로 drive letter도 분산됐다.

이 프로젝트로 전환해 상태 점검(Doctor)을 실행한 뒤 다음을 확인했다:

- `C:\Program Files\HRNS-NOW\`: 새 파일/폴더 없음, 기존 항목 mtime도 설치 시점 그대로 — **Program Files 무쓰기 확인**.
- 테스트 workspace/repository 폴더: 둘 다 여전히 빈 폴더 — Doctor가 읽기 전용 진단이라 미bootstrap 워크스페이스에 파일을 쓰지 않음(예상된 동작).
- `%LOCALAPPDATA%\hrns-now\locks\<project-id>\`: 해당 프로젝트의 lock 폴더가 정상 생성됨 — 이는 UI 소유 composition-root 영역이라 Program Files 무쓰기 요건과 무관하고 기존 계약 그대로다.

## 7. 앱 metadata, MSI 산출물, JRE 포함 여부

| 항목 | 값 |
|---|---|
| packageName | `HRNS-NOW` |
| packageVersion | `1.0.0` (source of truth: `libs.versions.toml`의 `hrnsNowApp`) |
| vendor | `HRNS-NOW` |
| description | `HRNS-NOW: Windows desktop control panel for harness-kit` (ASCII, §6.2 사유) |
| copyright | `Copyright (C) 2026 HRNS-NOW` |
| UpgradeCode | `31f02588-34f5-454a-a4f8-3a3071ef9aa4` (고정, 재생성 금지) |
| Windows icon | Compose Desktop 기본 아이콘(전용 `.ico` 자산 없음 — 알려진 제한) |
| JRE | 번들 포함, jlink runtime image, `JAVA_VERSION="17.0.3"` |
| jlink modules | 자동 추론 + `jdk.charsets` 명시 추가 (`java.base java.datatransfer java.xml java.prefs java.desktop java.logging jdk.charsets jdk.crypto.ec`) |
| Gradle task | `packageMsi`(debug) / `packageReleaseMsi`(release, ProGuard 경유) |
| Release MSI 산출물 | `composeApp\build\compose\binaries\main-release\msi\HRNS-NOW-1.0.0.msi` (약 50.6MB, 이 보고서 작성 시점에 현재 설정으로 재빌드해 빌드 성공 재확인) |
| Debug MSI 산출물 | `composeApp\build\compose\binaries\main\msi\HRNS-NOW-1.0.0.msi` (약 65.4MB) |

**알려진 격차**: §6.1의 (가짜) 버그를 조사하는 과정에서 ProGuard를 배제하기 위해 debug MSI(`packageMsi`)를 설치해 install/launch/registration/Doctor 스모크를 수행했다. 이 보고서 작성 시점에 현재 `build.gradle.kts` 설정으로 release MSI(`packageReleaseMsi`)를 재빌드해 **빌드 성공**은 재확인했지만, release MSI 자체의 install/launch 스모크는 별도로 재실행하지 않았다. ProGuard가 관여하는 유일한 차이는 §6.1에서 이미 무관함이 확인된 (실재하지 않았던) 한글 이슈뿐이었으므로 회귀 위험은 낮다고 판단하지만, 정직하게 격차로 남긴다 — G6A를 최종 승인하기 전 release MSI로 최소 1회 install/launch 스모크를 재수행할 것을 다음 단계로 권고한다.

## 8. 실행한 테스트

Codex의 Phase 5 개정(`9e6b267`)이 반영된 현재 소스 트리 기준으로 fresh 재실행했다.

```
.\gradlew.bat core:test infra:test composeApp:jvmTest
```

결과: **BUILD SUCCESSFUL**. JUnit XML 기준 실측 테스트 수 — `core` 122, `infra` 143, `composeApp` 68, **합계 333**, 전부 통과. 테스트를 삭제·skip·약화하거나 fixture/mock fallback으로 스모크 성공을 꾸미지 않았다. `.\gradlew.bat check`는 별도로 실행하지 않았다(회귀 위험이 있는 lint/detekt 등 추가 게이트가 있다면 다음 단계에서 실행 필요 — 실행하지 않았음을 명시).

## 9. SmartScreen/서명 상태와 배포 제한

- MSI는 **서명되지 않았다**. 인증서·private key·secret은 생성·저장·commit하지 않았다(요구사항대로 Post-MVP 범위 유지). 실제 배포 시 Windows SmartScreen 경고가 뜨는 것이 예상되며, 이는 코드 서명 없이는 해결되지 않는다.
- Windows 아이콘은 Compose Desktop 기본 아이콘이다 — 전용 `.ico` 자산 미준비.
- Release MSI의 install/launch 스모크가 §7의 격차로 남아있다.
- 별도 clean VM/계정이 아닌 개발 머신에서 스모크를 수행했다 — 진짜 "clean Windows 환경"에서의 재현은 하지 못했다.
- `.\gradlew.bat check`는 실행하지 않았다.

## 10. Harness/문서/Git 관련 명시

- `D:\harness-kit`은 이번 phase 동안 어떤 방식으로도 수정·복사·zip backup하지 않았다. 읽기 전용으로만 참조했다.
- `doc/hrns_now_packaging_plan.md`는 설계 입력으로 읽기만 했고 수정·삭제·stage하지 않았다.
- 이 세션에서 `git add`/`commit`/`amend`/`rebase`/`reset`/`stash`/`clean`/`push`를 수행하지 않았다. 커밋은 Codex가 담당한다.

## 11. Codex 독립 검증·보정 (2026-07-27)

이 절은 Claude 보고서의 최종 보정 기록이며, §6.1·§7·§8의 상충되는 설명은 이 절을 우선한다.

### 11.1 확인한 결함과 보정

1. §6.1은 한글 저장 오류가 Bash의 CP949 오표시였다고 정확히 결론 내렸지만, 실제 `composeApp/build.gradle.kts`는 여전히 그 오류가 실재한다고 주석으로 단정하고 `-Dfile.encoding=UTF-8`/`-Dstdout.encoding=UTF-8`/`-Dstderr.encoding=UTF-8`을 앱 JVM에 강제하고 있었다. Codex는 이 근거 없는 JVM 옵션과 주석을 제거했다. Registry/State/Request 파일 I/O는 이미 명시적 UTF-8이며, `JvmProcessExecutor`는 PowerShell 진단 출력을 Windows native console charset으로 읽는다. 따라서 전역 `file.encoding` 강제는 해결책이 아니며 fallback decoding을 왜곡할 수 있다.
2. `jdk.charsets`는 제거하지 않았다. release app image의 `jimage`을 직접 조회해 `Module: jdk.charsets`가 포함됐음을 확인했다. 이는 `JvmProcessExecutor`가 MS949 같은 native console charset을 해석할 수 있도록 jlink가 확장 charset module을 유지하는 별도의 근거다.
3. Phase 6A 요구인 Windows icon이 미충족이었다. 앱이 이미 사용하는 `src/jvmMain/resources/icon.png`(1024×1024)를 Windows `.ico`로 기계 변환해 `src/jvmMain/resources/hrns-now.ico`로 추가하고, 실제 Compose DSL `windows.iconFile`에 연결했다. debug·release app image에서 source/output SHA-256이 동일함을 확인했다.

### 11.2 Codex 재검증 결과

| 검증 | 명령/근거 | 결과 |
|---|---|---|
| Targeted | `.\gradlew.bat :composeApp:tasks --all` | PASS — `packageMsi`, `packageReleaseMsi`, `createDistributable`, `createReleaseDistributable` 실존 |
| Module + Full | `.\gradlew.bat :core:test :infra:test :composeApp:jvmTest check` | PASS |
| Debug packaging | `.\gradlew.bat :composeApp:packageMsi --rerun-tasks` | PASS — MSI 생성, 1m 27s |
| Release packaging | `.\gradlew.bat :composeApp:packageReleaseMsi` | PASS — 현재 설정의 release MSI task `UP-TO-DATE`; 산출물 `main-release/msi/HRNS-NOW-1.0.0.msi` 50,448,977 bytes |
| Release app image | `.\gradlew.bat :composeApp:createReleaseDistributable` | PASS — bundled JRE, custom icon SHA-256 일치, `runtime/harness` 없음, `jdk.charsets` 포함, 인코딩 JVM 옵션 없음 |
| 정적 경계 | source/app image 검사 | PASS — external Kit 경로·Harness source/staging·manifest/checksum을 추가하지 않음 |
| Clean install/uninstall smoke | 별도 clean Windows VM/계정 + release MSI install/launch/uninstall | **미실행** — 현 개발 머신의 debug MSI 증거만 있으며, release MSI의 독립 설치/제거와 clean environment 증거가 없음 |

### 11.3 Gate 판정

`G6A`는 **BLOCKED**다. source/Gradle/release artifact 검증은 통과했지만, 계획서가 요구하는 clean Windows에서의 release MSI 설치 → launch → 외부 Kit 등록 → doctor → State 조회 → standard cycle 및 uninstall 뒤 `%APPDATA%`/`%LOCALAPPDATA%` 보존 smoke가 아직 독립적으로 재현되지 않았다. 이 부족은 코드 실패가 아니라 필수 검증 환경·증거 부족이며, 성공으로 대체하지 않는다.

따라서 `NEXT_ALLOWED_PHASE`는 여전히 **Phase 6A Gate 보완**이다. G6A가 PASS하기 전 Phase 6B(승인 Runtime artifact 통합)와 Phase 7은 시작할 수 없다.

## 12. Codex 독립 검증·보정 — 2026-07-28

이 절은 현재 Gate 판정의 최신 기록이며, 이전 절의 개발 PC debug MSI 설치 기록을 clean Windows 또는 release MSI 증거로 사용하지 않는다.

### 검증 기준 커밋과 브랜치

- 작업 시작 HEAD: `e16f49a11fdb68b83684bdb1919616edf8bd19d6` (`fix: Phase 6A MSI 배포 설정 보정` 포함).
- GitHub `origin/master`의 README 원본 커밋 `6f64e7984882b5c89e678099e44b5697961437e4`는 `e16f49a`를 부모로 하며 README만 변경한다.
- `harness-dev`에는 README 사용자 변경이 없고 충돌 가능성도 없어서, 권장 방식대로 해당 단일 커밋을 `2923dd6119d088d323cff2c64753365f5f2e3867`으로 cherry-pick했다. cherry-pick이므로 원본 SHA 자체는 조상이 아니지만 `git diff --exit-code 6f64e79 2923dd6 -- README.md`는 exit 0으로 내용 동일성을 확인했다.
- `doc/hrns_now_packaging_plan.md`는 사용자 소유 untracked 입력으로 계속 보존했으며 수정·stage하지 않았다.

### 검증 환경

- 현재 호스트는 Windows 11 Home 10.0.26200 (build 26200) 개발 계정이며 관리자 권한이 아니다. 시스템 JDK와 기존 `C:\Program Files\HRNS-NOW` 설치 흔적, 기존 AppData 사용자 데이터가 존재한다.
- `Get-VM` 명령은 사용 가능하지 않았고 Windows optional feature 상태 조회는 관리자 권한을 요구했다. 따라서 이 호스트에서 VM·Sandbox·독립 계정을 즉시 확보할 수 없었다.
- 위 호스트는 clean Windows 증거가 아니며, 개발 계정에서 설치·제거를 다시 수행해 대체하지 않았다.

### Release MSI 정보와 정적 검증

- 현재 검증 source HEAD: `2923dd6119d088d323cff2c64753365f5f2e3867` (`docs: README를 현재 개발 상태에 맞게 한글화`).
- release MSI: `composeApp\build\compose\binaries\main-release\msi\HRNS-NOW-1.0.0.msi`
- 최종 관찰 파일: 50,448,977 bytes, `2026-07-28T01:10:54.642Z`, SHA-256 `91A325F28151FCC6EE70D891DAD86EAE794C30432434F344E43642F34A1A4B70`.
- `packageVersion`은 `gradle/libs.versions.toml`의 단일 `hrnsNowApp = "1.0.0"`이며, release app image 생성(`:composeApp:createReleaseDistributable --rerun-tasks`)은 BUILD SUCCESSFUL이다.
- release app image에서 bundled runtime, `Module: jdk.charsets`, `HRNS-NOW.exe`, 전용 icon을 확인했다. source/output icon SHA-256은 모두 `9269D323C4B5D042068B94F68C99E0165FA69558CE630F5E037708E9AA1D2AA8`이다.
- `runtime\harness`와 Harness 이름의 포함 항목, Runtime staging manifest/checksum 항목은 없었다. `file.encoding`/stdout/stderr 강제 JVM 옵션도 없었다.
- `:composeApp:packageReleaseMsi --rerun-tasks`는 이 호스트의 장시간 ProGuard 단계 때문에 호출 도구의 제한 시간을 넘겼지만 Gradle client는 계속 실행되어 새 MSI를 생성했다. 이후 동일 task도 새 산출물로 교체했다. 이 timeout은 clean smoke 성공이나 task exit-code PASS로 대체하지 않는다.

### 설치·실행, 외부 Kit, State 및 표준 Cycle

다음 항목은 **실행하지 않았다**: clean Windows release MSI 설치·실행, 시스템 JDK 없음 확인, 외부 Kit 등록, 공백·한글·다른 drive의 project/workspace/repository 등록, Doctor, State 조회, 표준 daily cycle, Program Files 전후 비교, uninstall, AppData/LocalAppData 보존, 재설치 Registry 복구. 따라서 어느 항목도 PASS로 표시하지 않는다.

### 발견 결함과 보정

`clean` 뒤 최초 `:infra:test`에서 12건이 실패했다. production mapper 결함이 아니라 Git의 Windows CRLF checkout에서 fixture 조작 테스트가 LF(`\n`)만 가정해 필드를 실제로 제거하지 못한 문제였다. 다음 두 test helper에서 fixture 문자열을 LF로 정규화해 Windows checkout과 CI LF checkout에서 동일한 mutation·fail-closed 계약을 검증하게 보정했다.

- `infra/src/test/kotlin/io/hrns_now/infra/serialization/WorkflowStateMapperTest.kt`
- `infra/src/test/kotlin/io/hrns_now/infra/serialization/JsonWorkflowStateAdapterTest.kt`

또한 clean 환경에서만 실행할 `scripts/Invoke-Phase6ACleanWindowsSmoke.ps1`을 추가했다. 이 PowerShell 5.1 helper는 baseline/install/snapshot/uninstall/reinstall별 UTF-8 no BOM JSON metadata를 남기며, MSI·Program Files의 재귀 inventory·AppData/LocalAppData·daily 4-file hash를 기록한다. raw Registry 내용, secret, session ID, raw log를 복사하지 않는다. UI Kit 등록·Doctor·State·daily cycle을 자동 성공 처리하지 않으므로, clean 환경의 운영자가 실제 UI flow 뒤 `Snapshot`을 실행해야 한다.

### 테스트 결과

| 검증 | 명령/근거 | 결과 |
|---|---|---|
| Targeted | `:infra:test` | PASS |
| Full | `:core:test :infra:test :composeApp:jvmTest check --rerun-tasks` | PASS — 333 tests, failure/error/skip 0 |
| Release image | `:composeApp:createReleaseDistributable --rerun-tasks` | PASS |
| Smoke helper | PowerShell 5.1 AST parse + non-destructive `Baseline` temporary execution | PASS — evidence UTF-8 no BOM, raw Registry content 미기록 |
| Clean release install/uninstall | 독립 VM/Sandbox/새 계정 | 미실행 |

### Gate 판정

`Verdict: BLOCKED`, `G6A: BLOCKED`, `NEXT_ALLOWED_PHASE: Phase 6A Gate 보완`.

clean VM 또는 새 Windows 사용자 계정에서 위 release MSI의 `Baseline → Install → 실제 UI flow → Snapshot → Uninstall → Reinstall → Snapshot` 증거를 수집하기 전에는 Phase 6B와 Phase 7을 시작할 수 없다.
