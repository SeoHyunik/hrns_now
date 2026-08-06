# HRNS-NOW Native QA 체크리스트

이 문서는 Compose Desktop 앱의 실제 사용자 상호작용을 검증하는 현재 체크리스트다. 자동 테스트나 합성 입력은 native QA 증거를 대체하지 않는다.

## 실행 전 Gate

다음을 모두 만족하기 전에는 native QA를 `PASS`로 판정하지 않는다.

- 현재 build SHA와 실행 artifact가 일치한다.
- HRNS-NOW 전체 `check`가 강제 재실행으로 통과한다.
- 선택한 Harness Kit의 manifest와 public entrypoint가 호환된다.
- fresh onboarding State가 HRNS-NOW production parser를 통과한다.
- live 호환성 감사의 `BLOCKER`와 `HIGH` finding이 해결되거나 명시적으로 수용된다.
- Kit, workspace, repository가 서로 분리된 QA 전용 경로다.

2026-08-06 현재 fresh onboarding State의 `required_next_action` 누락이 확인되어, 호환성 수정 전 native onboarding 성공 Gate는 차단 상태다.

## 증거 원칙

- 사용자가 실제로 클릭하고 관찰한 결과만 기록한다.
- 사용자 요청 없이 마우스·키보드 합성 입력을 사용하지 않는다.
- 각 항목은 `PASS`, `FAIL`, `NOT_EXECUTED`, `BLOCKED` 중 하나로 기록한다.
- 캡처에는 build SHA, 날짜, 선택한 runtime source, 프로젝트 표시명이 식별 가능해야 한다.
- token, secret, raw session ID, 사용자 개인정보, 전체 raw log는 캡처와 보고서에 포함하지 않는다.
- native 창 외에 PowerShell 콘솔이 노출되지 않아야 한다.

## 1. 앱 기동과 기본 레이아웃

- release app image 또는 검증 대상 Gradle build가 별도 PowerShell 창 없이 열린다.
- 메인 창이 응답 상태이며 제목이 `HRNS-NOW`다.
- 최소 지원 창 크기에서 탐색, 주 CTA, 확인 다이얼로그가 잘리지 않는다.
- 한국어와 영어 전환 시 레이아웃이 깨지지 않고 의미가 유지된다.
- 상단 활성 프로젝트 리본은 Registry의 활성 프로젝트가 있으면 `NONE`으로 떨어지지 않는다.

## 2. 프로젝트 등록

- 기본 runtime source는 source checkout 상대 `.local\harness-kit`을 의미하며, 설치본에서 임의 경로를 추측하지 않는다.
- 외부 Harness Kit은 고급 선택으로 명시한 절대 경로만 사용한다.
- 기본 profile ID는 표시 문자열이 아니라 `corp-default`다.
- Kit, workspace, repository의 동일 경로 및 양방향 포함 관계를 모두 거부한다.
- 존재하지 않는 경로, file 경로, 접근 불가 경로, incompatible manifest는 원인을 구분해 표시한다.
- `등록만`과 `등록 + 프로젝트 준비`가 서로 다른 동작임을 UI가 분명히 설명한다.

## 3. 쓰기 전 확인과 취소

- 프로젝트 준비 전에 확인 다이얼로그가 열린다.
- 다이얼로그가 repository bridge 3개와 external workspace 경로를 정확히 고지한다.
- 기존 bridge를 임의로 덮어쓰지 않는다는 설명이 보인다.
- 취소하면 Registry 이외의 repository/workspace 파일이 생성되지 않는다.
- 다이얼로그 ESC와 바깥 영역 클릭 동작이 문구와 일치한다.

## 4. 신규 온보딩

- `enter-project.ps1` 실행이 하나만 시작된다.
- repository에는 bridge 3개만 생성된다.
- workspace 오늘 날짜 폴더에는 required daily 4개가 생성된다.
- `REQUEST_STRUCTURED.md`와 legacy `WORK_QUEUE.json`/`WORKDAY_STATE.json` 부재가 실패 원인이 아니다.
- Validate-Ops JSON이 `overall=ok`이며 production adapter가 파싱한다.
- 생성된 `WORKFLOW_STATE.json`이 production State adapter에서 `Success`다.
- 성공은 exit 0, Validate-Ops, bridge, daily, State의 교집합으로만 표시된다.
- 등록 직후 `run-cycle.ps1` 또는 Bootstrap을 자동 실행하지 않는다.
- 준비가 실패해도 Registry entry는 유지되어 명시적으로 재시도할 수 있다.

## 5. 기존 프로젝트 복구

- bridge나 오늘 workspace가 부족한 활성 프로젝트에서만 `프로젝트 준비` CTA가 나타난다.
- Health Check만으로 repository/workspace 파일이 생성되지 않는다.
- 빠르게 두 번 클릭해도 실행과 진행 표시가 하나뿐이다.
- cancel/timeout/exception 뒤 process와 lock이 남지 않는다.
- 재실행은 기존 bridge를 보존하고 누락 artifact만 안전하게 준비한다.

## 6. Today work와 날짜 선택

- 오늘 required 4-file이 준비되면 요청 작성과 현재 State에 맞는 CTA가 표시된다.
- `REQUEST_INBOX.md` 저장은 낙관적 동시성 충돌을 감지하고 초안을 보존한다.
- UI는 `TODAY_STRATEGY.md`를 읽기만 한다.
- 과거 날짜에서는 read-only 조회만 허용하고 planning/execution/closure write CTA를 허용하지 않는다.
- 오늘 폴더가 없고 과거 폴더만 있으면 선택 이유와 쓰기 제한이 명확하다.

## 7. Planning·replan·execution

- request intake 상태에서 planning CTA가 State 정책과 일치한다.
- replan은 일반 planning과 다른 명시적 reason을 사용한다.
- active slice가 `code`이면 code wrapper, `doc`이면 doc wrapper만 허용한다.
- active pointer, authorized target, ops validation, wrapper가 불일치하면 실행을 차단한다.
- 실행 확인 화면에서 임의 target이나 wrapper를 입력할 수 없다.
- 실행 중 취소와 timeout 피드백이 명확하고, 종료 후 State 기반으로 CTA가 갱신된다.

## 8. Recovery와 Closure

- malformed/encoding/unsupported schema/access denied를 서로 다른 복구 안내로 표시한다.
- stop, blocked, failed reason의 unknown 값은 raw value를 보존하되 임의 성공으로 번역하지 않는다.
- Closure는 required artifacts, ops validation, queue, active slice, lock, Git 상태를 함께 판단한다.
- 정상 마감과 명시적 incomplete handoff를 구분한다.
- stdout 성공 문구만으로 마감을 완료 처리하지 않는다.

## 9. 보안과 진단 표시

- Doctor/Validate-Ops 결과는 정형화된 check와 안전한 요약만 표시한다.
- raw stdout/stderr, raw State JSON, raw session ID를 기본 화면에 노출하지 않는다.
- secret-shaped 값은 error, check message, snippet에서 masking된다.
- Registry에는 project metadata만 있고 token, secret, raw log가 없다.

## 10. 결과 기록

검증 보고서에는 다음을 포함한다.

```text
build SHA
실행 artifact 경로와 생성 시각
Windows/JDK 환경
Harness Kit 경로와 kit-version manifest
QA repository/workspace 경계
항목별 PASS/FAIL/NOT_EXECUTED/BLOCKED
사용자 캡처 또는 관찰 근거
발견 결함과 재현 절차
미실행 항목과 이유
Git 및 live Kit 무변경 확인
```

native QA는 모든 자동 Gate가 통과하고 필수 사용자 상호작용 증거가 모였을 때만 완료한다.
