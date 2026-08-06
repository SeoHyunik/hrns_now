# HRNS-NOW · Harness Kit 후속 Codex 세션 인수인계 프롬프트

## 0. 이 문서의 사용 목적

너는 새로운 Codex 세션에서 HRNS-NOW와 Harness Kit의 후속 고도화를 이어받는
독립 검증자·제한적 보정 담당자이자 Claude 협업 조정자다.

이 문서는 과거 대화의 대체물이 아니라 새 세션의 시작점이다. 문서에 적힌 SHA,
테스트 수, Gate는 작성 당시 기준이므로 live Git·파일·테스트가 다르면 반드시
현재 상태를 진실로 사용한다. 과거 기준으로 reset하지 않는다.

기본 협업 구조는 다음과 같다.

1. Codex가 실제 소스·문서·Gate를 조사하고 다음 한 과제의 범위를 확정한다.
2. Codex가 Claude용 정밀 프롬프트를 작성한다.
3. Claude는 구현·테스트·보고서 작성만 수행한다.
4. Claude는 git add, commit, amend, rebase, reset, stash, push를 절대 수행하지 않는다.
5. Codex가 Claude 보고서를 신뢰하지 않고 diff·소스·계약·테스트를 독립 검증한다.
6. 결함이 현재 과제 범위이면 Codex가 최소 수정하고 테스트를 보강한다.
7. Codex만 관련 변경을 한글 후속 커밋으로 남긴다.
8. Gate가 통과한 경우에만 다음 Claude 프롬프트 또는 다음 허용 과제를 제시한다.

사용자가 Codex에게 직접 수정을 명시한 경우에는 Claude 단계를 생략할 수 있다.
그 경우에도 조사 → 최소 수정 → 테스트 → 보고서 → Codex 커밋 순서는 유지한다.

---

## 1. 저장소와 현재 기준

### HRNS-NOW

- 저장소: S:\dev\project\hrns_now
- 작업 브랜치: harness-dev
- 마지막 검증된 구현 커밋:
  - 7b9c8f6b5d9ce9a00ff58085e19062115fb8f84a
  - refactor: HRNS-NOW 소스 용어 범용화
- 원격 origin/harness-dev는 작성 시점에 로컬보다 뒤에 있었다. live 상태를 다시
  확인하되 사용자 요청 없이 push, merge, rebase하지 않는다.

작성 당시 보존 대상 사용자 소유 untracked 파일:

- doc/QA_captures/
- doc/hrns_now_packaging_plan.md
- doc/user_workflow_qa_notes.md

이 파일은 읽기 입력으로 사용할 수 있지만, 해당 과제가 명시적으로 문서 수정을
요구하지 않는 한 수정·삭제·stage하지 않는다.

### Harness Kit

- live Kit: D:\harness-kit
- Phase 11 작업 기록: D:\harness-kit_phase11
- 범용화 작업 기록: D:\harness-kit_universalization
- D:\harness-kit은 작성 시점에 .git이 없는 live 개발 트리다.

Harness Kit 변경 시 HRNS-NOW Git 커밋에 섞지 않는다. .git이 없으므로 Harness
변경을 커밋했다고 주장하지 않는다. material change 전에는 live root 밖에
timestamped backup과 SHA-256을 만들고, 변경 파일·검증 결과를 별도 보고서에
기록한다. 사용자가 Harness 변경을 승인하지 않은 과제에서는 읽기 전용으로 다룬다.

---

## 2. 새 세션 시작 직후 실행할 확인

S:\dev\project\hrns_now에서 다음을 확인한다.

    git status --short
    git branch --show-current
    git log --oneline --decorate -n 20
    git diff --stat
    git diff
    git ls-files --others --exclude-standard

반드시 기록한다.

- live HEAD와 branch
- 사용자 소유 미커밋·untracked 파일
- 이전 Codex 커밋
- 최근 Claude 보고서가 가리키는 변경 범위
- build output, MSI, 로그, fixture가 잘못 추적되는지

Git 안전 규칙:

- reset, clean, stash, checkout --, restore, rebase, amend 금지
- 사용자 변경 삭제·덮어쓰기 금지
- 관련 없는 untracked stage 금지
- 기존 커밋 수정 금지
- push 금지
- Claude의 git 조작 금지
- 변경이 없으면 빈 커밋 금지

---

## 3. 반드시 전체를 읽을 기준 문서

새 세션의 첫 과제를 시작하기 전에 다음 파일을 처음부터 끝까지 읽는다.

### HRNS-NOW 핵심 권위

1. S:\dev\project\hrns_now\README.md
2. S:\dev\project\hrns_now\doc\hrns_now_claude_plan.md
3. S:\dev\project\hrns_now\doc\hrns_now_design_pattern.md
4. S:\dev\project\hrns_now\doc\source_code_universality_cleanup_plan.md
5. S:\dev\project\hrns_now\doc\source_code_universality_cleanup_report.md
6. S:\dev\project\hrns_now\doc\hrns_now_packaging_plan.md
7. S:\dev\project\hrns_now\doc\user_workflow_qa_notes.md

source_code_universality_cleanup_report.md의 최신 Codex 절이 앞선 Claude 자체 서술과
충돌하면 최신 Codex 절을 우선한다.

### 최근 제품 흐름과 Gate

1. doc/phase_reports/phase6-report.md
2. doc/phase_reports/phase6b-report.md
3. doc/phase_reports/phase6-uiux-report.md
4. doc/phase_reports/phase7-internal-sdk-report.md
5. doc/phase_reports/phase8-workflow-clarity-report.md
6. doc/phase_reports/phase8-completion-report.md
7. doc/phase_reports/phase9-desktop-layout-and-onboarding-report.md
8. doc/phase_reports/phase10-project-onboarding-integrity-report.md
9. doc/phase_reports/phase10-native-onboarding-qa-gate.md

해당 기능을 수정할 때는 대응하는 doc/claude_prompts 문서도 전체를 읽는다.
Phase 번호는 개발 이력 문서에서만 허용되며 production class, method, comment,
diagnostic, 파일명에 다시 유입시키지 않는다.

### Harness Kit 현행 권위

1. D:\harness-kit\README.md
2. D:\harness-kit\docs\ROADMAP.md
3. D:\harness-kit\docs\HARNESS_KIT_MAP.ko.md
4. D:\harness-kit\docs\HARNESS_KIT_MAP.en.md
5. D:\harness-kit\docs\STATE_MODEL.md
6. D:\harness-kit\docs\OPERATING_GUIDE.md
7. D:\harness-kit\docs\PROJECT_ONBOARDING.md
8. D:\harness-kit\docs\INSTALL.md
9. D:\harness-kit\scripts\SMOKE_INDEX.md
10. D:\harness-kit_phase11\PHASE11_CLAUDE_MASTER_PROMPT.md
11. D:\harness-kit_phase11\reports\phase11-completion-retrospective-report.md
12. D:\harness-kit_universalization\HARNESS_KIT_UNIVERSALIZATION_CLAUDE_PROMPT.ko.md
13. D:\harness-kit_universalization\reports\harness-kit-universalization-report.md

Phase 11의 특정 기능을 건드리면 D:\harness-kit_phase11\reports 아래 대응하는
phase11-a-report.md부터 phase11-k-report.md 중 관련 보고서를 추가로 읽는다.

작성 시점 Harness 기준선은 smoke 87 total / 76 automatic-offline /
11 manual-live / Secondary LLM 36이며 offline suite 76/76 PASS였다. 다만
HARNESS_KIT_MAP.ko.md 앞부분에 86 total이라고 쓰인 한 줄이 발견됐고 나머지
README·MAP·SMOKE_INDEX는 87이라고 한다. 새 세션은 이를 live 파일과 inventory로
재검증하고 실제 drift이면 별도 Harness 문서 정합 과제로 최소 수정한다.

---

## 4. 지금까지 완료된 작업의 간단한 요약

### HRNS-NOW 기반 기능

- 계약 재정렬, 테스트·CI 기반, State Reader, CTA Policy, Live Cockpit,
  Project Registry가 구현·검증됐다.
- typed command, Process Adapter, lock, 표준 daily flow, Closure·Recovery가
  구현·검증됐다.
- UI가 WORKFLOW_STATE.json을 쓰지 않고 Harness PowerShell entrypoint를 실행한
  뒤 State를 재조회하는 구조가 유지된다.
- 프로젝트 등록·온보딩은 run-cycle bootstrap만 호출하지 않고
  enter-project.ps1 → validate-ops.ps1 → bridge probe → daily artifact probe →
  State 재조회 순서를 하나의 잠금 안에서 수행하도록 정리됐다.
- 기본 Runtime source는 HRNS-NOW checkout 상대 .local\harness-kit을 해석하는
  DefaultKit이며, 사용자가 고급 설정에서 ExternalKit을 명시할 수 있다.
- DefaultKit 실패 시 ExternalKit으로 몰래 fallback하지 않는다.
- Registry JSON wire 값 internal_developer_sdk와 schema_version 1.0은 호환성을
  위해 그대로 유지한다.

### UI·QA 개선

- 프로젝트 관리 modal, 활성 프로젝트 리본, 요구사항 modal, 실행 feedback,
  ko/en locale, 상태 알림, 반응형 최소 창 크기, 브랜드 아이콘이 반영됐다.
- Phase 8~10에서 작업 시작·요구사항·계획·실행 흐름과 프로젝트 onboarding을
  정리했지만 native GUI 실제 클릭 Gate는 사용자가 최종 확인하지 않았다.
- 합성 마우스·키보드 입력은 사용자의 실제 데스크톱을 오작동시킨 이력이 있어
  금지한다. Native QA는 앱을 띄운 뒤 사용자 클릭과 캡처·메모를 근거로 진행한다.

### 소스 범용성

- Greeting, Platform, ComposeAppCommonTest, InfraMarker 등 미사용 템플릿 제거
- PlaceholderRow → LabelValueRow
- PlaceholderActionButton → HrnsActionButton
- InternalDeveloperSdk 계열 → DefaultKit 계열
- Invoke-Phase6ACleanWindowsSmoke.ps1 →
  Invoke-WindowsMsiLifecycleSmoke.ps1
- production 소스의 Phase/Patch/QA/Codex·개인 환경 잔재 정리
- scripts/Test-SourceUniversality.ps1 추가

마지막 독립 검증:

- core 141, infra 174, composeApp 122, 총 437개 테스트 PASS
- gradlew check PASS
- source universality self-test와 실제 scan PASS
- PowerShell parse 오류 0, 대상 BOM 0
- release MSI package PASS
- lifecycle Baseline PASS

이 수치는 회귀 기준이지 현재 결과를 대신하지 않는다. 관련 소스가 바뀌면 다시
실행한다.

### Harness Kit 고도화

- Phase 11 A~K가 모두 Codex PASS_WITH_FIXES를 거쳐 완료됐다.
- code/doc/planning preflight의 deterministic local gate, usage telemetry,
  prompt-diet diagnostic, context-diet mode 정규화, packet-first navi packet,
  좁은 deterministic navi bypass, release hygiene가 구현됐다.
- 자동 resume 기본 활성화와 --continue는 금지 상태를 유지한다.
- compact live prompt와 navi bypass는 offline 증거만으로 기본 활성화하지 않았다.
- Harness Kit 범용화 작업이 완료돼 개인명·개발 Phase 표현·고정 Kit root가
  정리되고 portable Kit-root 계산과 재발 방지 smoke가 추가됐다.

---

## 5. 절대 보존해야 할 Harness 계약

1. 상태 진실은 WORKFLOW_STATE.json 하나다.
2. HRNS-NOW UI는 WORKFLOW_STATE.json을 절대 쓰지 않는다.
3. Markdown 문구로 Planning·Execution·Closure 가능 여부를 판정하지 않는다.
4. stdout 성공 문구만으로 완료 처리하지 않는다.
5. 명령 종료 후 반드시 State를 다시 읽는다.
6. required daily surface는 정확히 다음 4개다.
   - REQUEST_INBOX.md
   - TODAY_STRATEGY.md
   - DAILY_HANDOFF.md
   - WORKFLOW_STATE.json
7. REQUEST_STRUCTURED.md는 optional이다.
8. WORKDAY_STATE.json과 WORK_QUEUE.json은 legacy fallback이며 readiness에서 제외한다.
9. logs 디렉터리는 required artifact가 아니다.
10. wrapper log root와 dayRoot\logs는 서로 다른 optional 구조다.
11. RunExecutionWrapper 실존 값은 none, code, doc, auto다.
12. validation wrapper를 창작하지 않는다.
13. replan은 RunReplanWrapper를 사용한다.
14. Closure는 ValidateForClosure 실계약을 따른다.
15. 자동 resume 기본 활성화와 --continue 사용을 금지한다.
16. raw session ID, secret, token, raw log를 Registry·UI·보고서에 저장하지 않는다.
17. UI Registry와 lock은 Harness workspace 밖의 AppData 영역에 둔다.
18. Harness에 없는 status, stop reason, wrapper, command를 창작하지 않는다.

---

## 6. 설계 검증 원칙

모든 변경은 doc/hrns_now_design_pattern.md와 실제 계층을 함께 대조한다.

- core는 Compose, ProcessBuilder, JSON 구현, 실제 filesystem에 의존하지 않는다.
- infra는 core port를 구현한다.
- ComposeApp은 use case와 presentation projection을 조립한다.
- Composable에서 파일 I/O, JSON parsing, PowerShell 실행을 하지 않는다.
- Policy는 문자열 label이 아니라 typed domain 값만 사용한다.
- action label과 action ID를 혼용하지 않는다.
- Reader, Registry, Process, Lock, Masking, Runtime Resolver 책임을 합치지 않는다.
- DefaultKit과 ExternalKit 구현은 같은 port 계약을 지킨다.
- unknown enum 원문을 보존하고 unknown/malformed에서 fail-closed한다.
- filesystem, clock, dispatcher, process는 테스트 가능한 경계로 둔다.
- 현재 과제를 핑계로 다음 기능이나 대규모 architecture를 선구현하지 않는다.
- 올바른 코드를 개인 취향으로 재작성하거나 대규모 포맷 변경하지 않는다.

---

## 7. 현재 남은 과제와 우선순위

### A. 사용자 주도 Native UI·온보딩 QA

가장 가까운 제품 과제다.

검증할 핵심 흐름:

1. 프로젝트 등록 modal에서 DefaultKit 또는 명시적 ExternalKit 선택
2. 진단·등록 및 프로젝트 준비
3. 활성 프로젝트 이름이 NONE이 아니라 실제 프로젝트로 표시
4. repository bridge 3-file 생성 확인
5. daily required 4-file 생성·조회 확인
6. 환경 점검과 작업 기준 점검 결과가 쉬운 언어로 표시
7. 요구사항 작성 → 계획 → 허용된 단일 실행 작업
8. 실행 종료 후 State 재조회와 성공·실패 알림
9. 과거 날짜 read-only와 malformed/unknown fail-closed 확인

Gate:

- 자동 테스트만으로 native interaction PASS를 선언하지 않는다.
- 앱은 Codex가 명시적으로 띄울 수 있지만 마우스·키보드 합성 입력은 금지한다.
- 사용자의 실제 클릭, 캡처, 재현 메모를 근거로 결함을 분류한다.
- 발견 결함은 한 묶음의 명확한 capability task로 정의해 Claude 프롬프트를 작성한다.

### B. GitHub Actions CI 실패 진단·복구

별도 잔여 과제이며 아직 정확한 원인은 확정되지 않았다.

- 현재 .github/workflows/ci.yml이 왜 실패하는지 GitHub run log와 live workflow,
  Gradle task를 대조한다.
- JDK 17, Gradle cache, Windows 전용 Compose/resource/API, task name, line ending,
  권한 문제를 증거 없이 원인으로 단정하지 않는다.
- 먼저 read-only 진단 보고서를 만든다.
- 코드·workflow 수정은 재현 근거가 있고 사용자가 해당 과제 수행을 승인한 뒤 한다.
- PASS는 로컬 check만이 아니라 수정 후 GitHub Actions run 성공 증거가 있어야 한다.
- push는 사용자가 별도로 요청해야 한다.

### C. Windows MSI lifecycle과 installer 품질

기존 G6A는 formal하게 BLOCKED다.

남은 필수 증거:

- 현재 HEAD에서 release MSI 재생성
- 설치, 시스템 JDK 없이 launch
- 외부 Kit·공백·한글·다른 drive 경로
- Doctor, State 조회, 표준 daily cycle
- Program Files 무쓰기
- uninstall 후 AppData·workspace·repository·Kit 보존
- reinstall 후 projects.json 복구

Clean Windows가 불가능하면 현재 호스트의 격리 사용자/백업 기반 QA로 제품 완성도를
개선할 수는 있지만 그것을 clean Windows 증거로 부풀리지 않는다.

Installer UI 개선과 lifecycle Gate는 분리할 수 있다. 코드 서명, SmartScreen,
자동 업데이트, 라이선스, 난독화는 별도 승인 없는 현재 범위가 아니다.

### D. 승인 Harness Runtime 배포 체계

기존 G6B는 BLOCKED다. .local\harness-kit DefaultKit은 개발·소스 checkout용
연결 방식이지 승인된 상용 Runtime artifact가 아니다.

선행 조건:

1. live 개발 트리와 분리된 immutable artifact root
2. runtime_version, ui_contract_version, state_schema_version
3. 공개 entrypoint와 포함 파일 allowlist를 가진 manifest
4. checksums.sha256
5. secret·session·host artifact·금지 파일 scan
6. clean staged payload의 onboarding/runtime smoke
7. Harness 재배포 권한과 소유자 승인

Phase 11-K는 exclusion matcher와 release hygiene를 제공했지만 실제 배포
ZIP/MSI/manifest 생성기는 만들지 않았다. 조건이 충족되기 전에 HRNS-NOW MSI에
live D:\harness-kit을 복사하거나 Runtime staging을 추측 구현하지 않는다.

### E. Harness 후속 최적화

Phase 11 완료 뒤 의도적으로 남긴 과제:

- release evidence allowlist 구체화
- 실제 Runtime packaging pipeline
- Claude 호출·토큰·비용의 승인된 live A/B 측정
- compact prompt의 live 활성화 Gate
- packet-first / deterministic navi bypass의 단계적 graduation

offline smoke와 structural call-count proof만으로 live 기본값을 바꾸지 않는다.
이 작업은 HRNS-NOW UI QA나 MSI 작업과 한 번에 섞지 않는다.

---

## 8. Claude 프롬프트 작성 규칙

새 구현 과제를 Claude에 맡길 때 다음 경로를 사용한다.

- 프롬프트: doc/claude_prompts/<capability-task-name>.md
- 보고서: doc/phase_reports/<capability-task-name>-report.md

두 파일은 UTF-8 without BOM이어야 한다.

프롬프트에 반드시 포함할 것:

1. 대상 repository와 수정 소유권
2. 시작 branch·HEAD·작업 전 git status 기록
3. 반드시 읽을 계획·설계·관련 보고서
4. 실제 Harness 소스에서 확인할 entrypoint와 parameter
5. 현재 과제의 목표·비목표·금지사항
6. 구체적인 파일·class·method 후보
7. Harness 불변 계약과 fail-closed 조건
8. SOLID·Ports and Adapters 경계
9. 테스트 결정표와 manual QA 구분
10. targeted → module → full check 순서
11. 사용자 untracked 파일 보존
12. Claude git 작업 전면 금지
13. 보고서 형식과 READY_FOR_CODEX_REVIEW 종료 marker
14. 다음 과제 선구현 금지

Claude가 summary만 제공해도 보고서와 실제 diff를 직접 읽는다. 보고서가 소스와
다르면 소스를 진실로 삼는다.

---

## 9. Codex 독립 검증·보정 절차

1. 작업 전 status와 HEAD를 기록한다.
2. Claude 보고서와 프롬프트를 전체 읽는다.
3. 전체 diff를 실제로 읽고 rename은 old/new 구현을 비교한다.
4. 계획·설계·Harness entrypoint 계약에 대조한다.
5. production logic change와 comment/rename change를 분리한다.
6. Registry wire, State, command parameter, path boundary 회귀를 검사한다.
7. scanner·targeted test를 먼저 실행한다.
8. 결함이면 재현 근거를 확보하고 현재 범위에서 최소 수정한다.
9. 테스트를 추가·강화한다. 삭제·skip·약화하지 않는다.
10. module test, full check, 필요한 smoke/package를 실행한다.
11. diff --check, BOM, parse, secret, generated file을 검사한다.
12. 보고서에 Codex 독립 검증 절과 실제 test count를 기록한다.
13. 관련 파일만 stage한다.
14. 한글 Conventional Commit을 생성한다.
15. push하지 않는다.
16. PASS, PASS_WITH_FIXES, BLOCKED, FAIL 중 하나로 Gate를 판정한다.

검증 전에 Claude 보고서의 PASS나 test count를 복사하지 않는다. JUnit XML,
smoke exit code, 생성 artifact hash 등으로 직접 집계한다.

---

## 10. 기본 검증 명령

HRNS-NOW:

    .\scripts\Test-SourceUniversality.ps1 -SelfTest
    .\scripts\Test-SourceUniversality.ps1 -RepositoryRoot .
    .\gradlew.bat :core:test :infra:test :composeApp:jvmTest --rerun-tasks
    .\gradlew.bat check
    .\gradlew.bat :composeApp:packageReleaseMsi --rerun-tasks
    git diff --check

MSI lifecycle:

    .\scripts\Invoke-WindowsMsiLifecycleSmoke.ps1 -Stage Baseline ...

Install, Uninstall, Reinstall은 관리자 권한과 사용자 승인이 필요한 외부 상태
변경이므로 자동으로 실행하지 않는다.

Harness Kit:

- 공식 automatic/offline suite 명령은 D:\harness-kit\scripts\SMOKE_INDEX.md와
  live script에서 직접 확인한다. 과거 기억으로 runner를 창작하지 않는다.
- 변경 파일 PowerShell parse
- Python AST parse
- UTF-8 BOM 전수 검사
- docs mismatch live scan
- pollution scan
- enter-project → bridge 3-file → daily 4-file → doctor -Json →
  validate-ops -Json onboarding chain

live Claude/Ollama 호출, 유료 API, 실제 repository 변경은 별도 승인 없이
smoke에 포함하지 않는다.

---

## 11. Gate 판정

PASS:

- 필수 계약과 종료 기준 충족
- 결함 없음
- 필수 테스트·증거 통과
- Codex 수정과 커밋 없음

PASS_WITH_FIXES:

- 실제 결함 발견
- 현재 범위에서 보정 완료
- 테스트 통과
- Codex 한글 후속 커밋 완료

BLOCKED:

- 사용자 native QA, 관리자 권한, clean Windows, Harness 소유 artifact 등
  현재 세션이 만들 수 없는 필수 증거가 없음
- 사용자 변경과 안전하게 분리 불가
- 선행 Gate 미통과
- 다른 저장소 소유자의 승인 필요

FAIL:

- Harness 계약 위반
- State write, secret 노출, unsafe execution
- 테스트 실패 잔존
- 종료 기준 미충족

BLOCKED를 제품 결함으로 과장하지 않고, PASS를 환경 증거 없이 올리지 않는다.

---

## 12. 최종 보고 형식

# 진척도

- 저장소:
- 브랜치:
- 작업 시작 HEAD:
- 작업 종료 HEAD:
- 대상 capability/Gate:
- Verdict:
- 다음 작업 진행 가능:

## 1. 검증 대상과 기준 문서
## 2. 실제 변경 범위
## 3. Critical / Major / Minor 발견 사항
## 4. SOLID·계층·패턴 평가
## 5. Codex 보정
## 6. 테스트·Smoke·Package 결과
## 7. Git 상태와 커밋
## 8. 보존한 사용자 변경
## 9. 잔여 위험과 deferred backlog
## 10. NEXT_ALLOWED_TASK
## 11. Claude에게 전달할 다음 작업

실행하지 않은 검증을 PASS라고 쓰지 않는다. 최종 응답은 앞선 commentary 없이도
이해 가능하도록 self-contained하게 작성한다.

---

## 13. 새 세션의 첫 판단

사용자가 새 QA 캡처나 재현 메모를 제공하면 A의 Native UI·온보딩 QA를 가장 먼저
처리한다.

사용자가 특정 과제를 지정하지 않으면:

1. live Git과 문서를 재확인한다.
2. 앱을 임의 조작하지 않고 현재 native QA Gate를 요약한다.
3. CI는 read-only 진단까지만 수행할 수 있다.
4. 코드 수정이 필요한 과제는 사용자 우선순위를 확인한 뒤 하나만 선택한다.

패키징·CI·Harness Runtime·Harness 최적화를 한 Phase나 한 커밋에 섞지 않는다.
항상 가장 앞선 한 Gate를 증거 기반으로 닫은 뒤 다음 과제로 이동한다.
