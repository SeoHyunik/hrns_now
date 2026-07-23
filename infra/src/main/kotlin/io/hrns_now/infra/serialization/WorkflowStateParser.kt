package io.hrns_now.infra.serialization

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** [WorkflowStateParser.parse] 결과. JSON 구조 파싱 단계만 책임진다 — 도메인 필수 필드 검증은 Mapper의 몫이다. */
internal sealed interface ParseResult {
    data class Success(val dto: HarnessWorkflowStateDto) : ParseResult
    data class Failure(val message: String) : ParseResult
}

/** 이미 디코딩된 텍스트를 받아 [HarnessWorkflowStateDto]로 파싱한다. 바이트/인코딩 처리는 하지 않는다. */
internal class WorkflowStateParser(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun parse(text: String): ParseResult =
        try {
            ParseResult.Success(json.decodeFromString(HarnessWorkflowStateDto.serializer(), text))
        } catch (exception: SerializationException) {
            ParseResult.Failure(exception.message ?: "JSON parse error")
        } catch (exception: IllegalArgumentException) {
            ParseResult.Failure(exception.message ?: "JSON parse error")
        }
}
