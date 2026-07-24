package io.hrns_now.infra

import io.hrns_now.core.domain.model.WorkspaceDay
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.FileTime

/**
 * `WORKFLOW_STATE.json`의 마지막 수정 시각만 저렴하게 확인하는 polling 전용 협력자다.
 *
 * [io.hrns_now.core.port.WorkflowStatePort.read]는 매 호출마다 전체 읽기·디코딩·파싱·매핑을
 * 수행하므로, 2~5초 polling에서 파일이 바뀌지 않았는데도 매번 호출하면 불필요한 부하가 생긴다.
 * 이 protocol은 `Files.getLastModifiedTime`만 호출해 "다시 읽어야 하는가"를 판단하는 데 쓰이고,
 * `WorkflowStatePort` 계약 자체는 바꾸지 않는다.
 */
class WorkflowStateChangeProbe {
    fun lastModifiedOrNull(day: WorkspaceDay): FileTime? =
        try {
            Files.getLastModifiedTime(day.dayRoot.resolve("WORKFLOW_STATE.json"))
        } catch (exception: IOException) {
            null
        } catch (exception: SecurityException) {
            null
        }
}
