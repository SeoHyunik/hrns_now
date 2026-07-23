package io.hrns_now.core.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class StopReasonTest {

    @Test
    fun `live workspace에서 관측된 정상 lifecycle stop reason을 typed 값으로 보존한다`() {
        val observed = mapOf(
            "request_intake_pending" to StopReason.RequestIntakePending,
            "planning_required" to StopReason.PlanningRequired,
            "planning_completed" to StopReason.PlanningCompleted,
            "ready_for_next_slice" to StopReason.ReadyForNextSlice,
            "execution_queue_completed" to StopReason.ExecutionQueueCompleted,
            "dispatch_contract_mismatch" to StopReason.DispatchContractMismatch,
        )

        observed.forEach { (raw, expected) ->
            assertEquals(expected, raw.toStopReason(), "raw=$raw")
        }
    }

    @Test
    fun `새 stop reason은 Unknown으로 원문을 보존한다`() {
        assertEquals(
            StopReason.Unknown("future_stop_reason"),
            "future_stop_reason".toStopReason(),
        )
    }
}
