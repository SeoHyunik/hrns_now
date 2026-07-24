# Phase 1D 프로젝트 Registry와 날짜 탐색 검증·보정 보고서

검증일: 2026-07-24
검증자: Codex
대상 저장소: `S:\dev\project\hrns_now`
브랜치: `harness-dev`
검증 전 HEAD: `06d6d84` (`fix: Phase 1C 실데이터 Cockpit 안전성 보강`)
Claude 커밋: 없음 — Claude는 working tree만 작성하고 Codex만 커밋한다.

---

## 진척도

- 대상 Phase: Phase 1D — 프로젝트 Registry와 날짜 탐색
- Verdict: PASS_WITH_FIXES
- 다음 Phase 진행 가능: 예
- NEXT_ALLOWED_PHASE: Phase 2 — Harness Kit 기계 판독 표면과 UI compatibility handshake

## 1. 검증 대상

- Claude 산출물: `doc/phase_reports/phase1d-report.md` 최초본과 Phase 1D working tree 전체
- 기준 계획: `doc/hrns_now_claude_plan.md` Phase 1D, §2.4~2.5, §3.4, Gate G1
- 설계 기준: `doc/hrns_now_design_pattern.md`의 Hexagonal Architecture, Repository, Boundary Policy, MVVM/UDF, Result/Projection, God ViewModel 금지 규칙
- 선행 Gate: Phase 1C Codex 커밋 `06d6d84`의 PASS_WITH_FIXES 확인
- Phase 식별 방식: 사용자가 `doc/phase_reports/phase1d-report.md`를 명시했고 working tree 변경 범위도 Phase 1D와 일치함
- 주요 검토 파일:
  - `core/domain/model/{HarnessProject,ProjectId,BoundaryModels}.kt`
  - `core/domain/policy/BoundaryPolicy.kt`, `core/port/ProjectRegistryPort.kt`, `core/result/RegistryResult.kt`
  - `core/usecase/{LoadCockpit,LoadProjects,RegisterProject,SelectProject,SelectWorkspaceDay,DeleteProject,ResolveActiveProject}UseCase.kt`
  - `infra/registry/{JsonProjectRegistryAdapter,ProjectRegistryDto,RealPathGateway}.kt`
  - `composeApp/.../App.kt`, `presentation/**`, `ui/{Components,Screens,Shell}.kt`
  - Phase 1D 신규·회귀 테스트
- live 후속 저장소 사전 확인: `D:\harness-kit`은 파일 tree는 존재하지만 2026-07-24 현재 `.git` metadata가 없음

## 2. 핵심 판정

Claude 초안은 typed `HarnessProject`/`ProjectId`, Repository port, 순수 `BoundaryPolicy`, `%APPDATA%\hrns-now\projects.json` adapter, 다중 프로젝트 UI 골격과 Registry 우선 선택을 구현했다. 세 root의 양방향 포함 6종과 동일 경로·real path 비교도 계획 방향에 맞았다.

그러나 최초 상태로는 등록 후 목록이 갱신되지 않았고, 비활성 프로젝트 삭제도 stale 목록을 유지했다. 선택의 `markActive` 실패와 삭제 write 실패를 무시해 UI가 실패를 성공으로 표시할 수 있었다. 날짜 선택 event/UI가 없어 `last_selected_date`는 저장만 가능한 미사용 필드였고, 일부 손상 entry가 있는 Registry에서 mutation하면 손상 원본을 quarantine하지 않은 채 제외할 수 있었다. 손상 조회도 정본을 복구하지 않아 매 조회마다 backup을 반복 생성했다. 또한 Phase 1C에서 고친 no-op polling sequence 경쟁이 되돌아갔고, Linux CI에서 Windows 역슬래시만 사용한 boundary 테스트가 의미를 잃는 문제가 있었다.

Codex는 Registry mutation 결과를 typed로 전달하고 목록을 모든 mutation 뒤 재조회하도록 보정했다. 등록은 활성 선택까지 영속화한 뒤에만 UI active 상태로 반영하며, 삭제/선택 실패를 성공으로 위장하지 않는다. 유효 `yyyy-MM-dd` 폴더 목록과 typed 날짜 선택 event를 추가하고, 과거 날짜를 동일 `WorkspaceDay`로 Reader/artifact에 전달하며 Registry metadata를 갱신한다. 손상 원본은 mutation 전에 보존하고 유효 entry로 정본을 원자 재작성한다. Registry 정본·temp·backup이 등록 프로젝트 root 아래 생기는 구성은 읽기와 mutation에서 fail-closed한다.

최종 구현은 Registry → 환경변수 fallback → 사용자 선택 우선순위, 저장 전 boundary 차단, 다중 프로젝트 전환, 유효 날짜 탐색과 과거 날짜 read-only를 충족한다. targeted, 세 모듈, 전체 `check`가 통과했으므로 Gate G1을 통과시키고 Phase 2 진입을 허용한다.

## 3. 발견 사항

### Critical

- **Registry mutation 실패를 성공으로 표시하고 UI 목록이 stale 상태로 남음** — `ProjectRegistryPort.delete`가 `Unit`이었고 `SelectProjectUseCase`가 `markActive` 결과를 무시했다. 등록·비활성 삭제 후 `registryProjects`도 다시 읽지 않았다. `core/port/ProjectRegistryPort.kt`, `core/usecase/{SelectProject,DeleteProject,LoadProjects}UseCase.kt`, `composeApp/.../AppViewModel.kt`를 typed 결과와 재조회 흐름으로 보정했다.
- **부분 손상 Registry mutation에서 원본 entry 유실 가능** — 최초 adapter는 `LoadOutcome.Loaded(droppedEntryCount > 0)`를 정상 snapshot처럼 사용해 손상 entry를 backup 없이 제거했다. 모든 mutation 전에 원본 bytes를 quarantine하고, 조회 복구 시 유효 entry로 정본을 재작성하도록 `infra/registry/JsonProjectRegistryAdapter.kt`를 수정했다.

### Major

- **날짜 탐색 UI와 typed event 누락** — `WorkspaceDayDiscovery`를 호출했지만 명시 날짜를 전달할 API와 화면 선택 목록이 없어 Phase 1D 필수 흐름을 완료할 수 없었다. `WorkspaceDayResolution`, `WorkspaceDaySelected`, `WorkspaceDayItem`, `SelectWorkspaceDayUseCase`와 Setup 날짜 목록을 추가했다.
- **Registry 손상/읽기 오류 출처가 UI에서 소실** — `ResolveActiveProjectUseCase`가 프로젝트 목록만 전달해 `RecoveredFromCorruption`/`Unreadable`을 표시할 수 없었다. typed load 결과를 resolution에 보존하고 안전한 사용자 메시지로 투영했다.
- **Phase 1C polling 경쟁 회귀** — `loadSequence`를 실제 read 이전이 아니라 모든 poll 시작 시 증가시켜 변경 없는 tick이 진행 중 refresh를 무효화할 수 있었다. 프로젝트/날짜 context generation과 실제 read sequence를 분리하고 latch 기반 회귀 테스트를 추가했다.
- **UI 소유 Registry가 프로젝트 root 아래 생성될 수 있음** — `%APPDATA%` 오구성 시 정본·temp·backup이 Kit/Workspace/Repository 아래 놓이는 것을 막는 방어가 없었다. 신규 후보와 기존 Registry entry 모두에 대해 lexical/가능한 real-path 기준으로 fail-closed한다.
- **Ubuntu CI 경로 테스트 비이식성** — core boundary 테스트의 Windows 역슬래시 경로는 Linux에서 하나의 filename으로 취급돼 포함 관계를 검증하지 못한다. 순수 정책 테스트는 host path separator를 사용하고, Registry DTO는 Windows drive-letter/UNC 절대 경로를 host와 무관하게 인식하도록 수정했다.

### Minor

- 빈 표시명/Profile 후보를 use case 수준에서 거부하지 않아 UI 우회 호출로 불완전 entry를 만들 수 있어 typed `InvalidCandidate`를 추가했다.
- 잘못된 `schema_version`, optional 날짜/시각, 중복 project ID를 정상값처럼 축약하지 않고 손상 복구 대상으로 분류했다.
- 최초 atomic failure 테스트는 다른 target의 실패만 확인해 기존 대상 보존을 증명하지 못했다. move 협력자를 좁게 주입해 같은 target의 move 실패에서도 기존 bytes와 temp 정리를 검증했다.

## 4. SOLID·설계 패턴 평가

| 항목 | 판정 | 근거 |
|---|---|---|
| SRP | PASS | Boundary 계산, real-path 확인, Registry 저장, 프로젝트/날짜 use case, UI projection과 lifecycle이 분리됨 |
| OCP | PASS | typed Result와 DTO mapper가 손상·unknown field·schema 확장을 중앙 처리하고 UI 문자열 분기를 만들지 않음 |
| LSP | PASS | fake/real Registry port가 save/delete/markActive 실패 의미를 동일하게 전달하며 거짓 성공을 반환하지 않음 |
| ISP | PASS | Registry port와 조회/등록/선택/삭제 use case가 process/lock/Compose API를 포함하지 않음 |
| DIP | PASS | core가 JSON·`Files`·`%APPDATA%`를 모르고 composition root가 infra adapter와 경로를 주입 |
| 계층 의존 방향 | PASS | `Compose → core usecase/port ← infra adapter` 방향과 presentation projection 경계를 유지 |
| 패턴 적정성 | PASS | Repository, Boundary Policy, Result, MVVM/UDF, Adapter를 Phase 1D 책임에 한정해 적용 |
| 과도한 추상화 | PASS | 별도 application 모듈·범용 filesystem 계층 없이 기존 모듈과 좁은 함수/port를 재사용 |

## 5. 수행한 수정

- `core/domain/**`, `core/port/ProjectRegistryPort.kt`, `core/result/RegistryResult.kt`
  - typed project/boundary/Registry 결과 모델 정렬
  - delete와 active 선택 실패를 숨기지 않는 계약
- `core/usecase/**`
  - boundary 통과 후에만 등록, 빈 후보 사전 거부
  - 목록 load, active 선택, typed 삭제, 마지막 날짜 metadata 갱신
  - active resolution에 Registry load 결과 보존
  - 명시 날짜가 탐색된 유효 폴더일 때만 선택하고 최신순 목록 제공
- `infra/registry/**`
  - UTF-8 no BOM, temp + atomic move, 같은 target move 실패 시 기존 파일 보존
  - 전체/부분 손상 원본 quarantine 후 유효 정본 재작성, 중복 backup 방지
  - schema/필수·optional typed field/중복 ID 검증
  - Registry 정본·temp·backup의 프로젝트 root 내부 배치 차단
  - Windows drive-letter/UNC 경로의 non-Windows CI 판독
- `composeApp/presentation/**`, `ui/**`, `App.kt`
  - 등록/선택/삭제/날짜 선택 typed event와 단일 StateFlow 반영
  - mutation 후 Registry 목록 재조회, source와 corruption/error 표시
  - 유효 날짜 목록과 과거 날짜 읽기 전용 badge
  - Registry/date/filesystem 협력자를 IO dispatcher에서 실행
  - project/day context generation과 실제 read sequence 분리
- 테스트
  - typed mutation 실패, 목록 갱신, 마지막 날짜 저장, 동일 WorkspaceDay, no-op poll 경쟁
  - 손상 정본 복구, mutation 전 원본 보존, target move 실패, 소유 경계, schema/optional field
  - host-independent boundary 정책과 Windows 경로 Registry round-trip

부작용 검토: `WORKFLOW_STATE.json` 쓰기, ProcessBuilder, PowerShell command, lock, request writer, Closure를 추가하지 않았다. `CompatibilityStatus`는 Phase 2 전까지 `Unknown`, process 상태는 `Idle`을 유지한다. 관련 없는 사용자 파일 `doc/hrns_now_packaging_plan.md`는 읽기·수정·stage하지 않았다.

## 6. 검증 결과

| 검증 | 명령 | 결과 |
|---|---|---|
| Targeted | `:core:test --tests ProjectSelectionUseCaseTest`, `:infra:test --tests JsonProjectRegistryAdapterTest`, `:composeApp:jvmTest --tests AppViewModelTest` | PASS — 3 + 15 + 13 = 31 tests |
| Module | `:core:test`, `:infra:test`, `:composeApp:jvmTest` | PASS — core 66, infra 60, Compose 26, 총 152 tests |
| Full | `.\gradlew.bat check --rerun-tasks --no-daemon --console=plain` | PASS — 152 tests, 실패/skip 0 |
| CI/Smoke | GitHub Actions 원격 실행 | 미실행 — push하지 않음. 로컬 전체 check와 Ubuntu CI용 순수 경로 계약을 검증 |

초기 세 Gradle invocation의 병렬 실행은 동일 working tree의 compiler worker 경합으로 timeout됐고 검증 결과로 사용하지 않았다. 잔존 Java process 종료를 확인한 뒤 모든 명령을 순차 재실행해 위 PASS 결과를 얻었다.

정적 검사:

- production의 `ProcessBuilder`, `--continue`, fake validation wrapper, queue.active 미보증 필드: 0건
- `WORKFLOW_STATE.json` write 경로: 0건
- Registry/backup/temp의 Harness workspace 의도적 생성 경로: 0건, 오구성 차단 테스트 PASS
- `git diff --check`: whitespace 오류 0건(CRLF 변환 안내만 존재)

## 7. Git 상태와 커밋

- 작업 전 상태: Phase 1D 소스·테스트·보고서가 수정/untracked인 Claude working tree, 별도 사용자 untracked 파일 `doc/hrns_now_packaging_plan.md`
- Claude 커밋: 없음
- Codex 보정 커밋: 본 보고서와 Phase 2 prompt를 포함한 별도 커밋으로 생성
- 커밋 메시지: `fix: Phase 1D Registry와 날짜 선택 안전성 보강`
- 미커밋 잔여: 사용자 파일 `doc/hrns_now_packaging_plan.md`만 보존 예정
- push 여부: 수행하지 않음
- 최종 SHA: Codex 최종 응답에 기록

## 8. 잔여 위험

- `D:\harness-kit` live tree에는 2026-07-24 현재 `.git` metadata가 없다. Phase 2 수정 전 canonical Git worktree/커밋 대상 확인이 필요하며, 확인되지 않으면 Claude는 live tree를 수정하지 않고 BLOCKED 보고해야 한다.
- Registry 동시성 보장은 현재 adapter 인스턴스 내부 `Mutex` 범위다. HRNS-NOW 다중 인스턴스와 외부 프로세스 상호배제는 Phase 3 lock 범위다.
- `CompatibilityStatus`는 Phase 2의 `kit-version.json` reader/policy가 연결되기 전까지 `Unknown`으로 실행을 잠근다.
- 원격 GitHub Actions는 push 금지 규칙에 따라 실행하지 않았다.

## 9. 다음 단계

- NEXT_ALLOWED_PHASE: Phase 2 — Harness Kit 기계 판독 표면과 UI compatibility handshake
- Claude에게 전달할 다음 작업: `doc/claude_prompts/phase2-harness-json-contract.md`
- 다음 Phase 진입 전 조건: `D:\harness-kit`의 canonical Git worktree를 확인하고, 본 Codex 커밋을 HRNS-NOW 기준 HEAD로 유지할 것
- Phase 2에서 doctor/validate-ops JSON, kit-version, compatibility policy, smoke/docs 연쇄 갱신만 수행하고 Phase 3 process/lock이나 Phase 4 실행을 선구현하지 말 것