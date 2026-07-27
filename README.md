# HRNS-NOW

> **Harness Kit Desktop Control Plane**  
> PowerShell 명령을 직접 외우지 않아도 Harness Kit의 현재 상태를 읽고, 지금 허용된 다음 행동을 안전하게 안내·실행하는 Windows 우선 데스크톱 애플리케이션입니다.

## 프로젝트 개요

HRNS-NOW는 Harness Kit을 Kotlin으로 다시 구현하는 프로그램이 아닙니다.

기존 Harness Kit의 PowerShell 진입점과 상태 계약을 그대로 유지하면서 다음 역할만 담당합니다.

1. 프로젝트와 작업 날짜를 선택합니다.
2. `WORKFLOW_STATE.json`을 읽어 현재 진행 상태를 해석합니다.
3. 상태·호환성·경계·실행 잠금을 종합해 허용된 다음 행동을 계산합니다.
4. 사용자가 승인한 행동을 정형화된 PowerShell 명령으로 실행합니다.
5. 실행 후 상태를 다시 읽고 다음 행동을 재계산합니다.
6. 중단·실패·마감 조건을 복구 센터와 Closure 화면에서 설명합니다.

즉, **Harness Kit이 실행 엔진**, **`WORKFLOW_STATE.json`이 기계 판독 가능한 단일 진실**, **HRNS-NOW가 안전한 데스크톱 제어판**입니다.

---

## 핵심 불변식

HRNS-NOW는 다음 원칙을 우회하지 않습니다.

- `WORKFLOW_STATE.json`을 UI가 직접 수정하지 않습니다.
- Harness Kit의 planning·execution·closure 로직을 Kotlin으로 복제하지 않습니다.
- Claude API를 직접 호출하거나 세션을 자동 재개하지 않습니다.
- 임의 PowerShell 콘솔이나 자유 형식 명령 실행 기능을 제공하지 않습니다.
- 알 수 없는 상태·스키마·호환성은 추측하지 않고 **fail-closed**로 실행을 잠급니다.
- 화면의 문구나 로그 문자열이 아니라 typed state와 정책 결과로 CTA를 결정합니다.
- Harness Runtime, 프로젝트 저장소, 프로젝트 workspace 사이의 경계를 검사합니다.
- secret, token, raw session ID, 원본 응답, raw log를 앱 Registry에 저장하지 않습니다.
- UI가 허용한 target이나 wrapper를 사용자가 임의 경로로 바꾸지 못하게 합니다.

---

## 현재 개발 상태

**기준일: 2026-07-27**

| 단계 | 상태 | 주요 결과 |
|---|---|---|
| Phase 0 | 완료 | Harness 계약 재정렬, 테스트·CI 기반 정비 |
| Phase 1 | 완료 | State Reader, fail-closed CTA, 실데이터 Cockpit, 프로젝트 Registry |
| Phase 2 | 완료 | Harness JSON 진단·호환성 계약 연동 |
| Phase 3 | 완료 | typed PowerShell 실행 어댑터, 프로세스 잠금, 온보딩 |
| Phase 4 | 완료 | 요청 작성, Planning·Replan, code/doc 실행 흐름 |
| Phase 5 | `PASS_WITH_FIXES` | Closure 정책, 복구 센터, Git 오염 확인, 진단 projection |
| Phase 6A | **BLOCKED** | Windows MSI·번들 JRE·아이콘·debug/release 패키징은 검증됐으나 clean Windows의 release MSI 통합 스모크가 남음 |
| Phase 6B | 미착수 | 승인된 Harness Runtime 릴리스 artifact 통합 |
| Phase 7 | 미착수 | opt-in 실험·고급 진단 기능 |

### Phase 6A가 아직 완료가 아닌 이유

현재 소스 기준으로 다음 검증은 통과했습니다.

- `core`, `infra`, `composeApp` 모듈 테스트
- 전체 `check`
- debug MSI 패키징
- release MSI 패키징
- release app image 생성
- JRE 번들 및 `jdk.charsets` 포함
- 전용 Windows 아이콘 반영
- 외부 Kit 참조와 Program Files 무쓰기 경계 유지

그러나 `G6A`가 요구하는 다음 독립 검증은 아직 수행되지 않았습니다.

```text
clean Windows 환경
  → release MSI 설치
  → 앱 실행
  → 외부 Harness Kit 지정
  → 프로젝트 등록
  → Doctor 및 State 조회
  → 표준 일일 cycle
  → 제거
  → AppData/LocalAppData 사용자 데이터 보존 확인
```

따라서 현재 생성되는 `1.0.0` MSI는 **패키징 검증 산출물**이며, 배포 승인이 끝난 정식 릴리스로 간주하지 않습니다. MSI는 아직 서명되지 않아 Windows SmartScreen 경고가 발생할 수 있습니다.

---

## 주요 기능

### 1. 프로젝트 온보딩과 Registry

프로젝트별로 다음 경로와 Profile을 등록하고 전환할 수 있습니다.

- Harness Kit root
- 프로젝트 workspace root
- Git repository root
- Harness profile
- 선택한 작업 날짜

등록 전에 경로의 상호 포함, junction·symlink를 고려한 실경계, Harness 호환성을 검사합니다.

Registry 해석 순서는 다음과 같습니다.

```text
사용자 Registry → 환경변수 fallback → 사용자 직접 선택
```

Registry 기본 위치:

```text
%APPDATA%\hrns-now\projects.json
```

실행 잠금 기본 위치:

```text
%LOCALAPPDATA%\hrns-now\locks\<project-id>\<date>.lock.json
```

### 2. `WORKFLOW_STATE.json` 안전 판독

현재 날짜의 Harness daily surface를 읽어 다음 정보를 구조화합니다.

- 현재 phase와 status
- queue 및 active slice
- required next action
- execution wrapper
- stop·blocked·failed reason
- authorized target
- artifact readiness
- ops validation
- closure 상태
- resume 지점
- role-sliced·usage guard 정보

알 수 없는 enum 값은 원문을 보존하고, unknown schema·malformed JSON·부분 기록 상태에서는 마지막 정상 projection을 stale로 표시하되 실행은 잠급니다.

Harness daily required surface는 다음 네 파일입니다.

```text
REQUEST_INBOX.md
TODAY_STRATEGY.md
DAILY_HANDOFF.md
WORKFLOW_STATE.json
```

`WORK_QUEUE.json`, `WORKDAY_STATE.json`은 legacy이며 정상 daily readiness의 필수 파일로 취급하지 않습니다.

### 3. 상태 기반 단일 CTA

UI는 화면 문자열이나 단순 버튼 조건문으로 실행 가능 여부를 결정하지 않습니다.

`ActionPolicy`가 다음 입력을 종합해 primary action과 허용 행동을 계산합니다.

- Workflow State
- Harness compatibility
- 프로젝트 경계 검사
- 현재 날짜 여부
- 프로세스 실행 상태
- HRNS-NOW 인스턴스 간 lock
- 외부 상태 변경 감지

대표 행동:

- 상태 점검
- 운영 검증
- 오늘 준비
- 요청 편집
- Planning
- Replan
- code slice 실행
- doc slice 실행
- Closure 검증
- 복구 센터 열기

### 4. 요청 작성과 동시성 보호

`REQUEST_INBOX.md`를 UI에서 편집할 수 있으며 다음 안전 규칙을 적용합니다.

- 기존 내용 보존
- 저장 전 diff 확인
- UTF-8 no BOM
- 임시 파일 작성 후 atomic move
- 로드 시 hash·mtime 보관
- 저장 직전 외부 변경 재검사
- 외부 변경 발견 시 덮어쓰기 차단
- 재로드·수동 병합 경로 제공
- `REQUEST_STRUCTURED.md` 직접 편집 금지

### 5. Harness 명령 실행

PowerShell 명령은 자유 형식 문자열이 아니라 typed command에서만 생성됩니다.

현재 연결되는 대표 진입점:

- `doctor.ps1`
- `validate-ops.ps1`
- `run-cycle.ps1` bootstrap
- planning wrapper
- replan wrapper
- code execution wrapper
- doc execution wrapper
- closure validation

프로세스 어댑터는 다음을 처리합니다.

- stdout·stderr 동시 drain
- timeout과 취소
- Windows process tree 종료
- 실행 시작·종료·exit code 기록
- Windows native console charset 처리
- 민감정보 마스킹
- 중복 실행 방지 lock

실행 완료는 stdout의 성공 문구로 판정하지 않습니다. 프로세스가 끝나면 State를 다시 읽고 queue·stop reason·CTA를 재계산합니다.

### 6. Closure와 복구 센터

Closure는 단순히 wrapper가 종료됐다는 이유만으로 허용되지 않습니다.

다음 조건을 종합해 `Allowed`, `Blocked`, `RequiresExplicitIncompleteHandoff`로 판단합니다.

- required 4-file 존재와 가독성
- State·queue 정상 판독
- ops validation 통과
- active slice와 resume 지점 정합성
- closure validated·clean handoff 상태
- 실행 lock 부재
- 예상 밖 Git 변경 검토
- 사용자의 명시적 확인이 필요한 dirty repository 여부

복구 센터는 stop reason과 queue marker를 기준으로 다음 세 가지를 구분해 표시합니다.

1. 무엇이 발생했는지
2. 현재까지 무엇이 보존됐는지
3. 사용자가 수행할 수 있는 다음 행동

continuity, usage ledger, failure history는 참고용 read-only projection이며 CTA의 권위로 사용하지 않습니다.

---

## 화면 구성

- **Setup** — Kit·workspace·repository·profile 등록과 진단
- **Cockpit** — 현재 State, queue, artifact, validation, 권장 행동 확인
- **Strategy** — 사람용 전략 문서와 기계 queue 비교
- **Run** — 실행 전 확인, 프로세스 상태, 로그, 결과 확인
- **Recovery** — stop reason별 복구 안내와 진단 요약
- **Closure** — 마감 조건과 명시적 확인

실데이터 연결 실패 시 demo mock으로 성공한 것처럼 대체하지 않습니다. demo 데이터는 명시적인 demo mode에서만 사용합니다.

---

## 모듈 구조

```text
hrns-now
├── core
├── infra
├── composeApp
├── doc
└── gradle
```

### `core`

Harness domain model, typed action·command, 정책, use case, port를 포함합니다.

- Compose 의존 없음
- 파일시스템·ProcessBuilder 의존 없음
- 정책을 순수 함수와 typed decision으로 유지

### `infra`

운영체제와 외부 시스템을 다루는 adapter 구현입니다.

- filesystem probe
- JSON State parser
- 프로젝트 Registry
- PowerShell process adapter
- Git status adapter
- recovery diagnostics adapter

### `composeApp`

Compose Desktop UI와 presentation 계층입니다.

- ViewModel과 `StateFlow`
- Setup·Cockpit·Strategy·Run·Recovery·Closure 화면
- UI projection과 demo 전용 데이터

의존 방향은 다음 원칙을 유지합니다.

```text
composeApp → core ← infra
```

---

## 기술 스택

- Kotlin `2.2.21`
- Compose Multiplatform `1.10.3`
- Kotlin Coroutines `1.10.2`
- Kotlin Serialization `1.9.0`
- AndroidX Lifecycle ViewModel
- Gradle Kotlin DSL
- JVM / JDK 17
- Windows PowerShell
- Compose Desktop native distribution / MSI

---

## 개발 환경에서 실행

### 요구사항

- Windows 10/11
- JDK 17
- 접근 가능한 외부 Harness Kit
- Gradle Wrapper 실행이 가능한 환경

### 애플리케이션 실행

```powershell
.\gradlew.bat :composeApp:run
```

### 전체 검증

```powershell
.\gradlew.bat check
```

모듈 테스트만 실행:

```powershell
.\gradlew.bat :core:test :infra:test :composeApp:jvmTest
```

---

## Windows MSI 패키징

현재 Phase 6A는 Windows MSI만 대상으로 합니다. DMG·DEB는 빌드 또는 검증 대상이 아닙니다.

### Debug MSI

```powershell
.\gradlew.bat :composeApp:packageMsi
```

기본 산출물:

```text
composeApp\build\compose\binaries\main\msi\HRNS-NOW-1.0.0.msi
```

### Release MSI

```powershell
.\gradlew.bat :composeApp:packageReleaseMsi
```

기본 산출물:

```text
composeApp\build\compose\binaries\main-release\msi\HRNS-NOW-1.0.0.msi
```

### Release app image

```powershell
.\gradlew.bat :composeApp:createReleaseDistributable
```

MSI는 bundled JRE를 포함하며 machine-wide 설치로 구성되어 설치 시 관리자 권한이 필요합니다.

설치 영역은 다음 원칙을 따릅니다.

```text
C:\Program Files\HRNS-NOW\  → 앱과 번들 JRE의 읽기·실행 영역
%APPDATA%\hrns-now\          → 프로젝트 Registry와 설정
%LOCALAPPDATA%\hrns-now\     → lock과 비민감 앱 데이터
선택한 project workspace      → Harness 실행 산출물
```

Program Files에는 Registry, workspace, Harness 로그, 사용자 작업 파일을 기록하지 않습니다.

---

## 문서

- [최종 통합 발전 계획](./doc/hrns_now_claude_plan.md)
- [Kotlin 아키텍처와 디자인 패턴](./doc/hrns_now_design_pattern.md)
- [패키징 계획](./doc/hrns_now_packaging_plan.md)
- [Phase 보고서](./doc/phase_reports)
- [다음 단계 작업 지시](./doc/claude_prompts)

개발 또는 검증을 시작하기 전에 현재 Phase 보고서와 다음 작업 지시를 함께 확인해야 합니다. 과거 보고서의 테스트 수나 판정보다 최신 Codex 독립 검증 절이 우선합니다.

---

## 로드맵

### 다음 허용 작업: Phase 6A Gate 보완

- clean Windows VM 또는 독립 계정 준비
- release MSI 설치·실행
- 외부 Kit과 프로젝트 등록
- Doctor·State 조회
- 표준 daily cycle 수행
- uninstall 후 사용자 데이터 보존 확인
- 결과를 근거와 함께 Phase 6 보고서에 반영

### 이후 단계

- **Phase 6B** — Harness 저장소가 승인한 재현 가능한 Runtime artifact만 MSI에 통합
- **Phase 7** — 메인 CTA와 분리된 opt-in 실험·고급 진단 기능
- **Post-MVP** — 코드 서명, 업데이트·롤백, 라이선스, portable data mode

`G6A`가 통과하기 전에는 Phase 6B 또는 Phase 7을 시작하지 않습니다.
