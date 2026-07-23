package io.hrns_now.core.domain.policy

import io.hrns_now.core.domain.model.WorkspaceDay
import java.nio.file.Path
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkspaceDaySelectionPolicyTest {
    private val root = Path.of("한글 작업 공간")
    private val today = LocalDate.of(2026, 7, 23)
    private val policy = WorkspaceDaySelectionPolicy(today)

    @Test
    fun `명시 날짜가 오늘과 최신 날짜보다 우선한다`() {
        val explicitDate = LocalDate.of(2026, 7, 21)

        val selection = policy.select(
            projectWorkspaceRoot = root,
            explicitDate = explicitDate,
            availableDates = listOf(today, LocalDate.of(2026, 7, 24)),
            purpose = WorkspaceDayPurpose.ReadOnly,
        )

        assertEquals(explicitDate, selection.workspaceDay.date)
        assertEquals(WorkspaceDaySelectionSource.Explicit, selection.source)
        assertTrue(selection.isReadOnly)
    }

    @Test
    fun `명시 날짜가 없으면 오늘이 최신 날짜보다 우선한다`() {
        val selection = policy.select(
            projectWorkspaceRoot = root,
            explicitDate = null,
            availableDates = listOf(LocalDate.of(2026, 7, 22), today, LocalDate.of(2026, 7, 24)),
            purpose = WorkspaceDayPurpose.ReadOnly,
        )

        assertEquals(today, selection.workspaceDay.date)
        assertEquals(WorkspaceDaySelectionSource.Today, selection.source)
        assertFalse(selection.isReadOnly)
    }

    @Test
    fun `오늘이 없으면 읽기 전용 조회만 최신 날짜로 대체한다`() {
        val latestDate = LocalDate.of(2026, 7, 22)

        val selection = policy.select(
            projectWorkspaceRoot = root,
            explicitDate = null,
            availableDates = listOf(LocalDate.of(2026, 7, 20), latestDate),
            purpose = WorkspaceDayPurpose.ReadOnly,
        )

        assertEquals(latestDate, selection.workspaceDay.date)
        assertEquals(WorkspaceDaySelectionSource.LatestReadOnlyFallback, selection.source)
        assertTrue(selection.isReadOnly)
    }

    @Test
    fun `실행 목적은 과거 최신 날짜로 자동 대체하지 않는다`() {
        val selection = policy.select(
            projectWorkspaceRoot = root,
            explicitDate = null,
            availableDates = listOf(LocalDate.of(2026, 7, 22)),
            purpose = WorkspaceDayPurpose.Execution,
        )

        assertEquals(today, selection.workspaceDay.date)
        assertEquals(WorkspaceDaySelectionSource.Today, selection.source)
        assertFalse(selection.isReadOnly)
    }

    @Test
    fun `WorkspaceDay는 daily와 두 로그 root를 분리한다`() {
        val workspaceDay = WorkspaceDay(projectWorkspaceRoot = root, date = today)

        assertEquals(root.resolve("2026-07-23"), workspaceDay.dayRoot)
        assertEquals(root.resolve("2026-07-23").resolve("logs"), workspaceDay.dayLogsRoot)
        assertEquals(root.resolve("logs").resolve("2026-07-23"), workspaceDay.wrapperLogsRoot)
    }
}
