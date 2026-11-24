package com.nbp.cobblemon_smartphone.client.ui.hd

/**
 * Higher-level layout components for the HD UI engine:
 * - [FlowRowNode]: horizontal wrapping layout for chips, tags, filters, and type badges
 * - [AccordionNode]: collapsible section with clickable header and chevron indicator
 * - [ListTileNode]: mobile-style list row with leading icon, title/subtitle, trailing text/widget, and optional chevron
 */

class FlowRowNode(
    var hGap: Float = 6f,
    var vGap: Float = 6f,
    var lineAlign: HdAlign = HdAlign.START,
    var crossAlign: HdAlign = HdAlign.CENTER
) : HdContainerNode() {

    private data class Line(
        val nodes: MutableList<HdNode> = mutableListOf(),
        var width: Float = 0f,
        var height: Float = 0f
    )

    private var cachedLines: List<Line> = emptyList()

    override fun measureContent(
        ctx: HdMeasureContext,
        availContentW: Float,
        availContentH: Float
    ): Pair<Float, Float> {
        val visibleChildren = children.filter { it.visible }
        if (visibleChildren.isEmpty()) {
            cachedLines = emptyList()
            return 0f to 0f
        }

        val maxW = if (availContentW.isFinite()) availContentW else Float.POSITIVE_INFINITY
        val lines = mutableListOf<Line>()
        var current = Line()

        for (child in visibleChildren) {
            child.measure(ctx, maxW, Float.POSITIVE_INFINITY)
            val cw = child.measuredWidth
            val ch = child.measuredHeight

            val neededW = if (current.nodes.isEmpty()) cw else current.width + hGap + cw
            if (current.nodes.isNotEmpty() && neededW > maxW) {
                lines.add(current)
                current = Line()
            }
            if (current.nodes.isNotEmpty()) {
                current.width += hGap
            }
            current.nodes.add(child)
            current.width += cw
            if (ch > current.height) current.height = ch
        }
        if (current.nodes.isNotEmpty()) {
            lines.add(current)
        }

        cachedLines = lines
        val totalW = lines.maxOfOrNull { it.width } ?: 0f
        val totalH = lines.sumOf { it.height.toDouble() }.toFloat() + vGap * (lines.size - 1).coerceAtLeast(0)
        return totalW to totalH
    }

    override fun arrange(x: Float, y: Float, w: Float, h: Float) {
        super.arrange(x, y, w, h)
        val inner = bounds.inset(padding)
        if (cachedLines.isEmpty()) return

        var curY = inner.y
        for (line in cachedLines) {
            var curX = when (lineAlign) {
                HdAlign.START, HdAlign.STRETCH -> inner.x
                HdAlign.CENTER -> inner.x + (inner.width - line.width).coerceAtLeast(0f) * 0.5f
                HdAlign.END -> inner.x + (inner.width - line.width).coerceAtLeast(0f)
            }
            for (child in line.nodes) {
                val cw = child.measuredWidth.coerceAtMost(inner.width)
                val ch = if (crossAlign == HdAlign.STRETCH) line.height else child.measuredHeight
                val cy = when (crossAlign) {
                    HdAlign.START, HdAlign.STRETCH -> curY
                    HdAlign.CENTER -> curY + (line.height - ch) * 0.5f
                    HdAlign.END -> curY + (line.height - ch)
                }
                child.arrange(curX, cy, cw, ch)
                curX += cw + hGap
            }
            curY += line.height + vGap
        }
    }
}

class AccordionNode(
    var title: String,
    var expanded: Boolean = false,
    var subtitle: String? = null,
    var icon: HdIconAtlas.Icon? = null,
    var gap: Float = 6f,
    var headerPadding: HdPadding = HdPadding.symmetric(horizontal = 9f, vertical = 7f),
    var bodyPadding: HdPadding = HdPadding.only(left = 9f, right = 9f, top = 6f, bottom = 9f),
    var radius: Float = 9f,
    var titleSizeDp: Float = 9.8f,
    var subtitleSizeDp: Float = 7.8f,
    var onToggle: ((Boolean) -> Unit)? = null
) : HdContainerNode() {

    private var cachedHeaderHeight: Float = 26f
    private var headerBounds: HdBounds = HdBounds.EMPTY

    init {
        width = HdSizeSpec.Fill
    }

    override fun measureContent(
        ctx: HdMeasureContext,
        availContentW: Float,
        availContentH: Float
    ): Pair<Float, Float> {
        val targetW = if (availContentW.isFinite()) availContentW else 180f
        val titleH = HdTextLayout.lineHeightDp(titleSizeDp, HdTextLayout.Weight.BOLD, ctx.viewportScale)
        val subH = if (!subtitle.isNullOrBlank()) {
            2f + HdTextLayout.lineHeightDp(subtitleSizeDp, HdTextLayout.Weight.REGULAR, ctx.viewportScale)
        } else 0f
        val iconH = if (icon != null) 13f else 0f
        cachedHeaderHeight = maxOf(titleH + subH, iconH, 12f) + headerPadding.vertical

        if (!expanded) {
            return targetW to cachedHeaderHeight
        }

        val visibleChildren = children.filter { it.visible }
        if (visibleChildren.isEmpty()) {
            return targetW to cachedHeaderHeight
        }

        val bodyAvailW = (targetW - bodyPadding.horizontal).coerceAtLeast(0f)
        var bodyH = gap * (visibleChildren.size - 1)
        for (child in visibleChildren) {
            child.measure(ctx, bodyAvailW, Float.POSITIVE_INFINITY)
            bodyH += child.measuredHeight
        }
        val totalH = cachedHeaderHeight + 0.8f + bodyPadding.vertical + bodyH
        return targetW to totalH
    }

    override fun arrange(x: Float, y: Float, w: Float, h: Float) {
        super.arrange(x, y, w, h)
        val inner = bounds.inset(padding)
        headerBounds = HdBounds(inner.x, inner.y, inner.width, cachedHeaderHeight)

        if (!expanded) return

        val visibleChildren = children.filter { it.visible }
        if (visibleChildren.isEmpty()) return

        val bodyX = inner.x + bodyPadding.left
        val bodyW = (inner.width - bodyPadding.horizontal).coerceAtLeast(0f)
        var curY = headerBounds.bottom + 0.8f + bodyPadding.top
        for (child in visibleChildren) {
            val cw = if (child.width is HdSizeSpec.Fill) bodyW else child.measuredWidth.coerceAtMost(bodyW)
            child.arrange(bodyX, curY, cw, child.measuredHeight)
            curY += child.measuredHeight + gap
        }
    }

    override fun renderBackground(rc: HdRenderContext) {
        if (background != null) {
            super.renderBackground(rc)
            return
        }
        val c = rc.colors
        rc.canvas.rect(bounds.x, bounds.y, bounds.width, bounds.height, c.surface, radius, 0.8f, c.primaryBorder)
    }

    override fun renderContent(rc: HdRenderContext) {
        val c = rc.colors
        val hdrHovered = headerBounds.contains(rc.mouseDpX, rc.mouseDpY)
        if (hdrHovered) {
            rc.canvas.rect(
                headerBounds.x + 1f,
                headerBounds.y + 1f,
                (headerBounds.width - 2f).coerceAtLeast(0f),
                (headerBounds.height - 2f).coerceAtLeast(0f),
                c.surfaceHover,
                (radius - 1f).coerceAtLeast(2f)
            )
        }

        val hInner = headerBounds.inset(headerPadding)
        var curX = hInner.x
        icon?.let { ic ->
            val iconSize = 13f
            rc.canvas.icon(ic, curX, hInner.y + (hInner.height - iconSize) * 0.5f, iconSize, c.primary)
            curX += iconSize + 6f
        }

        val chevronSize = 11f
        val chevronX = hInner.right - chevronSize
        val textMaxW = (chevronX - curX - 6f).coerceAtLeast(0f)

        val titleH = rc.canvas.lineHeight(titleSizeDp, HdTextLayout.Weight.BOLD)
        val hasSub = !subtitle.isNullOrBlank()
        val subH = if (hasSub) rc.canvas.lineHeight(subtitleSizeDp, HdTextLayout.Weight.REGULAR) else 0f
        val textBlockH = titleH + (if (hasSub) 2f + subH else 0f)
        val textTopY = hInner.y + (hInner.height - textBlockH) * 0.5f

        val trimmedTitle = rc.canvas.truncate(title, textMaxW, titleSizeDp, HdTextLayout.Weight.BOLD)
        rc.canvas.text(trimmedTitle, curX, textTopY, titleSizeDp, c.textDark, HdTextLayout.Weight.BOLD)

        if (hasSub) {
            val trimmedSub = rc.canvas.truncate(subtitle!!, textMaxW, subtitleSizeDp, HdTextLayout.Weight.REGULAR)
            rc.canvas.text(trimmedSub, curX, textTopY + titleH + 2f, subtitleSizeDp, c.textDarkMuted, HdTextLayout.Weight.REGULAR)
        }

        val chevronIcon = if (expanded) HdIconAtlas.Icon.CHEVRON_UP else HdIconAtlas.Icon.CHEVRON_DOWN
        rc.canvas.icon(
            chevronIcon,
            chevronX,
            hInner.y + (hInner.height - chevronSize) * 0.5f,
            chevronSize,
            c.textDarkMuted
        )

        if (expanded) {
            val visibleChildren = children.filter { it.visible }
            if (visibleChildren.isNotEmpty()) {
                val divCol = (c.primaryBorder and 0x00FFFFFF) or 0x59000000
                rc.canvas.rect(headerBounds.x + 6f, headerBounds.bottom, (headerBounds.width - 12f).coerceAtLeast(0f), 0.8f, divCol)
                for (child in visibleChildren) {
                    child.render(rc)
                }
            }
        }
    }

    override fun hitTest(dpX: Float, dpY: Float, outPath: MutableList<HdNode>) {
        if (!visible || !bounds.contains(dpX, dpY)) return
        outPath.add(this)
        if (expanded) {
            for (child in children) {
                child.hitTest(dpX, dpY, outPath)
            }
        }
    }

    override fun onMouseDown(dpX: Float, dpY: Float, button: Int, state: HdStateStore): Boolean {
        if (button == 0 && headerBounds.contains(dpX, dpY)) {
            expanded = !expanded
            onToggle?.invoke(expanded)
            return true
        }
        return super.onMouseDown(dpX, dpY, button, state)
    }
}

class ListTileNode(
    var title: String,
    var subtitle: String? = null,
    var leadingIcon: HdIconAtlas.Icon? = null,
    var leadingIconColor: HdColorSpec = HdColorSpec.Token("primary"),
    var trailingText: String? = null,
    var showChevron: Boolean = false,
    var enabled: Boolean = true,
    var radius: Float = 8f,
    var showBackground: Boolean = true,
    var titleSizeDp: Float = 9.6f,
    var subtitleSizeDp: Float = 7.8f
) : HdContainerNode() {

    private var trailingChildrenWidth: Float = 0f

    init {
        width = HdSizeSpec.Fill
        padding = HdPadding.symmetric(horizontal = 9f, vertical = 6f)
    }

    override fun measureContent(
        ctx: HdMeasureContext,
        availContentW: Float,
        availContentH: Float
    ): Pair<Float, Float> {
        val visibleTrailing = children.filter { it.visible }
        var trailingW = 0f
        var trailingMaxH = 0f
        for ((idx, child) in visibleTrailing.withIndex()) {
            child.measure(ctx, availContentW, Float.POSITIVE_INFINITY)
            if (idx > 0) trailingW += 6f
            trailingW += child.measuredWidth
            if (child.measuredHeight > trailingMaxH) trailingMaxH = child.measuredHeight
        }
        trailingChildrenWidth = trailingW

        val leadingW = if (leadingIcon != null) 14f + 8f else 0f
        val trailingTxtW = if (!trailingText.isNullOrBlank()) {
            HdTextLayout.measureWidthDp(trailingText!!, 8.6f, HdTextLayout.Weight.MEDIUM, ctx.viewportScale) + 6f
        } else 0f
        val chevronW = if (showChevron) 11f + 4f else 0f

        val titleW = HdTextLayout.measureWidthDp(title, titleSizeDp, HdTextLayout.Weight.BOLD, ctx.viewportScale)
        val subW = if (!subtitle.isNullOrBlank()) {
            HdTextLayout.measureWidthDp(subtitle!!, subtitleSizeDp, HdTextLayout.Weight.REGULAR, ctx.viewportScale)
        } else 0f
        val titleH = HdTextLayout.lineHeightDp(titleSizeDp, HdTextLayout.Weight.BOLD, ctx.viewportScale)
        val subH = if (!subtitle.isNullOrBlank()) {
            2f + HdTextLayout.lineHeightDp(subtitleSizeDp, HdTextLayout.Weight.REGULAR, ctx.viewportScale)
        } else 0f

        val contentW = leadingW + maxOf(titleW, subW) + trailingTxtW + (if (trailingW > 0f) trailingW + 6f else 0f) + chevronW
        val contentH = maxOf(titleH + subH, if (leadingIcon != null) 14f else 0f, trailingMaxH, 14f)
        val finalW = if (availContentW.isFinite()) availContentW else contentW
        return finalW to contentH
    }

    override fun arrange(x: Float, y: Float, w: Float, h: Float) {
        super.arrange(x, y, w, h)
        val visibleTrailing = children.filter { it.visible }
        if (visibleTrailing.isEmpty()) return

        val inner = bounds.inset(padding)
        val chevronReserve = if (showChevron) 15f else 0f
        var rightEdge = inner.right - chevronReserve

        for (child in visibleTrailing.asReversed()) {
            val cw = child.measuredWidth
            val ch = child.measuredHeight
            val cx = (rightEdge - cw).coerceAtLeast(inner.x)
            val cy = inner.y + (inner.height - ch) * 0.5f
            child.arrange(cx, cy, cw, ch)
            rightEdge = cx - 6f
        }
    }

    override fun renderBackground(rc: HdRenderContext) {
        if (background != null) {
            super.renderBackground(rc)
            return
        }
        if (!showBackground) return
        val c = rc.colors
        val hovered = enabled && onClick != null && isHovered(rc)
        val bg = when {
            !enabled -> c.buttonDisabledBg
            hovered -> c.surfaceHover
            else -> c.surface
        }
        rc.canvas.rect(bounds.x, bounds.y, bounds.width, bounds.height, bg, radius, 0.8f, c.primaryBorder)
    }

    override fun renderContent(rc: HdRenderContext) {
        val c = rc.colors
        val inner = bounds.inset(padding)
        var leftX = inner.x

        leadingIcon?.let { ic ->
            val icSize = 14f
            val icCol = if (enabled) leadingIconColor.resolve(c) else c.textDarkMuted
            rc.canvas.icon(ic, leftX, inner.y + (inner.height - icSize) * 0.5f, icSize, icCol)
            leftX += icSize + 8f
        }

        var rightX = inner.right
        if (showChevron) {
            val chevSize = 11f
            rc.canvas.icon(
                HdIconAtlas.Icon.CHEVRON_RIGHT,
                rightX - chevSize,
                inner.y + (inner.height - chevSize) * 0.5f,
                chevSize,
                c.textDarkMuted
            )
            rightX -= chevSize + 4f
        }

        val visibleTrailing = children.filter { it.visible }
        if (visibleTrailing.isNotEmpty()) {
            for (child in visibleTrailing) {
                child.render(rc)
            }
            rightX -= trailingChildrenWidth + 6f
        }

        if (!trailingText.isNullOrBlank()) {
            val tStr = trailingText!!
            val tW = rc.canvas.measureText(tStr, 8.6f, HdTextLayout.Weight.MEDIUM)
            val tH = rc.canvas.lineHeight(8.6f, HdTextLayout.Weight.MEDIUM)
            val tCol = if (enabled) c.textDarkMuted else c.textMuted
            rc.canvas.text(tStr, rightX - tW, inner.y + (inner.height - tH) * 0.5f, 8.6f, tCol, HdTextLayout.Weight.MEDIUM)
            rightX -= tW + 6f
        }

        val maxTxtW = (rightX - leftX).coerceAtLeast(0f)
        val titleH = rc.canvas.lineHeight(titleSizeDp, HdTextLayout.Weight.BOLD)
        val hasSub = !subtitle.isNullOrBlank()
        val subH = if (hasSub) rc.canvas.lineHeight(subtitleSizeDp, HdTextLayout.Weight.REGULAR) else 0f
        val textBlockH = titleH + (if (hasSub) 2f + subH else 0f)
        val textTopY = inner.y + (inner.height - textBlockH) * 0.5f

        val titleCol = if (enabled) c.textDark else c.textDarkMuted
        val trimmedTitle = rc.canvas.truncate(title, maxTxtW, titleSizeDp, HdTextLayout.Weight.BOLD)
        rc.canvas.text(trimmedTitle, leftX, textTopY, titleSizeDp, titleCol, HdTextLayout.Weight.BOLD)

        if (hasSub) {
            val trimmedSub = rc.canvas.truncate(subtitle!!, maxTxtW, subtitleSizeDp, HdTextLayout.Weight.REGULAR)
            rc.canvas.text(trimmedSub, leftX, textTopY + titleH + 2f, subtitleSizeDp, c.textDarkMuted, HdTextLayout.Weight.REGULAR)
        }
    }

    override fun onMouseDown(dpX: Float, dpY: Float, button: Int, state: HdStateStore): Boolean {
        if (!enabled) return false
        return super.onMouseDown(dpX, dpY, button, state)
    }
}
