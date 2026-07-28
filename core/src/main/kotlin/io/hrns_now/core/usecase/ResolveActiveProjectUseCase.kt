package io.hrns_now.core.usecase

import io.hrns_now.core.config.RuntimeConfig
import io.hrns_now.core.config.WorkspaceConfig
import io.hrns_now.core.config.WorkspaceRoots
import io.hrns_now.core.domain.model.HarnessProject
import io.hrns_now.core.port.ProjectRegistryPort
import io.hrns_now.core.result.RegistryLoadResult

/** 현재 활성 프로젝트/설정이 어디서 왔는지를 사용자가 알 수 있게 typed로 구분한다. */
sealed interface ActiveProjectSource {
    /** Registry에 저장된 "마지막으로 활성화된 프로젝트"에서 왔다. */
    data object Registry : ActiveProjectSource

    /** Registry가 비었거나 손상돼, 기존 환경변수 fallback으로 대체했다. */
    data object EnvironmentFallback : ActiveProjectSource

    /** Registry도, 환경변수도 없어 사용자가 프로젝트를 선택해야 한다. */
    data object NoneSelected : ActiveProjectSource
}

data class ActiveProjectResolution(
    val workspaceConfig: WorkspaceConfig,
    val project: HarnessProject?,
    val source: ActiveProjectSource,
    val registryProjects: List<HarnessProject>,
    val registryLoadResult: RegistryLoadResult,
)

/**
 * 앱 시작 시 활성 프로젝트를 결정하는 use case다. 우선순위는 계획서/프롬프트가 명시한
 * "Registry의 마지막 선택 → 기존 EnvironmentWorkspaceConfigProvider fallback → 사용자 선택 필요"
 * 순서를 그대로 따른다. Registry 조회 실패/손상을 mock 성공처럼 취급하지 않고 [ActiveProjectSource]로
 * 그 출처를 그대로 드러낸다.
 */
class ResolveActiveProjectUseCase(
    private val registry: ProjectRegistryPort,
    private val environmentConfigProvider: () -> WorkspaceConfig,
) {
    suspend operator fun invoke(): ActiveProjectResolution {
        val loaded = registry.findAll()
        val projects = loaded.projectsOrEmpty()
        val lastActiveId = loaded.lastActiveProjectIdOrNull()
        val active = lastActiveId?.let { id -> projects.firstOrNull { it.id == id } }

        if (active != null) {
            return ActiveProjectResolution(
                workspaceConfig = active.toWorkspaceConfig(),
                project = active,
                source = ActiveProjectSource.Registry,
                registryProjects = projects,
                registryLoadResult = loaded,
            )
        }

        val environmentConfig = environmentConfigProvider()
        val source = if (environmentConfig.roots.workspaceRoot.isNullOrBlank()) {
            ActiveProjectSource.NoneSelected
        } else {
            ActiveProjectSource.EnvironmentFallback
        }
        return ActiveProjectResolution(
            workspaceConfig = environmentConfig,
            project = null,
            source = source,
            registryProjects = projects,
            registryLoadResult = loaded,
        )
    }

    private fun RegistryLoadResult.projectsOrEmpty(): List<HarnessProject> =
        when (this) {
            is RegistryLoadResult.Success -> projects
            is RegistryLoadResult.RecoveredFromCorruption -> projects
            is RegistryLoadResult.Unreadable -> emptyList()
        }

    private fun RegistryLoadResult.lastActiveProjectIdOrNull() =
        when (this) {
            is RegistryLoadResult.Success -> lastActiveProjectId
            is RegistryLoadResult.RecoveredFromCorruption -> lastActiveProjectId
            is RegistryLoadResult.Unreadable -> null
        }
}

/**
 * Registry에 저장된 프로젝트를 [LoadCockpitUseCase]가 바로 쓸 수 있는 [WorkspaceConfig]로
 * 변환한다. PowerShell/Claude 명령 경로는 Registry 범위 밖이라(Phase 1D 미포함) 아직 옮기지
 * 않는다 — 여전히 환경변수로만 구성된다.
 *
 * `roots.kitRoot`는 여기서 채우지 않는다(항상 `null`) — `runtimeSource`는 typed 선택일 뿐
 * 아직 해석된 파일 시스템 경로가 아니기 때문이다(새 Phase 7,
 * `doc/hrns_now_design_pattern.md` §20.1). 호출자(`AppViewModel`)가
 * `RuntimeSourceResolverPort`로 해석한 뒤에만 이 값을 채운다.
 */
fun HarnessProject.toWorkspaceConfig(): WorkspaceConfig =
    WorkspaceConfig(
        workspaceName = displayName,
        profileName = profileId,
        roots = WorkspaceRoots(
            kitRoot = null,
            workspaceRoot = projectWorkspaceRoot.toString(),
            projectRoot = repositoryRoot.toString(),
        ),
        runtime = RuntimeConfig(powerShellPath = null, claudeCommand = null),
    )
