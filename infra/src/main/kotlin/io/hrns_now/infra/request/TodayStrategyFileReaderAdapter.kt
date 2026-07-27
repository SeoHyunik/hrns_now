package io.hrns_now.infra.request

import io.hrns_now.core.domain.model.WorkspaceDay
import io.hrns_now.core.port.TodayStrategyReaderPort
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.NoSuchFileException

/** `TODAY_STRATEGY.md` 읽기 전용 [TodayStrategyReaderPort] 구현이다. 절대 쓰지 않는다. */
class TodayStrategyFileReaderAdapter : TodayStrategyReaderPort {

    override fun read(day: WorkspaceDay): String? {
        val path = day.dayRoot.resolve("TODAY_STRATEGY.md").toAbsolutePath().normalize()
        return try {
            decodeUtf8NoBom(Files.readAllBytes(path))
        } catch (_: NoSuchFileException) {
            null
        } catch (_: AccessDeniedException) {
            null
        } catch (_: SecurityException) {
            null
        } catch (_: IOException) {
            null
        }
    }
}
