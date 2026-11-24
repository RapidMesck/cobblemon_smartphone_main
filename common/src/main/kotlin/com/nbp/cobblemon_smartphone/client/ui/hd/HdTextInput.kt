package com.nbp.cobblemon_smartphone.client.ui.hd

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen

/**
 * Resolution-independent HD text input field (single-line or wrapped multi-line) backed by
 * [HdStateStore.TextInputState]. Editing operations are pure and unit-testable without OpenGL.
 */
class TextInputNode(
    val inputId: String,
    private val stateStore: HdStateStore,
    var initialText: String = "",
    var placeholder: String = "",
    var fontSizeDp: Float = 9.8f,
    var fontId: String = HdTextLayout.DEFAULT_FONT_ID,
    var maxLength: Int = 280,
    var multiline: Boolean = false,
    var showCounter: Boolean = false,
    var radius: Float = 8f,
    var onChange: ((String) -> Unit)? = null,
    var onSubmit: ((String) -> Unit)? = null
) : HdNode() {

    init {
        id = inputId
        width = HdSizeSpec.Fill
        padding = HdPadding.symmetric(horizontal = 9f, vertical = 6f)
    }

    data class WrappedSpanLine(val startIdx: Int, val endIdx: Int, val text: String)

    private var lastViewportScale: Float = 1f

    override fun measureContent(ctx: HdMeasureContext, availContentW: Float, availContentH: Float): Pair<Float, Float> {
        lastViewportScale = ctx.viewportScale
        val lineH = HdTextLayout.lineHeightDp(fontSizeDp, HdTextLayout.Weight.REGULAR, ctx.viewportScale, fontId)
        val w = if (availContentW.isFinite()) availContentW else 160f
        val h = if (multiline) lineH * 4.5f else lineH
        return w to h
    }

    override fun renderBackground(rc: HdRenderContext) {
        val c = rc.colors
        val focused = rc.state.focusedId == inputId
        val hovered = isHovered(rc)
        val borderCol = when {
            focused -> c.primary
            hovered -> c.primaryHover
            else -> c.primaryBorder
        }
        val borderW = if (focused) 1.3f else 0.85f
        rc.canvas.rect(bounds.x, bounds.y, bounds.width, bounds.height, c.surface, radius, borderW, borderCol)
    }

    override fun renderContent(rc: HdRenderContext) {
        lastViewportScale = rc.viewportScale
        val st = stateStore.input(inputId, initialText)
        st.clampCursor()

        val inner = bounds.inset(padding)
        if (inner.width <= 1f || inner.height <= 1f) return

        if (multiline) {
            renderMultiline(rc, st, inner)
        } else {
            renderSingleLine(rc, st, inner)
        }
    }

    private fun renderSingleLine(rc: HdRenderContext, st: HdStateStore.TextInputState, inner: HdBounds) {
        val lineH = rc.canvas.lineHeight(fontSizeDp, HdTextLayout.Weight.REGULAR, fontId)
        val textY = inner.y + (inner.height - lineH) * 0.5f

        val caretRelX = if (st.text.isEmpty()) 0f else {
            rc.canvas.measureText(st.text.substring(0, st.cursor), fontSizeDp, HdTextLayout.Weight.REGULAR, fontId)
        }
        if (caretRelX - st.scrollOffsetX > inner.width - 2f) {
            st.scrollOffsetX = (caretRelX - inner.width + 2f).coerceAtLeast(0f)
        } else if (caretRelX - st.scrollOffsetX < 0f) {
            st.scrollOffsetX = caretRelX.coerceAtLeast(0f)
        }
        val totalTextW = if (st.text.isEmpty()) 0f else {
            rc.canvas.measureText(st.text, fontSizeDp, HdTextLayout.Weight.REGULAR, fontId)
        }
        val maxScroll = (totalTextW - inner.width + 2f).coerceAtLeast(0f)
        st.scrollOffsetX = st.scrollOffsetX.coerceIn(0f, maxScroll)

        val focused = rc.state.focusedId == inputId

        rc.canvas.clipped(inner.x - 1f, inner.y - 1f, inner.width + 2f, inner.height + 2f) {
            if (st.text.isEmpty()) {
                if (placeholder.isNotEmpty()) {
                    val ph = rc.canvas.truncate(placeholder, inner.width, fontSizeDp, HdTextLayout.Weight.REGULAR, fontId)
                    rc.canvas.text(ph, inner.x, textY, fontSizeDp, rc.colors.textDarkMuted, HdTextLayout.Weight.REGULAR, fontId)
                }
            } else {
                val drawX = inner.x - st.scrollOffsetX
                st.selectionRange()?.let { range ->
                    val selStartX = drawX + rc.canvas.measureText(st.text.substring(0, range.first), fontSizeDp, HdTextLayout.Weight.REGULAR, fontId)
                    val selEndX = drawX + rc.canvas.measureText(st.text.substring(0, range.last + 1), fontSizeDp, HdTextLayout.Weight.REGULAR, fontId)
                    val selCol = (rc.colors.primary and 0x00FFFFFF) or 0x45000000
                    rc.canvas.rect(selStartX, textY, (selEndX - selStartX).coerceAtLeast(1f), lineH, selCol, 2f)
                }
                rc.canvas.text(st.text, drawX, textY, fontSizeDp, rc.colors.textDark, HdTextLayout.Weight.REGULAR, fontId)
            }

            if (focused) {
                val blinkOn = ((System.currentTimeMillis() - st.lastEditTimeMs) / 530L) % 2L == 0L
                if (blinkOn) {
                    val caretX = inner.x + caretRelX - st.scrollOffsetX
                    rc.canvas.rect(caretX, textY + 0.5f, 1.2f, (lineH - 1f).coerceAtLeast(4f), rc.colors.primary, 0.6f)
                }
            }
        }
    }

    private fun renderMultiline(rc: HdRenderContext, st: HdStateStore.TextInputState, inner: HdBounds) {
        val lineH = rc.canvas.lineHeight(fontSizeDp, HdTextLayout.Weight.REGULAR, fontId)
        val lines = wrapWithIndices(st.text, inner.width, fontSizeDp, rc.viewportScale, fontId)
        val focused = rc.state.focusedId == inputId

        val cursorLineIdx = findLineForCursor(lines, st.cursor)
        val cursorTopY = cursorLineIdx * lineH
        val viewH = if (showCounter) (inner.height - 12f).coerceAtLeast(lineH) else inner.height
        if (cursorTopY - st.scrollOffsetX > viewH - lineH) {
            st.scrollOffsetX = (cursorTopY - (viewH - lineH)).coerceAtLeast(0f)
        } else if (cursorTopY - st.scrollOffsetX < 0f) {
            st.scrollOffsetX = cursorTopY.coerceAtLeast(0f)
        }
        val maxScrollY = (lines.size * lineH - viewH).coerceAtLeast(0f)
        st.scrollOffsetX = st.scrollOffsetX.coerceIn(0f, maxScrollY)

        rc.canvas.clipped(inner.x - 1f, inner.y - 1f, inner.width + 2f, viewH + 2f) {
            if (st.text.isEmpty()) {
                if (placeholder.isNotEmpty()) {
                    val ph = rc.canvas.truncate(placeholder, inner.width, fontSizeDp, HdTextLayout.Weight.REGULAR, fontId)
                    rc.canvas.text(ph, inner.x, inner.y, fontSizeDp, rc.colors.textDarkMuted, HdTextLayout.Weight.REGULAR, fontId)
                }
            } else {
                val selRange = st.selectionRange()
                lines.forEachIndexed { idx, line ->
                    val ly = inner.y + idx * lineH - st.scrollOffsetX
                    if (ly + lineH >= inner.y && ly <= inner.y + viewH) {
                        if (selRange != null) {
                            val s0 = maxOf(selRange.first, line.startIdx)
                            val s1 = minOf(selRange.last + 1, line.endIdx)
                            if (s0 < s1) {
                                val sx = inner.x + rc.canvas.measureText(st.text.substring(line.startIdx, s0), fontSizeDp, HdTextLayout.Weight.REGULAR, fontId)
                                val ex = inner.x + rc.canvas.measureText(st.text.substring(line.startIdx, s1), fontSizeDp, HdTextLayout.Weight.REGULAR, fontId)
                                val selCol = (rc.colors.primary and 0x00FFFFFF) or 0x45000000
                                rc.canvas.rect(sx, ly, (ex - sx).coerceAtLeast(1f), lineH, selCol, 2f)
                            }
                        }
                        if (line.text.isNotEmpty()) {
                            rc.canvas.text(line.text, inner.x, ly, fontSizeDp, rc.colors.textDark, HdTextLayout.Weight.REGULAR, fontId)
                        }
                    }
                }
            }

            if (focused) {
                val blinkOn = ((System.currentTimeMillis() - st.lastEditTimeMs) / 530L) % 2L == 0L
                if (blinkOn) {
                    val line = lines.getOrNull(cursorLineIdx)
                    val colOffset = if (line != null) {
                        val sub = st.text.substring(line.startIdx, st.cursor.coerceIn(line.startIdx, line.endIdx))
                        rc.canvas.measureText(sub, fontSizeDp, HdTextLayout.Weight.REGULAR, fontId)
                    } else 0f
                    val cy = inner.y + cursorLineIdx * lineH - st.scrollOffsetX
                    rc.canvas.rect(inner.x + colOffset, cy + 0.5f, 1.2f, (lineH - 1f).coerceAtLeast(4f), rc.colors.primary, 0.6f)
                }
            }
        }

        if (showCounter) {
            val counter = "${st.text.length}/$maxLength"
            val cw = rc.canvas.measureText(counter, 7.8f, HdTextLayout.Weight.BOLD, fontId)
            val col = if (st.text.length >= maxLength) rc.colors.danger else rc.colors.textDarkMuted
            rc.canvas.text(counter, inner.right - cw, inner.bottom - 9.5f, 7.8f, col, HdTextLayout.Weight.BOLD, fontId)
        }
    }

    override fun onMouseDown(dpX: Float, dpY: Float, button: Int, state: HdStateStore): Boolean {
        if (button != 0) return false
        val st = state.input(inputId, initialText)
        val inner = bounds.inset(padding)
        st.cursor = if (multiline) {
            val lineH = HdTextLayout.lineHeightDp(fontSizeDp, HdTextLayout.Weight.REGULAR, lastViewportScale, fontId)
            val lines = wrapWithIndices(st.text, inner.width, fontSizeDp, lastViewportScale, fontId)
            val lineIdx = ((dpY - inner.y + st.scrollOffsetX) / lineH).toInt().coerceIn(0, lines.lastIndex)
            val line = lines[lineIdx]
            val localIdx = findCursorIndexAtX(line.text, (dpX - inner.x).coerceAtLeast(0f), fontSizeDp, lastViewportScale, fontId)
            (line.startIdx + localIdx).coerceIn(0, st.text.length)
        } else {
            val clickRelX = (dpX - inner.x + st.scrollOffsetX).coerceAtLeast(0f)
            findCursorIndexAtX(st.text, clickRelX, fontSizeDp, lastViewportScale, fontId)
        }
        st.selectionAnchor = null
        st.resetBlink()
        focusInto(state)
        return true
    }

    override fun onMouseDrag(dpX: Float, dpY: Float, state: HdStateStore): Boolean {
        if (state.focusedId != inputId) return false
        val st = state.input(inputId, initialText)
        val inner = bounds.inset(padding)
        val nextIdx = if (multiline) {
            val lineH = HdTextLayout.lineHeightDp(fontSizeDp, HdTextLayout.Weight.REGULAR, lastViewportScale, fontId)
            val lines = wrapWithIndices(st.text, inner.width, fontSizeDp, lastViewportScale, fontId)
            val lineIdx = ((dpY - inner.y + st.scrollOffsetX) / lineH).toInt().coerceIn(0, lines.lastIndex)
            val line = lines[lineIdx]
            val localIdx = findCursorIndexAtX(line.text, (dpX - inner.x).coerceAtLeast(0f), fontSizeDp, lastViewportScale, fontId)
            (line.startIdx + localIdx).coerceIn(0, st.text.length)
        } else {
            val dragRelX = (dpX - inner.x + st.scrollOffsetX).coerceAtLeast(0f)
            findCursorIndexAtX(st.text, dragRelX, fontSizeDp, lastViewportScale, fontId)
        }
        if (st.selectionAnchor == null && nextIdx != st.cursor) {
            st.selectionAnchor = st.cursor
        }
        st.cursor = nextIdx
        st.clampCursor()
        st.resetBlink()
        return true
    }

    fun focusInto(state: HdStateStore) {
        state.focusedId = inputId
        state.activeInputHandler = { ev -> handleKey(ev.keyCode, ev.modifiers, state) }
        state.activeCharHandler = { ch -> handleChar(ch, state) }
    }

    fun handleChar(ch: Char, state: HdStateStore): Boolean {
        if (ch.code < 32 || ch.code == 127) return false
        val st = state.input(inputId, initialText)
        insertText(st, ch.toString(), maxLength)
        onChange?.invoke(st.text)
        return true
    }

    fun handleKey(keyCode: Int, modifiers: Int, state: HdStateStore): Boolean {
        val st = state.input(inputId, initialText)
        val ctrl = Screen.hasControlDown() || (modifiers and 2) != 0
        val shift = Screen.hasShiftDown() || (modifiers and 1) != 0

        if (ctrl) {
            when (keyCode) {
                KEY_A -> {
                    selectAll(st)
                    return true
                }
                KEY_C -> {
                    if (st.hasSelection()) {
                        Minecraft.getInstance().keyboardHandler.clipboard = st.selectedText()
                    }
                    return true
                }
                KEY_X -> {
                    if (st.hasSelection()) {
                        Minecraft.getInstance().keyboardHandler.clipboard = st.selectedText()
                        deleteSelection(st)
                        onChange?.invoke(st.text)
                    }
                    return true
                }
                KEY_V -> {
                    val clip = Minecraft.getInstance().keyboardHandler.clipboard
                    if (clip.isNotEmpty()) {
                        val raw = clip.replace("\r", "")
                        val sanitized = if (multiline) raw else raw.replace("\n", " ")
                        insertText(st, sanitized, maxLength)
                        onChange?.invoke(st.text)
                    }
                    return true
                }
            }
        }

        return when (keyCode) {
            KEY_BACKSPACE -> {
                if (backspace(st, ctrl)) onChange?.invoke(st.text)
                true
            }
            KEY_DELETE -> {
                if (deleteForward(st, ctrl)) onChange?.invoke(st.text)
                true
            }
            KEY_LEFT -> {
                moveCursor(st, -1, shift, ctrl)
                true
            }
            KEY_RIGHT -> {
                moveCursor(st, 1, shift, ctrl)
                true
            }
            KEY_UP -> {
                if (multiline) moveCursorVertical(st, -1, shift)
                multiline
            }
            KEY_DOWN -> {
                if (multiline) moveCursorVertical(st, 1, shift)
                multiline
            }
            KEY_HOME -> {
                moveToEdge(st, start = true, shift = shift)
                true
            }
            KEY_END -> {
                moveToEdge(st, start = false, shift = shift)
                true
            }
            KEY_ENTER, KEY_KP_ENTER -> {
                if (multiline && !ctrl) {
                    insertText(st, "\n", maxLength)
                    onChange?.invoke(st.text)
                } else {
                    onSubmit?.invoke(st.text)
                }
                true
            }
            KEY_ESCAPE -> {
                state.clearFocus()
                true
            }
            else -> false
        }
    }

    private fun moveCursorVertical(st: HdStateStore.TextInputState, dir: Int, shift: Boolean) {
        val inner = bounds.inset(padding)
        val lines = wrapWithIndices(st.text, inner.width.coerceAtLeast(40f), fontSizeDp, lastViewportScale, fontId)
        val curLine = findLineForCursor(lines, st.cursor)
        val nextLine = (curLine + dir).coerceIn(0, lines.lastIndex)
        if (nextLine == curLine) return
        if (shift && st.selectionAnchor == null) st.selectionAnchor = st.cursor
        val curOffset = st.cursor - lines[curLine].startIdx
        val target = lines[nextLine]
        st.cursor = (target.startIdx + curOffset.coerceAtMost(target.text.length)).coerceIn(0, st.text.length)
        if (!shift) st.selectionAnchor = null
        st.resetBlink()
    }

    companion object {
        private const val KEY_ENTER = 257
        private const val KEY_ESCAPE = 256
        private const val KEY_BACKSPACE = 259
        private const val KEY_DELETE = 261
        private const val KEY_RIGHT = 262
        private const val KEY_LEFT = 263
        private const val KEY_DOWN = 264
        private const val KEY_UP = 265
        private const val KEY_HOME = 268
        private const val KEY_END = 269
        private const val KEY_KP_ENTER = 335
        private const val KEY_A = 65
        private const val KEY_C = 67
        private const val KEY_V = 86
        private const val KEY_X = 88

        fun wrapWithIndices(
            text: String,
            maxWidthDp: Float,
            fontSizeDp: Float,
            viewportScale: Float,
            fontId: String = HdTextLayout.DEFAULT_FONT_ID
        ): List<WrappedSpanLine> {
            if (text.isEmpty()) return listOf(WrappedSpanLine(0, 0, ""))
            val out = mutableListOf<WrappedSpanLine>()
            val safeW = maxWidthDp.coerceAtLeast(24f)
            var lineStart = 0
            var lastSpace = -1
            var i = 0
            while (i < text.length) {
                val ch = text[i]
                if (ch == '\n') {
                    out.add(WrappedSpanLine(lineStart, i, text.substring(lineStart, i)))
                    i++
                    lineStart = i
                    lastSpace = -1
                    continue
                }
                if (ch == ' ') lastSpace = i
                val w = HdTextLayout.measureWidthDp(text.substring(lineStart, i + 1), fontSizeDp, HdTextLayout.Weight.REGULAR, viewportScale, fontId)
                if (w > safeW && i > lineStart) {
                    if (lastSpace > lineStart) {
                        out.add(WrappedSpanLine(lineStart, lastSpace + 1, text.substring(lineStart, lastSpace)))
                        lineStart = lastSpace + 1
                        lastSpace = -1
                        continue
                    } else {
                        out.add(WrappedSpanLine(lineStart, i, text.substring(lineStart, i)))
                        lineStart = i
                        lastSpace = -1
                        continue
                    }
                }
                i++
            }
            out.add(WrappedSpanLine(lineStart, text.length, text.substring(lineStart)))
            return out
        }

        fun findLineForCursor(lines: List<WrappedSpanLine>, cursor: Int): Int {
            for (idx in lines.indices) {
                val l = lines[idx]
                if (cursor >= l.startIdx && cursor <= l.endIdx) return idx
            }
            return (lines.size - 1).coerceAtLeast(0)
        }

        fun findCursorIndexAtX(
            text: String,
            targetDpX: Float,
            fontSizeDp: Float,
            viewportScale: Float,
            fontId: String = HdTextLayout.DEFAULT_FONT_ID
        ): Int {
            if (text.isEmpty() || targetDpX <= 0f) return 0
            var prevW = 0f
            for (i in 1..text.length) {
                val w = HdTextLayout.measureWidthDp(text.substring(0, i), fontSizeDp, HdTextLayout.Weight.REGULAR, viewportScale, fontId)
                val mid = (prevW + w) * 0.5f
                if (targetDpX < mid) return i - 1
                prevW = w
            }
            return text.length
        }

        fun selectAll(st: HdStateStore.TextInputState) {
            st.selectionAnchor = 0
            st.cursor = st.text.length
            st.resetBlink()
        }

        fun deleteSelection(st: HdStateStore.TextInputState): Boolean {
            val range = st.selectionRange() ?: return false
            st.text = st.text.removeRange(range.first, range.last + 1)
            st.cursor = range.first
            st.selectionAnchor = null
            st.resetBlink()
            return true
        }

        fun insertText(st: HdStateStore.TextInputState, inserted: String, maxLength: Int) {
            st.clampCursor()
            deleteSelection(st)
            val available = (maxLength - st.text.length).coerceAtLeast(0)
            if (available == 0 || inserted.isEmpty()) return
            val clipped = if (inserted.length > available) inserted.substring(0, available) else inserted
            st.text = st.text.substring(0, st.cursor) + clipped + st.text.substring(st.cursor)
            st.cursor += clipped.length
            st.resetBlink()
        }

        fun backspace(st: HdStateStore.TextInputState, wholeWord: Boolean = false): Boolean {
            st.clampCursor()
            if (deleteSelection(st)) return true
            if (st.cursor <= 0) return false
            val target = if (wholeWord) prevWordBoundary(st.text, st.cursor) else st.cursor - 1
            st.text = st.text.removeRange(target, st.cursor)
            st.cursor = target
            st.resetBlink()
            return true
        }

        fun deleteForward(st: HdStateStore.TextInputState, wholeWord: Boolean = false): Boolean {
            st.clampCursor()
            if (deleteSelection(st)) return true
            if (st.cursor >= st.text.length) return false
            val target = if (wholeWord) nextWordBoundary(st.text, st.cursor) else st.cursor + 1
            st.text = st.text.removeRange(st.cursor, target)
            st.resetBlink()
            return true
        }

        fun moveCursor(st: HdStateStore.TextInputState, dir: Int, shift: Boolean, wholeWord: Boolean) {
            st.clampCursor()
            if (shift && st.selectionAnchor == null) {
                st.selectionAnchor = st.cursor
            } else if (!shift && st.hasSelection()) {
                val range = st.selectionRange()!!
                st.cursor = if (dir < 0) range.first else range.last + 1
                st.selectionAnchor = null
                st.resetBlink()
                return
            }
            st.cursor = when {
                dir < 0 && wholeWord -> prevWordBoundary(st.text, st.cursor)
                dir > 0 && wholeWord -> nextWordBoundary(st.text, st.cursor)
                else -> (st.cursor + dir).coerceIn(0, st.text.length)
            }
            if (!shift) st.selectionAnchor = null
            st.clampCursor()
            st.resetBlink()
        }

        fun moveToEdge(st: HdStateStore.TextInputState, start: Boolean, shift: Boolean) {
            st.clampCursor()
            if (shift && st.selectionAnchor == null) {
                st.selectionAnchor = st.cursor
            }
            st.cursor = if (start) 0 else st.text.length
            if (!shift) st.selectionAnchor = null
            st.clampCursor()
            st.resetBlink()
        }

        private fun prevWordBoundary(text: String, from: Int): Int {
            var i = from.coerceIn(0, text.length)
            while (i > 0 && text[i - 1].isWhitespace()) i--
            while (i > 0 && !text[i - 1].isWhitespace()) i--
            return i
        }

        private fun nextWordBoundary(text: String, from: Int): Int {
            var i = from.coerceIn(0, text.length)
            while (i < text.length && text[i].isWhitespace()) i++
            while (i < text.length && !text[i].isWhitespace()) i++
            return i
        }
    }
}
