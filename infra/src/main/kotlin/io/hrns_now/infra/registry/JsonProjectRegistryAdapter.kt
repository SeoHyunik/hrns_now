package io.hrns_now.infra.registry

import io.hrns_now.core.domain.model.HarnessProject
import io.hrns_now.core.domain.model.ProjectId
import io.hrns_now.core.domain.model.RuntimeSource
import io.hrns_now.core.port.ProjectRegistryPort
import io.hrns_now.core.result.RegistryLoadResult
import io.hrns_now.core.result.RegistrySaveResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Instant

private sealed interface LoadOutcome {
    data class Loaded(
        val projects: List<HarnessProject>,
        val lastActiveProjectId: ProjectId?,
        val droppedEntryCount: Int,
    ) : LoadOutcome

    data object Empty : LoadOutcome
    data class Corrupt(val message: String) : LoadOutcome
    data class Unreadable(val message: String) : LoadOutcome
}

private sealed interface MutationSnapshotResult {
    data class Ready(
        val projects: List<HarnessProject>,
        val lastActiveProjectId: ProjectId?,
    ) : MutationSnapshotResult

    data class Failed(val message: String) : MutationSnapshotResult
}

/**
 * `%APPDATA%\hrns-now\projects.json`을 저장소로 쓰는 [ProjectRegistryPort] 구현이다.
 *
 * UTF-8 without BOM, 같은 디렉터리 temp + atomic move, 손상 원본 quarantine, 프로세스 내
 * read-modify-write 직렬화를 보장한다. 손상 entry를 제거하는 mutation 전에도 먼저 원본 bytes를
 * quarantine하므로 정상 entry만 남기는 과정에서 진단 원본을 잃지 않는다.
 */
class JsonProjectRegistryAdapter(
    private val registryPath: Path,
    private val json: Json = Json { prettyPrint = true; ignoreUnknownKeys = true },
    private val clock: () -> Instant = Instant::now,
    private val moveIntoPlace: (Path, Path) -> Unit = ::moveRegistryFile,
) : ProjectRegistryPort {

    private val mutex = Mutex()

    override suspend fun findAll(): RegistryLoadResult = mutex.withLock { findAllLocked() }

    override suspend fun findById(id: ProjectId): HarnessProject? =
        mutex.withLock {
            when (val loaded = findAllLocked()) {
                is RegistryLoadResult.Success -> loaded.projects.firstOrNull { it.id == id }
                is RegistryLoadResult.RecoveredFromCorruption -> loaded.projects.firstOrNull { it.id == id }
                is RegistryLoadResult.Unreadable -> null
            }
        }

    override suspend fun save(project: HarnessProject): RegistrySaveResult =
        mutex.withLock {
            if (isRegistryInsideProject(project)) {
                return@withLock RegistrySaveResult.Failed(
                    "Registry 경로는 Kit, Workspace, Repository root 밖에 있어야 합니다.",
                )
            }
            when (val snapshot = prepareMutationSnapshot()) {
                is MutationSnapshotResult.Failed -> RegistrySaveResult.Failed(snapshot.message)
                is MutationSnapshotResult.Ready -> {
                    if (snapshot.projects.any(::isRegistryInsideProject)) {
                        RegistrySaveResult.Failed(
                            "Registry 경로가 기존 프로젝트 root 아래에 있어 변경할 수 없습니다.",
                        )
                    } else {
                        val updated = snapshot.projects.filterNot { it.id == project.id } + project
                        writeEnvelope(updated, snapshot.lastActiveProjectId)
                    }
                }
            }
        }

    override suspend fun delete(id: ProjectId): RegistrySaveResult =
        mutex.withLock {
            when (val snapshot = prepareMutationSnapshot()) {
                is MutationSnapshotResult.Failed -> RegistrySaveResult.Failed(snapshot.message)
                is MutationSnapshotResult.Ready -> {
                    if (snapshot.projects.any(::isRegistryInsideProject)) {
                        RegistrySaveResult.Failed(
                            "Registry 경로가 기존 프로젝트 root 아래에 있어 변경할 수 없습니다.",
                        )
                    } else {
                        val updated = snapshot.projects.filterNot { it.id == id }
                        val newActiveId = if (snapshot.lastActiveProjectId == id) null else snapshot.lastActiveProjectId
                        writeEnvelope(updated, newActiveId)
                    }
                }
            }
        }

    override suspend fun markActive(id: ProjectId): RegistrySaveResult =
        mutex.withLock {
            when (val snapshot = prepareMutationSnapshot()) {
                is MutationSnapshotResult.Failed -> RegistrySaveResult.Failed(snapshot.message)
                is MutationSnapshotResult.Ready -> {
                    if (snapshot.projects.any(::isRegistryInsideProject)) {
                        return@withLock RegistrySaveResult.Failed(
                            "Registry 경로가 기존 프로젝트 root 아래에 있어 변경할 수 없습니다.",
                        )
                    }
                    if (snapshot.projects.none { it.id == id }) {
                        RegistrySaveResult.Failed("프로젝트를 찾을 수 없습니다: ${id.value}")
                    } else {
                        writeEnvelope(snapshot.projects, id)
                    }
                }
            }
        }

    /**
     * "마지막으로 활성화된 프로젝트" 선택만 지운다 — project entry 목록은
     * [markActive]/[save]와 동일한 atomic write 경로로 그대로 다시 쓴다.
     */
    override suspend fun clearActive(): RegistrySaveResult =
        mutex.withLock {
            when (val snapshot = prepareMutationSnapshot()) {
                is MutationSnapshotResult.Failed -> RegistrySaveResult.Failed(snapshot.message)
                is MutationSnapshotResult.Ready -> {
                    if (snapshot.projects.any(::isRegistryInsideProject)) {
                        RegistrySaveResult.Failed(
                            "Registry 경로가 기존 프로젝트 root 아래에 있어 변경할 수 없습니다.",
                        )
                    } else {
                        writeEnvelope(snapshot.projects, null)
                    }
                }
            }
        }

    private fun findAllLocked(): RegistryLoadResult =
        when (val outcome = loadOutcome()) {
            is LoadOutcome.Empty -> RegistryLoadResult.Success(emptyList(), null)
            is LoadOutcome.Loaded -> {
                if (outcome.projects.any(::isRegistryInsideProject)) {
                    RegistryLoadResult.Unreadable(
                        "Registry 경로가 프로젝트 root 아래에 있어 사용할 수 없습니다.",
                    )
                } else if (outcome.droppedEntryCount == 0) {
                    RegistryLoadResult.Success(outcome.projects, outcome.lastActiveProjectId)
                } else {
                    recoverCorruption(
                        projects = outcome.projects,
                        lastActiveProjectId = outcome.lastActiveProjectId,
                        message = "${outcome.droppedEntryCount}개 project entry가 손상되어 제외했습니다.",
                    )
                }
            }
            is LoadOutcome.Corrupt -> recoverCorruption(
                projects = emptyList(),
                lastActiveProjectId = null,
                message = "Registry 파일을 해석할 수 없어 격리했습니다: ${outcome.message}",
            )
            is LoadOutcome.Unreadable -> RegistryLoadResult.Unreadable(outcome.message)
        }

    /**
     * 손상 원본을 먼저 복사한 뒤 유효 entry만으로 정본을 원자 재작성한다. 재작성 실패 시에도
     * 원본과 quarantine은 남고, 다음 조회가 다시 typed corruption 결과를 반환한다.
     */
    private fun recoverCorruption(
        projects: List<HarnessProject>,
        lastActiveProjectId: ProjectId?,
        message: String,
    ): RegistryLoadResult {
        if (projects.any(::isRegistryInsideProject)) {
            return RegistryLoadResult.Unreadable(
                "Registry 경로가 프로젝트 root 아래에 있어 손상 복구 파일을 만들 수 없습니다.",
            )
        }
        val quarantinePath = quarantineLocked()
            ?: return RegistryLoadResult.Unreadable("손상 Registry 원본을 백업할 수 없어 복구하지 않았습니다.")
        val repaired = writeEnvelope(projects, lastActiveProjectId)
        val repairMessage = when (repaired) {
            RegistrySaveResult.Success -> message
            is RegistrySaveResult.Failed -> "$message 정본 재작성은 실패했습니다: ${repaired.message}"
        }
        return RegistryLoadResult.RecoveredFromCorruption(
            projects = projects,
            lastActiveProjectId = lastActiveProjectId,
            quarantinePath = quarantinePath,
            message = repairMessage,
        )
    }

    /** mutation이 손상 entry를 버리기 전 원본 bytes를 반드시 quarantine한다. */
    private fun prepareMutationSnapshot(): MutationSnapshotResult =
        when (val outcome = loadOutcome()) {
            LoadOutcome.Empty -> MutationSnapshotResult.Ready(emptyList(), null)
            is LoadOutcome.Loaded -> {
                if (outcome.droppedEntryCount > 0 && quarantineLocked() == null) {
                    MutationSnapshotResult.Failed("손상 Registry 원본을 백업할 수 없어 변경하지 않았습니다.")
                } else {
                    MutationSnapshotResult.Ready(outcome.projects, outcome.lastActiveProjectId)
                }
            }
            is LoadOutcome.Corrupt -> {
                val recovered = recoverCorruption(emptyList(), null, "Registry 손상을 복구했습니다.")
                when (recovered) {
                    is RegistryLoadResult.RecoveredFromCorruption -> MutationSnapshotResult.Failed(
                        "Registry 손상을 복구했습니다. 변경 작업을 다시 시도하세요.",
                    )
                    is RegistryLoadResult.Unreadable -> MutationSnapshotResult.Failed(recovered.message)
                    is RegistryLoadResult.Success -> MutationSnapshotResult.Failed("Registry 변경을 다시 시도하세요.")
                }
            }
            is LoadOutcome.Unreadable -> MutationSnapshotResult.Failed(outcome.message)
        }

    private fun loadOutcome(): LoadOutcome {
        if (!Files.exists(registryPath)) return LoadOutcome.Empty

        val bytes = try {
            Files.readAllBytes(registryPath)
        } catch (exception: IOException) {
            return LoadOutcome.Unreadable(exception.message ?: "registry read failed")
        } catch (exception: SecurityException) {
            return LoadOutcome.Unreadable(exception.message ?: "registry read denied")
        }

        val text = when (val decoded = decodeUtf8Strict(bytes)) {
            is DecodeResult.Text -> decoded.text
            is DecodeResult.InvalidEncoding -> return LoadOutcome.Corrupt(decoded.message)
        }

        val envelope = try {
            json.decodeFromString(ProjectRegistryFileDto.serializer(), text)
        } catch (exception: SerializationException) {
            return LoadOutcome.Corrupt(exception.message ?: "invalid JSON")
        } catch (exception: IllegalArgumentException) {
            return LoadOutcome.Corrupt(exception.message ?: "invalid JSON")
        }

        val schemaVersion = envelope.schemaVersion
            ?: return LoadOutcome.Corrupt("schema_version is missing")
        if (schemaVersion.substringBefore('.').toIntOrNull() != 1) {
            return LoadOutcome.Corrupt("unsupported schema_version")
        }

        val mapped = (envelope.projects ?: emptyList()).map { it.toDomain() }
        val validProjects = mapped.filterIsInstance<ProjectMapResult.Success>().map { it.project }
        val duplicateCount = validProjects.size - validProjects.distinctBy { it.id }.size
        val projects = validProjects.distinctBy { it.id }
        val droppedCount = mapped.count { it is ProjectMapResult.Failure } + duplicateCount
        val lastActiveId = envelope.lastActiveProjectId
            ?.takeIf { it.isNotBlank() }
            ?.let(::ProjectId)
            ?.takeIf { id -> projects.any { it.id == id } }

        return LoadOutcome.Loaded(projects, lastActiveId, droppedCount)
    }

    private fun writeEnvelope(
        projects: List<HarnessProject>,
        lastActiveProjectId: ProjectId?,
    ): RegistrySaveResult {
        val envelope = ProjectRegistryFileDto(
            schemaVersion = REGISTRY_SCHEMA_VERSION,
            lastActiveProjectId = lastActiveProjectId?.value,
            projects = projects.map { it.toDto() },
        )
        val text = json.encodeToString(ProjectRegistryFileDto.serializer(), envelope)

        return try {
            val dir = registryPath.toAbsolutePath().normalize().parent
            Files.createDirectories(dir)
            val temp = Files.createTempFile(dir, "projects", ".tmp")
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
                moveIntoPlace(temp, registryPath)
                RegistrySaveResult.Success
            } finally {
                Files.deleteIfExists(temp)
            }
        } catch (exception: IOException) {
            RegistrySaveResult.Failed(exception.message ?: "registry write failed")
        } catch (exception: SecurityException) {
            RegistrySaveResult.Failed(exception.message ?: "registry write denied")
        }
    }

    private fun quarantineLocked(): Path? {
        val baseName = "${registryPath.fileName}.corrupt-${clock().toEpochMilli()}"
        var index = 0
        var candidate = registryPath.resolveSibling(baseName)
        while (Files.exists(candidate)) {
            index += 1
            candidate = registryPath.resolveSibling("$baseName-$index")
        }
        return try {
            Files.copy(registryPath, candidate)
            candidate
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    /**
     * `DefaultKit`는 절대 경로를 domain에 갖고 있지 않으므로(§20.1) 이 검사에서
     * 제외한다 — repository-relative 기본 Kit 경로가 `%APPDATA%\hrns-now` 근처에 있을 일은
     * 실질적으로 없고, 필요한 real root 비교는 `ExternalKit`처럼 실제 절대 경로가 있을 때만
     * 의미가 있다.
     */
    private fun isRegistryInsideProject(project: HarnessProject): Boolean {
        val registryLexical = registryPath.toAbsolutePath().normalize()
        val registryRealCandidate = realPathCandidate(registryLexical)
        val projectRoots = listOfNotNull(
            (project.runtimeSource as? RuntimeSource.ExternalKit)?.root,
            project.projectWorkspaceRoot,
            project.repositoryRoot,
        )
        return projectRoots.any { root ->
            val rootLexical = root.toAbsolutePath().normalize()
            val lexicalOverlap = registryLexical == rootLexical || registryLexical.startsWith(rootLexical)
            val rootReal = try {
                rootLexical.toRealPath()
            } catch (_: IOException) {
                null
            } catch (_: SecurityException) {
                null
            }
            val realOverlap = registryRealCandidate != null && rootReal != null &&
                (registryRealCandidate == rootReal || registryRealCandidate.startsWith(rootReal))
            lexicalOverlap || realOverlap
        }
    }

    /** 존재하지 않는 leaf도 가장 가까운 기존 ancestor의 real path 기준으로 비교한다. */
    private fun realPathCandidate(path: Path): Path? {
        val missingNames = ArrayDeque<String>()
        var ancestor: Path? = path
        while (ancestor != null && !Files.exists(ancestor)) {
            ancestor.fileName?.toString()?.let(missingNames::addFirst)
            ancestor = ancestor.parent
        }
        val realAncestor = try {
            ancestor?.toRealPath() ?: return null
        } catch (_: IOException) {
            return null
        } catch (_: SecurityException) {
            return null
        }
        return missingNames.fold(realAncestor) { current, name -> current.resolve(name) }.normalize()
    }
}

private fun moveRegistryFile(source: Path, target: Path) {
    try {
        Files.move(
            source,
            target,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
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
    val decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    return try {
        DecodeResult.Text(decoder.decode(ByteBuffer.wrap(withoutBom)).toString())
    } catch (exception: CharacterCodingException) {
        DecodeResult.InvalidEncoding(exception.message ?: "invalid UTF-8 byte sequence")
    }
}
