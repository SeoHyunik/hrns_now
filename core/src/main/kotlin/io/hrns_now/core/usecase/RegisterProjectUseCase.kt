package io.hrns_now.core.usecase

import io.hrns_now.core.domain.model.HarnessProject
import io.hrns_now.core.domain.model.ProjectBoundaryResult
import io.hrns_now.core.domain.model.ProjectId
import io.hrns_now.core.domain.model.RootPathCheck
import io.hrns_now.core.domain.model.RuntimeIssue
import io.hrns_now.core.domain.model.RuntimeResolution
import io.hrns_now.core.domain.model.RuntimeSource
import io.hrns_now.core.domain.model.BoundaryStatus
import io.hrns_now.core.domain.policy.BoundaryPolicy
import io.hrns_now.core.port.ProjectRegistryPort
import io.hrns_now.core.result.RegistrySaveResult
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.UUID

/**
 * 사용자가 Setup 화면에서 입력한, 아직 검증되지 않은 신규 프로젝트 후보다. `useInternalDeveloperSdk`가
 * true면 [kitRootRaw]는 무시한다 — 표준 등록 흐름은 Kit 경로를 전혀 입력받지 않는다(새 Phase 7).
 * `kitRootRaw`는 사용자가 고급 설정에서 명시적으로 `외부 Harness Kit 사용`을 선택했을 때만 쓰인다.
 */
data class RegisterProjectCandidate(
    val displayName: String,
    val useInternalDeveloperSdk: Boolean,
    val kitRootRaw: String?,
    val projectWorkspaceRootRaw: String?,
    val repositoryRootRaw: String?,
    val profileId: String,
)

/**
 * `InvalidCandidate`의 typed 원인이다(새 Phase 8 §1) — presentation 계층이 사람이 읽는
 * [ProjectRegistrationInspection.InvalidCandidate.message] 문자열 일부를 비교해 원인을 추정하지
 * 않고, 이 sealed 값만으로 UI 처리(예: "고급 설정" 안내 노출 여부)를 분기할 수 있게 한다.
 */
sealed interface RegistrationRejectionReason {
    data object BlankDisplayName : RegistrationRejectionReason
    data object BlankProfile : RegistrationRejectionReason
    data object BlankExternalKitPath : RegistrationRejectionReason
    data object InvalidExternalKitPathFormat : RegistrationRejectionReason
    data class RuntimeMissing(val source: RuntimeSource) : RegistrationRejectionReason
    data class RuntimeInvalid(val source: RuntimeSource, val issue: RuntimeIssue) : RegistrationRejectionReason
}

sealed interface RegisterProjectResult {
    data class Registered(val project: HarnessProject) : RegisterProjectResult
    data class InvalidCandidate(val reason: RegistrationRejectionReason, val message: String) : RegisterProjectResult
    data class BoundaryRejected(val boundary: ProjectBoundaryResult) : RegisterProjectResult
    data class SaveFailed(val message: String) : RegisterProjectResult
}

/**
 * Registry write 전 단계의 typed 후보 검증 결과다. [Ready.resolvedKitRoot]는 [Ready.project]가
 * 저장할 [RuntimeSource]를 이미 안전하게 해석한 실제 root다 — Registry는 이 경로 자체를 저장하지
 * 않지만(특히 `InternalDeveloperSdk`), 등록 전 Doctor gate처럼 즉시 실행이 필요한 호출자는 이
 * 값을 재해석 없이 그대로 쓸 수 있다.
 */
sealed interface ProjectRegistrationInspection {
    data class Ready(val project: HarnessProject, val resolvedKitRoot: Path) : ProjectRegistrationInspection
    data class InvalidCandidate(
        val reason: RegistrationRejectionReason,
        val message: String,
    ) : ProjectRegistrationInspection
    data class BoundaryRejected(val boundary: ProjectBoundaryResult) : ProjectRegistrationInspection
}

/**
 * 신규 프로젝트를 경계 검증 후 Registry에 저장하는 use case다.
 *
 * boundary가 [BoundaryStatus.Valid]일 때만 [ProjectRegistryPort.save]를 호출한다 — 검증과 저장
 * 사이에 이를 우회하는 경로를 만들지 않는다(`doc/claude_prompts/phase1d-project-registry.md`
 * 금지사항). 실제 경로 실재/real path 확인은 [pathResolver](주입된 `infra.registry.RealPathGateway`)가
 * 담당하고, runtime source 가용성/무결성 확인은 [runtimeSourceResolver](주입된
 * `infra.runtime.DeveloperSdkRuntimeResolver`, 새 Phase 7)가 담당한다. 이 use case와
 * [BoundaryPolicy]는 그 결과만 소비한다.
 */
class RegisterProjectUseCase(
    private val pathResolver: (String?) -> RootPathCheck,
    private val runtimeSourceResolver: (RuntimeSource) -> RuntimeResolution,
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
            ?: return ProjectRegistrationInspection.InvalidCandidate(
                RegistrationRejectionReason.BlankDisplayName,
                "표시명을 입력하세요.",
            )
        val profileId = candidate.profileId.trim().takeIf(String::isNotEmpty)
            ?: return ProjectRegistrationInspection.InvalidCandidate(
                RegistrationRejectionReason.BlankProfile,
                "Profile을 입력하세요.",
            )

        val runtimeSourceCandidate: RuntimeSource = if (candidate.useInternalDeveloperSdk) {
            RuntimeSource.InternalDeveloperSdk
        } else {
            val raw = candidate.kitRootRaw?.trim()?.takeIf(String::isNotEmpty)
                ?: return ProjectRegistrationInspection.InvalidCandidate(
                    RegistrationRejectionReason.BlankExternalKitPath,
                    "외부 Harness Kit 경로를 입력하세요.",
                )
            val parsed = try {
                Path.of(raw)
            } catch (_: InvalidPathException) {
                return ProjectRegistrationInspection.InvalidCandidate(
                    RegistrationRejectionReason.InvalidExternalKitPathFormat,
                    "Kit 경로 형식이 올바르지 않습니다.",
                )
            }
            RuntimeSource.ExternalKit(parsed)
        }

        val runtimeResolution = runtimeSourceResolver(runtimeSourceCandidate)
        val resolvedKitRoot = when (runtimeResolution) {
            is RuntimeResolution.Resolved -> runtimeResolution.root
            is RuntimeResolution.Missing ->
                return ProjectRegistrationInspection.InvalidCandidate(
                    RegistrationRejectionReason.RuntimeMissing(runtimeResolution.source),
                    missingRuntimeMessage(runtimeResolution.source),
                )
            is RuntimeResolution.Invalid ->
                return ProjectRegistrationInspection.InvalidCandidate(
                    RegistrationRejectionReason.RuntimeInvalid(runtimeResolution.source, runtimeResolution.reason),
                    invalidRuntimeMessage(runtimeResolution),
                )
        }

        val kit = pathResolver(resolvedKitRoot.toString())
        val workspace = pathResolver(candidate.projectWorkspaceRootRaw)
        val repository = pathResolver(candidate.repositoryRootRaw)
        val boundary = boundaryPolicy.evaluate(kit = kit, workspace = workspace, repository = repository)

        if (boundary.status != BoundaryStatus.Valid) {
            return ProjectRegistrationInspection.BoundaryRejected(boundary)
        }

        val validKit = boundary.kit as RootPathCheck.Valid
        val validWorkspace = boundary.workspace as RootPathCheck.Valid
        val validRepository = boundary.repository as RootPathCheck.Valid
        // InternalDeveloperSdk는 절대 경로가 아니라 선택 그 자체만 Registry에 저장한다 — ExternalKit만
        // boundary가 정규화한 절대 경로를 저장한다(`doc/hrns_now_design_pattern.md` §20.1).
        val runtimeSourceToStore = when (runtimeSourceCandidate) {
            RuntimeSource.InternalDeveloperSdk -> RuntimeSource.InternalDeveloperSdk
            is RuntimeSource.ExternalKit -> RuntimeSource.ExternalKit(validKit.normalized)
        }
        return ProjectRegistrationInspection.Ready(
            project = HarnessProject(
                id = idFactory(),
                displayName = displayName,
                runtimeSource = runtimeSourceToStore,
                projectWorkspaceRoot = validWorkspace.normalized,
                repositoryRoot = validRepository.normalized,
                profileId = profileId,
                lastSelectedDate = null,
                lastDiagnosticsSummary = null,
                lastRunAt = null,
            ),
            resolvedKitRoot = resolvedKitRoot,
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
            is ProjectRegistrationInspection.InvalidCandidate ->
                RegisterProjectResult.InvalidCandidate(inspection.reason, inspection.message)
            is ProjectRegistrationInspection.BoundaryRejected -> RegisterProjectResult.BoundaryRejected(inspection.boundary)
        }

    private fun missingRuntimeMessage(source: RuntimeSource): String =
        when (source) {
            RuntimeSource.InternalDeveloperSdk ->
                "개발용 내장 SDK(.local\\harness-kit)를 찾을 수 없습니다. SDK를 준비하거나 고급 설정에서 외부 Harness Kit을 선택하세요."
            is RuntimeSource.ExternalKit ->
                "지정한 외부 Harness Kit 경로를 찾을 수 없습니다: ${source.root}"
        }

    private fun invalidRuntimeMessage(resolution: RuntimeResolution.Invalid): String {
        val reasonText = when (resolution.reason) {
            RuntimeIssue.NotDirectory -> "경로가 디렉터리가 아닙니다."
            RuntimeIssue.NotReadable -> "경로를 읽을 수 없습니다."
            RuntimeIssue.MissingEntrypoint -> "필요한 Harness 파일(doctor.ps1/validate-ops.ps1/run-cycle.ps1/kit-version.json)이 없습니다."
        }
        val sourceText = when (resolution.source) {
            RuntimeSource.InternalDeveloperSdk -> "개발용 내장 SDK"
            is RuntimeSource.ExternalKit -> "외부 Harness Kit"
        }
        return "$sourceText 확인 실패: $reasonText"
    }
}
