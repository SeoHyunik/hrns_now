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
    suspend operator fun invoke(candidate: RegisterProjectCandidate): RegisterProjectResult {
        val displayName = candidate.displayName.trim().takeIf(String::isNotEmpty)
            ?: return RegisterProjectResult.InvalidCandidate("표시명을 입력하세요.")
        val profileId = candidate.profileId.trim().takeIf(String::isNotEmpty)
            ?: return RegisterProjectResult.InvalidCandidate("Profile을 입력하세요.")
        val kit = pathResolver(candidate.kitRootRaw)
        val workspace = pathResolver(candidate.projectWorkspaceRootRaw)
        val repository = pathResolver(candidate.repositoryRootRaw)
        val boundary = boundaryPolicy.evaluate(kit = kit, workspace = workspace, repository = repository)

        if (boundary.status != BoundaryStatus.Valid) {
            return RegisterProjectResult.BoundaryRejected(boundary)
        }

        val validKit = boundary.kit as RootPathCheck.Valid
        val validWorkspace = boundary.workspace as RootPathCheck.Valid
        val validRepository = boundary.repository as RootPathCheck.Valid

        val project = HarnessProject(
            id = idFactory(),
            displayName = displayName,
            kitRoot = validKit.normalized,
            projectWorkspaceRoot = validWorkspace.normalized,
            repositoryRoot = validRepository.normalized,
            profileId = profileId,
            lastSelectedDate = null,
            lastDiagnosticsSummary = null,
            lastRunAt = null,
        )

        return when (val saved = registry.save(project)) {
            RegistrySaveResult.Success -> RegisterProjectResult.Registered(project)
            is RegistrySaveResult.Failed -> RegisterProjectResult.SaveFailed(saved.message)
        }
    }
}
