package io.hrns_now.infra.registry

import io.hrns_now.core.domain.model.HarnessProject
import io.hrns_now.core.domain.model.ProjectId
import io.hrns_now.core.domain.model.RuntimeSource
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
    /**
     * 새 Phase 7의 typed [RuntimeSource] 선택이다. `"internal_developer_sdk"` | `"external_kit"`만
     * 유효하다. 이 field가 없는(=schema_version 1.0 초기 항목) legacy entry는 [kitRoot]가
     * 있으면 `external_kit`으로 읽기 시점에 해석한다(`doc/hrns_now_design_pattern.md` §20.1) —
     * 별도 batch migration 없이, 다음에 이 project가 다시 저장될 때 이 field가 채워진다.
     */
    @SerialName("runtime_source_type") val runtimeSourceType: String? = null,
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

private const val RUNTIME_SOURCE_INTERNAL = "internal_developer_sdk"
private const val RUNTIME_SOURCE_EXTERNAL = "external_kit"

/**
 * 필수 project 필드(`id`/`display_name`/`project_workspace_root`/`repository_root`/`profile_id`)와
 * runtime source 선택 누락을 기본값으로 은폐하지 않는다 — 하나라도 없거나 해석할 수 없으면 이
 * entry만 [ProjectMapResult.Failure]로 떨어뜨리고, 나머지 유효한 entry는 그대로 살린다(부분 복구).
 */
internal fun HarnessProjectDto.toDomain(): ProjectMapResult {
    val id = id?.trim()?.takeIf(String::isNotEmpty)
        ?: return ProjectMapResult.Failure("project.id is missing")
    val displayName = displayName?.trim()?.takeIf(String::isNotEmpty)
        ?: return ProjectMapResult.Failure("project.display_name is missing (id=$id)")
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

    val runtimeSource = when (val typeRaw = runtimeSourceType?.trim()?.takeIf(String::isNotEmpty)) {
        RUNTIME_SOURCE_INTERNAL -> {
            if (!kitRoot.isNullOrBlank()) {
                return ProjectMapResult.Failure("project.kit_root must be absent for internal runtime source (id=$id)")
            }
            RuntimeSource.InternalDeveloperSdk
        }
        RUNTIME_SOURCE_EXTERNAL, null -> {
            // typeRaw == null: schema_version 1.0 legacy entry — kit_root만 있던 시절의 저장값을
            // ExternalKit으로 migration한다(파괴적 재작성이 아니라 읽기 시점 해석).
            val kitRootRaw = kitRoot?.trim()?.takeIf(String::isNotEmpty)
                ?: return ProjectMapResult.Failure("project.kit_root is missing (id=$id)")
            val parsedKitRoot = try {
                Path.of(kitRootRaw)
            } catch (_: InvalidPathException) {
                return ProjectMapResult.Failure("project.kit_root is invalid (id=$id)")
            }
            if (!isPortableAbsolutePath(kitRootRaw, parsedKitRoot)) {
                return ProjectMapResult.Failure("project.kit_root must be absolute (id=$id)")
            }
            RuntimeSource.ExternalKit(parsedKitRoot.normalize())
        }
        else -> return ProjectMapResult.Failure("project.runtime_source_type is unknown (id=$id, value=$typeRaw)")
    }

    return try {
        val parsedWorkspaceRoot = Path.of(workspaceRootRaw)
        val parsedRepositoryRoot = Path.of(repositoryRootRaw)
        if (
            !isPortableAbsolutePath(workspaceRootRaw, parsedWorkspaceRoot) ||
            !isPortableAbsolutePath(repositoryRootRaw, parsedRepositoryRoot)
        ) {
            return ProjectMapResult.Failure("project root path must be absolute (id=$id)")
        }
        ProjectMapResult.Success(
            HarnessProject(
                id = ProjectId(id),
                displayName = displayName,
                runtimeSource = runtimeSource,
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

/**
 * `InternalDeveloperSdk`는 선택 자체만 쓰고 `kit_root`는 항상 `null`이다 — repository-relative
 * SDK의 절대 경로를 저장하면 source checkout을 다른 위치로 옮겼을 때 깨진 경로가 남는다
 * (`doc/hrns_now_design_pattern.md` §20.1). `ExternalKit`만 사용자가 명시한 절대 경로를 저장한다.
 */
internal fun HarnessProject.toDto(): HarnessProjectDto =
    HarnessProjectDto(
        id = id.value,
        displayName = displayName,
        runtimeSourceType = when (runtimeSource) {
            RuntimeSource.InternalDeveloperSdk -> RUNTIME_SOURCE_INTERNAL
            is RuntimeSource.ExternalKit -> RUNTIME_SOURCE_EXTERNAL
        },
        kitRoot = (runtimeSource as? RuntimeSource.ExternalKit)?.root?.toString(),
        projectWorkspaceRoot = projectWorkspaceRoot.toString(),
        repositoryRoot = repositoryRoot.toString(),
        profileId = profileId,
        lastSelectedDate = lastSelectedDate?.toString(),
        lastDiagnosticsSummary = lastDiagnosticsSummary,
        lastRunAt = lastRunAt?.toString(),
    )
