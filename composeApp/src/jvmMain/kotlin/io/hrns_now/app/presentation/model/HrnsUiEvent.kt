package io.hrns_now.app.presentation.model

import io.hrns_now.core.domain.model.ProjectId
import io.hrns_now.core.domain.model.RequestEntryDraft
import io.hrns_now.core.domain.model.UiAction
import io.hrns_now.core.usecase.RegisterProjectCandidate
import java.time.LocalDate

/** Screen에서 ViewModel로 전달하는 typed UI event다. 표시 label은 식별자로 사용하지 않는다. */
sealed interface HrnsUiEvent {
    data class ActionRequested(val action: UiAction) : HrnsUiEvent
    /**
     * Closure는 ActionPolicy와 별도의 ClosurePolicy 재확인이 필요하다. repository 변경을
     * 인지한 경우에만 [incompleteHandoffAcknowledged]가 true가 될 수 있으며, 일반 action
     * event가 이 확인을 우회할 수 없다.
     */
    data class ClosureValidationRequested(val incompleteHandoffAcknowledged: Boolean) : HrnsUiEvent
    data class ProjectSelected(val id: ProjectId) : HrnsUiEvent
    data class ProjectRegistrationRequested(val candidate: RegisterProjectCandidate) : HrnsUiEvent
    data class ProjectDeletionRequested(val id: ProjectId) : HrnsUiEvent
    data class WorkspaceDaySelected(val date: LocalDate) : HrnsUiEvent

    /** 진행 중인 Doctor/ValidateOps 실행 취소를 요청한다(Phase 3). 실행 중이 아니면 무시된다. */
    data object HarnessRunCancelRequested : HrnsUiEvent

    /** 현재 날짜의 UI 소유 lock을 명시적으로 강제 해제한다(Phase 3). */
    data object LockForceReleaseRequested : HrnsUiEvent

    /** `REQUEST_INBOX.md`에 새 항목을 추가 저장한다(Phase 4). `REQUEST_STRUCTURED.md`는 건드리지 않는다. */
    data class RequestEntrySubmitted(val draft: RequestEntryDraft) : HrnsUiEvent
}
