package com.nbp.cobblemon_smartphone.client.ui.hd

import com.nbp.cobblemon_smartphone.theme.SmartphoneThemeColors
import net.minecraft.client.gui.GuiGraphics

sealed interface HdSizeSpec {
    data class Fixed(val dp: Float) : HdSizeSpec
    data object Wrap : HdSizeSpec
    data object Fill : HdSizeSpec
    data class Weight(val weight: Float = 1f) : HdSizeSpec
}

enum class HdAlign { START, CENTER, END, STRETCH }

data class HdPadding(
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f,
    val left: Float = 0f
) {
    val horizontal: Float get() = left + right
    val vertical: Float get() = top + bottom

    companion object {
        val ZERO = HdPadding()
        fun all(dp: Float) = HdPadding(dp, dp, dp, dp)
        fun symmetric(horizontal: Float = 0f, vertical: Float = 0f) =
            HdPadding(vertical, horizontal, vertical, horizontal)
        fun only(top: Float = 0f, right: Float = 0f, bottom: Float = 0f, left: Float = 0f) =
            HdPadding(top, right, bottom, left)
    }
}

data class HdBounds(
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f
) {
    val right: Float get() = x + width
    val bottom: Float get() = y + height
    val centerX: Float get() = x + width * 0.5f
    val centerY: Float get() = y + height * 0.5f

    fun contains(px: Float, py: Float): Boolean =
        px >= x && px <= right && py >= y && py <= bottom

    fun inset(padding: HdPadding): HdBounds = HdBounds(
        x = x + padding.left,
        y = y + padding.top,
        width = (width - padding.horizontal).coerceAtLeast(0f),
        height = (height - padding.vertical).coerceAtLeast(0f)
    )

    companion object {
        val EMPTY = HdBounds()
    }
}

/**
 * Pure, OpenGL-free measurement context so layout trees can be measured and unit-tested
 * headlessly without a Minecraft window or GL context.
 */
data class HdMeasureContext(
    val viewportScale: Float,
    val colors: SmartphoneThemeColors = SmartphoneThemeColors.DEFAULT
)

/**
 * Runtime render context passed down the [HdNode] tree during a frame.
 */
data class HdRenderContext(
    val canvas: HdCanvas,
    val guiGraphics: GuiGraphics,
    val colors: SmartphoneThemeColors,
    val state: HdStateStore,
    val mouseDpX: Float,
    val mouseDpY: Float,
    val isModalOverlayOpen: Boolean = false
) {
    val viewportScale: Float get() = canvas.viewport.scale
}
