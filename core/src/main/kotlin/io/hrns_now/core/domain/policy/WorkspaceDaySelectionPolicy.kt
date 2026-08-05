package io.hrns_now.core.domain.policy

import io.hrns_now.core.domain.model.WorkspaceDay
import java.nio.file.Path
import java.time.LocalDate

enum class WorkspaceDayPurpose {
    ReadOnly,
    Execution,
}

enum class WorkspaceDaySelectionSource {
    Explicit,
    Today,
    LatestReadOnlyFallback,
}

data class WorkspaceDaySelection(
    val workspaceDay: WorkspaceDay,
    val source: WorkspaceDaySelectionSource,
    val isReadOnly: Boolean,
)

/**
 * 날짜 선택 우선순위를 파일 시스템 탐색과 분리한 순수 정책이다.
 *
 * 명시 날짜는 존재 여부와 무관하게 우선하고, 명시 날짜가 없으면 오늘을 선택한다.
 * 오늘 디렉터리가 없을 때 최신 날짜로의 fallback은 읽기 전용 조회에서만 허용한다.
 */
class WorkspaceDaySelectionPolicy(
    /**
     * 오늘 날짜다. 호출자(`LoadCockpitUseCase`)가 "명시적으로 선택한 오늘"을
     * 파일 시스템 discovery 여부와 무관하게 허용할 수 있도록 공개한 값이다 — 이 값 자체가 선택 우선순위를
     * 바꾸지는 않는다(그 로직은 여전히 [select] 안에만 있다).
     */
    val today: LocalDate = LocalDate.now(),
) {
    fun select(
        projectWorkspaceRoot: Path,
        explicitDate: LocalDate?,
        availableDates: Collection<LocalDate>,
        purpose: WorkspaceDayPurpose,
    ): WorkspaceDaySelection {
        if (explicitDate != null) {
            return selection(
                projectWorkspaceRoot = projectWorkspaceRoot,
                date = explicitDate,
                source = WorkspaceDaySelectionSource.Explicit,
            )
        }

        if (today in availableDates || purpose == WorkspaceDayPurpose.Execution) {
            return selection(
                projectWorkspaceRoot = projectWorkspaceRoot,
                date = today,
                source = WorkspaceDaySelectionSource.Today,
            )
        }

        val latestDate = availableDates.maxOrNull()
        return if (latestDate != null) {
            selection(
                projectWorkspaceRoot = projectWorkspaceRoot,
                date = latestDate,
                source = WorkspaceDaySelectionSource.LatestReadOnlyFallback,
            )
        } else {
            selection(
                projectWorkspaceRoot = projectWorkspaceRoot,
                date = today,
                source = WorkspaceDaySelectionSource.Today,
            )
        }
    }

    private fun selection(
        projectWorkspaceRoot: Path,
        date: LocalDate,
        source: WorkspaceDaySelectionSource,
    ): WorkspaceDaySelection =
        WorkspaceDaySelection(
            workspaceDay = WorkspaceDay(
                projectWorkspaceRoot = projectWorkspaceRoot,
                date = date,
            ),
            source = source,
            isReadOnly = date != today,
        )
}
