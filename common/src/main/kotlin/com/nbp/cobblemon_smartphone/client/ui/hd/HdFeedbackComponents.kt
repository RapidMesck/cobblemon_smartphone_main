package com.nbp.cobblemon_smartphone.client.ui.hd

/**
 * Feedback & data display components for the HD UI engine:
 * - [BadgeNode]: compact semantic tag/chip with optional icon
 * - [StatBarNode]: labeled horizontal stat bar with value readout and proportional fill
 * - [EmptyStateNode]: centered empty-state view with icon, title, description, and optional action button
 */

class BadgeNode(
    var label: String,
    var icon: HdIconAtlas.Icon? = null,
    var variant: Variant = Variant.PRIMARY,
    var customBg: HdColorSpec? = null,
    var customTextColor: HdColorSpec? = null,
    var fontSizeDp: Float = 7.8f,
    var radius: Float = 9f
) : HdNode() {

    enum class Variant { PRIMARY, SUCCESS, WARNING, DANGER, NEUTRAL, GOLD }

    init {
        padding = HdPadding.symmetric(horizontal = 6.5f, vertical = 2.5f)
    }

    override fun measureContent(ctx: HdMeasureContext, availContentW: Float, availContentH: Float): Pair<Float, Float> {
        val iconSize = 9.5f
        val iconW = if (icon != null) iconSize + (if (label.isNotEmpty()) 3.5f else 0f) else 0f
        val textW = if (label.isNotEmpty()) {
            HdTextLayout.measureWidthDp(label, fontSizeDp, HdTextLayout.Weight.BOLD, ctx.viewportScale)
        } else 0f
        val lineH = HdTextLayout.lineHeightDp(fontSizeDp, HdTextLayout.Weight.BOLD, ctx.viewportScale)
        return (iconW + textW) to maxOf(lineH, if (icon != null) iconSize else 0f)
    }

    override fun renderBackground(rc: HdRenderContext) {
        val c = rc.colors
        val bg = customBg?.resolve(c) ?: when (variant) {
            Variant.PRIMARY -> c.primary
            Variant.SUCCESS -> c.success
            Variant.WARNING -> 0xFFD97706.toInt()
            Variant.DANGER -> c.danger
            Variant.NEUTRAL -> c.surfaceAlt
            Variant.GOLD -> c.gold
        }
        val borderCol = when (variant) {
            Variant.NEUTRAL -> c.primaryBorder
            else -> 0x33FFFFFF
        }
        rc.canvas.rect(bounds.x, bounds.y, bounds.width, bounds.height, bg, radius, 0.7f, borderCol)
    }

    override fun renderContent(rc: HdRenderContext) {
        val c = rc.colors
        val inner = bounds.inset(padding)
        val fg = customTextColor?.resolve(c) ?: when (variant) {
            Variant.NEUTRAL -> c.textDark
            Variant.GOLD -> 0xFF1B1917.toInt()
            else -> c.textPrimary
        }

        val iconSize = 9.5f
        var curX = inner.x
        icon?.let { ic ->
            rc.canvas.icon(ic, curX, inner.y + (inner.height - iconSize) * 0.5f, iconSize, fg)
            curX += iconSize + 3.5f
        }
        if (label.isNotEmpty()) {
            val lineH = rc.canvas.lineHeight(fontSizeDp, HdTextLayout.Weight.BOLD)
            val maxTxtW = (inner.right - curX).coerceAtLeast(0f)
            val trimmed = rc.canvas.truncate(label, maxTxtW, fontSizeDp, HdTextLayout.Weight.BOLD)
            rc.canvas.text(trimmed, curX, inner.y + (inner.height - lineH) * 0.5f, fontSizeDp, fg, HdTextLayout.Weight.BOLD)
        }
    }
}

class StatBarNode(
    var label: String,
    var value: Float,
    var maxValue: Float = 255f,
    var valueText: String? = null,
    var labelWidthDp: Float = 32f,
    var valueWidthDp: Float = 26f,
    var barHeightDp: Float = 7f,
    var fillColor: HdColorSpec = HdColorSpec.Token("primary"),
    var trackColor: HdColorSpec = HdColorSpec.Token("surface_alt"),
    var fontSizeDp: Float = 8.4f
) : HdNode() {

    init {
        width = HdSizeSpec.Fill
        padding = HdPadding.symmetric(horizontal = 2f, vertical = 1.5f)
    }

    fun normalizedRatio(): Float =
        if (maxValue <= 0f) 0f else (value / maxValue).coerceIn(0f, 1f)

    fun formattedValue(): String = valueText ?: SliderNode.formatDefault(value, 1f)

    override fun measureContent(ctx: HdMeasureContext, availContentW: Float, availContentH: Float): Pair<Float, Float> {
        val w = if (availContentW.isFinite()) availContentW else (labelWidthDp + valueWidthDp + 90f)
        val lineH = HdTextLayout.lineHeightDp(fontSizeDp, HdTextLayout.Weight.BOLD, ctx.viewportScale)
        return w to maxOf(lineH, barHeightDp)
    }

    override fun renderContent(rc: HdRenderContext) {
        val c = rc.colors
        val inner = bounds.inset(padding)
        val lineH = rc.canvas.lineHeight(fontSizeDp, HdTextLayout.Weight.BOLD)
        val textY = inner.y + (inner.height - lineH) * 0.5f

        val trimmedLbl = rc.canvas.truncate(label, labelWidthDp, fontSizeDp, HdTextLayout.Weight.BOLD)
        rc.canvas.text(trimmedLbl, inner.x, textY, fontSizeDp, c.textDarkMuted, HdTextLayout.Weight.BOLD)

        val valStr = formattedValue()
        val valX = inner.x + labelWidthDp + 4f
        val trimmedVal = rc.canvas.truncate(valStr, valueWidthDp, fontSizeDp, HdTextLayout.Weight.BOLD)
        val valW = rc.canvas.measureText(trimmedVal, fontSizeDp, HdTextLayout.Weight.BOLD)
        rc.canvas.text(trimmedVal, valX + (valueWidthDp - valW).coerceAtLeast(0f), textY, fontSizeDp, c.textDark, HdTextLayout.Weight.BOLD)

        val barX = valX + valueWidthDp + 6f
        val barW = (inner.right - barX).coerceAtLeast(0f)
        if (barW > 2f) {
            val barY = inner.y + (inner.height - barHeightDp) * 0.5f
            val r = barHeightDp * 0.5f
            rc.canvas.rect(barX, barY, barW, barHeightDp, trackColor.resolve(c), r, 0.6f, c.primaryBorder)
            val ratio = normalizedRatio()
            if (ratio > 0f) {
                val fillW = ((barW - 1.2f) * ratio).coerceAtLeast(minOf(barHeightDp, barW - 1.2f))
                rc.canvas.rect(barX + 0.6f, barY + 0.6f, fillW, (barHeightDp - 1.2f).coerceAtLeast(1f), fillColor.resolve(c), (r - 0.4f).coerceAtLeast(0f))
            }
        }
    }
}

class EmptyStateNode(
    var title: String,
    var description: String? = null,
    var icon: HdIconAtlas.Icon? = HdIconAtlas.Icon.INFO,
    var iconColor: HdColorSpec = HdColorSpec.Token("primary"),
    var actionLabel: String? = null,
    var actionIcon: HdIconAtlas.Icon? = null,
    var onAction: (() -> Unit)? = null
) : HdNode() {

    private var cachedDescLines: List<String> = emptyList()
    private var cachedDescLineH: Float = 11f
    private var cachedTitleH: Float = 13f
    private var actionButtonBounds: HdBounds = HdBounds.EMPTY
    private var actionBtnW: Float = 0f
    private var actionBtnH: Float = 22f

    init {
        width = HdSizeSpec.Fill
        padding = HdPadding.symmetric(horizontal = 16f, vertical = 20f)
    }

    override fun measureContent(ctx: HdMeasureContext, availContentW: Float, availContentH: Float): Pair<Float, Float> {
        val w = if (availContentW.isFinite()) availContentW else 180f
        var h = 0f

        if (icon != null) {
            h += 32f + 8f
        }

        cachedTitleH = HdTextLayout.lineHeightDp(10.6f, HdTextLayout.Weight.BOLD, ctx.viewportScale)
        h += cachedTitleH

        if (!description.isNullOrBlank()) {
            cachedDescLineH = HdTextLayout.lineHeightDp(8.8f, HdTextLayout.Weight.REGULAR, ctx.viewportScale)
            cachedDescLines = HdTextLayout.wrapPlainText(description!!, w, 8.8f, HdTextLayout.Weight.REGULAR, ctx.viewportScale)
            h += 4f + cachedDescLines.size.coerceAtLeast(1) * cachedDescLineH
        } else {
            cachedDescLines = emptyList()
        }

        if (!actionLabel.isNullOrBlank()) {
            val lblW = HdTextLayout.measureWidthDp(actionLabel!!, 9.0f, HdTextLayout.Weight.BOLD, ctx.viewportScale)
            val icW = if (actionIcon != null) 11f + 4f else 0f
            actionBtnW = (lblW + icW + 20f).coerceAtMost(w)
            actionBtnH = 22f
            h += 10f + actionBtnH
        } else {
            actionBtnW = 0f
        }

        return w to h
    }

    override fun arrange(x: Float, y: Float, w: Float, h: Float) {
        super.arrange(x, y, w, h)
        val inner = bounds.inset(padding)
        if (!actionLabel.isNullOrBlank() && actionBtnW > 0f) {
            val btnX = inner.x + (inner.width - actionBtnW) * 0.5f
            val btnY = inner.bottom - actionBtnH
            actionButtonBounds = HdBounds(btnX, btnY, actionBtnW, actionBtnH)
        } else {
            actionButtonBounds = HdBounds.EMPTY
        }
    }

    override fun renderContent(rc: HdRenderContext) {
        val c = rc.colors
        val inner = bounds.inset(padding)
        var curY = inner.y

        icon?.let { ic ->
            val circleR = 16f
            val cx = inner.centerX
            val cy = curY + circleR
            val icCol = iconColor.resolve(c)
            val badgeBg = (icCol and 0x00FFFFFF) or 0x22000000
            rc.canvas.circle(cx, cy, circleR, badgeBg, 0.8f, (icCol and 0x00FFFFFF) or 0x55000000)
            rc.canvas.icon(ic, cx - 8f, cy - 8f, 16f, icCol)
            curY += 32f + 8f
        }

        val trimmedTitle = rc.canvas.truncate(title, inner.width, 10.6f, HdTextLayout.Weight.BOLD)
        rc.canvas.textCentered(trimmedTitle, inner.centerX, curY, 10.6f, c.textDark, HdTextLayout.Weight.BOLD)
        curY += cachedTitleH

        if (cachedDescLines.isNotEmpty()) {
            curY += 4f
            for (line in cachedDescLines) {
                rc.canvas.textCentered(line, inner.centerX, curY, 8.8f, c.textDarkMuted, HdTextLayout.Weight.REGULAR)
                curY += cachedDescLineH
            }
        }

        if (!actionLabel.isNullOrBlank() && actionButtonBounds.width > 0f && actionButtonBounds.height > 0f) {
            val b = actionButtonBounds
            val hovered = b.contains(rc.mouseDpX, rc.mouseDpY)
            val bg = if (hovered) c.primaryHover else c.primary
            rc.canvas.rect(b.x, b.y, b.width, b.height, bg, 9f)
            val lbl = actionLabel!!
            val txtW = rc.canvas.measureText(lbl, 9.0f, HdTextLayout.Weight.BOLD)
            val icW = if (actionIcon != null) 11f else 0f
            val gap = if (actionIcon != null) 4f else 0f
            val totalW = icW + gap + txtW
            var penX = b.x + (b.width - totalW) * 0.5f
            actionIcon?.let { ic ->
                rc.canvas.icon(ic, penX, b.y + (b.height - 11f) * 0.5f, 11f, c.textPrimary)
                penX += 11f + gap
            }
            val lineH = rc.canvas.lineHeight(9.0f, HdTextLayout.Weight.BOLD)
            rc.canvas.text(lbl, penX, b.y + (b.height - lineH) * 0.5f, 9.0f, c.textPrimary, HdTextLayout.Weight.BOLD)
        }
    }

    override fun onMouseDown(dpX: Float, dpY: Float, button: Int, state: HdStateStore): Boolean {
        if (button == 0 && onAction != null && actionButtonBounds.contains(dpX, dpY)) {
            onAction?.invoke()
            return true
        }
        return super.onMouseDown(dpX, dpY, button, state)
    }
}
