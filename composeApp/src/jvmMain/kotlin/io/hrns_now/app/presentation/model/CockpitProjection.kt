package io.hrns_now.app.presentation.model

import io.hrns_now.core.domain.model.UiAction

/** 화면에서 강조할 단일 action이다. `label`은 표시 전용이며 action 식별에 쓰지 않는다. */
data class CockpitActionItem(
    val action: UiAction,
    val label: String,
    val enabled: Boolean,
)

/**
 * 오류/stale 상황의 3분리 표시다: 최근 작업 기록 / 마지막 정상 상태 보존 여부 / 사용자가 할 수 있는 일.
 * `Success`가 아닌 모든 [io.hrns_now.core.result.StateReadResult]는 이 값을 갖는다.
 */
data class CockpitDiagnostics(
    val whatHappened: String,
    val lastKnownGoodPreserved: Boolean,
    val nextStep: String,
)

/**
 * 실데이터 Cockpit 화면 전용 read model이다. [io.hrns_now.core.domain.model.WorkflowState]를
 * 그대로 노출하지 않고, 사용자 문구·표시 순서만 담당한다(`doc/hrns_now_design_pattern.md` §9).
 * 실행 가능 여부·fail-closed 판단은 [io.hrns_now.core.domain.policy.ActionPolicy]가 이미
 * 끝낸 결과([primaryAction]/[allowedActions])만 표현한다.
 *
 * raw session ID·token·secret·응답 원문·raw log는 이 타입에 담지 않는다 — 여기 필드는 전부
 * 이미 typed/label 처리된 값이다.
 */
data class CockpitProjection(
    val projectName: String?,
    val profileLabel: String,
    val dateLabel: String,
    val isReadOnlyDay: Boolean,
    val isStale: Boolean,
    val phaseLabel: String,
    val statusLabel: String,
    val queueStatusLabel: String,
    val activeCardId: String?,
    val activeSliceId: String?,
    val authorizedTargetLabel: String?,
    val stopReasonLabel: String?,
    val blockedReasonLabel: String?,
    val artifactItems: List<StatusChipModel>,
    val opsValidationLabel: String,
    val closureLabel: String,
    val executionCompletedLabel: String,
    val lastSuccessfulReadAtLabel: String?,
    val lastAttemptAtLabel: String?,
    val primaryAction: CockpitActionItem?,
    val allowedActions: List<CockpitActionItem>,
    val diagnostics: CockpitDiagnostics?,
    /**
     * Harness `kit-version.json` 호환성 판정이 [io.hrns_now.core.domain.model.CompatibilityStatus.Supported]가
     * 아닐 때만 채워지는 안전한 설명이다(Phase 2). `whatHappened`/`nextStep`은 버전 값처럼 이미
     * 정형화된 문구만 담으며, raw 파일 원문이나 예외 메시지를 담지 않는다.
     */
    val compatibilityDiagnostics: CockpitDiagnostics?,
)
