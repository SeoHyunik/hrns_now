# HRNS-NOW Kotlin 아키텍처·디자인 패턴 규범

- **작성일:** 2026-07-23
- **문서 지위:** 본 문서는 HRNS-NOW의 Kotlin 설계·문법 규범(normative)이다. 모든 Phase 구현과 코드 리뷰는 이 문서를 기준으로 판단한다.
- **검증 기준:** live `S:\dev\project\hrns_now`(harness-dev, Codex 검토 반영분 포함), live `D:\harness-kit` 실계약, `doc/hrns_now_claude_plan.md`(최종 계획서)
- **상위 문서:** 설계 원칙이 충돌할 경우 `doc/hrns_now_claude_plan.md`의 불변 원칙(2장)과 Phase 계약(4장)이 우선한다.

## 0. 원안 대비 검증·수정 내역

초안을 live 소스와 대조하여 다음을 수정·확정했다. (근거는 각 해당 절에 표기)

| # | 항목 | 판정 및 조치 |
|---|---|---|
| 1 | Use case 계층의 위치 | 초안은 `composeApp/application`에 배치했으나 최종 계획서 3.1은 "초기엔 `core` 내 usecase 패키지로 시작, 비대해지면 `:application` 분리"로 규정. **`core`에 배치로 정정** (§2, §17). Use case는 port에만 의존하므로 core 배치가 성립하며 Compose 없이 테스트 가능하다는 이점도 있다 |
| 2 | §17 패키지 구조 | 초안의 목표 구조는 유효하나 현재 live 구조와 다르다. **big-bang 재배치를 금지**하고 "현재 → 목표" 매핑과 이동 시점 규칙을 추가 (§17) |
| 3 | `ProjectRegistryPort` API | §3.2와 §10의 시그니처가 불일치 (`loadProjects/saveProject` vs `findAll/findById/save/delete`). **§10 형태로 통일** |
| 4 | `ExecutionWrapper.Auto` | `auto`는 run-cycle CLI 실계약에 존재하나(실측: `none\|code\|doc\|auto`), 계획서는 "queue가 노출한 단일 wrapper만 실행"을 요구. **CLI enum에는 유지하되 UI 액션으로 노출 금지** 규칙 추가 (§6.2) |
| 5 | 상태 머신에 `no_request` 누락 | harness-kit 실측 존재 상태이며 계획서 CTA 표에도 있음. **추가** (§7.1) |
| 6 | `StateReadResult` vs 기존 `Projection<T>`/`ProjectionMeta` | 초안은 관계를 정의하지 않았음. **계층별 역할로 정리**: sealed result는 Reader/port 계약, `Projection<T>`+`ProjectionMeta`는 화면 투영 메타 (§14). Phase 1A의 `ProjectionMeta.malformed=true` 계약 유지 |
| 7 | 실행 후 lock 해제 vs State 재읽기 순서 | 계획서 문구("lock 해제 확인 → State 재읽기")와 초안 코드(State 재읽기 후 finally 해제)가 상충. **State 재읽기는 lock 보유 중 수행으로 확정** — 계획서의 "해제 확인"은 실행 종료 후 잔존 lock 파일 정리 검증으로 해석한다 (§16) |
| 8 | 날짜 선택 정책 | live에 이미 `WorkspaceDaySelectionPolicy`(`core.domain.policy`, 순수 정책)가 구현되어 있음을 반영. 본 문서의 Policy Pattern 규범이 이미 실현된 첫 사례로 §11에 등재 |

초안에서 검증 후 **정확했던 핵심 사항**: stop reason·상태·차단 marker 값이 harness-kit에 실존(`usage_limit_blocked`, `claude_context_limit`, `dispatch_contract_mismatch`, `dispatch_metadata_conflict`, `manual_prerequisite_required`, `execution_blocked` 등), `-RunPlanningWrapper`/`-RunReplanWrapper`/`-RunExecutionWrapper code|doc`/`-ValidateForClosure` 명령 형태, `WORKFLOW_STATE.json` 위치(`dayRoot` 하위), validation wrapper 모드 부재. 단 `dispatch_metadata_conflict`는 `state.stop_reason`이 아니라 planning queue의 `blocked_reason`/`purpose_marker`이며, 실행 단계의 대응 stop reason은 `dispatch_contract_mismatch`다.

---

# 1. 최종 설계 결론

HRNS-NOW가 최종적으로 지향해야 할 구조는 다음 네 가지를 결합한 형태다.

> **Hexagonal Architecture + MVVM 기반 단방향 데이터 흐름 + CQRS-lite + 명시적 상태 머신과 정책 객체**

이를 한 문장으로 표현하면 다음과 같다.

> **Harness Kit은 실행 엔진으로 유지하고, HRNS-NOW는 Harness의 상태를 읽고 허용된 명령만 전달하는 안전한 Kotlin Control Plane이 되어야 한다.**

HRNS-NOW가 해서는 안 되는 일 (계획서 2.1~2.3과 동일):

- Harness 로직을 Kotlin으로 다시 구현
- Claude API를 직접 호출
- `WORKFLOW_STATE.json`을 직접 수정
- Markdown 문장을 분석해 실행 가능 여부 결정
- stdout의 성공 문구만 보고 작업 완료 처리
- 사용자가 임의 PowerShell 명령을 입력하게 허용

상태 진실은 `WORKFLOW_STATE.json` 하나이며, unknown 상태·스키마·enum에서는 모든 쓰기와 실행을 잠그는 **fail-closed** 원칙을 지킨다.

---

# 2. 전체 아키텍처

```text
┌─────────────────────────────────────────────┐
│                Compose Desktop              │
│   (composeApp: presentation + demo)         │
│  Screen → UiEvent → AppViewModel → UiState  │
└──────────────────────┬──────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────┐
│        Application Layer (core.usecase)     │
│  LoadCockpitUseCase                         │
│  ExecuteHarnessActionUseCase                │
│  SaveRequestUseCase                         │
│  ValidateClosureUseCase                     │
│  RefreshWorkspaceUseCase                    │
└──────────────┬─────────────────┬────────────┘
               │                 │
               ▼                 ▼
┌──────────────────────┐  ┌──────────────────────┐
│  Domain (core)       │  │   Ports (core.port)  │
│ WorkflowState        │  │ WorkflowStatePort    │
│ WorkspaceDay         │  │ HarnessRunnerPort    │
│ UiAction             │  │ ProjectRegistryPort  │
│ StopReason           │  │ RequestWriterPort    │
│ ActionPolicy         │  │ GitStatusPort        │
│ ClosurePolicy        │  │ ProcessLockPort      │
│ BoundaryPolicy       │  │ ClockPort            │
│ WorkspaceDaySelectionPolicy (live 구현됨)     │
└──────────────────────┘  └──────────┬───────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────┐
│       Infrastructure Adapters (infra)       │
│ JsonWorkflowStateAdapter                    │
│ PowerShellHarnessAdapter                    │
│ JsonProjectRegistryAdapter                  │
│ AtomicRequestWriterAdapter                  │
│ LocalProcessLockAdapter                     │
│ CommandLineGitStatusAdapter                 │
│ SecretMaskingProcessRunner (decorator)      │
└──────────────────────┬──────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────┐
│                  Harness Kit                │
│ run-cycle.ps1 / doctor.ps1 / validate-ops.ps1│
│ WORKFLOW_STATE.json (dayRoot 하위)           │
│ REQUEST_INBOX.md / TODAY_STRATEGY.md        │
│ DAILY_HANDOFF.md                            │
└─────────────────────────────────────────────┘
```

**Use case 배치 결정 (수정 내역 #1):** use case는 port(core 내 interface)에만 의존하므로 `core`의 `usecase` 패키지에 둔다. Compose·파일·프로세스 의존이 core에 유입되지 않는 한 이 배치는 hexagonal 원칙과 충돌하지 않는다. use case 수가 많아져 core가 비대해지면 그때 `:application` Gradle 모듈로 분리한다 (계획서 3.1). `composeApp`에는 presentation(ViewModel, UI projection, mapper, screen, component)과 `demo`(mock provider)만 둔다.

---

# 3. 최상위 패턴: Hexagonal Architecture

## 3.1 선택 이유

HRNS-NOW의 외부 의존성: Windows 파일 시스템, PowerShell, Harness Kit PS1, JSON 상태 파일, Git 명령, `%APPDATA%` Registry, `%LOCALAPPDATA%` Lock, 시스템 시간, 프로세스 PID, Compose Desktop.

이 외부 기술을 domain과 직접 결합하면:

```kotlin
@Composable
fun RunScreen() {
    val process = ProcessBuilder("powershell.exe", ...)   // 금지
    val json = File("WORKFLOW_STATE.json").readText()      // 금지
}
```

- UI 테스트 불가, PowerShell 없이 domain 테스트 불가능
- 파일 구조 변경이 화면까지 전파
- SRP·DIP 위반

따라서 **핵심 로직은 port에만 의존하고 외부 기술은 adapter로 연결한다.**

## 3.2 Port 예시

```kotlin
interface WorkflowStatePort {
    suspend fun read(day: WorkspaceDay): StateReadResult
}

interface HarnessRunnerPort {
    suspend fun execute(command: HarnessCommand): ProcessRunResult
    suspend fun cancel(runId: RunId): CancelResult
}

interface RequestWriterPort {
    suspend fun save(
        day: WorkspaceDay,
        request: RequestDraft,
        expectedVersion: FileVersion,
    ): RequestSaveResult
}
```

`ProjectRegistryPort`는 §10의 Repository 형태(`findAll`/`findById`/`save`/`delete`)를 단일 정본으로 사용한다 (수정 내역 #3).

port interface는 JSON, 파일 경로, PowerShell, Windows API를 알지 못한다.

## 3.3 Adapter 예시

```kotlin
class JsonWorkflowStateAdapter(
    private val fileSystem: FileSystemGateway,
    private val parser: WorkflowStateParser,
    private val retryPolicy: StateReadRetryPolicy,
) : WorkflowStatePort {

    override suspend fun read(day: WorkspaceDay): StateReadResult =
        retryPolicy.execute {
            val content = fileSystem.readUtf8(
                day.dayRoot.resolve("WORKFLOW_STATE.json"),
            )
            parser.parse(content)
        }
}
```

`WORKFLOW_STATE.json`의 위치는 반드시 `dayRoot`(=`<projectWorkspaceRoot>/<yyyy-MM-dd>/`) 하위다. live `WorkspaceDay`는 `dayRoot`, `dayLogsRoot`(day 산출물 로그), `wrapperLogsRoot`(`<root>/logs/<date>/`, wrapper 실행 로그)를 이미 구분해 제공한다 — 이 세 경로 해석을 다른 곳에 중복 구현하지 않는다.

핵심: domain이 adapter를 모르고, adapter가 domain port를 구현한다.

---

# 4. Harness와 HRNS-NOW 사이의 Anti-Corruption Layer

## 4.1 필요한 이유

`WORKFLOW_STATE.json`은 Harness Kit의 외부 계약이다. DTO를 그대로 UI까지 전달하면 Harness 필드명이 UI 전체로 확산되고, schema 추가가 화면 코드 연쇄 변경을 일으키며, raw 문자열 비교가 화면 곳곳에 흩어진다.

```text
WORKFLOW_STATE.json → HarnessWorkflowStateDto → WorkflowStateMapper → WorkflowState(domain)
```

## 4.2 외부 DTO

```kotlin
@Serializable
internal data class HarnessWorkflowStateDto(
    @SerialName("schema_version") val schemaVersion: String? = null,
    val state: HarnessStateDto? = null,
    val queue: HarnessQueueDto? = null,
)
```

DTO는 외부 구조를 있는 그대로 표현하고 `internal`로 격리한다. 파서 설정은 Phase 1A 계약을 따른다: `ignoreUnknownKeys = true` 필수, UTF-8(BOM 허용) 읽기, explicit null과 필드 누락 구분 정책 명시.

## 4.3 내부 Domain

```kotlin
data class WorkflowState(
    val schemaVersion: SchemaVersion,
    val phase: WorkflowPhase,
    val status: WorkflowStatus,
    val stopReason: StopReason?,
    val queue: WorkflowQueue,
    val artifacts: ArtifactReadiness,
    val opsValidation: OpsValidationState,
    val closure: ClosureState,
)
```

## 4.4 Unknown 값 보존

```kotlin
sealed interface StopReason {
    data object UsageLimitBlocked : StopReason
    data object ClaudeContextLimit : StopReason
    data object DispatchContractMismatch : StopReason
    data class Unknown(val raw: String) : StopReason
}

fun String?.toStopReason(): StopReason? =
    when (this) {
        null, "" -> null
        "usage_limit_blocked" -> StopReason.UsageLimitBlocked
        "claude_context_limit" -> StopReason.ClaudeContextLimit
        "dispatch_contract_mismatch" -> StopReason.DispatchContractMismatch
        else -> StopReason.Unknown(this)
    }
```

외부 시스템 값은 계속 추가되므로 `else -> null`로 버리지 않는다. `Unknown(raw)`은:

- 기존 코드가 새 값을 받아도 깨지지 않음 (OCP)
- 실행은 fail-closed로 잠김
- 원문을 진단 화면에 표시 가능, 데이터 손실 없음

매핑에 사용하는 문자열 상수는 harness-kit 실측 taxonomy만 사용한다(계획서 부록 C). `packet_contract_failed` 같은 창작 용어를 다시 들여오지 않는다.

---

# 5. CQRS-lite

완전한 CQRS까지 갈 필요는 없다. 읽기와 실행을 명확하게 분리하는 **CQRS-lite**가 적합하다.

## 5.1 Query 측

읽기 작업: State/Artifact readiness/Strategy/Handoff/Git status/Registry/로그 읽기.

```kotlin
class LoadCockpitUseCase(
    private val statePort: WorkflowStatePort,
    private val artifactPort: ArtifactReaderPort,
    private val actionPolicy: ActionPolicy,
) {
    suspend operator fun invoke(
        project: HarnessProject,
        day: WorkspaceDay,
    ): CockpitSnapshot {
        val state = statePort.read(day)
        val artifacts = artifactPort.read(day)
        return CockpitSnapshotAssembler.assemble(
            project = project, day = day,
            state = state, artifacts = artifacts,
            actions = actionPolicy.recommend(state = state, day = day),
        )
    }
}
```

## 5.2 Command 측

상태를 변경할 수 있는 작업: Bootstrap, Request 저장, Planning, Replan, Code/Doc execution, Closure validation.

```kotlin
sealed interface HarnessCommand {
    data class BootstrapDay(val project: HarnessProject, val day: WorkspaceDay) : HarnessCommand
    data class RunPlanning(val project: HarnessProject, val day: WorkspaceDay) : HarnessCommand
    data class RunReplan(val project: HarnessProject, val day: WorkspaceDay) : HarnessCommand
    data class RunExecution(
        val project: HarnessProject,
        val day: WorkspaceDay,
        val wrapper: ExecutionWrapper,
    ) : HarnessCommand
    data class ValidateClosure(val project: HarnessProject, val day: WorkspaceDay) : HarnessCommand
}
```

계획서의 typed CTA(`RunPlanning`, `RunReplan`, `RunCodeSlice`, `RunDocSlice`, `RunClosureValidation`)와 1:1로 대응한다.

## 5.3 분리해야 하는 이유

조회와 실행의 위험 수준이 다르다.

```text
State 새로고침 / 과거 날짜 조회 / doctor  → 읽기·진단
REQUEST 저장                              → 쓰기
Planning / Execution / Closure validation → Harness 상태·Repository 변경 가능
```

`readState()`부터 `closeDay()`까지 한 클래스에 몰아넣은 God Service는 SRP·ISP를 모두 위반한다.

---

# 6. Command Pattern

PowerShell 명령의 문자열 조립을 금지하고 typed command를 사용한다.

## 6.1 금지 방식

```kotlin
val command = "powershell.exe -File $script -ProjectRoot $projectRoot $extraArgs"
```

공백 경로 오류, quoting 오류, command injection, 존재하지 않는 인자 생성, Phase별 허용 명령 통제 불가.

## 6.2 표준 방식

```kotlin
class HarnessCommandEncoder {
    fun encode(command: HarnessCommand): List<String> =
        when (command) {
            is HarnessCommand.RunPlanning ->
                baseArguments(command) + listOf("-RunPlanningWrapper")
            is HarnessCommand.RunReplan ->
                baseArguments(command) + listOf("-RunReplanWrapper")
            is HarnessCommand.RunExecution ->
                baseArguments(command) + listOf("-RunExecutionWrapper", command.wrapper.cliValue)
            is HarnessCommand.ValidateClosure ->
                baseArguments(command) + listOf("-ValidateForClosure")
            is HarnessCommand.BootstrapDay ->
                baseArguments(command)
        }
    // baseArguments는 -WorkspaceRoot / -ProjectRoot / -KitRoot / -Profile / -Date 등
    // run-cycle.ps1 실계약 파라미터를 typed 값에서 생성한다.
}

enum class ExecutionWrapper(val cliValue: String) {
    Code("code"),
    Doc("doc"),
    Auto("auto"),
}
```

**규칙 (수정 내역 #4):**

- run-cycle CLI 실계약은 `none|code|doc|auto`다(실측). `validation` 같은 존재하지 않는 wrapper 값을 타입 수준에서 만들지 않는다.
- `Auto`는 CLI 계약 충실성을 위해 enum에 존재하지만, **UI 액션으로 노출하지 않는다.** 계획서는 "`WORKFLOW_STATE.queue`가 노출한 wrapper와 authorized target만 실행"을 요구하므로, UI는 queue가 지정한 `Code` 또는 `Doc`만 사용한다. validation-only slice의 실행 매핑은 Phase 4 착수 시 실계약으로 확정한다.
- 프로세스 기동은 `powershell.exe -NoProfile -ExecutionPolicy Bypass -File <script> <args...>` 형태의 인자 목록으로만 한다.

---

# 7. State Machine + Policy Pattern

HRNS-NOW의 핵심은 화면이 아니라 **현재 상태에서 어떤 행동이 허용되는지 결정하는 정책**이다.

## 7.1 상태 머신 (수정 내역 #5: `no_request` 포함)

```text
request_intake_pending
        ↓
   (no_request ←→ 새 요청 추가)
        ↓
planning_required
        ↓
planning_completed
        ↓
execution_ready
        ↓
execution_completed
        ↓
closure_validated
```

중간 차단 상태 (전부 harness-kit 실측 존재):

```text
execution_blocked
manual_prerequisite_required
usage_limit_blocked
claude_context_limit
```

`dispatch_metadata_conflict`는 상태가 아니라 planning queue의 `blocked_reason`/
`purpose_marker`다. 이 marker에서는 재계획만 허용하며 실행 CTA를 잠근다.

이 흐름을 화면의 `if` 문으로 흩어놓지 않는다.

## 7.2 금지: UI 조건문 정책

```kotlin
if (state.currentStatus == "planning_required") { PlanningButton() }   // 금지
```

화면이 많아질수록 같은 정책이 여러 곳에 복제된다. raw 문자열 비교는 ACL mapper 한 곳으로 한정한다(§19.3).

## 7.3 Action Policy

```kotlin
class ActionPolicy {
    fun recommend(context: ActionContext): RecommendedActions {
        if (!context.compatibility.isSupported) {
            return RecommendedActions(
                primary = UiAction.ShowCompatibilityIssue,
                allowed = setOf(UiAction.ShowCompatibilityIssue),
                blockedReason = "지원하지 않는 Harness 계약입니다.",
            )
        }
        if (context.state.isMalformed) {
            return RecommendedActions(
                primary = UiAction.OpenRecoveryCenter,
                allowed = setOf(UiAction.OpenRecoveryCenter, UiAction.Refresh),
                blockedReason = "상태 파일을 해석할 수 없습니다.",
            )
        }
        if (context.day.isHistorical) {
            return RecommendedActions(
                primary = UiAction.OpenToday,
                allowed = setOf(UiAction.OpenToday, UiAction.Refresh),
                blockedReason = "과거 날짜는 읽기 전용입니다.",
            )
        }
        return when (context.state.status) {
            WorkflowStatus.RequestIntakePending -> editRequestActions()
            WorkflowStatus.NoRequest -> newRequestActions()
            WorkflowStatus.PlanningRequired -> planningActions()
            WorkflowStatus.ExecutionReady -> executionActions(context.state)
            WorkflowStatus.ExecutionBlocked -> recoveryActions()
            WorkflowStatus.ClosureValidated -> nextDayActions()
            is WorkflowStatus.Unknown -> unknownStatusActions()
        }
    }
}
```

정책 함수는 순수해야 하며(파일·프로세스·Compose 미참조), 계획서 부록 B의 CTA 결정표 전 행을 parameterized test로 고정한다. guard 우선순위는 compatibility → malformed → 과거 날짜 → 상태별 분기 순이다.

---

# 8. MVVM + 단방향 데이터 흐름

## 8.1 구조

```text
사용자 입력 → HrnsUiEvent → AppViewModel → Use Case → Port/Adapter
→ 새 State 읽기 → HrnsUiState → Compose 렌더링
```

## 8.2 UI Event

```kotlin
sealed interface HrnsUiEvent {
    data object Refresh : HrnsUiEvent
    data object RunPrimaryAction : HrnsUiEvent
    data class SelectProject(val projectId: ProjectId) : HrnsUiEvent
    data class SelectDay(val date: LocalDate) : HrnsUiEvent
}
```

## 8.3 UI State

```kotlin
data class HrnsUiState(
    val selectedProject: HarnessProject? = null,
    val selectedDay: WorkspaceDay? = null,
    val cockpit: CockpitProjection? = null,
    val processRun: ProcessRunUiState = ProcessRunUiState.Idle,
    val refreshState: RefreshState = RefreshState.Idle,
    val recommendedActions: RecommendedActions = RecommendedActions.none(),
)
```

## 8.4 ViewModel

```kotlin
class AppViewModel(
    private val loadCockpit: LoadCockpitUseCase,
    private val executeAction: ExecuteHarnessActionUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(HrnsUiState())
    val state: StateFlow<HrnsUiState> = _state.asStateFlow()

    fun onEvent(event: HrnsUiEvent) {
        when (event) {
            HrnsUiEvent.Refresh -> refresh()
            HrnsUiEvent.RunPrimaryAction -> runPrimaryAction()
            is HrnsUiEvent.SelectProject -> selectProject(event.projectId)
            is HrnsUiEvent.SelectDay -> selectDay(event.date)
        }
    }
}
```

Compose는 state를 그리기만 한다.

```kotlin
@Composable
fun CockpitRoute(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsState()
    CockpitScreen(state = state, onEvent = viewModel::onEvent)
}
```

UI 스레드에서 파일 I/O를 하지 않는다. 현재 live의 `remember { buildProjections() }` 일회성 생성은 Phase 1C에서 이 구조로 교체한다(계획서 3.4). lifecycle-viewmodel-compose 의존성은 이미 도입되어 있다.

---

# 9. Projection Pattern

Harness domain 모델을 그대로 화면에 노출하지 않는다.

```text
WorkflowState → CockpitProjectionAssembler → CockpitProjection → Compose UI
```

```kotlin
data class CockpitProjection(
    val projectName: String,
    val dateLabel: String,
    val phaseLabel: String,
    val statusLabel: String,
    val stopReason: UserMessage?,
    val artifactItems: List<ArtifactItemProjection>,
    val primaryAction: ActionProjection?,
    val badges: List<StatusBadgeProjection>,
)
```

Projection이 담당하는 것: 사용자 문구, 날짜 포맷, 상태 색상, 표시 순서, enabled/disabled 표현.

Projection이 판단하면 안 되는 것: 실행 가능 여부, Harness 상태 전이, boundary 허용 여부, Closure 가능 여부 — 이 판단은 domain policy가 수행한 결과를 projection이 **표현만** 한다.

현재 live의 `core.projection`(StatusChipModel 등)은 UI read model이므로 Phase 1C에서 `composeApp/presentation/model`로 이동한다(계획서 3.1). Phase 1C 전까지는 현 위치를 유지한다.

---

# 10. Repository Pattern

프로젝트 Registry의 정본 port (수정 내역 #3):

```kotlin
interface ProjectRegistryPort {
    suspend fun findAll(): List<HarnessProject>
    suspend fun findById(id: ProjectId): HarnessProject?
    suspend fun save(project: HarnessProject)
    suspend fun delete(id: ProjectId)
}
```

```kotlin
class JsonProjectRegistryAdapter(
    private val registryPath: Path,   // %APPDATA%\hrns-now\projects.json 은 조립 지점에서 주입
    private val atomicWriter: AtomicFileWriter,
) : ProjectRegistryPort { /* ... */ }
```

Registry domain이 `%APPDATA%`를 직접 알면 안 된다. 경로는 composition root에서 주입한다. secret·token·session ID·응답 원문은 저장 금지(계획서 2.5), atomic write와 손상 복구를 갖춘다.

---

# 11. Strategy/Policy Pattern

각각 독립된 정책으로 분리한다.

| 정책 | 상태 | 비고 |
|---|---|---|
| `WorkspaceDaySelectionPolicy` | **live 구현 완료** (`core.domain.policy`) | 명시 날짜 > 오늘 > 최신(읽기 전용 fallback). Execution 목적일 때 과거 fallback 금지, `isReadOnly = (date != today)`. 본 규범이 이미 실현된 첫 사례 |
| `ActionPolicy` | Phase 1B | CTA 결정표 전 행 테스트 고정 |
| `ClosurePolicy` | Phase 5 | `Allowed / Blocked(reasons) / RequiresExplicitIncompleteHandoff(items)` |
| `BoundaryPolicy` | Phase 1D | 상호 포함 6종 + junction/symlink 실경로 비교, Registry 저장 이전 수행 |
| `CompatibilityPolicy` | Phase 2 연동 | schema major/contract 버전 판단 |
| `StateReadRetryPolicy` | Phase 1A | partial write 재읽기 (지연 → mtime/size 재확인 → 최대 2~3회) |
| `ExternalExecutionDetectionPolicy` | Phase 3~4 | UI가 유발하지 않은 State 변경 감지 휴리스틱 |
| `SecretMaskingPolicy` | Phase 3 | stdout/stderr 표시 전 마스킹 |

예:

```kotlin
class BoundaryPolicy {
    fun evaluate(project: HarnessProjectCandidate): ProjectBoundaryResult {
        val kit = normalize(project.kitRoot)
        val workspace = normalize(project.projectWorkspaceRoot)
        val repository = normalize(project.repositoryRoot)
        val violations = buildList {
            if (workspace.isInside(repository)) add(BoundaryViolation.WorkspaceInsideRepository)
            if (repository.isInside(workspace)) add(BoundaryViolation.RepositoryInsideWorkspace)
            if (kit.isInside(repository)) add(BoundaryViolation.KitInsideRepository)
            // + KitInsideWorkspace, 동일 경로, realPath 비교
        }
        return ProjectBoundaryResult(violations)
    }
}
```

람다로도 구현 가능하지만 정책이 여러 규칙과 테스트를 가지면 명시적 클래스가 적합하다.

---

# 12. Decorator Pattern

프로세스 실행에는 Decorator를 제한적으로 적용한다.

```text
Raw Process → Output Encoding → Secret Masking → Metrics → UI Delivery
```

```kotlin
class SecretMaskingProcessRunner(
    private val delegate: ProcessRunner,
    private val masker: SecretMasker,
) : ProcessRunner {
    override suspend fun execute(
        request: ProcessRequest,
        onOutput: suspend (ProcessOutput) -> Unit,
    ): ProcessResult =
        delegate.execute(request) { output ->
            onOutput(output.copy(text = masker.mask(output.text)))
        }
}
```

장점: 원본 수정 없이 masking 추가, 독립 테스트, OCP. 단 decorator 중첩 과다는 흐름 추적을 어렵게 하므로 위 권장 순서를 유지한다.

**Lock은 decorator로 만들지 않는다.** lock 획득/해제는 실행 use case(coordinator)에서 명시적으로 관리한다 — 실패 경로와 해제 시점이 명시적으로 보여야 하기 때문이다(§16).

---

# 13. Optimistic Concurrency Pattern

`REQUEST_INBOX.md` 저장에는 낙관적 동시성 제어가 필수다(계획서 Phase 4).

```kotlin
data class FileVersion(val modifiedAt: Instant, val size: Long, val hash: String)

data class LoadedRequest(val content: String, val version: FileVersion)

sealed interface RequestSaveResult {
    data object Saved : RequestSaveResult
    data class Conflict(val currentVersion: FileVersion) : RequestSaveResult
    data class Failed(val reason: String) : RequestSaveResult
}
```

로드 시 version 저장 → 저장 직전 재검증 → 불일치 시 `Conflict` 반환(덮어쓰기 금지, 재로드·수동 병합 제공). 쓰기는 temp 파일 작성 후 atomic move, UTF-8 no BOM.

---

# 14. Result Pattern과 Projection 메타의 관계 (수정 내역 #6)

## 14.1 금지 방식

```kotlin
fun readState(): WorkflowState? = try { /* ... */ } catch (_: Exception) { null }
```

파일 없음/권한 없음/malformed JSON/partial write/encoding 오류/schema mismatch를 구분할 수 없다.

## 14.2 Reader 계약: sealed result

```kotlin
sealed interface StateReadResult {
    data class Success(val state: WorkflowState, val sourceVersion: FileVersion) : StateReadResult
    data class Missing(val path: Path) : StateReadResult
    data class Malformed(val message: String, val lastKnownGood: WorkflowState?) : StateReadResult
    data class UnsupportedSchema(val rawVersion: String) : StateReadResult
    data class AccessDenied(val path: Path) : StateReadResult
}
```

## 14.3 계층별 역할 분담

live core에는 이미 `Projection<T>`(data + `ProjectionMeta(source, exists, malformed, stale, message)`)가 있고, Phase 1A 계약은 "최종 실패 시 `ProjectionMeta.malformed = true`, 마지막 정상 projection을 stale 표시로 유지"를 요구한다. 두 타입의 역할을 다음과 같이 확정한다.

| 타입 | 소속 계층 | 역할 |
|---|---|---|
| `StateReadResult` (sealed) | port/Reader 계약 | 실패 원인의 정밀 구분, retry 정책의 입력 |
| `Projection<T>` + `ProjectionMeta` | 화면 투영 | 마지막 정상 데이터 + stale/malformed 표시를 UI에 전달 |

변환 규칙: `Success → Projection(data, meta(exists=true))`, `Malformed → Projection(lastKnownGood, meta(malformed=true, stale=true))`, `Missing → Projection(null, meta(exists=false))`. UnsupportedSchema/AccessDenied는 malformed 계열로 투영하되 원문 메시지를 보존한다. 어떤 실패 계열이든 실행 CTA는 잠긴다(fail-closed).

---

# 15. Kotlin에서 클래스·함수·람다를 나누는 기준

## 클래스(interface 포함)로 구현할 대상

외부 시스템 연결, 상태·lifecycle 보유, 여러 메서드가 하나의 계약 구성, 구현체 교체 필요, mock/fake 필요, 설정·의존성 보유.

예: `WorkflowStatePort`, `PowerShellHarnessAdapter`, `ProjectRegistryPort`, `AppViewModel`, `ProcessRunner`, `StateReadRetryPolicy`, `WorkspaceDaySelectionPolicy`.

## 순수 함수로 구현할 대상

입력만으로 결과가 결정되고 상태가 없는 것.

```kotlin
fun WorkflowStatus.toDisplayLabel(): String
fun determineReadiness(artifacts: List<ArtifactState>): ArtifactReadiness
fun evaluateClosure(context: ClosureContext): ClosureDecision
```

## 람다로 구현할 대상

짧은 callback, 작은 행위 주입, 컬렉션 변환.

```kotlin
PrimaryButton(onClick = viewModel::runPrimaryAction)

class StateReader(private val delay: suspend (Duration) -> Unit)

val requiredReady = artifacts
    .filter { it.requirement == ArtifactRequirement.Required }
    .all { it.state == ArtifactState.Ready }
```

## 람다로 만들면 안 되는 대상

orchestration 전체(State 읽기 → CTA 판단 → lock → 실행 → masking → 재읽기 → Registry → UI)를 하나의 람다에 넣지 않는다. UI 람다는 다음 수준에 머무른다.

```kotlin
onClick = { viewModel.onEvent(HrnsUiEvent.RunPrimaryAction) }
```

---

# 16. 실행 Use Case의 최종 형태 (수정 내역 #7)

```kotlin
class ExecuteHarnessActionUseCase(
    private val actionPolicy: ActionPolicy,
    private val commandMapper: HarnessCommandMapper,
    private val lockPort: ProcessLockPort,
    private val runnerPort: HarnessRunnerPort,
    private val statePort: WorkflowStatePort,
) {
    suspend operator fun invoke(
        context: ExecutionContext,
        action: UiAction,
    ): ExecutionOutcome {
        val allowed = actionPolicy.recommend(context.toActionContext())
        if (action !in allowed.allowed) {
            return ExecutionOutcome.Rejected(
                reason = allowed.blockedReason ?: "허용되지 않은 작업입니다.",
            )
        }

        val command = commandMapper.map(action = action, context = context)
            ?: return ExecutionOutcome.UnsupportedAction(action)

        val lock = lockPort.acquire(
            projectId = context.project.id,
            date = context.day.date,
            command = command,
        )
        if (lock !is LockAcquireResult.Acquired) {
            return ExecutionOutcome.Locked(lock)
        }

        return try {
            val processResult = runnerPort.execute(command)
            // State 재읽기는 lock 보유 중 수행한다 — 다른 HRNS-NOW 인스턴스가
            // 재읽기 전에 새 실행을 시작하는 틈을 만들지 않기 위함이다.
            val refreshedState = statePort.read(context.day)
            ExecutionOutcome.Completed(
                process = processResult,
                refreshedState = refreshedState,
            )
        } finally {
            lockPort.release(lock.handle)
        }
    }
}
```

실행 종료 시퀀스(계획서 Phase 4)와의 대응:

```text
프로세스 종료 → exit code 기록 → [lock 보유 중] State 재읽기 → stop reason 해석
→ queue 갱신 → CTA 재계산 → lock 해제 → 잔존 lock 파일 정리 검증("lock 해제 확인")
```

**확정 규칙:** State 재읽기는 lock 보유 중 수행한다. 계획서의 "lock 해제 확인" 단계는 해제 후 잔존 lock 파일이 남지 않았는지에 대한 검증으로 해석한다. stdout 성공 문구로 완료를 판단하지 않으며, 완료 여부는 재읽은 `WORKFLOW_STATE.json`이 결정한다.

---

# 17. 패키지 구조: 목표와 현재, 이동 규칙 (수정 내역 #1, #2)

## 17.1 목표 구조

```text
core/src/main/kotlin/io/hrns_now/core/
├── domain/
│   ├── model/        HarnessProject, WorkspaceDay, WorkflowState, WorkflowStatus,
│   │                 StopReason, UiAction, ClosureDecision, ProcessRun
│   └── policy/       ActionPolicy, ClosurePolicy, BoundaryPolicy,
│                     CompatibilityPolicy, WorkspaceDaySelectionPolicy
├── usecase/          LoadCockpitUseCase, ExecuteHarnessActionUseCase,
│                     SaveRequestUseCase, ValidateClosureUseCase
├── port/             WorkflowStatePort, HarnessRunnerPort, ProjectRegistryPort,
│                     RequestWriterPort, ProcessLockPort, GitStatusPort
└── result/           StateReadResult, ProcessRunResult, Projection, ProjectionMeta
```

```text
infra/src/main/kotlin/io/hrns_now/infra/
├── filesystem/       WorkspaceArtifactAdapter, AtomicFileWriter, WorkspaceDateFinder
├── serialization/    HarnessWorkflowStateDto, WorkflowStateParser, WorkflowStateMapper
├── process/          PowerShellHarnessAdapter, JvmProcessExecutor,
│                     HarnessCommandEncoder, WindowsProcessTreeTerminator
├── registry/         JsonProjectRegistryAdapter
├── lock/             LocalProcessLockAdapter
├── git/              CommandLineGitStatusAdapter
└── security/         SecretMasker, SecretMaskingProcessRunner
```

```text
composeApp/src/jvmMain/kotlin/io/hrns_now/app/
├── presentation/
│   ├── model/        HrnsUiState, HrnsUiEvent, CockpitProjection (+ 기존 UI read model 이동분)
│   ├── mapper/       CockpitProjectionAssembler
│   ├── viewmodel/    AppViewModel
│   ├── screen/
│   └── component/
└── demo/             MockProjectionProvider, MockWorkspaceConfigProvider
```

Use case가 많아지면 `:application` Gradle 모듈로 분리한다. 초기부터 모듈을 늘리는 것은 과도한 추상화다.

## 17.2 현재 live 구조와의 매핑

| 현재 (live) | 목표 | 이동 시점 |
|---|---|---|
| `core/AppRoute.kt`, `core/Projection.kt`, `core/ProjectionMeta.kt` | `core/result/` (Projection 계열), AppRoute는 presentation 성격 재검토 | 해당 파일을 수정하는 Phase에서 |
| `core/domain/model/WorkspaceArtifact.kt` | `core/domain/model/` | **완료 (Phase 0)** |
| `core/config/*` | `core/domain/model/` 또는 통합 재설계 | Phase 1D (Registry 도입 시) |
| `core/projection/ProjectionModels.kt` | `composeApp/presentation/model/` | **Phase 1C** (계획서 명시) |
| `core/domain/model/WorkspaceDay.kt`, `core/domain/policy/WorkspaceDaySelectionPolicy.kt` | `core/domain/model/`, `core/domain/policy/` | **완료 (Phase 0)** |
| `infra/WorkspaceArtifactProbe.kt` | `infra/filesystem/` | Phase 1A~1C 중 접촉 시 |
| `composeApp/app/demo/*` | 유지 (이미 목표 위치) | — |

## 17.3 이동 규칙

1. **big-bang 재배치 금지.** 패키지 이동만을 위한 일괄 커밋을 만들지 않는다.
2. **신규 파일은 목표 구조에 생성한다.**
3. **기존 파일은 해당 Phase에서 내용을 수정할 때 함께 이동한다** (이동 + 수정을 같은 변경으로).
4. 이동 시 참조 갱신은 컴파일과 전체 테스트(`./gradlew check`)로 검증한다.

---

# 18. SOLID와 최종 패턴의 관계

| SOLID | HRNS-NOW 적용 |
|---|---|
| SRP | Reader, Policy, Runner, Registry, ViewModel 책임 분리 |
| OCP | adapter 교체, `Unknown(raw)` 보존, decorator 확장 |
| LSP | fake/real port 구현이 동일한 결과 계약 준수 |
| ISP | 작은 port interface로 소비자별 의존 분리 |
| DIP | core가 PowerShell·Compose·JSON·파일 시스템을 모름 |

- **SRP**: `PowerShellHarnessAdapter`는 프로세스 실행만 담당한다. CTA 결정, UI 문구, Registry 저장, Closure 판단, JSON parsing을 하지 않는다.
- **OCP**: 새 stop reason이 와도 파서가 실패하지 않고 `StopReason.Unknown(raw)`로 흡수한다. 새 process decoration은 원본 runner 수정 없이 decorator로 추가한다.
- **LSP**: `FakeWorkflowStatePort`가 malformed 상태를 무조건 성공으로 바꿔 반환하면 안 된다. real과 fake가 같은 의미 계약을 유지한다.
- **ISP**: `readState()`부터 `closeDay()`까지 가진 거대 interface(`HarnessManager`)를 만들지 않는다.
- **DIP**: `ActionPolicy`가 `File`, `ProcessBuilder`, Compose `Color`를 참조하면 안 된다.

---

# 19. 사용하지 말아야 할 패턴

## 19.1 God ViewModel

파일 읽기/JSON 파싱/PowerShell 실행/Registry 저장/Lock/Git/CTA 정책/로그 마스킹을 전부 가진 ViewModel 금지. ViewModel은 use case 호출과 UI state 조립만 담당한다.

## 19.2 Service Locator

```kotlin
GlobalServices.processRunner   // 금지
```

전역 객체는 테스트와 lifecycle을 어렵게 만든다. 명시적 constructor injection을 사용한다.

## 19.3 문자열 기반 상태 분기

```kotlin
if (status == "execution_ready") { ... }   // 금지
```

raw 문자열 비교는 Anti-Corruption Layer의 mapper 한 곳으로 한정한다.

## 19.4 과도한 Singleton

Process runner, ViewModel, Lock manager, Polling coordinator, Registry adapter는 lifecycle과 테스트 격리가 필요하므로 `object`로 만들지 않는다.

## 19.5 무의미한 Factory

단순 생성자를 감추는 factory 금지. 생성 규칙이 복잡하거나 runtime별 구현 선택이 필요할 때만 사용한다.

## 19.6 범용 Event Bus

화면-domain 사이를 전역 event bus로 연결하지 않는다. `UiEvent → ViewModel → StateFlow`로 충분하다.

---

# 20. Phase별 패턴 도입 시점

| Phase | 도입 패턴 | 비고 |
|---|---|---|
| 0A | Value Object, Artifact classification, Adapter 골격 | **완료** — `WorkspaceDay`, `ArtifactRequirement`, probe 재작성 + `WorkspaceDaySelectionPolicy`(순수 정책) 선반영 |
| 0B | Contract Test, Fixture, CI | **완료** |
| 1A | Anti-Corruption Layer, Result/Projection 연계(§14) | |
| 1B | Policy/Strategy, State Machine, typed `UiAction` | |
| 1C | MVVM, Unidirectional Data Flow, Projection 이동 | `core.projection` → presentation |
| 1D | Repository, Boundary Policy | |
| 2 | External Contract(JSON 출력), Compatibility Strategy | harness 측 작업, Fable |
| 3 | Command, Process Adapter, Decorator, Lock coordination | 코어는 Fable |
| 4 | Application Use Case 완성, CQRS-lite, Optimistic Concurrency | |
| 5 | Closure Policy, Recovery Strategy | |
| 6 | Composition Root, runtime configuration | |
| 7 | Plugin-like optional adapter, feature isolation | |

계획서는 이 순서를 Gate 단위로 고정하며, State Reader·CTA 정책·Cockpit·Registry 완료 후에만 Process adapter와 실제 실행을 붙인다.

---

# 21. 테스트 설계

## 21.1 Domain 테스트 — 외부 시스템 없이 실행

```kotlin
@Test
fun `알 수 없는 상태에서는 실행 액션을 허용하지 않는다`() {
    val actions = policy.recommend(
        context = fixture(status = WorkflowStatus.Unknown("future_status")),
    )
    assertEquals(setOf(UiAction.OpenRecoveryCenter), actions.allowed)
}
```

## 21.2 Adapter Contract Test — 모든 port 구현이 같은 의미 준수

```text
파일 없음     → Missing
잘린 JSON     → Malformed
미지원 schema → UnsupportedSchema
unknown status → Success + WorkflowStatus.Unknown(raw)
```

## 21.3 Integration Test

실제 fixture Workspace, PowerShell fixture script, stdout/stderr 동시 출력, timeout, cancel, 한글 출력, lock 충돌, State 재읽기.

## 21.4 CI 단계

초기 `./gradlew check`(비-Windows 가능) → Phase 3부터 Windows runner(PowerShell adapter/fixture) → Phase 6 MSI build + 설치·실행 smoke. (live에 `.github/workflows/ci.yml` 구축 완료)

---

# 22. 최종 권장 조합

## 필수

1. Hexagonal Architecture
2. Anti-Corruption Layer
3. MVVM + Unidirectional Data Flow
4. State Machine + Policy Pattern
5. Command Pattern
6. Adapter Pattern
7. CQRS-lite
8. Repository Pattern
9. Result/Projection Pattern (§14의 계층 분담)
10. Optimistic Concurrency

## 선택적

11. Decorator Pattern (마스킹/메트릭 한정, lock 제외)
12. Retry Strategy
13. Compatibility Strategy
14. Composition Root
15. Reducer 스타일 상태 전이

## 금지

God ViewModel, God Service, Service Locator, raw 문자열 상태 분기, PowerShell 문자열 조립, 과도한 interface/Factory/Singleton, global Event Bus, orchestration 전체를 담은 람다.

---

# 23. 최종 판단

HRNS-NOW의 이상적인 모습은 "Kotlin으로 다시 만든 Harness"가 아니다.

```text
Harness Kit          = 실제 개발 작업을 수행하는 실행 엔진
HRNS-NOW Domain      = Harness 상태를 안전한 Kotlin 타입으로 해석하는 규칙
Use Case (core)      = 읽기·실행·저장·검증 흐름 조정
Infrastructure       = JSON·파일·PowerShell·Git·Registry 연결
Compose UI           = 현재 상태와 허용된 행동을 사용자에게 표현
```

가장 중요한 설계 기준 세 가지:

1. **Harness와 UI를 분리한다** — Harness JSON과 PowerShell 인자를 UI가 직접 알지 않게 한다.
2. **실행보다 정책을 먼저 만든다** — 어떤 행동이 허용되는지 domain policy가 먼저 결정하고, UI와 process adapter는 그 결정을 따른다.
3. **객체지향과 함수형을 혼합한다** — 외부 경계는 interface+adapter class, 상태는 data class+sealed interface, 정책은 순수 함수 또는 policy class, UI callback은 lambda·함수 참조, 비동기 상태는 coroutine+StateFlow, 컬렉션 변환은 함수형 API.

최종적으로 HRNS-NOW는 다음 문장을 코드 구조 자체로 보장해야 한다.

> **사용자가 프로젝트를 선택하면, HRNS-NOW는 현재 Harness 상태를 정확히 읽고 지금 허용된 단 하나의 다음 행동만 안전하게 안내하고 실행한다.**
