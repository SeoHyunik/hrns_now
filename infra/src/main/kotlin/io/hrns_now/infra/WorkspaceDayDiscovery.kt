package io.hrns_now.infra

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * `projectWorkspaceRoot` 아래 `yyyy-MM-dd` 형식 디렉터리를 열거한다.
 *
 * [WorkspaceArtifactProbe]와 composeApp의 날짜 선택 조립(Phase 1C)이 동일한 탐색 로직을
 * 공유하기 위해 분리했다 — 두 곳에서 정규식/파싱 규칙이 갈라지면 "오늘 없으면 최신
 * 날짜로 read-only fallback" 정책이 화면마다 다르게 동작할 수 있다.
 */
class WorkspaceDayDiscovery {
    fun discover(projectWorkspaceRoot: Path): List<LocalDate> {
        if (!Files.isDirectory(projectWorkspaceRoot)) {
            return emptyList()
        }

        return try {
            Files.list(projectWorkspaceRoot).use { paths ->
                paths
                    .filter { path -> Files.isDirectory(path) }
                    .map { path -> parseDateDirectory(path.fileName.toString()) }
                    .filter { date -> date != null }
                    .map { date -> requireNotNull(date) }
                    .toList()
            }
        } catch (exception: IOException) {
            emptyList()
        } catch (exception: SecurityException) {
            emptyList()
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

    private companion object {
        val DATE_DIRECTORY_PATTERN = Regex("""\d{4}-\d{2}-\d{2}""")
    }
}
