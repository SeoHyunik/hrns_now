package io.hrns_now.app.presentation

import io.hrns_now.app.presentation.model.NotificationItem
import io.hrns_now.app.presentation.model.NotificationTone
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 전역 알림함의 단일 reducer다. `AppViewModel`이 등록/저장/점검/실행/마감
 * 검증처럼 사용자가 결과를 기다린 action의 typed outcome에서만 [push]를 호출한다 — 단순 화면
 * 전환·읽기 전용 선택에는 쓰지 않는다. `UiAction` 식별자와 표시 label을 분리해 여기 [message]는
 * 이미 조립된 표시 문구만 담는다.
 */
class NotificationCenter(
    private val clock: () -> Instant = Instant::now,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    private val maxItems: Int = 20,
) {
    private val _items = MutableStateFlow<List<NotificationItem>>(emptyList())
    val items: StateFlow<List<NotificationItem>> = _items.asStateFlow()

    fun push(message: String, tone: NotificationTone): String {
        val id = idFactory()
        _items.value = (listOf(NotificationItem(id, message, tone, clock())) + _items.value).take(maxItems)
        return id
    }

    fun markRead(id: String) {
        _items.value = _items.value.map { if (it.id == id) it.copy(read = true) else it }
    }

    fun markAllRead() {
        _items.value = _items.value.map { it.copy(read = true) }
    }

    fun dismiss(id: String) {
        _items.value = _items.value.filterNot { it.id == id }
    }
}
