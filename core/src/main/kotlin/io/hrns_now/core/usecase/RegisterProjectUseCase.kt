package io.hrns_now.core.usecase

import io.hrns_now.core.domain.model.HarnessProject
import io.hrns_now.core.domain.model.ProjectBoundaryResult
import io.hrns_now.core.domain.model.ProjectId
import io.hrns_now.core.domain.model.RootPathCheck
import io.hrns_now.core.domain.model.BoundaryStatus
import io.hrns_now.core.domain.policy.BoundaryPolicy
import io.hrns_now.core.port.ProjectRegistryPort
import io.hrns_now.core.result.RegistrySaveResult
import java.util.UUID

/** 사용자가 Setup 화면에서 입력한, 아직 검증되지 않은 신규 프로젝트 후보다. */
data class RegisterProjectCandidate(
    val displayName: String,
    val kitRootRaw: String?,
    val projectWorkspaceRootRaw: String?,
    val repositoryRootRaw: String?,
    val profileId: String,
)

sealed interface RegisterProjectResult {
    data class Registered(val project: HarnessProject) : RegisterProjectResult
    data class InvalidCandidate(val message: String) : RegisterProjectResult
    data class BoundaryRejected(val boundary: ProjectBoundaryResult) : RegisterProjectResult
    data class SaveFailed(val message: String) : RegisterProjectResult
}

/** Registry write 전 단계의 typed 후보 검증 결과다. */
sealed interface ProjectRegistrationInspection {
    data class Ready(val project: HarnessProject) : ProjectRegistrationInspection
    data class InvalidCandidate(val message: String) : ProjectRegistrationInspection
    data class BoundaryRejected(val boundary: ProjectBoundaryResult) : ProjectRegistrationInspection
}

/**
 * 신규 프로젝트를 경계 검증 후 Registry에 저장하는 use case다.
 *
 * boundary가 [BoundaryStatus.Valid]일 때만 [ProjectRegistryPort.save]를 호출한다 — 검증과 저장
 * 사이에 이를 우회하는 경로를 만들지 않는다(`doc/claude_prompts/phase1d-project-registry.md`
 * 금지사항). 실제 경로 실재/real path 확인은 [pathResolver](주입된 `infra.registry.RealPathGateway`)가
 * 담당하고, 이 use case와 [BoundaryPolicy]는 그 결과만 소비한다.
 */
class RegisterProjectUseCase(
    private val pathResolver: (String?) -> RootPathCheck,
    private val registry: ProjectRegistryPort,
    private val boundaryPolicy: BoundaryPolicy = BoundaryPolicy(),
    private val idFactory: () -> ProjectId = { ProjectId(UUID.randomUUID().toString()) },
) {
    /**
     * 후보를 저장하지 않고 경계까지 검증한다. Phase 3 onboarding은 이 결과로 Doctor와
     * compatibility를 먼저 확인한 뒤 [save]를 호출한다.
     */
    fun inspect(candidate: RegisterProjectCandidate): ProjectRegistrationInspection {
        val displayName = candidate.displayName.trim().takeIf(String::isNotEmpty)
            ?: return ProjectRegistrationInspection.InvalidCandidate("표시명을 입력하세요.")
        val profileId = candidate.profileId.trim().takeIf(String::isNotEmpty)
            ?: return ProjectRegistrationInspection.InvalidCandidate("Profile을 입력하세요.")
        val kit = pathResolver(candidate.kitRootRaw)
        val workspace = pathResolver(candidate.projectWorkspaceRootRaw)
        val repository = pathResolver(candidate.repositoryRootRaw)
        val boundary = boundaryPolicy.evaluate(kit = kit, workspace = workspace, repository = repository)

        if (boundary.status != BoundaryStatus.Valid) {
            return ProjectRegistrationInspection.BoundaryRejected(boundary)
        }

        val validKit = boundary.kit as RootPathCheck.Valid
        val validWorkspace = boundary.workspace as RootPathCheck.Valid
        val validRepository = boundary.repository as RootPathCheck.Valid
        return ProjectRegistrationInspection.Ready(
            HarnessProject(
                id = idFactory(),
                displayName = displayName,
                kitRoot = validKit.normalized,
                projectWorkspaceRoot = validWorkspace.normalized,
                repositoryRoot = validRepository.normalized,
                profileId = profileId,
                lastSelectedDate = null,
                lastDiagnosticsSummary = null,
                lastRunAt = null,
            ),
        )
    }

    suspend fun save(inspection: ProjectRegistrationInspection.Ready): RegisterProjectResult =
        when (val saved = registry.save(inspection.project)) {
            RegistrySaveResult.Success -> RegisterProjectResult.Registered(inspection.project)
            is RegistrySaveResult.Failed -> RegisterProjectResult.SaveFailed(saved.message)
        }

    suspend operator fun invoke(candidate: RegisterProjectCandidate): RegisterProjectResult =
        when (val inspection = inspect(candidate)) {
            is ProjectRegistrationInspection.Ready -> save(inspection)
            is ProjectRegistrationInspection.InvalidCandidate -> RegisterProjectResult.InvalidCandidate(inspection.message)
            is ProjectRegistrationInspection.BoundaryRejected -> RegisterProjectResult.BoundaryRejected(inspection.boundary)
        }
}
