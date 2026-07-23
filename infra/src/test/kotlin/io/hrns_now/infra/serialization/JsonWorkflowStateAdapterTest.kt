package io.hrns_now.infra.serialization

import io.hrns_now.core.domain.model.StopReason
import io.hrns_now.core.domain.model.WorkflowPhase
import io.hrns_now.core.domain.model.WorkflowStatus
import io.hrns_now.core.domain.model.WorkspaceDay
import io.hrns_now.core.domain.policy.StateReadRetryPolicy
import io.hrns_now.core.result.StateReadResult
import java.nio.file.Files
import java.nio.file.AccessDeniedException
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.FileTime
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun readFixtureText(name: String): String {
    val resource = JsonWorkflowStateAdapterTest::class.java.classLoader.getResource("fixtures/$name")
        ?: error("fixture not found: $name")
    return Files.readString(Paths.get(resource.toURI()))
}

class JsonWorkflowStateAdapterTest {

    private lateinit var tempRoot: Path
    private val date: LocalDate = LocalDate.of(2026, 6, 26)

    @BeforeTest
    fun setUp() {
        tempRoot = Files.createTempDirectory("hrns-now-state-adapter-")
    }

    @AfterTest
    fun tearDown() {
        tempRoot.toFile().deleteRecursively()
    }

    private fun day(root: Path = tempRoot): WorkspaceDay = WorkspaceDay(root, date)

    private fun stateFilePath(root: Path = tempRoot): Path =
        root.resolve(date.toString()).resolve("WORKFLOW_STATE.json")

    private fun writeStateBytes(bytes: ByteArray, root: Path = tempRoot) {
        val path = stateFilePath(root)
        Files.createDirectories(path.parent)
        Files.write(path, bytes)
    }

    private fun writeStateText(text: String, root: Path = tempRoot) =
        writeStateBytes(text.toByteArray(Charsets.UTF_8), root)

    private fun liveShapeText(): String = readFixtureText("workflow-state-live-shape.json")

    // ── 1. 정상 파싱 ────────────────────────────────────────────────────────

    @Test
    fun `live shape fixture를 읽어 typed WorkflowState를 반환한다`() {
        writeStateText(liveShapeText())
        val adapter = JsonWorkflowStateAdapter()

        val result = assertIs<StateReadResult.Success>(adapter.read(day()))

        assertEquals(WorkflowPhase.Execution, result.state.phase)
        assertEquals(WorkflowStatus.ExecutionBlocked, result.state.status)
        assertEquals(StopReason.DispatchContractMismatch, result.state.stopReason)
        assertEquals("sample-project", result.state.projectName)
        assertTrue(result.sourceVersion.size > 0)
        assertTrue(result.sourceVersion.hash.isNotBlank())
    }

    // ── 2/3. unknown 값 보존 ───────────────────────────────────────────────

    @Test
    fun `unknown top-level 및 nested key가 있어도 성공적으로 읽는다`() {
        val text = liveShapeText()
            .replace("\"notes\":", "\"brand_new_top_level_field\": 123,\n  \"notes\":")
            .replace(
                "\"current_phase\": \"execution\",",
                "\"brand_new_nested_field\": {\"x\": 1},\n    \"current_phase\": \"execution\",",
            )
        writeStateText(text)
        val result = assertIs<StateReadResult.Success>(JsonWorkflowStateAdapter().read(day()))
        assertEquals(WorkflowPhase.Execution, result.state.phase)
    }

    @Test
    fun `알려지지 않은 current_status 원문을 보존한다`() {
        val text = liveShapeText().replace(
            "\"current_status\": \"execution_blocked\"",
            "\"current_status\": \"future_status_xyz\"",
        )
        writeStateText(text)
        val result = assertIs<StateReadResult.Success>(JsonWorkflowStateAdapter().read(day()))
        assertEquals(WorkflowStatus.Unknown("future_status_xyz"), result.state.status)
    }

    @Test
    fun `알려지지 않은 stop_reason 원문을 보존한다`() {
        val text = liveShapeText().replace(
            "\"stop_reason\": \"dispatch_contract_mismatch\"",
            "\"stop_reason\": \"future_stop_reason_xyz\"",
        )
        writeStateText(text)
        val result = assertIs<StateReadResult.Success>(JsonWorkflowStateAdapter().read(day()))
        assertEquals(StopReason.Unknown("future_stop_reason_xyz"), result.state.stopReason)
    }

    // ── 5. 필수 필드 누락 ──────────────────────────────────────────────────

    @Test
    fun `필수 top-level 필드 누락은 재시도 소진 후 Malformed를 반환한다`() {
        val text = liveShapeText().replaceFirst("\"project_name\": \"sample-project\",\n", "")
        writeStateText(text)
        val adapter = JsonWorkflowStateAdapter(retryPolicy = StateReadRetryPolicy(maxAttempts = 2, delayMillis = 0))

        val result = assertIs<StateReadResult.Malformed>(adapter.read(day()))
        assertTrue(result.message.contains("project_name"))
        assertNull(result.lastKnownGood)
    }

    // ── 6/7. schema major/minor ────────────────────────────────────────────

    @Test
    fun `schema major 불일치는 재시도 없이 UnsupportedSchema를 반환한다`() {
        val text = liveShapeText()
            .replaceFirst("\"schema_version\": \"1.0\",\n  \"artifact_name\"", "\"schema_version\": \"2.0\",\n  \"artifact_name\"")
        writeStateText(text)
        var sleepCalls = 0
        val adapter = JsonWorkflowStateAdapter(sleep = { sleepCalls++ })

        val result = assertIs<StateReadResult.UnsupportedSchema>(adapter.read(day()))
        assertEquals("2.0", result.rawVersion)
        assertEquals(0, sleepCalls)
    }

    @Test
    fun `상위 minor는 정상적으로 읽는다`() {
        val text = liveShapeText()
            .replaceFirst("\"schema_version\": \"1.0\",\n  \"artifact_name\"", "\"schema_version\": \"1.9\",\n  \"artifact_name\"")
        writeStateText(text)
        val result = assertIs<StateReadResult.Success>(JsonWorkflowStateAdapter().read(day()))
        assertEquals(1, result.state.schemaVersion.major)
        assertEquals(9, result.state.schemaVersion.minor)
    }

    // ── 8/9. partial write retry와 last-known-good ─────────────────────────

    @Test
    fun `잘려나간 JSON은 재시도 중 파일이 복구되면 성공한다`() {
        val fullText = liveShapeText()
        val truncated = fullText.substring(0, fullText.length / 3)
        writeStateText(truncated)

        var sleepCalls = 0
        val adapter = JsonWorkflowStateAdapter(
            retryPolicy = StateReadRetryPolicy(maxAttempts = 3, delayMillis = 0),
            sleep = { sleepCalls++; writeStateText(fullText) },
        )

        val result = assertIs<StateReadResult.Success>(adapter.read(day()))
        assertEquals(WorkflowPhase.Execution, result.state.phase)
        assertTrue(sleepCalls >= 1)
    }

    @Test
    fun `반복 malformed 후 last-known-good을 stale로 유지한다`() {
        val adapter = JsonWorkflowStateAdapter(retryPolicy = StateReadRetryPolicy(maxAttempts = 2, delayMillis = 0))

        // 1) 먼저 성공적으로 읽어 last-known-good을 채운다.
        writeStateText(liveShapeText())
        val firstResult = assertIs<StateReadResult.Success>(adapter.read(day()))

        // 2) 파일을 영구적으로 손상시킨 뒤 같은 adapter 인스턴스로 다시 읽는다.
        writeStateText(liveShapeText().substring(0, 20))
        val secondResult = assertIs<StateReadResult.Malformed>(adapter.read(day()))

        assertNotNull(secondResult.lastKnownGood)
        assertEquals(firstResult.state, secondResult.lastKnownGood)
    }

    // ── 10/11. encoding ─────────────────────────────────────────────────────

    @Test
    fun `UTF-8 BOM이 있어도 정상적으로 읽는다`() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        writeStateBytes(bom + liveShapeText().toByteArray(Charsets.UTF_8))

        val result = assertIs<StateReadResult.Success>(JsonWorkflowStateAdapter().read(day()))
        assertEquals(WorkflowPhase.Execution, result.state.phase)
    }

    @Test
    fun `잘못된 UTF-8 바이트는 JSON malformed와 구분되는 EncodingError를 반환한다`() {
        // 0xC3 단독은 2바이트 UTF-8 시퀀스의 선행 바이트만 있고 후속 바이트가 없어 잘못된 시퀀스다.
        val invalidBytes = byteArrayOf(0x7B, 0x22, 0x61, 0x22, 0xC3.toByte(), 0x3A, 0x31, 0x7D) // {"a":<invalid>:1}
        writeStateBytes(invalidBytes)
        val adapter = JsonWorkflowStateAdapter(retryPolicy = StateReadRetryPolicy(maxAttempts = 2, delayMillis = 0))

        val result = assertIs<StateReadResult.EncodingError>(adapter.read(day()))
        assertTrue(result.message.isNotBlank())
        assertNull(result.lastKnownGood)
    }

    // ── 12. mtime/size 변경 감지 후 재시도 ──────────────────────────────────

    @Test
    fun `읽기 전후 metadata가 바뀌면 결정적으로 재시도한다`() {
        val bytes = liveShapeText().toByteArray(Charsets.UTF_8)
        val firstVersion = FileTime.fromMillis(1_000)
        val stableVersion = FileTime.fromMillis(2_000)
        var snapshotCalls = 0
        val fileAccess = object : WorkflowStateFileAccess {
            override fun snapshot(path: Path): FsSnapshot {
                snapshotCalls++
                val modifiedAt = if (snapshotCalls == 1) firstVersion else stableVersion
                return FsSnapshot(modifiedAt = modifiedAt, size = bytes.size.toLong())
            }

            override fun readAllBytes(path: Path): ByteArray = bytes
        }
        var sleepCalls = 0
        val adapter = JsonWorkflowStateAdapter(
            fileAccess = fileAccess,
            retryPolicy = StateReadRetryPolicy(maxAttempts = 3, delayMillis = 0),
            sleep = { sleepCalls++ },
        )

        val result = assertIs<StateReadResult.Success>(adapter.read(day()))

        assertEquals(1, sleepCalls)
        assertEquals(stableVersion.toInstant(), result.sourceVersion.modifiedAt)
    }

    // ── 13. 파일 없음 ───────────────────────────────────────────────────────

    @Test
    fun `상태 파일이 없으면 Missing을 반환한다`() {
        val result = assertIs<StateReadResult.Missing>(JsonWorkflowStateAdapter().read(day()))
        assertEquals(stateFilePath(), result.path)
    }

    @Test
    fun `metadata 접근이 거부되면 예외 대신 AccessDenied를 반환한다`() {
        val deniedAccess = object : WorkflowStateFileAccess {
            override fun snapshot(path: Path): FsSnapshot = throw AccessDeniedException(path.toString())
            override fun readAllBytes(path: Path): ByteArray = error("접근 거부 후 본문을 읽으면 안 된다")
        }
        val adapter = JsonWorkflowStateAdapter(
            fileAccess = deniedAccess,
            retryPolicy = StateReadRetryPolicy(maxAttempts = 3, delayMillis = 0),
            sleep = {},
        )

        val result = assertIs<StateReadResult.AccessDenied>(adapter.read(day()))

        assertEquals(stateFilePath().toAbsolutePath().normalize(), result.path)
    }

    // ── 14. 빈 파일 ─────────────────────────────────────────────────────────

    @Test
    fun `빈 파일은 재시도 소진 후 Malformed를 반환한다`() {
        writeStateText("")
        val adapter = JsonWorkflowStateAdapter(retryPolicy = StateReadRetryPolicy(maxAttempts = 2, delayMillis = 0))
        assertIs<StateReadResult.Malformed>(adapter.read(day()))
    }

    // ── 15. 공백·한글 경로 ──────────────────────────────────────────────────

    @Test
    fun `공백과 한글이 포함된 workspace 경로에서도 정상적으로 읽는다`() {
        val nestedRoot = tempRoot.resolve("한글 작업 공간 폴더")
        writeStateText(liveShapeText(), nestedRoot)

        val result = assertIs<StateReadResult.Success>(JsonWorkflowStateAdapter().read(day(nestedRoot)))
        assertEquals(WorkflowPhase.Execution, result.state.phase)
    }
}
