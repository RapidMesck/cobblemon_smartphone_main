package com.nbp.cobblemon_smartphone.client.gui

import net.minecraft.client.gui.GuiGraphics

/** Small pixel-art primitives shared by every Social screen. */
object SocialUi {
    enum class Icon { FEED, MESSAGE, PLUS, BELL, SEND, PHONE, TRASH, HEART, BACK, CLOSE, SEARCH, USER }

    const val NAVY = 0xFF163A49.toInt()
    const val NAVY_LIGHT = 0xFF24576A.toInt()
    const val CYAN = 0xFF3A96B6.toInt()
    const val CYAN_HOVER = 0xFF55B5D1.toInt()
    const val SURFACE = 0xFFF2FBFD.toInt()
    const val SURFACE_ALT = 0xFFDDEFF4.toInt()
    const val BORDER = 0xFF79B7C9.toInt()
    const val SHADOW = 0x35000000
    const val TEXT = 0xFF17313B.toInt()
    const val MUTED = 0xFF607D87.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
    const val DANGER = 0xFFD84A4A.toInt()
    const val SUCCESS = 0xFF43A968.toInt()
    const val GOLD = 0xFFF2C14E.toInt()

    fun surface(g: GuiGraphics, x: Int, y: Int, width: Int, height: Int, hovered: Boolean = false) {
        g.fill(x + 1, y + 2, x + width + 1, y + height + 2, SHADOW)
        g.fill(x, y, x + width, y + height, BORDER)
        g.fill(x + 1, y + 1, x + width - 1, y + height - 1, if (hovered) SURFACE_ALT else SURFACE)
    }

    fun iconButton(g: GuiGraphics, icon: Icon, x: Int, y: Int, size: Int, hovered: Boolean, active: Boolean = false) {
        val bg = when { active -> CYAN; hovered -> CYAN_HOVER; else -> SURFACE }
        g.fill(x, y, x + size, y + size, BORDER)
        g.fill(x + 1, y + 1, x + size - 1, y + size - 1, bg)
        val iconSize = if (icon == Icon.BELL || icon == Icon.PLUS || icon == Icon.PHONE || icon == Icon.SEND) 8 else 9
        drawIcon(
            g,
            icon,
            x + (size - iconSize) / 2,
            y + (size - iconSize) / 2,
            if (active) WHITE else TEXT
        )
    }

    fun drawIcon(g: GuiGraphics, icon: Icon, x: Int, y: Int, color: Int) {
        fun p(px: Int, py: Int, w: Int = 1, h: Int = 1) = g.fill(x + px, y + py, x + px + w, y + py + h, color)
        when (icon) {
            Icon.FEED -> { p(1, 1, 2, 2); p(4, 1, 4); p(1, 4, 2, 2); p(4, 4, 4); p(1, 7, 2); p(4, 7, 4) }
            Icon.MESSAGE -> { p(1, 1, 7, 1); p(0, 2, 1, 5); p(8, 2, 1, 5); p(1, 7, 2); p(6, 7, 2); p(3, 6, 3, 1); p(2, 7, 1, 2) }
            Icon.PLUS -> { p(3, 0, 2, 8); p(0, 3, 8, 2) }
            Icon.BELL -> { p(3, 0, 2); p(2, 1, 4); p(1, 2, 6, 4); p(0, 6, 8); p(3, 7, 2) }
            Icon.SEND -> {
                // Symmetric 8x8 arrow: every segment is two pixels thick around the centre axis.
                p(4, 0, 2); p(5, 1, 2); p(6, 2, 2)
                p(0, 3, 8, 2)
                p(6, 5, 2); p(5, 6, 2); p(4, 7, 2)
            }
            Icon.PHONE -> {
                // Strict 8x8 curved handset.
                p(0, 0, 3); p(0, 1); p(2, 1, 2); p(0, 2, 2); p(3, 2, 2)
                p(1, 3, 2); p(4, 3, 2); p(2, 4, 2); p(5, 4, 2)
                p(3, 5, 2); p(6, 5, 2); p(4, 6, 2); p(7, 6); p(5, 7, 3)
            }
            Icon.TRASH -> { p(2, 1, 5); p(1, 2, 7); p(2, 3, 5, 6); p(3, 4, 1, 4); p(5, 4, 1, 4) }
            Icon.HEART -> { p(1, 0, 3); p(5, 0, 3); p(0, 1, 9, 3); p(1, 4, 7); p(2, 5, 5); p(3, 6, 3); p(4, 7) }
            Icon.BACK -> { p(1, 4, 7); p(2, 3, 1, 3); p(3, 2, 1, 5); p(4, 1); p(4, 7) }
            Icon.CLOSE -> { p(1, 1, 2, 2); p(6, 1, 2, 2); p(2, 2, 2, 2); p(5, 2, 2, 2); p(3, 3, 3, 3); p(2, 5, 2, 2); p(5, 5, 2, 2); p(1, 6, 2, 2); p(6, 6, 2, 2) }
            Icon.SEARCH -> { p(1, 1, 5, 1); p(0, 2, 1, 4); p(6, 2, 1, 4); p(1, 6, 5); p(6, 6, 1); p(7, 7, 1); p(8, 8) }
            Icon.USER -> { p(3, 0, 3, 3); p(2, 3, 5); p(1, 5, 7, 4) }
        }
    }
}
