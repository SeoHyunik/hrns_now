package io.hrns_now.core.port

import io.hrns_now.core.domain.model.WorkspaceDay
import io.hrns_now.core.result.StateReadResult

/**
 * `WORKFLOW_STATE.json` 읽기 계약이다. 구현체는 이 파일을 절대 쓰지 않는다.
 *
 * 동기 파일 I/O만 다루므로 `suspend`를 붙이지 않는다. coroutine 기반
 * 비동기 배선은 ViewModel/프로세스 실행 계층에서 실제로 필요해질 때
 * 도입한다 — 지금 붙이면 core/infra에 kotlinx-coroutines 의존성만 추가되고
 * 아무도 활용하지 않는 선행 추상화가 된다.
 */
interface WorkflowStatePort {
    fun read(day: WorkspaceDay): StateReadResult
}
