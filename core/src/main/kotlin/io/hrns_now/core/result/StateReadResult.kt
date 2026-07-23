package io.hrns_now.core.result

import io.hrns_now.core.domain.model.FileVersion
import io.hrns_now.core.domain.model.WorkflowState
import java.nio.file.Path

/**
 * [io.hrns_now.core.port.WorkflowStatePort.read] 호출자가 처리해야 하는 typed 결과다.
 *
 * 일반 예외/`null`로 실패를 뭉뚱그리지 않는다 — 파일 없음, malformed JSON, encoding 오류,
 * 미지원 schema, 접근 거부를 서로 다른 사용자 행동(재시도 가능/불가능, 복구 센터 안내 등)으로
 * 이어질 수 있게 구분한다(`doc/hrns_now_design_pattern.md` §14).
 */
sealed interface StateReadResult {
    data class Success(
        val state: WorkflowState,
        val sourceVersion: FileVersion,
    ) : StateReadResult

    data class Missing(val path: Path) : StateReadResult

    /** JSON 구조 파싱 실패 또는 도메인 필수 필드 누락. 재시도 후에도 실패하면 [lastKnownGood]을 보존한다. */
    data class Malformed(
        val message: String,
        val lastKnownGood: WorkflowState?,
    ) : StateReadResult

    /** UTF-8 디코딩 실패(BOM 제외). JSON 구문 오류와 구분되는 별도 실패 계열이다. */
    data class EncodingError(
        val message: String,
        val lastKnownGood: WorkflowState?,
    ) : StateReadResult

    /** `schema_version`의 major가 [io.hrns_now.core.domain.model.SUPPORTED_SCHEMA_MAJOR]와 다름. 재시도하지 않는다. */
    data class UnsupportedSchema(val rawVersion: String) : StateReadResult

    /** 파일 접근 권한 거부. 재시도하지 않는다. */
    data class AccessDenied(val path: Path) : StateReadResult
}
