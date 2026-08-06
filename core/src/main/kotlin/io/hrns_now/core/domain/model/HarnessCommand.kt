package io.hrns_now.core.domain.model

import java.nio.file.Path
import java.time.LocalDate

/**
 * 실제로 프로세스와 연결하는 command의 안정된 식별자다. 표시 label이 아니라
 * lock payload/로그 등 machine 값으로 쓰인다 (`doc/hrns_now_design_pattern.md` §5.3).
 */
enum class HarnessCommandKind {
    Doctor,
    ValidateOps,
    OnboardProject,
    Bootstrap,
    Planning,
    Replan,
    ExecutionCode,
    ExecutionDoc,
    ClosureValidation,
}

/**
 * `run-cycle.ps1 -RunExecutionWrapper`의 실계약 값(`none|code|doc|auto`, `Auto` 제외 — `None`은
 * outbound 명령에 존재하지 않는다)이다. `Auto`는 CLI 계약 충실성을 위해 타입에 남기되, UI는
 * [io.hrns_now.core.domain.model.ActiveSliceKind]가 `Code`/`Doc`일 때만 dispatch하므로
 * `HarnessCommand.RunExecution(wrapper = Auto)`는 실제로 생성되지 않는다
 * (`doc/hrns_now_design_pattern.md` §6.2).
 */
enum class ExecutionWrapper(val cliValue: String) {
    Code("code"),
    Doc("doc"),
    Auto("auto"),
}

/** `run-cycle.ps1 -PlanningReason`의 `ValidateSet` 실계약과 동일하다. */
enum class PlanningReason(val cliValue: String) {
    InitialPlan("initial-plan"),
    ContinueExistingPlan("continue-existing-plan"),
    RepairCurrentPlan("repair-current-plan"),
    HumanRequestedReplan("human-requested-replan"),
    RequestRevisionReplan("request-revision-replan"),
    StaleArtifactRepair("stale-artifact-repair"),
}

/**
 * `run-cycle.ps1 -ReplanReason`의 `ValidateSet` 실계약과 동일하다(빈 문자열 제외 — 빈 문자열은
 * run-cycle.ps1이 `-RunReplanWrapper`를 `replanReasonRequired`로 차단하는 값이므로 이 타입에
 * 남기지 않는다).
 */
enum class ReplanReason(val cliValue: String) {
    RepairCurrentPlan("repair-current-plan"),
    HumanRequestedReplan("human-requested-replan"),
    RequestRevisionReplan("request-revision-replan"),
    StaleArtifactRepair("stale-artifact-repair"),
}

/**
 * Harness PowerShell 실행을 위한 typed command다(`doc/hrns_now_design_pattern.md` §6). 문자열
 * 조립이 아니라 [io.hrns_now.core.port.HarnessCommandEncoder]류의 순수 encoder가 이 값을
 * argument 목록으로 변환한다.
 *
 * read-only `Doctor`/`ValidateOps`, `run-cycle.ps1` 기반 mutating command
 * (`BootstrapDay`/`RunPlanning`/`RunReplan`/`RunExecution`), `ValidateClosure`를 포함한다.
 */
sealed interface HarnessCommand {
    val kind: HarnessCommandKind

    /** `scripts/doctor.ps1 -Json`에 대응한다. */
    data class Doctor(
        val kitRoot: Path,
        val workspaceRoot: Path?,
        val projectRoot: Path?,
        val date: LocalDate,
    ) : HarnessCommand {
        override val kind: HarnessCommandKind = HarnessCommandKind.Doctor
    }

    /** `scripts/validate-ops.ps1 -Json`에 대응한다. `workspaceRoot`는 이 스크립트의 필수 인자다. */
    data class ValidateOps(
        val workspaceRoot: Path,
        val kitRoot: Path,
        val profile: String?,
        val date: LocalDate,
    ) : HarnessCommand {
        override val kind: HarnessCommandKind = HarnessCommandKind.ValidateOps
    }

    /**
     * `scripts/enter-project.ps1`에 대응한다. repository bridge(`.claude/settings.local.json`,
     * `.claude/CLAUDE.md`, `tools/run-cycle.ps1`)와 external workspace(오늘 day root 포함 daily
     * 4-file)를 준비하는 명시적 온보딩 명령이다 — `-Force`/`-RunDoctor`/`-MaterializeSubagents`/
     * `-AgentNames`는 전달하지 않는다. 기존 bridge 파일은 덮어쓰지 않는다(live 계약: `enter-project.ps1`이
     * `-Force` 없이는 기존 파일을 보존함). `run-cycle.ps1` 기반 daily command와는 별개의 typed
     * 명령이며 boolean/switch로 뭉개지 않는다.
     */
    data class OnboardProject(
        val workspaceRoot: Path,
        val projectRoot: Path,
        val kitRoot: Path,
        val profile: String?,
        val date: LocalDate,
    ) : HarnessCommand {
        override val kind: HarnessCommandKind = HarnessCommandKind.OnboardProject
    }

    /** `scripts/run-cycle.ps1 -UsePythonSidecars`(wrapper 인자 없음)에 대응한다. */
    data class BootstrapDay(
        val workspaceRoot: Path,
        val projectRoot: Path,
        val kitRoot: Path,
        val profile: String?,
        val date: LocalDate,
    ) : HarnessCommand {
        override val kind: HarnessCommandKind = HarnessCommandKind.Bootstrap
    }

    /** `scripts/run-cycle.ps1 -RunPlanningWrapper -PlanningReason <reason>`에 대응한다. */
    data class RunPlanning(
        val workspaceRoot: Path,
        val projectRoot: Path,
        val kitRoot: Path,
        val profile: String?,
        val date: LocalDate,
        val reason: PlanningReason = PlanningReason.InitialPlan,
    ) : HarnessCommand {
        override val kind: HarnessCommandKind = HarnessCommandKind.Planning
    }

    /**
     * `scripts/run-cycle.ps1 -RunReplanWrapper -ReplanReason <reason>`에 대응한다. `reason`은
     * run-cycle.ps1이 빈 값일 때 `-RunReplanWrapper`를 차단하므로 필수 인자로 둔다.
     */
    data class RunReplan(
        val workspaceRoot: Path,
        val projectRoot: Path,
        val kitRoot: Path,
        val profile: String?,
        val date: LocalDate,
        val reason: ReplanReason,
    ) : HarnessCommand {
        override val kind: HarnessCommandKind = HarnessCommandKind.Replan
    }

    /** `scripts/run-cycle.ps1 -RunExecutionWrapper code|doc`에 대응한다. */
    data class RunExecution(
        val workspaceRoot: Path,
        val projectRoot: Path,
        val kitRoot: Path,
        val profile: String?,
        val date: LocalDate,
        val wrapper: ExecutionWrapper,
    ) : HarnessCommand {
        override val kind: HarnessCommandKind
            get() = when (wrapper) {
                ExecutionWrapper.Code -> HarnessCommandKind.ExecutionCode
                ExecutionWrapper.Doc -> HarnessCommandKind.ExecutionDoc
                ExecutionWrapper.Auto ->
                    error("RunExecution(wrapper = Auto)는 생성되지 않는다 — UI는 code/doc slice만 dispatch한다.")
            }
    }

    /**
     * `scripts/run-cycle.ps1 -ValidateForClosure`에 대응한다(실측: `run-cycle.ps1`은 이 switch를
     * `-RunPlanningWrapper`/`-RunReplanWrapper`/`-RunExecutionWrapper`와 같은 pass에서 결합하면
     * 즉시 `throw`한다). 내부적으로 결정적 `pre_handoff_validate.py`/`.ps1`만 호출하며 Claude를
     * 호출하지 않는다 — `state.closure.{validated,is_clean_handoff,validated_at,validator_notes}`와
     * `state.status`를 갱신할 뿐 다른 wrapper와 달리 실패해도 워크스페이스를 열린 상태로 되돌린다.
     */
    data class ValidateClosure(
        val workspaceRoot: Path,
        val projectRoot: Path,
        val kitRoot: Path,
        val profile: String?,
        val date: LocalDate,
    ) : HarnessCommand {
        override val kind: HarnessCommandKind = HarnessCommandKind.ClosureValidation
    }
}
