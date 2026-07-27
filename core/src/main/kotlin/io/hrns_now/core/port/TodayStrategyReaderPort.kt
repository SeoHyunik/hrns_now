package io.hrns_now.core.port

import io.hrns_now.core.domain.model.WorkspaceDay

/**
 * `TODAY_STRATEGY.md`의 사람이 읽는 raw 전체 텍스트 읽기 전용 계약이다. 절대 쓰지 않는다.
 *
 * 기계 판단(dispatch 허용 여부, 활성 slice)은 이 텍스트가 아니라 [WorkflowStatePort]가 읽는
 * `WORKFLOW_STATE.json`을 최종 진실로 삼는다 — 이 텍스트와 State가 어긋나면 State가 이긴다
 * (`docs/STATE_MODEL.md`, `doc/claude_prompts/phase4-standard-daily-flow.md` §3).
 */
fun interface TodayStrategyReaderPort {
    /** 파일이 없거나 읽을 수 없으면 `null`이다. */
    fun read(day: WorkspaceDay): String?
}
