package com.nbp.cobblemon_smartphone.client.gui

import net.minecraft.client.gui.GuiGraphics

/** Draws the page indicators without depending on the texture state of app badges/text. */
object PageDotRenderer {
    private val ACTIVE_ALPHA = intArrayOf(
        10, 17, 24, 29, 32, 29, 24, 17, 10,
        17, 28, 40, 50, 53, 50, 40, 28, 17,
        24, 40, 57, 229, 230, 229, 57, 40, 24,
        29, 50, 229, 242, 252, 242, 229, 50, 29,
        32, 53, 230, 252, 255, 252, 230, 53, 32,
        29, 50, 229, 242, 252, 242, 229, 50, 29,
        24, 40, 57, 229, 230, 229, 57, 40, 24,
        17, 28, 40, 50, 53, 50, 40, 28, 17,
        10, 17, 24, 29, 32, 29, 24, 17, 10,
    )
    private val INACTIVE_ALPHA = intArrayOf(
        2, 3, 5, 6, 6, 6, 5, 3, 2,
        3, 6, 8, 10, 10, 10, 8, 6, 3,
        5, 8, 11, 116, 116, 116, 11, 8, 5,
        6, 10, 116, 124, 131, 124, 116, 10, 6,
        6, 10, 116, 131, 133, 131, 116, 10, 6,
        6, 10, 116, 124, 131, 124, 116, 10, 6,
        5, 8, 11, 116, 116, 116, 11, 8, 5,
        3, 6, 8, 10, 10, 10, 8, 6, 3,
        2, 3, 5, 6, 6, 6, 5, 3, 2,
    )

    fun draw(guiGraphics: GuiGraphics, x: Int, y: Int, active: Boolean) {
        val alpha = if (active) ACTIVE_ALPHA else INACTIVE_ALPHA
        val rgb = if (active) 0x00FFFFFF else 0x00000000
        alpha.forEachIndexed { index, value ->
            if (value == 0) return@forEachIndexed
            val px = index % SIZE
            val py = index / SIZE
            guiGraphics.fill(x + px, y + py, x + px + 1, y + py + 1, (value shl 24) or rgb)
        }
    }

    private const val SIZE = 9
}
