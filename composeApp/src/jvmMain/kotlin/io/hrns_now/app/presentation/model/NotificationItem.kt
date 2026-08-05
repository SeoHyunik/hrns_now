package io.hrns_now.app.presentation.model

import java.time.Instant

/** 알림 카드의 색조다 — 정보성 값만 담고 원문 stdout/세션/경로 전체는 담지 않는다. */
enum class NotificationTone { Success, Failure, Info }

/**
 * 전역 알림함 한 항목이다. `message`는 이미 typed/label 처리된 안전한 요약 문장이어야 한다 —
 * raw process output·secret·session ID·전체 경로를 담지 않는다.
 */
data class NotificationItem(
    val id: String,
    val message: String,
    val tone: NotificationTone,
    val createdAt: Instant,
    val read: Boolean = false,
)
