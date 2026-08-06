package io.hrns_now.core.usecase

import io.hrns_now.core.domain.model.BoundaryStatus
import io.hrns_now.core.domain.model.HarnessProject
import io.hrns_now.core.domain.model.PathIssue
import io.hrns_now.core.domain.model.ProjectId
import io.hrns_now.core.domain.model.RootPathCheck
import io.hrns_now.core.domain.model.RuntimeIssue
import io.hrns_now.core.domain.model.RuntimeResolution
import io.hrns_now.core.domain.model.RuntimeSource
import io.hrns_now.core.port.ProjectRegistryPort
import io.hrns_now.core.result.RegistryLoadResult
import io.hrns_now.core.result.RegistrySaveResult
import java.nio.file.Path
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RegisterProjectUseCaseTest {

    private class SpyRegistryPort(
        private val saveResult: RegistrySaveResult = RegistrySaveResult.Success,
    ) : ProjectRegistryPort {
        var saveCallCount = 0
            private set
        var lastSaved: HarnessProject? = null

        override suspend fun findAll(): RegistryLoadResult = RegistryLoadResult.Success(emptyList(), null)
        override suspend fun findById(id: ProjectId): HarnessProject? = null
        override suspend fun save(project: HarnessProject): RegistrySaveResult {
            saveCallCount++
            lastSaved = project
            return saveResult
        }
        override suspend fun delete(id: ProjectId): RegistrySaveResult = RegistrySaveResult.Success
        override suspend fun markActive(id: ProjectId): RegistrySaveResult = RegistrySaveResult.Success
        override suspend fun clearActive(): RegistrySaveResult = RegistrySaveResult.Success
    }

    private fun candidate(
        useDefaultKit: Boolean = false,
        kitRoot: String? = "/kit",
        workspaceRoot: String? = "/workspace",
        repositoryRoot: String? = "/repo",
    ) = RegisterProjectCandidate(
        displayName = "테스트 프로젝트",
        useDefaultKit = useDefaultKit,
        kitRootRaw = kitRoot,
        projectWorkspaceRootRaw = workspaceRoot,
        repositoryRootRaw = repositoryRoot,
        profileId = "기본",
    )

    /** 실제 파일 시스템 확인 없이, 입력 문자열을 그대로 유효한 root로 취급하는 테스트 전용 resolver다. */
    private fun alwaysValidResolver(): (String?) -> RootPathCheck = { raw ->
        if (raw.isNullOrBlank()) {
            RootPathCheck.Invalid(PathIssue.NotProvided)
        } else {
            RootPathCheck.Valid(normalized = Path.of(raw), realPath = Path.of(raw))
        }
    }

    /** runtime source를 항상 그대로 해석해 주는 테스트 전용 resolver다 — boundary/save 로직 테스트용. */
    private fun alwaysResolvedRuntimeResolver(internalRoot: Path = Path.of("/internal-sdk")): (RuntimeSource) -> RuntimeResolution =
        { source ->
            val root = when (source) {
                RuntimeSource.DefaultKit -> internalRoot
                is RuntimeSource.ExternalKit -> source.root
            }
            RuntimeResolution.Resolved(source, root)
        }

    @Test
    fun `경계가 유효하면 등록하고 save를 호출한다`() = runTest {
        val registry = SpyRegistryPort()
        val useCase = RegisterProjectUseCase(
            pathResolver = alwaysValidResolver(),
            runtimeSourceResolver = alwaysResolvedRuntimeResolver(),
            registry = registry,
            idFactory = { ProjectId("fixed-id") },
        )

        val result = useCase(candidate())

        val registered = assertIs<RegisterProjectResult.Registered>(result)
        assertEquals(1, registry.saveCallCount)
        assertEquals(ProjectId("fixed-id"), registered.project.id)
        assertEquals(registry.lastSaved, registered.project)
        assertIs<RuntimeSource.ExternalKit>(registered.project.runtimeSource)
    }

    @Test
    fun `useDefaultKit가 true면 ExternalKit 경로 없이도 DefaultKit로 등록한다`() = runTest {
        val registry = SpyRegistryPort()
        val useCase = RegisterProjectUseCase(
            pathResolver = alwaysValidResolver(),
            runtimeSourceResolver = alwaysResolvedRuntimeResolver(),
            registry = registry,
            idFactory = { ProjectId("fixed-id") },
        )

        val result = useCase(candidate(useDefaultKit = true, kitRoot = null))

        val registered = assertIs<RegisterProjectResult.Registered>(result)
        assertEquals(RuntimeSource.DefaultKit, registered.project.runtimeSource)
    }

    @Test
    fun `내장 SDK가 Missing이면 save를 호출하지 않고 InvalidCandidate를 반환한다`() = runTest {
        val registry = SpyRegistryPort()
        val useCase = RegisterProjectUseCase(
            pathResolver = alwaysValidResolver(),
            runtimeSourceResolver = { source -> RuntimeResolution.Missing(source) },
            registry = registry,
        )

        val result = useCase(candidate(useDefaultKit = true, kitRoot = null))

        val invalid = assertIs<RegisterProjectResult.InvalidCandidate>(result)
        val reason = assertIs<RegistrationRejectionReason.RuntimeMissing>(invalid.reason)
        assertEquals(RuntimeSource.DefaultKit, reason.source)
        assertEquals(0, registry.saveCallCount)
    }

    @Test
    fun `내장 SDK가 필요 entrypoint 없이 Invalid면 save를 호출하지 않고 InvalidCandidate를 반환한다`() = runTest {
        val registry = SpyRegistryPort()
        val useCase = RegisterProjectUseCase(
            pathResolver = alwaysValidResolver(),
            runtimeSourceResolver = { source -> RuntimeResolution.Invalid(source, RuntimeIssue.MissingEntrypoint) },
            registry = registry,
        )

        val result = useCase(candidate(useDefaultKit = true, kitRoot = null))

        val invalid = assertIs<RegisterProjectResult.InvalidCandidate>(result)
        val reason = assertIs<RegistrationRejectionReason.RuntimeInvalid>(invalid.reason)
        assertEquals(RuntimeIssue.MissingEntrypoint, reason.issue)
        assertEquals(0, registry.saveCallCount)
    }

    @Test
    fun `외부 Kit 경로가 Missing이면 boundary 검사 전에 InvalidCandidate로 거부한다`() = runTest {
        val registry = SpyRegistryPort()
        val useCase = RegisterProjectUseCase(
            pathResolver = alwaysValidResolver(),
            runtimeSourceResolver = { source -> RuntimeResolution.Missing(source) },
            registry = registry,
        )

        val result = useCase(candidate(useDefaultKit = false, kitRoot = "/missing-kit"))

        val invalid = assertIs<RegisterProjectResult.InvalidCandidate>(result)
        val reason = assertIs<RegistrationRejectionReason.RuntimeMissing>(invalid.reason)
        assertIs<RuntimeSource.ExternalKit>(reason.source)
        assertEquals(0, registry.saveCallCount)
    }

    @Test
    fun `표시명·profile·외부 Kit 경로 공백은 서로 구분된 typed reason으로 거부한다`() = runTest {
        val registry = SpyRegistryPort()
        val useCase = RegisterProjectUseCase(
            pathResolver = alwaysValidResolver(),
            runtimeSourceResolver = alwaysResolvedRuntimeResolver(),
            registry = registry,
        )

        val blankName = assertIs<RegisterProjectResult.InvalidCandidate>(useCase(candidate().copy(displayName = " ")))
        assertEquals(RegistrationRejectionReason.BlankDisplayName, blankName.reason)

        val blankProfile = assertIs<RegisterProjectResult.InvalidCandidate>(useCase(candidate().copy(profileId = " ")))
        assertEquals(RegistrationRejectionReason.BlankProfile, blankProfile.reason)

        val blankKit = assertIs<RegisterProjectResult.InvalidCandidate>(useCase(candidate(kitRoot = " ")))
        assertEquals(RegistrationRejectionReason.BlankExternalKitPath, blankKit.reason)

        assertEquals(0, registry.saveCallCount)
    }

    @Test
    fun `경계 위반이면 save를 호출하지 않고 BoundaryRejected를 반환한다`() = runTest {
        val registry = SpyRegistryPort()
        // workspace가 repository 내부 -> 경계 위반
        val useCase = RegisterProjectUseCase(
            pathResolver = alwaysValidResolver(),
            runtimeSourceResolver = alwaysResolvedRuntimeResolver(),
            registry = registry,
        )

        val result = useCase(
            candidate(workspaceRoot = "/repo/workspace", repositoryRoot = "/repo"),
        )

        assertIs<RegisterProjectResult.BoundaryRejected>(result)
        assertEquals(0, registry.saveCallCount)
    }

    @Test
    fun `외부 Kit 경로를 비워두면 resolver 호출 전에 InvalidCandidate로 거부한다`() = runTest {
        val registry = SpyRegistryPort()
        var resolveCount = 0
        val useCase = RegisterProjectUseCase(
            pathResolver = alwaysValidResolver(),
            runtimeSourceResolver = { source ->
                resolveCount += 1
                alwaysResolvedRuntimeResolver()(source)
            },
            registry = registry,
        )

        val result = useCase(candidate(kitRoot = null))

        val invalid = assertIs<RegisterProjectResult.InvalidCandidate>(result)
        assertTrue(invalid.message.isNotBlank())
        assertEquals(0, resolveCount)
        assertEquals(0, registry.saveCallCount)
    }

    @Test
    fun `root 경로가 없으면 save를 호출하지 않고 BoundaryRejected를 반환한다`() = runTest {
        val registry = SpyRegistryPort()
        val useCase = RegisterProjectUseCase(
            pathResolver = alwaysValidResolver(),
            runtimeSourceResolver = alwaysResolvedRuntimeResolver(),
            registry = registry,
        )

        val result = useCase(candidate(workspaceRoot = null))

        val rejected = assertIs<RegisterProjectResult.BoundaryRejected>(result)
        assertFalse(rejected.boundary.status == BoundaryStatus.Valid)
        assertEquals(0, registry.saveCallCount)
    }

    @Test
    fun `save가 실패하면 SaveFailed를 반환한다`() = runTest {
        val registry = SpyRegistryPort(saveResult = RegistrySaveResult.Failed("disk full"))
        val useCase = RegisterProjectUseCase(
            pathResolver = alwaysValidResolver(),
            runtimeSourceResolver = alwaysResolvedRuntimeResolver(),
            registry = registry,
        )

        val result = useCase(candidate())

        val failed = assertIs<RegisterProjectResult.SaveFailed>(result)
        assertEquals("disk full", failed.message)
        assertTrue(registry.saveCallCount == 1)
    }
    @Test
    fun `표시명이나 profile이 비어 있으면 path 검사와 save 전에 거부한다`() = runTest {
        val registry = SpyRegistryPort()
        var resolveCount = 0
        val useCase = RegisterProjectUseCase(
            pathResolver = {
                resolveCount += 1
                RootPathCheck.Valid(Path.of("/unused"), Path.of("/unused"))
            },
            runtimeSourceResolver = alwaysResolvedRuntimeResolver(),
            registry = registry,
        )

        val result = useCase(candidate().copy(displayName = "  "))

        assertIs<RegisterProjectResult.InvalidCandidate>(result)
        assertEquals(0, resolveCount)
        assertEquals(0, registry.saveCallCount)
    }
}
