package io.hrns_now.infra.request

import io.hrns_now.core.domain.model.FileVersion
import io.hrns_now.core.domain.model.WorkspaceDay
import io.hrns_now.core.port.RequestSaveResult
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RequestInboxWriterAdapterTest {

    private lateinit var tempRoot: Path
    private val date: LocalDate = LocalDate.of(2026, 7, 27)
    private val adapter = RequestInboxWriterAdapter()

    @BeforeTest
    fun setUp() {
        tempRoot = Files.createTempDirectory("hrns-now-request-inbox-")
    }

    @AfterTest
    fun tearDown() {
        tempRoot.toFile().deleteRecursively()
    }

    private fun day(root: Path = tempRoot): WorkspaceDay = WorkspaceDay(root, date)

    private fun requestInboxPath(root: Path = tempRoot): Path =
        root.resolve(date.toString()).resolve("REQUEST_INBOX.md")

    private fun writeRequestInbox(bytes: ByteArray, root: Path = tempRoot) {
        val path = requestInboxPath(root)
        Files.createDirectories(path.parent)
        Files.write(path, bytes)
    }

    @Test
    fun `파일이 없으면 load는 null이다`() {
        assertNull(adapter.load(day()))
    }

    @Test
    fun `파일이 있으면 내용과 버전을 함께 읽는다`() {
        writeRequestInbox("# REQUEST_INBOX\n\n내용".toByteArray(Charsets.UTF_8))

        val loaded = adapter.load(day())

        assertEquals("# REQUEST_INBOX\n\n내용", loaded?.content)
        assertEquals(requestInboxPath().let(Files::size), loaded?.version?.size)
    }

    @Test
    fun `BOM이 있으면 제거하고 읽는다`() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        writeRequestInbox(bom + "본문".toByteArray(Charsets.UTF_8))

        val loaded = adapter.load(day())

        assertEquals("본문", loaded?.content)
    }

    @Test
    fun `버전이 일치하면 저장하고 새 내용을 UTF-8 no BOM으로 남긴다`() {
        writeRequestInbox("원본".toByteArray(Charsets.UTF_8))
        val loaded = requireNotNull(adapter.load(day()))

        val result = adapter.save(day(), "새 내용", loaded.version)

        assertEquals(RequestSaveResult.Saved, result)
        val bytes = Files.readAllBytes(requestInboxPath())
        assertEquals("새 내용", String(bytes, Charsets.UTF_8))
        assertTrue(bytes.take(3) != listOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
    }

    @Test
    fun `버전이 저장 직전 파일과 다르면 덮어쓰지 않고 Conflict를 반환한다`() {
        writeRequestInbox("원본".toByteArray(Charsets.UTF_8))
        val loaded = requireNotNull(adapter.load(day()))

        // 다른 프로세스가 먼저 저장한 것처럼 파일을 바꾼다.
        writeRequestInbox("다른 곳에서 바뀐 내용".toByteArray(Charsets.UTF_8))

        val result = adapter.save(day(), "내 새 내용", loaded.version)

        assertIs<RequestSaveResult.Conflict>(result)
        assertEquals("다른 곳에서 바뀐 내용", String(Files.readAllBytes(requestInboxPath()), Charsets.UTF_8))
    }

    @Test
    fun `저장 직전 파일이 없으면 Failed를 반환하고 새 파일을 만들지 않는다`() {
        val result = adapter.save(day(), "내용", FileVersion(java.time.Instant.EPOCH, 0, "hash"))

        assertIs<RequestSaveResult.Failed>(result)
        assertTrue(Files.notExists(requestInboxPath()))
    }

    @Test
    fun `저장 후 임시 파일을 남기지 않는다`() {
        writeRequestInbox("원본".toByteArray(Charsets.UTF_8))
        val loaded = requireNotNull(adapter.load(day()))

        adapter.save(day(), "새 내용", loaded.version)

        val remaining = Files.list(requestInboxPath().parent).use { it.toList() }
        assertEquals(listOf(requestInboxPath()), remaining)
    }

    @Test
    fun `공백과 한글이 섞인 workspace 경로에서도 정상 동작한다`() {
        val root = tempRoot.resolve("작업 공간 폴더")
        writeRequestInbox("한글 내용".toByteArray(Charsets.UTF_8), root)

        val loaded = requireNotNull(adapter.load(day(root)))
        assertEquals("한글 내용", loaded.content)

        val result = adapter.save(day(root), "갱신된 한글 내용", loaded.version)
        assertEquals(RequestSaveResult.Saved, result)
    }
}
