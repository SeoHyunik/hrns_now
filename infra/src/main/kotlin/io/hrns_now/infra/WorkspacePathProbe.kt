package io.hrns_now.infra

import io.hrns_now.core.config.PathProbeKind
import io.hrns_now.core.config.PathProbeResult
import io.hrns_now.core.config.PathProbeState
import io.hrns_now.core.config.WorkspaceConfig
import io.hrns_now.core.config.WorkspaceProbeSummary
import io.hrns_now.core.config.WorkspaceReadiness
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths

class WorkspacePathProbe {
    fun probe(config: WorkspaceConfig): WorkspaceProbeSummary =
        WorkspaceProbeSummary(
            kitRoot = probeDirectory("KitRoot", config.roots.kitRoot),
            workspaceRoot = probeDirectory("WorkspaceRoot", config.roots.workspaceRoot),
            projectRoot = probeDirectory("ProjectRoot", config.roots.projectRoot),
            powerShellPath = probeFileOrCommand("PowerShell 경로", config.runtime.powerShellPath),
            claudeCommand = probeFileOrCommand("Claude 명령", config.runtime.claudeCommand),
        )

    /**
     * `engineLabel`은 다른 root와 동일하게 실제 kit root 경로 확인 결과를 반영한다 — Kit이
     * 실제로 등록·해석돼도 리본이 고정된 "오프라인"으로 모순되게 보이지 않도록 한다.
     * `doctorLabel`은 이 계층에 실제 Doctor 실행 이력이 없으므로(그 정보는
     * `RunStatusProjection`이 별도로 갖는다) 거짓 "대기/실행 중" 인상을 주지 않는 중립 문구로
     * 고정한다 — 실제 실행 결과와의 통합은 별도 과제로 남긴다.
     */
    fun readiness(config: WorkspaceConfig, summary: WorkspaceProbeSummary): WorkspaceReadiness =
        WorkspaceReadiness(
            engineLabel = rootReadiness(
                result = summary.kitRoot,
                notConfiguredLabel = "미설정",
            ),
            workspaceLabel = rootReadiness(
                result = summary.workspaceRoot,
                notConfiguredLabel = "미선택",
            ),
            bridgeLabel = rootReadiness(
                result = summary.projectRoot,
                notConfiguredLabel = "미확인",
            ),
            profileLabel = config.profileName,
            doctorLabel = "미확인",
        )

    private fun rootReadiness(result: PathProbeResult, notConfiguredLabel: String): String =
        when (result.state) {
            PathProbeState.Exists -> "확인됨"
            PathProbeState.NotConfigured -> notConfiguredLabel
            else -> "확인 필요"
        }

    private fun probeDirectory(label: String, rawPath: String?): PathProbeResult =
        probePath(
            label = label,
            rawPath = rawPath,
            kind = PathProbeKind.Directory,
            expectedTypeName = "디렉터리",
            typeMatches = { path -> Files.isDirectory(path) },
        )

    private fun probeFileOrCommand(label: String, rawPath: String?): PathProbeResult {
        if (rawPath.isNullOrBlank()) {
            return PathProbeResult(
                label = label,
                rawPath = rawPath,
                kind = PathProbeKind.Command,
                state = PathProbeState.NotConfigured,
                message = "미설정",
            )
        }

        val normalized = rawPath.trim()
        if (!looksLikePath(normalized)) {
            return PathProbeResult(
                label = label,
                rawPath = normalized,
                kind = PathProbeKind.Command,
                state = PathProbeState.Unknown,
                message = "명령 경로는 이후 상태 점검에서 확인합니다.",
            )
        }

        return probePath(
            label = label,
            rawPath = normalized,
            kind = PathProbeKind.File,
            expectedTypeName = "파일",
            typeMatches = { path -> Files.isRegularFile(path) },
        )
    }

    private fun probePath(
        label: String,
        rawPath: String?,
        kind: PathProbeKind,
        expectedTypeName: String,
        typeMatches: (Path) -> Boolean,
    ): PathProbeResult {
        if (rawPath.isNullOrBlank()) {
            return PathProbeResult(
                label = label,
                rawPath = rawPath,
                kind = kind,
                state = PathProbeState.NotConfigured,
                message = "미설정",
            )
        }

        val normalized = rawPath.trim()
        return try {
            val path = Paths.get(normalized)
            when {
                !Files.exists(path) ->
                    result(label, normalized, kind, PathProbeState.Missing, "경로가 없습니다.")

                !typeMatches(path) ->
                    result(label, normalized, kind, PathProbeState.WrongType, "$expectedTypeName 유형이 아닙니다.")

                !Files.isReadable(path) ->
                    result(label, normalized, kind, PathProbeState.NotReadable, "읽을 수 없습니다.")

                else ->
                    result(label, normalized, kind, PathProbeState.Exists, "읽기 가능")
            }
        } catch (exception: InvalidPathException) {
            result(label, normalized, kind, PathProbeState.Unknown, "경로 형식을 확인해야 합니다.")
        } catch (exception: SecurityException) {
            result(label, normalized, kind, PathProbeState.Unknown, "권한 확인이 필요합니다.")
        } catch (exception: RuntimeException) {
            result(label, normalized, kind, PathProbeState.Unknown, "경로 상태를 확인해야 합니다.")
        }
    }

    private fun result(
        label: String,
        rawPath: String,
        kind: PathProbeKind,
        state: PathProbeState,
        message: String,
    ): PathProbeResult =
        PathProbeResult(
            label = label,
            rawPath = rawPath,
            kind = kind,
            state = state,
            message = message,
        )

    private fun looksLikePath(value: String): Boolean =
        value.contains('/') ||
            value.contains('\\') ||
            value.startsWith(".") ||
            value.startsWith("~") ||
            value.matches(Regex("^[A-Za-z]:.*"))
}
