package io.hrns_now.core.domain.model

/** Registry에 등록된 프로젝트의 typed 식별자다. 표시명(label)을 식별자로 쓰지 않는다. */
data class ProjectId(val value: String) {
    init {
        require(value.isNotBlank()) { "ProjectId는 빈 문자열일 수 없다" }
    }
}
