package io.hrns_now.core.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KitVersionTest {

    @Test
    fun `ContractVersion parse는 major와 minor 정수를 분리한다`() {
        val parsed = ContractVersion.parse("1.0")

        assertEquals(ContractVersion(1, 0, "1.0"), parsed)
    }

    @Test
    fun `ContractVersion parse는 두 자리 이상의 minor도 허용한다`() {
        val parsed = ContractVersion.parse("2.13")

        assertEquals(ContractVersion(2, 13, "2.13"), parsed)
    }

    @Test
    fun `ContractVersion parse는 앞뒤 공백을 허용한다`() {
        val parsed = ContractVersion.parse("  1.0  ")

        assertEquals(ContractVersion(1, 0, "1.0"), parsed)
    }

    @Test
    fun `ContractVersion parse는 major minor 형식이 아니면 null이다`() {
        assertNull(ContractVersion.parse("1"))
        assertNull(ContractVersion.parse("1.0.0"))
        assertNull(ContractVersion.parse("v1.0"))
        assertNull(ContractVersion.parse(""))
        assertNull(ContractVersion.parse("1.a"))
        assertNull(ContractVersion.parse("-1.0"))
    }

    private val sampleManifest = KitVersionManifest(
        kitVersion = KitVersion("2026.07.23"),
        stateSchemaVersion = ContractVersion(1, 0, "1.0"),
        uiContractVersion = ContractVersion(1, 0, "1.0"),
    )

    @Test
    fun `Supported와 SupportedWithUnknownFields는 CompatibilityStatus Supported로 축약된다`() {
        assertEquals(
            CompatibilityStatus.Supported,
            HarnessCompatibilityDetail.Supported(sampleManifest).toCompatibilityStatus(),
        )
        assertEquals(
            CompatibilityStatus.Supported,
            HarnessCompatibilityDetail.SupportedWithUnknownFields(sampleManifest).toCompatibilityStatus(),
        )
    }

    @Test
    fun `미지원 major 누락 malformed는 모두 CompatibilityStatus Unsupported로 축약된다`() {
        assertEquals(
            CompatibilityStatus.Unsupported,
            HarnessCompatibilityDetail.UnsupportedMajorVersion(sampleManifest).toCompatibilityStatus(),
        )
        assertEquals(
            CompatibilityStatus.Unsupported,
            HarnessCompatibilityDetail.MissingManifest.toCompatibilityStatus(),
        )
        assertEquals(
            CompatibilityStatus.Unsupported,
            HarnessCompatibilityDetail.MalformedManifest("invalid_json").toCompatibilityStatus(),
        )
    }
}
