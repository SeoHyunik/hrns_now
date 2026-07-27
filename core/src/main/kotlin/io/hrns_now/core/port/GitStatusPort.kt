package io.hrns_now.core.port

import io.hrns_now.core.domain.model.RepositoryStatus
import java.nio.file.Path

/**
 * `git status --short` 읽기 전용 계약이다. 구현체는 add/commit/reset/checkout/stash 등 어떤
 * git 쓰기 명령도 수행하지 않는다(`doc/claude_prompts/phase5-closure-recovery.md` §4).
 */
fun interface GitStatusPort {
    fun read(repositoryRoot: Path): RepositoryStatus
}
