package io.hrns_now.infra.serialization

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * `WORKFLOW_STATE.json`의 외부 JSON 모양을 있는 그대로 표현하는 DTO 계층이다
 * (Anti-Corruption Layer, `doc/hrns_now_design_pattern.md` §4).
 *
 * 모든 필드는 nullable + 기본값 `null`이다 — harness-kit 필드 추가/누락에 파서가
 * 깨지지 않게 하고(`ignoreUnknownKeys = true`와 결합), "필드가 필수인지" 판단은
 * 전부 `WorkflowStateMapper`에서 명시적으로 수행한다. DTO 자체는 requiredness를
 * 강제하지 않는다.
 */
@Serializable
internal data class HarnessWorkflowStateDto(
    @SerialName("schema_version") val schemaVersion: String? = null,
    val date: String? = null,
    @SerialName("project_name") val projectName: String? = null,
    @SerialName("workspace_root") val workspaceRoot: String? = null,
    @SerialName("repo_root") val repoRoot: String? = null,
    val profile: String? = null,
    @SerialName("required_next_action") val requiredNextAction: String? = null,
    val state: HarnessStateDto? = null,
    val queue: HarnessQueueDto? = null,
)

@Serializable
internal data class HarnessStateDto(
    @SerialName("current_phase") val currentPhase: String? = null,
    @SerialName("current_status") val currentStatus: String? = null,
    @SerialName("next_action") val nextAction: String? = null,
    @SerialName("execution_wrapper") val executionWrapper: String? = null,
    @SerialName("stop_reason") val stopReason: String? = null,
    @SerialName("blocked_reason") val blockedReason: String? = null,
    @SerialName("failed_reason") val failedReason: String? = null,
    @SerialName("human_action_required") val humanActionRequired: Boolean? = null,
    @SerialName("execution_completed") val executionCompleted: Boolean? = null,
    @SerialName("closure_validated") val closureValidated: Boolean? = null,
    @SerialName("clean_handoff") val cleanHandoff: Boolean? = null,
    @SerialName("resume_from_step_id") val resumeFromStepId: String? = null,
    @SerialName("artifacts_state") val artifactsState: HarnessArtifactsStateDto? = null,
    @SerialName("ops_validation") val opsValidation: HarnessOpsValidationDto? = null,
    val closure: HarnessClosureDto? = null,
    @SerialName("authorized_target_file") val authorizedTargetFile: String? = null,
    @SerialName("current_slice") val currentSlice: JsonElement? = null,
    @SerialName("slice_queue") val sliceQueue: JsonElement? = null,
    @SerialName("role_sliced") val roleSliced: JsonElement? = null,
    @SerialName("usage_guard") val usageGuard: JsonElement? = null,
)

@Serializable
internal data class HarnessArtifactsStateDto(
    @SerialName("request_inbox") val requestInbox: String? = null,
    @SerialName("today_strategy") val todayStrategy: String? = null,
    @SerialName("daily_handoff") val dailyHandoff: String? = null,
    @SerialName("workflow_state") val workflowState: String? = null,
)

@Serializable
internal data class HarnessOpsValidationDto(
    val passed: Boolean? = null,
    @SerialName("validated_at") val validatedAt: String? = null,
    val notes: String? = null,
)

@Serializable
internal data class HarnessClosureDto(
    @SerialName("is_clean_handoff") val isCleanHandoff: Boolean? = null,
    val validated: Boolean? = null,
    @SerialName("validated_at") val validatedAt: String? = null,
    @SerialName("validator_notes") val validatorNotes: String? = null,
)

@Serializable
internal data class HarnessQueueDto(
    val status: String? = null,
    val active: HarnessQueueActiveDto? = null,
    @SerialName("blocked_reason") val blockedReason: String? = null,
    @SerialName("last_updated_at") val lastUpdatedAt: String? = null,
)

@Serializable
internal data class HarnessQueueActiveDto(
    @SerialName("card_id") val cardId: String? = null,
    @SerialName("slice_id") val sliceId: String? = null,
)
