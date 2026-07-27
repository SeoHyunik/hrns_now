package io.hrns_now.infra.kitversion

import io.hrns_now.core.domain.model.ContractVersion
import io.hrns_now.core.domain.model.KitVersion
import io.hrns_now.core.domain.model.KitVersionManifest
import io.hrns_now.core.domain.model.KitVersionReadResult
import io.hrns_now.core.port.KitVersionManifestPort
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * `<kitRoot>/kit-version.json`의 외부 JSON 모양을 있는 그대로 표현하는 DTO다
 * (Anti-Corruption Layer, `doc/hrns_now_design_pattern.md` §4와 동일한 원칙). 모든 필드는
 * nullable + 기본값 `null`이다 — 필수 여부 판단은 어댑터가 명시적으로 수행한다.
 */
@Serializable
internal data class KitVersionFileDto(
    @SerialName("kit_version") val kitVersion: String? = null,
    @SerialName("state_schema_version") val stateSchemaVersion: String? = null,
    @SerialName("ui_contract_version") val uiContractVersion: String? = null,
)

private const val MANIFEST_FILE_NAME = "kit-version.json"

/**
 * Kit root의 `kit-version.json`을 읽는 [KitVersionManifestPort] 구현이다
 * (`doc/claude_prompts/phase2-harness-json-contract.md` §B.2).
 *
 * UTF-8 BOM은 허용하되 그 외 잘못된 인코딩은 malformed로 구분한다. `ignoreUnknownKeys=true`로
 * 향후 필드 확장을 허용하되, 세 필수 field 중 하나라도 없거나 형식이 잘못되면 fail-closed로
 * [KitVersionReadResult.Malformed]를 반환한다. `Malformed.reason`은 항상 이 어댑터가 만든
 * 안전한 분류 문자열이며, 원본 예외 메시지나 JSON 원문을 담지 않는다.
 */
class JsonKitVersionManifestAdapter(
    private val json: Json = Json { ignoreUnknownKeys = true },
) : KitVersionManifestPort {

    override fun readManifest(kitRoot: Path): KitVersionReadResult {
        val manifestPath = kitRoot.resolve(MANIFEST_FILE_NAME)
        if (!Files.isRegularFile(manifestPath)) {
            return KitVersionReadResult.Missing
        }

        val bytes = try {
            Files.readAllBytes(manifestPath)
        } catch (exception: IOException) {
            return KitVersionReadResult.Malformed("io_error")
        } catch (exception: SecurityException) {
            return KitVersionReadResult.Malformed("access_denied")
        }

        val text = when (val decoded = decodeUtf8AllowingBom(bytes)) {
            is DecodeResult.Text -> decoded.text
            DecodeResult.InvalidEncoding -> return KitVersionReadResult.Malformed("invalid_encoding")
        }

        val dto = try {
            json.decodeFromString(KitVersionFileDto.serializer(), text)
        } catch (exception: SerializationException) {
            return KitVersionReadResult.Malformed("invalid_json")
        } catch (exception: IllegalArgumentException) {
            return KitVersionReadResult.Malformed("invalid_json")
        }

        val kitVersionRaw = dto.kitVersion?.trim()?.takeIf(String::isNotEmpty)
            ?: return KitVersionReadResult.Malformed("missing_field:kit_version")
        val stateSchemaRaw = dto.stateSchemaVersion?.trim()?.takeIf(String::isNotEmpty)
            ?: return KitVersionReadResult.Malformed("missing_field:state_schema_version")
        val uiContractRaw = dto.uiContractVersion?.trim()?.takeIf(String::isNotEmpty)
            ?: return KitVersionReadResult.Malformed("missing_field:ui_contract_version")

        val stateSchemaVersion = ContractVersion.parse(stateSchemaRaw)
            ?: return KitVersionReadResult.Malformed("invalid_format:state_schema_version")
        val uiContractVersion = ContractVersion.parse(uiContractRaw)
            ?: return KitVersionReadResult.Malformed("invalid_format:ui_contract_version")

        return KitVersionReadResult.Success(
            KitVersionManifest(
                kitVersion = KitVersion(kitVersionRaw),
                stateSchemaVersion = stateSchemaVersion,
                uiContractVersion = uiContractVersion,
            ),
        )
    }
}

private sealed interface DecodeResult {
    data class Text(val text: String) : DecodeResult
    data object InvalidEncoding : DecodeResult
}

private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

private fun decodeUtf8AllowingBom(bytes: ByteArray): DecodeResult {
    val withoutBom =
        if (bytes.size >= UTF8_BOM.size && bytes.copyOfRange(0, UTF8_BOM.size).contentEquals(UTF8_BOM)) {
            bytes.copyOfRange(UTF8_BOM.size, bytes.size)
        } else {
            bytes
        }
    val decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    return try {
        DecodeResult.Text(decoder.decode(ByteBuffer.wrap(withoutBom)).toString())
    } catch (exception: CharacterCodingException) {
        DecodeResult.InvalidEncoding
    }
}
