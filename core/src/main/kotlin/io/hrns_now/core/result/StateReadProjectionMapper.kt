package io.hrns_now.core.result

import io.hrns_now.core.Projection
import io.hrns_now.core.ProjectionMeta
import io.hrns_now.core.domain.model.WorkflowState

/**
 * Reader의 정밀 실패 결과를 기존 화면 projection 메타 계약으로 변환한다.
 *
 * 이 함수는 UI 문구나 실행 정책을 판단하지 않는다. Phase 1A의 last-known-good을
 * `malformed + stale`로 전달해 Phase 1B 정책과 Phase 1C presentation이 fail-closed
 * 상태를 잃지 않도록 하는 Result/Projection 경계다.
 */
fun StateReadResult.toProjection(source: String): Projection<WorkflowState> =
    when (this) {
        is StateReadResult.Success ->
            Projection(
                data = state,
                meta = ProjectionMeta(source = source, exists = true),
            )

        is StateReadResult.Missing ->
            Projection(
                data = null,
                meta = ProjectionMeta(source = source, exists = false),
            )

        is StateReadResult.Malformed ->
            Projection(
                data = lastKnownGood,
                meta = ProjectionMeta(
                    source = source,
                    exists = true,
                    malformed = true,
                    stale = true,
                    message = message,
                ),
            )

        is StateReadResult.EncodingError ->
            Projection(
                data = lastKnownGood,
                meta = ProjectionMeta(
                    source = source,
                    exists = true,
                    malformed = true,
                    stale = true,
                    message = message,
                ),
            )

        is StateReadResult.UnsupportedSchema ->
            Projection(
                data = null,
                meta = ProjectionMeta(
                    source = source,
                    exists = true,
                    malformed = true,
                    message = "Unsupported schema version: $rawVersion",
                ),
            )

        is StateReadResult.AccessDenied ->
            Projection(
                data = null,
                meta = ProjectionMeta(
                    source = source,
                    exists = true,
                    malformed = true,
                    message = "Access denied: $path",
                ),
            )
    }
