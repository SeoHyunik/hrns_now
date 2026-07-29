package io.hrns_now.infra.lock

import io.hrns_now.core.domain.model.HarnessCommandKind
import io.hrns_now.core.domain.model.LockAcquireResult
import io.hrns_now.core.domain.model.LockHandle
import io.hrns_now.core.domain.model.LockPayload
import io.hrns_now.core.domain.model.LockReleaseResult
import io.hrns_now.core.domain.model.LockState
import io.hrns_now.core.domain.model.ProjectId
import io.hrns_now.core.domain.policy.LockStalePolicy
import io.hrns_now.core.port.LockInspection
import io.hrns_now.core.port.ProcessLockPort
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Serializable
internal data class LockPayloadDto(
    @SerialName("project_id") val projectId: String,
    val date: String,
    @SerialName("owner_pid") val ownerPid: Long,
    @SerialName("owner_kind") val ownerKind: String = "hrns-now",
    @SerialName("started_at") val startedAt: String,
    @SerialName("heartbeat_at") val heartbeatAt: String,
    val command: String,
)

internal fun LockPayload.toDto(): LockPayloadDto = LockPayloadDto(
    projectId = projectId.value,
    date = date.toString(),
    ownerPid = pid,
    startedAt = acquiredAt.toString(),
    heartbeatAt = heartbeatAt.toString(),
    command = commandKind.name,
)

internal fun LockPayloadDto.toDomain(): LockPayload? {
    if (ownerKind != "hrns-now") return null
    val kind = try {
        HarnessCommandKind.valueOf(command)
    } catch (_: IllegalArgumentException) {
        return null
    }
    return try {
        LockPayload(
            projectId = ProjectId(projectId),
            date = LocalDate.parse(date),
            pid = ownerPid,
            commandKind = kind,
            acquiredAt = Instant.parse(startedAt),
            heartbeatAt = Instant.parse(heartbeatAt),
        )
    } catch (_: java.time.format.DateTimeParseException) {
        null
    }
}

/**
 * `%LOCALAPPDATA%\hrns-now\locks\<projectId>\<yyyy-MM-dd>.lock.json`에 UI 소유 lock을 두는
 * [ProcessLockPort] 구현이다(`doc/claude_prompts/phase3-process-adapter-lock.md` §4).
 * Harness workspace에는 어떤 파일도 만들지 않는다.
 *
 * 테스트를 위해 root/clock/PID lookup을 모두 주입 가능하게 한다. `pidAlive`가 `null`을
 * 반환하면(생존 여부 불명) [LockStalePolicy]가 fail-closed로 Active 취급한다.
 */
class LocalProcessLockAdapter(
    private val locksRoot: Path,
    private val clock: () -> Instant = Instant::now,
    private val currentPid: () -> Long = { ProcessHandle.current().pid() },
    private val pidAlive: (Long) -> Boolean? = ::defaultPidAlive,
    private val stalePolicy: LockStalePolicy = LockStalePolicy(),
    private val json: Json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true },
) : ProcessLockPort {

    override suspend fun acquire(
        projectId: ProjectId,
        date: LocalDate,
        commandKind: HarnessCommandKind,
    ): LockAcquireResult {
        val path = lockPath(projectId, date)
        return try {
            Files.createDirectories(path.parent)

            tryCreateNew(path, projectId, date, commandKind)?.let { return it }

            val existing = readPayload(path)
                ?: return LockAcquireResult.Failed("잠금 파일을 해석할 수 없습니다.")
            val state = stalePolicy.evaluate(existing, clock(), pidAlive(existing.pid))
            if (state == LockState.Active) {
                return LockAcquireResult.Busy(existing)
            }

            Files.deleteIfExists(path)
            tryCreateNew(path, projectId, date, commandKind)?.let { return it }

            val raced = readPayload(path)
            if (raced != null) LockAcquireResult.Busy(raced) else LockAcquireResult.Failed("잠금 획득 경쟁에서 실패했습니다.")
        } catch (e: IOException) {
            LockAcquireResult.Failed(e.message ?: "lock acquire failed")
        } catch (e: SecurityException) {
            LockAcquireResult.Failed(e.message ?: "lock acquire denied")
        }
    }

    override suspend fun heartbeat(handle: LockHandle): Boolean {
        val path = lockPath(handle.projectId, handle.date)
        return try {
            val existing = readPayload(path) ?: return false
            if (!matches(existing, handle)) return false
            writeAtomic(path, existing.copy(heartbeatAt = clock()))
            true
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    override suspend fun release(handle: LockHandle): LockReleaseResult {
        val path = lockPath(handle.projectId, handle.date)
        return try {
            val existing = readPayload(path)
            if (existing == null || !matches(existing, handle)) {
                return LockReleaseResult.Released
            }
            Files.deleteIfExists(path)
            LockReleaseResult.Released
        } catch (e: IOException) {
            LockReleaseResult.Failed(e.message ?: "release failed")
        } catch (e: SecurityException) {
            LockReleaseResult.Failed(e.message ?: "release denied")
        }
    }

    override suspend fun inspect(projectId: ProjectId, date: LocalDate): LockInspection? {
        val payload = readPayload(lockPath(projectId, date)) ?: return null
        val state = stalePolicy.evaluate(payload, clock(), pidAlive(payload.pid))
        return LockInspection(payload, state)
    }

    override suspend fun forceRelease(projectId: ProjectId, date: LocalDate): LockReleaseResult =
        try {
            Files.deleteIfExists(lockPath(projectId, date))
            LockReleaseResult.Released
        } catch (e: IOException) {
            LockReleaseResult.Failed(e.message ?: "force release failed")
        } catch (e: SecurityException) {
            LockReleaseResult.Failed(e.message ?: "force release denied")
        }

    private fun tryCreateNew(
        path: Path,
        projectId: ProjectId,
        date: LocalDate,
        commandKind: HarnessCommandKind,
    ): LockAcquireResult.Acquired? {
        val now = clock()
        val pid = currentPid()
        val payload = LockPayload(projectId, date, pid, commandKind, now, now)
        val bytes = json.encodeToString(LockPayloadDto.serializer(), payload.toDto()).toByteArray(StandardCharsets.UTF_8)
        val parent = path.parent
        Files.createDirectories(parent)
        val candidate = Files.createTempFile(parent, "lock", ".tmp")
        return try {
            Files.newByteChannel(candidate, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE).use { channel ->
                channel.write(java.nio.ByteBuffer.wrap(bytes))
            }
            Files.move(candidate, path)
            LockAcquireResult.Acquired(LockHandle(projectId, date, pid, now))
        } catch (_: FileAlreadyExistsException) {
            null
        } finally {
            Files.deleteIfExists(candidate)
        }
    }

    private fun writeAtomic(path: Path, payload: LockPayload) {
        val dir = path.parent
        Files.createDirectories(dir)
        val temp = Files.createTempFile(dir, "lock", ".tmp")
        try {
            Files.newBufferedWriter(
                temp,
                StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            ).use { writer ->
                writer.write(json.encodeToString(LockPayloadDto.serializer(), payload.toDto()))
            }
            try {
                Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    private fun readPayload(path: Path): LockPayload? {
        if (!Files.exists(path)) return null
        return try {
            val text = Files.readString(path, StandardCharsets.UTF_8)
            json.decodeFromString(LockPayloadDto.serializer(), text).toDomain()
        } catch (_: IOException) {
            null
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun matches(payload: LockPayload, handle: LockHandle): Boolean =
        payload.projectId == handle.projectId &&
            payload.date == handle.date &&
            payload.pid == handle.pid &&
            payload.acquiredAt == handle.acquiredAt

    private fun lockPath(projectId: ProjectId, date: LocalDate): Path =
        locksRoot.resolve(projectId.value).resolve("$date.lock.json")
}

private fun defaultPidAlive(pid: Long): Boolean? =
    try {
        val handle = ProcessHandle.of(pid)
        if (handle.isPresent) handle.get().isAlive else false
    } catch (_: SecurityException) {
        null
    }
