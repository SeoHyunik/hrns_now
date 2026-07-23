package io.hrns_now.infra.serialization

import io.hrns_now.core.domain.model.ArtifactReadinessState
import io.hrns_now.core.domain.model.ExecutionWrapperState
import io.hrns_now.core.domain.model.QueueStatus
import io.hrns_now.core.domain.model.QueueBlockedReason
import io.hrns_now.core.domain.model.StopReason
import io.hrns_now.core.domain.model.WorkflowPhase
import io.hrns_now.core.domain.model.WorkflowStatus
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun readFixture(name: String): String {
    val resource = WorkflowStateMapperTest::class.java.classLoader.getResource("fixtures/$name")
        ?: error("fixture not found: $name")
    return Files.readString(Paths.get(resource.toURI()))
}

class WorkflowStateMapperTest {

    private val parser = WorkflowStateParser()
    private val mapper = WorkflowStateMapper()

    private fun parseFixtureDto(text: String): HarnessWorkflowStateDto {
        val parsed = assertIs<ParseResult.Success>(parser.parse(text))
        return parsed.dto
    }

    @Test
    fun `live shape fixture를 도메인 모델로 정확히 변환한다`() {
        val dto = parseFixtureDto(readFixture("workflow-state-live-shape.json"))
        val result = assertIs<MapResult.Success>(mapper.map(dto))
        val state = result.state

        assertEquals(1, state.schemaVersion.major)
        assertEquals("sample-project", state.projectName)
        assertEquals(WorkflowPhase.Execution, state.phase)
        assertEquals(WorkflowStatus.ExecutionBlocked, state.status)
        assertEquals(StopReason.DispatchContractMismatch, state.stopReason)
        assertEquals(ExecutionWrapperState.Code, state.executionWrapper)
        assertEquals(ArtifactReadinessState.Ready, state.artifacts.requestInbox)
        assertEquals(ArtifactReadinessState.Ready, state.artifacts.todayStrategy)
        assertEquals(ArtifactReadinessState.Ready, state.artifacts.dailyHandoff)
        assertEquals(ArtifactReadinessState.Ready, state.artifacts.workflowState)
        assertTrue(state.opsValidation.passed)
        assertEquals(false, state.closure.isCleanHandoff)
        assertEquals(QueueStatus.Active, state.queue.status)
        assertEquals("card-sample0000001", state.queue.active.cardId)
        assertEquals("slice-sample0000001", state.queue.active.sliceId)

        // current_slice는 fixture에서 JSON null -> 도메인 null로 보존된다.
        assertNull(state.currentSliceRaw)
        // slice_queue는 "{}" -> 원문 텍스트가 보존된다(내용 자체는 비어있어도 값 존재).
        assertEquals("{}", result.state.sliceQueueRaw?.text)
        assertTrue(result.state.roleSlicedRaw != null)
        assertTrue(result.state.usageGuardRaw != null)
    }

    @Test
    fun `알려지지 않은 current_status는 원문을 보존한다`() {
        val text = readFixture("workflow-state-live-shape.json")
            .replace("\"current_status\": \"execution_blocked\"", "\"current_status\": \"future_status_xyz\"")
        val dto = parseFixtureDto(text)
        val result = assertIs<MapResult.Success>(mapper.map(dto))
        assertEquals(WorkflowStatus.Unknown("future_status_xyz"), result.state.status)
    }

    @Test
    fun `live taxonomy의 request intake status와 planning required queue를 typed 값으로 변환한다`() {
        val text = readFixture("workflow-state-live-shape.json")
            .replace("\"current_status\": \"execution_blocked\"", "\"current_status\": \"request_intake_pending\"")
            .replaceFirst("\"status\": \"active\",\n", "\"status\": \"planning_required\",\n")
        val dto = parseFixtureDto(text)

        val result = assertIs<MapResult.Success>(mapper.map(dto))

        assertEquals(WorkflowStatus.RequestIntakePending, result.state.status)
        assertEquals(QueueStatus.PlanningRequired, result.state.queue.status)
    }

    @Test
    fun `dispatch metadata conflict queue marker를 stop reason과 구분해 typed 값으로 변환한다`() {
        val text = readFixture("workflow-state-live-shape.json")
            .replace(
                "\"blocked_reason\": \"\"\n  }",
                "\"blocked_reason\": \"dispatch_metadata_conflict\"\n  }",
            )
        val dto = parseFixtureDto(text)

        val result = assertIs<MapResult.Success>(mapper.map(dto))

        assertEquals(QueueBlockedReason.DispatchMetadataConflict, result.state.queue.blockedReason)
        assertEquals(StopReason.DispatchContractMismatch, result.state.stopReason)
    }

    @Test
    fun `알려지지 않은 stop_reason은 원문을 보존한다`() {
        val text = readFixture("workflow-state-live-shape.json")
            .replace("\"stop_reason\": \"dispatch_contract_mismatch\"", "\"stop_reason\": \"future_stop_reason_xyz\"")
        val dto = parseFixtureDto(text)
        val result = assertIs<MapResult.Success>(mapper.map(dto))
        assertEquals(StopReason.Unknown("future_stop_reason_xyz"), result.state.stopReason)
    }

    @Test
    fun `raw 중첩 JSON의 session ID와 token은 domain 경계 전에 치환한다`() {
        val rawSessionId = "11111111-2222-3333-4444-555555555555"
        val rawToken = "top-secret-token-value"
        val text = readFixture("workflow-state-live-shape.json")
            .replace(
                "\"session_id_present\": true,",
                "\"session_id_present\": true,\n          \"session_id\": \"$rawSessionId\",\n          \"access_token\": \"$rawToken\",",
            )
        val dto = parseFixtureDto(text)

        val result = assertIs<MapResult.Success>(mapper.map(dto))
        val roleSliced = assertNotNull(result.state.roleSlicedRaw).text

        assertFalse(roleSliced.contains(rawSessionId))
        assertFalse(roleSliced.contains(rawToken))
        assertTrue(roleSliced.contains("[REDACTED]"))
        assertTrue(roleSliced.contains("session_id_present"))
    }

    @Test
    fun `필수 top-level 필드가 없으면 실패한다`() {
        val text = readFixture("workflow-state-live-shape.json")
            .replaceFirst("\"project_name\": \"sample-project\",\n", "")
        val dto = parseFixtureDto(text)
        val result = assertIs<MapResult.Failure>(mapper.map(dto))
        assertTrue(result.message.contains("project_name"))
    }

    @Test
    fun `required_next_action 누락을 nullable 기본값으로 숨기지 않는다`() {
        val text = readFixture("workflow-state-live-shape.json")
            .replaceFirst(
                ",\n  \"required_next_action\": \"validate workflow-state-primary default runtime window and retire transitional proof scripts\"",
                "",
            )
        val dto = parseFixtureDto(text)

        val result = assertIs<MapResult.Failure>(mapper.map(dto))

        assertTrue(result.message.contains("required_next_action"))
    }

    @Test
    fun `안전 판단 boolean 누락을 false 기본값으로 숨기지 않는다`() {
        val text = readFixture("workflow-state-live-shape.json")
            .replaceFirst("    \"human_action_required\": true,\n", "")
        val dto = parseFixtureDto(text)

        val result = assertIs<MapResult.Failure>(mapper.map(dto))

        assertTrue(result.message.contains("human_action_required"))
    }

    @Test
    fun `artifacts_state 하위 필드가 하나라도 없으면 실패한다`() {
        val text = readFixture("workflow-state-live-shape.json")
            .replaceFirst("\"request_inbox\": \"ready\",\n      \"today_strategy\": \"ready\",\n", "")
        val dto = parseFixtureDto(text)
        val result = assertIs<MapResult.Failure>(mapper.map(dto))
        assertTrue(result.message.contains("artifacts_state"))
    }

    @Test
    fun `queue status가 없으면 실패한다`() {
        val text = readFixture("workflow-state-live-shape.json")
            .replaceFirst("\"status\": \"active\",\n", "")
        val dto = parseFixtureDto(text)
        val result = assertIs<MapResult.Failure>(mapper.map(dto))
        assertTrue(result.message.contains("queue.status"))
    }

    @Test
    fun `schema_version major가 지원 범위를 벗어나도 mapper 자체는 typed 값을 반환한다`() {
        val text = readFixture("workflow-state-live-shape.json")
            .replaceFirst("\"schema_version\": \"1.0\",\n  \"artifact_name\"", "\"schema_version\": \"2.0\",\n  \"artifact_name\"")
        val dto = parseFixtureDto(text)
        val result = assertIs<MapResult.Success>(mapper.map(dto))
        assertEquals(2, result.state.schemaVersion.major)
        // major 적합성 판단은 Adapter의 책임이며 Mapper는 판단하지 않는다.
    }

    @Test
    fun `schema_version 형식이 잘못되면 실패한다`() {
        val text = readFixture("workflow-state-live-shape.json")
            .replaceFirst("\"schema_version\": \"1.0\",\n  \"artifact_name\"", "\"schema_version\": \"not-a-version\",\n  \"artifact_name\"")
        val dto = parseFixtureDto(text)
        val result = assertIs<MapResult.Failure>(mapper.map(dto))
        assertTrue(result.message.contains("schema_version"))
    }
}
