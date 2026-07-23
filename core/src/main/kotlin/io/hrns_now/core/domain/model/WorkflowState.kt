package io.hrns_now.core.domain.model

import java.time.LocalDate

/**
 * `WORKFLOW_STATE.json`의 Phase 1A 최소 typed 표현이다.
 *
 * 이 타입은 harness-kit의 JSON 필드명이나 kotlinx.serialization을 전혀 모른다 —
 * `infra.serialization.WorkflowStateMapper`가 외부 DTO를 이 타입으로 변환하는
 * Anti-Corruption Layer 경계다(`doc/hrns_now_design_pattern.md` §4).
 *
 * 필드 구성은 `doc/hrns_now_claude_plan.md` 부록 A의 실측 스키마와
 * `doc/claude_prompts/phase1a-workflow-state-reader.md`의 최소 typed 필드 목록을
 * 그대로 따른다. top-level 필드와 `state`/`queue`의 필드를 한 aggregate로 평탄화하되,
 * `queue`는 [WorkflowQueue]로 분리해 top-level 동명 필드(`blocked_reason` 등)와
 * 혼동하지 않는다.
 */
data class WorkflowState(
    // --- top-level ---
    val schemaVersion: SchemaVersion,
    val date: LocalDate,
    val projectName: String,
    val workspaceRoot: String,
    val repoRoot: String,
    val profile: String,
    val requiredNextAction: String?,

    // --- state ---
    val phase: WorkflowPhase,
    val status: WorkflowStatus,
    val nextAction: String?,
    val executionWrapper: ExecutionWrapperState,
    val stopReason: StopReason?,
    val blockedReason: String?,
    val failedReason: String?,
    val humanActionRequired: Boolean,
    val executionCompleted: Boolean,
    val closureValidated: Boolean,
    val cleanHandoff: Boolean,
    val resumeFromStepId: String?,
    val authorizedTargetFile: String?,
    val artifacts: ArtifactsState,
    val opsValidation: OpsValidationState,
    val closure: ClosureState,
    /** 상세 계약 미확정 — [RawJsonValue] 참고. */
    val currentSliceRaw: RawJsonValue?,
    val sliceQueueRaw: RawJsonValue?,
    val roleSlicedRaw: RawJsonValue?,
    val usageGuardRaw: RawJsonValue?,

    // --- queue ---
    val queue: WorkflowQueue,
)
