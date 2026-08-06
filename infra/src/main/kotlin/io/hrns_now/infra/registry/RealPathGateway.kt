package io.hrns_now.infra.registry

import io.hrns_now.core.domain.model.PathIssue
import io.hrns_now.core.domain.model.RootPathCheck
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * root 경로 문자열 하나를 [RootPathCheck]로 검증하는 infra adapter다.
 *
 * `Files.exists`/`isDirectory`/`isReadable`/`toRealPath()` 등 실제 filesystem 호출은 전부
 * 여기서만 수행한다 — [io.hrns_now.core.domain.policy.BoundaryPolicy]는 이 결과만 소비하는
 * 순수 함수로 남는다. infra가 filesystem I/O를 전담하고 domain은 순수하게 유지하는 계층 분리
 * 원칙을 따른다.
 */
class RealPathGateway {
    fun resolve(raw: String?): RootPathCheck {
        if (raw == null) return RootPathCheck.Invalid(PathIssue.NotProvided)
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return RootPathCheck.Invalid(PathIssue.Blank)

        val normalized = try {
            Path.of(trimmed).toAbsolutePath().normalize()
        } catch (exception: InvalidPathException) {
            return RootPathCheck.Invalid(PathIssue.InvalidSyntax)
        } catch (exception: RuntimeException) {
            return RootPathCheck.Invalid(PathIssue.InvalidSyntax)
        }

        if (!Files.exists(normalized)) return RootPathCheck.Invalid(PathIssue.NotFound)
        if (!Files.isDirectory(normalized)) return RootPathCheck.Invalid(PathIssue.NotDirectory)
        if (!Files.isReadable(normalized)) return RootPathCheck.Invalid(PathIssue.NotReadable)

        val realPath = try {
            normalized.toRealPath()
        } catch (exception: IOException) {
            null
        } catch (exception: SecurityException) {
            null
        }
        return RootPathCheck.Valid(normalized = normalized, realPath = realPath)
    }
}
