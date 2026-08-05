package io.hrns_now.core.domain.policy

import io.hrns_now.core.domain.model.HarnessCompatibilityDetail
import io.hrns_now.core.domain.model.KitVersionManifest
import io.hrns_now.core.domain.model.KitVersionReadResult

/**
 * UI가 지원하는 `state_schema_version`/`ui_contract_version`의 major/minor 범위를 선언하고,
 * [KitVersionReadResult]로부터 [HarnessCompatibilityDetail]을 계산하는 순수 정책이다.
 * 파일·JSON·Compose를 참조하지 않는다.
 *
 * Kit은 이 판정에 관여하지 않는다 — `min_ui_version` 같은 역방향 필드는 존재하지 않고,
 * UI 쪽 이 정책이 스스로 지원 범위를 판단한다.
 */
class CompatibilityPolicy(
    private val supportedStateSchemaMajor: Int = 1,
    private val knownStateSchemaMinor: Int = 0,
    private val supportedUiContractMajor: Int = 1,
    private val knownUiContractMinor: Int = 0,
) {
    fun evaluate(readResult: KitVersionReadResult): HarnessCompatibilityDetail =
        when (readResult) {
            is KitVersionReadResult.Success -> evaluateManifest(readResult.manifest)
            KitVersionReadResult.Missing -> HarnessCompatibilityDetail.MissingManifest
            is KitVersionReadResult.Malformed -> HarnessCompatibilityDetail.MalformedManifest(readResult.reason)
        }

    private fun evaluateManifest(manifest: KitVersionManifest): HarnessCompatibilityDetail {
        val stateMajorSupported = manifest.stateSchemaVersion.major == supportedStateSchemaMajor
        val uiMajorSupported = manifest.uiContractVersion.major == supportedUiContractMajor
        if (!stateMajorSupported || !uiMajorSupported) {
            return HarnessCompatibilityDetail.UnsupportedMajorVersion(manifest)
        }

        val hasHigherMinor =
            manifest.stateSchemaVersion.minor > knownStateSchemaMinor ||
                manifest.uiContractVersion.minor > knownUiContractMinor

        return if (hasHigherMinor) {
            HarnessCompatibilityDetail.SupportedWithUnknownFields(manifest)
        } else {
            HarnessCompatibilityDetail.Supported(manifest)
        }
    }
}
