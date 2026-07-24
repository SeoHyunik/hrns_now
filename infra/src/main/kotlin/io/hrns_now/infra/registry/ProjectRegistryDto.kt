package io.hrns_now.infra.registry

import io.hrns_now.core.domain.model.HarnessProject
import io.hrns_now.core.domain.model.ProjectId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException

internal const val REGISTRY_SCHEMA_VERSION = "1.0"

/**
 * `%APPDATA%\hrns-now\projects.json`의 외부 JSON 모양을 있는 그대로 표현하는 DTO다
 * (Anti-Corruption Layer, `doc/hrns_now_design_pattern.md` §4와 동일한 원칙).
 * 모든 필드는 nullable + 기본값 `null`이다 — "필드가 필수인지" 판단은
 * [toDomain]에서 명시적으로 수행한다.
 */
@Serializable
internal data class ProjectRegistryFileDto(
    @SerialName("schema_version") val schemaVersion: String? = null,
    @SerialName("last_active_project_id") val lastActiveProjectId: String? = null,
    val projects: List<HarnessProjectDto>? = null,
)

@Serializable
internal data class HarnessProjectDto(
    val id: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("kit_root") val kitRoot: String? = null,
    @SerialName("project_workspace_root") val projectWorkspaceRoot: String? = null,
    @SerialName("repository_root") val repositoryRoot: String? = null,
    @SerialName("profile_id") val profileId: String? = null,
    @SerialName("last_selected_date") val lastSelectedDate: String? = null,
    @SerialName("last_diagnostics_summary") val lastDiagnosticsSummary: String? = null,
    @SerialName("last_run_at") val lastRunAt: String? = null,
)

internal sealed interface ProjectMapResult {
    data class Success(val project: HarnessProject) : ProjectMapResult
    data class Failure(val message: String) : ProjectMapResult
}

/**
 * 필수 project 필드(`id`/`display_name`/`kit_root`/`project_workspace_root`/`repository_root`/
 * `profile_id`) 누락을 기본값으로 은폐하지 않는다 — 하나라도 없으면 이 entry만 [ProjectMapResult.Failure]로
 * 떨어뜨리고, 나머지 유효한 entry는 그대로 살린다(부분 복구).
 */
internal fun HarnessProjectDto.toDomain(): ProjectMapResult {
    val id = id?.trim()?.takeIf(String::isNotEmpty)
        ?: return ProjectMapResult.Failure("project.id is missing")
    val displayName = displayName?.trim()?.takeIf(String::isNotEmpty)
        ?: return ProjectMapResult.Failure("project.display_name is missing (id=$id)")
    val kitRootRaw = kitRoot?.trim()?.takeIf(String::isNotEmpty)
        ?: return ProjectMapResult.Failure("project.kit_root is missing (id=$id)")
    val workspaceRootRaw = projectWorkspaceRoot?.trim()?.takeIf(String::isNotEmpty)
        ?: return ProjectMapResult.Failure("project.project_workspace_root is missing (id=$id)")
    val repositoryRootRaw = repositoryRoot?.trim()?.takeIf(String::isNotEmpty)
        ?: return ProjectMapResult.Failure("project.repository_root is missing (id=$id)")
    val profileId = profileId?.trim()?.takeIf(String::isNotEmpty)
        ?: return ProjectMapResult.Failure("project.profile_id is missing (id=$id)")

    val selectedDate = if (lastSelectedDate == null) {
        null
    } else {
        try {
            LocalDate.parse(lastSelectedDate)
        } catch (_: DateTimeParseException) {
            return ProjectMapResult.Failure("project.last_selected_date is invalid (id=$id)")
        }
    }
    val runAt = if (lastRunAt == null) {
        null
    } else {
        try {
            Instant.parse(lastRunAt)
        } catch (_: DateTimeParseException) {
            return ProjectMapResult.Failure("project.last_run_at is invalid (id=$id)")
        }
    }

    return try {
        val parsedKitRoot = Path.of(kitRootRaw)
        val parsedWorkspaceRoot = Path.of(workspaceRootRaw)
        val parsedRepositoryRoot = Path.of(repositoryRootRaw)
        if (
            !isPortableAbsolutePath(kitRootRaw, parsedKitRoot) ||
            !isPortableAbsolutePath(workspaceRootRaw, parsedWorkspaceRoot) ||
            !isPortableAbsolutePath(repositoryRootRaw, parsedRepositoryRoot)
        ) {
            return ProjectMapResult.Failure("project root path must be absolute (id=$id)")
        }
        ProjectMapResult.Success(
            HarnessProject(
                id = ProjectId(id),
                displayName = displayName,
                kitRoot = parsedKitRoot.normalize(),
                projectWorkspaceRoot = parsedWorkspaceRoot.normalize(),
                repositoryRoot = parsedRepositoryRoot.normalize(),
                profileId = profileId,
                lastSelectedDate = selectedDate,
                lastDiagnosticsSummary = lastDiagnosticsSummary,
                lastRunAt = runAt,
            ),
        )
    } catch (_: InvalidPathException) {
        ProjectMapResult.Failure("project root path is invalid (id=$id)")
    }
}

private fun isPortableAbsolutePath(raw: String, parsed: Path): Boolean =
    parsed.isAbsolute || WINDOWS_DRIVE_ABSOLUTE.matches(raw) || raw.startsWith("\\\\")

private val WINDOWS_DRIVE_ABSOLUTE = Regex("""^[A-Za-z]:[\\/].*""")

internal fun HarnessProject.toDto(): HarnessProjectDto =
    HarnessProjectDto(
        id = id.value,
        displayName = displayName,
        kitRoot = kitRoot.toString(),
        projectWorkspaceRoot = projectWorkspaceRoot.toString(),
        repositoryRoot = repositoryRoot.toString(),
        profileId = profileId,
        lastSelectedDate = lastSelectedDate?.toString(),
        lastDiagnosticsSummary = lastDiagnosticsSummary,
        lastRunAt = lastRunAt?.toString(),
    )
