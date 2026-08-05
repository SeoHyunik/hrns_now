package io.hrns_now.core.domain.policy

import io.hrns_now.core.domain.model.ContractVersion
import io.hrns_now.core.domain.model.HarnessCompatibilityDetail
import io.hrns_now.core.domain.model.KitVersion
import io.hrns_now.core.domain.model.KitVersionManifest
import io.hrns_now.core.domain.model.KitVersionReadResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * `CompatibilityPolicy`의 결정표를 표 기반으로 검증한다.
 * supported major → 정상, 상위 minor + 같은 major → tolerate, 미지원 major → 원인 표시,
 * manifest 없음 → legacy/unknown 잠금, malformed → fail-closed.
 */
class CompatibilityPolicyTest {

    private val policy = CompatibilityPolicy(
        supportedStateSchemaMajor = 1,
        knownStateSchemaMinor = 0,
        supportedUiContractMajor = 1,
        knownUiContractMinor = 0,
    )

    private fun manifest(stateSchema: String, uiContract: String, kitVersion: String = "2026.07.23") =
        KitVersionManifest(
            kitVersion = KitVersion(kitVersion),
            stateSchemaVersion = requireNotNull(ContractVersion.parse(stateSchema)),
            uiContractVersion = requireNotNull(ContractVersion.parse(uiContract)),
        )

    @Test
    fun `동일 major 동일 minor는 Supported다`() {
        val result = policy.evaluate(KitVersionReadResult.Success(manifest("1.0", "1.0")))

        assertIs<HarnessCompatibilityDetail.Supported>(result)
    }

    @Test
    fun `동일 major 상위 minor는 unknown field를 허용하는 SupportedWithUnknownFields다`() {
        val stateHigherMinor = policy.evaluate(KitVersionReadResult.Success(manifest("1.3", "1.0")))
        val uiHigherMinor = policy.evaluate(KitVersionReadResult.Success(manifest("1.0", "1.7")))

        assertIs<HarnessCompatibilityDetail.SupportedWithUnknownFields>(stateHigherMinor)
        assertIs<HarnessCompatibilityDetail.SupportedWithUnknownFields>(uiHigherMinor)
    }

    @Test
    fun `state_schema_version 미지원 major는 원인을 담아 UnsupportedMajorVersion이다`() {
        val result = policy.evaluate(KitVersionReadResult.Success(manifest("2.0", "1.0")))

        val unsupported = assertIs<HarnessCompatibilityDetail.UnsupportedMajorVersion>(result)
        assertEquals(2, unsupported.manifest.stateSchemaVersion.major)
    }

    @Test
    fun `ui_contract_version 미지원 major는 원인을 담아 UnsupportedMajorVersion이다`() {
        val result = policy.evaluate(KitVersionReadResult.Success(manifest("1.0", "3.0")))

        val unsupported = assertIs<HarnessCompatibilityDetail.UnsupportedMajorVersion>(result)
        assertEquals(3, unsupported.manifest.uiContractVersion.major)
    }

    @Test
    fun `양쪽 모두 미지원 major여도 UnsupportedMajorVersion 하나로 판정한다`() {
        val result = policy.evaluate(KitVersionReadResult.Success(manifest("9.0", "9.0")))

        assertIs<HarnessCompatibilityDetail.UnsupportedMajorVersion>(result)
    }

    @Test
    fun `manifest 없음은 legacy unknown으로 취급해 MissingManifest다`() {
        val result = policy.evaluate(KitVersionReadResult.Missing)

        assertEquals(HarnessCompatibilityDetail.MissingManifest, result)
    }

    @Test
    fun `malformed read는 사유를 보존한 채 fail-closed MalformedManifest다`() {
        val result = policy.evaluate(KitVersionReadResult.Malformed("missing_field:kit_version"))

        val malformed = assertIs<HarnessCompatibilityDetail.MalformedManifest>(result)
        assertEquals("missing_field:kit_version", malformed.reason)
    }
}
