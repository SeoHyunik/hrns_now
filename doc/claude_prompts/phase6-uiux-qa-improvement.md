# Claude 작업 지시 — 새 Phase 6: UI/UX QA 개선

## 역할·현재 Phase·Git

당신은 HRNS-NOW의 **새 Phase 6 UI/UX QA 개선 구현 담당자**다. 실제 사용 QA에서 드러난 혼란·용어·피드백·프로젝트 흐름을 개선한다. 기능을 임의로 확장하거나 Harness 계약을 바꾸지 않는다.

현재 저장소는 `S:\dev\project\hrns_now`, branch는 `harness-dev`다. Git commit, amend, rebase, reset, stash, clean, push는 수행하지 않는다. 모든 commit은 Codex만 수행한다.

완료 보고서는 `doc/phase_reports/phase6-uiux-report.md`에 **UTF-8 without BOM**으로 작성한다. `doc/hrns_now_packaging_plan.md`는 사용자 소유 untracked 설계 입력이므로 읽기·수정·삭제·stage하지 않는다.

## 반드시 먼저 읽을 문서

- `doc/hrns_now_claude_plan.md` — 특히 §0.5와 새 Phase 6, 보류 배포 과제 경계
- `doc/hrns_now_design_pattern.md` — 특히 §8, §9, §18~20의 MVVM/UDF/presentation 책임과 보류 6A/6B 경계
- `doc/phase_reports/phase5-report.md`, `doc/phase_reports/phase6-report.md`, `doc/phase_reports/phase6b-report.md`
- `doc/claude_prompts/phase6-uiux-qa-improvement.md` (이 문서)

현재 HEAD에는 다음 Codex 기록이 있다.

```text
c8e3fe3 docs: Phase 6A clean profile smoke 결과 기록
40213a3 docs: Phase 6B 런타임 통합 작업 지시 추가
4fc41b5 docs: Phase 6B 선행 조건 검증 기록
```

기존 Phase 6A/6B 패키징·Runtime 통합과 기존 Phase 7 실험 기능은 **보류 과제**다. 새 Phase 6의 완료가 G6A/G6B PASS나 기존 Phase 7 진입을 뜻하지 않는다. `D:\harness-kit`은 수정·복사·zip backup 생성 없이 read-only로만 다룬다.

## 제품 목표

사용자가 앱을 열었을 때 다음 네 가지를 즉시 알 수 있어야 한다.

1. 현재 어느 프로젝트를 보고 있는가.
2. 지금 상태가 무엇이며 문제가 있는가.
3. 지금 허용된 단 하나의 다음 작업이 무엇인가.
4. 눌렀던 작업이 실행 중인지, 성공했는지, 실패했는지와 이유가 무엇인가.

이 목표는 화면 미관만의 문제가 아니다. HRNS-NOW의 fail-closed CTA와 `WORKFLOW_STATE.json` 단일 진실을 사용자가 이해할 수 있도록 하는 UX 개선이다.

## 구현 범위

### 1. 프로젝트 흐름

- 화면의 상시 등록 폼을 제거하거나 기본 화면에서 숨긴다. 활성 프로젝트가 이름·상태·핵심 경로 요약과 함께 상단에서 분명히 보이게 한다.
- 화면/메뉴 이름은 **프로젝트 관리**, 주 동작은 **프로젝트 등록**으로 한다. 기존 프로젝트의 등록·전환·수정은 modal/dialog에서 처리한다.
- 프로젝트가 전혀 없을 때만 등록 온보딩을 보인다. 기존 Registry, BoundaryPolicy, compatibility 검사, `Registry → 환경변수 fallback → 사용자 선택` 순서는 바꾸지 않는다.
- 개인 경로, fixture, 날짜, `D:\harness-kit`을 production UI/code에 hardcode하지 않는다.

### 2. Action 실행 feedback

- `상태 점검 실행`은 UI에서 **환경 점검**으로 표시한다. 누른 즉시 running spinner/progress와 명확한 "점검 중" 상태를 보이고 동일 action의 중복 클릭을 막는다.
- 성공 시 초록색 결과 badge, 완료 시각, 짧은 결과 요약과 재실행 가능한 **다시 점검** 동작을 보인다. 외부 State·Kit·경로가 바뀔 수 있으므로 성공만으로 버튼을 영구 비활성화하지 않는다.
- 실패 시 실패 상태, 사람이 이해할 수 있는 원인, 재시도 가능 여부를 보인다. cancel을 기존 runner가 지원하는 동작에만 노출한다.
- feedback은 action label 문자열이 아니라 typed `UiAction`/실행 결과와 ViewModel의 단일 `StateFlow` 상태에서 조립한다. 클릭·stdout 문구만으로 성공 처리하지 않고, 기존 실행의 process 결과 및 lock 보유 중 State reread 계약을 유지한다.

### 3. 정보 구조와 한국어 용어

다음 명칭을 실제 화면·button·empty/error state·accessibility label에 일관되게 반영한다. 내부 class/command ID를 기계적으로 개명할 필요는 없다.

| 기존 | 새 표시명 |
|---|---|
| 작업공간 연결 | 프로젝트 관리 |
| 프로젝트 Registry | 프로젝트 관리 |
| 오늘 현황 | 작업 현황 |
| 다음 행동 | 다음 작업 |
| DIAGNOSTICS | 상태 진단 |
| 발생한 일 | 최근 작업 기록 |
| 이전 정상 기록 | 마지막 정상 상태 |
| 오늘 할 일 | 작업 계획 |
| Strategy | 개발 전략 |
| Queue | 작업 대기열 |
| 요청 작성 | 요구사항 작성 |
| 실행 현황 | 실행 기록 |
| Doctor | 환경 점검 |
| Ops Validation | 작업 기준 점검 |
| 오늘 준비 | 작업 준비 |

- `역할별 진행 단계`는 기본 화면에서 제거한다. 꼭 필요한 저수준 정보는 상세/접힘 영역으로만 제공한다.
- `작업 현황`과 `실행 기록`을 같은 화면명으로 중복하지 않는다.
- raw session ID, secret, token, raw log, internal path/debug jargon을 기본 화면에 노출하지 않는다.

### 4. 요구사항 작성 modal

- 상단에 **요구사항 작성** CTA를 두고 modal editor를 연다. 장문의 입력을 충분히 볼 수 있는 크기와 keyboard focus/ESC/닫기 동작을 제공한다.
- 저장 버튼이 비활성화되는 이유를 명확히 보인다. 필수 입력 누락, validation 오류, 저장 중 상태를 구분한다.
- 미저장 변경이 있는 상태에서 닫으면 확인을 요구한다. 저장 성공은 feedback으로 알리고 modal을 닫거나 저장 결과가 반영된 화면을 즉시 보여준다.
- 기존 `RequestWriterPort`, atomic write, optimistic concurrency, external REQUEST 변경 충돌 감지 계약을 약화하거나 우회하지 않는다. conflict/error를 사용자에게 이해 가능한 문구로 보인다.

### 5. Windows installer 품질

- 먼저 현재 Compose Desktop/JPackage/WiX MSI가 실제로 제공하는 installer UI와 metadata 범위를 확인한다.
- 현 계약 안에서 제품명, 아이콘, 한국어 안내, 설치 목적/위치의 최소 품질을 개선한다. app/Registry/lock/workspace/로그는 Program Files에 쓰지 않는 기존 경계를 보존한다.
- generated WiX UI만으로 해결할 수 없는 고급 installer experience는 근거와 한계를 보고서에 기록한다. 별도 bootstrapper, code signing, update, bundled Harness Runtime, Phase 6A/6B staging을 새로 만들지 않는다.

## 설계·안전 규칙

- Composable은 file I/O, JSON parsing, ProcessBuilder, PowerShell 실행을 직접 하지 않는다.
- ViewModel은 event 처리·use case 호출·`StateFlow<HrnsUiState>` 조립만 담당한다. action feedback reducer/state는 presentation 책임으로 분리한다.
- domain policy와 UI 표시 문구를 섞지 않는다. `ActionPolicy`, `ClosurePolicy`, `CompatibilityPolicy`, `BoundaryPolicy`를 화면 편의를 위해 바꾸지 않는다.
- UI가 `WORKFLOW_STATE.json`, Harness Markdown, log를 직접 쓰지 않는다. demo/mock을 실데이터 실패 fallback으로 사용하지 않는다.
- 현재 Phase 범위를 넘는 broad redesign, architecture rewrite, 테스트 삭제/skip, 대규모 자동 포맷을 하지 않는다.

## 테스트·수동 QA

1. presentation/ViewModel: 활성 프로젝트 표시, modal open/close, 실행 중 중복 action 차단, success/failure feedback, stale/error 상태를 테스트한다.
2. requirements: 저장 가능/불가 사유, 저장 중, optimistic concurrency conflict, 미저장 닫기 확인을 테스트한다.
3. 화면 용어는 핵심 label과 중복 화면명 제거를 회귀 테스트하거나 검증 가능한 UI model로 보장한다. label을 action ID로 사용하지 않는다.
4. installer 설정을 바꿨다면 MSI metadata/artifact inspection test와 `:composeApp:packageReleaseMsi --rerun-tasks`를 실행한다. 변경하지 않았다면 실제 미실행 사유를 기록한다.
5. targeted → module → full 순서로 실행한다.

```powershell
.\gradlew.bat :core:test
.\gradlew.bat :infra:test
.\gradlew.bat :composeApp:jvmTest
.\gradlew.bat check
```

가능하면 실행 중/성공/실패, project modal, requirement modal의 수동 QA 절차와 결과를 기록한다. Harness를 실제로 실행해야 하는 QA는 허용된 CTA와 fixture/안전한 workspace만 사용하고 stdout 성공만으로 완료 판정하지 않는다.

## 보고서 필수 내용

`doc/phase_reports/phase6-uiux-report.md`에 다음을 기록한다.

- 변경 파일, UI QA 발견 사항, 채택한 정보 구조와 용어 매핑
- active project/modal/action feedback/requirements modal/installer의 구현·수동 QA 근거
- presentation 책임 분리와 SOLID·MVVM/UDF 판단
- 테스트·MSI packaging 실행 결과 및 미실행 사유
- `WORKFLOW_STATE.json`/Harness 계약/기존 Action·Closure·Boundary·Compatibility 정책을 바꾸지 않았다는 근거
- 기존 6A/6B/7이 보류 과제이고 G6A/G6B 상태를 변경하지 않았다는 명시
- Git 작업 없음

완료 시 코드 변경 요약, 테스트 출력, 수동 QA 결과, 잔여 위험을 보고한다. 새 Phase 6의 Gate와 후속 과제 허용 여부는 Codex만 판정한다.
