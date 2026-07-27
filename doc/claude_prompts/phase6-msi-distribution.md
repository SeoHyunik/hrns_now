# Claude 작업 지시 — Phase 6: Windows MSI 패키징·배포

## 역할과 범위

당신은 HRNS-NOW의 **Phase 6 구현 담당자**다. 이 문서와 아래 선행 문서를 전부 읽은 뒤 MSI 전용 MVP만 구현·검증한다.

- `doc/hrns_now_claude_plan.md`
- `doc/hrns_now_design_pattern.md`
- `doc/phase_reports/phase5-report.md`
- `doc/claude_prompts/phase6-msi-distribution.md`

저장소는 `S:\dev\project\hrns_now`, branch는 `harness-dev`다. Git commit, amend, rebase, reset, stash, push를 수행하지 않는다. Codex만 commit한다. 완료 증거는 `doc/phase_reports/phase6-report.md`에 **UTF-8 without BOM**으로 작성한다.

`doc/hrns_now_packaging_plan.md`는 사용자의 untracked 작업이다. 사용자가 별도로 지시하지 않는 한 읽기·수정·삭제·stage하지 않는다.

## 선행 Gate와 Codex 변경 사항

Phase 5는 `PASS_WITH_FIXES`로 통과했으며 Codex가 다음 커밋을 만들었다.

```text
9e6b267 feat: Phase 5 마감 검증과 복구 센터 구현
```

이 커밋의 핵심 계약을 보존한다.

- `ClosurePolicy`와 `ActionPolicy`는 별도 정책이며 Closure validation은 두 정책의 교집합에서만 실행한다.
- dirty repository는 read-only `git status --short` 경고이며, Recovery 화면의 명시적 acknowledgement 없이는 closure validation을 실행하지 않는다.
- `ExecuteHarnessActionUseCase`의 policy 재검증 → typed command → lock → runner → lock 보유 중 State reread → release 순서는 변경하지 않는다.
- `WORKFLOW_STATE.json`은 UI가 절대 쓰지 않는다. Markdown/log/diagnostic은 CTA나 Closure의 권위가 아니다.
- Recovery diagnostics는 raw session ID, secret, token, raw log를 보존·표시하지 않는 optional reference projection이다.
- Harness의 현행 validator는 handoff placeholder를 typed signal로 제공하지 않는다. UI에서 Markdown 문구를 파싱해 Closure 판정으로 보완하지 않는다.

## Phase 6 목표

깨끗한 Windows 환경에서 MSI를 설치하고 다음 기본 흐름을 검증할 수 있는 MSI 전용 MVP를 완성한다.

```text
MSI 설치 → Kit root 지정 → 프로젝트 등록 → Doctor → 상태 조회 → 표준 일일 사이클
```

Harness Kit은 앱에 포함하거나 복사하지 않는다. 설치된 UI는 외부 Kit root를 참조하고 기존 compatibility handshake를 사용한다.

## 필수 구현

1. `composeApp/build.gradle.kts`를 실제 Compose Desktop packaging 계약으로 점검한다.
   - `TargetFormat.Msi`만 target으로 남긴다. DMG/DEB는 구현·검증하지 않는다.
   - 사용자 노출 앱 이름, package name, version, Windows icon을 실제 배포물 기준으로 정리한다.
   - version은 임의 hardcode가 아니라 현재 프로젝트의 단일 source of truth를 확인해 사용한다. 새 version policy가 필요하면 최소 설정으로 문서화한다.
2. JRE 번들 전략을 실제 Gradle/Compose 계약으로 결정한다.
   - clean Windows 실행을 위해 runtime 포함 여부와 jlink 사용 여부를 확인한다.
   - module pruning은 실행 smoke가 증명할 수 있을 때만 한다. 확신이 없으면 안전한 runtime bundle을 선택한다.
3. 첫 실행 Kit root 해석을 검증한다.
   - 기존 순서 `Registry → 환경변수 → 사용자 선택`과 compatibility handshake를 보존한다.
   - package에서 Kit을 검색·복사·내장하지 않는다.
4. Windows 경로 이식성을 검증한다.
   - drive letter 변경, 공백 경로, 한글 경로를 포함한 Kit/workspace/repository 구성에서 설치 앱이 경로를 문자열 연결으로 손상하지 않는지 확인한다.
5. 배포 안전성을 문서화한다.
   - 미서명 MSI의 SmartScreen 경고와 코드 서명 필요성을 정직하게 기록한다. 서명 인증서/secret을 저장하거나 생성하지 않는다.
   - installer, runtime image, build output, 실행 log, IDE cache를 Git에 추가하지 않는다.

## 금지 사항

- `D:\harness-kit` 수정, zip backup 생성, Harness Kit 동봉·복사
- Phase 7 실험 기능, raw State/log viewer, secondary LLM 기능 선구현
- `WORKFLOW_STATE.json`/Harness daily file 직접 쓰기
- `--continue`, 자동 resume, 임의 PowerShell 입력, stdout 성공 문구만으로 완료 판정
- user-owned `doc/hrns_now_packaging_plan.md` 변경 또는 Git stage
- 테스트 삭제·skip·계약 약화로 통과시키기

## 검증 순서

실제 task 이름은 먼저 `./gradlew.bat tasks`에서 확인한다. 그 뒤 최소 다음을 수행한다.

```powershell
.\gradlew.bat :core:test
.\gradlew.bat :infra:test
.\gradlew.bat :composeApp:jvmTest
.\gradlew.bat check
```

MSI task와 설치 도구(jpackage/WiX 등)가 준비돼 있으면 실제 MSI를 build하고 clean Windows install smoke를 수행한다. 도구가 없거나 권한이 없으면 추측해 성공 처리하지 말고 정확한 task, 출력, 재현 조건을 report에 남긴다.

## 보고서 필수 내용

`doc/phase_reports/phase6-report.md`에 다음을 기록한다.

- 변경 파일과 MSI/JRE/Kit external-reference 설계 근거
- 실제 Gradle packaging task와 산출물 위치(민감정보 제외)
- clean install smoke의 절차와 결과
- 공백·한글·drive-letter 경로 검증 근거
- SmartScreen/서명 상태와 알려진 배포 제한
- 실행한 테스트 및 미실행 사유
- Harness 변경 없음, Git 작업 없음 명시

Codex가 live 파일, diff, packaging output, 테스트, 설계 문서를 독립 검증한 뒤에만 commit과 Phase 7 진입을 판단한다.
