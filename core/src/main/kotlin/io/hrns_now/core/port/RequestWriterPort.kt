package io.hrns_now.core.port

import io.hrns_now.core.domain.model.FileVersion
import io.hrns_now.core.domain.model.WorkspaceDay

/** `REQUEST_INBOX.md`의 현재 raw 전체 텍스트와 낙관적 동시성 제어용 버전이다. */
data class LoadedRequest(
    val content: String,
    val version: FileVersion,
)

/**
 * `REQUEST_INBOX.md` 저장 결과다(`doc/hrns_now_design_pattern.md` §13). `Conflict`는 저장
 * 직전 재검증에서 파일이 [LoadedRequest.version] 이후로 바뀌었음을 뜻하며, 구현체는 이 경우
 * 파일을 덮어쓰지 않는다.
 */
sealed interface RequestSaveResult {
    data object Saved : RequestSaveResult
    data class Conflict(val currentVersion: FileVersion) : RequestSaveResult
    data class Failed(val reason: String) : RequestSaveResult
}

/**
 * `REQUEST_INBOX.md` 읽기/쓰기 전용 계약이다. `REQUEST_STRUCTURED.md`나
 * `WORKFLOW_STATE.json`은 이 port로 절대 쓰지 않는다.
 *
 * [WorkflowStatePort]와 마찬가지로 동기 파일 I/O만 다루며, 호출자(ViewModel)가 IO dispatcher
 * 안에서 호출할 책임을 진다.
 */
interface RequestWriterPort {
    /** 파일이 없거나 읽을 수 없으면 `null`이다. */
    fun load(day: WorkspaceDay): LoadedRequest?

    /**
     * `expectedVersion`이 저장 직전 실제 파일 버전과 다르면 덮어쓰지 않고 [RequestSaveResult.Conflict]를
     * 반환한다. 성공 시 UTF-8 no BOM으로 temp 파일에 쓴 뒤 원자적으로 이동한다.
     */
    fun save(day: WorkspaceDay, content: String, expectedVersion: FileVersion): RequestSaveResult
}
