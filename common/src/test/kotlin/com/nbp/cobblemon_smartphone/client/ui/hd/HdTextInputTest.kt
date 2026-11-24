package com.nbp.cobblemon_smartphone.client.ui.hd

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HdTextInputTest {

    @Test
    fun `inserts text up to maxLength and replaces active selection`() {
        val st = HdStateStore.TextInputState(text = "Hello world", cursor = 11)
        st.selectionAnchor = 6 // selects "world"

        TextInputNode.insertText(st, "Pikachu", maxLength = 20)
        assertEquals("Hello Pikachu", st.text)
        assertEquals(13, st.cursor)
        assertFalse(st.hasSelection())

        TextInputNode.insertText(st, "1234567890", maxLength = 16)
        assertEquals("Hello Pikachu123", st.text)
        assertEquals(16, st.cursor)
    }

    @Test
    fun `backspace and delete support whole word and selection deletion`() {
        val st = HdStateStore.TextInputState(text = "Catch wild Pikachu", cursor = 18)

        assertTrue(TextInputNode.backspace(st, wholeWord = true))
        assertEquals("Catch wild ", st.text)
        assertEquals(11, st.cursor)

        TextInputNode.moveToEdge(st, start = true, shift = false)
        assertTrue(TextInputNode.deleteForward(st, wholeWord = true))
        assertEquals(" wild ", st.text)
        assertEquals(0, st.cursor)
    }

    @Test
    fun `shift arrow selection and selectAll behave cleanly`() {
        val st = HdStateStore.TextInputState(text = "Cobblemon", cursor = 0)
        TextInputNode.moveCursor(st, dir = 1, shift = true, wholeWord = false)
        TextInputNode.moveCursor(st, dir = 1, shift = true, wholeWord = false)
        assertEquals("Co", st.selectedText())

        TextInputNode.selectAll(st)
        assertEquals("Cobblemon", st.selectedText())

        TextInputNode.moveCursor(st, dir = -1, shift = false, wholeWord = false)
        assertFalse(st.hasSelection())
        assertEquals(0, st.cursor)
    }

    @Test
    fun `findCursorIndexAtX maps x offset monotonically`() {
        val text = "Pikachu"
        val fullW = HdTextLayout.measureWidthDp(text, 10f, HdTextLayout.Weight.REGULAR, 1.5f)
        assertEquals(0, TextInputNode.findCursorIndexAtX(text, -5f, 10f, 1.5f))
        assertEquals(text.length, TextInputNode.findCursorIndexAtX(text, fullW + 10f, 10f, 1.5f))
        val midIdx = TextInputNode.findCursorIndexAtX(text, fullW * 0.5f, 10f, 1.5f)
        assertTrue(midIdx in 2..5)
    }
}
