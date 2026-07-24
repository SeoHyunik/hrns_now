package io.hrns_now.core.domain.policy

import io.hrns_now.core.domain.model.BoundaryViolation
import io.hrns_now.core.domain.model.ProjectBoundaryResult
import io.hrns_now.core.domain.model.RootPathCheck

/**
 * Kit/workspace/repository root 세 경로의 경계를 순수하게 판정한다
 * (`doc/hrns_now_design_pattern.md` §11, `doc/hrns_now_claude_plan.md` §2.4).
 *
 * 파일 시스템을 전혀 참조하지 않는다 — 입력은 이미 infra의
 * `RealPathGateway`가 만든 [RootPathCheck]다. Windows는 파일시스템 자체가
 * 대소문자를 구분하지 않으므로, JDK의 기본 `WindowsPath` 비교(`Path.equals`/`Path.startsWith`)가
 * 이미 대소문자 무관 비교를 수행한다는 점에 기대어 별도 정규화를 하지 않는다
 * (이 프로젝트는 Windows 전용 MVP다).
 */
class BoundaryPolicy {

    fun evaluate(
        kit: RootPathCheck,
        workspace: RootPathCheck,
        repository: RootPathCheck,
    ): ProjectBoundaryResult {
        val violations = buildList {
            addAll(
                pairViolations(
                    a = workspace,
                    b = repository,
                    aInsideB = BoundaryViolation.WorkspaceInsideRepository,
                    bInsideA = BoundaryViolation.RepositoryInsideWorkspace,
                    samePath = BoundaryViolation.WorkspaceRepositorySamePath,
                ),
            )
            addAll(
                pairViolations(
                    a = kit,
                    b = repository,
                    aInsideB = BoundaryViolation.KitInsideRepository,
                    bInsideA = BoundaryViolation.RepositoryInsideKit,
                    samePath = BoundaryViolation.KitRepositorySamePath,
                ),
            )
            addAll(
                pairViolations(
                    a = kit,
                    b = workspace,
                    aInsideB = BoundaryViolation.KitInsideWorkspace,
                    bInsideA = BoundaryViolation.WorkspaceInsideKit,
                    samePath = BoundaryViolation.KitWorkspaceSamePath,
                ),
            )
        }
        return ProjectBoundaryResult(kit = kit, workspace = workspace, repository = repository, violations = violations)
    }

    private fun pairViolations(
        a: RootPathCheck,
        b: RootPathCheck,
        aInsideB: BoundaryViolation,
        bInsideA: BoundaryViolation,
        samePath: BoundaryViolation,
    ): List<BoundaryViolation> {
        if (a !is RootPathCheck.Valid || b !is RootPathCheck.Valid) return emptyList()
        if (isSamePath(a, b)) return listOf(samePath)
        return buildList {
            if (isInside(child = a, parent = b)) add(aInsideB)
            if (isInside(child = b, parent = a)) add(bInsideA)
        }
    }

    private fun isSamePath(a: RootPathCheck.Valid, b: RootPathCheck.Valid): Boolean =
        a.normalized == b.normalized || (a.realPath != null && a.realPath == b.realPath)

    /**
     * lexical 정규화 경로와(둘 다 존재하면) real path 양쪽에서 포함 관계를 검사한다 —
     * junction/symlink가 lexical 경로만 다르게 보이게 만드는 우회를 real path 비교로 막는다.
     */
    private fun isInside(child: RootPathCheck.Valid, parent: RootPathCheck.Valid): Boolean {
        val lexicalInside = child.normalized != parent.normalized && child.normalized.startsWith(parent.normalized)
        val realInside = child.realPath != null && parent.realPath != null &&
            child.realPath != parent.realPath && child.realPath.startsWith(parent.realPath)
        return lexicalInside || realInside
    }
}
