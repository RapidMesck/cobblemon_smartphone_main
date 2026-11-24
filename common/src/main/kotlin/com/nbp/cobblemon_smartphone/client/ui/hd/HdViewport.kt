package com.nbp.cobblemon_smartphone.client.ui.hd

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

/**
 * Resolution-independent viewport mapping from density-independent design units (`dp`)
 * directly to physical framebuffer pixels, completely bypassing Minecraft's integer `guiScale`.
 */
data class HdViewport(
    val windowWidth: Int,
    val windowHeight: Int,
    val guiScale: Double,
    val designWidth: Float,
    val designHeight: Float,
    val scale: Float,
    val originX: Float,
    val originY: Float
) {
    data class GlScissorRect(val x: Int, val y: Int, val width: Int, val height: Int) {
        val isEmpty: Boolean get() = width <= 0 || height <= 0

        fun intersect(other: GlScissorRect): GlScissorRect {
            val left = maxOf(x, other.x)
            val bottom = maxOf(y, other.y)
            val right = minOf(x + width, other.x + other.width)
            val top = minOf(y + height, other.y + other.height)
            return GlScissorRect(
                x = left,
                y = bottom,
                width = (right - left).coerceAtLeast(0),
                height = (top - bottom).coerceAtLeast(0)
            )
        }
    }

    fun toPhysX(dpX: Float): Float = originX + dpX * scale
    fun toPhysY(dpY: Float): Float = originY + dpY * scale
    fun toPhysSize(dpSize: Float): Float = dpSize * scale

    fun physMouseToDpX(physX: Double): Float = (physX.toFloat() - originX) / scale
    fun physMouseToDpY(physY: Double): Float = (physY.toFloat() - originY) / scale

    fun mcGuiMouseToDpX(mcMouseX: Double): Float =
        ((mcMouseX * guiScale).toFloat() - originX) / scale

    fun mcGuiMouseToDpY(mcMouseY: Double): Float =
        ((mcMouseY * guiScale).toFloat() - originY) / scale

    fun toMcGuiX(dpX: Float): Float = toPhysX(dpX) / guiScale.toFloat().coerceAtLeast(1f)
    fun toMcGuiY(dpY: Float): Float = toPhysY(dpY) / guiScale.toFloat().coerceAtLeast(1f)
    fun toMcGuiScale(dpSize: Float): Float = toPhysSize(dpSize) / guiScale.toFloat().coerceAtLeast(1f)

    fun toGlScissor(dpX: Float, dpY: Float, dpW: Float, dpH: Float): GlScissorRect {
        val x0 = floor(toPhysX(dpX)).toInt().coerceIn(0, windowWidth)
        val x1 = ceil(toPhysX(dpX + dpW)).toInt().coerceIn(0, windowWidth)
        val yTop = floor(toPhysY(dpY)).toInt().coerceIn(0, windowHeight)
        val yBot = ceil(toPhysY(dpY + dpH)).toInt().coerceIn(0, windowHeight)
        return GlScissorRect(
            x = x0,
            y = (windowHeight - yBot).coerceIn(0, windowHeight),
            width = (x1 - x0).coerceAtLeast(0),
            height = (yBot - yTop).coerceAtLeast(0)
        )
    }

    companion object {
        const val MIN_USER_SCALE = 0.70f
        const val MAX_USER_SCALE = 1.45f

        fun compute(
            windowWidth: Int,
            windowHeight: Int,
            guiScale: Double,
            designWidth: Float,
            designHeight: Float,
            userScale: Float = 1.0f,
            maxWidthFraction: Float = 0.92f,
            maxHeightFraction: Float = 0.84f
        ): HdViewport {
            val safeW = windowWidth.coerceAtLeast(1)
            val safeH = windowHeight.coerceAtLeast(1)
            val safeGuiScale = guiScale.coerceAtLeast(1.0)
            val clampedUser = userScale.coerceIn(MIN_USER_SCALE, MAX_USER_SCALE)

            val baseScale = min(
                (safeW * maxWidthFraction) / designWidth,
                (safeH * maxHeightFraction) / designHeight
            )
            val maxFitScale = min(
                (safeW * 0.97f) / designWidth,
                (safeH * 0.97f) / designHeight
            ).coerceAtLeast(0.25f)

            val finalScale = (baseScale * clampedUser).coerceIn(0.25f, maxFitScale)
            val originX = (safeW - designWidth * finalScale) * 0.5f
            val originY = (safeH - designHeight * finalScale) * 0.5f

            return HdViewport(
                windowWidth = safeW,
                windowHeight = safeH,
                guiScale = safeGuiScale,
                designWidth = designWidth,
                designHeight = designHeight,
                scale = finalScale,
                originX = originX,
                originY = originY
            )
        }
    }
}
