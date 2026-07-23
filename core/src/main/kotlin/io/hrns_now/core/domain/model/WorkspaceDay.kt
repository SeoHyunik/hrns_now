package io.hrns_now.core.domain.model

import java.nio.file.Path
import java.time.LocalDate

/**
 * 프로젝트 워크스페이스 root와 특정 날짜의 day root를 명확히 구분한다.
 *
 * daily 4-file(REQUEST_INBOX.md 등)과 day 산출물 로그는 각각 [dayRoot], [dayLogsRoot] 아래에 있고,
 * wrapper 실행 로그(metrics/preflight/rendered)는 [wrapperLogsRoot] 아래에 있다.
 * 두 로그 위치는 harness-kit 실계약상 서로 다른 경로이며 혼동하지 않는다.
 */
data class WorkspaceDay(
    val projectWorkspaceRoot: Path,
    val date: LocalDate,
) {
    val dayRoot: Path
        get() = projectWorkspaceRoot.resolve(date.toString())

    val dayLogsRoot: Path
        get() = dayRoot.resolve("logs")

    val wrapperLogsRoot: Path
        get() = projectWorkspaceRoot.resolve("logs").resolve(date.toString())
}
