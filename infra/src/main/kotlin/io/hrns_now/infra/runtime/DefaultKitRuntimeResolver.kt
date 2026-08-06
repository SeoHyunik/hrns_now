package io.hrns_now.infra.runtime

import io.hrns_now.core.domain.model.RuntimeIssue
import io.hrns_now.core.domain.model.RuntimeResolution
import io.hrns_now.core.domain.model.RuntimeSource
import io.hrns_now.core.port.RuntimeSourceResolverPort
import java.nio.file.Files
import java.nio.file.Path

/**
 * [RuntimeSource]를 실제 root로 안전하게 해석하는 [RuntimeSourceResolverPort] 구현이다.
 *
 * `DefaultKit`는 HRNS-NOW source checkout 상대 `.local/harness-kit`을 가리킨다 —
 * [defaultKitRootProvider]는 기본으로 `user.dir`에서 시작해 HRNS-NOW source checkout 표지를
 * 상위로 탐색한 뒤 repository-relative 경로를 계산하며, 이 디렉터리를 생성·복사·수정하지 않는다.
 * 패키지된 설치본처럼 source checkout을 찾지 못하면 root를 추측하지 않고 `Missing`으로
 * fail-closed된다 — 이는 의도된 동작이다(개발 전용 편의, 배포 Runtime이 아님).
 *
 * `ExternalKit`은 사용자가 등록한 경로를 그대로 검사한다. 두 경우 모두 존재·디렉터리·읽기 가능
 * 여부와 공개 entrypoint(`doctor.ps1`/`validate-ops.ps1`/`run-cycle.ps1`)·`kit-version.json`의
 * **존재**만 확인한다 — 내용을 읽거나 파싱하지 않는다. 실제 manifest 파싱/호환성 판정은
 * [io.hrns_now.core.port.KitVersionManifestPort]/[io.hrns_now.core.domain.policy.CompatibilityPolicy]가
 * `Resolved.root`를 받은 뒤 별도로 수행한다.
 */
class DefaultKitRuntimeResolver(
    private val defaultKitRootProvider: () -> Path? = ::defaultKitRoot,
) : RuntimeSourceResolverPort {

    override fun resolve(source: RuntimeSource): RuntimeResolution {
        val root = when (source) {
            RuntimeSource.DefaultKit ->
                defaultKitRootProvider() ?: return RuntimeResolution.Missing(source)
            is RuntimeSource.ExternalKit -> source.root
        }

        if (!Files.exists(root)) {
            return RuntimeResolution.Missing(source)
        }
        if (!Files.isDirectory(root)) {
            return RuntimeResolution.Invalid(source, RuntimeIssue.NotDirectory)
        }
        if (!Files.isReadable(root)) {
            return RuntimeResolution.Invalid(source, RuntimeIssue.NotReadable)
        }
        val hasAllRequiredFiles = REQUIRED_ENTRYPOINTS.all { name -> Files.isRegularFile(root.resolve(name)) }
        if (!hasAllRequiredFiles) {
            return RuntimeResolution.Invalid(source, RuntimeIssue.MissingEntrypoint)
        }

        return RuntimeResolution.Resolved(source, root.toAbsolutePath().normalize())
    }

    companion object {
        /**
         * Kit root 아래 `scripts/doctor.ps1`, `scripts/validate-ops.ps1`, `scripts/run-cycle.ps1`,
         * `kit-version.json`의 **존재**만 확인한다 — [io.hrns_now.infra.process.HarnessCommandEncoder]가
         * 실제 실행 시점에 이 경로들을 다시 해석하므로, 여기서는 "이 root가 Kit처럼 보이는가"만
         * 판단한다. 두 위치가 실제로 다르면 `MissingEntrypoint`가 아니라 이후 실행 단계에서 typed
         * process 실패로 드러난다.
         */
        private val REQUIRED_ENTRYPOINTS = listOf(
            "scripts/doctor.ps1",
            "scripts/validate-ops.ps1",
            "scripts/run-cycle.ps1",
            "kit-version.json",
        )

        /**
         * `user.dir` 자체를 checkout root로 가정하지 않는다. Gradle/IDE/direct distributable
         * 실행은 서로 다른 working directory를 줄 수 있으므로, 현재 위치와 상위 경로에서
         * HRNS-NOW source checkout 표지를 찾아야 한다. 설치본처럼 표지를 찾지 못하면 Kit root를
         * 추측하지 않고 [RuntimeResolution.Missing]으로 fail-closed된다.
         */
        internal fun defaultKitRoot(): Path? =
            findHrnsNowSourceRoot(Path.of(System.getProperty("user.dir")))
                ?.resolve(".local")
                ?.resolve("harness-kit")

        /** 테스트 가능한 source checkout 탐색이다. 단순 `.git`만으로는 상위의 다른 저장소를
         * HRNS-NOW로 오인할 수 있으므로 Gradle root와 HRNS-NOW 모듈 표지를 함께 확인한다. */
        internal fun findHrnsNowSourceRoot(workingDirectory: Path): Path? =
            generateSequence(workingDirectory.toAbsolutePath().normalize()) { it.parent }
                .firstOrNull(::isHrnsNowSourceRoot)

        private fun isHrnsNowSourceRoot(candidate: Path): Boolean =
            Files.isRegularFile(candidate.resolve("settings.gradle.kts")) &&
                Files.isRegularFile(candidate.resolve("gradlew.bat")) &&
                Files.isRegularFile(candidate.resolve("core").resolve("build.gradle.kts")) &&
                Files.isRegularFile(candidate.resolve("composeApp").resolve("build.gradle.kts"))
    }
}
