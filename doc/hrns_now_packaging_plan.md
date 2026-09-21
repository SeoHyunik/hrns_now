# HRNS-NOW 패키징 및 Bundled Harness Runtime 계획서 — 초안

- **문서명:** `hrns_now_packaging_plan.md`
- **작성일:** 2026-07-23
- **문서 상태:** `DRAFT`
- **적용 대상:** `hrns_now` + `harness-kit`
- **목표 플랫폼:** Windows
- **우선 배포 형식:** MSI
- **장기 목표:** 라이선스/CD Key가 적용된 단일 제품형 HRNS-NOW 배포
- **변경 가능성:** 높음 — `hrns_now`와 `harness-kit`의 구현 진척 및 계약 변경에 따라 Phase별 재검토

---

# 0. 문서 목적

이 문서는 HRNS-NOW를 하나의 Windows 제품으로 패키징할 때 다음 요소를 어떻게 구성할지 정의하는 초안이다.

1. Kotlin/Compose 기반 HRNS-NOW 애플리케이션
2. 제품 내부에 포함되는 Harness Runtime
3. 사용자가 선택한 대상 Repository
4. Repository별로 자동 생성되는 Harness Workspace
5. 사용자 설정·라이선스·잠금·로그
6. 패키징, 설치, 제거, 업데이트, 복구
7. 향후 CD Key 및 상용 배포 확장

핵심 원칙은 다음과 같다.

> **HRNS-NOW 설치물에는 실행에 필요한 Harness Runtime을 포함하되, Repository와 Workspace 및 사용자 데이터는 설치 디렉터리 밖에 분리한다.**

---

# 1. 최종 제품 개념

## 1.1 제품 정의

HRNS-NOW는 Harness Kit을 단순히 실행하는 UI가 아니라 다음 역할을 수행하는 Windows Desktop Control Plane이다.

- 사용자가 대상 프로젝트를 등록
- 프로젝트별 Workspace 자동 생성
- Harness Runtime의 호환성과 무결성 검사
- `WORKFLOW_STATE.json`을 상태 진실로 읽음
- 현재 허용된 행동만 UI에 노출
- Harness PowerShell entrypoint를 typed command로 실행
- 실행 결과와 Workspace 산출물을 시각화
- 프로젝트·날짜별 작업 이력을 안전하게 보존
- 향후 라이선스 활성화에 따라 기능 사용 제어

## 1.2 최종 배포 이미지

```text
HRNS-NOW Installer
├── HRNS-NOW Application
├── Bundled Java Runtime
├── Bundled Harness Runtime
├── Runtime Manifest
├── Runtime Checksums
├── 기본 설정·리소스
└── 제거 프로그램
```

사용자는 Harness Kit을 별도로 설치하거나 경로를 입력하지 않는 것을 기본 UX로 한다.

---

# 2. 최상위 아키텍처

```text
┌─────────────────────────────────────────────────────┐
│                HRNS-NOW 설치 영역                   │
│                                                     │
│  HRNS-NOW.exe                                       │
│       │                                             │
│       ├── Kotlin/Compose UI                         │
│       ├── Domain Policy                             │
│       ├── Harness Adapter                           │
│       └── Bundled Harness Runtime                   │
└──────────────────────────┬──────────────────────────┘
                           │ typed PowerShell command
                           ▼
┌─────────────────────────────────────────────────────┐
│                  대상 Repository                    │
│  사용자가 UI에서 선택한 실제 Git 프로젝트           │
└──────────────────────────┬──────────────────────────┘
                           │ 프로젝트별 상태·로그
                           ▼
┌─────────────────────────────────────────────────────┐
│             사용자 로컬 Harness Workspace           │
│  프로젝트별 폴더 / 날짜별 4-file / wrapper 로그     │
└─────────────────────────────────────────────────────┘
```

---

# 3. 반드시 분리해야 하는 세 경로

## 3.1 Harness Runtime Root

설치된 Harness 실행 엔진의 위치다.

```text
C:\Program Files\HRNS-NOW\runtime\harness
```

포함:

```text
run-cycle.ps1
doctor.ps1
validate-ops.ps1
scripts\
manifest.json
checksums.sha256
```

특성:

- Installer 소유
- 일반 사용 중 수정 금지
- 읽기 및 실행 중심
- Workspace·로그·사용자 설정 저장 금지
- 업데이트 시 버전 단위 교체

## 3.2 Repository Root

사용자가 UI에서 선택한 실제 개발 프로젝트다.

예:

```text
S:\dev\project\auziraum
```

특성:

- 사용자 또는 Git 소유
- Harness가 허가된 범위에서 수정할 수 있음
- HRNS-NOW 설치 디렉터리와 분리
- Workspace 내부에 포함되면 안 됨
- Workspace가 Repository 내부에 있어도 안 됨

## 3.3 Project Workspace Root

대상 프로젝트의 Harness 상태·계획·인계·로그가 저장되는 위치다.

기본 위치:

```text
%LOCALAPPDATA%\HRNS-NOW\workspaces
```

특성:

- 프로젝트 등록 시 자동 생성
- 프로젝트별 독립 폴더
- 날짜별 Harness 4-file 구조 유지
- 앱 제거 시 기본 보존
- Repository와 별도
- Harness Runtime과 별도

---

# 4. 설치 후 디렉터리 구조

## 4.1 프로그램 설치 영역

```text
C:\Program Files\HRNS-NOW\
├── HRNS-NOW.exe
├── app\
│   ├── 애플리케이션 파일
│   └── bundled Java runtime
├── runtime\
│   └── harness\
│       ├── bin\
│       │   ├── run-cycle.ps1
│       │   ├── doctor.ps1
│       │   └── validate-ops.ps1
│       ├── scripts\
│       ├── docs\
│       ├── manifest.json
│       └── checksums.sha256
├── resources\
└── uninstaller\
```

실제 JPackage 결과 구조는 달라질 수 있으나 논리적 역할은 유지한다.

## 4.2 사용자 Roaming 설정

```text
%APPDATA%\HRNS-NOW\
├── projects.json
├── settings.json
├── ui-preferences.json
└── license\
    └── activation.json
```

금지:

- Harness 원본 로그
- raw session ID
- secret
- API key
- 전체 Claude 응답

## 4.3 사용자 Local 데이터

```text
%LOCALAPPDATA%\HRNS-NOW\
├── workspaces\
├── locks\
├── logs\
├── cache\
├── runtime-state\
└── updates\
```

---

# 5. 프로젝트별 Workspace 자동 생성

## 5.1 기본 흐름

```text
Repository 선택
→ 경로 정규화
→ Git 또는 지원 프로젝트 검사
→ 표시명 제안
→ Project ID 생성
→ Workspace 경로 계산
→ 경계 검사
→ Workspace 생성
→ Harness Runtime 검사
→ doctor 실행
→ Registry 저장
→ 필요 시 오늘 날짜 bootstrap
→ Cockpit 표시
```

## 5.2 Workspace 폴더명

사용자에게는 프로젝트 이름으로 보이지만 실제 경로는 `표시명 + 짧은 ID`를 사용한다.

```text
auziraum--8f31a2c4
```

이유:

- 같은 이름의 프로젝트 구분
- 프로젝트 경로 변경 대응
- 프로젝트명 충돌 방지
- 한글·특수문자·Windows 예약어 대응
- 삭제 후 재등록 구분

## 5.3 Project ID

```kotlin
@JvmInline
value class ProjectId(
    val value: String,
)
```

초기 생성은 UUID v4를 사용하고, 폴더명에는 UUID 앞 8자리를 표시한다.

## 5.4 프로젝트 Registry 예시

```json
{
  "schema_version": "1.0",
  "projects": [
    {
      "id": "8f31a2c4-1421-4cef-bef0-73e493cd0ef5",
      "display_name": "auziraum",
      "repository_root": "S:\\dev\\project\\auziraum",
      "workspace_root": "C:\\Users\\user\\AppData\\Local\\HRNS-NOW\\workspaces\\auziraum--8f31a2c4",
      "profile": "corp-springboot",
      "created_at": "2026-07-23T10:00:00+09:00",
      "last_opened_date": "2026-07-23",
      "last_runtime_version": "1.0.0"
    }
  ]
}
```

---

# 6. Workspace 내부 구조

## 6.1 권장 구조

```text
%LOCALAPPDATA%\HRNS-NOW\workspaces\
└── auziraum--8f31a2c4\
    ├── 2026-07-23\
    │   ├── REQUEST_INBOX.md
    │   ├── TODAY_STRATEGY.md
    │   ├── DAILY_HANDOFF.md
    │   ├── WORKFLOW_STATE.json
    │   └── logs\
    ├── 2026-07-24\
    │   ├── REQUEST_INBOX.md
    │   ├── TODAY_STRATEGY.md
    │   ├── DAILY_HANDOFF.md
    │   ├── WORKFLOW_STATE.json
    │   └── logs\
    └── logs\
        ├── 2026-07-23\
        ├── 2026-07-24\
        ├── claude-session-continuity\
        └── usage-ledger\
```

## 6.2 현행 Harness 계약 유지

필수 daily surface:

```text
<projectWorkspaceRoot>\<yyyy-MM-dd>\REQUEST_INBOX.md
<projectWorkspaceRoot>\<yyyy-MM-dd>\TODAY_STRATEGY.md
<projectWorkspaceRoot>\<yyyy-MM-dd>\DAILY_HANDOFF.md
<projectWorkspaceRoot>\<yyyy-MM-dd>\WORKFLOW_STATE.json
```

Optional:

```text
<dayRoot>\REQUEST_STRUCTURED.md
<dayRoot>\logs\
<projectWorkspaceRoot>\logs\<yyyy-MM-dd>\
```

Legacy:

```text
WORKDAY_STATE.json
WORK_QUEUE.json
```

Legacy 파일은 기본 UI에서 숨기고 정상 readiness 계산에 사용하지 않는다.

## 6.3 Workspace 소유권

| 항목 | 소유자 | HRNS-NOW 권한 |
|---|---|---|
| `REQUEST_INBOX.md` | 사람 | 안전 규칙 하 작성 가능 |
| `TODAY_STRATEGY.md` | Harness | 읽기 전용 |
| `DAILY_HANDOFF.md` | Harness | 읽기 전용 |
| `WORKFLOW_STATE.json` | Harness | 절대 쓰기 금지 |
| `REQUEST_STRUCTURED.md` | Harness | 읽기 또는 숨김 |
| wrapper 로그 | Harness | 읽기 전용 |
| day 로그 | Harness | 읽기 전용 |

---

# 7. Workspace 위치 정책

## 7.1 기본 위치

```text
%LOCALAPPDATA%\HRNS-NOW\workspaces
```

장점:

- 관리자 권한 불필요
- 사용자별 분리
- 빠른 로컬 디스크
- 설치 디렉터리와 분리
- 앱 제거 후 보존 가능

## 7.2 사용자 지정 위치

고급 설정으로 다음을 허용할 수 있다.

```text
기본 위치 사용
C:\Users\<사용자>\AppData\Local\HRNS-NOW\workspaces

사용자 지정 위치
D:\HRNS-Workspaces
```

## 7.3 금지 경로

다음은 등록을 차단한다.

- Workspace가 Repository 내부
- Repository가 Workspace 내부
- Workspace가 Harness Runtime 내부
- Harness Runtime이 Workspace 내부
- Repository가 Harness Runtime 내부
- Harness Runtime이 Repository 내부
- 둘 이상의 경로가 동일
- junction/symlink 해석 후 경계 겹침

예:

```text
S:\dev\project\auziraum\.hrns-workspace
```

---

# 8. Workspace 생성 정책

## 8.1 생성 시점

- Project Workspace Root: 프로젝트 등록 승인 시
- 날짜 폴더: 오늘 작업 시작, Bootstrap 실행, REQUEST 작성 시작 시
- 과거 프로젝트 읽기 전용 등록 시 오늘 폴더를 자동 생성하지 않을 수 있음

## 8.2 생성 책임

```text
HRNS-NOW
→ Project Workspace Root 생성
→ 경계·권한 검사
→ Harness bootstrap 호출

Harness Runtime
→ 날짜 폴더와 4-file 생성
→ State 초기화
```

HRNS-NOW가 Harness daily surface를 직접 흉내 내어 만들지 않는 것이 원칙이다.

## 8.3 부분 생성 실패

```text
Workspace Root 생성 성공
→ Harness bootstrap 실패
```

처리:

- Registry 저장 보류 또는 `setup_incomplete`
- 생성된 Workspace 경로 표시
- 재시도 제공
- 자동 삭제하지 않음
- 사용자가 삭제 여부 선택

---

# 9. Bundled Harness Runtime

## 9.1 제품 내부 명칭

Harness Kit은 최종 패키지에서 다음 명칭으로 취급한다.

> **Bundled Harness Runtime**

## 9.2 공개 entrypoint

```text
bin\doctor.ps1
bin\validate-ops.ps1
bin\run-cycle.ps1
```

금지:

- `scripts\lib` 내부 파일 직접 실행
- 내부 함수 dot-source
- 내부 파일 구조를 UI에서 직접 참조

## 9.3 Manifest

```json
{
  "runtime_name": "hrns-harness-runtime",
  "runtime_version": "1.0.0",
  "ui_contract_version": "1.0",
  "state_schema_version": "1.0",
  "minimum_powershell_version": "5.1",
  "supported_platforms": ["windows-x64"],
  "entrypoints": {
    "run_cycle": "bin/run-cycle.ps1",
    "doctor": "bin/doctor.ps1",
    "validate_ops": "bin/validate-ops.ps1"
  },
  "supported_execution_wrappers": ["none", "code", "doc", "auto"]
}
```

## 9.4 무결성

필수 파일:

```text
checksums.sha256
```

검증 실패 시:

```text
Runtime 손상
→ 모든 mutating command 잠금
→ doctor 또는 복구 화면만 제공
```

## 9.5 설치 영역 쓰기 금지

Harness Runtime은 다음 위치에 상태를 쓰지 않는다.

```text
C:\Program Files\HRNS-NOW\runtime\harness
```

모든 가변 출력은 전달된 Workspace에 기록한다.

---

# 10. Harness 경로 주입

## 10.1 제거해야 할 하드코딩

```powershell
$KitRoot = "D:\harness-kit"
$WorkspaceRoot = "D:\harness-workspaces\$ProjectName"
```

## 10.2 HRNS-NOW가 전달할 값

- Runtime Root
- Project Workspace Root
- Repository Root
- Profile
- Date
- Execution Mode
- Authorized Target

## 10.3 typed command 예

```kotlin
sealed interface HarnessCommand {
    data class BootstrapDay(
        val project: HarnessProject,
        val day: WorkspaceDay,
    ) : HarnessCommand

    data class RunPlanning(
        val project: HarnessProject,
        val day: WorkspaceDay,
    ) : HarnessCommand

    data class RunReplan(
        val project: HarnessProject,
        val day: WorkspaceDay,
    ) : HarnessCommand

    data class RunExecution(
        val project: HarnessProject,
        val day: WorkspaceDay,
        val wrapper: ExecutionWrapper,
    ) : HarnessCommand

    data class ValidateClosure(
        val project: HarnessProject,
        val day: WorkspaceDay,
    ) : HarnessCommand
}
```

---

# 11. HRNS-NOW Path 모델

```kotlin
data class HarnessRuntimePaths(
    val runtimeRoot: Path,
    val runCycleScript: Path,
    val doctorScript: Path,
    val validateOpsScript: Path,
    val manifest: Path,
    val checksums: Path,
)

data class HarnessProject(
    val id: ProjectId,
    val displayName: String,
    val repositoryRoot: Path,
    val projectWorkspaceRoot: Path,
    val profileId: String,
)

data class WorkspaceDay(
    val projectWorkspaceRoot: Path,
    val date: LocalDate,
) {
    val dayRoot: Path
        get() = projectWorkspaceRoot.resolve(date.toString())

    val wrapperLogsRoot: Path
        get() = projectWorkspaceRoot
            .resolve("logs")
            .resolve(date.toString())
}
```

Path 계산은 여러 화면이나 adapter에서 중복하지 않는다.

---

# 12. 앱 자체 데이터와 Harness Workspace 분리

## 12.1 Harness Workspace에 넣지 않을 것

```text
projects.json
settings.json
license.json
activation.json
ui.log
crash report
process lock
update cache
window state
```

## 12.2 별도 저장 위치

| 데이터 | 권장 위치 |
|---|---|
| 프로젝트 Registry | `%APPDATA%\HRNS-NOW\projects.json` |
| 앱 설정 | `%APPDATA%\HRNS-NOW\settings.json` |
| 라이선스 | `%APPDATA%\HRNS-NOW\license\` |
| lock | `%LOCALAPPDATA%\HRNS-NOW\locks\` |
| 앱 로그 | `%LOCALAPPDATA%\HRNS-NOW\logs\` |
| 업데이트 파일 | `%LOCALAPPDATA%\HRNS-NOW\updates\` |
| Workspace | `%LOCALAPPDATA%\HRNS-NOW\workspaces\` |

---

# 13. Portable 실행 고려

## 13.1 기본 정책

실행 파일을 외장 SSD 또는 USB로 옮겨 실행하더라도 데이터는 현재 PC의 AppData에 둔다.

```text
E:\Portable\HRNS-NOW.exe

데이터:
C:\Users\<사용자>\AppData\Local\HRNS-NOW\
```

## 13.2 이유

- 외장 장치 제거 중 손상 방지
- 다중 PC Workspace 충돌 방지
- 쓰기 권한 안정성
- 로그 성능
- 라이선스·기기 활성화 분리

## 13.3 향후 Portable Data Mode

```text
E:\HRNS-NOW\
├── HRNS-NOW.exe
├── runtime\
└── data\
    ├── workspaces\
    ├── registry\
    ├── locks\
    └── logs\
```

초기 MVP에는 포함하지 않는다.

---

# 14. 패키징 Source 구조

## 14.1 원격 Git에 포함

```text
hrns_now/
├── core/
├── infra/
├── composeApp/
├── packaging/
├── docs/
├── gradle/
├── build.gradle.kts
└── settings.gradle.kts
```

포함 항목:

- Harness port/interface
- typed command
- manifest model
- checksum validator
- command encoder
- Workspace manager
- Registry
- 패키징 script
- 비식별 fixture
- 계약 문서

## 14.2 원격 Git에서 제외

```text
hrns_now/
└── private/
    └── harness-kit/
```

`.gitignore` 초안:

```gitignore
# Private Harness source/runtime
/private/harness-kit/
/private/harness-runtime/
/private-artifacts/
harness-runtime-*.zip

# Packaging staging
/build/staged-runtime/
/build/private-runtime/
/build/package-input/

# Local configuration
/local.properties
/.env.local

# Runtime/user data
/runtime-data/
/harness-workspaces/
```

## 14.3 Git 유출 방지 검사

```powershell
$TrackedHarness = git ls-files 'private/harness-kit/*'

if ($TrackedHarness) {
    throw "Private Harness files must not be tracked by Git."
}
```

주의:

- `.gitignore`는 보안 도구가 아님
- `git add -f`로 강제 추가 가능
- 이미 추적된 파일은 ignore만으로 제거되지 않음
- pre-commit 또는 Gradle 검증 task 필요

---

# 15. Harness 원본 관리

## 15.1 권장 방식

원격 Git에는 올리지 않더라도 Harness 원본은 별도 로컬 Git으로 관리한다.

```text
D:\harness-kit
```

```powershell
git init
git add .
git commit -m "chore: Harness Runtime 기준선 생성"
```

remote는 연결하지 않는다.

## 15.2 이유

- Claude/Codex 변경 diff
- rollback
- 버전 tag
- Runtime package 재현
- HRNS-NOW 버전과 Harness 버전 매핑
- 패키징 전 변경 검증

## 15.3 최소 대안

```text
harness-runtime-1.0.0.zip
harness-runtime-1.0.0.sha256
harness-runtime-1.0.0-release-notes.md
```

---

# 16. Build Staging

## 16.1 원칙

개발용 Harness 폴더를 그대로 Installer에 포함하지 않는다.

```text
private/harness-kit
→ runtime 검증
→ 필요한 파일 선별
→ build/staged-runtime
→ manifest/checksum 생성
→ MSI 포함
```

## 16.2 포함 대상

- 공개 entrypoint
- 실행에 필요한 scripts/lib
- 필수 template
- 계약 문서
- manifest
- checksum

## 16.3 제외 대상

- `.git`
- 실제 Workspace
- 실행 로그
- usage ledger
- continuity 로그
- 세션 ID
- 개발용 fixture
- 임시 파일
- `*.rej`, `*.bak`
- secret, token
- IDE 설정

## 16.4 Build 실패 조건

- Harness source 경로 없음
- entrypoint 없음
- manifest 생성 실패
- checksum 실패
- 금지 파일 발견
- secret pattern 발견
- Runtime smoke 실패
- UI와 계약 버전 불일치
- Git 추적 금지 파일이 index에 존재

---

# 17. Runtime 버전 매핑

```json
{
  "hrns_now_version": "1.0.0",
  "harness_runtime_version": "1.0.0",
  "ui_contract_version": "1.0",
  "state_schema_version": "1.0",
  "build_id": "20260723-001",
  "build_timestamp": "2026-07-23T18:00:00+09:00"
}
```

UI 정보 화면:

```text
HRNS-NOW             1.0.0
Harness Runtime      1.0.0
UI Contract          1.0
State Schema         1.0
Build                20260723-001
```

---

# 18. 패키징 형식

## 18.1 MVP

```text
MSI
```

구성:

- Compose Desktop application
- bundled Java runtime
- bundled Harness Runtime
- 시작 메뉴 바로가기
- 제거 프로그램

## 18.2 EXE 용어 정리

### Installer EXE

```text
HRNS-NOW-Setup.exe
```

### Portable EXE

```text
HRNS-NOW.exe
```

초기 상품화는 MSI 또는 Setup EXE가 더 안정적이다. Portable 배포는 데이터·업데이트·라이선스 정책 확정 후 검토한다.

## 18.3 장기 후보

```text
MSI
→ Setup EXE bootstrapper
→ 자동 업데이트
→ 필요 시 MSIX 검토
```

---

# 19. 설치·제거 정책

## 19.1 설치

```text
Program Files
→ App + Java Runtime + Harness Runtime

AppData/LocalAppData
→ 첫 실행 시 사용자 데이터 생성
```

## 19.2 제거

기본 제거:

- Program Files의 HRNS-NOW
- 시작 메뉴 항목
- 설치 Registry

기본 보존:

- 프로젝트 Registry
- Workspace
- Harness 산출물
- 앱 설정
- 라이선스 정보

제거 UI 옵션:

```text
☐ 사용자 설정과 프로젝트 Workspace도 삭제
```

기본값은 체크 해제다.

## 19.3 프로젝트 등록 제거

```text
프로젝트 연결만 제거
프로젝트 연결 + Workspace 삭제
```

Workspace 삭제 시 전체 경로와 예상 용량을 표시하고 Repository는 절대 삭제하지 않는다.

---

# 20. Workspace 유지·정리 정책

## 20.1 기본 보존

모든 날짜 Workspace와 로그는 자동 삭제하지 않는다.

## 20.2 후속 정리 기능

- 프로젝트별 사용량
- 날짜별 보관 기간
- 오래된 wrapper 로그 정리
- Workspace ZIP export
- Workspace archive
- 프로젝트 제거 시 export
- 로그만 삭제
- daily state 보존

## 20.3 자동 정리 금지 대상

```text
REQUEST_INBOX.md
TODAY_STRATEGY.md
DAILY_HANDOFF.md
WORKFLOW_STATE.json
```

사용자의 명시적 동의 없이 삭제하지 않는다.

---

# 21. 실행 Lock과 외부 실행 감지

## 21.1 Lock 위치

```text
%LOCALAPPDATA%\HRNS-NOW\locks\<projectId>\<date>.lock.json
```

Harness Workspace에는 lock을 만들지 않는다.

## 21.2 Lock 범위

초기 보장:

- 동일 프로세스 내 중복 실행 차단
- 여러 HRNS-NOW 인스턴스 간 차단

외부 PowerShell 실행:

- 완전 차단이 아니라 State mtime/hash 변화 기반 감지
- “외부 실행 가능성”으로 표시
- Harness 협조 lock 계약 추가 시 강화

---

# 22. 라이선스/CD Key 확장

## 22.1 분리 원칙

```text
License Service
≠
Harness Runtime Encryption
```

CD Key는 기능 사용 권한을 판단하고 Runtime 무결성은 별도로 검증한다.

## 22.2 향후 모델

```text
CD Key 입력
→ 라이선스 서명 검증
→ 기기 활성화
→ 기능 entitlement 계산
→ HRNS-NOW 기능 활성화
```

## 22.3 라이선스 미인증 정책 후보

- 프로젝트 1개 제한
- 읽기 전용 Cockpit 허용
- Harness 실행 차단
- 체험 기간
- Export 제한

최종 정책은 상품화 단계 별도 문서로 정의한다.

---

# 23. 암호화·난독화·무결성

## 23.1 `.gitignore`

목적:

- 원격 Git에 Harness 원본이 올라가는 것 방지

제한:

- 보안 경계가 아님
- 강제 add 가능
- 로컬 유실 방지 불가

## 23.2 Harness 암호화

실행 시 복호화 방식은 가능하지만 MVP에는 권장하지 않는다.

이유:

- 앱에 복호화 로직 필요
- 키 추출 가능
- antivirus 오탐 가능성
- 실행 장애 지점 증가
- 유지보수 복잡성 증가
- 임시 복호화 파일 필요

## 23.3 우선순위

1. Runtime manifest
2. SHA-256 checksum
3. Installer 코드 서명
4. Runtime 변조 감지
5. 라이선스 서명 검증
6. 배포 채널 통제
7. 필요 시 난독화 검토

---

# 24. 업데이트 전략

## 24.1 초기

앱과 Harness Runtime을 하나의 버전으로 묶어 전체 Installer를 재배포한다.

```text
HRNS-NOW 1.1.0
+ Harness Runtime 1.2.0
→ HRNS-NOW-1.1.0.msi
```

장점:

- 호환성 단순
- 설치 상태 예측 가능
- 사용자 관리 쉬움

## 24.2 후속

Runtime side-by-side 설치 가능성:

```text
runtime\harness\
├── 1.0.0\
├── 1.1.0\
└── current.json
```

초기 MVP에서는 전체 Installer 업데이트를 우선한다.

---

# 25. 오류·복구 시나리오

## 25.1 Runtime 손상

```text
checksum mismatch
→ 실행 잠금
→ 복구 안내
→ Installer repair 또는 재설치
```

## 25.2 Workspace 권한 없음

```text
프로젝트 등록 또는 실행 차단
→ 권한 진단
→ 다른 Workspace 위치 선택
```

## 25.3 Repository 이동

```text
기존 경로 없음
→ 연결 끊김 표시
→ 새 경로 선택
→ Project ID·Workspace 유지
```

## 25.4 Workspace 이동

```text
새 위치 선택
→ atomic migration
→ 검증
→ 성공 후 Registry 갱신
```

## 25.5 앱 제거 후 재설치

```text
기존 AppData 발견
→ Registry 복원
→ Workspace 재연결
→ Runtime 버전 검사
```

---

# 26. 테스트 계획

## 26.1 Path 테스트

- 기본 LocalAppData 경로
- 공백
- 한글
- 긴 경로
- 드라이브 문자
- junction/symlink
- 동일 이름 프로젝트
- Repository 이동
- Workspace 사용자 지정

## 26.2 Packaging 테스트

- Harness 입력 없음 → 실패
- 필수 script 없음 → 실패
- 금지 파일 포함 → 실패
- checksum 생성
- Runtime smoke
- MSI 설치
- 제거 후 Workspace 보존
- clean PC 설치

## 26.3 Workspace 테스트

- 프로젝트 등록 시 생성
- 날짜 bootstrap
- 4-file 탐지
- optional 로그 없음
- legacy 파일 존재
- 과거 날짜 read-only
- 프로젝트 삭제 시 Workspace 보존
- 명시 삭제

## 26.4 Process 테스트

- Bundled Runtime 경로에서 실행
- 공백 경로
- Program Files read-only
- 출력은 Workspace로만 생성
- cancel
- timeout
- child process 종료
- State 재읽기

---

# 27. 개발 Phase 초안

## Phase P0 — 경로 추상화

- Harness의 `D:\harness-kit`, `D:\harness-workspaces` 하드코딩 조사
- Runtime/Repository/Workspace 인자 주입
- `WorkspaceDay` 확정
- 경계 검사
- 기존 live 방식 회귀 검증

**종료 기준:** 외부 임의 경로의 Runtime, Repository, Workspace 조합으로 Harness가 정상 동작한다.

## Phase P1 — Workspace Manager

- Project ID
- Workspace 경로 생성
- Registry 연결
- 사용자 지정 위치
- 날짜 탐색
- 삭제·보존 정책

**종료 기준:** UI 프로젝트 등록 시 프로젝트별 Workspace가 자동 생성되고 재실행 후 복원된다.

## Phase P2 — Bundled Runtime Contract

- manifest
- checksum
- 공개 entrypoint
- runtime version
- compatibility
- 금지 파일 목록
- staging script

**종료 기준:** 개발용 Harness에서 배포용 Runtime을 재현 가능하게 생성한다.

## Phase P3 — Packaging Pipeline

- Gradle staging
- Harness Git 추적 검사
- secret scan
- Runtime smoke
- MSI resource 포함
- build metadata

**종료 기준:** 승인된 Runtime 없이는 패키징이 실패하고 승인된 Runtime으로 MSI가 생성된다.

## Phase P4 — 설치·첫 실행

- Program Files 경로 탐색
- AppData 초기화
- Runtime 검증
- 프로젝트 등록
- Workspace 자동 생성
- doctor
- 첫 Cockpit

**종료 기준:** clean Windows에서 Harness 경로를 수동 지정하지 않고 프로젝트 등록이 가능하다.

## Phase P5 — 제거·복구

- Workspace 보존
- 전체 데이터 삭제 옵션
- repair
- 재설치 복원
- Repository 이동 재연결

**종료 기준:** 앱 업데이트·제거·재설치에도 사용자 Workspace가 안전하다.

## Phase P6 — 상품화

- 코드 서명
- 라이선스
- CD Key
- 업데이트
- crash report
- 제품 버전 정책
- EULA
- 개인정보·로그 정책

**종료 기준:** 상용 배포에 필요한 보안·운영·라이선스 체계를 갖춘다.

---

# 28. 아직 확정하지 않은 항목

| 항목 | 현재 초안 | 재검토 시점 |
|---|---|---|
| MSI vs Setup EXE | MSI 우선 | P3 |
| Portable 모드 | MVP 제외 | P5 이후 |
| Runtime 암호화 | MVP 제외 | 상품화 |
| Workspace 기본 위치 | LocalAppData | P1 |
| Workspace 폴더 ID | 이름+short UUID | P1 |
| Runtime side-by-side | MVP 제외 | 업데이트 설계 |
| 자동 업데이트 | MVP 제외 | P6 |
| CD Key 방식 | 미정 | P6 |
| Runtime 코드 서명 | 검토 | P3~P6 |
| AppData 제거 정책 | 기본 보존 | P5 |
| 외부 Harness 실행 lock | 휴리스틱 | Harness 계약 발전 시 |
| Harness Runtime 명칭 | 잠정 | P2 |

---

# 29. 계획 변경 원칙

이 문서는 초안이며 다음 조건에서 갱신한다.

1. Harness entrypoint 또는 parameter 계약 변경
2. `WORKFLOW_STATE.json` schema 변경
3. 로그 구조 변경
4. Workspace required surface 변경
5. Compose Desktop 패키징 구조 확정
6. 설치 방식 MSI/EXE 확정
7. 라이선스 모델 확정
8. 자동 업데이트 도입
9. Portable 모드 도입
10. Harness Runtime이 PowerShell 외 형태로 전환

변경 시 원칙:

- 기존 결정을 삭제하지 않고 변경 이력을 기록
- 코드 구현과 live Harness를 실측
- 문서 가정보다 실제 동작 우선
- 호환성 영향을 별도 표기
- 패키징 Gate 갱신

---

# 30. 현재 시점 권장 결론

```text
원격 Git
└── hrns_now Kotlin/Compose 소스

로컬 비공개 소스
└── harness-kit
    └── 로컬 Git 권장

빌드
└── harness-kit에서 배포 Runtime staging
    ├── manifest
    ├── checksum
    └── smoke

설치물
└── HRNS-NOW MSI
    ├── HRNS-NOW Application
    ├── Java Runtime
    └── Bundled Harness Runtime

사용자 PC
├── Program Files
│   └── 앱 + Harness Runtime
├── AppData
│   └── Registry + 설정 + 라이선스
└── LocalAppData
    └── 프로젝트별 Workspace + lock + 앱 로그
```

최종 사용자 흐름:

```text
HRNS-NOW 설치
→ 앱 실행
→ 대상 Repository 선택
→ 프로젝트명·Project ID 확정
→ LocalAppData에 Workspace 자동 생성
→ Bundled Harness Runtime 검사
→ doctor
→ 오늘 날짜 bootstrap
→ Cockpit 진입
→ Planning/Execution/Closure 수행
```

---

# 31. 최종 원칙

> **Harness Runtime은 제품에 포함하되 상태와 로그를 설치 폴더에 저장하지 않는다.**

> **대상 Repository와 Project Workspace는 반드시 분리한다.**

> **프로젝트 등록 시 HRNS-NOW가 Workspace를 자동 생성하고, Harness에는 모든 경로를 명시적으로 전달한다.**

> **Harness daily 4-file과 이중 로그 구조는 유지하되, 사용자는 내부 경로를 직접 관리하지 않는다.**

> **원격 Git에는 HRNS-NOW만 올리고, Harness 원본은 로컬 이력과 패키징 검증 절차로 관리한다.**

> **초기에는 앱과 Harness Runtime을 하나의 Installer 버전으로 묶어 유지보수 복잡도를 낮춘다.**

---

# 부록 A — 초기 `.gitignore`

```gitignore
# Private Harness source/runtime
/private/harness-kit/
/private/harness-runtime/
/private-artifacts/
harness-runtime-*.zip

# Packaging staging
/build/staged-runtime/
/build/private-runtime/
/build/package-input/

# Local configuration
/local.properties
/.env.local

# Runtime/user data
/runtime-data/
/harness-workspaces/

# Generated reports
/run-check.out
/run-check.err
/run-check.*
```

---

# 부록 B — 프로젝트 등록 Domain 초안

```kotlin
data class ProjectRegistrationRequest(
    val displayName: String,
    val repositoryRoot: Path,
    val profileId: String,
    val workspaceBaseOverride: Path? = null,
)

data class RegisteredHarnessProject(
    val id: ProjectId,
    val displayName: String,
    val repositoryRoot: Path,
    val projectWorkspaceRoot: Path,
    val profileId: String,
    val createdAt: Instant,
)

sealed interface ProjectRegistrationResult {
    data class Registered(
        val project: RegisteredHarnessProject,
    ) : ProjectRegistrationResult

    data class Rejected(
        val violations: List<ProjectBoundaryViolation>,
    ) : ProjectRegistrationResult

    data class BootstrapFailed(
        val project: RegisteredHarnessProject,
        val reason: String,
    ) : ProjectRegistrationResult
}
```

---

# 부록 C — 향후 검증 체크리스트

```text
[ ] Harness에 절대 경로 하드코딩이 남아 있지 않은가?
[ ] Runtime이 Program Files에 상태 파일을 쓰지 않는가?
[ ] Workspace가 Repository 안에 생성되지 않는가?
[ ] 프로젝트명이 같아도 Workspace가 충돌하지 않는가?
[ ] 앱 제거 후 Workspace가 보존되는가?
[ ] Repository 이동 후 Workspace를 재사용할 수 있는가?
[ ] Runtime manifest와 checksum이 생성되는가?
[ ] Harness 원본이 Git index에 포함되지 않는가?
[ ] 패키징 시 secret·session ID·실제 로그가 제외되는가?
[ ] clean Windows 환경에서 첫 실행이 가능한가?
[ ] Runtime 버전과 UI 계약 버전이 표시되는가?
[ ] 라이선스와 Runtime 무결성 책임이 분리돼 있는가?
```
