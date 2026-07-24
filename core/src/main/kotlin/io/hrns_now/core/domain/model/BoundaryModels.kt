package io.hrns_now.core.domain.model

import java.nio.file.Path

/**
 * root 경로 하나가 boundary 검사를 시작하기 전에 거쳐야 하는 유효성 문제다.
 * 파일 실재·real path 확인은 infra adapter/gateway(`infra.registry.RealPathGateway`)가 담당하고,
 * 이 타입은 그 결과를 표현만 한다.
 */
enum class PathIssue {
    /** 필드 자체가 구성되지 않음(null) — 예: 환경변수 fallback에서 kit root가 아예 없는 경우. */
    NotProvided,
    Blank,
    InvalidSyntax,
    NotFound,
    NotDirectory,
    NotReadable,
}

/**
 * 하나의 root 경로에 대한 유효성 확인 결과다. [Valid.realPath]는 junction/symlink 우회를
 * 차단하기 위해 `toRealPath()`가 성공한 경우에만 채워진다 — 실패하면 lexical 비교만 수행한다.
 */
sealed interface RootPathCheck {
    data class Valid(val normalized: Path, val realPath: Path?) : RootPathCheck
    data class Invalid(val issue: PathIssue) : RootPathCheck
}

/**
 * `doc/hrns_now_claude_plan.md` §2.4의 경계 원칙 — 세 root 쌍의 양방향 포함 관계 6종 +
 * 동일 경로 3종이다. lexical normalized 경로와(가능하면) real path 양쪽에서 검사한다.
 */
enum class BoundaryViolation {
    WorkspaceInsideRepository,
    RepositoryInsideWorkspace,
    KitInsideRepository,
    RepositoryInsideKit,
    KitInsideWorkspace,
    WorkspaceInsideKit,
    WorkspaceRepositorySamePath,
    KitRepositorySamePath,
    KitWorkspaceSamePath,
}

/**
 * [io.hrns_now.core.domain.policy.BoundaryPolicy]의 결과다. [status]는 [BoundaryStatus.Valid]를
 * 이 실제 평가에서만 만든다 — 호출자가 임의 상수로 대체하지 않는다.
 *
 * 세 root가 전부 [PathIssue.NotProvided]면(=프로젝트 자체가 아직 구성되지 않음) [BoundaryStatus.Unknown]이다.
 * 일부만 제공되었거나 경로 자체가 유효하지 않으면(존재하지 않음/디렉터리 아님/읽기 불가 등)
 * 경계를 판단할 수 없으므로 [BoundaryStatus.Invalid]로 fail-closed한다.
 */
data class ProjectBoundaryResult(
    val kit: RootPathCheck,
    val workspace: RootPathCheck,
    val repository: RootPathCheck,
    val violations: List<BoundaryViolation>,
) {
    val status: BoundaryStatus
        get() {
            val kitIssue = (kit as? RootPathCheck.Invalid)?.issue
            val workspaceIssue = (workspace as? RootPathCheck.Invalid)?.issue
            val repositoryIssue = (repository as? RootPathCheck.Invalid)?.issue
            val allNotProvided = kitIssue == PathIssue.NotProvided &&
                workspaceIssue == PathIssue.NotProvided &&
                repositoryIssue == PathIssue.NotProvided
            return when {
                allNotProvided -> BoundaryStatus.Unknown
                kitIssue != null || workspaceIssue != null || repositoryIssue != null -> BoundaryStatus.Invalid
                violations.isNotEmpty() -> BoundaryStatus.Invalid
                else -> BoundaryStatus.Valid
            }
        }
}
