package io.hrns_now.core.domain.model

import java.nio.file.Path

/**
 * 프로젝트의 Harness runtime을 어디서 가져오는지에 대한 typed 선택이다
 * (`doc/hrns_now_design_pattern.md` §20.1, 새 Phase 7). Registry는 이 선택만 저장한다 —
 * [InternalDeveloperSdk]의 실제 절대 경로는 저장하지 않고, [ExternalKit.root]만 사용자가
 * 명시적으로 등록한 값이다.
 */
sealed interface RuntimeSource {
    /**
     * HRNS-NOW source checkout 상대 경로 `.local/harness-kit`에 있는, 사용자가 제공하는
     * Git-ignore 개발용 SDK checkout이다. 배포 artifact가 아니며 MSI/release distributable에
     * 포함되지 않는다.
     */
    data object InternalDeveloperSdk : RuntimeSource

    /** 사용자가 고급 설정에서 명시적으로 선택한 외부 Harness Kit 경로다. */
    data class ExternalKit(val root: Path) : RuntimeSource
}

/**
 * [RuntimeSource]가 가리키는 root가 실제 Kit처럼 보이지 않을 때의 원인이다. root 자체가 없으면
 * [RuntimeResolution.Missing]으로 구분하고, 이 enum은 root는 있으나 유효하지 않은 경우에만 쓰인다.
 */
enum class RuntimeIssue {
    NotDirectory,
    NotReadable,
    MissingEntrypoint,
}

/**
 * [RuntimeSource]를 실제 파일 시스템 root로 해석한 결과다(`doc/hrns_now_design_pattern.md` §20.1).
 * compatibility(`KitVersionManifestPort`/`CompatibilityPolicy`)와는 별개의 fail-closed 원인이다 —
 * [Resolved]가 아니면 compatibility 판정 자체를 시도하지 않는다. command encoder/boundary
 * validation은 오직 [Resolved.root]만 전달받는다.
 */
sealed interface RuntimeResolution {
    val source: RuntimeSource

    data class Resolved(override val source: RuntimeSource, val root: Path) : RuntimeResolution
    data class Missing(override val source: RuntimeSource) : RuntimeResolution
    data class Invalid(override val source: RuntimeSource, val reason: RuntimeIssue) : RuntimeResolution
}
