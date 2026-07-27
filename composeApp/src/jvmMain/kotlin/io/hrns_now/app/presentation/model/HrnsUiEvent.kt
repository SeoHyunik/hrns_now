package io.hrns_now.app.presentation.model

import io.hrns_now.core.domain.model.ProjectId
import io.hrns_now.core.domain.model.UiAction
import io.hrns_now.core.usecase.RegisterProjectCandidate
import java.time.LocalDate

/** Screen에서 ViewModel로 전달하는 typed UI event다. 표시 label은 식별자로 사용하지 않는다. */
sealed interface HrnsUiEvent {
    data class ActionRequested(val action: UiAction) : HrnsUiEvent
    data class ProjectSelected(val id: ProjectId) : HrnsUiEvent
    data class ProjectRegistrationRequested(val candidate: RegisterProjectCandidate) : HrnsUiEvent
    data class ProjectDeletionRequested(val id: ProjectId) : HrnsUiEvent
    data class WorkspaceDaySelected(val date: LocalDate) : HrnsUiEvent

    /** 진행 중인 Doctor/ValidateOps 실행 취소를 요청한다(Phase 3). 실행 중이 아니면 무시된다. */
    data object HarnessRunCancelRequested : HrnsUiEvent

    /** 현재 날짜의 UI 소유 lock을 명시적으로 강제 해제한다(Phase 3). */
    data object LockForceReleaseRequested : HrnsUiEvent
}
