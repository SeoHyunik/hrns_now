package io.hrns_now.app.presentation.model

import io.hrns_now.core.domain.model.UiAction

/** Screen에서 ViewModel로 전달하는 typed UI event다. 표시 label은 식별자로 사용하지 않는다. */
sealed interface HrnsUiEvent {
    data class ActionRequested(val action: UiAction) : HrnsUiEvent
}
