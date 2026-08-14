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
}
