# Claude 작업 지시 — 새 Phase 7: 내장 개발용 Harness SDK runtime source

## 역할·현재 Phase·Git

당신은 HRNS-NOW의 **새 Phase 7 내장 개발용 Harness SDK runtime source 구현 담당자**다. 일반 프로젝트 등록에서 외부 Harness Kit 경로를 직접 지정해야 하는 개발자 중심 흐름을 개선한다. 사용자가 HRNS-NOW source checkout 안에 제공한 Git-ignore 개발 SDK를 표준 runtime source로 선택하되, 대상 project repository와 Harness workspace는 계속 외부 root로 유지한다.

저장소는 `S:\dev\project\hrns_now`, branch는 `harness-dev`다. 모든 Git commit은 Codex만 수행한다. `git add`, `commit`, `amend`, `rebase`, `reset`, `stash`, `clean`, `push`를 수행하지 않는다. 완료 보고서는 `doc/phase_reports/phase7-internal-sdk-report.md`에 **UTF-8 without BOM**으로 작성한다.

현재 HEAD에는 다음 Codex 커밋이 있다.

```text
b8ee279 fix: Phase 6 UI UX 피드백과 용어 개선
```

새 Phase 6(G6-UX)는 자동 검증과 MSI 재패키징은 통과했지만 native 창의 수동 QA 증빙이 남아 `BLOCKED`다. 제품 소유자는 이를 PASS로 바꾸지 않은 채, 2026-07-28에 이 Phase 7을 병행하도록 명시 승인했다. 이 예외를 보고서에 기록하되 G6-UX/G6A/G6B 또는 보류 기존 Phase 7E의 PASS를 주장하지 않는다.

## 반드시 먼저 읽을 문서

- `doc/hrns_now_claude_plan.md` — §0.4, §0.5, 새 Phase 7, 보류 6A/6B/7E 경계
- `doc/hrns_now_design_pattern.md` — §3, §10, §18~20 및 §20.1 RuntimeSource/Resolution 규범
- `README.md` — 최신 제품 상태·사용자 경계
- `doc/phase_reports/phase6-uiux-report.md`
- `doc/phase_reports/phase6b-report.md`
- `doc/claude_prompts/phase6-uiux-qa-improvement.md`
- `doc/claude_prompts/phase6b-bundled-runtime.md` — 차이점과 금지 범위를 확인하기 위한 참고
- 이 문서

`doc/hrns_now_packaging_plan.md`는 사용자 소유 untracked 설계 입력이다. 읽을 수는 있으나 수정·삭제·stage하지 않는다.

## 제품 결정과 정확한 경계

### 개발 SDK의 위치와 성격

canonical 위치는 **HRNS-NOW repository-relative** `.local\harness-kit\`이다. `.gitignore`에 이미 등록되어 있으며 사용자가 별도로 제공하는 개발 checkout이다.

이 디렉터리는 다음이 아니다.

- Git으로 추적되는 HRNS-NOW source
- 승인된 Harness release artifact
- MSI/release distributable에 포함할 Runtime
- `D:\harness-kit`을 자동 복사한 캐시
- Harness가 write하는 workspace/log/scratch 위치

따라서 다음 행위는 절대 하지 않는다.

- `D:\harness-kit` 수정·복사·junction/symlink 생성·zip backup·Git 작업
- `.local\harness-kit` 자동 생성·자동 복사·자동 update
- 개발 SDK를 MSI, app resources, build staging, Git index에 포함
- Harness script/template/manifest/checksum을 HRNS-NOW가 창작·수정
- `.git`, `.claude`, `logs`, `scratch`, fixture, secret/token/session/raw log를 SDK 입력으로 허용하거나 복사

SDK checkout 자체는 사용자가 준비한다. 이 Phase는 **SDK source 선택·안전한 해석·UX**를 구현하는 작업이지 Runtime 배포 작업이 아니다.

### 외부 root 소유권

```text
HRNS-NOW source root\.local\harness-kit\  = 개발용 SDK runtime source (Git ignore, read/execute)
target project repository                   = 사용자 Git 소유, 외부 root
Harness project workspace                   = Harness 산출물 root, 외부 root
%APPDATA%\hrns-now                         = Registry/config
%LOCALAPPDATA%\hrns-now                    = lock·비민감 앱 데이터
```

SDK/runtime root, target repository root, workspace root의 normalized absolute path와 가능한 real path는 계속 상호 포함·동일 관계가 없어야 한다. UI/SDK가 `WORKFLOW_STATE.json`, daily 4-file, Registry, lock, logs를 직접 만들거나 쓰면 안 된다.

## 목표 UX

1. 사용자는 표준 `프로젝트 관리` 등록 흐름에서 Kit root를 입력하지 않는다.
2. `.local\harness-kit`이 정상적으로 해석되면 `개발용 내장 SDK`라는 source와 가용성·호환성 상태만 본다.
3. SDK가 없거나 필요한 entrypoint/manifest를 읽을 수 없으면 원인을 보되, demo fallback 없이 실행·등록은 fail-closed로 잠긴다.
4. 외부 Harness Kit은 **고급 설정**에서 사용자가 명시적으로 `외부 Harness Kit 사용`을 선택한 경우에만 path 입력과 선택이 나타난다.
5. 기존 Registry의 external `kit_root` 값은 손실 없이 계속 동작한다. 내장 SDK 또는 환경변수가 이를 묵시적으로 덮어쓰지 않는다.

## 구현 범위

### 1. Typed runtime source와 Resolver

- `doc/hrns_now_design_pattern.md` §20.1을 따르는 sealed/typed `RuntimeSource` 및 `RuntimeResolution`을 도입한다. 이름은 현재 코드 관례에 맞춰 조정할 수 있으나 `InternalDeveloperSdk`, explicit external root, `Resolved/Missing/Invalid`의 의미는 유지한다.
- `HarnessProject`와 Registry DTO가 raw `kitRoot: Path`만을 정본으로 삼지 않게 정리한다. 프로젝트는 runtime source selection을 저장하고, command/compatibility/boundary에는 **오직 resolved root**가 전달되게 한다.
- `InternalDeveloperSdk` Registry 저장값은 source 종류만 보존한다. source checkout 위치의 절대 경로를 Registry에 저장하지 않는다.
- repository-relative `.local\harness-kit` 경로 계산은 composition root 또는 작은 infra resolver에만 둔다. Composable, ViewModel, domain policy, command encoder에 경로 문자열·`Program Files`·PowerShell 인자 조립을 넣지 않는다.
- existing `kit_root`만 가진 Registry entry는 명시적으로 `ExternalKit(existingPath)`으로 migration한다. JSON schema를 불필요하게 대폭 올리지 말고 optional field와 backward-compatible parser를 우선한다. migration 과정에서 Registry 원자성·손상 quarantine·UTF-8/BOM 계약을 약화하지 않는다.
- `HRNS_KIT_ROOT` 등 기존 환경변수는 legacy/external fallback으로만 유지할 수 있다. 저장된 `InternalDeveloperSdk` 또는 explicit `ExternalKit` selection을 무단으로 override하면 안 된다. `D:\harness-kit`이나 사용자 경로를 production code에 hardcode하지 않는다.

### 2. Runtime availability·compatibility·boundary 연결

- runtime resolver는 local SDK root의 존재와 실제로 필요한 Kit entrypoint/`kit-version.json`을 안전하게 확인한다. 현재 Harness 실계약의 `doctor.ps1`, `validate-ops.ps1`, `run-cycle.ps1` 외 임의 wrapper/state를 창작하지 않는다.
- availability/integrity와 `CompatibilityPolicy`는 구분한다. resolver의 Missing/Invalid은 별도 fail-closed 원인이고, `KitVersionManifestPort`→`CompatibilityPolicy`는 해석에 성공한 root에서 계속 동작한다.
- `BoundaryPolicy`/실경로 검사는 해석된 runtime root와 external workspace/repository 사이에서 실행한다. `.local` SDK가 HRNS-NOW source 내부라는 이유로 target project를 HRNS-NOW source 안에 등록하도록 허용하면 안 된다.
- typed HarnessCommand/encoder/runner는 이미 해석된 root만 전달받는다. 각 command에 internal/external 분기를 흩뿌리지 않는다.
- unknown/missing/malformed runtime은 실행 CTA를 잠그고 한국어 진단을 표시한다. 실제 State read 실패를 mock/demo로 대체하지 않는다.

### 3. 프로젝트 관리 UX

- 표준 신규 등록 modal/form에서 Kit root text field를 제거한다. source 카드에 `개발용 내장 SDK` 상태·호환성·부재 원인을 보여준다.
- `외부 Harness Kit 사용`은 고급 선택을 명시한 뒤에만 path field를 보여준다. action label이 runtime source 식별자가 되면 안 된다.
- 기존 external project는 project summary/modal에서 `외부 Harness Kit` source를 명확히 표시하고 기존 경로를 보존한다.
- source 전환, 등록 실패, SDK missing 상태가 active project/Registry를 조용히 지우거나 다른 프로젝트로 바꾸지 않게 한다.
- UI-local state 외 파일 I/O, manifest parsing, path resolution, PowerShell 실행은 Composable에 넣지 않는다. `AppViewModel`은 event/use case/`StateFlow` 조립만 담당한다.

### 4. 기존 계약 보존

- `WORKFLOW_STATE.json` 단일 진실, UI 직접 write 금지, required daily 4-file, optional log·`REQUEST_STRUCTURED.md`, legacy fallback 분리는 유지한다.
- `ActionPolicy`, `ClosurePolicy`, `CompatibilityPolicy`, `BoundaryPolicy`의 판정 자체를 UI 편의로 완화하지 않는다.
- `ExecuteHarnessActionUseCase`의 policy 재검증 → typed command → lock → runner → lock 보유 중 State reread → release 흐름을 유지한다.
- 자동 resume/`--continue`, Claude API 직접 호출, raw session ID/secret/token/raw log 저장·표시를 추가하지 않는다.
- MSI/Gradle package 설정, bundled JRE, Program Files data 경계, 보류 6A/6B staging은 수정하지 않는다.

## 테스트와 검증

다음 테스트를 추가하거나 보강한다. 테스트를 삭제·skip·약화해 통과시키지 않는다.

1. **core**: InternalDeveloperSdk/external source typed decision, resolved/missing/invalid fail-closed, stored explicit source가 fallback에 덮어써지지 않는지.
2. **infra**: repository-relative developer SDK resolver, required entrypoint·manifest missing/invalid, 공백·한글 path, junction/symlink를 포함한 boundary 입력, Registry legacy `kit_root` migration·새 source 직렬화·UTF-8 BOM·손상 recovery.
3. **composeApp/ViewModel**: standard registration UI model이 Kit 입력 없이 internal source를 선택하는지, advanced external 선택에만 field가 노출되는지, missing SDK가 실행 CTA를 잠그고 demo fallback하지 않는지, active/external legacy project 표시.
4. **회귀**: command mapper/encoder, compatibility handshake, registry 선택 순서, ActionPolicy fail-closed, Phase 6 action feedback/requirements modal 회귀.

실제 `.local\harness-kit`이 이 작업 환경에 없다면 fake fixture/temp directory로 resolver와 UI 상태를 검증하고, 실제 Kit 실행은 하지 않는다. `D:\harness-kit`을 복사해 테스트 fixture로 만들지 않는다.

다음 순서로 실행한다.

```powershell
.\gradlew.bat :core:test
.\gradlew.bat :infra:test
.\gradlew.bat :composeApp:jvmTest
.\gradlew.bat check
```

MSI packaging은 build 설정을 바꾸지 않았다면 재실행하지 말고 그 사유를 보고한다. generated runtime, `.local\harness-kit`, build output, log, Registry, fixture workspace를 Git에 추가하지 않는다.

## 보고서

`doc/phase_reports/phase7-internal-sdk-report.md`에 다음을 기록한다.

- 실제 local SDK 존재 여부와 **자동 copy 하지 않은** 사실
- 변경 파일, typed runtime source/resolver/Registry migration/UX 흐름
- external project·legacy Registry 호환성, runtime/workspace/repository ownership 및 boundary 근거
- SOLID, Ports and Adapters, Resolver/Repository/Composition Root 판단
- 테스트·수동 QA·미실행 검증과 정확한 사유
- `D:\harness-kit`, `doc/hrns_now_packaging_plan.md`, MSI/staging/G6A/G6B/기존 7E를 수정하지 않은 사실
- Git 작업 없음, Codex 독립 검증과 Gate 판정 필요

완료 보고에는 구현 요약, 실패 안전 동작, 테스트 출력, residual risk를 명확히 남긴다. G7-SDK PASS, Phase 6 수동 QA 완료, 보류 G6A/G6B/7E 재개 여부는 Codex만 판정한다.
