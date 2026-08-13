package com.edukasyon.studentai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
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
            }
        }
    }
}

internal sealed class MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock()
    data class Code(val content: String) : MarkdownBlock()
    data class BulletList(val items: List<String>) : MarkdownBlock()
    data class OrderedList(val items: List<String>) : MarkdownBlock()
}

internal fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    if (markdown.isBlank()) return emptyList()

    val blocks = mutableListOf<MarkdownBlock>()
    val lines = markdown.replace("\r\n", "\n").split('\n')
    var index = 0

    while (index < lines.size) {
        val line = lines[index]

        if (line.trimStart().startsWith("```")) {
            index = parseCodeFence(lines, index, blocks)
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
        if (line.isBlank()) break
        if (
            line.trimStart().startsWith("```") ||
            BULLET_LIST_REGEX.matches(line) ||
            ORDERED_LIST_REGEX.matches(line)
        ) {
            break
        }
        paragraph.append(' ').append(line.trim())
        index++
    }
    blocks += MarkdownBlock.Paragraph(paragraph.toString())
    return index
}

private val BULLET_LIST_REGEX = Regex("""^\s*[-*+]\s+(.+)$""")
private val ORDERED_LIST_REGEX = Regex("""^\s*(\d+)\.\s+(.+)$""")

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
        val boldMatch = BOLD_REGEX.find(text, cursor)
        val italicMatch = ITALIC_REGEX.find(text, cursor)
        val codeMatch = INLINE_CODE_REGEX.find(text, cursor)

        val next = listOfNotNull(linkMatch, boldMatch, italicMatch, codeMatch)
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
private val BOLD_REGEX = Regex("""\*\*(.+?)\*\*|__(.+?)__""")
private val ITALIC_REGEX = Regex("""(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)|(?<!_)_(?!_)(.+?)(?<!_)_(?!_)""")
private val INLINE_CODE_REGEX = Regex("""`([^`]+)`""")
