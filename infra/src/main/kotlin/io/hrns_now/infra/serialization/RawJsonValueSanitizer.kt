package io.hrns_now.infra.serialization

import io.hrns_now.core.domain.model.RawJsonValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Locale

/**
 * 상세 계약이 확정되지 않은 JSON이 domain 경계를 넘기 전에 민감 필드를 제거한다.
 *
 * Harness state에는 `role_sliced.stages[].session_id`가 실제로 존재할 수 있다. raw 값을
 * 그대로 [RawJsonValue]에 넣으면 이후 진단 화면이 무심코 session ID나 secret을 노출할
 * 수 있으므로, 알려지지 않은 중첩 구조에서도 민감한 이름의 필드를 재귀적으로 치환한다.
 */
internal class RawJsonValueSanitizer(
    private val json: Json = Json,
) {
    fun sanitize(element: JsonElement?): RawJsonValue? =
        element?.let { value ->
            RawJsonValue(json.encodeToString(JsonElement.serializer(), sanitizeElement(value)))
        }

    private fun sanitizeElement(element: JsonElement): JsonElement =
        when (element) {
            is JsonObject -> JsonObject(
                element.mapValues { (key, value) ->
                    if (key.isSensitive()) REDACTED else sanitizeElement(value)
                },
            )
            is JsonArray -> JsonArray(element.map(::sanitizeElement))
            else -> element
        }

    private fun String.isSensitive(): Boolean {
        val normalized = lowercase(Locale.ROOT).replace('-', '_')
        return normalized == "session_id" ||
            normalized.endsWith("_session_id") ||
            normalized == "token" ||
            normalized.endsWith("_token") ||
            normalized == "authorization" ||
            normalized == "api_key" ||
            normalized.endsWith("_api_key") ||
            normalized.contains("secret") ||
            normalized.contains("password") ||
            normalized.contains("private_key")
    }

    private companion object {
        val REDACTED: JsonPrimitive = JsonPrimitive("[REDACTED]")
    }
}
