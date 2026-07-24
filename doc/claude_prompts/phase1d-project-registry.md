# Claude 구현 프롬프트 — Phase 1D 프로젝트 Registry와 날짜 탐색

## 역할과 작업 규칙

당신은 `hrns_now`의 Phase 1D 구현 담당자다. 이번 작업은 프로젝트 Registry, 저장 전 boundary 검증, 다중 프로젝트 선택, 날짜 탐색까지로 제한한다.

- 기준 저장소: `S:\dev\project\hrns_now`
- 기준 브랜치: `harness-dev`
- 기준 문서: `doc/hrns_now_claude_plan.md`, `doc/hrns_now_design_pattern.md`
- 선행 보고서: `doc/phase_reports/phase1c-report.md`
- 두 기준 문서와 선행 보고서를 작업 전에 전체 읽는다.
- Claude는 Git commit, amend, rebase, reset, stash, clean, push를 수행하지 않는다.
- 구현·테스트·`doc/phase_reports/phase1d-report.md`만 working tree에 남긴다. 최종 검증·보정·커밋은 Codex가 수행한다.
- 기존 사용자 파일 `doc/hrns_now_packaging_plan.md`는 관련 없는 untracked 파일이므로 읽거나 수정·삭제·stage하지 않는다.
- Phase 2 JSON 계약, Phase 3 process/lock, Phase 4 실행·요청 저장, Phase 5 Closure를 선구현하지 않는다.

## 선행 Phase 1C의 확정 상태

Codex가 Claude의 Phase 1C 초안을 검증하면서 다음을 보정했다. 이 경계를 유지하고 회귀시키지 않는다.

1. `AppViewModel`은 AndroidX `ViewModel` + `viewModelScope`를 사용하며 단일 `StateFlow<HrnsUiState>`를 소유한다.
2. 화면 이벤트는 typed `HrnsUiEvent`, CTA identity는 typed `UiAction`으로 전달한다. 표시 label을 ID로 사용하지 않는다.
3. 파일 탐색, mtime 조회, artifact probe, `WorkflowStatePort.read`는 전부 IO dispatcher에서 실행한다. Composable에서 파일 I/O를 하지 않는다.
4. 조회 orchestration은 `core/usecase/LoadCockpitUseCase`, 화면 조립은 `CockpitUiStateAssembler`/`CockpitProjectionAssembler`, polling/lifecycle은 `AppViewModel`로 분리했다.
5. 수동 Refresh와 3초 mtime polling이 연결돼 있다. polling job은 하나이며, 변경 없는 tick이 진행 중인 수동 refresh를 무효화하지 않는다.
6. 실데이터 실패 시 mock fallback은 없다. demo는 명시적 `HRNS_DEMO_MODE`에서만 사용한다.
7. Phase 1C에서 실제 동작하는 CTA는 Refresh뿐이므로 다른 CTA는 표시하되 disabled다. Phase 1D에서 프로젝트 등록·선택 UI를 구현하면 해당 typed UI action만 실제 capability에 맞게 연결한다. process 실행 CTA는 계속 disabled다.
8. `core.projection` UI 모델은 `composeApp/presentation/model`로 이동했다.
9. 최종 계획과 live Harness는 `queue.active`에서 `card_id`/`slice_id` pointer만 보장한다. `wrapper`나 `authorized_target_file`을 `queue.active`에 다시 추가하거나 raw JSON에서 추측하지 않는다. Phase 2의 안정 계약 전까지 `activeSliceKind=null`로 fail-closed한다.
10. unknown enum 원문은 domain에 보존하지만 UI에는 raw 값을 표시하지 않는다. `state.blocked_reason`, schema raw version, session/token/secret 유사 원문도 화면에 직접 노출하지 않는다.
11. `CompatibilityStatus`는 Phase 2 전까지 `Unknown`을 유지한다. Phase 1D는 실제 boundary 결과만 공급하며 compatibility를 임의로 `Supported`로 승격하지 않는다.
12. Phase 1C 최종 로컬 검증은 core 36, infra 45, compose 20, 총 101 tests와 `gradlew check`가 통과했다.

## Phase 1D 목표

사용자가 여러 Harness 프로젝트를 안전하게 등록·전환하고 유효 날짜를 선택할 수 있게 한다. 앱 소유 Registry의 정본은 `%APPDATA%\hrns-now\projects.json`이며 Harness workspace에는 Registry나 UI 소유 파일을 만들지 않는다.

Registry 저장 전에 Kit, project workspace, repository 경계를 검증한다. 과거 날짜는 읽기 전용이고 write/execute CTA가 열리지 않아야 한다. 설정 해석 우선순위는 Registry → 환경변수 fallback → 사용자 선택이다.

## 필수 설계

### 1. Domain과 Port

`core`에 최소 typed 모델과 port를 둔다.

- `ProjectId`와 `HarnessProject`
- 표시명, Kit root, project workspace root, repository root, profile
- 마지막 선택 날짜, 마지막 진단 요약, 마지막 실행 시각은 계획서 범위에서 nullable typed 값으로 표현
- `ProjectRegistryPort`의 정본 API는 설계 문서 §10 그대로 사용:

```kotlin
interface ProjectRegistryPort {
    suspend fun findAll(): List<HarnessProject>
    suspend fun findById(id: ProjectId): HarnessProject?
    suspend fun save(project: HarnessProject)
    suspend fun delete(id: ProjectId)
}
```

JSON DTO, `%APPDATA%`, `Files`, Compose 타입을 core domain/port에 넣지 않는다. Registry 조회·저장 오류를 빈 목록이나 boolean으로 숨기지 말고 typed Result로 표현할 필요가 있으면 port 계약을 최소한으로 정교화하되, 위 repository 의미를 유지하고 설계 근거를 보고서에 남긴다.

### 2. Boundary Policy

`BoundaryPolicy`는 core의 순수 정책으로 두고 결과를 typed `ProjectBoundaryResult`/`BoundaryViolation`으로 반환한다. 문자열 에러 한 개로 축약하지 않는다.

저장 전에 다음 세 root 쌍의 양방향 포함 관계 6종을 모두 검사한다.

- workspace inside repository / repository inside workspace
- kit inside repository / repository inside kit
- kit inside workspace / workspace inside kit

추가 요구:

- blank/invalid path, 동일 경로, 존재하지 않음, 디렉터리 아님, 읽기 불가를 명시적으로 처리
- lexical absolute normalized path 비교
- 가능한 모든 기존 경로는 `toRealPath()`로 비교해 junction/symlink 우회를 차단
- Windows 경로의 대소문자 특성을 고려
- boundary가 `Valid`일 때만 Registry save 허용
- validation과 save 사이에 경계가 무시되는 우회 API를 만들지 않음
- `BoundaryStatus.Valid`은 이 실제 결과에서만 만들고 임의 상수로 주입하지 않음

파일 실재·real path 확인은 infra adapter/gateway가 담당하고, 순수 policy에는 정규화된/실경로 후보와 검사 결과를 입력하는 식으로 계층을 분리한다. 테스트 가능성을 위해 과도한 범용 filesystem abstraction은 만들지 않는다.

### 3. JSON Registry Adapter

`infra/registry/JsonProjectRegistryAdapter`를 구현한다.

- Registry 경로는 composition root에서 `%APPDATA%\hrns-now\projects.json`으로 계산해 주입한다. domain/adapter가 환경변수를 직접 전역 조회하지 않는다.
- UTF-8 without BOM
- 같은 디렉터리의 temp 파일에 완전 기록하고 flush/close 후 atomic move. `ATOMIC_MOVE` 미지원 fallback도 안전하게 처리
- 저장 중 실패하면 기존 정상 Registry를 보존
- 손상 JSON을 조용히 빈 목록으로 바꾸지 않음
- 손상 복구 시 원본 bytes를 보존하거나 복구 가능한 backup/quarantine을 만든 뒤 typed corruption 결과를 UI에 전달
- unknown JSON field에 내성을 갖되 필수 project 필드 누락을 기본값으로 은폐하지 않음
- 동시 save로 엔트리를 잃지 않도록 read-modify-write를 한 adapter 인스턴스 내에서 직렬화하고 테스트
- Registry 및 backup/temp에 secret, token, raw session ID, 응답 원문, raw log를 저장하지 않음
- Harness workspace, repository, Kit root 아래에는 Registry/temp/lock을 만들지 않음

### 4. Application Use Case와 선택 우선순위

ViewModel에 Registry parsing, path boundary 규칙, atomic write를 넣지 않는다. `core/usecase`의 작은 use case로 다음 흐름을 조정한다.

- 프로젝트 목록 로드
- 프로젝트 후보 검증 후 저장
- 프로젝트 선택·전환
- 삭제가 필요하면 명시적 사용자 이벤트와 typed 결과로 처리
- 선택 source 우선순위: Registry의 마지막 선택 → 기존 `EnvironmentWorkspaceConfigProvider` fallback → 사용자 선택 필요

현재 `LoadCockpitUseCase`가 생성 시점의 단일 `WorkspaceConfig`를 보유한다. 다중 프로젝트 전환을 위해 선택된 `HarnessProject`/config를 명시적으로 입력받도록 최소 변경하되 ViewModel이 다시 God Object가 되지 않게 한다. Registry가 비었거나 손상됐을 때 환경변수 fallback을 mock 성공처럼 취급하지 말고 source를 UI state에 명확히 표현한다.

### 5. 날짜 탐색과 읽기 전용 규칙

기존 `WorkspaceDayDiscovery`와 `WorkspaceDaySelectionPolicy`를 재사용한다.

- 유효한 `yyyy-MM-dd` 디렉터리만 노출
- 명시 선택 > 오늘 > 최신 읽기 전용 fallback
- 과거 날짜에는 읽기 전용 배지
- 과거 날짜에서 Registry metadata 갱신 외 Harness write/execute CTA 금지
- 프로젝트를 전환하면 선택 날짜, artifact probe, State reader, mtime probe가 모두 같은 `WorkspaceDay`를 사용
- 한글·공백·drive letter 경로를 지원하고 production에 특정 프로젝트/날짜를 하드코딩하지 않음

### 6. Presentation/MVVM

- Registry·프로젝트 선택·날짜 선택 상태를 `HrnsUiState`에 통합
- `HrnsUiEvent`를 통해 등록, 선택, 날짜 선택, 새로고침을 전달
- Registry/file I/O는 IO dispatcher
- polling은 프로젝트/날짜 전환 시 이전 대상 job이 중복되거나 낡은 결과를 새 선택 위에 덮지 않게 함
- Compose는 state를 렌더링하고 typed event만 전달
- 단일 권장 행동 강조와 Phase 1B fail-closed 결과 유지
- Registry/환경변수/사용자 선택 중 어떤 source가 적용됐는지 사용자가 알 수 있게 표시
- raw path가 필요한 Setup 화면 외에는 secret/session/raw diagnostics를 노출하지 않음

## 필수 테스트

최소 다음을 자동화한다.

1. Registry round-trip, 여러 프로젝트 findAll/findById/save/delete
2. UTF-8 no BOM과 한글·공백·drive-letter 경로
3. temp + atomic move 성공, move/write 실패 시 기존 파일 보존
4. corrupt/truncated JSON의 typed 오류와 원본/backup 보존 복구
5. unknown field 허용, 필수 필드 누락 거부
6. secret/session/token/raw log가 Registry DTO에 들어갈 수 없는 구조 및 저장 결과 비포함
7. boundary 양방향 6종, 동일 경로, invalid/missing/not-directory/not-readable
8. junction/symlink 또는 injectable real-path 동등 테스트
9. boundary 실패 시 save port 미호출
10. Registry → 환경변수 → 사용자 선택 우선순위
11. 다중 프로젝트 전환과 동일 `WorkspaceDay` 사용
12. 오늘/과거 날짜 선택 및 과거 날짜 실행 CTA 금지
13. 프로젝트 전환 중 늦게 끝난 이전 load가 새 프로젝트 상태를 덮지 않음
14. Registry와 날짜 탐색 I/O가 UI dispatcher에서 실행되지 않음
15. Phase 1A Reader, Phase 1B exact CTA 결정표, Phase 1C polling/raw 비노출 회귀

실제 task를 확인해 targeted → module → 전체 순으로 실행한다.

```powershell
.\gradlew.bat :core:test
.\gradlew.bat :infra:test
.\gradlew.bat :composeApp:jvmTest
.\gradlew.bat check
```

실패를 skip하거나 계약을 약화해 통과시키지 않는다. thread race 테스트는 `Thread.sleep`/실시간 delay에 의존하지 말고 latch, test dispatcher, fake gateway 등 결정적 동기화를 사용한다.

## 금지 사항

- `WORKFLOW_STATE.json` 쓰기
- Registry/임시/backup 파일을 Harness workspace에 저장
- raw session ID, secret, token, response, raw log 저장·표시
- boundary 검사 후 우회 저장
- lexical path만 검사하고 junction/symlink 무시
- Registry 오류 시 demo/mock fallback
- 과거 날짜를 오늘처럼 실행 가능 처리
- `queue.active.wrapper` 또는 `queue.active.authorized_target_file` 재도입
- compatibility를 Phase 2 전에 Supported로 가정
- ProcessBuilder, PowerShell command, lock, request writer, Closure 구현
- `-RunExecutionWrapper validation`, 자동 resume, `--continue`
- 관련 없는 리팩터링·대규모 포맷
- Git commit/push

## 완료 보고서

`doc/phase_reports/phase1d-report.md`를 UTF-8 without BOM으로 작성한다.

보고서에 반드시 포함:

- 변경 파일과 설계 책임
- Registry schema와 저장 경로
- atomic write/손상 복구 semantics
- boundary 6종 및 real path 처리
- 선택 우선순위와 날짜 정책
- Phase 1C Codex 보정사항의 유지 여부
- 테스트 명령과 실제 결과/건수
- 미구현 후속 Phase 경계
- Claude가 commit하지 않았다는 명시

구현 완료 선언만 하지 말고 working tree의 실제 소스와 테스트 결과를 근거로 보고한다.
