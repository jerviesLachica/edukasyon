package com.edukasyon.studentai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

@Composable
fun MarkdownChatText(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    // Rich content path: math, diagrams, graphs → WebView renderer
    if (hasRichContent(markdown)) {
        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        val html = remember(markdown, isDark) {
            buildRichContentHtml(markdown, isDark)
        }
        RichContentRenderer(
            html = html,
            modifier = modifier.fillMaxWidth(),
        )
        return
    }

    // Plain markdown path: existing Compose renderer
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    val headingStyle = when (block.level) {
                        1 -> typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        2 -> typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        3 -> typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, color = colors.primary)
                        else -> typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                    }
                    SelectionContainer {
                        Text(
                            text = buildInlineMarkdown(
                                text = block.text,
                                linkColor = colors.primary,
                                codeBackground = colors.surface.copy(alpha = 0.65f),
                            ),
                            style = headingStyle,
                            color = if (block.level == 3) colors.primary else colors.onSurface,
                            modifier = Modifier.padding(top = if (block.level <= 2) 8.dp else 4.dp, bottom = 2.dp),
                        )
                    }
                }

                is MarkdownBlock.Divider -> {
                    androidx.compose.material3.HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp),
                        color = colors.outlineVariant.copy(alpha = 0.4f),
                        thickness = 1.dp,
                    )
                }

                is MarkdownBlock.Paragraph -> {
                    SelectionContainer {
                        Text(
                            text = buildInlineMarkdown(
                                text = block.text,
                                linkColor = colors.primary,
                                codeBackground = colors.surface.copy(alpha = 0.65f),
                            ),
                            style = typography.bodyMedium,
                            color = colors.onSurface,
                        )
                    }
                }

                is MarkdownBlock.Code -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.surface.copy(alpha = 0.85f),
                    ) {
                        SelectionContainer {
                            Text(
                                text = block.content,
                                style = typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = colors.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            )
                        }
                    }
                }

                is MarkdownBlock.BulletList -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        block.items.forEach { item ->
                            Row {
                                Text(
                                    text = "•",
                                    style = typography.bodyMedium,
                                    color = colors.onSurface,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                                SelectionContainer(modifier = Modifier.weight(1f, fill = false)) {
                                    Text(
                                        text = buildInlineMarkdown(
                                            text = item,
                                            linkColor = colors.primary,
                                            codeBackground = colors.surface.copy(alpha = 0.65f),
                                        ),
                                        style = typography.bodyMedium,
                                        color = colors.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }

                is MarkdownBlock.OrderedList -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        block.items.forEachIndexed { index, item ->
                            Row {
                                Text(
                                    text = "${index + 1}.",
                                    style = typography.bodyMedium,
                                    color = colors.onSurface,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                                SelectionContainer(modifier = Modifier.weight(1f, fill = false)) {
                                    Text(
                                        text = buildInlineMarkdown(
                                            text = item,
                                            linkColor = colors.primary,
                                            codeBackground = colors.surface.copy(alpha = 0.65f),
                                        ),
                                        style = typography.bodyMedium,
                                        color = colors.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }

                is MarkdownBlock.Table -> {
                    MarkdownTableComposable(
                        table = block,
                        colors = colors,
                        typography = typography,
                    )
                }
            }
        }
    }
}

internal sealed class MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock()
    object Divider : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data class Code(val content: String) : MarkdownBlock()
    data class BulletList(val items: List<String>) : MarkdownBlock()
    data class OrderedList(val items: List<String>) : MarkdownBlock()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock()
}

internal fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    if (markdown.isBlank()) return emptyList()

    val blocks = mutableListOf<MarkdownBlock>()
    val lines = markdown.replace("\r\n", "\n").split('\n')
    var index = 0

    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trim()

        if (line.trimStart().startsWith("```")) {
            index = parseCodeFence(lines, index, blocks)
            continue
        }

        if (isTableStart(lines, index)) {
            index = parseTable(lines, index, blocks)
            continue
        }

        val headingMatch = HEADING_REGEX.find(trimmed)
        if (headingMatch != null && headingMatch.range.first == 0) {
            val level = headingMatch.groupValues[1].length
            val text = headingMatch.groupValues[2].trim()
            blocks += MarkdownBlock.Heading(level, text)
            index++
            continue
        }

        if (DIVIDER_REGEX.matches(trimmed)) {
            blocks += MarkdownBlock.Divider
            index++
            continue
        }

        if (BULLET_LIST_REGEX.matches(line)) {
            index = parseBulletList(lines, index, blocks)
            continue
        }

        if (ORDERED_LIST_REGEX.matches(line)) {
            index = parseOrderedList(lines, index, blocks)
            continue
        }

        if (line.isBlank()) {
            index++
            continue
        }

        index = parseParagraph(lines, index, blocks)
    }

    return blocks
}

private fun parseCodeFence(
    lines: List<String>,
    startIndex: Int,
    blocks: MutableList<MarkdownBlock>,
): Int {
    val content = StringBuilder()
    var index = startIndex + 1
    while (index < lines.size && !lines[index].trimStart().startsWith("```")) {
        if (content.isNotEmpty()) content.append('\n')
        content.append(lines[index])
        index++
    }
    blocks += MarkdownBlock.Code(content.toString())
    return if (index < lines.size) index + 1 else index
}

private fun parseBulletList(
    lines: List<String>,
    startIndex: Int,
    blocks: MutableList<MarkdownBlock>,
): Int {
    val items = mutableListOf<String>()
    var index = startIndex
    while (index < lines.size) {
        val match = BULLET_LIST_REGEX.find(lines[index]) ?: break
        items += match.groupValues[1].trim()
        index++
        if (index < lines.size && lines[index].isBlank()) break
    }
    blocks += MarkdownBlock.BulletList(items)
    return index
}

private fun parseOrderedList(
    lines: List<String>,
    startIndex: Int,
    blocks: MutableList<MarkdownBlock>,
): Int {
    val items = mutableListOf<String>()
    var index = startIndex
    while (index < lines.size) {
        val match = ORDERED_LIST_REGEX.find(lines[index]) ?: break
        items += match.groupValues[2].trim()
        index++
        if (index < lines.size && lines[index].isBlank()) break
    }
    blocks += MarkdownBlock.OrderedList(items)
    return index
}

private fun parseParagraph(
    lines: List<String>,
    startIndex: Int,
    blocks: MutableList<MarkdownBlock>,
): Int {
    val paragraph = StringBuilder(lines[startIndex].trim())
    var index = startIndex + 1
    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trim()
        if (line.isBlank()) break
        if (
            line.trimStart().startsWith("```") ||
            HEADING_REGEX.find(trimmed)?.range?.first == 0 ||
            DIVIDER_REGEX.matches(trimmed) ||
            BULLET_LIST_REGEX.matches(line) ||
            ORDERED_LIST_REGEX.matches(line) ||
            isTableStart(lines, index)
        ) {
            break
        }
        paragraph.append(' ').append(line.trim())
        index++
    }
    blocks += MarkdownBlock.Paragraph(paragraph.toString())
    return index
}

private val TABLE_SEPARATOR_REGEX = Regex("""^\s*\|?(\s*:?-{2,}:?\s*\|)+\s*(:?-{2,}:?\s*)?\|?\s*$""")

private fun isTableStart(lines: List<String>, index: Int): Boolean {
    if (index + 1 >= lines.size) return false
    val header = lines[index].trim()
    val separator = lines[index + 1].trim()
    if (!header.contains('|') || header.startsWith("```")) return false
    return TABLE_SEPARATOR_REGEX.matches(separator)
}

private fun splitTableRow(line: String): List<String> {
    var trimmed = line.trim()
    if (trimmed.startsWith('|')) trimmed = trimmed.substring(1)
    if (trimmed.endsWith('|')) trimmed = trimmed.substring(0, trimmed.length - 1)
    return trimmed.split('|').map { it.trim() }
}

private fun parseTable(
    lines: List<String>,
    startIndex: Int,
    blocks: MutableList<MarkdownBlock>,
): Int {
    val headers = splitTableRow(lines[startIndex])
    var index = startIndex + 2
    val rows = mutableListOf<List<String>>()
    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trim()
        if (line.isBlank() || !trimmed.contains('|') || trimmed.startsWith("```") || HEADING_REGEX.find(trimmed)?.range?.first == 0) {
            break
        }
        rows.add(splitTableRow(line))
        index++
    }
    blocks += MarkdownBlock.Table(headers, rows)
    return index
}

@Composable
private fun MarkdownTableComposable(
    table: MarkdownBlock.Table,
    colors: ColorScheme,
    typography: Typography,
    modifier: Modifier = Modifier,
) {
    if (table.headers.isEmpty() && table.rows.isEmpty()) return
    val colCount = maxOf(table.headers.size, table.rows.maxOfOrNull { it.size } ?: 0)
    if (colCount == 0) return

    val scrollState = rememberScrollState()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        color = colors.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(4.dp),
        ) {
            Column(modifier = Modifier.width(IntrinsicSize.Max)) {
                if (table.headers.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                colors.primaryContainer.copy(alpha = 0.65f),
                                RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                            )
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        for (c in 0 until colCount) {
                            val headerText = table.headers.getOrElse(c) { "" }
                            Box(
                                modifier = Modifier
                                    .weight(1f, fill = true)
                                    .padding(horizontal = 8.dp),
                            ) {
                                SelectionContainer {
                                    Text(
                                        text = buildInlineMarkdown(
                                            text = headerText,
                                            linkColor = colors.primary,
                                            codeBackground = colors.surface.copy(alpha = 0.7f),
                                        ),
                                        style = typography.labelLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = colors.onPrimaryContainer,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(
                        color = colors.outlineVariant.copy(alpha = 0.6f),
                        thickness = 1.dp,
                    )
                }

                table.rows.forEachIndexed { rowIndex, row ->
                    val isEven = rowIndex % 2 == 0
                    val rowBg = if (isEven) Color.Transparent else colors.surface.copy(alpha = 0.45f)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(rowBg)
                            .padding(vertical = 7.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        for (c in 0 until colCount) {
                            val cellText = row.getOrElse(c) { "" }
                            Box(
                                modifier = Modifier
                                    .weight(1f, fill = true)
                                    .padding(horizontal = 8.dp),
                            ) {
                                SelectionContainer {
                                    Text(
                                        text = buildInlineMarkdown(
                                            text = cellText,
                                            linkColor = colors.primary,
                                            codeBackground = colors.surface.copy(alpha = 0.65f),
                                        ),
                                        style = typography.bodyMedium,
                                        color = colors.onSurface,
                                    )
                                }
                            }
                        }
                    }

                    if (rowIndex < table.rows.size - 1) {
                        HorizontalDivider(
                            color = colors.outlineVariant.copy(alpha = 0.25f),
                            thickness = 0.5.dp,
                        )
                    }
                }
            }
        }
    }
}

private val HEADING_REGEX = Regex("""^(#{1,6})\s+(.+)$""")
private val DIVIDER_REGEX = Regex("""^(\s*[-*_]\s*){3,}$""")
private val BULLET_LIST_REGEX = Regex("""^\s*[-*+]\s+(.+)$""")
private val ORDERED_LIST_REGEX = Regex("""^\s*(\d+)\.\s+(.+)$""")

/** Returns true if the message contains renderable rich content (math, diagrams, graphs). */
internal fun hasRichContent(markdown: String): Boolean {
    if (markdown.contains("```mermaid")) return true
    if (markdown.contains("```chart")) return true
    // Conservative $-scan: only real math expressions count. "$5 and $10"
    // (currency) must stay on the fast Compose path.
    return containsMath(markdown)
}

/** Mirrors renderInlineMath's scan; true when at least one $...$ holds math. */
private fun containsMath(text: String): Boolean {
    var i = 0
    val n = text.length
    while (i < n) {
        if (i + 1 < n && text[i] == '$' && text[i + 1] == '$') {
            val end = text.indexOf("$$", i + 2)
            if (end != -1 && end > i + 2) {
                if (looksLikeMath(text.substring(i + 2, end).trim())) return true
                i = end + 2
                continue
            }
        }
        if (text[i] == '$') {
            val end = text.indexOf('$', i + 1)
            if (end != -1 && end > i + 1) {
                if (looksLikeMath(text.substring(i + 1, end).trim())) return true
                i = end + 1
                continue
            }
        }
        i++
    }
    return false
}

private fun androidx.compose.ui.graphics.Color.luminance(): Float {
    return 0.299f * red + 0.587f * green + 0.114f * blue
}

internal fun buildInlineMarkdown(
    text: String,
    linkColor: androidx.compose.ui.graphics.Color,
    codeBackground: androidx.compose.ui.graphics.Color,
): AnnotatedString = buildAnnotatedString {
    appendInlineMarkdown(text, linkColor, codeBackground)
}

private fun AnnotatedString.Builder.appendInlineMarkdown(
    text: String,
    linkColor: androidx.compose.ui.graphics.Color,
    codeBackground: androidx.compose.ui.graphics.Color,
) {
    var cursor = 0
    while (cursor < text.length) {
        val linkMatch = LINK_REGEX.find(text, cursor)
        val citationMatch = CITATION_REGEX.find(text, cursor)
        val boldMatch = BOLD_REGEX.find(text, cursor)
        val italicMatch = ITALIC_REGEX.find(text, cursor)
        val codeMatch = INLINE_CODE_REGEX.find(text, cursor)

        val next = listOfNotNull(linkMatch, citationMatch, boldMatch, italicMatch, codeMatch)
            .minByOrNull { it.range.first }

        if (next == null) {
            append(text.substring(cursor))
            break
        }

        if (next.range.first > cursor) {
            append(text.substring(cursor, next.range.first))
        }

        when {
            linkMatch != null && next.range == linkMatch.range -> {
                withLink(
                    LinkAnnotation.Url(
                        url = linkMatch.groupValues[2],
                        styles = TextLinkStyles(
                            style = SpanStyle(color = linkColor, fontWeight = FontWeight.Medium),
                        ),
                    ),
                ) {
                    append(linkMatch.groupValues[1])
                }
            }

            citationMatch != null && next.range == citationMatch.range -> {
                val content = citationMatch.groupValues[1]
                withStyle(
                    SpanStyle(
                        color = linkColor,
                        fontWeight = FontWeight.Bold,
                        background = linkColor.copy(alpha = 0.15f),
                    ),
                ) {
                    append("[$content]")
                }
            }

            boldMatch != null && next.range == boldMatch.range -> {
                val content = boldMatch.groupValues[1].ifEmpty { boldMatch.groupValues[2] }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(content)
                }
            }

            italicMatch != null && next.range == italicMatch.range -> {
                val content = italicMatch.groupValues[1].ifEmpty { italicMatch.groupValues[2] }
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(content)
                }
            }

            codeMatch != null && next.range == codeMatch.range -> {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = codeBackground,
                    ),
                ) {
                    append(codeMatch.groupValues[1])
                }
            }
        }

        cursor = next.range.last + 1
    }
}

private val LINK_REGEX = Regex("""\[([^\]]+)\]\(([^)]+)\)""")
private val CITATION_REGEX = Regex("""\[(\d+(?:\s*,\s*\d+)*)\](?!\()""")
private val BOLD_REGEX = Regex("""\*\*(.+?)\*\*|__(.+?)__""")
private val ITALIC_REGEX = Regex("""(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)|(?<!_)_(?!_)(.+?)(?<!_)_(?!_)""")
private val INLINE_CODE_REGEX = Regex("""`([^`]+)`""")
