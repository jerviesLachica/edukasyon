package com.edukasyon.studentai.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownChatTextTest {

    @Test
    fun parseMarkdownBlocks_supportsBoldParagraph() {
        val blocks = parseMarkdownBlocks("Hello **world**")
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Paragraph)
        assertEquals("Hello **world**", (blocks[0] as MarkdownBlock.Paragraph).text)
    }

    @Test
    fun parseMarkdownBlocks_supportsCodeFence() {
        val blocks = parseMarkdownBlocks(
            """
            Intro
            ```kotlin
            val x = 1
            ```
            After
            """.trimIndent(),
        )
        assertEquals(3, blocks.size)
        assertEquals("Intro", (blocks[0] as MarkdownBlock.Paragraph).text)
        assertEquals("val x = 1", (blocks[1] as MarkdownBlock.Code).content)
        assertEquals("After", (blocks[2] as MarkdownBlock.Paragraph).text)
    }

    @Test
    fun parseMarkdownBlocks_supportsLists() {
        val blocks = parseMarkdownBlocks(
            """
            - first
            - second

            1. one
            2. two
            """.trimIndent(),
        )
        assertEquals(2, blocks.size)
        assertEquals(listOf("first", "second"), (blocks[0] as MarkdownBlock.BulletList).items)
        assertEquals(listOf("one", "two"), (blocks[1] as MarkdownBlock.OrderedList).items)
    }

    @Test
    fun buildInlineMarkdown_rendersBoldAndLink() {
        val annotated = buildInlineMarkdown(
            text = "See **SchedMate** at [site](https://example.com)",
            linkColor = androidx.compose.ui.graphics.Color.Blue,
            codeBackground = androidx.compose.ui.graphics.Color.Gray,
        )
        assertTrue(annotated.text.contains("SchedMate"))
        assertTrue(annotated.text.contains("site"))
    }

    @Test
    fun parseMarkdownBlocks_supportsHeadingsAndDividers() {
        val blocks = parseMarkdownBlocks(
            """
            # Title 1
            ---
            ### Section 1.1
            Some paragraph text
            """.trimIndent(),
        )
        assertEquals(4, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Heading)
        assertEquals(1, (blocks[0] as MarkdownBlock.Heading).level)
        assertEquals("Title 1", (blocks[0] as MarkdownBlock.Heading).text)

        assertTrue(blocks[1] is MarkdownBlock.Divider)

        assertTrue(blocks[2] is MarkdownBlock.Heading)
        assertEquals(3, (blocks[2] as MarkdownBlock.Heading).level)
        assertEquals("Section 1.1", (blocks[2] as MarkdownBlock.Heading).text)

        assertTrue(blocks[3] is MarkdownBlock.Paragraph)
        assertEquals("Some paragraph text", (blocks[3] as MarkdownBlock.Paragraph).text)
    }

    @Test
    fun buildInlineMarkdown_rendersCitations() {
        val annotated = buildInlineMarkdown(
            text = "According to Piaget's theory [1], assimilation occurs first [2].",
            linkColor = androidx.compose.ui.graphics.Color.Blue,
            codeBackground = androidx.compose.ui.graphics.Color.Gray,
        )
        assertTrue(annotated.text.contains("[1]"))
        assertTrue(annotated.text.contains("[2]"))
    }

    @Test
    fun parseMarkdownBlocks_supportsTables() {
        val markdown = """
            Here is the table:
            | Subject | Day | Time |
            | :--- | :--- | :--- |
            | Math | Mon | 08:00 |
            | Science | Wed | 10:00 |
            After table paragraph.
        """.trimIndent()

        val blocks = parseMarkdownBlocks(markdown)
        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Paragraph)
        assertTrue(blocks[1] is MarkdownBlock.Table)
        assertTrue(blocks[2] is MarkdownBlock.Paragraph)

        val table = blocks[1] as MarkdownBlock.Table
        assertEquals(listOf("Subject", "Day", "Time"), table.headers)
        assertEquals(2, table.rows.size)
        assertEquals(listOf("Math", "Mon", "08:00"), table.rows[0])
        assertEquals(listOf("Science", "Wed", "10:00"), table.rows[1])
    }
}
