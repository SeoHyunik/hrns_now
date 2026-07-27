package io.hrns_now.core.domain.model

/** `templates/workspace/REQUEST_INBOX.md.tpl`의 "Recommended Entry Format" `Type:` 값이다. */
enum class RequestEntryType(val label: String) {
    Bug("bug"),
    Feature("feature"),
    Docs("docs"),
    Ops("ops"),
    Qa("QA"),
    Idea("idea"),
    Question("question"),
    Constraint("constraint"),
}

/** 같은 템플릿의 `Source:` 값이다. */
enum class RequestEntrySource(val label: String) {
    Human("human"),
    Qa("QA"),
    Usage("usage"),
    Report("report"),
    Handoff("handoff"),
    Other("other"),
}

/** 같은 템플릿의 `Priority:` 값이다. */
enum class RequestEntryPriority(val label: String) {
    High("high"),
    Medium("medium"),
    Low("low"),
    Unknown("unknown"),
}

/**
 * `REQUEST_INBOX.md`에 새 항목으로 추가할 사용자 입력이다. `constraints`는 템플릿의 고정
 * 필드가 아니지만, 템플릿 자체가 "loose format"·"does NOT need to be perfectly structured"라고
 * 명시하므로 `Notes` 다음에 `Constraints` 절을 추가해도 계약을 위반하지 않는다.
 */
data class RequestEntryDraft(
    val title: String,
    val type: RequestEntryType,
    val source: RequestEntrySource,
    val priority: RequestEntryPriority,
    val summary: String,
    val detail: String,
    val constraints: String,
)

/** [RequestEntryDraft]를 템플릿의 "Recommended Entry Format" markdown 블록으로 렌더링한다. */
fun RequestEntryDraft.toMarkdownEntry(): String {
    val detailLines = detail.lines().filter(String::isNotBlank).joinToString("\n") { "  - $it" }
    val constraintLines = constraints.lines().filter(String::isNotBlank).joinToString("\n") { "  - $it" }
    return buildString {
        appendLine("### ${title.trim()}")
        appendLine()
        appendLine("- Type: ${type.label}")
        appendLine("- Source: ${source.label}")
        appendLine("- Priority: ${priority.label}")
        appendLine("- Summary: ${summary.trim()}")
        appendLine("- Details:")
        if (detailLines.isNotBlank()) appendLine(detailLines) else appendLine("  - (none)")
        if (constraintLines.isNotBlank()) {
            appendLine("- Constraints:")
            appendLine(constraintLines)
        }
    }.trimEnd()
}
