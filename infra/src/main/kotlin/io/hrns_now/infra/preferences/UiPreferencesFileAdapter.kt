package io.hrns_now.infra.preferences

import io.hrns_now.core.domain.model.AppLocale
import io.hrns_now.core.port.UiPreferencesPort
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

@Serializable
private data class UiPreferencesFileDto(val locale: String? = null)

private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

/**
 * `%APPDATA%\hrns-now\ui-preferences.json`을 저장소로 쓰는 [UiPreferencesPort] 구현이다.
 * `WORKFLOW_STATE.json`/Harness workspace/project Registry와 완전히 분리된 파일이다.
 * UTF-8 without BOM, 같은 디렉터리 temp + atomic move를 쓴다. 손상되거나 해석할 수 없는 파일은
 * 조용히 `null`(=기본 locale)로 fail-closed하고 quarantine하지 않는다 — 다음 저장이 정본을
 * 자연스럽게 재작성하므로 Registry 수준의 복구 절차가 필요할 만큼 위험도가 높지 않다.
 */
class UiPreferencesFileAdapter(
    private val preferencesPath: Path,
    private val json: Json = Json { prettyPrint = true; ignoreUnknownKeys = true },
    private val moveIntoPlace: (Path, Path) -> Unit = ::moveIntoPlace,
) : UiPreferencesPort {

    override fun readLocale(): AppLocale? {
        if (!Files.exists(preferencesPath)) return null
        val bytes = try {
            Files.readAllBytes(preferencesPath)
        } catch (_: IOException) {
            return null
        } catch (_: SecurityException) {
            return null
        }
        val withoutBom = if (bytes.size >= UTF8_BOM.size && bytes.copyOfRange(0, UTF8_BOM.size).contentEquals(UTF8_BOM)) {
            bytes.copyOfRange(UTF8_BOM.size, bytes.size)
        } else {
            bytes
        }
        val text = String(withoutBom, StandardCharsets.UTF_8)
        val dto = try {
            json.decodeFromString(UiPreferencesFileDto.serializer(), text)
        } catch (_: SerializationException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
        return dto.locale?.let { code -> AppLocale.entries.firstOrNull { it.code == code } }
    }

    override fun writeLocale(locale: AppLocale) {
        val text = json.encodeToString(UiPreferencesFileDto.serializer(), UiPreferencesFileDto(locale.code))
        try {
            val dir = preferencesPath.toAbsolutePath().normalize().parent
            Files.createDirectories(dir)
            val temp = Files.createTempFile(dir, "ui-preferences", ".tmp")
            try {
                Files.newBufferedWriter(
                    temp,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE,
                ).use { writer ->
                    writer.write(text)
                    writer.flush()
                }
                moveIntoPlace(temp, preferencesPath)
            } finally {
                Files.deleteIfExists(temp)
            }
        } catch (_: IOException) {
            // locale 저장은 best-effort다 — 실패해도 다음 실행은 기본 locale로 fail-closed할 뿐 안전에 영향이 없다.
        } catch (_: SecurityException) {
            // 위와 동일한 이유로 무시한다.
        }
    }
}

private fun moveIntoPlace(source: Path, target: Path) {
    try {
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
    }
}
