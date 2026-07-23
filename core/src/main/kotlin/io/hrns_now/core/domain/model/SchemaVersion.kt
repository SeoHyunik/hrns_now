package io.hrns_now.core.domain.model

/**
 * `schema_version` 필드의 typed 표현이다. `major.minor` 형식만 지원하며,
 * 형식이 다르면 [parse]가 null을 반환해 호출자가 malformed로 처리하게 한다.
 *
 * 지원 major는 [SUPPORTED_SCHEMA_MAJOR](=1)이다. major가 다르면 Reader는
 * `UnsupportedSchema`로 반환해야 하며, 이 클래스 자체는 그 판단을 하지 않는다.
 * 상위 minor(예: 1.9)는 이 major 안에서 계속 지원 대상이다 — unknown key는
 * 파서가 무시하므로 minor가 올라가도 파싱이 깨지지 않는다.
 */
data class SchemaVersion(
    val major: Int,
    val minor: Int,
    val raw: String,
) {
    companion object {
        private val PATTERN = Regex("""^(\d+)\.(\d+)$""")

        fun parse(raw: String): SchemaVersion? {
            val match = PATTERN.matchEntire(raw.trim()) ?: return null
            val (majorText, minorText) = match.destructured
            val major = majorText.toIntOrNull() ?: return null
            val minor = minorText.toIntOrNull() ?: return null
            return SchemaVersion(major = major, minor = minor, raw = raw)
        }
    }
}

const val SUPPORTED_SCHEMA_MAJOR = 1
