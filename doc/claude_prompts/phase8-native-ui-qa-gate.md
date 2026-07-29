# Phase 8 Native UI QA Gate

## 목적과 상태

이 작업은 새 기능 Phase가 아니다. Phase 8 Workflow Clarity의 코드·자동 검증은 완료됐지만, 최종 Compose Desktop 창의 실제 사용성 증빙이 없어 Gate가 차단된 상태다.

```text
현재 기준 HEAD: Codex Phase 8 보정 커밋 이후 HEAD
Verdict: BLOCKED
G8-Workflow-Clarity: BLOCKED
NEXT_ALLOWED_PHASE: Phase 8 Native UI QA Gate
```

Claude는 Git 작업을 절대 하지 않는다. `git add`, `commit`, `amend`, `rebase`, `reset`, `clean`, `stash`, `push`를 수행하지 않는다. Codex만 커밋한다.

## 먼저 읽을 문서

```text
README.md
doc/hrns_now_design_pattern.md
doc/phase_reports/phase8-completion-report.md
doc/claude_prompts/phase8-completion-localization-native-qa.md
doc/user_workflow_qa_notes.md
```

`doc/hrns_now_packaging_plan.md`와 `doc/user_workflow_qa_notes.md`는 사용자 소유 untracked 파일이다. 읽기만 하며 수정·삭제·stage하지 않는다. `D:\harness-kit`은 수정·복사하지 않는다.

## Codex 보정 사항 — 되돌리지 말 것

- Action/Closure policy의 차단 사유는 core의 typed key이고, presentation만 ko/en 문구를 만든다.
- 실행 실패·lock·request save 실패의 raw path/process reason은 notification history에 남기지 않는다.
- 실제 `BootstrapDay` 실행 CTA는 `작업 계획`의 시작/요구사항 카드 한 곳에만 있다. 날짜 선택은 실행이 아니다.
- lock 파일은 완성된 payload를 쓴 뒤 기존 파일을 덮어쓰지 않는 방식으로 공개한다.
- Cockpit/Recovery diagnostics와 readiness 색상도 ko/en에 맞게 투영한다.

## 필수 Native QA

최신 HEAD에서 실제 창을 실행한다.

```powershell
Set-Location -LiteralPath 'S:\dev\project\hrns_now'
.\gradlew.bat :composeApp:run
```

사용자 또는 실제 창을 볼 수 있는 검증자가 아래 항목을 확인한 뒤, 사실만 보고서에 적는다.

1. 한국어와 English 전환
   - 프로젝트 관리, 작업 현황, 작업 계획, 실행 기록, 복구 센터의 제목·본문·모달·버튼·상태·오류가 선택 언어에 맞는지 확인한다.
   - technical field label(`Workspace root`, `Repository root`, `Kit root`, `Profile`)과 user-authored Markdown/profile 값은 자동 번역 대상이 아님을 구분한다.
   - 앱을 닫고 다시 열어 locale 선택이 유지되는지 확인한다.

2. 오늘 작업 흐름
   - 오늘 State가 없고 policy 조건이 충족되면 실제 `오늘 작업 시작`은 작업 계획 카드에만 있는지 확인한다.
   - 날짜 목록의 `오늘 날짜 선택`은 Bootstrap을 실행하지 않는지 확인한다.
   - 시작 뒤 State 재조회가 request-intake이면 그 자리가 `요구사항 작성`으로 바뀌는지 확인한다.
   - 과거 날짜, lock/running, malformed state, boundary/compatibility 실패에서는 write/execute가 계속 fail-closed인지 확인한다.

3. 등록·피드백·알림
   - internal SDK 누락 등록은 진행 상태와 이해 가능한 실패/고급 설정 안내를 등록 modal 안에서 보여 주는지 확인한다.
   - hover pointer/색상, disabled, loading, 성공·실패 toast와 notification tray가 일관되는지 확인한다.
   - notification history에 raw path, token, session ID, raw stdout/stderr가 없는지 확인한다.

4. 표시 품질
   - 날짜 pager 5개, selected/read-only 구분, 긴 Markdown heading/list/code, raw HTML 비실행을 확인한다.
   - 한글/영문 혼합 글꼴의 겹침·깨짐·과도한 letter spacing이 없는지 확인한다.

실제 Harness 실행, 유료 모델/API 호출, 대상 repository 변경은 임의로 수행하지 않는다. 결함이 발견될 때만 해당 Phase 8 범위의 최소 수정과 회귀 테스트를 한다. Phase 9 또는 packaging/runtime 배포 작업으로 확장하지 않는다.

## 보고서와 검증

검증 결과는 `doc/phase_reports/phase8-native-ui-qa-report.md`에 UTF-8 without BOM으로 작성한다. 다음을 포함한다.

- 작업 HEAD, 실행 환경, 실제 창 관찰자
- 각 checklist의 PASS/FAIL/미검증 및 스크린샷 또는 재현 절차
- 발견 결함과 최소 수정, 테스트 결과
- Harness Kit·사용자 untracked 파일 무변경 확인
- `Phase 8 Native UI QA 완료( Codex 검증 대기 )`까지만 기록한다. Gate PASS 선언과 Git 커밋은 Codex 역할이다.