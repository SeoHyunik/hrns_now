package io.hrns_now.infra.request

import io.hrns_now.core.domain.model.FileVersion
import io.hrns_now.core.domain.model.WorkspaceDay
import io.hrns_now.core.port.LoadedRequest
import io.hrns_now.core.port.RequestSaveResult
import io.hrns_now.core.port.RequestWriterPort
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * `REQUEST_INBOX.md` 전용 [RequestWriterPort] 구현이다. `REQUEST_STRUCTURED.md`나
 * `WORKFLOW_STATE.json`은 참조하지 않는다.
 *
 * 저장은 같은 디렉터리에 임시 파일을 UTF-8 no BOM으로 쓴 뒤 `ATOMIC_MOVE`로 교체한다 —
 * 임시 파일이 대상과 같은 디렉터리(같은 볼륨)에 있어야 원자적 이동이 보장된다.
 */
class RequestInboxWriterAdapter : RequestWriterPort {

    override fun load(day: WorkspaceDay): LoadedRequest? {
        val path = requestInboxPath(day)
        return try {
            val bytes = Files.readAllBytes(path)
            val modifiedAt = Files.getLastModifiedTime(path)
            val content = decodeUtf8NoBom(bytes) ?: return null
            LoadedRequest(content = content, version = versionOf(bytes, modifiedAt.toInstant()))
        } catch (_: NoSuchFileException) {
            null
        } catch (_: AccessDeniedException) {
            null
        } catch (_: SecurityException) {
            null
        } catch (_: IOException) {
            null
        }
    }

    override fun save(day: WorkspaceDay, content: String, expectedVersion: FileVersion): RequestSaveResult {
        val path = requestInboxPath(day)
        val currentVersion = try {
            versionOf(Files.readAllBytes(path), Files.getLastModifiedTime(path).toInstant())
        } catch (exception: IOException) {
            return RequestSaveResult.Failed(exception.message ?: "저장 직전 파일 상태를 확인하지 못했습니다.")
        } catch (exception: SecurityException) {
            return RequestSaveResult.Failed(exception.message ?: "저장 직전 파일 상태를 확인하지 못했습니다.")
        }
        if (currentVersion != expectedVersion) {
            return RequestSaveResult.Conflict(currentVersion)
        }

        return try {
            val bytes = stripBomIfPresent(content.toByteArray(Charsets.UTF_8))
            val tempFile = Files.createTempFile(path.parent, "REQUEST_INBOX", ".tmp")
            try {
                Files.write(tempFile, bytes)
                Files.move(
                    tempFile,
                    path,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } finally {
                Files.deleteIfExists(tempFile)
            }
            RequestSaveResult.Saved
        } catch (exception: IOException) {
            RequestSaveResult.Failed(exception.message ?: "파일을 저장하지 못했습니다.")
        } catch (exception: SecurityException) {
            RequestSaveResult.Failed(exception.message ?: "파일을 저장하지 못했습니다.")
        }
    }

    private fun requestInboxPath(day: WorkspaceDay): Path =
        day.dayRoot.resolve("REQUEST_INBOX.md").toAbsolutePath().normalize()

    private fun versionOf(bytes: ByteArray, modifiedAt: java.time.Instant): FileVersion =
        FileVersion(modifiedAt = modifiedAt, size = bytes.size.toLong(), hash = sha256Hex(bytes))

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}

internal val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

internal fun stripBomIfPresent(bytes: ByteArray): ByteArray =
    if (bytes.size >= UTF8_BOM.size && bytes.copyOfRange(0, UTF8_BOM.size).contentEquals(UTF8_BOM)) {
        bytes.copyOfRange(UTF8_BOM.size, bytes.size)
    } else {
        bytes
    }

internal fun decodeUtf8NoBom(bytes: ByteArray): String? {
    val withoutBom = stripBomIfPresent(bytes)
    val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    return try {
        decoder.decode(ByteBuffer.wrap(withoutBom)).toString()
    } catch (_: CharacterCodingException) {
        null
    }
}
