package io.hrns_now.core.usecase

import io.hrns_now.core.domain.model.FileVersion
import io.hrns_now.core.domain.model.RequestEntryDraft
import io.hrns_now.core.domain.model.RequestEntryPriority
import io.hrns_now.core.domain.model.RequestEntrySource
import io.hrns_now.core.domain.model.RequestEntryType
import io.hrns_now.core.domain.model.WorkspaceDay
import io.hrns_now.core.port.LoadedRequest
import io.hrns_now.core.port.RequestSaveResult
import io.hrns_now.core.port.RequestWriterPort
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SaveRequestUseCaseTest {
    private val day = WorkspaceDay(Path.of("C:/workspace"), LocalDate.of(2026, 7, 27))
    private val version = FileVersion(Instant.EPOCH, 11L, "before")
    private val draft = RequestEntryDraft(
        title = "새 요청",
        type = RequestEntryType.Bug,
        source = RequestEntrySource.Human,
        priority = RequestEntryPriority.High,
        summary = "요약",
        detail = "상세",
        constraints = "경계 유지",
    )

    @Test
    fun `현재 버전으로 REQUEST_INBOX 항목만 추가 저장한다`() {
        var savedContent: String? = null
        var savedVersion: FileVersion? = null
        val writer = object : RequestWriterPort {
            override fun load(day: WorkspaceDay) = LoadedRequest("# REQUEST_INBOX\n", version)
            override fun save(day: WorkspaceDay, content: String, expectedVersion: FileVersion): RequestSaveResult {
                savedContent = content
                savedVersion = expectedVersion
                return RequestSaveResult.Saved
            }
        }

        val outcome = SaveRequestUseCase(writer).invoke(day, draft)

        assertEquals(SaveRequestOutcome.Saved, outcome)
        assertEquals(version, savedVersion)
        assertTrue(requireNotNull(savedContent).contains("### 새 요청"))
        assertTrue(requireNotNull(savedContent).contains("- Constraints:"))
    }

    @Test
    fun `동시 변경 충돌을 실패로 바꾸지 않고 호출자에게 보존한다`() {
        val writer = object : RequestWriterPort {
            override fun load(day: WorkspaceDay) = LoadedRequest("# REQUEST_INBOX", version)
            override fun save(day: WorkspaceDay, content: String, expectedVersion: FileVersion) =
                RequestSaveResult.Conflict(FileVersion(Instant.EPOCH, 12L, "after"))
        }

        val outcome = SaveRequestUseCase(writer).invoke(day, draft)

        assertEquals(SaveRequestOutcome.Conflict, outcome)
    }

    @Test
    fun `입력 파일이 없으면 쓰지 않는다`() {
        var saved = false
        val writer = object : RequestWriterPort {
            override fun load(day: WorkspaceDay): LoadedRequest? = null
            override fun save(day: WorkspaceDay, content: String, expectedVersion: FileVersion): RequestSaveResult {
                saved = true
                return RequestSaveResult.Saved
            }
        }

        val outcome = SaveRequestUseCase(writer).invoke(day, draft)

        assertEquals(SaveRequestOutcome.InboxUnavailable, outcome)
        assertEquals(false, saved)
    }
}