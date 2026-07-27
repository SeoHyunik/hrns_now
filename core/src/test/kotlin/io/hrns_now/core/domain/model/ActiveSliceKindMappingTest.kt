package io.hrns_now.core.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * `state.execution_wrapper` → [ActiveSliceKind] 순수 매핑을 고정한다
 * (`docs/STATE_MODEL.md` §6.3, `doc/hrns_now_design_pattern.md` §6.2).
 */
class ActiveSliceKindMappingTest {

    @Test
    fun `Code는 ActiveSliceKind Code로 매핑한다`() {
        assertEquals(ActiveSliceKind.Code, ExecutionWrapperState.Code.toActiveSliceKind())
    }

    @Test
    fun `Doc은 ActiveSliceKind Doc으로 매핑한다`() {
        assertEquals(ActiveSliceKind.Doc, ExecutionWrapperState.Doc.toActiveSliceKind())
    }

    @Test
    fun `None은 검증전용 매핑이 확정되지 않아 null로 fail-closed한다`() {
        assertNull(ExecutionWrapperState.None.toActiveSliceKind())
    }

    @Test
    fun `Auto는 UI 액션으로 노출하지 않으므로 null로 fail-closed한다`() {
        assertNull(ExecutionWrapperState.Auto.toActiveSliceKind())
    }

    @Test
    fun `Unknown 원문은 raw를 보존한 채 ActiveSliceKind Unknown으로 매핑한다`() {
        val mapped = ExecutionWrapperState.Unknown("weird-value").toActiveSliceKind()

        val unknown = assertIs<ActiveSliceKind.Unknown>(mapped)
        assertEquals("weird-value", unknown.raw)
    }
}
