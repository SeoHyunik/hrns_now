package io.hrns_now.core.port

import io.hrns_now.core.domain.model.KitVersionReadResult
import java.nio.file.Path

/**
 * Kit root의 `kit-version.json` manifest를 읽는 의미만 표현하는 port다
 * (`doc/claude_prompts/phase2-harness-json-contract.md` §B.2). Kit root 밖의 파일을 탐색하거나
 * 쓰지 않는다. [io.hrns_now.core.port.WorkflowStatePort]와 같은 이유로 `suspend`가 아니다 —
 * 파일 I/O를 포함하는 순수 조회 함수이며, 호출자가 IO dispatcher 안에서 호출해야 한다.
 */
fun interface KitVersionManifestPort {
    fun readManifest(kitRoot: Path): KitVersionReadResult
}
