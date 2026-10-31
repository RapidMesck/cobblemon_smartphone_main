package com.nbp.cobblemon_smartphone.client.gui

import com.nbp.cobblemon_smartphone.CobblemonSmartphone
import com.nbp.cobblemon_smartphone.energy.getEnergy
import com.nbp.cobblemon_smartphone.energy.getMaxEnergy
import com.nbp.cobblemon_smartphone.energy.isInfiniteEnergy
import com.nbp.cobblemon_smartphone.theme.SmartphoneThemeHelper
import com.nbp.cobblemon_smartphone.util.SmartphoneHelper
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

object SmartphoneBatteryRenderer {
    const val SMALL_SCREEN_BATTERY_X = 101
    const val SMALL_SCREEN_BATTERY_Y = 19

    const val LARGE_SCREEN_BATTERY_X = 181
    const val LARGE_SCREEN_BATTERY_Y = 17

    const val BATTERY_BODY_WIDTH = 9
    const val BATTERY_TOTAL_WIDTH = 10
    const val BATTERY_HEIGHT = 5

    const val BORDER_COLOR = 0xFF5BD1DA.toInt()
    const val EMPTY_COLOR = 0xFF1D4A59.toInt()
    const val FILL_COLOR_HIGH = 0xFF22C55E.toInt()
    const val FILL_COLOR_MED = 0xFFEAB308.toInt()
    const val FILL_COLOR_LOW = 0xFFEF4444.toInt()

    fun resolveSmartphone(smartphoneStack: ItemStack?): ItemStack? {
        val player = Minecraft.getInstance().player ?: return smartphoneStack
        if (smartphoneStack != null) {
            if (player.mainHandItem.item == smartphoneStack.item) return player.mainHandItem
            if (player.offhandItem.item == smartphoneStack.item) return player.offhandItem
            val invMatch = player.inventory.items.firstOrNull { it.item == smartphoneStack.item }
            if (invMatch != null) return invMatch
        }
        return SmartphoneHelper.getSmartphone(player) ?: smartphoneStack
    }

    fun isHovered(screenX: Int, screenY: Int, mouseX: Int, mouseY: Int, isLargeScreen: Boolean): Boolean {
        if (!CobblemonSmartphone.config.energy.enableEnergy) return false
        val bx = screenX + if (isLargeScreen) LARGE_SCREEN_BATTERY_X else SMALL_SCREEN_BATTERY_X
        val by = screenY + if (isLargeScreen) LARGE_SCREEN_BATTERY_Y else SMALL_SCREEN_BATTERY_Y
        return mouseX >= bx && mouseX <= bx + BATTERY_TOTAL_WIDTH &&
                mouseY >= by && mouseY <= by + BATTERY_HEIGHT
    }

    fun render(
        guiGraphics: GuiGraphics,
        screenX: Int,
        screenY: Int,
        isLargeScreen: Boolean,
        smartphoneStack: ItemStack?
    ) {
        if (!CobblemonSmartphone.config.energy.enableEnergy) return
        val phone = resolveSmartphone(smartphoneStack) ?: return
        val max = phone.getMaxEnergy()
        if (max <= 0) return
        val current = phone.getEnergy()
        val theme = SmartphoneThemeHelper.resolveCurrentTheme(phone)
        val colors = theme.colors

        val bx = screenX + if (isLargeScreen) LARGE_SCREEN_BATTERY_X else SMALL_SCREEN_BATTERY_X
        val by = screenY + if (isLargeScreen) LARGE_SCREEN_BATTERY_Y else SMALL_SCREEN_BATTERY_Y

        val isInfinite = phone.isInfiniteEnergy()
        val borderColor = if (isInfinite) 0xFF00E5FF.toInt() else colors.batteryBorder

        // Battery outer border (9x5 body)
        // Top and bottom horizontal borders
        guiGraphics.fill(bx, by, bx + BATTERY_BODY_WIDTH, by + 1, borderColor)
        guiGraphics.fill(bx, by + BATTERY_HEIGHT - 1, bx + BATTERY_BODY_WIDTH, by + BATTERY_HEIGHT, borderColor)
        // Left and right vertical borders
        guiGraphics.fill(bx, by + 1, bx + 1, by + BATTERY_HEIGHT - 1, borderColor)
        guiGraphics.fill(bx + BATTERY_BODY_WIDTH - 1, by + 1, bx + BATTERY_BODY_WIDTH, by + BATTERY_HEIGHT - 1, borderColor)

        // Battery tip ("bico da pilha") at the right end (1px wide, 3px high centered)
        guiGraphics.fill(bx + BATTERY_BODY_WIDTH, by + 1, bx + BATTERY_TOTAL_WIDTH, by + BATTERY_HEIGHT - 1, borderColor)

        // Empty cavity background (7px wide, 3px high)
        guiGraphics.fill(bx + 1, by + 1, bx + BATTERY_BODY_WIDTH - 1, by + BATTERY_HEIGHT - 1, colors.batteryEmpty)

        // Dynamic charge fill inside the cavity
        val ratio = if (isInfinite) 1.0f else (current.toFloat() / max).coerceIn(0f, 1f)
        val innerWidth = BATTERY_BODY_WIDTH - 2
        val fillWidth = if (isInfinite || current > 0) Math.max(1, Math.round(innerWidth.toFloat() * ratio).toInt()).coerceIn(1, innerWidth) else 0

        if (fillWidth > 0) {
            val fillColor = when {
                isInfinite -> 0xFF00E5FF.toInt() // Ciano elétrico especial do Rotom
                ratio > 0.5f -> colors.batteryHigh
                ratio > 0.2f -> colors.batteryMed
                else -> colors.batteryLow
            }
            guiGraphics.fill(bx + 1, by + 1, bx + 1 + fillWidth, by + BATTERY_HEIGHT - 1, fillColor)
        }
    }

    fun renderTooltip(
        font: Font,
        guiGraphics: GuiGraphics,
        screenX: Int,
        screenY: Int,
        mouseX: Int,
        mouseY: Int,
        isLargeScreen: Boolean,
        smartphoneStack: ItemStack?
    ): Boolean {
        if (!isHovered(screenX, screenY, mouseX, mouseY, isLargeScreen)) return false
        val phone = resolveSmartphone(smartphoneStack) ?: return false
        val current = phone.getEnergy()
        val max = phone.getMaxEnergy()
        val isInfinite = phone.isInfiniteEnergy()

        val tooltip = if (isInfinite) {
            Component.translatable("tooltip.cobblemon_smartphone.energy_rotom_infinite")
        } else {
            val percent = if (max > 0) ((current.toDouble() / max) * 100).toInt() else 0
            Component.translatable(
                "tooltip.cobblemon_smartphone.energy_status",
                "%,d".format(current),
                "%,d".format(max),
                percent
            )
        }
        guiGraphics.renderTooltip(font, tooltip, mouseX, mouseY)
        return true
    }
}
