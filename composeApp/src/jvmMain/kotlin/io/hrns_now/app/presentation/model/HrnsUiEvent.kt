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

    /**
     * [prepareWorkspace]가 true(기본값)면 등록·선택·context 재조회에 이어 프로젝트 준비
     * (`enter-project` bridge/외부 workspace 온보딩)까지 한 흐름으로 시도한다. 등록만
     * 원하면 false를 보낸다 — 이 경우에도 등록 자체의 검증·경계·Doctor·compatibility 절차는
     * 동일하다. 이 이벤트는 사용자가 확인 UI(bridge 3종·external workspace path·"기존 bridge는
     * 덮어쓰지 않음")를 거친 뒤에만 `prepareWorkspace = true`로 올라온다.
     */
    data class ProjectRegistrationRequested(
        val candidate: RegisterProjectCandidate,
        val prepareWorkspace: Boolean = true,
    ) : HrnsUiEvent

    /** 등록 modal을 닫을 때 이전 시도의 running/success/failure 표시를 지운다. */
    data object RegistrationFeedbackDismissed : HrnsUiEvent
    data class ProjectDeletionRequested(val id: ProjectId) : HrnsUiEvent

    /**
     * Registry의 "마지막으로 활성화된 프로젝트" 선택만 지운다 — 등록된 project
     * entry, workspace, repository, Harness Kit, State/daily 파일은 건드리지 않는다.
     */
    data object ActiveProjectReleaseRequested : HrnsUiEvent

    /**
     * 이미 등록된 활성 프로젝트의 repository bridge 또는 오늘 external workspace 준비가
     * 누락됐을 때 다시 시도하는 단일 CTA다. Health Check(Doctor)와 구분되는 쓰기
     * 행동이며, daily `ActionPolicy`를 거치지 않고 현재 활성 프로젝트에 바로 적용된다.
     */
    data object ProjectOnboardingRequested : HrnsUiEvent
    data class WorkspaceDaySelected(val date: LocalDate) : HrnsUiEvent

    /** 진행 중인 Doctor/ValidateOps 실행 취소를 요청한다. 실행 중이 아니면 무시된다. */
    data object HarnessRunCancelRequested : HrnsUiEvent

    /** 현재 날짜의 UI 소유 lock을 명시적으로 강제 해제한다. */
    data object LockForceReleaseRequested : HrnsUiEvent

    /** `REQUEST_INBOX.md`에 새 항목을 추가 저장한다. `REQUEST_STRUCTURED.md`는 건드리지 않는다. */
    data class RequestEntrySubmitted(val draft: RequestEntryDraft) : HrnsUiEvent
}
