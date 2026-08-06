package io.hrns_now.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * `TODAY_STRATEGY.md` 같은 사람이 읽는 Harness 원문을 위한 안전하고 제한된 Markdown renderer다.
 * HTML/webview를 실행하지 않고, 원격 resource를 불러오지 않으며, 파일을 쓰거나
 * 명령을 실행하지 않는다 — 순수 문자열 파싱 후 Compose Text로만 그린다. heading/list/강조/
 * inline code/code block/인용/link의 실제 사용 범위만 지원하는 제한된 renderer다.
 */
private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class BulletItem(val text: String, val ordered: Boolean, val index: Int?) : MdBlock
    data class CodeBlock(val lines: List<String>) : MdBlock
    data class Quote(val text: String) : MdBlock
    data object Divider : MdBlock
}

private val headingRegex = Regex("^#{1,6}\\s+.*")
private val bulletRegex = Regex("^[-*+]\\s+.*")
private val orderedRegex = Regex("^(\\d+)\\.\\s+(.*)")
private val inlineRegex = Regex("\\[([^\\]]+)]\\(([^)]+)\\)|\\*\\*([^*]+)\\*\\*|`([^`]+)`")

private fun parseMarkdown(raw: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = raw.lines()
    val paragraphBuffer = StringBuilder()

    fun flushParagraph() {
        if (paragraphBuffer.isNotBlank()) {
            blocks += MdBlock.Paragraph(paragraphBuffer.toString().trim())
        }
        paragraphBuffer.clear()
    }

    var i = 0
    while (i < lines.size) {
        val trimmed = lines[i].trimEnd()
        when {
            trimmed.startsWith("```") -> {
                flushParagraph()
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trimEnd().startsWith("```")) {
                    codeLines += lines[i]
                    i++
                }
                blocks += MdBlock.CodeBlock(codeLines)
            }
            trimmed.isBlank() -> flushParagraph()
            headingRegex.matches(trimmed) -> {
                flushParagraph()
                val level = trimmed.takeWhile { it == '#' }.length
                blocks += MdBlock.Heading(level, trimmed.removePrefix("#".repeat(level)).trim())
            }
            trimmed.startsWith("> ") || trimmed == ">" -> {
                flushParagraph()
                blocks += MdBlock.Quote(trimmed.removePrefix(">").trim())
            }
            trimmed == "---" || trimmed == "***" -> {
                flushParagraph()
                blocks += MdBlock.Divider
            }
            bulletRegex.matches(trimmed) -> {
                flushParagraph()
                blocks += MdBlock.BulletItem(trimmed.drop(1).trim(), ordered = false, index = null)
            }
            orderedRegex.matches(trimmed) -> {
                flushParagraph()
                val match = requireNotNull(orderedRegex.matchEntire(trimmed))
                blocks += MdBlock.BulletItem(match.groupValues[2], ordered = true, index = match.groupValues[1].toIntOrNull())
            }
            else -> {
                if (paragraphBuffer.isNotEmpty()) paragraphBuffer.append(' ')
                paragraphBuffer.append(trimmed)
            }
        }
        i++
    }
    flushParagraph()
    return blocks
}

@Composable
private fun inlineMarkdownText(raw: String): AnnotatedString {
    val colors = LocalHrnsColors.current
    return buildAnnotatedString {
        var cursor = 0
        inlineRegex.findAll(raw).forEach { match ->
            if (match.range.first > cursor) append(raw.substring(cursor, match.range.first))
            val linkText = match.groups[1]?.value
            val linkUrl = match.groups[2]?.value
            val boldText = match.groups[3]?.value
            val codeText = match.groups[4]?.value
            when {
                linkText != null -> {
                    withStyle(SpanStyle(color = colors.accent, textDecoration = TextDecoration.Underline)) {
                        append(linkText)
                    }
                    if (!linkUrl.isNullOrBlank()) {
                        withStyle(SpanStyle(color = colors.tertiaryText, fontFamily = FontFamily.Monospace)) {
                            append(" ($linkUrl)")
                        }
                    }
                }
                boldText != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(boldText) }
                codeText != null -> withStyle(
                    SpanStyle(fontFamily = FontFamily.Monospace, background = colors.surfaceMuted),
                ) { append(" $codeText ") }
                else -> append(match.value)
            }
            cursor = match.range.last + 1
        }
        if (cursor < raw.length) append(raw.substring(cursor))
    }
}

/**
 * 원문(read-only) Markdown 문서를 안전하게 렌더링한다. 원문을 수정·정규화·번역하지 않고
 * 그대로 보여줄 뿐이다 — 임의의 영문 prose를 자동 번역했다고 주장하지 않는다.
 */
@Composable
fun SafeMarkdownDocument(text: String, modifier: Modifier = Modifier) {
    val colors = LocalHrnsColors.current
    val blocks = remember(text) { parseMarkdown(text) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    text = inlineMarkdownText(block.text),
                    style = when (block.level) {
                        1, 2 -> MaterialTheme.typography.titleLarge.copy(fontSize = 17.sp)
                        3, 4 -> MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp)
                        else -> MaterialTheme.typography.titleSmall.copy(fontSize = 13.5.sp)
                    },
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primaryText,
                )

                is MdBlock.Paragraph -> Text(
                    text = inlineMarkdownText(block.text),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, lineHeight = 21.sp),
                    color = colors.primaryText,
                )

                is MdBlock.BulletItem -> Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (block.ordered) "${block.index ?: "·"}." else "·",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                        color = colors.accent,
                        modifier = Modifier.width(22.dp),
                    )
                    Text(
                        text = inlineMarkdownText(block.text),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, lineHeight = 20.sp),
                        color = colors.primaryText,
                    )
                }

                is MdBlock.Quote -> Row(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .background(colors.borderStrong),
                    )
                    Text(
                        text = inlineMarkdownText(block.text),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.5.sp,
                            lineHeight = 20.sp,
                        ),
                        color = colors.secondaryText,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }

                is MdBlock.CodeBlock -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceMuted, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    block.lines.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.5.sp,
                                lineHeight = 19.sp,
                            ),
                            color = colors.primaryText,
                        )
                    }
                }

                MdBlock.Divider -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.borderSubtle),
                )
            }
        }
    }
}
