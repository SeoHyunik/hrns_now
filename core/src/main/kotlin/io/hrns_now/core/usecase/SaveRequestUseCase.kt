package io.hrns_now.core.usecase

import io.hrns_now.core.domain.model.RequestEntryDraft
import io.hrns_now.core.domain.model.WorkspaceDay
import io.hrns_now.core.domain.model.toMarkdownEntry
import io.hrns_now.core.port.RequestSaveResult
import io.hrns_now.core.port.RequestWriterPort

/**
 * `REQUEST_INBOX.md`에 사람 입력을 추가하는 Phase 4 command use case다.
 *
 * 저장 대상과 낙관적 동시성 검증은 [RequestWriterPort]로 한정한다. 이 use case는
 * `REQUEST_STRUCTURED.md`나 `WORKFLOW_STATE.json`에 접근하지 않으며, 파일 I/O dispatcher의
 * 선택은 호출자(ViewModel)가 담당한다.
 */
class SaveRequestUseCase(
    private val requestWriter: RequestWriterPort,
) {
    fun invoke(day: WorkspaceDay, draft: RequestEntryDraft): SaveRequestOutcome {
        val loaded = requestWriter.load(day) ?: return SaveRequestOutcome.InboxUnavailable
        val content = loaded.content.trimEnd('\n', ' ', '\t', '\r') + "\n\n" + draft.toMarkdownEntry() + "\n"
        return when (val result = requestWriter.save(day, content, loaded.version)) {
            RequestSaveResult.Saved -> SaveRequestOutcome.Saved
            is RequestSaveResult.Conflict -> SaveRequestOutcome.Conflict
            is RequestSaveResult.Failed -> SaveRequestOutcome.Failed(result.reason)
        }
    }
}

sealed interface SaveRequestOutcome {
    data object Saved : SaveRequestOutcome
    data object InboxUnavailable : SaveRequestOutcome
    data object Conflict : SaveRequestOutcome
    data class Failed(val reason: String) : SaveRequestOutcome
}