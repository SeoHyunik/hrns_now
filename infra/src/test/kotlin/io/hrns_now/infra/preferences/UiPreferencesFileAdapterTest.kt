package io.hrns_now.infra.preferences

import io.hrns_now.core.domain.model.AppLocale
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeBytes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 실제 `%APPDATA%`를 건드리지 않고 임시 디렉터리에만 쓴다. */
class UiPreferencesFileAdapterTest {

    private val createdDirs = mutableListOf<Path>()

    private fun tempPreferencesPath(): Path {
        val dir = Files.createTempDirectory("hrns-now-ui-prefs")
        createdDirs.add(dir)
        return dir.resolve("ui-preferences.json")
    }

    @AfterTest
    fun cleanup() {
        createdDirs.forEach { it.toFile().deleteRecursively() }
    }

    @Test
    fun `파일이 없으면 null을 반환한다`() {
        val adapter = UiPreferencesFileAdapter(tempPreferencesPath())
        assertNull(adapter.readLocale())
    }

    @Test
    fun `저장한 locale을 그대로 다시 읽는다`() {
        val path = tempPreferencesPath()
        val adapter = UiPreferencesFileAdapter(path)

        adapter.writeLocale(AppLocale.English)

        assertEquals(AppLocale.English, adapter.readLocale())
    }

    @Test
    fun `한글 locale 값도 round-trip 된다`() {
        val path = tempPreferencesPath()
        val adapter = UiPreferencesFileAdapter(path)

        adapter.writeLocale(AppLocale.Korean)
        adapter.writeLocale(AppLocale.English)
        adapter.writeLocale(AppLocale.Korean)

        assertEquals(AppLocale.Korean, adapter.readLocale())
    }

    @Test
    fun `저장 파일은 UTF-8 BOM 없이 원자적으로 기록된다`() {
        val path = tempPreferencesPath()
        val adapter = UiPreferencesFileAdapter(path)

        adapter.writeLocale(AppLocale.Korean)

        assertTrue(Files.exists(path))
        val bytes = Files.readAllBytes(path)
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        assertFalse(bytes.size >= 3 && bytes.copyOfRange(0, 3).contentEquals(bom))
        val text = String(bytes, StandardCharsets.UTF_8)
        assertTrue(text.contains("\"ko\""))
        // 쓰기 도중 임시 파일이 남지 않아야 한다.
        val siblings = Files.list(path.parent).use { it.toList() }
        assertEquals(listOf(path), siblings)
    }

    @Test
    fun `손상된 JSON은 예외를 던지지 않고 null로 처리한다`() {
        val path = tempPreferencesPath()
        path.writeBytes("not-json{{{".toByteArray(StandardCharsets.UTF_8))

        val adapter = UiPreferencesFileAdapter(path)

        assertNull(adapter.readLocale())
    }

    @Test
    fun `알 수 없는 locale 코드는 null로 처리한다`() {
        val path = tempPreferencesPath()
        path.writeBytes("""{"locale":"fr"}""".toByteArray(StandardCharsets.UTF_8))

        val adapter = UiPreferencesFileAdapter(path)

        assertNull(adapter.readLocale())
    }
}
