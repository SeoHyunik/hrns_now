package io.hrns_now.infra.runtime

import io.hrns_now.core.domain.model.RuntimeIssue
import io.hrns_now.core.domain.model.RuntimeResolution
import io.hrns_now.core.domain.model.RuntimeSource
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * fake fixture/temp 디렉터리로만 검증한다 — 실제 Harness Kit 설치 경로나 실제 `.local\harness-kit`을
 * 복사·참조하지 않는다. 실제 Harness 실행도 하지 않는다.
 */
class DefaultKitRuntimeResolverTest {

    private val createdDirs = mutableListOf<Path>()

    private fun tempDir(prefix: String): Path =
        Files.createTempDirectory(prefix).also { createdDirs.add(it) }

    private fun writeRequiredEntrypoints(root: Path) {
        Files.createDirectories(root.resolve("scripts"))
        root.resolve("scripts/doctor.ps1").writeText("# fixture doctor")
        root.resolve("scripts/validate-ops.ps1").writeText("# fixture validate-ops")
        root.resolve("scripts/run-cycle.ps1").writeText("# fixture run-cycle")
        root.resolve("kit-version.json").writeText("""{"kit_version":"fixture","state_schema_version":"1.0","ui_contract_version":"1.0"}""")
    }

    @AfterTest
    fun cleanup() {
        createdDirs.forEach { dir -> dir.toFile().deleteRecursively() }
    }

    @Test
    fun `root가 존재하지 않으면 Missing이다`() {
        val root = tempDir("hrns-sdk").resolve("does-not-exist")
        val resolver = DefaultKitRuntimeResolver { root }

        val result = resolver.resolve(RuntimeSource.DefaultKit)

        assertIs<RuntimeResolution.Missing>(result)
        assertEquals(RuntimeSource.DefaultKit, result.source)
    }

    @Test
    fun `root가 디렉터리가 아니면 Invalid NotDirectory다`() {
        val file = tempDir("hrns-sdk").resolve("harness-kit-but-a-file")
        Files.writeString(file, "not a directory")
        val resolver = DefaultKitRuntimeResolver { file }

        val result = resolver.resolve(RuntimeSource.DefaultKit)

        val invalid = assertIs<RuntimeResolution.Invalid>(result)
        assertEquals(RuntimeIssue.NotDirectory, invalid.reason)
    }

    @Test
    fun `필요한 entrypoint가 하나라도 없으면 Invalid MissingEntrypoint다`() {
        val root = tempDir("hrns-sdk")
        Files.createDirectories(root.resolve("scripts"))
        root.resolve("scripts/doctor.ps1").writeText("# only doctor, missing others")
        val resolver = DefaultKitRuntimeResolver { root }

        val result = resolver.resolve(RuntimeSource.DefaultKit)

        val invalid = assertIs<RuntimeResolution.Invalid>(result)
        assertEquals(RuntimeIssue.MissingEntrypoint, invalid.reason)
    }

    @Test
    fun `필요한 entrypoint가 모두 있으면 Resolved다`() {
        val root = tempDir("hrns-sdk")
        writeRequiredEntrypoints(root)
        val resolver = DefaultKitRuntimeResolver { root }

        val result = resolver.resolve(RuntimeSource.DefaultKit)

        val resolved = assertIs<RuntimeResolution.Resolved>(result)
        assertEquals(root.toAbsolutePath().normalize(), resolved.root)
    }

    @Test
    fun `한글과 공백이 포함된 경로도 정상적으로 해석한다`() {
        val root = tempDir("hrns-sdk-kr").resolve("한글 폴더 이름 테스트")
        Files.createDirectories(root)
        writeRequiredEntrypoints(root)
        val resolver = DefaultKitRuntimeResolver { root }

        val result = resolver.resolve(RuntimeSource.DefaultKit)

        val resolved = assertIs<RuntimeResolution.Resolved>(result)
        assertEquals(root.toAbsolutePath().normalize(), resolved.root)
    }

    @Test
    fun `ExternalKit은 defaultKitRootProvider를 쓰지 않고 주어진 경로를 그대로 확인한다`() {
        val internalRoot = tempDir("hrns-sdk-internal")
        writeRequiredEntrypoints(internalRoot)
        val externalRoot = tempDir("hrns-sdk-external")
        // internalRoot만 유효하고 externalRoot는 비워 둔다 — resolver가 실제로 externalRoot를
        // 검사하는지(내부 provider로 몰래 대체하지 않는지) 확인한다.
        val resolver = DefaultKitRuntimeResolver { internalRoot }

        val result = resolver.resolve(RuntimeSource.ExternalKit(externalRoot))

        val invalid = assertIs<RuntimeResolution.Invalid>(result)
        assertEquals(RuntimeIssue.MissingEntrypoint, invalid.reason)
        assertEquals(RuntimeSource.ExternalKit(externalRoot), invalid.source)
    }

    @Test
    fun `ExternalKit이 유효하면 Resolved root는 그 경로 자신이다`() {
        val externalRoot = tempDir("hrns-sdk-external-valid")
        writeRequiredEntrypoints(externalRoot)
        val resolver = DefaultKitRuntimeResolver { error("DefaultKit를 요청하면 안 된다") }

        val result = resolver.resolve(RuntimeSource.ExternalKit(externalRoot))

        val resolved = assertIs<RuntimeResolution.Resolved>(result)
        assertEquals(externalRoot.toAbsolutePath().normalize(), resolved.root)
    }

    @Test
    fun `source checkout 하위 working directory에서도 HRNS NOW root 상대 SDK를 찾는다`() {
        val root = tempDir("hrns-source-root")
        Files.writeString(root.resolve("settings.gradle.kts"), "// fixture")
        Files.writeString(root.resolve("gradlew.bat"), "@rem fixture")
        Files.createDirectories(root.resolve("core"))
        Files.writeString(root.resolve("core/build.gradle.kts"), "// fixture")
        Files.createDirectories(root.resolve("composeApp"))
        Files.writeString(root.resolve("composeApp/build.gradle.kts"), "// fixture")
        val nestedWorkingDirectory = root.resolve("composeApp/build/compose/binaries/main-release/app")
        Files.createDirectories(nestedWorkingDirectory)

        assertEquals(
            root.resolve(".local").resolve("harness-kit"),
            DefaultKitRuntimeResolver.findHrnsNowSourceRoot(nestedWorkingDirectory)
                ?.resolve(".local")
                ?.resolve("harness-kit"),
        )
    }

    @Test
    fun `다른 Git 또는 일반 working directory는 HRNS NOW source root로 추측하지 않는다`() {
        val root = tempDir("unrelated-working-directory")
        Files.createDirectories(root.resolve(".git"))

        assertNull(DefaultKitRuntimeResolver.findHrnsNowSourceRoot(root))
    }
}
