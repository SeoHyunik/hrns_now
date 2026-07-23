package io.hrns_now.infra

import io.hrns_now.core.config.WorkspaceConfig
import io.hrns_now.core.domain.model.ArtifactKind
import io.hrns_now.core.domain.model.ArtifactProbeResult
import io.hrns_now.core.domain.model.ArtifactProbeState
import io.hrns_now.core.domain.model.ArtifactRequirement
import io.hrns_now.core.domain.model.WorkspaceArtifactSummary
import io.hrns_now.core.domain.model.WorkspaceDay
import io.hrns_now.core.domain.policy.WorkspaceDayPurpose
import io.hrns_now.core.domain.policy.WorkspaceDaySelectionPolicy
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class WorkspaceArtifactProbe(
    private val today: LocalDate = LocalDate.now(),
    private val daySelectionPolicy: WorkspaceDaySelectionPolicy = WorkspaceDaySelectionPolicy(today),
) {
    fun probe(config: WorkspaceConfig): WorkspaceArtifactSummary =
        probe(config.roots.workspaceRoot)

    fun probe(workspaceRoot: String?): WorkspaceArtifactSummary =
        probeSelected(workspaceRoot = workspaceRoot, explicitDate = null)

    fun probe(workspaceRoot: String?, date: LocalDate): WorkspaceArtifactSummary =
        probeSelected(workspaceRoot = workspaceRoot, explicitDate = date)

    private fun probeSelected(
        workspaceRoot: String?,
        explicitDate: LocalDate?,
    ): WorkspaceArtifactSummary {
        val fallbackDate = explicitDate ?: today
        val fallbackArtifacts = expectedArtifacts(fallbackDate)
        if (workspaceRoot.isNullOrBlank()) {
            return WorkspaceArtifactSummary(
                items = fallbackArtifacts.map { artifact ->
                    artifact.result(
                        state = ArtifactProbeState.WorkspaceNotConfigured,
                        message = "작업공간이 선택되지 않았습니다.",
                    )
                },
            )
        }

        val projectWorkspaceRoot = try {
            Paths.get(workspaceRoot.trim())
        } catch (exception: InvalidPathException) {
            return unknownForAll(fallbackArtifacts, "작업공간 경로 형식을 확인해야 합니다.")
        } catch (exception: RuntimeException) {
            return unknownForAll(fallbackArtifacts, "작업공간 경로를 확인해야 합니다.")
        }

        val workspaceDay = try {
            daySelectionPolicy.select(
                projectWorkspaceRoot = projectWorkspaceRoot,
                explicitDate = explicitDate,
                availableDates = if (explicitDate == null) {
                    discoverWorkspaceDates(projectWorkspaceRoot)
                } else {
                    emptyList()
                },
                purpose = WorkspaceDayPurpose.ReadOnly,
            ).workspaceDay
        } catch (exception: IOException) {
            return unknownForAll(fallbackArtifacts, "날짜 디렉터리를 읽을 수 없습니다.")
        } catch (exception: SecurityException) {
            return unknownForAll(fallbackArtifacts, "날짜 디렉터리 권한 확인이 필요합니다.")
        }
        val artifacts = expectedArtifacts(workspaceDay.date)

        return WorkspaceArtifactSummary(
            items = artifacts.map { artifact -> probeArtifact(workspaceDay, artifact) },
        )
    }

    private fun probeArtifact(workspaceDay: WorkspaceDay, artifact: ExpectedArtifact): ArtifactProbeResult =
        try {
            val path = artifact.resolve(workspaceDay)
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

    /**
     * harness-kit 현행 계약(2026-07-23 실측): daily 4-file은 `<workspaceRoot>\<yyyy-MM-dd>\` 아래에 있고,
     * `WORK_QUEUE.json`/`WORKDAY_STATE.json`은 명시적 `-ForceDualFileCompatibility` fallback에서만
     * 생성되는 legacy 산출물이다. day 산출물 로그와 wrapper 실행 로그는 각각
     * `<dayRoot>\logs\`, `<workspaceRoot>\logs\<yyyy-MM-dd>\`에 있다.
     */
    private fun expectedArtifacts(date: LocalDate): List<ExpectedArtifact> {
        val dateText = date.toString()
        return listOf(
            ExpectedArtifact(
                label = "요청 입력함",
                displayPath = "$dateText/REQUEST_INBOX.md",
                kind = ArtifactKind.File,
                requirement = ArtifactRequirement.Required,
                resolvePath = { it.dayRoot.resolve("REQUEST_INBOX.md") },
            ),
            ExpectedArtifact(
                label = "오늘 할 일 파일",
                displayPath = "$dateText/TODAY_STRATEGY.md",
                kind = ArtifactKind.File,
                requirement = ArtifactRequirement.Required,
                resolvePath = { it.dayRoot.resolve("TODAY_STRATEGY.md") },
            ),
            ExpectedArtifact(
                label = "인수인계 파일",
                displayPath = "$dateText/DAILY_HANDOFF.md",
                kind = ArtifactKind.File,
                requirement = ArtifactRequirement.Required,
                resolvePath = { it.dayRoot.resolve("DAILY_HANDOFF.md") },
            ),
            ExpectedArtifact(
                label = "작업 상태 파일",
                displayPath = "$dateText/WORKFLOW_STATE.json",
                kind = ArtifactKind.File,
                requirement = ArtifactRequirement.Required,
                resolvePath = { it.dayRoot.resolve("WORKFLOW_STATE.json") },
            ),
            ExpectedArtifact(
                label = "정리된 요청 파일",
                displayPath = "$dateText/REQUEST_STRUCTURED.md",
                kind = ArtifactKind.File,
                requirement = ArtifactRequirement.Optional,
                resolvePath = { it.dayRoot.resolve("REQUEST_STRUCTURED.md") },
            ),
            ExpectedArtifact(
                label = "날짜 산출물 로그",
                displayPath = "$dateText/logs/",
                kind = ArtifactKind.Directory,
                requirement = ArtifactRequirement.Optional,
                resolvePath = { it.dayLogsRoot },
            ),
            ExpectedArtifact(
                label = "래퍼 실행 로그",
                displayPath = "logs/$dateText/",
                kind = ArtifactKind.Directory,
                requirement = ArtifactRequirement.Optional,
                resolvePath = { it.wrapperLogsRoot },
            ),
            ExpectedArtifact(
                label = "레거시 오늘 상태 파일",
                displayPath = "$dateText/WORKDAY_STATE.json",
                kind = ArtifactKind.File,
                requirement = ArtifactRequirement.Legacy,
                resolvePath = { it.dayRoot.resolve("WORKDAY_STATE.json") },
            ),
            ExpectedArtifact(
                label = "레거시 작업 큐 파일",
                displayPath = "$dateText/WORK_QUEUE.json",
                kind = ArtifactKind.File,
                requirement = ArtifactRequirement.Legacy,
                resolvePath = { it.dayRoot.resolve("WORK_QUEUE.json") },
            ),
        )
    }

    private fun discoverWorkspaceDates(projectWorkspaceRoot: Path): List<LocalDate> {
        if (!Files.isDirectory(projectWorkspaceRoot)) {
            return emptyList()
        }

        return Files.list(projectWorkspaceRoot).use { paths ->
            paths
                .filter { path -> Files.isDirectory(path) }
                .map { path -> parseDateDirectory(path.fileName.toString()) }
                .filter { date -> date != null }
                .map { date -> requireNotNull(date) }
                .toList()
        }
    }

    private fun parseDateDirectory(name: String): LocalDate? {
        if (!DATE_DIRECTORY_PATTERN.matches(name)) {
            return null
        }

        return try {
            LocalDate.parse(name, DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (exception: DateTimeParseException) {
            null
        }
    }

    private data class ExpectedArtifact(
        val label: String,
        val displayPath: String,
        val kind: ArtifactKind,
        val requirement: ArtifactRequirement,
        val resolvePath: (WorkspaceDay) -> Path,
    ) {
        fun resolve(workspaceDay: WorkspaceDay): Path = resolvePath(workspaceDay)

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
                requirement = requirement,
                state = state,
                message = message,
            )
    }

    private companion object {
        val DATE_DIRECTORY_PATTERN = Regex("""\d{4}-\d{2}-\d{2}""")
    }
}
