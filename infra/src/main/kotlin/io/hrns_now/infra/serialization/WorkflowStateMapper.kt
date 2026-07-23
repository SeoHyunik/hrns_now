package io.hrns_now.infra.serialization

import io.hrns_now.core.domain.model.ArtifactsState
import io.hrns_now.core.domain.model.ClosureState
import io.hrns_now.core.domain.model.OpsValidationState
import io.hrns_now.core.domain.model.QueuePointer
import io.hrns_now.core.domain.model.SchemaVersion
import io.hrns_now.core.domain.model.WorkflowQueue
import io.hrns_now.core.domain.model.WorkflowState
import io.hrns_now.core.domain.model.toArtifactReadinessState
import io.hrns_now.core.domain.model.toExecutionWrapperState
import io.hrns_now.core.domain.model.toQueueStatus
import io.hrns_now.core.domain.model.toQueueBlockedReason
import io.hrns_now.core.domain.model.toStopReason
import io.hrns_now.core.domain.model.toWorkflowPhase
import io.hrns_now.core.domain.model.toWorkflowStatus
import java.time.LocalDate
import java.time.format.DateTimeParseException

internal sealed interface MapResult {
    data class Success(val state: WorkflowState) : MapResult
    data class Failure(val message: String) : MapResult
}

/**
 * [HarnessWorkflowStateDto] → [WorkflowState] 변환을 전담하는 Anti-Corruption Layer 매퍼다.
 *
 * 정책: 최상위 필수 필드(schema_version, date, project_name, ..., current_phase,
 * current_status, artifacts_state 4항목, ops_validation, closure, queue.status)는
 * 필드 누락과 명시적 JSON null을 동일하게 취급한다 — 둘 다 [MapResult.Failure]다.
 * 이 실패는 파일이 쓰이는 도중(partial write) 읽었을 가능성이 있으므로 Adapter가 재시도한다.
 *
 * 반대로 상세 계약이 아직 불명확한 `current_slice`/`slice_queue`/`role_sliced`/`usage_guard`는
 * 필드 누락과 명시적 null을 구분하지 않고 동일하게 도메인 null(없음)로 처리한다 — 이 필드들은
 * Phase 1A의 readiness/CTA 판단에 관여하지 않기 때문이다.
 */
internal class WorkflowStateMapper(
    private val rawJsonValueSanitizer: RawJsonValueSanitizer = RawJsonValueSanitizer(),
) {
    fun map(dto: HarnessWorkflowStateDto): MapResult {
        val schemaVersionRaw = dto.schemaVersion
            ?: return MapResult.Failure("schema_version is missing")
        val schemaVersion = SchemaVersion.parse(schemaVersionRaw)
            ?: return MapResult.Failure("schema_version has invalid format: $schemaVersionRaw")

        val date = dto.date?.let { parseDateOrNull(it) }
            ?: return MapResult.Failure("date is missing or has invalid format: ${dto.date}")
        val projectName = dto.projectName ?: return MapResult.Failure("project_name is missing")
        val workspaceRoot = dto.workspaceRoot ?: return MapResult.Failure("workspace_root is missing")
        val repoRoot = dto.repoRoot ?: return MapResult.Failure("repo_root is missing")
        val profile = dto.profile ?: return MapResult.Failure("profile is missing")
        val requiredNextAction = dto.requiredNextAction
            ?: return MapResult.Failure("required_next_action is missing")

        val stateDto = dto.state ?: return MapResult.Failure("state object is missing")
        val queueDto = dto.queue ?: return MapResult.Failure("queue object is missing")

        val phaseRaw = stateDto.currentPhase ?: return MapResult.Failure("state.current_phase is missing")
        val statusRaw = stateDto.currentStatus ?: return MapResult.Failure("state.current_status is missing")
        val nextAction = stateDto.nextAction ?: return MapResult.Failure("state.next_action is missing")
        val executionWrapper = stateDto.executionWrapper
            ?: return MapResult.Failure("state.execution_wrapper is missing")
        val stopReason = stateDto.stopReason ?: return MapResult.Failure("state.stop_reason is missing")
        val blockedReason = stateDto.blockedReason ?: return MapResult.Failure("state.blocked_reason is missing")
        val failedReason = stateDto.failedReason ?: return MapResult.Failure("state.failed_reason is missing")
        val humanActionRequired = stateDto.humanActionRequired
            ?: return MapResult.Failure("state.human_action_required is missing")
        val executionCompleted = stateDto.executionCompleted
            ?: return MapResult.Failure("state.execution_completed is missing")
        val closureValidated = stateDto.closureValidated
            ?: return MapResult.Failure("state.closure_validated is missing")
        val cleanHandoff = stateDto.cleanHandoff
            ?: return MapResult.Failure("state.clean_handoff is missing")
        val resumeFromStepId = stateDto.resumeFromStepId
            ?: return MapResult.Failure("state.resume_from_step_id is missing")
        val authorizedTargetFile = stateDto.authorizedTargetFile
            ?: return MapResult.Failure("state.authorized_target_file is missing")

        val artifactsDto = stateDto.artifactsState
            ?: return MapResult.Failure("state.artifacts_state is missing")
        val artifacts = mapArtifacts(artifactsDto) ?: return MapResult.Failure(
            "state.artifacts_state is missing one or more of request_inbox/today_strategy/daily_handoff/workflow_state",
        )

        val opsValidationDto = stateDto.opsValidation
            ?: return MapResult.Failure("state.ops_validation is missing")
        val closureDto = stateDto.closure ?: return MapResult.Failure("state.closure is missing")
        val opsValidationPassed = opsValidationDto.passed
            ?: return MapResult.Failure("state.ops_validation.passed is missing")
        val isCleanClosureHandoff = closureDto.isCleanHandoff
            ?: return MapResult.Failure("state.closure.is_clean_handoff is missing")
        val isClosureValidated = closureDto.validated
            ?: return MapResult.Failure("state.closure.validated is missing")

        val queueStatusRaw = queueDto.status ?: return MapResult.Failure("queue.status is missing")

        val state = WorkflowState(
            schemaVersion = schemaVersion,
            date = date,
            projectName = projectName,
            workspaceRoot = workspaceRoot,
            repoRoot = repoRoot,
            profile = profile,
            requiredNextAction = requiredNextAction.ifBlank { null },
            phase = phaseRaw.toWorkflowPhase(),
            status = statusRaw.toWorkflowStatus(),
            nextAction = nextAction.ifBlank { null },
            executionWrapper = executionWrapper.toExecutionWrapperState(),
            stopReason = stopReason.toStopReason(),
            blockedReason = blockedReason.ifBlank { null },
            failedReason = failedReason.ifBlank { null },
            humanActionRequired = humanActionRequired,
            executionCompleted = executionCompleted,
            closureValidated = closureValidated,
            cleanHandoff = cleanHandoff,
            resumeFromStepId = resumeFromStepId.ifBlank { null },
            authorizedTargetFile = authorizedTargetFile.ifBlank { null },
            artifacts = artifacts,
            opsValidation = OpsValidationState(
                passed = opsValidationPassed,
                validatedAt = opsValidationDto.validatedAt,
                notes = opsValidationDto.notes,
            ),
            closure = ClosureState(
                isCleanHandoff = isCleanClosureHandoff,
                validated = isClosureValidated,
                validatedAt = closureDto.validatedAt,
                validatorNotes = closureDto.validatorNotes,
            ),
            currentSliceRaw = rawJsonValueSanitizer.sanitize(stateDto.currentSlice),
            sliceQueueRaw = rawJsonValueSanitizer.sanitize(stateDto.sliceQueue),
            roleSlicedRaw = rawJsonValueSanitizer.sanitize(stateDto.roleSliced),
            usageGuardRaw = rawJsonValueSanitizer.sanitize(stateDto.usageGuard),
            queue = WorkflowQueue(
                status = queueStatusRaw.toQueueStatus(),
                active = QueuePointer(
                    cardId = queueDto.active?.cardId?.ifBlank { null },
                    sliceId = queueDto.active?.sliceId?.ifBlank { null },
                ),
                blockedReason = queueDto.blockedReason.toQueueBlockedReason(),
                lastUpdatedAt = queueDto.lastUpdatedAt,
            ),
        )
        return MapResult.Success(state)
    }

    private fun mapArtifacts(dto: HarnessArtifactsStateDto): ArtifactsState? {
        val requestInbox = dto.requestInbox ?: return null
        val todayStrategy = dto.todayStrategy ?: return null
        val dailyHandoff = dto.dailyHandoff ?: return null
        val workflowState = dto.workflowState ?: return null
        return ArtifactsState(
            requestInbox = requestInbox.toArtifactReadinessState(),
            todayStrategy = todayStrategy.toArtifactReadinessState(),
            dailyHandoff = dailyHandoff.toArtifactReadinessState(),
            workflowState = workflowState.toArtifactReadinessState(),
        )
    }

    private fun parseDateOrNull(raw: String): LocalDate? =
        try {
            LocalDate.parse(raw)
        } catch (exception: DateTimeParseException) {
            null
        }
}
