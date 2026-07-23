package io.hrns_now.infra.serialization

import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull

private fun readFixture(name: String): String {
    val resource = WorkflowStateParserTest::class.java.classLoader.getResource("fixtures/$name")
        ?: error("fixture not found: $name")
    return Files.readString(Paths.get(resource.toURI()))
}

class WorkflowStateParserTest {

    private val parser = WorkflowStateParser()

    @Test
    fun `live shape fixture를 성공적으로 파싱한다`() {
        val result = parser.parse(readFixture("workflow-state-live-shape.json"))
        val success = assertIs<ParseResult.Success>(result)
        assertNotNull(success.dto.state)
        assertNotNull(success.dto.queue)
    }

    @Test
    fun `unknown top-level key는 무시한다`() {
        val text = readFixture("workflow-state-live-shape.json")
            .replace("\"notes\":", "\"brand_new_top_level_field\": \"future harness feature\",\n  \"notes\":")
        val result = parser.parse(text)
        assertIs<ParseResult.Success>(result)
    }

    @Test
    fun `unknown nested key는 무시한다`() {
        val text = readFixture("workflow-state-live-shape.json")
            .replace(
                "\"current_phase\": \"execution\",",
                "\"brand_new_nested_field\": {\"anything\": 1},\n    \"current_phase\": \"execution\",",
            )
        val result = parser.parse(text)
        val success = assertIs<ParseResult.Success>(result)
        assertNotNull(success.dto.state)
    }

    @Test
    fun `잘려나간 JSON은 파싱에 실패한다`() {
        val text = readFixture("workflow-state-live-shape.json")
        val truncated = text.substring(0, text.length / 3)
        val result = parser.parse(truncated)
        assertIs<ParseResult.Failure>(result)
    }

    @Test
    fun `빈 문자열은 파싱에 실패한다`() {
        val result = parser.parse("")
        assertIs<ParseResult.Failure>(result)
    }
}
