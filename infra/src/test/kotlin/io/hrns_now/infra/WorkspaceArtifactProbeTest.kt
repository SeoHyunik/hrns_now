package io.hrns_now.infra

import io.hrns_now.core.domain.model.ArtifactKind
import io.hrns_now.core.domain.model.ArtifactProbeResult
import io.hrns_now.core.domain.model.ArtifactProbeState
import io.hrns_now.core.domain.model.ArtifactRequirement
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkspaceArtifactProbeTest {

    private lateinit var tempRoot: Path
    private val date = LocalDate.of(2026, 7, 23)
    private val dateText = date.toString()

    @BeforeTest
    fun setUp() {
        tempRoot = Files.createTempDirectory("hrns-now-artifact-probe-")
    }

    @AfterTest
    fun tearDown() {
        tempRoot.toFile().deleteRecursively()
    }

    private fun writeDayFile(root: Path, name: String, content: String = "") {
        val dayRoot = root.resolve(dateText)
        Files.createDirectories(dayRoot)
        Files.writeString(dayRoot.resolve(name), content)
    }

    private fun itemByLabel(items: List<ArtifactProbeResult>, label: String) =
        items.single { it.label == label }

    @Test
    fun `날짜 폴더의 4-file을 정확히 탐지한다`() {
        writeDayFile(tempRoot, "REQUEST_INBOX.md")
        writeDayFile(tempRoot, "TODAY_STRATEGY.md")
        writeDayFile(tempRoot, "DAILY_HANDOFF.md")
        writeDayFile(tempRoot, "WORKFLOW_STATE.json")

        val summary = WorkspaceArtifactProbe().probe(tempRoot.toString(), date)

        assertTrue(summary.isRequiredReady)
        assertEquals(4, summary.requiredItems.size)
        summary.requiredItems.forEach { assertEquals(ArtifactProbeState.Exists, it.state) }
    }

    @Test
    fun `optional 파일 누락은 readiness 실패가 아니다`() {
        writeDayFile(tempRoot, "REQUEST_INBOX.md")
        writeDayFile(tempRoot, "TODAY_STRATEGY.md")
        writeDayFile(tempRoot, "DAILY_HANDOFF.md")
        writeDayFile(tempRoot, "WORKFLOW_STATE.json")
        // REQUEST_STRUCTURED.md와 두 로그 디렉터리는 의도적으로 생성하지 않음

        val summary = WorkspaceArtifactProbe().probe(tempRoot.toString(), date)

        assertTrue(summary.isRequiredReady)
        val optionalRequest = itemByLabel(summary.items, "정리된 요청 파일")
        assertEquals(ArtifactProbeState.Missing, optionalRequest.state)
        assertEquals(ArtifactRequirement.Optional, optionalRequest.requirement)
        val optionalLogs = summary.items.filter {
            it.requirement == ArtifactRequirement.Optional && it.kind == ArtifactKind.Directory
        }
        assertEquals(2, optionalLogs.size)
        optionalLogs.forEach { assertEquals(ArtifactProbeState.Missing, it.state) }
    }

    @Test
    fun `day 산출물 로그와 wrapper 실행 로그를 서로 다른 위치에서 탐지한다`() {
        writeDayFile(tempRoot, "REQUEST_INBOX.md")
        writeDayFile(tempRoot, "TODAY_STRATEGY.md")
        writeDayFile(tempRoot, "DAILY_HANDOFF.md")
        writeDayFile(tempRoot, "WORKFLOW_STATE.json")
        Files.createDirectories(tempRoot.resolve(dateText).resolve("logs"))
        Files.createDirectories(tempRoot.resolve("logs").resolve(dateText))

        val summary = WorkspaceArtifactProbe().probe(tempRoot.toString(), date)

        val dayLogs = itemByLabel(summary.items, "날짜 산출물 로그")
        assertEquals("$dateText/logs/", dayLogs.path)
        assertEquals(ArtifactProbeState.Exists, dayLogs.state)
        assertEquals(ArtifactRequirement.Optional, dayLogs.requirement)

        val wrapperLogs = itemByLabel(summary.items, "래퍼 실행 로그")
        assertEquals("logs/$dateText/", wrapperLogs.path)
        assertEquals(ArtifactProbeState.Exists, wrapperLogs.state)
        assertEquals(ArtifactRequirement.Optional, wrapperLogs.requirement)
        assertTrue(summary.isRequiredReady)
    }

    @Test
    fun `오늘 폴더가 없으면 읽기 전용 조회는 잘못된 이름과 파일을 무시하고 최신 날짜를 선택한다`() {
        val latestDate = LocalDate.of(2026, 7, 22)
        val latestRoot = tempRoot.resolve(latestDate.toString())
        Files.createDirectories(latestRoot)
        listOf(
            "REQUEST_INBOX.md",
            "TODAY_STRATEGY.md",
            "DAILY_HANDOFF.md",
            "WORKFLOW_STATE.json",
        ).forEach { name -> Files.writeString(latestRoot.resolve(name), "") }

        Files.createDirectories(tempRoot.resolve("2026-99-99"))
        Files.createDirectories(tempRoot.resolve("not-a-date"))
        Files.writeString(tempRoot.resolve("2026-07-21"), "date-shaped file")

        val summary = WorkspaceArtifactProbe(today = date).probe(tempRoot.toString())

        assertTrue(summary.isRequiredReady)
        summary.requiredItems.forEach { item ->
            assertTrue(item.path.startsWith("${latestDate}/"))
        }
    }

    @Test
    fun `legacy 파일 존재 여부는 정상 lane readiness에 영향 없다`() {
        writeDayFile(tempRoot, "REQUEST_INBOX.md")
        writeDayFile(tempRoot, "TODAY_STRATEGY.md")
        writeDayFile(tempRoot, "DAILY_HANDOFF.md")
        writeDayFile(tempRoot, "WORKFLOW_STATE.json")
        // legacy fallback 파일들도 함께 존재하는 경우
        writeDayFile(tempRoot, "WORKDAY_STATE.json")
        writeDayFile(tempRoot, "WORK_QUEUE.json")

        val summary = WorkspaceArtifactProbe().probe(tempRoot.toString(), date)

        assertTrue(summary.isRequiredReady)
        val legacyItems = summary.items.filter { it.requirement == ArtifactRequirement.Legacy }
        assertEquals(2, legacyItems.size)
        legacyItems.forEach { assertEquals(ArtifactProbeState.Exists, it.state) }
    }

    @Test
    fun `legacy 파일이 없어도 정상 lane readiness에 영향 없다`() {
        writeDayFile(tempRoot, "REQUEST_INBOX.md")
        writeDayFile(tempRoot, "TODAY_STRATEGY.md")
        writeDayFile(tempRoot, "DAILY_HANDOFF.md")
        writeDayFile(tempRoot, "WORKFLOW_STATE.json")
        // legacy 파일은 생성하지 않음 (harness 기본 lane과 동일한 상태)

        val summary = WorkspaceArtifactProbe().probe(tempRoot.toString(), date)

        assertTrue(summary.isRequiredReady)
        val legacyItems = summary.items.filter { it.requirement == ArtifactRequirement.Legacy }
        legacyItems.forEach { assertEquals(ArtifactProbeState.Missing, it.state) }
    }

    @Test
    fun `workspace root 직하에 동일 파일이 있어도 날짜 폴더 파일만 사용한다`() {
        // 잘못된 legacy 위치: workspaceRoot 바로 아래
        Files.writeString(tempRoot.resolve("REQUEST_INBOX.md"), "")
        Files.writeString(tempRoot.resolve("TODAY_STRATEGY.md"), "")
        Files.writeString(tempRoot.resolve("DAILY_HANDOFF.md"), "")
        Files.writeString(tempRoot.resolve("WORKFLOW_STATE.json"), "")
        // 날짜 폴더에는 아무것도 생성하지 않음

        val summary = WorkspaceArtifactProbe().probe(tempRoot.toString(), date)

        assertTrue(!summary.isRequiredReady)
        summary.requiredItems.forEach { assertEquals(ArtifactProbeState.Missing, it.state) }
    }

    @Test
    fun `날짜 폴더 자리에 디렉터리가 아닌 파일이 있으면 안전하게 Missing으로 처리한다`() {
        // dayRoot 경로 자체가 파일로 존재하는 손상 상태.
        // dayRoot가 파일이면 그 하위 경로는 존재할 수 없으므로 각 항목은 Missing으로 보고된다.
        // (WrongType이 아니라 Missing이지만, 두 경우 모두 required readiness는 fail-closed로 false다.)
        Files.writeString(tempRoot.resolve(dateText), "not a directory")

        val summary = WorkspaceArtifactProbe().probe(tempRoot.toString(), date)

        assertTrue(!summary.isRequiredReady)
        summary.requiredItems.forEach { assertEquals(ArtifactProbeState.Missing, it.state) }
    }

    @Test
    fun `기준 파일 자리에 디렉터리가 있으면 WrongType으로 처리한다`() {
        val dayRoot = tempRoot.resolve(dateText)
        Files.createDirectories(dayRoot)
        Files.createDirectories(dayRoot.resolve("REQUEST_INBOX.md"))
        writeDayFile(tempRoot, "TODAY_STRATEGY.md")
        writeDayFile(tempRoot, "DAILY_HANDOFF.md")
        writeDayFile(tempRoot, "WORKFLOW_STATE.json")

        val summary = WorkspaceArtifactProbe().probe(tempRoot.toString(), date)

        val requestInbox = itemByLabel(summary.items, "요청 입력함")
        assertEquals(ArtifactProbeState.WrongType, requestInbox.state)
    }

    @Test
    fun `공백과 한글이 포함된 workspace 경로를 정상 처리한다`() {
        val nestedRoot = tempRoot.resolve("한글 작업 공간 폴더")
        writeDayFile(nestedRoot, "REQUEST_INBOX.md")
        writeDayFile(nestedRoot, "TODAY_STRATEGY.md")
        writeDayFile(nestedRoot, "DAILY_HANDOFF.md")
        writeDayFile(nestedRoot, "WORKFLOW_STATE.json")

        val summary = WorkspaceArtifactProbe().probe(nestedRoot.toString(), date)

        assertTrue(summary.isRequiredReady)
    }

    @Test
    fun `workspace 경로가 비어있으면 모든 항목이 WorkspaceNotConfigured다`() {
        val summary = WorkspaceArtifactProbe().probe(null, date)

        summary.items.forEach { assertEquals(ArtifactProbeState.WorkspaceNotConfigured, it.state) }
        assertTrue(!summary.isRequiredReady)
    }
}
