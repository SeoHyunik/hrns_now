package io.hrns_now.core.domain.model

import java.time.Instant

/**
 * 파일의 특정 시점 정체성을 나타낸다. 파일시스템 mtime만으로는 partial write 경합이나
 * coarse timestamp 해상도로 인한 오탐을 완전히 배제할 수 없으므로 content hash를 함께 보존한다.
 *
 * [io.hrns_now.core.port.WorkflowStatePort]의 성공 결과 식별에 사용하며,
 * REQUEST_INBOX.md 낙관적 동시성 제어처럼 파일 정체성 비교가 필요한 다른 곳에도 동일한 형태를 재사용할 수 있다.
 */
data class FileVersion(
    val modifiedAt: Instant,
    val size: Long,
    val hash: String,
)
