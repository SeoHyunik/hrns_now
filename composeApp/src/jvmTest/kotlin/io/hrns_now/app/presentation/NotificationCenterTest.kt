package io.hrns_now.app.presentation

import io.hrns_now.app.presentation.model.NotificationTone
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 새 Phase 8 §4.2: 전역 알림함 reducer의 push/dismiss/read 상태 전이만 검증한다. raw process
 * detail을 담지 않는지는 [message] 값 자체가 이미 typed 요약 문자열이라는 계약으로 상위
 * 호출자(`AppViewModel`)가 보장하므로, 여기서는 reducer의 리스트 연산만 확인한다.
 */
class NotificationCenterTest {

    private fun center(maxItems: Int = 20): NotificationCenter {
        var counter = 0
        return NotificationCenter(
            clock = { Instant.EPOCH },
            idFactory = { "id-${counter++}" },
            maxItems = maxItems,
        )
    }

    @Test
    fun `push한 항목은 unread 상태로 목록 맨 앞에 쌓인다`() {
        val center = center()

        center.push("첫 번째", NotificationTone.Success)
        center.push("두 번째", NotificationTone.Failure)

        val items = center.items.value
        assertEquals(listOf("두 번째", "첫 번째"), items.map { it.message })
        assertTrue(items.all { !it.read })
    }

    @Test
    fun `markRead는 해당 id만 읽음 처리하고 나머지는 그대로 둔다`() {
        val center = center()
        center.push("A", NotificationTone.Info)
        center.push("B", NotificationTone.Info)
        val targetId = center.items.value.first { it.message == "A" }.id

        center.markRead(targetId)

        val items = center.items.value
        assertEquals(true, items.first { it.message == "A" }.read)
        assertEquals(false, items.first { it.message == "B" }.read)
    }

    @Test
    fun `markAllRead는 모든 항목을 읽음 처리한다`() {
        val center = center()
        center.push("A", NotificationTone.Info)
        center.push("B", NotificationTone.Info)

        center.markAllRead()

        assertTrue(center.items.value.all { it.read })
    }

    @Test
    fun `dismiss는 알림함 이력에서 해당 항목을 완전히 제거한다`() {
        val center = center()
        val id = center.push("사라질 항목", NotificationTone.Failure)
        center.push("남을 항목", NotificationTone.Success)

        center.dismiss(id)

        assertEquals(listOf("남을 항목"), center.items.value.map { it.message })
    }

    @Test
    fun `maxItems를 넘으면 가장 오래된 항목부터 잘려나간다`() {
        val center = center(maxItems = 2)

        center.push("1", NotificationTone.Info)
        center.push("2", NotificationTone.Info)
        center.push("3", NotificationTone.Info)

        assertEquals(listOf("3", "2"), center.items.value.map { it.message })
    }
}
