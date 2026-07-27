package io.hrns_now.core.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RequestEntryTest {

    @Test
    fun `모든 필드가 템플릿의 Recommended Entry Format 순서로 렌더링된다`() {
        val draft = RequestEntryDraft(
            title = "topic page loads wrong content",
            type = RequestEntryType.Bug,
            source = RequestEntrySource.Qa,
            priority = RequestEntryPriority.High,
            summary = "stale content renders briefly",
            detail = "observed while switching routes\nstale data appears briefly",
            constraints = "must not regress cache reuse",
        )

        val markdown = draft.toMarkdownEntry()

        assertEquals(
            """
            ### topic page loads wrong content

            - Type: bug
            - Source: QA
            - Priority: high
            - Summary: stale content renders briefly
            - Details:
              - observed while switching routes
              - stale data appears briefly
            - Constraints:
              - must not regress cache reuse
            """.trimIndent(),
            markdown,
        )
    }

    @Test
    fun `제약이 비어 있으면 Constraints 절을 만들지 않는다`() {
        val draft = RequestEntryDraft(
            title = "제목",
            type = RequestEntryType.Idea,
            source = RequestEntrySource.Human,
            priority = RequestEntryPriority.Unknown,
            summary = "요약",
            detail = "상세",
            constraints = "",
        )

        val markdown = draft.toMarkdownEntry()

        assertTrue("Constraints" !in markdown)
    }

    @Test
    fun `상세가 비어 있으면 none 자리 표시자를 남긴다`() {
        val draft = RequestEntryDraft(
            title = "제목",
            type = RequestEntryType.Question,
            source = RequestEntrySource.Other,
            priority = RequestEntryPriority.Low,
            summary = "요약",
            detail = "",
            constraints = "",
        )

        val markdown = draft.toMarkdownEntry()

        assertTrue(markdown.contains("- Details:\n  - (none)"))
    }
}
