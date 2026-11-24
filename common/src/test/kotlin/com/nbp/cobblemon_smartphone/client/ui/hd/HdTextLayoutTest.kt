package com.nbp.cobblemon_smartphone.client.ui.hd

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HdTextLayoutTest {

    @Test
    fun `parses mentions and hashtags into styled spans`() {
        val spans = HdTextLayout.parseSocialSpans("Hey @Red check #Cobblemon today!")
        assertEquals(5, spans.size)
        assertEquals(HdTextLayout.StyledSpan("Hey ", HdTextLayout.StyledSpan.Kind.PLAIN), spans[0])
        assertEquals(HdTextLayout.StyledSpan("@Red", HdTextLayout.StyledSpan.Kind.MENTION), spans[1])
        assertEquals(HdTextLayout.StyledSpan(" check ", HdTextLayout.StyledSpan.Kind.PLAIN), spans[2])
        assertEquals(HdTextLayout.StyledSpan("#Cobblemon", HdTextLayout.StyledSpan.Kind.HASHTAG), spans[3])
        assertEquals(HdTextLayout.StyledSpan(" today!", HdTextLayout.StyledSpan.Kind.PLAIN), spans[4])
    }

    @Test
    fun `wraps text within target dp width and preserves explicit newlines`() {
        val text = "Line one has several words that should wrap cleanly\nLine two"
        val lines = HdTextLayout.wrapSocialText(text, maxWidthDp = 120f, fontSizeDp = 10.5f, viewportScale = 1.8f)

        assertTrue(lines.size >= 3)
        assertEquals("Line two", lines.last().plainText)
        lines.forEach { line ->
            assertTrue(line.widthDp <= 120.5f, "Line exceeded max width: ${line.widthDp} (${line.plainText})")
        }
    }

    @Test
    fun `truncates overflowing text with ellipsis within budget`() {
        val longName = "SuperLongTrainerUsernameThatDoesNotFitInCardHeader"
        val truncated = HdTextLayout.truncateDp(longName, maxWidthDp = 90f, fontSizeDp = 11f, weight = HdTextLayout.Weight.BOLD, viewportScale = 1.5f)
        assertTrue(truncated.endsWith("…"))
        val w = HdTextLayout.measureWidthDp(truncated, 11f, HdTextLayout.Weight.BOLD, 1.5f)
        assertTrue(w <= 90.5f, "Truncated width $w exceeded 90dp")
    }

    @Test
    fun `resolves fallback fonts for gender and shiny symbols`() {
        for (symbol in listOf("♂", "♀", "★")) {
            val cp = symbol.codePointAt(0)
            val font = HdTextLayout.fontForCodePoint(cp, HdTextLayout.Weight.BOLD, 20)
            assertTrue(font.size == 20)
            assertTrue(HdTextLayout.glyphAdvancePx(cp, HdTextLayout.Weight.BOLD, 20) > 0f)
        }
    }
}
