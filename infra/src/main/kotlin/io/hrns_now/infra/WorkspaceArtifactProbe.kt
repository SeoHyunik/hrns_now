package io.hrns_now.infra

import io.hrns_now.core.artifact.ArtifactKind
import io.hrns_now.core.artifact.ArtifactProbeResult
import io.hrns_now.core.artifact.ArtifactProbeState
import io.hrns_now.core.artifact.WorkspaceArtifactSummary
import io.hrns_now.core.config.WorkspaceConfig
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate

class WorkspaceArtifactProbe(
    private val today: LocalDate = LocalDate.now()
) {
    fun probe(config: WorkspaceConfig): WorkspaceArtifactSummary =
        probe(config.roots.workspaceRoot)

    fun probe(workspaceRoot: String?): WorkspaceArtifactSummary {
        val artifacts = expectedArtifacts()
        if (workspaceRoot.isNullOrBlank()) {
            return WorkspaceArtifactSummary(
                items = artifacts.map { artifact ->
                    artifact.result(
                        state = ArtifactProbeState.WorkspaceNotConfigured,
                        message = "작업공간이 선택되지 않았습니다.",
                    )
                },
            )
        }

        val root = try {
            Paths.get(workspaceRoot.trim())
        } catch (exception: InvalidPathException) {
            return unknownForAll(artifacts, "작업공간 경로 형식을 확인해야 합니다.")
        } catch (exception: RuntimeException) {
            return unknownForAll(artifacts, "작업공간 경로를 확인해야 합니다.")
        }

        return WorkspaceArtifactSummary(
            items = artifacts.map { artifact -> probeArtifact(root, artifact) },
        )
    }

    private fun probeArtifact(root: Path, artifact: ExpectedArtifact): ArtifactProbeResult =
        try {
            val path = artifact.resolve(root)
            when {
                !Files.exists(path) ->
                    artifact.result(ArtifactProbeState.Missing, "경로가 없습니다.")

                !artifact.matchesKind(path) ->
                    artifact.result(ArtifactProbeState.WrongType, artifact.wrongTypeMessage())

                !Files.isReadable(path) ->
                    artifact.result(ArtifactProbeState.NotReadable, "읽을 수 없습니다.")

                else ->
                    artifact.result(ArtifactProbeState.Exists, "읽기 가능")
            }
        } catch (exception: SecurityException) {
            artifact.result(ArtifactProbeState.Unknown, "권한 확인이 필요합니다.")
        } catch (exception: RuntimeException) {
            artifact.result(ArtifactProbeState.Unknown, "기준 파일 상태를 확인해야 합니다.")
        }

    private fun unknownForAll(
        artifacts: List<ExpectedArtifact>,
        message: String,
    ): WorkspaceArtifactSummary =
        WorkspaceArtifactSummary(
            items = artifacts.map { artifact ->
                artifact.result(ArtifactProbeState.Unknown, message)
            },
        )

    private fun expectedArtifacts(): List<ExpectedArtifact> {
        val todayText = today.toString()
        return listOf(
            ExpectedArtifact("오늘 상태 파일", "WORKDAY_STATE.json", ArtifactKind.File),
            ExpectedArtifact("요청 입력함", "REQUEST_INBOX.md", ArtifactKind.File),
            ExpectedArtifact("정리된 요청 파일", "REQUEST_STRUCTURED.md", ArtifactKind.File),
            ExpectedArtifact("오늘 할 일 파일", "TODAY_STRATEGY.md", ArtifactKind.File),
            ExpectedArtifact("인수인계 파일", "DAILY_HANDOFF.md", ArtifactKind.File),
            ExpectedArtifact("오늘 실행 로그", "logs/$todayText/", ArtifactKind.Directory, listOf("logs", todayText)),
        )
    }

    private data class ExpectedArtifact(
        val label: String,
        val displayPath: String,
        val kind: ArtifactKind,
        val pathParts: List<String> = listOf(displayPath),
    ) {
        fun resolve(root: Path): Path =
            pathParts.fold(root) { current, part -> current.resolve(part) }

        fun matchesKind(path: Path): Boolean =
            when (kind) {
                ArtifactKind.File -> Files.isRegularFile(path)
                ArtifactKind.Directory -> Files.isDirectory(path)
            }

        fun wrongTypeMessage(): String =
            when (kind) {
                ArtifactKind.File -> "파일 유형이 아닙니다."
                ArtifactKind.Directory -> "디렉터리 유형이 아닙니다."
            }

        fun result(
            state: ArtifactProbeState,
            message: String,
        ): ArtifactProbeResult =
            ArtifactProbeResult(
                label = label,
                path = displayPath,
                kind = kind,
                state = state,
                message = message,
            )
    }
}
