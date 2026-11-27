package com.nbp.cobblemon_smartphone.client.gui

import com.nbp.cobblemon_smartphone.client.RotomDroneClientManager
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import java.util.Locale

object RotomDroneOverlay {
    private const val CYAN = 0xFF38BDF8.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val BG = 0xB8071322.toInt()
    private const val SIGNAL_GOOD = 0xFF22C55E.toInt()
    private const val SIGNAL_WARN = 0xFFFACC15.toInt()
    private const val SIGNAL_LOW = 0xFFEF4444.toInt()
    private const val SIGNAL_OFF = 0x55FFFFFF

    fun render(guiGraphics: GuiGraphics) {
        if (!RotomDroneClientManager.isActive() || RotomDroneClientManager.isCapturingPhoto()) return
        val mc = Minecraft.getInstance()
        if (mc.screen != null) return

        val width = mc.window.guiScaledWidth
        val height = mc.window.guiScaledHeight
        val cx = width / 2
        val cy = height / 2
        val font = mc.font

        // Tech viewfinder corners
        drawCorner(guiGraphics, 12, 12, 1, 1)
        drawCorner(guiGraphics, width - 13, 12, -1, 1)
        drawCorner(guiGraphics, 12, height - 13, 1, -1)
        drawCorner(guiGraphics, width - 13, height - 13, -1, -1)

        // Subtle center reticle
        guiGraphics.fill(cx - 5, cy, cx - 2, cy + 1, 0x9938BDF8.toInt())
        guiGraphics.fill(cx + 3, cy, cx + 6, cy + 1, 0x9938BDF8.toInt())
        guiGraphics.fill(cx, cy - 5, cx + 1, cy - 2, 0x9938BDF8.toInt())
        guiGraphics.fill(cx, cy + 3, cx + 1, cy + 6, 0x9938BDF8.toInt())

        // Top telemetry pill: title + distance + signal bars
        val dist = RotomDroneClientManager.getDistanceToPlayer()
        val maxRange = RotomDroneClientManager.getMaxRange()
        val ratio = (1.0 - (dist / maxRange)).coerceIn(0.0, 1.0)
        val activeBars = when {
            ratio > 0.72 -> 4
            ratio > 0.45 -> 3
            ratio > 0.20 -> 2
            else -> 1
        }
        val signalColor = when (activeBars) {
            4, 3 -> SIGNAL_GOOD
            2 -> SIGNAL_WARN
            else -> SIGNAL_LOW
        }

        val title = Component.translatable("cobblemon_smartphone.rotom.drone_hud_title").string
        val distText = String.format(Locale.ROOT, "%.1fm / %.0fm", dist, maxRange)
        val topText = "$title  |  $distText"
        val barsWidth = 18
        val totalTopW = font.width(topText) + 8 + barsWidth
        val topLeft = cx - totalTopW / 2

        guiGraphics.fill(topLeft - 7, 10, topLeft + totalTopW + 7, 26, BG)
        guiGraphics.fill(topLeft - 7, 10, topLeft + totalTopW + 7, 11, CYAN)
        guiGraphics.drawString(font, topText, topLeft, 14, WHITE, false)

        // 4 ascending signal bars
        val barBaseX = topLeft + font.width(topText) + 8
        val barBaseY = 23
        for (i in 0 until 4) {
            val barH = 3 + i * 2
            val bx = barBaseX + i * 4
            val col = if (i < activeBars) signalColor else SIGNAL_OFF
            guiGraphics.fill(bx, barBaseY - barH, bx + 3, barBaseY, col)
        }

        // Bottom controls hint
        val hintKey = if (RotomDroneClientManager.canTakeAerialPhoto()) {
            "cobblemon_smartphone.rotom.drone_hud_controls_photo"
        } else {
            "cobblemon_smartphone.rotom.drone_hud_controls"
        }
        val hint = Component.translatable(hintKey).string
        val hintW = font.width(hint)
        guiGraphics.fill(cx - hintW / 2 - 7, height - 27, cx + hintW / 2 + 7, height - 11, BG)
        guiGraphics.drawString(font, hint, cx - hintW / 2, height - 23, WHITE, false)
    }

    private fun drawCorner(guiGraphics: GuiGraphics, x: Int, y: Int, dx: Int, dy: Int) {
        val len = 18
        val x2 = x + dx * len
        val y2 = y + dy * len
        guiGraphics.fill(minOf(x, x2), y, maxOf(x, x2) + 1, y + 2, CYAN)
        guiGraphics.fill(x, minOf(y, y2), x + 2, maxOf(y, y2) + 1, CYAN)
    }
}
