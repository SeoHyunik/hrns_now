package io.hrns_now.core.domain.policy

import io.hrns_now.core.domain.model.BoundaryStatus
import io.hrns_now.core.domain.model.BoundaryViolation
import io.hrns_now.core.domain.model.PathIssue
import io.hrns_now.core.domain.model.RootPathCheck
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BoundaryPolicyTest {

    private val policy = BoundaryPolicy()

    private fun valid(path: String, realPath: String? = path): RootPathCheck.Valid =
        RootPathCheck.Valid(normalized = Path.of(path), realPath = realPath?.let { Path.of(it) })

    @Test
    fun `세 root가 서로 겹치지 않으면 Valid다`() {
        val result = policy.evaluate(
            kit = valid("/kit"),
            workspace = valid("/workspace"),
            repository = valid("/repo"),
        )
        assertEquals(BoundaryStatus.Valid, result.status)
        assertTrue(result.violations.isEmpty())
    }

    @Test
    fun `workspace가 repository 내부면 위반이다`() {
        val result = policy.evaluate(
            kit = valid("/kit"),
            workspace = valid("/repo/workspace"),
            repository = valid("/repo"),
        )
        assertEquals(BoundaryStatus.Invalid, result.status)
        assertTrue(BoundaryViolation.WorkspaceInsideRepository in result.violations)
    }

    @Test
    fun `repository가 workspace 내부면 위반이다`() {
        val result = policy.evaluate(
            kit = valid("/kit"),
            workspace = valid("/workspace"),
            repository = valid("/workspace/repo"),
        )
        assertEquals(BoundaryStatus.Invalid, result.status)
        assertTrue(BoundaryViolation.RepositoryInsideWorkspace in result.violations)
    }

    @Test
    fun `kit이 repository 내부면 위반이다`() {
        val result = policy.evaluate(
            kit = valid("/repo/kit"),
            workspace = valid("/workspace"),
            repository = valid("/repo"),
        )
        assertEquals(BoundaryStatus.Invalid, result.status)
        assertTrue(BoundaryViolation.KitInsideRepository in result.violations)
    }

    @Test
    fun `repository가 kit 내부면 위반이다`() {
        val result = policy.evaluate(
            kit = valid("/kit"),
            workspace = valid("/workspace"),
            repository = valid("/kit/repo"),
        )
        assertEquals(BoundaryStatus.Invalid, result.status)
        assertTrue(BoundaryViolation.RepositoryInsideKit in result.violations)
    }

    @Test
    fun `kit이 workspace 내부면 위반이다`() {
        val result = policy.evaluate(
            kit = valid("/workspace/kit"),
            workspace = valid("/workspace"),
            repository = valid("/repo"),
        )
        assertEquals(BoundaryStatus.Invalid, result.status)
        assertTrue(BoundaryViolation.KitInsideWorkspace in result.violations)
    }

    @Test
    fun `workspace가 kit 내부면 위반이다`() {
        val result = policy.evaluate(
            kit = valid("/kit"),
            workspace = valid("/kit/workspace"),
            repository = valid("/repo"),
        )
        assertEquals(BoundaryStatus.Invalid, result.status)
        assertTrue(BoundaryViolation.WorkspaceInsideKit in result.violations)
    }

    @Test
    fun `세 경로 중 둘이 동일 경로면 각각 다른 samePath 위반이다`() {
        val workspaceRepoSame = policy.evaluate(
            kit = valid("/kit"),
            workspace = valid("/shared"),
            repository = valid("/shared"),
        )
        assertEquals(listOf(BoundaryViolation.WorkspaceRepositorySamePath), workspaceRepoSame.violations)

        val kitRepoSame = policy.evaluate(
            kit = valid("/shared"),
            workspace = valid("/workspace"),
            repository = valid("/shared"),
        )
        assertEquals(listOf(BoundaryViolation.KitRepositorySamePath), kitRepoSame.violations)

        val kitWorkspaceSame = policy.evaluate(
            kit = valid("/shared"),
            workspace = valid("/shared"),
            repository = valid("/repo"),
        )
        assertEquals(listOf(BoundaryViolation.KitWorkspaceSamePath), kitWorkspaceSame.violations)
    }

    @Test
    fun `junction symlink로 lexical 경로는 다르지만 real path가 같으면 위반이다`() {
        // lexical로는 서로 다른 경로처럼 보이지만(예: junction), 실제 real path는 같은 위치를 가리킨다.
        val result = policy.evaluate(
            kit = valid("/kit"),
            workspace = valid("/workspace-junction", realPath = "/real/shared-target"),
            repository = valid("/repo-real-path", realPath = "/real/shared-target"),
        )
        assertEquals(BoundaryStatus.Invalid, result.status)
        assertTrue(BoundaryViolation.WorkspaceRepositorySamePath in result.violations)
    }

    @Test
    fun `junction으로 인한 real path 포함 관계도 감지한다`() {
        val result = policy.evaluate(
            kit = valid("/kit"),
            workspace = valid("/workspace-junction", realPath = "/real/repo-target/workspace"),
            repository = valid("/repo-real-path", realPath = "/real/repo-target"),
        )
        assertEquals(BoundaryStatus.Invalid, result.status)
        assertTrue(BoundaryViolation.WorkspaceInsideRepository in result.violations)
    }

    @Test
    fun `real path를 확인할 수 없으면 lexical 비교만으로 판정한다`() {
        val result = policy.evaluate(
            kit = valid("/kit", realPath = null),
            workspace = valid("/repo/workspace", realPath = null),
            repository = valid("/repo", realPath = null),
        )
        assertEquals(BoundaryStatus.Invalid, result.status)
        assertTrue(BoundaryViolation.WorkspaceInsideRepository in result.violations)
    }

    @Test
    fun `경로 하나라도 invalid하면 Invalid로 fail-closed한다`() {
        val notFound = policy.evaluate(
            kit = valid("/kit"),
            workspace = RootPathCheck.Invalid(PathIssue.NotFound),
            repository = valid("/repo"),
        )
        assertEquals(BoundaryStatus.Invalid, notFound.status)

        val blank = policy.evaluate(
            kit = valid("/kit"),
            workspace = RootPathCheck.Invalid(PathIssue.Blank),
            repository = valid("/repo"),
        )
        assertEquals(BoundaryStatus.Invalid, blank.status)

        val notDirectory = policy.evaluate(
            kit = valid("/kit"),
            workspace = RootPathCheck.Invalid(PathIssue.NotDirectory),
            repository = valid("/repo"),
        )
        assertEquals(BoundaryStatus.Invalid, notDirectory.status)

        val notReadable = policy.evaluate(
            kit = valid("/kit"),
            workspace = RootPathCheck.Invalid(PathIssue.NotReadable),
            repository = valid("/repo"),
        )
        assertEquals(BoundaryStatus.Invalid, notReadable.status)

        val invalidSyntax = policy.evaluate(
            kit = valid("/kit"),
            workspace = RootPathCheck.Invalid(PathIssue.InvalidSyntax),
            repository = valid("/repo"),
        )
        assertEquals(BoundaryStatus.Invalid, invalidSyntax.status)
    }

    @Test
    fun `세 root가 전부 구성되지 않았으면 Unknown이다`() {
        val result = policy.evaluate(
            kit = RootPathCheck.Invalid(PathIssue.NotProvided),
            workspace = RootPathCheck.Invalid(PathIssue.NotProvided),
            repository = RootPathCheck.Invalid(PathIssue.NotProvided),
        )
        assertEquals(BoundaryStatus.Unknown, result.status)
    }

    @Test
    fun `일부만 구성되지 않았으면 Unknown이 아니라 Invalid다`() {
        val result = policy.evaluate(
            kit = RootPathCheck.Invalid(PathIssue.NotProvided),
            workspace = valid("/workspace"),
            repository = valid("/repo"),
        )
        assertEquals(BoundaryStatus.Invalid, result.status)
    }
}
