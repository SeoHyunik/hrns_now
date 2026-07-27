package io.hrns_now.infra.kitversion

import io.hrns_now.core.domain.model.ContractVersion
import io.hrns_now.core.domain.model.KitVersion
import io.hrns_now.core.domain.model.KitVersionReadResult
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class JsonKitVersionManifestAdapterTest {

    private fun tempKitRoot(): Path = Files.createTempDirectory("hrns-kit-version-test")

    private fun writeManifest(kitRoot: Path, text: String, bom: Boolean = false) {
        val bytes = if (bom) {
            byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + text.toByteArray(StandardCharsets.UTF_8)
        } else {
            text.toByteArray(StandardCharsets.UTF_8)
        }
        Files.write(kitRoot.resolve("kit-version.json"), bytes)
    }

    @Test
    fun `정상 manifest는 round-trip으로 typed 값을 만든다`() {
        val kitRoot = tempKitRoot()
        writeManifest(
            kitRoot,
            """{"kit_version": "2026.07.23", "state_schema_version": "1.0", "ui_contract_version": "1.0"}""",
        )

        val result = JsonKitVersionManifestAdapter().readManifest(kitRoot)

        val success = assertIs<KitVersionReadResult.Success>(result)
        assertEquals(KitVersion("2026.07.23"), success.manifest.kitVersion)
        assertEquals(ContractVersion(1, 0, "1.0"), success.manifest.stateSchemaVersion)
        assertEquals(ContractVersion(1, 0, "1.0"), success.manifest.uiContractVersion)
    }

    @Test
    fun `알 수 없는 필드는 관대하게 허용한다`() {
        val kitRoot = tempKitRoot()
        writeManifest(
            kitRoot,
            """
            {
              "kit_version": "2026.07.23",
              "state_schema_version": "1.0",
              "ui_contract_version": "1.0",
              "future_field": {"nested": true},
              "min_ui_version": "9.9"
            }
            """.trimIndent(),
        )

        val result = JsonKitVersionManifestAdapter().readManifest(kitRoot)

        assertIs<KitVersionReadResult.Success>(result)
    }

    @Test
    fun `UTF-8 BOM이 있어도 정상 읽는다`() {
        val kitRoot = tempKitRoot()
        writeManifest(
            kitRoot,
            """{"kit_version": "2026.07.23", "state_schema_version": "1.0", "ui_contract_version": "1.0"}""",
            bom = true,
        )

        val result = JsonKitVersionManifestAdapter().readManifest(kitRoot)

        assertIs<KitVersionReadResult.Success>(result)
    }

    @Test
    fun `kit-version json 파일이 없으면 Missing이다`() {
        val kitRoot = tempKitRoot()

        val result = JsonKitVersionManifestAdapter().readManifest(kitRoot)

        assertEquals(KitVersionReadResult.Missing, result)
    }

    @Test
    fun `깨진 JSON은 Malformed다`() {
        val kitRoot = tempKitRoot()
        writeManifest(kitRoot, """{"kit_version": "2026.07.23", """)

        val result = JsonKitVersionManifestAdapter().readManifest(kitRoot)

        assertIs<KitVersionReadResult.Malformed>(result)
    }

    @Test
    fun `kit_version field 누락은 Malformed다`() {
        val kitRoot = tempKitRoot()
        writeManifest(kitRoot, """{"state_schema_version": "1.0", "ui_contract_version": "1.0"}""")

        val result = JsonKitVersionManifestAdapter().readManifest(kitRoot)

        val malformed = assertIs<KitVersionReadResult.Malformed>(result)
        assertEquals("missing_field:kit_version", malformed.reason)
    }

    @Test
    fun `state_schema_version field 누락은 Malformed다`() {
        val kitRoot = tempKitRoot()
        writeManifest(kitRoot, """{"kit_version": "2026.07.23", "ui_contract_version": "1.0"}""")

        val result = JsonKitVersionManifestAdapter().readManifest(kitRoot)

        val malformed = assertIs<KitVersionReadResult.Malformed>(result)
        assertEquals("missing_field:state_schema_version", malformed.reason)
    }

    @Test
    fun `ui_contract_version field 누락은 Malformed다`() {
        val kitRoot = tempKitRoot()
        writeManifest(kitRoot, """{"kit_version": "2026.07.23", "state_schema_version": "1.0"}""")

        val result = JsonKitVersionManifestAdapter().readManifest(kitRoot)

        val malformed = assertIs<KitVersionReadResult.Malformed>(result)
        assertEquals("missing_field:ui_contract_version", malformed.reason)
    }

    @Test
    fun `빈 문자열 필드도 누락과 동일하게 Malformed다`() {
        val kitRoot = tempKitRoot()
        writeManifest(
            kitRoot,
            """{"kit_version": "   ", "state_schema_version": "1.0", "ui_contract_version": "1.0"}""",
        )

        val result = JsonKitVersionManifestAdapter().readManifest(kitRoot)

        val malformed = assertIs<KitVersionReadResult.Malformed>(result)
        assertEquals("missing_field:kit_version", malformed.reason)
    }

    @Test
    fun `버전 형식이 major minor가 아니면 Malformed다`() {
        val kitRoot = tempKitRoot()
        writeManifest(
            kitRoot,
            """{"kit_version": "2026.07.23", "state_schema_version": "one-point-oh", "ui_contract_version": "1.0"}""",
        )

        val result = JsonKitVersionManifestAdapter().readManifest(kitRoot)

        val malformed = assertIs<KitVersionReadResult.Malformed>(result)
        assertEquals("invalid_format:state_schema_version", malformed.reason)
    }

    @Test
    fun `잘못된 인코딩 바이트는 Malformed이며 원본 바이트를 노출하지 않는다`() {
        val kitRoot = tempKitRoot()
        val invalidUtf8 = byteArrayOf(0x7B, 0xFF.toByte(), 0xFE.toByte(), 0x7D)
        Files.write(kitRoot.resolve("kit-version.json"), invalidUtf8)

        val result = JsonKitVersionManifestAdapter().readManifest(kitRoot)

        val malformed = assertIs<KitVersionReadResult.Malformed>(result)
        assertEquals("invalid_encoding", malformed.reason)
    }
}
