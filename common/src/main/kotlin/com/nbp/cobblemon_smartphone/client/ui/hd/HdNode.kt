package com.nbp.cobblemon_smartphone.client.ui.hd

/**
 * Base node of the declarative HD UI component tree. Both Kotlin screens and datapack JSON
 * screens compile into trees of [HdNode].
 */
abstract class HdNode {
    var id: String? = null
    var width: HdSizeSpec = HdSizeSpec.Wrap
    var height: HdSizeSpec = HdSizeSpec.Wrap
    var minWidth: Float = 0f
    var minHeight: Float = 0f
    var maxWidth: Float = Float.POSITIVE_INFINITY
    var maxHeight: Float = Float.POSITIVE_INFINITY
    var padding: HdPadding = HdPadding.ZERO
    var visible: Boolean = true
    var clipChildren: Boolean = false
    var tooltip: String? = null
    var background: HdBackground? = null
    var hoverBackground: HdBackground? = null
    var onClick: (() -> Unit)? = null

    var measuredWidth: Float = 0f
        protected set
    var measuredHeight: Float = 0f
        protected set
    var bounds: HdBounds = HdBounds.EMPTY
        protected set

    fun measure(ctx: HdMeasureContext, availW: Float, availH: Float) {
        if (!visible) {
            measuredWidth = 0f
            measuredHeight = 0f
            return
        }
        val cappedAvailW = minOf(availW, maxWidth)
        val cappedAvailH = minOf(availH, maxHeight)

        val targetOuterW = when (val w = width) {
            is HdSizeSpec.Fixed -> w.dp
            is HdSizeSpec.Fill -> cappedAvailW
            is HdSizeSpec.Weight -> cappedAvailW
            is HdSizeSpec.Wrap -> cappedAvailW
        }
        val targetOuterH = when (val h = height) {
            is HdSizeSpec.Fixed -> h.dp
            is HdSizeSpec.Fill -> cappedAvailH
            is HdSizeSpec.Weight -> cappedAvailH
            is HdSizeSpec.Wrap -> cappedAvailH
        }

        val innerAvailW = (targetOuterW - padding.horizontal).coerceAtLeast(0f)
        val innerAvailH = (targetOuterH - padding.vertical).coerceAtLeast(0f)

        val (contentW, contentH) = measureContent(ctx, innerAvailW, innerAvailH)

        val rawW = when (width) {
            is HdSizeSpec.Fixed -> (width as HdSizeSpec.Fixed).dp
            is HdSizeSpec.Fill -> if (availW.isFinite()) availW else contentW + padding.horizontal
            is HdSizeSpec.Weight -> if (availW.isFinite()) availW else contentW + padding.horizontal
            is HdSizeSpec.Wrap -> contentW + padding.horizontal
        }
        val rawH = when (height) {
            is HdSizeSpec.Fixed -> (height as HdSizeSpec.Fixed).dp
            is HdSizeSpec.Fill -> if (availH.isFinite()) availH else contentH + padding.vertical
            is HdSizeSpec.Weight -> if (availH.isFinite()) availH else contentH + padding.vertical
            is HdSizeSpec.Wrap -> contentH + padding.vertical
        }

        measuredWidth = rawW.coerceIn(minWidth, maxWidth)
        measuredHeight = rawH.coerceIn(minHeight, maxHeight)
    }

    protected abstract fun measureContent(
        ctx: HdMeasureContext,
        availContentW: Float,
        availContentH: Float
    ): Pair<Float, Float>

    open fun arrange(x: Float, y: Float, w: Float, h: Float) {
        bounds = HdBounds(x, y, w, h)
    }

    fun render(rc: HdRenderContext) {
        if (!visible || bounds.width <= 0f || bounds.height <= 0f) return
        renderBackground(rc)
        if (clipChildren) {
            rc.canvas.clipped(bounds.x, bounds.y, bounds.width, bounds.height) {
                renderContent(rc)
            }
        } else {
            renderContent(rc)
        }
    }

    protected open fun renderBackground(rc: HdRenderContext) {
        val hovered = isHovered(rc)
        val bg = (if (hovered) hoverBackground else null) ?: background ?: return
        val x = bounds.x
        val y = bounds.y
        val w = bounds.width
        val h = bounds.height
        if (bg.shadowBlur > 0f) {
            val sCol = bg.shadowColor.resolve(rc.colors)
            if (((sCol ushr 24) and 0xFF) > 0) {
                rc.canvas.shadow(x, y, w, h, bg.radius, bg.shadowBlur, bg.shadowOffsetY, sCol)
            }
        }
        val topCol = bg.color.resolve(rc.colors)
        val botCol = bg.colorBottom?.resolve(rc.colors) ?: topCol
        val brdCol = bg.borderColor.resolve(rc.colors)
        val hasFill = ((topCol ushr 24) and 0xFF) > 0 || ((botCol ushr 24) and 0xFF) > 0
        val hasBorder = bg.borderWidth > 0f && ((brdCol ushr 24) and 0xFF) > 0
        if (hasFill || hasBorder) {
            rc.canvas.rectCorners(
                x = x,
                y = y,
                w = w,
                h = h,
                rTL = bg.radius,
                rTR = bg.radius,
                rBR = bg.radius,
                rBL = bg.radius,
                colorTop = topCol,
                colorBottom = botCol,
                borderW = bg.borderWidth,
                borderColor = brdCol
            )
        }
    }

    protected abstract fun renderContent(rc: HdRenderContext)

    open fun hitTest(dpX: Float, dpY: Float, outPath: MutableList<HdNode>) {
        if (!visible || !bounds.contains(dpX, dpY)) return
        outPath.add(this)
    }

    open fun onMouseDown(dpX: Float, dpY: Float, button: Int, state: HdStateStore): Boolean {
        if (button == 0 && onClick != null) {
            onClick?.invoke()
            return true
        }
        return false
    }

    open fun onMouseDrag(dpX: Float, dpY: Float, state: HdStateStore): Boolean = false

    open fun onMouseUp(dpX: Float, dpY: Float, button: Int, state: HdStateStore): Boolean = false

    open fun onScroll(dpX: Float, dpY: Float, deltaY: Double, state: HdStateStore): Boolean = false

    protected fun isHovered(rc: HdRenderContext): Boolean {
        val nodeId = id
        return if (nodeId != null && rc.state.hoveredId != null) {
            rc.state.hoveredId == nodeId
        } else {
            bounds.contains(rc.mouseDpX, rc.mouseDpY)
        }
    }
}
