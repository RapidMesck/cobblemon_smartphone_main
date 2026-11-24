package com.nbp.cobblemon_smartphone.client.ui.hd

/**
 * Retained UI state keyed by node ID so declarative component trees can rebuild freely
 * without losing scroll offsets, text input cursors/selections, active tabs, or focus.
 */
class HdStateStore {

    data class ScrollState(
        var offset: Float = 0f,
        var maxScroll: Float = 0f,
        var dragging: Boolean = false,
        var dragStartMouseDp: Float = 0f,
        var dragStartOffset: Float = 0f
    )

    data class TextInputState(
        var text: String = "",
        var cursor: Int = 0,
        var selectionAnchor: Int? = null,
        var scrollOffsetX: Float = 0f,
        var lastEditTimeMs: Long = System.currentTimeMillis()
    ) {
        fun clampCursor() {
            cursor = cursor.coerceIn(0, text.length)
            selectionAnchor = selectionAnchor?.coerceIn(0, text.length)?.takeIf { it != cursor }
        }

        fun hasSelection(): Boolean = selectionAnchor != null && selectionAnchor != cursor

        fun selectionRange(): IntRange? {
            val anchor = selectionAnchor ?: return null
            if (anchor == cursor) return null
            return minOf(anchor, cursor) until maxOf(anchor, cursor)
        }

        fun selectedText(): String {
            val r = selectionRange() ?: return ""
            return text.substring(r.first, r.last + 1)
        }

        fun resetBlink() {
            lastEditTimeMs = System.currentTimeMillis()
        }
    }

    enum class ToastKind { INFO, SUCCESS, WARNING, ERROR }

    data class ToastState(
        val message: String,
        val kind: ToastKind = ToastKind.INFO,
        val icon: HdIconAtlas.Icon? = null,
        val createdAtMs: Long = System.currentTimeMillis(),
        val durationMs: Long = 2600L
    ) {
        fun isExpired(nowMs: Long = System.currentTimeMillis()): Boolean =
            nowMs - createdAtMs >= durationMs

        fun alpha(nowMs: Long = System.currentTimeMillis()): Float {
            val elapsed = (nowMs - createdAtMs).coerceAtLeast(0L)
            if (elapsed >= durationMs) return 0f
            val fadeIn = (elapsed / 160f).coerceIn(0f, 1f)
            val remaining = (durationMs - elapsed).coerceAtLeast(0L)
            val fadeOut = (remaining / 280f).coerceIn(0f, 1f)
            return minOf(fadeIn, fadeOut)
        }
    }

    private val scrollStates = LinkedHashMap<String, ScrollState>()
    private val inputStates = LinkedHashMap<String, TextInputState>()
    private val localValues = LinkedHashMap<String, String>()

    var focusedId: String? = null
    var hoveredId: String? = null
    var pressedId: String? = null
    var activeToast: ToastState? = null
    var activeInputHandler: ((TextInputKeyEvent) -> Boolean)? = null
    var activeCharHandler: ((Char) -> Boolean)? = null

    data class TextInputKeyEvent(val keyCode: Int, val scanCode: Int, val modifiers: Int)

    fun showToast(
        message: String,
        kind: ToastKind = ToastKind.INFO,
        icon: HdIconAtlas.Icon? = null,
        durationMs: Long = 2600L,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val defaultIcon = icon ?: when (kind) {
            ToastKind.INFO -> HdIconAtlas.Icon.INFO
            ToastKind.SUCCESS -> HdIconAtlas.Icon.CHECK
            ToastKind.WARNING -> HdIconAtlas.Icon.WARNING
            ToastKind.ERROR -> HdIconAtlas.Icon.CLOSE
        }
        activeToast = ToastState(
            message = message,
            kind = kind,
            icon = defaultIcon,
            createdAtMs = nowMs,
            durationMs = durationMs.coerceAtLeast(400L)
        )
    }

    fun currentToast(nowMs: Long = System.currentTimeMillis()): ToastState? {
        val t = activeToast ?: return null
        if (t.isExpired(nowMs)) {
            activeToast = null
            return null
        }
        return t
    }

    fun dismissToast() {
        activeToast = null
    }

    fun scroll(id: String): ScrollState = scrollStates.getOrPut(id) { ScrollState() }

    fun input(id: String, initialText: String = ""): TextInputState =
        inputStates.getOrPut(id) {
            TextInputState(text = initialText, cursor = initialText.length)
        }

    fun getInputText(id: String): String = inputStates[id]?.text ?: ""

    fun allInputValues(): Map<String, String> = inputStates.mapValues { it.value.text }

    fun setValue(key: String, value: String) {
        localValues[key] = value
    }

    fun getValue(key: String, default: String = ""): String = localValues[key] ?: default

    fun toggleBool(key: String, default: Boolean = false): Boolean {
        val next = !(getValue(key, default.toString()).toBooleanStrictOrNull() ?: default)
        localValues[key] = next.toString()
        return next
    }

    fun getBool(key: String, default: Boolean = false): Boolean =
        localValues[key]?.toBooleanStrictOrNull() ?: default

    fun clearFocus() {
        focusedId = null
        activeInputHandler = null
        activeCharHandler = null
    }
}
