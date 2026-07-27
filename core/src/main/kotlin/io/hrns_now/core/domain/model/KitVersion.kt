package io.hrns_now.core.domain.model

/**
 * Kit root의 `kit-version.json`에 담긴 자유 형식 빌드 식별자다. 호환성 판정에는 쓰이지
 * 않는 순수 표시용 값이며, `major.minor` 같은 의미를 갖지 않는다(현재 값은 CalVer 형태다).
 */
@JvmInline
value class KitVersion(val raw: String)

/**
 * `<major>.<minor>` 형태의 계약 버전이다. `state_schema_version`/`ui_contract_version` 모두
 * 이 형태를 쓴다. Kit은 이 값으로 UI 구현 버전을 지시하지 않는다 — UI 쪽
 * [io.hrns_now.core.domain.policy.CompatibilityPolicy]가 이 값을 보고 자신의 지원 범위를
 * 스스로 판정한다.
 */
data class ContractVersion(val major: Int, val minor: Int, val raw: String) {
    companion object {
        private val PATTERN = Regex("""^(\d+)\.(\d+)$""")

        /** `major`/`minor`가 음이 아닌 정수인 `"<major>.<minor>"` 형식이 아니면 `null`이다. */
        fun parse(raw: String): ContractVersion? {
            val trimmed = raw.trim()
            val match = PATTERN.matchEntire(trimmed) ?: return null
            val major = match.groupValues[1].toIntOrNull() ?: return null
            val minor = match.groupValues[2].toIntOrNull() ?: return null
            return ContractVersion(major, minor, trimmed)
        }
    }
}

/** `kit-version.json`의 세 필수 field를 있는 그대로 표현한 typed 정본이다. */
data class KitVersionManifest(
    val kitVersion: KitVersion,
    val stateSchemaVersion: ContractVersion,
    val uiContractVersion: ContractVersion,
)

/**
 * Kit root manifest 읽기 결과다(Anti-Corruption Layer 경계). 파일 존재 여부·JSON 파싱·필수
 * field 형식까지만 표현한다 — "이 UI가 이 버전을 지원하는지"는 여기서 판단하지 않고
 * [io.hrns_now.core.domain.policy.CompatibilityPolicy]가 별도로 계산한다.
 */
sealed interface KitVersionReadResult {
    data class Success(val manifest: KitVersionManifest) : KitVersionReadResult
    data object Missing : KitVersionReadResult

    /**
     * `reason`은 항상 어댑터가 만든 안전한 진단 분류값이다(예: 누락된 field 이름, 버전 형식
     * 오류) — 원본 예외 메시지나 임의 JSON 원문을 그대로 담지 않는다.
     */
    data class Malformed(val reason: String) : KitVersionReadResult
}

/**
 * [io.hrns_now.core.domain.policy.CompatibilityPolicy]가 [KitVersionReadResult]로부터 계산한
 * 순수 판정 결과다. 모든 variant는 이미 typed된 버전 값만 담으며, raw 파일 원문이나 예외
 * 메시지를 담지 않는다.
 */
sealed interface HarnessCompatibilityDetail {
    data class Supported(val manifest: KitVersionManifest) : HarnessCompatibilityDetail
    data class SupportedWithUnknownFields(val manifest: KitVersionManifest) : HarnessCompatibilityDetail
    data class UnsupportedMajorVersion(val manifest: KitVersionManifest) : HarnessCompatibilityDetail
    data object MissingManifest : HarnessCompatibilityDetail
    data class MalformedManifest(val reason: String) : HarnessCompatibilityDetail
}

/**
 * [ActionContext]/[io.hrns_now.core.domain.policy.ActionPolicy]가 소비하는 3값 게이트로
 * 축약한다. `Supported`/`SupportedWithUnknownFields`만 실행을 허용하고 나머지는 모두
 * write/execute CTA를 잠근다(`ActionPolicy`가 이미 `!= Supported`를 fail-closed로 처리한다).
 */
fun HarnessCompatibilityDetail.toCompatibilityStatus(): CompatibilityStatus =
    when (this) {
        is HarnessCompatibilityDetail.Supported,
        is HarnessCompatibilityDetail.SupportedWithUnknownFields,
        -> CompatibilityStatus.Supported

        is HarnessCompatibilityDetail.UnsupportedMajorVersion,
        HarnessCompatibilityDetail.MissingManifest,
        is HarnessCompatibilityDetail.MalformedManifest,
        -> CompatibilityStatus.Unsupported
    }
