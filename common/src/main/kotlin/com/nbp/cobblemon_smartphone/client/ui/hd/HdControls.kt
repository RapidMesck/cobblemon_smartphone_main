package com.nbp.cobblemon_smartphone.client.ui.hd

import kotlin.math.abs
import kotlin.math.round

/**
 * Interactive form controls for the HD UI engine:
 * - [SwitchNode]: mobile-style toggle switch with optional label
 * - [SliderNode]: horizontal value slider supporting click, drag, and optional step snapping
 * - [NumberStepperNode]: compact `- [value] +` numeric stepper
 * - [SegmentedControlNode]: unified exclusive option bar
 */

class SwitchNode(
    var label: String = "",
    var checked: Boolean = false,
    var enabled: Boolean = true,
    var fontSizeDp: Float = 9.0f,
    var trackWidthDp: Float = 28f,
    var trackHeightDp: Float = 15f,
    var labelOnLeft: Boolean = true,
    var onToggle: ((Boolean) -> Unit)? = null
) : HdNode() {

    init {
        padding = HdPadding.symmetric(horizontal = 2f, vertical = 2f)
    }

    override fun measureContent(ctx: HdMeasureContext, availContentW: Float, availContentH: Float): Pair<Float, Float> {
        if (label.isEmpty()) return trackWidthDp to trackHeightDp
        val textW = HdTextLayout.measureWidthDp(label, fontSizeDp, HdTextLayout.Weight.MEDIUM, ctx.viewportScale)
        val lineH = HdTextLayout.lineHeightDp(fontSizeDp, HdTextLayout.Weight.MEDIUM, ctx.viewportScale)
        return (textW + 6f + trackWidthDp) to maxOf(lineH, trackHeightDp)
    }

    override fun renderContent(rc: HdRenderContext) {
        val c = rc.colors
        val inner = bounds.inset(padding)
        val hovered = enabled && isHovered(rc)

        val trackY = inner.y + (inner.height - trackHeightDp) * 0.5f
        val trackX = if (label.isEmpty()) {
            inner.x
        } else if (labelOnLeft) {
            inner.right - trackWidthDp
        } else {
            inner.x
        }

        val trackBg = when {
            !enabled -> c.buttonDisabledBg
            checked -> if (hovered) c.primaryHover else c.primary
            hovered -> c.surfaceHover
            else -> c.surfaceAlt
        }
        val trackBorder = when {
            !enabled -> c.textMuted
            checked || hovered -> c.primary
            else -> c.primaryBorder
        }
        val radius = trackHeightDp * 0.5f
        rc.canvas.rect(trackX, trackY, trackWidthDp, trackHeightDp, trackBg, radius, 0.85f, trackBorder)

        val thumbPad = 2f
        val thumbR = ((trackHeightDp - thumbPad * 2f) * 0.5f).coerceAtLeast(2f)
        val thumbCx = if (checked) {
            trackX + trackWidthDp - thumbPad - thumbR
        } else {
            trackX + thumbPad + thumbR
        }
        val thumbCy = trackY + trackHeightDp * 0.5f
        val thumbCol = if (enabled) c.textPrimary else c.textMuted
        rc.canvas.circle(thumbCx, thumbCy, thumbR, thumbCol)

        if (label.isNotEmpty()) {
            val lineH = rc.canvas.lineHeight(fontSizeDp, HdTextLayout.Weight.MEDIUM)
            val textCol = if (enabled) c.textDark else c.textDarkMuted
            val maxTxtW = (inner.width - trackWidthDp - 6f).coerceAtLeast(0f)
            val trimmed = rc.canvas.truncate(label, maxTxtW, fontSizeDp, HdTextLayout.Weight.MEDIUM)
            val textX = if (labelOnLeft) inner.x else trackX + trackWidthDp + 6f
            rc.canvas.text(trimmed, textX, inner.y + (inner.height - lineH) * 0.5f, fontSizeDp, textCol, HdTextLayout.Weight.MEDIUM)
        }
    }

    override fun onMouseDown(dpX: Float, dpY: Float, button: Int, state: HdStateStore): Boolean {
        if (!enabled || button != 0) return false
        checked = !checked
        onToggle?.invoke(checked)
        return true
    }
}

class SliderNode(
    var value: Float = 0f,
    var min: Float = 0f,
    var max: Float = 1f,
    var step: Float = 0f,
    var label: String = "",
    var showValue: Boolean = true,
    var valueFormat: ((Float) -> String)? = null,
    var enabled: Boolean = true,
    var fontSizeDp: Float = 8.6f,
    var trackHeightDp: Float = 5f,
    var thumbRadiusDp: Float = 6f,
    var onChange: ((Float) -> Unit)? = null
) : HdNode() {

    private var dragging: Boolean = false

    init {
        width = HdSizeSpec.Fill
        padding = HdPadding.symmetric(horizontal = 2f, vertical = 2f)
    }

    private fun hasHeader(): Boolean = label.isNotEmpty() || showValue

    override fun measureContent(ctx: HdMeasureContext, availContentW: Float, availContentH: Float): Pair<Float, Float> {
        val w = if (availContentW.isFinite()) availContentW else 140f
        val trackAreaH = maxOf(trackHeightDp, thumbRadiusDp * 2f)
        val headerH = if (hasHeader()) {
            HdTextLayout.lineHeightDp(fontSizeDp, HdTextLayout.Weight.MEDIUM, ctx.viewportScale) + 4f
        } else {
            0f
        }
        return w to (headerH + trackAreaH)
    }

    override fun renderContent(rc: HdRenderContext) {
        val c = rc.colors
        val inner = bounds.inset(padding)
        val hovered = enabled && (dragging || isHovered(rc))

        var trackAreaTop = inner.y
        if (hasHeader()) {
            val lineH = rc.canvas.lineHeight(fontSizeDp, HdTextLayout.Weight.MEDIUM)
            val valStr = if (showValue) formatValue(value) else ""
            val valW = if (valStr.isNotEmpty()) rc.canvas.measureText(valStr, fontSizeDp, HdTextLayout.Weight.BOLD) else 0f

            if (label.isNotEmpty()) {
                val maxLabelW = (inner.width - valW - (if (valW > 0f) 6f else 0f)).coerceAtLeast(0f)
                val trimmed = rc.canvas.truncate(label, maxLabelW, fontSizeDp, HdTextLayout.Weight.MEDIUM)
                val lblCol = if (enabled) c.textDark else c.textDarkMuted
                rc.canvas.text(trimmed, inner.x, inner.y, fontSizeDp, lblCol, HdTextLayout.Weight.MEDIUM)
            }
            if (valStr.isNotEmpty()) {
                val valCol = if (enabled) c.primary else c.textDarkMuted
                rc.canvas.text(valStr, inner.right - valW, inner.y, fontSizeDp, valCol, HdTextLayout.Weight.BOLD)
            }
            trackAreaTop += lineH + 4f
        }

        val trackAreaH = maxOf(trackHeightDp, thumbRadiusDp * 2f)
        val trackLeft = inner.x + thumbRadiusDp
        val trackRight = (inner.right - thumbRadiusDp).coerceAtLeast(trackLeft)
        val trackW = (trackRight - trackLeft).coerceAtLeast(1f)
        val trackCy = trackAreaTop + trackAreaH * 0.5f
        val trackY = trackCy - trackHeightDp * 0.5f
        val trackR = trackHeightDp * 0.5f

        val trackBg = if (enabled) c.surfaceAlt else c.buttonDisabledBg
        rc.canvas.rect(trackLeft, trackY, trackW, trackHeightDp, trackBg, trackR, 0.7f, c.primaryBorder)

        val ratio = normalize(value, min, max)
        val fillW = trackW * ratio
        val fillCol = if (enabled) (if (hovered) c.primaryHover else c.primary) else c.textMuted
        if (fillW > 0.5f) {
            rc.canvas.rect(trackLeft, trackY, fillW.coerceAtLeast(trackHeightDp), trackHeightDp, fillCol, trackR)
        }

        val thumbCx = trackLeft + trackW * ratio
        val thumbFill = if (enabled) c.textPrimary else c.textMuted
        val thumbBorder = if (enabled) (if (hovered) c.primaryHover else c.primary) else c.primaryBorder
        rc.canvas.circle(thumbCx, trackCy, thumbRadiusDp, thumbFill, 1.3f, thumbBorder)
    }

    private fun updateFromDpX(dpX: Float): Boolean {
        val inner = bounds.inset(padding)
        val trackLeft = inner.x + thumbRadiusDp
        val trackRight = (inner.right - thumbRadiusDp).coerceAtLeast(trackLeft)
        val trackW = (trackRight - trackLeft).coerceAtLeast(1f)
        val t = ((dpX - trackLeft) / trackW).coerceIn(0f, 1f)
        val raw = min + (max - min) * t
        val next = snap(raw, min, max, step)
        if (abs(next - value) > 1e-5f) {
            value = next
            onChange?.invoke(next)
            return true
        }
        return false
    }

    override fun onMouseDown(dpX: Float, dpY: Float, button: Int, state: HdStateStore): Boolean {
        if (!enabled || button != 0) return false
        dragging = true
        updateFromDpX(dpX)
        return true
    }

    override fun onMouseDrag(dpX: Float, dpY: Float, state: HdStateStore): Boolean {
        if (!enabled || !dragging) return false
        updateFromDpX(dpX)
        return true
    }

    override fun onMouseUp(dpX: Float, dpY: Float, button: Int, state: HdStateStore): Boolean {
        if (!dragging) return false
        dragging = false
        return true
    }

    fun formatValue(v: Float): String = valueFormat?.invoke(v) ?: formatDefault(v, step)

    companion object {
        fun normalize(value: Float, min: Float, max: Float): Float {
            if (max <= min) return 0f
            return ((value - min) / (max - min)).coerceIn(0f, 1f)
        }

        fun snap(rawValue: Float, min: Float, max: Float, step: Float): Float {
            if (max <= min) return min
            val clamped = rawValue.coerceIn(min, max)
            if (step <= 0f) return clamped
            val steps = round((clamped - min) / step)
            return (min + steps * step).coerceIn(min, max)
        }

        fun formatDefault(value: Float, step: Float): String {
            val isWholeStep = step >= 1f && abs(step - round(step)) < 1e-4f
            if (isWholeStep || abs(value - round(value)) < 1e-4f) {
                return round(value).toInt().toString()
            }
            val oneDec = round(value * 10f) / 10f
            return if (abs(value - oneDec) < 1e-3f) {
                String.format(java.util.Locale.ROOT, "%.1f", value)
            } else {
                String.format(java.util.Locale.ROOT, "%.2f", value)
            }
        }
    }
}

class NumberStepperNode(
    var value: Float = 0f,
    var min: Float = 0f,
    var max: Float = 100f,
    var step: Float = 1f,
    var label: String = "",
    var enabled: Boolean = true,
    var fontSizeDp: Float = 9.0f,
    var buttonSizeDp: Float = 18f,
    var valueWidthDp: Float = 36f,
    var valueFormat: ((Float) -> String)? = null,
    var onChange: ((Float) -> Unit)? = null
) : HdNode() {

    init {
        padding = HdPadding.symmetric(horizontal = 2f, vertical = 2f)
    }

    private fun clusterWidth(): Float = buttonSizeDp * 2f + valueWidthDp

    override fun measureContent(ctx: HdMeasureContext, availContentW: Float, availContentH: Float): Pair<Float, Float> {
        val cw = clusterWidth()
        if (label.isEmpty()) return cw to buttonSizeDp
        val textW = HdTextLayout.measureWidthDp(label, fontSizeDp, HdTextLayout.Weight.MEDIUM, ctx.viewportScale)
        val lineH = HdTextLayout.lineHeightDp(fontSizeDp, HdTextLayout.Weight.MEDIUM, ctx.viewportScale)
        return (textW + 6f + cw) to maxOf(lineH, buttonSizeDp)
    }

    private fun minusBounds(inner: HdBounds): HdBounds {
        val clusterX = if (label.isEmpty()) inner.x else inner.right - clusterWidth()
        val cy = inner.y + (inner.height - buttonSizeDp) * 0.5f
        return HdBounds(clusterX, cy, buttonSizeDp, buttonSizeDp)
    }

    private fun plusBounds(inner: HdBounds): HdBounds {
        val clusterX = if (label.isEmpty()) inner.x else inner.right - clusterWidth()
        val cy = inner.y + (inner.height - buttonSizeDp) * 0.5f
        return HdBounds(clusterX + buttonSizeDp + valueWidthDp, cy, buttonSizeDp, buttonSizeDp)
    }

    override fun renderContent(rc: HdRenderContext) {
        val c = rc.colors
        val inner = bounds.inset(padding)
        val cw = clusterWidth()
        val clusterX = if (label.isEmpty()) inner.x else inner.right - cw
        val cy = inner.y + (inner.height - buttonSizeDp) * 0.5f

        if (label.isNotEmpty()) {
            val lineH = rc.canvas.lineHeight(fontSizeDp, HdTextLayout.Weight.MEDIUM)
            val maxTxtW = (inner.width - cw - 6f).coerceAtLeast(0f)
            val trimmed = rc.canvas.truncate(label, maxTxtW, fontSizeDp, HdTextLayout.Weight.MEDIUM)
            val lblCol = if (enabled) c.textDark else c.textDarkMuted
            rc.canvas.text(trimmed, inner.x, inner.y + (inner.height - lineH) * 0.5f, fontSizeDp, lblCol, HdTextLayout.Weight.MEDIUM)
        }

        // Outer pill container
        rc.canvas.rect(clusterX, cy, cw, buttonSizeDp, c.surface, 7f, 0.8f, c.primaryBorder)

        val minusB = minusBounds(inner)
        val plusB = plusBounds(inner)
        val canDec = enabled && value > min + 1e-5f
        val canInc = enabled && value < max - 1e-5f

        val minusHover = canDec && minusB.contains(rc.mouseDpX, rc.mouseDpY)
        val plusHover = canInc && plusB.contains(rc.mouseDpX, rc.mouseDpY)

        if (minusHover) {
            rc.canvas.rect(minusB.x + 1f, minusB.y + 1f, minusB.width - 2f, minusB.height - 2f, c.surfaceHover, 6f)
        }
        if (plusHover) {
            rc.canvas.rect(plusB.x + 1f, plusB.y + 1f, plusB.width - 2f, plusB.height - 2f, c.surfaceHover, 6f)
        }

        val iconSize = (buttonSizeDp * 0.55f).coerceIn(8f, 12f)
        val minusCol = if (canDec) c.textDark else c.textDarkMuted
        val plusCol = if (canInc) c.textDark else c.textDarkMuted
        rc.canvas.icon(
            HdIconAtlas.Icon.MINUS,
            minusB.x + (minusB.width - iconSize) * 0.5f,
            minusB.y + (minusB.height - iconSize) * 0.5f,
            iconSize,
            minusCol
        )
        rc.canvas.icon(
            HdIconAtlas.Icon.PLUS,
            plusB.x + (plusB.width - iconSize) * 0.5f,
            plusB.y + (plusB.height - iconSize) * 0.5f,
            iconSize,
            plusCol
        )

        // Subtle vertical separators
        val sepCol = (c.primaryBorder and 0x00FFFFFF) or 0x55000000
        rc.canvas.rect(minusB.right, cy + 3f, 0.7f, buttonSizeDp - 6f, sepCol)
        rc.canvas.rect(plusB.x, cy + 3f, 0.7f, buttonSizeDp - 6f, sepCol)

        val valStr = valueFormat?.invoke(value) ?: SliderNode.formatDefault(value, step)
        val lineH = rc.canvas.lineHeight(fontSizeDp, HdTextLayout.Weight.BOLD)
        val trimmedVal = rc.canvas.truncate(valStr, valueWidthDp - 4f, fontSizeDp, HdTextLayout.Weight.BOLD)
        val valCol = if (enabled) c.textDark else c.textDarkMuted
        rc.canvas.textCentered(
            trimmedVal,
            minusB.right + valueWidthDp * 0.5f,
            cy + (buttonSizeDp - lineH) * 0.5f,
            fontSizeDp,
            valCol,
            HdTextLayout.Weight.BOLD
        )
    }

    override fun onMouseDown(dpX: Float, dpY: Float, button: Int, state: HdStateStore): Boolean {
        if (!enabled || button != 0) return false
        val inner = bounds.inset(padding)
        val delta = when {
            minusBounds(inner).contains(dpX, dpY) -> -step.coerceAtLeast(1e-4f)
            plusBounds(inner).contains(dpX, dpY) -> step.coerceAtLeast(1e-4f)
            else -> return false
        }
        val next = SliderNode.snap(value + delta, min, max, step)
        if (abs(next - value) > 1e-5f) {
            value = next
            onChange?.invoke(next)
        }
        return true
    }
}

class SegmentedControlNode(
    var options: List<Option>,
    var selectedId: String,
    var enabled: Boolean = true,
    var fontSizeDp: Float = 8.6f,
    var radius: Float = 8f,
    var onSelect: ((String) -> Unit)? = null
) : HdNode() {

    data class Option(
        val id: String,
        val label: String,
        val icon: HdIconAtlas.Icon? = null
    )

    init {
        width = HdSizeSpec.Fill
        height = HdSizeSpec.Fixed(24f)
    }

    override fun measureContent(ctx: HdMeasureContext, availContentW: Float, availContentH: Float): Pair<Float, Float> {
        val w = if (availContentW.isFinite()) availContentW else (options.size.coerceAtLeast(1) * 56f)
        return w to 24f
    }

    override fun renderContent(rc: HdRenderContext) {
        if (options.isEmpty()) return
        val c = rc.colors
        val inner = bounds.inset(padding)
        val bg = if (enabled) c.surfaceAlt else c.buttonDisabledBg
        rc.canvas.rect(inner.x, inner.y, inner.width, inner.height, bg, radius, 0.8f, c.primaryBorder)

        val inset = 2f
        val contentX = inner.x + inset
        val contentY = inner.y + inset
        val contentW = (inner.width - inset * 2f).coerceAtLeast(0f)
        val contentH = (inner.height - inset * 2f).coerceAtLeast(0f)
        val slotW = contentW / options.size
        val innerRadius = (radius - 2f).coerceAtLeast(2f)

        options.forEachIndexed { idx, opt ->
            val sx = contentX + idx * slotW
            val slotBounds = HdBounds(sx, contentY, slotW, contentH)
            val isSelected = opt.id == selectedId
            val hovered = enabled && slotBounds.contains(rc.mouseDpX, rc.mouseDpY)

            if (isSelected) {
                val activeBg = if (enabled) c.primary else c.textMuted
                rc.canvas.rect(sx, contentY, slotW, contentH, activeBg, innerRadius)
            } else if (hovered) {
                rc.canvas.rect(sx, contentY, slotW, contentH, c.surfaceHover, innerRadius)
            }

            val fg = when {
                isSelected -> c.textPrimary
                !enabled -> c.textDarkMuted
                else -> c.textDark
            }
            val iconSize = 10.5f
            val hasIcon = opt.icon != null
            val hasLabel = opt.label.isNotEmpty()
            val gap = if (hasIcon && hasLabel) 3.5f else 0f
            val maxTxtW = (slotW - 8f - (if (hasIcon) iconSize + gap else 0f)).coerceAtLeast(0f)
            val trimmed = if (hasLabel) rc.canvas.truncate(opt.label, maxTxtW, fontSizeDp, HdTextLayout.Weight.BOLD) else ""
            val txtW = if (trimmed.isNotEmpty()) rc.canvas.measureText(trimmed, fontSizeDp, HdTextLayout.Weight.BOLD) else 0f
            val totalW = (if (hasIcon) iconSize else 0f) + gap + txtW
            var curX = sx + (slotW - totalW) * 0.5f

            opt.icon?.let { ic ->
                rc.canvas.icon(ic, curX, contentY + (contentH - iconSize) * 0.5f, iconSize, fg)
                curX += iconSize + gap
            }
            if (trimmed.isNotEmpty()) {
                val lineH = rc.canvas.lineHeight(fontSizeDp, HdTextLayout.Weight.BOLD)
                rc.canvas.text(trimmed, curX, contentY + (contentH - lineH) * 0.5f, fontSizeDp, fg, HdTextLayout.Weight.BOLD)
            }
        }
    }

    override fun onMouseDown(dpX: Float, dpY: Float, button: Int, state: HdStateStore): Boolean {
        if (!enabled || button != 0 || options.isEmpty()) return false
        val inner = bounds.inset(padding)
        if (!inner.contains(dpX, dpY)) return false
        val contentX = inner.x + 2f
        val contentW = (inner.width - 4f).coerceAtLeast(1f)
        val slotW = contentW / options.size
        val idx = ((dpX - contentX) / slotW).toInt().coerceIn(0, options.lastIndex)
        val nextId = options[idx].id
        selectedId = nextId
        onSelect?.invoke(nextId)
        return true
    }
}
