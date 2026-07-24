package io.hrns_now.infra.registry

import io.hrns_now.core.domain.model.HarnessProject
import io.hrns_now.core.domain.model.ProjectId
import io.hrns_now.core.result.RegistryLoadResult
import io.hrns_now.core.result.RegistrySaveResult
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsonProjectRegistryAdapterTest {

    private fun tempRegistryPath(): Path {
        val dir = Files.createTempDirectory("hrns-registry-test")
        return dir.resolve("projects.json")
    }

    private fun project(
        id: String,
        displayName: String = "테스트 프로젝트 $id",
        workspaceRoot: Path = Path.of("S:\\workspace-$id"),
    ): HarnessProject = HarnessProject(
        id = ProjectId(id),
        displayName = displayName,
        kitRoot = Path.of("S:\\kit-$id"),
        projectWorkspaceRoot = workspaceRoot,
        repositoryRoot = Path.of("S:\\repo-$id"),
        profileId = "기본",
        lastSelectedDate = LocalDate.of(2026, 6, 26),
        lastDiagnosticsSummary = "ok",
        lastRunAt = Instant.parse("2026-06-26T12:00:00Z"),
    )

    @Test
    fun `findAll findById save delete round-trip이 여러 프로젝트에서 동작한다`() = runTest {
        val adapter = JsonProjectRegistryAdapter(tempRegistryPath())
        val a = project("a")
        val b = project("b")

        assertEquals(RegistrySaveResult.Success, adapter.save(a))
        assertEquals(RegistrySaveResult.Success, adapter.save(b))

        val all = assertIs<RegistryLoadResult.Success>(adapter.findAll())
        assertEquals(setOf(a, b), all.projects.toSet())
        assertEquals(a, adapter.findById(a.id))
        assertNull(adapter.findById(ProjectId("missing")))

        adapter.delete(a.id)
        val afterDelete = assertIs<RegistryLoadResult.Success>(adapter.findAll())
        assertEquals(listOf(b), afterDelete.projects)
    }

    @Test
    fun `한글 공백 drive-letter 경로를 UTF-8 no BOM으로 저장하고 다시 읽는다`() = runTest {
        val path = tempRegistryPath()
        val adapter = JsonProjectRegistryAdapter(path)
        val koreanProject = project(
            id = "kr",
            displayName = "한글 표시명 테스트",
            workspaceRoot = Path.of("D:\\작업 공간 폴더\\워크스페이스"),
        )

        adapter.save(koreanProject)

        val bytes = Files.readAllBytes(path)
        val hasBom = bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        assertFalse(hasBom)
        val text = String(bytes, StandardCharsets.UTF_8)
        assertTrue(text.contains("한글 표시명 테스트"))
        assertTrue(text.contains("작업 공간 폴더"))

        val loaded = assertIs<RegistryLoadResult.Success>(adapter.findAll())
        assertEquals(koreanProject, loaded.projects.single())
    }

    @Test
    fun `쓰기가 성공하면 atomic move로 교체되고 임시 파일이 남지 않는다`() = runTest {
        val path = tempRegistryPath()
        val adapter = JsonProjectRegistryAdapter(path)

        adapter.save(project("a"))

        val siblingFiles = Files.list(path.parent).use { it.toList() }
        assertEquals(listOf(path), siblingFiles)
    }

    @Test
    fun `최종 move가 실패하면 같은 대상의 기존 정상 파일을 보존하고 temp를 정리한다`() = runTest {
        val path = tempRegistryPath()
        JsonProjectRegistryAdapter(path).save(project("a"))
        val beforeBytes = Files.readAllBytes(path)
        val failingAdapter = JsonProjectRegistryAdapter(
            registryPath = path,
            moveIntoPlace = { _, _ -> throw IOException("move failed") },
        )

        val result = failingAdapter.save(project("b"))

        assertIs<RegistrySaveResult.Failed>(result)
        assertEquals(beforeBytes.toList(), Files.readAllBytes(path).toList())
        assertEquals(listOf(path), Files.list(path.parent).use { it.toList() })
    }

    @Test
    fun `전체 JSON이 손상되면 원본을 격리 복사하고 typed 오류로 알린다`() = runTest {
        val path = tempRegistryPath()
        Files.writeString(path, "{ not valid json ][")

        val adapter = JsonProjectRegistryAdapter(path)
        val result = adapter.findAll()

        val recovered = assertIs<RegistryLoadResult.RecoveredFromCorruption>(result)
        assertTrue(recovered.projects.isEmpty())
        assertTrue(Files.exists(recovered.quarantinePath))
        assertTrue(Files.readString(recovered.quarantinePath).contains("not valid json"))
        assertTrue(Files.exists(path))
    }

    @Test
    fun `일부 project entry만 손상되면 유효한 entry는 살리고 손상분만 제외한다`() = runTest {
        val path = tempRegistryPath()
        Files.writeString(
            path,
            """
            {
              "schema_version": "1.0",
              "last_active_project_id": null,
              "projects": [
                {"id": "valid-1", "display_name": "정상", "kit_root": "S:\\k", "project_workspace_root": "S:\\w", "repository_root": "S:\\r", "profile_id": "기본"},
                {"id": "broken-1", "display_name": "필드 누락"}
              ]
            }
            """.trimIndent(),
        )

        val adapter = JsonProjectRegistryAdapter(path)
        val result = adapter.findAll()

        val recovered = assertIs<RegistryLoadResult.RecoveredFromCorruption>(result)
        assertEquals(1, recovered.projects.size)
        assertEquals("valid-1", recovered.projects.single().id.value)
        assertTrue(Files.exists(recovered.quarantinePath))
    }

    @Test
    fun `알려지지 않은 필드는 허용하고 필수 필드 누락은 거부한다`() = runTest {
        val path = tempRegistryPath()
        Files.writeString(
            path,
            """
            {
              "schema_version": "1.0",
              "unknown_top_level_field": "ignored",
              "projects": [
                {"id": "a", "display_name": "정상", "kit_root": "S:\\k", "project_workspace_root": "S:\\w", "repository_root": "S:\\r", "profile_id": "기본", "unknown_project_field": 123}
              ]
            }
            """.trimIndent(),
        )

        val adapter = JsonProjectRegistryAdapter(path)
        val result = assertIs<RegistryLoadResult.Success>(adapter.findAll())
        assertEquals("a", result.projects.single().id.value)
    }

    @Test
    fun `secret token session id는 DTO 구조에 없으므로 저장된 파일에도 없다`() = runTest {
        val path = tempRegistryPath()
        val adapter = JsonProjectRegistryAdapter(path)
        adapter.save(project("a"))

        val text = Files.readString(path)
        listOf("session_id", "token", "secret", "password", "api_key").forEach { forbidden ->
            assertFalse(text.contains(forbidden, ignoreCase = true), "registry must not contain '$forbidden'")
        }
    }

    @Test
    fun `markActive는 존재하는 프로젝트만 활성화하고 findAll에 반영된다`() = runTest {
        val path = tempRegistryPath()
        val adapter = JsonProjectRegistryAdapter(path)
        val a = project("a")
        adapter.save(a)

        assertEquals(RegistrySaveResult.Success, adapter.markActive(a.id))
        val loaded = assertIs<RegistryLoadResult.Success>(adapter.findAll())
        assertEquals(a.id, loaded.lastActiveProjectId)

        assertIs<RegistrySaveResult.Failed>(adapter.markActive(ProjectId("missing")))
    }

    @Test
    fun `동시 save가 서로의 프로젝트를 잃지 않는다`() = runTest {
        val path = tempRegistryPath()
        val adapter = JsonProjectRegistryAdapter(path)
        val projects = (1..20).map { project("p$it") }

        coroutineScope {
            projects.forEach { p ->
                launch(Dispatchers.Default) { adapter.save(p) }
            }
        }

        val loaded = assertIs<RegistryLoadResult.Success>(adapter.findAll())
        assertEquals(projects.toSet(), loaded.projects.toSet())
    }
    @Test
    fun `손상 조회는 원본을 한 번 격리하고 유효 정본으로 복구한다`() = runTest {
        val path = tempRegistryPath()
        Files.writeString(path, "{ truncated")
        val adapter = JsonProjectRegistryAdapter(path, clock = { Instant.EPOCH })

        val first = assertIs<RegistryLoadResult.RecoveredFromCorruption>(adapter.findAll())
        val second = assertIs<RegistryLoadResult.Success>(adapter.findAll())

        assertTrue(Files.readString(first.quarantinePath).contains("truncated"))
        assertTrue(second.projects.isEmpty())
        assertEquals(1L, Files.list(path.parent).use { files -> files.filter { it.fileName.toString().contains(".corrupt-") }.count() })
    }

    @Test
    fun `부분 손상 Registry mutation은 제외된 entry 원본을 먼저 격리한다`() = runTest {
        val path = tempRegistryPath()
        Files.writeString(
            path,
            """
            {
              "schema_version": "1.0",
              "projects": [
                {"id":"valid","display_name":"정상","kit_root":"S:\\k","project_workspace_root":"S:\\w","repository_root":"S:\\r","profile_id":"기본"},
                {"id":"broken","display_name":"누락"}
              ]
            }
            """.trimIndent(),
        )
        val adapter = JsonProjectRegistryAdapter(path, clock = { Instant.EPOCH })

        assertEquals(RegistrySaveResult.Success, adapter.save(project("new")))

        val quarantine = Files.list(path.parent).use { files ->
            files.filter { it.fileName.toString().contains(".corrupt-") }.findFirst().orElseThrow()
        }
        assertTrue(Files.readString(quarantine).contains("\"id\":\"broken\""))
        val loaded = assertIs<RegistryLoadResult.Success>(adapter.findAll())
        assertEquals(setOf("valid", "new"), loaded.projects.map { it.id.value }.toSet())
    }

    @Test
    fun `Registry 경로가 프로젝트 root 아래면 temp와 정본을 만들지 않는다`() = runTest {
        val workspace = Files.createDirectories(Files.createTempDirectory("hrns-boundary").resolve("workspace"))
        val path = workspace.resolve("appdata/hrns-now/projects.json")
        val adapter = JsonProjectRegistryAdapter(path)
        val unsafeProject = project("unsafe", workspaceRoot = workspace)

        val result = adapter.save(unsafeProject)

        assertIs<RegistrySaveResult.Failed>(result)
        assertFalse(Files.exists(path))
        assertFalse(Files.exists(path.parent))
    }

    @Test
    fun `지원하지 않는 schema와 잘못된 optional 날짜는 손상으로 분류한다`() = runTest {
        val schemaPath = tempRegistryPath()
        Files.writeString(schemaPath, """{"schema_version":"2.0","projects":[]}""")
        assertIs<RegistryLoadResult.RecoveredFromCorruption>(JsonProjectRegistryAdapter(schemaPath).findAll())

        val datePath = tempRegistryPath()
        Files.writeString(
            datePath,
            """
            {
              "schema_version":"1.0",
              "projects":[
                {"id":"a","display_name":"정상","kit_root":"S:\\k","project_workspace_root":"S:\\w","repository_root":"S:\\r","profile_id":"기본","last_selected_date":"not-a-date"}
              ]
            }
            """.trimIndent(),
        )
        val recovered = assertIs<RegistryLoadResult.RecoveredFromCorruption>(
            JsonProjectRegistryAdapter(datePath).findAll(),
        )
        assertTrue(recovered.projects.isEmpty())
    }
    @Test
    fun `기존 등록 project root 아래에 놓인 Registry도 후속 mutation을 차단한다`() = runTest {
        val workspace = Files.createDirectories(Files.createTempDirectory("hrns-existing-boundary").resolve("workspace"))
        val path = workspace.resolve("appdata/hrns-now/projects.json")
        Files.createDirectories(path.parent)
        val existing = project("existing", workspaceRoot = workspace)
        val envelope = ProjectRegistryFileDto(
            schemaVersion = REGISTRY_SCHEMA_VERSION,
            lastActiveProjectId = existing.id.value,
            projects = listOf(existing.toDto()),
        )
        Files.writeString(path, Json.encodeToString(ProjectRegistryFileDto.serializer(), envelope))
        val before = Files.readAllBytes(path)
        val adapter = JsonProjectRegistryAdapter(path)

        assertIs<RegistryLoadResult.Unreadable>(adapter.findAll())
        val result = adapter.save(project("new"))

        assertIs<RegistrySaveResult.Failed>(result)
        assertEquals(before.toList(), Files.readAllBytes(path).toList())
        assertEquals(listOf(path), Files.list(path.parent).use { it.toList() })
    }
}
