package com.nbp.cobblemon_smartphone.client.ui.hd

import kotlin.math.ceil

abstract class HdContainerNode : HdNode() {
    val children = mutableListOf<HdNode>()

    fun add(node: HdNode): HdNode {
        children.add(node)
        return node
    }

    override fun hitTest(dpX: Float, dpY: Float, outPath: MutableList<HdNode>) {
        if (!visible || !bounds.contains(dpX, dpY)) return
        outPath.add(this)
        for (child in children) {
            child.hitTest(dpX, dpY, outPath)
        }
    }

    override fun renderContent(rc: HdRenderContext) {
        for (child in children) {
            if (child.visible) {
                child.render(rc)
            }
        }
    }
}

class ColumnNode(
    var gap: Float = 0f,
    var crossAlign: HdAlign = HdAlign.START,
    var mainAlign: HdAlign = HdAlign.START
) : HdContainerNode() {

    override fun measureContent(
        ctx: HdMeasureContext,
        availContentW: Float,
        availContentH: Float
    ): Pair<Float, Float> {
        val visibleChildren = children.filter { it.visible }
        if (visibleChildren.isEmpty()) return 0f to 0f

        val totalGap = gap * (visibleChildren.size - 1)
        var usedH = totalGap
        var maxW = 0f
        var totalWeight = 0f

        for (child in visibleChildren) {
            val hSpec = child.height
            if (hSpec is HdSizeSpec.Weight && availContentH.isFinite()) {
                totalWeight += hSpec.weight.coerceAtLeast(0f)
            } else {
                val remH = if (availContentH.isFinite()) (availContentH - usedH).coerceAtLeast(0f) else Float.POSITIVE_INFINITY
                child.measure(ctx, availContentW, remH)
                usedH += child.measuredHeight
                if (child.measuredWidth > maxW) maxW = child.measuredWidth
            }
        }

        if (totalWeight > 0f && availContentH.isFinite()) {
            val remaining = (availContentH - usedH).coerceAtLeast(0f)
            for (child in visibleChildren) {
                val hSpec = child.height
                if (hSpec is HdSizeSpec.Weight) {
                    val share = remaining * (hSpec.weight.coerceAtLeast(0f) / totalWeight)
                    child.measure(ctx, availContentW, share)
                    usedH += child.measuredHeight
                    if (child.measuredWidth > maxW) maxW = child.measuredWidth
                }
            }
        }

        return maxW to usedH.coerceAtLeast(0f)
    }

    override fun arrange(x: Float, y: Float, w: Float, h: Float) {
        super.arrange(x, y, w, h)
        val inner = bounds.inset(padding)
        val visibleChildren = children.filter { it.visible }
        if (visibleChildren.isEmpty()) return

        val totalGap = gap * (visibleChildren.size - 1)
        val totalChildH = visibleChildren.sumOf { it.measuredHeight.toDouble() }.toFloat() + totalGap
        var curY = when (mainAlign) {
            HdAlign.START, HdAlign.STRETCH -> inner.y
            HdAlign.CENTER -> inner.y + (inner.height - totalChildH).coerceAtLeast(0f) * 0.5f
            HdAlign.END -> inner.y + (inner.height - totalChildH).coerceAtLeast(0f)
        }

        for (child in visibleChildren) {
            val stretchW = crossAlign == HdAlign.STRETCH || child.width is HdSizeSpec.Fill
            val cw = if (stretchW) inner.width else child.measuredWidth.coerceAtMost(inner.width)
            val cx = when {
                stretchW || crossAlign == HdAlign.START -> inner.x
                crossAlign == HdAlign.CENTER -> inner.x + (inner.width - cw) * 0.5f
                crossAlign == HdAlign.END -> inner.x + (inner.width - cw)
                else -> inner.x
            }
            val ch = child.measuredHeight
            child.arrange(cx, curY, cw, ch)
            curY += ch + gap
        }
    }
}

class RowNode(
    var gap: Float = 0f,
    var crossAlign: HdAlign = HdAlign.START,
    var mainAlign: HdAlign = HdAlign.START
) : HdContainerNode() {

    override fun measureContent(
        ctx: HdMeasureContext,
        availContentW: Float,
        availContentH: Float
    ): Pair<Float, Float> {
        val visibleChildren = children.filter { it.visible }
        if (visibleChildren.isEmpty()) return 0f to 0f

        val totalGap = gap * (visibleChildren.size - 1)
        var usedW = totalGap
        var maxH = 0f
        var totalWeight = 0f

        for (child in visibleChildren) {
            val wSpec = child.width
            if (wSpec is HdSizeSpec.Weight && availContentW.isFinite()) {
                totalWeight += wSpec.weight.coerceAtLeast(0f)
            } else {
                val remW = if (availContentW.isFinite()) (availContentW - usedW).coerceAtLeast(0f) else Float.POSITIVE_INFINITY
                child.measure(ctx, remW, availContentH)
                usedW += child.measuredWidth
                if (child.measuredHeight > maxH) maxH = child.measuredHeight
            }
        }

        if (totalWeight > 0f && availContentW.isFinite()) {
            val remaining = (availContentW - usedW).coerceAtLeast(0f)
            for (child in visibleChildren) {
                val wSpec = child.width
                if (wSpec is HdSizeSpec.Weight) {
                    val share = remaining * (wSpec.weight.coerceAtLeast(0f) / totalWeight)
                    child.measure(ctx, share, availContentH)
                    usedW += child.measuredWidth
                    if (child.measuredHeight > maxH) maxH = child.measuredHeight
                }
            }
        }

        return usedW.coerceAtLeast(0f) to maxH
    }

    override fun arrange(x: Float, y: Float, w: Float, h: Float) {
        super.arrange(x, y, w, h)
        val inner = bounds.inset(padding)
        val visibleChildren = children.filter { it.visible }
        if (visibleChildren.isEmpty()) return

        val totalGap = gap * (visibleChildren.size - 1)
        val totalChildW = visibleChildren.sumOf { it.measuredWidth.toDouble() }.toFloat() + totalGap
        var curX = when (mainAlign) {
            HdAlign.START, HdAlign.STRETCH -> inner.x
            HdAlign.CENTER -> inner.x + (inner.width - totalChildW).coerceAtLeast(0f) * 0.5f
            HdAlign.END -> inner.x + (inner.width - totalChildW).coerceAtLeast(0f)
        }

        for (child in visibleChildren) {
            val stretchH = crossAlign == HdAlign.STRETCH || child.height is HdSizeSpec.Fill
            val ch = if (stretchH) inner.height else child.measuredHeight.coerceAtMost(inner.height)
            val cy = when {
                stretchH || crossAlign == HdAlign.START -> inner.y
                crossAlign == HdAlign.CENTER -> inner.y + (inner.height - ch) * 0.5f
                crossAlign == HdAlign.END -> inner.y + (inner.height - ch)
                else -> inner.y
            }
            val cw = child.measuredWidth
            child.arrange(curX, cy, cw, ch)
            curX += cw + gap
        }
    }
}

class StackNode(
    var hAlign: HdAlign = HdAlign.START,
    var vAlign: HdAlign = HdAlign.START
) : HdContainerNode() {

    private val alignments = mutableMapOf<HdNode, Pair<HdAlign, HdAlign>>()

    fun addAligned(node: HdNode, horizontal: HdAlign = hAlign, vertical: HdAlign = vAlign): HdNode {
        children.add(node)
        alignments[node] = horizontal to vertical
        return node
    }

    override fun measureContent(
        ctx: HdMeasureContext,
        availContentW: Float,
        availContentH: Float
    ): Pair<Float, Float> {
        var maxW = 0f
        var maxH = 0f
        for (child in children) {
            if (!child.visible) continue
            child.measure(ctx, availContentW, availContentH)
            if (child.measuredWidth > maxW) maxW = child.measuredWidth
            if (child.measuredHeight > maxH) maxH = child.measuredHeight
        }
        return maxW to maxH
    }

    override fun arrange(x: Float, y: Float, w: Float, h: Float) {
        super.arrange(x, y, w, h)
        val inner = bounds.inset(padding)
        for (child in children) {
            if (!child.visible) continue
            val (ah, av) = alignments[child] ?: (hAlign to vAlign)
            val cw = if (ah == HdAlign.STRETCH || child.width is HdSizeSpec.Fill) inner.width else child.measuredWidth.coerceAtMost(inner.width)
            val ch = if (av == HdAlign.STRETCH || child.height is HdSizeSpec.Fill) inner.height else child.measuredHeight.coerceAtMost(inner.height)
            val cx = when (ah) {
                HdAlign.START, HdAlign.STRETCH -> inner.x
                HdAlign.CENTER -> inner.x + (inner.width - cw) * 0.5f
                HdAlign.END -> inner.x + (inner.width - cw)
            }
            val cy = when (av) {
                HdAlign.START, HdAlign.STRETCH -> inner.y
                HdAlign.CENTER -> inner.y + (inner.height - ch) * 0.5f
                HdAlign.END -> inner.y + (inner.height - ch)
            }
            child.arrange(cx, cy, cw, ch)
        }
    }
}

class GridNode(
    var columns: Int = 2,
    var colGap: Float = 8f,
    var rowGap: Float = 8f
) : HdContainerNode() {

    private var computedColW = 0f
    private var rowHeights = emptyList<Float>()

    override fun measureContent(
        ctx: HdMeasureContext,
        availContentW: Float,
        availContentH: Float
    ): Pair<Float, Float> {
        val cols = columns.coerceAtLeast(1)
        val visibleChildren = children.filter { it.visible }
        if (visibleChildren.isEmpty()) {
            computedColW = 0f
            rowHeights = emptyList()
            return 0f to 0f
        }

        val totalColGap = colGap * (cols - 1)
        val colW = if (availContentW.isFinite()) {
            ((availContentW - totalColGap) / cols).coerceAtLeast(0f)
        } else {
            100f
        }
        computedColW = colW

        val numRows = ceil(visibleChildren.size / cols.toDouble()).toInt()
        val rHeights = MutableList(numRows) { 0f }

        visibleChildren.forEachIndexed { idx, child ->
            val r = idx / cols
            child.measure(ctx, colW, availContentH)
            if (child.measuredHeight > rHeights[r]) {
                rHeights[r] = child.measuredHeight
            }
        }
        rowHeights = rHeights

        val totalW = cols * colW + totalColGap
        val totalH = rHeights.sum() + rowGap * (numRows - 1).coerceAtLeast(0)
        return totalW to totalH
    }

    override fun arrange(x: Float, y: Float, w: Float, h: Float) {
        super.arrange(x, y, w, h)
        val inner = bounds.inset(padding)
        val cols = columns.coerceAtLeast(1)
        val visibleChildren = children.filter { it.visible }
        if (visibleChildren.isEmpty()) return

        val colW = ((inner.width - colGap * (cols - 1)) / cols).coerceAtLeast(0f)
        val rowY = FloatArray(rowHeights.size)
        var cy = inner.y
        for (r in rowHeights.indices) {
            rowY[r] = cy
            cy += rowHeights[r] + rowGap
        }

        visibleChildren.forEachIndexed { idx, child ->
            val r = idx / cols
            val c = idx % cols
            val cx = inner.x + c * (colW + colGap)
            child.arrange(cx, rowY[r], colW, rowHeights[r])
        }
    }
}

class ScrollViewNode(
    val scrollId: String,
    var stateStore: HdStateStore,
    var scrollbarWidth: Float = 4f,
    var scrollbarGap: Float = 4f,
    var showScrollbar: Boolean = true,
    var scrollSpeed: Float = 22f,
    var anchorBottom: Boolean = false,
    var onNearBottom: (() -> Unit)? = null
) : HdContainerNode() {

    init {
        clipChildren = true
        width = HdSizeSpec.Fill
        height = HdSizeSpec.Weight(1f)
    }

    private var contentHeight = 0f

    override fun measureContent(
        ctx: HdMeasureContext,
        availContentW: Float,
        availContentH: Float
    ): Pair<Float, Float> {
        val visibleChildren = children.filter { it.visible }
        if (visibleChildren.isEmpty()) {
            contentHeight = 0f
            return 0f to 0f
        }
        val reservedScroll = if (showScrollbar) scrollbarWidth + scrollbarGap else 0f
        val childAvailW = (availContentW - reservedScroll).coerceAtLeast(0f)
        var maxW = 0f
        var totalH = 0f
        for (child in visibleChildren) {
            child.measure(ctx, childAvailW, Float.POSITIVE_INFINITY)
            if (child.measuredWidth > maxW) maxW = child.measuredWidth
            totalH += child.measuredHeight
        }
        contentHeight = totalH
        val targetH = if (availContentH.isFinite()) availContentH else totalH
        return (maxW + reservedScroll) to targetH
    }

    override fun arrange(x: Float, y: Float, w: Float, h: Float) {
        super.arrange(x, y, w, h)
        val inner = bounds.inset(padding)
        val scroll = stateStore.scroll(scrollId)
        scroll.maxScroll = (contentHeight - inner.height).coerceAtLeast(0f)
        scroll.offset = scroll.offset.coerceIn(0f, scroll.maxScroll)

        val reservedScroll = if (showScrollbar && scroll.maxScroll > 0f) scrollbarWidth + scrollbarGap else 0f
        val childW = (inner.width - reservedScroll).coerceAtLeast(0f)
        var curY = if (anchorBottom && contentHeight > inner.height) {
            inner.bottom - contentHeight + scroll.offset
        } else {
            inner.y - scroll.offset
        }
        for (child in children) {
            if (!child.visible) continue
            child.arrange(inner.x, curY, childW, child.measuredHeight)
            curY += child.measuredHeight
        }
    }

    override fun renderContent(rc: HdRenderContext) {
        val inner = bounds.inset(padding)
        for (child in children) {
            if (!child.visible) continue
            if (child.bounds.bottom >= inner.y - 4f && child.bounds.y <= inner.bottom + 4f) {
                child.render(rc)
            }
        }
        val scroll = stateStore.scroll(scrollId)
        if (showScrollbar && scroll.maxScroll > 0f && inner.height > 0f) {
            val trackX = inner.right - scrollbarWidth
            val trackY = inner.y
            val trackH = inner.height
            rc.canvas.rect(trackX, trackY, scrollbarWidth, trackH, 0x22FFFFFF, scrollbarWidth * 0.5f)
            val handleH = (trackH * trackH / (trackH + scroll.maxScroll)).coerceAtLeast(18f)
            val progress = if (anchorBottom) (1f - scroll.offset / scroll.maxScroll) else (scroll.offset / scroll.maxScroll)
            val handleY = trackY + (trackH - handleH) * progress.coerceIn(0f, 1f)
            val thumbCol = if (scroll.dragging) 0xCCFFFFFF.toInt() else 0x80FFFFFF.toInt()
            rc.canvas.rect(trackX, handleY, scrollbarWidth, handleH, thumbCol, scrollbarWidth * 0.5f)
        }
    }

    private fun scrollbarTrackBounds(): HdBounds {
        val inner = bounds.inset(padding)
        return HdBounds(inner.right - scrollbarWidth - 3f, inner.y, scrollbarWidth + 6f, inner.height)
    }

    override fun onMouseDown(dpX: Float, dpY: Float, button: Int, state: HdStateStore): Boolean {
        val scroll = state.scroll(scrollId)
        if (button == 0 && showScrollbar && scroll.maxScroll > 0f && scrollbarTrackBounds().contains(dpX, dpY)) {
            scroll.dragging = true
            scroll.dragStartMouseDp = dpY
            scroll.dragStartOffset = scroll.offset
            return true
        }
        return false
    }

    override fun onMouseDrag(dpX: Float, dpY: Float, state: HdStateStore): Boolean {
        val scroll = state.scroll(scrollId)
        if (!scroll.dragging) return false
        val trackH = bounds.inset(padding).height.coerceAtLeast(1f)
        if (scroll.maxScroll > 0f) {
            val delta = (dpY - scroll.dragStartMouseDp) * scroll.maxScroll / trackH
            val signedDelta = if (anchorBottom) -delta else delta
            scroll.offset = (scroll.dragStartOffset + signedDelta).coerceIn(0f, scroll.maxScroll)
        }
        return true
    }

    override fun onMouseUp(dpX: Float, dpY: Float, button: Int, state: HdStateStore): Boolean {
        val scroll = state.scroll(scrollId)
        if (scroll.dragging) {
            scroll.dragging = false
            return true
        }
        return false
    }

    override fun onScroll(dpX: Float, dpY: Float, deltaY: Double, state: HdStateStore): Boolean {
        val scroll = state.scroll(scrollId)
        if (scroll.maxScroll <= 0f) return false
        val signedDelta = if (anchorBottom) (deltaY * scrollSpeed).toFloat() else -(deltaY * scrollSpeed).toFloat()
        scroll.offset = (scroll.offset + signedDelta).coerceIn(0f, scroll.maxScroll)
        if (scroll.offset >= scroll.maxScroll - 28f) {
            onNearBottom?.invoke()
        }
        return true
    }
}

class SpacerNode(sizeW: Float = 0f, sizeH: Float = 0f) : HdNode() {
    init {
        if (sizeW > 0f) width = HdSizeSpec.Fixed(sizeW)
        if (sizeH > 0f) height = HdSizeSpec.Fixed(sizeH)
    }

    override fun measureContent(ctx: HdMeasureContext, availContentW: Float, availContentH: Float): Pair<Float, Float> =
        0f to 0f

    override fun renderContent(rc: HdRenderContext) {}
}

class DividerNode(
    var thickness: Float = 0.8f,
    var color: HdColorSpec = HdColorSpec.Token("primary_border", 0.35f),
    var vertical: Boolean = false
) : HdNode() {
    init {
        if (vertical) {
            width = HdSizeSpec.Fixed(thickness)
            height = HdSizeSpec.Fill
        } else {
            width = HdSizeSpec.Fill
            height = HdSizeSpec.Fixed(thickness)
        }
    }

    override fun measureContent(ctx: HdMeasureContext, availContentW: Float, availContentH: Float): Pair<Float, Float> =
        if (vertical) thickness to 0f else 0f to thickness

    override fun renderContent(rc: HdRenderContext) {
        val col = color.resolve(rc.colors)
        rc.canvas.rect(bounds.x, bounds.y, bounds.width, bounds.height, col)
    }
}
