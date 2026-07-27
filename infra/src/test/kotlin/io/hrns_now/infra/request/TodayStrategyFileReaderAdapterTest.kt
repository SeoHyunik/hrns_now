package io.hrns_now.infra.request

import io.hrns_now.core.domain.model.WorkspaceDay
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TodayStrategyFileReaderAdapterTest {

    private lateinit var tempRoot: Path
    private val date: LocalDate = LocalDate.of(2026, 7, 27)
    private val adapter = TodayStrategyFileReaderAdapter()

    @BeforeTest
    fun setUp() {
        tempRoot = Files.createTempDirectory("hrns-now-today-strategy-")
    }

    @AfterTest
    fun tearDown() {
        tempRoot.toFile().deleteRecursively()
    }

    private fun day(): WorkspaceDay = WorkspaceDay(tempRoot, date)

    @Test
    fun `파일이 없으면 null이다`() {
        assertNull(adapter.read(day()))
    }

    @Test
    fun `파일이 있으면 raw 텍스트를 그대로 읽는다`() {
        val path = tempRoot.resolve(date.toString()).resolve("TODAY_STRATEGY.md")
        Files.createDirectories(path.parent)
        Files.write(path, "# TODAY_STRATEGY\n\nExecution wrapper: code".toByteArray(Charsets.UTF_8))

        assertEquals("# TODAY_STRATEGY\n\nExecution wrapper: code", adapter.read(day()))
    }

    @Test
    fun `BOM이 있으면 제거하고 읽는다`() {
        val path = tempRoot.resolve(date.toString()).resolve("TODAY_STRATEGY.md")
        Files.createDirectories(path.parent)
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        Files.write(path, bom + "본문".toByteArray(Charsets.UTF_8))

        assertEquals("본문", adapter.read(day()))
    }
}
