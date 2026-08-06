package io.hrns_now.infra.serialization

import io.hrns_now.core.domain.model.FileVersion
import io.hrns_now.core.domain.model.SUPPORTED_SCHEMA_MAJOR
import io.hrns_now.core.domain.model.SchemaVersion
import io.hrns_now.core.domain.model.WorkflowState
import io.hrns_now.core.domain.model.WorkspaceDay
import io.hrns_now.core.domain.policy.StateReadRetryPolicy
import io.hrns_now.core.port.WorkflowStatePort
import io.hrns_now.core.result.StateReadResult
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

internal data class FsSnapshot(val modifiedAt: FileTime, val size: Long)

/**
 * NIO 호출을 Adapter orchestration에서 분리하는 infra 내부 협력자다.
 *
 * metadata 변경과 접근 거부를 시간/ACL에 의존하지 않고 contract test로 재현하기 위한
 * 테스트 경계이기도 하며, core port나 domain에는 노출하지 않는다.
 */
internal interface WorkflowStateFileAccess {
    fun snapshot(path: Path): FsSnapshot
    fun readAllBytes(path: Path): ByteArray
}

private object NioWorkflowStateFileAccess : WorkflowStateFileAccess {
    override fun snapshot(path: Path): FsSnapshot =
        FsSnapshot(
            modifiedAt = Files.getLastModifiedTime(path),
            size = Files.size(path),
        )

    override fun readAllBytes(path: Path): ByteArray = Files.readAllBytes(path)
}

private sealed interface SnapshotOutcome {
    data class Found(val snapshot: FsSnapshot) : SnapshotOutcome
    data object Missing : SnapshotOutcome
    data object AccessDenied : SnapshotOutcome
    data class RetryableFailure(val message: String) : SnapshotOutcome
}

private sealed interface AttemptOutcome {
    data class Done(val result: StateReadResult) : AttemptOutcome
    data class Retryable(val onExhausted: StateReadResult) : AttemptOutcome
}

/**
 * `WORKFLOW_STATE.json`을 읽어 [WorkflowState]로 변환하는 [WorkflowStatePort] 구현이다.
 *
 * 절대 이 파일에 쓰지 않는다. 읽기 전/후 파일 metadata(mtime, size)를 비교해 partial write를
 * 감지하고, JSON 구문 오류·도메인 필수 필드 누락·UTF-8 디코딩 오류를 모두 재시도 대상으로
 * 취급한다. 재시도가 모두 소진되면 마지막으로 성공했던 [WorkflowState](last-known-good)를
 * 함께 반환한다.
 */
class JsonWorkflowStateAdapter private constructor(
    private val retryPolicy: StateReadRetryPolicy,
    private val sleep: (Long) -> Unit,
    private val fileAccess: WorkflowStateFileAccess,
) : WorkflowStatePort {

    constructor(
        retryPolicy: StateReadRetryPolicy = StateReadRetryPolicy(),
        sleep: (Long) -> Unit = { millis -> if (millis > 0) Thread.sleep(millis) },
    ) : this(retryPolicy, sleep, NioWorkflowStateFileAccess)

    internal constructor(
        fileAccess: WorkflowStateFileAccess,
        retryPolicy: StateReadRetryPolicy,
        sleep: (Long) -> Unit,
    ) : this(retryPolicy, sleep, fileAccess)

    // Parser/Mapper는 internal DTO(HarnessWorkflowStateDto)를 주고받으므로 public 생성자
    // 파라미터로 노출하지 않는다 — infra 모듈 내부 테스트에서는 internal 가시성으로 직접
    // 단위 테스트할 수 있다(Kotlin의 main/test friend 컴파일).
    private val parser: WorkflowStateParser = WorkflowStateParser()
    private val mapper: WorkflowStateMapper = WorkflowStateMapper()

    private val lastKnownGood = ConcurrentHashMap<Path, WorkflowState>()

    override fun read(day: WorkspaceDay): StateReadResult {
        val path = day.dayRoot.resolve("WORKFLOW_STATE.json").toAbsolutePath().normalize()

        var attempt = 1
        while (true) {
            when (val outcome = attemptRead(path)) {
                is AttemptOutcome.Done -> return outcome.result
                is AttemptOutcome.Retryable -> {
                    val decision = retryPolicy.decide(attempt)
                    if (!decision.shouldRetry) return outcome.onExhausted
                    sleep(decision.delayMillis)
                    attempt++
                }
            }
        }
    }

    private fun attemptRead(path: Path): AttemptOutcome {
        val before = when (val snapshot = captureSnapshot(path)) {
            is SnapshotOutcome.Found -> snapshot.snapshot
            SnapshotOutcome.Missing -> return AttemptOutcome.Done(StateReadResult.Missing(path))
            SnapshotOutcome.AccessDenied -> return AttemptOutcome.Done(StateReadResult.AccessDenied(path))
            is SnapshotOutcome.RetryableFailure ->
                return AttemptOutcome.Retryable(
                    StateReadResult.Malformed(snapshot.message, lastKnownGood[path]),
                )
        }

        val bytes = try {
            fileAccess.readAllBytes(path)
        } catch (exception: NoSuchFileException) {
            return AttemptOutcome.Done(StateReadResult.Missing(path))
        } catch (exception: AccessDeniedException) {
            return AttemptOutcome.Done(StateReadResult.AccessDenied(path))
        } catch (exception: SecurityException) {
            return AttemptOutcome.Done(StateReadResult.AccessDenied(path))
        } catch (exception: IOException) {
            return AttemptOutcome.Retryable(
                StateReadResult.Malformed(exception.message ?: "IO error while reading file", lastKnownGood[path]),
            )
        }

        val after = when (val snapshot = captureSnapshot(path)) {
            is SnapshotOutcome.Found -> snapshot.snapshot
            SnapshotOutcome.Missing -> return AttemptOutcome.Done(StateReadResult.Missing(path))
            SnapshotOutcome.AccessDenied -> return AttemptOutcome.Done(StateReadResult.AccessDenied(path))
            is SnapshotOutcome.RetryableFailure ->
                return AttemptOutcome.Retryable(
                    StateReadResult.Malformed(snapshot.message, lastKnownGood[path]),
                )
        }
        if (before != after || after.size != bytes.size.toLong()) {
            return AttemptOutcome.Retryable(
                StateReadResult.Malformed("file changed while being read", lastKnownGood[path]),
            )
        }

        val text = when (val decoded = decodeUtf8Strict(bytes)) {
            is DecodeResult.InvalidEncoding ->
                return AttemptOutcome.Retryable(
                    StateReadResult.EncodingError(decoded.message, lastKnownGood[path]),
                )
            is DecodeResult.Text -> decoded.text
        }

        val dto = when (val parsed = parser.parse(text)) {
            is ParseResult.Failure ->
                return AttemptOutcome.Retryable(
                    StateReadResult.Malformed(parsed.message, lastKnownGood[path]),
                )
            is ParseResult.Success -> parsed.dto
        }

        val schemaVersionRaw = dto.schemaVersion
            ?: return AttemptOutcome.Retryable(
                StateReadResult.Malformed("schema_version is missing", lastKnownGood[path]),
            )
        val schemaVersion = SchemaVersion.parse(schemaVersionRaw)
            ?: return AttemptOutcome.Retryable(
                StateReadResult.Malformed("schema_version has invalid format: $schemaVersionRaw", lastKnownGood[path]),
            )
        if (schemaVersion.major != SUPPORTED_SCHEMA_MAJOR) {
            // 미지원 major는 partial write 증상이 아니라 확정적 비호환이므로 재시도하지 않는다.
            return AttemptOutcome.Done(StateReadResult.UnsupportedSchema(schemaVersion.raw))
        }

        val state = when (val mapped = mapper.map(dto)) {
            is MapResult.Failure ->
                return AttemptOutcome.Retryable(
                    StateReadResult.Malformed(mapped.message, lastKnownGood[path]),
                )
            is MapResult.Success -> mapped.state
        }

        lastKnownGood[path] = state
        val version = FileVersion(
            modifiedAt = after.modifiedAt.toInstant(),
            size = after.size,
            hash = sha256Hex(bytes),
        )
        return AttemptOutcome.Done(StateReadResult.Success(state, version))
    }

    private fun captureSnapshot(path: Path): SnapshotOutcome =
        try {
            SnapshotOutcome.Found(fileAccess.snapshot(path))
        } catch (exception: NoSuchFileException) {
            SnapshotOutcome.Missing
        } catch (exception: AccessDeniedException) {
            SnapshotOutcome.AccessDenied
        } catch (exception: SecurityException) {
            SnapshotOutcome.AccessDenied
        } catch (exception: IOException) {
            SnapshotOutcome.RetryableFailure(exception.message ?: "IO error while reading file metadata")
        }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}

private sealed interface DecodeResult {
    data class Text(val text: String) : DecodeResult
    data class InvalidEncoding(val message: String) : DecodeResult
}

private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

private fun decodeUtf8Strict(bytes: ByteArray): DecodeResult {
    val withoutBom = if (bytes.size >= UTF8_BOM.size && bytes.copyOfRange(0, UTF8_BOM.size).contentEquals(UTF8_BOM)) {
        bytes.copyOfRange(UTF8_BOM.size, bytes.size)
    } else {
        bytes
    }
    val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    return try {
        DecodeResult.Text(decoder.decode(ByteBuffer.wrap(withoutBom)).toString())
    } catch (exception: CharacterCodingException) {
        DecodeResult.InvalidEncoding(exception.message ?: "invalid UTF-8 byte sequence")
    }
}
